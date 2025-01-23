package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.ar.core.Pose;

import java.util.ArrayList;
import java.util.List;

import collision_detection.Object_Info;


public class ObjectDetectionOverlay extends View {
    private List<Object_Info> activeobjects = new ArrayList<>();
    private Pose pose;
    private Object_Info warning_object;
    private Paint boxPaint, textPaint, WarningPaint;

    public ObjectDetectionOverlay(Context context) {
        super(context);
        init();
    }

    // 새로 추가한 XML용 생성자
    public ObjectDetectionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // 선택적으로 추가: 스타일 속성을 사용하는 경우
    public ObjectDetectionOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Bounding box 스타일
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5);

        // 텍스트 스타일
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50);
    }

    public void setobjects_pose(List<Object_Info> activeobjects, Pose pose) {

        this.activeobjects = activeobjects;
        this.pose = pose;
        invalidate(); // 뷰 갱신 요청
    }

    public void setwarning(Object_Info object) {
        this.warning_object = object;
        invalidate(); // 뷰 갱신 요청
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        List<Object_Info> objectcopy = new ArrayList<>(activeobjects);
        if (objectcopy.isEmpty()) {
            // Maybe draw a "No detections" text or skip rendering

            canvas.save();                                // 현재 캔버스 상태 저장
            canvas.rotate(90, 50, 50);            // 캔버스를 -90도 회전 (pivot: (textX,textY))
            canvas.drawText("No detections", 50, 50, textPaint);
            canvas.restore();
            return;
        }
        for (Object_Info object : objectcopy) {
            // Bounding box 그리기
            canvas.drawRect(object.getBoundingbox(), boxPaint);
            // Label 및 Confidence 표시
            String text = object.getlabel() + "(" + String.format("%.2f", object.getConfidenceScore()) + ")";

            String textpos = " (" + String.format("%.1f, %.1f, %.1f", object.getPosition()[0], object.getPosition()[1], object.getPosition()[2]) + ")";
            String textvel = "(" + String.format("%.1f, %.1f, %.1f", object.getVelocity()[0], object.getVelocity()[1], object.getVelocity()[2]) + ")";
            double distance = Math.sqrt(Math.pow(object.getPosition()[0] - pose.tx(), 2) + Math.pow(object.getPosition()[1] - pose.ty(), 2) + Math.pow(object.getPosition()[2] - pose.tz(), 2));
            float textX = object.getBoundingbox().left;
            float textY = object.getBoundingbox().top - 10;
            float textmidx = (object.getBoundingbox().left + object.getBoundingbox().right) / 2;
            float textmidy = (object.getBoundingbox().top + object.getBoundingbox().bottom) / 2;

            canvas.save();                                // 현재 캔버스 상태 저장
            canvas.rotate(90, textX, textY);            // 캔버스를 -90도 회전 (pivot: (textX,textY))
            canvas.drawText(text, textX, textY, textPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(90, textmidx, textmidy);
            canvas.drawText("d:" + String.format("%.1f", distance), textmidx, textmidy, textPaint);
            canvas.restore();                             // 회전 전 상태로 복원


        }


    }
}