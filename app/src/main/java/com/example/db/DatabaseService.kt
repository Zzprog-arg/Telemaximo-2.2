package com.example.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.Calendar
import java.util.Date
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class UserAccount(
    val id: Int,
    val username: String,
    val password: String,
    val estado: String,          // "active" or "disabled"
    val expiresAt: Timestamp?,
    val maxDevices: Int,
    val lastLogin: Timestamp?,
    val isAdmin: Boolean,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?
)

sealed class LoginResult {
    data class Success(val user: UserAccount) : LoginResult()
    object WrongCredentials : LoginResult()
    object Disabled : LoginResult()
    object Expired : LoginResult()
    data class Error(val message: String) : LoginResult()
}

data class SupabaseResponse(val code: Int, val text: String)

object DatabaseService {
    private const val TAG = "IPTV_DB_SERVICE"
    
    // Default mode: "supabase" as requested. Fully API-compatible, native over HTTPS.
    // Falls back to "local" (SQLite native) or "remote" (MySQL raw JDBC).
    private var dbMode = "supabase"
    
    // MySQL Raw Settings
    private var dHost = "zephyr.proxy.rlwy.net"
    private var dPort = "45569"
    private var dUser = "root"
    private var dPass = "FLUnZYlYdxCwDBjmiJZyMnLywkTLgeJG"
    private var dDb = "railway"
    
    // Supabase REST API Settings
    private var supabaseUrl = "https://kcwkbobwcnifkejnamsa.supabase.co"
    private var supabaseKey = "sb_publishable_TfMAybeB4n5TJFoUIKjRyQ_Fc0wI9ZL"
    
    private var appContext: Context? = null

    init {
        // Force-load the JDBC driver class on startup to support alternative MySQL JDBC fallback
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed loading com.mysql.cj.jdbc.Driver, trying legacy driver", t)
            try {
                Class.forName("com.mysql.jdbc.Driver")
            } catch (tx: Throwable) {
                Log.e(TAG, "Legacy driver load failed too", tx)
            }
        }
    }

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        dbMode = prefs.getString("db_mode", "supabase") ?: "supabase"
        dHost = prefs.getString("db_host", "zephyr.proxy.rlwy.net") ?: "zephyr.proxy.rlwy.net"
        dPort = prefs.getString("db_port", "45569") ?: "45569"
        dUser = prefs.getString("db_user", "root") ?: "root"
        dPass = prefs.getString("db_pass", "FLUnZYlYdxCwDBjmiJZyMnLywkTLgeJG") ?: "FLUnZYlYdxCwDBjmiJZyMnLywkTLgeJG"
        dDb = prefs.getString("db_name", "railway") ?: "railway"
        supabaseUrl = prefs.getString("supabase_url", "https://kcwkbobwcnifkejnamsa.supabase.co") ?: "https://kcwkbobwcnifkejnamsa.supabase.co"
        supabaseKey = prefs.getString("supabase_key", "sb_publishable_TfMAybeB4n5TJFoUIKjRyQ_Fc0wI9ZL") ?: "sb_publishable_TfMAybeB4n5TJFoUIKjRyQ_Fc0wI9ZL"
        appContext = context.applicationContext
        Log.d(TAG, "Loaded DB settings. Mode: $dbMode, Supabase: $supabaseUrl, MySQL: $dHost:$dPort")
    }

    fun getDbMode(): String {
        return dbMode
    }

    fun getSupabaseUrl(): String = supabaseUrl
    fun getSupabaseKey(): String = supabaseKey

    fun saveSettings(context: Context, host: String, port: String, user: String, pass: String, db: String) {
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("db_host", host.trim())
            .putString("db_port", port.trim())
            .putString("db_user", user.trim())
            .putString("db_pass", pass)
            .putString("db_name", db.trim())
            .apply()
        
        dHost = host.trim()
        dPort = port.trim()
        dUser = user.trim()
        dPass = pass
        dDb = db.trim()
        appContext = context.applicationContext
        Log.d(TAG, "Saved remote DB settings: $dHost:$dPort/$dDb")
    }

    fun saveSupabaseSettings(context: Context, url: String, key: String) {
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("supabase_url", url.trim())
            .putString("supabase_key", key.trim())
            .apply()
        
        supabaseUrl = url.trim()
        supabaseKey = key.trim()
        appContext = context.applicationContext
        Log.d(TAG, "Saved Supabase API settings: $supabaseUrl")
    }

    fun saveDbMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("db_mode", mode.trim()).apply()
        dbMode = mode.trim()
        appContext = context.applicationContext
        Log.d(TAG, "Saved local db mode preference: $dbMode")
    }

    private fun getConnection(): Connection {
        val url = "jdbc:mysql://$dHost:$dPort/$dDb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=3000&socketTimeout=3000"
        return DriverManager.getConnection(url, dUser, dPass)
    }

    // --- Supabase REST API helper functions ---
    private fun performSupabaseRequest(
        method: String,
        path: String,
        body: String? = null
    ): SupabaseResponse {
        var conn: HttpURLConnection? = null
        try {
            val urlStr = if (supabaseUrl.endsWith("/")) {
                "${supabaseUrl}rest/v1$path"
            } else {
                "$supabaseUrl/rest/v1$path"
            }
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            // Core headers for Supabase API auth and resolution
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            
            if (body != null && (method == "POST" || method == "PATCH" || method == "PUT")) {
                conn.doOutput = true
                conn.outputStream.use { os ->
                    val input = body.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }
            }
            
            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            return SupabaseResponse(code, responseText)
        } catch (e: Throwable) {
            Log.e(TAG, "Supabase HTTP Request error: $method $path", e)
            return SupabaseResponse(-1, e.localizedMessage ?: "Network connectivity error")
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTimestamp(str: String?): java.sql.Timestamp? {
        if (str.isNullOrEmpty() || str == "null") return null
        try {
            // Postgres stores with T and Z separators - normalize to yyyy-MM-dd HH:mm:ss
            val cleanStr = str.replace("T", " ").replace("Z", "")
            // Separate nanoseconds/milliseconds fragment if present
            val parts = cleanStr.split(".")
            val datePart = parts[0]
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val parsed = format.parse(datePart)
            if (parsed != null) {
                return java.sql.Timestamp(parsed.time)
            }
        } catch (e: Throwable) {
            // Fallback: check if it's numeric timestamp in ms
            try {
                return java.sql.Timestamp(str.toLong())
            } catch (ignored: Throwable) {}
        }
        return null
    }

    private fun formatTimestamp(ts: java.sql.Timestamp?): String {
        if (ts == null) return "null"
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(Date(ts.time))
    }

    private fun parseUserFromJson(obj: JSONObject): UserAccount {
        val id = obj.optInt("id", 0)
        val username = obj.optString("username", "")
        val password = obj.optString("password", "")
        val estado = obj.optString("estado", "active")
        val expiresAt = parseTimestamp(obj.optString("expires_at", ""))
        val maxDevices = obj.optInt("max_devices", 1)
        val lastLogin = parseTimestamp(obj.optString("last_login", ""))
        val isAdminStr = obj.optString("is_admin", "false")
        val isAdmin = obj.optBoolean("is_admin", false) || 
                      obj.optInt("is_admin", 0) == 1 || 
                      isAdminStr.equals("true", ignoreCase = true) || 
                      isAdminStr == "1"
        val createdAt = parseTimestamp(obj.optString("created_at", ""))
        val updatedAt = parseTimestamp(obj.optString("updated_at", ""))
        
        return UserAccount(
            id = id,
            username = username,
            password = password,
            estado = estado,
            expiresAt = expiresAt,
            maxDevices = maxDevices,
            lastLogin = lastLogin,
            isAdmin = isAdmin,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    suspend fun getM3uUrl(context: Context): String = withContext(Dispatchers.IO) {
        val defaultUrl = "https://raw.githubusercontent.com/Zzprog-arg/uwu.m3u/fd5dba6c9f6d8cfcccf345aa5c22b71071bee47f/lista2.m3u"
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString("local_m3u_url_cache", defaultUrl) ?: defaultUrl
        
        if (dbMode == "local") {
            try {
                val helper = LocalDatabaseHelper(context)
                val url = helper.getSettingLocal("m3u_url", defaultUrl)
                if (url != cachedUrl) {
                    prefs.edit().putString("local_m3u_url_cache", url).apply()
                }
                return@withContext url
            } catch (t: Throwable) {
                Log.e(TAG, "Local setting fetch failed", t)
                return@withContext cachedUrl
            }
        } else if (dbMode == "supabase") {
            try {
                val res = performSupabaseRequest("GET", "/settings?key_name=eq.m3u_url")
                if (res.code in 200..299) {
                    val array = JSONArray(res.text)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val url = obj.optString("val_value", defaultUrl)
                        if (url.isNotBlank()) {
                            prefs.edit().putString("local_m3u_url_cache", url).apply()
                            return@withContext url
                        }
                    } else {
                        val body = JSONObject().apply {
                            put("key_name", "m3u_url")
                            put("val_value", defaultUrl)
                        }.toString()
                        performSupabaseRequest("POST", "/settings", body)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase settings fetch failed", t)
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            var rs: ResultSet? = null
            try {
                conn = getConnection()
                val createQuery = """
                    CREATE TABLE IF NOT EXISTS settings (
                        key_name VARCHAR(150) PRIMARY KEY,
                        val_value TEXT
                    )
                """.trimIndent()
                val createStmt = conn.prepareStatement(createQuery)
                createStmt.executeUpdate()
                createStmt.close()

                val query = "SELECT val_value FROM settings WHERE key_name = ?"
                stmt = conn.prepareStatement(query)
                stmt.setString(1, "m3u_url")
                rs = stmt.executeQuery()
                if (rs.next()) {
                    val url = rs.getString("val_value")
                    if (!url.isNullOrBlank()) {
                        prefs.edit().putString("local_m3u_url_cache", url).apply()
                        return@withContext url
                    }
                } else {
                    val insertQuery = "INSERT INTO settings (key_name, val_value) VALUES (?, ?)"
                    val insertStmt = conn.prepareStatement(insertQuery)
                    insertStmt.setString(1, "m3u_url")
                    insertStmt.setString(2, defaultUrl)
                    insertStmt.executeUpdate()
                    insertStmt.close()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "MySQL settings fetch failed", e)
            } finally {
                try { rs?.close() } catch (ignored: Throwable) {}
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
        return@withContext cachedUrl
    }

    suspend fun updateM3uUrl(context: Context, newUrl: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("local_m3u_url_cache", newUrl).apply()
        
        if (dbMode == "local") {
            try {
                val helper = LocalDatabaseHelper(context)
                return@withContext helper.saveSettingLocal("m3u_url", newUrl.trim())
            } catch (t: Throwable) {
                Log.e(TAG, "Local setting update failed", t)
                return@withContext false
            }
        } else if (dbMode == "supabase") {
            try {
                val getRes = performSupabaseRequest("GET", "/settings?key_name=eq.m3u_url")
                if (getRes.code in 200..299) {
                    val array = JSONArray(getRes.text)
                    if (array.length() > 0) {
                        val body = JSONObject().apply {
                            put("val_value", newUrl.trim())
                        }.toString()
                        val patchRes = performSupabaseRequest("PATCH", "/settings?key_name=eq.m3u_url", body)
                        return@withContext patchRes.code in 200..299
                    } else {
                        val body = JSONObject().apply {
                            put("key_name", "m3u_url")
                            put("val_value", newUrl.trim())
                        }.toString()
                        val postRes = performSupabaseRequest("POST", "/settings", body)
                        return@withContext postRes.code in 200..299
                    }
                } else {
                    val body = JSONObject().apply {
                        put("key_name", "m3u_url")
                        put("val_value", newUrl.trim())
                    }.toString()
                    val postRes = performSupabaseRequest("POST", "/settings", body)
                    return@withContext postRes.code in 200..299
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase setting update failed", t)
                return@withContext false
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            try {
                conn = getConnection()
                val createQuery = """
                    CREATE TABLE IF NOT EXISTS settings (
                        key_name VARCHAR(150) PRIMARY KEY,
                        val_value TEXT
                    )
                """.trimIndent()
                val createStmt = conn.prepareStatement(createQuery)
                createStmt.executeUpdate()
                createStmt.close()

                val query = "INSERT INTO settings (key_name, val_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE val_value = ?"
                stmt = conn.prepareStatement(query)
                stmt.setString(1, "m3u_url")
                stmt.setString(2, newUrl.trim())
                stmt.setString(3, newUrl.trim())
                return@withContext stmt.executeUpdate() >= 1
            } catch (e: Throwable) {
                Log.e(TAG, "MySQL setting update failed", e)
                return@withContext false
            } finally {
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }

    // Auto-create / check database elements
    suspend fun initializeDb(): Boolean = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            Log.d(TAG, "DB mode is LOCAL. Initializing local SQLite helper.")
            try {
                val ctx = appContext ?: return@withContext false
                val helper = LocalDatabaseHelper(ctx)
                val db = helper.writableDatabase
                db.close()
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Error initializing local SQLite database", t)
                false
            }
        } else if (dbMode == "supabase") {
            Log.d(TAG, "DB mode is SUPABASE. Reaching endpoint: $supabaseUrl")
            try {
                // Since PostgREST cannot execute DDL tables natively over raw REST,
                // we check connectivity to our schema, and seed default rows if table exists but empty.
                val testRes = performSupabaseRequest("GET", "/users?select=id&limit=1")
                if (testRes.code == 404 || (testRes.code == 400 && testRes.text.contains("relation"))) {
                    Log.e(TAG, "Supabase table 'users' does not exist yet.")
                    // Inform calling code about missing table
                    return@withContext false
                }
                
                if (testRes.code !in 200..299) {
                    Log.e(TAG, "Supabase connection error. Code: ${testRes.code}, Response: ${testRes.text}")
                    return@withContext false
                }
                
                // Seed default user values if totally empty for easy immediate setup
                val array = JSONArray(testRes.text)
                if (array.length() == 0) {
                    Log.d(TAG, "Seeding default admin and user accounts into newly-linked Supabase table.")
                    val seedBody = """
                        [
                          {"username":"admin", "password":"admin123", "estado":"active", "expires_at":"2030-01-01T00:00:00Z", "max_devices":5, "is_admin":true},
                          {"username":"demo", "password":"demo123", "estado":"active", "expires_at":"2027-12-31T23:59:59Z", "max_devices":2, "is_admin":false},
                          {"username":"vencido", "password":"vencido123", "estado":"active", "expires_at":"2024-01-01T00:00:00Z", "max_devices":1, "is_admin":false},
                          {"username":"desactivado", "password":"desactivado123", "estado":"disabled", "expires_at":"2030-01-01T00:00:00Z", "max_devices":1, "is_admin":false}
                        ]
                    """.trimIndent()
                    performSupabaseRequest("POST", "/users", seedBody)
                }
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed initializing Supabase API connection", t)
                false
            }
        } else {
            Log.d(TAG, "DB mode is REMOTE. Initializing remote MySQL on Railway.")
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            try {
                conn = getConnection()
                
                val createQuery = """
                    CREATE TABLE IF NOT EXISTS users (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) UNIQUE NOT NULL,
                        password VARCHAR(100) NOT NULL,
                        estado VARCHAR(20) DEFAULT 'active',
                        expires_at DATETIME,
                        max_devices INT DEFAULT 1,
                        is_admin TINYINT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        last_login DATETIME
                    )
                """.trimIndent()
                
                stmt = conn.prepareStatement(createQuery)
                stmt.executeUpdate()
                stmt.close()

                val checkEmptyQuery = "SELECT COUNT(*) FROM users"
                stmt = conn.prepareStatement(checkEmptyQuery)
                val rs = stmt.executeQuery()
                var count = 0
                if (rs.next()) {
                    count = rs.getInt(1)
                }
                rs.close()
                stmt.close()

                if (count == 0) {
                    val seedQuery = """
                        INSERT INTO users (username, password, estado, expires_at, max_devices, is_admin)
                        VALUES 
                        ('admin', 'admin123', 'active', '2030-01-01 00:00:00', 5, 1),
                        ('demo', 'demo123', 'active', '2027-12-31 23:59:59', 2, 0),
                        ('vencido', 'vencido123', 'active', '2024-01-01 00:00:00', 1, 0),
                        ('desactivado', 'desactivado123', 'disabled', '2030-01-01 00:00:00', 1, 0)
                    """.trimIndent()
                    stmt = conn.prepareStatement(seedQuery)
                    stmt.executeUpdate()
                }
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Error during Remote MySQL DB initialization", e)
                false
            } finally {
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }

    // Authenticate user credentials
    suspend fun login(usernameInput: String, passwordInput: String): LoginResult = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            val ctx = appContext ?: return@withContext LoginResult.Error("Contexto de la app no inicializado")
            val helper = LocalDatabaseHelper(ctx)
            helper.loginLocal(usernameInput, passwordInput)
        } else if (dbMode == "supabase") {
            try {
                val encodedUser = URLEncoder.encode(usernameInput.trim(), "UTF-8")
                val res = performSupabaseRequest("GET", "/users?username=eq.$encodedUser")
                if (res.code !in 200..299) {
                    return@withContext LoginResult.Error("API Supabase falló (${res.code}): ${res.text}")
                }
                
                val array = JSONArray(res.text)
                if (array.length() == 0) {
                    return@withContext LoginResult.WrongCredentials
                }
                
                val userObj = array.getJSONObject(0)
                val user = parseUserFromJson(userObj)
                
                if (user.password != passwordInput.trim()) {
                    return@withContext LoginResult.WrongCredentials
                }
                
                if (user.estado.lowercase() == "disabled") {
                    return@withContext LoginResult.Disabled
                } else if (user.expiresAt != null && user.expiresAt.before(Date())) {
                    return@withContext LoginResult.Expired
                } else {
                    // Update last login timestamp asynchronously
                    val nowStr = formatTimestamp(Timestamp(System.currentTimeMillis()))
                    val patchPayload = "{\"last_login\": \"$nowStr\"}"
                    performSupabaseRequest("PATCH", "/users?id=eq.${user.id}", patchPayload)
                    
                    return@withContext LoginResult.Success(user.copy(lastLogin = Timestamp(System.currentTimeMillis())))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase login exception", t)
                return@withContext LoginResult.Error("Error: ${t.localizedMessage}")
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            var rs: ResultSet? = null
            try {
                conn = getConnection()
                val query = "SELECT * FROM users WHERE username = ?"
                stmt = conn.prepareStatement(query)
                stmt.setString(1, usernameInput.trim())
                rs = stmt.executeQuery()

                if (!rs.next()) {
                    LoginResult.WrongCredentials
                } else {
                    val dbPass = rs.getString("password")
                    if (dbPass != passwordInput) {
                        LoginResult.WrongCredentials
                    } else {
                        val user = UserAccount(
                            id = rs.getInt("id"),
                            username = rs.getString("username"),
                            password = dbPass,
                            estado = rs.getString("estado") ?: "active",
                            expiresAt = rs.getTimestamp("expires_at"),
                            maxDevices = rs.getInt("max_devices"),
                            lastLogin = rs.getTimestamp("last_login"),
                            isAdmin = rs.getInt("is_admin") == 1 || rs.getBoolean("is_admin") || rs.getString("is_admin")?.equals("true", ignoreCase = true) == true,
                            createdAt = rs.getTimestamp("created_at"),
                            updatedAt = rs.getTimestamp("updated_at")
                        )

                        if (user.estado.lowercase() == "disabled") {
                            LoginResult.Disabled
                        } else if (user.expiresAt != null && user.expiresAt.before(Date())) {
                            LoginResult.Expired
                        } else {
                            val updateAccessQuery = "UPDATE users SET last_login = NOW() WHERE id = ?"
                            val updateStmt = conn.prepareStatement(updateAccessQuery)
                            updateStmt.setInt(1, user.id)
                            updateStmt.executeUpdate()
                            updateStmt.close()

                            LoginResult.Success(user.copy(lastLogin = Timestamp(System.currentTimeMillis())))
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Remote connection login failed", e)
                LoginResult.Error("Error MySQL: ${e.localizedMessage}")
            } finally {
                try { rs?.close() } catch (ignored: Throwable) {}
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }

    // List all users
    suspend fun getAllUsers(): List<UserAccount> = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            val ctx = appContext ?: return@withContext emptyList<UserAccount>()
            val helper = LocalDatabaseHelper(ctx)
            helper.getAllUsersLocal()
        } else if (dbMode == "supabase") {
            val usersList = mutableListOf<UserAccount>()
            try {
                val res = performSupabaseRequest("GET", "/users?order=id.desc")
                if (res.code in 200..299) {
                    val array = JSONArray(res.text)
                    for (i in 0 until array.length()) {
                        usersList.add(parseUserFromJson(array.getJSONObject(i)))
                    }
                } else {
                    Log.e(TAG, "Failed to get users from Supabase: ${res.code} - ${res.text}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase list users exception", t)
            }
            usersList
        } else {
            val usersList = mutableListOf<UserAccount>()
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            var rs: ResultSet? = null
            try {
                conn = getConnection()
                val query = "SELECT * FROM users ORDER BY id DESC"
                stmt = conn.prepareStatement(query)
                rs = stmt.executeQuery()
                while (rs.next()) {
                    usersList.add(
                        UserAccount(
                            id = rs.getInt("id"),
                            username = rs.getString("username"),
                            password = rs.getString("password"),
                            estado = rs.getString("estado") ?: "active",
                            expiresAt = rs.getTimestamp("expires_at"),
                            maxDevices = rs.getInt("max_devices"),
                            lastLogin = rs.getTimestamp("last_login"),
                            isAdmin = rs.getInt("is_admin") == 1 || rs.getBoolean("is_admin") || rs.getString("is_admin")?.equals("true", ignoreCase = true) == true,
                            createdAt = rs.getTimestamp("created_at"),
                            updatedAt = rs.getTimestamp("updated_at")
                        )
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed fetching remote user accounts", e)
            } finally {
                try { rs?.close() } catch (ignored: Throwable) {}
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
            usersList
        }
    }

    // Create user accounts
    suspend fun createUser(user: UserAccount): Boolean = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            val ctx = appContext ?: return@withContext false
            val helper = LocalDatabaseHelper(ctx)
            helper.createUserLocal(user)
        } else if (dbMode == "supabase") {
            try {
                val bodyObj = JSONObject().apply {
                    put("username", user.username.trim())
                    put("password", user.password.trim())
                    put("estado", user.estado)
                    put("expires_at", if (user.expiresAt != null) formatTimestamp(user.expiresAt) else null)
                    put("max_devices", user.maxDevices)
                    put("is_admin", user.isAdmin)
                }
                val res = performSupabaseRequest("POST", "/users", bodyObj.toString())
                res.code in 200..299
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase user creation failed", t)
                false
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            try {
                conn = getConnection()
                val query = """
                    INSERT INTO users (username, password, estado, expires_at, max_devices, is_admin)
                    VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
                stmt = conn.prepareStatement(query)
                stmt.setString(1, user.username.trim())
                stmt.setString(2, user.password)
                stmt.setString(3, user.estado)
                stmt.setTimestamp(4, user.expiresAt)
                stmt.setInt(5, user.maxDevices)
                stmt.setInt(6, if (user.isAdmin) 1 else 0)
                stmt.executeUpdate() == 1
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Remote creation failed", e)
                false
            } finally {
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }

    // Update user information
    suspend fun updateUser(user: UserAccount): Boolean = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            val ctx = appContext ?: return@withContext false
            val helper = LocalDatabaseHelper(ctx)
            helper.updateUserLocal(user)
        } else if (dbMode == "supabase") {
            try {
                val bodyObj = JSONObject().apply {
                    put("username", user.username.trim())
                    put("password", user.password.trim())
                    put("estado", user.estado)
                    put("expires_at", if (user.expiresAt != null) formatTimestamp(user.expiresAt) else null)
                    put("max_devices", user.maxDevices)
                    put("is_admin", user.isAdmin)
                }
                val res = performSupabaseRequest("PATCH", "/users?id=eq.${user.id}", bodyObj.toString())
                res.code in 200..299
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase user update failed", t)
                false
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            try {
                conn = getConnection()
                val query = """
                    UPDATE users 
                    SET username = ?, password = ?, estado = ?, expires_at = ?, max_devices = ?, is_admin = ?
                    WHERE id = ?
                """.trimIndent()
                stmt = conn.prepareStatement(query)
                stmt.setString(1, user.username.trim())
                stmt.setString(2, user.password)
                stmt.setString(3, user.estado)
                stmt.setTimestamp(4, user.expiresAt)
                stmt.setInt(5, user.maxDevices)
                stmt.setInt(6, if (user.isAdmin) 1 else 0)
                stmt.setInt(7, user.id)
                stmt.executeUpdate() == 1
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Remote update failed", e)
                false
            } finally {
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }

    // Delete accounts
    suspend fun deleteUser(userId: Int): Boolean = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            val ctx = appContext ?: return@withContext false
            val helper = LocalDatabaseHelper(ctx)
            helper.deleteUserLocal(userId)
        } else if (dbMode == "supabase") {
            try {
                val res = performSupabaseRequest("DELETE", "/users?id=eq.$userId")
                res.code in 200..299
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase user deletion failed", t)
                false
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            try {
                conn = getConnection()
                val query = "DELETE FROM users WHERE id = ?"
                stmt = conn.prepareStatement(query)
                stmt.setInt(1, userId)
                stmt.executeUpdate() == 1
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Remote deletion failed", e)
                false
            } finally {
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }

    // Change duration
    suspend fun changeSubscriptionDuration(userId: Int, monthsDiff: Int): Boolean = withContext(Dispatchers.IO) {
        if (dbMode == "local") {
            val ctx = appContext ?: return@withContext false
            val helper = LocalDatabaseHelper(ctx)
            helper.changeSubscriptionDurationLocal(userId, monthsDiff)
        } else if (dbMode == "supabase") {
            try {
                val fetchRes = performSupabaseRequest("GET", "/users?id=eq.$userId")
                if (fetchRes.code !in 200..299) return@withContext false
                
                val array = JSONArray(fetchRes.text)
                if (array.length() == 0) return@withContext false
                
                val userObj = array.getJSONObject(0)
                val user = parseUserFromJson(userObj)
                
                val calendar = Calendar.getInstance()
                val now = Date()
                val baseDate = if (user.expiresAt != null && user.expiresAt.after(now)) {
                    user.expiresAt
                } else {
                    now
                }
                calendar.time = baseDate
                calendar.add(Calendar.MONTH, monthsDiff)
                
                val newExpires = Timestamp(calendar.timeInMillis)
                val payload = JSONObject().apply {
                    put("expires_at", formatTimestamp(newExpires))
                }.toString()
                
                val patchRes = performSupabaseRequest("PATCH", "/users?id=eq.$userId", payload)
                patchRes.code in 200..299
            } catch (t: Throwable) {
                Log.e(TAG, "Supabase duration update failed", t)
                false
            }
        } else {
            var conn: Connection? = null
            var stmt: PreparedStatement? = null
            var rs: ResultSet? = null
            try {
                conn = getConnection()
                
                val getQuery = "SELECT expires_at FROM users WHERE id = ?"
                stmt = conn.prepareStatement(getQuery)
                stmt.setInt(1, userId)
                rs = stmt.executeQuery()
                
                var currentExpires: Date? = null
                if (rs.next()) {
                    currentExpires = rs.getTimestamp("expires_at")
                }
                rs.close()
                stmt.close()
                
                val calendar = Calendar.getInstance()
                val now = Date()
                
                val baseDate = if (currentExpires != null && currentExpires.after(now)) {
                    currentExpires
                } else {
                    now
                }
                
                calendar.time = baseDate
                calendar.add(Calendar.MONTH, monthsDiff)
                val newExpiresStr = Timestamp(calendar.timeInMillis)
                
                val updateQuery = "UPDATE users SET expires_at = ? WHERE id = ?"
                stmt = conn.prepareStatement(updateQuery)
                stmt.setTimestamp(1, newExpiresStr)
                stmt.setInt(2, userId)
                stmt.executeUpdate() == 1
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed updating remote subscription duration", e)
                false
            } finally {
                try { rs?.close() } catch (ignored: Throwable) {}
                try { stmt?.close() } catch (ignored: Throwable) {}
                try { conn?.close() } catch (ignored: Throwable) {}
            }
        }
    }
}
