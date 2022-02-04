package no.nav.dagpenger.quizshow.api.auth

import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest

internal fun validator(jwtCredential: JWTCredential): Principal {
    requireNotNull(jwtCredential.payload.claims["pid"] ?: jwtCredential.payload.claims["sub"]) {
        "Token må inneholde fødselsnummer for personen"
    }
    return JWTPrincipal(jwtCredential.payload)
}

internal val JWTPrincipal.fnr get() = (this.payload.claims["pid"] ?: this.payload.claims["sub"])!!.asString()

internal fun ApplicationRequest.jwt(): String = this.parseAuthorizationHeader().let { authHeader ->
    (authHeader as? HttpAuthHeader.Single)?.blob ?: throw IllegalArgumentException("JWT not found")
}
