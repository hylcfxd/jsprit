package com.graphhopper.jsprit.examples;

import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.ServiceDeliveriesFirstConstraint;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.DistanceUnit;
import com.graphhopper.jsprit.core.util.GoogleCosts;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import com.graphhopper.jsprit.util.Examples;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RivigoRegionalVRP {

    /**
     * Only INTEGER values allowed
     */
    private enum VehicleFleetType {
        _14_Feet, _17_Feet, _20_Feet, _22_Feet, _32_Feet;
    }

    //Vehicle capacity in Kgs
    private static final int _14_Feet_Vehicle_Capacity_In_KGs = 2500;
    private static final int _17_Feet_Vehicle_Capacity_In_KGs = 3000;
    private static final int _20_Feet_Vehicle_Capacity_In_KGs = 4000;
    private static final int _22_Feet_Vehicle_Capacity_In_KGs = 5000;
    private static final int _32_Feet_Vehicle_Capacity_In_KGs = 10000;

    //Transportation Cost in Rs.
    private static final int _14_Feet_Vehicle_Transportation_Cost_Per_Km = 15;
    private static final int _17_Feet_Vehicle_Transportation_Cost_Per_Km = 17;
    private static final int _20_Feet_Vehicle_Transportation_Cost_Per_Km = 20;
    private static final int _22_Feet_Vehicle_Transportation_Cost_Per_Km = 22;
    private static final int _32_Feet_Vehicle_Transportation_Cost_Per_Km = 32;

    //Avg. Vehicle Speed in KMPH
    private static final int avgVehicleSpeedInKMPH = 30;

    //Time in Hours
    private static final int vehicleDispatchTimeFromPC = 5;
    private static final int pickupServiceTime = 1;
    private static final int deliveryServiceTime = 1;
    private static final int oneDay = 24;


    public static void main(String[] args) {
        solver();
    }

    private static List<Double> createLocation(Double lat, Double lng) {
        List<Double> loc = new ArrayList<>();
        loc.add(lat);
        loc.add(lng);
        return loc;
    }

    private static Location loc(Coordinate coordinate) {
        return Location.Builder.newInstance().setCoordinate(coordinate).build();
    }

    private static Collection<VehicleImpl> vehicleFactory(VehicleFleetType vehicleFleetType, int count, List<Double> coordinates) {
        /**
         * Vehicle Type Builder Factory
         */
        VehicleType vehicleTypeFactory = null;
        if (vehicleFleetType.equals(VehicleFleetType._14_Feet)) {
            VehicleTypeImpl.Builder _14FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._14_Feet + "_VehicleType")
                .addCapacityDimension(0, _14_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_14_Feet_Vehicle_Transportation_Cost_Per_Km);
            vehicleTypeFactory = _14FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._17_Feet)) {
            VehicleTypeImpl.Builder _17FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._17_Feet + "_VehicleType")
                .addCapacityDimension(0, _17_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_17_Feet_Vehicle_Transportation_Cost_Per_Km);
            vehicleTypeFactory = _17FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._20_Feet)) {
            VehicleTypeImpl.Builder _20FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._20_Feet + "_VehicleType")
                .addCapacityDimension(0, _20_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_20_Feet_Vehicle_Transportation_Cost_Per_Km);
            vehicleTypeFactory = _20FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._22_Feet)) {
            VehicleTypeImpl.Builder _22FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._22_Feet + "_VehicleType")
                .addCapacityDimension(0, _22_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_22_Feet_Vehicle_Transportation_Cost_Per_Km);
            vehicleTypeFactory = _22FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._32_Feet)) {
            VehicleTypeImpl.Builder _32FeetVehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._32_Feet + "_VehicleType")
                .addCapacityDimension(0, _32_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_32_Feet_Vehicle_Transportation_Cost_Per_Km);
            vehicleTypeFactory = _32FeetVehicleTypeBuilder.build();
        }

        /**
         * Vehicle Builder Factory
         */
        Collection<VehicleImpl> vehicles = new ArrayList<>();
        for (int i=1; i<=count; i++) {
            VehicleImpl vehicle = VehicleImpl.Builder.newInstance(vehicleFleetType.toString()+"Vehicle_Id:"+i)
                .setType(vehicleTypeFactory)
                .setStartLocation(loc(Coordinate.newInstance(coordinates.get(1), coordinates.get(0))))
                .setEarliestStart(vehicleDispatchTimeFromPC)
//                .setLatestArrival(24)
                .build();
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    private static void solver() {

        /**
         * Input locations, demands and cutoffs
         */
        List<List<Double>> locations = new ArrayList<>();
        locations.add(createLocation(30.236,76.861));
        locations.add(createLocation(31.625029,74.918999));
        locations.add(createLocation(30.684431,76.823187));
        locations.add(createLocation(31.349958,75.571194));
        locations.add(createLocation(29.664056,76.9900082));
        locations.add(createLocation(30.897212,75.8741285));

        int[][] demands = new int[][] {
            {0,1,3,1,0,2},
            {0,0,0,0,0,0},
            {0,0,0,0,0,0},
            {0,0,0,0,0,0},
            {0,0,0,0,0,0},
            {1,0,1,0,0,0}

//            {206, 60, 271, 64, 16, 216},
//            {4, 1, 5, 1, 0, 4},
//            {6, 1, 8, 2, 0, 7},
//            {27, 7, 35, 8, 2, 28},
//            {0, 0, 0, 0, 0, 0},
//            {107, 31, 141, 33, 8, 113}
        };

        int[][] deliveryTimeWindow = new int[][] {{0, 22}, {0, 14}, {0,13}, {0,13}, {0,14}, {0,13}};
        int[][] pickupTimeWindow = new int[][] {{0,24}, {0,17}, {0,17}, {0,17}, {0,17}, {0,17}};

        Examples.createOutputFolder();

        Collection<Shipment> shipments = new ArrayList<>();
        for (int i=0; i<locations.size(); i++) {
            for (int j=0; j<locations.size(); j++) {
                if (i != j && demands[i][j] != 0) {
                    Shipment.Builder shipmentBuilder = Shipment.Builder.newInstance(i+" to "+j).addSizeDimension(0,demands[i][j])
                        .setPickupLocation(loc(Coordinate.newInstance(locations.get(i).get(1), locations.get(i).get(0))))
                        .setDeliveryLocation(loc(Coordinate.newInstance(locations.get(j).get(1), locations.get(j).get(0))))
                        /**
                         * Time Window Constraints
                         */
                        .setDeliveryServiceTime(deliveryServiceTime)
                        .setDeliveryTimeWindow(TimeWindow.newInstance(deliveryTimeWindow[j][0], deliveryTimeWindow[j][1]));
                        if (i!=0) {
                            shipmentBuilder.setPickupServiceTime(pickupServiceTime)
                                .setPickupTimeWindow(TimeWindow.newInstance(pickupTimeWindow[j][0], pickupTimeWindow[j][1]));
                        }
                    Shipment shipment = shipmentBuilder.build();
                    shipments.add(shipment);
                }
            }
        }

        /**
         * setup problem
		 */
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._14_Feet, 1, locations.get(0)));
        vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._17_Feet, 1, locations.get(0)));
        vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._20_Feet, 1, locations.get(0)));
        vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._22_Feet, 1, locations.get(0)));
        vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._32_Feet, 1, locations.get(0)));
        vrpBuilder.addAllJobs(shipments);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        GoogleCosts googleCosts = new GoogleCosts(DistanceUnit.Kilometer);
        googleCosts.setSpeed(avgVehicleSpeedInKMPH);
        vrpBuilder.setRoutingCost(googleCosts);
        VehicleRoutingProblem problem = vrpBuilder.build();

		/**
         * build the algorithm
		 */
        StateManager stateManager = new StateManager(problem);
        ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);
        constraintManager.addConstraint(new ServiceDeliveriesFirstConstraint(), ConstraintManager.Priority.CRITICAL);

        VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(problem)
                                            .setStateAndConstraintManager(stateManager,constraintManager)
                                            .buildAlgorithm();

//        algorithm.setMaxIterations(10000);
		/**
         * and search a solution
		 */
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();

		/**
		 * get the best
		 */
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        new VrpXMLWriter(problem, solutions).write("output/mixed-shipments-services-problem-with-solution.xml");

        SolutionPrinter.print(bestSolution);

		/**
		 * plot
		 */
        Plotter problemPlotter = new Plotter(problem);
        problemPlotter.plotShipments(true);
        problemPlotter.plot("output/simpleMixedEnRoutePickupAndDeliveryExample_problem.png", "en-route pd and depot bounded deliveries");

        Plotter solutionPlotter = new Plotter(problem, Solutions.bestOf(solutions));
        solutionPlotter.plotShipments(true);
        solutionPlotter.plot("output/simpleMixedEnRoutePickupAndDeliveryExample_solution.png", "en-route pd and depot bounded deliveries");
    }
}
