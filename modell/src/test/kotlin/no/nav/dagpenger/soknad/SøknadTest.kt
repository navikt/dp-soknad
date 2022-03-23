package no.nav.dagpenger.soknad

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SøknadTest {

    @Test
    fun `en person kan kun ha én opprettet søknad av gangen`() {
        val person = Person("fnr")
        person.opprettNySøknad()

        assertThrows<IllegalStateException> { person.opprettNySøknad() }
    }
}
