// SPDX-License-Identifier: Apache-2.0
package com.android.settings.wheel;

import android.content.Context;
import android.os.UserHandle;

import lineageos.providers.LineageSettings;

/** Mirrors the policy that consumes power-key gestures while wheel UI is active. */
public final class WheelSideButtonUtils {
    private WheelSideButtonUtils() {}

    public static boolean isEnabled(Context context) {
        return context.getResources()
                        .getBoolean(com.android.internal.R.bool.config_r1SideButtonInput)
                && LineageSettings.Secure.getIntForUser(
                                context.getContentResolver(),
                                LineageSettings.Secure.WHEEL_UI,
                                1,
                                UserHandle.USER_CURRENT)
                        != 0;
    }
}
