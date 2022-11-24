package no.nav.dagpenger.soknad.utils

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
