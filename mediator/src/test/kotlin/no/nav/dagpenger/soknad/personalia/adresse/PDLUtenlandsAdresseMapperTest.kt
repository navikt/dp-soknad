package no.nav.dagpenger.soknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.soknad.personalia.PDLAdresseMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PDLUtenlandsAdresseMapperTest {
    @Test
    fun `Alle felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createUtenlandskAdresse(
                adressenavnNummer = "adressenavnNummer",
                bygningEtasjeLeilighet = "bygningEtasjeLeilighet",
                postboksNummerNavn = "postboksNummerNavn",
                postkode = "postkode",
                bySted = "bysted",
                landKode = "NOR",
            ),
        ).let { adresse ->
            assertEquals("adressenavnNummer", adresse.adresselinje1)
            assertEquals("bygningEtasjeLeilighet", adresse.adresselinje2)
            assertEquals("postboksNummerNavn", adresse.adresselinje3)
            assertEquals("postkode", adresse.postnummer)
            assertEquals("bysted", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    @Test
    fun `Noen felter utfylt, andre er tomme eller null`() {
        PDLAdresseMapper.formatertAdresse(
            createUtenlandskAdresse(
                adressenavnNummer = "   ",
                bygningEtasjeLeilighet = null,
                postboksNummerNavn = "postboksNummerNavn",
                postkode = null,
                landKode = "SWE",
            ),
        ).let { adresse ->
            assertEquals("postboksNummerNavn", adresse.adresselinje1)
            assertEquals("", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("", adresse.postnummer)
            assertEquals("", adresse.poststed)
            assertEquals("SE", adresse.landkode)
            assertEquals("SVERIGE", adresse.land)
        }
    }

    @Test
    fun `Ingen felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createUtenlandskAdresse(),
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

    private fun createUtenlandskAdresse(
        adressenavnNummer: String? = null,
        bygningEtasjeLeilighet: String? = null,
        postboksNummerNavn: String? = null,
        postkode: String? = null,
        bySted: String? = null,
        landKode: String? = null,
    ): PDLAdresse.UtenlandskAdresse {
        return PDLAdresse.UtenlandskAdresse(
            adresseMetadata = createAdresseMetadata(),
            adressenavnNummer = adressenavnNummer,
            bySted = bySted,
            bygningEtasjeLeilighet = bygningEtasjeLeilighet,
            landKode = landKode,
            postboksNummerNavn = postboksNummerNavn,
            postkode = postkode,
        )
    }
}
