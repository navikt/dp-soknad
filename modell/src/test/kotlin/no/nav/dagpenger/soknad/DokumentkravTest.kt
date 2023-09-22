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
    |  "sannsynliggjoresAv": [],
    |  "readOnly": false
    |}
    """.trimMargin(),
)

internal class DokumentkravTest {
    private val dokumentFaktum = Faktum(faktumJson(id = "1", beskrivendeId = "f1"))
    private val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson(id = "2", beskrivendeId = "f2")))
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres,
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
            dokumentkrav,
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
            dokumentkrav,
        )
    }

    @Test
    fun `gir riktig skjemakode`() {
        assertEquals("N6", krav("ID").tilSkjemakode())
        assertEquals("T3", krav("faktum.dokument-avtjent-militaer-sivilforsvar-tjeneste-siste-12-mnd-dokumentasjon").tilSkjemakode())
        assertEquals("K1", krav("faktum.dokument-tjenestepensjon").tilSkjemakode())
        assertEquals("K1", krav("faktum.dokument-arbeidslos-GFF-hvilken-periode").tilSkjemakode())
        assertEquals("K1", krav("faktum.dokument-garantilott-GFF-hvilken-periode").tilSkjemakode())
        assertEquals("K1", krav("faktum.dokument-etterlonn").tilSkjemakode())
        assertEquals("K1", krav("faktum.dokument-dagpenger-eos-land").tilSkjemakode())
        assertEquals("K1", krav("faktum.dokument-annen-ytelse").tilSkjemakode())
        assertEquals("V6", krav("faktum.dokument-okonomiske-goder-tidligere-arbeidsgiver").tilSkjemakode())
        assertEquals("O2", krav("faktum.dokument-arbeidsavtale").tilSkjemakode())
        assertEquals("T8", krav("faktum.dokument-arbeidsforhold-avskjediget").tilSkjemakode())
        assertEquals("T8", krav("faktum.dokument-arbeidsforhold-blitt-sagt-opp").tilSkjemakode())
        assertEquals("T8", krav("faktum.dokument-arbeidsforhold-sagt-opp-selv").tilSkjemakode())
        assertEquals("T8", krav("faktum.dokument-arbeidsforhold-redusert").tilSkjemakode())
        assertEquals("M6", krav("faktum.dokument-timelister").tilSkjemakode())
        assertEquals("M7", krav("faktum.dokument-brev-fra-bobestyrer-eller-konkursforvalter").tilSkjemakode())
        assertEquals("T6", krav("faktum.dokument-arbeidsforhold-permittert").tilSkjemakode())
        assertEquals("T9", krav("faktum.dokument-bekreftelse-fra-lege-eller-annen-behandler").tilSkjemakode())
        assertEquals("Y2", krav("faktum.dokument-fulltid-bekreftelse-fra-relevant-fagpersonell").tilSkjemakode())
        assertEquals("Y2", krav("faktum.dokument-hele-norge-bekreftelse-fra-relevant-fagpersonell").tilSkjemakode())
        assertEquals("Y2", krav("faktum.dokument-alle-typer-bekreftelse-fra-relevant-fagpersonell").tilSkjemakode())
        assertEquals("T2", krav("faktum.dokument-utdanning-sluttdato").tilSkjemakode())
        assertEquals("X8", krav("faktum.dokument-foedselsattest-bostedsbevis-for-barn-under-18aar").tilSkjemakode())
    }

    private fun krav(beskrivendeId: String): Krav {
        val faktum = Faktum(faktumJson("1", beskrivendeId))
        val sannsynliggjøring = Sannsynliggjøring("id", faktum)
        return Krav(sannsynliggjøring)
    }
}
