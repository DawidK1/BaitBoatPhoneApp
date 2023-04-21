package com.urmom.baitboatphoneapp;

public class GpsPoint {

    public GpsPoint() {
        depth = 0.0;
        distance = 0.0;
    }

    public double latitude;
    public double longitude;
    public String name;
    public String description;
    public int modificationDate;

    public double depth;
    public double distance;

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GpsPoint other = (GpsPoint) obj;

        boolean equal = true;
        equal &= longitude == other.longitude;
        equal &= latitude == other.latitude;
        equal &= depth == other.depth;
        equal &= name.equals(other.name);
        equal &= description.equals(other.description);
        return  equal;


    }
}
