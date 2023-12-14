package org.lineageos.jelly

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.util.Base64
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.lineageos.jelly.utils.TabUtils
import org.lineageos.jelly.utils.TabUtils.openInNewTab
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

class LaunchFileActivity : AppCompatActivity() {
    private var url: String? = null
    private var fos: FileOutputStream? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent = getIntent()
        if (intent != null && intent!!.action != null) {
            if (intent!!.action == Intent.ACTION_SEND) {
                url = intent!!.getStringExtra(Intent.EXTRA_TEXT)
                if (url == null) {
                    urlCacheLocalUri(intent!!.extras!![Intent.EXTRA_STREAM] as Uri?)
                    finish()
                }
                val indexOfUrl = url!!.toLowerCase().indexOf("http")
                if (indexOfUrl == -1) finish() else {
                    val containsURL = url!!.substring(indexOfUrl)
                    val endOfUrl = containsURL.indexOf(" ")
                    url = if (endOfUrl != -1) {
                        containsURL.substring(0, endOfUrl)
                    } else {
                        containsURL
                    }
                }
                openInNewTab(this, url, true)
            } else if (intent!!.action == Intent.ACTION_PROCESS_TEXT && intent!!.getStringExtra(Intent.EXTRA_PROCESS_TEXT) != null) {
                openInNewTab(this, intent!!.getStringExtra(Intent.EXTRA_PROCESS_TEXT), true)
            } else if (intent!!.action == Intent.ACTION_WEB_SEARCH && intent!!.getStringExtra(SearchManager.QUERY) != null) {
                openInNewTab(this, intent!!.getStringExtra(SearchManager.QUERY), true)
            } else if (intent!!.getBooleanExtra("kill_all", false)) {
                TabUtils.killAll(applicationContext)
            } else if (intent!!.scheme != null &&
                    (intent!!.scheme == "content" || intent!!.scheme == "file")) {
                if (intent!!.scheme == "content" || intent!!.dataString!!.endsWith(".eml") //|| (intent.getType().equals("message/rf822"))
                ) {
                    urlCacheLocalUri(intent!!.data)
                } else {
                    url = intent!!.dataString
                }
                if (!hasStoragePermissionRead()) {
                    //finish();
                } else {
                    Toast.makeText(this, "permission READ_storage granted", Toast.LENGTH_LONG).show()
                }
                //TabUtils.openInNewTab(this, url, true);
            }
        }
        finish()
    }

    private fun urlCacheLocalUri(uri: Uri?) {
        var sMime = ""
        if (intent!!.dataString == null || !intent!!.dataString!!.substring(intent!!.dataString!!.lastIndexOf("/")).contains(".")) {
            if (contentResolver.getType(uri!!) != null) {
                sMime = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri))
                if (sMime == ".bin") sMime = mimeHead(uri) else if (sMime == ".null") {
                    val i = contentResolver.getType(uri)!!.indexOf("/")
                    // if (i==0) sMime = "."; else
                    sMime = "." + contentResolver.getType(uri)!!.substring(i + 1)
                    if (sMime == ".*") sMime = mimeHead(uri)
                } else if (sMime == ".eml") {
                    sMime = ".html"
                }
            } else sMime = mimeHead(uri)
        } else if (intent!!.dataString!!.endsWith(")")) sMime = mimeHead(uri)
        if (uri.toString().endsWith(".eml") || mimeHead(uri) == ".eml") {
            sMime = ".html"
        }
        val f = File(baseContext.cacheDir, uri!!.lastPathSegment!!.replace(":", "").replace("/", ".")
                + sMime)
        try {
            fos = FileOutputStream(f)
            val input = baseContext.contentResolver.openInputStream(uri)
            val buffer = ByteArray(1024 * 4)
            var n = 0
            while (-1 != input!!.read(buffer).also { n = it }) {
                fos!!.write(buffer, 0, n)
            }
        } catch (e: IOException) {
            //Log.e("errro", e.toString());
        } catch (e: NullPointerException) {
        }
        url = "file:///" + f.path
    }

    private fun mimeHead(uri: Uri?): String {
        try {
            val buffer = ByteArray(1024)
            baseContext.contentResolver.openInputStream(uri!!)!!.read(buffer)
            var sHead = String(buffer, StandardCharsets.UTF_8)
            if (sHead.contains("\nContent-Transfer-Encoding: quoted-printable")) return ".mht"
            if (sHead.contains("\nContent-Transfer-Encoding: binary")) return ".htm"
            if (sHead.contains("\nContent-Type: multipart/mixed;")) return ".eml"
            sHead = sHead.toUpperCase()
            if (sHead.startsWith("<!DOCTYPE HTML")) return ".htm"
            if (sHead.startsWith("<?XML") && sHead.contains("\n<SVG")) return ".svg"
            if (sHead.startsWith("<?XML")) return ".xml"
            if (sHead.contains("\n<!DOCTYPE HTML")) return ".htm"
        } catch (e: IOException) {
            //Log.e("errro", e.toString());
            return e.toString()
        } catch (e: NullPointerException) {
            return e.toString()
        }
        return "."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "permission validation", Toast.LENGTH_LONG).show()
            openInNewTab(this, url, true)
        } else {
            Toast.makeText(this, "permission READ_storage DENIED", Toast.LENGTH_LONG).show()
            ActivityCompat.finishAffinity(this)
        }
    }

    private fun hasStoragePermissionRead(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            } else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            return false
        } else {
            openInNewTab(this, url, true)
        }
        return true
    }
}
