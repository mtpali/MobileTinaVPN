package com.v2ray.ang.ui

import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MobileTinaStoreAboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobiletina_store_about)

        // Match the approved MobileTina store page: no visible toolbar/back button.
        // Android system back remains available.
        findViewById<View>(R.id.toolbar_store_about)?.visibility = View.GONE

        findViewById<View>(R.id.card_instagram_1).setOnClickListener {
            Utils.openUri(this, RuntimeUriVault.resolve(0))
        }
        findViewById<View>(R.id.card_instagram_2).setOnClickListener {
            Utils.openUri(this, RuntimeUriVault.resolve(1))
        }
        findViewById<View>(R.id.card_instagram_3).setOnClickListener {
            Utils.openUri(this, RuntimeUriVault.resolve(2))
        }
        findViewById<View>(R.id.card_developer).setOnClickListener {
            Utils.openUri(this, RuntimeUriVault.resolve(3))
        }
    }
}

/**
 * Keeps private destinations out of plaintext DEX/resource constants in hardened builds.
 * The object itself is intentionally private so R8 can freely rename, inline and repackage it.
 */
private object RuntimeUriVault {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val nonces = arrayOf(
        "4LpFZkgVE5sNoFID",
        "4WIjAILwHt+VyKps",
        "noGosL0MyjQt1t3B",
        "6Khkosxai71t3N2U"
    )

    private val payloads = arrayOf(
        "KihA/GK4E5U+Ml2PDWedhVsPUPosjQ5FQcTwuZ0aOBtTq3RGYHs1pFjYakcnoJNZWX5DO4ly",
        "/j/BcFrM48ygoEVkxRVnw8bSR6PQdvpmPiagPsD/WMsDhqvrEYEw0jeng4WaO5ORJfoyqDZheQ==",
        "o29BTjERho3Sc9QKamUWLIY8fiaJiRXxOWS37espW4WNLagHno/zVcJ/C+mKYfheQvGjK830Vg==",
        "ptces4eou4K0EAGhbgEJJGaRElS7/xOqpraFJGVFNYZS23M="
    )

    fun resolve(slot: Int): String {
        require(slot in payloads.indices)

        // Pack the key as two signed longs instead of a readable byte/string constant.
        val keyBytes = ByteBuffer.allocate(16)
            .putLong(-4369561144073588421L)
            .putLong(-9098965365161072602L)
            .array()

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val nonce = Base64.decode(nonces[slot], Base64.NO_WRAP)
            val payload = Base64.decode(payloads[slot], Base64.NO_WRAP)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, nonce)
            )
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } finally {
            keyBytes.fill(0)
        }
    }
}
