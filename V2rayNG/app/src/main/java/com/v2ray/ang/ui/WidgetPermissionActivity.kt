package com.v2ray.ang.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.v2ray.ang.core.LauncherManager

/** Transparent consent bridge; never inflates MainActivity or shows the product screen. */
class WidgetPermissionActivity : ComponentActivity() {
    private var cancelled = false
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!cancelled && !isFinishing && result.resultCode == RESULT_OK) {
            LauncherManager.startSmartService(this, toggle = true)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val permission = VpnService.prepare(this)
        if (permission == null) {
            LauncherManager.startSmartService(this, toggle = true)
            finish()
        } else consent.launch(permission)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A second widget tap while consent is open cancels the outstanding request.
        cancelled = true
        finish()
    }
}
