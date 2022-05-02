package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Companion.finnSøknad
import no.nav.dagpenger.soknad.Søknad.Companion.harAlleredeOpprettetSøknad
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse

class Person private constructor(
    søknadsfunksjon: (person: Person) -> MutableList<Søknad>,
    private val ident: String,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : Aktivitetskontekst, PersonObserver {

    companion object {
        internal fun rehydrer(
            ident: String,
            aktivitetslogg: Aktivitetslogg,
            søknadsfunksjon: (person: Person) -> MutableList<Søknad>,
        ): Person = Person(søknadsfunksjon, ident, aktivitetslogg)
    }


    private val søknader: MutableList<Søknad>

    init {
        require(ident.matches("\\d{11}".toRegex())) { "Ugyldig ident, må være 11 sifre" }
        this.søknader = søknadsfunksjon(this)
    }

    fun ident() = ident

    private val observers = mutableListOf<PersonObserver>()

    constructor(ident: String) : this({ mutableListOf() }, ident)
    constructor(ident: String, søknadsfunksjon: (person: Person) -> MutableList<Søknad>) : this(søknadsfunksjon, ident)

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        if (søknader.harAlleredeOpprettetSøknad()) {
            ønskeOmNySøknadHendelse.severe("Kan ikke ha flere enn én opprettet søknad.")
        }

        kontekst(ønskeOmNySøknadHendelse, "Ønske om søknad registrert")
        søknader.add(
            Søknad(ønskeOmNySøknadHendelse.søknadID(), this).also {
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

    private fun finnSøknad(journalførtHendelse: JournalførtHendelse): Søknad? {
        return søknader.finnSøknad(journalførtHendelse.journalpostId())
    }

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

    fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(søknadMidlertidigJournalførtHendelse, "Søknad midlertidig journalført")
        finnSøknad(søknadMidlertidigJournalførtHendelse).also { søknaden ->
            søknaden.håndter(søknadMidlertidigJournalførtHendelse)
        }
    }

    fun håndter(journalførtHendelse: JournalførtHendelse) {
        kontekst(journalførtHendelse, "Søknad journalført")
        val søknaden = finnSøknad(journalførtHendelse)
        when {
            søknaden != null -> søknaden.håndter(journalførtHendelse)
            else -> journalførtHendelse.info("Fant ikke søknaden for ${journalførtHendelse.journalpostId()}")
        }
    }

    fun accept(visitor: PersonVisitor) {
        visitor.visitPerson(ident)
        visitor.visitPerson(ident, søknader)
        visitor.preVisitSøknader()
        søknader.forEach { it.accept(visitor) }
        visitor.postVisitSøknader()
        aktivitetslogg.accept(visitor)
    }

    fun addObserver(søknadObserver: PersonObserver) {
        observers.add(søknadObserver)
    }

    override fun søknadTilstandEndret(event: PersonObserver.SøknadEndretTilstandEvent) {
        observers.forEach {
            it.søknadTilstandEndret(event)
        }
    }

    private fun kontekst(hendelse: Hendelse, melding: String) {
        hendelse.kontekst(this)
        hendelse.info(melding)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst =
        SpesifikkKontekst(kontekstType = "person", mapOf("ident" to ident))

    override fun equals(other: Any?): Boolean {
        return other is Person && this.ident == other.ident
    }
}
