package no.nav.dagpenger.soknad.livssyklus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotliquery.Session
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.PersonData
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData.SpråkData
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger { }

interface LivssyklusRepository {
    fun hent(ident: String, komplettAktivitetslogg: Boolean = false): Person?
    fun lagre(person: Person)
    fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad?
}

class LivssyklusPostgresRepository(private val dataSource: DataSource) : LivssyklusRepository {
    override fun hent(ident: String, komplettAktivitetslogg: Boolean): Person? {
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
                        PersonData(
                            ident = row.string("person_ident"),
                            søknader = session.hentSøknadsData(ident)
                        ).also {
                            if (!komplettAktivitetslogg) return@also
                            it.aktivitetsLogg = session.hentAktivitetslogg(row.int("person_id"))
                        }
                    }
                }.asSingle
            )?.createPerson()
        }
    }

    override fun lagre(person: Person) {
        val visitor = PersonPersistenceVisitor(person)
        val lagreTid = measureTimeMillis {
            using(sessionOf(dataSource)) { session ->
                session.transaction { transactionalSession ->
                    val internId: Long = transactionalSession.run(
                        queryOf(
                            //language=PostgreSQL
                            "SELECT id FROM person_v1 WHERE ident=:ident",
                            mapOf("ident" to person.ident())
                        ).map { row -> row.longOrNull("id") }.asSingle
                    ) ?: transactionalSession.run(
                        queryOf(
                            //language=PostgreSQL
                            "INSERT INTO person_v1(ident) VALUES(:ident) ON CONFLICT DO NOTHING RETURNING id",
                            paramMap = mapOf("ident" to visitor.ident)
                        ).map { row ->
                            row.long("id")
                        }.asSingle
                    )!!
                    val aktivitetsloggLagring = measureTimeMillis {
                        transactionalSession.run(
                            queryOf(
                                //language=PostgreSQL
                                statement = "INSERT INTO aktivitetslogg_v2 (person_id, data) VALUES (:person_id, :data)",
                                paramMap = mapOf(
                                    "person_id" to internId,
                                    "data" to PGobject().apply {
                                        type = "jsonb"
                                        value = objectMapper.writeValueAsString(visitor.aktivitetslogg.toMap())
                                    }
                                )
                            ).asUpdate
                        )
                    }
                    logger.info { "Brukte $aktivitetsloggLagring ms på å lagre aktivitetslogg" }
                    val slettSøknader = measureTimeMillis {
                        visitor.slettedeSøknader().forEach {
                            transactionalSession.run(it.deleteQuery(visitor.ident))
                        }
                    }
                    logger.info { "Brukte $slettSøknader ms på å slette søknader" }
                    val lagreSøknader = measureTimeMillis {
                        logger.info { "Lagrer ${visitor.søknader().size} søknader" }
                        visitor.søknader().insertQuery(visitor.ident, transactionalSession)
                        visitor.søknader().forEach {
                            it.insertDokumentQuery(transactionalSession)
                        }
                    }
                    logger.info { "Brukte $lagreSøknader ms på å lagre søknader" }
                }
            }
        }
        logger.info { "Brukte $lagreTid ms på å lagre person" }
    }

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

    private fun Session.hentDokumentData(søknadId: UUID): List<PersonData.SøknadData.DokumentData> {
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
                PersonData.SøknadData.DokumentData(urn = row.string("dokument_lokasjon"))
            }.asList
        )
    }

    private fun Session.hentAktivitetslogg(ident: Int): PersonData.AktivitetsloggData = run(
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
    ).fold(PersonData.AktivitetsloggData(mutableListOf())) { acc, data ->
        PersonData.AktivitetsloggData(acc.aktiviteter + data.aktiviteter)
    }

    private fun Session.hentSøknadsData(ident: String): List<PersonData.SøknadData> {
        return this.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                    SELECT uuid, tilstand, journalpost_id, innsendt_tidspunkt, spraak
                    FROM  soknad_v1
                    WHERE person_ident = :ident
                """.trimIndent(),
                paramMap = mapOf(
                    "ident" to ident
                )
            ).map { row ->
                val søknadsId = UUID.fromString(row.string("uuid"))
                PersonData.SøknadData(
                    søknadsId = søknadsId,
                    tilstandType = PersonData.SøknadData.TilstandData.rehydrer(row.string("tilstand")),
                    dokumenter = hentDokumentData(søknadsId),
                    journalpostId = row.stringOrNull("journalpost_id"),
                    innsendtTidspunkt = row.zonedDateTimeOrNull("innsendt_tidspunkt"),
                    språkData = SpråkData(row.string("spraak"))
                )
            }.asList
        )
    }

    internal class PersistentSøkerOppgave(private val søknad: JsonNode) : SøkerOppgave {
        override fun søknadUUID(): UUID = UUID.fromString(søknad[SøkerOppgave.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = søknad[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
        override fun ferdig(): Boolean = søknad[SøkerOppgave.Keys.FERDIG].asBoolean()

        override fun asFrontendformat(): JsonNode {
            søknad as ObjectNode
            val kopiAvSøknad = søknad.deepCopy()
            kopiAvSøknad.remove(SøkerOppgave.Keys.FØDSELSNUMMER)
            return kopiAvSøknad
        }

        override fun asJson(): String = søknad.toString()
    }
}

private fun List<PersonData.SøknadData>.insertQuery(personIdent: String, session: Session) =
    session.batchPreparedNamedStatement(
        // language=PostgreSQL
        statement = """
            INSERT INTO soknad_v1(uuid, person_ident, tilstand, journalpost_id, spraak)
            VALUES (:uuid, :person_ident, :tilstand, :journalpostID, :spraak)
            ON CONFLICT(uuid) DO UPDATE SET tilstand=:tilstand,
                                            journalpost_id=:journalpostID,
                                            innsendt_tidspunkt = :innsendtTidspunkt
        """.trimIndent(),
        params = map {
            mapOf<String, Any?>(
                "uuid" to it.søknadsId,
                "person_ident" to personIdent,
                "tilstand" to it.tilstandType.name,
                "journalpostID" to it.journalpostId,
                "innsendtTidspunkt" to it.innsendtTidspunkt,
                "spraak" to it.språkData.verdi
            )
        }
    )

private fun PersonData.SøknadData.deleteQuery(personIdent: String): UpdateQueryAction {
    logger.info { "Prøver å slette søknad: $søknadsId" }
    return queryOf(
        // language=PostgreSQL
        statement = "DELETE FROM soknad_v1 WHERE uuid=:id AND person_ident = :person_ident",
        paramMap = mapOf(
            "id" to søknadsId.toString(),
            "person_ident" to personIdent
        )
    ).asUpdate
}

private fun PersonData.SøknadData.insertDokumentQuery(session: Session) =
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

private class PersonPersistenceVisitor(person: Person) : PersonVisitor {
    lateinit var ident: String
    fun søknader() = søknader.filterNot(slettet())
    fun slettedeSøknader() = søknader.filter(slettet())
    private fun slettet(): (PersonData.SøknadData) -> Boolean =
        { it.tilstandType == PersonData.SøknadData.TilstandData.Slettet }

    private val søknader: MutableList<PersonData.SøknadData> = mutableListOf()
    lateinit var aktivitetslogg: Aktivitetslogg

    init {
        person.accept(this)
    }

    override fun visitPerson(ident: String) {
        this.ident = ident
    }

    override fun visitPerson(ident: String, søknader: List<Søknad>) {
        this.ident = ident
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg
    }

    override fun visitSøknad(
        søknadId: UUID,
        person: Person,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk
    ) {
        søknader.add(
            PersonData.SøknadData(
                søknadsId = søknadId,
                tilstandType = when (tilstand.tilstandType) {
                    Søknad.Tilstand.Type.UnderOpprettelse -> PersonData.SøknadData.TilstandData.UnderOpprettelse
                    Søknad.Tilstand.Type.Påbegynt -> PersonData.SøknadData.TilstandData.Påbegynt
                    Søknad.Tilstand.Type.AvventerArkiverbarSøknad -> PersonData.SøknadData.TilstandData.AvventerArkiverbarSøknad
                    Søknad.Tilstand.Type.AvventerMidlertidligJournalføring -> PersonData.SøknadData.TilstandData.AvventerMidlertidligJournalføring
                    Søknad.Tilstand.Type.AvventerJournalføring -> PersonData.SøknadData.TilstandData.AvventerJournalføring
                    Søknad.Tilstand.Type.Journalført -> PersonData.SøknadData.TilstandData.Journalført
                    Søknad.Tilstand.Type.Slettet -> PersonData.SøknadData.TilstandData.Slettet
                },
                dokumenter = dokument.toDokumentData(),
                journalpostId = journalpostId,
                innsendtTidspunkt = innsendtTidspunkt,
                språkData = SpråkData(språk.verdi)
            )
        )
    }

    private fun Søknad.Dokument?.toDokumentData(): List<PersonData.SøknadData.DokumentData> {
        return this?.let { it.varianter.map { v -> PersonData.SøknadData.DokumentData(urn = v.urn) } }
            ?: emptyList()
    }
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate, val språk: String)
