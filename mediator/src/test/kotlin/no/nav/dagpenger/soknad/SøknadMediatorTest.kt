package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.db.PersonRepository
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SøknadMediatorTest {
    companion object {
        private const val testIdent = "12345678912"
    }

    private lateinit var mediator: SøknadMediator
    private val testRapid = TestRapid()

    private val personRepository = object : PersonRepository {
        private val db = mutableMapOf<String, Person>()
        override fun hent(ident: String): Person? = db[ident]

        override fun lagre(person: Person) {
            db[person.ident()] = person
        }
    }

    @BeforeEach
    fun setup() {
        mediator = SøknadMediator(testRapid, personRepository)
    }

    @Test
    fun `Skal håndtere ønske om ny søknad`() {

        mediator.håndter(ØnskeOmNySøknadHendelse(testIdent))
        assertEquals(1, testRapid.inspektør.size)
        val behov = testRapid.inspektør.message(0)
        assertEquals("Behov", behov["@event_name"])
    }
}
