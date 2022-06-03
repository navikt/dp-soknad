package no.nav.dagpenger.søknad

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PersonTest {
    @Test
    fun equality() {
        val person = Person("12345678910")

        assertEquals(person, person)
        assertEquals(Person("12345678910"), Person("12345678910"))
        assertNotEquals(Person("12345678911"), Person("12345678910"))
    }
}
