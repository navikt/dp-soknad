package no.nav.dagpenger.søknad

import no.nav.dagpenger.søknad.Søknad.Companion.finnSøknad
import no.nav.dagpenger.søknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.søknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.søknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.søknad.hendelse.Hendelse
import no.nav.dagpenger.søknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.søknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.søknad.hendelse.ØnskeOmNySøknadHendelse

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
        // if (søknader.harAlleredeOpprettetSøknad()) {
        //    ønskeOmNySøknadHendelse.severe("Kan ikke ha flere enn én opprettet søknad.")
        // }
        kontekst(ønskeOmNySøknadHendelse, "Ønske om søknad registrert")
        søknader.add(
            Søknad(ønskeOmNySøknadHendelse.søknadID(), ønskeOmNySøknadHendelse.språk(), this).also {
                it.håndter(ønskeOmNySøknadHendelse)
            }
        )
    }

    fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse) {
        kontekst(harPåbegyntSøknadHendelse, "Fortsetter på påbegynt søknad")
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse, "Oppretter søknad")

        val søknaden = finnSøknad(søknadOpprettetHendelse)

        søknaden.håndter(søknadOpprettetHendelse)
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

    fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse) {
        kontekst(faktumOppdatertHendelse, "Faktum oppdatert")
        finnSøknad(faktumOppdatertHendelse).also { søknaden ->
            søknaden.håndter(faktumOppdatertHendelse)
        }
    }

    fun håndter(slettSøknadHendelse: SlettSøknadHendelse) {
        kontekst(slettSøknadHendelse, "Forsøker å slette søknad med ${slettSøknadHendelse.søknadID}")
        finnSøknad(slettSøknadHendelse).also { søknaden ->
            søknaden.håndter(slettSøknadHendelse)
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

    override fun søknadSlettet(event: PersonObserver.SøknadSlettetEvent) {
        observers.forEach {
            it.søknadSlettet(event)
        }
    }

    private fun finnSøknad(søknadHendelse: SøknadHendelse) =
        søknader.finnSøknad(søknadHendelse.søknadID()) ?: søknadHendelse.severe("Fant ikke søknaden")

    private fun finnSøknad(journalførtHendelse: JournalførtHendelse): Søknad? {
        return søknader.finnSøknad(journalførtHendelse.journalpostId())
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
