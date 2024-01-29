/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.annotation.UiContext
import org.lineageos.jelly.R
import org.lineageos.jelly.utils.TabUtils
import java.io.BufferedReader
import java.text.DateFormat

class SslCertificateInfoDialog(
    @UiContext context: Context,
) : Dialog(context) {
    private val urlView by lazy { findViewById<TextView>(R.id.url) }
    private val domainView by lazy { findViewById<TextView>(R.id.domain) }
    private val issuedByCNView by lazy { findViewById<KeyValueView>(R.id.issuedByCnView) }
    private val issuedByOView by lazy { findViewById<KeyValueView>(R.id.issuedByOView) }
    private val issuedByUNView by lazy { findViewById<KeyValueView>(R.id.issuedByUnView) }
    private val x509 by lazy { findViewById<KeyValueView>(R.id.x509) }
    private val dismissButton by lazy { findViewById<Button>(R.id.dismissButton) }
    private val pingButton by lazy { findViewById<Button>(R.id.ping) }
    private val virusTotalButton by lazy { findViewById<Button>(R.id.virustotal) }
    private var domain = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.ssl_certificate_info_dialog)
        setTitle(R.string.ssl_cert_dialog_title)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        pingButton.setOnClickListener {
            urlView.text = try {
                Runtime.getRuntime().exec("ping -w 1 $domain").inputStream.bufferedReader().use(BufferedReader::readText)
            } catch (ex:Exception) {ex.toString()}
        }
        virusTotalButton.setOnClickListener {
            TabUtils.openInNewTab(context, "https://www.virustotal.com/gui/domain/$domain/summary", false)
            //TabUtils.openInNewTab(context, "https://www.mywot.com/scorecard/$domain", false)
        }
        dismissButton.setOnClickListener {
            dismiss()
        }
    }

    fun setUrlAndCertificate(
        url: String, certificate: SslCertificate
    ) {
        // Get the domain name
        domain = Uri.parse(url).host!!
        val nn = System.getProperty("line.separator")

        // Get the validity dates
        val startDate = certificate.validNotBeforeDate
        val endDate = certificate.validNotAfterDate

        // Update TextViews
        urlView.text = url// Runtime.getRuntime().exec("ping -w 1 "+ domain).inputStream.bufferedReader().use(BufferedReader::readText)
        domainView.text = (buildString {
            append(context.getString(R.string.ssl_cert_dialog_domain).uppercase() + nn)
            append(domain + nn )
            // ping -w 1 Uri.parse(url).host
            append(certificate.toString() + nn)
            append(context.getString(R.string.ssl_cert_dialog_validity).uppercase() + nn)
            append("<<" + context.getString(R.string.ssl_cert_dialog_issued_on) + nn)
            append(DateFormat.getDateTimeInstance().format(startDate) + nn)
            append(">>" + context.getString(R.string.ssl_cert_dialog_expires_on) + nn)
            append(DateFormat.getDateTimeInstance().format(endDate))
        })

        issuedByCNView.setText(
            R.string.ssl_cert_dialog_common_name,
            certificate.issuedBy.cName
        )
        issuedByOView.setText(
            R.string.ssl_cert_dialog_organization,
            certificate.issuedBy.oName
        )
        issuedByUNView.setText(
            R.string.ssl_cert_dialog_organizational_unit,
            certificate.issuedBy.uName
        )
        x509.setText(
            R.string.ssl_x509,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    certificate.x509Certificate.toString()
                else ""
        )

    }
}
