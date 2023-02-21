package no.nav.dagpenger.soknad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Innsending.Dokument.Dokumentvariant
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.helpers.FerdigSøknadData
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
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
            FerdigSøknadData
        )
        testSøknadObserver = TestSøknadObserver().also { søknad.addObserver(it) }
        plantUmlObservatør = PlantUmlObservatør().also { søknad.addObserver(it) }
    }

    @Test
    fun `Kan ikke oppdatere faktum når søknaden er sendt inn`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterSendInnSøknad()

        assertThrows<AktivitetException> {
            håndterFaktumOppdatering()
        }
    }

    @Test
    fun `håndterer søkeroppgave hendelse`() {
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
                sannsynliggjøring("2", "f2-1", "f2-2")
            )
        )

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") { håndterSendInnSøknad() }
    }

    @Test
    fun `Søker oppretter dagpenger søknad uten dokumentkrav og ferdigstiller den`() {
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
        håndterSendInnSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )
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
                sannsynliggjøring("2", "f2-1", "f2-2")
            )
        )
        håndterLeggtilFil("1", "urn:sid:f1.1")
        håndterLeggtilFil("1", "urn:sid:f1.2")

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") { håndterSendInnSøknad() }

        håndterLeggtilFil("2", "urn:sid:f2.1")

        håndterDokumentkravSammenstilling(kravId = "1", urn = "urn:sid:bundle1")
        håndterDokumentkravSammenstilling(kravId = "2", urn = "urn:sid:bundle2")
        val søknadInnsendtHendelse = håndterSendInnSøknad()
//
//        assertTilstander(
//            UnderOpprettelse,
//            Påbegynt,
//            Innsendt
//        )
//        // Ettersending
//        håndterLeggtilFil("2", "urn:sid:f2.2")
//        håndterDokumentkravSammenstilling(kravId = "2", urn = "urn:sid:bundle2")
//        assertBehovContains(
//            Behovtype.DokumentkravSvar
//        ) { behovParametre ->
//            assertEquals("2", behovParametre["id"])
//            assertEquals("dokument", behovParametre["type"])
//            assertEquals("urn:sid:bundle2", behovParametre["urn"])
//            assertNotNull(behovParametre["lastOppTidsstempel"])
//            assertEquals(inspektør.søknadId.toString(), behovParametre["søknad_uuid"])
//            assertEquals(testIdent, behovParametre["ident"])
//        }
//
//        Thread.sleep(1000)
//        val ettersendingHendelse = håndterSendInnSøknad()
//        assertEquals(søknadInnsendtHendelse.innsendtidspunkt(), testSøknadObserver.innsendt)
//        assertNotEquals(ettersendingHendelse.innsendtidspunkt(), testSøknadObserver.innsendt)
//        assertBehovContains(
//            behovtype = Behovtype.NyEttersending
//        ) { behovParametre ->
//            assertEquals(ettersendingHendelse.innsendtidspunkt(), behovParametre["innsendtTidspunkt"])
//            assertEquals(søknadId.toString(), behovParametre["søknad_uuid"])
//            assertEquals(testIdent, behovParametre["ident"])
//        }
//        assertDokumenter(
//            behovtype = Behovtype.NyEttersending,
//            expected = listOf(
//                Innsending.Dokument(
//                    uuid = UUID.randomUUID(),
//                    kravId = "2",
//                    skjemakode = "N6",
//                    varianter = listOf(
//                        Innsending.Dokument.Dokumentvariant(
//                            uuid = UUID.randomUUID(),
//                            filnavn = "f2-1",
//                            urn = "urn:sid:bundle2",
//                            variant = "ARKIV",
//                            type = "PDF"
//                        )
//                    )
//                )
//            )
//        )
    }

    @Test
    fun `Søker oppretter dagpenger søknad med dokumentkrav og ferdigstiller den`() {
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
                sannsynliggjøring("2", "f2-1", "f2-2")
            )
        )
        håndterLeggtilFil("1", "urn:sid:f1.1")
        håndterLeggtilFil("1", "urn:sid:f1.2")

        assertThrows<AktivitetException>("Alle dokumentkrav må være besvart") { håndterSendInnSøknad() }

        håndterLeggtilFil("2", "urn:sid:f2.1")

        håndterDokumentkravSammenstilling(kravId = "1", urn = "urn:sid:bundle1")
        håndterDokumentkravSammenstilling(kravId = "2", urn = "urn:sid:bundle2")
        val hendelse = håndterSendInnSøknad()

        assertEquals(hendelse.innsendtidspunkt(), testSøknadObserver.innsendt)
        // Sjekk at vi har sendt ut hendelse om innsending av dokumentkrav
        assertEquals(testSøknadObserver.dokumentkrav?.søknadId, søknadId)
        assertEquals(testSøknadObserver.dokumentkrav?.innsendingstype, "NyInnsending")

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertBehovContains(
            behovtype = Behovtype.NyInnsending
        ) { behovParametre ->
            assertEquals(hendelse.innsendtidspunkt(), behovParametre["innsendtTidspunkt"])
            assertEquals(søknadId.toString(), behovParametre["søknad_uuid"])
            assertEquals(testIdent, behovParametre["ident"])
        }
        assertDokumenter(
            behovtype = Behovtype.NyInnsending,
            listOf(
                Innsending.Dokument(
                    uuid = UUID.randomUUID(),
                    kravId = "1",
                    skjemakode = "N6",
                    varianter = listOf(
                        Dokumentvariant(
                            uuid = UUID.randomUUID(),
                            filnavn = "f1-1",
                            urn = "urn:sid:bundle1",
                            variant = "ARKIV",
                            type = "PDF"
                        )
                    )
                ),
                Innsending.Dokument(
                    uuid = UUID.randomUUID(),
                    kravId = "2",
                    skjemakode = "N6",
                    varianter = listOf(
                        Dokumentvariant(
                            uuid = UUID.randomUUID(),
                            filnavn = "f2-1",
                            urn = "urn:sid:bundle2",
                            variant = "ARKIV",
                            type = "PDF"
                        )
                    )
                )
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

        assertEquals(hendelse.innsendtidspunkt(), testSøknadObserver.innsendt)
        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt
        )

        assertThrows<AktivitetException> { håndterSendInnSøknad() }
        assertBehovContains(
            behovtype = Behovtype.NyInnsending
        ) { behovParametre ->
            assertEquals(hendelse.innsendtidspunkt(), behovParametre["innsendtTidspunkt"])
            assertEquals(søknadId.toString(), behovParametre["søknad_uuid"])
            assertEquals(testIdent, behovParametre["ident"])
        }
        assertDokumenter(
            behovtype = Behovtype.NyInnsending,
            listOf(
                Innsending.Dokument(
                    uuid = UUID.randomUUID(),
                    kravId = "1",
                    skjemakode = "N6",
                    varianter = listOf(
                        Dokumentvariant(
                            uuid = UUID.randomUUID(),
                            filnavn = "f1-1",
                            urn = "urn:sid:bundle1",
                            variant = "ARKIV",
                            type = "PDF"
                        )
                    )
                )
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

    private fun assertDokumenter(behovtype: Behovtype, expected: List<Innsending.Dokument>) {
        val behov = inspektør.aktivitetslogg.behov().findLast {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")
        val jacksonObjectMapper = jacksonObjectMapper()
        val dokumenter: List<Innsending.Dokument> = (behov.detaljer()["dokumentkrav"] as List<Map<String, Any>>).map {
            jacksonObjectMapper.convertValue(it, Innsending.Dokument::class.java)
        }

        expected.forEachIndexed { index, dokument ->
            val actual = dokumenter[index]
            assertEquals(dokument.skjemakode, actual.skjemakode)
            assertEquals(dokument.kravId, actual.kravId)
            assertVarianter(dokument.varianter, actual.varianter)
        }
    }

    private fun assertVarianter(expectedVarianter: List<Dokumentvariant>, actualVarianter: List<Dokumentvariant>) {
        expectedVarianter.forEachIndexed { index, expected ->
            val actual = actualVarianter[index]
            assertEquals(expected.filnavn, actual.filnavn)
            assertEquals(expected.variant, actual.variant)
            assertEquals(expected.type, actual.type)
            assertEquals(expected.urn, actual.urn)
        }
    }

    private fun assertBehovContains(behovtype: Behovtype, block: (Map<String, Any>) -> Unit) {
        val behov = inspektør.aktivitetslogg.behov().findLast {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        block(behov.detaljer() + behov.kontekst())
    }
}
