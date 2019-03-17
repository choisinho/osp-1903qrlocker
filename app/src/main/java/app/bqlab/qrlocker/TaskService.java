package app.bqlab.qrlocker;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

public class TaskService extends Service {
    SharedPreferences mSetting;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mSetting = getSharedPreferences("SETTING", MODE_PRIVATE);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
        mSetting.edit().remove("MASTER").apply();
        mSetting.edit().remove("DEVICE_CONNECTED").apply();
        mSetting.edit().remove("DEVICE_ADDRESS").apply();
    }
}
