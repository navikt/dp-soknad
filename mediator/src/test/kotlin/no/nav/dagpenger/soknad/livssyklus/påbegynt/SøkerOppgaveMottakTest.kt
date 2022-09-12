package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.plugins.NotFoundException
import io.mockk.mockk
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class SøkerOppgaveMottakTest {
    private val testRapid = TestRapid()
    private val språkVerdi = "NO"

    @AfterEach
    fun reset() {
        testRapid.reset()
    }

    @Test
    fun `lese svar fra kafka`() {
        Postgres.withMigratedDb {
            val søknadMediator = SøknadMediator(
                testRapid,
                SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource),
                LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource),
                mockk(),
                mockk(),
                SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            ).also {
                SøkerOppgaveMottak(testRapid, it)
            }
            testRapid.reset()
            val søknadUuid = UUID.randomUUID()
            val ident = "01234567891"
            søknadMediator.behandle(ØnskeOmNySøknadHendelse(søknadUuid, ident, språkVerdi))
            testRapid.sendTestMessage(nySøknad(søknadUuid, ident))
            søknadMediator.hent(søknadUuid).also {
                assertDoesNotThrow {
                    val seksjoner = it!!.asFrontendformat()["seksjoner"]
                    assertEquals(1, seksjoner.size())
                    assertEquals(0, seksjoner[0]["fakta"].size())
                    assertEquals(ident, it.eier())
                    assertEquals(søknadUuid, it.søknadUUID())
                    assertEquals(false, it.asFrontendformat()["ferdig"].asBoolean())
                }
            }
            assertDoesNotThrow {
                søknadMediator.hent(søknadUuid, sistLagretEtter = LocalDateTime.now().minusMinutes(2))
            }
            assertThrows<NotFoundException> {
                søknadMediator.hent(søknadUuid, sistLagretEtter = LocalDateTime.now().plusMinutes(2))
            }
        }
    }

    //language=JSON
    private fun nySøknad(søknadUuid: UUID, ident: String) = """{
  "@event_name": "søker_oppgave",
  "fødselsnummer": "$ident",
  "versjon_id": 0,
  "versjon_navn": "test",
  "@opprettet": "${LocalDateTime.now().minusSeconds(3)}",
  "@id": "76be48d5-bb43-45cf-8d08-98206d0b9bd1",
  "søknad_uuid": "$søknadUuid",
  "ferdig": false,
  "seksjoner": [
    {
      "beskrivendeId": "seksjon1",
      "fakta": []
    }
  ]
}
    """.trimIndent()
}
