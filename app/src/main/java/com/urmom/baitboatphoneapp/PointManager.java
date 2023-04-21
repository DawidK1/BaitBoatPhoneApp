package com.urmom.baitboatphoneapp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.LinkedList;

;

public class PointManager {
    String TAG = "BaitBoatPhoneApp";
    private LinkedList<GpsPoint> points;
    private String filename = "points.txt";
    private String dirFilename = "baitBoatAppData";

    public PointManager() {
        points = new LinkedList<>();
        points.clear();
        loadPointsFromFile();
    }

    public void add(GpsPoint point) {
        add(point, 0);
    }

    public void add(GpsPoint point, int idx) {
        points.add(idx, point);
        savePointsToFile();
    }

    public void savePointsToFile() {
        JSONObject pointObj = null;
        JSONArray pointArray = new JSONArray();
        try {
            for (int i = 0; i < points.size(); i++) {
                pointObj = new JSONObject();
                pointObj.put("latitude", points.get(i).latitude);
                pointObj.put("longitude", points.get(i).longitude);
                pointObj.put("name", points.get(i).name);
                pointObj.put("modificationDate", points.get(i).modificationDate);
                pointObj.put("description", points.get(i).description);
                pointObj.put("depth", points.get(i).depth);
                pointArray.put(pointObj);
            }
            writeFileOnInternalStorage(pointArray.toString());

        } catch (Exception e) {
            Log.d(TAG, "Error parsing points to file!");
            e.printStackTrace();
        }
    }

    public void loadPointsFromFile() {
        String fileContent = "";
        String line = "";
        File dir = new File(MainActivity.context.getFilesDir(), dirFilename);
        try {
            File readfile = new File(dir, filename);
            BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(readfile)));
            while (line != null) {
                fileContent += line;
                fileContent += "\n";
                line = fr.readLine();
            }
            fr.close();

        } catch (Exception e) {
            Log.d(TAG, "Failed to read points from file!");
            e.printStackTrace();
        }
        points = getListFromString(fileContent);

    }

    LinkedList<GpsPoint> getListFromString(String jsonStr) {
        JSONArray array;
        GpsPoint point;
        LinkedList<GpsPoint> result = new LinkedList<>();
        result.clear();

        try {
            array = new JSONArray(jsonStr);

            for (int i = 0; i < array.length(); i++) {
                point = new GpsPoint();
                point.latitude = ((JSONObject) array.get(i)).getDouble("latitude");
                point.longitude = ((JSONObject) array.get(i)).getDouble("longitude");
                point.name = ((JSONObject) array.get(i)).getString("name");
                point.description = ((JSONObject) array.get(i)).getString("description");
                point.modificationDate = ((JSONObject) array.get(i)).getInt("modificationDate");
                point.depth = ((JSONObject) array.get(i)).getDouble("depth");
                result.add(point);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to parse json from file!");
            e.printStackTrace();
        }
        Log.d(TAG, "getting data from JSON size is: " + result.size());
        return result;
    }


    public void writeFileOnInternalStorage(String sBody) {
        File dir = new File(MainActivity.context.getFilesDir(), dirFilename);
        if (!dir.exists()) {
            dir.mkdir();
        }

        try {
            File gpxfile = new File(dir, filename);
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBody);
            writer.flush();
            writer.close();
            Log.d(TAG, "Write done at " + gpxfile.getAbsolutePath());
        } catch (Exception e) {
            Log.d(TAG, "Failed to write file to drive");
            e.printStackTrace();
        }
    }

    LinkedList<GpsPoint> getPoints() {
        return points;
    }

    public void deletePoint(GpsPoint point) {
        if (points.remove(point)) {
            Log.d(TAG, "Point removed successfully");
            savePointsToFile();
        }
    }

    public void editPoint(GpsPoint refPoint, GpsPoint newPoint) {
        if (refPoint.equals(newPoint)) {
            Log.d(TAG, "No changes, no edit");
            return;
        }
        int idx = points.indexOf(refPoint);
        if (points.remove(refPoint)) {
            Log.d(TAG, "Point edited");
            add(newPoint, idx);
        }
    }
}
