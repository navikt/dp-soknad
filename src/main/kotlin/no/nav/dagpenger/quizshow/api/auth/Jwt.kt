package no.nav.dagpenger.quizshow.api.auth

import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal

internal fun validator(jwtCredential: JWTCredential): Principal {
    requireNotNull(jwtCredential.payload.claims["pid"] ?: jwtCredential.payload.claims["sub"]) {
        "Token må inneholde fødselsnummer for personen"
    }
    return JWTPrincipal(jwtCredential.payload)
}

internal val JWTPrincipal.fnr get() = (this.payload.claims["pid"] ?: this.payload.claims["sub"])!!.asString()
