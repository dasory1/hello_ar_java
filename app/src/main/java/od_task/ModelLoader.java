package od_task;
import android.content.Context;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ModelLoader {
    public static MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(context.getAssets().openFd(modelPath).getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = context.getAssets().openFd(modelPath).getStartOffset();
            long declaredLength = context.getAssets().openFd(modelPath).getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }
    public static String calculateModelHash(Context context, String modelPath) throws IOException, NoSuchAlgorithmException {
        try (FileInputStream inputStream = new FileInputStream(context.getAssets().openFd(modelPath).getFileDescriptor())) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            long startOffset = context.getAssets().openFd(modelPath).getStartOffset();
            long declaredLength = context.getAssets().openFd(modelPath).getDeclaredLength();

            inputStream.skip(startOffset); // Skip to the start offset
            byte[] buffer = new byte[8192]; // 8KB buffer
            long totalRead = 0;
            int bytesRead;

            while (totalRead < declaredLength && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, declaredLength - totalRead))) != -1) {
                md.update(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            // Convert the hash to a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : md.digest()) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        }
    }
}
