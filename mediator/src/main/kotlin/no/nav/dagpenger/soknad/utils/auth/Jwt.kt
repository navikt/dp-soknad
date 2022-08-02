package no.nav.dagpenger.soknad.utils.auth

import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPayloadHolder
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.ApplicationRequest

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