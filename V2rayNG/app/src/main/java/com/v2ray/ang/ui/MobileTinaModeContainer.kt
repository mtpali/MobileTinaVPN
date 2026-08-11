package com.v2ray.ang.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Owns deliberate horizontal mode swipes while leaving vertical server-list scrolling untouched.
 * Direction: +1 = left-to-right, -1 = right-to-left.
 */
class MobileTinaModeContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val interceptThreshold = 24f * density
    private val triggerThreshold = 58f * density

    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var gestureTriggered = false
    private var swipeListener: ((direction: Int) -> Unit)? = null

    fun setOnModeSwipeListener(listener: (direction: Int) -> Unit) {
        swipeListener = listener
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
                gestureTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) >= interceptThreshold && abs(dx) > abs(dy) * 1.2f) {
                    intercepting = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!intercepting) return super.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_MOVE && !gestureTriggered) {
            val dx = event.x - downX
            if (abs(dx) >= triggerThreshold) {
                gestureTriggered = true
                swipeListener?.invoke(if (dx > 0f) 1 else -1)
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetGesture()
        }
        return true
    }

    private fun resetGesture() {
        intercepting = false
        gestureTriggered = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
