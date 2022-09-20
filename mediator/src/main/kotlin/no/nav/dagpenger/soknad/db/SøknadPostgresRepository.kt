package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import de.slub.urn.URN
import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.livssyklus.PåbegyntSøknad
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.serder.AktivitetsloggDTO
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.SøknadDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO.KravTilstandDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.postgresql.util.PGobject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class SøknadPostgresRepository(private val dataSource: DataSource) :
    SøknadRepository {
    override fun hent(søknadId: UUID, ident: String): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT uuid, tilstand, spraak, sist_endret_av_bruker
                    FROM  soknad_v1
                    WHERE uuid = :uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId
                    )
                ).map(rowToSøknadDTO(ident, session)).asSingle
            )?.rehydrer()
        }
    }

    override fun hentSøknader(ident: String): Set<Søknad> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                            SELECT uuid, tilstand, spraak, sist_endret_av_bruker
                            FROM  soknad_v1
                            WHERE person_ident = :ident
                    """.trimIndent(),
                    paramMap = mapOf(
                        "ident" to ident
                    )
                ).map(rowToSøknadDTO(ident, session)).asList
            ).map { it.rehydrer() }.toSet()
        }
    }

    private fun rowToSøknadDTO(
        ident: String,
        session: Session
    ) = { row: Row ->
        // TODO: Fjern duplisering fra hent()
        val søknadsId = UUID.fromString(row.string("uuid"))
        SøknadDTO(
            søknadsId = søknadsId,
            ident = ident,
            tilstandType = SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
            språkDTO = SøknadDTO.SpråkDTO(row.string("spraak")),
            dokumentkrav = SøknadDTO.DokumentkravDTO(
                session.hentDokumentKrav(søknadsId)
            ),
            sistEndretAvBruker = row.zonedDateTimeOrNull("sist_endret_av_bruker"),
            aktivitetslogg = session.hentAktivitetslogg(søknadsId)
        )
    }

    override fun lagre(søknad: Søknad) {
        val visitor = SøknadPersistenceVisitor(søknad)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                visitor.queries().forEach {
                    transactionalSession.run(it.asUpdate)
                }
            }
        }
    }

    override fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad? {
        TODO("Not yet implemented")
    }

    internal class PersistentSøkerOppgave(private val søknad: JsonNode) : SøkerOppgave {
        override fun søknadUUID(): UUID = UUID.fromString(søknad[SøkerOppgave.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = søknad[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
        override fun opprettet(): LocalDateTime = søknad[SøkerOppgave.Keys.OPPRETTET].asLocalDateTime()
        override fun ferdig(): Boolean = søknad[SøkerOppgave.Keys.FERDIG].asBoolean()

        override fun asFrontendformat(): JsonNode {
            søknad as ObjectNode
            val kopiAvSøknad = søknad.deepCopy()
            kopiAvSøknad.remove(SøkerOppgave.Keys.FØDSELSNUMMER)
            return kopiAvSøknad
        }

        override fun asJson(): String = søknad.toString()
        override fun sannsynliggjøringer(): Set<Sannsynliggjøring> {
            TODO("not implemented")
        }
    }
}

internal fun Session.hentAktivitetslogg(søknadId: UUID): AktivitetsloggDTO? = run(
    queryOf(
        //language=PostgreSQL
        """
        SELECT a.data AS aktivitetslogg
        FROM aktivitetslogg_v1 AS a
        WHERE a.soknad_uuid = :soknadId
        """.trimIndent(),
        mapOf(
            "soknadId" to søknadId
        )
    ).map { row ->
        row.binaryStream("aktivitetslogg").aktivitetslogg()
    }.asSingle
)

private class SøknadPersistenceVisitor(søknad: Søknad) : SøknadVisitor {
    private lateinit var søknadId: UUID
    private val queries = mutableListOf<Query>()

    init {
        søknad.accept(this)
    }

    fun queries() = queries

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.søknadId = søknadId
        queries.add(
            queryOf(
                // language=PostgreSQL
                "INSERT INTO person_v1 (ident) VALUES (:ident) ON CONFLICT DO NOTHING",
                mapOf("ident" to ident)
            )
        )
        queries.add(
            queryOf(
                // language=PostgreSQL
                """
                INSERT INTO soknad_v1(uuid, person_ident, tilstand, spraak)
                VALUES (:uuid, :person_ident, :tilstand, :spraak)
                ON CONFLICT(uuid) DO UPDATE SET tilstand=:tilstand,
                                                sist_endret_av_bruker = :sistEndretAvBruker
                """.trimIndent(),
                mapOf(
                    "uuid" to søknadId,
                    "person_ident" to ident,
                    "tilstand" to tilstand.tilstandType.name,
                    "spraak" to språk.verdi.toLanguageTag(),
                    "sistEndretAvBruker" to sistEndretAvBruker
                )
            )
        )
        // todo: fiks journalpost og innsending
        // journalpost?.let { jp ->
        //     jp.varianter.map { variant ->
        //         queryOf(
        //             //language=PostgreSQL
        //             """
        //             INSERT INTO dokument_v1(soknad_uuid, dokument_lokasjon)
        //                 VALUES(:uuid, :urn) ON CONFLICT (dokument_lokasjon) DO NOTHING
        //             """.trimIndent(),
        //             mapOf(
        //                 "uuid" to søknadId.toString(),
        //                 "urn" to variant.urn
        //             )
        //         )
        //     }.also {
        //         queries.addAll(it)
        //     }
        // }
    }

    override fun visitKrav(krav: Krav) {
        queries.add(
            queryOf(
                // language=PostgreSQL
                """
                INSERT INTO dokumentkrav_v1(faktum_id, beskrivende_id, soknad_uuid, faktum, sannsynliggjoer, tilstand, valg,
                                            begrunnelse)
                VALUES (:faktum_id, :beskrivende_id, :soknad_uuid, :faktum, :sannsynliggjoer, :tilstand, :valg, :begrunnelse)
                ON CONFLICT (faktum_id, soknad_uuid) DO UPDATE SET beskrivende_id  = :beskrivende_id,
                                                                   faktum          = :faktum,
                                                                   sannsynliggjoer = :sannsynliggjoer,
                                                                   tilstand        = :tilstand,
                                                                   valg            = :valg,
                                                                   begrunnelse     = :begrunnelse
                """.trimIndent(),
                mapOf<String, Any?>(
                    "faktum_id" to krav.id,
                    "soknad_uuid" to søknadId,
                    "beskrivende_id" to krav.beskrivendeId,
                    "faktum" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(krav.sannsynliggjøring.faktum())
                    },
                    "sannsynliggjoer" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(krav.sannsynliggjøring.sannsynliggjør())
                    },
                    "tilstand" to krav.tilstand.name,
                    "valg" to krav.svar.valg.name,
                    "begrunnelse" to krav.svar.begrunnelse
                )
            )
        )
        // fjerne alle filer for søknaden. Modellen har fasit på hvor mange som legges til i neste block
        queries.add(
            queryOf(
                // language=PostgreSQL
                "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid",
                mapOf("uuid" to søknadId)
            )
        )

        queries.addAll(
            krav.svar.filer.map { fil ->
                queryOf(
                    // language=PostgreSQL
                    statement = """
                    INSERT INTO dokumentkrav_filer_v1(faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt)
                    VALUES (:faktum_id, :soknad_uuid, :filnavn, :storrelse, :urn, :tidspunkt)
                    ON CONFLICT (faktum_id, soknad_uuid, urn) DO UPDATE SET filnavn = :filnavn, 
                                                                            storrelse = :storrelse, 
                                                                            tidspunkt = :tidspunkt
                    """.trimIndent(),
                    mapOf(
                        "faktum_id" to krav.id,
                        "soknad_uuid" to søknadId,
                        "filnavn" to fil.filnavn,
                        "storrelse" to fil.storrelse,
                        "urn" to fil.urn.toString(),
                        "tidspunkt" to fil.tidspunkt
                    )
                )
            }
        )
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = "INSERT INTO aktivitetslogg_v1 (soknad_uuid, data) VALUES (:uuid, :data) ON CONFLICT (soknad_uuid) DO UPDATE SET data = :data",
                paramMap = mapOf(
                    "uuid" to søknadId,
                    "data" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(aktivitetslogg.toMap())
                    }
                )
            )
        )
    }

    override fun visit(innsendingId: UUID, innsending: Innsending.InnsendingType, tilstand: Innsending.Tilstand.Type, innsendt: ZonedDateTime, journalpost: String?, hovedDokument: Innsending.Dokument?, dokumenter: List<Innsending.Dokument>) {
        queries.add(
                queryOf(
                        //language=PostgreSQL
                        statement = "INSERT INTO innsending_v1(innsending_uuid, innsendt, journalpost_id, innsendingtype, tilstand, brevkode) VALUES (:innsending_uuid, :innsendt, :journalpost_id, :innsendingtype, :tilstand, row(:tittel, :skjemakode)::brevkode)",
                        paramMap = mapOf(
                                "innsending_uuid" to innsendingId,
                                "innsendt" to innsendt,
                                "journalpost_id" to journalpost,
                                "innsendingtype" to innsending.name,
                                "tilstand" to tilstand.name,
                                "tittel" to "tittel",
                                "skjemakode" to "skjemakode"
                        )
                )
        )
    }
}

private fun Session.hentDokumentKrav(søknadsId: UUID): Set<KravDTO> =
    this.run(
        queryOf(
            // language=PostgreSQL
            """
                  SELECT faktum_id, beskrivende_id, faktum, sannsynliggjoer, tilstand, valg, begrunnelse 
                  FROM dokumentkrav_v1 
                  WHERE soknad_uuid = :soknad_uuid
            """.trimIndent(),
            mapOf(
                "soknad_uuid" to søknadsId
            )
        ).map { row ->
            val faktumId = row.string("faktum_id")
            KravDTO(
                id = faktumId,
                beskrivendeId = row.string("beskrivende_id"),
                sannsynliggjøring = SannsynliggjøringDTO(
                    id = faktumId,
                    faktum = objectMapper.readTree(row.binaryStream("faktum")),
                    sannsynliggjør = objectMapper.readTree(row.binaryStream("sannsynliggjoer")).toSet()
                ),
                svar = SvarDTO(
                    begrunnelse = row.stringOrNull("begrunnelse"),
                    filer = hentFiler(søknadsId, faktumId),
                    valg = SvarValgDTO.valueOf(row.string("valg"))
                ),
                tilstand = row.string("tilstand")
                    .let { KravTilstandDTO.valueOf(it) }
            )
        }.asList
    ).toSet()

private fun Session.hentFiler(
    søknadsId: UUID,
    faktumId: String
): Set<KravDTO.FilDTO> {
    return this.run(
        queryOf(
            //language=PostgreSQL
            statement = """
                  SELECT faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt 
                  FROM dokumentkrav_filer_v1 
                  WHERE soknad_uuid = :soknad_uuid
                  AND faktum_id = :faktum_id 
            """.trimIndent(),
            paramMap = mapOf(
                "soknad_uuid" to søknadsId,
                "faktum_id" to faktumId
            )
        ).map { row ->
            KravDTO.FilDTO(
                filnavn = row.string("filnavn"),
                urn = URN.rfc8141().parse(row.string("urn")),
                storrelse = row.long("storrelse"),
                tidspunkt = row.instant("tidspunkt").atZone(ZoneId.of("Europe/Oslo"))
            )
        }.asList
    ).toSet()
}
