package com.urmom.baitboatphoneapp;

import android.os.Bundle;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;

public class GpsPointsActivity extends AppCompatActivity implements GpsPointRecyclerInterface {
    PointManager mPointManager = null;
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
    }

    @Override
    public void onItemClick(int position) {

    }
}