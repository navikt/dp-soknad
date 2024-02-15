package no.nav.dagpenger.innsending

import no.nav.dagpenger.soknad.Innsending
import java.util.UUID

interface InnsendingRepository {
    fun hent(innsendingId: UUID): Innsending?
    fun lagre(innsending: Innsending)
    fun hentInnsending(journalPostId: String): Innsending
}
