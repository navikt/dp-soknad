package no.nav.dagpenger.soknad

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadTest {
    private val testIdent = "fnr"

    @Test
    fun `person oppretter en søknad med tilstand Opprettet`() {
        val person = Person(testIdent)
        person.opprettNySøknad()
    }

    @Test
    fun `en person kan kun ha én opprettet søknad av gangen`() {
        val person = Person("fnr")
        person.opprettNySøknad()
        assertThrows<IllegalStateException> { person.opprettNySøknad() }
    }

    private class PersonTestVisitor : PersonVisitor {
        override fun visitPerson(søknader: List<Søknad>, ident: String) {
            assertEquals(1, søknader.size)
            assertEquals("fnr", ident)
        }
    }

    private class SøknadTestVisitor : SøknadVisitor {
        override fun visitSøknad(søknadId: UUID, tilstand: Søknad.Tilstand) {
            assertEquals(Søknad.Opprettet, tilstand)
        }
    }
}
