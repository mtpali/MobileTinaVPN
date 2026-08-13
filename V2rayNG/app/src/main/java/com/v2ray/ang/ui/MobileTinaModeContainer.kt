package com.v2ray.ang.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Observes deliberate horizontal swipes without stealing touch events from the child
 * ScrollView/RecyclerView/ViewPager hierarchy.
 *
 * Direction: +1 = left-to-right (Manual), -1 = right-to-left (Auto).
 */
class MobileTinaModeContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val triggerThreshold = 58f * density
    private val horizontalBias = 1.2f

    private var downX = 0f
    private var downY = 0f
    private var trackingGesture = false
    private var ownsUnclaimedTouchSequence = false
    private var swipeListener: ((direction: Int) -> Unit)? = null

    fun setOnModeSwipeListener(listener: (direction: Int) -> Unit) {
        swipeListener = listener
    }

    /**
     * Observe the complete gesture at container level. dispatchTouchEvent() is called
     * before child views, so a horizontal swipe is still detected even when the manual
     * server list, TabLayout or another child handles the touch sequence itself.
     * Vertical scrolling and normal clicks remain entirely with the child view.
     *
     * When Auto has no subscription yet, its ScrollView may be non-scrollable and can
     * decline ACTION_DOWN on empty space. In that case this container claims only that
     * otherwise-unhandled sequence so Android keeps delivering MOVE/UP and the first-run
     * swipe works exactly like it does after subscription content is present.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                trackingGesture = true

                val handled = super.dispatchTouchEvent(event)
                ownsUnclaimedTouchSequence = !handled
                return handled || ownsUnclaimedTouchSequence
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val isHorizontalSwipe = trackingGesture &&
                    abs(dx) >= triggerThreshold &&
                    abs(dx) > abs(dy) * horizontalBias

                // Let a child that claimed ACTION_DOWN finish its own gesture first. If no
                // child claimed the sequence, keep consuming it here until this ACTION_UP.
                val handled = super.dispatchTouchEvent(event)
                val ownedByContainer = ownsUnclaimedTouchSequence
                trackingGesture = false
                ownsUnclaimedTouchSequence = false
                if (isHorizontalSwipe) {
                    swipeListener?.invoke(if (dx > 0f) 1 else -1)
                    return true
                }
                return handled || ownedByContainer
            }

            MotionEvent.ACTION_CANCEL -> {
                val handled = super.dispatchTouchEvent(event)
                val ownedByContainer = ownsUnclaimedTouchSequence
                trackingGesture = false
                ownsUnclaimedTouchSequence = false
                return handled || ownedByContainer
            }

            else -> {
                val handled = super.dispatchTouchEvent(event)
                return handled || ownsUnclaimedTouchSequence
            }
        }
    }
}
