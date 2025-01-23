package od_task;
import android.graphics.Bitmap;
import android.graphics.RectF;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import android.content.Context;


import java.io.IOException;

public class ObDetector {

    public static List<Detectionclass> runInference(Context context, Bitmap bitmap, DetectionOptions options) {
        List<Detectionclass> results = new ArrayList<>();
        try {
            // Step 1: Load the model, labels
            MappedByteBuffer modelBuffer = ModelLoader.loadModelFile(context, "model.tflite");
            Labelloader labelload = new Labelloader(context, "labels.txt");//coco paper dataset labels

            // Step 2: Create the TensorFlow Lite Interpreter
            TFLiteInterpreter tfliteInterpreter = new TFLiteInterpreter(modelBuffer);

            // Step 3: Convert Bitmap to ByteBuffer
            int width = tfliteInterpreter.getInterpreter().getInputTensor(0).shape()[2];
            int height = tfliteInterpreter.getInterpreter().getInputTensor(0).shape()[1];
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap,width,height,true); //resizing
            ByteBuffer inputBuffer = BitmapToBufferConverter.bitmapToByteBuffer(resizedBitmap, width, height);

            // Step 4: Create output buffer
            float[][][] detectionBoxes = new float[1][25][4]; // Shape: [1, 25, 4]
            float[][] detectionClasses = new float[1][25]; // Shape: [1, 25]
            float[][] detectionScores = new float[1][25]; // Shape: [1, 25]
            float[] numDetections = new float[1]; // Shape: [1]


            // Step 5: Run the inference
            tfliteInterpreter.getInterpreter().runForMultipleInputsOutputs(
                    new Object[]{inputBuffer},
                    new HashMap<Integer, Object>() {{
                        put(0,detectionBoxes); put(1,detectionClasses); put(2,detectionScores); put(3,numDetections);
                    }}
            );

            // Step 6: Process the results based on options
            int maxResults = options.getMaxResults();
            float scoreThreshold = options.getScoreThreshold();

            int resultCount = 0;


            for (int i = 0; i < detectionBoxes[0].length; i++) {
                float score = detectionScores[0][i];

                if (score > scoreThreshold) {
                    float xMin = detectionBoxes[0][i][0]* bitmap.getWidth(); //left
                    float yMin = detectionBoxes[0][i][1]* bitmap.getHeight(); //top
                    float xMax = detectionBoxes[0][i][2]* bitmap.getWidth(); //right
                    float yMax = detectionBoxes[0][i][3]* bitmap.getHeight(); //bottom
                    RectF boundingBox = new RectF(xMin, yMin, xMax, yMax);

                    String label = labelload.getLabel((int)detectionClasses[0][i]);
                    Categoryclass category = new Categoryclass(label, detectionScores[0][i],detectionClasses[0][i]);
                    //List<Categoryclass> categories = new ArrayList<>();
                    //categories.add(category);

                    Detectionclass detection = new Detectionclass(boundingBox, category);
                    results.add(detection);

                    resultCount++;
                    if (resultCount >= maxResults) {
                        break;
                    }
                }
            }

            // Close the interpreter
            tfliteInterpreter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }
}
