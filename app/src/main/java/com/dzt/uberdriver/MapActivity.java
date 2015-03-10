package com.dzt.uberdriver;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogRecord;


public class MapActivity extends ActionBarActivity implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, GoogleMap.OnMarkerClickListener{

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    String SENDER_ID = "1050416204779";

    static final String TAG = "GCMDemo";

    TextView mDisplay;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    SharedPreferences prefs;
    Context context;

    String regid;


    private GoogleMap map;
    private GoogleApiClient mGoogleApiClient;
    boolean syncWithServer=true;
    double latitude, longitude;
    String rideid;
    Button acceptbutton;
    Polyline polylineroute;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);


        map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();

        map.setMyLocationEnabled(true);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.setOnMarkerClickListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

        /*
        context = getApplicationContext();

        if(checkPlayServices())
        {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);
            Log.d("Registration id = ", regid);

            if (regid.isEmpty()) {
                registerInBackground();
            }
        }
        else
        {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
        */
    }


    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the registration ID in your app is up to you.
        return getSharedPreferences(MapActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the registration ID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                //mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);

    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    private void sendRegistrationIdToBackend() {
        // Your implementation here.
        Log.d("reg id", regid);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    private boolean checkPlayServices()
    {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i("Tag", "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null)
        {
            latitude = mLastLocation.getLatitude();
            longitude = mLastLocation.getLongitude();

            LatLng latLng = new LatLng(latitude, longitude);

            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));

            map.animateCamera(CameraUpdateFactory.zoomTo(16));
            map.addMarker(new MarkerOptions().position(latLng).title("You are here")); //.icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon_top)).anchor(0.5,0.5)

            Thread timer = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(true)
                    {
                        if (syncWithServer)
                        {
                            String response = getUberRequest();
                            threadMsg(response);

                        }

                        try
                        {
                            Thread.sleep(7000);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

                private void threadMsg(String msg) {
                    if (!msg.equals(null) && !msg.equals("")) {
                        Message msgObj = handler.obtainMessage();
                        Bundle b = new Bundle();
                        b.putString("message", msg);
                        msgObj.setData(b);
                        handler.sendMessage(msgObj);
                    }
                }

                private final Handler handler = new Handler() {

                    public void handleMessage(Message msg) {

                        String aResponse = msg.getData().getString("message");

                        if ((null != aResponse)) {
                            addMarkers(aResponse);
                        }
                        else
                        {
                            Toast.makeText(getBaseContext(),"Not Got Response From Server.",Toast.LENGTH_SHORT).show();
                        }
                    }
                };
            });
            timer.start();

        }
        else
        {
            Toast.makeText(this, "Could not retrieve your location", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onClick(View v) {
        switch(v.getId())
        {
            default:
            case R.id.acceptbutton:
                    // TODO remove pending request
                break;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public String getUberRequest()
    {
        HttpClient client = new DefaultHttpClient();
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("uber/request/get?latitude=");
        sb.append(latitude);
        sb.append("&longitude=");
        sb.append(longitude);
        sb.append("&radius=0.005");
        HttpGet get = new HttpGet(sb.toString());
        String retorno = "";
        StringBuilder stringBuilder = new StringBuilder();
        try {
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            //InputStream stream = new InputStream(entity.getContent(),"UTF-8");
            InputStream stream = entity.getContent();
            BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                stringBuilder.append(line);
            }
            //threadMsg(stringBuilder.toString());
            //Thread.sleep(7000);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    public void addMarkers(String response)
    {
        try
        {
            JSONArray array = new JSONArray(response);
            int l = array.length();
            map.clear();

            map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("You are here"));

            for(int i = 0; i < l; i++)
            {
                JSONObject client = array.getJSONObject(i);
                double latitude = client.getDouble("user_lat");
                double longitude = client.getDouble("user_lon");
                String clientid = client.getString("user_id");
                String rideid = client.getString("pending_ride_id");
                map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title(clientid).icon(BitmapDescriptorFactory.fromResource(R.drawable.client_icon)).snippet(rideid));
            }
        }
        catch(JSONException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if(syncWithServer)
        {
            String id = marker.getSnippet();
            double userLat = marker.getPosition().latitude;
            double userLon = marker.getPosition().longitude;
            //Toast.makeText(this, "Pending ride id = "+id, Toast.LENGTH_SHORT).show();

            URLpetition petition = new URLpetition("get shortest time");
            StringBuilder sb = new StringBuilder();
            sb.append("http://maps.googleapis.com/maps/api/directions/json?origin=");
            sb.append(latitude);
            sb.append(",");
            sb.append(longitude);
            sb.append("&destination=");
            sb.append(userLat);
            sb.append(",");
            sb.append(userLon);
            sb.append("&mode=driving&sensor=false");
            petition.execute(sb.toString());

            showAcceptButton(id);
            syncWithServer = false;
        }
        else
        {
            syncWithServer = true;
            acceptbutton.setVisibility(View.INVISIBLE);
            polylineroute.remove();
        }

        return true;
    }

    private class URLpetition extends AsyncTask<String, Void, String>
    {
        String action;
        public URLpetition(String action)
        {
            this.action = action;
        }
        @Override
        protected String doInBackground(String... params) {
            HttpClient client = new DefaultHttpClient();
            Log.d("url = ", params[0]);
            HttpGet get = new HttpGet(params[0]);
            String retorno="";
            StringBuilder stringBuilder = new StringBuilder();
            try {
                HttpResponse response = client.execute(get);
                HttpEntity entity = response.getEntity();
                //InputStream stream = new InputStream(entity.getContent(),"UTF-8");
                InputStream stream = entity.getContent();
                BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                String line;
                while ((line= r.readLine()) != null) {
                    stringBuilder.append(line);
                }

                if(action.equals("get shortest time"))
                {
                    return stringBuilder.toString();
                }
            }
            catch(IOException e) {
                Log.d("Error: ", e.getMessage());
            }
            Log.d("Return text = ", retorno);
            return retorno;
        }

        @Override
        protected void onPostExecute(String result) {
            if (action.equals("get shortest time"))
            {
                try
                {
                    ArrayList<LatLng> geopoints = new ArrayList<LatLng>();

                    JSONObject jsonObject = new JSONObject(result);
                    int shortestTimeTemp = jsonObject.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("duration").getInt("value");
                    JSONArray steps = jsonObject.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
                    int stepscount = steps.length();
                    geopoints.add(new LatLng(steps.getJSONObject(0).getJSONObject("start_location").getDouble("lat"),steps.getJSONObject(0).getJSONObject("start_location").getDouble("lng")));
                    for (int i = 0; i<stepscount; i++)
                    {
                        double lat = steps.getJSONObject(i).getJSONObject("end_location").getDouble("lat");
                        double lng = steps.getJSONObject(i).getJSONObject("end_location").getDouble("lng");
                        geopoints.add(new LatLng(lat, lng));
                    }
                    drawRoute(geopoints);
                    showMSG("Time to destination: " + shortestTimeTemp + " seconds");
                }
                catch(JSONException e)
                {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void onPreExecute() {}
    }
    private void showMSG(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showAcceptButton(String rideid)
    {
        acceptbutton = (Button)findViewById(R.id.acceptbutton);
        acceptbutton.setVisibility(View.VISIBLE);
        acceptbutton.setOnClickListener(this);
        this.rideid = rideid;
    }

    private void drawRoute(ArrayList<LatLng> geopoints)
    {
        PolylineOptions rectLine = new PolylineOptions().width(10).color(Color.RED);
        for (int i = 0; i < geopoints.size(); i++) {
            rectLine.add(geopoints.get(i));
        }
        polylineroute = map.addPolyline(rectLine);
    }
}
