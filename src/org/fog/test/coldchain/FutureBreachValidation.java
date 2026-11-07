package org.fog.test.coldchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class FutureBreachValidation {

    private static final double SAFE_MIN = 2.0;
    private static final double SAFE_MAX = 8.0;
    private static final int HORIZON = 2;
    private static final int SEQUENCE_LENGTH = 20;
    private static final int TEST_SEQUENCE_COUNT = 20;

    private static class Sample {
        final double current;
        final double previous;
        final int label;

        Sample(double current, double previous, int label) {
            this.current = current;
            this.previous = previous;
            this.label = label;
        }
    }

    private static Instances createDataset(int capacity) {
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

        Instances data = new Instances("FutureBreachDataset", attributes, capacity);
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }

    private static List<double[]> generateTestSequences(int count, long seed) {
        List<double[]> sequences = new ArrayList<>();
        Random random = new Random(seed);

        for (int s = 0; s < count; s++) {
            double[] temperatures = new double[SEQUENCE_LENGTH];
            double temperature = 3.5 + random.nextDouble() * 2.5;
            int pattern = s % 3;
            int eventStart = 4 + random.nextInt(5);

            for (int i = 0; i < temperatures.length; i++) {
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
            sequences.add(temperatures);
        }
        return sequences;
    }

    private static List<Sample> convertSequences(List<double[]> sequences) {
        List<Sample> samples = new ArrayList<>();

        for (double[] temperatures : sequences) {
            for (int i = 1; i + HORIZON < temperatures.length; i++) {
                boolean futureBreach = false;

                for (int j = i + 1; j <= i + HORIZON; j++) {
                    if (temperatures[j] < SAFE_MIN || temperatures[j] > SAFE_MAX) {
                        futureBreach = true;
                        break;
                    }
                }

                samples.add(new Sample(
                        temperatures[i],
                        temperatures[i - 1],
                        futureBreach ? 1 : 0));
            }
        }
        return samples;
    }

    private static int baselinePrediction(Sample sample) {
        double current = sample.current;
        double change = current - sample.previous;

        if (current < SAFE_MIN || current > SAFE_MAX) {
            return 1;
        }
        if (change > 0 && current >= 7.0) {
            return 1;
        }
        if (change < 0 && current <= 3.0) {
            return 1;
        }
        return 0;
    }

    private static int[][] evaluateActualPredictor(
            FutureBreachML.Predictor predictor, List<Sample> samples) throws Exception {
        int[][] matrix = new int[2][2];

        for (Sample sample : samples) {
            String result = predictor.predictFutureBreach(
                    sample.current, sample.previous);
            int prediction = "BREACH_EXPECTED".equals(result) ? 1 : 0;
            matrix[sample.label][prediction]++;
        }
        return matrix;
    }

    private static int[][] evaluateBaseline(List<Sample> samples) {
        int[][] matrix = new int[2][2];

        for (Sample sample : samples) {
            matrix[sample.label][baselinePrediction(sample)]++;
        }
        return matrix;
    }

    private static void printMetrics(String name, int[][] matrix) {
        int tn = matrix[0][0];
        int fp = matrix[0][1];
        int fn = matrix[1][0];
        int tp = matrix[1][1];
        int total = tn + fp + fn + tp;

        double accuracy = total == 0 ? 0.0 : 100.0 * (tn + tp) / total;
        double precision = tp + fp == 0 ? 0.0 : 100.0 * tp / (tp + fp);
        double recall = tp + fn == 0 ? 0.0 : 100.0 * tp / (tp + fn);
        double f1 = precision + recall == 0.0
                ? 0.0 : 2.0 * precision * recall / (precision + recall);

        System.out.println("\n=== " + name + " ===");
        System.out.printf("Accuracy:          %.2f%%%n", accuracy);
        System.out.printf("Breach precision:  %.2f%%%n", precision);
        System.out.printf("Breach recall:     %.2f%%%n", recall);
        System.out.printf("Breach F1-score:   %.2f%%%n", f1);
        System.out.println("Confusion matrix (actual rows, predicted columns):");
        System.out.println("                       Predicted no-breach | Predicted breach");
        System.out.printf("Actual no-breach:       %17d | %16d%n", tn, fp);
        System.out.printf("Actual breach:          %17d | %16d%n", fn, tp);
        System.out.println("Test examples: " + total);
    }

    public static void main(String[] args) throws Exception {
        /*
         * This is the deployed model's own training method. The test
         * sequences below use a separate random seed.
         */
        FutureBreachML.Predictor predictor = FutureBreachML.trainModel();

        List<Sample> testing = convertSequences(
                generateTestSequences(TEST_SEQUENCE_COUNT, 2026));

        int actualBreaches = 0;
        for (Sample sample : testing) {
            if (sample.label == 1) {
                actualBreaches++;
            }
        }

        System.out.println("=== Deployed FutureBreachML Validation ===");
        System.out.println("Model under test: FutureBreachML.trainModel()");
        System.out.println("Training sequences: 100 (defined in FutureBreachML)");
        System.out.println("Independent test sequences: " + TEST_SEQUENCE_COUNT);
        System.out.println("Readings per sequence: " + SEQUENCE_LENGTH);
        System.out.println("Forecast horizon: next " + HORIZON + " readings");
        System.out.println("Test examples: " + testing.size());
        System.out.println("Actual breach examples: " + actualBreaches);

        int[][] mlMatrix = evaluateActualPredictor(predictor, testing);
        int[][] baselineMatrix = evaluateBaseline(testing);

        printMetrics("Actual FutureBreachML Random Forest", mlMatrix);
        printMetrics("Rule-Based Baseline", baselineMatrix);

        System.out.println("\nNotes:");
        System.out.println("- The Random Forest tested here is trained by FutureBreachML.trainModel().");
        System.out.println("- Test data uses a different seed but the same synthetic sequence-generation rules.");
        System.out.println("- These results do not establish real-world pharmaceutical safety or accuracy.");
    }
}
