package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediatorTest {
    private val rapid = TestRapid()
    private val mediator = Mediator(rapid)

    @Test
    fun `publiserer ny-søknadsmelding på kafka`() {
        val fnr = "12345678910"
        mediator.nySøknad(fnr)
        rapid.inspektør.message(0).also {
            assertEquals(fnr, it["fødselsnummer"].asText())
            assertTrue(it.has("avklaringsId"))
            assertTrue(it.has("@event_name"))
            assertEquals("ønsker_rettighetsavklaring", it["@event_name"].asText())
        }
    }



    @Test
    fun `reagerer på mottak av kafkamelding`() {

        rapid.sendTestMessage(seksjonMessage)
    }

    val seksjonMessage = """{
  "@event_name": "behov",
  "@opprettet": "2020-10-28T12:50:36.349916",
  "@id": "e685a88d-02e6-4683-b417-fd8a750162fe",
  "@behov": [
    "Ønsker dagpenger fra dato"
  ],
  "fødselsnummer": "12345678910",
  "fakta": [
    {
      "type": "GrunnleggendeFaktum",
      "navn": "Ønsker dagpenger fra dato",
      "id": "1",
      "avhengigFakta": [],
      "avhengerAvFakta": [],
      "clazz": "localdate",
      "rootId": 1,
      "indeks": 0,
      "roller": [
        "søker"
      ]
    }
  ],
  "system_read_count": 0
}"""
}