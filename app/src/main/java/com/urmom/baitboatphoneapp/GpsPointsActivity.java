package com.urmom.baitboatphoneapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GpsPointsActivity extends AppCompatActivity implements GpsPointRecyclerInterface {
    private final static String TAG = "BaitBoatPhoneApp";
    PointManager mPointManager = null;
    Button addNewPointButton;
    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gps_points_activity);
        recyclerView = findViewById(R.id.points_recycler_view);
        mPointManager = new PointManager();

        GpsP_RecyclerViewAdapter adapter = new GpsP_RecyclerViewAdapter(this, mPointManager.getPoints(), this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        addNewPointButton = findViewById(R.id.add_point_button);
        addNewPointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onAddButtonClick();
            }
        });
        Log.d(TAG, "Oncreate pointActivity");
    }

    @Override
    public void onItemClick(int position) {
        GpsPoint point = mPointManager.getPoints().get(position);
        Log.d(TAG, String.format("Clicked point: id %d, name:%s, lat %f lon %f", position, point.name, point.latitude, point.longitude));
        new GpsPointClickDIalog(point, mPointManager, this).show(getSupportFragmentManager(), "fragmentDialog");
    }

    @Override
    public void onSomethingChanged() {
        finish();
        startActivity(getIntent());
    }


    private void onAddButtonClick() {
        final GpsPoint point = new GpsPoint();
        point.latitude = MainActivity.lastBoatLattitude;
        point.longitude = MainActivity.lastBoatLongitude;

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        String currentDateandTime = sdf.format(new Date());
        point.name = "Nowy punkt";
        point.description = "Punkt utworzony " + currentDateandTime + ".";


        mPointManager.add(point);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Dodano nowy punkt o nazwie " + point.name, Toast.LENGTH_LONG).show();
            }
        });
        onSomethingChanged();
    }
}