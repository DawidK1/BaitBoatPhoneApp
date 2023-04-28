package com.urmom.baitboatphoneapp;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

public class ExtraControlActivity extends AppCompatActivity {

    SeekBar ledSeekBar = null;
    Button specialCmdButton = null;
    Button calibVoltageButton = null;
    Button calibPressureButton = null;
    Button compensateMotorsButton = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extra_control);
        ledSeekBar = findViewById(R.id.led_set_bar);

        ledSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                onLedValueChanged(seekBar.getProgress());

            }
        });

        specialCmdButton = findViewById(R.id.special_cmd_button);
        calibVoltageButton = findViewById(R.id.calib_voltage_button);
        calibPressureButton = findViewById(R.id.calib_pressure_button);
        compensateMotorsButton = findViewById(R.id.motor_compensation_button);

        specialCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onSpecialCmdClick();
            }
        });
        calibVoltageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onCalibVoltageClick();
            }
        });
        calibPressureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onCalibPressureClick();
            }
        });
        compensateMotorsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onMotorCalibClick();
            }
        });
    }

    void onSpecialCmdClick()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setMessage("Wpisz komendę i wciśnij wyślij").setPositiveButton("Wyślij", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                MainActivity.requestSpecialCmd(input.getText().toString().toUpperCase().getBytes());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"Wysłano komendę " + input.getText().toString().toUpperCase(), Toast.LENGTH_LONG).show();
                    }
                });

            }
        }).setNegativeButton("Anuluj", null);
        builder.create().show();
    }

    void onCalibVoltageClick()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);
        builder.setMessage("Wpisz rzeczywiste napięcie akumulatorów, z jednym lub dwoma miejscami po kropce:").setPositiveButton("Wyślij", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                double voltage = 0;
                try {
                    voltage = Double.valueOf(input.getText().toString());
                }catch (Exception e)
                {
                    voltage = 0;
                }

                String response = "";
                if((voltage > 10.0) && (voltage < 15.0))
                {
                    response = "Wysłano zapytanie o kalibrację napięcia łódki";
                    MainActivity.requestSpecialCmd(BoatProtocolParser.getCalibVoltageRequest(voltage));
                }else
                {
                    response = "Niepoprawna wartość napięcia!";
                }
                final String respFinal = response;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),respFinal, Toast.LENGTH_LONG).show();
                    }
                });

            }
        }).setNegativeButton("Anuluj", null);
        builder.create().show();
    }

    void onCalibPressureClick()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);
        builder.setMessage("Wpisz rzeczywiste ciśnienie atmosferyczne, z dokładnością jednego miejsca po kropce:").setPositiveButton("Wyślij", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                double pressure = 0;
                try {
                    pressure = Double.valueOf(input.getText().toString());
                }catch (Exception e)
                {
                    pressure = 0;
                }

                String response = "";
                if((pressure > 950.0) && (pressure < 1050.0))
                {
                    response = "Wysłano zapytanie o kalibrację pomiaru ciśnienia łódki";
                    MainActivity.requestSpecialCmd(BoatProtocolParser.getCalibPressureRequest(pressure));
                }else
                {
                    response = "Niepoprawna wartość ciśnienia!";
                }
                final String respFinal = response;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),respFinal, Toast.LENGTH_LONG).show();
                    }
                });

            }
        }).setNegativeButton("Anuluj", null);
        builder.create().show();
    }

    void onMotorCalibClick()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        builder.setView(input);
        builder.setMessage("Wpisz wartość kompensacji silników od -30% do 30%. Wartość mniejsza od zero jeśli łódka skręca w lewo:").setPositiveButton("Wyślij", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                int motorCompensation = 0;
                try {
                    motorCompensation = Integer.valueOf(input.getText().toString());
                }catch (Exception e)
                {
                    motorCompensation = 0;
                }

                String response = "";
                if((motorCompensation > -30) && (motorCompensation < 30.0))
                {
                    response = "Wysłano zapytanie o kalibrację silników łódki";
                    MainActivity.requestSpecialCmd(BoatProtocolParser.getCalibMotorCompensationRequest(motorCompensation));
                }else
                {
                    response = "Niepoprawna wartość kalibracji silników!";
                }
                final String respFinal = response;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),respFinal, Toast.LENGTH_LONG).show();
                    }
                });

            }
        }).setNegativeButton("Anuluj", null);
        builder.create().show();
    }


    void onLedValueChanged(int newVal)
    {
        byte[] ledCmd = BoatProtocolParser.getSetLedRequest(newVal);
        MainActivity.requestSpecialCmd(ledCmd);
        final int ledVal = newVal;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),String.format("Ustawiam jasność LED na %d%%",ledVal), Toast.LENGTH_SHORT).show();
            }
        });
    }

}