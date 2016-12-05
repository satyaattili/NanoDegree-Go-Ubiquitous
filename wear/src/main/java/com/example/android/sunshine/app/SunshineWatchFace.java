/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.example.android.sunshine.common.CommonConstants;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import in.mobileappdev.wear.R;

import static com.example.android.sunshine.common.CommonConstants.KEY_ID_WEATHER;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
  private static final Typeface NORMAL_TYPEFACE =
      Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

  /**
   * Update rate in milliseconds for interactive mode. We update once a second since seconds are
   * displayed in interactive mode.
   */
  private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

  /**
   * Handler message id for updating the time periodically in interactive mode.
   */
  private static final int MSG_UPDATE_TIME = 0;
  private static final String TAG = "SunshineWatchFace";

  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }



  private static class EngineHandler extends Handler {
    private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

    public EngineHandler(SunshineWatchFace.Engine reference) {
      mWeakReference = new WeakReference<>(reference);
    }

    @Override
    public void handleMessage(Message msg) {
      SunshineWatchFace.Engine engine = mWeakReference.get();
      if (engine != null) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            engine.handleUpdateTimeMessage();
            break;
        }
      }
    }
  }

  private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
      GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    final Handler mUpdateTimeHandler = new EngineHandler(this);
    boolean mRegisteredTimeZoneReceiver = false;
    Paint mBackgroundPaint;
    Bitmap mBackgroundBitmap;
    Paint mTimePaint;
    Bitmap mWeatherIcon;
    boolean mAmbient;
    Calendar mCalendar;

    Paint mTimeSecondsPaint;
    Paint mDatePaint;
    Paint mDateAmbientPaint;
    Paint mTempHighPaint;
    Paint mTempLowPaint;
    Paint mTempLowAmbientPaint;

    String mWeatherHigh;
    String mWeatherLow;

    float mTimeYOffset;
    float mDateYOffset;
    float mWeatherYOffset;
    float mWeatherIconYOffset;


    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      }
    };


    GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(Wearable.API)
        .build();


    float mXOffset;
    float mYOffset;

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    boolean mLowBitAmbient;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
          .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
          .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
          .setShowSystemUiTime(false)
          .build());
      Resources resources = SunshineWatchFace.this.getResources();
      mYOffset = resources.getDimension(R.dimen.digital_date_y_offset);

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setColor(resources.getColor(R.color.background));

      mTimePaint = new Paint();
      mTimePaint = createTextPaint(Color.WHITE);
      mTimeSecondsPaint = createTextPaint(Color.WHITE);
      mDatePaint = createTextPaint(resources.getColor(R.color.white));
      mDateAmbientPaint = createTextPaint(Color.WHITE);
      mTempHighPaint = createTextPaint(Color.WHITE);
      mTempLowPaint = createTextPaint(resources.getColor(R.color.white));
      mTempLowAmbientPaint = createTextPaint(Color.WHITE);

      mCalendar = Calendar.getInstance();
    }

    @Override
    public void onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      super.onDestroy();
    }

    private Paint createTextPaint(int textColor) {
      Paint paint = new Paint();
      paint.setColor(textColor);
      paint.setTypeface(NORMAL_TYPEFACE);
      paint.setAntiAlias(true);
      return paint;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      if (visible) {
        mGoogleApiClient.connect();

        registerReceiver();

        mCalendar.setTimeZone(TimeZone.getDefault());
        long now = System.currentTimeMillis();
        mCalendar.setTimeInMillis(now);
      } else {
        unregisterReceiver();

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
          Wearable.DataApi.removeListener(mGoogleApiClient, this);
          mGoogleApiClient.disconnect();
        }
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    private void registerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);

      // Load resources that have alternate values for round watches.
      Resources resources = SunshineWatchFace.this.getResources();
      boolean isRound = insets.isRound();

      mTimeYOffset = resources.getDimension(isRound
          ? R.dimen.digital_time_y_offset_round : R.dimen.digital_time_y_offset);

      mDateYOffset = resources.getDimension(isRound
          ? R.dimen.digital_date_y_offset_round : R.dimen.digital_date_y_offset);

      mWeatherYOffset = resources.getDimension(isRound
          ? R.dimen.digital_weather_y_offset_round : R.dimen.digital_weather_y_offset);

      mWeatherIconYOffset = resources.getDimension(isRound
          ? R.dimen.digital_weather_icon_y_offset_round : R.dimen.digital_weather_icon_y_offset);

      float timeTextSize = resources.getDimension(isRound
          ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
      float dateTextSize = resources.getDimension(isRound
          ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
      float tempTextSize = resources.getDimension(isRound
          ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

      mTimePaint.setTextSize(timeTextSize);
      mTimeSecondsPaint.setTextSize((float) (tempTextSize * 0.80));
      mDatePaint.setTextSize(dateTextSize);
      mDateAmbientPaint.setTextSize(dateTextSize);
      mTempHighPaint.setTextSize(tempTextSize);
      mTempLowAmbientPaint.setTextSize(tempTextSize);
      mTempLowPaint.setTextSize(tempTextSize);

    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }


    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      if (mAmbient != inAmbientMode) {
        mAmbient = inAmbientMode;
        if (mLowBitAmbient) {
          mTimePaint.setAntiAlias(!inAmbientMode);
        }
        invalidate();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      // Draw the background.
      if (mAmbient) {
        canvas.drawColor(Color.BLACK);
      } else {
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
        if(mBackgroundBitmap != null){
          Paint alphaPaint = new Paint();
          alphaPaint.setAlpha(80);
          canvas.drawBitmap(mBackgroundBitmap, 0, 0, alphaPaint);
        }

      }

      // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
      long now = System.currentTimeMillis();
      mCalendar.setTimeInMillis(now);

      boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFace.this);
      int minute = mCalendar.get(Calendar.MINUTE);
      int second = mCalendar.get(Calendar.SECOND);
      int am_pm  = mCalendar.get(Calendar.AM_PM);

      String timeText;
      if (is24Hour) {
        int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
        timeText = String.format("%02d:%02d", hour, minute);
      } else {
        int hour = mCalendar.get(Calendar.HOUR);
        if (hour == 0) {
          hour = 12;
        }
        timeText = String.format("%d:%02d", hour, minute);
      }

      String secondsText = String.format("%02d", second);
      String amPmText = Helper.getAmPmString(getResources(), am_pm);
      float timeTextLen = mTimePaint.measureText(timeText);
      float xOffsetTime = timeTextLen / 2;
      if (mAmbient) {
        if (!is24Hour) {
          xOffsetTime = xOffsetTime + (mTimeSecondsPaint.measureText(amPmText) / 2);
        }
      } else {
        xOffsetTime = xOffsetTime + (mTimeSecondsPaint.measureText(secondsText) / 2);
      }
      float xOffsetTimeFromCenter = bounds.centerX() - xOffsetTime;
      canvas.drawText(timeText, xOffsetTimeFromCenter, mTimeYOffset, mTimePaint);
      if (mAmbient) {
        if (!is24Hour) {
          canvas.drawText(amPmText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeYOffset,
              mTimeSecondsPaint);
        }
      } else {
        canvas.drawText(secondsText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeYOffset,
            mTimeSecondsPaint);
      }

      // Decide which paint to user for the next bits dependent on ambient mode.
      Paint datePaint = mAmbient ? mDateAmbientPaint : mDatePaint;

      Resources resources = getResources();

      // Draw the date
      String dayOfWeekString   = Helper.getDayOfWeekString(resources, mCalendar.get(Calendar
          .DAY_OF_WEEK));
      String monthOfYearString = Helper.getMonth(resources, mCalendar.get(Calendar
          .MONTH));

      int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);
      int year = mCalendar.get(Calendar.YEAR);

      String dateText = String.format("%s, %s %d %d", dayOfWeekString, monthOfYearString, dayOfMonth, year);
      float xOffsetDate = datePaint.measureText(dateText) / 2;
      canvas.drawText(dateText, bounds.centerX() - xOffsetDate, mDateYOffset, datePaint);

      // Draw high and low temp if we have it
      if (mWeatherHigh != null && mWeatherLow != null && mWeatherIcon != null) {

        float highTextLen = mTempHighPaint.measureText(mWeatherHigh);
        float lowTextLen = mTempLowAmbientPaint.measureText(mWeatherLow);
        float xOffset = bounds.centerX() - ((highTextLen + lowTextLen + 20) / 2);
        if (mAmbient) {
          canvas.drawText(mWeatherHigh, xOffset, mWeatherYOffset, mTempHighPaint);
          canvas.drawText(mWeatherLow, xOffset + highTextLen + 20, mWeatherYOffset,
              mTempLowAmbientPaint);
        } else {
          canvas.drawText(mWeatherHigh, xOffset, mWeatherYOffset, mTempHighPaint);
          canvas.drawText(mWeatherLow, xOffset + highTextLen + 20 , mWeatherYOffset, mTempLowPaint);
          float iconXOffset = bounds.centerX();
          canvas.drawBitmap(mWeatherIcon, iconXOffset - mWeatherIcon.getHeight()/2,
              mWeatherIconYOffset ,
              null);

        }
      }
    }

    /**
     * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private void updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }

    /**
     * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    /**
     * Handle updating the time periodically in interactive mode.
     */
    private void handleUpdateTimeMessage() {
      invalidate();
      if (shouldTimerBeRunning()) {
        long timeMs = System.currentTimeMillis();
        long delayMs = INTERACTIVE_UPDATE_RATE_MS
            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
      }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
      Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
      requestWeatherInfo();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
      Log.d(TAG, "onDataChanged");

      for (DataEvent dataEvent : dataEventBuffer) {
        if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
          DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
          String path = dataEvent.getDataItem().getUri().getPath();
          Log.d(TAG, path);
          if (path.equals(CommonConstants.PATH_WEATHER_INFO)) {
            if (dataMap.containsKey(CommonConstants.KEY_HIGH)) {
              mWeatherHigh = dataMap.getString(CommonConstants.KEY_HIGH);
              Log.d(TAG, "High = " + mWeatherHigh);
            } else {
              Log.d(TAG, "What? No high?");
            }

            if (dataMap.containsKey(CommonConstants.KEY_LOW)) {
              mWeatherLow = dataMap.getString(CommonConstants.KEY_LOW);
              Log.d(TAG, "Low = " + mWeatherLow);
            } else {
              Log.d(TAG, "What? No low?");
            }

            if (dataMap.containsKey(KEY_ID_WEATHER)) {
              int weatherId = dataMap.getInt(KEY_ID_WEATHER);
              Drawable b = getResources().getDrawable(Helper.getIconResourceForWeatherCondition
                  (weatherId));
              Bitmap icon = ((BitmapDrawable) b).getBitmap();
              mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), Helper.getBackgroundResourceForWeatherCondition(weatherId));

              float scaledWidth =
                  (mTempHighPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
              mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth,
                  (int) mTempHighPaint.getTextSize(), true);

            } else {
              Log.d(TAG, "What? no weatherId?");
            }

            invalidate();
          }

        }
        }
    }

    private void requestWeatherInfo() {
      PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(CommonConstants.PATH_WEATHER);
      putDataMapRequest.getDataMap().putString(CommonConstants.KEY_UUID, UUID.randomUUID()
          .toString());
      PutDataRequest request = putDataMapRequest.asPutDataRequest();

      Wearable.DataApi.putDataItem(mGoogleApiClient, request)
          .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
              if (!dataItemResult.getStatus().isSuccess()) {
                Log.d(TAG, "Failed to get wearable data");
              } else {
                Log.d(TAG, "Success to get wearable data");
              }
            }
          });
    }
  }
}
