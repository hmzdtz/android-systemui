/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.row.wrapper;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.widget.MessagingLayout;
import com.android.internal.widget.MessagingLinearLayout;
import com.android.systemui.R;
import com.android.systemui.statusbar.TransformableView;
import com.android.systemui.statusbar.ViewTransformationHelper;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.notification.TransformState;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.HybridNotificationView;

/**
 * Wraps a notification containing a messaging template
 */
public class NotificationMessagingTemplateViewWrapper extends NotificationTemplateViewWrapper {

    private final int mMinHeightWithActions;
    private final View mTitle;
    private final View mTitleInHeader;
    private final MessagingLayout mMessagingLayout;
    private MessagingLinearLayout mMessagingLinearLayout;
    private ViewGroup mImageMessageContainer;

    protected NotificationMessagingTemplateViewWrapper(Context ctx, View view,
                                                       ExpandableNotificationRow row) {
        super(ctx, view, row);
        mTitle = mView.findViewById(com.android.internal.R.id.title);
        mTitleInHeader = mView.findViewById(com.android.internal.R.id.header_text_secondary);
        mMessagingLayout = (MessagingLayout) view;
        mMinHeightWithActions = NotificationUtils.getFontScaledHeight(ctx,
                R.dimen.notification_messaging_actions_min_height);
    }

    static void setCustomImageMessageTransform(
            ViewTransformationHelper transformationHelper, ViewGroup imageMessageContainer) {
        if (imageMessageContainer != null) {
            // Let's ignore the image message container since that is transforming as part of the
            // messages already.  This is also required to prevent a clipping artifact caused by the
            // alpha layering triggering hardware rendering mode that in turn results in more
            // aggressive clipping than we want.
            transformationHelper.setCustomTransformation(
                    new ViewTransformationHelper.CustomTransformation() {
                        @Override
                        public boolean transformTo(
                                TransformState ownState,
                                TransformableView otherView,
                                float transformationAmount) {
                            if (otherView instanceof HybridNotificationView) {
                                return false;
                            }
                            // we're hidden by default by the transformState
                            ownState.ensureVisible();
                            // Let's do nothing otherwise, this is already handled by the messages
                            return true;
                        }

                        @Override
                        public boolean transformFrom(
                                TransformState ownState,
                                TransformableView otherView,
                                float transformationAmount) {
                            return transformTo(ownState, otherView, transformationAmount);
                        }
                    }, imageMessageContainer.getId());
        }
    }

    private void resolveViews() {
        mMessagingLinearLayout = mMessagingLayout.getMessagingLinearLayout();
        mImageMessageContainer = mMessagingLayout.getImageMessageContainer();
    }

    @Override
    public void onContentUpdated(ExpandableNotificationRow row) {
        // Reinspect the notification. Before the super call, because the super call also updates
        // the transformation types and we need to have our values set by then.
        resolveViews();
        super.onContentUpdated(row);
    }

    @Override
    protected void updateTransformedTypes() {
        // This also clears the existing types
        super.updateTransformedTypes();
        if (mMessagingLinearLayout != null) {
            mTransformationHelper.addTransformedView(mMessagingLinearLayout);
        }
        // The title is not as important for messaging, and stays in the header when expanded,
        // but this ensures it animates cleanly between the two positions
        if (mTitle == null && mTitleInHeader != null) {
            mTransformationHelper.addTransformedView(TransformableView.TRANSFORMING_VIEW_TITLE,
                    mTitleInHeader);
        }
        setCustomImageMessageTransform(mTransformationHelper, mImageMessageContainer);
    }

    @Override
    public void setRemoteInputVisible(boolean visible) {
        mMessagingLayout.showHistoricMessages(visible);
    }

    @Override
    public int getMinLayoutHeight() {
        if (mActionsContainer != null && mActionsContainer.getVisibility() != View.GONE) {
            return mMinHeightWithActions;
        }
        return super.getMinLayoutHeight();
    }
}
