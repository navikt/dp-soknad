package no.nav.dagpenger.søknad.livssyklus.påbegynt

import io.mockk.mockk
import no.nav.dagpenger.søknad.Person
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.db.Postgres
import no.nav.dagpenger.søknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.søknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.søknad.utils.db.PostgresDataSourceBuilder
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

class SøkerOppgaveMottakTest {
    private val testRapid = TestRapid()

    @AfterEach
    fun reset() {
        testRapid.reset()
    }

    @Test
    fun `lese svar fra kafka`() {

        Postgres.withMigratedDb {
            val søknadMediator = SøknadMediator(testRapid, SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource), mockk(), mockk(), mockk()).also {
                SøkerOppgaveMottak(testRapid, it)
            }
            testRapid.reset()
            val søknadUuid = UUID.randomUUID()
            val ident = "01234567891"
            lagrePersonMedSøknad(søknadUuid, ident)
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
        }
    }
    //language=JSON
    private fun nySøknad(søknadUuid: UUID, ident: String) = """{
  "@event_name": "søker_oppgave",
  "fødselsnummer": "$ident",
  "versjon_id": 0,
  "versjon_navn": "test",
  "@opprettet": "2022-05-13T14:48:09.059643",
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

    private fun lagrePersonMedSøknad(søknadUuid: UUID, ident: String = "01234567891") {
        val person = Person(ident)
        person.håndter(ØnskeOmNySøknadHendelse(ident, søknadUuid))
        val livssyklusPostgresRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
        livssyklusPostgresRepository.lagre(person)
    }
}
