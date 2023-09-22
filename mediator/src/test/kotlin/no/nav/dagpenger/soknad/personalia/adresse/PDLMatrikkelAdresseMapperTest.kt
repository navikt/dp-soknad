package no.nav.dagpenger.soknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.AdresseMetadata
import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.soknad.personalia.Adresse
import no.nav.dagpenger.soknad.personalia.PDLAdresseMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PDLMatrikkelAdresseMapperTest {

    @Test
    fun `tom adresse for matrikkel adresse`() {
        PDLAdresseMapper.formatertAdresse(
            PDLAdresse.MatrikkelAdresse(
                adresseMetadata = AdresseMetadata(
                    adresseType = AdresseMetadata.AdresseType.BOSTEDSADRESSE,
                    master = AdresseMetadata.MasterType.PDL,

                    coAdresseNavn = null,
                ),
                bruksenhetsnummer = null,
                kommunenummer = null,
                matrikkelId = null,
                postnummer = "2013",
                tilleggsnavn = null,
            ),
        ).let {
            assertEquals(Adresse(), it)
        }
    }
}
