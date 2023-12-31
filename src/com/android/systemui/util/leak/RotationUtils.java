/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.util.leak;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.annotation.IntDef;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.Surface;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class RotationUtils {

    public static final int ROTATION_NONE = 0;
    public static final int ROTATION_LANDSCAPE = 1;
    public static final int ROTATION_UPSIDE_DOWN = 2;
    public static final int ROTATION_SEASCAPE = 3;

    /**
     * @return the current rotation, differentiating between none (rot_0), landscape (rot_90), and
     * seascape (rot_180). upside down is not distinguished here
     */
    @Rotation
    public static int getRotation(Context context) {
        int rot = context.getDisplay().getRotation();
        if (rot == Surface.ROTATION_90) {
            return ROTATION_LANDSCAPE;
        } else if (rot == Surface.ROTATION_270) {
            return ROTATION_SEASCAPE;
        } else {
            return ROTATION_NONE;
        }
    }

    /**
     * @return the current rotation, differentiating between landscape (rot_90), seascape
     * (rot_270), and upside down (rot_180)
     */
    @Rotation
    public static int getExactRotation(Context context) {
        int rot = context.getDisplay().getRotation();
        if (rot == Surface.ROTATION_90) {
            return ROTATION_LANDSCAPE;
        } else if (rot == Surface.ROTATION_270) {
            return ROTATION_SEASCAPE;
        } else if (rot == Surface.ROTATION_180) {
            return ROTATION_UPSIDE_DOWN;
        } else {
            return ROTATION_NONE;
        }
    }

    /**
     * To string
     */
    public static String toString(@Rotation int rot) {
        switch (rot) {
            case ROTATION_NONE:
                return "None (0)";
            case ROTATION_LANDSCAPE:
                return "Landscape (1)";
            case ROTATION_UPSIDE_DOWN:
                return "Upside down (2)";
            case ROTATION_SEASCAPE:
                return "Seascape (3)";
            default:
                return "Unknown (" + rot + ")";
        }
    }

    /**
     * Create a Resources using the specified rotation for the configuration. Use this to retrieve
     * resources in values or values-land without needing an actual rotation to happen.
     *
     * @param rot     the target rotation for which to create the resources
     * @param context a context
     * @return a Resources object configured for the given orientation
     */
    public static Resources getResourcesForRotation(@Rotation int rot, Context context) {
        int orientation;
        switch (rot) {
            case ROTATION_NONE:
            case ROTATION_UPSIDE_DOWN:
                orientation = ORIENTATION_PORTRAIT;
                break;
            case ROTATION_LANDSCAPE:
            case ROTATION_SEASCAPE:
                orientation = ORIENTATION_LANDSCAPE;
                break;

            default:
                throw new IllegalArgumentException("Unknown rotation: " + rot);
        }
        Configuration c = new Configuration(context.getResources().getConfiguration());
        c.orientation = orientation;
        Context rotated = context.createConfigurationContext(c);
        return rotated.getResources();
    }

    // Not to be confused with Surface.Rotation
    @IntDef(prefix = {"ROTATION_"}, value = {
            ROTATION_NONE,
            ROTATION_LANDSCAPE,
            ROTATION_SEASCAPE,
            ROTATION_UPSIDE_DOWN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Rotation {
    }
}
