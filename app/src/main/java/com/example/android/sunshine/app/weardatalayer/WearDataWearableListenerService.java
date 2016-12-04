package com.example.android.sunshine.app.weardatalayer;

import android.annotation.SuppressLint;
import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.example.android.sunshine.common.CommonConstants;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by satyanarayana.avv on 04-12-2016.
 */

public class WearDataWearableListenerService extends WearableListenerService {

  private static final String TAG = "WearDataWearableListenerService";

  @SuppressLint("LongLogTag")
  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent dataEvent : dataEvents) {
      if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
        String path = dataEvent.getDataItem().getUri().getPath();
        Log.d(TAG, path);
        if (path.equals(CommonConstants.PATH_WEATHER)) {
          SunshineSyncAdapter.syncImmediately(this);
        }
      }
    }
  }
}
