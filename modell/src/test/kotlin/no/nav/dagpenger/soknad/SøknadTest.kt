package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.helpers.FerdigSøknadData
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
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

internal class SøknadTest {
    private lateinit var søknad: Søknad
    private lateinit var testSøknadObserver: TestSøknadObserver
    private lateinit var plantUmlObservatør: PlantUmlObservatør
    private val inspektør get() = TestSøknadInspektør(søknad)
    private val språk = "NO"

    private fun sannsynliggjøring(
        sannsynliggjøringId: String,
        dokumentFaktum: String,
        faktaSomSannsynliggjøres: String
    ): Sannsynliggjøring {
        return Sannsynliggjøring(
            id = sannsynliggjøringId,
            faktum = Faktum(faktumJson("1", dokumentFaktum)),
            sannsynliggjør = mutableSetOf(Faktum(faktumJson("2", faktaSomSannsynliggjøres)))
        )
    }

    private val søknadId = UUID.randomUUID()

    @BeforeEach
    internal fun setUp() {
        søknad = Søknad(
            søknadId,
            Språk(språk),
            testIdent,
            FerdigSøknadData,

        )
        testSøknadObserver = TestSøknadObserver().also { søknad.addObserver(it) }
        plantUmlObservatør = PlantUmlObservatør().also { søknad.addObserver(it) }
    }

    @Test
    fun `Kan ikke oppdatere faktum når søknaden er sendt inn`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterSendInnSøknad()
        // håndterArkiverbarSøknad()

        assertThrows<AktivitetException> {
            håndterFaktumOppdatering()
        }
    }

    @Test
    fun `Søker oppretter dagpenger søknad, ferdigstiller den og ettersender til søknad`() {
        håndterØnskeOmNySøknadHendelse()
        with(inspektør.opprettet) {
            assertNotNull(this)
            assertEquals(LocalDate.now(), this.toLocalDate())
        }
        assertBehov(
            Behovtype.NySøknad,
            mapOf(
                "prosessnavn" to "Dagpenger",
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
        håndterLeggtilFil("1", "urn:sid:f1.1")
        håndterLeggtilFil("1", "urn:sid:f1.2")
        håndterDokumentasjonIkkeTilgjengelig("2", "Har ikke")

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") { håndterSendInnSøknad() }

        håndterLeggtilFil("3", "urn:sid:f3.1")

        håndterDokumentkravSammenstilling(kravId = "1", urn = "urn:sid:bundle1")
        håndterDokumentkravSammenstilling(kravId = "3", urn = "urn:sid:bundle3")
        håndterSendInnSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )
        // Ettersending
        håndterLeggtilFil("2", "urn:sid:f2.1")
        håndterDokumentkravSammenstilling(kravId = "2", urn = "urn:sid:bundle2")
        assertBehovContains(
            Behovtype.DokumentkravSvar,
        ) { behovParametre ->
            assertEquals("2", behovParametre["id"])
            assertEquals("dokument", behovParametre["type"])
            assertEquals("urn:sid:bundle2", behovParametre["urn"])
            assertNotNull(behovParametre["lastOppTidsstempel"])
            assertEquals(inspektør.søknadId.toString(), behovParametre["søknad_uuid"])
            assertEquals(testIdent, behovParametre["ident"])
        }

        val ettersendingHendelse = håndterSendInnSøknad()
        assertBehov(
            behovtype = Behovtype.NyEttersending,
            forventetDetaljer = mapOf(
                "innsendtTidspunkt" to ettersendingHendelse.innsendtidspunkt(),

                "dokumentkrav" to listOf<Map<String, Any>>(
                    Innsending.Dokument(
                        uuid = UUID.randomUUID(),
                        kravId = "",
                        skjemakode = "N6",
                        varianter = listOf(
                            Innsending.Dokument.Dokumentvariant(
                                uuid = UUID.randomUUID(),
                                filnavn = "f2-1",
                                urn = "urn:sid:bundle2",
                                variant = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ).toMap()
                ),
                "søknad_uuid" to søknadId.toString(),
                "ident" to testIdent,
            )
        )
    }

    @Test
    fun `Søker oppretter dagpenger søknad og ferdigstiller den`() {
        håndterØnskeOmNySøknadHendelse()
        with(inspektør.opprettet) {
            assertNotNull(this)
            assertEquals(LocalDate.now(), this.toLocalDate())
        }
        assertBehov(
            Behovtype.NySøknad,
            mapOf(
                "prosessnavn" to "Dagpenger",
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

        assertBehov(
            behovtype = Behovtype.NyInnsending,
            forventetDetaljer = mapOf(
                "innsendtTidspunkt" to hendelse.innsendtidspunkt(),
                "dokumentkrav" to listOf<Map<String, Any>>(
                    Innsending.Dokument(
                        uuid = UUID.randomUUID(),
                        kravId = "",
                        skjemakode = "N6",
                        varianter = listOf(
                            Innsending.Dokument.Dokumentvariant(
                                uuid = UUID.randomUUID(),
                                filnavn = "f1-1",
                                urn = "urn:sid:bundle1",
                                variant = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ).toMap(),
                    Innsending.Dokument(
                        uuid = UUID.randomUUID(),
                        kravId = "",
                        skjemakode = "N6",
                        varianter = listOf(
                            Innsending.Dokument.Dokumentvariant(
                                uuid = UUID.randomUUID(),
                                filnavn = "f3-1",
                                urn = "urn:sid:bundle2",
                                variant = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ).toMap(),

                ),
                "søknad_uuid" to søknadId.toString(),
                "ident" to testIdent,
            )
        )
        assertPuml("Søker oppretter søknad og ferdigstiller den")
    }

    @Test
    fun `Opprette generell innsending og journalføre`() {
        val prosessnavn = Prosessnavn("Innsending")
        håndterØnskeOmNySøknadHendelse(prosessnavn)
        with(inspektør.opprettet) {
            assertNotNull(this)
            assertEquals(LocalDate.now(), this.toLocalDate())
        }
        håndterNySøknadOpprettet(prosessnavn)
        håndterFaktumOppdatering()
        håndterSøkerOppgaveHendelse(
            setOf(
                sannsynliggjøring("1", "f1-1", "f1-2")
            )
        )

        håndterLeggtilFil("1", "urn:sid:1")
        håndterDokumentkravSammenstilling(kravId = "1", urn = "urn:sid:bundle1")
        val hendelse = håndterSendInnSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertThrows<AktivitetException> { håndterSendInnSøknad() }

        assertBehov(
            behovtype = Behovtype.NyInnsending,
            forventetDetaljer = mapOf(
                "innsendtTidspunkt" to hendelse.innsendtidspunkt(),
                "dokumentkrav" to listOf<Map<String, Any>>(
                    Innsending.Dokument(
                        uuid = UUID.randomUUID(),
                        kravId = "",
                        skjemakode = "N6",
                        varianter = listOf(
                            Innsending.Dokument.Dokumentvariant(
                                uuid = UUID.randomUUID(),
                                filnavn = "f1-1",
                                urn = "urn:sid:bundle1",
                                variant = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ).toMap(),
                ),
                "søknad_uuid" to søknadId.toString(),
                "ident" to testIdent,
            )
        )
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

    @Test
    fun `Oppdaterer prosessversjon`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterMigrertProsessHendelse()

        assertEquals(2, testSøknadObserver.sisteVersjon?.versjon)
    }

    private fun håndterNySøknadOpprettet(prosessnavn: Prosessnavn = Prosessnavn("Dagpenger")) {
        søknad.håndter(SøknadOpprettetHendelse(Prosessversjon(prosessnavn.id, 1), inspektør.søknadId, testIdent))
    }

    private fun håndterSlettet() {
        søknad.håndter(SlettSøknadHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterMigrertProsessHendelse() {
        søknad.håndter(
            MigrertProsessHendelse(
                inspektør.søknadId,
                testIdent,
                Prosessversjon("navn", 2)
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

    private fun håndterØnskeOmNySøknadHendelse(prosessnavn: Prosessnavn = Prosessnavn("Dagpenger")) {
        søknad.håndter(
            ØnskeOmNySøknadHendelse(
                søknadID = UUID.randomUUID(),
                ident = testIdent,
                språk = språk,
                prosessnavn = prosessnavn
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

    private fun assertPuml(tittel: String) {
        plantUmlObservatør.verify(tittel)
    }

    private fun assertBehov(behovtype: Behovtype, forventetDetaljer: Map<String, Any> = emptyMap()) {
        val behov = inspektør.aktivitetslogg.behov().findLast {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        assertEquals(
            forventetDetaljer,
            behov.detaljer() + behov.kontekst()
        )
    }

    private fun assertBehovContains(behovtype: Behovtype, block: (Map<String, Any>) -> Unit) {
        val behov = inspektør.aktivitetslogg.behov().findLast {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        block(behov.detaljer() + behov.kontekst())
    }
}
