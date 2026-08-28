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
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import java.security.MessageDigest

/** Builds the install-scoped social notice without placing its text in Android resources. */
internal object MobileTinaFirstLaunchDialog {
    private val k = byteArrayOf(110, 95, 55, 100, 50, 99, 57, 49)
    private val expected = byteArrayOf(
        -122, 66, 42, 113, 37, -81, -15, -21,
        45, -101, -64, -117, -52, -116, 118, -77,
        104, -31, -25, -9, -99, 6, -114, 62,
        26, 51, -16, 1, 55, -96, -86, 0
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

        val lines = arrayOf(q.a(9), q.a(10), q.a(11))
        verify(lines)

        val surface = MaterialColors.getColor(activity, MaterialR.attr.colorSurface, Color.WHITE)
        val onSurface = MaterialColors.getColor(activity, MaterialR.attr.colorOnSurface, Color.BLACK)
        val primary = MaterialColors.getColor(activity, MaterialR.attr.colorPrimary, Color.rgb(103, 80, 164))
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 24).toFloat()
                setColor(surface)
            }
        }

        root.addView(View(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(131, 58, 180), Color.rgb(253, 29, 29), Color.rgb(252, 175, 69))
            ).apply { cornerRadius = dp(activity, 3).toFloat() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 5)).apply {
            bottomMargin = dp(activity, 18)
        })

        root.addView(TextView(activity).apply {
            text = q.a(8)
            setTextColor(onSurface)
            textSize = 19f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(activity, 16)
        })

        lines.forEach { value ->
            root.addView(TextView(activity).apply {
                text = value
                setTextColor(onSurface)
                textSize = 15f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(activity, 14).toFloat()
                    setColor(ColorUtils.setAlphaComponent(onSurface, 16))
                    setStroke(dp(activity, 1), ColorUtils.setAlphaComponent(onSurface, 34))
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
