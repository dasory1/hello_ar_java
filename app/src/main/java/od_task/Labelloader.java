package od_task;
import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Labelloader {
    private List<String> labels;

    // Constructor to load labels from the assets folder
    public Labelloader(Context context, String labelpath) {
        labels = new ArrayList<>();
        loadLabels(context,labelpath);
    }

    // Method to load labels from the assets folder
    private void loadLabels(Context context, String labelpath) {
        try {
            InputStream is = context.getAssets().open(labelpath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to get the label for a given class index
    public String getLabel(int index) {
        if (index >= 0 && index < labels.size()) {
            return labels.get(index);
        }
        return "Unknown";
    }
}
