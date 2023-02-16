package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GyldigSvarTest {
    private val objectMapper = jacksonObjectMapper()

    @ParameterizedTest
    @CsvSource(
        "boolean | true",
        """localdate | "2022-01-15"""",
        "double | 3.0",
        "double | 2",
        """envalg | "valg1"""",
        """flervalg | ["valg1"]""",
        """int | 5""",
        """periode | {"fom":"2022-01-15","tom":"2022-01-29"}""",
        """periode | {"fom":"2022-01-15","tom":null}""",
        """periode | {"fom":"2022-01-15"}""",
        """tekst | "en tekst"""",
        """land | "NOR"""",
        """dokument |{"urn":"urn:vedlegg:5cf29893-e948-4d64-a295-2f6c5153ba44/c7cb3716-8750-4898-a90e-5ad90067760e","lastOppTidsstempel":"2023-02-14T13:36:31.479923"}""",
        delimiter = '|'
    )
    fun `Skal kunne opprette boolean type svar `(type: String, forventetSvar: String) {
        val jsonSvar = objectMapper.readTree("""{"type": "$type", "svar": $forventetSvar}""")
        assertDoesNotThrow {
            val svar = GyldigSvar(jsonSvar)
            assertEquals(type, svar.type)
            assertEquals(forventetSvar, svar.svarAsJson.toString())
        }
    }

    @ParameterizedTest
    @CsvSource(
        """boolean | "blabla"""",
        """localdate | "xxxas"""",
        """double | "bla"""",
        """envalg | " """",
        """envalg | [""]""",
        """flervalg | []""",
        """flervalg | [""]""",
        """int | "tekst"""",
        """periode | {"fom":"2022231-01-15"}""",
        """periode | {"fom":"2022-01-15", "tom": "blabla"}""",
        """periode | {"fom":"2022-01-15", "tom": "2022-01-14"}""",
        """land | "NORWAY"""",
        """dokument |{"urn":"fd","lastOppTidsstempel":"2023-02-14T13:36:31.479923"}""",
        """dokument |{"urn":"fd","lastOppTidsstempel":"sdf"}""",
        delimiter = '|'
    )
    fun `Skal validere svar i henhold til type`(type: String, forventetSvar: String) {
        val jsonSvar = objectMapper.readTree("""{"type": "$type", "svar": $forventetSvar}""")
        assertThrows<IllegalArgumentException> {
            GyldigSvar(jsonSvar)
        }
    }

    @ParameterizedTest
    @CsvSource(
        """boolean | null""",
        """localdate | null""",
        """double | null""",
        """envalg | null""",
        """flervalg | null""",
        """int | null""",
        """periode | null""",
        """land | null""",
        """dokument | null""",
        delimiter = '|'
    )
    fun `Skal tillate null som svar`(type: String, forventetSvar: String) {
        val jsonSvar = objectMapper.readTree("""{"type": "$type", "svar": $forventetSvar}""")
        GyldigSvar(jsonSvar)
    }

    @Test
    fun `Skal kunne opprette et generator svar`() {
        val jsonSvar = objectMapper.readTree(generatorSvar)
        assertDoesNotThrow {
            val svar = GyldigSvar(jsonSvar)
            assertEquals("generator", svar.type)
            assertEquals(jsonSvar["svar"].toString(), svar.svarAsJson.toString())
        }
    }

    @Test
    fun `Skal ikke kunne opprette et ugyldig generator svar`() {
        val jsonSvar = objectMapper.readTree(ugyldigGeneratorSvar)
        assertThrows<IllegalArgumentException> {
            GyldigSvar(jsonSvar)
        }
    }

    @Test
    fun `Skal fjerne indeks fra generator svar `() {
        val jsonSvar = objectMapper.readTree(generatorSvarMedIndeks)
        assertDoesNotThrow {
            val svar = GyldigSvar(jsonSvar)
            svar.svarAsJson.forEach { indeks ->
                indeks.forEach { faktum ->
                    assertFalse(faktum["id"].asText().contains("."), "Faktum $faktum id inneholder indeks")
                }
            }
        }
    }

    // language=JSON
    private val generatorSvar = """
      {
        "type": "generator",
        "svar": [
          [
            {
              "id": "11",
              "svar": "Ola Nordmann",
              "type": "tekst"
            },
            {
              "id": "12",
              "svar": "2010-01-08",
              "type": "localdate"
            }
          ],
          []
        ]
      }
    """.trimIndent()

    // language=JSON
    private val ugyldigGeneratorSvar = """
  {
    "id": "1001",
    "type": "generator",
    "svar": [
      [
        {
          "id": "1002.1",
          "beskrivendeId": "faktum.barn-fornavn-mellomnavn",
          "type": "tekst",
          "svar": "KREATIV FLAKKENDE"
        },
        {
          "id": "1003.1",
          "beskrivendeId": "faktum.barn-etternavn",
          "type": "tekst",
          "svar": "TAREMEL"
        },
        {
          "id": "1004.1",
          "beskrivendeId": "faktum.barn-foedselsdato",
          "type": "localdate",
          "svar": "2008-11-14"
        },
        {
          "id": "1005.1",
          "beskrivendeId": "faktum.barn-statsborgerskap",
          "type": "land",
          "svar": "NOR"
        }
      ],
      [
        {
          "beskrivendeId": "faktum.barn-fornavn-mellomnavn",
          "type": "tekst",
          "svar": "Jeg har"
        },
        {
          "beskrivendeId": "faktum.barn-etternavn",
          "type": "tekst",
          "svar": "Byttet-navn"
        },
        {
          "id": "1004.2",
          "beskrivendeId": "faktum.barn-foedselsdato",
          "type": "localdate",
          "svar": "2016-08-07"
        },
        {
          "id": "1005.2",
          "beskrivendeId": "faktum.barn-statsborgerskap",
          "type": "land",
          "svar": "NOR"
        }
      ]
    ]
  }"""

    // language=JSON
    private val generatorSvarMedIndeks = """
{
  "id": "1001",
  "type": "generator",
  "svar": [
    [
      {
        "id": "1002.1",
        "beskrivendeId": "faktum.barn-fornavn-mellomnavn",
        "type": "tekst",
        "svar": "KREATIV FLAKKENDE"
      },
      {
        "id": "1003.1",
        "beskrivendeId": "faktum.barn-etternavn",
        "type": "tekst",
        "svar": "TAREMEL"
      },
      {
        "id": "1004.1",
        "beskrivendeId": "faktum.barn-foedselsdato",
        "type": "localdate",
        "svar": "2008-11-14"
      },
      {
        "id": "1005.1",
        "beskrivendeId": "faktum.barn-statsborgerskap",
        "type": "land",
        "svar": "NOR"
      }
    ]
  ]
}
    """.trimIndent()
}
