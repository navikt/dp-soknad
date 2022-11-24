package no.nav.dagpenger.soknad.innsending

import no.nav.dagpenger.soknad.Innsending
import java.util.UUID

interface InnsendingRepository {
    fun opprett(innsendingId: UUID, ident: String): Innsending
    fun hent(innsendingId: UUID): Innsending?
    fun lagre(innsending: Innsending)
    fun finnFor(søknadsId: UUID): List<Innsending>
}
