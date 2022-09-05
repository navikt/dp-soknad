package no.nav.dagpenger.soknad.livssyklus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import de.slub.urn.URN
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.SøknadhåndtererVisitor
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.PersonDTO
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.Companion.toKravdata
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.SpråkDTO
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

private val logger = KotlinLogging.logger { }

interface LivssyklusRepository {
    fun hent(ident: String, komplettAktivitetslogg: Boolean = false): Søknadhåndterer?
    fun lagre(søknadhåndterer: Søknadhåndterer, ident: String)
    fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad?
}

interface SøknadRepository {
    fun hent(søknadId: UUID, ident: String): Søknad?
    fun lagre(søknad: Søknad): Boolean
    fun hentDokumentkravFor(søknadId: UUID, ident: String): Dokumentkrav
    fun harTilgang(ident: String, søknadId: UUID): Boolean
    fun oppdaterDokumentkrav(søknadId: UUID, ident: String, dokumentkrav: Dokumentkrav)
    fun slettDokumentasjonkravFil(søknadId: UUID, ident: String, kravId: String, urn: URN)
}

class LivssyklusPostgresRepository(private val dataSource: DataSource) : LivssyklusRepository {
    override fun hent(ident: String, komplettAktivitetslogg: Boolean): Søknadhåndterer? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    """
                        SELECT p.id AS person_id, p.ident AS person_ident
                        FROM person_v1 AS p
                        WHERE p.ident = :ident
                    """.trimIndent(),
                    paramMap = mapOf("ident" to ident)
                ).map { row ->
                    row.stringOrNull("person_ident")?.let { ident ->
                        PersonDTO(
                            ident = row.string("person_ident"),
                            søknader = session.hentSøknadsData(ident)
                        ).also {
                            if (!komplettAktivitetslogg) return@also
                            it.aktivitetsLogg = session.hentAktivitetslogg(row.int("person_id"))
                        }
                    }
                }.asSingle
            )?.createSøknadhåndterer()
        }
    }

    override fun lagre(søknadhåndterer: Søknadhåndterer, ident: String) {
        val visitor = SøknadhåndtererPersistenceVisitor(søknadhåndterer)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                val internId =
                    hentInternPersonId(transactionalSession, ident) ?: lagrePerson(transactionalSession, ident)!!

                lagreAktivitetslogg(transactionalSession, internId, visitor.aktivitetslogg)

                logger.info { "Lagrer ${visitor.søknader().size} søknader" }
                visitor.søknader().insertQuery(ident, transactionalSession)
                visitor.søknader().forEach {
                    it.insertDokumentQuery(transactionalSession)
                }
                visitor.søknader().insertDokumentkrav(transactionalSession)
            }
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
        )

    private fun hentInternPersonId(transactionalSession: TransactionalSession, ident: String) =
        transactionalSession.run(
            queryOf(
                //language=PostgreSQL
                "SELECT id FROM person_v1 WHERE ident=:ident",
                mapOf("ident" to ident)
            ).map { row -> row.longOrNull("id") }.asSingle
        )

    override fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "SELECT uuid, opprettet, spraak FROM soknad_v1 WHERE person_ident=:ident AND tilstand=:paabegyntTilstand",
                    mapOf("ident" to personIdent, "paabegyntTilstand" to Søknad.Tilstand.Type.Påbegynt.name)
                ).map { row ->
                    PåbegyntSøknad(
                        UUID.fromString(row.string("uuid")),
                        row.localDate("opprettet"),
                        row.string("spraak")
                    )
                }.asSingle
            )
        }
    }

    private fun Session.hentDokumentData(søknadId: UUID): List<PersonDTO.SøknadDTO.DokumentDTO> {
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

    private fun Session.hentAktivitetslogg(ident: Int): PersonDTO.AktivitetsloggDTO = run(
        queryOf(
            //language=PostgreSQL
            statement = """
                SELECT a.data AS aktivitetslogg
                FROM aktivitetslogg_v2 AS a
                WHERE a.person_id = :ident
                ORDER BY id ASC
            """.trimIndent(),
            paramMap = mapOf(
                "ident" to ident
            )
        ).map { row ->
            row.binaryStream("aktivitetslogg").aktivitetslogg()
        }.asList
    ).fold(PersonDTO.AktivitetsloggDTO(mutableListOf())) { acc, data ->
        PersonDTO.AktivitetsloggDTO(acc.aktiviteter + data.aktiviteter)
    }

    private fun Session.hentSøknadsData(ident: String): List<PersonDTO.SøknadDTO> {
        return this.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                    SELECT uuid, tilstand, journalpost_id, innsendt_tidspunkt, spraak, sist_endret_av_bruker
                    FROM  soknad_v1
                    WHERE person_ident = :ident
                """.trimIndent(),
                paramMap = mapOf(
                    "ident" to ident
                )
            ).map { row ->
                val søknadsId = UUID.fromString(row.string("uuid"))
                PersonDTO.SøknadDTO(
                    søknadsId = søknadsId,
                    tilstandType = PersonDTO.SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
                    dokumenter = hentDokumentData(søknadsId),
                    journalpostId = row.stringOrNull("journalpost_id"),
                    innsendtTidspunkt = row.zonedDateTimeOrNull("innsendt_tidspunkt"),
                    språkDTO = SpråkDTO(row.string("spraak")),
                    dokumentkrav = PersonDTO.SøknadDTO.DokumentkravDTO(
                        this.hentDokumentKrav(søknadsId)
                    ),
                    sistEndretAvBruker = row.zonedDateTimeOrNull("sist_endret_av_bruker")
                )
            }.asList
        )
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

private fun Session.hentFiler(søknadsId: UUID, faktumId: String): Set<KravDTO.FilDTO> {
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
            KravDTO.FilDTO(
                filnavn = row.string("filnavn"),
                urn = URN.rfc8141().parse(row.string("urn")),
                storrelse = row.long("storrelse"),
                tidspunkt = row.instant("tidspunkt").atZone(ZoneId.of("Europe/Oslo"))
            )
        }.asList
    ).toSet()
}

internal fun Session.hentDokumentKrav(søknadsId: UUID): Set<KravDTO> =
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
            KravDTO(
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
                    .let { KravDTO.KravTilstandDTO.valueOf(it) }
            )
        }.asList
    ).toSet()

private fun List<PersonDTO.SøknadDTO>.insertDokumentkrav(transactionalSession: TransactionalSession) =
    this.map {
        it.dokumentkrav.kravData.insertKravData(it.søknadsId, transactionalSession)
    }

internal fun Set<KravDTO>.insertKravData(
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

private fun List<PersonDTO.SøknadDTO>.insertQuery(personIdent: String, session: Session) =
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

private fun PersonDTO.SøknadDTO.insertDokumentQuery(session: TransactionalSession) =
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

private class SøknadhåndtererPersistenceVisitor(søknadhåndterer: Søknadhåndterer) : SøknadhåndtererVisitor {

    fun søknader() = søknader.filterNot(slettet())
    fun slettedeSøknader() = søknader.filter(slettet())
    private fun slettet(): (PersonDTO.SøknadDTO) -> Boolean =
        { it.tilstandType == PersonDTO.SøknadDTO.TilstandDTO.Slettet }

    private val søknader: MutableList<PersonDTO.SøknadDTO> = mutableListOf()
    lateinit var aktivitetslogg: Aktivitetslogg

    init {
        søknadhåndterer.accept(this)
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg
    }

    override fun visitSøknad(
        søknadId: UUID,
        søknadObserver: SøknadObserver,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        søknader.add(
            PersonDTO.SøknadDTO(
                søknadsId = søknadId,
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
                språkDTO = SpråkDTO(språk.verdi),
                dokumentkrav = dokumentkrav.toDokumentKravData(),
                sistEndretAvBruker = sistEndretAvBruker
            )
        )
    }

    private fun Søknad.Dokument?.toDokumentData(): List<PersonDTO.SøknadDTO.DokumentDTO> {
        return this?.let { it.varianter.map { v -> PersonDTO.SøknadDTO.DokumentDTO(urn = v.urn) } }
            ?: emptyList()
    }
}

internal fun Dokumentkrav.toDokumentKravData(): PersonDTO.SøknadDTO.DokumentkravDTO {
    return PersonDTO.SøknadDTO.DokumentkravDTO(
        kravData = (this.aktiveDokumentKrav() + this.inAktiveDokumentKrav()).map { krav -> krav.toKravdata() }
            .toSet()
    )
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate, val språk: String)
