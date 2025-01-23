package od_task;
import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BitmapToBufferConverter {
    public static ByteBuffer bitmapToByteBuffer(Bitmap bitmap, int width, int height) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(width * height * 3); // 3 channels (RGB)
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        int pixelIndex = 0;
        for(int pixel : intValues){
                //int pixel = intValues[pixelIndex++];
                byteBuffer.put((byte)((pixel >> 16) & 0xFF)); // R
                byteBuffer.put((byte)((pixel >> 8) & 0xFF));  // G
                byteBuffer.put((byte)(pixel & 0xFF));         // B

        }
        return byteBuffer;
    }
    public static ByteBuffer BM_TO_FloatBuffer(Bitmap bitmap, int width, int height) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(width * height * 3*4); // 3 channels (RGB)
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        int pixelIndex = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pixel = intValues[pixelIndex++];
                byteBuffer.putFloat(((pixel >> 16) & 0xFF)/255.0f); // R
                byteBuffer.putFloat(((pixel >> 8) & 0xFF)/255.0f);  // G
                byteBuffer.putFloat((pixel & 0xFF)/255.0f);         // B
            }
        }
        return byteBuffer;
    }
}
