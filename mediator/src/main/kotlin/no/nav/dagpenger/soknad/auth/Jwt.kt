package no.nav.dagpenger.soknad.auth

import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPayloadHolder
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest

internal fun validator(jwtCredential: JWTCredential): Principal {
    requirePid(jwtCredential)
    return JWTPrincipal(jwtCredential.payload)
}

internal fun ApplicationCall.ident(): String = requireNotNull(this.authentication.principal<JWTPrincipal>()) { "Ikke autentisert" }.fnr

internal val JWTPrincipal.fnr get(): String = requirePid(this)

private fun requirePid(credential: JWTPayloadHolder): String = requireNotNull(credential.payload.claims["pid"]?.asString()) { "Token må inneholde fødselsnummer for personen i claim 'pid'" }

internal fun ApplicationRequest.jwt(): String = this.parseAuthorizationHeader().let { authHeader ->
    (authHeader as? HttpAuthHeader.Single)?.blob ?: throw IllegalArgumentException("JWT not found")
}