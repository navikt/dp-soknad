package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.OpprettNySøknadHendelse
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
        håndterNySøknadHendelse()
        assertSøknadOpprettet()
        assertBehov(Behovtype.NySøknad)
    }

    private fun assertBehov(behovtype: Behovtype) {
        assertEquals(behovtype, TestSøknadInspektør(person).personLogg.behov()[0].type)
    }

    private fun assertSøknadOpprettet() {
        assertEquals(Søknad.Opprettet, TestSøknadInspektør(person).gjeldendetilstand)
    }

    private fun håndterNySøknadHendelse() {
        person.håndter(OpprettNySøknadHendelse())
    }

    @Test
    fun `person oppretter en søknad med tilstand Opprettet`() {
        val person = Person(testIdent)
        person.håndter(OpprettNySøknadHendelse())
        assertEquals(1, PersonTestVisitor(person).antallSøknader)
    }

    @Test
    fun `en person kan kun ha én opprettet søknad av gangen`() {
        val person = Person("fnr")
        person.håndter(OpprettNySøknadHendelse())
        assertThrows<Aktivitetslogg.AktivitetException> { person.håndter(OpprettNySøknadHendelse()) }

        println(person.aktivitetslogg.toString())
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
            assertEquals(Søknad.Opprettet, tilstand)
        }
    }
}
