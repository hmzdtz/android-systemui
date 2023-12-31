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

package com.android.systemui.user;

import android.app.Activity;

import com.android.settingslib.users.EditUserInfoController;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Dagger module for User related classes.
 */
@Module
public abstract class UserModule {

    private static final String FILE_PROVIDER_AUTHORITY = "com.android.systemui.fileprovider";

    @Provides
    public static EditUserInfoController provideEditUserInfoController() {
        return new EditUserInfoController(FILE_PROVIDER_AUTHORITY);
    }

    /**
     * Provides UserSwitcherActivity
     */
    @Binds
    @IntoMap
    @ClassKey(UserSwitcherActivity.class)
    public abstract Activity provideUserSwitcherActivity(UserSwitcherActivity activity);
}
