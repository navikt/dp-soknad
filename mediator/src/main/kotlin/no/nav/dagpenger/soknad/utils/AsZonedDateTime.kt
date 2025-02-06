package no.nav.dagpenger.soknad.utils

import com.fasterxml.jackson.databind.JsonNode
import java.time.ZonedDateTime

fun JsonNode.asZonedDateTime(): ZonedDateTime = asText().let { ZonedDateTime.parse(it) }
