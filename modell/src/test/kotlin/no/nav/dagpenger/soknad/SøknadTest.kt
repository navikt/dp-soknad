package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Søknad.Journalpost.Variant
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val dokumentFaktum =
        Faktum(faktumJson("1", "f1"))
    private val faktaSomSannsynliggjøres =
        mutableSetOf(
            Faktum(faktumJson("2", "f2"))
        )
    private val sannsynliggjøring1 = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val sannsynliggjøring2 = Sannsynliggjøring(
        id = "2",
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val sannsynliggjøring3 = Sannsynliggjøring(
        id = "3",
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )

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
        assertBehov(Behovtype.NySøknad, mapOf("ident" to testIdent, "søknad_uuid" to inspektør.søknadId.toString()))
        håndterNySøknadOpprettet()
        håndterFaktumOppdatering()
        håndterSøkerOppgaveHendelse(setOf(sannsynliggjøring1, sannsynliggjøring2, sannsynliggjøring3))
        håndterLeggtilFil("1", "urn:sid:1")
        håndterLeggtilFil("1", "urn:sid:2")
        håndterDokumentasjonIkkeTilgjengelig("2", "Har ikke")

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") { håndterSendInnSøknad() }

        håndterLeggtilFil("3", "urn:sid:3")

        håndterDokumentkravSammenstilling(kravId = "1", urn = "urn:sid:3")
        håndterDokumentkravSammenstilling(kravId = "3", urn = "urn:sid:3")

        val hendelse = håndterSendInnSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertBehov(
            Behovtype.ArkiverbarSøknad,
            mapOf(
                "innsendtTidspunkt" to hendelse.innsendtidspunkt().toString(),
                "type" to "NY_DIALOG",
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent,
            )
        )
        håndterArkiverbarSøknad()
        val hoveddokument =
            Søknad.Journalpost(
                varianter = listOf(
                    Variant(
                        urn = "urn:dokument:1",
                        format = "ARKIV",
                        type = "PDF"
                    )
                )
            )

        assertBehov(
            Behovtype.NyJournalpost,
            mapOf(
                "hovedDokument" to hoveddokument.varianter,
                "vedlegg" to emptyList<Any>(),
                "type" to "NY_DIALOG",
                "søknad_uuid" to inspektør.søknadId.toString(),
                "ident" to testIdent
            )
        )
        håndterMidlertidigJournalførtSøknad()
        håndterJournalførtSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertPuml("Søker oppretter søknad og ferdigstiller den")
    }

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
        søknad.håndter(SøknadOpprettetHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterSlettet() {
        søknad.håndter(SlettSøknadHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterArkiverbarSøknad() {
        søknad.håndter(
            ArkiverbarSøknadMottattHendelse(
                inspektør.søknadId,
                testIdent,
                "urn:dokument:1".lagTestDokument()
            )
        )
    }

    private fun håndterMidlertidigJournalførtSøknad() {
        søknad.håndter(SøknadMidlertidigJournalførtHendelse(inspektør.søknadId, testIdent, testJournalpostId))
    }

    private fun håndterJournalførtSøknad() {
        søknad.håndter(JournalførtHendelse(inspektør.søknadId, testJournalpostId, testIdent))
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
        søknad.håndter(ØnskeOmNySøknadHendelse(søknadID = UUID.randomUUID(), ident = testIdent, språk = språk))
    }

    private fun håndterLeggtilFil(kravId: String, urn: String) {
        val hendelse = LeggTilFil(
            inspektør.søknadId,
            testIdent,
            kravId,
            fil = Krav.Fil("test.jpg", URN.rfc8141().parse(urn), 0, ZonedDateTime.now())
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

    private fun assertPuml(tittel: String) {
        plantUmlObservatør.verify(tittel)
    }

    private fun assertBehov(behovtype: Behovtype, forventetDetaljer: Map<String, Any> = emptyMap()) {
        val behov = inspektør.aktivitetslogg.behov().find {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        assertEquals(forventetDetaljer, behov.detaljer() + behov.kontekst())
    }
}

private fun String.lagTestDokument(): Søknad.Journalpost = Søknad.Journalpost(
    varianter = listOf(
        Variant(this, "ARKIV", "PDF")
    )
)
