package com.android.server;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import com.android.internal.pantech.led.LedInfo;
import com.android.internal.pantech.led.LedManager;

import android.util.Log;
import android.os.Handler;

class LedInfoQueue {

    static final String TAG = "LedInfoQueue";
    private static final boolean DEBUG = LedManagerService.DEBUG_QUEUE;
    final ArrayList<LedInfo> mLedInfos = new ArrayList<LedInfo>();
    final LedManagerService mService;
    Handler mHandler;

    LedInfoQueue(LedManagerService service, Handler handler) {
        mService = service;
        mHandler = handler;
    }

    protected void enqueueLedInfoLocked(LedInfo info) {
        final int appId = info.getAppId();
        if(!info.isPeriodicEvent()) {
            if(mLedInfos.size() > 0 && 
                    mLedInfos.get(0).getAppId() > appId) {
                if(DEBUG) Log.w(TAG, "Reject enqueue one-shot event id : " + appId + ". Because of priority of requested event lower than head event.");
                // death handler
                mService.removeDeathHandler(appId);
                return;
            }
        }

        if(!replaceLedInfoLocked(info)) {
            if(DEBUG) Log.d(TAG, "Enqueue event [appId : " + appId + "]");
            mLedInfos.add(info);
        }
        sortListLocked();
    }

    protected void dequeueLedInfoLocked(int appId) {
        int index = 0;
        for(; index < mLedInfos.size(); index++) {
            LedInfo info = mLedInfos.get(index);
            if(appId == info.getAppId()) {
                if(DEBUG) Log.d(TAG, "Dequeue event [appId : " + appId + "]");
                mLedInfos.remove(index);
                // death handler
                mService.removeDeathHandler(appId);
                break;
            }
        }
    }

    protected void scheduleLedInfoLocked() {
        LedInfo head = removeHeadLocked();
        mService.processLedEventLocked(head);
    }

    private LedInfo removeHeadLocked() {
        if(mLedInfos.size() > 0) {
            return mLedInfos.remove(0);
        }
        return null;
    }

    private boolean replaceLedInfoLocked(LedInfo info) {
        for(int i=0; i<mLedInfos.size(); i++) {
            if(mLedInfos.get(i).getAppId() == info.getAppId()) {
                if(DEBUG) Log.d(TAG, "Replace event [appId : " + mLedInfos.get(i).getAppId() + "] to [appId : " + info.getAppId() + "]");
                mLedInfos.set(i, info);
                return true;
            }
        }
        return false;
    }

    private void sortListLocked() {
        // sort to descending order
        Collections.sort(mLedInfos, new Comparator<LedInfo>() {
            @Override
            public int compare(LedInfo info1, LedInfo info2) {
                return info1.getAppId() > info2.getAppId() ? -1 : info1.getAppId() < info2.getAppId() ? 1 : 0;
            }

            public boolean equals(LedInfo info) {
                return false;
            }
        });
    }


    protected void dumpQueue(PrintWriter pw) {
        for(LedInfo info : mLedInfos) {
            pw.println("  - " + info.toString());
        }
    }
}
