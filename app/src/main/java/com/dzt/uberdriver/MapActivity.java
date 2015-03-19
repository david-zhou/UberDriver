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
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
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


public class MapActivity extends ActionBarActivity implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, GoogleMap.OnMarkerClickListener, LocationListener{

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private GoogleMap map;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest locationRequest;
    boolean syncWithServer=true, onRide = false;
    double latitude, longitude;
    String rideid, currentrideid, driverid, selectedmarker = "";
    Button acceptbutton, beginridebutton, endridebutton;
    Polyline polylineroute = null;
    Location currentLocation;
    LatLng lastPointOnRoute;
    float rideDistance;
    ArrayList<Marker> markers = new ArrayList<Marker>();

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
        createLocationRequest();
        mGoogleApiClient.connect();

        SharedPreferences sp = getSharedPreferences("Session", MODE_PRIVATE);
        driverid = sp.getString("driver_id", "");
    }

    protected void createLocationRequest()
    {
        locationRequest = new LocationRequest();

        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
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
    public void onLocationChanged(Location location) {
        currentLocation = location;

        if(onRide)
        {
            PolylineOptions line = new PolylineOptions().width(5).color(Color.BLUE);
            line.add(lastPointOnRoute);
            line.add(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));

            float [] results = new float[1];
            Location.distanceBetween(lastPointOnRoute.latitude, lastPointOnRoute.longitude, currentLocation.getLatitude(), currentLocation.getLongitude(), results);
            rideDistance += results[0];
            lastPointOnRoute = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            Polyline polyline = map.addPolyline(line);
        }
        else
        {
            StringBuilder url = new StringBuilder();
            url.append(getResources().getString(R.string.ip));
            url.append("drivers/position/update?driverid=");
            url.append(driverid);
            url.append("&latitude=");
            url.append(location.getLatitude());
            url.append("&longitude=");
            url.append(location.getLongitude());

            URLpetition petition = new URLpetition("update driver position");
            petition.execute(url.toString());

        }
    }

    private void beginSyncThread()
    {
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

    @Override
    public void onConnected(Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,locationRequest, this);
        if (mLastLocation != null)
        {
            currentLocation = mLastLocation;
            latitude = mLastLocation.getLatitude();
            longitude = mLastLocation.getLongitude();

            LatLng latLng = new LatLng(latitude, longitude);

            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));

            map.animateCamera(CameraUpdateFactory.zoomTo(16));
            //map.addMarker(new MarkerOptions().position(latLng).title("You are here")); //.icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon_top)).anchor(0.5,0.5)
            beginSyncThread();
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


                StringBuilder sb = new StringBuilder();
                sb.append(getResources().getString(R.string.ip));
                sb.append("uber/request/accept?driverid=");
                sb.append(driverid);
                sb.append("&pendingrequestid=");
                sb.append(rideid);

                URLpetition petition = new URLpetition("accept uber request");
                petition.execute(sb.toString());
                acceptbutton.setVisibility(View.INVISIBLE);

                removeClients();

                break;

            case R.id.beginridebutton:

                enableEndRideButton();
                beginRide();
                drawRideRoute();
                polylineroute.remove();
                polylineroute = null;
                // TODO add route to destination

                break;

            case R.id.endridebutton:

                onRide = false;
                endRide();
                break;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private void removeClients()
    {
        int l = markers.size();
        for (int i = 0; i < l; i++)
        {
            if(!markers.get(i).getId().equals(selectedmarker))
            {
                markers.get(i).remove();
            }
        }
    }

    void beginRide()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("uber/ride/begin?rideid=");
        sb.append(currentrideid);
        sb.append("&latitude=");
        sb.append(currentLocation.getLatitude());
        sb.append("&longitude=");
        sb.append(currentLocation.getLongitude());

        URLpetition petition = new URLpetition("begin ride");
        petition.execute(sb.toString());
    }

    void endRide()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("uber/ride/end?rideid=");
        sb.append(currentrideid);
        sb.append("&latitude=");
        sb.append(currentLocation.getLatitude());
        sb.append("&longitude=");
        sb.append(currentLocation.getLongitude());
        sb.append("&distance=");
        sb.append(rideDistance);


        URLpetition petition = new URLpetition("end ride");
        petition.execute(sb.toString());
    }

    void drawRideRoute()
    {
        lastPointOnRoute = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        onRide = true;
        rideDistance = 0;
    }

    public String getUberRequest()
    {
        HttpClient client = new DefaultHttpClient();
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("uber/request/get?latitude=");
        sb.append(currentLocation.getLatitude());
        sb.append("&longitude=");
        sb.append(currentLocation.getLongitude());
        sb.append("&radius=0.005");
        HttpGet get = new HttpGet(sb.toString());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            InputStream stream = entity.getContent();
            BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                stringBuilder.append(line);
            }
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
            markers.clear();
            //map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("You are here"));

            for(int i = 0; i < l; i++)
            {
                JSONObject client = array.getJSONObject(i);
                double latitude = client.getDouble("user_lat");
                double longitude = client.getDouble("user_lon");
                String clientid = client.getString("user_id");
                String rideid = client.getString("pending_ride_id");
                Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title(clientid).icon(BitmapDescriptorFactory.fromResource(R.drawable.client_icon)).snippet(rideid));
                markers.add(marker);
            }
        }
        catch(JSONException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if(!marker.getId().equals(selectedmarker))
        {
            String id = marker.getSnippet();
            double userLat = marker.getPosition().latitude;
            double userLon = marker.getPosition().longitude;
            //Toast.makeText(this, "Pending ride id = "+id, Toast.LENGTH_SHORT).show();

            URLpetition petition = new URLpetition("get shortest time");
            StringBuilder sb = new StringBuilder();
            sb.append("http://maps.googleapis.com/maps/api/directions/json?origin=");
            sb.append(currentLocation.getLatitude());
            sb.append(",");
            sb.append(currentLocation.getLongitude());
            sb.append("&destination=");
            sb.append(userLat);
            sb.append(",");
            sb.append(userLon);
            sb.append("&mode=driving&sensor=false");
            petition.execute(sb.toString());

            showAcceptButton(id);
            syncWithServer = false;
            selectedmarker = marker.getId();
        }
        else
        {
            selectedmarker = "";
            syncWithServer = true;
            acceptbutton.setVisibility(View.INVISIBLE);
            if(polylineroute != null)
            {
                polylineroute.remove();
                polylineroute = null;
            }
        }
        return true;
    }

    private void showMSG(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void postAcceptUberRequest(String json)
    {
        if (json.equals("{request id not found}"))
        {
            showMSG("Request taken by other");
        }
        else
        {
            showMSG("On my way");
            try
            {
                JSONObject jsonObject = new JSONObject(json);
                String rideid = jsonObject.getString("ride_id");
                setRideId(rideid);
            }
            catch(JSONException e)
            {
                e.printStackTrace();
            }
            enableBeginRideButton();
        }
    }

    private void postUpdateDriverPosition(String json)
    {

    }

    private void enableUber()
    {

    }


    private void postBeginRide(String json)
    {

    }

    private void getGeoPoints(String json)
    {
        try
        {
            ArrayList<LatLng> geopoints = new ArrayList<LatLng>();

            JSONObject jsonObject = new JSONObject(json);
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

    private void showAcceptButton(String rideid)
    {
        acceptbutton = (Button)findViewById(R.id.acceptbutton);
        acceptbutton.setVisibility(View.VISIBLE);
        acceptbutton.setOnClickListener(this);
        this.rideid = rideid;
    }

    private void drawRoute(ArrayList<LatLng> geopoints)
    {
        if(polylineroute != null)
        {
            polylineroute.remove();
        }
        PolylineOptions rectLine = new PolylineOptions().width(10).color(Color.RED);
        for (int i = 0; i < geopoints.size(); i++) {
            rectLine.add(geopoints.get(i));
        }
        polylineroute = map.addPolyline(rectLine);
    }

    private void enableBeginRideButton()
    {
        beginridebutton = (Button) findViewById(R.id.beginridebutton);
        beginridebutton.setOnClickListener(this);
        beginridebutton.setVisibility(View.VISIBLE);
        acceptbutton.setVisibility(View.INVISIBLE);
    }

    private void enableEndRideButton()
    {
        endridebutton = (Button) findViewById(R.id.endridebutton);
        endridebutton.setOnClickListener(this);
        endridebutton.setVisibility(View.VISIBLE);
        beginridebutton.setVisibility(View.INVISIBLE);
    }

    private void setRideId(String id)
    {
        this.currentrideid = id;
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
                return stringBuilder.toString();

            }
            catch(IOException e) {
                Log.d("Error: ", e.getMessage());
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {

            switch(action)
            {
                default:
                    break;
                case "get shortest time":
                    getGeoPoints(result);
                    break;
                case "accept uber request":
                    postAcceptUberRequest(result);
                    break;
                case "update driver position":
                    postUpdateDriverPosition(result);
                    break;
                case "begin ride":
                    postBeginRide(result);
                    break;
                case "end ride":
                    enableUber();
                    break;
            }
        }

        @Override
        protected void onPreExecute()
        {

        }
    }
}
