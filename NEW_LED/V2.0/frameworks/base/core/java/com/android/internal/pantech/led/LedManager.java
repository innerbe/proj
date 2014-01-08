package com.android.internal.pantech.led;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import android.util.Log;

/**
 * LedManager provides access to led control.
 * <p>
 * Use <code>Context.getSystemService(Context.LED_SERVICE)</code> to get
 * an instance of this class.
 */
public class LedManager {

    private final Context mContext;
    private static ILedManager sService;
    private final IBinder mToken = new Binder();
    private static final String TAG = "LedManager";

    public static int APPID_MIN = 0;
    public static int APPID_BATTERY = 5;
    public static int APPID_CALL = 20;
    public static int APPID_TOP = APPID_CALL + 1;

    /**
     * Replace event
     */
    public static int LED_FLAG_REPLACE = 0x00000001;

    /**
     * Undefined flag
     */
    public static int LED_FLAG_UNDEFINED = 0x00000002;


    /**
     * @hide
     */
    public LedManager(Context context) {
        mContext = context;
    }

    /**
     * @hide
     */
    private static ILedManager getService() {
        if(sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(Context.LED_SERVICE);
        sService = ILedManager.Stub.asInterface(b);
        return sService;
    }

    public void postEvent(LedInfo ledInfo, int flag) {
        ILedManager service = getService();
        try {
            service.postEvent(ledInfo, LED_FLAG_REPLACE, mToken);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in postEvent" + e);
        }
    }

    public void removeEvent(LedInfo ledInfo) {
        ILedManager service = getService();
        try {
            service.removeEvent(ledInfo, mToken);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in removeEvent" + e);
        }
    }
}
