package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.mockk
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NySøknad
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

internal class BehovMediatorTest {
    private companion object {
        private const val testIdent = "12345678912"
        private lateinit var behovMediator: BehovMediator
    }

    private val testSøknadUuid = UUID.randomUUID().toString()
    private val testRapid = TestRapid()
    private lateinit var aktivitetslogg: Aktivitetslogg
    private lateinit var person: Person

    @BeforeEach
    fun setup() {
        person = Person(testIdent)
        aktivitetslogg = Aktivitetslogg()
        behovMediator = BehovMediator(
            rapidsConnection = testRapid,
            sikkerLogg = mockk(relaxed = true)
        )
        testRapid.reset()
    }

    @Test
    internal fun `Behov blir sendt og inneholder det den skal`() {
        val hendelse = TestHendelse("Hendelse1", aktivitetslogg.barn())
        hendelse.kontekst(person)
        hendelse.kontekst(Testkontekst("Testkontekst"))

        hendelse.behov(
            NySøknad,
            "Behøver tom søknad for denne søknaden",
            mapOf(
                "parameter1" to "verdi1",
                "parameter2" to "verdi2"
            )
        )

        behovMediator.håndter(hendelse)

        val inspektør = testRapid.inspektør

        assertEquals(1, inspektør.size)
        inspektør.message(0).also { json ->
            assertStandardBehovFelter(json)
            assertEquals(listOf("NySøknad"), json["@behov"].map(JsonNode::asText))
            assertEquals(testIdent, json["ident"].asText())
            assertEquals("Testkontekst", json["Testkontekst"].asText())
            assertEquals("verdi1", json["parameter1"].asText())
            assertEquals("verdi2", json["parameter2"].asText())
            assertEquals("verdi1", json["NySøknad"]["parameter1"].asText())
            assertEquals("verdi2", json["NySøknad"]["parameter2"].asText())
        }
    }

    @Test
    internal fun `Gruppere behov`() {
        val hendelse = TestHendelse("Hendelse1", aktivitetslogg.barn())
        hendelse.kontekst(person)
        hendelse.kontekst(Testkontekst("Testkontekst"))

        hendelse.behov(
            ArkiverbarSøknad,
            "Trenger søknad på et arkiverbart format",
            mapOf(
                "parameter1" to "verdi1",
                "parameter2" to "verdi2"
            )
        )

        hendelse.behov(
            NySøknad,
            "Behøver tom søknad for denne søknaden",
            mapOf(
                "parameter3" to "verdi3",
                "parameter4" to "verdi4"
            )
        )

        behovMediator.håndter(hendelse)

        val inspektør = testRapid.inspektør

        assertEquals(1, inspektør.size)
        inspektør.message(0).also { json ->
            assertStandardBehovFelter(json)
            assertEquals(listOf("ArkiverbarSøknad", "NySøknad"), json["@behov"].map(JsonNode::asText))
            assertEquals(testIdent, json["ident"].asText())
            assertEquals("Testkontekst", json["Testkontekst"].asText())
            assertEquals("verdi1", json["parameter1"].asText())
            assertEquals("verdi2", json["parameter2"].asText())
            assertEquals("verdi3", json["parameter3"].asText())
            assertEquals("verdi4", json["parameter4"].asText())
            assertEquals("verdi1", json["ArkiverbarSøknad"]["parameter1"].asText())
            assertEquals("verdi2", json["ArkiverbarSøknad"]["parameter2"].asText())
            assertEquals("verdi3", json["NySøknad"]["parameter3"].asText())
            assertEquals("verdi4", json["NySøknad"]["parameter4"].asText())
        }
    }

    @Test
    internal fun `sjekker etter duplikatverdier`() {
        val hendelse = TestHendelse("Hendelse1", aktivitetslogg.barn())
        hendelse.kontekst(person)
        hendelse.behov(
            NySøknad,
            "Behøver tom søknad for denne søknaden",
            mapOf(
                "ident" to testIdent
            )
        )
        hendelse.behov(
            NySøknad,
            "Behøver tom søknad for denne søknaden",
            mapOf(
                "ident" to testIdent
            )
        )

        assertThrows<IllegalArgumentException> { behovMediator.håndter(hendelse) }
    }

    @Test
    internal fun `kan ikke produsere samme behov`() {
        val hendelse = TestHendelse("Hendelse1", aktivitetslogg.barn())
        hendelse.kontekst(person)
        hendelse.behov(NySøknad, "Behøver tom søknad for denne søknaden")
        hendelse.behov(NySøknad, "Behøver tom søknad for denne søknaden")

        assertThrows<IllegalArgumentException> { behovMediator.håndter(hendelse) }
    }

    private fun assertStandardBehovFelter(json: JsonNode) {
        assertEquals("behov", json["@event_name"].asText())
        assertTrue(json.hasNonNull("@id"))
        assertDoesNotThrow { UUID.fromString(json["@id"].asText()) }
        assertTrue(json.hasNonNull("@opprettet"))
        assertDoesNotThrow { LocalDateTime.parse(json["@opprettet"].asText()) }
        assertEquals("behov", json["@event_name"].asText())
    }

    private class Testkontekst(
        private val melding: String
    ) : Aktivitetskontekst {
        override fun toSpesifikkKontekst() = SpesifikkKontekst(melding, mapOf(melding to melding))
    }

    private class TestHendelse(
        private val melding: String,
        internal val logg: Aktivitetslogg
    ) : Hendelse(logg), Aktivitetskontekst {
        init {
            logg.kontekst(this)
        }

        // override fun ident(): String = testIdent

        override fun toSpesifikkKontekst() = SpesifikkKontekst("TestHendelse")
        override fun kontekst(kontekst: Aktivitetskontekst) {
            logg.kontekst(kontekst)
        }
    }
}
