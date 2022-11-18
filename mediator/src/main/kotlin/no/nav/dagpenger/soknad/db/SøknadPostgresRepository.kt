package no.nav.dagpenger.soknad.db

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
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.serder.AktivitetsloggDTO
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.InnsendingDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO.KravTilstandDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.ProsessversjonDTO
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.properties.Delegates

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
                    SELECT uuid, tilstand, spraak, sist_endret_av_bruker, opprettet, person_ident, versjon
                    FROM  soknad_v1
                    WHERE uuid = :uuid AND tilstand != 'Slettet'
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId
                    )
                ).map(rowToSøknadDTO(session)).asSingle
            )?.rehydrer()
        }
    }

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
        val metadata = session.hentMetadata(innsendingId)
        InnsendingDTO(
            innsendingId = innsendingId,
            type = type,
            innsendt = row.norskZonedDateTime("innsendt"),
            journalpostId = row.stringOrNull("journalpost_id"),
            tilstand = InnsendingDTO.TilstandDTO.rehydrer(row.string("tilstand")),
            hovedDokument = dokumenter.hovedDokument,
            dokumenter = dokumenter.dokumenter,
            ettersendinger = ettersendinger,
            metadata = metadata
        )
    }

    override fun hentSøknader(ident: String): Set<Søknad> = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf( //language=PostgreSQL
                """
                SELECT uuid, tilstand, spraak, sist_endret_av_bruker, opprettet, person_ident, versjon
                FROM  soknad_v1
                WHERE person_ident = :ident AND tilstand != 'Slettet' ORDER by sist_endret_av_bruker DESC 
                """.trimIndent(),
                mapOf(
                    "ident" to ident
                )
            ).map(rowToSøknadDTO(session)).asList
        ).map { it.rehydrer() }.toSet()
    }

    private fun rowToSøknadDTO(session: Session): (Row) -> SøknadDTO {
        return { row: Row ->
            val søknadsId = UUID.fromString(row.string("uuid"))
            SøknadDTO(
                søknadsId = søknadsId,
                ident = row.string("person_ident"),
                tilstandType = SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
                språkDTO = SøknadDTO.SpråkDTO(row.string("spraak")),
                dokumentkrav = SøknadDTO.DokumentkravDTO(
                    session.hentDokumentKrav(søknadsId)
                ),
                sistEndretAvBruker = row.zonedDateTime("sist_endret_av_bruker").withZoneSameInstant(tidssone),
                innsendingDTO = session.hentInnsending(søknadsId),
                aktivitetslogg = session.hentAktivitetslogg(søknadsId),
                opprettet = row.norskZonedDateTime("opprettet"),
                prosessversjon = session.hentProsessversjon(søknadsId),
                data = lazy {
                    SøknadDataPostgresRepository(dataSource).hentSøkerOppgave(søknadsId)
                },
                versjon = row.int("versjon")
            )
        }
    }

    override fun hentPåbegynteSøknader(prosessversjon: Prosessversjon): List<Søknad> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( //language=PostgreSQL
                    """
                    SELECT * 
                    FROM soknad_v1
                             LEFT JOIN soknadmal mal ON soknad_v1.soknadmal = mal.id
                    WHERE tilstand = :tilstand
                      AND ((mal.prosessnavn = :prosessnavn AND :prosessversjon > mal.prosessversjon) OR mal IS NULL) 
                    """.trimIndent(),
                    mapOf(
                        "tilstand" to Tilstand.Type.Påbegynt.toString(),
                        "prosessnavn" to prosessversjon.prosessnavn.id,
                        "prosessversjon" to prosessversjon.versjon
                    )
                ).map(rowToSøknadDTO(session)).asList
            ).map { it.rehydrer() }
        }
    }

    override fun opprett(søknadID: UUID, språk: Språk, ident: String) = Søknad(søknadID, språk, ident)

    override fun lagre(søknad: Søknad) {
        val visitor = SøknadPersistenceVisitor(søknad)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                val dbVersjon = transactionalSession.hentVersjon(visitor.søknadId)
                if (dbVersjon != null && dbVersjon != visitor.versjon) {
                    throw SøknadRepository.OptimistiskLåsingException(
                        søknadId = visitor.søknadId,
                        databaseVersjon = dbVersjon,
                        nyVersjon = visitor.versjon
                    )
                }
                visitor.queries().forEach {
                    transactionalSession.run(it.asUpdate)
                }
            }
        }
    }
}

private fun Session.hentVersjon(søknadsId: UUID) = run(
    queryOf( // language=PostgreSQL
        "SELECT versjon FROM soknad_v1 WHERE uuid = :uuid",
        mapOf(
            "uuid" to søknadsId
        )
    ).map {
        it.intOrNull("versjon")
    }.asSingle
)
private fun Session.hentMetadata(innsendingId: UUID): InnsendingDTO.MetadataDTO? = run(
    queryOf( //language=PostgreSQL
        "SELECT * FROM metadata WHERE innsending_uuid = :innsending_uuid",
        mapOf("innsending_uuid" to innsendingId)
    ).map { row ->
        InnsendingDTO.MetadataDTO(
            skjemakode = row.string("skjemakode")
        )
    }.asSingle
)

private fun Session.hentDokumenter(innsendingId: UUID): InnsendingDTO.DokumenterDTO {
    val dokumenter = InnsendingDTO.DokumenterDTO()
    val hovedDokumentUUID = getHovedDokumentUUID(innsendingId)
    this.run(
        queryOf( //language=PostgreSQL
            "SELECT * FROM dokument_v1 WHERE innsending_uuid = :innsendingId",
            mapOf("innsendingId" to innsendingId)
        ).map { row ->
            val dokumentUUID = row.uuid("dokument_uuid")
            val varianter: List<Dokumentvariant> = this@hentDokumenter.hentVarianter(dokumentUUID)
            val dokument = Innsending.Dokument(
                uuid = dokumentUUID,
                kravId = row.stringOrNull("kravid"),
                skjemakode = row.string("brevkode"),
                varianter = varianter
            )
            when (dokumentUUID == hovedDokumentUUID) {
                true -> dokumenter.hovedDokument = dokument
                false -> dokumenter.dokumenter.add(dokument)
            }
        }.asList
    )
    return dokumenter
}

private fun Session.getHovedDokumentUUID(innsendingId: UUID) = this.run(
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

internal fun Session.hentProsessversjon(søknadId: UUID): ProsessversjonDTO? = run(
    queryOf( //language=PostgreSQL
        """
                    SELECT prosessnavn, prosessversjon
                    FROM  soknadmal
                    JOIN soknad_v1 soknad ON soknad.soknadmal = soknadmal.id 
                    WHERE soknad.uuid = :soknadId
        """.trimIndent(),
        mapOf(
            "soknadId" to søknadId
        )
    ).map { row ->
        ProsessversjonDTO(prosessnavn = row.string("prosessnavn"), versjon = row.int("prosessversjon"))
    }.asSingle
)

private class SøknadPersistenceVisitor(søknad: Søknad) : SøknadVisitor {
    private lateinit var aktivEttersending: UUID
    private var ettersending: Boolean = false
    private val ettersendinger: MutableMap<UUID, MutableSet<UUID>> = mutableMapOf()
    lateinit var søknadId: UUID
    var versjon by Delegates.notNull<Int>()
    private val queries = mutableListOf<Query>()

    init {
        søknad.accept(this)
    }

    fun queries() = queries

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        tilstand: Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?,
        versjon: Int
    ) {
        this.søknadId = søknadId
        this.versjon = versjon
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
                   INSERT INTO soknad_v1(uuid, person_ident, tilstand, spraak, opprettet, sist_endret_av_bruker, soknadmal, endret, versjon)
                   VALUES (:uuid, :person_ident, :tilstand, :spraak, :opprettet, :sistEndretAvBruker, 
                        (SELECT id FROM soknadmal WHERE prosessnavn = :prosessnavn AND prosessversjon = :prosessversjon),
                        (NOW() AT TIME ZONE 'utc'::TEXT), :nyVersjon)
                   ON CONFLICT(uuid) DO UPDATE SET tilstand=:tilstand,
                                                sist_endret_av_bruker = :sistEndretAvBruker, 
                                                soknadmal=(SELECT id FROM soknadmal WHERE prosessnavn = :prosessnavn AND prosessversjon = :prosessversjon),
                                                endret = (NOW() AT TIME ZONE 'utc'::TEXT),
                                                versjon = :nyVersjon
                """.trimIndent(),
                mapOf(
                    "uuid" to søknadId,
                    "person_ident" to ident,
                    "tilstand" to tilstand.tilstandType.name,
                    "spraak" to språk.verdi.toLanguageTag(),
                    "opprettet" to opprettet,
                    "sistEndretAvBruker" to sistEndretAvBruker,
                    "prosessnavn" to prosessversjon?.prosessnavn?.id,
                    "prosessversjon" to prosessversjon?.versjon,
                    "nyVersjon" to (versjon + 1)
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
                                            begrunnelse, bundle_urn, innsendt)
                VALUES (:faktum_id, :beskrivende_id, :soknad_uuid, :faktum, :sannsynliggjoer, :tilstand, :valg, :begrunnelse, :bundle, :innsendt)
                ON CONFLICT (faktum_id, soknad_uuid) DO UPDATE SET beskrivende_id  = :beskrivende_id,
                                                                   faktum          = :faktum,
                                                                   sannsynliggjoer = :sannsynliggjoer,
                                                                   tilstand        = :tilstand,
                                                                   valg            = :valg,
                                                                   begrunnelse     = :begrunnelse,
                                                                   bundle_urn      = :bundle,
                                                                   innsendt        = :innsendt
                """.trimIndent(),
                mapOf(
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
                    "begrunnelse" to krav.svar.begrunnelse,
                    "bundle" to krav.svar.bundle?.toString(),
                    "innsendt" to krav.svar.innsendt
                )
            )
        )
        // fjerne alle filer for søknaden. Modellen har fasit på hvor mange som legges til i neste block
        queries.add(
            queryOf(
                // language=PostgreSQL
                "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid AND faktum_id = :faktum_id",
                mapOf(
                    "uuid" to søknadId,
                    "faktum_id" to krav.id
                )
            )
        )

        queries.addAll(
            krav.svar.filer.map { fil ->
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
                        "faktum_id" to krav.id,
                        "soknad_uuid" to søknadId,
                        "filnavn" to fil.filnavn,
                        "storrelse" to fil.storrelse,
                        "urn" to fil.urn.toString(),
                        "tidspunkt" to fil.tidspunkt,
                        "bundlet" to fil.bundlet
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
        metadata: Innsending.Metadata?
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
                INSERT INTO innsending_v1(innsending_uuid, soknad_uuid, innsendt, journalpost_id, innsendingtype, tilstand)
                VALUES (:innsending_uuid, :soknad_uuid, :innsendt, :journalpost_id, :innsendingtype, :tilstand)
                ON CONFLICT (innsending_uuid) DO UPDATE SET innsendt = :innsendt, 
                                                            journalpost_id = :journalpost_id,
                                                            innsendingtype = :innsendingtype,
                                                            tilstand = :tilstand
                """.trimIndent(),
                paramMap = mapOf(
                    "innsending_uuid" to innsendingId,
                    "soknad_uuid" to søknadId,
                    "innsendt" to innsendt,
                    "journalpost_id" to journalpost,
                    "innsendingtype" to innsending.name,
                    "tilstand" to tilstand.name
                )
            )
        )
        metadata?.let {
            queries.add(
                queryOf( //language=PostgreSQL
                    """
                    INSERT INTO metadata (innsending_uuid, skjemakode)
                    VALUES (:innsending_uuid, :skjemakode)
                    ON CONFLICT (innsending_uuid) DO UPDATE SET skjemakode=:skjemakode
                    """.trimIndent(),
                    mapOf(
                        "innsending_uuid" to innsendingId,
                        "skjemakode" to it.skjemakode
                    )
                )
            )
        }

        dokumenter.toMutableList().apply {
            hovedDokument?.let {
                add(it)
            }
        }.forEach { dokument ->
            queries.add(
                queryOf( //language=PostgreSQL
                    """
                    INSERT INTO dokument_v1 (dokument_uuid, innsending_uuid, brevkode, kravid)
                    VALUES (:dokument_uuid, :innsending_uuid, :brevkode, :kravId)
                    ON CONFLICT (dokument_uuid) DO UPDATE SET brevkode = :brevkode, kravid = :kravId
                    """.trimIndent(),
                    mapOf(
                        "dokument_uuid" to dokument.uuid,
                        "innsending_uuid" to innsendingId,
                        "brevkode" to dokument.skjemakode,
                        "kravId" to dokument.kravId
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
                  SELECT faktum_id, beskrivende_id, faktum, sannsynliggjoer, tilstand, valg, begrunnelse, bundle_urn, innsendt
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
                    valg = SvarValgDTO.valueOf(row.string("valg")),
                    bundle = row.stringOrNull("bundle_urn")?.let { URN.rfc8141().parse(it) },
                    innsendt = row.boolean("innsendt")
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
                  SELECT faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt, bundlet
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
                tidspunkt = row.norskZonedDateTime("tidspunkt"),
                bundlet = row.boolean("bundlet")
            )
        }.asList
    ).toSet()
}

private val tidssone = ZoneId.of("Europe/Oslo")
private fun Row.norskZonedDateTime(columnLabel: String): ZonedDateTime =
    this.zonedDateTime(columnLabel).withZoneSameInstant(tidssone)
