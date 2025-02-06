package no.nav.dagpenger.soknad

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class SannsynliggjøringTest {
    private val dokfaktum =
        Faktum(faktumJson("1", "f1"))
    private val faktumSomSannsynliggjøres =
        Faktum(faktumJson("2", "f2"))
    private val sannsynliggjøring =
        Sannsynliggjøring(
            "1",
            dokfaktum,
            sannsynliggjør =
                mutableSetOf(
                    faktumSomSannsynliggjøres,
                ),
        )

    @Test
    fun `likhet test`() {
        assertEquals(sannsynliggjøring, sannsynliggjøring)
        assertEquals(
            Sannsynliggjøring(
                "1",
                dokfaktum,
                sannsynliggjør =
                    mutableSetOf(
                        faktumSomSannsynliggjøres,
                    ),
            ),
            sannsynliggjøring,
        )

        assertNotEquals(
            Sannsynliggjøring(
                "2",
                dokfaktum,
                sannsynliggjør =
                    mutableSetOf(
                        faktumSomSannsynliggjøres,
                    ),
            ),
            sannsynliggjøring,
        )
        assertNotEquals(Any(), sannsynliggjøring)
        assertNotEquals(null, sannsynliggjøring)
    }

    @Test
    fun `hashcode test`() {
        assertEquals(sannsynliggjøring.hashCode(), sannsynliggjøring.hashCode())
        assertEquals(
            Sannsynliggjøring(
                "1",
                dokfaktum,
                sannsynliggjør =
                    mutableSetOf(
                        faktumSomSannsynliggjøres,
                    ),
            ).hashCode(),
            sannsynliggjøring.hashCode(),
        )
    }
}
