package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.http.HttpHeaders
import mu.KotlinLogging
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.Configuration.tokenXClient

private val logger = KotlinLogging.logger {}

internal class PersonOppslag(
    private val personOppslag: PersonOppslag,
    private val tokenProvider: (token: String, audience: String) -> String = { s: String, a: String ->
        tokenXClient.tokenExchange(s, a).accessToken
    },
    private val pdlAudience: String = Configuration.pdlAudience
) {
    suspend fun hentPerson(fnr: String, subjectToken: String): Person {
        val person = personOppslag.hentPerson(
            fnr,
            mapOf(
                HttpHeaders.Authorization to "Bearer ${tokenProvider.invoke(subjectToken, pdlAudience)}"
            )
        ).also {
            logger.info { "Fikk hentet person: $it" }
        }
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
            forNavn = person.fornavn,
            mellomNavn = person.mellomnavn ?: "",
            etterNavn = person.etternavn,
            fødselsDato = person.fodselsdato,
            postAdresse = adresse,
            folkeregistrertAdresse = adresse
        )
    }
}
