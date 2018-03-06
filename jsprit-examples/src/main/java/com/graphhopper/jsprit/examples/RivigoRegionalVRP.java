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

    private static void solver() {

        /**
         * Only INTEGER values allowed
         */
        //Time in Hours
        final int vehicleDispatchTimeFromPC = 5;
        final int pickupServiceTime = 1;
        final int deliveryServiceTime = 1;
        final int regionalTat = 24;
        final int oneDay = 24;

        //Avg. Vehicle Speed in KMPH
        final int avgVehicleSpeedInKMPH = 30;

        //Vehicle capacity in Kgs
        final int _14_Feet_Vehicle_Capacity_In_KGs = 2500;
        final int _17_Feet_Vehicle_Capacity_In_KGs = 3000;
        final int _20_Feet_Vehicle_Capacity_In_KGs = 4000;
        final int _22_Feet_Vehicle_Capacity_In_KGs = 5000;
        final int _32_Feet_Vehicle_Capacity_In_KGs = 10000;

        //Transportation Cost in Rs.
        final int _14_Feet_Vehicle_Transportation_Cost_Per_Km = 15;
        final int _17_Feet_Vehicle_Transportation_Cost_Per_Km = 17;
        final int _20_Feet_Vehicle_Transportation_Cost_Per_Km = 20;
        final int _22_Feet_Vehicle_Transportation_Cost_Per_Km = 22;
        final int _32_Feet_Vehicle_Transportation_Cost_Per_Km = 32;

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

        /**
         * Vehicle Type Builder Factory
         */
        VehicleTypeImpl.Builder _14FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance("14_Feet_VehicleType")
                                                        .addCapacityDimension(0, _14_Feet_Vehicle_Capacity_In_KGs)
                                                        .setCostPerDistance(_14_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_14Feet = _14FeetVehicleBuilder.build();

        VehicleTypeImpl.Builder _17FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance("17_Feet_VehicleType")
                                                        .addCapacityDimension(0, _17_Feet_Vehicle_Capacity_In_KGs)
                                                        .setCostPerDistance(_17_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_17Feet = _17FeetVehicleBuilder.build();

        VehicleTypeImpl.Builder _20FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance("20_Feet_VehicleType")
                                                        .addCapacityDimension(0, _20_Feet_Vehicle_Capacity_In_KGs)
                                                        .setCostPerDistance(_20_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_20Feet = _20FeetVehicleBuilder.build();

        VehicleTypeImpl.Builder _22FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance("22_Feet_VehicleType")
                                                        .addCapacityDimension(0, _22_Feet_Vehicle_Capacity_In_KGs)
                                                        .setCostPerDistance(_22_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_22Feet = _22FeetVehicleBuilder.build();

        VehicleTypeImpl.Builder _32FeetVehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("32_Feet_VehicleType")
                                                            .addCapacityDimension(0, _32_Feet_Vehicle_Capacity_In_KGs)
                                                            .setCostPerDistance(_32_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_32Feet = _32FeetVehicleTypeBuilder.build();


        /**
         * Vehicle Builder Factory
         */
        VehicleImpl _14FeetVehicle_1 = VehicleImpl.Builder.newInstance("14FeetVehicle_Id:1").setType(vehicleType_14Feet)
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setEarliestStart(vehicleDispatchTimeFromPC)
//                                        .setLatestArrival(24)
                                        .build();

        VehicleImpl _17FeetVehicle_1 = VehicleImpl.Builder.newInstance("17FeetVehicle_Id:1").setType(vehicleType_17Feet)
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setEarliestStart(vehicleDispatchTimeFromPC)
//                                        .setLatestArrival(24)
                                        .build();

        VehicleImpl _20FeetVehicle_1 = VehicleImpl.Builder.newInstance("20FeetVehicle_Id:1").setType(vehicleType_20Feet)
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setEarliestStart(vehicleDispatchTimeFromPC)
//                                        .setLatestArrival(24)
                                        .build();

        VehicleImpl _22FeetVehicle_1 = VehicleImpl.Builder.newInstance("22FeetVehicle_Id:1").setType(vehicleType_22Feet)
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setEarliestStart(vehicleDispatchTimeFromPC)
//                                        .setLatestArrival(24)
                                        .build();

        VehicleImpl _32FeetVehicle_1 = VehicleImpl.Builder.newInstance("32FeetVehicle_Id:1").setType(vehicleType_32Feet)
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setEarliestStart(vehicleDispatchTimeFromPC)
//                                        .setLatestArrival(24)
                                        .build();

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
                        if (i!=0)
                            shipmentBuilder.setPickupServiceTime(pickupServiceTime)
                                            .setPickupTimeWindow(TimeWindow.newInstance(pickupTimeWindow[j][0],pickupTimeWindow[j][1]));

                    Shipment shipment = shipmentBuilder.build();
                    shipments.add(shipment);
                }
            }
        }

        /**
         * setup problem
		 */
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addVehicle(_14FeetVehicle_1);
        vrpBuilder.addVehicle(_17FeetVehicle_1);
        vrpBuilder.addVehicle(_20FeetVehicle_1);
        vrpBuilder.addVehicle(_22FeetVehicle_1);
        vrpBuilder.addVehicle(_32FeetVehicle_1);
        vrpBuilder.addAllJobs(shipments);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        GoogleCosts googleCosts = new GoogleCosts(DistanceUnit.Kilometer);
        googleCosts.setSpeed(avgVehicleSpeedInKMPH);
        vrpBuilder.setRoutingCost(googleCosts);
//        GreatCircleCosts greatCircleCosts = new GreatCircleCosts(DistanceUnit.Kilometer);
//        greatCircleCosts.setSpeed(avgVehicleSpeedInKMPH);
//        vrpBuilder.setRoutingCost(greatCircleCosts);
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
