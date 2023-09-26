package no.nav.dagpenger.soknad

import no.nav.dagpenger.aktivitetslogg.Aktivitet

enum class SoknadBehov : Aktivitet.Behov.Behovtype {
    NySøknad,
    DokumentkravSvar,
    InnsendingMetadata,
    ArkiverbarSøknad,
    NyJournalpost,
    NyInnsending,
    NyEttersending,

    // Historiske behov som ikke lengre brukes aktivt, men må være tilgjengelig for aktivitetsloggen.
    OppgaveOmEttersending,
}
