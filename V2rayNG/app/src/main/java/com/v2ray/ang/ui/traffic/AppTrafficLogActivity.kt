package com.v2ray.ang.ui.traffic

import android.app.Application
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.dto.entities.AppTrafficLogEntry
import com.v2ray.ang.handler.AppTrafficLogManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppTrafficLogActivity : BaseComponentActivity() {
    private val model: AppTrafficLogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    AppTrafficLogManager.requestRefresh(applicationContext)
                    model.refresh()
                    delay(5_000L) // Signal a native snapshot; the UI reads only local aggregates.
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val rows by model.rows.collectAsStateWithLifecycle()
        val supported = AppTrafficLogManager.isSupported()
        val context = LocalContext.current
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.mobiletina_app_traffic_log),
                    onBackClick = { finish() },
                    actions = {
                        IconButton(onClick = {
                            model.clear { toastError(R.string.toast_failure) }
                        }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.mobiletina_log_clear))
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!supported) {
                    Text(stringResource(R.string.mobiletina_log_android_required), modifier = Modifier.padding(20.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.key }) { row ->
                        Text(
                            text = "${row.label} : ${Formatter.formatShortFileSize(context, row.bytes)}",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

class AppTrafficLogViewModel(application: Application) : AndroidViewModel(application) {
    private val updates = Mutex()
    private val _rows = MutableStateFlow<List<AppTrafficLogEntry>>(emptyList())
    internal val rows = _rows.asStateFlow()

    suspend fun refresh() = updates.withLock { readSnapshot() }

    private suspend fun readSnapshot() = withContext(Dispatchers.IO) {
        _rows.value = AppTrafficLogManager.entries()
    }

    fun clear(onFailure: () -> Unit) {
        viewModelScope.launch {
            try {
                updates.withLock {
                    withContext(Dispatchers.IO) { AppTrafficLogManager.clearHistory(getApplication<Application>()) }
                    readSnapshot()
                }
            } catch (_: Exception) { onFailure() }
        }
    }
}
