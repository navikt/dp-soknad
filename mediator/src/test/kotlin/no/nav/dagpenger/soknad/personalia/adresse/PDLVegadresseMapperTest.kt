package no.nav.dagpenger.soknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.AdresseMetadata
import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.soknad.personalia.PDLAdresseMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PDLVegadresseMapperTest {
    @Test
    fun `Alle felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createVegAdresse(
                adresseMetadata = createAdresseMetadata("coadressenavn"),
                adressenavn = "adressenavn",
                husbokstav = "husbokstav",
                husnummer = "husnummer",
                postnummer = "2013",
            ),
        ).let { adresse ->
            assertEquals("coadressenavn", adresse.adresselinje1)
            assertEquals("adressenavn husnummerhusbokstav", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("2013", adresse.postnummer)
            assertEquals("Skjetten", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    @Test
    fun `Noen felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createVegAdresse(
                adressenavn = "adressenavn",
                postnummer = "2013",
            ),
        ).let { adresse ->
            assertEquals("adressenavn", adresse.adresselinje1)
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
            createVegAdresse(),
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

    private fun createVegAdresse(
        adresseMetadata: AdresseMetadata = createAdresseMetadata(),
        adressenavn: String? = null,
        husbokstav: String? = null,
        husnummer: String? = null,
        postnummer: String? = null,
    ): PDLAdresse.VegAdresse {
        return PDLAdresse.VegAdresse(
            adresseMetadata = adresseMetadata,
            adressenavn = adressenavn,
            husbokstav = husbokstav,
            husnummer = husnummer,
            postnummer = postnummer,
        )
    }
}
