package com.potpal.mirrortrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.potpal.mirrortrack.data.entities.DataPointEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DataPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MirrorDatabase : RoomDatabase() {

    abstract fun dataPointDao(): DataPointDao

    companion object {
        const val DB_NAME = "mirrortrack.db"

        /**
         * Build a Room instance backed by SQLCipher. The passphrase is the raw
         * key bytes produced by CryptoManager (Argon2id over the user-provided
         * passphrase + salt).
         *
         * CRITICAL: the caller is responsible for zeroing `rawKey` after this
         * factory consumes it. SupportOpenHelperFactory copies the bytes
         * internally, so the caller's copy can (and must) be wiped.
         *
         * Security notes:
         *  - No fallback to unencrypted. If rawKey is wrong, openDatabase throws.
         *  - SQLCipher's default KDF iter count is fine; we do heavy Argon2id
         *    derivation *before* handing the key to SQLCipher, so SQLCipher's
         *    built-in PBKDF2 runs on already-high-entropy input.
         *  - We explicitly call System.loadLibrary("sqlcipher") once via
         *    net.zetetic.database.sqlcipher.SQLiteDatabase.loadLibs(context)
         *    in MirrorTrackApp.onCreate.
         */
        fun build(context: Context, rawKey: ByteArray): MirrorDatabase {
            val factory = SupportOpenHelperFactory(rawKey, null, false)
            return Room.databaseBuilder(
                context.applicationContext,
                MirrorDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
