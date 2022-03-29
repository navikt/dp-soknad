package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.mockk
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
    internal fun `Sender NySøkad behov`() {
        val hendelse = TestHendelse("Hendelse1", aktivitetslogg.barn())
        hendelse.kontekst(person)

        hendelse.behov(
            NySøknad,
            "Behøver tom søknad for denne søknaden",
            mapOf(
                "ident" to testIdent
            )
        )

        behovMediator.håndter(hendelse)

        val inspektør = testRapid.inspektør

        assertEquals(1, inspektør.size)
        inspektør.message(0).also {
            println(it)
            assertEquals("behov", it["@event_name"].asText())
            assertTrue(it.hasNonNull("@id"))
            assertDoesNotThrow { UUID.fromString(it["@id"].asText()) }
            assertTrue(it.hasNonNull("@opprettet"))
            assertDoesNotThrow { LocalDateTime.parse(it["@opprettet"].asText()) }
            assertEquals(listOf("NySøknad"), it["@behov"].map(JsonNode::asText))
            assertEquals("behov", it["@event_name"].asText())
            assertEquals(testIdent, it["ident"].asText())
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
