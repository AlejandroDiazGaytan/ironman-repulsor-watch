package com.starkindustries.repulsor;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;

public class MainActivity extends Activity implements SensorEventListener {

    private RepulsorView repulsorView;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private SoundPool soundPool;
    private int soundChargeId = 0;
    private int soundBlastId = 0;
    private int soundCancelId = 0;
    private int activeStreamId = 0;

    private Vibrator vibrator;
    private AudioManager audioManager;

    // Filtro de gravedad y estado del gesto
    private final float[] gravity = new float[]{0f, 0f, 9.81f};
    private final float[] aimGravity = new float[]{0f, 0f, 9.81f};
    private boolean aimPoseCaptured = false;
    private float latestGyroMagnitude = 0f;

    private long stateTimestamp = 0L;
    private static final long CHARGE_SETTLE_MS = 520L;
    private static final long CANCEL_MIN_HOLD_MS = 780L;
    private static final long POST_ACTION_COOLDOWN_MS = 1100L;
    private boolean isIdleArmed = true;

    // Niveles de sensibilidad: 0 = ALTA, 1 = MEDIA, 2 = BAJA
    private int sensitivityIndex = 0;
    private final float[] thresholds = new float[]{3.8f, 6.0f, 9.0f};
    private final String[] labels = new String[]{
            "SENSIBILIDAD: ALTA",
            "SENSIBILIDAD: MEDIA",
            "SENSIBILIDAD: BAJA"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        repulsorView = new RepulsorView(this);
        repulsorView.setListener(new RepulsorView.OnRepulsorInteractionListener() {
            @Override
            public void onCenterTap() {
                // Si está en reposo -> Carga. Si ya está cargado -> ¡Dispara!
                if (repulsorView.getCurrentState() == RepulsorView.STATE_CHARGED) {
                    triggerBlast();
                } else {
                    triggerCharge();
                }
            }

            @Override
            public void onToggleSensitivity() {
                sensitivityIndex = (sensitivityIndex + 1) % thresholds.length;
                repulsorView.setSensitivityLabel(labels[sensitivityIndex]);
                vibrateShortTick();
            }
        });
        setContentView(repulsorView);

        // Asegurar volumen audible en el altavoz del Galaxy Watch 6
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (currentVol == 0 && maxVol > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (maxVol * 0.78f), 0);
            }
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();

        soundChargeId = soundPool.load(this, R.raw.repulsor_charge, 1);
        soundBlastId = soundPool.load(this, R.raw.repulsor_blast, 1);
        soundCancelId = soundPool.load(this, R.raw.repulsor_cancel, 1);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    private void playSound(int soundId, int rawFallbackRes) {
        if (soundPool != null && soundId != 0) {
            if (activeStreamId != 0) {
                soundPool.stop(activeStreamId);
            }
            activeStreamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
            if (activeStreamId != 0) return;
        }
        try {
            MediaPlayer mp = MediaPlayer.create(this, rawFallbackRes);
            if (mp != null) {
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.start();
            }
        } catch (Exception ignored) {
        }
    }

    // 1. CARGAR EL REPULSOR (Al levantar la mano)
    private void triggerCharge() {
        stateTimestamp = System.currentTimeMillis();
        aimPoseCaptured = false;
        repulsorView.showChargeState();
        playSound(soundChargeId, R.raw.repulsor_charge);

        // Vibración ascendente de carga
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] timings = new long[]{0, 70, 35, 90, 30, 120, 25, 160, 20, 220};
            int[] amplitudes = new int[]{0, 60, 0, 100, 0, 150, 0, 200, 0, 255};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                vibrator.vibrate(450);
            }
        }
    }

    // 2. DISPARAR EL PLASMA (Empujón/sacudida con la mano cargada o toque en pantalla)
    private void triggerBlast() {
        stateTimestamp = System.currentTimeMillis();
        isIdleArmed = false;
        repulsorView.showBlastState();
        playSound(soundBlastId, R.raw.repulsor_blast);

        // Vibración potente de cañonazo y retroceso
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] timings = new long[]{0, 220, 35, 110, 40, 80};
            int[] amplitudes = new int[]{0, 255, 0, 190, 0, 110};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                vibrator.vibrate(380);
            }
        }
    }

    // 3. CANCELAR / DESCARGAR (Al bajar la mano sin disparar -> Audio en reversa)
    private void triggerCancel() {
        stateTimestamp = System.currentTimeMillis();
        isIdleArmed = false;
        repulsorView.showCancelState();
        playSound(soundCancelId, R.raw.repulsor_cancel);

        // Vibración descendente suave (desenergizando)
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] timings = new long[]{0, 140, 30, 100, 35, 70, 40, 50};
            int[] amplitudes = new int[]{0, 190, 0, 130, 0, 80, 0, 40};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                vibrator.vibrate(250);
            }
        }
    }

    private void vibrateShortTick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(35);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float gx = event.values[0];
            float gy = event.values[1];
            float gz = event.values[2];
            latestGyroMagnitude = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            return;
        }

        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Separar vector de gravedad (orientación de la mano) y aceleración lineal (impulso)
        final float alpha = 0.82f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        float lx = event.values[0] - gravity[0];
        float ly = event.values[1] - gravity[1];
        float lz = event.values[2] - gravity[2];
        float linearAccel = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);

        long now = System.currentTimeMillis();
        long elapsedInState = now - stateTimestamp;
        float baseThreshold = thresholds[sensitivityIndex];
        int state = repulsorView.getCurrentState();

        // =========================================================================
        // ESTADO A: REPULSOR EN REPOSO -> Detectar levantamiento de mano para CARGAR
        // =========================================================================
        if (state == RepulsorView.STATE_IDLE) {
            if (!isIdleArmed && linearAccel < baseThreshold * 0.42f) {
                isIdleArmed = true;
            }

            if (isIdleArmed && elapsedInState > POST_ACTION_COOLDOWN_MS && linearAccel > baseThreshold) {
                isIdleArmed = false;
                triggerCharge();
            }
            return;
        }

        // =========================================================================
        // ESTADO B: REPULSOR CARGADO -> Detectar DISPARO o BAJADA DE MANO (CANCELAR)
        // =========================================================================
        if (state == RepulsorView.STATE_CHARGED) {
            // Capturar la orientación de puntería justo cuando termina de levantar la mano
            if (!aimPoseCaptured && elapsedInState > 360L) {
                aimGravity[0] = gravity[0];
                aimGravity[1] = gravity[1];
                aimGravity[2] = gravity[2];
                aimPoseCaptured = true;
            }

            // 1. ¿Hizo el gesto de DISPARO? (Empujón/retroceso rápido con la muñeca)
            if (elapsedInState > CHARGE_SETTLE_MS) {
                float fireThreshold = baseThreshold * 1.08f;
                boolean sharpSnap = (linearAccel > fireThreshold) || (linearAccel > baseThreshold * 0.78f && latestGyroMagnitude > 4.2f);
                if (sharpSnap) {
                    triggerBlast();
                    return;
                }
            }

            // 2. ¿Bajó la mano sin disparar? (Cambio de ángulo gravitacional suave)
            if (aimPoseCaptured && elapsedInState > CANCEL_MIN_HOLD_MS) {
                float dgx = gravity[0] - aimGravity[0];
                float dgy = gravity[1] - aimGravity[1];
                float dgz = gravity[2] - aimGravity[2];
                float poseDelta = (float) Math.sqrt(dgx * dgx + dgy * dgy + dgz * dgz);

                // Si rotó/bajó el brazo alejándose de la postura de puntería sin dar un golpe brusco
                if (poseDelta > 4.1f && linearAccel < baseThreshold * 0.90f) {
                    triggerCancel();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
