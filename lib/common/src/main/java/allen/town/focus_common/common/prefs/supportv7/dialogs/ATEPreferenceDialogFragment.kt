/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package allen.town.focus_common.common.prefs.supportv7.dialogs

import allen.town.focus_common.views.AccentMaterialDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.DialogPreference.TargetFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
open class ATEPreferenceDialogFragment : DialogFragment(), DialogInterface.OnClickListener {
    private var mWhichButtonClicked = 0
    var preference: DialogPreference? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawFragment = this.targetFragment
//        check(rawFragment is TargetFragment) { "Target fragment must implement TargetFragment interface" }
        val fragment = rawFragment as? TargetFragment
        val key = this.arguments?.getString(ARG_KEY)
        preference = fragment?.findPreference(key!!)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = this.activity
        val builder = AccentMaterialDialog(
            context!!,
            com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert
        )
            .setTitle(if (TextUtils.isEmpty(getTitleStr()))preference?.dialogTitle else getTitleStr())
            .setIcon(preference?.dialogIcon)
            .setMessage(preference?.dialogMessage ?: getContentStr())
            .setPositiveButton(preference?.positiveButtonText, this)
            .setNegativeButton(preference?.negativeButtonText, this)
        onPrepareDialogBuilder(builder)
        val dialog = builder.create()
        if (needInputMethod()) {
            requestInputMethod(dialog)
        }
        return dialog
    }

    protected open fun onPrepareDialogBuilder(builder: MaterialAlertDialogBuilder?) {}
    protected fun needInputMethod(): Boolean {
        return false
    }

    protected open fun getTitleStr():String?{
        return null
    }

    protected open fun getContentStr(): String? {
        return null
    }

    private fun requestInputMethod(dialog: Dialog) {
        val window = dialog.window
        window!!.setSoftInputMode(5)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.i(TAG, "onDismiss: $mWhichButtonClicked")
        onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
    }

    open fun onDialogClosed(positiveResult: Boolean) {}
    override fun onClick(dialog: DialogInterface, which: Int) {
        Log.i(TAG, "onClick: $which")
        mWhichButtonClicked = which
        onDialogClosed(which == DialogInterface.BUTTON_POSITIVE)
    }

    companion object {
        protected const val ARG_KEY = "key"
        private const val TAG = "ATEPreferenceDialog"

        @JvmStatic
        fun newInstance(key: String?): ATEPreferenceDialogFragment {
            val fragment = ATEPreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}