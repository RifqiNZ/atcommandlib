package com.atcommandlib2;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

interface command {
void time (Context context);
}

public class SendData extends FragmentActivity implements command, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    TelephonyManager tm;

    //signal
    int biarGCounter=0;
    ArrayList<String> dataFullCell;

    //init Google Client
    private GoogleApiClient googleApiClient;
    private final static int REQUEST_CHECK_SETTINGS_GPS = 0x1;
    private final static int REQUEST_ID_MULTIPLE_PERMISSIONS = 0x2;
    private Location mylocation;

    //InitTime

    private PendingIntent pendingIntent;
    private static final int ALARM_REQUEST_CODE = 134;
    private int setJam = 3;
    //
    String ping, jitter,download, upload, longitudePhone,latitudePhone,nameIsp,ip,hostLocation;

    //initData
    static int position = 0;
    static int lastPosition = 0;
    GetSpeedTestHostsHandler getSpeedTestHostsHandler = null;
    HashSet<String> tempBlackList;
    //
    Context contextDuplicate=null;

    public void time (Context context){
        Intent alarmIntent = new Intent(context, AppReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, alarmIntent, 0);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 10);
//        Log.d("Data Running___","OKTIME");
        //cal.set(Calendar.MINUTE,30);
        AlarmManager alarmManager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);

        //setupGoogleGPSON
        contextDuplicate=context;
        setUpGClient(contextDuplicate);
    }

    public void run(){
        tempBlackList = new HashSet<>();
        Log.d("Data Running___","OKTIMErun");

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
    public void dataCellID(){

    }
    private synchronized void setUpGClient(Context context) {
        googleApiClient = new GoogleApiClient.Builder(context)
                .enableAutoManage(this, 0, this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        checkPermissions();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (biarGCounter==0){
            LocationManager lm = (LocationManager) getBaseContext().getSystemService(Context.LOCATION_SERVICE);
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                List<CellInfo> cellInfoList = tm.getAllCellInfo();
                String cellID=String.valueOf(cellInfoList.get(0));
                String[] dataCell=cellID.split(" ");

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    String signalStrength= String.valueOf(tm.getSignalStrength());
                    String[] signalData= signalStrength.split(" ");
                    //rssi
                    dataFullCell.add(signalData[1].split("=")[1]);
                    //rsrp
                    dataFullCell.add(signalData[2].split("=")[1]);
                    //rsrq
                    dataFullCell.add(signalData[3].split("=")[1]);
                    //rssnr
                    dataFullCell.add(signalData[4].split("=")[1]);
                    //cqi
                    dataFullCell.add(signalData[5].split("=")[1]);
                    //ta
                    dataFullCell.add(signalData[6].split("=")[1]);
                    //levelSignal
                    dataFullCell.add(signalData[8].split("=")[1]);
//                        int signalIndex=0;
//                        for (String sD : signalData){
//                            signalIndex++;
//                            Log.d("cellSignalData",sD);
//                        }
                }

                //mCi
                dataFullCell.add(dataCell[4].split("=")[1]);
                //mPCi
                dataFullCell.add(dataCell[5].split("=")[1]);
                //mTac
                dataFullCell.add(dataCell[6].split("=")[1]);
                //mearfcn
                dataFullCell.add(dataCell[7].split("=")[1]);
                //mbandwidth
                dataFullCell.add(dataCell[8].split("=")[1]);
                //mMcc
                dataFullCell.add(dataCell[9].split("=")[1]);
                //mMnc
                dataFullCell.add(dataCell[10].split("=")[1]);
                //ISP
                dataFullCell.add(dataCell[11].split("=")[1]);
//                    int x= 0;
//                for (String da : data){
//                    x++;
//                    Log.d("cellData",da);
//                    if (x==data.length){
//                        break;
//                    }
//                }
//                    Intent intent= new Intent(MainActivity.this,sendData.class);
//                    intent.putExtra("data",dataFullCell);
//                    startActivity(intent);
                Log.d("DataCel", String.valueOf(dataFullCell));
            }
            biarGCounter++;
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }
    //auto gps
    private void checkPermissions() {
        int permissionLocation = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (permissionLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
//                List<CellInfo> cellInfoList = tm.getAllCellInfo();
//                Log.d("cellInfo", String.valueOf(cellInfoList.get(0)));
            }
        } else {
            getMyLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//    if (requestCode== 1){
//        if (grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED){
//Toast.makeText(this,"permission grnated",Toast.LENGTH_LONG).show();
//        }
//        else{
//            Toast.makeText(this,"permission denied",Toast.LENGTH_LONG).show();
//        }
//    }
        int permissionLocation = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionLocation == PackageManager.PERMISSION_GRANTED) {
            getMyLocation();
        }
    }

    public void getMyLocation() {

        if (googleApiClient != null) {
            if (googleApiClient.isConnected()) {
                int permissionLocation = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);
                if (permissionLocation == PackageManager.PERMISSION_GRANTED) {
                    mylocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                    LocationRequest locationRequest = new LocationRequest();
                    locationRequest.setInterval(3000);
                    locationRequest.setFastestInterval(3000);
                    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                            .addLocationRequest(locationRequest);
                    builder.setAlwaysShow(true);
                    LocationServices.FusedLocationApi
                            .requestLocationUpdates(googleApiClient, locationRequest, this);
                    PendingResult<LocationSettingsResult> result =
                            LocationServices.SettingsApi
                                    .checkLocationSettings(googleApiClient, builder.build());
                    result.setResultCallback(new ResultCallback<LocationSettingsResult>() {

                        @Override
                        public void onResult(LocationSettingsResult result) {
                            final Status status = result.getStatus();
                            switch (status.getStatusCode()) {
                                case LocationSettingsStatusCodes.SUCCESS:
                                    // All location settings are satisfied.
                                    // You can initialize location requests here.
                                    int permissionLocation = ContextCompat
                                            .checkSelfPermission(contextDuplicate,
                                                    Manifest.permission.ACCESS_FINE_LOCATION);
                                    if (permissionLocation == PackageManager.PERMISSION_GRANTED) {
                                        mylocation = LocationServices.FusedLocationApi
                                                .getLastLocation(googleApiClient);

                                    }

                                    break;
                                case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                    // Location settings are not satisfied.
                                    // But could be fixed by showing the user a dialog.
                                    try {
                                        // Show the dialog by calling startResolutionForResult(),
                                        // and check the result in onActivityResult().
                                        // Ask to turn on GPS automatically
                                        status.startResolutionForResult(SendData.this,
                                                REQUEST_CHECK_SETTINGS_GPS);
                                    } catch (IntentSender.SendIntentException e) {
                                        // Ignore the error.
                                    }
                                    break;
                                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                    // Location settings are not satisfied.
                                    // However, we have no way
                                    // to fix the
                                    // settings so we won't show the dialog.
                                    // finish();
                                    break;
                            }
                        }
                    });
                }
            }
        }
    }

}
