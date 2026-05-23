package com.indianservers.ruhi

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity
data class ConversationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val userText: String,
    val ruhiResponse: String,
    val detectedEmotion: String
)

@Entity
data class MoodSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val happiness: Float,
    val energy: Float,
    val curiosity: Float
)

@Entity
data class MemoryFragment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val summary: String,
    val embedding: String,
    val emotionTag: String,
    val timestamp: Long,
    val importance: Int
)

@Entity
data class FaceProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val faceEmbedding: String,
    val relationshipScore: Float,
    val totalInteractions: Int,
    val lastSeen: Long
)

@Entity
data class PersonalityProfile(
    @PrimaryKey val id: Long = 1,
    val openness: Float,
    val warmth: Float,
    val playfulness: Float,
    val seriousness: Float,
    val updatedAt: Long
)

@Entity(indices = [Index(value = ["key"], unique = true)])
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val title: String,
    val description: String,
    val unlockedAt: Long?,
    val progress: Int,
    val target: Int
)

@Entity
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val stepCount: Int
)

@Entity(indices = [Index(value = ["routeId", "sequence"], unique = true)])
data class Waypoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val sequence: Int,
    val leftSpeed: Float,
    val rightSpeed: Float,
    val durationMs: Int,
    val headPan: Float,
    val headTilt: Float,
    val ledColor: Int
)

@Entity
data class PatrolEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val imageUri: String?,
    val faceDetected: Boolean
)

@Entity
data class Performance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val steps: String,
    val createdAt: Long
)

@Entity
data class NeedsSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val energy: Float,
    val social: Float,
    val stimulation: Float,
    val comfort: Float,
    val expression: Float,
    val safety: Float
)

@Entity
data class InnerThought(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val thought: String,
    val leaked: Boolean
)

@Entity
data class BondLevel(
    @PrimaryKey val id: Long = 1,
    val level: Int,
    val updatedAt: Long,
    val lastSeenAt: Long
)

@Entity
data class InsideJoke(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val summary: String,
    val context: String,
    val timestamp: Long
)

@Dao
interface ConversationDao {
    @Insert suspend fun insert(entry: ConversationEntry)

    @Query("SELECT * FROM ConversationEntry ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ConversationEntry>

    @Query("SELECT * FROM ConversationEntry ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): ConversationEntry?

    @Query("SELECT COUNT(*) FROM ConversationEntry")
    suspend fun count(): Int
}

@Dao
interface MoodDao {
    @Insert suspend fun insert(snapshot: MoodSnapshot)

    @Query("SELECT * FROM MoodSnapshot ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MoodSnapshot>

    @Query("SELECT * FROM MoodSnapshot ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): MoodSnapshot?
}

@Dao
interface MemoryFragmentDao {
    @Insert suspend fun insert(fragment: MemoryFragment)

    @Query("SELECT * FROM MemoryFragment ORDER BY importance DESC, timestamp DESC LIMIT :limit")
    suspend fun topRecent(limit: Int): List<MemoryFragment>

    @Query("SELECT * FROM MemoryFragment ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MemoryFragment>
}

@Dao
interface FaceProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(profile: FaceProfile): Long

    @Query("SELECT * FROM FaceProfile")
    suspend fun all(): List<FaceProfile>

    @Query("UPDATE FaceProfile SET relationshipScore = :score, totalInteractions = :interactions, lastSeen = :lastSeen WHERE id = :id")
    suspend fun updateSeen(id: Long, score: Float, interactions: Int, lastSeen: Long)
}

@Dao
interface PersonalityProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(profile: PersonalityProfile)

    @Query("SELECT * FROM PersonalityProfile WHERE id = 1")
    suspend fun get(): PersonalityProfile?
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(achievement: Achievement)

    @Query("SELECT * FROM Achievement")
    suspend fun all(): List<Achievement>

    @Query("SELECT * FROM Achievement WHERE `key` = :key LIMIT 1")
    suspend fun find(key: String): Achievement?

    @Query("UPDATE Achievement SET progress = :progress, unlockedAt = :unlockedAt WHERE `key` = :key")
    suspend fun updateProgress(key: String, progress: Int, unlockedAt: Long?)
}

@Dao
interface WaypointDao {
    @Insert suspend fun insertRoute(route: Route): Long

    @Insert suspend fun insertWaypoint(waypoint: Waypoint)

    @Insert suspend fun insertWaypoints(waypoints: List<Waypoint>)

    @Query("SELECT * FROM Route ORDER BY createdAt DESC")
    suspend fun routes(): List<Route>

    @Query("SELECT * FROM Waypoint WHERE routeId = :routeId ORDER BY sequence ASC")
    suspend fun waypointsForRoute(routeId: Long): List<Waypoint>

    @Query("DELETE FROM Waypoint WHERE routeId = :routeId")
    suspend fun deleteWaypoints(routeId: Long)

    @Update suspend fun updateRoute(route: Route)
}

@Dao
interface PatrolEventDao {
    @Insert suspend fun insert(event: PatrolEvent)

    @Query("SELECT * FROM PatrolEvent ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PatrolEvent>
}

@Dao
interface PerformanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(performance: Performance): Long

    @Query("SELECT * FROM Performance ORDER BY createdAt DESC")
    suspend fun all(): List<Performance>

    @Query("SELECT * FROM Performance WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): Performance?
}

@Dao
interface NeedsDao {
    @Insert suspend fun insert(snapshot: NeedsSnapshot)

    @Query("SELECT * FROM NeedsSnapshot ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): NeedsSnapshot?
}

@Dao
interface InnerThoughtDao {
    @Insert suspend fun insert(thought: InnerThought)

    @Query("SELECT * FROM InnerThought ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<InnerThought>
}

@Dao
interface BondDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(bond: BondLevel)

    @Query("SELECT * FROM BondLevel WHERE id = 1")
    suspend fun get(): BondLevel?
}

@Dao
interface InsideJokeDao {
    @Insert suspend fun insert(joke: InsideJoke)

    @Query("SELECT * FROM InsideJoke ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<InsideJoke>
}

@Database(
    entities = [
        ConversationEntry::class,
        MoodSnapshot::class,
        MemoryFragment::class,
        FaceProfile::class,
        PersonalityProfile::class,
        Achievement::class,
        Route::class,
        Waypoint::class,
        PatrolEvent::class,
        Performance::class,
        NeedsSnapshot::class,
        InnerThought::class,
        BondLevel::class,
        InsideJoke::class
    ],
    version = 6,
    exportSchema = false
)
abstract class RuhiDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun moodDao(): MoodDao
    abstract fun memoryFragmentDao(): MemoryFragmentDao
    abstract fun faceProfileDao(): FaceProfileDao
    abstract fun personalityProfileDao(): PersonalityProfileDao
    abstract fun achievementDao(): AchievementDao
    abstract fun waypointDao(): WaypointDao
    abstract fun patrolEventDao(): PatrolEventDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun needsDao(): NeedsDao
    abstract fun innerThoughtDao(): InnerThoughtDao
    abstract fun bondDao(): BondDao
    abstract fun insideJokeDao(): InsideJokeDao

    companion object {
        @Volatile private var instance: RuhiDatabase? = null

        fun getInstance(context: Context): RuhiDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RuhiDatabase::class.java,
                    "ruhi.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
