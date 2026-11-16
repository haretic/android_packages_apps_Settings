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

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

/**
 * The built-in speaker's tone correction, on devices that keep its switch in a system property
 * (config_speaker_tone_property; empty hides the switch). A property that equals the device's
 * default (config_speaker_tone_default) is stored empty, and a write to
 * config_speaker_gain_apply_property has the device put the change in.
 */
public class SpeakerTonePreferenceController extends TogglePreferenceController {

    private final String mProperty;
    private final String mApplyProperty;
    private final boolean mDefault;

    public SpeakerTonePreferenceController(@NonNull Context context, @NonNull String key) {
        super(context, key);
        final Resources res = context.getResources();
        mProperty = res.getString(R.string.config_speaker_tone_property);
        mApplyProperty = res.getString(R.string.config_speaker_gain_apply_property);
        mDefault = res.getBoolean(R.bool.config_speaker_tone_default);
    }

    @Override
    public int getAvailabilityStatus() {
        return TextUtils.isEmpty(mProperty) ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return SystemProperties.getBoolean(mProperty, mDefault);
    }

    @Override
    public boolean setChecked(boolean checked) {
        SystemProperties.set(mProperty, checked == mDefault ? "" : checked ? "1" : "0");
        if (!TextUtils.isEmpty(mApplyProperty)) {
            SystemProperties.set(mApplyProperty, Long.toString(SystemClock.elapsedRealtime()));
        }
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_sound;
    }
}
