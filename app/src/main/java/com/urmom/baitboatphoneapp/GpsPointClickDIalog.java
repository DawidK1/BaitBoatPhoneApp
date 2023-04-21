package com.urmom.baitboatphoneapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class GpsPointClickDIalog extends DialogFragment {
    GpsPoint point;
    PointManager pointManager;
    GpsPointRecyclerInterface gpsPointRecyclerInterface;
    GpsPointClickDIalog(GpsPoint point, PointManager pointManager, GpsPointRecyclerInterface gpsPointRecyclerInterface){
        this.point = point;
        this.pointManager = pointManager;
        this.gpsPointRecyclerInterface = gpsPointRecyclerInterface;
    }
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final String[] options = getActivity().getResources().getStringArray(R.array.gpsPointClickArray);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(point.name);
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(i == 0)
                {
                    MainActivity.requestGoToTarget(point);
                }

                if( i == 1)
                {
                    onEditPointClick();
                }

                if(i == 2)
                {
                  // google maps
                }

                if(i == 3)
                {
                    onDeletePointClick();
                }
            }
        });

        return builder.create();
    }


    void onEditPointClick()
    {

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.edit_point_view, null);

        final EditText nameText = view.findViewById(R.id.point_input_name);
        nameText.setText(point.name);

        final EditText descText = view.findViewById(R.id.point_input_description);
        descText.setText(point.description);

        final EditText depthText = view.findViewById(R.id.point_input_depth);
        depthText.setText(String.format("%.1f", point.depth));


        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Edytuj punkt")
                .setMessage(String.format("%.6fN %f.6E", point.latitude, point.longitude))
                .setView(view)
                .setPositiveButton("Zmień", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        GpsPoint newPoint = new GpsPoint();
                        newPoint.latitude = point.latitude;
                        newPoint.longitude = point.longitude;
                        newPoint.depth = point.depth;
                        newPoint.name = nameText.getText().toString();
                        newPoint.description = descText.getText().toString();
                        newPoint.depth = Double.valueOf(depthText.getText().toString());

                        pointManager.editPoint(point, newPoint);
                        gpsPointRecyclerInterface.onSomethingChanged();
                    }
                })
                .setNegativeButton("Anuluj", null)
                .create();
        dialog.show();
    }
    void onDeletePointClick()
    {
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(getContext());
        builder.setMessage("Czy na pewno chcesz usunąć punkt?").setPositiveButton("Tak", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                pointManager.deletePoint(point);
                Log.d("TAG", "deleted point");
                gpsPointRecyclerInterface.onSomethingChanged();
            }
        }).setNegativeButton("Nie", null);


        //Creating dialog box
        AlertDialog alert = builder.create();
        //Setting the title manually
        alert.setTitle("USUWANIE PUNKTU");
        alert.show();
    }
}

