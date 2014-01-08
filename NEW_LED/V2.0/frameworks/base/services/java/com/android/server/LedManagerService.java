package com.android.server;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import com.android.internal.pantech.led.LedInfo;
import com.android.internal.pantech.led.ILedManager;

public class LedManagerService extends ILedManager.Stub {

    private static final String TAG = "LedManagerService";
    static final boolean DEBUG_QUEUE = true;
    static final boolean DEBUG_SERVICE = true;
    static final boolean DEBUG_DEATH = true;

    static final int TURN_OFF_MSG = 0;
    static final int SCHEDULE_EVENT_MSG = 1;

    private LedInfoQueue mLedInfoQueue;
    private Object mLock = new Object();
    private final Context mContext;
    private int mNativePointer;
    private HandlerThread mHandlerThread;
    private Handler mOneShotEventHandler;
    private OneShotEvent mOneShotEventRunnable = new OneShotEvent();
    private final ArrayList<LedClientDeathHandler> mLedClientDeathHandlers = new ArrayList<LedClientDeathHandler>();
    private LedInfo mCurrentLedInfo;
    Handler mHandler;

    LedManagerService(Context context) {
        mNativePointer = init_native();
        mContext = context;
        mCurrentLedInfo = null;
        mHandler = new LedManagerHandler();

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mOneShotEventHandler = new Handler(mHandlerThread.getLooper());
        mLedInfoQueue = new LedInfoQueue(this, mHandler);
    }

    public void postEvent(LedInfo ledInfo, int flag, IBinder token) {
        synchronized(mLock) {
            // death handler
            // this code must Ap E It U Ya De.
            final int appId = ledInfo.getAppId();
            addDeathHandler(appId, token); 

            mLedInfoQueue.enqueueLedInfoLocked(ledInfo);
            mLedInfoQueue.scheduleLedInfoLocked();
        }
    }

    public void removeEvent(LedInfo ledInfo, IBinder token) {
        synchronized(mLock) {
            if(mCurrentLedInfo != null && mCurrentLedInfo.getAppId() == ledInfo.getAppId()) {
                if(DEBUG_SERVICE) Log.d(TAG, "removeEvent: remove current");
                turnOff();
            }
            mLedInfoQueue.dequeueLedInfoLocked(ledInfo.getAppId());
            mLedInfoQueue.scheduleLedInfoLocked();
        }
    }

    private void sceduleLedInfo() {
        synchronized(mLock) {
            mLedInfoQueue.scheduleLedInfoLocked();
        }
    }

    class LedManagerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if(DEBUG_SERVICE) Log.d(TAG, "handleMessage: what: " + msg.what + ", info: " + (LedInfo)msg.obj);
            switch (msg.what) {
                case TURN_OFF_MSG:
                    turnOff();
                    break;
                case SCHEDULE_EVENT_MSG:
                    sceduleLedInfo();
                    break;
            }
        }
    }

    protected void processLedEventLocked(LedInfo head) {
        if(DEBUG_SERVICE) Log.d(TAG, "processLedEvent: [Current : " + mCurrentLedInfo + "], [Head: " + head + "]");
        if(mCurrentLedInfo == null && head == null) {
            setFinishEventLocked();
        }
        else if(mCurrentLedInfo != null && head != null) {
            if(head.getAppId() >= mCurrentLedInfo.getAppId()) { 
                if(mCurrentLedInfo.isPeriodicEvent()) {
                    mLedInfoQueue.enqueueLedInfoLocked(mCurrentLedInfo);
                }
                // play head
                if(head.isPeriodicEvent()) {
                    performPeriodicEventLocked(head);
                } else {
                    performOneShotEventLocked(head);
                }
            }
            else {
                if(head.isPeriodicEvent()) {
                    mLedInfoQueue.enqueueLedInfoLocked(head);
                }
                else {
                    //death handler
                    removeDeathHandler(head.getAppId());
                }
            }
        }
        else if(mCurrentLedInfo == null) {
            // play head
            if(head.isPeriodicEvent()) {
                performPeriodicEventLocked(head);
            } else {
                performOneShotEventLocked(head);
            }
        }
    }

    private void turnOff() {
        synchronized(mLock) {
            if(mCurrentLedInfo != null) {
               if(mCurrentLedInfo.isPeriodicEvent()) {
                	setFinishEventLocked();
                } 
                else {
                    mOneShotEventRunnable.stopRunnable();
                }
            }
        }
    }

    private void performPeriodicEventLocked(LedInfo head) {
        synchronized(mLock) {
            if(DEBUG_SERVICE) Log.d(TAG, "performPeriodicEventLocked: appId : " + head.getAppId());
            mOneShotEventRunnable.stopRunnable();
            mCurrentLedInfo = head;
            setLedLocked(mCurrentLedInfo.mColor[0], 
                        mCurrentLedInfo.mColor[1], 
                        mCurrentLedInfo.mColor[2], 
                        mCurrentLedInfo.mColor[3], 
                        mCurrentLedInfo.mColor[4], 
                        mCurrentLedInfo.mColor[5], 
                        mCurrentLedInfo.mColor[6], 
                        mCurrentLedInfo.mOnMs, mCurrentLedInfo.mOffMs, mCurrentLedInfo.mOption);
        }
    }

    private void performOneShotEventLocked(LedInfo head) {
        synchronized(mLock) {
            if(DEBUG_SERVICE) Log.d(TAG, "performOneShotEventLocked: appId : " + head.getAppId());
            mOneShotEventRunnable.stopRunnable();
            mCurrentLedInfo = head;
            mOneShotEventRunnable.setLedInfo(head);
            mOneShotEventHandler.post(mOneShotEventRunnable);
        }
    }

    class OneShotEvent implements Runnable {

        private int mPatternCount;
        private int mRepeat;
        private boolean mStopSignal;
        private boolean mIsRunning;
        private LedInfo mLedInfo;

        public void run() {
            synchronized(this) {
                mIsRunning = true;
                if(DEBUG_SERVICE) Log.d(TAG, "---begin OneShotEvent");
                mStopSignal = false;
                mPatternCount = mLedInfo.getPatternCount();
                mRepeat = mLedInfo.getRepeatCount();
                for(int repeat=0; repeat < mRepeat && !mStopSignal; repeat++) {
                    for(int patternIndex=0; patternIndex < mPatternCount && !mStopSignal; patternIndex++) {
                        int pattern[] = mLedInfo.getPattern(patternIndex);
                        int duration = pattern[0];
                        setLedLocked(pattern[1], 
                                    pattern[2],
                                    pattern[3],
                                    pattern[4],
                                    pattern[5],
                                    pattern[6],
                                    pattern[7],
                                    0, 0, mLedInfo.mOption);
                        try {
                            wait(duration);
                        } catch(InterruptedException e) {
                        }
                    }
                }

                if(!mStopSignal) {
                    setFinishEventLocked();
                    mHandler.sendMessage(mHandler.obtainMessage(SCHEDULE_EVENT_MSG));
                }
                if(DEBUG_SERVICE) Log.d(TAG, "---end OneShotEvent");
                mIsRunning = false;
            }
        }

        public void setLedInfo(LedInfo info) {
            // for call by value
            if(info.isPeriodicEvent()) {
                mLedInfo = new LedInfo(info.getAppId(), info.mColor, info.mOnMs, info.mOffMs);
            }
            else {
                final int N = info.mPattern.length;
                final int M = info.mPattern[0].length;
                int pattern[][] = new int[N][M];
                for(int i=0;i<N;i++) {
                    for(int j=0;j<M;j++) {
                        pattern[i][j] = info.mPattern[i][j];
                    }
                }
               
                mLedInfo = new LedInfo(info.getAppId(), pattern, info.getRepeatCount());
            }
        }

        public void stopRunnable() {
            synchronized(this) {
                if(mIsRunning) {
                    mStopSignal = true;
                    notify();
                    setFinishEventLocked();
                }
            }
        }
    }

    private void setFinishEventLocked() {
        if(DEBUG_SERVICE) Log.d(TAG, "setFinishEventLocked");

        // death handler
        if(mCurrentLedInfo != null) {
            final int appId = mCurrentLedInfo.getAppId();
            removeDeathHandler(appId);
        }

        setLedLocked(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        mCurrentLedInfo = null;
    }

    private void setLedLocked(int led1, int led2, int led3, int led4, int led5, int led6, int led7, int onMs, int offMs, int option) {
        setLed_native(mNativePointer, led1, led2, led3, led4, led5, led6, led7, onMs, offMs, option);
    }

    private class LedClientDeathHandler implements IBinder.DeathRecipient {
        private IBinder mToken;
        private int mAppId;

        LedClientDeathHandler(IBinder token, int appId) {
            mToken = token;
            mAppId = appId;
        }

        @Override
        public void binderDied() {
            Log.w(TAG, "binderDied. appId : " + mAppId);

            synchronized(mLock) {
                // 1. remove info from LedClientDeathHandlers
                mToken.unlinkToDeath(this, 0);
                int index = findDeathHandlerIndexLocked(mAppId);
                if(index > -1) {
                    mLedClientDeathHandlers.remove(index);
                }

                // 2. remove info from LedInfoQueue. don't care duplicated deletion. 
                mLedInfoQueue.dequeueLedInfoLocked(mAppId);

                // turn off Led
                if(mCurrentLedInfo != null && mCurrentLedInfo.getAppId() == mAppId) {
                    if(mCurrentLedInfo.isPeriodicEvent()) {
                        setFinishEventLocked();
                    }
                    else {
                        mOneShotEventRunnable.stopRunnable();
                    }
                }
            }
        }

        public int getAppId() {
            return mAppId;
        }

        public IBinder getBinder() {
            return mToken;
        }

        @Override
        public String toString() {
            return "AppId : " + mAppId;
        }
    }

    private int findDeathHandlerIndexLocked(int appId) {
        final int size = mLedClientDeathHandlers.size();
        for(int index=0; index < size; index++) {
            LedClientDeathHandler h = mLedClientDeathHandlers.get(index);
            if(h.getAppId() == appId)
                return index;
        }
        
        return -1;
    }

    protected void addDeathHandler(int appId, IBinder token) {
        // death handler
        final int index = findDeathHandlerIndexLocked(appId);
        if(appId > -1 && index == -1) {
            if(DEBUG_DEATH) Log.d(TAG, "add death handler id : " + appId);
            LedClientDeathHandler h = new LedClientDeathHandler(token, appId);
            try {
                token.linkToDeath(h, 0);
            } catch(RemoteException e) {}
            mLedClientDeathHandlers.add(h);
        }
    }
    protected void removeDeathHandler(int appId) {
        final int index = findDeathHandlerIndexLocked(appId);
        if(index == -1)
            return;
        if(DEBUG_DEATH) Log.d(TAG, "remove death handler id : " + appId);
        LedClientDeathHandler h = mLedClientDeathHandlers.remove(index);
        h.getBinder().unlinkToDeath(h, 0);
    }

    private static native int init_native();
    private static native void setLed_native(int ptr, int led1, int led2, int led3, int led4, int led5, int led6, int led7, int onMs, int offMs, int option);

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);
        
        pw.println("Current Led info:");
        pw.print(mCurrentLedInfo);
        pw.println();
        pw.println("\nLedInfoQueue:");
        mLedInfoQueue.dumpQueue(pw);
        pw.println("\nDeathHandler List:");
        for(LedClientDeathHandler handler : mLedClientDeathHandlers) {
            pw.println("  - " + handler.toString());
        }

    }
}
