package fr.terrapilot.assistant.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChantierDao {
    @Query("SELECT * FROM chantiers ORDER BY dateCreation DESC")
    fun observeAll(): Flow<List<ChantierEntity>>

    @Query("SELECT * FROM chantiers WHERE nom LIKE '%' || :q || '%' OR adresse LIKE '%' || :q || '%' ORDER BY dateCreation DESC")
    fun search(q: String): Flow<List<ChantierEntity>>

    @Query("SELECT * FROM chantiers WHERE id = :id")
    suspend fun getById(id: Long): ChantierEntity?

    @Insert
    suspend fun insert(item: ChantierEntity): Long

    @Update
    suspend fun update(item: ChantierEntity)

    @Delete
    suspend fun delete(item: ChantierEntity)
}

@Dao
interface NiveauDao {
    @Query("SELECT * FROM niveaux WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<NiveauEntity>>

    @Insert
    suspend fun insert(item: NiveauEntity): Long

    @Delete
    suspend fun delete(item: NiveauEntity)
}

@Dao
interface PenteDao {
    @Query("SELECT * FROM pentes WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<PenteEntity>>

    @Insert
    suspend fun insert(item: PenteEntity): Long
}

@Dao
interface VolumeDao {
    @Query("SELECT * FROM volumes WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<VolumeEntity>>

    @Insert
    suspend fun insert(item: VolumeEntity): Long
}

@Dao
interface ImplantationDao {
    @Query("SELECT * FROM implantations WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<ImplantationEntity>>

    @Insert
    suspend fun insert(item: ImplantationEntity): Long
}

@Dao
interface ReleveDao {
    @Query("SELECT * FROM releves_topo WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<RelevePointEntity>>

    @Insert
    suspend fun insert(item: RelevePointEntity): Long

    @Delete
    suspend fun delete(item: RelevePointEntity)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<PlanEntity>>

    @Insert
    suspend fun insert(item: PlanEntity): Long

    @Update
    suspend fun update(item: PlanEntity)
}

@Dao
interface ReseauDao {
    @Query("SELECT * FROM reseaux WHERE chantierId = :cid ORDER BY id DESC")
    fun observeByChantier(cid: Long): Flow<List<ReseauEntity>>

    @Insert
    suspend fun insert(item: ReseauEntity): Long

    @Delete
    suspend fun delete(item: ReseauEntity)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE chantierId = :cid ORDER BY date DESC")
    fun observeByChantier(cid: Long): Flow<List<PhotoEntity>>

    @Insert
    suspend fun insert(item: PhotoEntity): Long

    @Delete
    suspend fun delete(item: PhotoEntity)
}

@Dao
interface HistoriqueDao {
    @Query("SELECT * FROM historique WHERE chantierId = :cid ORDER BY date DESC LIMIT 50")
    fun observeByChantier(cid: Long): Flow<List<HistoriqueEntity>>

    @Insert
    suspend fun insert(item: HistoriqueEntity): Long
}
