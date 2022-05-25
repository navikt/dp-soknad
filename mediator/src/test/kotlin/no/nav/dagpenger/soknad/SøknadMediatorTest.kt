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
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.mottak.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.mottak.JournalførtMottak
import no.nav.dagpenger.soknad.mottak.NyJournalpostMottak
import no.nav.dagpenger.soknad.mottak.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.søknad.SøkerOppgaveMottak
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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

        SøkerOppgaveMottak(testRapid, mediator)
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
    @Disabled
    fun `Skal håndtere ønske om ny søknad`() {

        val søknadUuid = UUID.randomUUID()
        mediator.behandle(ØnskeOmNySøknadHendelse(testIdent, søknadUuid))
        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, hentOppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadUuid.toString()))
        assertEquals(Påbegynt, hentOppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(faktumSvar(søknadUuid.toString()))
        testRapid.sendTestMessage(søkerOppgave(søknadUuid.toString(), testIdent))
        assertNotNull(livsyklusRepository.hent(søknadUuid))
        testRapid.sendTestMessage(faktumSvar())
        assertNull(livsyklusRepository.hent(søknadUuid))

        mediator.behandle(SøknadInnsendtHendelse(søknadUuid, testIdent))
        val oppdaterInspektør = hentOppdatertInspektør()
        assertEquals(AvventerArkiverbarSøknad, oppdaterInspektør.gjeldendetilstand)
        assertNotNull(oppdaterInspektør.innsendtTidspunkt)
        assertEquals(listOf("ArkiverbarSøknad"), behov(1))

        testRapid.sendTestMessage(
            arkiverbarsøknadLøsning(
                testIdent,
                søknadId(),
                Søknad.Dokument(varianter = emptyList())
            )
        )
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
    private fun faktumSvar(søknadUuid: String = UUID.randomUUID().toString()) = """
    {
      "søknad_uuid": "$søknadUuid",
      "@event_name": "faktum_svar",
      "fakta": [
        {
          "id": "12345",
          "svar": "Hest er best på maten",
          "type": "tekst"
        }
      ],
      "@opprettet": "${LocalDateTime.now()}",
      "@id": "${UUID.randomUUID()}"
}""".trimMargin()

    // language=JSON
    private fun søkerOppgave(søknadUuid: String, fødselsnummer: String) = """
        {
          "fødselsnummer" : $fødselsnummer,
          "@event_name" : "søker_oppgave",
          "versjon_id" : -2313,
          "versjon_navn" : "Dagpenger",
          "@opprettet" : "2022-05-25T15:58:42.935604",
          "@id" : "9020622d-8b60-41d7-80d7-3405b0f2448d",
          "søknad_uuid" : $søknadUuid,
          "ferdig" : false,
          "seksjoner" : [ {
            "beskrivendeId" : "test",
            "fakta" : [ {
              "id" : "1",
              "type" : "boolean",
              "beskrivendeId" : "boolean",
              "svar" : true,
              "roller" : [ "søker" ],
              "gyldigeValg" : [ "boolean.true", "boolean.false" ]
            }, {
              "id" : "2",
              "type" : "int",
              "beskrivendeId" : "heltall",
              "roller" : [ "søker" ]
            } ]
          } ]
        }
    """.trimMargin()

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
    private fun arkiverbarsøknadLøsning(ident: String, søknadUuid: String, dokument: Søknad.Dokument) = """
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
        "ArkiverbarSøknad": "$dokument"
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
