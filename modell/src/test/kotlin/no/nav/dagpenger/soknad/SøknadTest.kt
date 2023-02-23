package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.helpers.FerdigSøknadData
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
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
        håndterFaktumOppdatering()
        håndterSendInnSøknad()

        assertThrows<AktivitetException> {
            håndterFaktumOppdatering()
        }
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
        val hendelse = håndterSendInnSøknad()

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

    private fun assertTilstander(vararg tilstander: Type) {
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
