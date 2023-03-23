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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.zerokol.views.joystickView.JoystickView;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private Button mTestButton = null;
    private JoystickView joystickView = null;

    private  protocolParser mParser = null;
    private UartService mService = null;
    Handler mHandler;

    private int mJoyPower = 0;
    private int mJoyAngle = 0;


    private final static String TAG = "BaitBoatPhoneApp";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final String TAG = "MainActivityBoat";

        mHandler = new Handler();

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
        mTestButton = findViewById(R.id.button);
        joystickView = findViewById(R.id.joystickView);


        mTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "Button pressed");
                StartConnecting();
            }
        });

        joystickView.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                onJoystickUpdate(power, angle);
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL);

        mParser = new protocolParser();
        service_init();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                periodicControlSend();
                mHandler.postDelayed(this, 200);
            }
        }, 2000);

        Log.d(TAG, "OnCreate done");
    }


    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
//                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
//                        Log.d(TAG, "UART_CONNECT_MSG");
//                        btnConnectDisconnect.setText("Disconnect");
//                        uartConnected = true;
//                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
//                        listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
//                        messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
//                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
//                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
//                        Log.d(TAG, "UART_DISCONNECT_MSG");
//                        btnConnectDisconnect.setText("Connect");
//                        uartConnected = false;
//                        ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
//                        listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
//                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        //setUiState();

                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
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
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
//                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
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
        if (mService.isTxPossible()) {
            sendLoadDrop();
        } else {

            mService.initialize();
            mService.startScanning();
        }

    }

    void ShowWarning(String warningText)
    {

    }

    void processBoatMessages()
    {
        protocolParser.Command cmd;

        mParser.parseRxBuffer();

        while (mParser.isRxCommandAvailable())
        {
            cmd = mParser.getBoatMessage();
            Log.d(TAG,"Got Boat msg! ID is " + cmd.type + " val1 is " + cmd.val1 + " val2 is " + cmd.val2);
        }

    }
    void sendLoadDrop() {
        if(mService.isTxPossible())
        {
            mService.writeRXCharacteristic(mParser.getLoadDropRequest());
        }
        else
        {
            ShowWarning("Nie można wysłać komendy!");
        }
    }

    void sendGoHome() {
        if(mService.isTxPossible())
        {
            mService.writeRXCharacteristic(mParser.getGoHomeRequest());
        }
        else
        {
            ShowWarning("Nie można wysłać komendy!");
        }
    }

    void sendSetHome() {
        if(mService.isTxPossible())
        {
            mService.writeRXCharacteristic(mParser.getSetHomeRequest());
        }
        else
        {
            ShowWarning("Nie można wysłać komendy!");
        }
    }



    void onJoystickUpdate(int power, int angle) {
        mJoyPower = power;
        mJoyAngle = angle;
    }

    void periodicControlSend() {
         byte[] controlVals = mParser.generateMotorValuesFromJoystick(mJoyPower, mJoyAngle);

//         Log.d(TAG, "Control values: " + new String(controlVals, 0));
         if (mService.isTxPossible()) {
            mService.writeRXCharacteristic(controlVals);
        }
    }


}
