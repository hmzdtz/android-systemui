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

package com.android.systemui.log

import android.os.Trace
import android.util.Log
import com.android.systemui.log.dagger.LogModule
import com.android.systemui.util.collection.RingBuffer
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * A simple ring buffer of recyclable log messages
 *
 * The goal of this class is to enable logging that is both extremely chatty and extremely
 * lightweight. If done properly, logging a message will not result in any heap allocations or
 * string generation. Messages are only converted to strings if the log is actually dumped (usually
 * as the result of taking a bug report).
 *
 * You can dump the entire buffer at any time by running:
 *
 * ```
 * $ adb shell dumpsys activity service com.android.systemui/.SystemUIService <bufferName>
 * ```
 *
 * ...where `bufferName` is the (case-sensitive) [name] passed to the constructor.
 *
 * By default, only messages of WARN level or higher are echoed to logcat, but this can be adjusted
 * locally (usually for debugging purposes).
 *
 * To enable logcat echoing for an entire buffer:
 *
 * ```
 * $ adb shell settings put global systemui/buffer/<bufferName> <level>
 * ```
 *
 * To enable logcat echoing for a specific tag:
 *
 * ```
 * $ adb shell settings put global systemui/tag/<tag> <level>
 * ```
 *
 * In either case, `level` can be any of `verbose`, `debug`, `info`, `warn`, `error`, `assert`, or
 * the first letter of any of the previous.
 *
 * Buffers are provided by [LogModule]. Instances should be created using a [LogBufferFactory].
 *
 * @param name The name of this buffer, printed when the buffer is dumped and in some other
 * situations.
 * @param maxSize The maximum number of messages to keep in memory at any one time. Buffers start
 * out empty and grow up to [maxSize] as new messages are logged. Once the buffer's size reaches
 * the maximum, it behaves like a ring buffer.
 */
class LogBuffer @JvmOverloads constructor(
    private val name: String,
    private val maxSize: Int,
    private val logcatEchoTracker: LogcatEchoTracker,
    private val systrace: Boolean = true
) {
    private val buffer = RingBuffer(maxSize) { LogMessageImpl.create() }

    private val echoMessageQueue: BlockingQueue<LogMessage>? =
        if (logcatEchoTracker.logInBackgroundThread) ArrayBlockingQueue(10) else null

    init {
        if (logcatEchoTracker.logInBackgroundThread && echoMessageQueue != null) {
            thread(start = true, name = "LogBuffer-$name", priority = Thread.NORM_PRIORITY) {
                try {
                    while (true) {
                        echoToDesiredEndpoints(echoMessageQueue.take())
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    var frozen = false
        private set

    private val mutable
        get() = !frozen && maxSize > 0

    /**
     * Logs a message to the log buffer
     *
     * May also log the message to logcat if echoing is enabled for this buffer or tag.
     *
     * The actual string of the log message is not constructed until it is needed. To accomplish
     * this, logging a message is a two-step process. First, a fresh instance  of [LogMessage] is
     * obtained and is passed to the [initializer]. The initializer stores any relevant data on the
     * message's fields. The message is then inserted into the buffer where it waits until it is
     * either pushed out by newer messages or it needs to printed. If and when this latter moment
     * occurs, the [printer] function is called on the message. It reads whatever data the
     * initializer stored and converts it to a human-readable log message.
     *
     * @param tag A string of at most 23 characters, used for grouping logs into categories or
     * subjects. If this message is echoed to logcat, this will be the tag that is used.
     * @param level Which level to log the message at, both to the buffer and to logcat if it's
     * echoed. In general, a module should split most of its logs into either INFO or DEBUG level.
     * INFO level should be reserved for information that other parts of the system might care
     * about, leaving the specifics of code's day-to-day operations to DEBUG.
     * @param initializer A function that will be called immediately to store relevant data on the
     * log message. The value of `this` will be the LogMessage to be initialized.
     * @param printer A function that will be called if and when the message needs to be dumped to
     * logcat or a bug report. It should read the data stored by the initializer and convert it to
     * a human-readable string. The value of `this` will be the LogMessage to be printed.
     * **IMPORTANT:** The printer should ONLY ever reference fields on the LogMessage and NEVER any
     * variables in its enclosing scope. Otherwise, the runtime will need to allocate a new instance
     * of the printer for each call, thwarting our attempts at avoiding any sort of allocation.
     */
    inline fun log(
        tag: String,
        level: LogLevel,
        initializer: LogMessage.() -> Unit,
        noinline printer: LogMessage.() -> String
    ) {
        val message = obtain(tag, level, printer)
        initializer(message)
        commit(message)
    }

    /**
     * You should call [log] instead of this method.
     *
     * Obtains the next [LogMessage] from the ring buffer. If the buffer is not yet at max size,
     * grows the buffer by one.
     *
     * After calling [obtain], the message will now be at the end of the buffer. The caller must
     * store any relevant data on the message and then call [commit].
     */
    @Synchronized
    fun obtain(
        tag: String,
        level: LogLevel,
        printer: (LogMessage) -> String
    ): LogMessageImpl {
        if (!mutable) {
            return FROZEN_MESSAGE
        }
        val message = buffer.advance()
        message.reset(tag, level, System.currentTimeMillis(), printer)
        return message
    }

    /**
     * You should call [log] instead of this method.
     *
     * After acquiring a message via [obtain], call this method to signal to the buffer that you
     * have finished filling in its data fields. The message will be echoed to logcat if
     * necessary.
     */
    @Synchronized
    fun commit(message: LogMessage) {
        if (!mutable) {
            return
        }
        // Log in the background thread only if echoMessageQueue exists and has capacity (checking
        // capacity avoids the possibility of blocking this thread)
        if (echoMessageQueue != null && echoMessageQueue.remainingCapacity() > 0) {
            try {
                echoMessageQueue.put(message)
            } catch (e: InterruptedException) {
                // the background thread has been shut down, so just log on this one
                echoToDesiredEndpoints(message)
            }
        } else {
            echoToDesiredEndpoints(message)
        }
    }

    /** Sends message to echo after determining whether to use Logcat and/or systrace. */
    private fun echoToDesiredEndpoints(message: LogMessage) {
        val includeInLogcat = logcatEchoTracker.isBufferLoggable(name, message.level) ||
                logcatEchoTracker.isTagLoggable(message.tag, message.level)
        echo(message, toLogcat = includeInLogcat, toSystrace = systrace)
    }

    /** Converts the entire buffer to a newline-delimited string */
    @Synchronized
    fun dump(pw: PrintWriter, tailLength: Int) {
        val iterationStart = if (tailLength <= 0) {
            0
        } else {
            max(0, buffer.size - tailLength)
        }

        for (i in iterationStart until buffer.size) {
            dumpMessage(buffer[i], pw)
        }
    }

    /**
     * "Freezes" the contents of the buffer, making it immutable until [unfreeze] is called.
     * Calls to [log], [obtain], and [commit] will not affect the buffer and will return dummy
     * values if necessary.
     */
    @Synchronized
    fun freeze() {
        if (!frozen) {
            log(TAG, LogLevel.DEBUG, { str1 = name }, { "$str1 frozen" })
            frozen = true
        }
    }

    /**
     * Undoes the effects of calling [freeze].
     */
    @Synchronized
    fun unfreeze() {
        if (frozen) {
            log(TAG, LogLevel.DEBUG, { str1 = name }, { "$str1 unfrozen" })
            frozen = false
        }
    }

    private fun dumpMessage(message: LogMessage, pw: PrintWriter) {
        pw.print(DATE_FORMAT.format(message.timestamp))
        pw.print(" ")
        pw.print(message.level.shortString)
        pw.print(" ")
        pw.print(message.tag)
        pw.print(": ")
        pw.println(message.printer(message))
    }

    private fun echo(message: LogMessage, toLogcat: Boolean, toSystrace: Boolean) {
        if (toLogcat || toSystrace) {
            val strMessage = message.printer(message)
            if (toSystrace) {
                echoToSystrace(message, strMessage)
            }
            if (toLogcat) {
                echoToLogcat(message, strMessage)
            }
        }
    }

    private fun echoToSystrace(message: LogMessage, strMessage: String) {
        Trace.instantForTrack(
            Trace.TRACE_TAG_APP, "UI Events",
            "$name - ${message.level.shortString} ${message.tag}: $strMessage"
        )
    }

    private fun echoToLogcat(message: LogMessage, strMessage: String) {
        when (message.level) {
            LogLevel.VERBOSE -> Log.v(message.tag, strMessage)
            LogLevel.DEBUG -> Log.d(message.tag, strMessage)
            LogLevel.INFO -> Log.i(message.tag, strMessage)
            LogLevel.WARNING -> Log.w(message.tag, strMessage)
            LogLevel.ERROR -> Log.e(message.tag, strMessage)
            LogLevel.WTF -> Log.wtf(message.tag, strMessage)
        }
    }
}

private const val TAG = "LogBuffer"
private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
private val FROZEN_MESSAGE = LogMessageImpl.create()