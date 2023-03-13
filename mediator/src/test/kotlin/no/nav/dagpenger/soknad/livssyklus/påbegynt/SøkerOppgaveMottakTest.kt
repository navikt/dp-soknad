package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.SøkerOppgaveNotFoundException
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
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
                SøknadDataPostgresRepository(PostgresDataSourceBuilder.dataSource),
                mockk(),
                mockk(),
                SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource),
                mockk(),
            ).also {
                SøkerOppgaveMottak(testRapid, it)
            }
            testRapid.reset()
            val søknadUuid = UUID.randomUUID()
            val ident = "01234567891"
            søknadMediator.behandle(
                ØnskeOmNySøknadHendelse(
                    søknadUuid,
                    ident,
                    språkVerdi,
                    prosessnavn = Prosessnavn("prosessnavn"),
                ),
            )
            testRapid.sendTestMessage(nySøknad(søknadUuid, ident))
            søknadMediator.hentSøkerOppgave(søknadUuid).also {
                assertDoesNotThrow {
                    with(jacksonObjectMapper().readTree(it.toJson())) {
                        val seksjoner = this["seksjoner"]
                        assertEquals(1, seksjoner.size())
                        assertEquals(0, seksjoner[0]["fakta"].size())
                        assertEquals(ident, it.eier())
                        assertEquals(søknadUuid, it.søknadUUID())
                        assertEquals(false, this["ferdig"].asBoolean())
                    }
                }
            }
            assertDoesNotThrow {
                søknadMediator.hentSøkerOppgave(søknadUuid, nyereEnn = 0)
            }
            assertThrows<SøkerOppgaveNotFoundException> {
                søknadMediator.hentSøkerOppgave(søknadUuid, nyereEnn = 5)
            }
        }
    }

    @Test
    fun `søknader som ikke finnes skal ikke behandles`() {
        val uuidSomIkkeFinnes = UUID.randomUUID()
        val søknadRepository = mockk<SøknadRepository>().also {
            every { it.hent(uuidSomIkkeFinnes) } throws SøknadMediator.SøknadIkkeFunnet("ikke funnet")
        }
        SøknadMediator(
            rapidsConnection = testRapid,
            søknadDataRepository = mockk(),
            søknadMalRepository = mockk(),
            ferdigstiltSøknadRepository = mockk(),
            søknadRepository = søknadRepository,
            dokumentkravRepository = mockk(),
        )
            .also {
                SøkerOppgaveMottak(testRapid, it)
            }

        testRapid.sendTestMessage(nySøknad(uuidSomIkkeFinnes, "01234567891"))

        verify(exactly = 1) { søknadRepository.hent(uuidSomIkkeFinnes) }
        verify(exactly = 0) { søknadRepository.lagre(any()) }
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
