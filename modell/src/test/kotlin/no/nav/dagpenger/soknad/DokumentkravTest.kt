package no.nav.dagpenger.soknad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun faktumJson(id: String, beskrivendeId: String) = jacksonObjectMapper().readTree(
    """{
    |  "id": "$id",
    |  "type": "boolean",
    |  "beskrivendeId": "$beskrivendeId",
    |  "svar": true,
    |  "roller": [
    |    "søker"
    |  ],
    |  "gyldigeValg": [
    |    "f1.svar.ja",
    |    "f1.svar.nei"
    |  ],
    |  "sannsynliggjøresAv": [],
    |  "readOnly": false
    |}
    """.trimMargin()
)

internal class DokumentkravTest {
    private val dokumentFaktum = Faktum(faktumJson(id = "1", beskrivendeId = "f1"))
    private val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson(id = "2", beskrivendeId = "f2")))
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )

    @Test
    fun `håndtere dokumentkrav som et speil fra sannsynliggjøringer`() {
        val dokumentkrav = Dokumentkrav()
        assertTrue(dokumentkrav.aktiveDokumentKrav().isEmpty())
        assertTrue(dokumentkrav.inAktiveDokumentKrav().isEmpty())

        dokumentkrav.håndter(setOf(sannsynliggjøring))

        with(dokumentkrav.aktiveDokumentKrav()) {
            assertFalse(isEmpty())
            assertEquals(1, this.size)
            val førsteKrav = this.first()
            assertEquals("1", førsteKrav.id)
            assertEquals("f1", førsteKrav.beskrivendeId)
            assertTrue(førsteKrav.fakta.contains(faktaSomSannsynliggjøres.first()))
            assertEquals(førsteKrav.svar, Krav.Svar())
        }

        assertTrue(dokumentkrav.inAktiveDokumentKrav().isEmpty())
        val nyttDokumentkrav = Faktum(faktumJson(id = "3", beskrivendeId = "f3"))
        val nySannsynliggjøring = Sannsynliggjøring(nyttDokumentkrav.id, nyttDokumentkrav, faktaSomSannsynliggjøres)

        dokumentkrav.håndter(setOf(sannsynliggjøring, nySannsynliggjøring))

        with(dokumentkrav.aktiveDokumentKrav()) {
            assertFalse(isEmpty())
            assertEquals(2, this.size)
            val krav = this.toList()
            assertEquals("1", krav[0].id)
            assertEquals("f1", krav[0].beskrivendeId)
            assertTrue(krav[0].fakta.contains(faktaSomSannsynliggjøres.first()))
            assertEquals(krav[0].svar, Krav.Svar())

            assertEquals("3", krav[1].id)
            assertEquals("f3", krav[1].beskrivendeId)
            assertTrue(krav[1].fakta.contains(faktaSomSannsynliggjøres.first()))
            assertEquals(krav[1].svar, Krav.Svar())
        }

        dokumentkrav.håndter(setOf(nySannsynliggjøring))

        with(dokumentkrav.aktiveDokumentKrav()) {
            assertFalse(isEmpty())
            assertEquals(1, this.size)
            val krav = this.toList()
            assertEquals("3", krav[0].id)
            assertEquals("f3", krav[0].beskrivendeId)
            assertTrue(krav[0].fakta.contains(faktaSomSannsynliggjøres.first()))
            assertEquals(krav[0].svar, Krav.Svar())
        }

        with(dokumentkrav.inAktiveDokumentKrav()) {
            assertFalse(isEmpty())
            assertEquals(1, this.size)
            val krav = this.toList()
            assertEquals("1", krav[0].id)
            assertEquals("f1", krav[0].beskrivendeId)
            assertTrue(krav[0].fakta.contains(faktaSomSannsynliggjøres.first()))
            assertEquals(krav[0].svar, Krav.Svar())
        }
    }

    @Test
    fun `likhet test`() {
        val dokumentkrav = Dokumentkrav()
        assertEquals(dokumentkrav, dokumentkrav)
        assertEquals(dokumentkrav, Dokumentkrav())
        dokumentkrav.håndter(setOf(sannsynliggjøring))

        assertEquals(
            Dokumentkrav().also {
                it.håndter(setOf(sannsynliggjøring))
            },
            dokumentkrav
        )

        assertNotEquals(Dokumentkrav(), dokumentkrav)
        assertNotEquals(dokumentkrav, Any())
        assertNotEquals(dokumentkrav, null)
        val nyttDokumentkrav = Faktum(faktumJson(id = "3", beskrivendeId = "f3"))
        val nySannsynliggjøring = Sannsynliggjøring(nyttDokumentkrav.id, nyttDokumentkrav, faktaSomSannsynliggjøres)
        assertNotEquals(
            Dokumentkrav().also {
                it.håndter(setOf(sannsynliggjøring, nySannsynliggjøring))
            },
            dokumentkrav
        )
    }
}
