package no.nav.dagpenger.søknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.søknad.personalia.PDLAdresseMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PDLUtenlandsAdresseIFrittFormatMapperTest {

    @Test
    fun `Alle felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createPostAdresseIFrittFormat(
                adresseLinje1 = "adresseLinje1",
                adresseLinje2 = "adresseLinje2",
                adresseLinje3 = "adresseLinje3",
                postnummer = "2001",
            )
        ).let { adresse ->
            assertEquals("adresseLinje1", adresse.adresselinje1)
            assertEquals("adresseLinje2", adresse.adresselinje2)
            assertEquals("adresseLinje3", adresse.adresselinje3)
            assertEquals("2001", adresse.postnummer)
            assertEquals("Lillestrøm", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    @Test
    fun `Noen felter utfylt, andre er tomme eller null`() {
        PDLAdresseMapper.formatertAdresse(
            createPostAdresseIFrittFormat(
                adresseLinje1 = "",
                adresseLinje2 = "adresseLinje2",
                adresseLinje3 = "   ",
                postnummer = "2013",
            )
        ).let { adresse ->
            assertEquals("adresseLinje2", adresse.adresselinje1)
            assertEquals("", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("2013", adresse.postnummer)
            assertEquals("Skjetten", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    @Test
    fun `Ingen felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createPostAdresseIFrittFormat()
        ).let { adresse ->
            assertEquals("", adresse.adresselinje1)
            assertEquals("", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("", adresse.postnummer)
            assertEquals("", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    private fun createPostAdresseIFrittFormat(
        adresseLinje1: String? = null,
        adresseLinje2: String? = null,
        adresseLinje3: String? = null,
        postnummer: String? = null
    ): PDLAdresse.PostAdresseIFrittFormat {
        return PDLAdresse.PostAdresseIFrittFormat(
            adresseMetadata = createAdresseMetadata(),
            adresseLinje1 = adresseLinje1,
            adresseLinje2 = adresseLinje2,
            adresseLinje3 = adresseLinje3,
            postnummer = postnummer
        )
    }
}
