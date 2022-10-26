package no.nav.dagpenger.soknad.utils.auth

import io.ktor.server.plugins.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.prometheus.client.CollectorRegistry
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.SøknadMediator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadEierValidatorTest {
    private val søknadId1 = UUID.randomUUID()
    private val søknadId2 = UUID.randomUUID()
    private val ukjentSøknadId = UUID.randomUUID()
    private val eier1 = "eier1"
    private val eier2 = "eier2"

    private val søknadMediatorMock: SøknadMediator = mockk<SøknadMediator>().also {
        every { it.hentEier(søknadId1) } returns eier1
        every { it.hentEier(søknadId2) } returns eier2
        every { it.hentEier(ukjentSøknadId) } returns null
    }

    @Test
    fun `rikig eier gir ingen validerings feil`() {
        SøknadEierValidator(søknadMediatorMock).let { validator ->
            assertDoesNotThrow {
                validator.valider(søknadId1, eier1)
            }
        }
    }

    @Test
    fun `feil eier gir validerings feil`() {
        SøknadEierValidator(søknadMediatorMock).let { validator ->
            assertThrows<IkkeTilgangExeption> {
                validator.valider(søknadId2, eier1)
            }
        }
    }

    @Test
    fun `Ingen eier for søknad gir validerings feil`() {
        SøknadEierValidator(søknadMediatorMock).let { validator ->
            assertThrows<NotFoundException> {
                validator.valider(ukjentSøknadId, eier1)
            }
        }
    }

    @Test
    fun caching() {
        repeat(5) { SøknadEierValidator(søknadMediatorMock).valider(søknadId1, eier1) }
        verify(exactly = 1) { søknadMediatorMock.hentEier(søknadId1) }

        repeat(5) {
            try {
                SøknadEierValidator(søknadMediatorMock).valider(ukjentSøknadId, eier1)
            } catch (_: Exception) {
            }
        }
        verify(exactly = 5) { søknadMediatorMock.hentEier(ukjentSøknadId) }
    }

    @Test
    fun `har cache metrics`() {
        val søknadEierValidator = SøknadEierValidator(søknadMediatorMock)
        repeat(50) {
            søknadEierValidator.valider(søknadId1, eier1)
        }

        CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(emptySet()).asSequence().filter {
            it.name.contains("caffeine")
        }.toList().let {
            assertTrue(it.isNotEmpty())
        }
    }
}
