package erenik.accelerometersampler;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Handler;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
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
        GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status> {
    protected static final String TAG = "MainActivity";
    /**
     * A receiver for DetectedActivity objects broadcast by the
     * {@code ActivityDetectionIntentService}.
     */
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;


    static int timesClicked = 0;
    Handler h = new Handler(); // iteration handler
    // Frame-rate for graph updates? 50 fps?
    int frameRateUpdateDelayMs = 20; // ms

    SensorManager sensorManager;
    Sensor accSensor, gyroSensor;
    GoogleApiClient mGoogleApiClient;


    // Default on, Start/Stop button to toggle it.
    boolean on = true;

    /**
     * Adapter backed by a list of DetectedActivity objects.
     */
    private DetectedActivitiesAdapter mAdapter;
    private SensingFrame sensingFrame = new SensingFrame("");
    ArrayList<SensingFrame> sensingFrames = new ArrayList<>(); // Past sensing frames.

    String SensingFramesAsCSV(){
        String total = "";
        total += SensingFrame.CSVHeaders()+"\n";
        for (int i = 0; i < sensingFrames.size(); ++i){
            SensingFrame sf = sensingFrames.get(i);
            total += sf.toString();
        }
        return total;
    }
    String chosenTransport = "";
    public int herz = 20; // Samples per second.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("Starting up AccelerometerSampler application");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_display);

        // Populate spinner for transport.
        Spinner spinner = (Spinner) findViewById(R.id.spinnerTransport);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.transports, android.R.layout.simple_spinner_item);// Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);// Apply the adapter to the spinner
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                TextView tv = (TextView)view;
                if (tv != null) {
                    System.out.println("Selected: " + tv.getText());
                    chosenTransport = (String) tv.getText();
                    sensingFrame.transportString = chosenTransport;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        findViewById(R.id.buttonSendAsText).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                // Fetch the data as text?
                String total = SensingFramesAsCSV();
              //  System.out.println("Full string? "+total);
                sendIntent.putExtra(Intent.EXTRA_TEXT, total);
                sendIntent.setType("text/plain");
                startActivity(sendIntent);
            }
        });


        findViewById(R.id.button_Restart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorChangesAcc = 0;
                sensorChangesGyro = 0;
                accPoints.clear();
                gyroPoints.clear();
                locationSamples.clear();
                sensingFrames.clear(); // Clear it
                sensingFrame = new SensingFrame(chosenTransport); // Reset it.
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

        /// Set up the graphs to static graphs (non-rescaling as was tested earlier..)
        GraphView graphAcc = (GraphView) findViewById(R.id.graphAcc);
        Viewport vp = graphAcc.getViewport();
        vp.setYAxisBoundsManual(true);
        vp.setMaxY(15);
        vp.setMinY(-15);
        GraphView graphAccMagn = (GraphView) findViewById(R.id.graphAccMagn);
        vp = graphAccMagn.getViewport();
        vp.setYAxisBoundsManual(true);
        vp.setMaxY(20);
        vp.setMinY(0);
        GraphView graphGyro = (GraphView) findViewById(R.id.graphGyro);
        vp = graphGyro.getViewport();
        vp.setYAxisBoundsManual(true);
        vp.setMaxY(5);
        vp.setMinY(-5);
        GraphView graphGyroMagn = (GraphView) findViewById(R.id.graphGyroMagn);
        vp = graphGyroMagn.getViewport();
        vp.setYAxisBoundsManual(true);
        vp.setMaxY(7);
        vp.setMinY(0);

        /// Set up Accelerometer sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        float secondsPerSample = 1.f / herz;
        int microsecondsPerSample = (int) (secondsPerSample * 1000000);
        System.out.println("Using sampling rate of "+herz+"Hz, seconds per sample: "+secondsPerSample+" ms per sample: "+microsecondsPerSample);
//        int microSeconds = 10 000 000;
        sensorManager.registerListener(this, accSensor, microsecondsPerSample);
        sensorManager.registerListener(this, gyroSensor, microsecondsPerSample);

        /// Set up handler for iterated samplings
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Iterate();
            }
        }, frameRateUpdateDelayMs);
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

        updateAccGraph();
        updateGyroGraph();

        long now = System.currentTimeMillis();
        if (sensingFrame.startTimeMs + sensingFrame.durationMs < now) { // Finish the frame?
            /// Calculate sensing frame, if applicable.
            SensingFrame finishedOne = sensingFrame;
            sensingFrame = new SensingFrame(chosenTransport);
            finishedOne.CalcStats();
            // Save the finished one into a list or some sort, display it as well?
            tv = (TextView) findViewById(R.id.textView_SensingFrameAcc);
            tv.setText(finishedOne.accString(" /  "));
            tv = (TextView) findViewById(R.id.textView_SensingFrameGyro);
            tv.setText(finishedOne.gyroString(" / "));
            finishedOne.ClearMagnitudeData(); // Clear data not needed from now?
            sensingFrames.add(finishedOne);
        }

        /// Query next sampling.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Iterate();
            }
        }, frameRateUpdateDelayMs);
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

    int sensorChangesAcc = 0;
    int sensorChangesGyro = 0;

    /// All stored accelerometer-sensor points.
    ArrayList<SensorData> accPoints = new ArrayList<>(),
        gyroPoints = new ArrayList<>();
    ArrayList<MagnitudeData> accMagnPoints = new ArrayList<>(),
        gyroMagnPoints = new ArrayList<>();
    long lastGraphUpdateMs = System.currentTimeMillis();

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!on)
            return;

        float[] values = event.values; // Update TextViews
        String text = "";
        for (int i = 0; i < values.length; ++i) {
            text = text + String.format("%.1f", values[i])+" ";
        }
        if (event.sensor == accSensor) {
            TextView sensorText = (TextView) findViewById(R.id.textView_AccelerometerValues);
            sensorText.setText("" + text + "  sample " + sensorChangesAcc++);
            SensorData newSensorData = new SensorData(); // Copy over data from Android data to own class type.
            newSensorData.timestamp = event.timestamp; // Time-stamp in nanoseconds.
            System.arraycopy(event.values, 0, newSensorData.values, 0, 3);
            accPoints.add(newSensorData);
            // If reaching full capacity (or just a lot >1000), clear some?
            if (accPoints.size() > 1000)
                accPoints = Halve(accPoints);
            MagnitudeData magnData = new MagnitudeData(newSensorData.VectorLength(), newSensorData.timestamp);
            accMagnPoints.add(magnData);
            sensingFrame.accMagns.add(magnData);
        }
        else if (event.sensor == gyroSensor){
            TextView sensorText = (TextView) findViewById(R.id.textView_GyroscopeValues);
            sensorText.setText("" + text + "  sample " + sensorChangesGyro++);
            SensorData newSensorData = new SensorData(); // Copy over data from Android data to own class type.
            newSensorData.timestamp = event.timestamp; // Time-stamp in nanoseconds.
            System.arraycopy(event.values, 0, newSensorData.values, 0, 3);
            gyroPoints.add(newSensorData);
            if (gyroPoints.size() > 1000)
                gyroPoints = Halve(gyroPoints);
            MagnitudeData magnData = new MagnitudeData(newSensorData.VectorLength(), newSensorData.timestamp);
            gyroMagnPoints.add(magnData);
            sensingFrame.gyroMagns.add(magnData);
        }
        /*
        else
        {
  //          System.out.println("Sleep");
        }*/
    }

    private static ArrayList<SensorData> Halve(ArrayList<SensorData> points) {
        System.out.println("Halving");
        ArrayList<SensorData> newList = new ArrayList<>();
        for (int i = points.size() / 2; i < points.size(); ++i)
            newList.add(points.get(i));
        return newList;
    }

    float bounds = 15.f;
    int millisecondsToShow = 1000;

    public void updateAccGraph() {
        updateTripleVectorGraph((GraphView)findViewById(R.id.graphAcc), accPoints);
        updateSingleGraph((GraphView)findViewById(R.id.graphAccMagn), accMagnPoints);
    }
    public void updateGyroGraph() {
        updateTripleVectorGraph((GraphView) findViewById(R.id.graphGyro), gyroPoints);
        updateSingleGraph((GraphView)findViewById(R.id.graphGyroMagn), gyroMagnPoints);
    }

    public void updateSingleGraph(GraphView graph, ArrayList<MagnitudeData> magnData){
        if (magnData.size() == 0) return;
        lastGraphUpdateMs = System.currentTimeMillis();
        graph.removeAllSeries(); // Clear old data.
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);        // use static labels for horizontal and vertical labels
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);
        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>();
        /// Should use boolean for which one to use...?
        boolean useMaxTimeDiff = true;
        int maxTimeDiffMs = millisecondsToShow;
        int maxTimeDiffNanoSecs = maxTimeDiffMs * 1000000;
        int maxPoints = 20;

        long lastTimeStamp = magnData.get(magnData.size() - 1).timestamp;
        int firstIndex = (magnData.size() - maxPoints) > 0? magnData.size() - maxPoints : 0; // First index if using number of points.
        if (useMaxTimeDiff) // Find first index for when using the time diff max.
            for (int i = magnData.size() - 1; i > 0; --i)
            {
                long timeStamp = magnData.get(i).timestamp;
                if (lastTimeStamp - timeStamp > maxTimeDiffNanoSecs)
                {
                    firstIndex = i;
                    break;
                }
            }

        long firstTimeStamp = magnData.get(firstIndex).timestamp;

        int sampleNumber = 0;
        int firstX = -1, lastX = -1;
        for (int i = firstIndex; i < magnData.size(); ++i) {
            MagnitudeData sd = magnData.get(i);
            // timediff in nanosecs: sd.timestamp - firstTimeStamp
            int x = sampleNumber;
            if (firstX == -1)
                firstX = x;
            lastX = x;
            for (int j = 0; j < 3; ++j) {
                series.appendData(new DataPoint(x, sd.magnitude), false, 1000, true);
            }
            ++sampleNumber;
        }
        Viewport vp = graph.getViewport();
        vp.setXAxisBoundsManual(true);
        vp.setMinX(firstX); // Set min/max X
        vp.setMaxX(lastX);
        series.setColor(0xFF222233);
        graph.addSeries(series);
    }
    public void updateTripleVectorGraph(GraphView graph, ArrayList<SensorData> dataPoints) {
        if (dataPoints.size() == 0) return;
        lastGraphUpdateMs = System.currentTimeMillis();
        graph.removeAllSeries(); // Clear old data.
        // use static labels for horizontal and vertical labels
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);
        ArrayList<LineGraphSeries<DataPoint>> series = new ArrayList<>();
        for (int i = 0; i < 3; ++i)
            series.add(new LineGraphSeries<DataPoint>());
        boolean useMaxTimeDiff = true;        /// Should use boolean for which one to use...?
        int maxTimeDiffMs = millisecondsToShow;
        int maxTimeDiffNanoSecs = maxTimeDiffMs * 1000000;
        int maxPoints = 20;
        long lastTimeStamp = dataPoints.get(dataPoints.size() - 1).timestamp;
        int firstIndex = (dataPoints.size() - maxPoints) > 0? dataPoints.size() - maxPoints : 0; // First index if using number of points.
        if (useMaxTimeDiff) // Find first index for when using the time diff max.
            for (int i = dataPoints.size() - 1; i > 0; --i)
            {
                long timeStamp = dataPoints.get(i).timestamp;
                if (lastTimeStamp - timeStamp > maxTimeDiffNanoSecs)
                {
                    firstIndex = i;
                    break;
                }
            }
        long firstTimeStamp = dataPoints.get(firstIndex).timestamp;
        int sampleNumber = 0;
        int firstX = -1, lastX = -1;
        float largestAbsVal = 0;
        for (int i = firstIndex; i < dataPoints.size(); ++i) {
            SensorData sd = dataPoints.get(i);
            int x = sampleNumber;            // timediff in nanosecs: sd.timestamp - firstTimeStamp
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
        Viewport vp = graph.getViewport();
        vp.setXAxisBoundsManual(true);
        vp.setMinX(firstX); // Set min/max X
        vp.setMaxX(lastX);
        series.get(0).setColor(0xFFFF0000);
        series.get(1).setColor(0xFF00FF00);
        series.get(2).setColor(0xFF0000FF);
        for (int i = 0; i < 3; ++i)
            graph.addSeries(series.get(i));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
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
    public void onResult(Status status) {
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
