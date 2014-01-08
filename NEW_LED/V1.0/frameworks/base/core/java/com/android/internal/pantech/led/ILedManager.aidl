package com.android.internal.pantech.led;

import com.android.internal.pantech.led.LedInfo;
/** {@hide} */
interface ILedManager {
    void postEvent(in LedInfo ledInfo, int flag, IBinder token);
    //void removeEvent(in LedInfo ledInfo, IBinder token);
    void removeEvent(int appId, IBinder token);
}
