package no.nav.dagpenger.soknad.sletterutine

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

internal class VaktmesterRepositoryTest {
    private val språk = Språk("NO")

    @Test
    fun `Sletter gamle søknader etter gitt tidsinterval`() {
        val påbegyntSøknadGammel = UUID.randomUUID()
        val påbegyntSøknadIdNy = UUID.randomUUID()
        val journalførtSøknadId = UUID.randomUUID()
        val testPersonIdent = "12345678910"
        val person = Person(testPersonIdent) {
            mutableListOf(
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadGammel,
                    person = it,
                    tilstandsType = "Påbegynt",
                    dokument = null,
                    journalpostId = "1456",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk
                ),
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadIdNy,
                    person = it,
                    tilstandsType = "Påbegynt",
                    dokument = null,
                    journalpostId = "jouhasjk",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk
                ),
                Søknad.rehydrer(
                    søknadId = journalførtSøknadId,
                    person = it,
                    tilstandsType = "Journalført",
                    dokument = null,
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk
                )
            )
        }

        Postgres.withMigratedDb {
            val nå = LocalDateTime.now()
            val livssyklusRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).also {
                it.lagre(person)
            }

            VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                editOpprettet(påbegyntSøknadGammel, nå.minusDays(30), PostgresDataSourceBuilder.dataSource)
                editOpprettet(journalførtSøknadId, nå.minusDays(30), PostgresDataSourceBuilder.dataSource)
                it.slettPåbegynteSøknaderEldreEnn(nå.minusDays(7L)).also { rader ->
                    assertEquals(1, rader)
                }
                livssyklusRepository.hent(person.ident()).also { oppdatertPerson ->
                    assertEquals(
                        2,
                        TestPersonVisitor(oppdatertPerson).søknader.size
                    )
                }
                livssyklusRepository.hentPåbegyntSøknad(person.ident()).also { påbegynteSøknader ->
                    assertEquals(påbegyntSøknadIdNy, påbegynteSøknader!!.uuid)
                }
            }
        }
    }

    private fun editOpprettet(søknadId: UUID, opprettetDato: LocalDateTime, ds: DataSource): Int {
        return using(sessionOf(ds)) {
            it.run(
                queryOf(
                    "UPDATE soknad_v1 SET opprettet=:opprettet WHERE uuid=:uuid",
                    mapOf("uuid" to søknadId.toString(), "opprettet" to opprettetDato)
                ).asUpdate
            )
        }
    }

    private class TestPersonVisitor(person: Person?) : PersonVisitor {
        init {
            person?.accept(this)
        }

        lateinit var søknader: List<Søknad>
        override fun visitPerson(ident: String, søknader: List<Søknad>) {
            this.søknader = søknader
        }
    }
}
