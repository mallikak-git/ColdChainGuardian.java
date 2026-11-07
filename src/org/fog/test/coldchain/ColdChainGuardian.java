
package org.fog.test.coldchain;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;

import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;

import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;

import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;

import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;

import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

public class ColdChainGuardian {

    // ==========================================
    // DEVICE AND SENSOR LISTS
    // ==========================================

    static List<FogDevice> fogDevices =
            new ArrayList<FogDevice>();

    static List<Sensor> sensors =
            new ArrayList<Sensor>();

    static List<Actuator> actuators =
            new ArrayList<Actuator>();

    // ==========================================
    // COLD CHAIN TEMPERATURE LIMITS
    // ==========================================

    static final double MIN_SAFE_TEMPERATURE = 2.0;
    static final double MAX_SAFE_TEMPERATURE = 8.0;
    static final double WARNING_MARGIN = 1.0;

    // ==========================================
    // OFFLINE DATASET METRICS
    // ==========================================

    static int totalReadings = 0;
    static int warningCount = 0;
    static int predictedBreachCount = 0;
    static int actualBreachCount = 0;
    static int reactiveAlertReading = -1;
    static int proactiveAlertReading = -1;

    // ==========================================
    // ACTUAL FOG SIMULATION METRICS
    // ==========================================

    static int simulationReadings = 0;
    static int simulationWarningCount = 0;
    static int simulationPredictedBreachCount = 0;
    static int simulationActualBreachCount = 0;

    static double previousSimulationTemperature = Double.NaN;

    // ==========================================
    // MAIN
    // ==========================================

    public static void main(String[] args) {
        System.out.println("=== COLD CHAIN GUARDIAN MAIN STARTED ===");
        System.out.println("======================================");
        System.out.println("       COLD CHAIN GUARDIAN");
        System.out.println("======================================");
        System.out.println("### COLD CHAIN GUARDIAN UPDATED VERSION ###");

        try {

            // 1. INITIALIZE CLOUDSIM

            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            System.out.println("CloudSim initialized successfully.");

            // 2. CREATE FOG BROKER

            FogBroker broker = new FogBroker("broker");
            int userId = broker.getId();
            String appId = "ColdChainApp";

            System.out.println("Fog Broker created: " + broker.getName());

            // 3. CREATE APPLICATION

            Application application =
                    createColdChainApplication(appId, userId);

            application.setUserId(userId);

            System.out.println(
                    "Cold Chain Application created: "
                            + application.getAppId()
            );

            // 4. TEMPERATURE READINGS

            double[] temperatureReadings = {
                    5.0, 5.5, 6.0, 6.5,
                    7.0, 7.5, 8.0, 8.5
            };

            // 5. CREATE CLOUD

            FogDevice cloud = createFogDevice(
                    "cloud",
                    44800,
                    40000,
                    100,
                    10000,
                    0,
                    0.01,
                    16 * 103,
                    16 * 83.25
            );

            cloud.setParentId(-1);
            fogDevices.add(cloud);
            System.out.println("CLOUD HOST MIPS: "
                    + cloud.getHost().getTotalMips());

            System.out.println("Cloud created: " + cloud.getName());

            // 6. CREATE FOG GATEWAY

            FogDevice fogGateway = createFogDevice(
                    "fog-gateway",
                    2800,
                    4000,
                    10000,
                    10000,
                    1,
                    0.0,
                    107.339,
                    83.4333
            );

            fogGateway.setParentId(cloud.getId());
            fogGateway.setUplinkLatency(100);
            fogDevices.add(fogGateway);
            System.out.println("GATEWAY HOST MIPS: "
                    + fogGateway.getHost().getTotalMips());

            // Register actual gateway risk analyzer callback
            fogGateway.setTupleCompletionListener(
                    ColdChainGuardian::processSimulationReading
            );

            System.out.println(
                    "Fog Gateway created: " + fogGateway.getName()
            );

            // 7. CREATE PHARMACEUTICAL VEHICLE

            FogDevice vehicle = createFogDevice(
                    "pharma-vehicle-1",
                    1000,
                    2000,
                    5000,
                    5000,
                    2,
                    0.0,
                    87.53,
                    82.44
            );

            vehicle.setParentId(fogGateway.getId());
            vehicle.setUplinkLatency(10);
            fogDevices.add(vehicle);
            System.out.println("VEHICLE HOST MIPS: "
                    + vehicle.getHost().getTotalMips());

            System.out.println(
                    "Pharmaceutical Vehicle created: "
                            + vehicle.getName()
            );

            // 8. CREATE TEMPERATURE SENSOR

            Sensor temperatureSensor = new Sensor(
                    "temperature-sensor-1",
                    "TEMP",
                    userId,
                    appId,
                    new DeterministicDistribution(5)
            );

            temperatureSensor.setGatewayDeviceId(vehicle.getId());
            temperatureSensor.setLatency(1.0);
            temperatureSensor.setTemperatureReadings(temperatureReadings);

            sensors.add(temperatureSensor);

            System.out.println(
                    "Temperature Sensor created: "
                            + temperatureSensor.getName()
            );

            // 9. OFFLINE PROACTIVE BREACH PREDICTION

            System.out.println();
            System.out.println("======================================");
            System.out.println("   OFFLINE PROACTIVE BREACH PREDICTION");
            System.out.println("======================================");

            ColdChainML.TrainedModel currentRiskModel =
                    ColdChainML.trainModel();

            double previousTemperature = temperatureReadings[0];

            for (int i = 0; i < temperatureReadings.length; i++) {

                double currentTemperature = temperatureReadings[i];

                analyzeTemperature(
                        currentTemperature,
                        previousTemperature
                );

                if (i > 0) {
                    try {
                        String mlRisk = currentRiskModel.predictRisk(
                                currentTemperature,
                                previousTemperature
                        );

                        System.out.println(
                                "ML Risk Prediction -> Temperature: "
                                        + currentTemperature
                                        + " °C | ML Status: "
                                        + mlRisk
                        );

                    } catch (Exception e) {
                        System.out.println(
                                "ML prediction failed at reading "
                                        + (i + 1) + ": " + e.getMessage()
                        );
                    }
                }

                previousTemperature = currentTemperature;
            }

            // 10. REACTIVE VS PROACTIVE COMPARISON

            compareReactiveAndProactive(temperatureReadings);

            // 11. PRINT AND SAVE OFFLINE DATASET

            printResultDataset(temperatureReadings);
            saveResultsToCSV(temperatureReadings);

            // 12. MODULE MAPPING

            ModuleMapping moduleMapping =
                    ModuleMapping.createModuleMapping();

            moduleMapping.addModuleToDevice(
                    "temperature_monitor",
                    vehicle.getName()
            );

            moduleMapping.addModuleToDevice(
                    "risk_analyzer",
                    fogGateway.getName()
            );

            moduleMapping.addModuleToDevice(
                    "alert_system",
                    cloud.getName()
            );

            System.out.println("Module mapping created successfully.");

            // 13. CREATE CONTROLLER

            Controller controller = new Controller(
                    "master-controller",
                    fogDevices,
                    sensors,
                    actuators
            );

            System.out.println("Controller created successfully.");

            // 14. SUBMIT APPLICATION

            controller.submitApplication(
                    application,
                    new ModulePlacementMapping(
                            fogDevices,
                            application,
                            moduleMapping
                    )
            );

            System.out.println("Application submitted successfully.");

            // 15. START SIMULATION

            TimeKeeper.getInstance().setSimulationStartTime(
                    Calendar.getInstance().getTimeInMillis()
            );

            System.out.println();
            System.out.println("======================================");
            System.out.println("       STARTING SIMULATION");
            System.out.println("======================================");

            CloudSim.startSimulation();

            // 16. ACTUAL FOG SIMULATION RISK METRICS

            System.out.println();
            System.out.println("======================================");
            System.out.println(" ACTUAL FOG SIMULATION RISK METRICS");
            System.out.println("======================================");

            System.out.println(
                    "Simulation readings processed = " + simulationReadings
            );

            System.out.println(
                    "Simulation warning readings = " + simulationWarningCount
            );

            System.out.println(
                    "Simulation predicted breaches = "
                            + simulationPredictedBreachCount
            );

            System.out.println(
                    "Simulation actual breaches = " + simulationActualBreachCount
            );

            System.out.println("======================================");

            System.out.println("******** TEST AFTER SIMULATION ********");


// 17. GET ENERGY RESULTS

            double cloudEnergy = cloud.getEnergyConsumption();
            double fogEnergy = fogGateway.getEnergyConsumption();
            double vehicleEnergy = vehicle.getEnergyConsumption();

// 18. SAVE MEASURED ENERGY RESULTS

            saveEnergyResultsToCSV(
                    cloudEnergy,
                    fogEnergy,
                    vehicleEnergy
            );


            // 19. FINAL OFFLINE DATASET METRICS

            System.out.println();
            System.out.println("======================================");
            System.out.println("       OFFLINE DATASET METRICS");
            System.out.println("======================================");

            System.out.println(
                    "Total temperature readings = " + totalReadings
            );

            System.out.println(
                    "Warning readings = " + warningCount
            );

            System.out.println(
                    "Predicted breaches = " + predictedBreachCount
            );

            System.out.println(
                    "Actual breaches = " + actualBreachCount
            );

            System.out.println(
                    "Reactive alert reading (zero-based index) = "
                            + reactiveAlertReading
            );

            System.out.println(
                    "Proactive alert reading (zero-based index) = "
                            + proactiveAlertReading
            );

            if (reactiveAlertReading != -1
                    && proactiveAlertReading != -1) {

                int earlyWarningAdvantage =
                        reactiveAlertReading - proactiveAlertReading;

                System.out.println(
                        "Early warning advantage = "
                                + earlyWarningAdvantage
                                + " reading(s)"
                );
            }

            System.out.println("======================================");
            System.out.println("       SIMULATION COMPLETED");
            System.out.println("======================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // TEMPERATURE STATUS CHECK
    // ==========================================

    private static String checkTemperature(double temperature) {

        if (temperature < MIN_SAFE_TEMPERATURE
                || temperature > MAX_SAFE_TEMPERATURE) {
            return "BREACH";

        } else if (
                temperature <= MIN_SAFE_TEMPERATURE + WARNING_MARGIN
                        || temperature >= MAX_SAFE_TEMPERATURE - WARNING_MARGIN) {
            return "WARNING";

        } else {
            return "SAFE";
        }
    }

    // ==========================================
    // PROACTIVE BREACH PREDICTION
    // ==========================================

    private static String predictTemperatureRisk(
            double currentTemperature,
            double previousTemperature) {

        double temperatureChange =
                currentTemperature - previousTemperature;

        if (temperatureChange > 0
                && currentTemperature >= 7.0
                && currentTemperature < MAX_SAFE_TEMPERATURE) {
            return "PREDICTED_BREACH";
        }

        if (temperatureChange < 0
                && currentTemperature <= 3.0
                && currentTemperature > MIN_SAFE_TEMPERATURE) {
            return "PREDICTED_BREACH";
        }

        return "NO_IMMEDIATE_RISK";
    }

    // ==========================================
    // OFFLINE DATASET RISK ANALYZER
    // ==========================================

    private static void analyzeTemperature(
            double currentTemperature,
            double previousTemperature) {

        totalReadings++;

        String status = checkTemperature(currentTemperature);

        if ("WARNING".equals(status)) {
            warningCount++;
        }

        String prediction = "NO_IMMEDIATE_RISK";

        if (previousTemperature != currentTemperature) {
            prediction = predictTemperatureRisk(
                    currentTemperature,
                    previousTemperature
            );
        }

        if ("PREDICTED_BREACH".equals(prediction)) {
            predictedBreachCount++;
        }

        if ("BREACH".equals(status)) {
            actualBreachCount++;
        }

        System.out.println(
                "Offline Analysis -> Temperature: "
                        + currentTemperature
                        + " °C | Status: " + status
                        + " | Prediction: " + prediction
        );

        if ("PREDICTED_BREACH".equals(prediction)) {
            System.out.println(
                    "PROACTIVE ALERT: Temperature is moving "
                            + "toward an unsafe level!"
            );
        }

        if ("BREACH".equals(status)) {
            System.out.println(
                    "CRITICAL ALERT: Cold-chain temperature "
                            + "breach detected!"
            );
        }
    }

    // ==========================================
    // ACTUAL GATEWAY RISK ANALYSIS
    // ==========================================


    private static void processSimulationReading(Tuple tuple) {

        double currentTemperature = tuple.getTemperature();

        if (Double.isNaN(currentTemperature)
                || Double.isInfinite(currentTemperature)) {
            System.out.println("[GATEWAY ERROR] Invalid temperature reading.");
            return;
        }

        simulationReadings++;

        String status = checkTemperature(currentTemperature);
        String prediction;
        String trend;
        String mlCurrentRisk = "NOT_AVAILABLE";
        String mlFutureRisk = "NOT_AVAILABLE";

        double previousTemperature = previousSimulationTemperature;

        // Run the trained Random Forest models at the fog gateway.
        // The first reading has no previous value, so ML inference starts
        // when a valid pair of consecutive readings is available.
        if (!Double.isNaN(previousTemperature)) {
            try {
                System.out.println("=== ML INFERENCE STARTED ===");

                ColdChainPredictionService.Prediction mlResult =
                        ColdChainPredictionService.predict(
                                currentTemperature,
                                previousTemperature
                        );
                System.out.println("=== ML INFERENCE COMPLETED ===");
                mlCurrentRisk = mlResult.getCurrentRisk();
                mlFutureRisk = mlResult.getFutureBreachRisk();
            } catch (Exception mlException) {
                System.out.println(
                        "[GATEWAY ML ERROR] " + mlException.getMessage()
                );
            }
        }

        if (Double.isNaN(previousTemperature)) {

            trend = "INITIAL READING";
            prediction = "NO_IMMEDIATE_RISK";

        } else {

            double temperatureChange =
                    currentTemperature - previousTemperature;

            if (temperatureChange > 0) {
                trend = "RISING";
            } else if (temperatureChange < 0) {
                trend = "FALLING";
            } else {
                trend = "STABLE";
            }

            if ("BREACH".equals(status)) {
                prediction = "BREACH_ALREADY_OCCURRED";
            } else {
                prediction = predictTemperatureRisk(
                        currentTemperature,
                        previousTemperature
                );
            }
        }

        if ("WARNING".equals(status)) {
            simulationWarningCount++;
        }

        if ("PREDICTED_BREACH".equals(prediction)) {
            simulationPredictedBreachCount++;
        }

        if ("BREACH".equals(status)) {
            simulationActualBreachCount++;
        }

        System.out.println();
        System.out.println("========== GATEWAY RISK ANALYSIS ==========");
        System.out.println("Tuple ID          : " + tuple.getCloudletId());
        System.out.println("Temperature       : " + currentTemperature + " °C");

        if (Double.isNaN(previousTemperature)) {
            System.out.println("Previous Temp     : Not Available");
        } else {
            System.out.println("Previous Temp     : " + previousTemperature + " °C");
        }

        System.out.println("Temperature Trend : " + trend);
        System.out.println("Current Status    : " + status);
        System.out.println("Rule-Based Prediction : " + prediction);
        System.out.println("ML Current-Risk Class : " + mlCurrentRisk);
        System.out.println("ML Future-Breach Class: " + mlFutureRisk);

        if ("BREACH_EXPECTED".equals(mlFutureRisk)) {
            System.out.println(
                    "[ML PROACTIVE ALERT] Random Forest predicts a possible "
                            + "breach within the next two readings."
            );
        }

        if ("BREACH_ALREADY_OCCURRED".equals(prediction)) {

            System.out.println(
                    "[CRITICAL ALERT] Temperature safety limit has been crossed!"
            );

        } else if ("PREDICTED_BREACH".equals(prediction)) {

            System.out.println(
                    "[PROACTIVE ALERT] Temperature trend indicates a possible breach."
            );

        } else if ("WARNING".equals(status)) {

            System.out.println(
                    "[WARNING ALERT] Temperature is approaching the safety limit."
            );

        } else {

            System.out.println(
                    "[SAFE] Temperature is currently within the normal range."
            );
        }

        System.out.println("===========================================");

        previousSimulationTemperature = currentTemperature;
    }


    // ==========================================
    // REACTIVE VS PROACTIVE COMPARISON
    // ==========================================

    private static void compareReactiveAndProactive(
            double[] temperatureReadings) {

        int reactiveReading = -1;
        int proactiveReading = -1;

        for (int i = 1; i < temperatureReadings.length; i++) {

            double current = temperatureReadings[i];
            double previous = temperatureReadings[i - 1];

            if (reactiveReading == -1
                    && current > MAX_SAFE_TEMPERATURE) {
                reactiveReading = i;
            }

            String prediction = predictTemperatureRisk(
                    current,
                    previous
            );

            if (proactiveReading == -1
                    && "PREDICTED_BREACH".equals(prediction)) {
                proactiveReading = i;
            }
        }

        reactiveAlertReading = reactiveReading;
        proactiveAlertReading = proactiveReading;

        System.out.println();
        System.out.println("======================================");
        System.out.println("   REACTIVE vs PROACTIVE");
        System.out.println("======================================");

        System.out.println(
                "Reactive alert reading (zero-based index) = "
                        + reactiveReading
        );

        System.out.println(
                "Proactive alert reading (zero-based index) = "
                        + proactiveReading
        );

        if (reactiveReading != -1 && proactiveReading != -1) {
            int readingsEarly = reactiveReading - proactiveReading;

            System.out.println(
                    "Early warning advantage = "
                            + readingsEarly + " reading(s)"
            );
        }

        System.out.println("======================================");
    }

    // ==========================================
    // RESULT DATASET
    // ==========================================

    private static void printResultDataset(
            double[] temperatureReadings) {

        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "                 COLD CHAIN RESULT DATASET"
        );
        System.out.println(
                "=============================================================="
        );

        System.out.printf(
                "%-8s %-15s %-15s %-20s%n",
                "Reading", "Temperature", "Status", "Prediction"
        );

        System.out.println(
                "--------------------------------------------------------------"
        );

        double previousTemperature = temperatureReadings[0];

        for (int i = 0; i < temperatureReadings.length; i++) {

            double currentTemperature = temperatureReadings[i];

            String status = checkTemperature(currentTemperature);
            String prediction = "NO_IMMEDIATE_RISK";

            if (i > 0) {
                prediction = predictTemperatureRisk(
                        currentTemperature,
                        previousTemperature
                );
            }

            System.out.printf(
                    "%-8d %-15.1f %-15s %-20s%n",
                    i + 1,
                    currentTemperature,
                    status,
                    prediction
            );

            previousTemperature = currentTemperature;
        }

        System.out.println(
                "=============================================================="
        );
    }

    // ==========================================
    // CREATE FOG DEVICE
    // ==========================================

    private static FogDevice createFogDevice(
            String nodeName,
            long mips,
            int ram,
            long upBw,
            long downBw,
            int level,
            double ratePerMips,
            double busyPower,
            double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        peList.add(
                new Pe(
                        0,
                        new PeProvisionerOverbooking(mips)
                )
        );
        System.out.println("DEVICE: " + nodeName);
        System.out.println("INPUT MIPS: " + mips);
        System.out.println("PE MIPS: " + peList.get(0).getMips());
        System.out.println("PROVISIONER MIPS: "
                + peList.get(0).getPeProvisioner().getMips());
        System.out.println("AVAILABLE MIPS: "
                + peList.get(0).getPeProvisioner().getAvailableMips());
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";

        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        LinkedList<Storage> storageList = new LinkedList<Storage>();

        FogDeviceCharacteristics characteristics =
                new FogDeviceCharacteristics(
                        arch,
                        os,
                        vmm,
                        host,
                        time_zone,
                        cost,
                        costPerMem,
                        costPerStorage,
                        costPerBw
                );

        FogDevice fogdevice = null;

        try {
            fogdevice = new FogDevice(
                    nodeName,
                    characteristics,
                    new AppModuleAllocationPolicy(hostList),
                    storageList,
                    10,
                    upBw,
                    downBw,
                    0,
                    ratePerMips
            );

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (fogdevice != null) {
            fogdevice.setLevel(level);
        }

        return fogdevice;
    }

    // ==========================================
    // CREATE COLD CHAIN APPLICATION
    // ==========================================

    @SuppressWarnings({"serial"})
    private static Application createColdChainApplication(
            String appId,
            int userId) {

        Application application =
                Application.createApplication(appId, userId);

        application.addAppModule("temperature_monitor", 10);
        application.addAppModule("risk_analyzer", 10);
        application.addAppModule("alert_system", 10);

        application.addAppEdge(
                "TEMP",
                "temperature_monitor",
                1000,
                1000,
                "TEMP_DATA",
                Tuple.UP,
                AppEdge.SENSOR
        );

        application.addAppEdge(
                "temperature_monitor",
                "risk_analyzer",
                500,
                500,
                "TEMPERATURE_READING",
                Tuple.UP,
                AppEdge.MODULE
        );

        application.addAppEdge(
                "risk_analyzer",
                "alert_system",
                500,
                500,
                "RISK_ALERT",
                Tuple.UP,
                AppEdge.MODULE
        );

        application.addTupleMapping(
                "temperature_monitor",
                "TEMP",
                "TEMPERATURE_READING",
                new FractionalSelectivity(1.0)
        );
        application.addTupleMapping(
                "risk_analyzer",
                "TEMPERATURE_READING",
                "RISK_ALERT",
                new FractionalSelectivity(1.0)
        );

        return application;
    }

    // ==========================================
    // SAVE TEMPERATURE RESULTS TO CSV
    // ==========================================

    private static void saveResultsToCSV(
            double[] temperatureReadings) {

        String fileName = "cold_chain_results.csv";

        try {
            ColdChainML.TrainedModel mlModel =
                    ColdChainML.trainModel();

            FutureBreachML.Predictor futureModel =
                    FutureBreachML.trainModel();

            int currentRiskEvaluated = 0;
            int currentRiskCorrectCount = 0;
            int futureBreachEvaluated = 0;
            int futureBreachCorrect = 0;

            try (PrintWriter writer =
                         new PrintWriter(new FileWriter(fileName))) {

                writer.println(
                        "Reading,Temperature,Status,RuleBasedPrediction,"
                                + "MLPrediction,FutureBreachMLPrediction,"
                                + "ActualFutureBreach,CurrentRiskMLCorrect,"
                                + "FutureMLCorrect"
                );

                double previousTemperature =
                        temperatureReadings.length > 0
                                ? temperatureReadings[0] : Double.NaN;

                for (int i = 0; i < temperatureReadings.length; i++) {

                    double currentTemperature = temperatureReadings[i];

                    String status = checkTemperature(currentTemperature);
                    String ruleBasedPrediction = "NO_IMMEDIATE_RISK";
                    String mlPrediction = "NOT_AVAILABLE";
                    String futureBreachPrediction = "NOT_AVAILABLE";
                    String actualFutureBreach = "NOT_AVAILABLE";
                    String currentRiskCorrect = "NOT_AVAILABLE";
                    String futureMLCorrect = "NOT_AVAILABLE";

                    if (i > 0) {

                        ruleBasedPrediction = predictTemperatureRisk(
                                currentTemperature,
                                previousTemperature
                        );

                        try {
                            mlPrediction = mlModel.predictRisk(
                                    currentTemperature,
                                    previousTemperature
                            );

                            currentRiskCorrect =
                                    mlPrediction.equals(status) ? "YES" : "NO";

                            currentRiskEvaluated++;

                            if ("YES".equals(currentRiskCorrect)) {
                                currentRiskCorrectCount++;
                            }

                        } catch (Exception e) {
                            mlPrediction = "ML_ERROR";
                            currentRiskCorrect = "NOT_AVAILABLE";

                            System.out.println(
                                    "Current-risk ML prediction failed at reading "
                                            + (i + 1) + ": " + e.getMessage()
                            );
                        }

                        try {
                            futureBreachPrediction =
                                    futureModel.predictFutureBreach(
                                            currentTemperature,
                                            previousTemperature
                                    );

                        } catch (Exception e) {
                            futureBreachPrediction = "ML_ERROR";

                            System.out.println(
                                    "Future-breach ML prediction failed at reading "
                                            + (i + 1) + ": " + e.getMessage()
                            );
                        }
                    }

                    if (i + 2 < temperatureReadings.length) {

                        boolean breachInNextTwo = false;

                        for (int j = i + 1; j <= i + 2; j++) {
                            if (temperatureReadings[j] < MIN_SAFE_TEMPERATURE
                                    || temperatureReadings[j] > MAX_SAFE_TEMPERATURE) {
                                breachInNextTwo = true;
                                break;
                            }
                        }

                        actualFutureBreach = breachInNextTwo
                                ? "BREACH_EXPECTED"
                                : "NO_BREACH_EXPECTED";

                        if (!"NOT_AVAILABLE".equals(futureBreachPrediction)
                                && !"ML_ERROR".equals(futureBreachPrediction)) {

                            futureMLCorrect =
                                    futureBreachPrediction.equals(actualFutureBreach)
                                            ? "YES" : "NO";

                            futureBreachEvaluated++;

                            if ("YES".equals(futureMLCorrect)) {
                                futureBreachCorrect++;
                            }
                        }
                    }

                    writer.println(
                            (i + 1) + ","
                                    + currentTemperature + ","
                                    + status + ","
                                    + ruleBasedPrediction + ","
                                    + mlPrediction + ","
                                    + futureBreachPrediction + ","
                                    + actualFutureBreach + ","
                                    + currentRiskCorrect + ","
                                    + futureMLCorrect
                    );

                    previousTemperature = currentTemperature;
                }
            }

            System.out.println();
            System.out.println(
                    "CSV RESULTS SAVED SUCCESSFULLY: " + fileName
            );

            System.out.println(
                    "Current-risk ML correctness: "
                            + currentRiskCorrectCount + "/"
                            + currentRiskEvaluated + " evaluated readings"
            );

            System.out.println(
                    "Future-breach ML correctness: "
                            + futureBreachCorrect + "/"
                            + futureBreachEvaluated + " evaluated readings"
            );

            System.out.println(
                    "Note: Future-breach validation is NOT_AVAILABLE "
                            + "when fewer than two future readings exist."
            );

            System.out.println(
                    "Note: This is a small predefined sequence; "
                            + "these counts are not real-world accuracy metrics."
            );

        } catch (Exception e) {
            System.out.println("Error while saving CSV:");
            e.printStackTrace();
        }
    }

    // ==========================================
    // SAVE ENERGY RESULTS TO CSV
    // ==========================================

    private static void saveEnergyResultsToCSV(
            double cloudEnergy,
            double fogEnergy,
            double vehicleEnergy) {

        String fileName = "cold_chain_energy.csv";

        try (PrintWriter writer =
                     new PrintWriter(new FileWriter(fileName))) {

            writer.println("Device,EnergyConsumed_WattSeconds");
            writer.println("Cloud," + cloudEnergy);
            writer.println("Fog Gateway," + fogEnergy);
            writer.println("Pharma Vehicle," + vehicleEnergy);

            double totalEnergy =
                    cloudEnergy + fogEnergy + vehicleEnergy;

            writer.println("Total," + totalEnergy);

            System.out.println();
            System.out.println("======================================");
            System.out.println("MEASURED ENERGY RESULTS");
            System.out.println("======================================");
            System.out.println("File: " + fileName);
            System.out.println("Cloud Energy = " + cloudEnergy);
            System.out.println("Fog Gateway Energy = " + fogEnergy);
            System.out.println("Pharma Vehicle Energy = " + vehicleEnergy);
            System.out.println("Total Energy = " + totalEnergy);
            System.out.println(
                    "Execution cost and network usage are not measured in this report."
            );
            System.out.println("======================================");

        } catch (Exception e) {
            System.out.println("Error while saving energy CSV:");
            e.printStackTrace();
        }
    }
}
