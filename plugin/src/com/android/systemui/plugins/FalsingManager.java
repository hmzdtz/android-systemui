/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.plugins;

import android.annotation.IntDef;
import android.net.Uri;
import android.view.MotionEvent;

import com.android.systemui.plugins.annotations.ProvidesInterface;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface that decides whether a touch on the phone was accidental. i.e. Pocket Dialing.
 * <p>
 * {@see com.android.systemui.classifier.FalsingManagerImpl}
 */
@ProvidesInterface(version = FalsingManager.VERSION)
public interface FalsingManager {
    int VERSION = 6;

    int NO_PENALTY = 0;
    int LOW_PENALTY = 1;
    int MODERATE_PENALTY = 2;
    int HIGH_PENALTY = 3;

    void onSuccessfulUnlock();

    boolean isUnlockingDisabled();

    /**
     * Returns true if the gesture should be rejected.
     */
    boolean isFalseTouch(int interactionType);

    /**
     * Does basic checking to see if gesture looks like a tap.
     * <p>
     * Only does the most basic of checks. No penalty is applied if this method returns false.
     * <p>
     * For more robust analysis, use {@link #isFalseTap(int)}.
     */
    boolean isSimpleTap();

    /**
     * Returns true if the FalsingManager thinks the last gesture was not a valid tap.
     * <p>
     * This method runs a more thorough analysis than the similar {@link #isSimpleTap()},
     * that can include historical interactions and other contextual cues to see
     * if the tap looks accidental.
     * <p>
     * Use this method to validate a tap for launching an action, like opening
     * a notification.
     * <p>
     * The only parameter, penalty, indicates how much this should affect future gesture
     * classifications if this tap looks like a false. As single taps are hard to confirm as false
     * or otherwise, a low penalty value is encouraged unless context indicates otherwise.
     */
    boolean isFalseTap(@Penalty int penalty);

    /**
     * Returns true if the last two gestures do not look like a double tap.
     * <p>
     * Only works on data that has already been reported to the FalsingManager. Be sure that
     * {@link com.android.systemui.classifier.FalsingCollector#onTouchEvent(MotionEvent)}
     * has already been called for all of the taps you want considered.
     * <p>
     * This looks at the last two gestures on the screen, ensuring that they meet the following
     * criteria:
     * <p>
     * a) There are at least two gestures.
     * b) The last two gestures look like taps.
     * c) The last two gestures look like a double tap taken together.
     * <p>
     * This method is _not_ context aware. That is to say, if two taps occur on two neighboring
     * views, but are otherwise close to one another, this will report a successful double tap.
     * It is up to the caller to decide
     *
     * @return
     */
    boolean isFalseDoubleTap();

    boolean isClassifierEnabled();

    boolean shouldEnforceBouncer();

    Uri reportRejectedTouch();

    boolean isReportingEnabled();

    /**
     * From com.android.systemui.Dumpable.
     */
    void dump(PrintWriter pw, String[] args);

    /**
     * Don't call this. It's meant for internal use to allow switching between implementations.
     * <p>
     * Tests may also call it.
     **/
    void cleanupInternal();

    /**
     * Call to report a ProximityEvent to the FalsingManager.
     */
    void onProximityEvent(ProximityEvent proximityEvent);

    /**
     * Adds a {@link FalsingBeliefListener}.
     */
    void addFalsingBeliefListener(FalsingBeliefListener listener);

    /**
     * Removes a {@link FalsingBeliefListener}.
     */
    void removeFalsingBeliefListener(FalsingBeliefListener listener);

    /**
     * Adds a {@link FalsingTapListener}.
     */
    void addTapListener(FalsingTapListener falsingTapListener);

    /**
     * Removes a {@link FalsingTapListener}.
     */
    void removeTapListener(FalsingTapListener falsingTapListener);

    @IntDef({
            NO_PENALTY,
            LOW_PENALTY,
            MODERATE_PENALTY,
            HIGH_PENALTY
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Penalty {
    }

    /**
     * Listener that is alerted when falsing belief level crosses a predfined threshold.
     */
    interface FalsingBeliefListener {
        void onFalse();
    }

    /**
     * Listener that is alerted when a double tap is required to confirm a single tap.
     **/
    interface FalsingTapListener {
        void onDoubleTapRequired();
    }

    /**
     * Passed to {@link FalsingManager#onProximityEvent}.
     */
    interface ProximityEvent {
        /**
         * Returns true when the proximity sensor was covered.
         */
        boolean getCovered();

        /**
         * Returns when the proximity sensor was covered in nanoseconds.
         */
        long getTimestampNs();
    }
}
