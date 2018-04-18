package com.rivigo.network;

import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.ServiceDeliveriesFirstConstraint;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.DistanceUnit;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import com.graphhopper.jsprit.util.Examples;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.util.Pair;
import org.javatuples.Quartet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class RivigoRegionalVRP {

    /**
     * Only INTEGER values allowed
     */
    private enum VehicleFleetType {
        _14_Feet, _17_Feet, _20_Feet, _22_Feet, _24_Feet, _32_Feet
    }

    //Vehicle capacity in Kgs
    private static final int _14_Feet_Vehicle_Capacity_In_KGs = 2500;
    private static final int _17_Feet_Vehicle_Capacity_In_KGs = 3000;
    private static final int _20_Feet_Vehicle_Capacity_In_KGs = 4000;
    private static final int _22_Feet_Vehicle_Capacity_In_KGs = 5000;
    private static final int _24_Feet_Vehicle_Capacity_In_KGs = 6000;
    private static final int _32_Feet_Vehicle_Capacity_In_KGs = 10000;

    //Transportation Cost in Rs.
    private static final double _14_Feet_Vehicle_Transportation_Cost_Per_Km = 15;
    private static final double _17_Feet_Vehicle_Transportation_Cost_Per_Km = 17;
    private static final double _20_Feet_Vehicle_Transportation_Cost_Per_Km = 20;
    private static final double _22_Feet_Vehicle_Transportation_Cost_Per_Km = 22;
    private static final double _24_Feet_Vehicle_Transportation_Cost_Per_Km = 24;
    private static final double _32_Feet_Vehicle_Transportation_Cost_Per_Km = 32;

    //Waiting Cost in Rs.
    private static final double waitingTimeCost = 0;

    //Avg. Vehicle Speed in KMPH
    private static final double avgVehicleSpeedInKMPH = 30;

    //Time in Hours
    private static final double oneHour = 1;
    private static final double oneDay = 24;
    private static final double vehicleDispatchTimeFromPC = 0;
    private static final double pickupServiceTimeForTouchingNode = 1;
    private static final double deliveryServiceTimeForTouchingNode = 1;

    //Input Data
    private static String salesPlanCSVFile = "jsprit-examples/src/main/resources/sales_plan.csv";
    private static String neo4JLocationCSVFile = "jsprit-examples/src/main/resources/neo4j_location.csv";
    private static List<String> networkNodes = new ArrayList<>();
    private static Map<String,List<String>> pcToBranchMap= new HashMap<>();
    private static Map<Pair<String,String>, Double> salesPlan = new HashMap<>();
    private static Map<String,Coordinate> locationCodeToLatLngMap = new HashMap<>();
    private static Map<String,Pair<Integer,Integer>> locationCodeToTimeCutoffsMap = new HashMap<>();

    //To ensure branch connectivity to pc
    private static double minWeightToBeHonoured_In_Kgs = 100;

    //Other hyper-parameters
    private static int maxIterations = 200;

    public static void main (String[] args) {
        init();
        for (String cluster: pcToBranchMap.keySet()) {
            String clusterHeadCode = "AMBT1";
            try (
                BufferedWriter writer = new BufferedWriter(new FileWriter("output/RegionalOutput-" + clusterHeadCode + ".csv"));
                CSVPrinter csvPrinter = new CSVPrinter(writer,CSVFormat.EXCEL.withIgnoreEmptyLines());
            ) {
                List<String> header = new ArrayList<>(Arrays.asList("Cluster", "Iteration", "Network", "Cost", "Vehicles_Required",
                    "Vehicle_Capacity_In_Tonnes", "Route", "Time_Route", "Max_Dispatch_Time_From_PC_For_Last_Vehicle",
                    "Arrival_Time_At_PC", "Round_Trip_Duration", "Impossible_Shipments_For_Day0"));
                for(String node : pcToBranchMap.get(clusterHeadCode)){
                   header.add(node.concat("_Delivery_Day"));
                }
                header.addAll(pcToBranchMap.keySet());
                csvPrinter.printRecord(header);
                networkSolverAlgo(true, csvPrinter, clusterHeadCode);
                System.out.println("RegionalOutput-" + clusterHeadCode + " file created...");
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        }
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
                Pair<Integer, Integer> cutoffs = new Pair<>(Integer.parseInt(data[5])/3600000, Integer.parseInt(data[6])/3600000);
                locationCodeToTimeCutoffsMap.put(data[2],cutoffs);
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
                .setCostPerDistance(_14_Feet_Vehicle_Transportation_Cost_Per_Km)
                .setCostPerWaitingTime(waitingTimeCost);
            vehicleTypeFactory = _14FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._17_Feet)) {
            VehicleTypeImpl.Builder _17FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._17_Feet + "_VehicleType")
                .addCapacityDimension(0, _17_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_17_Feet_Vehicle_Transportation_Cost_Per_Km)
                .setCostPerWaitingTime(waitingTimeCost);
            vehicleTypeFactory = _17FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._20_Feet)) {
            VehicleTypeImpl.Builder _20FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._20_Feet + "_VehicleType")
                .addCapacityDimension(0, _20_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_20_Feet_Vehicle_Transportation_Cost_Per_Km)
                .setCostPerWaitingTime(waitingTimeCost);
            vehicleTypeFactory = _20FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._22_Feet)) {
            VehicleTypeImpl.Builder _22FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._22_Feet + "_VehicleType")
                .addCapacityDimension(0, _22_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_22_Feet_Vehicle_Transportation_Cost_Per_Km)
                .setCostPerWaitingTime(waitingTimeCost);
            vehicleTypeFactory = _22FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._24_Feet)) {
            VehicleTypeImpl.Builder _24FeetVehicleBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._24_Feet + "_VehicleType")
                .addCapacityDimension(0, _24_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_24_Feet_Vehicle_Transportation_Cost_Per_Km)
                .setCostPerWaitingTime(waitingTimeCost);
            vehicleTypeFactory = _24FeetVehicleBuilder.build();
        }

        else if (vehicleFleetType.equals(VehicleFleetType._32_Feet)) {
            VehicleTypeImpl.Builder _32FeetVehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance(VehicleFleetType._32_Feet + "_VehicleType")
                .addCapacityDimension(0, _32_Feet_Vehicle_Capacity_In_KGs)
                .setCostPerDistance(_32_Feet_Vehicle_Transportation_Cost_Per_Km)
                .setCostPerWaitingTime(waitingTimeCost);
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
//                .setLatestArrival(oneDay+vehicleDispatchTimeFromPC+oneHour*5)
                .setReturnToDepot(true)
                .build();
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    private static void networkSolverAlgo(Boolean debug, CSVPrinter csvPrinter, String clusterHeadCode) {

        /**
         * Input locations, demands and cutoffs
         */
        int[][] demands = getSalesDataOfCluster(clusterHeadCode);
        List<String> locationCodes = pcToBranchMap.get(clusterHeadCode);
        int indexOfPc = locationCodes.indexOf(clusterHeadCode);
        List<Coordinate> locationCoordinates = getNodeLocationsOfCluster(clusterHeadCode);

        int lowerTimeBoundForBranch = 0;
        int upperTimeBoundForBranch = 24;
        int lowerTimeBoundForPC = 0;
        int upperTimeBoundForPC = 36;
        int[][] deliveryTimeWindow = new int[locationCodes.size()][2];
        int[][] pickupTimeWindow = new int[locationCodes.size()][2];

        int maxDeliveryCutoffInCluster = 0;
        for (String node: locationCodes) {
            if (node.equalsIgnoreCase(clusterHeadCode)) {
                pickupTimeWindow[locationCodes.indexOf(node)][0] = lowerTimeBoundForPC;
                pickupTimeWindow[locationCodes.indexOf(node)][1] = locationCodeToTimeCutoffsMap.get(node).getSecond();
                deliveryTimeWindow[locationCodes.indexOf(node)][0] = locationCodeToTimeCutoffsMap.get(node).getFirst();
                deliveryTimeWindow[locationCodes.indexOf(node)][1] = upperTimeBoundForPC;

            } else {
                pickupTimeWindow[locationCodes.indexOf(node)][0] = locationCodeToTimeCutoffsMap.get(node).getFirst() + (int) oneHour;
                pickupTimeWindow[locationCodes.indexOf(node)][1] = upperTimeBoundForBranch;
                deliveryTimeWindow[locationCodes.indexOf(node)][0] = lowerTimeBoundForBranch;
                deliveryTimeWindow[locationCodes.indexOf(node)][1] = locationCodeToTimeCutoffsMap.get(node).getSecond();
                if (deliveryTimeWindow[locationCodes.indexOf(node)][1] > maxDeliveryCutoffInCluster)
                    maxDeliveryCutoffInCluster = deliveryTimeWindow[locationCodes.indexOf(node)][1];
            }
        }

        int iteration=0;
        pickupTimeWindow[locationCodes.indexOf(clusterHeadCode)][1] = maxDeliveryCutoffInCluster;
        for (int dispatchTime=lowerTimeBoundForPC; dispatchTime<=maxDeliveryCutoffInCluster; dispatchTime++) {
//        for (int dispatchTime=2; dispatchTime<=2; dispatchTime++) {
            pickupTimeWindow[locationCodes.indexOf(clusterHeadCode)][0] = dispatchTime;
            if (debug) {
                System.out.println("Iteration: "+iteration+"\n");
                System.out.println("Solving for Cluster: " + clusterHeadCode + "\n");
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

            List<String> impossibleDeliveryLocationsForDay0 = new ArrayList<>();
            for(int epoch=0; epoch<=1; epoch++) {
                Collection<Shipment> shipments = new ArrayList<>();
                for (int day = 0; day < 1; day++) {
                    for (int from = 0; from < locationCoordinates.size(); from++) {
                        for (int to = 0; to < locationCoordinates.size(); to++) {
                            if (from != to && demands[from][to] != 0) {
                                List<Integer> splitDemand = splitDemand(demands[from][to]);
                                for (int demand = 0; demand < splitDemand.size(); demand++) {
                                    Shipment.Builder shipmentBuilder = Shipment.Builder
                                        .newInstance("Day" + day + ":" + locationCodes.get(from)
                                            + " - Day" + day + ":" + locationCodes.get(to) + ", ShipmentNo:" + (demand + 1))
                                        .addSizeDimension(0, splitDemand.get(demand))
                                        .setPickupLocation(loc(locationCoordinates.get(from)))
                                        .setDeliveryLocation(loc(locationCoordinates.get(to)))
                                        /**
                                         * Time Window constraints
                                         */
                                        .setPickupTimeWindow(TimeWindow.newInstance(pickupTimeWindow[from][0] + (oneDay * day), pickupTimeWindow[from][1] + (oneDay * day)))
                                        .setDeliveryTimeWindow(TimeWindow.newInstance(deliveryTimeWindow[to][0] + (oneDay * day), deliveryTimeWindow[to][1] + (oneDay * day)))
                                        /**
                                         * Service time for touching nodes
                                         */
                                        .setPickupServiceTime(pickupServiceTimeForTouchingNode)
                                        .setDeliveryServiceTime(deliveryServiceTimeForTouchingNode);

                                    /**
                                     * Relaxing delivery time windows for places where delivery is not possible on Day:0
                                     */
                                    if (epoch==1 && impossibleDeliveryLocationsForDay0.size()>0) {
                                        for(String node: impossibleDeliveryLocationsForDay0) {
                                            if (locationCodes.indexOf(node) == to) {
                                                shipmentBuilder.setDeliveryTimeWindow(
                                                    TimeWindow.newInstance(lowerTimeBoundForBranch, upperTimeBoundForBranch));
                                                break;
                                            }
                                        }
                                    }

                                    /**
                                     * Override pickup time for multiple shipments
                                     */
                                    if (splitDemand.size() > 1) {
                                        shipmentBuilder.setPickupServiceTime(pickupServiceTimeForTouchingNode + (oneHour * 2) * demand);
                                    }
                                    /**
                                     * Override service time for destination/source PC (Being taken care by National Line Hauls)
                                     */
                                    if (from == indexOfPc) {
                                        shipmentBuilder.setPickupServiceTime(0);
                                    }
                                    if (to == indexOfPc) {
                                        shipmentBuilder.setDeliveryServiceTime(0);
                                    }
                                    /**
                                     * Extending window by 1 day for intra-cluster movement
                                     */
                                    if (from != indexOfPc && to != indexOfPc) {
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
                 * Setup problem model
                 */
                VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

                for (String locationCode : locationCodes) {
                    vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._14_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
                    vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._17_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
                    vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._20_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
                    vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._22_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
//                    vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._24_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
                    vrpBuilder.addAllVehicles(vehicleFactory(VehicleFleetType._32_Feet, 10, locationCode, vehicleDispatchTimeFromPC));
                }

                vrpBuilder.addAllJobs(shipments);
                vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
                GoogleCosts googleCosts = new GoogleCosts(DistanceUnit.Kilometer, avgVehicleSpeedInKMPH);
                vrpBuilder.setRoutingCost(googleCosts);
                VehicleRoutingProblem problem = vrpBuilder.build();

                /**
                 * Build the algorithm
                 */
                StateManager stateManager = new StateManager(problem);
                ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);
                constraintManager.addConstraint(new ServiceDeliveriesFirstConstraint(), ConstraintManager.Priority.CRITICAL);

                VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(problem)
                    .setStateAndConstraintManager(stateManager, constraintManager)
                    .buildAlgorithm();
                algorithm.setMaxIterations(maxIterations);

                /**
                 * Search a solution
                 */
                Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();

                /**
                 * Get the best solution
                 */
                VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);
                new VrpXMLWriter(problem, solutions).write("output/mixed-shipments-services-problem-with-solution.xml");
                SolutionPrinter.print(bestSolution);

                /**
                 * Plot
                 */
                Plotter problemPlotter = new Plotter(problem);
                problemPlotter.plotShipments(true);
                problemPlotter.plot("output/simpleMixedEnRoutePickupAndDeliveryExample_problem.png", "Network for "+clusterHeadCode);

                Plotter solutionPlotter = new Plotter(problem, Solutions.bestOf(solutions));
                solutionPlotter.plotShipments(true);
                solutionPlotter.plot("output/simpleMixedEnRoutePickupAndDeliveryExample_solution.png", "Network for "+clusterHeadCode);

                /**
                 * Get details of impossible shipments for Day:0
                 */
                Collection<Job> impossibleShipmentsForDay0 = bestSolution.getUnassignedJobs();
                if (impossibleShipmentsForDay0.size() != 0) {
                    for (Job job: impossibleShipmentsForDay0) {
                        impossibleDeliveryLocationsForDay0.add(parseShipmentIdToGetSourceNode(job.getId(),1));
                    }
                }
                else{
                    break;
                }
            }
            parser(iteration, dispatchTime, clusterHeadCode, impossibleDeliveryLocationsForDay0, csvPrinter);
            iteration++;
        }
    }

    private static void parser(int iteration, double vehicleDispatchTimeFromPC, String clusterHeadCode,
                                    List<String> impossibleDeliveryLocationsForDay0, CSVPrinter csvPrinter) {
        try {
            File inputFile = new File("output/mixed-shipments-services-problem-with-solution.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();
            NodeList solutions = doc.getElementsByTagName("solution");
            Node firstSolutionNode = solutions.item(0);
            System.out.println("----------------------------------------------------------------------------------------");
            Element firstSolutionElement = (Element) firstSolutionNode;
//            System.out.println("Root element : " + doc.getDocumentElement().getNodeName());
            System.out.println("Solution details (Iteration-"+iteration+"):\n");
            System.out.println("Cost: Rs. " + firstSolutionElement.getElementsByTagName("cost").item(0).getTextContent());
            NodeList routeList = firstSolutionElement.getElementsByTagName("route");
            System.out.println("Vehicles: " + routeList.getLength());
            System.out.println("-------------------------------------------------");
            List<List<Pair<String,String>>> allRoutes = new ArrayList<>();
            for (int route = 0; route < routeList.getLength(); route++) {
                List<Double> loadSplit = new ArrayList<>(Arrays.asList(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0));
                Node routeNode = routeList.item(route);
                if (routeNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element routeNodeElement = (Element) routeNode;
                    String vehicleId = routeNodeElement.getElementsByTagName("vehicleId").item(0).getTextContent();
                    System.out.println("Vehicle Id: " + routeNodeElement.getElementsByTagName("vehicleId").item(0).getTextContent());
                    System.out.println("Start Time: " + routeNodeElement.getElementsByTagName("start").item(0).getTextContent() + "\n");
                    List<Pair<String,String>> routeOfVehicle = new ArrayList<>();
                    List<Quartet<String,String,String,String>> timeRouteOfVehicle = new ArrayList<>();
                    NodeList actList = routeNodeElement.getElementsByTagName("act");
                    String arrivalTimeAtPc = "";
                    for (int act = 0; act < actList.getLength(); act++) {
                        Node actNode = actList.item(act);
                        if (actNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element actNodeElement = (Element) actNode;
                            System.out.println("Shipment Type: " + actNodeElement.getAttribute("type"));
                            System.out.println("Shipment Id: " + actNodeElement.getElementsByTagName("shipmentId").item(0).getTextContent());
                            Pair<String,String> shipmentTypeAndId = new Pair<String, String>(
                                actNodeElement.getAttribute("type"),
                                actNodeElement.getElementsByTagName("shipmentId").item(0).getTextContent());
                            Quartet<String, String, String, String> completeShipmentData = new Quartet<>(
                                actNodeElement.getAttribute("type"),
                                actNodeElement.getElementsByTagName("shipmentId").item(0).getTextContent(),
                                actNodeElement.getElementsByTagName("arrTime").item(0).getTextContent(),
                                actNodeElement.getElementsByTagName("endTime").item(0).getTextContent()
                            );
                            routeOfVehicle.add(shipmentTypeAndId);
                            timeRouteOfVehicle.add(completeShipmentData);
                            System.out.println("Arrival Time: " + actNodeElement.getElementsByTagName("arrTime").item(0).getTextContent());
                            System.out.println("Departure Time: " + actNodeElement.getElementsByTagName("endTime").item(0).getTextContent());
                            if (actNodeElement.getAttribute("type").equalsIgnoreCase("deliverShipment")
                                && parseShipmentIdToGetSourceNode(actNodeElement.getElementsByTagName("shipmentId").item(0).getTextContent(), 1)
                                .equalsIgnoreCase(clusterHeadCode)) {
                                String src = parseShipmentIdToGetSourceNode(actNodeElement.getElementsByTagName("shipmentId").item(0).getTextContent(), 0);
//                                int shipmentNumber = parseShipmentIdToGetShipmentNumber(actNodeElement.getElementsByTagName("shipmentId").item(0).getTextContent());
                                System.out.println("Src node: " + src);
                                // Load Split
                                int pcIndex=0;
                                for (String pc : pcToBranchMap.keySet()) {
                                    System.out.print(getTotalShipmentFromNodeToClusterHead(src.trim(), pc) + ", ");
                                    loadSplit.set(pcIndex,loadSplit.get(pcIndex)+getTotalShipmentFromNodeToClusterHead(src.trim(), pc));
                                    pcIndex++;
                                }
                            }
                        }
                        System.out.println();
                    }
                    arrivalTimeAtPc = routeNodeElement.getElementsByTagName("end").item(0).getTextContent();
//                    timeRouteOfVehicle.add(new Quartet<>("","","end",routeNodeElement.getElementsByTagName("end").item(0).getTextContent()));
                    List<String> outputData = new ArrayList<>();
                    // Cluster
                    outputData.add(clusterHeadCode);
                    // Iteration
                    outputData.add(iteration + "");
                    // Network
                    outputData.add("-");
                    // Cost
                    outputData.add(firstSolutionElement.getElementsByTagName("cost").item(0).getTextContent());
                    // Vehicles Required
                    outputData.add(routeList.getLength() + "");
                    // Vehicle Capacity
                    if (vehicleId.contains(VehicleFleetType._14_Feet.toString()))
                        outputData.add(_14_Feet_Vehicle_Capacity_In_KGs/1000.0+"");
                    else if (vehicleId.contains(VehicleFleetType._17_Feet.toString()))
                        outputData.add(_17_Feet_Vehicle_Capacity_In_KGs/1000.0+"");
                    else if (vehicleId.contains(VehicleFleetType._20_Feet.toString()))
                        outputData.add(_20_Feet_Vehicle_Capacity_In_KGs/1000.0+"");
                    else if (vehicleId.contains(VehicleFleetType._22_Feet.toString()))
                        outputData.add(_22_Feet_Vehicle_Capacity_In_KGs/1000.0+"");
                    else if (vehicleId.contains(VehicleFleetType._32_Feet.toString()))
                        outputData.add(_32_Feet_Vehicle_Capacity_In_KGs/1000.0+"");
                    // Route
                    outputData.add(createConnectedRoute(routeOfVehicle));
                    // Time Route
                    outputData.add(createConnectedTimeRoute(timeRouteOfVehicle));
                    // Dispatch Time from PC
                    outputData.add(vehicleDispatchTimeFromPC+"");
                    // Arrival Time at PC
                    outputData.add(getValidTime(arrivalTimeAtPc));
                    // Round Trip Duration
                    outputData.add((Double.parseDouble(getValidTime(arrivalTimeAtPc))-vehicleDispatchTimeFromPC)+"");
                    // Impossible Shipments
                    outputData.add(impossibleDeliveryLocationsForDay0.size()+"");
                    // Delivery day for each node
                    for(String node: pcToBranchMap.get(clusterHeadCode)) {
                        if (impossibleDeliveryLocationsForDay0.contains(node))
                            outputData.add(1+"");
                        else
                            outputData.add(0+"");
                    }
                    // Load Split
                    for(double load : loadSplit){
                        outputData.add(load+"");
                    }
                    // Write data to output file
                    csvPrinter.printRecord(outputData);

                    allRoutes.add(routeOfVehicle);
                    System.out.println("End Time: " + routeNodeElement.getElementsByTagName("end").item(0).getTextContent());
                    System.out.println("--------------------------------------------------------------------");
                }
                System.out.println();
            }
            List<String> networkRow = new ArrayList<>();
            networkRow.add("");
            networkRow.add("");
            networkRow.add(createNetwork(allRoutes));
            csvPrinter.printRecord(networkRow);
            csvPrinter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        for (int i=0; i<demands.length; i++) {
            if (pcToBranchMap.get(pc).indexOf(pc) != i) {
                demands[pcToBranchMap.get(pc).indexOf(pc)][i] = (int) getTotalDeliveryAtNode(pcToBranchMap.get(pc).get(i));
                demands[i][pcToBranchMap.get(pc).indexOf(pc)] = (int) getTotalPickupAtNode(pcToBranchMap.get(pc).get(i));
            }
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

    private static double getTotalDeliveryAtNode(String deliveryNode) {

        double delivery=0;
        for (String node: networkNodes) {
            if (!deliveryNode.equalsIgnoreCase(node)){
                Pair<String, String> odPair = new Pair<>(node, deliveryNode);
                delivery+=salesPlan.get(odPair);
            }
        }
        return delivery>0?delivery:minWeightToBeHonoured_In_Kgs;
    }

    private static double getTotalShipmentFromNodeToClusterHead(String node, String pc) {

        double shipment=0;
        for (String branchOrPc: pcToBranchMap.get(pc)) {
            Pair<String, String> odPair = new Pair<>(node, branchOrPc);
            shipment+=salesPlan.get(odPair);
        }
        return shipment;
    }

    private static double getTotalShipmentFromClusterHeadToNode(String pc, String node) {

        double shipment=0;
        for (String branchOrPc: pcToBranchMap.get(pc)) {
            Pair<String, String> odPair = new Pair<>(branchOrPc, node);
            shipment+=salesPlan.get(odPair);
        }
        return shipment;
    }

    private static String parseShipmentIdToGetSourceNode(String id, int index) {
        return id.split(",")[0].split("-")[index].split(":")[1];
    }

    private static int parseShipmentIdToGetShipmentNumber(String id) {
        return Integer.parseInt(id.split(",")[1].split(":")[1]);
    }

    private static String getValidTime(String timeString) {
        double time = Math.round(Double.parseDouble(timeString)*100.0)/100.0;
        int hour = (int)time;
        int min = (int)((100 * time - 100 * hour)/100.0*60);
        return ""+hour+"."+min;
    }

    private static String createConnectedRoute(List<Pair<String,String>> route) {
        List<String> connectedRoute = new ArrayList<>();
        for (Pair<String,String> shipmentTypeAndId : route) {
            if (shipmentTypeAndId.getFirst().trim().equalsIgnoreCase("pickupShipment"))
                connectedRoute.add(parseShipmentIdToGetSourceNode(shipmentTypeAndId.getSecond(), 0).trim());
            else
                connectedRoute.add(parseShipmentIdToGetSourceNode(shipmentTypeAndId.getSecond(),1).trim());
        }
        StringBuilder parsedRoute = new StringBuilder("[");
        parsedRoute.append(connectedRoute.get(0));
        for (int i=1; i<connectedRoute.size(); i++) {
            if (!connectedRoute.get(i).equalsIgnoreCase(connectedRoute.get(i-1))) {
                parsedRoute.append("-"+connectedRoute.get(i));
            }
        }
        parsedRoute.append("]");
        return parsedRoute.toString();
    }

    private static String createConnectedTimeRoute(List<Quartet<String,String,String,String>> timeRoute) {
        List<String> connectedRoute = new ArrayList<>();
//        if (timeRoute.get(timeRoute.size()-1).getValue3().equalsIgnoreCase(timeRoute.get(timeRoute.size()-2).getValue3())) {
//            timeRoute = timeRoute.subList(0, timeRoute.size() - 1);
//        }
        for (Quartet<String,String,String,String> shipmentInfo : timeRoute) {
            StringBuilder nodeWithTimeRange = new StringBuilder();
            if (shipmentInfo.getValue0().trim().equalsIgnoreCase("pickupShipment")) {
                nodeWithTimeRange.append(parseShipmentIdToGetSourceNode(shipmentInfo.getValue1(),0).trim());
                nodeWithTimeRange.append("(P:");
                nodeWithTimeRange.append(getValidTime(shipmentInfo.getValue2()));
                nodeWithTimeRange.append("-");
                nodeWithTimeRange.append(getValidTime(shipmentInfo.getValue3()));
                nodeWithTimeRange.append(")");
                connectedRoute.add(nodeWithTimeRange.toString());
            }
            else {
                nodeWithTimeRange.append(parseShipmentIdToGetSourceNode(shipmentInfo.getValue1(),1).trim());
                nodeWithTimeRange.append("(D:");
                nodeWithTimeRange.append(getValidTime(shipmentInfo.getValue2()));
                nodeWithTimeRange.append("-");
                nodeWithTimeRange.append(getValidTime(shipmentInfo.getValue3()));
                nodeWithTimeRange.append(")");
                connectedRoute.add(nodeWithTimeRange.toString());
            }
//            else if (shipmentInfo.getValue0().trim().equalsIgnoreCase("")) {
//                nodeWithTimeRange.append(parseShipmentIdToGetSourceNode(timeRoute.get(0).getValue0(),0).trim()+"()");
//                connectedRoute.add(nodeWithTimeRange.toString());
//            }
        }

        connectedRoute = connectedRoute.stream().distinct().collect(Collectors.toList());
        StringBuilder parsedTimeRoute = new StringBuilder("[");
        parsedTimeRoute.append(connectedRoute.get(0));
        for (int i=1; i<connectedRoute.size()-1; i++) {
            if (!connectedRoute.get(i).split("\\(")[0].equalsIgnoreCase(connectedRoute.get(i - 1).split("\\(")[0])) {
                parsedTimeRoute.append("-" + connectedRoute.get(i));
            } else {
                parsedTimeRoute.deleteCharAt(parsedTimeRoute.length() - 1);
                parsedTimeRoute.append(',');
                parsedTimeRoute.append(connectedRoute.get(i).split("\\(")[1]);
            }
        }
        parsedTimeRoute.append("-"+connectedRoute.get(connectedRoute.size()-1).split("\\(")[0]+"]");
        parsedTimeRoute.delete(parsedTimeRoute.indexOf("("),parsedTimeRoute.indexOf(")")+1);
        return parsedTimeRoute.toString();
    }

    private static String createNetwork( List<List<Pair<String,String>>> allRoutes) {
        StringBuilder network = new StringBuilder("[");
        for (List<Pair<String,String>> route: allRoutes) {
            network.append(createConnectedRoute(route)+", ");
        }
        network.append("]");
        return network.toString();
    }
}
