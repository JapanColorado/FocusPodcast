/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
@file:JvmName("ActivityExtensionsUtils")
package allen.town.focus_common.extensions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import com.google.android.material.appbar.MaterialToolbar

fun AppCompatActivity.applyToolbar(toolbar: MaterialToolbar?) {
    toolbar?.run {
        ToolbarContentTintHelper.colorBackButton(this)
        setSupportActionBar(this)
    }

}

/**
 * android 13需要动态获取通知权限
 */
fun AppCompatActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationManagerCompat.from(this)
            .areNotificationsEnabled()
    ) {
        requestPermissions(
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS
            ), notificationRequestCode()
        )
    }
}

fun AppCompatActivity.notificationRequestCode(): Int {
    return 10010
}

inline fun <reified T : Any> Activity.extra(key: String, default: T? = null) = lazy {
    val value = intent?.extras?.get(key)
    if (value is T) value else default
}

inline fun <reified T : Any> Intent.extra(key: String, default: T? = null) = lazy {
    val value = extras?.get(key)
    if (value is T) value else default
}

inline fun <reified T : Any> Activity.extraNotNull(key: String, default: T? = null) = lazy {
    val value = intent?.extras?.get(key)
    requireNotNull(if (value is T) value else default) { key }
}

fun Activity.dip(@DimenRes id: Int): Int {
    return resources.getDimensionPixelSize(id)
}

inline val Activity.rootView: View get() = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)