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

package com.android.systemui.media.dialog

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.PowerExemptionManager
import android.view.View
import com.android.internal.logging.UiEventLogger
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.systemui.animation.DialogLaunchAnimator
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.media.nearby.NearbyMediaDevicesManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import java.util.Optional
import javax.inject.Inject

/**
 * Factory to create [MediaOutputDialog] objects.
 */
class MediaOutputDialogFactory @Inject constructor(
    private val context: Context,
    private val mediaSessionManager: MediaSessionManager,
    private val lbm: LocalBluetoothManager?,
    private val starter: ActivityStarter,
    private val broadcastSender: BroadcastSender,
    private val notifCollection: CommonNotifCollection,
    private val uiEventLogger: UiEventLogger,
    private val dialogLaunchAnimator: DialogLaunchAnimator,
    private val nearbyMediaDevicesManagerOptional: Optional<NearbyMediaDevicesManager>,
    private val audioManager: AudioManager,
    private val powerExemptionManager: PowerExemptionManager
) {
    companion object {
        var mediaOutputDialog: MediaOutputDialog? = null
    }

    /** Creates a [MediaOutputDialog] for the given package. */
    fun create(packageName: String, aboveStatusBar: Boolean, view: View? = null) {
        // Dismiss the previous dialog, if any.
        mediaOutputDialog?.dismiss()

        val controller = MediaOutputController(
            context, packageName,
            mediaSessionManager, lbm, starter, notifCollection,
            dialogLaunchAnimator, nearbyMediaDevicesManagerOptional, audioManager,
            powerExemptionManager
        )
        val dialog =
            MediaOutputDialog(context, aboveStatusBar, broadcastSender, controller, uiEventLogger)
        mediaOutputDialog = dialog

        // Show the dialog.
        if (view != null) {
            dialogLaunchAnimator.showFromView(dialog, view)
        } else {
            dialog.show()
        }
    }

    /** dismiss [MediaOutputDialog] if exist. */
    fun dismiss() {
        mediaOutputDialog?.dismiss()
        mediaOutputDialog = null
    }
}
