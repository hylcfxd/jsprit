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
import com.graphhopper.jsprit.core.util.GreatCircleCosts;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import com.graphhopper.jsprit.util.Examples;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RivigoRegionalVRP {

    public static List<Double> createLocation(Double lat, Double lng) {
        List<Double> loc = new ArrayList<>();
        loc.add(lat);
        loc.add(lng);
        return loc;
    }

    public static void main(String[] args) {
        solver();
    }

    public static void solver() {

        final int dispatchTime = 9;
        final int _20_Feet_Vehicle_Transportation_Cost_Per_Km = 20;
        final int _32_Feet_Vehicle_Transportation_Cost_Per_Km = 32;

        /**
         * Input locations and demands
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
        };


        Examples.createOutputFolder();

        /**
         * Vehicle Type Builder Factory
         */

        VehicleTypeImpl.Builder _20FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance("20_Feet_VehicleType")
                                                        .addCapacityDimension(0, 50)
                                                        .setCostPerDistance(_20_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_20Feet = _20FeetVehicleBuilder.build();

        VehicleTypeImpl.Builder _32FeetVehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("32_Feet_VehicleType")
                                                            .addCapacityDimension(0, 70)
                                                            .setCostPerDistance(_32_Feet_Vehicle_Transportation_Cost_Per_Km);
        VehicleType vehicleType_32Feet = _32FeetVehicleTypeBuilder.build();


        /**
         * Vehicle Builder Factory
         */
        VehicleImpl _20FeetVehicle_1 = VehicleImpl.Builder.newInstance("20FeetVehicle_Id:1")
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setType(vehicleType_20Feet)
                                        .build();

        VehicleImpl _20FeetVehicle_2 = VehicleImpl.Builder.newInstance("20FeetVehicle_Id:2")
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setType(vehicleType_20Feet)
                                        .build();


        VehicleImpl _32FeetVehicle_1 = VehicleImpl.Builder.newInstance("32FeetVehicle_Id:1")
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setType(vehicleType_32Feet)
                                        .build();

        VehicleImpl _32FeetVehicle_2 = VehicleImpl.Builder.newInstance("32FeetVehicle_Id:2")
                                        .setStartLocation(loc(Coordinate.newInstance(locations.get(0).get(1),locations.get(0).get(0))))
                                        .setType(vehicleType_32Feet)
                                        .build();

        Collection<Shipment> shipments = new ArrayList<>();

        for (int i=0; i<demands.length; i++) {
            for (int j=0; j<demands.length; j++) {
                if (i != j && demands[i][j] != 0) {
                    Shipment.Builder shipmentBuilder = Shipment.Builder.newInstance(i+" to "+j).addSizeDimension(0,demands[i][j])
                        .setPickupLocation(loc(Coordinate.newInstance(locations.get(i).get(1), locations.get(i).get(0))))
                        .setDeliveryLocation(loc(Coordinate.newInstance(locations.get(j).get(1), locations.get(j).get(0))))
                        .setPickupServiceTime(1)
                        .setDeliveryServiceTime(1)
                        ;

//                    if (i==0 && j==1) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,14));
//                    }
//                    else if (i==0 && j==2) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13));
//                    }
//                    else if (i==0 && j==3) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13));
//                    }
//                    else if (i==0 && j==5) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13));
//                    }
//                    else if (i==5 && j==0) {
//                        shipmentBuilder.setPickupTimeWindow(TimeWindow.newInstance(14,16));
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,22));
//                    }
//                    else if (i==5 && j==2) {
//                        shipmentBuilder.setPickupTimeWindow(TimeWindow.newInstance(14,16));
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13));
//                    }

//                    if (i==0 && j==1) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,14-dispatchTime));
//                    }
//                    else if (i==0 && j==2) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13-dispatchTime));
//                    }
//                    else if (i==0 && j==3) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13-dispatchTime));
//                    }
//                    else if (i==0 && j==5) {
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13-dispatchTime));
//                    }
//                    else if (i==5 && j==0) {
//                        shipmentBuilder.setPickupTimeWindow(TimeWindow.newInstance(14-dispatchTime,16-dispatchTime));
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,22-dispatchTime));
//                    }
//                    else if (i==5 && j==2) {
//                        shipmentBuilder.setPickupTimeWindow(TimeWindow.newInstance(14-dispatchTime,16-dispatchTime));
//                        shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(0,13-dispatchTime));
//                    }

                    Shipment shipment = shipmentBuilder.build();
                    shipments.add(shipment);
                }
            }
        }

        /**
         * setup problem
		 */

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addVehicle(_20FeetVehicle_1);
        vrpBuilder.addVehicle(_20FeetVehicle_2);
        vrpBuilder.addVehicle(_32FeetVehicle_1);
        vrpBuilder.addVehicle(_32FeetVehicle_2);
        vrpBuilder.addAllJobs(shipments);
//        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.INFINITE);
        GreatCircleCosts greatCircleCosts = new GreatCircleCosts(DistanceUnit.Kilometer);
        greatCircleCosts.setSpeed(50);
        vrpBuilder.setRoutingCost(greatCircleCosts);
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

    private static Location loc(Coordinate coordinate) {
        return Location.Builder.newInstance().setCoordinate(coordinate).build();
    }

}
