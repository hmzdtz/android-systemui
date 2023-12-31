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

package com.android.systemui.statusbar.policy;

import android.net.Uri;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener;

import javax.inject.Inject;

/**
 * Handles granting and revoking inline URI grants associated with RemoteInputs.
 */
@SysUISingleton
public class RemoteInputUriController {

    private static final String TAG = "RemoteInputUriController";
    private final IStatusBarService mStatusBarManagerService;
    /**
     * Ensures that inline URI permissions are cleared when notification entries are removed from
     * the shade.
     */
    private final NotifCollectionListener mInlineUriListener = new NotifCollectionListener() {
        @Override
        public void onEntryRemoved(@NonNull NotificationEntry entry, int reason) {
            try {
                mStatusBarManagerService.clearInlineReplyUriPermissions(entry.getKey());
            } catch (RemoteException ex) {
                ex.rethrowFromSystemServer();
            }
        }
    };

    @Inject
    public RemoteInputUriController(IStatusBarService statusBarService) {
        mStatusBarManagerService = statusBarService;
    }

    /**
     * Attach this controller as a listener to the provided NotificationEntryManager to ensure
     * that RemoteInput URI grants are cleaned up when the notification entry is removed from
     * the shade.
     */
    public void attach(CommonNotifCollection manager) {
        manager.addCollectionListener(mInlineUriListener);
    }

    /**
     * Create a temporary grant which allows the app that submitted the notification access to the
     * specified URI.
     */
    public void grantInlineReplyUriPermission(StatusBarNotification sbn, Uri data) {
        try {
            mStatusBarManagerService.grantInlineReplyUriPermission(
                    sbn.getKey(), data, sbn.getUser(), sbn.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to grant URI permissions:" + e.getMessage(), e);
        }
    }
}
