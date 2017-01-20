package erenik.accelerometersampler;

import android.*;
import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Handler;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionApi;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Ref src code for Google play services Activity recognition API
 https://github.com/googlesamples/android-play-location/tree/master/ActivityRecognition
 * https://github.com/googlesamples/android-play-location/tree/master/ActivityRecognition/app/src/main/java/com/google/android/gms/location/sample/activityrecognition
 */

public class SensorDisplayActivity
        extends AppCompatActivity
        implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status> {
    protected static final String TAG = "MainActivity";
    /**
     * A receiver for DetectedActivity objects broadcast by the
     * {@code ActivityDetectionIntentService}.
     */
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;


    static int timesClicked = 0;
    Handler h = new Handler(); // iteration handler
    int delay = 1000; // ms

    SensorManager sensorManager;
    Sensor sensor;
    GoogleApiClient mGoogleApiClient;


    // Default on, Start/Stop button to toggle it.
    boolean on = true;

    /**
     * Adapter backed by a list of DetectedActivity objects.
     */
    private DetectedActivitiesAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("Starting up AccelerometerSampler application");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_display);

        findViewById(R.id.button_Restart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorChanges = 0;
                accPoints.clear();
                locationSamples.clear();
            }
        });
        findViewById(R.id.button_StartStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on = !on;
                ((TextView) v).setText(on ? "Stop" : "Start");
            }
        });

        InitGoogleAPI();

        /// Set up Accelerometer sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensor, 10000000);

        /// Set up handler for iterated samplings
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Iterate();
            }
        }, delay);
    }

    /// Location code.
    LocationManager lMan;
    private class LocationData {
        LocationData(int timestampSystemSeconds, double longitude, double latitude) {
            this.timestampSystemSeconds = timestampSystemSeconds;
            this.longitude = longitude;
            this.latitude = latitude;
        }
        int timestampSystemSeconds; // System time ms?
        double longitude, latitude;
    };
    List<LocationData> locationSamples = new ArrayList<>();

    private void InitLocationUpdates() {
        System.out.println("InitLocationUpdates");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            System.out.println("Lacking permissions to request location updates.");
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, LocationRequest.create(), new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Add the data into the graph?
                System.out.println("Location changed: "+location.getLatitude()+" "+location.getLongitude());
                ((TextView)findViewById(R.id.textView_GPSLocation)).setText(location.getLatitude()+"lat "+location.getLongitude()+"long");
                locationSamples.add(new LocationData((int) (System.currentTimeMillis() / 1000), location.getLatitude(), location.getLongitude()));
                UpdateLocationGraph();
            }
        });

//        InitGPS();
    }

    void UpdateLocationGraph() {
        ArrayList<LineGraphSeries<DataPoint>> series = new ArrayList<>();
        LineGraphSeries<DataPoint> longitudeSeries = new LineGraphSeries<>(),
                latitudeSeries = new LineGraphSeries<>();

        GraphView graph = (GraphView) findViewById(R.id.graphGPS);
        graph.removeAllSeries(); // Clear old data.

        // use static labels for horizontal and vertical labels
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
        staticLabelsFormatter.setHorizontalLabels(new String[] {"old", "newest"});
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);

        Viewport vp = graph.getViewport();
//        vp.setYAxisBoundsManual(true); vp.setMaxY(bounds); vp.setMinY(-bounds);

        // Always reduce bounds to fit the view if possible.
        int maxPoints = 20;
        int firstIndex = (locationSamples.size() - maxPoints) > 0? locationSamples.size() - maxPoints : 0; // First index if using number of points.
        int sampleNumber = 0;
        int firstX = -1, lastX = -1;
        float largestAbsVal = 0;
        for (int i = firstIndex; i < locationSamples.size(); ++i)
        {
            LocationData ld = locationSamples.get(i);
            // timediff in nanosecs: sd.timestamp - firstTimeStamp
            int x = (int) ld.timestampSystemSeconds;
            if (firstX == -1)
                firstX = x;
            lastX = x;
            longitudeSeries.appendData(new DataPoint(x, ld.longitude), false, 1000, true);
            latitudeSeries.appendData(new DataPoint(x, ld.latitude), false, 1000, true);
        }
        // Move towards the highest value + 1 always?
        bounds = 0.95f * bounds + 0.05f * (largestAbsVal + 3);
  //      vp.setXAxisBoundsManual(true);
    //    vp.setMinX(firstX); // Set min/max X
      //  vp.setMaxX(lastX);
        // 0 to 25, not being displayed...
        TextView tv = (TextView) findViewById(R.id.textView_Bounds);
        tv.setText("firstX: "+firstX+" lastX: "+lastX+" indices added: "+(accPoints.size() - firstIndex));

        longitudeSeries.setColor(0xFFFF0000);
        latitudeSeries.setColor(0xFF00FF00);
        graph.addSeries(longitudeSeries);
        graph.addSeries(latitudeSeries);
    }

    void InitGPS()
    {
        // Android non-google Location
        lMan = (LocationManager) getBaseContext().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            return;
        }
        lMan.addNmeaListener(new GpsStatus.NmeaListener() {
            @Override
            public void onNmeaReceived(long l, String s) {
                System.out.println("Long: " + l + " s: " + s);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        lMan.addNmeaListener(new GpsStatus.NmeaListener() {
            @Override
            public void onNmeaReceived(long l, String s) {
                System.out.println("Long: " + l + " s: " + s);
            }
        });

    }

    synchronized void InitGoogleAPI()
    {
        System.out.println("Init google API for activity recognition.");
        Context context = getBaseContext();

        // Set up Google Play Services for Activity regognition?
        mGoogleApiClient  =  new GoogleApiClient.Builder(context)
                .addApi(ActivityRecognition.API) // For Activity recognition.
                .addApi(LocationServices.API) // For location services
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect(); // Start services upon successful connection.
    }

    /// Handle changes to the Google Services API connection.
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        System.out.println("onConnected");
        System.out.println("- connected? "+mGoogleApiClient.isConnected());
        requestActivityUpdates(); // Request Google activity recognition
        InitLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.out.println("onConnectionSuspended ");

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        System.out.println("onConnectionFailed");
    }


    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Request activity updates
    public void requestActivityUpdates() {
        if (!mGoogleApiClient.isConnected()) {
            System.out.println("requestActivityUpdates - Not connected");
            Toast.makeText(this, "Not connected.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        System.out.println("Request activity updates");
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient,
                Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    int iterated = 0;
    public void Iterate() {
        /// Update text
        TextView tv = (TextView) findViewById(R.id.PrimaryTextView);
        tv.setText("tc "+timesClicked+" iterated: "+iterated++);
        /// Query next sampling.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Iterate();
            }
        }, delay);
    }

    /// Application state changes - reconnect/disconnect google services and other sensors?
    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the broadcast receiver that informs this activity of the DetectedActivity
        // object broadcast sent by the intent service.
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(Constants.BROADCAST_ACTION));
    }

    @Override
    protected void onPause() {
        // Unregister the broadcast receiver that was registered during onResume().
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_sensor_display, menu);
        return true;
    }

    int sensorChanges = 0;
    private class SensorData
    {
        long timestamp = 0;
        float[] values = new float[3];
    }

    /// All stored accelerometer-sensor points.
    ArrayList<SensorData> accPoints = new ArrayList<>();
    long lastGraphUpdateMs = System.currentTimeMillis();

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (!on)
            return;

        float[] values = event.values; // Update TextViews
        String text = "";
        for (int i = 0; i < values.length; ++i)
        {
            int integral = (int)values[i];
            text = text + integral + " ";
        }
        TextView sensorText = (TextView) findViewById(R.id.textView_AccelerometerValues);
        sensorText.setText(""+text+"  sample "+sensorChanges++);

        SensorData newSensorData = new SensorData(); // Copy over data from Android data to own class type.
        newSensorData.timestamp = event.timestamp; // Time-stamp in nanoseconds.
        System.arraycopy(event.values, 0, newSensorData.values, 0, 3);
        accPoints.add(newSensorData);

        // Every X updates, update the graph?
        long now = System.currentTimeMillis();
        if (now - lastGraphUpdateMs > 40) // Update around 25 fps? every
        {
//            System.out.println("Update");
            updateAccGraph(1000);
        }
        else
        {
  //          System.out.println("Sleep");
        }
    }

    float bounds = 15.f;

    public void updateAccGraph(int millisecondsToShow)
    {
        lastGraphUpdateMs = System.currentTimeMillis();
        GraphView graph = (GraphView) findViewById(R.id.graphAcc);
        graph.removeAllSeries(); // Clear old data.

        // use static labels for horizontal and vertical labels
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
//        staticLabelsFormatter.setHorizontalLabels(new String[] {"old", "newest"});
//        staticLabelsFormatter.setVerticalLabels(new String[] {"-10", "0", "10"});
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);

        Viewport vp = graph.getViewport();
        vp.setYAxisBoundsManual(true);

        // Always reduce bounds to fit the view if possible.
        vp.setMaxY(bounds);
        vp.setMinY(-bounds);

        ArrayList<LineGraphSeries<DataPoint>> series = new ArrayList<>();
        for (int i = 0; i < 3; ++i)
            series.add(new LineGraphSeries<DataPoint>());

        /// Should use boolean for which one to use...?
        boolean useMaxTimeDiff = true;
        int maxTimeDiffMs = millisecondsToShow;
        int maxTimeDiffNanoSecs = maxTimeDiffMs * 1000000;
        int maxPoints = 20;

        long lastTimeStamp = accPoints.get(accPoints.size() - 1).timestamp;
        int firstIndex = (accPoints.size() - maxPoints) > 0? accPoints.size() - maxPoints : 0; // First index if using number of points.
        if (useMaxTimeDiff) // Find first index for when using the time diff max.
            for (int i = accPoints.size() - 1; i > 0; --i)
            {
                long timeStamp = accPoints.get(i).timestamp;
                if (lastTimeStamp - timeStamp > maxTimeDiffNanoSecs)
                {
                    firstIndex = i;
                    break;
                }
            }

        long firstTimeStamp = accPoints.get(firstIndex).timestamp;

        int sampleNumber = 0;
        int firstX = -1, lastX = -1;
        float largestAbsVal = 0;
        for (int i = firstIndex; i < accPoints.size(); ++i)
        {
            SensorData sd = accPoints.get(i);
            // timediff in nanosecs: sd.timestamp - firstTimeStamp
            int x = sampleNumber;
            if (firstX == -1)
                firstX = x;
            lastX = x;
            for (int j = 0; j < 3; ++j) {
                series.get(j).appendData(new DataPoint(x, sd.values[j]), false, 1000, true);
                float absVal = Math.abs(sd.values[j]);
                if (absVal > largestAbsVal)
                    largestAbsVal = absVal;
            }
            ++sampleNumber;
        }
        // Move towards the highest value + 1 always?
        bounds = 0.95f * bounds + 0.05f * (largestAbsVal + 3);


        vp.setXAxisBoundsManual(true);
        vp.setMinX(firstX); // Set min/max X
        vp.setMaxX(lastX);
        // 0 to 25, not being displayed...
        TextView tv = (TextView) findViewById(R.id.textView_Bounds);
        tv.setText("firstX: "+firstX+" lastX: "+lastX+" indices added: "+(accPoints.size() - firstIndex));

        series.get(0).setColor(0xFFFF0000);
        series.get(1).setColor(0xFF00FF00);
        series.get(2).setColor(0xFF0000FF);
        for (int i = 0; i < 3; ++i)
            graph.addSeries(series.get(i));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        /// Oioi.
        // D:
        System.out.println("Accuracy changed");
    }

    private SharedPreferences getSharedPreferencesInstance() {
        return getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
    }
    /**
     * Retrieves the boolean from SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private boolean getUpdatesRequestedState() {
        return getSharedPreferencesInstance()
                .getBoolean(Constants.ACTIVITY_UPDATES_REQUESTED_KEY, false);
    }
    private void setUpdatesRequestedState(boolean requestingUpdates) {
        getSharedPreferencesInstance()
                .edit()
                .putBoolean(Constants.ACTIVITY_UPDATES_REQUESTED_KEY, requestingUpdates)
                .commit();
    }

    /**
     * Runs when the result of calling requestActivityUpdates() and removeActivityUpdates() becomes
     * available. Either method can complete successfully or with an error.
     *
     * @param status The Status returned through a PendingIntent when requestActivityUpdates()
     *               or removeActivityUpdates() are called.
     */
    public void onResult(Status status)
    {
        System.out.println("onResult: "+status);
        if (status.isSuccess()) {
            // Toggle the status of activity updates requested, and save in shared preferences.
            boolean requestingUpdates = !getUpdatesRequestedState();
            setUpdatesRequestedState(requestingUpdates);

            // Update the UI. Requesting activity updates enables the Remove Activity Updates
            // button, and removing activity updates enables the Add Activity Updates button.
        //    setButtonsEnabledState();
        } else {
            Log.e(TAG, "Error adding or removing activity detection: " + status.getStatusMessage());
        }
    }
    protected void updateDetectedActivitiesList(ArrayList<DetectedActivity> detectedActivities) {
        mAdapter.updateActivities(detectedActivities);
    }
    /**
     * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
     * Receives a list of one or more DetectedActivity objects associated with the current state of
     * the device.
     */
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "activity-detection-response-receiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<DetectedActivity> updatedActivities =
                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
            if (updatedActivities.size() < 0)
            {
                System.out.println("Activities array empty");
                return;
            }
            updateDetectedActivitiesList(updatedActivities);
            System.out.println("Received list of activities");

            HashMap<Integer, Integer> detectedActivitiesMap = new HashMap<>();
            for (DetectedActivity activity : updatedActivities) {
                System.out.println("Detected act: "+activity.toString()+"");
//                detectedActivitiesMap.put(activity.getType(), activity.getConfidence());
            }
        }
    }
}
