/*
* Copyright (C) 2015 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.omnirom.device;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final boolean DEBUG = false;
    protected static final int GESTURE_REQUEST = 1;
    private static final int GESTURE_WAKELOCK_DURATION = 3000;

    // Supported scancodes
    //#define KEY_GESTURE_CIRCLE      250 // draw circle to lunch camera
    //#define KEY_GESTURE_TWO_SWIPE	251 // swipe two finger vertically to play/pause
    //#define KEY_GESTURE_V           252 // draw v to toggle flashlight
    //#define KEY_GESTURE_LEFT_V      253 // draw left arrow for previous track
    //#define KEY_GESTURE_RIGHT_V     254 // draw right arrow for next track

    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int KEY_DOUBLE_TAP = 143;
    private static final int KEY_HOME = 102;
    private static final int KEY_BACK = 158;
    private static final int KEY_RECENTS = 580;
 
    //private static final String BUTTON_DISABLE_FILE = "/sys/kernel/touchscreen/button_disable";

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_V_SCANCODE,
        KEY_DOUBLE_TAP
    };

    private static final int[] sHandledGestures = new int[]{
        GESTURE_V_SCANCODE
    };

    private static final int[] sDisabledButtons = new int[]{
        KEY_HOME,
        KEY_BACK,
        KEY_RECENTS
    };
    protected final Context mContext;
    private final PowerManager mPowerManager;
    private EventHandler mEventHandler;
    private WakeLock mGestureWakeLock;
    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;
    private static boolean mButtonDisabled;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HARDWARE_KEYS_DISABLE),
                    false, this);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            setButtonDisable(mContext);
        }
    }

    public KeyHandler(Context context) {
        mContext = context;
        mEventHandler = new EventHandler();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            switch(event.getScanCode()) {
            case GESTURE_V_SCANCODE:
                if (DEBUG) Log.i(TAG, "GESTURE_V_SCANCODE");
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                Intent torchIntent = new Intent("com.android.systemui.TOGGLE_FLASHLIGHT");
                torchIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
                mContext.sendBroadcastAsUser(torchIntent, user);
                break;
            }
        }
    }

    @Override
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }

        if (mButtonDisabled) {
            if (DEBUG) Log.i(TAG, "scanCode=" + event.getScanCode());
            if (ArrayUtils.contains(sDisabledButtons, event.getScanCode())) {
                return true;
            }
        }
        boolean isKeySupported = ArrayUtils.contains(sHandledGestures, event.getScanCode());
        if (isKeySupported && !mEventHandler.hasMessages(GESTURE_REQUEST)) {
            if (DEBUG) Log.i(TAG, "scanCode=" + event.getScanCode());
            Message msg = getMessageForKeyEvent(event);
            mEventHandler.sendMessage(msg);
        }
        return isKeySupported;
    }

    @Override
    public boolean canHandleKeyEvent(KeyEvent event) {
        return ArrayUtils.contains(sSupportedGestures, event.getScanCode());
    }

    @Override
    public boolean isDisabledKeyEvent(KeyEvent event) {
        if (mButtonDisabled) {
            if (ArrayUtils.contains(sDisabledButtons, event.getScanCode())) {
                return true;
            }
        }
        return false;
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }

    public static void setButtonDisable(Context context) {
        mButtonDisabled = Settings.System.getInt(
                context.getContentResolver(), Settings.System.HARDWARE_KEYS_DISABLE, 0) == 1;
        if (DEBUG) Log.i(TAG, "setButtonDisable=" + mButtonDisabled);
        //Utils.writeValue(BUTTON_DISABLE_FILE, disableButtons ? "1" : "0");
    }

    @Override
    public boolean isCameraLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        return event.getScanCode() == GESTURE_CIRCLE_SCANCODE;
    }

    @Override
    public boolean isWakeEvent(KeyEvent event){
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        return event.getScanCode() == KEY_DOUBLE_TAP;
    }
}

