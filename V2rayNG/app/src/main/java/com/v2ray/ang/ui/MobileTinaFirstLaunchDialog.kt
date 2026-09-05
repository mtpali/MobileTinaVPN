package com.v2ray.ang.ui

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.util.Utils
import java.security.MessageDigest

/** Builds the install-scoped social notice without placing its text in Android resources. */
internal object MobileTinaFirstLaunchDialog {
    private val k = byteArrayOf(110, 95, 55, 100, 50, 99, 57, 49)
    private val expected = byteArrayOf(
        126, -106, -45, 48, 54, 46, 41, 67,
        116, 41, 113, -9, -78, 119, -12, 119,
        27, 33, 8, 12, 36, 39, -95, -4,
        -66, 17, 44, 110, 127, -14, 62, 125
    )

    fun showOnce(
        activity: AppCompatActivity,
        preferences: SharedPreferences,
        onDismiss: () -> Unit
    ): Dialog? {
        val key = String(k, Charsets.UTF_8)
        if (preferences.getBoolean(key, false) || activity.isFinishing || activity.isDestroyed) {
            return null
        }

        val lines = arrayOf(q.a(9), q.a(10))
        verify(lines)

        val primary = Color.rgb(34, 158, 217)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(activity, 24), dp(activity, 22), dp(activity, 24), dp(activity, 20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 24).toFloat()
                setColor(Color.rgb(8, 15, 27))
                setStroke(dp(activity, 1), Color.rgb(49, 92, 126))
            }
        }

        root.addView(View(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(42, 171, 238), Color.rgb(34, 115, 165))
            ).apply { cornerRadius = dp(activity, 3).toFloat() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 5)).apply {
            bottomMargin = dp(activity, 20)
        })

        root.addView(TextView(activity).apply {
            text = lines[0]
            setTextColor(Color.WHITE)
            textSize = 19f
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(activity, 16)
        })

        val telegram = TextView(activity).apply {
            text = lines[1]
            setTextColor(Color.rgb(137, 211, 246))
            textSize = 18f
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isClickable = true
            isFocusable = true
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 16).toFloat()
                setColor(Color.rgb(13, 31, 50))
                setStroke(dp(activity, 1), primary)
            }
            setOnClickListener {
                val username = lines[1].removePrefix("@")
                try {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username"))
                    )
                } catch (_: ActivityNotFoundException) {
                    Utils.openUri(activity, q.a(3))
                }
            }
        }
        root.addView(
            telegram,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(activity, 12) }
        )

        val close = MaterialButton(activity).apply {
            text = r.a(6)
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
        }
        root.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)).apply {
            topMargin = dp(activity, 6)
        })

        val dialog = Dialog(activity).apply {
            setContentView(root)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            setOnDismissListener { onDismiss() }
        }
        close.setOnClickListener { dialog.dismiss() }

        return try {
            dialog.show()
            dialog.window?.let { window ->
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0.58f)
                val targetWidth = (activity.resources.displayMetrics.widthPixels * 0.88f).toInt()
                    .coerceAtMost(dp(activity, 430))
                window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            }
            preferences.edit().putBoolean(key, true).commit()
            dialog
        } catch (_: RuntimeException) {
            if (dialog.isShowing) dialog.dismiss()
            null
        }
    }

    private fun verify(lines: Array<String>) {
        val digest = MessageDigest.getInstance("SHA-256")
        lines.forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        if (!MessageDigest.isEqual(digest.digest(), expected)) {
            throw SecurityException()
        }
    }

    private fun dp(activity: AppCompatActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}
