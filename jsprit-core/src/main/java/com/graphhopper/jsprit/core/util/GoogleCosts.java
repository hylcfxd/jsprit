package com.graphhopper.jsprit.core.util;


import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
       init();
    }

    public GoogleCosts(DistanceUnit distanceUnit, Double speed) {
        super();
        this.distanceUnit = distanceUnit;
        this.speed = speed;
        init();
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

    private void init() {

        /**
         * Create in-memory maps
         */
        String csvFile = "jsprit-examples/src/main/resources/neo4j_location.csv";
        String line = "";
        String cvsSplitBy = ",";

        List<Location> locations = new ArrayList<>();
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while ((line = br.readLine()) != null) {
                // use comma as separator
                if (count == 0) {
                    count += 1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                Location location = loc(Coordinate.newInstance(Double.parseDouble(data[4]), Double.parseDouble(data[3])));
                locations.add(location);
                Long id = Long.parseLong(data[0]);
                locationToIdMap.put(location, id);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        csvFile = "jsprit-examples/src/main/resources/tats.csv";
        count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while ((line = br.readLine()) != null) {
                // use comma as separator
                if (count == 0) {
                    count += 1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                ODPair odPair = ODPair.newInstance(Long.parseLong(data[0]), Long.parseLong(data[1]));
                ODPairDistance.put(odPair, Double.parseDouble(data[2]));
                if (data.length == 4)
                    ODPairTAT.put(odPair, Long.parseLong(data[3]));
                else
                    ODPairTAT.put(odPair, (long)((Double.parseDouble(data[2]) / speed) * 3600000));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        /**
         * Get google distance between all OD Pairs if it doesn't exist in data-store
         */
        boolean dataMissing = false;
        for (Location loc1: locations) {
            for (Location loc2: locations) {
                ODPair odPair = ODPair.newInstance(locationToIdMap.get(loc1),locationToIdMap.get(loc2));
                if (ODPairDistance.get(odPair) == null) {
                    dataMissing = true;
                    System.out.println("Tats data is missing in data-store");
                    System.exit(1);
                    double distance = GoogleDistanceCalculator.calculateDistance(loc1, loc2, DistanceUnit.Kilometer);
                    System.out.println(locationToIdMap.get(loc1).toString()+ " " +locationToIdMap.get(loc2).toString()+ " " +distance);
                }
            }
        }
        if(dataMissing) {
            System.out.println("Above data is missing in data-store");
            System.exit(2);
        }
    }

    private static Location loc(Coordinate coordinate) {
        return Location.Builder.newInstance().setCoordinate(coordinate).build();
    }

    public static void main(String[] args) {
        GoogleCosts googleCosts = new GoogleCosts(DistanceUnit.Kilometer, 35.0);
    }
}
