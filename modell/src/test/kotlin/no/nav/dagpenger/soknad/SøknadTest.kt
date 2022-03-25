package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMotattHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadTest {

    companion object {
        private val testIdent = "fnr"
        val person = Person(testIdent)
    }

    @Test
    fun `Søker oppretter søknad og ferdigstiller den`() {
        håndterØnskeOmNySøknadHendelse()
        assertØnskeOmNySøknadHendelse()
        assertBehov(Behovtype.NySøknad)
        håndterNySøknadOpprettet()
        assertEquals(Søknad.UnderArbeid, oppdatertInspektør().gjeldendetilstand)
        håndterSendInnSøknad()
        assertEquals(Søknad.AvventerArkiverbarSøknad, oppdatertInspektør().gjeldendetilstand)
        assertBehov(Behovtype.ArkiverbarSøknad)
        håndterArkiverbarSøknad()
        assertEquals(Søknad.AvventerJournalføring, oppdatertInspektør().gjeldendetilstand)
        assertBehov(Behovtype.Journalføring)
        println(person.aktivitetslogg.toString())
    }

    @Test
    fun `person oppretter en søknad med tilstand Opprettet`() {
        val person = Person(testIdent)
        person.håndter(ØnskeOmNySøknadHendelse())
        assertEquals(1, PersonTestVisitor(person).antallSøknader)
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

    private fun assertØnskeOmNySøknadHendelse() {
        assertEquals(Søknad.UnderOpprettelse, oppdatertInspektør().gjeldendetilstand)
    }

    private fun håndterSendInnSøknad() {
        person.håndter(SøknadInnsendtHendelse(oppdatertInspektør().søknadId))
    }

    private fun oppdatertInspektør() = TestSøknadInspektør(person)

    private fun håndterØnskeOmNySøknadHendelse() {
        person.håndter(ØnskeOmNySøknadHendelse())
    }

    private class PersonTestVisitor(person: Person) : PersonVisitor {

        init {
            person.accept(this)
        }

        var antallSøknader = 0
        override fun visitPerson(ident: String) {
            assertEquals("fnr", ident)
        }

        override fun postVisitSøknader() {
            antallSøknader++
        }

        override fun visitSøknad(søknadId: UUID, tilstand: Søknad.Tilstand) {
            assertEquals(Søknad.UnderOpprettelse, tilstand)
        }
    }
}
