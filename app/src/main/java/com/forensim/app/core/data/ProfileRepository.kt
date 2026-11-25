package com.forensim.app.core.data

import android.content.Context
import com.forensim.app.core.model.Gender
import com.forensim.app.core.model.Profile
import com.forensim.app.core.network.ApiException
import com.forensim.app.core.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * The signed-in user's own profile: name, gender, student ID and photo. Photos live in the private
 * "avatars" bucket at <user_id>/<random>.jpg and are cached on the device for offline display.
 */
class ProfileRepository(private val client: SupabaseClient, context: Context) {
    private val avatarDir = File(context.filesDir, "avatars")

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    /** JPEG/PNG bytes of the current user's photo, or null for the initials avatar. */
    private val _avatar = MutableStateFlow<ByteArray?>(null)
    val avatar: StateFlow<ByteArray?> = _avatar.asStateFlow()

    private fun uid() = client.session.value?.userId ?: throw ApiException(401, "Please sign in again.")

    suspend fun refresh(): Profile {
        val uid = uid()
        val p = client.select("profiles?id=eq.$uid&select=*", ListSerializer(Profile.serializer())).firstOrNull()
            ?: throw ApiException(404, "Profile not found.")
        _profile.value = p
        loadAvatar(p.avatarPath)
        return p
    }

    suspend fun updateDetails(fullName: String, gender: Gender?, studentId: String?) {
        val name = fullName.trim()
        if (name.length < 2) throw ApiException(400, "Enter your full name.")
        val id = studentId?.trim()?.ifBlank { null }
        if (id != null && id.length > 32) throw ApiException(400, "Student ID can be at most 32 characters.")
        client.update("profiles", "id=eq.${uid()}", buildJsonObject {
            put("full_name", name)
            put("gender", gender?.key)
            put("student_id", id)
            put("updated_at", java.time.Instant.now().toString())
        })
        refresh()
    }

    /** Uploads an already downscaled JPEG and points the profile at it; the old photo is deleted. */
    suspend fun uploadAvatar(jpeg: ByteArray) {
        val uid = uid()
        val path = "$uid/${UUID.randomUUID()}.jpg"
        client.uploadObject(AVATARS, path, jpeg, "image/jpeg")
        val old = _profile.value?.avatarPath
        client.update("profiles", "id=eq.$uid", buildJsonObject {
            put("avatar_path", path)
            put("updated_at", java.time.Instant.now().toString())
        })
        if (old != null && old != path) runCatching { client.deleteObject(AVATARS, old) }
        writeCache(path, jpeg)
        _avatar.value = jpeg
        _profile.value = _profile.value?.copy(avatarPath = path)
    }

    suspend fun removeAvatar() {
        val uid = uid()
        val old = _profile.value?.avatarPath ?: return
        client.update("profiles", "id=eq.$uid", buildJsonObject {
            put("avatar_path", null as String?)
            put("updated_at", java.time.Instant.now().toString())
        })
        runCatching { client.deleteObject(AVATARS, old) }
        _avatar.value = null
        _profile.value = _profile.value?.copy(avatarPath = null)
    }

    fun clear() {
        _profile.value = null
        _avatar.value = null
    }

    private suspend fun loadAvatar(path: String?) {
        if (path == null) { _avatar.value = null; return }
        val cached = cacheFile(path)
        val bytes = withContext(Dispatchers.IO) { cached.takeIf { it.exists() }?.readBytes() }
            ?: runCatching { client.downloadObject(AVATARS, path).also { writeCache(path, it) } }.getOrNull()
        _avatar.value = bytes
    }

    private fun cacheFile(path: String) = File(avatarDir, path.replace('/', '_'))

    private suspend fun writeCache(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        avatarDir.mkdirs()
        avatarDir.listFiles()?.forEach { it.delete() } // only the current photo is kept
        cacheFile(path).writeBytes(bytes)
    }

    companion object {
        const val AVATARS = "avatars"
    }
}
