package com.urmom.baitboatphoneapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;

public class GpsP_RecyclerViewAdapter extends RecyclerView.Adapter<GpsP_RecyclerViewAdapter.MyViewHolder> {
    private final GpsPointRecyclerInterface gpsPointRecyclerInterface;
    Context context;
    LinkedList<GpsPoint> points;
    public GpsP_RecyclerViewAdapter(Context context, LinkedList<GpsPoint> points, GpsPointRecyclerInterface gpsPointRecyclerInterface)
    {
    this.gpsPointRecyclerInterface = gpsPointRecyclerInterface;
    this.context = context;
    this.points = points;

    }
    @NonNull
    @Override
    public GpsP_RecyclerViewAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.gps_point_row, parent, false);
        return new GpsP_RecyclerViewAdapter.MyViewHolder(view, gpsPointRecyclerInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull GpsP_RecyclerViewAdapter.MyViewHolder holder, int position) {
        GpsPoint p = points.get(position);
        holder.tvName.setText(" " + p.name);
        holder.tvDescription.setText(" " + p.description);
        holder.tvCoords.setText(String.format("%.6fN \n%.6fE ",p.longitude, p.latitude));
        holder.tvDistance.setText(String.format(" Odległość %dm", (int)p.distance));
        holder.tvDepth.setText(String.format("Głębokość: %.1fm ", p.depth));

    }

    @Override
    public int getItemCount() {
        return points.size();
    }
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvDescription;
        TextView tvCoords;
        TextView tvDistance;
        TextView tvDepth;

        public MyViewHolder(@NonNull View itemView, final GpsPointRecyclerInterface gpsPointRecyclerInterface) {
            super(itemView);
            tvName = itemView.findViewById(R.id.point_name_text);
            tvDescription = itemView.findViewById(R.id.point_description);
            tvCoords = itemView.findViewById(R.id.point_coords);
            tvDistance = itemView.findViewById(R.id.point_distance);
            tvDepth = itemView.findViewById(R.id.point_depth);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(gpsPointRecyclerInterface != null)
                    {
                        int pos = getAdapterPosition();
                        if(pos != RecyclerView.NO_POSITION)
                        {
                            gpsPointRecyclerInterface.onItemClick(pos);
                        }
                    }
                }
            });
        }
    }
}
