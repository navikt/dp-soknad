package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

internal class VaktmesterPostgresRepositoryTest {
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
                    dokumentLokasjon = "urn:hubba:la",
                    journalpostId = "1456"
                ),
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadIdNy,
                    person = it,
                    tilstandsType = "Påbegynt",
                    dokumentLokasjon = "urn:hubba:la",
                    journalpostId = "jouhasjk"
                ),
                Søknad.rehydrer(
                    søknadId = journalførtSøknadId,
                    person = it,
                    tilstandsType = "Journalført",
                    dokumentLokasjon = "urn:hubba:bubba",
                    journalpostId = "journalpostid"
                )
            )
        }

        Postgres.withMigratedDb {
            val nå = LocalDateTime.now()
            val livsyklusRepository = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).also {
                it.lagre(person)
            }

            VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                editoOprettet(påbegyntSøknadGammel, nå.minusDays(30), PostgresDataSourceBuilder.dataSource)
                editoOprettet(journalførtSøknadId, nå.minusDays(30), PostgresDataSourceBuilder.dataSource)
                it.slettPåbegynteSøknaderEldreEnn(nå.minusDays(7L)).also { rader ->
                    Assertions.assertEquals(1, rader)
                }
                livsyklusRepository.hent(person.ident()).also { oppdatertPerson ->
                    Assertions.assertEquals(
                        2,
                        TestPersonVisitor(oppdatertPerson).søknader.size
                    )
                }
                livsyklusRepository.hentPåbegynte(person.ident()).also { påbegynteSøknader ->
                    Assertions.assertEquals(1, påbegynteSøknader.size)
                    Assertions.assertEquals(påbegyntSøknadIdNy, påbegynteSøknader.first().uuid)
                }
            }
        }
    }

    private fun editoOprettet(søknadId: UUID, opprettetDato: LocalDateTime, ds: DataSource): Int {
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
