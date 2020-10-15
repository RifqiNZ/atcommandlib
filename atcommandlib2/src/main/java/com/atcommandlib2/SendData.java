package com.atcommandlib2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.atcommandlib2.test.HttpDownloadTest;
import com.atcommandlib2.test.HttpUploadTest;
import com.atcommandlib2.test.PingTest;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

interface command {
public void time (Context context);
public void run();
public void send();
}

public class SendData implements command{
    //InitTime


    private PendingIntent pendingIntent;
    private static final int ALARM_REQUEST_CODE = 134;
    private int interval_seconds = 3;
    public static Context context2;
    //
    String ping, jitter,download, upload, longitudePhone,latitudePhone,nameIsp,ip,hostLocation;

    //initData
    static int position = 0;
    static int lastPosition = 0;
    GetSpeedTestHostsHandler getSpeedTestHostsHandler = null;
    HashSet<String> tempBlackList;

    public void time (Context context){
        context2=context;
        Intent alarmIntent = new Intent(context2, com.atcommandlib2.AppReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, alarmIntent, 0);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 15);
        cal.set(Calendar.MINUTE,30);
        AlarmManager alarmManager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
    }

    public void run(){
        tempBlackList = new HashSet<>();

        //startHttpHosthandler
        getSpeedTestHostsHandler = new GetSpeedTestHostsHandler();
        getSpeedTestHostsHandler.start();

        //run all Service
        new Thread(new Runnable() {
            @Override
            public void run() {
                //Get egcodes.speedtest hosts
                int timeCount = 600; //1min
                while (!getSpeedTestHostsHandler.isFinished()) {
                    timeCount--;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    //when Error Connection
//                    if (timeCount <= 0) {
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(getApplicationContext(), "No Connection...", Toast.LENGTH_LONG).show();
//
//                            }
//                        });
//                        getSpeedTestHostsHandler = null;
//                        return;
//                    }
                }

                //findClosestServer
                HashMap<Integer, String> mapKey = getSpeedTestHostsHandler.getMapKey();
                HashMap<Integer, List<String>> mapValue = getSpeedTestHostsHandler.getMapValue();
                double selfLat = getSpeedTestHostsHandler.getSelfLat();
                double selfLon = getSpeedTestHostsHandler.getSelfLon();
                double tmp = 19349458;
                double dist = 0.0;
                int findServerIndex = 0;

                for (int index : mapKey.keySet()) {
                    if (tempBlackList.contains(mapValue.get(index).get(5))) {
                        continue;
                    }

                    Location source = new Location("Source");
                    source.setLatitude(selfLat);
                    source.setLongitude(selfLon);

                    List<String> ls = mapValue.get(index);
                    Location dest = new Location("Dest");
                    dest.setLatitude(Double.parseDouble(ls.get(0)));
                    dest.setLongitude(Double.parseDouble(ls.get(1)));

                    double distance = source.distanceTo(dest);
                    if (tmp > distance) {
                        tmp = distance;
                        dist = distance;
                        findServerIndex = index;
                    }
                }
                String testAddr = mapKey.get(findServerIndex).replace("http://", "https://");
                final List<String> info = mapValue.get(findServerIndex);
                final double distance = dist;

                final List<Double> pingRateList = new ArrayList<>();
                final List<Double> downloadRateList = new ArrayList<>();
                final List<Double> uploadRateList = new ArrayList<>();
                Boolean pingTestStarted = false;
                Boolean pingTestFinished = false;
                Boolean downloadTestStarted = false;
                Boolean downloadTestFinished = false;
                Boolean uploadTestStarted = false;
                Boolean uploadTestFinished = false;

                //Init Test
                final PingTest pingTest = new PingTest(info.get(6).replace(":8080", ""), 3);
                final HttpDownloadTest downloadTest = new HttpDownloadTest(testAddr.replace(testAddr.split("/")[testAddr.split("/").length - 1], ""));
                final HttpUploadTest uploadTest = new HttpUploadTest(testAddr);

//                pingTest.start();
//                downloadTest.start();
//                uploadTest.start();

//Tests
                while (true) {
                    if (!pingTestStarted) {
                        pingTest.start();
                        pingTestStarted = true;
                    }
                    if (pingTestFinished && !downloadTestStarted) {
                        downloadTest.start();
                        downloadTestStarted = true;
                    }
                    if (downloadTestFinished && !uploadTestStarted) {
                        uploadTest.start();
                        uploadTestStarted = true;
                    }


                    //Ping Test
                    if (pingTestFinished) {
                        //Failure
                        if (pingTest.getAvgRtt() == 0) {
                            System.out.println("Ping error...");
                        } else {
                            //Success

                        }
                    } else {
                        pingRateList.add(pingTest.getInstantRtt());

                    }


                    //Download Test
                    if (pingTestFinished) {
                        if (downloadTestFinished) {
                            //Failure
                            if (downloadTest.getFinalDownloadRate() == 0) {
                                System.out.println("Download error...");
                            } else {
                                //Success
                            }
                        } else {
                            //Calc position
                            double downloadRate = downloadTest.getInstantDownloadRate();
                            downloadRateList.add(downloadRate);

                            lastPosition = position;

                        }
                    }


                    //Upload Test
                    if (downloadTestFinished) {
                        if (uploadTestFinished) {
                            //Failure
                            if (uploadTest.getFinalUploadRate() == 0) {
                                System.out.println("Upload error...");
                            } else {
                                //Success
                            }
                        } else {
                            //Calc position
                            double uploadRate = uploadTest.getInstantUploadRate();
                            uploadRateList.add(uploadRate);

                            lastPosition = position;

                        }
                    }

                    //Test bitti
                    if (pingTestFinished && downloadTestFinished && uploadTest.isFinished()) {
                        break;
                    }

                    if (pingTest.isFinished()) {
                        pingTestFinished = true;
                    }
                    if (downloadTest.isFinished()) {
                        downloadTestFinished = true;
                    }
                    if (uploadTest.isFinished()) {
                        uploadTestFinished = true;
                    }

                    if (pingTestStarted && !pingTestFinished) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                        }
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                    }
                }

//                Log.d("Data", "Ping = " + dec.format(pingTest.getInstantRtt()));
//                Log.d("Data", "Download =" + dec.format(downloadTest.getInstantDownloadRate()));
//                Log.d("Data", "Upload =" + dec.format(uploadTest.getInstantUploadRate()));
////        Log.d("Data","PingTextview ="+pingTextView.getText());
////        Log.d("Data","DownloadTextview ="+downloadTextView.getText());
////        Log.d("Data","UploadTextview ="+uploadTextView.getText());
//                Log.d("Data", "ISP =" + getSpeedTestHostsHandler.getIsp());
//                Log.d("Data", "IP =" + getSpeedTestHostsHandler.getIp());
//                Log.d("Data", "Lat =" + getSpeedTestHostsHandler.getSelfLat());
//                Log.d("Data", "Lon =" + getSpeedTestHostsHandler.getSelfLon());
//                Log.d("Data", "ClosestServer ="+String.format("Host Location: %s [Distance: %s km]", info.get(2), new DecimalFormat("#.##").format(distance / 1000)));
//                Log.d("Data", "Ping = " + pingTest);
//
             ping= String.valueOf(pingTest.getAvgRtt());
             jitter= String.valueOf(pingTest.getJitter());
             download= String.valueOf(downloadTest.getFinalDownloadRate());
             upload= String.valueOf(uploadTest.getFinalUploadRate());
             longitudePhone= String.valueOf(getSpeedTestHostsHandler.getSelfLon());
             latitudePhone= String.valueOf(getSpeedTestHostsHandler.getSelfLat());
             nameIsp=getSpeedTestHostsHandler.getIsp();
             ip=getSpeedTestHostsHandler.getIp();
             hostLocation=info.get(2)+" "+ new DecimalFormat("#.##").format(distance / 1000);
             Log.d("DataReal=======",ping+jitter+download+upload+longitudePhone+latitudePhone+nameIsp+ip+hostLocation);

            send();
            }
        }).start();

    }
    public void send() {
        MediaType MEDIA_TYPE = MediaType.parse("application/json");
        String url = "http://34.87.107.144:8080/api/v1/MvjROrD0kg73XoC8CMLP/telemetry";

        OkHttpClient client = new OkHttpClient();

        JSONObject postdata = new JSONObject();
        try {
            postdata.put("Ping", ping);
            postdata.put("Jitter", jitter);
            postdata.put("Upload", upload);
            postdata.put("Download", download);
            postdata.put("LongitudePhone", longitudePhone);
            postdata.put("LatitudePhone", latitudePhone);
            postdata.put("NameIsp", nameIsp);
            postdata.put("IP", ip);
            postdata.put("HostLocation", hostLocation);
            postdata.put("username", "kkkkk");
            postdata.put("password", "cuskdtj");


        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(MEDIA_TYPE, postdata.toString());

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String mMessage = e.getMessage().toString();
                Log.d("failure Response___", mMessage);
                //call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                String mMessage = response.body().string();
                Log.d("failure_____", mMessage);

            }
        });


    }

}
