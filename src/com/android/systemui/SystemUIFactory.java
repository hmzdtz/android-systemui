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

package com.android.systemui;

import android.app.ActivityThread;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.dagger.DaggerGlobalRootComponent;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.WMComponent;
import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider;
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider;
import com.android.wm.shell.dagger.WMShellConcurrencyModule;
import com.android.wm.shell.transition.ShellTransitions;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import javax.inject.Provider;

/**
 * Class factory to provide customizable SystemUI components.
 */
public class SystemUIFactory {
    private static final String TAG = "SystemUIFactory";

    static SystemUIFactory mFactory;
    private GlobalRootComponent mRootComponent;
    private WMComponent mWMComponent;
    private SysUIComponent mSysUIComponent;
    private boolean mInitializeComponents;

    public SystemUIFactory() {
    }

    public static <T extends SystemUIFactory> T getInstance() {
        return (T) mFactory;
    }

    public static void createFromConfig(Context context) {
        createFromConfig(context, false);
    }

    @VisibleForTesting
    public static void createFromConfig(Context context, boolean fromTest) {
        if (mFactory != null) {
            return;
        }

        final String clsName = context.getString(R.string.config_systemUIFactoryComponent);
        if (clsName == null || clsName.length() == 0) {
            throw new RuntimeException("No SystemUIFactory component configured");
        }

        try {
            Class<?> cls = null;
            cls = context.getClassLoader().loadClass(clsName);
            mFactory = (SystemUIFactory) cls.newInstance();
            mFactory.init(context, fromTest);
        } catch (Throwable t) {
            Log.w(TAG, "Error creating SystemUIFactory component: " + clsName, t);
            throw new RuntimeException(t);
        }
    }

    @VisibleForTesting
    static void cleanup() {
        mFactory = null;
    }

    @VisibleForTesting
    public void init(Context context, boolean fromTest)
            throws ExecutionException, InterruptedException {
        // Only initialize components for the main system ui process running as the primary user
        mInitializeComponents = !fromTest
                && android.os.Process.myUserHandle().isSystem()
                && ActivityThread.currentProcessName().equals(ActivityThread.currentPackageName());
        mRootComponent = buildGlobalRootComponent(context);

        // Stand up WMComponent
        setupWmComponent(context);
        if (mInitializeComponents) {
            // Only initialize when not starting from tests since this currently initializes some
            // components that shouldn't be run in the test environment
            mWMComponent.init();
        }

        // And finally, retrieve whatever SysUI needs from WMShell and build SysUI.
        SysUIComponent.Builder builder = mRootComponent.getSysUIComponent();
        if (mInitializeComponents) {
            // Only initialize when not starting from tests since this currently initializes some
            // components that shouldn't be run in the test environment
            builder = prepareSysUIComponentBuilder(builder, mWMComponent)
                    .setPip(mWMComponent.getPip())
                    .setLegacySplitScreen(mWMComponent.getLegacySplitScreen())
                    .setSplitScreen(mWMComponent.getSplitScreen())
                    .setOneHanded(mWMComponent.getOneHanded())
                    .setBubbles(mWMComponent.getBubbles())
                    .setHideDisplayCutout(mWMComponent.getHideDisplayCutout())
                    .setShellCommandHandler(mWMComponent.getShellCommandHandler())
                    .setAppPairs(mWMComponent.getAppPairs())
                    .setTaskViewFactory(mWMComponent.getTaskViewFactory())
                    .setTransitions(mWMComponent.getTransitions())
                    .setStartingSurface(mWMComponent.getStartingSurface())
                    .setDisplayAreaHelper(mWMComponent.getDisplayAreaHelper())
                    .setTaskSurfaceHelper(mWMComponent.getTaskSurfaceHelper())
                    .setRecentTasks(mWMComponent.getRecentTasks())
                    .setCompatUI(mWMComponent.getCompatUI())
                    .setDragAndDrop(mWMComponent.getDragAndDrop())
                    .setBackAnimation(mWMComponent.getBackAnimation());
        } else {
            // TODO: Call on prepareSysUIComponentBuilder but not with real components. Other option
            // is separating this logic into newly creating SystemUITestsFactory.
            builder = prepareSysUIComponentBuilder(builder, mWMComponent)
                    .setPip(Optional.ofNullable(null))
                    .setLegacySplitScreen(Optional.ofNullable(null))
                    .setSplitScreen(Optional.ofNullable(null))
                    .setOneHanded(Optional.ofNullable(null))
                    .setBubbles(Optional.ofNullable(null))
                    .setHideDisplayCutout(Optional.ofNullable(null))
                    .setShellCommandHandler(Optional.ofNullable(null))
                    .setAppPairs(Optional.ofNullable(null))
                    .setTaskViewFactory(Optional.ofNullable(null))
                    .setTransitions(new ShellTransitions() {
                    })
                    .setDisplayAreaHelper(Optional.ofNullable(null))
                    .setStartingSurface(Optional.ofNullable(null))
                    .setTaskSurfaceHelper(Optional.ofNullable(null))
                    .setRecentTasks(Optional.ofNullable(null))
                    .setCompatUI(Optional.ofNullable(null))
                    .setDragAndDrop(Optional.ofNullable(null))
                    .setBackAnimation(Optional.ofNullable(null));
        }
        mSysUIComponent = builder.build();
        if (mInitializeComponents) {
            mSysUIComponent.init();
        }

        // Every other part of our codebase currently relies on Dependency, so we
        // really need to ensure the Dependency gets initialized early on.
        Dependency dependency = mSysUIComponent.createDependency();
        dependency.start();
    }

    /**
     * Sets up {@link #mWMComponent}. On devices where the Shell runs on its own main thread,
     * this will pre-create the thread to ensure that the components are constructed on the
     * same thread, to reduce the likelihood of side effects from running the constructors on
     * a different thread than the rest of the class logic.
     */
    private void setupWmComponent(Context context) {
        WMComponent.Builder wmBuilder = mRootComponent.getWMComponentBuilder();
        if (!mInitializeComponents || !WMShellConcurrencyModule.enableShellMainThread(context)) {
            // If running under tests or shell thread is not enabled, we don't need anything special
            mWMComponent = wmBuilder.build();
            return;
        }

        // If the shell main thread is enabled, initialize the component on that thread
        HandlerThread shellThread = WMShellConcurrencyModule.createShellMainThread();
        shellThread.start();

        // Use an async handler since we don't care about synchronization
        Handler shellHandler = Handler.createAsync(shellThread.getLooper());
        boolean built = shellHandler.runWithScissors(() -> {
            wmBuilder.setShellMainThread(shellThread);
            mWMComponent = wmBuilder.build();
        }, 5000);
        if (!built) {
            Log.w(TAG, "Failed to initialize WMComponent");
            throw new RuntimeException();
        }
    }

    /**
     * Prepares the SysUIComponent builder before it is built.
     *
     * @param sysUIBuilder the builder provided by the root component's getSysUIComponent() method
     * @param wm           the built WMComponent from the root component's getWMComponent() method
     */
    protected SysUIComponent.Builder prepareSysUIComponentBuilder(
            SysUIComponent.Builder sysUIBuilder, WMComponent wm) {
        return sysUIBuilder;
    }

    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponent.builder()
                .context(context)
                .build();
    }

    protected boolean shouldInitializeComponents() {
        return mInitializeComponents;
    }

    public GlobalRootComponent getRootComponent() {
        return mRootComponent;
    }

    public WMComponent getWMComponent() {
        return mWMComponent;
    }

    public SysUIComponent getSysUIComponent() {
        return mSysUIComponent;
    }

    /**
     * Returns the list of {@link CoreStartable} components that should be started at startup.
     */
    public Map<Class<?>, Provider<CoreStartable>> getStartableComponents() {
        return mSysUIComponent.getStartables();
    }

    /**
     * Returns the list of additional system UI components that should be started.
     */
    public String getVendorComponent(Resources resources) {
        return resources.getString(R.string.config_systemUIVendorServiceComponent);
    }

    /**
     * Returns the list of {@link CoreStartable} components that should be started per user.
     */
    public Map<Class<?>, Provider<CoreStartable>> getStartableComponentsPerUser() {
        return mSysUIComponent.getPerUserStartables();
    }

    /**
     * Creates an instance of ScreenshotNotificationSmartActionsProvider.
     * This method is overridden in vendor specific implementation of Sys UI.
     */
    public ScreenshotNotificationSmartActionsProvider
    createScreenshotNotificationSmartActionsProvider(
            Context context, Executor executor, Handler uiHandler) {
        return new ScreenshotNotificationSmartActionsProvider();
    }

    /**
     * Creates an instance of BackGestureTfClassifierProvider.
     * This method is overridden in vendor specific implementation of Sys UI.
     */
    public BackGestureTfClassifierProvider createBackGestureTfClassifierProvider(
            AssetManager am, String modelName) {
        return new BackGestureTfClassifierProvider();
    }
}
