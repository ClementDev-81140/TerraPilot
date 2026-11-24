package fr.terrapilot.assistant.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class ReportData(
    val niveaux: List<NiveauEntity>,
    val pentes: List<PenteEntity>,
    val volumes: List<VolumeEntity>,
    val reseaux: List<ReseauEntity>
)

object TerraRepository {
    fun observeChantiers(context: Context): Flow<List<ChantierEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.chantierDao().observeAll() }

    fun searchChantiers(context: Context, q: String): Flow<List<ChantierEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.chantierDao().search(q) }

    suspend fun insertChantier(context: Context, c: ChantierEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.chantierDao().insert(c) }

    suspend fun deleteChantier(context: Context, c: ChantierEntity) =
        DatabaseAccessGate.withDatabase(context) { it.chantierDao().delete(c) }

    fun observeNiveaux(context: Context, cid: Long): Flow<List<NiveauEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.niveauDao().observeByChantier(cid) }

    suspend fun insertNiveau(context: Context, n: NiveauEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.niveauDao().insert(n) }

    fun observePentes(context: Context, cid: Long): Flow<List<PenteEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.penteDao().observeByChantier(cid) }

    suspend fun insertPente(context: Context, p: PenteEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.penteDao().insert(p) }

    fun observeVolumes(context: Context, cid: Long): Flow<List<VolumeEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.volumeDao().observeByChantier(cid) }

    suspend fun insertVolume(context: Context, v: VolumeEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.volumeDao().insert(v) }

    fun observeImplantations(context: Context, cid: Long): Flow<List<ImplantationEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.implantationDao().observeByChantier(cid) }

    suspend fun insertImplantation(context: Context, i: ImplantationEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.implantationDao().insert(i) }

    fun observeReleves(context: Context, cid: Long): Flow<List<RelevePointEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.releveDao().observeByChantier(cid) }

    suspend fun insertReleve(context: Context, r: RelevePointEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.releveDao().insert(r) }

    fun observePlans(context: Context, cid: Long): Flow<List<PlanEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.planDao().observeByChantier(cid) }

    suspend fun insertPlan(context: Context, p: PlanEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.planDao().insert(p) }

    fun observeReseaux(context: Context, cid: Long): Flow<List<ReseauEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.reseauDao().observeByChantier(cid) }

    suspend fun insertReseau(context: Context, r: ReseauEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.reseauDao().insert(r) }

    fun observePhotos(context: Context, cid: Long): Flow<List<PhotoEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.photoDao().observeByChantier(cid) }

    suspend fun insertPhoto(context: Context, p: PhotoEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.photoDao().insert(p) }

    fun observeHistorique(context: Context, cid: Long): Flow<List<HistoriqueEntity>> =
        DatabaseAccessGate.observeFlow(context) { it.historiqueDao().observeByChantier(cid) }

    suspend fun insertHistorique(context: Context, h: HistoriqueEntity): Long =
        DatabaseAccessGate.withDatabase(context) { it.historiqueDao().insert(h) }

    suspend fun getReportData(context: Context, cid: Long): ReportData =
        DatabaseAccessGate.withDatabase(context) { db ->
            ReportData(
                niveaux = db.niveauDao().observeByChantier(cid).first(),
                pentes = db.penteDao().observeByChantier(cid).first(),
                volumes = db.volumeDao().observeByChantier(cid).first(),
                reseaux = db.reseauDao().observeByChantier(cid).first()
            )
        }
}
