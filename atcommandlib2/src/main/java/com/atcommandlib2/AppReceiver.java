package com.atcommandlib2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.Calendar;

public class AppReceiver extends BroadcastReceiver {
    private PendingIntent pendingIntent;
    private static final int ALARM_REQUEST_CODE = 134;
    //set interval notifikasi 1 jam
    private int interval_Hour = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent alarmIntent = new Intent(context, AppReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, alarmIntent, 0);

        //set waktu sekarang berdasarkan interval
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, interval_Hour);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        //set alarm manager dengan memasukkan waktu yang telah dikonversi menjadi milliseconds
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        } else if (android.os.Build.VERSION.SDK_INT >= 19) {
            manager.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        }
        //kirim notifikasi
        Log.d("Data Running___","OK1");
        SendData sd=new SendData();
        sd.run();

    }

}
