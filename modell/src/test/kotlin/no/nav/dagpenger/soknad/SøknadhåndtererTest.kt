package no.nav.dagpenger.soknad

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class SøknadhåndtererTest {
    @Test
    fun equality() {
        val søknadhåndterer = Søknadhåndterer("12345678910")

        assertEquals(søknadhåndterer, søknadhåndterer)
        assertEquals(Søknadhåndterer("12345678910"), Søknadhåndterer("12345678910"))
        assertNotEquals(Søknadhåndterer("12345678911"), Søknadhåndterer("12345678910"))
    }
}
