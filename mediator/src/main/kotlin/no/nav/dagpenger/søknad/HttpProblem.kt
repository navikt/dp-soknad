package no.nav.dagpenger.søknad

import java.net.URI

// As of https://tools.ietf.org/html/rfc7807
data class HttpProblem(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int? = 500,
    val detail: String? = null,
    val instance: URI = URI.create("about:blank")
)
