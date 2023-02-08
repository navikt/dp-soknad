package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Krav
import java.util.UUID
import javax.sql.DataSource

interface DokumentkravRepository {
    fun slett(søknadsUuid: UUID, kravId: String)
    fun leggTil(søknadsUuid: UUID, kravId: String, fil: Krav.Fil)
}

internal class PostgresDokumentkravRepository(private val datasource: DataSource) : DokumentkravRepository {

    override fun slett(søknadsUuid: UUID, kravId: String) {
        using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid AND faktum_id = :faktum_id",
                    mapOf(
                        "uuid" to søknadsUuid,
                        "faktum_id" to kravId
                    )
                ).asUpdate
            )
        }
    }

    override fun leggTil(søknadsUuid: UUID, kravId: String, fil: Krav.Fil) {
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
                        "faktum_id" to kravId,
                        "soknad_uuid" to søknadsUuid,
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
}
