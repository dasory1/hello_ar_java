package od_task;

import android.media.Image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class imageToBytebuffer {
    public static ByteBuffer yuvToInputBufferWithResize(Image image, int inputWidth, int inputHeight) {
        // Get YUV planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = planes[0].getRowStride();
        int pixelStride = planes[1].getPixelStride();

        // Allocate ByteBuffer for input
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3); // RGB
        inputBuffer.order(ByteOrder.nativeOrder());

        // Resize and convert YUV to RGB
        for (int y = 0; y < inputHeight; y++) {
            for (int x = 0; x < inputWidth; x++) {
                // Map input coordinates to original YUV coordinates
                float scaleX = (float) width / inputWidth;
                float scaleY = (float) height / inputHeight;
                int srcX = Math.round(x * scaleX);
                int srcY = Math.round(y * scaleY);

                // Ensure bounds are not exceeded
                srcX = Math.min(srcX, width - 1);
                srcY = Math.min(srcY, height - 1);

                // YUV to RGB conversion
                int yValue = yBuffer.get(srcY * rowStride + srcX) & 0xFF;
                int uValue = uBuffer.get((srcY / 2) * (rowStride / 2) + (srcX / 2) * pixelStride) & 0xFF;
                int vValue = vBuffer.get((srcY / 2) * (rowStride / 2) + (srcX / 2) * pixelStride) & 0xFF;

                // YUV to RGB conversion formula
                int r = (int) (yValue + 1.370705 * (vValue - 128));
                int g = (int) (yValue - 0.337633 * (uValue - 128) - 0.698001 * (vValue - 128));
                int b = (int) (yValue + 1.732446 * (uValue - 128));

                // Clamp RGB values to [0, 255]
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                // Put RGB values into the buffer
                inputBuffer.put((byte) r);
                inputBuffer.put((byte) g);
                inputBuffer.put((byte) b);
            }
        }

        inputBuffer.rewind(); // Reset buffer position for reading
        return inputBuffer;
    }
}
