package com.graphhopper.jsprit.core.util;


import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Abhishek Tripathi
 */

public class GoogleCosts extends AbstractForwardVehicleRoutingTransportCosts {

    private DistanceUnit distanceUnit = DistanceUnit.Kilometer;
    private double speed = 1.;
    private double detour = 1.;
    private Map<Location,Long> locationToIdMap = new HashMap<>();
    private Map<ODPair, Double> ODPairDistance = new HashMap<>();
    private Map<ODPair, Long> ODPairTAT= new HashMap<>();

    public void setSpeed(double speed) {
        this.speed = speed;
    }
    public void setDetour(double detour) {
        this.detour = detour;
    }

   public GoogleCosts() {
       super();
       createLocationToIdMapping();
       fetchSectionalTatData();
    }

    public GoogleCosts(DistanceUnit distanceUnit) {
        super();
        createLocationToIdMapping();
        fetchSectionalTatData();
        this.distanceUnit = distanceUnit;
    }

    public Map<Location, Long> getLocationToIdMap() {
        return locationToIdMap;
    }


    @Override
    public double getTransportCost(Location from, Location to, double time, Driver driver, Vehicle vehicle) {
        double distance;
        try {
            distance = calculateDistance(from, to);
        } catch (NullPointerException e) {
            throw new NullPointerException("Cannot calculate distance as coordinates are missing. Either add coordinates or use another transport-cost-calculator.");
        }
        double costs = distance;
        if (vehicle != null) {
            if (vehicle.getType() != null) {
                costs = distance * vehicle.getType().getVehicleCostParams().perDistanceUnit;
            }
        }
        return costs;
    }

    @Override
    public double getTransportTime(Location fromLocation, Location toLocation, double time, Driver driver, Vehicle vehicle) {
        if (fromLocation.getCoordinate() == null || toLocation.getCoordinate() == null)
            throw new NullPointerException("either from or to location is null");
//        return GoogleDistanceCalculator.calculateDistance(from, to, distanceUnit) * detour;
        return ODPairTAT.get(ODPair.newInstance(locationToIdMap.get(fromLocation),locationToIdMap.get(toLocation)))/3600000.0;
    }

    @Override
    public double getDistance(Location fromLocation, Location toLocation, double departureTime, Vehicle vehicle) {
        if (fromLocation.getCoordinate() == null || toLocation.getCoordinate() == null)
            throw new NullPointerException("either from or to location is null");
//        return GoogleDistanceCalculator.calculateDistance(from, to, distanceUnit) * detour;
        return ODPairDistance.get(ODPair.newInstance(locationToIdMap.get(fromLocation),locationToIdMap.get(toLocation)));
    }

    private double calculateDistance(Location fromLocation, Location toLocation) {
        Coordinate from = null;
        Coordinate to = null;
        if (fromLocation.getCoordinate() != null && toLocation.getCoordinate() != null) {
            from = fromLocation.getCoordinate();
            to = toLocation.getCoordinate();
        }
        if (from == null || to == null) throw new NullPointerException("either from or to location is null");
//        return GoogleDistanceCalculator.calculateDistance(from, to, distanceUnit) * detour;
        return ODPairDistance.get(ODPair.newInstance(locationToIdMap.get(fromLocation),locationToIdMap.get(toLocation)));
    }

    private void createLocationToIdMapping() {
        String csvFile = "/home/user/Documents/Rivigo/jsprit/jsprit-examples/src/main/resources/neo4j_location.csv";
        String line = "";
        String cvsSplitBy = ",";

        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while ((line = br.readLine()) != null) {
                // use comma as separator
                if (count==0){
                    count+=1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                Location location = loc(Coordinate.newInstance(Double.parseDouble(data[3]),Double.parseDouble(data[4])));
                Long id = Long.parseLong(data[0]);
                locationToIdMap.put(location,id);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fetchSectionalTatData() {
        String csvFile = "/home/user/Documents/Rivigo/jsprit/jsprit-examples/src/main/resources/sectional_tat.csv";
        String line = "";
        String cvsSplitBy = ",";

        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while ((line = br.readLine()) != null) {
                // use comma as separator
                if (count==0){
                    count+=1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                ODPair odPair = ODPair.newInstance(Long.parseLong(data[0]),Long.parseLong(data[1]));
                Double distance = Double.parseDouble(data[2]);
                Long tat = Long.parseLong(data[3]);
                ODPairDistance.put(odPair,distance);
                ODPairTAT.put(odPair,tat);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Location loc(Coordinate coordinate) {
        return Location.Builder.newInstance().setCoordinate(coordinate).build();
    }

//    public static void main(String args[]) {
//        GoogleCosts googleCosts = new GoogleCosts(DistanceUnit.Kilometer);
//        List<Coordinate> locations = new ArrayList<>();
//        locations.add(Coordinate.newInstance(30.236,76.861));
//        locations.add(Coordinate.newInstance(30.897212,75.8741285));
//        locations.add(Coordinate.newInstance(30.684431,76.823187));
//        locations.add(Coordinate.newInstance(31.625029,74.918999));
//        locations.add(Coordinate.newInstance(31.349958,75.571194));
//        locations.add(Coordinate.newInstance(29.664056,76.9900082));
//        for (int i=0; i<6; i++) {
//            Coordinate loc1 = locations.get(i);
//            for (int j=0; j<6; j++) {
//                Coordinate loc2 = locations.get(j);
//                double distance = GoogleDistanceCalculator.calculateDistance(loc1, loc2, DistanceUnit.Kilometer);
//                System.out.println(
//                    googleCosts.locationToIdMap.get(loc(loc1)).toString()
//                    +" "+
//                    googleCosts.locationToIdMap.get(loc(loc2)).toString()
//                    +" "+
//                    distance
//                );
//            }
//        }
//    }
}
