package no.nav.dagpenger.soknad.mal

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SøknadMalRepositoryTest {
    private val malJson =
        objectMapper.createObjectNode().also {
            it.put("test", "test")
        }

    @Test
    fun `søknadmal skal lagres`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                var observertMal: SøknadMal? = null
                repo.addObserver { mal -> observertMal = mal }
                val prosessversjon = Prosessversjon("prosessnavn", 1)
                val expectedMal = SøknadMal(prosessversjon, jacksonObjectMapper().createObjectNode())
                assertEquals(1, repo.lagre(expectedMal))
                assertAntallMalerIBasen(1)

                assertEquals(0, repo.lagre(expectedMal))
                assertAntallMalerIBasen(1)
                assertNotNull(observertMal)
                val versjon2 = expectedMal.copy(prosessversjon = prosessversjon.copy(versjon = 456))
                assertEquals(1, repo.lagre(versjon2))
                assertAntallMalerIBasen(2)
            }
        }
    }

    @Test
    fun `Lagring er idempotent - samme versjon og prosessnavn lagres bare 1 gang`() {
        withMigratedDb {
            val malRepository = SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource)
            val prosessversjon = Prosessversjon("test", 1)
            val søknadMal = SøknadMal(prosessversjon, malJson)
            malRepository.lagre(søknadMal).also { antallOppdaterteRader ->
                assertEquals(1, antallOppdaterteRader)
            }
            malRepository.lagre(søknadMal).also { antallOppdaterteRader ->
                assertEquals(0, antallOppdaterteRader)
            }
            val annenSøknadMalVersjon = søknadMal.copy(prosessversjon = prosessversjon.copy(versjon = 2))
            malRepository.lagre(annenSøknadMalVersjon).also { antallOppdaterteRader ->
                assertEquals(1, antallOppdaterteRader)
            }
        }
    }

    @Test
    fun `Hente ut søknadsmal fra basen`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                val prosessversjon =
                    Prosessversjon(
                        Prosessnavn("Dagpenger"),
                        123,
                    )
                val førsteMal = SøknadMal(prosessversjon, jacksonObjectMapper().createObjectNode())

                assertThrows<IngenMalFunnetException> { repo.hentNyesteMal(prosessversjon.prosessnavn) }

                repo.lagre(førsteMal)
                val hentetFørsteMal = repo.hentNyesteMal(prosessversjon.prosessnavn)
                assertEquals(førsteMal, hentetFørsteMal)
                val andreMal = førsteMal.copy(prosessversjon = prosessversjon.copy(versjon = 789))
                repo.lagre(andreMal)
                val hentetAndreMal = repo.hentNyesteMal(prosessversjon.prosessnavn)
                assertEquals(andreMal, hentetAndreMal)
                assertNotEquals(førsteMal, hentetAndreMal)
            }
        }
    }

    @Test
    fun `henter ut prosessversjon`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                repo.lagre(SøknadMal(Prosessversjon("test", 1), objectMapper.createObjectNode()))
                assertEquals(Prosessnavn("test"), repo.prosessnavn("test"))
                assertEquals(Prosessnavn("test"), repo.prosessversjon("test", 1).prosessnavn)
                assertEquals(1, repo.prosessversjon("test", 1).versjon)
            }
        }
    }

    private fun assertAntallMalerIBasen(antall: Int) {
        using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("SELECT COUNT(*) FROM soknadmal").map {
                    assertEquals(antall, it.int(1))
                }.asSingle,
            )
        }
    }
}
