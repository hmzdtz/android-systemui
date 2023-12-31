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

package com.android.systemui.media.taptotransfer.common

import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.LogcatEchoTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.PrintWriter
import java.io.StringWriter

@SmallTest
class MediaTttLoggerTest : SysuiTestCase() {

    private lateinit var buffer: LogBuffer
    private lateinit var logger: MediaTttLogger

    @Before
    fun setUp() {
        buffer = LogBufferFactory(DumpManager(), mock(LogcatEchoTracker::class.java))
            .create("buffer", 10)
        logger = MediaTttLogger(DEVICE_TYPE_TAG, buffer)
    }

    @Test
    fun logStateChange_bufferHasDeviceTypeTagAndStateNameAndId() {
        val stateName = "test state name"
        val id = "test id"

        logger.logStateChange(stateName, id)

        val stringWriter = StringWriter()
        buffer.dump(PrintWriter(stringWriter), tailLength = 0)
        val actualString = stringWriter.toString()

        assertThat(actualString).contains(DEVICE_TYPE_TAG)
        assertThat(actualString).contains(stateName)
        assertThat(actualString).contains(id)
    }

    @Test
    fun logChipRemoval_bufferHasDeviceTypeAndReason() {
        val reason = "test reason"
        logger.logChipRemoval(reason)

        val stringWriter = StringWriter()
        buffer.dump(PrintWriter(stringWriter), tailLength = 0)
        val actualString = stringWriter.toString()

        assertThat(actualString).contains(DEVICE_TYPE_TAG)
        assertThat(actualString).contains(reason)
    }
}

private const val DEVICE_TYPE_TAG = "TEST TYPE"
