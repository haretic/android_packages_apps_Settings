/*
 * Copyright (C) 2026 The haretic Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.sound;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.SliderPreferenceController;
import com.android.settingslib.widget.SliderPreference;

/**
 * The built-in speaker's gain in dB, on devices that keep it in a system property
 * (config_speaker_gain_property; empty hides the slider). The slider spans the gains the device
 * calls safe (config_speaker_gain_min to _max); a higher one set elsewhere shows as the top.
 * A property that equals the default is stored empty, and a write to
 * config_speaker_gain_apply_property has the device put the gain in.
 */
public class SpeakerGainPreferenceController extends SliderPreferenceController {

    private final String mProperty;
    private final String mApplyProperty;
    private final int mMin;
    private final int mMax;
    private final int mDefault;
    private SliderPreference mPreference;

    public SpeakerGainPreferenceController(@NonNull Context context, @NonNull String key) {
        super(context, key);
        final Resources res = context.getResources();
        mProperty = res.getString(R.string.config_speaker_gain_property);
        mApplyProperty = res.getString(R.string.config_speaker_gain_apply_property);
        mMin = res.getInteger(R.integer.config_speaker_gain_min);
        mMax = res.getInteger(R.integer.config_speaker_gain_max);
        mDefault = res.getInteger(R.integer.config_speaker_gain_default);
    }

    @Override
    public int getAvailabilityStatus() {
        return TextUtils.isEmpty(mProperty) || mMax <= mMin ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference != null) {
            mPreference.setMin(getMin());
            mPreference.setMax(getMax());
            mPreference.setUpdatesContinuously(true);
            mPreference.setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setSummary(mContext.getString(R.string.speaker_gain_summary, gain()));
    }

    /** The gain in dB as stored, which may be past the slider's top. */
    private int gain() {
        try {
            return Integer.parseInt(SystemProperties.get(mProperty, ""));
        } catch (NumberFormatException e) {
            return mDefault;
        }
    }

    // The slider counts from 0: its widget has no negative values
    @Override
    public int getSliderPosition() {
        return Math.max(mMin, Math.min(mMax, gain())) - mMin;
    }

    @Override
    public boolean setSliderPosition(int position) {
        final int db = Math.max(mMin, Math.min(mMax, position + mMin));
        if (db == gain()) {
            return true;
        }
        SystemProperties.set(mProperty, db == mDefault ? "" : Integer.toString(db));
        if (!TextUtils.isEmpty(mApplyProperty)) {
            SystemProperties.set(mApplyProperty, Long.toString(SystemClock.elapsedRealtime()));
        }
        if (mPreference != null) {
            mPreference.setSummary(mContext.getString(R.string.speaker_gain_summary, db));
        }
        return true;
    }

    @Override
    public int getMax() {
        return mMax - mMin;
    }

    @Override
    public int getMin() {
        return 0;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_sound;
    }
}
