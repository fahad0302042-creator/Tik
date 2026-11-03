package com.lagradost.cloudstream3.utils

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lagradost.cloudstream3.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented checks for the Tik TV fork specific behaviors. */
@RunWith(AndroidJUnit4::class)
class ForkTvHelperInstrumentedTest {
    private fun buildHierarchy(): Triple<LinearLayout, LinearLayout, View> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outer = LinearLayout(context).apply { id = 100 }
        val middle = LinearLayout(context).apply { id = 200 }
        val leaf = TextView(context).apply { id = 300 }
        middle.addView(leaf)
        outer.addView(middle)
        return Triple(outer, middle, leaf)
    }

    @Test
    fun isWithinFindsDirectAndNestedAncestors() {
        val (outer, middle, leaf) = buildHierarchy()

        assertTrue(ForkTvHelper.isWithin(leaf, middle.id))
        assertTrue(ForkTvHelper.isWithin(leaf, outer.id))
        assertTrue(ForkTvHelper.isWithin(middle, outer.id))
    }

    @Test
    fun isWithinIsFalseForUnrelatedIdsAndNoId() {
        val (_, _, leaf) = buildHierarchy()
        val detached = TextView(ApplicationProvider.getApplicationContext<android.content.Context>())

        assertFalse(ForkTvHelper.isWithin(leaf, 999))
        assertFalse(ForkTvHelper.isWithin(detached, 100))
        assertFalse(ForkTvHelper.isWithin(leaf, View.NO_ID))
        assertFalse(ForkTvHelper.isWithin(null, 100))
    }

    @Test
    fun forkHasItsOwnApplicationId() {
        assertFalse(
            "The fork must never ship the official cloudstream application id",
            BuildConfig.APPLICATION_ID == "com.lagradost.cloudstream3"
        )
        assertTrue(BuildConfig.APPLICATION_ID.contains("tiktv"))
    }

    @Test
    fun tvLauncherEntryIsDeclared() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(intent, 0)

        assertTrue("No leanback launcher entry found, the app will not show up on Android TV", resolved.isNotEmpty())
    }

    @Test
    fun updaterIsIsolatedFromOfficialRepository() {
        assertEquals("fahad0302042-creator", BuildConfig.FORK_UPDATE_OWNER)
        assertEquals("Tik", BuildConfig.FORK_UPDATE_REPOSITORY)
        assertFalse(
            "Fork updates must stay disabled while no release channel exists",
            BuildConfig.FORK_APP_UPDATES_ENABLED
        )
    }
}
