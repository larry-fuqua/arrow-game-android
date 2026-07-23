package com.larry.arrowgame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class UserProfile(
    val userId: String,
    val displayName: String,
    val email: String = "",
    val authProvider: String = "guest", // guest | local
    val createdAt: Long = System.currentTimeMillis(),
)

data class ScoreEntry(
    val userId: String,
    val displayName: String,
    val levelId: Int,
    val score: Int,
    val elapsedSec: Float,
    val moves: Int,
    val seed: Int?,
    val timestamp: Long = System.currentTimeMillis(),
)

class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("arrow_game", Context.MODE_PRIVATE)

    fun loadProfile(): UserProfile? {
        val id = prefs.getString("profile_user_id", null) ?: return null
        return UserProfile(
            userId = id,
            displayName = prefs.getString("profile_display_name", "Player") ?: "Player",
            email = prefs.getString("profile_email", "") ?: "",
            authProvider = prefs.getString("profile_auth_provider", "guest") ?: "guest",
            createdAt = prefs.getLong("profile_created_at", System.currentTimeMillis()),
        )
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString("profile_user_id", profile.userId)
            .putString("profile_display_name", profile.displayName)
            .putString("profile_email", profile.email)
            .putString("profile_auth_provider", profile.authProvider)
            .putLong("profile_created_at", profile.createdAt)
            .apply()
    }

    fun clearProfile() {
        prefs.edit()
            .remove("profile_user_id")
            .remove("profile_display_name")
            .remove("profile_email")
            .remove("profile_auth_provider")
            .remove("profile_created_at")
            .apply()
    }

    fun isMuted(): Boolean = prefs.getBoolean("muted", false)

    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean("muted", muted).apply()
    }

    fun loadScores(): List<ScoreEntry> {
        val raw = prefs.getString("scores_json", null) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("scores") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ScoreEntry(
                            userId = o.getString("user_id"),
                            displayName = o.optString("display_name", "Player"),
                            levelId = o.getInt("level_id"),
                            score = o.getInt("score"),
                            elapsedSec = o.getDouble("elapsed_sec").toFloat(),
                            moves = o.optInt("moves", 0),
                            seed = if (o.has("seed") && !o.isNull("seed")) o.getInt("seed") else null,
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveScore(entry: ScoreEntry) {
        val scores = loadScores().toMutableList()
        scores.add(entry)
        val trimmed = scores.takeLast(500)
        val arr = JSONArray()
        for (s in trimmed) {
            arr.put(
                JSONObject().apply {
                    put("user_id", s.userId)
                    put("display_name", s.displayName)
                    put("level_id", s.levelId)
                    put("score", s.score)
                    put("elapsed_sec", s.elapsedSec.toDouble())
                    put("moves", s.moves)
                    if (s.seed != null) put("seed", s.seed) else put("seed", JSONObject.NULL)
                    put("timestamp", s.timestamp)
                }
            )
        }
        prefs.edit().putString("scores_json", JSONObject().put("scores", arr).toString()).apply()
    }

    fun bestForUser(userId: String, levelId: Int): ScoreEntry? {
        return loadScores()
            .filter { it.userId == userId && it.levelId == levelId }
            .maxWithOrNull(compareBy<ScoreEntry> { it.score }.thenBy { -it.elapsedSec })
    }

    fun makeGuest(displayName: String = "Guest"): UserProfile {
        val name = displayName.trim().ifEmpty { "Guest" }
        val uid = "guest-" + sha12("$name-${System.currentTimeMillis()}-${UUID.randomUUID()}")
        val profile = UserProfile(userId = uid, displayName = name, authProvider = "guest")
        saveProfile(profile)
        return profile
    }

    fun makeNamedLocal(displayName: String): UserProfile {
        val safe = displayName.trim().ifEmpty { "Player" }
        val uid = "local-" + sha12(safe.lowercase())
        val profile = UserProfile(userId = uid, displayName = safe, authProvider = "local")
        saveProfile(profile)
        return profile
    }

    private fun sha12(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(s.toByteArray())
        return dig.joinToString("") { "%02x".format(it) }.take(12)
    }
}
