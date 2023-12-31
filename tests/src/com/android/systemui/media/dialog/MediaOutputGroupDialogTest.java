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

package com.android.systemui.media.dialog;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.os.PowerExemptionManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.animation.DialogLaunchAnimator;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.media.nearby.NearbyMediaDevicesManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.notification.NotificationEntryManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class MediaOutputGroupDialogTest extends SysuiTestCase {

    private static final String TEST_PACKAGE = "test_package";
    private final DialogLaunchAnimator mDialogLaunchAnimator = mock(DialogLaunchAnimator.class);
    private final AudioManager mAudioManager = mock(AudioManager.class);
    // Mock
    private MediaSessionManager mMediaSessionManager = mock(MediaSessionManager.class);
    private LocalBluetoothManager mLocalBluetoothManager = mock(LocalBluetoothManager.class);
    private ActivityStarter mStarter = mock(ActivityStarter.class);
    private BroadcastSender mBroadcastSender = mock(BroadcastSender.class);
    private LocalMediaManager mLocalMediaManager = mock(LocalMediaManager.class);
    private MediaDevice mMediaDevice = mock(MediaDevice.class);
    private MediaDevice mMediaDevice1 = mock(MediaDevice.class);
    private NotificationEntryManager mNotificationEntryManager =
            mock(NotificationEntryManager.class);
    private NearbyMediaDevicesManager mNearbyMediaDevicesManager = mock(
            NearbyMediaDevicesManager.class);
    private PowerExemptionManager mPowerExemptionManager = mock(PowerExemptionManager.class);

    private MediaOutputGroupDialog mMediaOutputGroupDialog;
    private MediaOutputController mMediaOutputController;
    private List<MediaDevice> mMediaDevices = new ArrayList<>();

    @Before
    public void setUp() {
        mMediaOutputController = new MediaOutputController(mContext, TEST_PACKAGE,
                mMediaSessionManager, mLocalBluetoothManager, mStarter,
                mNotificationEntryManager, mDialogLaunchAnimator,
                Optional.of(mNearbyMediaDevicesManager), mAudioManager, mPowerExemptionManager);
        mMediaOutputController.mLocalMediaManager = mLocalMediaManager;
        mMediaOutputGroupDialog = new MediaOutputGroupDialog(mContext, false, mBroadcastSender,
                mMediaOutputController);
        mMediaOutputGroupDialog.show();
        when(mLocalMediaManager.getSelectedMediaDevice()).thenReturn(mMediaDevices);
    }

    @After
    public void tearDown() {
        mMediaOutputGroupDialog.dismissDialog();
    }

    @Test
    public void getStopButtonVisibility_returnVisible() {
        assertThat(mMediaOutputGroupDialog.getStopButtonVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void getHeaderSubtitle_singleDevice_verifyTitle() {
        mMediaDevices.add(mMediaDevice);

        assertThat(mMediaOutputGroupDialog.getHeaderSubtitle()).isEqualTo(
                mContext.getText(R.string.media_output_dialog_single_device));
    }

    @Test
    public void getHeaderSubtitle_multipleDevices_verifyTitle() {
        mMediaDevices.add(mMediaDevice);
        mMediaDevices.add(mMediaDevice1);

        assertThat(mMediaOutputGroupDialog.getHeaderSubtitle()).isEqualTo(mContext.getString(
                R.string.media_output_dialog_multiple_devices, mMediaDevices.size()));
    }

}
