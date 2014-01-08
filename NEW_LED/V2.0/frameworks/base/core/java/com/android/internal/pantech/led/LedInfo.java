package com.android.internal.pantech.led;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * <p>
 * LedInfo 
 * </p>
 *
 * <pre class="prettyprint">
 * import com.android.internal.pantech.LedInfo;
 * import com.android.internal.pantech.LedManager;
 *
 * public class TestLedActivity {
 *      int[][] mPatterns = {
 *          {200, LedInfo.RED, LedInfo.WHITE, LedInfo.YELLOW, LedInfo.GREEN, LedInfo.BLUE, LedInfo.CYAN, LedInfo.MAGENTA},
 *          {200, LedInfo.WHITE, LedInfo.WHITE, LedInfo.WHITE, LedInfo.WHITE, LedInfo.WHITE, LedInfo.WHITE, LedInfo.WHITE}, 
 *      };
 *
 *      LedInfo mPeriodicEvent;
 *      LedInfo mOneShotEvent;
 *
 *      public void onCreate() {
 *          mPeriodicEvent = new LedInfo(LedManager.APPID_CALL, LedInfo.BLUE, 200, 3000);
 *          mOneShotEvent = new LedInfo(LedManager.APPID_ALARM, mPatterns, 1);
 *          mLedManager = (LedManager) getSystemService(Context.LED_SERVICE);
 *      }
 *
 *      public void performOneShotEvent(boolean start) {
 *          if(start)
 *              mLedManager.postEvent(mOneShotEvent, 0);
 *          else
 *              mLedManager.removeEvent(mOneShotEvent);
 *      }
 *
 *      public void performPeriodicEvent(boolean start) {
 *          if(start)
 *              mLedManager.postEvent(mPeriodicEvent, 0);
 *          else
 *              mLedManager.removeEvent(mPeriodicEvent);
 *      }
 * }
 * </pre>
 */

public class LedInfo implements Parcelable {

    public static final int LED_COUNT = 7;
    private boolean mPeriodicity;
    private int mAppId;
    public int mOption;

    /* for periodic */
    public int[] mColor = new int[LED_COUNT];
    public int mOnMs;
    public int mOffMs;

    /* for one-shot */
    public int[][] mPattern;
    public int mRepeat;

    public static final int BLACK   = 0x00000000;
    public static final int WHITE   = 0x00FFFFFF;
    public static final int RED     = 0x00FF0000;
    public static final int YELLOW  = 0x00FFFF00;
    public static final int GREEN   = 0x0000FF00;
    public static final int CYAN    = 0x0000FFFF;
    public static final int BLUE    = 0x000000FF;;
    public static final int MAGENTA = 0x00FF00FF;

    /**
     * Led information for on-shot event;
     *
     * @param appId
     * @param pattern 
     * @param repeat
     */
    public LedInfo(int appId, int[][] pattern, int repeat) {
        if(!isValidArguments(appId, pattern, repeat)) {
            throw new IllegalArgumentException("invalid arguments");
        }
        mAppId = appId;
        mPattern = pattern;
        mRepeat = repeat;
        mPeriodicity = false;
    }

    /**
     * Led information for periodic event;
     *
     * @param appId
     * @param color 
     * @param onMs
     * @param offMs
     */
    public LedInfo(int appId, int[] color, int onMs, int offMs) {
        if(!isValidArguments(appId, color, onMs, offMs)) {
            throw new IllegalArgumentException("invalid arguments");
        }
        mAppId = appId;
        mColor = color;
        mOnMs = onMs;
        mOffMs = offMs;
        mPeriodicity = true;
    }   

    public LedInfo(Parcel parcel) {
        mPeriodicity = (parcel.readInt() == 1) ? true : false;
        mAppId = parcel.readInt();
        mOption = parcel.readInt();
        if(mPeriodicity) {
            mColor = parcel.createIntArray();
            mOnMs = parcel.readInt();
            mOffMs = parcel.readInt(); 
        }
        else {
            final int nPattern = parcel.readInt();
            final int color[] = parcel.createIntArray();
            mPattern = new int[nPattern][color.length];
            mPattern[0] = color;
            for(int i=1; i<nPattern; i++) {
                mPattern[i] = parcel.createIntArray();
            }
            mRepeat = parcel.readInt();
        }
    }

    public void setPattern(int[][] pattern) {
        if(!mPeriodicity) {
            mPattern = pattern;
            return;
        }
        throw new IllegalArgumentException("only One-shot event use setPattern()");
    }

    public void setColor(int[] color) {
        if(mPeriodicity) {
            mColor = color;
            return;
        }
        throw new IllegalArgumentException("only periodic event use setColor()");
    }


    public int getAppId() {
        return mAppId;
    }

    public int getPatternCount() {
        if(!mPeriodicity) {
            return mPattern.length;
        }
        return -1;
    }

    public int[] getPattern(int index) {
        if(!mPeriodicity && index < mPattern.length)
            return mPattern[index];
        return null;
    }
    
    public boolean isPeriodicEvent() {
        return mPeriodicity;
    }
    
    public int getRepeatCount() {
    	if(!mPeriodicity) {
    		return mRepeat;
    	}
    	return 0;
    }

    private boolean isValidArguments(int appId, int[] color, int onMs, int offMs) {
        if((appId > LedManager.APPID_MIN && appId < LedManager.APPID_TOP) &&
            (color != null && color.length == LED_COUNT) &&
            (onMs > -1 && offMs > -1)) {
            return true;
        }
        return false;
    }
    private boolean isValidArguments(int appId, int[][] pattern, int repeat) {
        if((appId > LedManager.APPID_MIN && appId < LedManager.APPID_TOP) &&
            (pattern != null && pattern[0].length == LED_COUNT+1) &&
            repeat > 0) {
            return true;
        }
        return false;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mPeriodicity ? 1 : 0);
        parcel.writeInt(mAppId);
        parcel.writeInt(mOption);
        if(mPeriodicity) {
            //parcel.writeInt(mColor);
            parcel.writeIntArray(mColor);
            parcel.writeInt(mOnMs);
            parcel.writeInt(mOffMs);
        }
        else {
            final int N = mPattern.length;
            parcel.writeInt(N);
            for(int i=0; i<N; i++) {
                parcel.writeIntArray(mPattern[i]);
            }
            parcel.writeInt(mRepeat);
        }
    }

    public int describeContents() {
        return 0;
    }
    public static final Parcelable.Creator<LedInfo> CREATOR = new Parcelable.Creator<LedInfo>() {
        public LedInfo createFromParcel(Parcel parcel) {
            return new LedInfo(parcel);
        }

        public LedInfo[] newArray(int size) {
            return new LedInfo[size];
        }
    };

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AppId: " + mAppId);
        builder.append(", Periodicity: " + mPeriodicity);
        if(mPeriodicity) {
            for(int i=0;i<mColor.length;i++) {
                builder.append("\n#" + i + "] " + "color: 0x" + Integer.toHexString(mColor[i]));
            }
            builder.append(", OnMs: " + mOnMs);
            builder.append(", OffMs: " + mOffMs);
        }
        else {
            builder.append(", Repeat: " + mRepeat);
            for(int nPattern=0; nPattern < mPattern.length; nPattern++) {
                builder.append("\n#" + nPattern + "] ");
                for(int i=0; i < mPattern[nPattern].length; i++) {
                    if(i == 0)
                        builder.append("duration: " + mPattern[nPattern][0]);
                    else
                        builder.append("color : \t0x" + Integer.toHexString(mPattern[nPattern][i]));
                }
            }
        }
        return builder.toString();
    }

}
