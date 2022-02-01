package no.nav.dagpenger.quizshow.api.personalia

import java.time.LocalDate

internal class PersonOppslag {
    suspend fun hentPerson(fnr: String): Person {
        val adresse = Adresse(
            adresselinje1 = "adresselinje1",
            adresselinje2 = "adresselinje2",
            adresselinje3 = "adresselinje3",
            byEllerStedsnavn = "byEllerStedsnavn",
            landkode = "NOR",
            land = "Norge",
            postkode = "2013"
        )
        return Person(
            forNavn = "forNavn",
            mellonNavn = "mellonNavn",
            etterNavn = "etterNavn",
            fødselsDato = LocalDate.of(2000, 5, 1),
            postAdresse = adresse,
            folkeregistrertAdresse = adresse
        )
    }
}
