package no.nav.dagpenger.soknad

import io.mockk.mockk
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerMidlertidligJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.Søknadsprosess.NySøknadsProsess
import no.nav.dagpenger.soknad.Søknadsprosess.PåbegyntSøknadsProsess
import no.nav.dagpenger.soknad.db.FerdigstiltSøknadRepository
import no.nav.dagpenger.soknad.db.LivsyklusPostgresRepository
import no.nav.dagpenger.soknad.db.LivsyklusRepository
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.SøknadMalRepository
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.mottak.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.mottak.JournalførtMottak
import no.nav.dagpenger.soknad.mottak.NyJournalpostMottak
import no.nav.dagpenger.soknad.mottak.SøknadOpprettetHendelseMottak
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadMediatorTest {
    companion object {
        private const val testIdent = "12345678912"
        private const val testJournalpostId = "123455PDS"
    }

    private lateinit var mediator: SøknadMediator
    private val livsyklusRepository: LivsyklusRepository = LivsyklusPostgresRepository(Postgres.withMigratedDb())
    private val søknadMalRepositoryMock = mockk<SøknadMalRepository>()
    private val ferdigstiltSøknadRepository = mockk<FerdigstiltSøknadRepository>()
    private val testRapid = TestRapid()

    @BeforeEach
    fun setup() {
        mediator = SøknadMediator(testRapid, livsyklusRepository, søknadMalRepositoryMock, ferdigstiltSøknadRepository)
        SøknadOpprettetHendelseMottak(testRapid, mediator)
        ArkiverbarSøknadMottattHendelseMottak(testRapid, mediator)
        NyJournalpostMottak(testRapid, mediator)
        JournalførtMottak(testRapid, mediator)
    }

    @Test
    fun `Skal håndtere ønske om ny søknad når det finnes en påbegynt søknad`() {
        val testident2 = "12346578910"

        val expectedSøknadsId = mediator.hentEllerOpprettSøknadsprosess(testident2).also {
            assertTrue(it is NySøknadsProsess)
        }.getSøknadsId()

        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, hentOppdatertInspektør(testident2).gjeldendetilstand)

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadId(testident2), testident2))
        assertEquals(Påbegynt, hentOppdatertInspektør(testident2).gjeldendetilstand)

        mediator.hentEllerOpprettSøknadsprosess(testident2).also {
            assertTrue(it is PåbegyntSøknadsProsess)
            assertEquals(expectedSøknadsId.toString(), it.getSøknadsId().toString())
        }
        assertEquals(1, testRapid.inspektør.size)

        mediator.behandle(SøknadInnsendtHendelse(UUID.fromString(søknadId(testident2)), testident2))
        assertEquals(AvventerArkiverbarSøknad, hentOppdatertInspektør(testident2).gjeldendetilstand)
        assertEquals(listOf("ArkiverbarSøknad"), behov(1))
    }

    @Test
    fun `Skal håndtere ønske om ny søknad`() {
        mediator.behandle(ØnskeOmNySøknadHendelse(testIdent, UUID.randomUUID()))
        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, hentOppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadId()))
        assertEquals(Påbegynt, hentOppdatertInspektør().gjeldendetilstand)

        val søknadID = UUID.fromString(søknadId())
        mediator.behandle(SøknadInnsendtHendelse(søknadID, testIdent))
        assertEquals(AvventerArkiverbarSøknad, hentOppdatertInspektør().gjeldendetilstand)
        assertNotNull(livsyklusRepository.hentInnsendtTidspunkt(søknadID))
        assertEquals(listOf("ArkiverbarSøknad"), behov(1))

        testRapid.sendTestMessage(arkiverbarsøknadLøsning(testIdent, søknadId(), "urn:dokument:1"))
        assertEquals(AvventerMidlertidligJournalføring, hentOppdatertInspektør().gjeldendetilstand)

        assertEquals(listOf("NyJournalpost"), behov(2))

        testRapid.sendTestMessage(
            nyJournalpostLøsning(
                ident = testIdent,
                søknadUuid = søknadId(),
                journalpostId = testJournalpostId
            )
        )
        assertEquals(AvventerJournalføring, hentOppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(søknadJournalførtHendelse(ident = testIdent, journalpostId = testJournalpostId))
        assertEquals(Journalført, hentOppdatertInspektør().gjeldendetilstand)
    }

    @Test
    fun `Hva skjer om en får JournalførtHendelse som ikke er tilknyttet en søknad`() {
        testRapid.sendTestMessage(søknadJournalførtHendelse(ident = testIdent, journalpostId = "UKJENT"))
    }

    private fun søknadId(ident: String = testIdent) = hentOppdatertInspektør(ident).gjeldendeSøknadId

    private fun behov(indeks: Int) = testRapid.inspektør.message(indeks)["@behov"].map { it.asText() }

    fun hentOppdatertInspektør(ident: String = testIdent) =
        TestPersonInspektør(livsyklusRepository.hent(ident)!!)

    // language=JSON
    private fun nySøknadBehovsløsning(søknadUuid: String, ident: String = testIdent) = """
    {
      "@event_name": "behov",
      "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
      "@behov": [
        "NySøknad"
      ],
      "ident": "$ident",
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
      "@løsning": {"NySøknad": "$søknadUuid"}
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
        "ArkiverbarSøknad": "$dokumentLokasjon"
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
    private fun søknadJournalførtHendelse(ident: String, journalpostId: String) = """
    {
      "@id": "7d1938c6-f1ae-435d-8d83-c7f200b9cc2b",
      "@opprettet": "2022-04-04T10:39:58.621716",
      "journalpostId": "$journalpostId",
      "datoRegistrert": "2022-04-04T10:39:58.586548",
      "skjemaKode": "test",
      "tittel": "Tittel",
      "type": "NySøknad",
      "fødselsnummer": "$ident",
      "aktørId": "1234455",
      "fagsakId": "1234",
      "@event_name": "innsending_ferdigstilt",
      "system_read_count": 0
    }
""".trimMargin()
}
