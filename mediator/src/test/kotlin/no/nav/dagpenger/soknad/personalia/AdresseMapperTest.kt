package no.nav.dagpenger.soknad.personalia

import no.nav.dagpenger.pdl.adresse.AdresseMetadata
import no.nav.dagpenger.pdl.adresse.AdresseMetadata.AdresseType
import no.nav.dagpenger.pdl.adresse.AdresseMetadata.MasterType
import no.nav.dagpenger.pdl.adresse.PDLAdresse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class AdresseMapperTest {

    @Test
    fun `henter ut folkeregistrert adresse`() {
        val pdlAdresses = listOf(
            createPdlAdresse(AdresseType.OPPHOLDSADRESSE, "2000"),
            createPdlAdresse(AdresseType.BOSTEDSADRESSE, "2001"),
            createPdlAdresse(AdresseType.KONTAKTADRESSE, "2002"),
        )

        (1..5).onEach {
            pdlAdresses.shuffled(Random(it)).let { randomizedList ->
                assertEquals("2001", AdresseMapper(randomizedList).folkeregistertAdresse?.postnummer)
            }
        }
    }

    @Test
    fun `folkeregistert adresse kan være null`() {
        assertNull(AdresseMapper(emptyList()).folkeregistertAdresse)
        assertNull(
            AdresseMapper(
                listOf(
                    createPdlAdresse(AdresseType.OPPHOLDSADRESSE, "2000"),
                    createPdlAdresse(AdresseType.KONTAKTADRESSE, "2001"),
                ),
            ).folkeregistertAdresse,
        )
    }

    private fun createPdlAdresse(
        adresseType: AdresseType,
        postnummer: String,
    ): PDLAdresse {
        return PDLAdresse.PostboksAdresse(
            adresseMetadata = AdresseMetadata(
                adresseType = adresseType,
                master = MasterType.PDL,
            ),
            postnummer = postnummer,
        )
    }
}
