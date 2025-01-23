package collision_detection;

import android.app.Activity;
import android.content.Context;
import android.media.Image;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.examples.java.helloar.ObjectDetectionOverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import od_task.Detectionclass;

public class TrackingSystem {
    private ObjectTrackingModule trackingModule;
    //private ObjectManagementModule managementModule;
    private CollisionpredictModule collisiondetector;
    private TTSManager ttsManager;
    private TextToSpeech tts;
    private boolean isTTSInitialized = false;
    private Context context;
    private ObjectDetectionOverlay overlayView;
    private float[] prev_location;
    private float[] userVelocity;
    public TrackingSystem(Context context, ObjectDetectionOverlay overlayView ) {
        this.context = context;
        this.overlayView = overlayView;
        trackingModule = new ObjectTrackingModule();
        collisiondetector = new CollisionpredictModule();
        userVelocity = new float[3];
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // 초기화 성공 시 TTSManager 생성
                ttsManager = new TTSManager(tts);
                isTTSInitialized = true; // 초기화 완료 표시
                Log.d("TrackingSystem", "TextToSpeech initialized successfully.");
            } else {
                // 초기화 실패 처리
                Log.e("TrackingSystem", "Failed to initialize TextToSpeech.");
            }
        });

    }
    public static class CameraInfo {
        private static CameraIntrinsics intrinsics;

        public static CameraIntrinsics getIntrinsics() {
            if (intrinsics == null) {
                throw new IllegalStateException("Intrinsics not initialized");
            }
            return intrinsics;
        }
        public static boolean isInitialized() {
            return intrinsics != null;
        }

        // Intrinsics 초기화 (첫 번째 프레임에서만)
        public static void initializeIntrinsics(Frame frame) {
            if (!isInitialized()) {
                intrinsics = frame.getCamera().getTextureIntrinsics();
            }
        }
    }


    public List<Object_Info>  processFrame(List<Detectionclass> detections, Image depthMap, Pose camerapose, float deltaTime) {

        if(prev_location ==null){
            userVelocity[0] = 0.0f;
            userVelocity[1] = 0.0f;
            userVelocity[2] = 0.0f;
        }
        else{
            userVelocity[0] = (camerapose.tx()-prev_location[0])/deltaTime;
            userVelocity[1] = (camerapose.ty()-prev_location[1])/deltaTime;
            userVelocity[2] = (camerapose.tz()-prev_location[2])/deltaTime;
        }
        prev_location = camerapose.getTranslation();


        //현재 프레임의 물체 추적, 정보 저장
        List<Object_Info> trackedObjects = trackingModule.objectTrack(detections, depthMap, camerapose, deltaTime);

        // 추적 결과 바탕으로 업데이트
        trackingModule.updateObjects(trackedObjects);

        // 활성 물체 목록을 갱신
        List<Object_Info> activeObjects = trackingModule.getActiveObjects();
        //trackingModule.displayObjects(); // 활성화된 물체 출력

        CollisionWarning warning = collisiondetector.processCollision(activeObjects, camerapose.getTranslation(), userVelocity);
        if (ttsManager.canSpeak()) {
            // 충돌 여부 판단

            if(warning!=null) {
                String message = warning.getMessage();
                if (message != null) {
                    ttsManager.speak(message);
                }
            }
        }

        return activeObjects;
    }


}

class CollisionWarning {
    private int objectid;
    private String label;
    private double collision_time;
    private Object_Info object;
    public CollisionWarning(Object_Info alarmobject, double collision_time) {
        this.object = alarmobject;
        this.objectid = alarmobject.getid();
        this.label = alarmobject.getlabel();
        this.collision_time = collision_time;

    }
    public String getMessage() {

            return "Watch out " + label;

    }
    public String getlabel(){
        return this.label;
    }
    public double getcollision_time(){
        return this.collision_time;
    }
    public Object_Info getobject(){
        return this.object;
    }
}

