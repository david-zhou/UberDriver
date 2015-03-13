package com.dzt.uberdriver;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

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


public class MainActivity extends ActionBarActivity implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    Button login;
    EditText username;
    private GoogleApiClient mGoogleApiClient;
    double latitude, longitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LocationManager locationManager =(LocationManager) getSystemService(LOCATION_SERVICE);
        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();

            mGoogleApiClient.connect();
        }
        else
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }

    }

    @Override
    public void onDestroy()
    {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
    public void onClick(View v) {
        switch(v.getId())
        {
            default:
            case R.id.login:
                URLpetition petition = new URLpetition("login driver");

                StringBuilder sb = new StringBuilder();
                sb.append(getResources().getString(R.string.ip));
                sb.append("drivers/login?id=");
                sb.append(username.getText());
                sb.append("&latitude=");
                sb.append(latitude);
                sb.append("&longitude=");
                sb.append(longitude);

                petition.execute(sb.toString());
                break;
        }
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

                if(action.equals("login driver"))
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
            if (action.equals("login driver"))
            {
                if(result.equals("id not valid"))
                {
                    showMSG("ID not valid");
                }
                else
                {
                    //showMSG(result);
                    try
                    {
                        JSONObject jsonObject = new JSONObject(result);
                        String name, lastname, vehicle;
                        name = jsonObject.getString("name");
                        lastname = jsonObject.getString("last_name");
                        vehicle = jsonObject.getString("vehicle");
                        saveSession(name, lastname, vehicle);

                    }
                    catch(JSONException e)
                    {
                        e.printStackTrace();
                    }
                }

            }
        }

        @Override
        protected void onPreExecute() {}
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        latitude = mLastLocation.getLatitude();
        longitude = mLastLocation.getLongitude();

        login = (Button) findViewById(R.id.login);
        username = (EditText) findViewById(R.id.username);

        login.setOnClickListener(this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public void showMSG(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public void saveSession(String name, String lastname, String vehicle)
    {
        SharedPreferences sp = getSharedPreferences("Session", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        editor.putString("name", name);
        editor.putString("driver_id",username.getText().toString());
        editor.putString("lastname", lastname);
        editor.putString("vehicle", vehicle);
        editor.commit();

        //showMSG("Welcome "+name + " " + lastname);
        startActivity(new Intent(this, MapActivity.class));
        this.finish();
    }
}
