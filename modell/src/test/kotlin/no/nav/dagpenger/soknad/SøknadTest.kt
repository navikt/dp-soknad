package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.hendelse.OpprettNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadTest {
    private val testIdent = "fnr"

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
