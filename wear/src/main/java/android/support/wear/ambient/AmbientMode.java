/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.support.wear.ambient;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.wearable.compat.WearableActivityController;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Use this as a headless Fragment to add ambient support to an Activity on Wearable devices.
 * <p>
 * The application that uses this should add the {@link android.Manifest.permission#WAKE_LOCK}
 * permission to its manifest.
 * <p>
 * The primary entry  point for this code is the {@link #attachAmbientSupport(Activity)} method.
 * It should be called with an {@link Activity} as an argument and that {@link Activity} will then
 * be able to receive ambient lifecycle events through an {@link AmbientCallback}. The
 * {@link Activity} will also receive a {@link AmbientController} object from the attachment which
 * can be used to query the current status of the ambient mode, or toggle simple settings.
 * An example of how to attach {@link AmbientMode} to your {@link Activity} and use
 * the {@link AmbientController} can be found below:
 * <p>
 * <pre class="prettyprint">{@code
 *     AmbientMode.AmbientController controller = AmbientMode.attachAmbientSupport(this);
 *     controller.setAutoResumeEnabled(true);
 * }</pre>
 */
public final class AmbientMode extends Fragment {

    /**
     * Property in bundle passed to {@code AmbientCallback#onEnterAmbient(Bundle)} to indicate
     * whether burn-in protection is required. When this property is set to true, views must be
     * shifted around periodically in ambient mode. To ensure that content isn't shifted off
     * the screen, avoid placing content within 10 pixels of the edge of the screen. Activities
     * should also avoid solid white areas to prevent pixel burn-in. Both of these requirements
     * only apply in ambient mode, and only when this property is set to true.
     */
    public static final String EXTRA_BURN_IN_PROTECTION =
            WearableActivityController.EXTRA_BURN_IN_PROTECTION;

    /**
     * Property in bundle passed to {@code AmbientCallback#onEnterAmbient(Bundle)} to indicate
     * whether the device has low-bit ambient mode. When this property is set to true, the screen
     * supports fewer bits for each color in ambient mode. In this case, activities should disable
     * anti-aliasing in ambient mode.
     */
    public static final String EXTRA_LOWBIT_AMBIENT =
            WearableActivityController.EXTRA_LOWBIT_AMBIENT;

    /**
     * Fragment tag used by default when adding {@link AmbientMode} to add ambient support to an
     * {@link Activity}.
     */
    public static final String FRAGMENT_TAG = "android.support.wearable.ambient.AmbientMode";

    /**
     * Interface for any {@link Activity} that wishes to implement Ambient Mode. Use the
     * {@link #getAmbientCallback()} method to return and {@link AmbientCallback} which can be used
     * to bind the {@link AmbientMode} to the instantiation of this interface.
     * <p>
     * <pre class="prettyprint">{@code
     * return new AmbientMode.AmbientCallback() {
     *     public void onEnterAmbient(Bundle ambientDetails) {...}
     *     public void onExitAmbient(Bundle ambientDetails) {...}
     *  }
     * }</pre>
     */
    public interface AmbientCallbackProvider {
        /**
         * @return the {@link AmbientCallback} to be used by this class to communicate with the
         * entity interested in ambient events.
         */
        AmbientCallback getAmbientCallback();
    }

    /**
     * Callback to receive ambient mode state changes. It must be used by all users of AmbientMode.
     */
    public abstract static class AmbientCallback {
        /**
         * Called when an activity is entering ambient mode. This event is sent while an activity is
         * running (after onResume, before onPause). All drawing should complete by the conclusion
         * of this method. Note that {@code invalidate()} calls will be executed before resuming
         * lower-power mode.
         * <p>
         * <p><em>Derived classes must call through to the super class's implementation of this
         * method. If they do not, an exception will be thrown.</em>
         *
         * @param ambientDetails bundle containing information about the display being used.
         *                      It includes information about low-bit color and burn-in protection.
         */
        public void onEnterAmbient(Bundle ambientDetails) {}

        /**
         * Called when the system is updating the display for ambient mode. Activities may use this
         * opportunity to update or invalidate views.
         */
        public void onUpdateAmbient() {};

        /**
         * Called when an activity should exit ambient mode. This event is sent while an activity is
         * running (after onResume, before onPause).
         * <p>
         * <p><em>Derived classes must call through to the super class's implementation of this
         * method. If they do not, an exception will be thrown.</em>
         */
        public void onExitAmbient() {};
    }

    private final AmbientDelegate.AmbientCallback mCallback =
            new AmbientDelegate.AmbientCallback() {
                @Override
                public void onEnterAmbient(Bundle ambientDetails) {
                    mSuppliedCallback.onEnterAmbient(ambientDetails);
                }

                @Override
                public void onExitAmbient() {
                    mSuppliedCallback.onExitAmbient();
                }

                @Override
                public void onUpdateAmbient() {
                    mSuppliedCallback.onUpdateAmbient();
                }
            };
    private AmbientDelegate mDelegate;
    private AmbientCallback mSuppliedCallback;
    private AmbientController mController;

    /**
     * Constructor
     */
    public AmbientMode() {
        mController = new AmbientController();
    }

    @Override
    @CallSuper
    public void onAttach(Context context) {
        super.onAttach(context);
        mDelegate = new AmbientDelegate(getActivity(), new WearableControllerProvider(), mCallback);

        if (context instanceof AmbientCallbackProvider) {
            mSuppliedCallback = ((AmbientCallbackProvider) context).getAmbientCallback();
        } else {
            throw new IllegalArgumentException(
                    "fragment should attach to an activity that implements AmbientCallback");
        }
    }

    @Override
    @CallSuper
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDelegate.onCreate();
        mDelegate.setAmbientEnabled();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        mDelegate.onResume();
    }

    @Override
    @CallSuper
    public void onPause() {
        mDelegate.onPause();
        super.onPause();
    }

    @Override
    @CallSuper
    public void onStop() {
        mDelegate.onStop();
        super.onStop();
    }

    @Override
    @CallSuper
    public void onDestroy() {
        mDelegate.onDestroy();
        super.onDestroy();
    }

    @Override
    @CallSuper
    public void onDetach() {
        mDelegate = null;
        super.onDetach();
    }

    /**
     * Attach ambient support to the given activity.
     *
     * @param activity the activity to attach ambient support to. This activity has to also
     *                implement {@link AmbientCallbackProvider}
     * @return the associated {@link AmbientController} which can be used to query the state of
     * ambient mode and toggle simple settings related to it.
     */
    public static <T extends Activity & AmbientCallbackProvider> AmbientController
            attachAmbientSupport(T activity) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        AmbientMode ambientFragment = (AmbientMode) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (ambientFragment == null) {
            AmbientMode fragment = new AmbientMode();
            fragmentManager
                    .beginTransaction()
                    .add(fragment, FRAGMENT_TAG)
                    .commit();
            ambientFragment = fragment;
        }
        return ambientFragment.mController;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mDelegate != null) {
            mDelegate.dump(prefix, fd, writer, args);
        }
    }

    @VisibleForTesting
    void setAmbientDelegate(AmbientDelegate delegate) {
        mDelegate = delegate;
    }

    /**
     * A class for interacting with the ambient mode on a wearable device. This class can be used to
     * query the current state of ambient mode and to enable or disable certain settings.
     * An instance of this class is returned to the user when they attach their {@link Activity}
     * to {@link AmbientMode}.
     */
    public final class AmbientController {
        private static final String TAG = "AmbientController";

        // Do not initialize outside of this class.
        AmbientController() {}

        /**
         * Sets whether this activity's task should be moved to the front when the system exits
         * ambient mode. If true, the activity's task may be moved to the front if it was the last
         * activity to be running when ambient started, depending on how much time the system spent
         * in ambient mode.
         */
        public void setAutoResumeEnabled(boolean enabled) {
            if (mDelegate != null) {
                mDelegate.setAutoResumeEnabled(enabled);
            } else {
                Log.w(TAG, "The fragment is not yet fully initialized, this call is a no-op");
            }
        }

        /**
         * @return {@code true} if the activity is currently in ambient.
         */
        public boolean isAmbient() {
            return mDelegate == null ? false : mDelegate.isAmbient();
        }
    }
}
