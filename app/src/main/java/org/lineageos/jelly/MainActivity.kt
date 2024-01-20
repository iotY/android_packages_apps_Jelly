/*
 * SPDX-FileCopyrightText: 2020-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.TaskDescription
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.HttpResponseCache
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebSettings
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.jelly.favorite.FavoriteActivity
import org.lineageos.jelly.favorite.FavoriteProvider
import org.lineageos.jelly.history.HistoryActivity
import org.lineageos.jelly.ui.MenuDialog
import org.lineageos.jelly.ui.UrlBarLayout
import org.lineageos.jelly.utils.AdBlocker
import org.lineageos.jelly.utils.IntentUtils
import org.lineageos.jelly.utils.PermissionsUtils
import org.lineageos.jelly.utils.SharedPreferencesExt
import org.lineageos.jelly.utils.TabUtils
import org.lineageos.jelly.utils.TabUtils.openInNewTab
import org.lineageos.jelly.utils.UiUtils
import org.lineageos.jelly.utils.UrlUtils
import org.lineageos.jelly.webview.WebViewExt
import org.lineageos.jelly.webview.WebViewExtActivity
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : WebViewExtActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    // Views
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private val constraintLayout by lazy { findViewById<ConstraintLayout>(R.id.constraintLayout) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val urlBarLayout by lazy { findViewById<UrlBarLayout>(R.id.urlBarLayout) }
    private val webView by lazy { findViewById<WebViewExt>(R.id.webView) }
    private val incognitoIcon by lazy {  findViewById<View>(R.id.incognitoIcon) as ImageButton }

    private val fileRequest =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            fileRequestCallback.invoke(it)
        }
    private lateinit var fileRequestCallback: ((data: List<Uri>) -> Unit)
    override fun launchFileRequest(input: Array<String>) {
        fileRequest.launch(input)
    }
    override fun setFileRequestCallback(cb: (data: List<Uri>) -> Unit) {
        fileRequestCallback = cb
    }

    private val urlResolvedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!intent.hasExtra(Intent.EXTRA_INTENT) ||
                !intent.hasExtra(Intent.EXTRA_RESULT_RECEIVER)
            ) {
                return
            }
            @Suppress("UnsafeIntentLaunch")
            val resolvedIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            } else {
                @Suppress("Deprecation")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)!!
            }
            if (TextUtils.equals(packageName, resolvedIntent.getPackage())) {
                val url: String = intent.getStringExtra(IntentUtils.EXTRA_URL)!!
                webView.loadUrl(url)
            } else {
                startActivity(resolvedIntent)
            }
            val receiver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    Intent.EXTRA_RESULT_RECEIVER,
                    ResultReceiver::class.java
                )!!
            } else {
                @Suppress("Deprecation")
                intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER)!!
            }
            receiver.send(Activity.RESULT_CANCELED, Bundle())
        }
    }
    private var mWaitingDownloadUrl: String? = null
    private var urlIcon: Bitmap? = null
    private var incognito = false
    private var customView: View? = null
    private var fullScreenCallback: CustomViewCallback? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var menuDialog: MenuDialog

    private val sharedPreferencesExt by lazy { SharedPreferencesExt(this) }

    private val permissionsUtils by lazy { PermissionsUtils(this) }

    private lateinit var locationDialogCallback: (() -> Unit)
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.isNotEmpty()) {
            if (permissionsUtils.locationPermissionsGranted()) {
                // Precise or approximate location access granted.
                locationDialogCallback()
            }
        }
    }

    private val startForResultSaveArchive = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri = result.data!!.data!!
            webView.saveWebArchive(pathSaveWebArchive() + nameSaveWebArchive(), false) { s: String? ->
                if (s != null) {
                    try {
                        //val input: InputStream = BufferedInputStream(FileInputStream(File(s).absoluteFile) as InputStream?)
                        val pfd = baseContext.contentResolver.openFileDescriptor(uri, "w")
                        BufferedInputStream(FileInputStream(File(s).absoluteFile)).use { stream ->
                            stream.copyTo(FileOutputStream(pfd!!.fileDescriptor))
                            //stream.copyTo(FileOutputStream(File(uri.getPath()!!)))
                        }
                        pfd!!.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            // Perform operations on the document using its URI.
        } else webView.saveWebArchive(pathSaveWebArchive() + nameSaveWebArchive())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Make sure prefs are set before loading them
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        preferenceManager.registerOnSharedPreferenceChangeListener(this)

        val intent = intent
        var url = intent.dataString
        incognito = when (sharedPreferencesExt.incognitoPolicy) {
            ALWAYS_DEFAULT_TO_INCOGNITO -> true
            EXTERNAL_DEFAULT_TO_INCOGNITO -> !Intent.ACTION_MAIN.equals(intent.action)
            else -> intent.getBooleanExtra(IntentUtils.EXTRA_INCOGNITO, false)
        }
        var desktopMode = false

        // Restore from previous instance
        savedInstanceState?.let {
            incognito = it.getBoolean(IntentUtils.EXTRA_INCOGNITO, incognito)
            url = url?.takeIf {
                    url -> url.isNotEmpty()
            } ?: it.getString(IntentUtils.EXTRA_URL, null)
            desktopMode = it.getBoolean(IntentUtils.EXTRA_DESKTOP_MODE, false)
        }

        urlBarLayout.isIncognito = incognito
        urlBarLayout.webView = webView
        window.navigationBarColor = UiUtils.getGray(this.resources)


        menuDialog = MenuDialog(this) { option: MenuDialog.Option ->
            val isDesktop = webView.isDesktopMode

            when (option) {
                MenuDialog.Option.BACK -> webView.goBack()
                MenuDialog.Option.FORWARD -> webView.goForward()
                MenuDialog.Option.NEW_TAB -> openInNewTab(this, null, false)
                MenuDialog.Option.NEW_PRIVATE_TAB -> openInNewTab(this, null, true)
                MenuDialog.Option.REFRESH -> webView.reload()
                MenuDialog.Option.ADD_TO_FAVORITE -> uiScope.launch {
                    webView.title?.let { title ->
                        webView.url?.let { url ->
                            setAsFavorite(title, url)
                        }
                    }
                }
                MenuDialog.Option.SHARE -> {
                    // Delay a bit to allow popup menu hide animation to play
                    Handler(Looper.getMainLooper()).postDelayed({
                        webView.url?.let { url -> shareUrl(url) }
                    }, 300)
                }
                MenuDialog.Option.FIND_IN_PAGE -> {
                    // Run the search setup
                    showSearch()
                }
                MenuDialog.Option.FAVORITES -> startActivity(Intent(this, FavoriteActivity::class.java))
                MenuDialog.Option.HISTORY -> startActivity(Intent(this, HistoryActivity::class.java))
                MenuDialog.Option.DOWNLOADS -> startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                MenuDialog.Option.ADD_TO_HOME_SCREEN -> addShortcut(webView.getTitle()!!, webView.getUrl()!!)
                MenuDialog.Option.PRINT -> {
                    val printManager = getSystemService(PrintManager::class.java)
                    val documentName = "Jelly document"
                    val printAdapter = webView.createPrintDocumentAdapter(documentName)
                    printManager.print(
                        documentName, printAdapter,
                        PrintAttributes.Builder().build()
                    )
                }
                MenuDialog.Option.DESKTOP_VIEW -> {
                    webView.isDesktopMode = !isDesktop
                    menuDialog.isDesktopMode = !isDesktop
                }
                MenuDialog.Option.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
                MenuDialog.Option.SAVE -> {
                    if (Build.VERSION.SDK_INT < 29) {
                        webView.saveWebArchive(pathSaveWebArchive() + nameSaveWebArchive())
                    } else {
                        intent.action = Intent.ACTION_CREATE_DOCUMENT
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "text/mht"
                        intent.putExtra(Intent.EXTRA_TITLE, nameSaveWebArchive())
                        startForResultSaveArchive.launch(intent)
                    }
                    addShortcut("\u2707" + webView.title, "file:///" + pathSaveWebArchive() + nameSaveWebArchive())
                    uiScope.launch {
                        setAsFavorite("\u2707" + webView.title, pathSaveWebArchive() + nameSaveWebArchive())
                    }
                    Toast.makeText(this, "\u2707" + getExternalFilesDir(null), Toast.LENGTH_LONG).show()

                }

                else -> {
                    val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    val tasks = am.appTasks
                    if (tasks != null && tasks.size > 0) {
                        if (option != MenuDialog.Option.KILL_THIS) {
                            for (i in 1 until tasks.size) {
                                tasks[i].setExcludeFromRecents(true)
                                tasks[i].finishAndRemoveTask()
                            }
                        }
                        if (option != MenuDialog.Option.KILL_OTHERS) {
                            tasks[0].setExcludeFromRecents(true)
                            tasks[0].finishAndRemoveTask()
                        }
                    }
                    if (option == MenuDialog.Option.KILL_ALL) {
                        Process.killProcess(Process.myPid())
                    }

                }
            }
            menuDialog.dismiss()
        }
        urlBarLayout.onMoreButtonClickCallback = {
            //window.currentFocus?.let { UiUtils.hideKeyboard(window, it) }
            menuDialog.showAsDropdownMenu(urlBarLayout, sharedPreferencesExt.reachModeEnabled)
        }

        webView.init(this, urlBarLayout, incognito)
        webView.isDesktopMode = desktopMode
        //webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = WebSettings.LOAD_NORMAL
        webView.settings.allowUniversalAccessFromFileURLs = true

        /*if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            //WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
            //https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        }

         */


        webView.loadUrl(url ?: sharedPreferencesExt.homePage)
        webView.requestFocus()
        AdBlocker.init(this)
        setUiMode()
        try {
            val httpCacheDir = File(cacheDir, "suggestion_responses")
            val httpCacheSize = 1024 * 1024.toLong() // 1 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize)
        } catch (e: IOException) {
            Log.i(TAG, "HTTP response cache installation failed:$e")
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    urlBarLayout.currentMode == UrlBarLayout.UrlBarMode.SEARCH -> {
                        urlBarLayout.currentMode = UrlBarLayout.UrlBarMode.URL
                        window.currentFocus?.let { UiUtils.hideKeyboard(window, it) }
                        window.currentFocus?.clearFocus()
                        webView.requestFocus()
                    }

                    customView != null -> {
                        onHideCustomView()
                    }

                    webView.canGoBack() -> {
                        webView.goBack()
                    }

                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                urlResolvedReceiver, IntentFilter(IntentUtils.EVENT_URL_RESOLVED),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(urlResolvedReceiver, IntentFilter(IntentUtils.EVENT_URL_RESOLVED))
        }
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        unregisterReceiver(urlResolvedReceiver)
        HttpResponseCache.getInstalled().flush()
        super.onStop()
    }

    public override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        when (requestCode) {
            STORAGE_PERM_REQ -> if (hasStoragePermission() && mWaitingDownloadUrl != null) {
                downloadFileAsk(mWaitingDownloadUrl, null, null, mimeType = null, contentLength = 0)
            } else {
                if (shouldShowRequestPermissionRationale(
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.permission_error_title)
                            .setMessage(R.string.permission_error_storage)
                            .setCancelable(false)
                            .setPositiveButton(getString(R.string.permission_error_ask_again)
                            ) { _: DialogInterface?, _: Int -> requestStoragePermission() }
                            .setNegativeButton(getString(R.string.dismiss)
                            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                            .show()
                } else {
                    Snackbar.make(constraintLayout, getString(R.string.permission_error_forever),
                            Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        CookieManager.getInstance()
            .setAcceptCookie(!webView.isIncognito && sharedPreferencesExt.cookiesEnabled)
        if (sharedPreferencesExt.lookLockEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Preserve webView status
        outState.putString(IntentUtils.EXTRA_URL, webView.url)
        outState.putBoolean(IntentUtils.EXTRA_INCOGNITO, webView.isIncognito)
        outState.putBoolean(IntentUtils.EXTRA_DESKTOP_MODE, webView.isDesktopMode)
    }

    private fun showSearch() {
        urlBarLayout.currentMode = UrlBarLayout.UrlBarMode.SEARCH
    }

    private fun shareUrl(url: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, url)
        if (sharedPreferencesExt.advancedShareEnabled && url == webView.url) {
            val file = File(cacheDir, System.currentTimeMillis().toString() + ".png")
            try {
                FileOutputStream(file).use { out ->
                    val bm = webView.snap
                    bm.compress(Bitmap.CompressFormat.PNG, 70, out)
                    out.flush()
                    out.close()
                    intent.putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(this, PROVIDER, file)
                    )
                    intent.type = "image/png"
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: IOException) {
                Log.e(TAG, "${e.message}", e)
            }
        } else {
            intent.type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
    }


    private suspend fun setAsFavorite(title: String, url: String) {
        val color = urlIcon?.takeUnless { it.isRecycled }?.let { bitmap ->
            UiUtils.getColor(bitmap, false).takeUnless { it == Color.TRANSPARENT }
        } ?: UiUtils.getGray(this.resources)
        withContext(Dispatchers.Default) {
            FavoriteProvider.addOrUpdateItem(contentResolver, title, url, color)
            withContext(Dispatchers.Main) {
                Snackbar.make(
                    constraintLayout, getString(R.string.favorite_added),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun downloadFileAsk(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val fileName = UrlUtils.guessFileName(url, contentDisposition, mimeType)

        if (  Build.VERSION.SDK_INT < 29 ) {
            if (!hasStoragePermission()) {
                mWaitingDownloadUrl = url
                requestStoragePermission()
                return
            }
            mWaitingDownloadUrl = null
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.download_title)
            .setMessage(getString(R.string.download_message, fileName))
            .setPositiveButton(
                getString(R.string.download_positive)
            ) { _: DialogInterface?, _: Int -> fetchFile(url, userAgent, fileName) }
            .setNegativeButton(
                getString(R.string.dismiss)
            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .show()
    }

    private fun fetchFile(url: String?, userAgent: String?, fileName: String) {
        val request = try {
            DownloadManager.Request(Uri.parse(url))
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Cannot download non http or https scheme")
            return
        }

        // Let this downloaded file be scanned by MediaScanner - so that it can
        // show up in Gallery app, for example.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            request.allowScanningByMediaScanner()
        }
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setMimeType(
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(url)
            )
        )
        userAgent?.let {
            request.addRequestHeader("User-Agent", it)
        }
        CookieManager.getInstance().getCookie(url)?.takeUnless { it.isEmpty() }?.let {
            request.addRequestHeader("Cookie", it)
        }
        getSystemService(DownloadManager::class.java).enqueue(request)
    }

    override fun showSheetMenu(url: String, shouldAllowDownload: Boolean) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_actions, LinearLayout(this))
        val tabLayout = view.findViewById<View>(R.id.sheetNewTabLayout)
        val shareLayout = view.findViewById<View>(R.id.sheetShareLayout)
        val favouriteLayout = view.findViewById<View>(R.id.sheetFavouriteLayout)
        val downloadLayout = view.findViewById<View>(R.id.sheetDownloadLayout)
        tabLayout.setOnClickListener {
            openInNewTab(this, url, incognito)
            sheet.dismiss()
        }
        shareLayout.setOnClickListener {
            shareUrl(url)
            sheet.dismiss()
        }
        favouriteLayout.setOnClickListener {
            uiScope.launch {
                setAsFavorite(url, url)
            }
            sheet.dismiss()
        }
        if (shouldAllowDownload) {
            downloadLayout.setOnClickListener {
                downloadFileAsk(url, webView.settings.userAgentString, null, null, 0)
                sheet.dismiss()
            }
            downloadLayout.visibility = View.VISIBLE
        }
        sheet.setContentView(view)
        sheet.show()
    }
    private fun requestStoragePermission() {
        val permissionArray = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        requestPermissions(permissionArray, STORAGE_PERM_REQ)
    }

    private fun hasStoragePermission(): Boolean {
        val result = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    /*
     * This is called only if GeolocationPermissions doesn't have an explicit entry for @origin
     */
    override fun showLocationDialog(origin: String, callback: GeolocationPermissions.Callback) {
        locationDialogCallback = {
            AlertDialog.Builder(this)
                .setTitle(R.string.location_dialog_title)
                .setMessage(getString(R.string.location_dialog_message, origin))
                .setPositiveButton(R.string.location_dialog_allow) { _, _ ->
                    callback(origin, true, true)
                }
                .setNegativeButton(R.string.location_dialog_block) { _, _ ->
                    callback(origin, false, true)
                }
                .setNeutralButton(R.string.location_dialog_cancel) { _, _ ->
                    callback(origin, false, false)
                }
                .setOnCancelListener {
                    callback(origin, false, false)
                }
                .create()
                .show()
        }

        if (!permissionsUtils.locationPermissionsGranted()) {
            locationPermissionRequest.launch(PermissionsUtils.locationPermissions)
        } else {
            locationDialogCallback()
        }
    }

    override fun onFaviconLoaded(favicon: Bitmap?) {
        favicon?.let { it ->
            if (it.isRecycled) {
                return
            }
            urlIcon = it.copy(it.config, true)
            //updateTaskDescription()
            val color = (urlIcon?.takeUnless { it.isRecycled }?.let { bitmap ->
                    UiUtils.getColor(bitmap, false).takeUnless { it == Color.TRANSPARENT}
                } ?: UiUtils.getGray(this.resources)
            )

            setTaskDescription(TaskDescription(webView.title,
                    urlIcon,color))
            window.statusBarColor = color
            window.navigationBarColor = color
            toolbar.setBackgroundColor(color)

            if (!incognito) {
                if (urlIcon == null || urlIcon!!.isRecycled) {
                    incognitoIcon.visibility = View.GONE
                } else {
                    incognitoIcon.visibility = View.VISIBLE
                    incognitoIcon.setImageBitmap(urlIcon) //incognitoIcon.setImageDrawable(RoundedBitmapDrawableFactory.create(resources,urlIcon)) //incognitoIcon.setImageDrawable(BitmapDrawable(resources,urlIcon))
                    incognitoIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                }

            }

            if (!it.isRecycled) {
                it.recycle()
            }
        }
    }

    private fun updateTaskDescription() {
        setTaskDescription(
            TaskDescription(
                webView.title,
                urlIcon, Color.WHITE
            )
        )
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback) {
        customView?.let {
            callback.onCustomViewHidden()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        customView = view
        fullScreenCallback = callback
        setImmersiveMode(true)
        customView?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
        addContentView(
            customView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        appBarLayout.visibility = View.GONE
        webView.visibility = View.GONE
    }

    override fun onHideCustomView() {
        val customView = customView ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setImmersiveMode(false)
        appBarLayout.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE
        val viewGroup = customView.parent as ViewGroup
        viewGroup.removeView(customView)
        fullScreenCallback?.onCustomViewHidden()
        fullScreenCallback = null
        this.customView = null
    }

    private fun addShortcut(sTitle: String, sUrl: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            data = Uri.parse(sUrl)
            action = Intent.ACTION_MAIN
        }
        val launcherIcon = urlIcon?.let {
            IconCompat.createWithBitmap(UiUtils.getShortcutIcon(it, Color.WHITE))
        } ?: IconCompat.createWithResource(this, R.mipmap.ic_launcher)
        //val title = webView.title.toString()
        val shortcutInfoCompat = ShortcutInfoCompat.Builder(this, sTitle)
            .setShortLabel(sTitle)
            .setIcon(launcherIcon)
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcutInfoCompat, null)
    }

    @Suppress("DEPRECATION")
    private fun setImmersiveMode(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!enable)
            window.insetsController?.let {
                val flags = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                val behavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (enable) {
                    it.hide(flags)
                    it.systemBarsBehavior = behavior
                } else {
                    it.show(flags)
                    it.systemBarsBehavior = behavior.inv()
                }
            }
        } else {
            var flags = window.decorView.systemUiVisibility
            val immersiveModeFlags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            flags = if (enable) {
                flags or immersiveModeFlags
            } else {
                flags and immersiveModeFlags.inv()
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setImmersiveMode(hasFocus && customView != null)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "key_reach_mode" -> setUiMode()
            "key_dp_toolbar" -> setUiMode()
            "key_force_dark" -> setUiMode()
        }
    }

    private fun setUiMode() {
        // Now you don't see it
        constraintLayout.alpha = 0f
        // Magic happens
        toolbar.layoutParams.height = sharedPreferencesExt.dpToolbar*resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
        if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            if (sharedPreferencesExt.webForceDark) WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_ON)
            else WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_OFF)
        }

        changeUiMode(sharedPreferencesExt.reachModeEnabled)
        // Now you see it
        constraintLayout.alpha = 1f
    }

    private fun changeUiMode(isReachMode: Boolean) {
        appBarLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = when (isReachMode) {
                true -> ConstraintLayout.LayoutParams.UNSET
                false -> ConstraintLayout.LayoutParams.PARENT_ID
            }
            bottomToBottom = when (isReachMode) {
                true -> ConstraintLayout.LayoutParams.PARENT_ID
                false -> ConstraintLayout.LayoutParams.UNSET
            }
        }

        webView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToBottom = when (isReachMode) {
                true -> ConstraintLayout.LayoutParams.UNSET
                false -> ConstraintLayout.LayoutParams.PARENT_ID
            }
            bottomToTop = when (isReachMode) {
                true -> R.id.appBarLayout
                false -> ConstraintLayout.LayoutParams.UNSET
            }
            topToBottom = when (isReachMode) {
                true -> ConstraintLayout.LayoutParams.UNSET
                false -> R.id.appBarLayout
            }
            topToTop = when (isReachMode) {
                true -> ConstraintLayout.LayoutParams.PARENT_ID
                false -> ConstraintLayout.LayoutParams.UNSET
            }
        }
    }

    private fun pathSaveWebArchive(): String {
        return (getExternalFilesDir(null).toString() //not working gradle: targetSdkVersion 30
                + File.separator)
    }

    private fun nameSaveWebArchive(): String {
        return (webView.title!!.replace("[^a-zA-Z0-9\\-]".toRegex(), "_")
                + ".mht")
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PROVIDER = "${BuildConfig.APPLICATION_ID}.fileprovider"
        private const val STORAGE_PERM_REQ = 423
        private const val ALWAYS_DEFAULT_TO_INCOGNITO = 1
        private const val EXTERNAL_DEFAULT_TO_INCOGNITO = 2
        // Intents used for QuickTiles and other shortcuts
        fun handleShortcuts(c: Context, shortcut: String?): Boolean {
            when (shortcut) {
                "incognito" -> {
                    val intent = Intent(c, MainActivity::class.java)
                    intent.putExtra(IntentUtils.EXTRA_INCOGNITO, true)
                    c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "newtab" -> c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "favorites" -> c.startActivity(Intent(c, FavoriteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "history" -> c.startActivity(Intent(c, HistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "killall" -> TabUtils.killAll(c)
            }
            return true
        }
    }
}
