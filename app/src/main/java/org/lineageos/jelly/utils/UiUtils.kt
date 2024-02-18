/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.webkit.WebSettings
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.palette.graphics.Palette
import org.lineageos.jelly.BuildConfig

object UiUtils {
    fun isColorLight(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val hsl = FloatArray(3)
        ColorUtils.RGBToHSL(red, green, blue, hsl)
        return hsl[2] > 0.5f
    }

    fun userWeb(s: String): String {
        var tmp = "0"
        if (s.indexOf("Chrome/") > 0) {
            tmp = s.substring(s.indexOf("Chrome/")+7)
            if (s.indexOf(".") > 0) {
                tmp = tmp.substring(0, tmp.indexOf("."))
            }
        }
        return tmp
    }

    fun fakeUserAgent(ctx: Context, b: Boolean, bWay: Boolean): String {
        val tmp = if (b) ((0..100000).random().toString() + "." + (0..1000).random())
        else "?????.???"
        return if (bWay) ("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE +
                "; Android SDK Build/" + tmp +
                "; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/" +
                userWeb(WebSettings.getDefaultUserAgent(ctx)) +
                ".0.1.2 Mobile Safari/537.36")
        else ("Mozilla/5.0 (Android 10; Mobile; rv:" +
                userWeb(WebSettings.getDefaultUserAgent(ctx)) +
                ".0) Gecko/"+
                userWeb(WebSettings.getDefaultUserAgent(ctx)) +
                ".0 Firefox/"+
                userWeb(WebSettings.getDefaultUserAgent(ctx)) +
                ".0")
    }

    fun getGray(resources: Resources?): Int {
        when (resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> return Color.GRAY
            Configuration.UI_MODE_NIGHT_NO -> return Color.LTGRAY
            Configuration.UI_MODE_NIGHT_UNDEFINED -> return Color.LTGRAY
        }
        return Color.LTGRAY
    }

    fun getColor(bitmap: Bitmap, incognito: Boolean): Int {
        val palette = Palette.from(bitmap).generate()
        val fallback = Color.TRANSPARENT
        return if (incognito) palette.getMutedColor(fallback) else palette.getVibrantColor(fallback)
    }

    fun getShortcutIcon(bitmap: Bitmap, themeColor: Int): Bitmap {
        val out = Bitmap.createBitmap(
            bitmap.width, bitmap.width,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.width)
        val radius = bitmap.width / 2.toFloat()
        paint.isAntiAlias = true
        paint.color = themeColor
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return Bitmap.createScaledBitmap(out, 192, 192, true)
    }

    fun dpToPx(res: Resources, dp: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, res.displayMetrics)

    /**
     * Hides the keyboard.
     *
     * @param window the [Window] where the view is attached to.
     * @param view The [View] that is currently accepting input.
     */
    fun hideKeyboard(window: Window, view: View) {
        WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.ime())
    }

    /**
     * Shows the keyboard.
     *
     * @param window the [Window] where the view is attached to.
     * @param view The [View] that is currently accepting input.
     */
    fun showKeyboard(window: Window, view: View) {
        WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.ime())
    }
}
