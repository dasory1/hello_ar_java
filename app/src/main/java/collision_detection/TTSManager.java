package collision_detection;
import android.speech.tts.TextToSpeech;
import android.content.Context;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

public class TTSManager {
    private final TextToSpeech tts;
    private boolean isSpeaking = false;
    private long lastSpeakingEndTime = 0; // 마지막 TTS 종료 시간
    private final long speakingCooldown = 500; // 1초 대기 시간 (밀리초 단위)

    public TTSManager(TextToSpeech tts) {
        this.tts = tts;
        tts.setLanguage(Locale.US);
        tts.setSpeechRate(1.2f);
        tts.setPitch(1.0f);

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                isSpeaking = true; // 음성 시작
            }

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false; // 음성 종료
                lastSpeakingEndTime = System.currentTimeMillis(); // 종료 시간 기록
            }

            @Override
            public void onError(String utteranceId) {
                isSpeaking = false; // 오류 시 상태 초기화
                lastSpeakingEndTime = System.currentTimeMillis(); // 오류 발생 시 종료 시간 기록
            }
        });
    }

    public void speak(String message) {
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "CollisionWarning");
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public boolean canSpeak() {
        return !isSpeaking && (System.currentTimeMillis() - lastSpeakingEndTime >= speakingCooldown);
    }
}