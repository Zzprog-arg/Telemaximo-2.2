package com.example.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.sql.Timestamp
import java.util.Calendar
import java.util.Date

class LocalDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val TAG = "IPTV_SQLITE"
        private const val DATABASE_NAME = "iptv_local.db"
        private const val DATABASE_VERSION = 3

        const val TABLE_USERS = "users"
        const val COLUMN_ID = "id"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_ESTADO = "estado"
        const val COLUMN_EXPIRES_AT = "expires_at"
        const val COLUMN_MAX_DEVICES = "max_devices"
        const val COLUMN_IS_ADMIN = "is_admin"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_LAST_LOGIN = "last_login"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT UNIQUE NOT NULL,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_ESTADO TEXT DEFAULT 'active',
                $COLUMN_EXPIRES_AT INTEGER,
                $COLUMN_MAX_DEVICES INTEGER DEFAULT 1,
                $COLUMN_IS_ADMIN INTEGER DEFAULT 0,
                $COLUMN_CREATED_AT INTEGER,
                $COLUMN_UPDATED_AT INTEGER,
                $COLUMN_LAST_LOGIN INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)

        val createSettingsTable = """
            CREATE TABLE IF NOT EXISTS settings (
                key_name TEXT PRIMARY KEY,
                val_value TEXT
            )
        """.trimIndent()
        db.execSQL(createSettingsTable)

        // Seed default settings
        val cvSetting = ContentValues().apply {
            put("key_name", "m3u_url")
            put("val_value", "https://raw.githubusercontent.com/Zzprog-arg/uwu.m3u/fd5dba6c9f6d8cfcccf345aa5c22b71071bee47f/lista2.m3u")
        }
        db.insert("settings", null, cvSetting)

        // Seed default credentials with standard demo user schemas
        val now = System.currentTimeMillis()
        val future2030 = 1893456000000L // 2030-01-01
        val future2027 = 1830211200000L // 2027-12-31
        val past2024 = 1704067200000L   // 2024-01-01

        insertUser(db, "admin", "admin123", "active", future2030, 5, 1, now)
        insertUser(db, "demo", "demo123", "active", future2027, 2, 0, now)
        insertUser(db, "vencido", "vencido123", "active", past2024, 1, 0, now)
        insertUser(db, "desactivado", "desactivado123", "disabled", future2030, 1, 0, now)
        Log.d(TAG, "Local SQLite tables seeded with admin and sample users.")
    }

    private fun insertUser(db: SQLiteDatabase, user: String, pass: String, estado: String, expires: Long, maxDevices: Int, isAdmin: Int, now: Long) {
        val cv = ContentValues().apply {
            put(COLUMN_USERNAME, user)
            put(COLUMN_PASSWORD, pass)
            put(COLUMN_ESTADO, estado)
            put(COLUMN_EXPIRES_AT, expires)
            put(COLUMN_MAX_DEVICES, maxDevices)
            put(COLUMN_IS_ADMIN, isAdmin)
            put(COLUMN_CREATED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        db.insert(TABLE_USERS, null, cv)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // --- Data Handlers corresponding exactly to DatabaseService APIs ---

    fun loginLocal(usernameInput: String, passwordInput: String): LoginResult {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_USERNAME = ?",
            arrayOf(usernameInput.trim()),
            null,
            null,
            null
        )
        try {
            if (!cursor.moveToFirst()) {
                return LoginResult.WrongCredentials
            }
            val dbPass = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD))
            if (dbPass != passwordInput) {
                return LoginResult.WrongCredentials
            }

            val expiresValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_EXPIRES_AT))) null
                                else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_EXPIRES_AT))
            val expiresVal = expiresValInt?.let { Timestamp(it) }

            val lastLoginValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_LAST_LOGIN))) null
                                  else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_LOGIN))
            val lastLoginVal = lastLoginValInt?.let { Timestamp(it) }

            val createdValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))) null
                                else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            val createdVal = createdValInt?.let { Timestamp(it) }

            val updatedValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))) null
                                else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
            val updatedVal = updatedValInt?.let { Timestamp(it) }

            val user = UserAccount(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                password = dbPass,
                estado = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ESTADO)) ?: "active",
                expiresAt = expiresVal,
                maxDevices = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MAX_DEVICES)),
                lastLogin = lastLoginVal,
                isAdmin = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ADMIN)) == 1,
                createdAt = createdVal,
                updatedAt = updatedVal
            )

            if (user.estado.lowercase() == "disabled") {
                return LoginResult.Disabled
            } else if (user.expiresAt != null && user.expiresAt.before(Date())) {
                return LoginResult.Expired
            } else {
                // Register access in SQLite database
                val writeDb = this.writableDatabase
                val cv = ContentValues().apply {
                    put(COLUMN_LAST_LOGIN, System.currentTimeMillis())
                }
                writeDb.update(TABLE_USERS, cv, "$COLUMN_ID = ?", arrayOf(user.id.toString()))
                return LoginResult.Success(user.copy(lastLogin = Timestamp(System.currentTimeMillis())))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Local login failed", t)
            return LoginResult.Error("Error Local: ${t.localizedMessage}")
        } finally {
            cursor.close()
            db.close()
        }
    }

    fun getAllUsersLocal(): List<UserAccount> {
        val list = mutableListOf<UserAccount>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_USERS, null, null, null, null, null, "$COLUMN_ID DESC")
        try {
            if (cursor.moveToFirst()) {
                do {
                    val expiresValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_EXPIRES_AT))) null
                                        else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_EXPIRES_AT))
                    val expiresVal = expiresValInt?.let { Timestamp(it) }

                    val lastLoginValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_LAST_LOGIN))) null
                                          else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_LOGIN))
                    val lastLoginVal = lastLoginValInt?.let { Timestamp(it) }

                    val createdValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))) null
                                        else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                    val createdVal = createdValInt?.let { Timestamp(it) }

                    val updatedValInt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))) null
                                        else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
                    val updatedVal = updatedValInt?.let { Timestamp(it) }

                    list.add(
                        UserAccount(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                            password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                            estado = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ESTADO)) ?: "active",
                            expiresAt = expiresVal,
                            maxDevices = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MAX_DEVICES)),
                            lastLogin = lastLoginVal,
                            isAdmin = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ADMIN)) == 1,
                            createdAt = createdVal,
                            updatedAt = updatedVal
                        )
                    )
                } while (cursor.moveToNext())
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Local fetch failed", t)
        } finally {
            cursor.close()
            db.close()
        }
        return list
    }

    fun createUserLocal(user: UserAccount): Boolean {
        val db = this.writableDatabase
        return try {
            val cv = ContentValues().apply {
                put(COLUMN_USERNAME, user.username.trim())
                put(COLUMN_PASSWORD, user.password)
                put(COLUMN_ESTADO, user.estado)
                put(COLUMN_EXPIRES_AT, user.expiresAt?.time)
                put(COLUMN_MAX_DEVICES, user.maxDevices)
                put(COLUMN_IS_ADMIN, if (user.isAdmin) 1 else 0)
                put(COLUMN_CREATED_AT, System.currentTimeMillis())
                put(COLUMN_UPDATED_AT, System.currentTimeMillis())
            }
            db.insertOrThrow(TABLE_USERS, null, cv) != -1L
        } catch (t: Throwable) {
            Log.e(TAG, "Local create user failed", t)
            false
        } finally {
            db.close()
        }
    }

    fun updateUserLocal(user: UserAccount): Boolean {
        val db = this.writableDatabase
        return try {
            val cv = ContentValues().apply {
                put(COLUMN_USERNAME, user.username.trim())
                put(COLUMN_PASSWORD, user.password)
                put(COLUMN_ESTADO, user.estado)
                put(COLUMN_EXPIRES_AT, user.expiresAt?.time)
                put(COLUMN_MAX_DEVICES, user.maxDevices)
                put(COLUMN_IS_ADMIN, if (user.isAdmin) 1 else 0)
                put(COLUMN_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_USERS, cv, "$COLUMN_ID = ?", arrayOf(user.id.toString())) > 0
        } catch (t: Throwable) {
            Log.e(TAG, "Local update user failed", t)
            false
        } finally {
            db.close()
        }
    }

    fun deleteUserLocal(userId: Int): Boolean {
        val db = this.writableDatabase
        return try {
            db.delete(TABLE_USERS, "$COLUMN_ID = ?", arrayOf(userId.toString())) > 0
        } catch (t: Throwable) {
            Log.e(TAG, "Local delete user failed", t)
            false
        } finally {
            db.close()
        }
    }

    fun changeSubscriptionDurationLocal(userId: Int, monthsDiff: Int): Boolean {
        val db = this.writableDatabase
        return try {
            val list = getAllUsersLocal()
            val user = list.firstOrNull { it.id == userId } ?: return false

            val calendar = Calendar.getInstance()
            val now = Date()
            val baseDate = if (user.expiresAt != null && user.expiresAt.after(now)) {
                user.expiresAt
            } else {
                now
            }

            calendar.time = baseDate
            calendar.add(Calendar.MONTH, monthsDiff)
            val newExpiresTime = calendar.timeInMillis

            val cv = ContentValues().apply {
                put(COLUMN_EXPIRES_AT, newExpiresTime)
                put(COLUMN_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_USERS, cv, "$COLUMN_ID = ?", arrayOf(userId.toString())) > 0
        } catch (t: Throwable) {
            Log.e(TAG, "Local subscription renewal failed", t)
            false
        } finally {
            db.close()
        }
    }

    fun getSettingLocal(key: String, defaultValue: String): String {
        val db = this.readableDatabase
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS settings (key_name TEXT PRIMARY KEY, val_value TEXT)")
            val cursor = db.query(
                "settings",
                arrayOf("val_value"),
                "key_name = ?",
                arrayOf(key),
                null,
                null,
                null
            )
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow("val_value"))
                }
            } finally {
                cursor.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get setting $key", t)
        } finally {
            db.close()
        }
        return defaultValue
    }

    fun saveSettingLocal(key: String, value: String): Boolean {
        val db = this.writableDatabase
        return try {
            db.execSQL("CREATE TABLE IF NOT EXISTS settings (key_name TEXT PRIMARY KEY, val_value TEXT)")
            val cv = ContentValues().apply {
                put("key_name", key)
                put("val_value", value)
            }
            db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save setting $key", t)
            false
        } finally {
            db.close()
        }
    }
}
