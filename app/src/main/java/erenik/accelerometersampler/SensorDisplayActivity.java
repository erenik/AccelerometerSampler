package erenik.accelerometersampler;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionApi;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.HashMap;

/** Ref src code for Google play services Activity recognition API
 https://github.com/googlesamples/android-play-location/tree/master/ActivityRecognition
 * https://github.com/googlesamples/android-play-location/tree/master/ActivityRecognition/app/src/main/java/com/google/android/gms/location/sample/activityrecognition
*/

public class SensorDisplayActivity
        extends AppCompatActivity
        implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status>
{
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

    /**
     * Adapter backed by a list of DetectedActivity objects.
     */
    private DetectedActivitiesAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        System.out.println("Starting up AccelerometerSampler application");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_display);

        Button fab = (Button) findViewById(R.id.buttonUploadToServer);
        /// Send data to Server? Store locally?
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView tv = (TextView) findViewById(R.id.PrimaryTextView);
                tv.setText("Very snackyyy " + timesClicked++);
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

    synchronized void InitGoogleAPI()
    {
        System.out.println("Init google API for activity recognition.");
        Context context = getBaseContext();

        // Set up Google Play Services for Activity regognition?
        mGoogleApiClient  =  new GoogleApiClient.Builder(context)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
        // Connect it.


        // Request updates.
//        ActivityRecognitionApi.requestActivityUpdates
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
    public void requestActivityUpdates()
    {
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
    public void Iterate()
    {
        /// Sample accelerometer

        /// Update text
        TextView tv = (TextView) findViewById(R.id.PrimaryTextView);
        tv.setText("Very snackyyy "+timesClicked+" iterated: "+iterated++);

        /// Query next sampling.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Iterate();
            }
        }, delay);
    }

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
    @Override
    public void onSensorChanged(SensorEvent event)
    {
        float[] values = event.values;
        String text = "";
        for (int i = 0; i < values.length; ++i)
        {
            int integral = (int)values[i];
            text = text + integral + " ";
        }
        // Very cool.
        TextView sensorText = (TextView) findViewById(R.id.textView_AccelerometerValues);
        sensorText.setText(""+text+"  sample "+sensorChanges++);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        /// Oioi.
        // D:
        System.out.println("Accuracy changed");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        System.out.println("onConnected");
        System.out.println("- connected? "+mGoogleApiClient.isConnected());
        requestActivityUpdates(); // Request Google activity recognition
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.out.println("onConnectionSuspended ");

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        System.out.println("onConnectionFailed");
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
