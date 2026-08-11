package com.v2ray.ang.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils

class MobileTinaStoreAboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobiletina_store_about)

        // Match the approved MobileTina store page: no visible toolbar/back button.
        // Android system back remains available.
        findViewById<View>(R.id.toolbar_store_about)?.visibility = View.GONE

        findViewById<View>(R.id.card_instagram_1).setOnClickListener {
            Utils.openUri(this, "https://www.instagram.com/mobile.tina/")
        }
        findViewById<View>(R.id.card_instagram_2).setOnClickListener {
            Utils.openUri(this, "https://www.instagram.com/mobile.tina2/")
        }
        findViewById<View>(R.id.card_instagram_3).setOnClickListener {
            Utils.openUri(this, "https://www.instagram.com/mobile.tinaa/")
        }
        findViewById<View>(R.id.card_developer).setOnClickListener {
            Utils.openUri(this, "https://t.me/vpn963")
        }
    }
}
