package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.PersonObserver.SøknadEndretTilstandEvent

class TestPersonObserver : PersonObserver {

    internal var slettet: Boolean = false
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>().also {
        it.add(Søknad.Tilstand.Type.UnderOpprettelse)
    }

    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {
        tilstander.add(event.gjeldendeTilstand)
    }

    override fun søknadSlettet(event: PersonObserver.SøknadSlettetEvent) {
        slettet = true
    }
}

class TestPersonVisitor(person: Person) : PersonVisitor {

    private val sannsynliggjøringer: MutableSet<Sannsynliggjøring> = mutableSetOf()
    private val aktiveKrav: MutableSet<Krav> = mutableSetOf()
    private val inaktiveKrav: MutableSet<Krav> = mutableSetOf()

    init {
        person.accept(this)
    }

    fun sannsynliggjøringer(): Set<Sannsynliggjøring> = sannsynliggjøringer.toSet()

    fun aktiveDokumentkrav(): Set<Krav> = aktiveKrav.toSet()
    fun inaktiveDokumentkrav(): Set<Krav> = inaktiveKrav.toSet()

    override fun visitSannsynliggjøringer(sannsynliggjøringer: Set<Sannsynliggjøring>) {
        this.sannsynliggjøringer.addAll(sannsynliggjøringer)
    }

    override fun visitAktiveKrav(krav: Set<Krav>) {
        this.aktiveKrav.addAll(krav)
    }

    override fun visitInaktiveKrav(krav: Set<Krav>) {
        this.inaktiveKrav.addAll(krav)
    }
}
