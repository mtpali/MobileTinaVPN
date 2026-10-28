package com.v2ray.ang.ui

import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.security.MessageDigest
import com.v2ray.ang.util.Utils

/** Builds the install-scoped social notice without placing its text in Android resources. */
internal object MobileTinaFirstLaunchDialog {
    private val k = byteArrayOf(110, 95, 55, 100, 50, 99, 57, 49)
    private val expected = byteArrayOf(
        106, -43, 22, 116, 120, 123, 43, -115,
        57, 123, -97, -100, -29, -59, 18, 2,
        -95, -73, -104, -6, -114, -74, 34, -118,
        -17, 101, 111, 82, -27, 127, 96, 74
    )

    internal fun noticeLines(): Array<String> = arrayOf(q.a(9), q.a(10)).also(::verify)

    fun showOnce(
        activity: AppCompatActivity,
        preferences: SharedPreferences,
        onDismiss: () -> Unit
    ): Dialog? {
        val key = String(k, Charsets.UTF_8)
        if (preferences.getBoolean(key, false) || activity.isFinishing || activity.isDestroyed) {
            return null
        }

        val lines = noticeLines()

        val primary = Color.rgb(193, 53, 132)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 24).toFloat()
                setColor(Color.BLACK)
                setStroke(dp(activity, 1), Color.WHITE)
            }
        }

        root.addView(View(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(primary, primary)
            ).apply { cornerRadius = dp(activity, 3).toFloat() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 5)).apply {
            bottomMargin = dp(activity, 18)
        })

        lines.forEachIndexed { index, value ->
            root.addView(TextView(activity).apply {
                text = value
                isClickable = true
                isFocusable = true
                setOnClickListener { Utils.openUri(activity, q.a(index)) }
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(activity, 14).toFloat()
                    setColor(Color.rgb(18, 18, 20))
                    setStroke(dp(activity, 1), Color.WHITE)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(activity, 10)
            })
        }

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
