package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMotattHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SøknadTest {

    companion object {
        private val testIdent = "fnr"
        val person = Person(testIdent)
    }

    @Test
    fun `Søker oppretter søknad og ferdigstiller den`() {
        håndterØnskeOmNySøknadHendelse()
        assertEquals(Søknad.Tilstand.Type.UnderOpprettelse, oppdatertInspektør().gjeldendetilstand)
        assertBehov(Behovtype.NySøknad)
        håndterNySøknadOpprettet()
        assertEquals(Søknad.Tilstand.Type.UnderArbeid, oppdatertInspektør().gjeldendetilstand)
        håndterSendInnSøknad()
        assertEquals(Søknad.Tilstand.Type.AvventerArkiverbarSøknad, oppdatertInspektør().gjeldendetilstand)
        assertBehov(Behovtype.ArkiverbarSøknad)
        håndterArkiverbarSøknad()
        assertEquals(Søknad.Tilstand.Type.AvventerJournalføring, oppdatertInspektør().gjeldendetilstand)
        assertBehov(Behovtype.Journalføring)
        håndterJournalførtSøknad()
        assertEquals(Søknad.Tilstand.Type.Journalført, oppdatertInspektør().gjeldendetilstand)
        println(person.aktivitetslogg.toString())
    }

    private fun håndterJournalførtSøknad() {
        person.håndter(SøknadJournalførtHendelse(oppdatertInspektør().søknadId))
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

    private fun håndterNySøknadOpprettet() {
        person.håndter(SøknadOpprettetHendelse(oppdatertInspektør().søknadId))
    }

    private fun håndterArkiverbarSøknad() {
        person.håndter(ArkiverbarSøknadMotattHendelse(oppdatertInspektør().søknadId, "urn:dokument:1"))
    }

    private fun assertBehov(behovtype: Behovtype) {
        oppdatertInspektør().personLogg.behov().find {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")
    }

    private fun håndterSendInnSøknad() {
        person.håndter(SøknadInnsendtHendelse(oppdatertInspektør().søknadId))
    }

    private fun oppdatertInspektør() = TestSøknadInspektør(person)

    private fun håndterØnskeOmNySøknadHendelse() {
        person.håndter(ØnskeOmNySøknadHendelse())
    }
}
