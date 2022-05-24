package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SøknadMalPostgresRepositoryTest {

    private val malJson = objectMapper.createObjectNode().also {
        it.put("test", "test")
    }

    @Test
    fun `Lagring er identpotent - samme versjon og prosessnavn lagres bare 1 gang`() {
        Postgres.withMigratedDb {
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
}
