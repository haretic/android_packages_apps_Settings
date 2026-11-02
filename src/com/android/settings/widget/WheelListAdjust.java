/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.widget;

import android.content.res.Resources;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.CustomListPreference;
import com.android.settings.R;
import com.android.settings.RestrictedListPreference;
import com.android.settings.display.TimeoutListPreference;

/**
 * The wheel UI's inline adjust for a list of choices: the side button's press grabs the row
 * instead of opening the list, the wheel's steps then run through the choices in the row's
 * summary, and a press sets the one shown. A press before any step opens the list as before.
 */
public final class WheelListAdjust implements View.OnKeyListener {

    /** Sets the choice shown, as a slider lets go, after a pause in the wheel's turning. */
    private static final long LET_GO_MS = 4000;

    private static WheelListAdjust sActive;

    private final ListPreference mPreference;
    private final View mRow;
    private final TextView mSummary;
    private final CharSequence mSummaryText;
    private final int mSummaryVisibility;
    private final int mStart;
    private int mIndex;
    private final Runnable mLetGo = () -> end(true);

    private WheelListAdjust(ListPreference preference, View row, int index) {
        mPreference = preference;
        mRow = row;
        mSummary = row.findViewById(android.R.id.summary);
        mSummaryText = mSummary != null ? mSummary.getText() : null;
        mSummaryVisibility = mSummary != null ? mSummary.getVisibility() : View.GONE;
        mStart = index;
        mIndex = index;
    }

    /**
     * Whether the press that would open {@code preference}'s list grabbed it, or set the choice
     * the wheel turned to, instead; false where the list should open.
     */
    public static boolean onDisplayDialog(PreferenceFragmentCompat fragment, Preference preference) {
        final WheelListAdjust active = sActive;
        if (active != null) {
            final boolean turned = active.mIndex != active.mStart;
            active.end(turned);
            if (active.mPreference == preference) {
                return turned;
            }
        }
        if (!(preference instanceof ListPreference list) || !isPlainList(list)) {
            return false;
        }
        final View row = findRow(fragment, preference);
        if (row == null || !row.hasFocus() || !isWheelFocus(row)) {
            return false;
        }
        final int index = list.findIndexOfValue(list.getValue());
        sActive = new WheelListAdjust(list, row, Math.max(0, index));
        sActive.start();
        return true;
    }

    /** Puts back a row the wheel holds, as its screen goes. */
    public static void cancel() {
        if (sActive != null) {
            sActive.end(false);
        }
    }

    /**
     * Whether {@code list}'s dialog only picks one of its choices: not where the dialog asks to
     * confirm, adds choices of its own or has some an admin holds back.
     */
    private static boolean isPlainList(ListPreference list) {
        final CharSequence[] entries = list.getEntries();
        if (entries == null || entries.length < 2 || list.getEntryValues() == null) {
            return false;
        }
        if (list instanceof RestrictedListPreference restricted) {
            if (restricted.isDisabledByAdmin()) {
                return false;
            }
            for (CharSequence entry : entries) {
                if (restricted.isRestrictedForEntry(entry)) {
                    return false;
                }
            }
            return list.getClass() == RestrictedListPreference.class
                    || list instanceof TimeoutListPreference;
        }
        return !(list instanceof CustomListPreference)
                || list.getClass() == CustomListPreference.class;
    }

    private static View findRow(PreferenceFragmentCompat fragment, Preference preference) {
        final RecyclerView list = fragment.getListView();
        if (list == null || !(list.getAdapter()
                instanceof PreferenceGroup.PreferencePositionCallback positions)) {
            return null;
        }
        final RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(
                positions.getPreferenceAdapterPosition(preference));
        return holder != null ? holder.itemView : null;
    }

    private static boolean isWheelFocus(View view) {
        // The view's own resources: they have the framework's overlays, where config_wheelFocus is
        final Resources res = view.getResources();
        final int id = res.getIdentifier("config_wheelFocus", "bool", "android");
        return id != 0 && res.getBoolean(id) && !view.isInTouchMode();
    }

    private void start() {
        mRow.setOnKeyListener(this);
        // The row's pressed state, and the wheel's ring thicker round it
        mRow.setPressed(true);
        show();
        mRow.postDelayed(mLetGo, LET_GO_MS);
        Toast.makeText(mRow.getContext(), R.string.wheel_list_grabbed, Toast.LENGTH_SHORT).show();
    }

    private void show() {
        if (mSummary != null) {
            mSummary.setText(mPreference.getEntries()[mIndex]);
            mSummary.setVisibility(View.VISIBLE);
        }
    }

    private void end(boolean set) {
        if (sActive == this) {
            sActive = null;
        }
        mRow.removeCallbacks(mLetGo);
        mRow.setOnKeyListener(null);
        mRow.setPressed(false);
        if (mSummary != null) {
            mSummary.setText(mSummaryText);
            mSummary.setVisibility(mSummaryVisibility);
        }
        if (set && mIndex != mStart) {
            final String value = mPreference.getEntryValues()[mIndex].toString();
            if (mPreference.callChangeListener(value)) {
                mPreference.setValue(value);
            }
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false;
        }
        if (v.isInTouchMode() || !v.hasFocus()) {
            end(false);
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // Down, the wheel's clockwise turn, goes on down the list
            final int last = mPreference.getEntries().length - 1;
            mIndex = Math.max(0, Math.min(last,
                    mIndex + (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
            show();
            mRow.removeCallbacks(mLetGo);
            mRow.postDelayed(mLetGo, LET_GO_MS);
        }
        return true;
    }
}
