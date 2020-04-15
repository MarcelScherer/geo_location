package com.scherer.goe_location;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener, View.OnClickListener {

    private LocationManager Location_GPS;
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetometer;
    private SharedPreferences last_position;
    private Location stored_loacation;
    private TextView LongiText;
    private TextView LadiText;
    private TextView angle;
    private TextView distance;
    private double aim_angle;
    private double Kompass_angle;
    private ImageView kompass;
    private Display mDisplay;
    private Button home_button;

    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];

    private Handler warning_handler = new Handler();
    private Runnable warning_toast = new Runnable()
    {
        public void run()
        {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Location_GPS = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        // Get accelerometer and magnetometer sensors from the sensor manager.
        // The getDefaultSensor() method returns null if the sensor
        // is not available on the device.
        mSensorManager = (SensorManager) getSystemService(
                Context.SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER);
        mSensorMagnetometer = mSensorManager.getDefaultSensor(
                Sensor.TYPE_MAGNETIC_FIELD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d("APP", "onCreate: ");

        // Get the display from the window manager (for rotation).
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDisplay = wm.getDefaultDisplay();

        LongiText = (TextView)findViewById(R.id.LongiText);
        LadiText  = (TextView)findViewById(R.id.LadiText);
        distance  = (TextView)findViewById(R.id.dist);
        angle     = (TextView)findViewById(R.id.angle);
        kompass   = (ImageView)findViewById(R.id.kompass);
        home_button = (Button)findViewById(R.id.button);

        home_button.setOnClickListener(this);
        last_position = PreferenceManager.getDefaultSharedPreferences(this);
        stored_loacation = new Location("");
        stored_loacation.setLongitude(last_position.getFloat("Longitude", 0.0f));
        stored_loacation.setLatitude(last_position.getFloat("Latitude", 0.0f));

        if ( !Location_GPS.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            Toast einToast = Toast.makeText(getApplicationContext(), "Bitte GPS einschalten", Toast.LENGTH_SHORT);
            einToast.show();
            warning_handler.postDelayed(warning_toast, 1000);
        }
    }

    public void onClick(View v){
        if (v == home_button){
            LongiText.setText("9.238568");
            LadiText.setText("50.092498");
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        try {
           Location_GPS.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (SecurityException e) {
            Toast einToast = Toast.makeText(getApplicationContext(), "Bitte GPS Berechtigung erteilen", Toast.LENGTH_SHORT);
            einToast.show();
        }
        if (mSensorAccelerometer != null) {
            mSensorManager.registerListener(this, mSensorAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mSensorMagnetometer != null) {
            mSensorManager.registerListener(this, mSensorMagnetometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        LongiText.setText(Double.toString(stored_loacation.getLongitude()));
        LadiText.setText(Double.toString(stored_loacation.getLatitude()));
    }

    @Override
    protected void onPause(){
        super.onPause();
        SharedPreferences.Editor editor = last_position.edit();
        editor.putFloat("Longitude", (float)(stored_loacation.getLongitude() ));
        editor.putFloat("Latitude", (float)(stored_loacation.getLatitude() ));
        editor.commit();
        mSensorManager.unregisterListener(this);
        Location_GPS.removeUpdates(this);
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onLocationChanged(Location actaul_loc) {
        float[] ergebnis = new float[1];
        try {
            stored_loacation.setLongitude(Double.parseDouble(LongiText.getText().toString()));
            stored_loacation.setLatitude(Double.parseDouble(LadiText.getText().toString()));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        actaul_loc.distanceBetween(actaul_loc.getLatitude(),
                actaul_loc.getLongitude(),
                stored_loacation.getLatitude(),
                stored_loacation.getLongitude(),
                ergebnis);
        String s = String.format("%.1f",( ergebnis[0]/1000.0f));
        distance.setText(s + " km");

        aim_angle = actaul_loc.bearingTo(stored_loacation);
        Log.d("APP", "angle: " + Double.toString(aim_angle));
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // The sensor type (as defined in the Sensor class).
        int sensorType = sensorEvent.sensor.getType();

        // The sensorEvent object is reused across calls to onSensorChanged().
        // clone() gets a copy so the data doesn't change out from under us
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerData = sensorEvent.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagnetometerData = sensorEvent.values.clone();
                break;
            default:
                return;
        }
        // Compute the rotation matrix: merges and translates the data
        // from the accelerometer and magnetometer, in the device coordinate
        // system, into a matrix in the world's coordinate system.
        //
        // The second argument is an inclination matrix, which isn't
        // used in this example.
        float[] rotationMatrix = new float[9];
        boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                null, mAccelerometerData, mMagnetometerData);

        // Remap the matrix based on current device/activity rotation.
        float[] rotationMatrixAdjusted = new float[9];
        switch (mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                rotationMatrixAdjusted = rotationMatrix.clone();
                break;
            case Surface.ROTATION_90:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                        rotationMatrixAdjusted);
                break;
            case Surface.ROTATION_180:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                        rotationMatrixAdjusted);
                break;
            case Surface.ROTATION_270:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                        rotationMatrixAdjusted);
                break;
        }

        // Get the orientation of the device (azimuth, pitch, roll) based
        // on the rotation matrix. Output units are radians.
        float orientationValues[] = new float[3];
        if (rotationOK) {
            SensorManager.getOrientation(rotationMatrixAdjusted,
                    orientationValues);
        }

        // Pull out the individual values from the array.
        float azimuth = orientationValues[0] / 3.1415f * 180.0f;
        float roll = orientationValues[2] / 3.1415f * 180.0f;

        if(    (roll <  100 && roll >  80)
            || (roll > -100 && roll < -80)){
            Kompass_angle = (azimuth - aim_angle);
            kompass.setRotation((float) 0.0);
            angle.setText(String.format("%.2f", Kompass_angle) + "°");
        }else {
            Kompass_angle = (360.0 - azimuth + aim_angle);
            kompass.setRotation((float) Kompass_angle);
            angle.setText("--°");
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }
}
