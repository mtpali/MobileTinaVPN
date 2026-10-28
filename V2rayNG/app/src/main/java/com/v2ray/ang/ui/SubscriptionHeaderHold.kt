package com.v2ray.ang.ui

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs

/** A deliberate 1.5-second hold, independent of the system's accessibility long-press delay. */
internal class SubscriptionHeaderHold(
    private val view: View,
    owner: LifecycleOwner,
    private val valid: () -> Boolean,
    private val onHold: () -> Unit,
) : View.OnTouchListener, LifecycleEventObserver, View.OnAttachStateChangeListener {
    private val lifecycle = owner.lifecycle
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val action = Runnable {
        if (view.isAttachedToWindow && valid()) {
            fired = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onHold()
        }
    }

    init {
        owner.lifecycle.addObserver(this)
        view.addOnAttachStateChangeListener(this)
    }

    fun cancel() {
        view.removeCallbacks(action)
        fired = false
    }

    fun dispose() {
        cancel()
        lifecycle.removeObserver(this)
        view.removeOnAttachStateChangeListener(this)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                downX = event.x
                downY = event.y
                if (valid()) view.postDelayed(action, 1_500L)
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) cancel()
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> cancel()
            MotionEvent.ACTION_UP -> {
                val consumed = fired
                cancel()
                return consumed
            }
        }
        return false
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_PAUSE) cancel()
        if (event == Lifecycle.Event.ON_DESTROY) {
            cancel()
            dispose()
        }
    }

    override fun onViewDetachedFromWindow(v: View) = cancel()
    override fun onViewAttachedToWindow(v: View) = Unit
}
