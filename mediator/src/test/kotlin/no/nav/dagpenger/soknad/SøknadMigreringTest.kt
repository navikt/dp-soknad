package no.nav.dagpenger.soknad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.livssyklus.asUUID
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadMigreringTest {
    private val søknadMaler by lazy { SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource) }
    private val søknader by lazy { SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource) }
    private fun versjon(versjon: Int) =
        SøknadMal(prosessversjon(versjon), jacksonObjectMapper().createObjectNode())

    private fun prosessversjon(versjon: Int) = Prosessversjon("prosessnavn", versjon)

    private val søknadId = UUID.randomUUID()
    private val ident = "123123123"
    private val rapid = TestRapid()

    @AfterEach
    fun cleanUp() {
        rapid.reset()
    }

    @Test
    fun `Skal lytte på nye maler og starte migrering for påbegynte søknader`() {
        Postgres.withMigratedDb {
            var observertMal = 0
            søknadMaler.addObserver { observertMal++ }
            SøknadMigrering(søknader, søknadMaler, rapid)

            søknadMaler.lagre(versjon(1))
            søknader.lagre(
                Søknad(søknadId, Språk("no"), ident).apply {
                    håndter(
                        SøknadOpprettetHendelse(
                            prosessversjon(1),
                            søknadId,
                            ident
                        )
                    )
                }
            )
            søknadMaler.lagre(versjon(2))

            assertEquals(2, observertMal)
            assertEquals(1, rapid.inspektør.size)

            with(rapid.inspektør.message(0)) {
                assertNotNull(get("søknad_uuid").asUUID())
                assertEquals(ident, get("ident").asText())
            }
        }
    }
}
