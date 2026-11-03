package com.lagradost.cloudstream3.utils

import android.view.View
import android.view.ViewParent

/** Small, testable TV behaviors added by the Tik TV fork. */
object ForkTvHelper {
    /**
     * Default inset for television screens. The user can still select 0 dp in Settings > UI.
     * Phone/emulator layouts keep Cloudstream's original 0 dp default.
     */
    fun defaultOverscanDp(isTv: Boolean, tvDefaultDp: Int): Int =
        if (isTv) tvDefaultDp.coerceIn(0, 100) else 0

    /** True when [view] itself, or any ancestor, has [ancestorId]. */
    fun isWithin(view: View?, ancestorId: Int): Boolean {
        if (ancestorId == View.NO_ID) return false
        var current: View? = view
        while (current != null) {
            if (current.id == ancestorId) return true
            val parent: ViewParent? = current.parent
            current = parent as? View
        }
        return false
    }
}
