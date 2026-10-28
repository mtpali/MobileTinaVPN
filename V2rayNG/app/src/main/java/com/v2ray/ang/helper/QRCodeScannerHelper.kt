package com.v2ray.ang.helper

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.v2ray.ang.ui.ScannerActivity

/**
 * Helper for scanning QR codes.
 *
 * This class encapsulates the logic for launching the QR code scanner activity
 * and handling the scan result.
 */
class QRCodeScannerHelper(private val activity: ComponentActivity) {
    private var scanCallback: ((String?) -> Unit)? = null

    private val scanLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = scanCallback
            scanCallback = null
            val text = if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra("SCAN_RESULT") else null
            callback?.invoke(text)
        }

    /**
     * Launch the QR code scanner activity.
     *
     * @param onResult Callback invoked with the scan result (null if cancelled or failed)
     */
    fun launch(onResult: (String?) -> Unit) {
        scanCallback = onResult
        scanLauncher.launch(Intent(activity, ScannerActivity::class.java))
    }
}
