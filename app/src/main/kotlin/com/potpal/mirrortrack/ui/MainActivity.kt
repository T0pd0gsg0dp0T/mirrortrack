package com.potpal.mirrortrack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.ui.theme.MirrorTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var databaseHolder: DatabaseHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MirrorTrackTheme {
                MirrorTrackNavHost(isDbOpen = databaseHolder.isOpen())
            }
        }
    }
}
