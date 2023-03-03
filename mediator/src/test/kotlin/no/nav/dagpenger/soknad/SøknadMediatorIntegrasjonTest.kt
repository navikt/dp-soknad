package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.slub.urn.URN
import io.mockk.mockk
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.DokumentkravSvar
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyEttersending
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.PostgresDokumentkravRepository
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.tjenester.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.innsending.tjenester.JournalførtMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyJournalpostMottak
import no.nav.dagpenger.soknad.innsending.tjenester.SkjemakodeMottak
import no.nav.dagpenger.soknad.livssyklus.påbegynt.FaktumSvar
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgaveMottak
import no.nav.dagpenger.soknad.livssyklus.start.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.utils.asZonedDateTime
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.rapids_rivers.toUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

internal class SøknadMediatorIntegrasjonTest {
    private val søknadUuid = UUID.randomUUID()
    private val testIdent = "12345678913"
    private val språkVerdi = "NO"

    private lateinit var søknadMediator: SøknadMediator
    private lateinit var innsendingMediator: InnsendingMediator
    private val testRapid = TestRapid()

    @BeforeEach
    fun setup() {
        val dataSource = Postgres.withMigratedDb()
        søknadMediator = SøknadMediator(
            rapidsConnection = testRapid,
            søknadDataRepository = SøknadDataPostgresRepository(dataSource),
            søknadMalRepository = SøknadMalPostgresRepository(dataSource).also {
                it.lagre(
                    søknadMal = SøknadMal(
                        prosessversjon = Prosessversjon(
                            navn = "Dagpenger",
                            versjon = 123
                        ),
                        mal = jacksonObjectMapper().createObjectNode()
                    )
                )
            },
            ferdigstiltSøknadRepository = mockk(),
            søknadRepository = SøknadPostgresRepository(dataSource),
            dokumentkravRepository = PostgresDokumentkravRepository(dataSource),
            søknadObservers = listOf()
        )

        innsendingMediator = InnsendingMediator(
            rapidsConnection = testRapid,
            innsendingRepository = mockk()
        )

        SøkerOppgaveMottak(testRapid, søknadMediator)
        SøknadOpprettetHendelseMottak(testRapid, søknadMediator)
        ArkiverbarSøknadMottattHendelseMottak(testRapid, innsendingMediator)
        NyJournalpostMottak(testRapid, innsendingMediator)
        JournalførtMottak(testRapid, innsendingMediator)
        SkjemakodeMottak(testRapid, innsendingMediator)
    }

    @Test
    fun `håndtere dokumentkrav sammenstilling`() {
        behandleDokumentkravSammenstilling(kravId = "1", urn = "urn:vedlegg:krav1")

        assertEquals(1, testRapid.inspektør.size)
        val behovJson = testRapid.inspektør.message(0)
        assertEquals("DokumentkravSvar", behovJson["@behov"].single().asText())
        assertEquals("$søknadUuid", behovJson["søknad_uuid"].asText())

        behovJson["DokumentkravSvar"].let { dokumentKravNode ->
            assertEquals("1", dokumentKravNode["id"].asText())
            assertEquals("dokument", dokumentKravNode["type"].asText())
            assertDoesNotThrow {
                dokumentKravNode["lastOppTidsstempel"].asLocalDateTime()
            }
        }
    }

    @Test
    fun `Kan ikke sende inn en søknad med ubesvarte dokumentkrav`() {
        behandleØnskeOmNySøknadHendelse()
        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, gjeldendeTilstand())

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadUuid.toString()))
        assertEquals(Påbegynt, gjeldendeTilstand())

        testRapid.sendTestMessage(søkerOppgave(søknadUuid.toString().toUUID(), testIdent, ferdig = false))

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") {
            behandleSøknadInnsendtHendelse()
        }
    }

    @Test
    fun `Søker oppretter dagpengesøknad med dokumenktrav, ferdigstiller den og ettersender et dokument`() {
        behandleØnskeOmNySøknadHendelse()
        assertEquals(1, testRapid.inspektør.size)
        assertEquals(listOf("NySøknad"), behov(0))
        assertEquals(UnderOpprettelse, gjeldendeTilstand())

        testRapid.sendTestMessage(nySøknadBehovsløsning(søknadUuid.toString()))
        assertEquals(Påbegynt, gjeldendeTilstand())

        testRapid.sendTestMessage(søkerOppgave(søknadUuid.toString().toUUID(), testIdent, ferdig = false))

        søknadMediator.behandle(FaktumSvar(søknadUuid, "1234", "boolean", testIdent, BooleanNode.TRUE))
        val partisjonsnøkkel = testRapid.inspektør.key(1)
        assertEquals(
            testIdent,
            partisjonsnøkkel,
            "Partisjonsnøkkel for faktum_svar skal være ident '$testIdent' var '$partisjonsnøkkel"
        )
        assertTrue("faktum_svar" in testRapid.inspektør.message(1).toString())

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") {
            behandleSøknadInnsendtHendelse()
        }

        behandleLeggTilFil(kravId = "1", urn = "urn:vedlegg:f1.1")
        behandleLeggTilFil(kravId = "1", urn = "urn:vedlegg:f1.2")
        behandleDokumentkravSammenstilling(kravId = "1", urn = "urn:dokumentsammenstilling:f1")

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") {
            behandleSøknadInnsendtHendelse()
        }

        behandleDokumentasjonIkkeTilgjengelig(kravId = "2", begrunnelse = "Har det ikke")

        testRapid.sendTestMessage(søkerOppgave(søknadUuid.toString().toUUID(), testIdent, ferdig = true))

        behandleSøknadInnsendtHendelse()
        assertEquals(Innsendt, gjeldendeTilstand())

        // Ettersending
        behandleLeggTilFil(kravId = "2", urn = "urn:vedlegg:f2.1")
        behandleDokumentkravSammenstilling(kravId = "2", urn = "urn:dokumentsammenstilling:f2")

        assertBehovContains(DokumentkravSvar) {
            assertEquals("2", it["id"].asText())
            assertEquals("dokument", it["type"].asText())
            assertEquals("urn:dokumentsammenstilling:f2", it["urn"].asText())
            assertNotNull(it["lastOppTidsstempel"].asLocalDateTime())
            assertEquals(søknadUuid.toString(), it["søknad_uuid"].asText())
        }

        behandleSøknadInnsendtHendelse()

        assertBehovContains(NyEttersending) {
            assertNotNull(it["innsendtTidspunkt"].asZonedDateTime())
            assertEquals(søknadUuid.toString(), it["søknad_uuid"].asText())
            assertEquals(testIdent, it["ident"].asText())
        }

        assertDokumenter(
            listOf(
                Innsending.Dokument(
                    uuid = UUID.randomUUID(),
                    kravId = "2",
                    skjemakode = "N6",
                    varianter = listOf(
                        Innsending.Dokument.Dokumentvariant(
                            uuid = UUID.randomUUID(),
                            filnavn = "d2",
                            urn = "urn:dokumentsammenstilling:f2",
                            variant = "ARKIV",
                            type = "PDF"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `Hva skjer om en får JournalførtHendelse som ikke er tilknyttet en søknad`() {
        testRapid.sendTestMessage(
            journalførtHendelse(
                ident = testIdent,
                journalpostId = "UKJENT"
            )
        )
    }

    private fun assertBehovContains(behovtype: Aktivitetslogg.Aktivitet.Behov.Behovtype, block: (JsonNode) -> Unit) {
        val behov: JsonNode = sisteBehov()
        assertTrue(
            behov["@behov"].map {
                it.asText()
            }.contains(behovtype.name)
        )
        block(behov)
    }

    private fun sisteBehov() = testRapid.inspektør.message(testRapid.inspektør.size - 1)

    private fun assertDokumenter(expected: List<Innsending.Dokument>) {
        val behov = sisteBehov()
        val jacksonObjectMapper = jacksonObjectMapper()

        val dokumenter = behov["dokumentkrav"].let {
            jacksonObjectMapper.convertValue<List<Innsending.Dokument>>(it)
        }

        expected.forEachIndexed { index, dokument ->
            val actual = dokumenter[index]
            assertEquals(dokument.skjemakode, actual.skjemakode)
            assertEquals(dokument.kravId, actual.kravId)
            assertVarianter(dokument.varianter, actual.varianter)
        }
    }

    private fun assertVarianter(
        expectedVarianter: List<Innsending.Dokument.Dokumentvariant>,
        actualVarianter: List<Innsending.Dokument.Dokumentvariant>,
    ) {
        expectedVarianter.forEachIndexed { index, expected ->
            val actual = actualVarianter[index]
            assertEquals(expected.filnavn, actual.filnavn)
            assertEquals(expected.variant, actual.variant)
            assertEquals(expected.type, actual.type)
            assertEquals(expected.urn, actual.urn)
        }
    }

    private fun behandleDokumentkravSammenstilling(kravId: String, urn: String) {
        søknadMediator.behandle(
            DokumentKravSammenstilling(
                søknadID = søknadUuid,
                ident = testIdent,
                kravId = kravId,
                urn = URN.rfc8141().parse(urn)
            )
        )
    }

    private fun behandleDokumentasjonIkkeTilgjengelig(kravId: String, begrunnelse: String) {
        søknadMediator.behandle(
            DokumentasjonIkkeTilgjengelig(
                søknadID = søknadUuid,
                ident = testIdent,
                kravId = kravId,
                valg = Krav.Svar.SvarValg.SEND_SENERE,
                begrunnelse = begrunnelse
            )
        )
    }

    private fun gjeldendeTilstand() = oppdatertInspektør().gjeldendetilstand

    private fun behandleØnskeOmNySøknadHendelse() {
        søknadMediator.behandle(
            ØnskeOmNySøknadHendelse(
                søknadUuid,
                testIdent,
                språkVerdi,
                prosessnavn = Prosessnavn("Dagpenger")
            )
        )
    }

    private fun behandleSøknadInnsendtHendelse() {
        søknadMediator.behandle(
            SøknadInnsendtHendelse(
                søknadID = søknadUuid,
                ident = testIdent
            )
        )
    }

    private fun behandleLeggTilFil(kravId: String, urn: String) {
        søknadMediator.behandle(
            LeggTilFil(
                søknadID = søknadUuid,
                ident = testIdent,
                kravId = kravId,
                fil = Krav.Fil(
                    filnavn = "test.jpg",
                    urn = URN.rfc8141().parse(urn),
                    storrelse = 0,
                    tidspunkt = ZonedDateTime.now(),
                    bundlet = false
                )
            )
        )
    }

    private fun behov(indeks: Int) = testRapid.inspektør.message(indeks)["@behov"].map { it.asText() }

    private fun oppdatertInspektør(ident: String = testIdent) =
        TestSøknadhåndtererInspektør(søknadMediator.hentSøknader(ident).first())

    // language=JSON
    private fun søkerOppgave(søknadUuid: UUID, ident: String, ferdig: Boolean) = """{
  "@event_name": "søker_oppgave",
  "fødselsnummer": $ident,
  "versjon_id": 0,
  "versjon_navn": "test",
  "@opprettet": "2022-05-13T14:48:09.059643",
  "@id": "76be48d5-bb43-45cf-8d08-98206d0b9bd1",
  "søknad_uuid": "$søknadUuid",
  "ferdig": $ferdig,
  "seksjoner": [
    {
      "beskrivendeId": "seksjon1",
      "fakta": [
        {
          "id": "1",
          "type": "int",
          "beskrivendeId": "f1",
          "sannsynliggjoresAv": [
            {
              "id": "1",
              "type": "dokument",
              "beskrivendeId": "d1.1",
              "roller": [
                "saksbehandler"
              ],
              "sannsynliggjoresAv": []
            }
          ],
          "svar": 11
          
        },
        {
          "id": "2",
          "type": "generator",
          "beskrivendeId": "f2",
          "svar": [
            [
              {
                "id": "2",
                "type": "int",
                "beskrivendeId": "f2",
                "svar": 11,
                "roller": [
                  "søker"
                ],
                "sannsynliggjoresAv": [
                  {
                    "id": "2",
                    "type": "dokument",
                    "beskrivendeId": "d2",
                    "roller": [
                      "saksbehandler"                    ],
                    "sannsynliggjoresAv": []
                  }
                ]
              }
            ]
          ]
        }
      ]
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
            "prosessnavn": "Dagpenger",
            "versjon": 123
          }
        }
      }
    }
    """.trimMargin()

    // language=JSON
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
