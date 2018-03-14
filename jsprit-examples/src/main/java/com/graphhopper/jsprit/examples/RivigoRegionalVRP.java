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
import org.apache.commons.math3.util.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RivigoRegionalVRP {

    /**
     * Only INTEGER values allowed
     */
    private enum VehicleFleetType {
        _14_Feet, _17_Feet, _20_Feet, _22_Feet, _32_Feet
    }

    //Vehicle capacity in Kgs
    private static final int _14_Feet_Vehicle_Capacity_In_KGs = 2500;
    private static final int _17_Feet_Vehicle_Capacity_In_KGs = 3000;
    private static final int _20_Feet_Vehicle_Capacity_In_KGs = 4000;
    private static final int _22_Feet_Vehicle_Capacity_In_KGs = 5000;
    private static final int _32_Feet_Vehicle_Capacity_In_KGs = 10000;

    //Transportation Cost in Rs.
    private static final double _14_Feet_Vehicle_Transportation_Cost_Per_Km = 15;
    private static final double _17_Feet_Vehicle_Transportation_Cost_Per_Km = 17;
    private static final double _20_Feet_Vehicle_Transportation_Cost_Per_Km = 20;
    private static final double _22_Feet_Vehicle_Transportation_Cost_Per_Km = 22;
    private static final double _32_Feet_Vehicle_Transportation_Cost_Per_Km = 32;

    //Avg. Vehicle Speed in KMPH
    private static final double avgVehicleSpeedInKMPH = 30;

    //Time in Hours
    private static final double oneHour = 1;
    private static final double oneDay = 24;
    private static final double vehicleDispatchTimeFromPC = 0;
    private static final double pickupServiceTimeForTouchingNode = 1;
    private static final double deliveryServiceTimeForTouchingNode = 1;

    //Input Data
    private static String salesPlanCSVFile = "/home/user/Documents/Rivigo/jsprit/jsprit-examples/src/main/resources/sales_plan.csv";
    private static String neo4JLocationCSVFile = "/home/user/Documents/Rivigo/jsprit/jsprit-examples/src/main/resources/neo4j_location.csv";
    private static List<String> networkNodes = new ArrayList<>();
    private static Map<String,List<String>> pcToBranchMap= new HashMap<>();
    private static Map<Pair<String,String>, Double> salesPlan = new HashMap<>();
    private static Map<String,Coordinate> locationCodeToLatLngMap = new HashMap<>();

    //To ensure branch connectivity to pc
    private static double minWeightToBeHonoured_In_Kgs = 100;

    public static void main(String[] args) {
        init();
        solver(true);
//        parseOutput();
    }

    private static void init() {
        String line = "";
        String cvsSplitBy = ",";
        int count = 0;

        List<String> clusterHeadList = new ArrayList<>();
        List<String> processingCenters = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(salesPlanCSVFile))) {
            while ((line = br.readLine()) != null) {
                // skipping header
                if (count==0){
                    count+=1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                clusterHeadList.add(data[0]);
                processingCenters.add(data[1]);
            }
            networkNodes = processingCenters;
            int nonZeroIndex1=-1;
            int nonZeroIndex2=0;
            for(int i=0; i<clusterHeadList.size(); i++) {
                if (!clusterHeadList.get(i).isEmpty()) {
                    nonZeroIndex1=nonZeroIndex2;
                    nonZeroIndex2=i;
                    if (nonZeroIndex1!=nonZeroIndex2) {
                        List<String> temp = new ArrayList<>();
                        for (int j=nonZeroIndex1; j<nonZeroIndex2; j++) {
                            temp.add(processingCenters.get(j));
                        }
                        pcToBranchMap.put(clusterHeadList.get(nonZeroIndex1),temp);
                    }
                }
            }
            List<String> temp = new ArrayList<>();
            for (int j=nonZeroIndex2; j<clusterHeadList.size(); j++) {
                temp.add(processingCenters.get(j));
            }
            pcToBranchMap.put(clusterHeadList.get(nonZeroIndex2),temp);
        } catch (IOException e) {
            e.printStackTrace();
        }

        count=0;
        try (BufferedReader br = new BufferedReader(new FileReader(salesPlanCSVFile))) {
            int startIndex = 2;
            while ((line = br.readLine()) != null) {
                // skipping header
                if (count==0){
                    count+=1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                for (int i = 0; i< networkNodes.size(); i++) {
                    Pair<String,String> odPair = new Pair<>(data[1], networkNodes.get(i));
                    salesPlan.put(odPair,Double.parseDouble(data[startIndex+i])*1000.0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        count=0;
        try (BufferedReader br = new BufferedReader(new FileReader(neo4JLocationCSVFile))) {
            while ((line = br.readLine()) != null) {
                // skipping header
                if (count==0){
                    count+=1;
                    continue;
                }
                String[] data = line.split(cvsSplitBy);
                locationCodeToLatLngMap.put(data[2],Coordinate.newInstance(Double.parseDouble(data[4]),Double.parseDouble(data[3])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Collection<VehicleImpl> vehicleFactory(VehicleFleetType vehicleFleetType, int count, String locationCode, double vehicleDispatchTimeFromPC) {
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
            VehicleImpl vehicle = VehicleImpl.Builder.newInstance(vehicleFleetType.toString()+"Vehicle_Id:"+i+"-"+locationCode)
                .setType(vehicleTypeFactory)
                .setStartLocation(loc(locationCodeToLatLngMap.get(locationCode)))
                .setEarliestStart(vehicleDispatchTimeFromPC)
                .setLatestArrival(oneDay+vehicleDispatchTimeFromPC+oneHour*5)
                .setReturnToDepot(true)
                .build();
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    private static void solver(Boolean debug) {

        /**
         * Input locations, demands and cutoffs
         */
        String cluster = "AMBT1";
        int[][] demands = getSalesDataOfCluster(cluster);
        List<String> locationCodes = pcToBranchMap.get(cluster);
        List<Coordinate> locationCoordinates = getNodeLocationsOfCluster(cluster);

        int[][] deliveryTimeWindow = new int[][] {{22,28}, {0,14}, {0,13}, {0,13}, {0,14}, {0,13}};
        int[][] pickupTimeWindow = new int[][] {{3,5}, {15,24}, {18,24}, {19,24}, {15,24}, {22,24}};

        if (debug) {
            System.out.println("Solving for Cluster: " + cluster + "\n");
            System.out.print("Cluster Nodes: ");
            for (String node: locationCodes) {
                System.out.print(node+", ");
            }
            System.out.println("\n\nDemands:");
            for (int[] demand: demands) {
                for (int value: demand) {
                    System.out.print(value + " ");
                }
                System.out.println();
            }
            System.out.println("");
            System.out.println("Pickup Window, Delivery Window");
            for (int i=0; i<locationCodes.size(); i++) {
                System.out.println(locationCodes.get(i)+ ": ["+pickupTimeWindow[i][0]+","
                    +pickupTimeWindow[i][1]+"], ["+deliveryTimeWindow[i][0]+","+deliveryTimeWindow[i][1]+"]");
            }
            System.out.println();
            Examples.createOutputFolder();
        }

        Collection<Shipment> shipments = new ArrayList<>();
        for (int day=0; day<1; day++) {
            for (int from = 0; from < locationCoordinates.size(); from++) {
                for (int to = 0; to < locationCoordinates.size(); to++) {
                    if (from != to && demands[from][to] != 0) {
                        List<Integer> splitDemand = splitDemand(demands[from][to]);
                        for (int demand=0; demand < splitDemand.size(); demand++) {
                            Shipment.Builder shipmentBuilder = Shipment.Builder
                                .newInstance("Day" + day + "." + locationCodes.get(from) + " - Day:" + day + "." + locationCodes.get(to) + ", ShipmentNo:"+(demand+1))
                                .addSizeDimension(0, splitDemand.get(demand))
                                .setPickupLocation(loc(locationCoordinates.get(from)))
                                .setDeliveryLocation(loc(locationCoordinates.get(to)))
                                /**
                                 * Time Window Constraints
                                 */
                                // For touching nodes
                                .setPickupTimeWindow(TimeWindow.newInstance(pickupTimeWindow[from][0] + (oneDay * day), pickupTimeWindow[from][1] + (oneDay * day)))
                                .setPickupServiceTime(pickupServiceTimeForTouchingNode)
                                .setDeliveryTimeWindow(TimeWindow.newInstance(deliveryTimeWindow[to][0] + (oneDay * day), deliveryTimeWindow[to][1] + (oneDay * day)))
                                .setDeliveryServiceTime(deliveryServiceTimeForTouchingNode);

                            // Override service time for destination/source PC
                            if (from == 0) {
                                shipmentBuilder.setPickupServiceTime(0);
                            }
                            if (to == 0) {
                                shipmentBuilder.setDeliveryServiceTime(deliveryServiceTimeForTouchingNode);
                            }
                            // Extending window by 1 day for intra-cluster movement
                            if (from != 0 && to != 0) {
                                shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(deliveryTimeWindow[to][0] + (oneDay * (day + 1)),
                                    deliveryTimeWindow[to][1] + (oneDay * (day + 1))));
                            }
                            Shipment shipment = shipmentBuilder.build();
                            shipments.add(shipment);
                        }
                    }
                }
            }
        }

        /**
         * setup problem
		 */
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        for (String locationCode: locationCodes) {
            vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._14_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
            vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._17_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
            vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._20_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
            vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._22_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
            vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._32_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
        }

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

    private static void parseOutput() {
        try {
            File inputFile = new File("/home/user/Documents/Rivigo/jsprit/output/mixed-shipments-services-problem-with-solution.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();
            NodeList solutions = doc.getElementsByTagName("solution");
            Node firstSolutionNode = solutions.item(0);
            System.out.println("----------------------------");
            Element firstSolutionElement = (Element) firstSolutionNode;
//            System.out.println("Root element : " + doc.getDocumentElement().getNodeName());
            System.out.println("Solution details:\n");
            System.out.println("Cost: Rs. "+ firstSolutionElement.getElementsByTagName("cost").item(0).getTextContent());
            NodeList routeList = firstSolutionElement.getElementsByTagName("route");
            System.out.println("Vehicles: "+routeList.getLength());
            System.out.println("--------------------------------------------------------------------");
            for (int route = 0; route < routeList.getLength(); route++) {
                Node routeNode = routeList.item(route);
                if (routeNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element routeNodeElement = (Element) routeNode;
                    System.out.println("Vehicle Id: "
                        + routeNodeElement
                        .getElementsByTagName("vehicleId")
                        .item(0)
                        .getTextContent());
                    System.out.println("Start Time: "
                        + routeNodeElement
                        .getElementsByTagName("start")
                        .item(0)
                        .getTextContent()+"\n");
                    NodeList actList = routeNodeElement.getElementsByTagName("act");
                    for (int act = 0; act < actList.getLength(); act++) {
                        Node actNode = actList.item(act);
                        if (actNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element actNodeElement = (Element) actNode;
                            System.out.println("Shipment Type: "
                                +actNodeElement.getAttribute("type"));
                            System.out.println("Shipment Id: "
                                + actNodeElement
                                .getElementsByTagName("shipmentId")
                                .item(0)
                                .getTextContent());
                            System.out.println("Arrival Time: "
                                + actNodeElement
                                .getElementsByTagName("arrTime")
                                .item(0)
                                .getTextContent());
                            System.out.println("Departure Time: "
                                + actNodeElement
                                .getElementsByTagName("endTime")
                                .item(0)
                                .getTextContent());
                        }
                        System.out.println();
                    }
                    System.out.println("End Time: "
                        + routeNodeElement
                        .getElementsByTagName("end")
                        .item(0)
                        .getTextContent());
                    System.out.println("--------------------------------------------------------------------");
                }
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        String csvFile = "/Users/mkyong/csv/abc.csv";
//        FileWriter writer = new FileWriter(csvFile);
//
//        CSVUtils.writeLine(writer, Arrays.asList("a", "b", "c", "d"));
//
//        //custom separator + quote
//        CSVUtils.writeLine(writer, Arrays.asList("aaa", "bb,b", "cc,c"), ',', '"');
//
//        //custom separator + quote
//        CSVUtils.writeLine(writer, Arrays.asList("aaa", "bbb", "cc,c"), '|', '\'');
//
//        //double-quotes
//        CSVUtils.writeLine(writer, Arrays.asList("aaa", "bbb", "cc\"c"));
//
//
//        writer.flush();
//        writer.close();

    }

    private static Location loc(Coordinate coordinate) {
        return Location.Builder.newInstance().setCoordinate(coordinate).build();
    }

    private static List<Coordinate> getNodeLocationsOfCluster(String pc) {

        List<Coordinate> locations = new ArrayList<>();
        for (String node: pcToBranchMap.get(pc)) {
            locations.add(locationCodeToLatLngMap.get(node));
        }
        return locations;
    }

    private static int[][] getSalesDataOfCluster(String pc) {

        int[][] demands = new int[pcToBranchMap.get(pc).size()][pcToBranchMap.get(pc).size()];
        for (int i=1; i<demands.length; i++) {
            demands[0][i] = (int) getTotalDeliveryAtNode(pcToBranchMap.get(pc).get(i));
            demands[i][0] = (int) getTotalPickupAtNode(pcToBranchMap.get(pc).get(i));
        }
        return demands;
    }

    private static List<Integer> splitDemand(int demand) {

        List<Integer> splitDemand = new ArrayList<>();
        while (demand>_32_Feet_Vehicle_Capacity_In_KGs) {
            splitDemand.add(_32_Feet_Vehicle_Capacity_In_KGs);
            demand -= _32_Feet_Vehicle_Capacity_In_KGs;
        }
        splitDemand.add(demand);
        return splitDemand;
    }

    private static double getTotalPickupAtNode(String sourceNode) {

        double pickup=0;
        for (String node: networkNodes) {
            if(!sourceNode.equalsIgnoreCase(node)) {
                Pair<String, String> odPair = new Pair<>(sourceNode, node);
                pickup+=salesPlan.get(odPair);
            }
        }
        return pickup>0?pickup:minWeightToBeHonoured_In_Kgs;
    }

    private static double getTotalDeliveryAtNode(String sourceNode) {

        double delivery=0;
        for (String node: networkNodes) {
            if (!sourceNode.equalsIgnoreCase(node)){
                Pair<String, String> odPair = new Pair<>(node, sourceNode);
                delivery+=salesPlan.get(odPair);
            }
        }
        return delivery>0?delivery:minWeightToBeHonoured_In_Kgs;
    }

    private static double getTotalShipmentFromNodeToCluster(String node, String pc) {

        double shipment=0;
        for (String branchOrPc: pcToBranchMap.get(pc)) {
            Pair<String, String> odPair = new Pair<>(node, branchOrPc);
            shipment+=salesPlan.get(odPair);
        }
        return shipment;
    }

    private static double getTotalShipmentFromClusterToNode(String pc, String node) {

        double shipment=0;
        for (String branchOrPc: pcToBranchMap.get(pc)) {
            Pair<String, String> odPair = new Pair<>(branchOrPc, node);
            shipment+=salesPlan.get(odPair);
        }
        return shipment;
    }
}
