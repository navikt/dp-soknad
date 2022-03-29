package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Companion.finnSøknad
import no.nav.dagpenger.soknad.Søknad.Companion.harAlleredeOpprettetSøknad
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

class Person private constructor(
    private val søknader: MutableList<Søknad>,
    private val ident: String,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : Aktivitetskontekst, PersonObserver {

    init {
        require(ident.matches("\\d{11}".toRegex())) { "Ugyldig ident, må være 11 sifre" }
    }

    private val observers = mutableListOf<PersonObserver>()

    constructor(ident: String) : this(mutableListOf(), ident)

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        if (søknader.harAlleredeOpprettetSøknad()) {
            ønskeOmNySøknadHendelse.severe("Kan ikke ha flere enn én opprettet søknad.")
        }

        kontekst(ønskeOmNySøknadHendelse, "Ønske om søknad registrert")
        søknader.add(
            Søknad(UUID.randomUUID(), this).also {
                it.håndter(ønskeOmNySøknadHendelse)
            }
        )
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse, "Oppretter søknad")

        val søknaden = finnSøknad(søknadOpprettetHendelse)

        søknaden.håndter(søknadOpprettetHendelse)
    }

    private fun finnSøknad(søknadHendelse: SøknadHendelse) =
        søknader.finnSøknad(søknadHendelse.søknadID()) ?: søknadHendelse.severe("Fant ikke søknaden")

    fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        kontekst(søknadInnsendtHendelse, "Sender inn søknaden")
        val søknaden = finnSøknad(søknadInnsendtHendelse)
        søknaden.håndter(søknadInnsendtHendelse)
    }

    fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse) {
        kontekst(arkiverbarSøknadMotattHendelse, "Arkiverbar søknad motatt")
        finnSøknad(arkiverbarSøknadMotattHendelse).also { søknaden ->
            søknaden.håndter(arkiverbarSøknadMotattHendelse)
        }
    }

    fun håndter(søknadJournalførtHendelse: SøknadJournalførtHendelse) {
        kontekst(søknadJournalførtHendelse, "Søknad journalført")
        finnSøknad(søknadJournalførtHendelse).also { søknaden ->
            søknaden.håndter(søknadJournalførtHendelse)
        }
    }

    fun accept(visitor: PersonVisitor) {
        visitor.visitPerson(ident)
        visitor.preVisitSøknader()
        søknader.forEach { it.accept(visitor) }
        visitor.postVisitSøknader()
        aktivitetslogg.accept(visitor)
    }

    fun addObserver(søknadObserver: PersonObserver) {
        observers.add(søknadObserver)
    }

    override fun søknadTilstandEndret(søknadEndretTilstandEvent: PersonObserver.SøknadEndretTilstandEvent) {
        observers.forEach {
            it.søknadTilstandEndret(søknadEndretTilstandEvent)
        }
    }

    private fun kontekst(hendelse: Hendelse, melding: String) {
        hendelse.kontekst(this)
        hendelse.info(melding)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst =
        SpesifikkKontekst(kontekstType = "person", mapOf("ident" to ident))
}
