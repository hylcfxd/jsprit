package com.rivigo.network;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.LatLng;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.util.DistanceUnit;

/**
 * Created by Abhishek Tripathi on 03.05.18.
 */
public class GoogleDistanceCalculator {

    public static double calculateDistance(Location loc1, Location loc2, DistanceUnit distanceUnit) {
        double lat1 = loc1.getCoordinate().getY();
        double lat2 = loc2.getCoordinate().getY();
        double lon1 = loc1.getCoordinate().getX();
        double lon2 = loc2.getCoordinate().getX();

        String API_KEY = "AIzaSyAR-giu_izTcgeHcEdcgCrFmP5XaG-qHkA"; // Abhishek Tripathi's
        API_KEY = "AIzaSyAxSIcrUds16uJkTXcYJFdEaiBNC0xetoU"; // Abhishek Tripathi's
        API_KEY = "AIzaSyDO6vXXGy97o6g573XEjN1SXgaFwsRz-Qk"; // Rivigo's
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
