package com.zyren.app.core.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val importance: Int = 5,
    val useCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis()
)

/** role: user | assistant | error */
@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val capability: String,
    val risk: String,
    val detail: String,
    val outcome: String
)

data class UsageRow(val capability: String, val n: Int)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories ORDER BY pinned DESC, importance DESC, updatedAt DESC LIMIT :n")
    suspend fun top(n: Int): List<MemoryEntity>
    @Query("SELECT * FROM memories")
    suspend fun list(): List<MemoryEntity>
    @Query("UPDATE memories SET useCount = useCount + 1, lastUsedAt = :now WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, now: Long)
    @Query("DELETE FROM memories WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
    @Query("SELECT * FROM memories WHERE content = :c LIMIT 1")
    suspend fun findByContent(c: String): MemoryEntity?
    @Query("SELECT * FROM memories WHERE category = :c ORDER BY updatedAt DESC LIMIT :n")
    suspend fun byCategory(c: String, n: Int): List<MemoryEntity>
    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
    @Insert suspend fun insert(m: MemoryEntity): Long
    @Update suspend fun update(m: MemoryEntity)
    @Delete suspend fun delete(m: MemoryEntity)
    @Query("DELETE FROM memories") suspend fun clear()
    @Query("DELETE FROM memories WHERE id IN (SELECT id FROM memories WHERE pinned = 0 ORDER BY updatedAt ASC LIMIT :n)")
    suspend fun deleteOldestUnpinned(n: Int)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat ORDER BY id ASC")
    fun observeAll(): Flow<List<ChatEntity>>
    @Query("SELECT * FROM (SELECT * FROM chat ORDER BY id DESC LIMIT :n) ORDER BY id ASC")
    suspend fun recent(n: Int): List<ChatEntity>
    @Insert suspend fun insert(c: ChatEntity): Long
    @Query("DELETE FROM chat") suspend fun clear()
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit ORDER BY id DESC LIMIT 60")
    fun observeRecent(): Flow<List<AuditEntity>>
    @Query("SELECT capability, COUNT(*) AS n FROM audit GROUP BY capability")
    fun observeUsage(): Flow<List<UsageRow>>
    @Query("SELECT * FROM audit WHERE outcome LIKE 'hata%' AND createdAt > :ts ORDER BY id DESC LIMIT 100")
    suspend fun errorsSince(ts: Long): List<AuditEntity>
    @Query("SELECT * FROM audit WHERE outcome LIKE 'hata%' ORDER BY id DESC LIMIT 30")
    fun observeErrors(): Flow<List<AuditEntity>>
    @Insert suspend fun insert(a: AuditEntity): Long
    @Query("DELETE FROM audit WHERE id NOT IN (SELECT id FROM audit ORDER BY id DESC LIMIT 500)")
    suspend fun prune()
}

@Database(entities = [MemoryEntity::class, ChatEntity::class, AuditEntity::class], version = 1, exportSchema = false)
abstract class ZyrenDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatDao(): ChatDao
    abstract fun auditDao(): AuditDao
}
