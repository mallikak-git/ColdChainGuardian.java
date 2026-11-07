
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;

public class WekaTest {
    public static void main(String[] args) {
        RandomForest model = new RandomForest();

        System.out.println("Weka ML library is working!");
        System.out.println("Random Forest model created successfully.");
    }
}