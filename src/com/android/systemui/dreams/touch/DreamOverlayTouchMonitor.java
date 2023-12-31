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

package com.android.systemui.dreams.touch;

import android.graphics.Region;
import android.view.GestureDetector;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dreams.touch.dagger.InputSessionComponent;
import com.android.systemui.shared.system.InputChannelCompat;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * {@link DreamOverlayTouchMonitor} is responsible for monitoring touches and gestures over the
 * dream overlay and redirecting them to a set of listeners. This monitor is in charge of figuring
 * out when listeners are eligible for receiving touches and filtering the listener pool if
 * touches are consumed.
 */
public class DreamOverlayTouchMonitor {
    // This executor is used to protect {@code mActiveTouchSessions} from being modified
    // concurrently. Any operation that adds or removes values should use this executor.
    private final Executor mExecutor;
    private final Lifecycle mLifecycle;
    private final HashSet<TouchSessionImpl> mActiveTouchSessions = new HashSet<>();
    private final Collection<DreamTouchHandler> mHandlers;
    private final InputChannelCompat.InputEventListener mInputEventListener =
            new InputChannelCompat.InputEventListener() {
                @Override
                public void onInputEvent(InputEvent ev) {
                    // No Active sessions are receiving touches. Create sessions for each listener
                    if (mActiveTouchSessions.isEmpty()) {
                        final HashMap<DreamTouchHandler, DreamTouchHandler.TouchSession> sessionMap =
                                new HashMap<>();

                        for (DreamTouchHandler handler : mHandlers) {
                            final Region initiationRegion = Region.obtain();
                            handler.getTouchInitiationRegion(initiationRegion);

                            if (!initiationRegion.isEmpty()) {
                                // Initiation regions require a motion event to determine pointer
                                // location
                                // within the region.
                                if (!(ev instanceof MotionEvent)) {
                                    continue;
                                }

                                final MotionEvent motionEvent = (MotionEvent) ev;

                                // If the touch event is outside the region, then ignore.
                                if (!initiationRegion.contains(Math.round(motionEvent.getX()),
                                        Math.round(motionEvent.getY()))) {
                                    continue;
                                }
                            }

                            final TouchSessionImpl sessionStack =
                                    new TouchSessionImpl(DreamOverlayTouchMonitor.this, null);
                            mActiveTouchSessions.add(sessionStack);
                            sessionMap.put(handler, sessionStack);
                        }

                        // Informing handlers of new sessions is delayed until we have all
                        // created so the
                        // final session is correct.
                        sessionMap.forEach((dreamTouchHandler, touchSession)
                                -> dreamTouchHandler.onSessionStart(touchSession));
                    }

                    // Find active sessions and invoke on InputEvent.
                    mActiveTouchSessions.stream()
                            .map(touchSessionStack -> touchSessionStack.getEventListeners())
                            .flatMap(Collection::stream)
                            .forEach(inputEventListener -> inputEventListener.onInputEvent(ev));
                }
            };
    private final GestureDetector.OnGestureListener mOnGestureListener =
            new GestureDetector.OnGestureListener() {
                private boolean evaluate(Evaluator evaluator) {
                    final Set<TouchSessionImpl> consumingSessions = new HashSet<>();

                    // When a gesture is consumed, it is assumed that all touches for the current
                    // session
                    // should be directed only to those TouchSessions until those sessions are
                    // popped. All
                    // non-participating sessions are removed from receiving further updates with
                    // {@link DreamOverlayTouchMonitor#isolate}.
                    final boolean eventConsumed = mActiveTouchSessions.stream()
                            .map(touchSession -> {
                                boolean consume = touchSession.getGestureListeners()
                                        .stream()
                                        .map(listener -> evaluator.evaluate(listener))
                                        .anyMatch(consumed -> consumed);

                                if (consume) {
                                    consumingSessions.add(touchSession);
                                }
                                return consume;
                            }).anyMatch(consumed -> consumed);

                    if (eventConsumed) {
                        DreamOverlayTouchMonitor.this.isolate(consumingSessions);
                    }

                    return eventConsumed;
                }

                // This method is called for gesture events that cannot be consumed.
                private void observe(Consumer<GestureDetector.OnGestureListener> consumer) {
                    mActiveTouchSessions.stream()
                            .map(touchSession -> touchSession.getGestureListeners())
                            .flatMap(Collection::stream)
                            .forEach(listener -> consumer.accept(listener));
                }

                @Override
                public boolean onDown(MotionEvent e) {
                    return evaluate(listener -> listener.onDown(e));
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                       float velocityY) {
                    return evaluate(listener -> listener.onFling(e1, e2, velocityX, velocityY));
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    observe(listener -> listener.onLongPress(e));
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                                        float distanceY) {
                    return evaluate(listener -> listener.onScroll(e1, e2, distanceX, distanceY));
                }

                @Override
                public void onShowPress(MotionEvent e) {
                    observe(listener -> listener.onShowPress(e));
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return evaluate(listener -> listener.onSingleTapUp(e));
                }
            };
    private final InputSessionComponent.Factory mInputSessionFactory;
    private InputSession mCurrentInputSession;
    /**
     * This lifecycle observer ensures touch monitoring only occurs while the overlay is "resumed".
     * This concept is mapped over from the equivalent view definition: The {@link LifecycleOwner}
     * will report the dream is not resumed when it is obscured (from the notification shade being
     * expanded for example) or not active (such as when it is destroyed).
     */
    private final LifecycleObserver mLifecycleObserver = new DefaultLifecycleObserver() {
        @Override
        public void onResume(@NonNull LifecycleOwner owner) {
            startMonitoring();
        }

        @Override
        public void onPause(@NonNull LifecycleOwner owner) {
            stopMonitoring();
        }
    };


    /**
     * Designated constructor for {@link DreamOverlayTouchMonitor}
     *
     * @param executor            This executor will be used for maintaining the active listener
     *                            list to avoid
     *                            concurrent modification.
     * @param lifecycle           {@link DreamOverlayTouchMonitor} will listen to this lifecycle
     *                                                            to determine
     *                            whether touch monitoring should be active.
     * @param inputSessionFactory This factory will generate the {@link InputSession} requested by
     *                            the monitor. Each session should be unique and valid when
     *                            returned.
     * @param handlers            This set represents the {@link DreamTouchHandler} instances
     *                            that will
     *                            participate in touch handling.
     */
    @Inject
    public DreamOverlayTouchMonitor(
            @Main Executor executor,
            Lifecycle lifecycle,
            InputSessionComponent.Factory inputSessionFactory,
            Set<DreamTouchHandler> handlers) {
        mHandlers = handlers;
        mInputSessionFactory = inputSessionFactory;
        mExecutor = executor;
        mLifecycle = lifecycle;
    }

    /**
     * Adds a new {@link TouchSessionImpl} to participate in receiving future touches and gestures.
     */
    private ListenableFuture<DreamTouchHandler.TouchSession> push(
            TouchSessionImpl touchSessionImpl) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mExecutor.execute(() -> {
                if (!mActiveTouchSessions.remove(touchSessionImpl)) {
                    completer.set(null);
                    return;
                }

                final TouchSessionImpl touchSession =
                        new TouchSessionImpl(this, touchSessionImpl);
                mActiveTouchSessions.add(touchSession);
                completer.set(touchSession);
            });

            return "DreamOverlayTouchMonitor::push";
        });
    }

    /**
     * Removes a {@link TouchSessionImpl} from receiving further updates.
     */
    private ListenableFuture<DreamTouchHandler.TouchSession> pop(
            TouchSessionImpl touchSessionImpl) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mExecutor.execute(() -> {
                if (mActiveTouchSessions.remove(touchSessionImpl)) {
                    touchSessionImpl.onRemoved();

                    final TouchSessionImpl predecessor = touchSessionImpl.getPredecessor();

                    if (predecessor != null) {
                        mActiveTouchSessions.add(predecessor);
                    }

                    completer.set(predecessor);
                }
            });

            return "DreamOverlayTouchMonitor::pop";
        });
    }

    private int getSessionCount() {
        return mActiveTouchSessions.size();
    }

    /**
     * When invoked, instantiates a new {@link InputSession} to monitor touch events.
     */
    private void startMonitoring() {
        stopMonitoring();
        mCurrentInputSession = mInputSessionFactory.create(
                        "dreamOverlay",
                        mInputEventListener,
                        mOnGestureListener,
                        true)
                .getInputSession();
    }

    /**
     * Destroys any active {@link InputSession}.
     */
    private void stopMonitoring() {
        if (mCurrentInputSession == null) {
            return;
        }

        mCurrentInputSession.dispose();
        mCurrentInputSession = null;
    }

    /**
     * Initializes the monitor. should only be called once after creation.
     */
    public void init() {
        mLifecycle.addObserver(mLifecycleObserver);
    }

    private void isolate(Set<TouchSessionImpl> sessions) {
        Collection<TouchSessionImpl> removedSessions = mActiveTouchSessions.stream()
                .filter(touchSession -> !sessions.contains(touchSession))
                .collect(Collectors.toCollection(HashSet::new));

        removedSessions.forEach(touchSession -> touchSession.onRemoved());

        mActiveTouchSessions.removeAll(removedSessions);
    }

    /**
     * The {@link Evaluator} interface allows for callers to inspect a listener from the
     * {@link android.view.GestureDetector.OnGestureListener} set. This helps reduce duplicated
     * iteration loops over this set.
     */
    private interface Evaluator {
        boolean evaluate(GestureDetector.OnGestureListener listener);
    }

    /**
     * {@link TouchSessionImpl} implements {@link DreamTouchHandler.TouchSession} for
     * {@link DreamOverlayTouchMonitor}. It enables the monitor to access the associated listeners
     * and provides the associated client with access to the monitor.
     */
    private static class TouchSessionImpl implements DreamTouchHandler.TouchSession {
        private final HashSet<InputChannelCompat.InputEventListener> mEventListeners =
                new HashSet<>();
        private final HashSet<GestureDetector.OnGestureListener> mGestureListeners =
                new HashSet<>();
        private final HashSet<Callback> mCallbacks = new HashSet<>();

        private final TouchSessionImpl mPredecessor;
        private final DreamOverlayTouchMonitor mTouchMonitor;

        TouchSessionImpl(DreamOverlayTouchMonitor touchMonitor, TouchSessionImpl predecessor) {
            mPredecessor = predecessor;
            mTouchMonitor = touchMonitor;
        }

        @Override
        public void registerCallback(Callback callback) {
            mCallbacks.add(callback);
        }

        @Override
        public boolean registerInputListener(
                InputChannelCompat.InputEventListener inputEventListener) {
            return mEventListeners.add(inputEventListener);
        }

        @Override
        public boolean registerGestureListener(GestureDetector.OnGestureListener gestureListener) {
            return mGestureListeners.add(gestureListener);
        }

        @Override
        public ListenableFuture<DreamTouchHandler.TouchSession> push() {
            return mTouchMonitor.push(this);
        }

        @Override
        public ListenableFuture<DreamTouchHandler.TouchSession> pop() {
            return mTouchMonitor.pop(this);
        }

        @Override
        public int getActiveSessionCount() {
            return mTouchMonitor.getSessionCount();
        }

        /**
         * Returns the active listeners to receive touch events.
         */
        public Collection<InputChannelCompat.InputEventListener> getEventListeners() {
            return mEventListeners;
        }

        /**
         * Returns the active listeners to receive gesture events.
         */
        public Collection<GestureDetector.OnGestureListener> getGestureListeners() {
            return mGestureListeners;
        }

        /**
         * Returns the {@link TouchSessionImpl} that preceded this current session. This will
         * become the new active session when this session is popped.
         */
        private TouchSessionImpl getPredecessor() {
            return mPredecessor;
        }

        /**
         * Called by the monitor when this session is removed.
         */
        private void onRemoved() {
            mCallbacks.forEach(callback -> callback.onRemoved());
        }
    }
}
