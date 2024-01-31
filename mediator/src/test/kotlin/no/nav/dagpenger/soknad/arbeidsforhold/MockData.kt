package no.nav.dagpenger.soknad.arbeidsforhold

val mockAaregResponse =
    // language=JSON
    """
            [
              {
                "id": "H911050676R16054L0001",
                "arbeidssted": {
                  "type": "Underenhet",
                  "identer": [
                    {
                      "type": "ORGANISASJONSNUMMER",
                      "ident": "910825518"
                    }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2014-01-01",
                  "sluttdato": "2015-01-01"
                }
              },
              {
                "id": "V911050676R16054L0001",
                "arbeidssted": {
                  "type": "Underenhet",
                  "identer": [
                    {
                      "type": "ORGANISASJONSNUMMER",
                      "ident": "910825577"
                    }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2016-01-01"
                }
              }, 
              {
                "id": "V911050676R16054L0003",
                "arbeidssted": {
                  "type": "Person",
                  "identer": [
                    {
                      "type": "AKTØR_ID",
                      "ident": "12345678903"
                    }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2016-01-01"
                }
              }
              
            ]
    """.trimIndent()

val mockEregResponse =
    // language=JSON
    """
         {"navn": {"navnelinje1": "ABC AS"}}
    """.trimIndent()
