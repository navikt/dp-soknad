package no.nav.dagpenger.soknad

import FerdigSøknadData
import com.fasterxml.jackson.databind.node.BooleanNode
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerJournalføring
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerMetadata
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerMidlertidligJournalføring
import no.nav.dagpenger.soknad.Innsending.TilstandType.Journalført
import no.nav.dagpenger.soknad.Innsending.TilstandType.Opprettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.innsending.tjenester.JournalførtMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyJournalpostMottak
import no.nav.dagpenger.soknad.innsending.tjenester.SkjemakodeMottak
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.FaktumSvar
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgaveMottak
import no.nav.dagpenger.soknad.livssyklus.start.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mal.SøknadMalRepository
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.rapids_rivers.toUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

internal class SøknadMediatorTest {
    companion object {
        private const val testIdent = "12345678913"
        private const val testJournalpostId = "123455PDS"
        private val språkVerdi = "NO"
        private val søknadMedDokumentasjonsKravId = UUID.randomUUID()
        private fun søknadMedDokumentasjonsKrav(): Søknad {
            val originalFaktumJson = faktumJson("1", "f1")
            val dokumentFaktum =
                Faktum(originalFaktumJson)
            val originalFaktumSomsannsynliggjøresFakta = faktumJson("2", "f2")
            val faktaSomSannsynliggjøres =
                mutableSetOf(
                    Faktum(originalFaktumSomsannsynliggjøresFakta)
                )
            val sannsynliggjøring = Sannsynliggjøring(
                id = dokumentFaktum.id,
                faktum = dokumentFaktum,
                sannsynliggjør = faktaSomSannsynliggjøres
            )
            val krav = Krav(
                sannsynliggjøring
            )
            return Søknad.rehydrer(
                søknadId = søknadMedDokumentasjonsKravId,
                ident = testIdent,
                opprettet = ZonedDateTime.now(),
                språk = Språk("NO"),
                dokumentkrav = Dokumentkrav.rehydrer(
                    krav = setOf(krav)
                ),
                sistEndretAvBruker = ZonedDateTime.now(),
                tilstandsType = Innsendt,
                aktivitetslogg = Aktivitetslogg(),
                innsending = NyInnsending.rehydrer(
                    innsendingId = UUID.randomUUID(),
                    type = Innsending.InnsendingType.NY_DIALOG,
                    innsendt = ZonedDateTime.now(),
                    journalpostId = null,
                    tilstandsType = Opprettet,
                    hovedDokument = null,
                    dokumenter = listOf(),
                    ettersendinger = listOf(),
                    metadata = Innsending.Metadata(
                        skjemakode = "hubba"
                    )
                ),
                prosessversjon = null,
                data = FerdigSøknadData
            )
        }
    }

    private lateinit var mediator: SøknadMediator
    private val testRapid = TestRapid()

    private object TestSøknadRepository : SøknadRepository {
        private val søknader = mutableListOf<Søknad>()

        override fun hentEier(søknadId: UUID): String? {
            TODO("Not yet implemented")
        }

        override fun hent(søknadId: UUID): Søknad? {
            return søknader.find { it.søknadUUID() == søknadId }
        }

        override fun hentSøknader(ident: String): Set<Søknad> {
            return søknader.toSet()
        }

        override fun lagre(søknad: Søknad) {
            søknader.add(søknad)
        }

        override fun hentPåbegynteSøknader(prosessversjon: Prosessversjon): List<Søknad> {
            TODO("Not yet implemented")
        }

        override fun opprett(søknadID: UUID, språk: Språk, ident: String) =
            Søknad(søknadID, språk, ident, data = FerdigSøknadData)

        fun clear() {
            søknader.clear()
        }
    }

    @BeforeEach
    fun setup() {
        mediator = SøknadMediator(
            rapidsConnection = testRapid,
            søknadDataRepository = mockk(relaxed = true),
            søknadMalRepository = mockk<SøknadMalRepository>().also {
                every { it.prosessversjon(any(), any()) } returns Prosessversjon("test", 1)
            },
            ferdigstiltSøknadRepository = mockk(),
            søknadRepository = TestSøknadRepository
        )

        SøkerOppgaveMottak(testRapid, mediator)
        SøknadOpprettetHendelseMottak(testRapid, mediator)
        ArkiverbarSøknadMottattHendelseMottak(testRapid, mediator)
        NyJournalpostMottak(testRapid, mediator)
        JournalførtMottak(testRapid, mediator)
        SkjemakodeMottak(testRapid, mediator)
    }

    @AfterEach
    fun tearDown() {
        TestSøknadRepository.clear()
    }

    @Test
    fun `Søknaden går gjennom livssyklusen med alle tilstander`() {
        val søknadUuid = UUID.randomUUID()
        mediator.behandle(
            ØnskeOmNySøknadHendelse(
                søknadUuid,
                testIdent,
                språkVerdi,
                prosessnavn = Prosessnavn("prosessnavn")
            )
        )
        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, oppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadUuid.toString()))
        assertEquals(Påbegynt, oppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(søkerOppgave(søknadUuid.toString().toUUID(), testIdent))

        mediator.behandle(FaktumSvar(søknadUuid, "1234", "boolean", testIdent, BooleanNode.TRUE))
        val partisjonsnøkkel = testRapid.inspektør.key(1)
        assertEquals(
            testIdent,
            partisjonsnøkkel,
            "Partisjonsnøkkel for faktum_svar skal være ident '$testIdent' var '$partisjonsnøkkel"
        )
        assertTrue("faktum_svar" in testRapid.inspektør.message(1).toString())

        testRapid.sendTestMessage(ferdigSøkerOppgave(søknadUuid.toString().toUUID(), testIdent))
        mediator.behandle(SøknadInnsendtHendelse(søknadUuid, testIdent))

        assertEquals(AvventerMetadata, oppdatertInspektør().gjeldendeInnsendingTilstand)
        assertEquals(Innsendt, oppdatertInspektør().gjeldendetilstand)

        assertEquals(listOf("InnsendingMetadata"), behov(2))

        testRapid.sendTestMessage(
            innsendingBrevkodeLøsning(
                testIdent,
                søknadId(),
                innsendingId()
            )
        )

        assertEquals(AvventerArkiverbarSøknad, oppdatertInspektør().gjeldendeInnsendingTilstand)
        assertEquals(Innsendt, oppdatertInspektør().gjeldendetilstand)
        assertEquals(listOf("ArkiverbarSøknad"), behov(3))

        testRapid.sendTestMessage(
            arkiverbarsøknadLøsning(
                testIdent,
                søknadId(),
                innsendingId()
            )
        )
        assertEquals(AvventerMidlertidligJournalføring, oppdatertInspektør().gjeldendeInnsendingTilstand)
        assertEquals(Innsendt, oppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(
            nyJournalpostLøsning(
                ident = testIdent,
                søknadUuid = søknadId(),
                innsendingId(),
                journalpostId = testJournalpostId
            )
        )
        assertEquals(AvventerJournalføring, oppdatertInspektør().gjeldendeInnsendingTilstand)
        assertEquals(Innsendt, oppdatertInspektør().gjeldendetilstand)

        testRapid.sendTestMessage(
            søknadJournalførtHendelse(
                søknadUuid,
                ident = testIdent,
                journalpostId = testJournalpostId
            )
        )
        assertEquals(Journalført, oppdatertInspektør().gjeldendeInnsendingTilstand)
        assertEquals(Innsendt, oppdatertInspektør().gjeldendetilstand)
        // Verifiser at det er mulig å hente en komplett aktivitetslogg
        mediator.hentSøknader(testIdent).first().let {
            with(TestSøknadhåndtererInspektør(it).aktivitetslogg["aktiviteter"]!!) {
                assertEquals("Ønske om søknad registrert", first()["melding"])
                assertEquals("Søknad journalført", last()["melding"])
            }
        }
    }

    private fun innsendingId() = oppdatertInspektør(testIdent).gjeldendeInnsendingId

    private fun innsendingBrevkodeLøsning(
        ident: String,
        søknadUuid: String,
        innsendingId: UUID
    ) = //language=JSON
        """
        {
          "@event_name": "behov",
          "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
          "@behov": [
            "InnsendingMetadata"
          ],
          "ident": "$ident",
          "søknad_uuid": "$søknadUuid",
          "innsendingId": "$innsendingId",
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
            "InnsendingMetadata": {
              "tittel": "Søknad om dagpenger",
              "skjemakode": "04.04-01"
            }
          }
        }
        """.trimIndent()

    @Test
    fun `Hva skjer om en får JournalførtHendelse som ikke er tilknyttet en søknad`() {
        testRapid.sendTestMessage(
            journalførtHendelse(
                ident = testIdent,
                journalpostId = "UKJENT"
            )
        )
    }

    private fun søknadId(ident: String = testIdent) = oppdatertInspektør(ident).gjeldendeSøknadId

    private fun behov(indeks: Int) = testRapid.inspektør.message(indeks)["@behov"].map { it.asText() }

    private fun oppdatertInspektør(ident: String = testIdent) =
        TestSøknadhåndtererInspektør(mediator.hentSøknader(ident).first())

    // language=JSON
    private fun søkerOppgave(søknadUuid: UUID, ident: String) = """{
      "@event_name": "søker_oppgave",
      "fødselsnummer": $ident,
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

    // language=JSON
    private fun ferdigSøkerOppgave(søknadUuid: UUID, ident: String) = """{
      "@event_name": "søker_oppgave",
      "fødselsnummer": $ident,
      "versjon_id": 0,
      "versjon_navn": "test",
      "@opprettet": "2022-05-13T14:48:09.059643",
      "@id": "76be48d5-bb43-45cf-8d08-98206d0b9bd1",
      "søknad_uuid": "$søknadUuid",
      "ferdig": true,
      "seksjoner": [
        {
          "beskrivendeId": "seksjon1",
          "fakta": []
        }
      ]
    }
    """.trimIndent()

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
      "@løsning": {
        "NySøknad": {
          "prosessversjon": {
            "prosessnavn": "prosessnavn",
            "versjon": 123
          }
        }
      }
    }
    """.trimMargin()

    // language=JSON
    private fun arkiverbarsøknadLøsning(ident: String, søknadUuid: String, innsendingId: UUID) = """
{
  "@event_name": "behov",
  "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
  "@behov": [
    "ArkiverbarSøknad"
  ],
  "ident": "$ident",
  "søknad_uuid": "$søknadUuid",
  "innsendingId": "$innsendingId",  
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
    "ArkiverbarSøknad": [
      {
        "metainfo": {
          "innhold": "netto.pdf",
          "filtype": "PDF",          "variant": "NETTO"
        },
        "urn": "urn:vedlegg:soknadId/netto.pdf"
      },
      {
        "metainfo": {
          "innhold": "brutto.pdf",
          "filtype": "PDF",
          "variant": "BRUTTO"
        },
        "urn": "urn:vedlegg:soknadId/brutto.pdf"
      }
    ]
  }
}
    """.trimMargin()

    // language=JSON
    private fun nyJournalpostLøsning(ident: String, søknadUuid: String, innsendingId: UUID, journalpostId: String) =
        """
    {
      "@event_name": "behov",
      "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
      "@behov": [
        "NyJournalpost"
      ],
      "ident": "$ident",
      "søknad_uuid": "$søknadUuid",
      "innsendingId": "$innsendingId",
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
}
        """.trimMargin()

    // language=JSON
    private fun søknadJournalførtHendelse(søknadUuid: UUID, ident: String, journalpostId: String) = """
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
      "system_read_count": 0,
      "søknadsData": {
        "søknad_uuid": "$søknadUuid"
      }
    }
    """.trimMargin()

    private fun journalførtHendelse(ident: String, journalpostId: String) = """
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
      "system_read_count": 0,
    """.trimMargin()
}
