package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Persistent per-block state for the Dubber V2 review screen.
 *
 * Rows survive app restarts so a locked subtitle block (or a pinned block
 * voice) never changes until the user deliberately unlocks/unpins.
 *
 * stateKey = "$videoKey#$cueIndex" where videoKey identifies the loaded
 * project (video file name + byte length, or the subtitle name).
 */
@Entity(tableName = "dub_cue_states")
data class DubCueState(
    @PrimaryKey val stateKey: String,
    val videoKey: String,
    val cueIndex: Int,
    val locked: Boolean = false,
    val textOverride: String? = null,
    val voiceName: String? = null,
    val voicePinned: Boolean = false
)

@Dao
interface DubCueStateDao {

    @Query("SELECT * FROM dub_cue_states WHERE videoKey = :videoKey")
    fun forVideo(videoKey: String): List<DubCueState>

    @Upsert
    fun upsert(state: DubCueState)

    @Query("DELETE FROM dub_cue_states WHERE stateKey = :stateKey")
    fun deleteByKey(stateKey: String)

    @Query("DELETE FROM dub_cue_states WHERE videoKey = :videoKey")
    fun deleteForVideo(videoKey: String)
}
