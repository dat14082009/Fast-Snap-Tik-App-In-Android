package com.stone.tiktoksave.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadStatus(val label: String) {
    COMPLETED("Hoàn tất"),
    FAILED("Thất bại"),
    CANCELED("Đã hủy");

    companion object {
        fun fromName(name: String): DownloadStatus = entries.firstOrNull { it.name == name } ?: FAILED
    }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val author: String,
    val posterPath: String?,
    val fileUri: String?,
    val mime: String?,
    val label: String,
    val status: String,
    val errorMessage: String?,
    val createdAt: Long
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

class HistoryRepository(private val dao: HistoryDao) {
    val items: Flow<List<HistoryEntity>> = dao.observeAll()

    suspend fun add(entity: HistoryEntity) {
        dao.insert(entity)
    }

    /** Chỉ xóa khỏi lịch sử (và ảnh poster đã cache), không xóa tệp đã tải. */
    suspend fun remove(entity: HistoryEntity) {
        dao.deleteById(entity.id)
        entity.posterPath?.let { path ->
            withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
        }
    }
}
