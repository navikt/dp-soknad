package no.nav.dagpenger.søknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.søknad.personalia.PDLAdresseMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PDLPostboksAdresseMapperTest {

    @Test
    fun `Alle felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createPostboksAdresse(
                postbokseier = "postbokseier",
                postboks = "postboks",
                postnummer = "2000"
            )
        ).let { adresse ->
            assertEquals("postbokseier", adresse.adresselinje1)
            assertEquals("postboks", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("2000", adresse.postnummer)
            assertEquals("Lillestrøm", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    @Test
    fun `Noen felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createPostboksAdresse(
                postbokseier = null,
                postboks = "  ",
                "2001"
            )
        ).let { adresse ->
            assertEquals("", adresse.adresselinje1)
            assertEquals("", adresse.adresselinje2)
            assertEquals("", adresse.adresselinje3)
            assertEquals("2001", adresse.postnummer)
            assertEquals("Lillestrøm", adresse.poststed)
            assertEquals("NO", adresse.landkode)
            assertEquals("NORGE", adresse.land)
        }
    }

    @Test
    fun `Ingen felter utfylt`() {
        PDLAdresseMapper.formatertAdresse(
            createPostboksAdresse()
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

    private fun createPostboksAdresse(
        postbokseier: String? = null,
        postboks: String? = null,
        postnummer: String? = null,
    ): PDLAdresse.PostboksAdresse {
        return PDLAdresse.PostboksAdresse(
            adresseMetadata = createAdresseMetadata(),
            postbokseier = postbokseier,
            postboks = postboks,
            postnummer = postnummer
        )
    }
}
