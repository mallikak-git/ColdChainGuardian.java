
package org.fog.test.coldchain;

import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Random;

public class ColdChainML {

    private static Instances createTrainingDataset() {

        ArrayList<Attribute> attributes = new ArrayList<>();

        attributes.add(new Attribute("temperature"));
        attributes.add(new Attribute("previousTemperature"));
        attributes.add(new Attribute("temperatureChange"));

        ArrayList<String> labels = new ArrayList<>();
        labels.add("SAFE");
        labels.add("WARNING");
        labels.add("BREACH");

        attributes.add(new Attribute("risk", labels));

        Instances dataset = new Instances(
                "ColdChainTemperature",
                attributes,
                0
        );

        dataset.setClassIndex(dataset.numAttributes() - 1);

        double[][] readings = {
                {4.0, 4.0, 0.0, 0},
                {5.0, 4.5, 0.5, 0},
                {5.5, 5.0, 0.5, 0},
                {6.0, 5.5, 0.5, 0},
                {6.5, 6.0, 0.5, 1},
                {7.0, 6.5, 0.5, 1},
                {7.5, 7.0, 0.5, 1},
                {8.0, 7.5, 0.5, 1},
                {8.5, 8.0, 0.5, 2},
                {9.0, 8.5, 0.5, 2},
                {1.5, 2.5, -1.0, 2},
                {2.5, 3.5, -1.0, 1},
                {3.5, 4.0, -0.5, 0},
                {5.0, 5.0, 0.0, 0},
                {7.2, 6.8, 0.4, 1},
                {8.2, 7.8, 0.4, 2}
        };

        for (double[] row : readings) {
            dataset.add(new DenseInstance(1.0, row));
        }

        return dataset;
    }

    public static TrainedModel trainModel() throws Exception {

        Instances dataset = createTrainingDataset();

        RandomForest model = new RandomForest();
        model.setNumIterations(100);
        model.buildClassifier(dataset);

        Instances header = new Instances(dataset, 0);

        return new TrainedModel(model, header);
    }

    public static class TrainedModel {

        private final RandomForest model;
        private final Instances header;

        private TrainedModel(RandomForest model, Instances header) {
            this.model = model;
            this.header = header;
        }

        public String predictRisk(
                double currentTemperature,
                double previousTemperature
        ) throws Exception {

            double temperatureChange =
                    currentTemperature - previousTemperature;

            double[] values = {
                    currentTemperature,
                    previousTemperature,
                    temperatureChange,
                    Double.NaN
            };

            Instance newReading = new DenseInstance(1.0, values);
            newReading.setDataset(header);

            double prediction = model.classifyInstance(newReading);

            return header.classAttribute().value((int) prediction);
        }
    }

    public static void main(String[] args) throws Exception {

        Instances dataset = createTrainingDataset();

        RandomForest model = new RandomForest();
        model.setNumIterations(100);
        model.buildClassifier(dataset);

        Evaluation evaluation = new Evaluation(dataset);

        evaluation.crossValidateModel(
                new RandomForest(),
                dataset,
                4,
                new Random(42)
        );

        System.out.println("=== Cold Chain Guardian ML Results ===");
        System.out.println("Training instances: " + dataset.numInstances());
        System.out.println("Model: Random Forest");
        System.out.println(
                "Cross-validation accuracy: "
                        + String.format(
                        "%.2f%%",
                        evaluation.pctCorrect()
                )
        );

        System.out.println(evaluation.toSummaryString());
        System.out.println(evaluation.toClassDetailsString());
        System.out.println(evaluation.toMatrixString());

        TrainedModel trainedModel = trainModel();

        System.out.println("\n=== Sample ML Predictions ===");

        System.out.println(
                "Temperature 6.0°C: "
                        + trainedModel.predictRisk(6.0, 5.5)
        );

        System.out.println(
                "Temperature 7.5°C: "
                        + trainedModel.predictRisk(7.5, 7.0)
        );

        System.out.println(
                "Temperature 8.5°C: "
                        + trainedModel.predictRisk(8.5, 8.0)
        );

        System.out.println("\nML model trained successfully!");
    }
}