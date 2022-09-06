package no.nav.dagpenger.soknad.db

import com.zaxxer.hikari.HikariDataSource
import de.slub.urn.URN
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.serder.PersonDTO
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.Companion.toKravdata
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class SøknadPostgresRepository(private val dataSource: HikariDataSource) :
    SøknadRepository {
    override fun hent(søknadId: UUID, ident: String, komplettAktivitetslogg: Boolean): Søknad {
        sjekkTilgang(ident, søknadId)
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT uuid, tilstand, journalpost_id, innsendt_tidspunkt, spraak, sist_endret_av_bruker
                    FROM  soknad_v1
                    WHERE uuid = :uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId.toString()
                    )
                ).map { row ->
                    val søknadsId = UUID.fromString(row.string("uuid"))
                    PersonDTO.SøknadDTO(
                        søknadsId = søknadsId,
                        ident = ident,
                        tilstandType = PersonDTO.SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
                        dokumenter = session.hentDokumentData(søknadsId),
                        journalpostId = row.stringOrNull("journalpost_id"),
                        innsendtTidspunkt = row.zonedDateTimeOrNull("innsendt_tidspunkt"),
                        språkDTO = PersonDTO.SøknadDTO.SpråkDTO(row.string("spraak")),
                        dokumentkrav = PersonDTO.SøknadDTO.DokumentkravDTO(
                            session.hentDokumentKrav(søknadsId)
                        ),
                        sistEndretAvBruker = row.zonedDateTimeOrNull("sist_endret_av_bruker")
                    )
                }.asSingle
            )?.rehydrer()!!
        }
    }
    override fun lagre(søknad: Søknad) {
        val visitor = SøknadPersistenceVisitor(søknad)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                val personId =
                    hentInternPersonId(transactionalSession, visitor.søknadDTO.ident) ?: lagrePerson(transactionalSession, visitor.søknadDTO.ident)

                // @todo: Lagre aktivitetslogg på søknad
                lagreAktivitetslogg(transactionalSession, personId, visitor.aktivitetslogg)

                // @todo: soknad_v1 må bruke intern person id fra person_v1 for normalisering! Ikke ident direkte
                listOf(visitor.søknadDTO).insertQuery(visitor.søknadDTO.ident, transactionalSession)
                listOf(visitor.søknadDTO).forEach {
                    it.insertDokumentQuery(transactionalSession)
                }
                listOf(visitor.søknadDTO).insertDokumentkrav(transactionalSession)
            }
        }
    }

    override fun hentDokumentkravFor(søknadId: UUID, ident: String): Dokumentkrav {
        sjekkTilgang(ident, søknadId)
        return using(sessionOf(dataSource)) { session ->
            Dokumentkrav.rehydrer(session.hentDokumentKrav(søknadId).map { it.rehydrer() }.toSet())
        }
    }

    private fun harTilgang(ident: String, søknadId: UUID): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                // language=PostgreSQL
                queryOf(
                    """
                       SELECT 1 from soknad_v1 WHERE person_ident = :ident AND uuid = :uuid
                    """.trimIndent(),
                    mapOf(
                        "uuid" to søknadId.toString(),
                        "ident" to ident
                    )
                ).map {
                    it.int(1) == 1
                }.asSingle
            ) ?: false
        }

    override fun oppdaterDokumentkrav(søknadId: UUID, ident: String, dokumentkrav: Dokumentkrav) {
        sjekkTilgang(ident, søknadId)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                dokumentkrav.toDokumentKravData().kravData.insertKravData(søknadId, transactionalSession)
            }
        }
    }

    override fun slettDokumentasjonkravFil(søknadId: UUID, ident: String, kravId: String, urn: URN) {
        sjekkTilgang(ident, søknadId)
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    statement = """
                       DELETE FROM dokumentkrav_filer_v1 
                       WHERE soknad_uuid = :soknadId
                       AND faktum_id = :kravId 
                       AND urn = :urn 
                    """.trimIndent(),
                    paramMap = mapOf(
                        "soknadId" to søknadId.toString(),
                        "kravId" to kravId,
                        "urn" to urn.toString()
                    )
                ).asUpdate
            )
        }
    }

    private fun lagreAktivitetslogg(
        transactionalSession: TransactionalSession,
        internId: Long,
        aktivitetslogg: Aktivitetslogg
    ) {
        transactionalSession.run(
            queryOf(
                //language=PostgreSQL
                statement = "INSERT INTO aktivitetslogg_v2 (person_id, data) VALUES (:person_id, :data)",
                paramMap = mapOf(
                    "person_id" to internId,
                    "data" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(aktivitetslogg.toMap())
                    }
                )
            ).asUpdate
        )
    }
    private fun lagrePerson(transactionalSession: TransactionalSession, ident: String) =
        transactionalSession.run(
            queryOf(
                //language=PostgreSQL
                "INSERT INTO person_v1(ident) VALUES(:ident) ON CONFLICT DO NOTHING RETURNING id",
                paramMap = mapOf("ident" to ident)
            ).map { row ->
                row.long("id")
            }.asSingle
        ) ?: throw RuntimeException("Kunne ikke lagre ny person i DB")

    private fun hentInternPersonId(transactionalSession: TransactionalSession, ident: String) =
        transactionalSession.run(
            queryOf(
                //language=PostgreSQL
                "SELECT id FROM person_v1 WHERE ident=:ident",
                mapOf("ident" to ident)
            ).map { row -> row.longOrNull("id") }.asSingle
        )

    private fun sjekkTilgang(ident: String, søknadId: UUID) =
        if (!harTilgang(ident, søknadId)) throw IkkeTilgangExeption("Har ikke tilgang til søknadId $søknadId") else Unit
}

internal class SøknadPersistenceVisitor(søknad: Søknad) : SøknadVisitor {

    lateinit var søknadDTO: PersonDTO.SøknadDTO
    lateinit var aktivitetslogg: Aktivitetslogg

    init {
        søknad.accept(this)
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        søknadObserver: SøknadObserver,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        søknadDTO = PersonDTO.SøknadDTO(
            søknadsId = søknadId,
            ident = ident,
            tilstandType = when (tilstand.tilstandType) {
                Søknad.Tilstand.Type.UnderOpprettelse -> PersonDTO.SøknadDTO.TilstandDTO.UnderOpprettelse
                Søknad.Tilstand.Type.Påbegynt -> PersonDTO.SøknadDTO.TilstandDTO.Påbegynt
                Søknad.Tilstand.Type.AvventerArkiverbarSøknad -> PersonDTO.SøknadDTO.TilstandDTO.AvventerArkiverbarSøknad
                Søknad.Tilstand.Type.AvventerMidlertidligJournalføring -> PersonDTO.SøknadDTO.TilstandDTO.AvventerMidlertidligJournalføring
                Søknad.Tilstand.Type.AvventerJournalføring -> PersonDTO.SøknadDTO.TilstandDTO.AvventerJournalføring
                Søknad.Tilstand.Type.Journalført -> PersonDTO.SøknadDTO.TilstandDTO.Journalført
                Søknad.Tilstand.Type.Slettet -> PersonDTO.SøknadDTO.TilstandDTO.Slettet
            },
            dokumenter = dokument.toDokumentData(),
            journalpostId = journalpostId,
            innsendtTidspunkt = innsendtTidspunkt,
            språkDTO = PersonDTO.SøknadDTO.SpråkDTO(språk.verdi),
            dokumentkrav = dokumentkrav.toDokumentKravData(),
            sistEndretAvBruker = sistEndretAvBruker
        )
    }

    private fun Søknad.Dokument?.toDokumentData(): List<PersonDTO.SøknadDTO.DokumentDTO> {
        return this?.let { it.varianter.map { v -> PersonDTO.SøknadDTO.DokumentDTO(urn = v.urn) } }
            ?: emptyList()
    }
}

internal fun List<PersonDTO.SøknadDTO>.insertQuery(personIdent: String, session: Session) =
    session.batchPreparedNamedStatement(
        // language=PostgreSQL
        statement = """
            INSERT INTO soknad_v1(uuid, person_ident, tilstand, journalpost_id, spraak)
            VALUES (:uuid, :person_ident, :tilstand, :journalpostID, :spraak)
            ON CONFLICT(uuid) DO UPDATE SET tilstand=:tilstand,
                                            journalpost_id=:journalpostID,
                                            innsendt_tidspunkt = :innsendtTidspunkt,
                                            sist_endret_av_bruker = :sistEndretAvBruker
        """.trimIndent(),
        params = map {
            mapOf<String, Any?>(
                "uuid" to it.søknadsId,
                "person_ident" to personIdent,
                "tilstand" to it.tilstandType.name,
                "journalpostID" to it.journalpostId,
                "innsendtTidspunkt" to it.innsendtTidspunkt,
                "spraak" to it.språkDTO.verdi,
                "sistEndretAvBruker" to it.sistEndretAvBruker
            )
        }
    )

internal fun PersonDTO.SøknadDTO.insertDokumentQuery(session: TransactionalSession) =
    session.batchPreparedNamedStatement(
        //language=PostgreSQL
        statement = """
             INSERT INTO dokument_v1(soknad_uuid, dokument_lokasjon)
                 VALUES(:uuid, :urn) ON CONFLICT (dokument_lokasjon) DO NOTHING 
        """.trimIndent(),
        params = dokumenter.map {
            mapOf(
                "uuid" to this.søknadsId.toString(),
                "urn" to it.urn
            )
        }
    )

internal fun List<PersonDTO.SøknadDTO>.insertDokumentkrav(transactionalSession: TransactionalSession) =
    this.map {
        it.dokumentkrav.kravData.insertKravData(it.søknadsId, transactionalSession)
    }

internal fun Set<PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO>.insertKravData(
    søknadsId: UUID,
    transactionalSession: TransactionalSession
) {
    transactionalSession.batchPreparedNamedStatement(
        // language=PostgreSQL
        statement = """
            INSERT INTO dokumentkrav_v1(faktum_id, beskrivende_id, soknad_uuid, faktum, sannsynliggjoer, tilstand, valg, begrunnelse)
            VALUES (:faktum_id, :beskrivende_id, :soknad_uuid, :faktum, :sannsynliggjoer, :tilstand, :valg, :begrunnelse)
            ON CONFLICT (faktum_id, soknad_uuid) DO UPDATE SET beskrivende_id = :beskrivende_id,
                                                               faktum = :faktum,
                                                               sannsynliggjoer = :sannsynliggjoer,
                                                               tilstand = :tilstand, 
                                                               valg = :valg,
                                                               begrunnelse = :begrunnelse
                                                               
                                                               
        """.trimIndent(),
        params = map { krav ->
            mapOf<String, Any?>(
                "faktum_id" to krav.id,
                "soknad_uuid" to søknadsId,
                "beskrivende_id" to krav.beskrivendeId,
                "faktum" to PGobject().apply {
                    type = "jsonb"
                    value = objectMapper.writeValueAsString(krav.sannsynliggjøring.faktum)
                },
                "sannsynliggjoer" to PGobject().apply {
                    type = "jsonb"
                    value = objectMapper.writeValueAsString(krav.sannsynliggjøring.sannsynliggjør)
                },
                "tilstand" to krav.tilstand.name,
                "valg" to krav.svar.valg.name,
                "begrunnelse" to krav.svar.begrunnelse
            )
        }
    )
    // fjerne alle filer for søknaden. Modellen har fasit på hvor mange som legges til i neste block
    transactionalSession.run(
        // language=PostgreSQL
        queryOf(
            """
                 DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid
            """.trimIndent(),
            mapOf(
                "uuid" to søknadsId.toString()
            )
        ).asExecute
    )

    transactionalSession.batchPreparedNamedStatement(
        // language=PostgreSQL
        statement = """
            INSERT INTO dokumentkrav_filer_v1(faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt)
            VALUES (:faktum_id, :soknad_uuid, :filnavn, :storrelse, :urn, :tidspunkt)
            ON CONFLICT (faktum_id, soknad_uuid, urn) DO UPDATE SET filnavn = :filnavn, 
                                                                    storrelse = :storrelse, 
                                                                    tidspunkt = :tidspunkt

        """.trimIndent(),
        params = flatMap { krav ->
            krav.svar.filer.map { fil ->
                mapOf<String, Any>(
                    "faktum_id" to krav.id,
                    "soknad_uuid" to søknadsId,
                    "filnavn" to fil.filnavn,
                    "storrelse" to fil.storrelse,
                    "urn" to fil.urn.toString(),
                    "tidspunkt" to fil.tidspunkt,
                )
            }
        }
    )
}

internal fun Dokumentkrav.toDokumentKravData(): PersonDTO.SøknadDTO.DokumentkravDTO {
    return PersonDTO.SøknadDTO.DokumentkravDTO(
        kravData = (this.aktiveDokumentKrav() + this.inAktiveDokumentKrav()).map { krav -> krav.toKravdata() }
            .toSet()
    )
}

internal fun Session.hentDokumentKrav(søknadsId: UUID): Set<PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO> =
    this.run(
        queryOf(
            // language=PostgreSQL
            """
                  SELECT faktum_id, beskrivende_id, faktum, sannsynliggjoer, tilstand, valg, begrunnelse 
                  FROM dokumentkrav_v1 
                  WHERE soknad_uuid = :soknad_uuid
            """.trimIndent(),
            mapOf(
                "soknad_uuid" to søknadsId.toString()
            )
        ).map { row ->
            val faktumId = row.string("faktum_id")
            PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO(
                id = faktumId,
                beskrivendeId = row.string("beskrivende_id"),
                sannsynliggjøring = PersonDTO.SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO(
                    id = faktumId,
                    faktum = objectMapper.readTree(row.binaryStream("faktum")),
                    sannsynliggjør = objectMapper.readTree(row.binaryStream("sannsynliggjoer")).toSet(),

                ),
                svar = PersonDTO.SøknadDTO.DokumentkravDTO.SvarDTO(
                    begrunnelse = row.stringOrNull("begrunnelse"),
                    filer = hentFiler(søknadsId, faktumId),
                    valg = PersonDTO.SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO.valueOf(row.string("valg"))
                ),
                tilstand = row.string("tilstand")
                    .let { PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.KravTilstandDTO.valueOf(it) }
            )
        }.asList
    ).toSet()

internal fun Session.hentDokumentData(søknadId: UUID): List<PersonDTO.SøknadDTO.DokumentDTO> {
    return this.run(
        queryOf(
            //language=PostgreSQL
            statement = """
                SELECT dokument_lokasjon FROM dokument_v1 
                WHERE soknad_uuid = :soknadId
            """.trimIndent(),
            paramMap = mapOf(
                "soknadId" to søknadId.toString()
            )
        ).map { row ->
            PersonDTO.SøknadDTO.DokumentDTO(urn = row.string("dokument_lokasjon"))
        }.asList
    )
}
private fun Session.hentFiler(søknadsId: UUID, faktumId: String): Set<PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.FilDTO> {
    return this.run(
        queryOf(
            statement = """
                  SELECT faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt 
                  FROM dokumentkrav_filer_v1 
                  WHERE soknad_uuid = :soknad_uuid
                  AND faktum_id = :faktum_id 
            """.trimIndent(),
            paramMap = mapOf(
                "soknad_uuid" to søknadsId.toString(),
                "faktum_id" to faktumId
            )
        ).map { row ->
            PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.FilDTO(
                filnavn = row.string("filnavn"),
                urn = URN.rfc8141().parse(row.string("urn")),
                storrelse = row.long("storrelse"),
                tidspunkt = row.instant("tidspunkt").atZone(ZoneId.of("Europe/Oslo"))
            )
        }.asList
    ).toSet()
}
