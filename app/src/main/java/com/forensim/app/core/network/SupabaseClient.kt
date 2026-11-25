package com.forensim.app.core.network

import com.forensim.app.core.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Signed-in session. Tokens are user-scoped JWTs; the app never holds privileged keys. */
@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("user_id") val userId: String,
    val email: String,
)

/** A failed request with a user-presentable message. */
class ApiException(val status: Int, override val message: String, val code: String? = null) : Exception(message) {
    val isNetwork: Boolean get() = status == 0
}

/**
 * Minimal Supabase client over OkHttp: GoTrue auth, PostgREST tables/RPCs and Edge Functions.
 * Authorization is always enforced server-side (RLS, SECURITY DEFINER functions, Edge Functions);
 * this class only carries the user's JWT.
 */
class SupabaseClient(
    private val baseUrl: String,
    private val publishableKey: String,
    private val sessionStore: SessionStore,
    val json: Json,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // AI tutor calls can take a while
        .build()
    private val jsonType = "application/json".toMediaType()
    private val refreshLock = Mutex()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    // ---------------------------------------------------------------- auth

    suspend fun restoreSession(): Session? {
        val stored = sessionStore.load() ?: return null
        _session.value = stored
        return try {
            ensureFreshToken()
            _session.value
        } catch (e: ApiException) {
            if (e.isNetwork) stored else {
                signOutLocal(); null
            }
        }
    }

    suspend fun signIn(email: String, password: String): Session {
        val body = buildJsonObject { put("email", email.trim()); put("password", password) }
        val res = execute(authRequest("/auth/v1/token?grant_type=password", body), authed = false)
        return saveTokenResponse(res)
    }

    /**
     * Returns a session, or null when the project requires e-mail confirmation first. In that case
     * the confirmation link (PKCE) opens [redirectTo] in the app, which signs the user in.
     */
    suspend fun signUp(email: String, password: String, fullName: String, redirectTo: String): Session? {
        val verifier = Pkce.verifier()
        sessionStore.savePkceVerifier(verifier)
        sessionStore.savePendingFlow("signup")
        val body = buildJsonObject {
            put("email", email.trim()); put("password", password)
            put("data", buildJsonObject { put("full_name", fullName.trim()) })
            put("code_challenge", Pkce.challenge(verifier))
            put("code_challenge_method", "s256")
        }
        val url = "$baseUrl/auth/v1/signup".toHttpUrl().newBuilder().addQueryParameter("redirect_to", redirectTo).build()
        val res = execute(authRequest(url.toString().removePrefix(baseUrl), body), authed = false).jsonObject
        return if (res["access_token"] != null) saveTokenResponse(res) else null
    }

    /** When the signed-in account was created (GET /auth/v1/user), as an ISO timestamp. */
    suspend fun currentUserCreatedAt(): String? {
        val req = Request.Builder().url("$baseUrl/auth/v1/user").get().build()
        return execute(req, authed = true).jsonObject["created_at"]?.jsonPrimitive?.contentOrNull
    }

    /** Which OAuth providers are enabled for this project (GET /auth/v1/settings). */
    suspend fun enabledProviders(): Set<String> {
        val req = Request.Builder().url("$baseUrl/auth/v1/settings").get().build()
        val external = execute(req, authed = false).jsonObject["external"]?.jsonObject ?: return emptySet()
        return external.filterValues { runCatching { it.jsonPrimitive.content == "true" }.getOrDefault(false) }.keys
    }

    /**
     * OAuth with PKCE: returns the authorize URL to open in a browser tab. The verifier is kept in
     * [SessionStore] so the exchange still works if Android kills the app while the browser is open.
     */
    suspend fun oauthAuthorizeUrl(provider: String, redirectTo: String): String {
        val verifier = Pkce.verifier()
        sessionStore.savePkceVerifier(verifier)
        sessionStore.savePendingFlow("oauth")
        return "$baseUrl/auth/v1/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("provider", provider)
            .addQueryParameter("redirect_to", redirectTo)
            .addQueryParameter("code_challenge", Pkce.challenge(verifier))
            .addQueryParameter("code_challenge_method", "s256")
            .build().toString()
    }

    suspend fun exchangeOAuthCode(code: String): Session {
        val verifier = sessionStore.takePkceVerifier() ?: throw ApiException(400, "Sign-in expired. Please try again.")
        val body = buildJsonObject { put("auth_code", code); put("code_verifier", verifier) }
        return saveTokenResponse(execute(authRequest("/auth/v1/token?grant_type=pkce", body), authed = false))
    }

    /**
     * Starts an e-mail change. Supabase e-mails a confirmation link to the new address (and, with
     * "secure email change" on, to the current one too); the change applies once confirmed. The
     * link opens [redirectTo].
     */
    suspend fun requestEmailChange(newEmail: String, redirectTo: String) {
        val url = "$baseUrl/auth/v1/user".toHttpUrl().newBuilder().addQueryParameter("redirect_to", redirectTo).build()
        val req = Request.Builder().url(url)
            .put(json.encodeToString(JsonObject.serializer(), buildJsonObject { put("email", newEmail.trim()) }).toRequestBody(jsonType))
            .build()
        execute(req, authed = true)
    }

    /** Sets a new password for the signed-in user (after a recovery link, or from settings). */
    suspend fun updatePassword(password: String) {
        val req = Request.Builder().url("$baseUrl/auth/v1/user")
            .put(json.encodeToString(JsonObject.serializer(), buildJsonObject { put("password", password) }).toRequestBody(jsonType))
            .build()
        execute(req, authed = true)
    }

    /**
     * Sends a password-reset e-mail. With PKCE the link returns to [redirectTo] with a one-time code
     * that signs the user in so they can choose a new password. Always succeeds from the user's
     * view to avoid account enumeration.
     */
    suspend fun requestPasswordReset(email: String, redirectTo: String) {
        val verifier = Pkce.verifier()
        sessionStore.savePkceVerifier(verifier)
        sessionStore.savePendingFlow("recovery")
        val url = "$baseUrl/auth/v1/recover".toHttpUrl().newBuilder().addQueryParameter("redirect_to", redirectTo).build()
        val body = buildJsonObject {
            put("email", email.trim())
            put("code_challenge", Pkce.challenge(verifier))
            put("code_challenge_method", "s256")
        }
        execute(Request.Builder().url(url).post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonType)).build(), authed = false)
    }

    /** Which flow the pending PKCE code belongs to; read once, when the code arrives. */
    suspend fun takePendingFlow(): String? = sessionStore.takePendingFlow()

    /** Forces a token refresh so the session picks up server-side changes (e.g. a new e-mail). */
    suspend fun refreshSession() {
        _session.value?.let { _session.value = it.copy(expiresAt = 0) }
        ensureFreshToken()
    }

    /** Local sign-out, then a best-effort server-side revoke. */
    suspend fun signOut() {
        signOutLocally()?.let { revokeSession(it) }
    }

    /**
     * Forgets the session on this device and returns its access token (for [revokeSession]).
     * Runs to completion even if the caller is cancelled, so a sign-out is never left half done.
     */
    suspend fun signOutLocally(): String? = withContext(NonCancellable) {
        val token = _session.value?.accessToken
        signOutLocal()
        token
    }

    /** Revokes every session of this user on every device. Throws if the server can't be reached. */
    suspend fun revokeAllSessions() {
        execute(authRequest("/auth/v1/logout?scope=global", buildJsonObject { }), authed = true)
    }

    /** Round-trip time to the auth service in milliseconds. Throws if the server can't be reached. */
    suspend fun ping(): Long {
        val start = System.nanoTime()
        execute(Request.Builder().url("$baseUrl/auth/v1/health").get().build(), authed = false)
        return (System.nanoTime() - start) / 1_000_000
    }

    /** Ends the session on the server (revokes its refresh token). Failures are ignored. */
    suspend fun revokeSession(accessToken: String) {
        val req = authRequest("/auth/v1/logout", buildJsonObject { }).newBuilder()
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $accessToken")
            .build()
        try {
            withContext(Dispatchers.IO) { http.newCall(req).await().close() }
        } catch (e: IOException) {
            // Offline: the token simply expires on its own.
        }
    }

    private suspend fun signOutLocal() {
        _session.value = null
        sessionStore.clear()
    }

    private suspend fun ensureFreshToken() {
        val s = _session.value ?: throw ApiException(401, "Please sign in again.")
        if (s.expiresAt - nowSeconds() > 60) return
        refreshLock.withLock {
            val again = _session.value ?: throw ApiException(401, "Please sign in again.")
            if (again.expiresAt - nowSeconds() > 60) return
            val body = buildJsonObject { put("refresh_token", again.refreshToken) }
            val res = execute(authRequest("/auth/v1/token?grant_type=refresh_token", body), authed = false)
            saveTokenResponse(res)
        }
    }

    private suspend fun saveTokenResponse(res: JsonElement): Session {
        val o = res.jsonObject
        val user = o["user"]!!.jsonObject
        val expiresIn = o["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
        val s = Session(
            accessToken = o["access_token"]!!.jsonPrimitive.content,
            refreshToken = o["refresh_token"]!!.jsonPrimitive.content,
            expiresAt = o["expires_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: (nowSeconds() + expiresIn),
            userId = user["id"]!!.jsonPrimitive.content,
            email = user["email"]?.jsonPrimitive?.contentOrNull ?: "",
        )
        _session.value = s
        sessionStore.save(s)
        return s
    }

    // ---------------------------------------------------------------- data

    /** GET /rest/v1/{path}; [path] includes the table and PostgREST query string. */
    suspend fun <T> select(path: String, serializer: KSerializer<T>): T {
        val req = Request.Builder().url("$baseUrl/rest/v1/$path").get().build()
        return decode(serializer, execute(req, authed = true))
    }

    suspend fun <T> rpc(function: String, params: JsonObject, serializer: KSerializer<T>): T =
        decode(serializer, rpcRaw(function, params))

    /** Decoding large responses on the main thread stalls taps and animations; do it on a worker. */
    private suspend fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T =
        withContext(Dispatchers.Default) { json.decodeFromJsonElement(serializer, element) }

    suspend fun rpcRaw(function: String, params: JsonObject = JsonObject(emptyMap())): JsonElement {
        val req = Request.Builder().url("$baseUrl/rest/v1/rpc/$function")
            .post(json.encodeToString(JsonObject.serializer(), params).toRequestBody(jsonType)).build()
        return execute(req, authed = true)
    }

    suspend fun insert(table: String, body: JsonElement, upsert: Boolean = false) {
        val req = Request.Builder().url("$baseUrl/rest/v1/$table")
            .header("Prefer", if (upsert) "resolution=ignore-duplicates,return=minimal" else "return=minimal")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)).build()
        execute(req, authed = true)
    }

    /** POST one row and get it back as the server stored it (ids, defaults, trigger-set fields). */
    suspend fun <T> insertReturning(table: String, body: JsonElement, serializer: KSerializer<T>): T {
        val req = Request.Builder().url("$baseUrl/rest/v1/$table")
            .header("Prefer", "return=representation")
            .header("Accept", "application/vnd.pgrst.object+json")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)).build()
        return decode(serializer, execute(req, authed = true))
    }

    /** PATCH rows matching [filter] (PostgREST filter query, e.g. "id=eq.123"). */
    suspend fun update(table: String, filter: String, body: JsonObject) {
        val req = Request.Builder().url("$baseUrl/rest/v1/$table?$filter")
            .header("Prefer", "return=minimal")
            .patch(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonType)).build()
        execute(req, authed = true)
    }

    /** DELETE rows matching [filter] (PostgREST filter query); RLS decides what is allowed. */
    suspend fun delete(table: String, filter: String) {
        val req = Request.Builder().url("$baseUrl/rest/v1/$table?$filter").header("Prefer", "return=minimal").delete().build()
        execute(req, authed = true)
    }

    /** A valid access token (refreshed if it is about to expire), for long-lived connections. */
    suspend fun freshAccessToken(): String {
        ensureFreshToken()
        return _session.value?.accessToken ?: throw ApiException(401, "Please sign in again.")
    }

    private val socketHttp by lazy {
        http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    }

    /**
     * Live INSERT/UPDATE/DELETE notifications for [table] rows matching [filter], via Supabase
     * Realtime (Phoenix channel over a WebSocket). Emits Unit per change; callers re-read the rows
     * over REST, so row-level security applies exactly as for normal reads. Completes with an
     * error when the socket drops, so callers can retry.
     */
    fun tableChanges(table: String, filter: String): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val token = freshAccessToken()
        val wsUrl = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=$publishableKey&vsn=1.0.0"
        val topic = "realtime:$table:$filter"
        var ref = 0
        fun frame(t: String, event: String, payload: JsonObject) = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("topic", t); put("event", event); put("payload", payload); put("ref", (++ref).toString())
        })
        val join = buildJsonObject {
            put("config", buildJsonObject {
                put("broadcast", buildJsonObject { put("self", false) })
                put("presence", buildJsonObject { put("key", "") })
                put("postgres_changes", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("event", "*"); put("schema", "public"); put("table", table); put("filter", filter) })
                })
            })
            put("access_token", token)
        }
        val socket = socketHttp.newWebSocket(Request.Builder().url(wsUrl).build(), object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                webSocket.send(frame(topic, "phx_join", join))
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                val event = runCatching { json.parseToJsonElement(text).jsonObject["event"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                if (event == "postgres_changes") trySend(Unit)
                if (event == "phx_error" || event == "system" && text.contains("\"status\":\"error\"")) {
                    close(IOException("Realtime channel error"))
                }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                close(IOException("Realtime closed: $code"))
            }
        })
        val heartbeat = launch {
            while (true) {
                kotlinx.coroutines.delay(25_000)
                socket.send(frame("phoenix", "heartbeat", JsonObject(emptyMap())))
            }
        }
        awaitClose {
            heartbeat.cancel()
            socket.close(1000, null)
        }
    }

    suspend fun <T> invokeFunction(name: String, body: JsonElement, serializer: KSerializer<T>): T {
        val req = Request.Builder().url("$baseUrl/functions/v1/$name")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonType)).build()
        return decode(serializer, execute(req, authed = true))
    }

    // ---------------------------------------------------------------- transport

    private fun authRequest(path: String, body: JsonObject): Request =
        Request.Builder().url("$baseUrl$path")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonType)).build()

    private suspend fun execute(request: Request, authed: Boolean): JsonElement {
        val bytes = send(request, authed)
        return withContext(Dispatchers.Default) {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
        }
    }

    private suspend fun send(request: Request, authed: Boolean, retried: Boolean = false): ByteArray {
        if (authed) ensureFreshToken()
        val builder = request.newBuilder().header("apikey", publishableKey)
        if (request.header("Accept") == null) builder.header("Accept", "application/json")
        if (authed) _session.value?.let { builder.header("Authorization", "Bearer ${it.accessToken}") }
        val (code, bytes) = withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).await().use { it.code to (it.body?.bytes() ?: ByteArray(0)) }
            } catch (e: IOException) {
                throw ApiException(0, "You appear to be offline. Check your connection and try again.")
            }
        }
        if (code == 401 && authed && !retried) {
            // Token may have been revoked or expired early: force one refresh, then retry.
            _session.value?.let { _session.value = it.copy(expiresAt = 0) }
            return send(request, authed = true, retried = true)
        }
        if (code !in 200..299) {
            val text = bytes.toString(Charsets.UTF_8)
            // The server's error reply (never the request body), so failures can be diagnosed from logcat.
            android.util.Log.w("ForensimApi", "HTTP $code ${request.method} ${request.url.encodedPath}: ${text.take(300)}")
            throw parseError(code, text)
        }
        return bytes
    }

    // ---------------------------------------------------------------- storage

    /** Uploads a file to Storage as the signed-in user (bucket policies decide who may write). */
    suspend fun uploadObject(bucket: String, path: String, bytes: ByteArray, contentType: String) {
        val req = Request.Builder().url("$baseUrl/storage/v1/object/$bucket/$path")
            .header("x-upsert", "false")
            .post(bytes.toRequestBody(contentType.toMediaType())).build()
        send(req, authed = true)
    }

    suspend fun downloadObject(bucket: String, path: String): ByteArray {
        val req = Request.Builder().url("$baseUrl/storage/v1/object/authenticated/$bucket/$path")
            .header("Accept", "*/*").get().build()
        return send(req, authed = true)
    }

    suspend fun deleteObject(bucket: String, path: String) {
        val req = Request.Builder().url("$baseUrl/storage/v1/object/$bucket/$path").delete().build()
        send(req, authed = true)
    }

    private fun parseError(code: Int, text: String): ApiException {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        fun field(name: String) = obj?.get(name)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        val message = field("message") ?: field("error_description") ?: field("msg") ?: field("error")
            ?: "Request failed ($code)"
        val friendly = when {
            code == 400 && message.contains("Invalid login credentials", true) -> "Incorrect e-mail or password."
            code == 401 -> "Your session has expired. Please sign in again."
            code == 429 -> "Too many requests. Please wait a moment and try again."
            code >= 500 -> "The server had a problem. Please try again shortly."
            else -> message
        }
        return ApiException(code, friendly, field("code"))
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    companion object {
        fun projectHost(url: String) = url.toHttpUrl().host
    }
}

/** RFC 7636 PKCE helpers. */
object Pkce {
    private val random = java.security.SecureRandom()

    fun verifier(): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        return base64Url(bytes)
    }

    fun challenge(verifier: String): String =
        base64Url(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, _, _ -> response.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
