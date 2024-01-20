/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.http.SslCertificate
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.lineageos.jelly.R
import org.lineageos.jelly.SettingsActivity
import org.lineageos.jelly.ext.requireActivity
import org.lineageos.jelly.suggestions.SuggestionsAdapter
import org.lineageos.jelly.utils.SharedPreferencesExt
import org.lineageos.jelly.utils.UiUtils
import kotlin.reflect.safeCast


/**
 * App's main URL and search view.
 */
class UrlBarLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    // Views
    private val autoCompleteTextView by lazy { findViewById<AutoCompleteTextView>(R.id.autoCompleteTextView) }
    private val incognitoIcon by lazy { findViewById<ImageButton>(R.id.incognitoIcon) }
    private val loadingProgressIndicator by lazy { findViewById<LinearProgressIndicator>(R.id.loadingProgressIndicator) }
    private val moreButton by lazy { findViewById<ImageButton>(R.id.moreButton)!! }
    private val searchCancelButton by lazy { findViewById<ImageButton>(R.id.searchCancelButton) }
    private val searchClearButton by lazy { findViewById<ImageButton>(R.id.searchClearButton) }
    private val searchEditText by lazy { findViewById<EditText>(R.id.searchEditText) }
    private val searchNextButton by lazy { findViewById<ImageButton>(R.id.searchNextButton) }
    private val searchPreviousButton by lazy { findViewById<ImageButton>(R.id.searchPreviousButton) }
    private val searchResultCountTextView by lazy { findViewById<TextView>(R.id.searchResultCountTextView) }
    private val secureButton by lazy { findViewById<ImageButton>(R.id.secureButton) }
    private val urlBarLayoutGroupSearch by lazy { findViewById<Group>(R.id.urlBarLayoutGroupSearch) }
    private val urlBarLayoutGroupUrl by lazy { findViewById<Group>(R.id.urlBarLayoutGroupUrl) }

    private val intentSettings by lazy {Intent(context, SettingsActivity::class.java)}
    private val sharedPreferencesExt by lazy { SharedPreferencesExt(context) }
    private val dialog by lazy { AlertDialog.Builder(context) }

    enum class UrlBarMode {
        URL,
        SEARCH,
    }
    var currentMode = UrlBarMode.URL
        set(value) {
            field = value


            if (value == UrlBarMode.SEARCH) {
                urlBarLayoutGroupUrl.isVisible = false
                urlBarLayoutGroupSearch.isVisible = true
                searchEditText.requestFocus()
                //??//requireActivity().window.currentFocus?.let { UiUtils.showKeyboard(requireActivity().window, it) }
                //??//UiUtils.showKeyboard(requireActivity().window, searchEditText)
                val imm = getSystemService(context, InputMethodManager::class.java)
                //??//imm!!.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                imm!!.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)//THE only1 ??

            } else  {
                urlBarLayoutGroupSearch.isVisible = false
                urlBarLayoutGroupUrl.isVisible = true
            }

        }

    var isIncognito = false
        set(value) {
            field = value

            incognitoIcon.isVisible = value
        }

    var loadingProgress: Int = 100
        set(value) {
            field = value

            loadingProgressIndicator.progress = value
        }
    private var isLoading = false
        set(value) {
            field = value

            loadingProgressIndicator.isVisible = value
        }

    var url: String? = null
        set(value) {
            field = value

            autoCompleteTextView.setText(value)
            when (value?.subSequence(0 ,8)) {
                "file:///" -> {
                    secureButton.isVisible = true
                    secureButton.setImageResource(R.drawable.ic_save)
                    if (!isIncognito) incognitoIcon.setImageResource(R.drawable.ic_launcher_monochrome)
                }
                "content:" -> {
                    secureButton.isVisible = true
                    secureButton.setImageResource(R.drawable.ic_save)
                    if (!isIncognito) incognitoIcon.setImageResource(R.drawable.ic_launcher_monochrome)
                }
                "https://" -> {
                secureButton.isVisible = true
                secureButton.setImageResource(R.drawable.ic_lock)
                }
                else -> secureButton.isVisible =false
            }
        }

    var title: String? = null
        set(value) {
            field = value
            autoCompleteTextView.setText(value)
        }
    var webView: WebView? = null

    private var certificate: SslCertificate? = null

    private var wasKeyboardVisible = false

    // Search
    var searchPositionInfo = Pair(0, 0)
        @SuppressLint("SetTextI18n")
        set(value) {
            field = value

            val hasResults = value.second > 0
            searchPreviousButton.isEnabled = hasResults && value.first > 0
            searchNextButton.isEnabled = hasResults && value.first + 1 < value.second
            searchResultCountTextView.text =
                "${if (hasResults) value.first + 1 else 0}/${value.second}"

            val hasInput = searchEditText.text.isNotEmpty()
            searchResultCountTextView.isVisible = hasInput
            searchClearButton.isVisible = hasInput
        }

    // Callbacks
    var onMoreButtonClickCallback: (() -> Unit)? = null
    var onLoadUrlCallback: ((url: String) -> Unit)? = null
    var onStartSearchCallback: ((query: String) -> Unit)? = null
    var onSearchPositionChangeCallback: ((next: Boolean) -> Unit)? = null
    var onClearSearchCallback: (() -> Unit)? = null

    // Dialogs
    private val sslCertificateInfoDialog by lazy {
        SslCertificateInfoDialog(context).apply {
            create()
        }
    }

    init {
        inflate(context, R.layout.url_bar_layout, this)
        autoCompleteTextView.setAdapter(SuggestionsAdapter(context))

    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        //https://developer.android.com/develop/ui/views/layout/sw-keyboard
        autoCompleteTextView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                UiUtils.hideKeyboard(requireActivity().window, autoCompleteTextView)
                autoCompleteTextView.setText(title)
                autoCompleteTextView.clearFocus()
                webView?.requestFocus(FOCUS_DOWN or FOCUS_UP)

            } else autoCompleteTextView.setText(if (sharedPreferencesExt.urlBarSearch) url else title)
        }
        autoCompleteTextView.setOnEditorActionListener { _, actionId: Int, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    UiUtils.hideKeyboard(requireActivity().window, autoCompleteTextView)
                    onLoadUrlCallback?.invoke(autoCompleteTextView.text.toString())
                    autoCompleteTextView.clearFocus()
                    webView?.requestFocus(FOCUS_DOWN or FOCUS_UP)
                    true
                }
                else -> false
            }
        }
        autoCompleteTextView.setOnKeyListener { _, keyCode: Int, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    UiUtils.hideKeyboard(requireActivity().window, autoCompleteTextView)
                    onLoadUrlCallback?.invoke(autoCompleteTextView.text.toString())
                    autoCompleteTextView.clearFocus()
                    webView?.requestFocus(FOCUS_DOWN or FOCUS_UP)
                    true
                }
                else -> false
            }
        }
        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val text = String::class.safeCast(autoCompleteTextView.adapter.getItem(position))
                ?: return@setOnItemClickListener
            UiUtils.hideKeyboard(requireActivity().window, autoCompleteTextView)
            autoCompleteTextView.clearFocus()
            onLoadUrlCallback?.invoke(text)
            webView?.requestFocus(FOCUS_DOWN or FOCUS_UP)
        }
        if (isIncognito && Build.VERSION.SDK_INT >= 26) {
            autoCompleteTextView.imeOptions = autoCompleteTextView.imeOptions or
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        moreButton.setOnClickListener {
            requireActivity().window.currentFocus?.let { it1 -> UiUtils.hideKeyboard(requireActivity().window, it1) }
            onMoreButtonClickCallback?.invoke()
        }

        // Set secure button callback
        secureButton.setOnClickListener {
            if (certificate == null) {
                dialog.setTitle("URL")
                    .setMessage(url.toString())
                    .setCancelable(true)
                    .create()
                    .show()
            }
            else certificate?.let { cert ->
                url?.let {url ->
                    sslCertificateInfoDialog.setUrlAndCertificate(url, cert)
                    sslCertificateInfoDialog.show()
                }
            }
        }
        // Set FAVICON button callback
        incognitoIcon.setOnClickListener {
            webView?.reload()
        }
        // Set secure button LONG callback
        incognitoIcon.setOnLongClickListener {
            webView?.goForward()
            true
        }
        // Set FAVICON button LONG callback
        secureButton.setOnLongClickListener {
            startActivity(context,intentSettings, null)
            true
        }

        // Set search callbacks
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) UiUtils.hideKeyboard(requireActivity().window, searchEditText)
        }
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when(actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {

                    searchEditText.text?.toString()?.takeUnless { it.isEmpty() }?.also {
                        onStartSearchCallback?.invoke(it)
                    } ?: run {
                        clearSearch()
                    }
                    UiUtils.hideKeyboard(requireActivity().window, searchEditText)
                    searchEditText.clearFocus()
                    true
                }
                else -> false
            }
        }
        searchEditText.setOnKeyListener { _, keyCode: Int, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    searchEditText.text?.toString()?.takeUnless { it.isEmpty() }?.also {
                        onStartSearchCallback?.invoke(it)
                    } ?: run {
                        clearSearch()
                    }
                    UiUtils.hideKeyboard(requireActivity().window, searchEditText)
                    searchEditText.clearFocus()
                    true
                }
                else -> false
            }
        }
        searchCancelButton.setOnClickListener {
            clearSearch()
            searchEditText.clearFocus()
            UiUtils.hideKeyboard(requireActivity().window, it)
            webView?.requestFocus()
            currentMode = UrlBarMode.URL
        }
        searchClearButton.setOnClickListener {
            clearSearch()
            searchEditText.requestFocus()
            UiUtils.showKeyboard(requireActivity().window, searchEditText)
        }
        searchPreviousButton.setOnClickListener { onSearchPositionChangeCallback?.invoke(false) }
        searchNextButton.setOnClickListener { onSearchPositionChangeCallback?.invoke(true) }
    }

    fun onPageLoadStarted(url: String?) {
        this.url = url
        certificate = null
        isLoading = true
    }

    fun onPageLoadFinished(certificate: SslCertificate?) {
        this.certificate = certificate
        isLoading = false
    }

    private fun clearSearch() {
        searchEditText.setText("")
        searchPositionInfo = EMPTY_SEARCH_RESULT
        onClearSearchCallback?.invoke()
    }

    companion object {
        private val EMPTY_SEARCH_RESULT = Pair(0, 0)
    }
}
