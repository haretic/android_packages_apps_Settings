/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;

import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;

/**
 * The haretic release, as its image is named: haretic-YYYYMMDD-nightly, -beta or -release.
 * Pressed a few times in a row, as Android's version is, it opens the easter egg, a falling
 * blocks game played with the wheel; the side button presses it, so the presses may be slower
 * than taps.
 */
public class HareticVersionPreferenceController extends BasePreferenceController {

    private static final String TAG = "HareticVersion";
    static final String PROPERTY = "ro.haretic.version";
    private static final String EASTER_EGG = "haretic.intent.action.EASTER_EGG";
    private static final int PRESSES = 5;
    /** The most between one press and the next. */
    private static final long PRESS_GAP_MS = 2000;

    private int mPresses;
    private long mLastPress;

    public HareticVersionPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return TextUtils.isEmpty(SystemProperties.get(PROPERTY))
                ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return SystemProperties.get(PROPERTY);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())
                || Utils.isMonkeyRunning()) {
            return false;
        }
        final long now = SystemClock.uptimeMillis();
        mPresses = now - mLastPress <= PRESS_GAP_MS ? mPresses + 1 : 1;
        mLastPress = now;
        if (mPresses < PRESSES) {
            return true;
        }
        mPresses = 0;
        if (mContext.getSystemService(UserManager.class)
                .hasUserRestriction(UserManager.DISALLOW_FUN)) {
            return true;
        }
        try {
            mContext.startActivity(new Intent(EASTER_EGG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No easter egg in this build");
        }
        return true;
    }
}
