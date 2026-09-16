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

package allen.town.focus_common.common.prefs.supportv7.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.Set;

import allen.town.focus_common.common.prefs.supportv7.ATEMultiListPreference;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class ATEMultiListPreferenceDialogFragmentCompat extends ATEPreferenceDialogFragment {
    private static final String TAG = "ATEPreferenceDialog";
    private boolean[] mClickedDialogEntryIndex;

    public static ATEMultiListPreferenceDialogFragmentCompat getInstance(String key) {
        final ATEMultiListPreferenceDialogFragmentCompat fragment = new ATEMultiListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    private ATEMultiListPreference getListPreference() {
        return (ATEMultiListPreference) getPreference();
    }

    @Override
    protected void onPrepareDialogBuilder(MaterialAlertDialogBuilder builder) {
        super.onPrepareDialogBuilder(builder);

        final ATEMultiListPreference preference = getListPreference();

        if (preference.getEntries() == null || preference.getEntryValues() == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }


        mClickedDialogEntryIndex = preference.getSelectedItemsList();

        builder.setMultiChoiceItems(preference.getEntries(), preference.getSelectedItemsList(),(dialog, which,isChecked)->{
            mClickedDialogEntryIndex[which] = isChecked;
        });

        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onDialogClosed(true);
            }
        });
//        builder.setNegativeButton(null, null);
//        builder.setNeutralButton(null, null);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        final ATEMultiListPreference preference = getListPreference();
        Log.i(TAG, "onDialogClosed: " + positiveResult);
        Set<String> values = new HashSet<>();
        if (positiveResult &&
                preference.getEntryValues() != null) {
            for (int i = 0; i < mClickedDialogEntryIndex.length; i++) {
                if(mClickedDialogEntryIndex[i]){
                    values.add(preference.getEntryValues()[i].toString());
                }
            }
            Log.i(TAG, "onDialogClosed: value " + mClickedDialogEntryIndex.length);
            if (preference.callChangeListener(values)) {
                preference.setValues(values);
                Log.i(TAG, "onDialogClosed: set value ");
            }
        }
    }

}