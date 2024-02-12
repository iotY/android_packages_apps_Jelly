/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.lineageos.jelly.utils.SharedPreferencesExt
import java.util.Date
import kotlin.reflect.safeCast

class SettingsActivity : AppCompatActivity() {
    // Views
    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }

        if (Build.VERSION.SDK_INT < 29) window.navigationBarColor = Color.LTGRAY
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        private val sharedPreferencesExt by lazy { SharedPreferencesExt(requireContext()) }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            setDivider(ColorDrawable(Color.TRANSPARENT))
            setDividerHeight(0)
        }

        override fun onCreatePreferences(savedInstance: Bundle?, rootKey: String?) {
            // Load the preferences from an XML resource
            setPreferencesFromResource(R.xml.settings, rootKey)
            val vWebview = userWeb(WebSettings.getDefaultUserAgent(context))

            findPreference<Preference>("key_home_page")?.let {
                bindPreferenceSummaryToValue(it, sharedPreferencesExt.defaultHomePage)
            }
            findPreference<Preference>("key_urlbar")?.let {
                it.title = context?.getString(R.string.search_bar_hint) + context?.getString(R.string.favorite_edit_positive)
                it.summary = context?.getString(R.string.pref_urlbar_summary) + " " + context?.getString(R.string.favorite_edit_title)
            }
            findPreference<Preference>("key_force_dark")?.let {
                if (vWebview.toInt() >= 76) {
                    it.summary = context?.getString(R.string.pref_force_dark_summary ,vWebview)
                } else preferenceScreen.removePreference(it)
            }
            findPreference<Preference>("key_about_notice")?.let {
                it.title = "Notice: " + "(" + BuildConfig.BUILD_TYPE + ")"
                it.summary =  Date(context?.packageManager?.getPackageInfo( BuildConfig.APPLICATION_ID, 0)?.firstInstallTime!!).toString()
            }
            findPreference<Preference>("key_about_useragent")?.let {
                it.title = "android WebView version = $vWebview"
                it.summary = "UserAgent fingerprint"
            }
            findPreference<Preference>("key_about_resume")?.let {
                it.title = "About: " + BuildConfig.APPLICATION_ID
                //it.summary =
            }
            findPreference<Preference>("key_about_whatsnew")?.let {
                it.title = "What's new in: jQuarks v" + BuildConfig.VERSION_NAME
                it.summary = Date(context?.packageManager?.getPackageInfo( BuildConfig.APPLICATION_ID, 0)?.lastUpdateTime!!).toString() +
                        "\n\u3004" + context?.packageManager?.getInstallerPackageName(BuildConfig.APPLICATION_ID)
            }
            /*
            val uiModeManager: UiModeManager? = requireActivity().getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            if (uiModeManager!!.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            if (resources.getBoolean(R.bool.is_tablet)) {
                findPreference<SwitchPreference>("key_reach_mode")?.let {
                    preferenceScreen.removePreference(it)
                }
            }*/
        }
        private fun userWeb(s: String): String {
            var tmp = "0"
            if (s.indexOf("Chrome/") > 0) {
                tmp = s.substring(s.indexOf("Chrome/")+7)
                if (s.indexOf(".") > 0) {
                    tmp = tmp.substring(0, tmp.indexOf("."))
                }
            }
            return tmp
        }

        private fun showZinfo(s: String, t: String, linky: Boolean) {
            val showText = TextView(context)
            showText.text = s
            showText.setTextIsSelectable(true)
            if (linky) {
                showText.autoLinkMask = Linkify.ALL
                Linkify.addLinks(showText, Linkify.WEB_URLS)
            }
            val builder = android.app.AlertDialog.Builder(context, android.R.style.ThemeOverlay_Material_Dialog_Alert)
            builder.setView(showText)
                    .setTitle(t)
                    .setCancelable(true)
                    .setNegativeButton(android.R.string.ok, null)
                    .show()
        }

        private fun bindPreferenceSummaryToValue(preference: Preference, def: String) {
            preference.onPreferenceChangeListener = this

            onPreferenceChange(
                preference,
                PreferenceManager
                    .getDefaultSharedPreferences(preference.context)
                    .getString(preference.key, def)
            )
        }

        override fun onPreferenceChange(preference: Preference, value: Any?): Boolean {
            val stringValue = value.toString()

            ListPreference::class.safeCast(preference)?.also {
                val prefIndex = it.findIndexOfValue(stringValue)
                if (prefIndex >= 0) {
                    preference.summary = it.entries[prefIndex]
                }
            } ?: run {
                preference.summary = stringValue
            }
            return true
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "key_home_page" -> {
                    editHomePage(preference)
                    true
                }
                "key_about_notice" -> {
                    showZinfo(this.resources.openRawResource(R.raw.full_description).bufferedReader().use { it1 -> it1.readText() } +
                            "\n" + context?.getString(R.string.pref_about)
                            , "Notice"
                            , true)
                    true
                }
                "key_about_whatsnew" -> {
                    showZinfo(this.resources.openRawResource(R.raw.whatsnew).bufferedReader().use { it1 -> it1.readText() } +
                            "\n" + context?.getString(R.string.pref_about)
                            , "Whats's new"
                            , true)
                    true
                }
                "key_about_useragent" -> {
                    showZinfo(WebSettings.getDefaultUserAgent(context)
                            , "UserAgent"
                            , true)
                    true
                }
                "key_cookie_clear" -> {
                    CookieManager.getInstance().removeAllCookies(null)
                    Toast.makeText(
                        preference.context, getString(R.string.pref_cookie_clear_done),
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }
                else -> {
                    super.onPreferenceTreeClick(preference)
                }
            }
        }

        private fun editHomePage(preference: Preference) {
            val builder = AlertDialog.Builder(preference.context)
            val alertDialog = builder.create()
            val inflater = alertDialog.layoutInflater
            val homepageView = inflater.inflate(
                R.layout.dialog_homepage_edit,
                LinearLayout(preference.context)
            )
            val homepageUrlEditText = homepageView.findViewById<EditText>(R.id.homepageUrlEditText)
            homepageUrlEditText.setText(sharedPreferencesExt.homePage)
            builder.setTitle(R.string.pref_start_page_dialog_title)
                .setMessage(R.string.pref_start_page_dialog_message)
                .setView(homepageView)
                .setPositiveButton(
                    android.R.string.ok
                ) { _: DialogInterface?, _: Int ->
                    val url = homepageUrlEditText.text.toString().ifEmpty {
                        sharedPreferencesExt.defaultHomePage
                    }
                    sharedPreferencesExt.homePage = url
                    preference.summary = url
                }
                .setNeutralButton(
                    R.string.pref_start_page_dialog_reset
                ) { _: DialogInterface?, _: Int ->
                    val url = sharedPreferencesExt.defaultHomePage
                    sharedPreferencesExt.homePage = url
                    preference.summary = url
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
