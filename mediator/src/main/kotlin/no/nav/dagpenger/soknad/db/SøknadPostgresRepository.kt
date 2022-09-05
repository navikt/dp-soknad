package no.nav.dagpenger.soknad.db

import com.zaxxer.hikari.HikariDataSource
import de.slub.urn.URN
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.hentDokumentKrav
import no.nav.dagpenger.soknad.livssyklus.insertKravData
import no.nav.dagpenger.soknad.serder.PersonDTO
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.Companion.toKravdata
import java.time.ZonedDateTime
import java.util.UUID

class SøknadPostgresRepository(private val dataSource: HikariDataSource) :
    SøknadRepository {
    override fun hent(søknadId: UUID, ident: String): Søknad? {
        TODO("not implemented")
    }
    override fun lagre(søknad: Søknad): Boolean {
        TODO("not implemented")
    }

    override fun hentDokumentkravFor(søknadId: UUID, ident: String): Dokumentkrav {
        sjekkTilgang(ident, søknadId)
        return using(sessionOf(dataSource)) { session ->
            Dokumentkrav.rehydrer(session.hentDokumentKrav(søknadId).map { it.rehydrer() }.toSet())
        }
    }

    override fun harTilgang(ident: String, søknadId: UUID): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                // language=PostgreSQL
                queryOf(
                    """
                       SELECT 1 from soknad_v1 WHERE person_ident = :ident AND uuid = :uuid
                    """.trimIndent(),
                    mapOf(
                        "uuid" to søknadId.toString(),
                        "ident" to ident
                    )
                ).map {
                    it.int(1) == 1
                }.asSingle
            ) ?: false
        }

    override fun oppdaterDokumentkrav(søknadId: UUID, ident: String, dokumentkrav: Dokumentkrav) {
        sjekkTilgang(ident, søknadId)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                dokumentkrav.toDokumentKravData().kravData.insertKravData(søknadId, transactionalSession)
            }
        }
    }

    override fun slettDokumentasjonkravFil(søknadId: UUID, ident: String, kravId: String, urn: URN) {
        sjekkTilgang(ident, søknadId)
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    statement = """
                       DELETE FROM dokumentkrav_filer_v1 
                       WHERE soknad_uuid = :soknadId
                       AND faktum_id = :kravId 
                       AND urn = :urn 
                    """.trimIndent(),
                    paramMap = mapOf(
                        "soknadId" to søknadId.toString(),
                        "kravId" to kravId,
                        "urn" to urn.toString()
                    )
                ).asUpdate
            )
        }
    }

    private fun sjekkTilgang(ident: String, søknadId: UUID) =
        if (!harTilgang(ident, søknadId)) throw IkkeTilgangExeption("Har ikke tilgang til søknadId $søknadId") else Unit
}

internal class SøknadPersistenceVisitor(søknad: Søknad) : SøknadVisitor {

    lateinit var søknadDTO: PersonDTO.SøknadDTO

    init {
        søknad.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        søknadObserver: SøknadObserver,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        søknadDTO = PersonDTO.SøknadDTO(
            søknadsId = søknadId,
            tilstandType = when (tilstand.tilstandType) {
                Søknad.Tilstand.Type.UnderOpprettelse -> PersonDTO.SøknadDTO.TilstandDTO.UnderOpprettelse
                Søknad.Tilstand.Type.Påbegynt -> PersonDTO.SøknadDTO.TilstandDTO.Påbegynt
                Søknad.Tilstand.Type.AvventerArkiverbarSøknad -> PersonDTO.SøknadDTO.TilstandDTO.AvventerArkiverbarSøknad
                Søknad.Tilstand.Type.AvventerMidlertidligJournalføring -> PersonDTO.SøknadDTO.TilstandDTO.AvventerMidlertidligJournalføring
                Søknad.Tilstand.Type.AvventerJournalføring -> PersonDTO.SøknadDTO.TilstandDTO.AvventerJournalføring
                Søknad.Tilstand.Type.Journalført -> PersonDTO.SøknadDTO.TilstandDTO.Journalført
                Søknad.Tilstand.Type.Slettet -> PersonDTO.SøknadDTO.TilstandDTO.Slettet
            },
            dokumenter = dokument.toDokumentData(),
            journalpostId = journalpostId,
            innsendtTidspunkt = innsendtTidspunkt,
            språkDTO = PersonDTO.SøknadDTO.SpråkDTO(språk.verdi),
            dokumentkrav = dokumentkrav.toDokumentKravData(),
            sistEndretAvBruker = sistEndretAvBruker
        )
    }

    private fun Søknad.Dokument?.toDokumentData(): List<PersonDTO.SøknadDTO.DokumentDTO> {
        return this?.let { it.varianter.map { v -> PersonDTO.SøknadDTO.DokumentDTO(urn = v.urn) } }
            ?: emptyList()
    }
}

internal fun Dokumentkrav.toDokumentKravData(): PersonDTO.SøknadDTO.DokumentkravDTO {
    return PersonDTO.SøknadDTO.DokumentkravDTO(
        kravData = (this.aktiveDokumentKrav() + this.inAktiveDokumentKrav()).map { krav -> krav.toKravdata() }
            .toSet()
    )
}
