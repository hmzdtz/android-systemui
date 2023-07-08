/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.dreams.complication;

/**
 * A {@link ComplicationId} is a value to uniquely identify a complication during the current
 * runtime and within a particular scope. Any guarantees beyond this will need to be enforced
 * externally.
 */
public class ComplicationId {
    private final int mId;

    private ComplicationId(int id) {
        mId = id;
    }

    @Override
    public String toString() {
        return "ComplicationId{" + "mId=" + mId + "}";
    }

    /**
     * An associated factory for minting ids that are unique in for the factory's scope.
     */
    public static class Factory {
        private int mNextId;

        ComplicationId getNextId() {
            return new ComplicationId(mNextId++);
        }
    }
}
