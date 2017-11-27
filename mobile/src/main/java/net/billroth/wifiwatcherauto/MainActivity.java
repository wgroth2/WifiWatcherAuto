package net.billroth.wifiwatcherauto;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private LocationManager locationManager;
    private ConnectivityManager connectivityManager;
    private boolean isConnected = false;
    private boolean isReceiverRegistered = false;
    private String appName;
    private String currentSSID,lastSSID;
    private long minGPSScanTime = 5000;
    private double latestLongitude = 0.0;
    private double latestLatitude = 0.0;
    private double latestAltitude = 0.0;
    private double lastLong, lastLat, lastAlt = 0.0;
    private boolean locationValid = false;

    //
    // http://blog.appliedinformaticsinc.com/sending-json-data-to-server-using-async-thread/
    //

    //add background inline class here
    private class SendJsonDataToServer extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {
            String JsonResponse;
            String JsonDATA = params[0];

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            try {
                // Consider making a string.
                URL url = null;
                try {
                    url = new URL("http://billroth.net/json/test.php");
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

                try {
                    if (url != null) {
                        urlConnection = (HttpURLConnection) url.openConnection();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
                urlConnection.setDoOutput(true);
                // is output buffer writter
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");
                //set headers and method
                Writer writer = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8"));
                writer.write(JsonDATA);
                // json data
                writer.close();
                InputStream inputStream = urlConnection.getInputStream();
                //input stream
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                //
                // Read results
                //
                String inputLine;
                while ((inputLine = reader.readLine()) != null)
                    buffer.append(inputLine + "\n");
                if (buffer.length() == 0) {
                    // Stream was empty. No point in parsing.
                    return null;
                }
                JsonResponse = buffer.toString();

                //response data
                Log.i(appName,JsonResponse);

                //send to post execute
                return JsonResponse;

            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(appName, "Error closing stream", e);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
        }

    }
    //
    // TODO Finish send inclde async methods
    // http://blog.appliedinformaticsinc.com/sending-json-data-to-server-using-async-thread/
    //
    private void sendJSON(double longitude, double lat, double alt, String SSID) {
        String sLong, sLat, sAlt;
        sLong = (Double.valueOf(longitude)).toString();
        sLat = (Double.valueOf(lat)).toString();
        sAlt = (Double.valueOf(alt)).toString();

        JSONObject js = new JSONObject();
        try {
            js.put("longitude", sLong);
            js.put("latitude", sLat);
            js.put("altitude", sAlt);
            js.put("SSID", SSID);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
            if (js.length() > 0) {
                new SendJsonDataToServer().execute(String.valueOf(js));
            }
        }
    //
    // TODO: send JSON to server here.
    // Send info to server/queue on when/where/SSID
    //
    private void recordLocation() {

        if(latestAltitude == lastAlt && latestLatitude == lastLat && latestLongitude == lastLong && lastSSID.equals(currentSSID)) { // if its the same as the last one, do nothing
            int x=0;
            x++;
        } else {
            // excellent example: http://hmkcode.com/android-send-json-data-to-server/
            SimpleDateFormat s = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss", Locale.US);
            String date_format = s.format(new Date());
            String msg = "Record location: Long: " + latestLongitude + " Lat: " + latestLatitude + " Alt: " + latestAltitude + " current SSID: " + currentSSID + " @ " + date_format;

            Log.d(appName, "Before send: " + msg);
            sendJSON(latestLongitude, latestLatitude, latestAltitude, currentSSID);
            setStatus(msg);
            Log.d(appName, "After send " + msg);
            lastAlt = latestAltitude;
            lastLong = latestLongitude;
            lastLat = latestLatitude;
            lastSSID = currentSSID;
        }
    }

    //
    // Set up broadcast receiver for install/removal in onResume/onPause
    //
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            getResources().getString(R.string.log_app_name);
            Log.d(appName, "getNetworkInfo: OnReceive");
            NetworkInfo info = getNetworkInfo(context);
            if (info != null && info.isConnected()) {
                isConnected = true;
                Log.d(appName, "Network is connected");
                setStatus("Network connected");
                TextView wifiText = findViewById(R.id.wifi_state_text);
                currentSSID = info.getExtraInfo();
                String mess = "SSID = " + currentSSID;
                wifiText.setText(mess);

            } else {
                isConnected = false;
                Log.d(appName, "Network is disconnected");
                String mess = getResources().getString(R.string.disconnected);
                TextView wifiText = findViewById(R.id.wifi_state_text);
                wifiText.setText(mess);
                setStatus("Network disconnected");
                currentSSID = null;
            }
            if (isConnected && locationValid)
                recordLocation();
        }
    };


    private NetworkInfo getNetworkInfo(Context context) {
        ConnectivityManager connManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            return connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        }else return null;
    }

    //
    // TODO: Set up spinner
    //
    private long setUpSpinner() {
        return 5;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar =  findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        appName = getResources().getString(R.string.log_app_name);

        //
        // Set up spinner
        //
        minGPSScanTime = setUpSpinner();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        //
        // todo: figure out preferences
        //
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
        // ********** get Gps location service LocationManager object ***********/
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);



        if (null != locationManager) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            // Parameters :
            //   First(provider)    :  the name of the provider with which to register
            //   Second(minTime)    :  the minimum time interval for notifications,
            //                         in milliseconds. This field is only used as a hint
            //                         to conserve power, and actual time between location
            //                         updates may be greater or lesser than this value.
            //   Third(minDistance) :  the minimum distance interval for notifications, in meters
            //   Fourth(listener)   :  a {#link LocationListener} whose onLocationChanged(Location)
            //                         method will be called for each location update
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    minGPSScanTime,   // 5 sec
                    20, this);
        }
    }

    protected void setStatus(String msg) {
        TextView status = findViewById(R.id.statusView);
        status.setText(msg);

    }

    @Override
    public void onLocationChanged(Location location) {
        String str = "Latitude: "+location.getLatitude()+" Longitude: "+location.getLongitude();
        locationValid = true;

        int d = Log.d(appName, "location changed");

        TextView longText = findViewById(R.id.long_field1);
        TextView latText = findViewById(R.id.lat_field1);
        TextView altText = findViewById(R.id.alt_field1);

        longText.setText(String.valueOf( latestLongitude = location.getLongitude()));
        latText.setText(String.valueOf( latestLatitude = location.getLatitude()));
        altText.setText(String.valueOf( latestAltitude = location.getAltitude()));

        setStatus(str);
        if(isConnected && locationValid)
            recordLocation();
    }

    /**
     * Called when the provider status changes. This method is called when
     * a provider is unable to fetch a location or if the provider has recently
     * become available after a period of unavailability.
     *
     * @param provider the name of the location provider associated with this
     *                 update.
     * @param status   {@link LocationProvider#OUT_OF_SERVICE} if the
     *                 provider is out of service, and this is not expected to change in the
     *                 near future; {@link LocationProvider#TEMPORARILY_UNAVAILABLE} if
     *                 the provider is temporarily unavailable but is expected to be available
     *                 shortly; and {@link LocationProvider#AVAILABLE} if the
     *                 provider is currently available.
     * @param extras   an optional Bundle which will contain provider specific
     *                 status variables.
     *                 <p>
     *                 <p> A number of common key/value pairs for the extras Bundle are listed
     *                 below. Providers that use any of the keys on this list must
     *                 provide the corresponding value as described below.
     *                 <p>
     *                 <ul>
     *                 <li> satellites - the number of satellites used to derive the fix
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        int i = Log.d(appName,"Status changed, status = " + status);
    }

    /**
     * Called when the provider is enabled by the user.
     *
     * @param provider the name of the location provider associated with this
     *                 update.
     */
    @Override
    public void onProviderEnabled(String provider) {
        setStatus("Gps turned on ");
        Log.d(appName, "GPS Turned on");
    }

    /**
     * Called when the provider is disabled by the user. If requestLocationUpdates
     * is called on an already disabled provider, this method is called
     * immediately.
     *
     * @param provider the name of the location provider associated with this
     *                 update.
     */
    @Override
    public void onProviderDisabled(String provider) {
        setStatus("Gps turned off ");
        Log.d(appName, "GPS Turned off");
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        //
        // todo: Process preferences Bundle
        //
        super.onResume();
        if (!isReceiverRegistered) {
            isReceiverRegistered = true;
            registerReceiver(receiver, new IntentFilter("android.net.wifi.STATE_CHANGE"));
            // IntentFilter to wifi state change is "android.net.wifi.STATE_CHANGE"
        }
    }
    //
    // TODO: Deal with pause resume: https://developer.android.com/guide/components/activities/activity-lifecycle.html#saras
    //
    @Override
    protected void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            isReceiverRegistered = false;
            unregisterReceiver(receiver);
        }
    }
    @Override
    protected void onStop() {
        super.onStop();  // Always call the superclass method first
    }
//
    // For Completeness
    //
    @Override
    protected void onDestroy() {
        super.onDestroy();  // Always call the superclass method first
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
            startActivity(new Intent(this,SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
