package no.nav.dagpenger.soknad

// language=JSON
fun nySøknadBehovsløsning(søknadUuid: String) = """
{
  "@event_name": "behov",
  "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
  "@behov": [
    "NySøknad"
  ],
  "ident": "12345678912",
  "søknad_uuid": "$søknadUuid",
  "NySøknad": {},
  "@id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
  "@opprettet": "2022-03-30T12:19:08.418821",
  "system_read_count": 0,
  "system_participating_services": [
    {
      "id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
      "time": "2022-03-30T12:19:08.418821"
    }
  ],
  "@løsning": {"NySøknad": {"søknad_uuid": "$søknadUuid"}}
}""".trimMargin()
