package com.drejkim.androidwearmotionsensors;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.FloatMath;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

public class SensorFragment extends Fragment implements SensorEventListener {

    private static final float SHAKE_THRESHOLD = 1.1f;
    private static final int SHAKE_WAIT_TIME_MS = 250;
    private static final float ROTATION_THRESHOLD = 2.0f;
    private static final int ROTATION_WAIT_TIME_MS = 100;

    private View mView;
    private TextView mTextTitle;
    private TextView mTextValues;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private int mSensorType;
    private long mShakeTime = 0;
    private long mRotationTime = 0;

    private Button startButton;
    private Button stopButton;
    private boolean isMeasuring = false;
    private GoogleApiClient mGoogleApiClient;

    private static final String COUNT_KEY = "com.example.key.count";
    private int count = 0;

    private static final String TAG = "SensorFragment";

    private ArrayList<String> accelerometerData;

    public static SensorFragment newInstance(int sensorType) {
        SensorFragment f = new SensorFragment();

        // Supply sensorType as an argument
        Bundle args = new Bundle();
        args.putInt("sensorType", sensorType);
        f.setArguments(args);

        return f;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        accelerometerData = new ArrayList<String>();

        Bundle args = getArguments();
        if(args != null) {
            mSensorType = args.getInt("sensorType");
        }

        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(mSensorType);

    }

    private void initButtons()
    {
        startButton.setOnClickListener( new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                isMeasuring = true;
            }
        });

        stopButton.setOnClickListener( new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                isMeasuring = false;
            }
        });
    }


    public void sendData()
    {
        if(accelerometerData.size() < 200) return;

        Log.i(TAG, "Sending data!");
        Log.i(TAG, "Connected? " + mGoogleApiClient.isConnected());

        if(mGoogleApiClient.isConnected())
        {
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/count");
            putDataMapReq.getDataMap().putStringArrayList(COUNT_KEY, accelerometerData);

            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            Log.i(TAG, pendingResult.toString());
        }

        accelerometerData.clear();
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.sensor, container, false);

        mTextTitle = (TextView) mView.findViewById(R.id.text_title);
        mTextTitle.setText(mSensor.getStringType());
        mTextValues = (TextView) mView.findViewById(R.id.text_values);


        mGoogleApiClient = new GoogleApiClient.Builder(mView.getContext())
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();

        startButton = (Button) mView.findViewById(R.id.startButton);
        stopButton = (Button) mView.findViewById(R.id.stopButton);
        initButtons();

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if(isMeasuring)
        {
            // If sensor is unreliable, then just return
            if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
            {
                return;
            }

            // Show this on the watch face
            mTextValues.setText(
                    "x = " + Float.toString(event.values[0]) + "\n" +
                            "y = " + Float.toString(event.values[1]) + "\n" +
                            "z = " + Float.toString(event.values[2]) + "\n"
            );

            if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                detectShake(event);
            }
            else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                detectRotation(event);
            }
        }
        else
        {
           // Log.i(TAG, "Not measuring data!");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    // References:
    //  - http://jasonmcreynolds.com/?p=388
    //  - http://code.tutsplus.com/tutorials/using-the-accelerometer-on-android--mobile-22125
    private void detectShake(SensorEvent event) {
        long now = System.currentTimeMillis();

        float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;

        accelerometerData.add(System.currentTimeMillis() + "," + gX + "," + gY + "," + gZ);
        sendData();

        /*
        if((now - mShakeTime) > SHAKE_WAIT_TIME_MS) {
            mShakeTime = now;

            float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
            float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
            float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;



            // gForce will be close to 1 when there is no movement
            float gForce = FloatMath.sqrt(gX*gX + gY*gY + gZ*gZ);
            System.out.println(gForce);

            // Change background color if gForce exceeds threshold;
            // otherwise, reset the color
            if(gForce > SHAKE_THRESHOLD) {
                mView.setBackgroundColor(Color.rgb(0, 100, 0));
            }
            else {
                mView.setBackgroundColor(Color.BLACK);
            }
        }
        */
    }

    private void detectRotation(SensorEvent event) {
        long now = System.currentTimeMillis();

        if((now - mRotationTime) > ROTATION_WAIT_TIME_MS) {
            mRotationTime = now;

            // Change background color if rate of rotation around any
            // axis and in any direction exceeds threshold;
            // otherwise, reset the color
            if(Math.abs(event.values[0]) > ROTATION_THRESHOLD ||
               Math.abs(event.values[1]) > ROTATION_THRESHOLD ||
               Math.abs(event.values[2]) > ROTATION_THRESHOLD) {
                mView.setBackgroundColor(Color.rgb(0, 100, 0));
            }
            else {
                mView.setBackgroundColor(Color.BLACK);
            }
        }
    }
}
