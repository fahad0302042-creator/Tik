package com.tiktv.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Plain, D-pad friendly settings screen. Rows are built in code so the whole screen
 * stays focusable with a TV remote.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        list = findViewById(R.id.settingsList)

        val subtitle = findViewById<TextView>(R.id.settingsSubtitle)
        subtitle.text = getString(R.string.help_body) + "\n\n" + getString(R.string.set_back_hint)

        buildRows()
    }

    override fun onResume() {
        super.onResume()
        buildRows()
    }

    // ------------------------------------------------------------------ rows

    private fun buildRows() {
        list.removeAllViews()

        addRow(
            getString(R.string.set_home_title),
            homeSummary()
        ) { showHomeChooser() }

        addRow(
            getString(R.string.set_layout_title),
            if (prefs.mobileSite) getString(R.string.set_layout_mobile)
            else getString(R.string.set_layout_desktop)
        ) { showLayoutChooser() }

        addToggle(
            getString(R.string.set_cinema_title),
            getString(R.string.set_cinema_sub),
            prefs.cinema
        ) { prefs.cinema = it }

        addToggle(
            getString(R.string.set_autoplay_title),
            getString(R.string.set_autoplay_sub),
            prefs.autoplayNext
        ) { prefs.autoplayNext = it }

        addToggle(
            getString(R.string.set_start_cursor_title),
            getString(R.string.set_start_cursor_sub),
            prefs.startWithCursor
        ) { prefs.startWithCursor = it }

        addRow(
            getString(R.string.set_cursor_speed_title),
            speedName(prefs.cursorSpeed)
        ) { showSpeedChooser() }

        addRow(
            getString(R.string.set_signout_title),
            getString(R.string.set_signout_sub)
        ) { confirmSignOut() }

        addRow(
            getString(R.string.set_reset_title),
            getString(R.string.set_reset_sub)
        ) { confirmReset() }

        addRow(
            getString(R.string.set_about_title),
            getString(R.string.set_about_sub, BuildConfig.VERSION_NAME)
        ) { showAbout() }

        list.getChildAt(0)?.requestFocus()
    }

    private fun homeSummary(): String {
        val url = prefs.homeUrl
        return when (url) {
            "https://www.tiktok.com/", "https://www.tiktok.com/foryou" ->
                getString(R.string.set_home_sub_default)

            else -> url
        }
    }

    private fun speedName(value: Int): String = when (value) {
        0 -> getString(R.string.set_speed_slow)
        2 -> getString(R.string.set_speed_fast)
        else -> getString(R.string.set_speed_normal)
    }

    private fun addRow(title: String, subtitle: String, onClick: () -> Unit) {
        val row = baseRow(title, subtitle)
        row.isClickable = true
        row.setOnClickListener { onClick() }
        list.addView(row)
    }

    private fun addToggle(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = baseRow(title, subtitle)

        val toggle = Switch(this).apply {
            isChecked = checked
            isFocusable = false
            isClickable = false
        }

        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = row.getChildAt(0) as LinearLayout
        row.removeView(titleBlock)
        holder.addView(
            titleBlock,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        holder.addView(
            toggle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        row.addView(
            holder,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        row.isClickable = true
        row.setOnClickListener {
            val next = !toggle.isChecked
            toggle.isChecked = next
            onChange(next)
        }
        list.addView(row)
    }

    private fun baseRow(title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setBackgroundResource(R.drawable.bg_row)
        row.isFocusable = true
        row.setPadding(dp(22), dp(14), dp(22), dp(14))
        if (Build.VERSION.SDK_INT >= 26) row.defaultFocusHighlightEnabled = false

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(10)
        row.layoutParams = params

        val titleView = TextView(this)
        titleView.setTextAppearance(this, R.style.SettingsRowTitle)
        titleView.text = title

        val subtitleView = TextView(this)
        subtitleView.setTextAppearance(this, R.style.SettingsRowSubtitle)
        subtitleView.text = subtitle

        val block = LinearLayout(this)
        block.orientation = LinearLayout.VERTICAL
        block.addView(titleView)
        block.addView(subtitleView)
        row.addView(block)

        return row
    }

    // ------------------------------------------------------------------ dialogs

    private fun showHomeChooser() {
        val urls = arrayOf(
            "https://www.tiktok.com/",
            "https://www.tiktok.com/following",
            "https://www.tiktok.com/explore",
            "https://www.tiktok.com/live"
        )
        val labels = arrayOf("For You", "Following", "Explore", "LIVE")
        val current = urls.indexOf(prefs.homeUrl)

        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.set_home_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.homeUrl = urls[which]
                dialog.dismiss()
                buildRows()
            }
            .setNeutralButton(R.string.custom_url_title) { _, _ -> askCustomHome() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askCustomHome() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText(prefs.homeUrl)
        input.setSelection(input.text?.length ?: 0)

        val container = FrameLayout(this)
        container.setPadding(dp(24), dp(8), dp(24), 0)
        container.addView(
            input,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.custom_url_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var url = input.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    prefs.homeUrl = url
                    buildRows()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
    }

    private fun showLayoutChooser() {
        val labels = arrayOf(
            getString(R.string.set_layout_desktop),
            getString(R.string.set_layout_mobile)
        )
        val checked = if (prefs.mobileSite) 1 else 0

        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.set_layout_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.mobileSite = which == 1
                dialog.dismiss()
                buildRows()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSpeedChooser() {
        val labels = arrayOf(
            getString(R.string.set_speed_slow),
            getString(R.string.set_speed_normal),
            getString(R.string.set_speed_fast)
        )
        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.set_cursor_speed_title)
            .setSingleChoiceItems(labels, prefs.cursorSpeed) { dialog, which ->
                prefs.cursorSpeed = which
                dialog.dismiss()
                buildRows()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.set_signout_title)
            .setMessage(R.string.set_signout_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                toast(getString(R.string.set_signout_done))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.set_reset_title)
            .setMessage(R.string.set_reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.reset()
                buildRows()
                toast(getString(R.string.set_reset_done))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.set_about_title)
            .setMessage(R.string.set_about_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ------------------------------------------------------------------ helpers

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
