package com.google.ar.core.examples.java.helloar;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.widget.ImageView;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import od_task.BitmapToBufferConverter;
import od_task.Categoryclass;
import od_task.DetectionOptions;
import od_task.Detectionclass;
import od_task.Labelloader;

public class OD_processor {
    private Interpreter TFInterpreter;

    public OD_processor(Interpreter TFInterpreter){
        this.TFInterpreter = TFInterpreter;
    }
    private Labelloader labelloader;
    public void setLabelloader(Labelloader loader){
        labelloader = loader;
    }

    private static final int MAX_SAVE_FRAMES = 10;
    private int savedFrameCount = 0;
    public List<Detectionclass> OD_process(Context context,Image image, DetectionOptions options) {
        List<Detectionclass> results = new ArrayList<>();


        //Preprocess
        long startTime = System.nanoTime();

        Bitmap RGBbitmap = imageToBitmap(context,image);  //YUV to RGB



            //Bitmap RGBbitmap = sample_imageToBitmap(image);
            long time1 = System.nanoTime();
            long durationInMillis = (time1 - startTime) / 1_000; // Convert to milliseconds
//            Log.d("Performance", "//////////////////////////////////////////////////////////////////////////");
//            Log.d("Performance", "YUV to RGB took: " + String.format("%.2f", durationInMillis/1000.0) + " ms");

        int width = TFInterpreter.getInputTensor(0).shape()[1];
        int height = TFInterpreter.getInputTensor(0).shape()[2];
    //        Log.d("Shape", "Input W H: " + width+", "+height);
    //        Log.d("Shape", "InputTensor shape : " + Arrays.toString(TFInterpreter.getInputTensor(0).shape() ));
    //        Log.d("Shape", "OutputTensor count : " + TFInterpreter.getOutputTensorCount());
    //        for(int i=0;i<TFInterpreter.getOutputTensorCount();i++) {
    //            Log.d("Shape", +i+"th OutputTensor shape : " + Arrays.toString(TFInterpreter.getOutputTensor(i).shape()));
    //        }
        Bitmap resizedbitmap = Bitmap.createScaledBitmap(RGBbitmap, width, height, true);

//
//        if (context instanceof Activity) {
//            Activity activity = (Activity) context;
//            activity.runOnUiThread(() -> {
//                ImageView imageView = activity.findViewById(R.id.my_image_view);
//                if (imageView != null) {
//                    imageView.setImageBitmap(resizedbitmap);
//                }
//                if (savedFrameCount >100 && savedFrameCount<100+MAX_SAVE_FRAMES) {
//                    Log.d("message","image generated");
//                    saveBitmapToFile(context, resizedbitmap, savedFrameCount);
//
//                }
//            });
//        }
//        savedFrameCount++;

            long time2 = System.nanoTime();
            long durationInMillis1 = (time2 - time1) / 1_000; // Convert to milliseconds
            //Log.d("Performance", "resizing took: " + String.format("%.2f", durationInMillis1/1000.0) + " ms");

        ByteBuffer inputBuffer = BitmapToBufferConverter.bitmapToByteBuffer(resizedbitmap,width,height);
        //ByteBuffer inputBuffer = BitmapToBufferConverter.BM_TO_FloatBuffer(resizedbitmap,width,height);

            long time3 = System.nanoTime();
            long durationInMillis2 = (time3 - time2) / 1_000; // Convert to milliseconds
           // Log.d("Performance", "Bitmap To Buffer took: " + String.format("%.2f", durationInMillis2/1000.0) + " ms");
//
            long durationInMillis3 = (time3 - startTime) / 1_000; // Convert to milliseconds
          //  Log.d("Performance", "      pre-process total took: " + String.format("%.2f", durationInMillis3/1000.0) + " ms");

        //Run inference, post process
        run_inference_efficientdet(inputBuffer,results, options);
        //run_inference_yolov5(inputBuffer,results, options);




        return results;
    }

    public void saveBitmapToFile(Context context, Bitmap bitmap, int index) {
        // 1) 저장 경로 얻기
        File dir = new File(context.getExternalFilesDir(null), "frames");
        if (!dir.exists()) dir.mkdirs();

        // 2) 실제 파일 생성
        File outFile = new File(dir, "frame_" + index + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            // 3) JPEG로 압축 저장
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            Log.d("saveBitmapToFile", "Saved frame to: " + outFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void run_yolo11(ByteBuffer inputBuffer, List<Detectionclass> results, DetectionOptions options) {
        // Create output buffer
        float[][][] detectionBoxes = new float[1][8400][4];
        float[][] detectionClasses = new float[1][8400];
        float[][] detectionScores = new float[1][8400];

        long startTime = System.nanoTime();


        TFInterpreter.runForMultipleInputsOutputs(
                new Object[]{inputBuffer},
                new HashMap<Integer, Object>() {{
                    put(0,detectionBoxes); put(1,detectionScores); put(2,detectionClasses);
                }}
        );
        //////////////////////////////////////////////////////////////////////////////////////////////////
        long startTime2 = System.nanoTime();
        long durationInMillis = (startTime2 - startTime) / 1_000_000; // Convert to milliseconds
        Log.d("Performance", "inference : " + durationInMillis + " ms");
        //////////////////////////////////////////////////////////////////////////////////////////////////
        int maxResults = options.getMaxResults();
        float scoreThreshold = options.getScoreThreshold();
        int resultCount = 0;
        int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int displayHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        for (int i = 0; i < detectionBoxes[0].length; i++) {
            float score = detectionScores[0][i];
//            String label = labelloader.getLabel((int)detectionClasses[0][i]);
//            Log.d("label",i+"th label: "+label);
//            Log.d("SCORE","SCORE: "+score);

            if (score > scoreThreshold) {
                //Log.d("canvas","Display W: "+displayWidth+" H: "+displayHeight);
                float center_x = detectionBoxes[0][i][0];
                float center_y = detectionBoxes[0][i][1];
                float half_width = detectionBoxes[0][i][2] / 2;
                float half_height = detectionBoxes[0][i][3] / 2;

                float xMin = displayWidth * (1 - center_y - half_height);
                float yMin = displayHeight * (center_x - half_width);
                float xMax = displayWidth * (1 - center_y + half_height);
                float yMax = displayHeight * (center_x + half_width);

                RectF boundingBox = new RectF(xMin, yMin, xMax, yMax);

                String label = labelloader.getLabel((int)detectionClasses[0][i]);
                Categoryclass category = new Categoryclass(label, detectionScores[0][i],detectionClasses[0][i]);
                Detectionclass detection = new Detectionclass(boundingBox, category);
                Log.d("message","   Detected:"+label+ ",  score:"+score);
                results.add(detection);

                resultCount++;
                if (resultCount >= maxResults) {
                    break;
                }
            }
        }
        long endTime = System.nanoTime();
        long durationInMillis2 = (endTime - startTime2) / 1_000_000; // Convert to milliseconds

        Log.d("Performance", "post-process took: " + durationInMillis2 + " ms");

//        long Totaltime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
//        Log.d("Performance", "Totaltime took: " + Totaltime + " ms");

    }

    public void run_inference_efficientdet(ByteBuffer inputBuffer, List<Detectionclass> results, DetectionOptions options) {
        // Create output buffer
        float[][][] detectionBoxes = new float[1][25][4]; // Shape: [1, 25, 4]
        float[][] detectionClasses = new float[1][25]; // Shape: [1, 25]
        float[][] detectionScores = new float[1][25]; // Shape: [1, 25]
        float[] numDetections = new float[1]; // Shape: [1]

        long startTime = System.nanoTime();


        TFInterpreter.runForMultipleInputsOutputs(
                new Object[]{inputBuffer},
                new HashMap<Integer, Object>() {{
                    put(0,detectionBoxes); put(1,detectionClasses); put(2,detectionScores); put(3,numDetections);
                }}
        );
        //////////////////////////////////////////////////////////////////////////////////////////////////
            long startTime2 = System.nanoTime();
            long durationInMillis = (startTime2 - startTime) / 1_000_000; // Convert to milliseconds
            Log.d("Performance", "      running inference took: " + durationInMillis + " ms");
        //////////////////////////////////////////////////////////////////////////////////////////////////
        int maxResults = options.getMaxResults();
        float scoreThreshold = options.getScoreThreshold();
        int resultCount = 0;
        int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int displayHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        for (int i = 0; i < detectionBoxes[0].length; i++) {
            float score = detectionScores[0][i];
//            String label = labelloader.getLabel((int)detectionClasses[0][i]);
//            Log.d("label",i+"th label: "+label);

            if (score > scoreThreshold) {
                //Log.d("canvas","Display W: "+displayWidth+" H: "+displayHeight);
                float xMin = displayWidth - detectionBoxes[0][i][2]* displayWidth;
                float yMin = detectionBoxes[0][i][1]* displayHeight;
                float xMax = displayWidth - detectionBoxes[0][i][0]* displayWidth;
                float yMax = detectionBoxes[0][i][3]* displayHeight;

                RectF boundingBox = new RectF(xMin, yMin, xMax, yMax);

                String label = labelloader.getLabel((int)detectionClasses[0][i]);
                Categoryclass category = new Categoryclass(label, detectionScores[0][i],detectionClasses[0][i]);
                Detectionclass detection = new Detectionclass(boundingBox, category);
               // Log.d("message","   Detected:"+label+ ",  score:"+score);
                results.add(detection);

                resultCount++;
                if (resultCount >= maxResults) {
                    break;
                }
            }
        }
            long endTime = System.nanoTime();
            long durationInMillis2 = (endTime - startTime2) / 1_000_000; // Convert to milliseconds
           // Log.d("Performance", "      post-process took: " + String.format("%.2f", durationInMillis2/1000.0) + " ms");

//            long OD_Totaltime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
//            Log.d("Performance", "    OD_Totaltime took: " + OD_Totaltime + " ms");

    }
    public void run_inference_yolov5(ByteBuffer inputBuffer, List<Detectionclass> results, DetectionOptions options) {
        // Create output buffer
        float[][][] outputbuffer = new float[1][6300][85]; // Shape: [1, 25, 4]

        long startTime = System.nanoTime();


        TFInterpreter.run(inputBuffer,outputbuffer);
        //////////////////////////////////////////////////////////////////////////////////////////////////
        long startTime2 = System.nanoTime();
        long durationInMillis = (startTime2 - startTime) / 1_000_000; // Convert to milliseconds
        Log.d("Performance", "inference took: " + durationInMillis + " ms");
        //////////////////////////////////////////////////////////////////////////////////////////////////
        int maxResults = options.getMaxResults();
        float scoreThreshold = options.getScoreThreshold();
        int resultCount = 0;

        int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int displayHeight = Resources.getSystem().getDisplayMetrics().heightPixels;


        for (int i = 0; i < outputbuffer[0].length; i++) {
            float score = outputbuffer[0][i][4];
//            String label = labelloader.getLabel((int)detectionClasses[0][i]);
//            Log.d("label",i+"th label: "+label);
            float final_confidence;
            if (score > scoreThreshold) {
                //Log.d("canvas","Display W: "+displayWidth+" H: "+displayHeight);
                float maxclassscore = 0;
                int maxclassindex = 0;
                for (int j = 5; j < 85; j++) {
                    if (outputbuffer[0][i][j] > maxclassscore) {
                        maxclassscore = outputbuffer[0][i][4];
                        maxclassindex = j - 5;
                    }
                }
                final_confidence = maxclassscore * score;
                //Log.d("message",   " final_confidence:" + final_confidence);
                if (final_confidence  > scoreThreshold) {
                    float center_x = outputbuffer[0][i][0];
                    float center_y = outputbuffer[0][i][1];
                    float half_width = outputbuffer[0][i][2] / 2;
                    float half_height = outputbuffer[0][i][3] / 2;

                    float xMin = displayWidth * (1 - center_y - half_height);
                    float yMin = displayHeight * (center_x - half_width);
                    float xMax = displayWidth * (1 - center_y + half_height);
                    float yMax = displayHeight * (center_x + half_width);

                    RectF boundingBox = new RectF(xMin, yMin, xMax, yMax);

                    String label = labelloader.getLabel(maxclassindex);
                    Categoryclass category = new Categoryclass(label, final_confidence, maxclassindex);
                    Detectionclass detection = new Detectionclass(boundingBox, category);
                    Log.d("message", label + " detected, score:" + score);
                    results.add(detection);

                    resultCount++;
                    if (resultCount >= maxResults) {
                        break;
                    }
                }
            }
        }
        long endTime = System.nanoTime();
        long durationInMillis2 = (endTime - startTime2) / 1_000_000; // Convert to milliseconds
        Log.d("Performance", "post-process took: " + durationInMillis2 + " ms");

        long Totaltime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
        Log.d("Performance", "Totaltime took: " + Totaltime + " ms");

    }









    private Bitmap sample_imageToBitmap(Image image) {
        // YUV_420_888 형식의 Image를 Bitmap으로 변환
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Unsupported image format");
        }

        Image.Plane[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] yuvBytes = new byte[width * height * 3 / 2];
        int offset = 0;

        for (int i = 0; i < planes.length; i++) {
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            int planeWidth = (i == 0) ? width : width / 2;
            int planeHeight = (i == 0) ? height : height / 2;

            byte[] planeBytes = new byte[buffer.remaining()];
            buffer.get(planeBytes);

            for (int row = 0; row < planeHeight; row++) {
                int outputOffset = offset + row * planeWidth;
                int inputOffset = row * rowStride;

                for (int col = 0; col < planeWidth; col++) {
                    if (inputOffset + col * pixelStride < planeBytes.length) {
                        yuvBytes[outputOffset + col] = planeBytes[inputOffset + col * pixelStride];
                    }
                }
            }

            if (i == 0) {
                offset += width * height;
            } else {
                offset += (width/2) * (height/2);
            }
        }

        // YUV to Bitmap 변환
        YuvImage yuvImage = new YuvImage(yuvBytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, outputStream);
        byte[] jpegBytes = outputStream.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    public Bitmap imageToBitmap(Context context, Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format: " + image.getFormat());
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int pixelCount = width * height;

        // Prepare RenderScript
        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicYuvToRGB scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        // Prepare buffers
        int pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888);
        byte[] yuvBuffer = new byte[pixelCount * pixelSizeBits / 8];

        // Copy YUV data to the buffer
        copyImageToBuffer(image, yuvBuffer, pixelCount);

        // Create input and output allocations
        Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Type.Builder elemType = new Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21);
        Allocation inputAllocation = Allocation.createSized(rs, elemType.create().getElement(), yuvBuffer.length);
        Allocation outputAllocation = Allocation.createFromBitmap(rs, outputBitmap);

        // Convert YUV to RGB
        inputAllocation.copyFrom(yuvBuffer);
        scriptYuvToRgb.setInput(inputAllocation);
        scriptYuvToRgb.forEach(outputAllocation);
        outputAllocation.copyTo(outputBitmap);

        // Release resources
        rs.destroy();

        return outputBitmap;
    }
    private void copyImageToBuffer(Image image, byte[] outputBuffer, int pixelCount) {
        Rect crop = new Rect(0, 0, image.getWidth(), image.getHeight());
        Image.Plane[] planes = image.getPlanes();

        for (int planeIndex = 0; planeIndex < planes.length; planeIndex++) {
            Image.Plane plane = planes[planeIndex];

            int outputStride;
            int outputOffset;

            switch (planeIndex) {
                case 0:
                    outputStride = 1;
                    outputOffset = 0;
                    break;
                case 1:
                    outputStride = 2;
                    outputOffset = pixelCount + 1; // U plane starts after Y data
                    break;
                case 2:
                    outputStride = 2;
                    outputOffset = pixelCount; // V plane starts before U data
                    break;
                default:
                    return;
            }

            ByteBuffer planeBuffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();

            Rect planeCrop = (planeIndex == 0) ? crop :
                    new Rect(crop.left / 2, crop.top / 2, crop.right / 2, crop.bottom / 2);

            int planeWidth = planeCrop.width();
            int planeHeight = planeCrop.height();

            byte[] rowBuffer = new byte[rowStride];
            int rowLength = (pixelStride == 1 && outputStride == 1) ?
                    planeWidth : (planeWidth - 1) * pixelStride + 1;

            for (int row = 0; row < planeHeight; row++) {
                planeBuffer.position((row + planeCrop.top) * rowStride + planeCrop.left * pixelStride);

                if (pixelStride == 1 && outputStride == 1) {
                    planeBuffer.get(outputBuffer, outputOffset, rowLength);
                    outputOffset += rowLength;
                } else {
                    planeBuffer.get(rowBuffer, 0, rowLength);
                    for (int col = 0; col < planeWidth; col++) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride];
                        outputOffset += outputStride;
                    }
                }
            }
        }
    }
}