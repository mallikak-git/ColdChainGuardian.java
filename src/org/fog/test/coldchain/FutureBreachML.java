
package org.fog.test.coldchain;

import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Random;

public class FutureBreachML {

    private static final double SAFE_MIN = 2.0;
    private static final double SAFE_MAX = 8.0;

    public static class Predictor {
        private final RandomForest model;
        private final Instances structure;

        private Predictor(RandomForest model, Instances structure) {
            this.model = model;
            this.structure = new Instances(structure, 0);
        }

        public String predictFutureBreach(
                double currentTemperature,
                double previousTemperature) throws Exception {

            double change = currentTemperature - previousTemperature;

            double[] values = {
                    currentTemperature,
                    previousTemperature,
                    change,
                    SAFE_MAX - currentTemperature,
                    currentTemperature - SAFE_MIN,
                    Double.NaN
            };

            Instance instance = new DenseInstance(1.0, values);
            instance.setDataset(structure);

            int prediction = (int) model.classifyInstance(instance);

            return structure.classAttribute().value(prediction);
        }
    }

    private static Instances createDataset() {
        ArrayList<Attribute> attributes = new ArrayList<>();

        attributes.add(new Attribute("currentTemperature"));
        attributes.add(new Attribute("previousTemperature"));
        attributes.add(new Attribute("temperatureChange"));
        attributes.add(new Attribute("distanceToUpperLimit"));
        attributes.add(new Attribute("distanceToLowerLimit"));

        ArrayList<String> labels = new ArrayList<>();
        labels.add("NO_BREACH_EXPECTED");
        labels.add("BREACH_EXPECTED");
        attributes.add(new Attribute("futureRisk", labels));

        Instances data = new Instances(
                "FutureBreachDataset", attributes, 100);
        data.setClassIndex(data.numAttributes() - 1);

        return data;
    }

    private static double[] generateSequence(
            Random random, int length, int pattern) {

        double[] temperatures = new double[length];
        double temperature = 3.5 + random.nextDouble() * 2.5;
        int eventStart = 4 + random.nextInt(5);

        for (int i = 0; i < length; i++) {
            if (i > 0) {
                double change;

                if (pattern == 1 && i >= eventStart) {
                    change = 0.25 + random.nextDouble() * 0.30;
                } else if (pattern == 2 && i >= eventStart) {
                    change = -0.25 - random.nextDouble() * 0.30;
                } else {
                    change = -0.20 + random.nextDouble() * 0.40;
                }

                temperature += change;
            }

            temperatures[i] = temperature;
        }

        return temperatures;
    }

    private static void addSequence(
            Instances data, double[] temperatures) {

        for (int i = 1; i + 2 < temperatures.length; i++) {
            boolean futureBreach = false;

            for (int j = i + 1; j <= i + 2; j++) {
                if (temperatures[j] < SAFE_MIN
                        || temperatures[j] > SAFE_MAX) {
                    futureBreach = true;
                    break;
                }
            }

            double current = temperatures[i];
            double previous = temperatures[i - 1];

            double[] values = {
                    current,
                    previous,
                    current - previous,
                    SAFE_MAX - current,
                    current - SAFE_MIN,
                    futureBreach ? 1.0 : 0.0
            };

            data.add(new DenseInstance(1.0, values));
        }
    }

    public static Predictor trainModel() throws Exception {
        Instances data = createDataset();
        Random random = new Random(42);

        for (int s = 0; s < 100; s++) {
            int pattern = s % 3;
            double[] sequence = generateSequence(random, 20, pattern);
            addSequence(data, sequence);
        }

        RandomForest model = new RandomForest();
        model.setNumIterations(100);
        model.setSeed(42);
        model.buildClassifier(data);

        return new Predictor(model, data);
    }

    public static void main(String[] args) throws Exception {
        Predictor predictor = trainModel();

        System.out.println("=== Future Breach ML Test ===");
        System.out.println("Training sequences: 100");
        System.out.println("Forecast horizon: next 2 readings");
        System.out.println("Training labels are generated from future readings.");
        System.out.println();

        double[][] tests = {
                {6.0, 5.8},
                {7.5, 7.2},
                {8.5, 8.2}
        };

        for (double[] test : tests) {
            System.out.printf(
                    "%.1f C: %s%n",
                    test[0],
                    predictor.predictFutureBreach(test[0], test[1])
            );
        }

        System.out.println();
        System.out.println("Model trained successfully.");
        System.out.println("Note: This model uses synthetic training data.");
    }
}
