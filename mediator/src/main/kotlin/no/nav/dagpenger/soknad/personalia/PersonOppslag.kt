package no.nav.dagpenger.soknad.personalia

import io.ktor.http.HttpHeaders
import mu.KotlinLogging
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.pdl.adresse.AdresseVisitor
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Configuration.tokenXClient

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
        )

        val adresseMapper = AdresseMapper(AdresseVisitor(person).adresser)

        return Person(
            forNavn = person.fornavn,
            mellomNavn = person.mellomnavn ?: "",
            etterNavn = person.etternavn,
            fødselsDato = person.fodselsdato,
            postAdresse = adresseMapper.postAdresse,
            folkeregistrertAdresse = adresseMapper.folkeregistertAdresse
        )
    }
}
