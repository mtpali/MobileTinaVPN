package com.v2ray.ang.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MobileTinaStoreAboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.x7)

        findViewById<View>(R.id.a0)?.visibility = View.GONE

        findViewById<TextView>(R.id.b0).text = q.a(8)
        findViewById<TextView>(R.id.b1).text = q.a(4)
        findViewById<TextView>(R.id.b2).text = q.a(5)
        findViewById<TextView>(R.id.b3).text = q.a(6)
        findViewById<TextView>(R.id.b4).text = q.a(7)

        findViewById<View>(R.id.a1).setOnClickListener { Utils.openUri(this, q.a(0)) }
        findViewById<View>(R.id.a2).setOnClickListener { Utils.openUri(this, q.a(1)) }
        findViewById<View>(R.id.a3).setOnClickListener { Utils.openUri(this, q.a(2)) }
        findViewById<View>(R.id.a4).setOnClickListener { Utils.openUri(this, q.a(3)) }
    }
}

private object q {
    private val u = intArrayOf(89,117,192,143,82,248,156,108,250,42,29,3,140,117,32,238,75,44,0,114,250,65,250,46)
    private val v = intArrayOf(227,133,40,91,96,213,68,59,219,235,56,17,31,141,153,234,92,152,132,82,198,92,1,73)

    private val w = arrayOf(
        intArrayOf(76,83,159,3,176,47,150,51,62,247,237,242,138,10,22,242,100,103,195,36,93,1,87,55,201,185,109,147,127,68,217,65,74,195,125,58,96,220,103,219,31,104,45,29,59,163,7,214,12,8,253,198,11,196,150,104,104,191,227,120,138,85,213,153,229,240),
        intArrayOf(160,193,112,38,200,201,228,194,15,71,60,146,189,145,114,94,159,196,55,1,154,50,90,224,3,212,145,167,77,222,177,66,31,254,66,226,59,116,46,116,28,119,193,27,79,210,72,28,134,101,238,44,46,226,4,244,148,248,11,57,52,228,191,174,177,102,167),
        intArrayOf(154,47,220,205,206,168,69,104,123,67,213,193,27,125,24,58,4,111,165,98,47,31,103,54,49,88,8,120,148,117,215,116,17,31,3,126,109,90,23,194,48,139,65,187,242,110,172,76,191,192,23,58,72,222,65,217,127,112,2,214,92,25,98,134,62,176,237),
        intArrayOf(171,8,244,246,214,17,75,146,74,81,29,29,27,44,53,17,83,211,3,202,12,69,246,197,228,70,116,186,163,144,249,184,110,109,104,4,166,165,180,67,68,234,150,119,197,205,114),
        intArrayOf(222,130,10,149,222,194,132,203,134,227,72,155,105,213,165,62,70,115,69,101,38,201,84,58,178,212,219,160,57,133,156,249,171,232,68,41,152,189,60,92,165,225,23,213,121,149,105,200,64,108,56,79,193,39,216,166,174,172,213,81,22,76,31,80,205,173,195,53,193,183,13,12,48,19,5,46,246),
        intArrayOf(180,148,204,244,143,39,209,221,39,201,73,151,70,51,157,136,212,4,228,227,127,159,179,39,25,156,32,116,215,196,71,89,216,25,96,108,152,221,81,110,102,123,197,9,115,108,207,176,162,83,157,220,50,185,125,178,90,227,85,27,29,217,30,0,66,103,171,93,236,254,4,83,30,33,195,81,193),
        intArrayOf(97,229,65,197,254,91,245,227,46,96,70,225,93,96,79,104,228,125,147,185,172,46,84,228,78,57,201,190,76,173,18,136,99,218,113,61,153,77,213,197,114,211,10,187,249,11,145,200,211,240,218,63,235,57,149),
        intArrayOf(140,192,4,20,14,80,214,84,146,98,57,52,14,80,185,237,18,171,90,108,132,7,238,240,151,71,125,1,108,51,220,67,59,203,101,44,2,242,100,11,251,22,169,155,156,240,230,202,27,108,241,232,126,14,206,42,32,70,131,32,177,51),
        intArrayOf(160,15,208,203,78,91,106,84,243,191,86,233,210,68,255,103,54,81,15,27,45,219,224,156,170,155,120,122,168,226,235,101,85,143,202,225,99,29,69,114,82,157,124,154,6,178,247,20,216,66,91,84,167,65,220,235,183,145,192,243,223,195,137,210,111,186,125,195,219,35,14,230,206,234,246,122,80,147,199,193,157,228,228,225,121,26,213,195,88,2,33,64,148,236,36)
    )

    fun a(n: Int): String {
        require(n in w.indices)
        val s = ByteArray(u.size) { i -> (u[i] xor v[i]).toByte() }
        val d = MessageDigest.getInstance("SHA-256")
        d.update(BuildConfig.APPLICATION_ID.toByteArray(Charsets.UTF_8))
        d.update(s)
        s.fill(0)
        val k = d.digest().copyOf(16)
        val e = w[n]
        val z = ByteArray(e.size) { i ->
            (e[i] xor ((0x5d + n * 37 + i * 13) and 0xff)).toByte()
        }
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, z.copyOfRange(0, 12)))
            String(c.doFinal(z.copyOfRange(12, z.size)), Charsets.UTF_8)
        } finally {
            k.fill(0)
            z.fill(0)
        }
    }
}
