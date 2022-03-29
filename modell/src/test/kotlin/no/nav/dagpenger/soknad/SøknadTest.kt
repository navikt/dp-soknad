package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.AvventerJournalføring
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderArbeid
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val testIdent = "12345678912"

internal class SøknadTest {

    private lateinit var person: Person
    private lateinit var observatør: TestPersonObserver
    private val inspektør get() = TestSøknadInspektør(person)

    @BeforeEach
    internal fun setUp() {
        person = Person(testIdent)
        observatør = TestPersonObserver().also { person.addObserver(it) }
    }

    @Test
    fun `Søker oppretter søknad og ferdigstiller den`() {
        håndterØnskeOmNySøknadHendelse()
        assertBehov(Behovtype.NySøknad)
        håndterNySøknadOpprettet()
        håndterSendInnSøknad()
        assertBehov(Behovtype.ArkiverbarSøknad)
        håndterArkiverbarSøknad()
        assertBehov(Behovtype.Journalføring)
        håndterJournalførtSøknad()

        assertTilstander(
            UnderOpprettelse,
            UnderArbeid,
            AvventerArkiverbarSøknad,
            AvventerJournalføring,
            Journalført
        )

        println(person.aktivitetslogg.toString())
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
    fun `en person kan kun ha én opprettet søknad av gangen`() {
        val person = Person(testIdent)
        person.håndter(ØnskeOmNySøknadHendelse())
        assertThrows<Aktivitetslogg.AktivitetException> { person.håndter(ØnskeOmNySøknadHendelse()) }
    }

    private fun assertTilstander(vararg tilstander: Søknad.Tilstand.Type) {
        assertEquals(tilstander.asList(), observatør.tilstander)
    }

    private fun håndterJournalførtSøknad() {
        person.håndter(SøknadJournalførtHendelse(inspektør.søknadId))
    }

    private fun håndterNySøknadOpprettet() {
        person.håndter(SøknadOpprettetHendelse(inspektør.søknadId))
    }

    private fun håndterArkiverbarSøknad() {
        person.håndter(ArkiverbarSøknadMottattHendelse(inspektør.søknadId, "urn:dokument:1"))
    }

    private fun assertBehov(behovtype: Behovtype) {
        inspektør.personLogg.behov().find {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")
    }

    private fun håndterSendInnSøknad() {
        person.håndter(SøknadInnsendtHendelse(inspektør.søknadId))
    }

    private fun håndterØnskeOmNySøknadHendelse() {
        person.håndter(ØnskeOmNySøknadHendelse())
    }
}
