/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import org.lineageos.jelly.MainActivity

object TabUtils {
    fun openInNewTab(context: Context, url: String?, incognito: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            url?.takeIf { it.isNotEmpty() }?.let {
                data = Uri.parse(it)
            }
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            putExtra(IntentUtils.EXTRA_INCOGNITO, incognito)
        }
        context.startActivity(intent)
    }

    fun killAll(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.appTasks
        if (tasks != null && tasks.size > 0) {
            for (i in 1 until tasks.size) {
                tasks[i].setExcludeFromRecents(true)
                tasks[i].finishAndRemoveTask()
            }
            tasks[0].setExcludeFromRecents(true)
            tasks[0].finishAndRemoveTask()
        }
        Process.killProcess(Process.myPid())
    }

}
