package com.zaitxcode.opencut.core.account

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * A profile on this device.
 *
 * OpenCut has no server account: a "sign in" creates a local profile so that
 * settings, drafts and export defaults can be attributed to a person. A guest
 * profile is created automatically when the user prefers not to sign in at all,
 * which means every feature works identically in both cases.
 */
@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    val handle: String,
    val avatarColor: Int,
    val isGuest: Boolean,
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
) {
    val initial: String
        get() = displayName.trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * Persists the current profile.
 *
 * A single JSON file under `files/account/profile.json` holds the signed in
 * profile. Deleting it is the whole "sign out and forget me" flow, which keeps
 * the privacy story honest: nothing leaves the device.
 */
class AccountStore(context: Context) {

    private val dir = File(context.filesDir, "account").apply { mkdirs() }
    private val file = File(dir, "profile.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Returns the stored profile, or `null` when the user has not chosen yet. */
    fun current(): UserProfile? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(UserProfile.serializer(), file.readText())
        }.getOrNull()
    }

    /** True once the user has made a choice (guest or local account). */
    fun hasProfile(): Boolean = current() != null

    /** Continues without an account: every feature stays available. */
    fun continueAsGuest(): UserProfile {
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            displayName = "Guest",
            handle = "@guest",
            avatarColor = 0xFF3A7AFE.toInt(),
            isGuest = true,
            createdAt = now,
            lastSeenAt = now,
        )
        save(profile)
        return profile
    }

    /** Creates a local profile from a display name. */
    fun createLocalAccount(displayName: String): UserProfile {
        val clean = displayName.trim().ifBlank { "Creator" }.take(32)
        val now = System.currentTimeMillis()
        val handle = "@" + clean.lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
            .ifBlank { "creator" }
            .take(20)
        val palette = intArrayOf(
            0xFF3A7AFE.toInt(),
            0xFFE0A34A.toInt(),
            0xFF4CAF7D.toInt(),
            0xFFB169C7.toInt(),
            0xFFFF6B6B.toInt(),
        )
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            displayName = clean,
            handle = handle,
            avatarColor = palette[(clean.length + clean.first().code) % palette.size],
            isGuest = false,
            createdAt = now,
            lastSeenAt = now,
        )
        save(profile)
        return profile
    }

    /** Renames the current profile, keeping its identity. */
    fun rename(displayName: String): UserProfile? {
        val existing = current() ?: return null
        val clean = displayName.trim().ifBlank { existing.displayName }.take(32)
        val updated = existing.copy(
            displayName = clean,
            isGuest = false,
            handle = if (existing.isGuest) {
                "@" + clean.lowercase().replace(Regex("[^a-z0-9]+"), "").ifBlank { "creator" }.take(20)
            } else {
                existing.handle
            },
        )
        save(updated)
        return updated
    }

    /** Marks activity, used for "last seen" in the account screen. */
    fun touch(): UserProfile? {
        val existing = current() ?: return null
        val updated = existing.copy(lastSeenAt = System.currentTimeMillis())
        save(updated)
        return updated
    }

    /**
     * Signs out but keeps the projects.
     *
     * Deleting the profile only removes the identity; drafts are the user's work
     * and are never deleted implicitly.
     */
    fun signOut(): Boolean = file.delete()

    /** Deletes the profile file. Projects remain unless the caller removes them. */
    fun deleteProfile(): Boolean = file.delete()

    private fun save(profile: UserProfile) {
        dir.mkdirs()
        val tmp = File(dir, "profile.json.tmp")
        tmp.writeText(json.encodeToString(UserProfile.serializer(), profile))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
