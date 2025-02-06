package no.nav.dagpenger.soknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.soknad.personalia.PDLAdresseMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PDLPostAdresseIFrittFormatMapperTest {
    @Test
    fun `Alle felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createUtenlandsAdresseIFrittFormat(
                adresseLinje1 = "adresseLinje1",
                adresseLinje2 = "adresseLinje2",
                adresseLinje3 = "adresseLinje3",
                postkode = "postkode",
                byEllerStedsnavn = "byEllerStedsnavn",
                landKode = "DNK",
            ),
        ).let { adresse ->
            assertEquals("adresseLinje1", adresse.adresselinje1)
            assertEquals("adresseLinje2", adresse.adresselinje2)
            assertEquals("adresseLinje3", adresse.adresselinje3)
            assertEquals("postkode", adresse.postnummer)
            assertEquals("byEllerStedsnavn", adresse.poststed)
            assertEquals("DK", adresse.landkode)
            assertEquals("DANMARK", adresse.land)
        }
    }

    @Test
    fun `Noen felter utfylt, andre er tomme eller null`() {
        PDLAdresseMapper.formatertAdresse(
            createUtenlandsAdresseIFrittFormat(
                adresseLinje1 = "",
                adresseLinje2 = "adresseLinje2",
                adresseLinje3 = "   ",
                postkode = "postkode",
                byEllerStedsnavn = "byEllerStedsnavn",
                landKode = "ERI",
            ),
        ).let { adresse ->
            assertEquals("adresseLinje2", adresse.adresselinje1)
            assertEquals("", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("postkode", adresse.postnummer)
            assertEquals("byEllerStedsnavn", adresse.poststed)
            assertEquals("ER", adresse.landkode)
            assertEquals("ERITREA", adresse.land)
        }
    }

    @Test
    fun `Ingen felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createUtenlandsAdresseIFrittFormat(),
        ).let { adresse ->
            assertEquals("", adresse.adresselinje1)
            assertEquals("", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("", adresse.postnummer)
            assertEquals("", adresse.poststed)
            assertEquals("", adresse.landkode)
            assertEquals("", adresse.land)
        }
    }

    private fun createUtenlandsAdresseIFrittFormat(
        adresseLinje1: String? = null,
        adresseLinje2: String? = null,
        adresseLinje3: String? = null,
        postkode: String? = null,
        byEllerStedsnavn: String? = null,
        landKode: String? = null,
    ): PDLAdresse.UtenlandsAdresseIFrittFormat {
        return PDLAdresse.UtenlandsAdresseIFrittFormat(
            adresseMetadata = createAdresseMetadata(),
            adresseLinje1 = adresseLinje1,
            adresseLinje2 = adresseLinje2,
            adresseLinje3 = adresseLinje3,
            postkode = postkode,
            byEllerStedsnavn = byEllerStedsnavn,
            landKode = landKode,
        )
    }
}
