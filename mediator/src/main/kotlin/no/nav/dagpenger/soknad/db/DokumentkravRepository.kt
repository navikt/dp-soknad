package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.db.DBUtils.norskZonedDateTime
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.serder.SøknadDTO
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import java.util.UUID
import javax.sql.DataSource

val logger = KotlinLogging.logger { }

interface DokumentkravRepository {
    fun håndter(hendelse: LeggTilFil)
    fun håndter(hendelse: SlettFil)
    fun håndter(hendelse: DokumentasjonIkkeTilgjengelig)
    fun håndter(hendelse: DokumentKravSammenstilling)
    fun hent(søknadId: UUID): Dokumentkrav
    fun hentDTO(søknadId: UUID): SøknadDTO.DokumentkravDTO
}

internal class PostgresDokumentkravRepository(private val datasource: DataSource) : DokumentkravRepository {

    override fun håndter(hendelse: LeggTilFil) {
        val fil = hendelse.fil
        using(sessionOf(datasource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        // language=PostgreSQL
                        statement = """
                    INSERT INTO dokumentkrav_filer_v1(faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt, bundlet)
                    VALUES (:faktum_id, :soknad_uuid, :filnavn, :storrelse, :urn, :tidspunkt,:bundlet)
                    ON CONFLICT (faktum_id, soknad_uuid, urn) DO UPDATE SET filnavn = :filnavn, 
                                                                            storrelse = :storrelse, 
                                                                            tidspunkt = :tidspunkt,
                                                                            bundlet = :bundlet
                        """.trimIndent(),
                        mapOf(
                            "faktum_id" to hendelse.kravId,
                            "soknad_uuid" to hendelse.søknadID,
                            "filnavn" to fil.filnavn,
                            "storrelse" to fil.storrelse,
                            "urn" to fil.urn.toString(),
                            "tidspunkt" to fil.tidspunkt,
                            "bundlet" to fil.bundlet
                        )
                    ).asExecute
                )
                tx.settDokumentkravTilSendNå(hendelse)
            }
        }
    }

    private fun Session.settDokumentkravTilSendNå(hendelse: LeggTilFil) {
        run(
            queryOf(
                // language=PostgreSQL
                """ UPDATE dokumentkrav_v1 SET valg = '${Krav.Svar.SvarValg.SEND_NÅ.name}', begrunnelse = null
                            WHERE soknad_uuid = :soknadId AND faktum_id = :kravId
                        """.trimMargin(),
                mapOf(
                    "soknadId" to hendelse.søknadID,
                    "kravId" to hendelse.kravId,
                )
            ).asUpdate
        )
    }

    override fun håndter(hendelse: SlettFil) {
        using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid AND faktum_id = :faktum_id AND urn = :urn",
                    mapOf(
                        "uuid" to hendelse.søknadID,
                        "faktum_id" to hendelse.kravId,
                        "urn" to hendelse.urn.toString()
                    )
                ).asUpdate
            )
        }
    }

    override fun håndter(hendelse: DokumentasjonIkkeTilgjengelig) {
        using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    """ UPDATE dokumentkrav_v1 SET valg = :valg, begrunnelse = :begrunnelse
                        WHERE soknad_uuid = :soknadId AND faktum_id = :kravId
                    """.trimMargin(),
                    mapOf(
                        "soknadId" to hendelse.søknadID,
                        "kravId" to hendelse.kravId,
                        "valg" to hendelse.valg.name,
                        "begrunnelse" to hendelse.begrunnelse
                    )
                ).asUpdate
            )
        }.also { rowsUpdated ->
            if (rowsUpdated != 1) {
                logger.warn {
                    "Fant ikke dokumentasjon krav ${hendelse.kravId} for søknad: ${hendelse.søknadID}"
                }
            }
        }
    }

    override fun håndter(hendelse: DokumentKravSammenstilling) {
        using(sessionOf(datasource)) { session ->
            session.transaction { tx ->
                tx.settDokumentasjonskravSomBundlet(hendelse)
                tx.settDokumentasjonskravfilerSomBundlet(hendelse)
            }
        }
    }

    private fun Session.settDokumentasjonskravSomBundlet(hendelse: DokumentKravSammenstilling) {
        run(
            queryOf(
                // language=PostgreSQL
                """ UPDATE dokumentkrav_v1 SET bundle_urn = :bundle_urn, valg = '${Krav.Svar.SvarValg.SEND_NÅ.name}'
                            WHERE soknad_uuid = :soknadId AND faktum_id = :kravId
                        """.trimMargin(),
                mapOf(
                    "soknadId" to hendelse.søknadID,
                    "kravId" to hendelse.kravId,
                    "bundle_urn" to hendelse.urn().toString()
                )
            ).asUpdate
        )
    }

    private fun Session.settDokumentasjonskravfilerSomBundlet(hendelse: DokumentKravSammenstilling) {
        run(
            queryOf(
                // language=PostgreSQL
                """ UPDATE dokumentkrav_filer_v1 SET bundlet = true
                        WHERE soknad_uuid = :soknadId AND faktum_id = :kravId
                    """.trimMargin(),
                mapOf(
                    "soknadId" to hendelse.søknadID,
                    "kravId" to hendelse.kravId
                )
            ).asUpdate
        )
    }

    override fun hentDTO(søknadId: UUID): SøknadDTO.DokumentkravDTO {
        return using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    statement = """
                                      SELECT faktum_id, beskrivende_id, faktum, sannsynliggjoer, tilstand, valg, begrunnelse, bundle_urn, innsendt
                                      FROM dokumentkrav_v1 
                                      WHERE soknad_uuid = :soknad_uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "soknad_uuid" to søknadId
                    )
                ).map { row ->
                    val faktumId = row.string("faktum_id")
                    SøknadDTO.DokumentkravDTO.KravDTO(
                        id = faktumId,
                        beskrivendeId = row.string("beskrivende_id"),
                        sannsynliggjøring = SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO(
                            id = faktumId,
                            faktum = objectMapper.readTree(row.binaryStream("faktum")),
                            sannsynliggjør = objectMapper.readTree(row.binaryStream("sannsynliggjoer")).toSet()
                        ),
                        svar = SøknadDTO.DokumentkravDTO.SvarDTO(
                            begrunnelse = row.stringOrNull("begrunnelse"),
                            filer = session.hentFiler(søknadId, faktumId),
                            valg = SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO.valueOf(row.string("valg")),
                            bundle = row.stringOrNull("bundle_urn")?.let { URN.rfc8141().parse(it) },
                            innsendt = row.boolean("innsendt")
                        ),
                        tilstand = row.string("tilstand")
                            .let { SøknadDTO.DokumentkravDTO.KravDTO.KravTilstandDTO.valueOf(it) }
                    )
                }.asList
            )
                .toSet()
                .let { SøknadDTO.DokumentkravDTO(it) }
        }
    }

    override fun hent(søknadId: UUID): Dokumentkrav = hentDTO(søknadId).rehydrer()

    private fun Session.hentFiler(
        søknadsId: UUID,
        faktumId: String
    ): Set<SøknadDTO.DokumentkravDTO.KravDTO.FilDTO> {
        return this.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                  SELECT faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt, bundlet
                  FROM dokumentkrav_filer_v1 
                  WHERE soknad_uuid = :soknad_uuid
                  AND faktum_id = :faktum_id 
                """.trimIndent(),
                paramMap = mapOf(
                    "soknad_uuid" to søknadsId,
                    "faktum_id" to faktumId
                )
            ).map { row ->
                SøknadDTO.DokumentkravDTO.KravDTO.FilDTO(
                    filnavn = row.string("filnavn"),
                    urn = URN.rfc8141().parse(row.string("urn")),
                    storrelse = row.long("storrelse"),
                    tidspunkt = row.norskZonedDateTime("tidspunkt"),
                    bundlet = row.boolean("bundlet")
                )
            }.asList
        ).toSet()
    }
}
