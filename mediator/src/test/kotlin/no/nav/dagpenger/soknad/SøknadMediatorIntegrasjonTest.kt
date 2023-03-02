package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.slub.urn.URN
import io.mockk.mockk
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
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
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.rapids_rivers.toUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class SøknadMediatorIntegrasjonTest {
    companion object {
        private const val testIdent = "12345678913"
        private const val testJournalpostId = "123455PDS"
        private val språkVerdi = "NO"
    }

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
                            navn = "prosessnavn",
                            versjon = 123
                        ),
                        mal = jacksonObjectMapper().createObjectNode()
                    )
                )
            },
            ferdigstiltSøknadRepository = mockk(),
            søknadRepository = SøknadPostgresRepository(dataSource),
            dokumentkravRepository = mockk(relaxed = true), søknadObservers = listOf()
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
        val søknadUuid = UUID.randomUUID()
        søknadMediator.behandle(
            DokumentKravSammenstilling(
                søknadID = søknadUuid, ident = testIdent, kravId = "1", urn = URN.rfc8141().parse("urn:vedlegg:krav1")
            )
        )
        assertEquals(1, testRapid.inspektør.size)
        val behovJson = testRapid.inspektør.message(0)
        assertEquals("DokumentkravSvar", behovJson["@behov"].single().asText())
        assertEquals("$søknadUuid", behovJson["søknad_uuid"].asText())

        behovJson["DokumentkravSvar"].let { dokumentKravNode ->
            assertEquals("1", dokumentKravNode["id"].asText())
            assertEquals("1", dokumentKravNode["id"].asText())
            assertEquals("dokument", dokumentKravNode["type"].asText())
            assertDoesNotThrow {
                dokumentKravNode["lastOppTidsstempel"].asLocalDateTime()
            }
        }
    }

    @Test
    fun `Søknaden går gjennom livssyklusen med alle tilstander`() {
        val søknadUuid = UUID.randomUUID()
        søknadMediator.behandle(
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

        søknadMediator.behandle(FaktumSvar(søknadUuid, "1234", "boolean", testIdent, BooleanNode.TRUE))
        val partisjonsnøkkel = testRapid.inspektør.key(1)
        assertEquals(
            testIdent,
            partisjonsnøkkel,
            "Partisjonsnøkkel for faktum_svar skal være ident '$testIdent' var '$partisjonsnøkkel"
        )
        assertTrue("faktum_svar" in testRapid.inspektør.message(1).toString())

        testRapid.sendTestMessage(ferdigSøkerOppgave(søknadUuid.toString().toUUID(), testIdent))
        søknadMediator.behandle(SøknadInnsendtHendelse(søknadUuid, testIdent))

        assertEquals(Innsendt, oppdatertInspektør().gjeldendetilstand)
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

    private fun behov(indeks: Int) = testRapid.inspektør.message(indeks)["@behov"].map { it.asText() }

    private fun oppdatertInspektør(ident: String = testIdent) =
        TestSøknadhåndtererInspektør(søknadMediator.hentSøknader(ident).first())

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
