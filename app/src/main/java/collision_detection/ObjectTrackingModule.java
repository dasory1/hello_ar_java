package collision_detection;

import static collision_detection.CalculationUtils.*;

import android.media.Image;
import android.util.Log;

import com.google.ar.core.Pose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import od_task.Detectionclass;
public class ObjectTrackingModule {

    private List<Object_Info> existingTracks = new ArrayList<>(); // 이전 프레임의 추적 정보
    private int nextTrackId = 1;
    private int maxlost = 5;

    // 현재 프레임의 탐지 결과와 이전 프레임의 정보를 결합하여 추적 수행
    public List<Object_Info> objectTrack(List<Detectionclass> detections, Image depthMap, Pose cameraPose, float deltaTime) {
        List<Object_Info> updatedTracks = new ArrayList<>(); // 현재 프레임에서 업데이트된 트랙
        boolean[] matchedDetections = new boolean[detections.size()]; // 탐지 결과 매칭 여부

        // 1. 이전 프레임의 트랙 정보로 object의 현재 상태 예측
        for (Object_Info track : existingTracks) {
            track.predict(deltaTime); // 이전 속도와 위치를 기반으로 현재 위치 예측
        }

        // 2. 탐지 결과와 기존 트랙 매칭
        for (Object_Info track : existingTracks) {
            Detectionclass bestMatch = null;
            float bestScore = 0.5f; // 매칭 기준값 (IoU 또는 거리)
            int bestMatchIndex = -1;

            for (int i = 0; i < detections.size(); i++) {
                if (matchedDetections[i]) continue; // 이미 매칭된 탐지 결과는 스킵

                Detectionclass detection = detections.get(i);

                // IoU 계산
                float score = calculateIoU(track.getBoundingbox(), detection.getBoundingBox());

                if (score > bestScore ) { // 거리 임계값: 50.0m
                    bestScore = score;
                    bestMatch = detection;
                    bestMatchIndex = i;
                }
            }

            if (bestMatch != null) {
                // 매칭된 탐지 결과로 물체 상태 업데이트
                track.update(bestMatch, depthMap, cameraPose, deltaTime);
                matchedDetections[bestMatchIndex] = true; // 탐지 결과 매칭 완료
            } else {
                // 매칭 실패한 물체는 탐지 실패 횟수 증가
                track.incrementLostCount();
            }
            updatedTracks.add(track);
        }

        // 3. 새로운 탐지 결과 등록
        for (int i = 0; i < detections.size(); i++) {
            if (!matchedDetections[i]) {
                Detectionclass detection = detections.get(i);
                Object_Info newTrack = new Object_Info(nextTrackId++, detection, depthMap, cameraPose); // 새로운 트랙 생성
                Log.d("new","new object added");
                updatedTracks.add(newTrack); // 새로운 트랙을 업데이트된 트랙 목록에 추가
            }
        }

        // 4. 기존 트랙 리스트 갱신
        existingTracks.clear();
        existingTracks.addAll(updatedTracks);

        return updatedTracks;
    }
    public List<Object_Info> getActiveObjects(){
            return existingTracks;
        };
    public void updateObjects(List<Object_Info> trackedObjects){
        if (trackedObjects == null) {
            return;
        }
        trackedObjects.removeIf(object -> object.getLostCount() > maxlost);
        existingTracks = trackedObjects;
    }
    public void displayObjects() {
        if(existingTracks.isEmpty()) {
            Log.d("In active", "Nothing Active");
            return;
        }

        for (Object_Info object : existingTracks) {

            Log.d("In active ", "id:" + object.getid() +" "+ object.getlabel()+", p:"+ Arrays.toString(object.getPosition())+" lost:"+object.getLostCount());
        }
    }


}

