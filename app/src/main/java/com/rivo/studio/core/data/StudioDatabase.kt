package com.rivo.studio.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rivo.studio.core.model.AgentTaskRecord
import com.rivo.studio.core.model.ConversationRecord
import com.rivo.studio.core.model.ExecutionRecord
import com.rivo.studio.core.model.MessageRecord
import com.rivo.studio.core.model.ProjectRecord
import com.rivo.studio.core.model.RecentFileRecord
import com.rivo.studio.core.model.SnapshotRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectRecord>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProject(id: String): ProjectRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectRecord)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE projects SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)
}

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files WHERE projectId = :projectId ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecentFiles(projectId: String, limit: Int = 12): Flow<List<RecentFileRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: RecentFileRecord)

    @Query("SELECT * FROM recent_files WHERE projectId = :projectId")
    suspend fun filesForProject(projectId: String): List<RecentFileRecord>

    @Query("DELETE FROM recent_files WHERE projectId = :projectId AND relativePath = :relativePath")
    suspend fun deletePath(projectId: String, relativePath: String)

    @Query("DELETE FROM recent_files WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<ConversationRecord>>

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForProject(projectId: String): ConversationRecord?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ConversationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationRecord)

    @Query("DELETE FROM conversations WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageRecord>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun messages(conversationId: String, limit: Int = 40): List<MessageRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageRecord)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 50")
    fun observeForProject(projectId: String): Flow<List<AgentTaskRecord>>

    @Query("SELECT * FROM agent_tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AgentTaskRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: AgentTaskRecord)
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM executions WHERE projectId = :projectId ORDER BY startedAt DESC LIMIT 50")
    fun observeForProject(projectId: String): Flow<List<ExecutionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ExecutionRecord)

    @Query("UPDATE executions SET state = 'FAILED', finishedAt = :finishedAt, diagnostic = :diagnostic WHERE state IN ('STARTING', 'RUNNING', 'STOPPING')")
    suspend fun markInterrupted(finishedAt: Long, diagnostic: String)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 100")
    fun observeForProject(projectId: String): Flow<List<SnapshotRecord>>

    @Query("SELECT * FROM snapshots WHERE id = :id LIMIT 1")
    suspend fun get(id: String): SnapshotRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SnapshotRecord)
}

@Database(
    entities = [
        ProjectRecord::class,
        RecentFileRecord::class,
        ConversationRecord::class,
        MessageRecord::class,
        AgentTaskRecord::class,
        ExecutionRecord::class,
        SnapshotRecord::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun recentFiles(): RecentFileDao
    abstract fun conversations(): ConversationDao
    abstract fun agentTasks(): AgentTaskDao
    abstract fun executions(): ExecutionDao
    abstract fun snapshots(): SnapshotDao

    companion object {
        @Volatile private var instance: StudioDatabase? = null

        fun get(context: Context): StudioDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StudioDatabase::class.java,
                "rivo_studio_local.db",
            ).build().also { instance = it }
        }
    }
}
