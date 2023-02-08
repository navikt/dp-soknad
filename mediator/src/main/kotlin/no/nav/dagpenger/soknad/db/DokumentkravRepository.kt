package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import javax.sql.DataSource

interface DokumentkravRepository {
    fun håndter(hendelse: LeggTilFil)
    fun håndter(hendelse: SlettFil)
}

internal class PostgresDokumentkravRepository(private val datasource: DataSource) : DokumentkravRepository {

    override fun håndter(hendelse: LeggTilFil) {
        val fil = hendelse.fil
        using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    statement = """
                    INSERT INTO dokumentkrav_filer_v1(faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt, bundlet)
                    VALUES (:faktum_id, :soknad_uuid, :filnavn, :storrelse, :urn, :tidspunkt,:bundlet)
                    ON CONFLICT (faktum_id, soknad_uuid, urn) DO UPDATE SET filnavn = :filnavn, 
                                                                            storrelse = :storrelse, 
                                                                            tidspunkt = :tidspunkt,
                                                                            bundlet = :bundlet
                    """.trimIndent(),
                    mapOf(
                        "faktum_id" to hendelse.kravId,
                        "soknad_uuid" to hendelse.søknadID,
                        "filnavn" to fil.filnavn,
                        "storrelse" to fil.storrelse,
                        "urn" to fil.urn.toString(),
                        "tidspunkt" to fil.tidspunkt,
                        "bundlet" to fil.bundlet
                    )
                ).asExecute
            )
        }
    }

    override fun håndter(hendelse: SlettFil) {
        using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid AND faktum_id = :faktum_id AND urn = :urn",
                    mapOf(
                        "uuid" to hendelse.søknadID,
                        "faktum_id" to hendelse.kravId,
                        "urn" to hendelse.urn.toString()
                    )
                ).asUpdate
            )
        }
    }
}
