package com.urmom.baitboatphoneapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.zerokol.views.joystickView.JoystickView;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    public static Context context;

    private  final static int SEND_TASK_PERIOD = 300;
    private Button mDropButton = null;
    private Button mSetHomeButton = null;
    private Button mGoHomeButton = null;
    private Spinner dropDownMenu= null;

    private JoystickView joystickView = null;
    private TextView mGpsText = null;
    private TextView mOrientationText = null;
    private TextView mVbatText = null;
    private TextView mTempPressText = null;

    private BoatProtocolParser mParser = null;
    private UartService mService = null;
    Handler mHandler;

    private int mJoyPower = 0;
    private int mJoyAngle = 0;

    private  boolean mDropRequested = false;
    private  boolean mGoHomeRequested = false;
    private  boolean mSetHomeRequested = false;

    private double lastTempVal = 0;
    private double lastPressVal = 0;

    public static double lastBoatLattitude = 19.94579406207298;
    public static double lastBoatLongitude = 50.05745068299209;

    static GpsPoint lastRequestedGoal = new GpsPoint();
    public static boolean isNewGoalRequested = false;


    private final static String TAG = "BaitBoatPhoneApp";
    private String deviceName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final String TAG = "MainActivityBoat";

        mHandler = new Handler();
        context = getApplicationContext();

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();

        }
        mDropButton = findViewById(R.id.dropButton);
        mSetHomeButton = findViewById(R.id.setHomeButton);
        mGoHomeButton = findViewById(R.id.goHomeButton);

        joystickView = findViewById(R.id.joystickView);

        mGpsText = findViewById(R.id.gpsData);
        mOrientationText = findViewById(R.id.orientationText);
        mVbatText = findViewById(R.id.vbatText);
        mTempPressText = findViewById(R.id.tempPressText);
        dropDownMenu = findViewById(R.id.dropDownMenu);

        mGpsText.setVisibility(View.INVISIBLE);
        mOrientationText.setVisibility(View.INVISIBLE);
        mVbatText.setVisibility(View.INVISIBLE);
        mTempPressText.setVisibility(View.INVISIBLE);

        mDropButton.setVisibility(View.INVISIBLE);
        mSetHomeButton.setVisibility(View.INVISIBLE);
        mGoHomeButton.setVisibility(View.INVISIBLE);
        ArrayAdapter<CharSequence> adapter= ArrayAdapter.createFromResource(this, R.array.dropDownMenuOptions, R.layout.main_spinner_item);
        adapter.setDropDownViewResource(R.layout.main_spinner_item);
        dropDownMenu.setAdapter(adapter);

        dropDownMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                onSpinnerClick(i);
                dropDownMenu.setSelection(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                dropDownMenu.setSelection(0);
            }
        });
        mDropButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendLoadDrop();
            }
        });

        mSetHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendSetHome();
            }
        });

        mGoHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendGoHome();
            }
        });

        joystickView.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                onJoystickUpdate(power, angle);
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL);

        mParser = new BoatProtocolParser();
        service_init();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                periodicControlSend();
                mHandler.postDelayed(this, SEND_TASK_PERIOD);
            }
        }, 2000);

        updateSettings();

        Log.d(TAG, "OnCreate done");
    }


    @Override
    protected void onResume()
    {
        super.onResume();
        updateSettings();

    }
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                ShowWarning("Połączono");
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        StartConnecting();
                        joystickView.setVisibility(View.INVISIBLE);
                        mDropButton.setVisibility(View.INVISIBLE);
                        mSetHomeButton.setVisibility(View.INVISIBLE);
                        mGoHomeButton.setVisibility(View.INVISIBLE);
                    }
                });

                ShowWarning("Rozłączono, powód: " + mService.disconnectReason);
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                joystickView.setVisibility(View.VISIBLE);
                mDropButton.setVisibility(View.VISIBLE);
                mSetHomeButton.setVisibility(View.VISIBLE);
                mGoHomeButton.setVisibility(View.VISIBLE);

            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                while (mService.hasRxBytes()) {
                    mParser.addByte(mService.getRxByte());
                }
                processBoatMessages();
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
//                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DEAD_CONNECTION)) {
                ShowWarning("Brak komunikacji, reset połączenia");
            }

        }
    };

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            StartConnecting();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "onServiceDisconnected mService= ");
        }

    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    void StartConnecting() {
            mService.initialize();
            mService.setDeviceName(deviceName);
            mService.startScanning();


    }

    void ShowWarning(final String warningText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), warningText, Toast.LENGTH_SHORT).show();
            }
        });
    }

    void processBoatMessages() {
        BoatProtocolParser.ReceivedBoatMessage cmd;

        mParser.parseRxBuffer();

        while (mParser.isRxCommandAvailable()) {
            cmd = mParser.getBoatMessage();
            switch (cmd.type) {
                case BoatProtocolParser.TYPE_GPS: {
                    updateGpsField(cmd.val1, cmd.val2);
                    break;
                }
                case BoatProtocolParser.TYPE_COMPASS: {
                    updateOrientationField(cmd.val1);
                    break;
                }
                case BoatProtocolParser.TYPE_VOLTAGE: {
                    updateVbatField(cmd.val1);
                    break;
                }
                case BoatProtocolParser.TYPE_PRESSURE: {
                    lastPressVal = cmd.val1;
                    updateTempPressField(lastTempVal, lastPressVal);
                    break;
                }
                case BoatProtocolParser.TYPE_TEMPERATURE: {
                    lastTempVal = cmd.val1;
                    updateTempPressField(lastTempVal, lastPressVal);
                    break;
                }

            }

        }

    }

    void sendLoadDrop() {
        mDropRequested = true;

    }

    void sendGoHome() {
        mGoHomeRequested = true;
    }

    void sendSetHome() {
        mSetHomeRequested = true;
    }

    static void requestGoToTarget(GpsPoint point)
    {
        lastRequestedGoal = point;
        MainActivity.isNewGoalRequested = true;
        Log.d(TAG, "Requested going to point " + point.name);

    }

    void updateGpsField(double lat, double lon) {
        mGpsText.setText(String.format("%.6fN, %.6fW", lat, lon));
        if (mGpsText.getVisibility() == View.INVISIBLE) {
            mGpsText.setVisibility(View.VISIBLE);
        }
    }

    void updateVbatField(double vbat) {
        mVbatText.setText(String.format("%.2fV", vbat));
        if (mVbatText.getVisibility() == View.INVISIBLE) {
            mVbatText.setVisibility(View.VISIBLE);
        }
    }

    void updateOrientationField(double orientation) {
        mOrientationText.setText(String.format("%d° (%s)", (int) orientation, mParser.getDirectionName((int)orientation)));
        if (mOrientationText.getVisibility() == View.INVISIBLE) {
            mOrientationText.setVisibility(View.VISIBLE);
        }
    }

    void updateTempPressField(double temp, double press) {
        mTempPressText.setText(String.format("%.1f°C   %.1fhPa", temp, press));
        if (mTempPressText.getVisibility() == View.INVISIBLE) {
            mTempPressText.setVisibility(View.VISIBLE);
        }
    }

    void onJoystickUpdate(int power, int angle) {
        mJoyPower = power;
        mJoyAngle = angle;
    }


    void periodicControlSend() {
//        byte[] msgToBoat = mParser.generateMotorValuesFromJoystick(mJoyPower, mJoyAngle);
        byte[] msgToBoat = "".getBytes();

        if (mService.isTxPossible()) {
            mService.timeoutWatchdog();

            if(MainActivity.isNewGoalRequested)
            {
                msgToBoat = extendArray(msgToBoat, mParser.getGoToPointRequest(lastRequestedGoal.latitude, lastRequestedGoal.longitude));
                ShowWarning("Wysyłam łódkę do punktu o nazwie " + lastRequestedGoal.name);
                MainActivity.isNewGoalRequested = false;
            }
            if(mDropRequested)
            {
                mDropRequested = false;
                msgToBoat = extendArray(msgToBoat, mParser.getLoadDropRequest());
            }

            if(mGoHomeRequested)
            {
                mGoHomeRequested = false;
                msgToBoat = extendArray(msgToBoat, mParser.getGoHomeRequest());
            }

            if(mSetHomeRequested)
            {
                mSetHomeRequested = false;
                msgToBoat = extendArray(msgToBoat, mParser.getSetHomeRequest());
            }

            if(!mService.isBleSending())
            {
                msgToBoat = extendArray(msgToBoat, mParser.generateMotorValuesFromJoystick(mJoyPower, mJoyAngle));
            }

            if(msgToBoat.length > 0)
            {
                Log.d(TAG, "Sending " + new String(msgToBoat, 0));
                mService.sendData(msgToBoat);
            }

        }
    }


    void onSpinnerClick(int pos)
    {
        if (pos == 1)
        {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        else if(pos == 2)
        {
            Intent intent = new Intent(this, GpsPointsActivity.class);
            startActivity(intent);
        }
    }
    byte[] extendArray(byte[] arr1, byte[] arr2)
    {
        byte[] result = new byte[arr1.length + arr2.length];
        for(int i = 0 ; i < arr1.length; i++)
        {
            result[i] = arr1[i];
        }
        for(int i = 0 ; i < arr2.length; i++)
        {
            result[i+ arr1.length] = arr2[i];
        }
    return result;

    }

    void updateSettings()
    {
        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(this);
        int boatPower = sharedPref.getInt("boat_max_power", 100);
        String bleRelayName = sharedPref.getString("ble_device_name_pref", "");
        int motorCompensation = sharedPref.getInt("boat_motor_compensation", 0);
        deviceName = bleRelayName;
        mParser.setMaxAllowedMotorVal(boatPower);
        mParser.setMotorCompensation(motorCompensation);
    }
}
