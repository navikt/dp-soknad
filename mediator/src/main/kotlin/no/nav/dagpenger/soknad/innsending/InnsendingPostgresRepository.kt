package no.nav.dagpenger.soknad.innsending

import kotliquery.Query
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.Dokument.Dokumentvariant
import no.nav.dagpenger.soknad.InnsendingVisitor
import no.nav.dagpenger.soknad.db.DBUtils.norskZonedDateTime
import no.nav.dagpenger.soknad.db.DataConstraintException
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

internal class InnsendingPostgresRepository(private val ds: DataSource) : InnsendingRepository {

    override fun hent(innsendingId: UUID): Innsending? {
        return using(sessionOf(ds)) { session ->
            // todo fix me
            val metadata = null
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT *
                    FROM innsending_v1
                    WHERE innsending_uuid = :innsending_uuid 
                    """.trimIndent(),
                    paramMap = mapOf(
                        "innsending_uuid" to innsendingId
                    )
                ).map { row ->
                    val dialogId = row.uuid("soknad_uuid")
                    val dokumenter = session.hentDokumenter(innsendingId)
                    Innsending.rehydrer(
                        innsendingId = row.uuid("innsending_uuid"),
                        type = Innsending.InnsendingType.valueOf(row.string("innsendingtype")),
                        ident = session.hentIdent(dialogId),
                        søknadId = dialogId,
                        innsendt = row.norskZonedDateTime("innsendt"),
                        journalpostId = row.stringOrNull("journalpost_id"),
                        tilstandsType = Innsending.TilstandType.valueOf(row.string("tilstand")),
                        hovedDokument = dokumenter.hovedDokument,
                        dokumenter = dokumenter.dokumenter, // todo fixme
                        metadata = session.hentMetadata(innsendingId)
                    )
                }.asSingle
            )
        }
    }

    override fun lagre(innsending: Innsending) {
        using(sessionOf(ds)) { session ->
            session.transaction { tx ->
                val innsendingPersistenceVisitor = InnsendingPersistenceVisitor(innsending = innsending)

                // 1. Lagre innsenindg
                tx.run(innsendingPersistenceVisitor.innsendingQuery.asUpdate)
                // 2. Lagre dokumenter
                innsendingPersistenceVisitor.batchPreparedStatements().forEach {
                    tx.batchPreparedNamedStatement(it.statement, it.params)
                }
                // 3. Lagre hoveddokument, metadata
                innsendingPersistenceVisitor.queries().forEach { query ->
                    tx.run(query.asUpdate)
                }
            }
        }
    }

    override fun finnFor(søknadsId: UUID): List<Innsending> {
        TODO("Not yet implemented")
    }

    private fun Session.hentIdent(dialogId: UUID): String {
        return this.run(
            queryOf(
                statement = """SELECT person_ident FROM soknad_v1 WHERE uuid = :uuid""",
                paramMap = mapOf(
                    "uuid" to dialogId
                )
            ).map { row -> row.string("person_ident") }.asSingle
        ) ?: throw DataConstraintException("Fant ikke ident for dialogId: $dialogId")
    }

    private fun Session.hentDokumenter(innsendingId: UUID): Dokumenter {
        return this.run(
            queryOf( //language=PostgreSQL
                "SELECT * FROM dokument_v1 WHERE innsending_uuid = :innsendingId",
                mapOf("innsendingId" to innsendingId)
            ).map { row ->
                val dokumentUUID = row.uuid("dokument_uuid")
                val dokument = Innsending.Dokument(
                    uuid = dokumentUUID,
                    kravId = row.stringOrNull("kravid"),
                    skjemakode = row.string("brevkode"),
                    varianter = hentVarianter(dokumentUUID)
                )
                dokument
            }.asList
        ).let { Dokumenter(it, this.getHovedDokumentUUID(innsendingId)) }
    }

    private fun Session.getHovedDokumentUUID(innsendingId: UUID): UUID? = this.run(
        queryOf( //language=PostgreSQL
            "SELECT dokument_uuid FROM hoveddokument_v1 WHERE innsending_uuid = :innsendingId",
            mapOf("innsendingId" to innsendingId)
        ).map { row ->
            row.uuidOrNull("dokument_uuid")
        }.asSingle
    )

    private fun Session.hentVarianter(dokumentUuid: UUID) = run(
        queryOf( //language=PostgreSQL
            "SELECT * FROM dokumentvariant_v1 WHERE dokument_uuid = :dokument_uuid",
            mapOf("dokument_uuid" to dokumentUuid)
        ).map { row ->
            Dokumentvariant(
                row.uuid("dokumentvariant_uuid"),
                row.string("filnavn"),
                row.string("urn"),
                row.string("variant"),
                row.string("type")
            )
        }.asList
    )

    private fun Session.hentMetadata(innsendingId: UUID): Innsending.Metadata? = run(
        queryOf( //language=PostgreSQL
            "SELECT * FROM metadata WHERE innsending_uuid = :innsending_uuid",
            mapOf("innsending_uuid" to innsendingId)
        ).map { row ->
            row.stringOrNull("skjemakode")?.let { skjemakode ->
                Innsending.Metadata(skjemakode)
            }
        }.asSingle
    )
}

private class Dokumenter(private val alleDokumenter: List<Innsending.Dokument>, hovedDokumentId: UUID?) :
    List<Innsending.Dokument> by alleDokumenter {
    val hovedDokument: Innsending.Dokument? = this.alleDokumenter.firstOrNull { it.uuid == hovedDokumentId }
    val dokumenter: List<Innsending.Dokument> = this.alleDokumenter.filterNot { it.uuid == hovedDokumentId }
}

private data class BatchPreparedStatement(val statement: String, val params: List<Map<String, Any?>>)
private class InnsendingPersistenceVisitor(innsending: Innsending) : InnsendingVisitor {

    lateinit var innsendingQuery: Query
    private val queries: MutableList<Query> = mutableListOf()
    private val batchPreparedStatements: MutableList<BatchPreparedStatement> = mutableListOf()

    init {
        innsending.accept(this)
    }

    fun queries(): List<Query> = queries
    fun batchPreparedStatements(): MutableList<BatchPreparedStatement> = batchPreparedStatements

    override fun visit(
        innsendingId: UUID,
        søknadId: UUID,
        ident: String,
        innsendingType: Innsending.InnsendingType,
        tilstand: Innsending.TilstandType,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        dokumenter: List<Innsending.Dokument>,
        metadata: Innsending.Metadata?,
    ) {
        innsendingQuery = queryOf(
            //language=PostgreSQL
            statement = """
                INSERT INTO innsending_v1(innsending_uuid, soknad_uuid, innsendt, journalpost_id, innsendingtype, tilstand) 
                VALUES(:innsending_uuid, :soknad_uuid, :innsendt, :journalpost_id, :innsendingtype, :tilstand)
                ON CONFLICT (innsending_uuid) DO UPDATE SET innsendt = :innsendt, 
                                                            journalpost_id = :journalpost_id,
                                                            innsendingtype = :innsendingtype,
                                                            tilstand = :tilstand
                """.trimMargin(),
            paramMap = mutableMapOf(
                "innsending_uuid" to innsendingId,
                "soknad_uuid" to søknadId,
                "innsendt" to innsendt,
                "journalpost_id" to journalpost,
                "tilstand" to tilstand.name,
                "innsendingtype" to innsendingType.name
            )
        )

        val dokumentParams: MutableList<Map<String, Any?>> = mutableListOf()
        val dokumentvariantParams: MutableList<Map<String, Any?>> = mutableListOf()

        dokumenter.toMutableList().apply {
            hovedDokument?.let { add(it) }
        }.forEach { dokument ->
            dokumentParams.add(
                mapOf(
                    "dokument_uuid" to dokument.uuid,
                    "innsending_uuid" to innsendingId,
                    "brevkode" to dokument.skjemakode,
                    "kravId" to dokument.kravId
                )
            )

            dokument.varianter.forEach { variant ->
                dokumentvariantParams.add(
                    mapOf(
                        "dokumentvariant_uuid" to variant.uuid,
                        "dokument_uuid" to dokument.uuid,
                        "filnavn" to variant.filnavn,
                        "urn" to variant.urn,
                        "variant" to variant.variant,
                        "type" to variant.type
                    )
                )
            }
        }

        batchPreparedStatements.add(
            BatchPreparedStatement(
                //language=PostgreSQL
                statement = """ INSERT INTO dokument_v1 (dokument_uuid, innsending_uuid, brevkode, kravid)
                    VALUES (:dokument_uuid, :innsending_uuid, :brevkode, :kravId)
                    ON CONFLICT (dokument_uuid) DO UPDATE SET brevkode = :brevkode, kravid = :kravId""",
                params = dokumentParams
            )
        )

        batchPreparedStatements.add(
            BatchPreparedStatement(
                //language=PostgreSQL
                statement = """
                    INSERT INTO dokumentvariant_v1 (dokumentvariant_uuid, dokument_uuid, filnavn, urn, variant, type)
                        VALUES (:dokumentvariant_uuid, :dokument_uuid, :filnavn, :urn, :variant, :type)
                        ON CONFLICT (dokumentvariant_uuid) DO UPDATE SET filnavn = :filnavn, 
                                                                         urn = :urn,
                                                                         variant = :variant,
                                                                         type = :type
                """.trimIndent(),
                params = dokumentvariantParams
            )
        )

        hovedDokument?.let { hovedDokument ->
            queries.add(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                        INSERT INTO hoveddokument_v1(innsending_uuid, dokument_uuid) 
                        VALUES (:innsending_uuid, :dokument_uuid)
                        ON CONFLICT (innsending_uuid) DO UPDATE SET dokument_uuid = :dokumentvariant_uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "innsending_uuid" to innsendingId,
                        "dokument_uuid" to hovedDokument.uuid
                    )
                )
            )
        }

        metadata?.let { metadata ->
            queries.add(
                queryOf( //language=PostgreSQL
                    """
                    INSERT INTO metadata (innsending_uuid, skjemakode)
                    VALUES (:innsending_uuid, :skjemakode)
                    ON CONFLICT (innsending_uuid) DO UPDATE SET skjemakode=:skjemakode
                    """.trimIndent(),
                    mapOf(
                        "innsending_uuid" to innsendingId,
                        "skjemakode" to metadata.skjemakode
                    )
                )
            )
        }
    }
}
