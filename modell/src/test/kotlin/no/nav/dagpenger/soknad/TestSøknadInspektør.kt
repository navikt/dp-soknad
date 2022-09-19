package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadInspektør(søknad: Søknad) : SøknadVisitor {
    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var dokumentkrav: Dokumentkrav
    lateinit var aktivitetslogg: Aktivitetslogg
    lateinit var innsending: InnsendingData
    private var ettersending: Boolean = false
    val ettersendinger = mutableListOf<InnsendingData>()

    data class InnsendingData(
        val innsendingId: UUID,
        val innsending: Innsending.InnsendingType,
        val tilstand: Innsending.Tilstand.Type,
        val innsendt: ZonedDateTime,
        val journalpost: String?,
        val hovedDokument: Innsending.Dokument?,
        val dokumenter: List<Innsending.Dokument>
    )

    init {
        søknad.accept(this)
    }

    val innsendingId get() = innsending!!.innsendingId

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.søknadId = søknadId
        this.dokumentkrav = dokumentkrav
    }

    override fun preVisitEttersendinger() {
        ettersending = true
    }

    override fun postVisitEttersendinger() {
        ettersending = false
    }

    override fun visit(
        innsendingId: UUID,
        innsending: Innsending.InnsendingType,
        tilstand: Innsending.Tilstand.Type,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        vedlegg: List<Innsending.Dokument>
    ) {
        val innsendingData = InnsendingData(
            innsendingId,
            innsending,
            tilstand,
            innsendt,
            journalpost,
            hovedDokument,
            vedlegg
        )
        if (ettersending) {
            ettersendinger.add(innsendingData)
        } else {
            this.innsending = innsendingData
        }
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }

    override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg
    }
}
