/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;

/**
 * Wrapper around internal ActivityOptions creation.
 */
public abstract class ActivityOptionsCompat {

    /**
     * @return ActivityOptions for starting a task in split screen as the primary window.
     * @Deprecated
     */
    public static ActivityOptions makeSplitScreenOptions(boolean dockTopLeft) {
        return ActivityOptions.makeBasic();
    }

    /**
     * @return ActivityOptions for starting a task in freeform.
     */
    public static ActivityOptions makeFreeformOptions() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        return options;
    }

    public static ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapterCompat remoteAnimationAdapter) {
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter.getWrapped(),
                remoteAnimationAdapter.getRemoteTransition().getTransition());
    }

    /**
     * Constructs an ActivityOptions object that will delegate its transition handling to a
     * `remoteTransition`.
     */
    public static ActivityOptions makeRemoteTransition(RemoteTransitionCompat remoteTransition) {
        return ActivityOptions.makeRemoteTransition(remoteTransition.getTransition());
    }

    /**
     * Returns ActivityOptions for overriding task transition animation.
     */
    public static ActivityOptions makeCustomAnimation(Context context, int enterResId,
                                                      int exitResId, final Runnable callback,
                                                      final Handler callbackHandler) {
        return ActivityOptions.makeCustomTaskAnimation(context, enterResId, exitResId,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted(long elapsedRealTime) {
                        if (callback != null) {
                            callbackHandler.post(callback);
                        }
                    }
                }, null /* finishedListener */);
    }

    /**
     * Sets the flag to freeze the recents task list reordering as a part of launching the activity.
     */
    public static ActivityOptions setFreezeRecentTasksList(ActivityOptions opts) {
        opts.setFreezeRecentTasksReordering();
        return opts;
    }

    /**
     * Sets the launch event time from launcher.
     */
    public static ActivityOptions setLauncherSourceInfo(ActivityOptions opts, long uptimeMillis) {
        opts.setSourceInfo(ActivityOptions.SourceInfo.TYPE_LAUNCHER, uptimeMillis);
        return opts;
    }
}
