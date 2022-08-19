package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.soknad.Søknad.Dokument.Variant
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerMidlertidligJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

private const val testIdent = "12345678912"
private const val testJournalpostId = "J123"

internal class SøknadTest {

    private lateinit var person: Person
    private lateinit var personObserver: TestPersonObserver
    private lateinit var plantUmlObservatør: PlantUmlObservatør
    private val inspektør get() = TestSøknadInspektør(person)
    private val språk = "NO"

    @BeforeEach
    internal fun setUp() {
        person = Person(testIdent)
        personObserver = TestPersonObserver().also { person.addObserver(it) }
        plantUmlObservatør = PlantUmlObservatør().also {
            person.addObserver(it)
        }
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
        håndterSøkerOppgaveHendelse()
        val innsendtHendelse = håndterSendInnSøknad()
        assertBehov(
            Behovtype.ArkiverbarSøknad,
            mapOf(
                "ident" to testIdent,
                "søknad_uuid" to inspektør.søknadId.toString(),
                "innsendtTidspunkt" to innsendtHendelse.innsendtidspunkt().toString()
            )
        )
        håndterArkiverbarSøknad()

        val dokumenter = listOf(
            Søknad.Dokument(
                varianter = listOf(
                    Variant(
                        urn = "urn:dokument:1",
                        format = "ARKIV",
                        type = "PDF"
                    )
                )
            )
        )

        assertBehov(
            Behovtype.NyJournalpost,
            mapOf("dokumenter" to dokumenter, "ident" to testIdent, "søknad_uuid" to inspektør.søknadId.toString())
        )
        håndterMidlertidigJournalførtSøknad()
        håndterJournalførtSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført
        )

        assertPuml("Søker oppretter søknad og ferdigstiller den")
    }

    @Test
    fun `oppretter en person `() {
        val person = Person(testIdent)
        object : PersonVisitor {
            init {
                person.accept(this)
            }

            override fun visitPerson(ident: String) {
                assertEquals(testIdent, ident)
            }
        }
    }

    @Test
    fun `håndtere sannsynliggjøringer og dokumentkrav`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterFaktumOppdatering()
        håndterSøkerOppgaveHendelse()
        with(TestPersonVisitor(person).sannsynliggjøringer()) {
            assertEquals(
                1, this.size
            )
            assertEquals(sannsynliggjøring, this.first())
        }
        val nyttDokumentkrav = dokumentFaktum.copy("2")
        val nySannsynliggjøring = Sannsynliggjøring(nyttDokumentkrav.id, nyttDokumentkrav, faktaSomSannsynliggjøres)
        håndterSøkerOppgaveHendelse(
            sannsynliggjøringer = setOf(
                nySannsynliggjøring, sannsynliggjøring
            )
        )

        with(TestPersonVisitor(person)) {

            with(this.sannsynliggjøringer()) {
                assertEquals(
                    2, this.size
                )
                assertTrue(this.contains(sannsynliggjøring))
                assertTrue(this.contains(nySannsynliggjøring))
            }
            with(this.aktiveDokumentkrav()) {
                assertEquals(
                    2, this.size
                )
            }
        }

        håndterSøkerOppgaveHendelse(
            sannsynliggjøringer = setOf(
                nySannsynliggjøring
            )
        )

        with(TestPersonVisitor(person)) {

            with(this.sannsynliggjøringer()) {
                assertEquals(
                    1, this.size
                )

                assertTrue(this.contains(nySannsynliggjøring))
            }
            with(this.aktiveDokumentkrav()) {
                assertEquals(
                    1, this.size
                )
            }

            with(this.inaktiveDokumentkrav()) {
                assertEquals(
                    1, this.size
                )
            }
        }
    }

    @Test
    fun `Slett søknad for person`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterSlettet()

        assertTrue(personObserver.slettet)

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            Slettet
        )
    }

    @Test
    @Disabled("Midlertidig løsning for en enklere feedbackloop for testing av frontend")
    fun `en person kan kun ha én opprettet eller påbegynt søknad av gangen`() {
        val person = Person(testIdent)
        val søknadID = UUID.randomUUID()
        person.håndter(ØnskeOmNySøknadHendelse(søknadID, språk, testIdent))
        assertThrows<AktivitetException> {
            person.håndter(ØnskeOmNySøknadHendelse(søknadID, språk, testIdent))
        }

        person.håndter(SøknadOpprettetHendelse(søknadID, testIdent))

        assertThrows<AktivitetException> {
            person.håndter(ØnskeOmNySøknadHendelse(søknadID, språk, testIdent))
        }
    }

    private fun håndterNySøknadOpprettet() {
        person.håndter(SøknadOpprettetHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterSlettet() {
        person.håndter(SlettSøknadHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterArkiverbarSøknad() {
        person.håndter(
            ArkiverbarSøknadMottattHendelse(
                inspektør.søknadId,
                testIdent,
                "urn:dokument:1".lagTestDokument()
            )
        )
    }

    private fun håndterMidlertidigJournalførtSøknad() {
        person.håndter(SøknadMidlertidigJournalførtHendelse(inspektør.søknadId, testIdent, testJournalpostId))
    }

    private fun håndterJournalførtSøknad() {
        person.håndter(JournalførtHendelse(testJournalpostId, testIdent))
    }

    private fun håndterFaktumOppdatering() {
        person.håndter(FaktumOppdatertHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterSøkerOppgaveHendelse(sannsynliggjøringer: Set<Sannsynliggjøring> = setOf(sannsynliggjøring)) {
        person.håndter(
            SøkeroppgaveHendelse(
                inspektør.søknadId, testIdent,
                sannsynliggjøringer
            )
        )
    }

    private fun håndterSendInnSøknad(): SøknadInnsendtHendelse {
        return SøknadInnsendtHendelse(inspektør.søknadId, testIdent).also {
            person.håndter(it)
        }
    }

    private fun håndterØnskeOmNySøknadHendelse() {
        person.håndter(ØnskeOmNySøknadHendelse(UUID.randomUUID(), språk, testIdent))
    }

    private fun assertTilstander(vararg tilstander: Søknad.Tilstand.Type) {
        assertEquals(tilstander.asList(), personObserver.tilstander)
    }

    private fun assertPuml(tittel: String) {
        plantUmlObservatør.verify(tittel)
    }

    private fun assertBehov(behovtype: Behovtype, forventetDetaljer: Map<String, Any> = emptyMap()) {
        val behov = inspektør.personLogg.behov().find {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        assertEquals(forventetDetaljer, behov.detaljer() + behov.kontekst())
    }
}

private fun String.lagTestDokument(): Søknad.Dokument = Søknad.Dokument(
    varianter = listOf(
        Variant(this, "ARKIV", "PDF")
    )
)
