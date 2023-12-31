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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class ComplicationHostViewControllerTest extends SysuiTestCase {
    @Complication.Category
    static final int COMPLICATION_CATEGORY = Complication.CATEGORY_SYSTEM;
    @Mock
    ConstraintLayout mComplicationHostView;
    @Mock
    LifecycleOwner mLifecycleOwner;
    @Mock
    LiveData<Collection<ComplicationViewModel>> mComplicationViewModelLiveData;
    @Mock
    ComplicationCollectionViewModel mViewModel;
    @Mock
    ComplicationViewModel mComplicationViewModel;
    @Mock
    ComplicationLayoutEngine mLayoutEngine;
    @Mock
    ComplicationId mComplicationId;
    @Mock
    Complication mComplication;
    @Mock
    Complication.ViewHolder mViewHolder;
    @Mock
    View mComplicationView;
    @Mock
    ComplicationLayoutParams mComplicationLayoutParams;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mViewModel.getComplications()).thenReturn(mComplicationViewModelLiveData);
        when(mComplicationViewModel.getId()).thenReturn(mComplicationId);
        when(mComplicationViewModel.getComplication()).thenReturn(mComplication);
        when(mComplication.createView(eq(mComplicationViewModel))).thenReturn(mViewHolder);
        when(mViewHolder.getView()).thenReturn(mComplicationView);
        when(mViewHolder.getCategory()).thenReturn(COMPLICATION_CATEGORY);
        when(mViewHolder.getLayoutParams()).thenReturn(mComplicationLayoutParams);
        when(mComplicationView.getParent()).thenReturn(mComplicationHostView);
    }

    /**
     * Ensures the lifecycle of complications is properly handled.
     */
    @Test
    public void testViewModelObservation() {
        final ArgumentCaptor<Observer<Collection<ComplicationViewModel>>> observerArgumentCaptor =
                ArgumentCaptor.forClass(Observer.class);
        final ComplicationHostViewController controller = new ComplicationHostViewController(
                mComplicationHostView,
                mLayoutEngine,
                mLifecycleOwner,
                mViewModel);

        controller.init();

        verify(mComplicationViewModelLiveData).observe(eq(mLifecycleOwner),
                observerArgumentCaptor.capture());

        final Observer<Collection<ComplicationViewModel>> observer =
                observerArgumentCaptor.getValue();

        // Add complication and ensure it is added to the view.
        final HashSet<ComplicationViewModel> complications = new HashSet<>(
                Arrays.asList(mComplicationViewModel));
        observer.onChanged(complications);

        verify(mLayoutEngine).addComplication(eq(mComplicationId), eq(mComplicationView),
                eq(mComplicationLayoutParams), eq(COMPLICATION_CATEGORY));

        // Remove complication and ensure it is removed from the view by id.
        observer.onChanged(new HashSet<>());

        verify(mLayoutEngine).removeComplication(eq(mComplicationId));
    }
}
