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
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage

class LaunchFileActivity : AppCompatActivity() {
    private var url: String? = null
    private var emlPart = 0
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
        var bEml = false
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
                    bEml = true
                }
            } else sMime = mimeHead(uri)
        } else if (intent!!.dataString!!.endsWith(")")) sMime = mimeHead(uri)
        if (uri.toString().endsWith(".eml") || mimeHead(uri) == ".eml") {
            sMime = ".html"
            bEml = true
        }
        val f = File(baseContext.cacheDir, uri!!.lastPathSegment!!.replace(":", "").replace("/", ".")
                + sMime)
        try {
            fos = FileOutputStream(f)
            val input = baseContext.contentResolver.openInputStream(uri)
            if (bEml) {
                val props = System.getProperties()
                props["mail.host"] = "smtp.dummydomain.com"
                props["mail.transport.protocol"] = "smtp"
                val mailSession: Session = Session.getDefaultInstance(props, null)
                val message = MimeMessage(mailSession, input)
                rfcHead(message, mailSession)
                fos!!.flush()
                fos!!.close()
            } else {
                val buffer = ByteArray(1024 * 4)
                var n = 0
                while (-1 != input!!.read(buffer).also { n = it }) {
                    fos!!.write(buffer, 0, n)
                }
            }
        } catch (e: IOException) {
            //Log.e("errro", e.toString());
        } catch (e: NullPointerException) {
        } catch (e: MessagingException) {
        }
        url = "file:///" + f.path
    }

    private fun htmlWrite(i: Int, s: String) {
        var sTab = ""
        if (i > 0) sTab = String(CharArray(i - 1)).replace("\u0000", "===")
        try {
            fos!!.write((Html.toHtml((SpannableString(sTab + s + "\n"))).toByteArray(Charset.forName("UTF-8"))))
        } catch (e: IOException) {
            //Log.e("errro", e.toString());
        }
    }

    private fun beforeS(src: String, s: String): String {
        return if (src.contains(s)) src.substring(0, src.indexOf(s)) else src
    }

    private fun rfcHead(message: MimeMessage, mailSession: Session) {
        try {
            htmlWrite(0, "$$$ : " + message.getDescription())
            htmlWrite(0, "SUBJECT : " + message.getSubject())
            htmlWrite(0, "FROM : " + message.getFrom().get(0))
            htmlWrite(0, "REPLYTO : " + message.getReplyTo().get(0))
            htmlWrite(0, "BODY : " + beforeS(message.getContentType(), ";"))
            if (message.getContentType().startsWith("multipart")) {
                val multiPart: Multipart = message.getContent() as Multipart
                pp(multiPart, ">> ", mailSession)
            } else htmlWrite(0, "--------------")
        } catch (e: IOException) {
            //Log.e("errro", e.toString());
        } catch (e: MessagingException) {
        }
    }

    private fun pp(multiPart: Multipart, s: String, mailSession: Session) {
        emlPart += 1
        val emlTab = String(CharArray(emlPart)).replace("\u0000", s)
        try {
            val numberOfParts: Int = multiPart.getCount()
            htmlWrite(0, "\n\n")
            htmlWrite(emlPart, "$emlTab--------------MULTIPART EMAIL:Parts=$numberOfParts")
            for (partCount in 0 until numberOfParts) {
                val part: MimeBodyPart = multiPart.getBodyPart(partCount) as MimeBodyPart
                htmlWrite(emlPart, "$emlTab°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°")
                htmlWrite(emlPart, emlTab + "Part type::" + beforeS(part.getContentType(), ";"))
                htmlWrite(emlPart, emlTab + "Part Name::" + part.getFileName())
                htmlWrite(emlPart, emlTab + "Part Description::" + part.getDescription())
                htmlWrite(emlPart, emlTab + "Part Disposition::" + part.getDisposition())
                htmlWrite(emlPart, emlTab + "Part Encoding::" + part.getEncoding())
                if (part.getContentType().startsWith("multipart")) {
                    val sub: Multipart = part.getContent() as Multipart
                    pp(sub, ">>>> ", mailSession)
                } else if (part.getContentType().startsWith("message/rfc822")) {
                    rfcHead(part.getContent() as MimeMessage, mailSession)
                } else if (part.getContentType().startsWith("text/html")) {
                    fos!!.write((part.getLineCount().toString() + part.getContent().toString()).toByteArray(Charset.forName("UTF-8")))
                } else if (part.getContentType().startsWith("image") && part.getEncoding() == "base64") {
                    fos!!.write(("<img src='data:" + beforeS(part.getContentType(), ";") + ";" + part.getEncoding() + ",").toByteArray(Charset.forName("UTF-8")))
                    val input: InputStream = part.getInputStream()
                    val buffer = ByteArray(4096)
                    val byteBuffer = ByteArrayOutputStream()
                    var byteRead: Int
                    while (input.read(buffer).also { byteRead = it } != -1) {
                        byteBuffer.write(buffer, 0, byteRead)
                    }
                    fos!!.write(Base64.encodeToString(byteBuffer.toByteArray(), Base64.DEFAULT).toByteArray(Charset.forName("UTF-8")))
                    fos!!.write("'>".toByteArray(Charset.forName("UTF-8")))
                }
                htmlWrite(emlPart, "...")
            }
        } catch (e: IOException) {
            //Log.e("errro", e.toString());
        } catch (e: MessagingException) {
        }
        htmlWrite(0, "\n\n")
        emlPart -= 1
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