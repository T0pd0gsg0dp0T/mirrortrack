package com.potpal.mirrortrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.potpal.mirrortrack.collectors.personal.VoiceModelInstaller
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MirrorTrackApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Load the native SQLCipher libraries. This must happen exactly once,
        // before any SQLCipher database is opened. Safe to call from Application.
        System.loadLibrary("sqlcipher")
        appScope.launch {
            VoiceModelInstaller.ensureInstalled(this@MirrorTrackApp)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
