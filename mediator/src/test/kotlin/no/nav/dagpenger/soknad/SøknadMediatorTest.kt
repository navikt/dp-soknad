package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerMidlertidligJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.db.PersonRepository
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.mottak.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.mottak.JournalførtMottak
import no.nav.dagpenger.soknad.mottak.NyJournalpostMottak
import no.nav.dagpenger.soknad.mottak.SøknadOpprettetHendelseMottak
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

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
        ArkiverbarSøknadMottattHendelseMottak(testRapid, mediator)
        NyJournalpostMottak(testRapid, mediator)
        JournalførtMottak(testRapid, mediator)
    }

    @Test
    fun `Skal håndtere ønske om ny søknad`() {
        mediator.behandle(ØnskeOmNySøknadHendelse(testIdent))
        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, hentOppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadId()))
        assertEquals(Påbegynt, hentOppdatertInspektør().gjeldendetilstand)

        mediator.behandle(SøknadInnsendtHendelse(UUID.fromString(søknadId()), testIdent))
        assertEquals(AvventerArkiverbarSøknad, hentOppdatertInspektør().gjeldendetilstand)
        assertEquals(listOf("ArkiverbarSøknad"), behov(1))

        testRapid.sendTestMessage(arkiverbarsøknadLøsning(testIdent, søknadId(), "urn:dokument:1"))
        assertEquals(AvventerMidlertidligJournalføring, hentOppdatertInspektør().gjeldendetilstand)

        assertEquals(listOf("NyJournalpost"), behov(2))
        testRapid.sendTestMessage(nyJournalpostLøsning(ident = testIdent, søknadUuid = søknadId(), journalpostId = "123455PDS"))
        assertEquals(AvventerJournalføring, hentOppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(søknadJournalførtHendelse(ident = testIdent, søknadUuid = søknadId()))
        assertEquals(Journalført, hentOppdatertInspektør().gjeldendetilstand)
    }

    private fun søknadId() = hentOppdatertInspektør().gjeldendeSøknadId

    private fun behov(indeks: Int) = testRapid.inspektør.message(indeks)["@behov"].map { it.asText() }

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

    // language=JSON
    private fun arkiverbarsøknadLøsning(ident: String, søknadUuid: String, dokumentLokasjon: DokumentLokasjon) = """
    {
      "@event_name": "behov",
      "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
      "@behov": [
        "ArkiverbarSøknad"
      ],
      "ident": "$ident",
      "søknad_uuid": "$søknadUuid",
      "ArkiverbarSøknad": {},
      "@id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
      "@opprettet": "2022-03-30T12:19:08.418821",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
          "time": "2022-03-30T12:19:08.418821"
        }
      ],
      "@løsning": {
        "ArkiverbarSøknad": {
          "dokumentLokasjon": "$dokumentLokasjon"
        }
      }
}""".trimMargin()

    // language=JSON
    private fun nyJournalpostLøsning(ident: String, søknadUuid: String, journalpostId: String) = """
    {
      "@event_name": "behov",
      "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
      "@behov": [
        "NyJournalpost"
      ],
      "ident": "$ident",
      "søknad_uuid": "$søknadUuid",
      "NyJournalpost": {},
      "@id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
      "@opprettet": "2022-03-30T12:19:08.418821",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
          "time": "2022-03-30T12:19:08.418821"
        }
      ],
      "@løsning": {
        "NyJournalpost": "$journalpostId"
      }
}""".trimMargin()

    // language=JSON
    private fun søknadJournalførtHendelse(ident: String, søknadUuid: String) = """
    {
      "@id": "7d1938c6-f1ae-435d-8d83-c7f200b9cc2b",
      "@opprettet": "2022-04-04T10:39:58.621716",
      "journalpostId": "12455",
      "datoRegistrert": "2022-04-04T10:39:58.586548",
      "skjemaKode": "test",
      "tittel": "Tittel",
      "type": "NySøknad",
      "fødselsnummer": "$ident",
      "aktørId": "1234455",
      "fagsakId": "1234",
      "søknadsData": {
        "søknad_uuid": "$søknadUuid"
      },
      "@event_name": "innsending_ferdigstilt",
      "system_read_count": 0
    }
""".trimMargin()
}
