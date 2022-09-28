package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import de.slub.urn.URN
import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.Dokument.Dokumentvariant
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.livssyklus.PåbegyntSøknad
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.serder.AktivitetsloggDTO
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.InnsendingDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO.KravTilstandDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.postgresql.util.PGobject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class SøknadPostgresRepository(private val dataSource: DataSource) :
    SøknadRepository {
    override fun hentEier(søknadId: UUID): String? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT person_ident
                    FROM  soknad_v1
                    WHERE uuid = :uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId
                    )
                ).map { it.stringOrNull("person_ident") }.asSingle
            )
        }
    }

    override fun hent(søknadId: UUID): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT uuid, tilstand, spraak, sist_endret_av_bruker, person_ident
                    FROM  soknad_v1
                    WHERE uuid = :uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId
                    )
                ).map(rowToSøknadDTO(session)).asSingle
            )?.rehydrer()
        }
    }

    private fun Row.norskZonedDateTime(columnLabel: String): ZonedDateTime =
        this.zonedDateTime(columnLabel).withZoneSameInstant(ZoneId.of("Europe/Oslo"))

    private fun Session.hentInnsending(søknadId: UUID): InnsendingDTO? {
        return this.run(
            queryOf(
                //language=PostgreSQL
                statement = """   
                       SELECT *
                       FROM innsending_v1
                       WHERE soknad_uuid = :soknad_uuid AND innsendingtype = 'NY_DIALOG'
                """.trimMargin(),
                paramMap = mapOf(
                    "soknad_uuid" to søknadId
                )
            ).map(rowToInnsendingDTO(this)).asSingle
        )
    }

    private fun Session.hentEttersendinger(innsendingId: UUID): List<InnsendingDTO> {
        return run(
            queryOf(
                //language=PostgreSQL
                statement = """   
                   SELECT innsending_v1.*
                   FROM innsending_v1, ettersending_v1
                   WHERE ettersending_v1.innsending_uuid = :innsending_uuid AND innsending_v1.innsending_uuid = ettersending_v1.ettersending_uuid 
                """.trimMargin(),
                paramMap = mapOf(
                    "innsending_uuid" to innsendingId
                )
            ).map(rowToInnsendingDTO(this)).asList
        )
    }

    private fun rowToInnsendingDTO(session: Session) = { row: Row ->
        val innsendingId = row.uuid("innsending_uuid")
        val dokumenter: InnsendingDTO.DokumenterDTO = session.hentDokumenter(innsendingId)
        val type = InnsendingDTO.InnsendingTypeDTO.rehydrer(row.string("innsendingtype"))
        val ettersendinger = when (type) {
            InnsendingDTO.InnsendingTypeDTO.NY_DIALOG -> session.hentEttersendinger(innsendingId)
            InnsendingDTO.InnsendingTypeDTO.ETTERSENDING_TIL_DIALOG -> emptyList()
        }
        InnsendingDTO(
            innsendingId = innsendingId,
            type = type,
            innsendt = row.norskZonedDateTime("innsendt"),
            journalpostId = row.stringOrNull("journalpost_id"),
            tilstand = InnsendingDTO.TilstandDTO.rehydrer(row.string("tilstand")),
            hovedDokument = dokumenter.hovedDokument,
            dokumenter = dokumenter.dokumenter,
            ettersendinger = ettersendinger,
            brevkode = row.stringOrNull("skjemakode")
                ?.let { skjemakode -> Innsending.Brevkode(skjemakode) }
        )
    }

    override fun hentSøknader(ident: String): Set<Søknad> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                            SELECT uuid, tilstand, spraak, sist_endret_av_bruker,person_ident
                            FROM  soknad_v1
                            WHERE person_ident = :ident
                    """.trimIndent(),
                    paramMap = mapOf(
                        "ident" to ident
                    )
                    // todo
                ).map(rowToSøknadDTO(session)).asList
            ).map { it.rehydrer() }.toSet()
        }
    }

    private fun rowToSøknadDTO(session: Session) =
        { row: Row ->
            val søknadsId = UUID.fromString(row.string("uuid"))
            SøknadDTO(
                søknadsId = søknadsId,
                ident = row.string("person_ident"),
                tilstandType = SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
                språkDTO = SøknadDTO.SpråkDTO(row.string("spraak")),
                dokumentkrav = SøknadDTO.DokumentkravDTO(
                    session.hentDokumentKrav(søknadsId)
                ),
                sistEndretAvBruker = row.zonedDateTimeOrNull("sist_endret_av_bruker")
                    ?.withZoneSameInstant(ZoneId.of("Europe/Oslo")),
                innsendingDTO = session.hentInnsending(søknadsId),
                aktivitetslogg = session.hentAktivitetslogg(søknadsId)
            )
        }

    override fun lagre(søknad: Søknad) {
        val visitor = SøknadPersistenceVisitor(søknad)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                visitor.queries().forEach {
                    transactionalSession.run(it.asUpdate)
                }
            }
        }
    }

    override fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad? {
        TODO("Not yet implemented")
    }

    internal class PersistentSøkerOppgave(private val søknad: JsonNode) : SøkerOppgave {
        override fun søknadUUID(): UUID = UUID.fromString(søknad[SøkerOppgave.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = søknad[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
        override fun opprettet(): LocalDateTime = søknad[SøkerOppgave.Keys.OPPRETTET].asLocalDateTime()
        override fun ferdig(): Boolean = søknad[SøkerOppgave.Keys.FERDIG].asBoolean()

        override fun asFrontendformat(): JsonNode {
            søknad as ObjectNode
            val kopiAvSøknad = søknad.deepCopy()
            kopiAvSøknad.remove(SøkerOppgave.Keys.FØDSELSNUMMER)
            return kopiAvSøknad
        }

        override fun asJson(): String = søknad.toString()
        override fun sannsynliggjøringer(): Set<Sannsynliggjøring> {
            TODO("not implemented")
        }
    }
}

private fun Session.hentDokumenter(innsendingId: UUID): InnsendingDTO.DokumenterDTO {
    val dokumenter = InnsendingDTO.DokumenterDTO()
    this.run(
        queryOf(
            //language=PostgreSQL
            """WITH hoveddokument AS (SELECT dokument_uuid
                       FROM hoveddokument_v1
                       WHERE hoveddokument_v1.innsending_uuid = :innsendingId)
            SELECT dokument_v1.*, (dokument_v1.dokument_uuid = hoveddokument.dokument_uuid) AS er_hoveddokument
            FROM dokument_v1,
                 hoveddokument
            WHERE dokument_v1.innsending_uuid = :innsendingId 
            """.trimIndent(),
            mapOf(
                "innsendingId" to innsendingId
            )
        ).map { row ->
            val dokument_uuid = row.uuid("dokument_uuid")
            val varianter: List<Dokumentvariant> = this@hentDokumenter.hentVarianter(dokument_uuid)
            val dokument = Innsending.Dokument(
                dokument_uuid,
                row.string("brevkode"),
                varianter
            )
            when (row.boolean("er_hoveddokument")) {
                true -> dokumenter.hovedDokument = dokument
                false -> dokumenter.dokumenter.add(dokument)
            }
        }.asSingle
    )
    return dokumenter
}

private fun Session.hentVarianter(dokumentUuid: UUID) = run(
    queryOf(
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

internal fun Session.hentAktivitetslogg(søknadId: UUID): AktivitetsloggDTO? = run(
    queryOf(
        //language=PostgreSQL
        """
        SELECT a.data AS aktivitetslogg
        FROM aktivitetslogg_v1 AS a
        WHERE a.soknad_uuid = :soknadId
        """.trimIndent(),
        mapOf(
            "soknadId" to søknadId
        )
    ).map { row ->
        row.binaryStream("aktivitetslogg").aktivitetslogg()
    }.asSingle
)

private class SøknadPersistenceVisitor(søknad: Søknad) : SøknadVisitor {
    private lateinit var aktivEttersending: UUID
    private var ettersending: Boolean = false
    private val ettersendinger: MutableMap<UUID, MutableSet<UUID>> = mutableMapOf()
    private lateinit var søknadId: UUID
    private val queries = mutableListOf<Query>()

    init {
        søknad.accept(this)
    }

    fun queries() = queries

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.søknadId = søknadId
        queries.add(
            queryOf(
                // language=PostgreSQL
                "INSERT INTO person_v1 (ident) VALUES (:ident) ON CONFLICT DO NOTHING",
                mapOf("ident" to ident)
            )
        )
        queries.add(
            queryOf(
                // language=PostgreSQL
                """
                INSERT INTO soknad_v1(uuid, person_ident, tilstand, spraak, sist_endret_av_bruker)
                VALUES (:uuid, :person_ident, :tilstand, :spraak, :sistEndretAvBruker)
                ON CONFLICT(uuid) DO UPDATE SET tilstand=:tilstand,
                                                sist_endret_av_bruker = :sistEndretAvBruker
                """.trimIndent(),
                mapOf(
                    "uuid" to søknadId,
                    "person_ident" to ident,
                    "tilstand" to tilstand.tilstandType.name,
                    "spraak" to språk.verdi.toLanguageTag(),
                    "sistEndretAvBruker" to sistEndretAvBruker
                )
            )
        )
    }

    override fun visitKrav(krav: Krav) {
        queries.add(
            queryOf(
                // language=PostgreSQL
                """
                INSERT INTO dokumentkrav_v1(faktum_id, beskrivende_id, soknad_uuid, faktum, sannsynliggjoer, tilstand, valg,
                                            begrunnelse)
                VALUES (:faktum_id, :beskrivende_id, :soknad_uuid, :faktum, :sannsynliggjoer, :tilstand, :valg, :begrunnelse)
                ON CONFLICT (faktum_id, soknad_uuid) DO UPDATE SET beskrivende_id  = :beskrivende_id,
                                                                   faktum          = :faktum,
                                                                   sannsynliggjoer = :sannsynliggjoer,
                                                                   tilstand        = :tilstand,
                                                                   valg            = :valg,
                                                                   begrunnelse     = :begrunnelse
                """.trimIndent(),
                mapOf<String, Any?>(
                    "faktum_id" to krav.id,
                    "soknad_uuid" to søknadId,
                    "beskrivende_id" to krav.beskrivendeId,
                    "faktum" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(krav.sannsynliggjøring.faktum().originalJson())
                    },
                    "sannsynliggjoer" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(
                            krav.sannsynliggjøring.sannsynliggjør().map { it.originalJson() }
                        )
                    },
                    "tilstand" to krav.tilstand.name,
                    "valg" to krav.svar.valg.name,
                    "begrunnelse" to krav.svar.begrunnelse
                )
            )
        )
        // fjerne alle filer for søknaden. Modellen har fasit på hvor mange som legges til i neste block
        queries.add(
            queryOf(
                // language=PostgreSQL
                "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid",
                mapOf("uuid" to søknadId)
            )
        )

        queries.addAll(
            krav.svar.filer.map { fil ->
                queryOf(
                    // language=PostgreSQL
                    statement = """
                    INSERT INTO dokumentkrav_filer_v1(faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt)
                    VALUES (:faktum_id, :soknad_uuid, :filnavn, :storrelse, :urn, :tidspunkt)
                    ON CONFLICT (faktum_id, soknad_uuid, urn) DO UPDATE SET filnavn = :filnavn, 
                                                                            storrelse = :storrelse, 
                                                                            tidspunkt = :tidspunkt
                    """.trimIndent(),
                    mapOf(
                        "faktum_id" to krav.id,
                        "soknad_uuid" to søknadId,
                        "filnavn" to fil.filnavn,
                        "storrelse" to fil.storrelse,
                        "urn" to fil.urn.toString(),
                        "tidspunkt" to fil.tidspunkt
                    )
                )
            }
        )
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = "INSERT INTO aktivitetslogg_v1 (soknad_uuid, data) VALUES (:uuid, :data) ON CONFLICT (soknad_uuid) DO UPDATE SET data = :data",
                paramMap = mapOf(
                    "uuid" to søknadId,
                    "data" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(aktivitetslogg.toMap())
                    }
                )
            )
        )
    }

    override fun visit(
        innsendingId: UUID,
        innsending: Innsending.InnsendingType,
        tilstand: Innsending.TilstandType,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        dokumenter: List<Innsending.Dokument>,
        brevkode: Innsending.Brevkode?
    ) {
        if (ettersending) {
            ettersendinger.getOrPut(aktivEttersending) {
                mutableSetOf()
            }.add(innsendingId)
        } else {
            aktivEttersending = innsendingId
        }
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO innsending_v1(innsending_uuid, soknad_uuid, innsendt, journalpost_id, innsendingtype, tilstand, skjemakode)
                    VALUES (:innsending_uuid, :soknad_uuid, :innsendt, :journalpost_id, :innsendingtype, :tilstand, :skjemakode)
                    ON CONFLICT (innsending_uuid) DO UPDATE SET innsendt = :innsendt, 
                                                                journalpost_id = :journalpost_id,
                                                                innsendingtype = :innsendingtype,
                                                                tilstand = :tilstand,
                                                                skjemakode = :skjemakode

                """.trimIndent(),
                paramMap = mapOf(
                    "innsending_uuid" to innsendingId,
                    "soknad_uuid" to søknadId,
                    "innsendt" to innsendt,
                    "journalpost_id" to journalpost,
                    "innsendingtype" to innsending.name,
                    "tilstand" to tilstand.name,
                    "skjemakode" to brevkode?.skjemakode
                )
            )
        )
        dokumenter.toMutableList().apply {
            hovedDokument?.let { add(it) }
        }.forEach { dokument ->
            queries.add(
                queryOf(
                    //language=PostgreSQL
                    """
                        INSERT INTO dokument_v1 (dokument_uuid, innsending_uuid, brevkode)
                        VALUES (:dokument_uuid, :innsending_uuid, :brevkode)
                        ON CONFLICT (dokument_uuid) DO UPDATE SET brevkode = :brevkode
                    """.trimIndent(),
                    mapOf(
                        "dokument_uuid" to dokument.uuid,
                        "innsending_uuid" to innsendingId,
                        "brevkode" to dokument.brevkode
                    )
                )
            )
            dokument.varianter.forEach { variant ->
                queries.add(
                    queryOf(
                        //language=PostgreSQL
                        """
                            INSERT INTO dokumentvariant_v1 (dokumentvariant_uuid, dokument_uuid, filnavn, urn, variant, type)
                            VALUES (:dokumentvariant_uuid, :dokument_uuid, :filnavn, :urn, :variant, :type)
                            ON CONFLICT (dokumentvariant_uuid) DO UPDATE SET filnavn = :filnavn, 
                                                                             urn = :urn,
                                                                             variant = :variant,
                                                                             type = :type
                        """.trimIndent(),
                        mapOf(
                            "dokumentvariant_uuid" to variant.uuid,
                            "dokument_uuid" to dokument.uuid,
                            "filnavn" to variant.filnavn,
                            "urn" to variant.urn,
                            "variant" to variant.variant,
                            "type" to variant.type
                        )
                    )
                )
            }
        }
        if (hovedDokument != null) {
            queries.add(
                queryOf(
                    //language=PostgreSQL
                    """
                        INSERT INTO hoveddokument_v1(innsending_uuid, dokument_uuid)
                        VALUES (:innsending_uuid, :dokumentvariant_uuid)
                        ON CONFLICT (innsending_uuid) DO UPDATE SET dokument_uuid = :dokumentvariant_uuid
                    """.trimIndent(),
                    mapOf(
                        "innsending_uuid" to innsendingId,
                        "dokumentvariant_uuid" to hovedDokument.uuid
                    )
                )
            )
        }
    }

    override fun preVisitEttersendinger() {
        ettersending = true
    }

    override fun postVisitEttersendinger() {
        ettersending = false
        ettersendinger.forEach { (innsending, ettersendinger) ->
            ettersendinger.forEach { ettersending ->
                queries.add(
                    queryOf(
                        //language=PostgreSQL
                        "INSERT INTO ettersending_v1 (innsending_uuid, ettersending_uuid) VALUES (:innsending_uuid, :ettersending_uuid) ON CONFLICT DO NOTHING",
                        mapOf(
                            "innsending_uuid" to innsending,
                            "ettersending_uuid" to ettersending
                        )
                    )
                )
            }
        }
    }
}

private fun Session.hentDokumentKrav(søknadsId: UUID): Set<KravDTO> =
    this.run(
        queryOf(
            // language=PostgreSQL
            """
                  SELECT faktum_id, beskrivende_id, faktum, sannsynliggjoer, tilstand, valg, begrunnelse 
                  FROM dokumentkrav_v1 
                  WHERE soknad_uuid = :soknad_uuid
            """.trimIndent(),
            mapOf(
                "soknad_uuid" to søknadsId
            )
        ).map { row ->
            val faktumId = row.string("faktum_id")
            KravDTO(
                id = faktumId,
                beskrivendeId = row.string("beskrivende_id"),
                sannsynliggjøring = SannsynliggjøringDTO(
                    id = faktumId,
                    faktum = objectMapper.readTree(row.binaryStream("faktum")),
                    sannsynliggjør = objectMapper.readTree(row.binaryStream("sannsynliggjoer")).toSet()
                ),
                svar = SvarDTO(
                    begrunnelse = row.stringOrNull("begrunnelse"),
                    filer = hentFiler(søknadsId, faktumId),
                    valg = SvarValgDTO.valueOf(row.string("valg"))
                ),
                tilstand = row.string("tilstand")
                    .let { KravTilstandDTO.valueOf(it) }
            )
        }.asList
    ).toSet()

private fun Session.hentFiler(
    søknadsId: UUID,
    faktumId: String
): Set<KravDTO.FilDTO> {
    return this.run(
        queryOf(
            //language=PostgreSQL
            statement = """
                  SELECT faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt 
                  FROM dokumentkrav_filer_v1 
                  WHERE soknad_uuid = :soknad_uuid
                  AND faktum_id = :faktum_id 
            """.trimIndent(),
            paramMap = mapOf(
                "soknad_uuid" to søknadsId,
                "faktum_id" to faktumId
            )
        ).map { row ->
            KravDTO.FilDTO(
                filnavn = row.string("filnavn"),
                urn = URN.rfc8141().parse(row.string("urn")),
                storrelse = row.long("storrelse"),
                tidspunkt = row.instant("tidspunkt").atZone(ZoneId.of("Europe/Oslo"))
            )
        }.asList
    ).toSet()
}
