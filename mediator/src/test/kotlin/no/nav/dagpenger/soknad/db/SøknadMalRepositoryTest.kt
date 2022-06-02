package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.søknad.db.IngenMalFunnetException
import no.nav.dagpenger.soknad.søknad.db.SøknadMal
import no.nav.dagpenger.soknad.søknad.db.SøknadMalPostgresRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SøknadMalRepositoryTest {

    private val malJson = objectMapper.createObjectNode().also {
        it.put("test", "test")
    }

    @Test
    fun `søknadmal skal lagres`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                val expectedMal = SøknadMal("prosessnavn", 123, jacksonObjectMapper().createObjectNode())
                assertEquals(1, repo.lagre(expectedMal))
                assertAntallMalerIBasen(1)

                assertEquals(0, repo.lagre(expectedMal))
                assertAntallMalerIBasen(1)

                val versjon2 = expectedMal.copy(prosessversjon = 456)
                assertEquals(1, repo.lagre(versjon2))
                assertAntallMalerIBasen(2)
            }
        }
    }

    @Test
    fun `Lagring er idempotent - samme versjon og prosessnavn lagres bare 1 gang`() {
        withMigratedDb {
            val malRepository = SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadMal = SøknadMal("test", 1, malJson)
            malRepository.lagre(søknadMal).also { antallOppdaterteRader ->
                assertEquals(1, antallOppdaterteRader)
            }
            malRepository.lagre(søknadMal).also { antallOppdaterteRader ->
                assertEquals(0, antallOppdaterteRader)
            }
            val annenSøknadMalVersjon = søknadMal.copy(prosessversjon = 2)
            malRepository.lagre(annenSøknadMalVersjon).also { antallOppdaterteRader ->
                assertEquals(1, antallOppdaterteRader)
            }
        }
    }

    @Test
    fun `Hente ut søknadsmal fra basen`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                val prosessnavn = "Dagpenger"
                val førsteMal = SøknadMal(prosessnavn, 123, jacksonObjectMapper().createObjectNode())

                assertThrows<IngenMalFunnetException> { repo.hentNyesteMal(prosessnavn) }

                repo.lagre(førsteMal)
                val hentetFørsteMal = repo.hentNyesteMal(prosessnavn)
                assertEquals(førsteMal, hentetFørsteMal)

                val andreMal = førsteMal.copy(prosessversjon = 789)
                repo.lagre(andreMal)
                val hentetAndreMal = repo.hentNyesteMal(prosessnavn)
                assertEquals(andreMal, hentetAndreMal)
                assertNotEquals(førsteMal, hentetAndreMal)
            }
        }
    }

    private fun assertAntallMalerIBasen(antall: Int) {
        using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("SELECT COUNT(*) FROM soknadmal").map {
                    assertEquals(antall, it.int(1))
                }.asSingle
            )
        }
    }
}
