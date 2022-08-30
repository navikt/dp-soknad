package no.nav.dagpenger.soknad.utils.serder

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.blackbird.BlackbirdModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

val objectMapper = jacksonObjectMapper().apply {
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    registerModule(BlackbirdModule())
    registerModule(JavaTimeModule())
}
