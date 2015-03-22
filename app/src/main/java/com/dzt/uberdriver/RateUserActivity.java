package com.dzt.uberdriver;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class RateUserActivity extends ActionBarActivity implements View.OnClickListener, RatingBar.OnRatingBarChangeListener {

    RatingBar ratingBar;
    Button acceptButton;
    TextView ratingText;
    boolean ratingChanged = false;
    String rideId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate_user);

        assignViews();

    }

    private void assignViews()
    {
        ratingBar = (RatingBar) findViewById(R.id.rating_bar);
        ratingBar.setOnRatingBarChangeListener(this);

        ratingText = (TextView) findViewById(R.id.rating_label);

        acceptButton = (Button) findViewById(R.id.accept_rating_button);
        acceptButton.setOnClickListener(this);

        rideId = getIntent().getExtras().getString("rideId");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_rate_user, menu);
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
                break;
            case R.id.accept_rating_button:
                if(ratingChanged)
                {
                    sendDriverRating();
                }
                else
                {
                    ratingText.setError("Please rate your client");
                }
                break;
        }
    }

    @Override
    public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
        ratingChanged = true;
    }

    private void sendDriverRating()
    {
        float ratingStars = ratingBar.getRating();
        int ratingInt = (int) ratingStars;

        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.ip));
        sb.append("uber/ride/rating/driver?rideid=");
        sb.append(rideId);
        sb.append("&rating=");
        sb.append(ratingInt);

        URLpetition petition = new URLpetition("send driver rating");
        petition.execute(sb.toString());
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
                return stringBuilder.toString();

            }
            catch(IOException e) {
                Log.d("Error: ", e.getMessage());
            }
            Log.d("Return text = ", retorno);
            return retorno;
        }

        @Override
        protected void onPostExecute(String result) {
            switch (action)
            {
                default:
                    break;
                case "send driver rating":
                    if (result.equals("Rating sent"))
                    {
                        finishRide();
                    }
                    break;
            }
        }

        @Override
        protected void onPreExecute() {}
    }

    private void finishRide()
    {
        Intent intent = new Intent(this, MapActivity.class);
        startActivity(intent);
        finish();
    }
}
