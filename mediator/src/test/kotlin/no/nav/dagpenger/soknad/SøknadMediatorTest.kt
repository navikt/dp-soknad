package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.db.PersonRepository
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.mottak.SøknadOpprettetHendelseMottak
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SøknadMediatorTest {
    companion object {
        private const val testIdent = "12345678912"
    }

    private lateinit var mediator: SøknadMediator
    private val testRapid = TestRapid()

    private val personRepository = object : PersonRepository {
        private val db = mutableMapOf<String, Person>()
        override fun hent(ident: String): Person? = db[ident]

        override fun lagre(person: Person) {
            db[person.ident()] = person
        }
    }

    @BeforeEach
    fun setup() {
        mediator = SøknadMediator(testRapid, personRepository)
        SøknadOpprettetHendelseMottak(testRapid, mediator)
    }

    @Test
    fun `Skal håndtere ønske om ny søknad`() {
        mediator.behandle(ØnskeOmNySøknadHendelse(testIdent))
        assertEquals(1, testRapid.inspektør.size)
        val nySøknadBehov = testRapid.inspektør.message(0)
        assertEquals(listOf("NySøknad"), nySøknadBehov["@behov"].map { it.asText() })
        assertEquals(UnderOpprettelse, hentOppdatertInspektør().gjeldendetilstand)

        val søknadUuid = nySøknadBehov["søknad_uuid"].asText()
        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadUuid))
        assertEquals(Påbegynt, hentOppdatertInspektør().gjeldendetilstand)
    }

    fun hentOppdatertInspektør() = TestPersonInspektør(personRepository.hent(testIdent)!!)

    // language=JSON
    private fun nySøknadBehovsløsning(søknadUuid: String) = """
{
  "@event_name": "behov",
  "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
  "@behov": [
    "NySøknad"
  ],
  "ident": "12345678912",
  "søknad_uuid": "$søknadUuid",
  "NySøknad": {},
  "@id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
  "@opprettet": "2022-03-30T12:19:08.418821",
  "system_read_count": 0,
  "system_participating_services": [
    {
      "id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
      "time": "2022-03-30T12:19:08.418821"
    }
  ],
  "@løsning": {"NySøknad": {"søknad_uuid": "$søknadUuid"}}
}""".trimMargin()
}
