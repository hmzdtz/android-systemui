/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.util.sensors;

/**
 * A wrapper class for sensors that have a boolean state - above/below.
 */
public interface ThresholdSensor {
    /**
     * Optional label to use for logging.
     * <p>
     * This should be set to something meaningful by owner of the instance.
     */
    void setTag(String tag);

    /**
     * Change the delay used when registering the sensor.
     * <p>
     * If the sensor is already registered, this should cause it to re-register with the new
     * delay.
     */
    void setDelay(int delay);

    /**
     * True if this sensor successfully loads and can be listened to.
     */
    boolean isLoaded();

    /**
     * Registers with the sensor and calls the supplied callback on value change.
     * <p>
     * If this instance is paused, the listener will be recorded, but no registration with
     * the underlying physical sensor will occur until {@link #resume()} is called.
     *
     * @see #unregister(Listener)
     */
    void register(Listener listener);

    /**
     * Unregisters from the physical sensor without removing any supplied listeners.
     * <p>
     * No events will be sent to listeners as long as this sensor is paused.
     *
     * @see #resume()
     * @see #unregister(Listener)
     */
    void pause();

    /**
     * Resumes listening to the physical sensor after previously pausing.
     *
     * @see #pause()
     */
    void resume();

    /**
     * Unregister a listener with the sensor.
     *
     * @see #register(Listener)
     */
    void unregister(Listener listener);

    /**
     * Name of the sensor.
     */
    String getName();

    /**
     * Type of the sensor.
     */
    String getType();

    /**
     * Interface for listening to events on {@link ThresholdSensor}
     */
    interface Listener {
        /**
         * Called whenever the threshold for the registered sensor is crossed.
         */
        void onThresholdCrossed(ThresholdSensorEvent event);
    }
}
