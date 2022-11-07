package no.nav.dagpenger.soknad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerJournalføring
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerMetadata
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerMidlertidligJournalføring
import no.nav.dagpenger.soknad.Innsending.TilstandType.Journalført
import no.nav.dagpenger.soknad.Innsending.TilstandType.Opprettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

private const val testIdent = "12345678912"
private const val testJournalpostId = "J123"

internal class SøknadTest {
    private lateinit var søknad: Søknad
    private lateinit var testSøknadObserver: TestSøknadObserver
    private lateinit var plantUmlObservatør: PlantUmlObservatør
    private val inspektør get() = TestSøknadInspektør(søknad)
    private val språk = "NO"

    private companion object {
        val objectMapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()
    }

    private fun sannsynliggjøring(
        sannsynliggjøringId: String,
        dokumentFaktum: String,
        faktaSomSannsynliggjøres: String
    ): Sannsynliggjøring {
        val dokumentFaktum = Faktum(faktumJson("1", dokumentFaktum))
        val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson("2", faktaSomSannsynliggjøres)))
        return Sannsynliggjøring(
            id = sannsynliggjøringId,
            faktum = dokumentFaktum,
            sannsynliggjør = faktaSomSannsynliggjøres
        )
    }

    @BeforeEach
    internal fun setUp() {
        søknad = Søknad(UUID.randomUUID(), Språk(språk), testIdent)
        testSøknadObserver = TestSøknadObserver().also { søknad.addObserver(it) }
        plantUmlObservatør = PlantUmlObservatør().also { søknad.addObserver(it) }
    }

    @Test
    fun `Kan ikke oppdatere faktum når søknaden er sendt inn`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterSendInnSøknad()
        håndterArkiverbarSøknad()

        assertThrows<AktivitetException> {
            håndterFaktumOppdatering()
        }
    }

    @Test
    fun `Søker oppretter søknad og ferdigstiller den`() {
        håndterØnskeOmNySøknadHendelse()
        with(inspektør.opprettet) {
            assertNotNull(this)
            assertEquals(LocalDate.now(), this.toLocalDate())
        }
        assertBehov(
            Behovtype.NySøknad,
            mapOf(
                "prosessnavn" to "prosessnavn",
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent
            )
        )
        håndterNySøknadOpprettet()
        håndterFaktumOppdatering()
        håndterSøkerOppgaveHendelse(
            setOf(
                sannsynliggjøring("1", "f1-1", "f1-2"),
                sannsynliggjøring("2", "f2-1", "f2-2"),
                sannsynliggjøring("3", "f3-1", "f3-2")
            )
        )
        håndterLeggtilFil("1", "urn:sid:1")
        håndterLeggtilFil("1", "urn:sid:2")
        håndterDokumentasjonIkkeTilgjengelig("2", "Har ikke")

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") { håndterSendInnSøknad() }

        håndterLeggtilFil("3", "urn:sid:3")

        håndterDokumentkravSammenstilling(kravId = "1", urn = "urn:sid:bundle1")
        håndterDokumentkravSammenstilling(kravId = "3", urn = "urn:sid:bundle2")
        val hendelse = håndterSendInnSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertInnsendingTilstand(
            AvventerMetadata
        )

        assertBehov(
            Behovtype.InnsendingMetadata,
            mapOf(
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent,
                "type" to "NY_DIALOG",
                "innsendingId" to inspektør.innsendingId.toString()
            )
        )

        håndterInnsendingMetadata()

        assertInnsendingTilstand(
            AvventerArkiverbarSøknad
        )

        assertBehov(
            Behovtype.ArkiverbarSøknad,
            mapOf(
                "innsendtTidspunkt" to hendelse.innsendtidspunkt().toString(),
                "dokumentasjonKravId" to listOf("1", "3"),
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent,
                "type" to "NY_DIALOG",
                "innsendingId" to inspektør.innsendingId.toString(),
            )
        )
        håndterArkiverbarSøknad()
        assertInnsendingTilstand(
            AvventerMidlertidligJournalføring
        )
        val hoveddokument = mutableMapOf(
            "varianter" to listOf(
                mapOf<String, Any>(
                    "filnavn" to "",
                    "urn" to "urn:dokument:1",
                    "variant" to "ARKIV",
                    "type" to "PDF"
                )
            ),
            "brevkode" to "NAV 04-01.02"
        )

        assertBehov(
            Behovtype.NyJournalpost,
            mapOf(
                "hovedDokument" to hoveddokument,
                "dokumenter" to listOf(
                    mapOf(
                        "varianter" to listOf(
                            mapOf<String, Any>(
                                "filnavn" to "f1-1",
                                "urn" to "urn:sid:bundle1",
                                "variant" to "ARKIV",
                                "type" to "PDF"
                            )
                        ),
                        "brevkode" to "N6"
                    ),
                    mapOf(
                        "varianter" to listOf(
                            mapOf<String, Any>(
                                "filnavn" to "f3-1",
                                "urn" to "urn:sid:bundle2",
                                "variant" to "ARKIV",
                                "type" to "PDF"
                            )
                        ),
                        "brevkode" to "N6"
                    )
                ),
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent,
                "type" to "NY_DIALOG",
                "innsendingId" to inspektør.innsendingId.toString()
            )
        )
        håndterMidlertidigJournalførtSøknad()
        assertInnsendingTilstand(
            AvventerJournalføring
        )
        håndterJournalførtSøknad()
        assertInnsendingTilstand(
            Journalført
        )
        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertInnsendingTilstander(
            Opprettet,
            AvventerMetadata,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført
        )

        assertPuml("Søker oppretter søknad og ferdigstiller den")
        // Ettersending
        håndterLeggtilFil("2", "urn:sid:2")
        håndterDokumentkravSammenstilling(kravId = "2", urn = "urn:sid:bundle3")
        assertBehovContains(
            Behovtype.DokumentkravSvar
        ) { behovParametre ->
            assertEquals("2", behovParametre["id"])
            assertEquals("dokument", behovParametre["type"])
            assertEquals("urn:sid:bundle3", behovParametre["urn"])
            assertNotNull(behovParametre["lastOppTidsstempel"])
            assertEquals(inspektør.søknadId.toString(), behovParametre["søknad_uuid"])
            assertEquals(testIdent, behovParametre["ident"])
        }

        val ettersendingHendelse = håndterSendInnSøknad()

        assertBehov(
            Behovtype.ArkiverbarSøknad,
            mapOf(
                "innsendtTidspunkt" to ettersendingHendelse.innsendtidspunkt().toString(),
                "dokumentasjonKravId" to listOf("2"),
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent,
                "type" to "ETTERSENDING_TIL_DIALOG",
                "innsendingId" to ettersendinger().innsendingId.toString(),
            )
        )

        håndterArkiverbarSøknad(ettersendinger().innsendingId)

        assertBehov(
            Behovtype.NyJournalpost,
            mapOf(
                "hovedDokument" to hoveddokument.also { it["brevkode"] = "NAVe 04-01.02" },
                "dokumenter" to listOf(
                    mapOf(
                        "varianter" to listOf(
                            mapOf<String, Any>(
                                "filnavn" to "f2-1",
                                "urn" to "urn:sid:bundle3",
                                "variant" to "ARKIV",
                                "type" to "PDF"
                            )
                        ),
                        "brevkode" to "N6"
                    )
                ),
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent,
                "type" to InnsendingType.ETTERSENDING_TIL_DIALOG.name,
                "innsendingId" to ettersendinger().innsendingId.toString()
            )
        )

        håndterMidlertidigJournalførtSøknad(ettersendinger().innsendingId)
        assertEttersendingTilstand(AvventerJournalføring)

        håndterJournalførtSøknad()
        assertEttersendingTilstand(Journalført)
    }

    private fun assertInnsendingTilstand(tilstand: Innsending.TilstandType) {
        assertEquals(tilstand, inspektør.innsending.tilstand)
    }

    private fun assertEttersendingTilstand(tilstand: Innsending.TilstandType) {
        assertEquals(tilstand, ettersendinger().tilstand)
    }

    private fun ettersendinger() = inspektør.ettersendinger.last()
    @Test
    fun `Slett søknad for person`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterSlettet()

        assertTrue(testSøknadObserver.slettet)

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Slettet
        )
    }

    private fun håndterNySøknadOpprettet() {
        søknad.håndter(SøknadOpprettetHendelse(Prosessversjon("navn", 1), inspektør.søknadId, testIdent))
    }

    private fun håndterSlettet() {
        søknad.håndter(SlettSøknadHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterInnsendingMetadata() {
        søknad.håndter(
            InnsendingMetadataMottattHendelse(
                inspektør.innsendingId,
                inspektør.søknadId,
                testIdent,
                "04-01.02"
            )
        )
    }

    private fun håndterArkiverbarSøknad(innsendingId: UUID = inspektør.innsendingId) {
        søknad.håndter(
            ArkiverbarSøknadMottattHendelse(
                innsendingId,
                inspektør.søknadId,
                testIdent,
                "urn:dokument:1".lagTestDokument()
            )
        )
    }

    private fun håndterMidlertidigJournalførtSøknad(innsendingId: UUID = inspektør.innsendingId) {
        søknad.håndter(
            SøknadMidlertidigJournalførtHendelse(
                innsendingId,
                inspektør.søknadId,
                testIdent,
                testJournalpostId
            )
        )
    }

    private fun håndterJournalførtSøknad() {
        søknad.håndter(
            JournalførtHendelse(
                inspektør.søknadId,
                testJournalpostId,
                testIdent
            )
        )
    }

    private fun håndterFaktumOppdatering() {
        søknad.håndter(FaktumOppdatertHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterSøkerOppgaveHendelse(sannsynliggjøringer: Set<Sannsynliggjøring> = emptySet()) {
        søknad.håndter(
            SøkeroppgaveHendelse(
                inspektør.søknadId,
                testIdent,
                sannsynliggjøringer
            )
        )
    }

    private fun håndterSendInnSøknad(): SøknadInnsendtHendelse {
        return SøknadInnsendtHendelse(inspektør.søknadId, testIdent).also {
            søknad.håndter(it)
        }
    }

    private fun håndterØnskeOmNySøknadHendelse() {
        søknad.håndter(
            ØnskeOmNySøknadHendelse(
                søknadID = UUID.randomUUID(),
                ident = testIdent,
                språk = språk,
                prosessnavn = Prosessnavn("prosessnavn")
            )
        )
    }

    private fun håndterLeggtilFil(kravId: String, urn: String) {
        val hendelse = LeggTilFil(
            inspektør.søknadId,
            testIdent,
            kravId,
            fil = Krav.Fil(
                filnavn = "test.jpg",
                urn = URN.rfc8141().parse(urn),
                storrelse = 0,
                tidspunkt = ZonedDateTime.now(),
                bundlet = false
            )
        )
        søknad.håndter(hendelse)
    }

    private fun håndterDokumentasjonIkkeTilgjengelig(kravId: String, begrunnelse: String) {
        val hendelse = DokumentasjonIkkeTilgjengelig(
            inspektør.søknadId,
            testIdent,
            kravId = kravId,
            valg = Krav.Svar.SvarValg.SENDER_IKKE,
            begrunnelse = begrunnelse
        )
        søknad.håndter(hendelse)
    }

    private fun håndterDokumentkravSammenstilling(kravId: String, urn: String) {
        val hendelse = DokumentKravSammenstilling(
            inspektør.søknadId,
            testIdent,
            kravId = kravId,
            urn = URN.rfc8141().parse(urn)
        )

        søknad.håndter(hendelse)
    }

    private fun assertTilstander(vararg tilstander: Søknad.Tilstand.Type) {
        assertEquals(tilstander.asList(), testSøknadObserver.tilstander)
    }

    private fun assertInnsendingTilstander(vararg tilstander: Innsending.TilstandType) {
        assertEquals(tilstander.asList(), testSøknadObserver.innsendTilstander)
    }

    private fun assertPuml(tittel: String) {
        plantUmlObservatør.verify(tittel)
    }

    private fun assertBehov(behovtype: Behovtype, forventetDetaljer: Map<String, Any> = emptyMap()) {
        val behov = inspektør.aktivitetslogg.behov().findLast {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        assertEquals(
            objectMapper.writeValueAsString(forventetDetaljer),
            objectMapper.writeValueAsString(behov.detaljer() + behov.kontekst())
        )
    }

    private fun assertBehovContains(behovtype: Behovtype, block: (Map<String, Any>) -> Unit) {
        val behov = inspektør.aktivitetslogg.behov().findLast {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        block(behov.detaljer() + behov.kontekst())
    }
}

private fun String.lagTestDokument() = listOf(
    Innsending.Dokument.Dokumentvariant(filnavn = "", urn = this, variant = "ARKIV", type = "PDF")
)
