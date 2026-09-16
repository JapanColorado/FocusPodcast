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

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import allen.town.focus_common.util.Timber;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class SingleListDialogFragmentCompat extends ATEPreferenceDialogFragment {
    private DialogInterface.OnClickListener mListener;
    private CharSequence[] mItems;
    private int mClickedDialogEntryIndex;
    private String mTitle;
    private String mContent;

    public static SingleListDialogFragmentCompat getInstance(int selectIndex, @Nullable CharSequence[] items, @Nullable final DialogInterface.OnClickListener listener,String title) {
        final SingleListDialogFragmentCompat fragment = new SingleListDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        fragment.mListener = listener;
        fragment.mItems = items;
        fragment.mClickedDialogEntryIndex = selectIndex;
        fragment.mTitle=title;
        return fragment;
    }

    public static SingleListDialogFragmentCompat getInstance(int selectIndex, @Nullable CharSequence[] items, @Nullable final DialogInterface.OnClickListener listener, String title, String content) {
        final SingleListDialogFragmentCompat fragment = new SingleListDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        fragment.mListener = listener;
        fragment.mItems = items;
        fragment.mClickedDialogEntryIndex = selectIndex;
        fragment.mTitle = title;
        fragment.mContent = content;
        return fragment;
    }


    @Override
    protected void onPrepareDialogBuilder(MaterialAlertDialogBuilder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setSingleChoiceItems(mItems, mClickedDialogEntryIndex, (dialog, which) -> {
            mClickedDialogEntryIndex = which;
//            dismiss();
//            onClick(dialog, which);
        });

        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
        builder.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mListener.onClick(dialog, mClickedDialogEntryIndex);
            }
        });
        builder.setNeutralButton(null, null);
    }
    @Override
    public String getTitleStr() {
        return mTitle;
    }

    @Override
    public String getContentStr() {
        return mContent;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        Timber.d("onClick: " + which);
        mClickedDialogEntryIndex = which;
        super.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
    }
}