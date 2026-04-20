package com.potpal.mirrortrack.ui.feed

import androidx.lifecycle.ViewModel
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.entities.DataPointEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class LiveFeedViewModel @Inject constructor(
    private val dao: DataPointDao
) : ViewModel() {

    val recentPoints: Flow<List<DataPointEntity>> = dao.observeRecent(500)
}
