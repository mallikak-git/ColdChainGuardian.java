# Cold Chain Guardian – ML Integration and Run Guide

## What is implemented

This project includes two Weka Random Forest models:

1. `ColdChainML` classifies the current temperature condition as SAFE, WARNING, or BREACH using current temperature, previous temperature, and temperature change.
2. `FutureBreachML` predicts whether a temperature breach is expected within the next two readings. Its training labels are generated from the next two values in synthetic temperature sequences.

`ColdChainPredictionService` trains each model once and reuses the trained models.

### iFogSim integration

The `processSimulationReading(Tuple)` gateway callback now calls `ColdChainPredictionService.predict(...)` for each reading after the first reading. It prints:
- rule-based current status and rule-based prediction;
- Random Forest current-risk classification;
- Random Forest future-breach classification;
- an `[ML PROACTIVE ALERT]` message when the future model predicts `BREACH_EXPECTED`.

The existing three-layer iFogSim architecture and tuple route are retained.

## Important scientific limitation

The training data in these ML classes is synthetic and small / programmatically generated. The future-breach model's validation test uses separately seeded synthetic sequences generated using the same sequence-generation rules as training. The validation metrics therefore measure performance on this synthetic setup only. They are **not** evidence of real-world pharmaceutical prediction accuracy.

Do not describe this as a model trained on real pharmaceutical transport data, and do not claim field validation. Before real deployment, collect representative, quality-controlled historical temperature data, define product-specific storage limits and prediction horizons with domain experts, split data by shipment / route / time to avoid leakage, and validate prospectively.

The current-risk classifier's 16-row training set is also illustrative. Its 4-fold cross-validation result is a small-sample demonstration, not a reliable generalisation estimate.

## Build and run

The project was compiled and the main simulation was run successfully in the supplied project environment using Java 21 in this workspace. The original project was developed with Java 17; use JDK 17 in IntelliJ if that is your configured environment.

### IntelliJ IDEA
1. Open the `iFogSim-main` folder as a project.
2. Ensure the `jars` directory is added to the module dependencies / classpath (the existing project setup includes Weka).
3. Set Project SDK to JDK 17 (or a compatible JDK).
4. Run `org.fog.test.coldchain.ColdChainGuardian`.
5. In the console, search for `ML Current-Risk Class`, `ML Future-Breach Class`, and `[ML PROACTIVE ALERT]`.

### Command line (from project root, Windows PowerShell)
```powershell
$cp = "out; jars\*"
java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp org.fog.test.coldchain.ColdChainGuardian
```
If the `out` directory does not contain freshly compiled classes, compile the project first using the IntelliJ project configuration or your existing build setup. The `--add-opens` option addresses reflective-access warnings from the bundled legacy Weka / CloudSim dependencies on newer Java runtimes; if running under Java 17 and no such warning occurs, it may not be needed.

## Validation

Run `org.fog.test.coldchain.ColdChainML` to see the current-risk model's small-sample cross-validation report.

Run `org.fog.test.coldchain.FutureBreachValidation` to compare the deployed future-breach Random Forest against the included rule-based baseline on independent synthetic test sequences.

Validation output from this workspace is included in `ML_VALIDATION_RESULTS.txt`.
