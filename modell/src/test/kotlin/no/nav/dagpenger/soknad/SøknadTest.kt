package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.helpers.FerdigSøknadData
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
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
import java.util.UUID

internal class SøknadTest {
    private val testIdent = "12345678912"
    private lateinit var søknad: Søknad
    private lateinit var testSøknadObserver: TestSøknadObserver
    private lateinit var plantUmlObservatør: PlantUmlObservatør
    private val inspektør get() = TestSøknadInspektør(søknad)
    private val språk = "NO"

    private fun sannsynliggjøring(
        sannsynliggjøringId: String,
        dokumentFaktum: String,
        faktaSomSannsynliggjøres: String,
    ): Sannsynliggjøring {
        return Sannsynliggjøring(
            id = sannsynliggjøringId,
            faktum = Faktum(faktumJson("1", dokumentFaktum)),
            sannsynliggjør = mutableSetOf(Faktum(faktumJson("2", faktaSomSannsynliggjøres))),
        )
    }

    private val søknadId = UUID.randomUUID()

    @BeforeEach
    internal fun setUp() {
        søknad =
            Søknad(
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
                "ident" to testIdent,
            ),
        )
        håndterNySøknadOpprettet()
        håndterFaktumOppdatering()
        håndterSøkerOppgaveHendelse(
            setOf(
                sannsynliggjøring("1", "f1-1", "f1-2"),
                sannsynliggjøring("2", "f2-1", "f2-2"),
            ),
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
                "ident" to testIdent,
            ),
        )
        håndterNySøknadOpprettet()
        håndterFaktumOppdatering()
        håndterSendInnSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Innsendt,
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
            Slettet,
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
                Prosessversjon("navn", 2),
            ),
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
                sannsynliggjøringer,
            ),
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
                prosessnavn = prosessnavn,
            ),
        )
    }

    private fun assertTilstander(vararg tilstander: Søknad.Tilstand.Type) {
        assertEquals(tilstander.asList(), testSøknadObserver.tilstander)
    }

    private fun assertBehov(
        behovtype: Behovtype,
        forventetDetaljer: Map<String, Any> = emptyMap(),
    ) {
        val behov =
            inspektør.aktivitetslogg.behov().findLast {
                it.type == behovtype
            } ?: throw AssertionError("Fant ikke behov $behovtype")

        assertEquals(
            forventetDetaljer,
            behov.detaljer() + behov.kontekst(),
        )
    }
}
