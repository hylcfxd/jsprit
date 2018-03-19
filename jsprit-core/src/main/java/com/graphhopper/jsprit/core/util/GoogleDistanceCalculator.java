package com.graphhopper.jsprit.core.util;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.LatLng;

/**
 * Created by Abhishek Tripathi on 03.05.18.
 */
public class GoogleDistanceCalculator {

    public static double calculateDistance(Coordinate coord1, Coordinate coord2, DistanceUnit distanceUnit) {
        double lat1 = coord1.getY();
        double lat2 = coord2.getY();
        double lon1 = coord1.getX();
        double lon2 = coord2.getX();

//        String API_KEY = "AIzaSyAR-giu_izTcgeHcEdcgCrFmP5XaG-qHkA";
        String API_KEY = "AIzaSyAxSIcrUds16uJkTXcYJFdEaiBNC0xetoU";
        GeoApiContext geoApiContext = new GeoApiContext.Builder().apiKey(API_KEY).build();

        LatLng origin = new LatLng(lat1,lon1);
        LatLng destination = new LatLng(lat2,lon2);
        double distance;
        try {
            DistanceMatrix distanceMatrix = DistanceMatrixApi.newRequest(geoApiContext)
                                            .origins(origin)
                                            .destinations(destination)
                                            .await();

            if( distanceMatrix.rows.length == 0 ||
                distanceMatrix.rows[0].elements.length == 0 )
                throw new RuntimeException("No distance and duration found.");

           distance = distanceMatrix.rows[0].elements[0].distance.inMeters;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        if (distanceUnit.equals(DistanceUnit.Kilometer)) {
            distance = distance / 1000.0;
        }
        return distance;
    }
}
