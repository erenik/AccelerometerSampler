package erenik.accelerometersampler;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import android.os.Handler;

public class SensorDisplayActivity extends AppCompatActivity implements SensorEventListener
{
    static int timesClicked = 0;
    Handler h = new Handler(); // iteration handler
    int delay = 1000; // ms

    SensorManager sensorManager;
    Sensor sensor;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_display);
        Button fab = (Button) findViewById(R.id.buttonUploadToServer);
        /// Send data to Server? Store locally?
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Very snackbar", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                TextView tv = (TextView) findViewById(R.id.PrimaryTextView);
                tv.setText("Very snackyyy " + timesClicked++);
            }
        });

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
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sensor_display, menu);
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
}
