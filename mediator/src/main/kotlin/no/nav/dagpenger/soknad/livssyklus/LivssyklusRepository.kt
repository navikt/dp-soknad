package no.nav.dagpenger.soknad.livssyklus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.SøknadhåndtererVisitor
import no.nav.dagpenger.soknad.db.SøknadPersistenceVisitor
import no.nav.dagpenger.soknad.db.hentAktivitetslogg
import no.nav.dagpenger.soknad.db.hentDokumentData
import no.nav.dagpenger.soknad.db.hentDokumentKrav
import no.nav.dagpenger.soknad.db.insertDokumentQuery
import no.nav.dagpenger.soknad.db.insertDokumentkrav
import no.nav.dagpenger.soknad.db.insertQuery
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.PersonDTO
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.SpråkDTO
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

private val logger = KotlinLogging.logger { }

interface LivssyklusRepository {
    fun hent(ident: String, komplettAktivitetslogg: Boolean = false): Søknadhåndterer?
    fun lagre(søknadhåndterer: Søknadhåndterer, ident: String)
    fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad?
}

interface SøknadRepository {
    fun hent(søknadId: UUID, ident: String, komplettAktivitetslogg: Boolean = false): Søknad
    fun hentSøknader(ident: String, komplettAktivitetslogg: Boolean = false): Set<Søknad>
    fun lagre(søknad: Søknad)
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
                            søknader = session.hentSøknadsData(ident, komplettAktivitetslogg)
                        )
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

                logger.info { "Lagrer ${visitor.søknader().size} søknader" }
                visitor.søknader().map { it.first } .insertQuery(ident, transactionalSession)
                visitor.søknader().forEach {
                    it.first.insertDokumentQuery(transactionalSession)
                    lagreAktivitetslogg(transactionalSession, it.first.søknadsId, it.second)
                }
                visitor.søknader().map { it.first }.insertDokumentkrav(transactionalSession)
            }
        }
    }

    private fun lagreAktivitetslogg(
        transactionalSession: TransactionalSession,
        søknadId: UUID,
        aktivitetslogg: Aktivitetslogg
    ) {
        transactionalSession.run(
            queryOf(
                //language=PostgreSQL
                statement = "INSERT INTO aktivitetslogg_v3 (soknad_uuid, data) VALUES (:uuid, :data)",
                paramMap = mapOf(
                    "uuid" to søknadId.toString(),
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

    private fun Session.hentSøknadsData(ident: String, komplettAktivitetslogg: Boolean): List<PersonDTO.SøknadDTO> {
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
                    ident = ident,
                    tilstandType = PersonDTO.SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
                    dokumenter = hentDokumentData(søknadsId),
                    journalpostId = row.stringOrNull("journalpost_id"),
                    innsendtTidspunkt = row.zonedDateTimeOrNull("innsendt_tidspunkt"),
                    språkDTO = SpråkDTO(row.string("spraak")),
                    dokumentkrav = PersonDTO.SøknadDTO.DokumentkravDTO(
                        this.hentDokumentKrav(søknadsId)
                    ),
                    sistEndretAvBruker = row.zonedDateTimeOrNull("sist_endret_av_bruker")
                ).also {
                    if (!komplettAktivitetslogg) return@also
                    it.aktivitetslogg = this.hentAktivitetslogg(søknadsId)
                }
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

private class SøknadhåndtererPersistenceVisitor(søknadhåndterer: Søknadhåndterer) : SøknadhåndtererVisitor {

    fun søknader() = søknader

    private val søknader: MutableList<Pair<PersonDTO.SøknadDTO, Aktivitetslogg>> = mutableListOf()

    init {
        søknadhåndterer.accept(this)
    }

    override fun visitSøknader(søknader: List<Søknad>) {
        this.søknader.addAll(
            søknader.map { søknader -> SøknadPersistenceVisitor(søknader).let { Pair(it.søknadDTO, it.aktivitetslogg) } }
        )
    }
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate, val språk: String)
