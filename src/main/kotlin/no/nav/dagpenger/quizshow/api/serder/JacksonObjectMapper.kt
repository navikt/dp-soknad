package no.nav.dagpenger.quizshow.api.serder

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val objectMapper = jacksonObjectMapper().also {
    it.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    it.registerModule(JavaTimeModule())
}
