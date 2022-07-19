package no.nav.dagpenger.soknad.personalia.adresse

import no.nav.dagpenger.pdl.adresse.AdresseMetadata

internal fun createAdresseMetadata(coAdresseNavn: String? = null): AdresseMetadata {
    return AdresseMetadata(
        adresseType = AdresseMetadata.AdresseType.BOSTEDSADRESSE,
        master = AdresseMetadata.MasterType.PDL,
        coAdresseNavn = coAdresseNavn
    )
}

internal val adresseMetadata = createAdresseMetadata()
