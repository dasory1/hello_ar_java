package collision_detection;

import static collision_detection.CalculationUtils.get3DPosition;
import static collision_detection.CalculationUtils.identityMatrix;
import static collision_detection.CalculationUtils.inverse_33;
import static collision_detection.CalculationUtils.matrixAdd;
import static collision_detection.CalculationUtils.matrixMultiply;
import static collision_detection.CalculationUtils.matrixSubtract;
import static collision_detection.CalculationUtils.matrixVectorMultiply;
import static collision_detection.CalculationUtils.transpose;
import static collision_detection.CalculationUtils.vectorSubtract;

import android.graphics.RectF;
import android.media.Image;
import android.util.Log;

import com.google.ar.core.Pose;

import java.util.Arrays;

import od_task.Detectionclass;

public class Object_Info {
    private int id; // 물체 ID
    private KalmanFilter3D kalmanFilter; // 칼만 필터
    private Detectionclass recent_detection;
    private int lostCount; // 탐지 실패 횟수

    public Object_Info(int id, Detectionclass detection, Image depthMap, Pose cameraPose) {
        this.id = id;
        this.recent_detection = detection;

        float[] initialPosition = get3DPosition(detection, depthMap, cameraPose);
        Log.d("Position","initial pos: "+String.format("%.2f, %.2f, %.2f",initialPosition[0],initialPosition[1],initialPosition[2]));

        float[][] initialCovariance = new float[6][6];
        float[][] processNoise = new float[6][6];
        float[][] measurementNoise = new float[3][3];

        for (int i = 0; i < 6; i++) {
            initialCovariance[i][i] = 0.2f;
            if (i < 3) measurementNoise[i][i] = 0.1f;
            processNoise[i][i] = 0.005f;
        }

        // 칼만 필터 초기화
        this.kalmanFilter = new KalmanFilter3D(initialPosition, initialCovariance, processNoise, measurementNoise);
        this.lostCount = 0;
    }


    public void predict(float deltaTime) {
        // 칼만 필터를 사용하여 위치와 속도 예측
        kalmanFilter.predict(deltaTime);
    }

    public void update(Detectionclass detection, Image depthMap, Pose cameraPose, float deltaTime) {
        // 탐지 결과를 기반으로 칼만 필터 상태 업데이트
        float[] observedPosition = get3DPosition(detection, depthMap, cameraPose);
      //  Log.d("update","id:"+this.getid()+" Observed p:("+String.format("%.2f, %.2f, %.2f",observedPosition[0],observedPosition[1],observedPosition[2])+")");
        kalmanFilter.update(observedPosition, deltaTime);
        //Log.d("update","id:"+this.getid()+" updated v:("+String.format("%.2f, %.2f, %.2f",this.getVelocity()[0],this.getVelocity()[1],this.getVelocity()[2])+")");
        this.recent_detection = detection;
        this.lostCount = 0; // 탐지 성공 시 lostCount 초기화
    }

    public void incrementLostCount() {
        this.lostCount++;
    }

    public int getLostCount() {
        return this.lostCount;
    }

    public int getid() {
        return this.id;
    }

    public float[] getPosition() {
        return Arrays.copyOfRange(kalmanFilter.getstate(), 0, 3); // 현재 위치 반환
    }

    public float[] getVelocity() {
        return Arrays.copyOfRange(kalmanFilter.getstate(), 3, 6); // 현재 위치 반환
    }

    public RectF getBoundingbox() {
        return this.recent_detection.getBoundingBox();
    }


    public float getConfidenceScore() {
        return this.recent_detection.getCategories().getScore();
    }


    public String getlabel() {
        return this.recent_detection.getCategories().getLabel();
    }



}
class KalmanFilter3D {

    private float[] state; // [p_x, p_y, p_z, v_x, v_y, v_z]
    private float[][] covariance; // State covariance matrix
    private float[][] processNoise; // Process noise matrix
    private float[][] measurementNoise; // Measurement noise matrix
    //private float[][] kalmanGain; // Kalman gain

    public KalmanFilter3D(float[] initialPosition, float[][] initialCovariance, float[][] processNoise, float[][] measurementNoise) {
        this.state = new float[] {initialPosition[0], initialPosition[1], initialPosition[2], 0.0f, 0.0f, 0.0f};
        this.covariance = initialCovariance;
        this.processNoise = processNoise;
        this.measurementNoise = measurementNoise;
        //this.kalmanGain = new float[6][3];
    }

    public void predict(float dt) {
        float[][] F = {
                {1, 0, 0,    dt, 0, 0 },
                {0, 1, 0,    0, dt, 0 },
                {0, 0, 1,    0, 0, dt },
                /* 속도 -> 속도 + a*dt */
                {0, 0, 0,    1, 0, 0, },
                {0, 0, 0,    0, 1, 0, },
                {0, 0, 0,    0, 0, 1, },

        };

        // 1) 상태 예측: state = F * state
        state = matrixVectorMultiply(F, state);

        // 2) 공분산 예측: P = F * P * F^T + Q
        covariance = matrixAdd(
                matrixMultiply(
                        matrixMultiply(F, covariance),
                        transpose(F)
                ),
                processNoise  // Q
        );

        // return new float[] {state[0], state[1], state[2], state[3], state[4], state[5]}; // Predicted position and velocity
    }

    public void update(float[] measuredPosition, float deltaTime) {
        float[][] H = {
                {1, 0, 0, 0, 0, 0},
                {0, 1, 0, 0, 0, 0},
                {0, 0, 1, 0, 0, 0}
        };

        // 1) Residual: y = z - H * x
        float[] hX = matrixVectorMultiply(H, state); // 예측된 위치
        float[] y  = vectorSubtract(measuredPosition, hX);

        // 2) S = H*P*H^T + R  (측정 예측 공분산) (3*3)
        float[][] S = matrixAdd(
                matrixMultiply(
                        matrixMultiply(H, covariance),
                        transpose(H)
                ),
                measurementNoise  // R
        );

        // 3) K = P * H^T * S^-1  (칼만 이득) (6*3)
        float[][] S_inv  = inverse_33(S);
        float[][] K      = matrixMultiply(
                matrixMultiply(covariance, transpose(H)),
                S_inv
        );

        // 4) 상태 업데이트: x = x + K * y
        float[] adjustment = matrixVectorMultiply(K, y);
        for (int i = 0; i < state.length; i++) {
            state[i] += adjustment[i];
        }

        // 5) 공분산 업데이트: P = (I - K*H) * P
        float[][] I = identityMatrix(6);
        float[][] KH = matrixMultiply(K, H);
        float[][] IminusKH = matrixSubtract(I, KH);
        covariance = matrixMultiply(IminusKH, covariance);
    }

    public float[] getstate(){
        return this.state;
    }
}
