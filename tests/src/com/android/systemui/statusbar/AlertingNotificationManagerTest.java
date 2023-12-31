/*
 * Copyright (C) 2018 The Android Open Source Project
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


package com.android.systemui.statusbar;

import static com.android.systemui.statusbar.notification.row.NotificationRowContentBinder.FLAG_CONTENT_VIEW_CONTRACTED;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.policy.HeadsUpManagerLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AlertingNotificationManagerTest extends SysuiTestCase {
    protected static final int TEST_MINIMUM_DISPLAY_TIME = 200;
    protected static final int TEST_AUTO_DISMISS_TIME = 500;
    protected static final int TEST_TIMEOUT_TIME = 15000;
    private static final String TEST_PACKAGE_NAME = "test";
    private static final int TEST_UID = 0;
    // Number of notifications to use in tests requiring multiple notifications
    private static final int TEST_NUM_NOTIFICATIONS = 4;
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    protected NotificationEntry mEntry;
    protected Handler mTestHandler;
    protected boolean mTimedOut = false;
    protected final Runnable TEST_TIMEOUT_RUNNABLE = () -> mTimedOut = true;
    @Mock
    protected ExpandableNotificationRow mRow;
    private AlertingNotificationManager mAlertingNotificationManager;
    private StatusBarNotification mSbn;

    protected AlertingNotificationManager createAlertingNotificationManager() {
        return new TestableAlertingNotificationManager();
    }

    protected StatusBarNotification createNewSbn(int id, Notification.Builder n) {
        return new StatusBarNotification(
                TEST_PACKAGE_NAME /* pkg */,
                TEST_PACKAGE_NAME,
                id,
                null /* tag */,
                TEST_UID,
                0 /* initialPid */,
                n.build(),
                new UserHandle(ActivityManager.getCurrentUser()),
                null /* overrideGroupKey */,
                0 /* postTime */);
    }

    protected StatusBarNotification createNewNotification(int id) {
        Notification.Builder n = new Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setContentTitle("Title")
                .setContentText("Text");
        return createNewSbn(id, n);
    }

    @Before
    public void setUp() {
        mTestHandler = Handler.createAsync(Looper.myLooper());
        mSbn = createNewNotification(0 /* id */);
        mEntry = new NotificationEntryBuilder()
                .setSbn(mSbn)
                .build();
        mEntry.setRow(mRow);

        mAlertingNotificationManager = createAlertingNotificationManager();
    }

    @After
    public void tearDown() {
        mTestHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void testShowNotification_addsEntry() {
        mAlertingNotificationManager.showNotification(mEntry);

        assertTrue(mAlertingNotificationManager.isAlerting(mEntry.getKey()));
        assertTrue(mAlertingNotificationManager.hasNotifications());
        assertEquals(mEntry, mAlertingNotificationManager.getEntry(mEntry.getKey()));
    }

    @Test
    public void testShowNotification_autoDismisses() {
        mAlertingNotificationManager.showNotification(mEntry);
        mTestHandler.postDelayed(TEST_TIMEOUT_RUNNABLE, TEST_TIMEOUT_TIME);

        // Wait for remove runnable and then process it immediately
        TestableLooper.get(this).processMessages(1);

        assertFalse("Test timed out", mTimedOut);
        assertFalse(mAlertingNotificationManager.isAlerting(mEntry.getKey()));
    }

    @Test
    public void testRemoveNotification_removeDeferred() {
        mAlertingNotificationManager.showNotification(mEntry);

        // Try to remove but defer, since the notification has not been shown long enough.
        mAlertingNotificationManager.removeNotification(
                mEntry.getKey(), false /* releaseImmediately */);

        assertTrue(mAlertingNotificationManager.isAlerting(mEntry.getKey()));
    }

    @Test
    public void testRemoveNotification_forceRemove() {
        mAlertingNotificationManager.showNotification(mEntry);

        // Remove forcibly with releaseImmediately = true.
        mAlertingNotificationManager.removeNotification(
                mEntry.getKey(), true /* releaseImmediately */);

        assertFalse(mAlertingNotificationManager.isAlerting(mEntry.getKey()));
    }

    @Test
    public void testReleaseAllImmediately() {
        for (int i = 0; i < TEST_NUM_NOTIFICATIONS; i++) {
            StatusBarNotification sbn = createNewNotification(i);
            NotificationEntry entry = new NotificationEntryBuilder()
                    .setSbn(sbn)
                    .build();
            entry.setRow(mRow);
            mAlertingNotificationManager.showNotification(entry);
        }

        mAlertingNotificationManager.releaseAllImmediately();

        assertEquals(0, mAlertingNotificationManager.getAllEntries().count());
    }

    @Test
    public void testCanRemoveImmediately_notShownLongEnough() {
        mAlertingNotificationManager.showNotification(mEntry);

        // The entry has just been added so we should not remove immediately.
        assertFalse(mAlertingNotificationManager.canRemoveImmediately(mEntry.getKey()));
    }

    @Test
    public void testShouldExtendLifetime() {
        mAlertingNotificationManager.showNotification(mEntry);

        // While the entry is alerting, it should not be removable.
        assertTrue(mAlertingNotificationManager.shouldExtendLifetime(mEntry));
    }

    @Test
    public void testSetShouldManageLifetime_setShouldManage() {
        mAlertingNotificationManager.showNotification(mEntry);

        mAlertingNotificationManager.setShouldManageLifetime(mEntry, true /* shouldManage */);

        assertTrue(mAlertingNotificationManager.mExtendedLifetimeAlertEntries.contains(mEntry));
    }

    @Test
    public void testSetShouldManageLifetime_setShouldManageCallsRemoval() {
        mAlertingNotificationManager.showNotification(mEntry);
        mAlertingNotificationManager.setShouldManageLifetime(mEntry, true /* shouldManage */);
        if (mAlertingNotificationManager instanceof TestableAlertingNotificationManager) {
            TestableAlertingNotificationManager testableManager =
                    (TestableAlertingNotificationManager) mAlertingNotificationManager;
            verify(testableManager.mLastCreatedEntry).removeAsSoonAsPossible();
        }
    }

    @Test
    public void testSetShouldManageLifetime_setShouldNotManage() {
        mAlertingNotificationManager.mExtendedLifetimeAlertEntries.add(mEntry);

        mAlertingNotificationManager.setShouldManageLifetime(mEntry, false /* shouldManage */);

        assertFalse(mAlertingNotificationManager.mExtendedLifetimeAlertEntries.contains(mEntry));
    }

    private final class TestableAlertingNotificationManager extends AlertingNotificationManager {
        private AlertEntry mLastCreatedEntry;

        private TestableAlertingNotificationManager() {
            super(mock(HeadsUpManagerLogger.class));
            mMinimumDisplayTime = TEST_MINIMUM_DISPLAY_TIME;
            mAutoDismissNotificationDecay = TEST_AUTO_DISMISS_TIME;
            mHandler.removeCallbacksAndMessages(null);
            mHandler = mTestHandler;
        }

        @Override
        protected void onAlertEntryAdded(AlertEntry alertEntry) {
        }

        @Override
        protected void onAlertEntryRemoved(AlertEntry alertEntry) {
        }

        @Override
        protected AlertEntry createAlertEntry() {
            mLastCreatedEntry = spy(super.createAlertEntry());
            return mLastCreatedEntry;
        }

        @Override
        public int getContentFlag() {
            return FLAG_CONTENT_VIEW_CONTRACTED;
        }
    }
}
