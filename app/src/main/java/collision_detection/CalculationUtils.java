package collision_detection;

import android.content.res.Resources;
import android.graphics.RectF;
import android.media.Image;
import android.util.Log;

import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Pose;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import od_task.Detectionclass;

public class CalculationUtils {
    static int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
    static int displayHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
    static CameraIntrinsics intrinsics = TrackingSystem.CameraInfo.getIntrinsics();
    public static float calculatedepth(int x_grid, int y_grid, Image depthimage){
        //x_grid, y_grid는 display상의 grid

        int depthX = y_grid * depthimage.getWidth() / displayHeight;
        int depthY =  depthimage.getHeight() - x_grid * depthimage.getHeight() / displayWidth;

        ByteBuffer depthByteBuffer = depthimage.getPlanes()[0].getBuffer();
        depthByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int index = depthY*depthimage.getWidth()+depthX;
        int lsb = depthByteBuffer.get(index*2)& 0xFF;
        int msb = depthByteBuffer.get(index*2+1)& 0xFF;
        return ((msb<<8) | lsb)/1000.0f;

    }

    public static float[] get3DPosition(Detectionclass detection, Image depthMap, Pose cameraPose){
        int mid_x = (int)(detection.getBoundingBox().left + detection.getBoundingBox().right)/2;
        int mid_y = (int)(detection.getBoundingBox().top + detection.getBoundingBox().bottom)/2;
        float depth = calculatedepth(mid_x,mid_y,depthMap);
        //Log.d("Depth","depth:"+depth);

        int[] dimensions = intrinsics.getImageDimensions();
        float fx = intrinsics.getFocalLength()[0];
        float fy = intrinsics.getFocalLength()[1];
        float cx = intrinsics.getPrincipalPoint()[0];
        float cy = intrinsics.getPrincipalPoint()[1];

        // Convert depth pixel to 3D camera coordinates
        float[] cameraCoords = new float[3];

        cameraCoords[0] = depth * (mid_x*dimensions[0]/(float)displayHeight-cx) / fx; // X
        cameraCoords[1] = depth * (mid_y*dimensions[1]/(float)displayWidth-cy) / fy;// Y
        cameraCoords[2] = -depth;

        float[] translation = cameraPose.getTranslation();
        float[] q1 = cameraPose.getRotationQuaternion();
        float[] p1 = new float[]{cameraCoords[0],cameraCoords[1],cameraCoords[2],0};
        float[] q_star = new float[]{-q1[0],-q1[1],-q1[2],q1[3]};
        float[] p_prime = multiply_quarternion(multiply_quarternion(q1,p1),q_star);

        return new float[] {p_prime[0]+translation[0],p_prime[1]+translation[1],p_prime[2]+translation[2]};
    };

    public static float[] multiply_quarternion(float[] q1, float[] q2){
        float x1 = q1[0],y1 = q1[1], z1 = q1[2], w1 = q1[3];
        float x2 = q2[0],y2 = q2[1], z2 = q2[2], w2 = q2[3];
        return new float[]{
                w1*x2 + x1*w2 + y1*z2 - z1*y2, //x
                w1*y2 - x1*z2 + y1*w2 + z1*x2, //y
                w1*z2 + x1*y2 - y1*x2 + z1*w2, //z
                w1*w2 - x1*x2 - y1*y2 - z1*z2  //w
        };

    }

    // IoU 계산 (Bounding Box 간의 Intersection over Union)
    public static float calculateIoU(RectF box1, RectF box2) {
        float intersection = calculateIntersectionArea(box1, box2);
        float union = getArea(box1) + getArea(box2) - intersection;
        return intersection / union;
    }
    public static float getArea(RectF box1) {

        float width = Math.max(0, box1.right - box1.left);
        float height = Math.max(0, box1.bottom-box1.top);

        return width * height;
    }
    public static float calculateIntersectionArea(RectF box1, RectF box2) {
        float x1 = Math.max(box1.left, box2.left);
        float y1 = Math.max(box1.top, box2.top);
        float x2 = Math.min(box1.right, box2.right);
        float y2 = Math.min(box1.bottom, box2.bottom);

        float width = Math.max(0, x2 - x1);
        float height = Math.max(0, y2 - y1);

        return width * height;
    }


    public static float[][] matrixMultiply(float[][] A, float[][] B) {
        int rows = A.length;
        int cols = B[0].length;
        int shared = B.length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                for (int k = 0; k < shared; k++) {
                    result[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return result;
    }

    public static float[] matrixVectorMultiply(float[][] A, float[] v) {
        float[] result = new float[A.length];
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < v.length; j++) {
                result[i] += A[i][j] * v[j];
            }
        }
        return result;
    }

    public static float[][] matrixAdd(float[][] A, float[][] B) {
        int rows = A.length;
        int cols = A[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = A[i][j] + B[i][j];
            }
        }
        return result;
    }

    public static float[][] matrixSubtract(float[][] A, float[][] B) {
        int rows = A.length;
        int cols = A[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = A[i][j] - B[i][j];
            }
        }
        return result;
    }

    public static float[][] transpose(float[][] A) {
        int rows = A.length;
        int cols = A[0].length;
        float[][] result = new float[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[j][i] = A[i][j];
            }
        }
        return result;
    }

    public static float[][] inverse_33(float[][] A){
        // 1) 3×3 행렬 원소 추출
        float a11 = A[0][0], a12 = A[0][1], a13 = A[0][2];
        float a21 = A[1][0], a22 = A[1][1], a23 = A[1][2];
        float a31 = A[2][0], a32 = A[2][1], a33 = A[2][2];

        // 2) 행렬식(det)
        float det = a11 * (a22 * a33 - a23 * a32)
                - a12 * (a21 * a33 - a23 * a31)
                + a13 * (a21 * a32 - a22 * a31);

        // det이 0 또는 매우 작은 경우 = 특이행렬
        if (Math.abs(det) < 1e-12) {
            throw new IllegalArgumentException("Matrix is singular or near-singular, det=" + det);
        }

        // 3) 수반행렬 adj(A) (여인수행렬(cofactor matrix)의 전치)
        //   = (cofactor[i][j])^T
        float[][] adj = new float[3][3];

        // cofactor 행렬을 직접 전개: Cij = (-1)^(i+j) * minor(i,j)
        // 여기서는 간단히 항목별로 계산
        adj[0][0] =  (a22*a33 - a23*a32);
        adj[0][1] = -(a12*a33 - a13*a32);
        adj[0][2] =  (a12*a23 - a13*a22);

        adj[1][0] = -(a21*a33 - a23*a31);
        adj[1][1] =  (a11*a33 - a13*a31);
        adj[1][2] = -(a11*a23 - a13*a21);

        adj[2][0] =  (a21*a32 - a22*a31);
        adj[2][1] = -(a11*a32 - a12*a31);
        adj[2][2] =  (a11*a22 - a12*a21);

        // 4) A^-1 = (1/det) × adj(A)
        float invDet = 1.0f / det;

        float[][] inv = new float[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inv[i][j] = adj[i][j] * invDet;
            }
        }
        return inv;
    }

    public static float[][] identityMatrix(int size) {
        float[][] I = new float[size][size];
        for (int i = 0; i < size; i++) {
            I[i][i] = 1.0f;
        }
        return I;
    }

    public static float[] vectorSubtract(float[] A, float[] B) {
        float[] result = new float[A.length];
        for (int i = 0; i < A.length; i++) {
            result[i] = A[i] - B[i];
        }
        return result;
    }

}
