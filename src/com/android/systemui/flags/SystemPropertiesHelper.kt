/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.flags

import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/**
 * Proxy to make {@link SystemProperties} easily testable.
 */
@SysUISingleton
open class SystemPropertiesHelper @Inject constructor() {
    fun get(name: String): String {
        return SystemProperties.get(name)
    }

    fun getBoolean(name: String, default: Boolean): Boolean {
        return SystemProperties.getBoolean(name, default)
    }

    fun setBoolean(name: String, value: Boolean) {
        SystemProperties.set(name, if (value) "1" else "0")
    }

    fun set(name: String, value: String) {
        SystemProperties.set(name, value)
    }

    fun set(name: String, value: Int) {
        set(name, value.toString())
    }

    fun erase(name: String) {
        set(name, "")
    }
}