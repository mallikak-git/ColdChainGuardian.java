
package org.fog.test.coldchain;

/**
 * Reusable ML prediction service for Cold Chain Guardian.
 * Models are trained once and reused for subsequent readings.
 */
public final class ColdChainPredictionService {

    private static ColdChainML.TrainedModel currentRiskModel;
    private static FutureBreachML.Predictor futureBreachModel;

    private ColdChainPredictionService() {
        // Utility class; do not instantiate.
    }

    public static synchronized void initialize() throws Exception {
        if (currentRiskModel == null) {
            currentRiskModel = ColdChainML.trainModel();
        }

        if (futureBreachModel == null) {
            futureBreachModel = FutureBreachML.trainModel();
        }
    }

    public static synchronized Prediction predict(
            double currentTemperature,
            double previousTemperature) throws Exception {

        initialize();

        if (Double.isNaN(currentTemperature)
                || Double.isNaN(previousTemperature)) {
            throw new IllegalArgumentException(
                    "Both current and previous temperatures are required."
            );
        }

        String currentRisk = currentRiskModel.predictRisk(
                currentTemperature,
                previousTemperature
        );

        String futureRisk = futureBreachModel.predictFutureBreach(
                currentTemperature,
                previousTemperature
        );

        return new Prediction(currentRisk, futureRisk);
    }

    public static final class Prediction {
        private final String currentRisk;
        private final String futureBreachRisk;

        public Prediction(
                String currentRisk,
                String futureBreachRisk) {
            this.currentRisk = currentRisk;
            this.futureBreachRisk = futureBreachRisk;
        }

        public String getCurrentRisk() {
            return currentRisk;
        }

        public String getFutureBreachRisk() {
            return futureBreachRisk;
        }
    }
}

