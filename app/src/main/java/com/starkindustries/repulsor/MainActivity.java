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
    private SoundPool soundPool;
    private int repulsorSoundId = 0;
    private boolean soundLoaded = false;
    private Vibrator vibrator;
    private AudioManager audioManager;

    // Filtro de gravedad para aislar el movimiento brusco de levantar la mano
    private final float[] gravity = new float[]{0f, 0f, 9.81f};
    private long lastTriggerTime = 0L;
    private static final long COOLDOWN_MS = 1350L;
    private boolean isArmed = true;

    // Niveles de sensibilidad: 0 = ALTA, 1 = MEDIA, 2 = BAJA
    private int sensitivityIndex = 0;
    private final float[] thresholds = new float[]{3.8f, 6.2f, 9.5f};
    private final String[] labels = new String[]{
            "SENSIBILIDAD: ALTA",
            "SENSIBILIDAD: MEDIA",
            "SENSIBILIDAD: BAJA"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mantener pantalla encendida en el Galaxy Watch 6 mientras se usa el repulsor
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        repulsorView = new RepulsorView(this);
        repulsorView.setListener(new RepulsorView.OnRepulsorInteractionListener() {
            @Override
            public void onManualTrigger() {
                fireRepulsor();
            }

            @Override
            public void onToggleSensitivity() {
                sensitivityIndex = (sensitivityIndex + 1) % thresholds.length;
                repulsorView.setSensitivityLabel(labels[sensitivityIndex]);
                vibrateShortTick();
            }
        });
        setContentView(repulsorView);

        // Configurar audio para el altavoz del Galaxy Watch 6
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (currentVol == 0 && maxVol > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (maxVol * 0.75f), 0);
            }
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();

        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status == 0) {
                soundLoaded = true;
            }
        });

        repulsorSoundId = soundPool.load(this, R.raw.repulsor_charge, 1);

        // Configurar vibrador háptico del reloj
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Configurar acelerómetro
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void fireRepulsor() {
        lastTriggerTime = System.currentTimeMillis();
        repulsorView.triggerChargeEffect();

        // 1. Reproducir sonido con cero latencia
        if (soundLoaded && repulsorSoundId != 0) {
            soundPool.play(repulsorSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else {
            try {
                MediaPlayer mp = MediaPlayer.create(this, R.raw.repulsor_charge);
                if (mp != null) {
                    mp.setOnCompletionListener(MediaPlayer::release);
                    mp.start();
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Patrón de vibración ascendente sincronizado con la carga de plasma (1 segundo)
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] timings = new long[]{0, 80, 40, 100, 35, 130, 30, 180, 20, 260};
            int[] amplitudes = new int[]{0, 60, 0, 100, 0, 150, 0, 200, 0, 255};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                vibrator.vibrate(500);
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
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
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
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Filtro paso-bajo para estimar la gravedad y paso-alto para la aceleración lineal de la mano
        final float alpha = 0.82f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        float lx = event.values[0] - gravity[0];
        float ly = event.values[1] - gravity[1];
        float lz = event.values[2] - gravity[2];

        float linearAccel = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        long now = System.currentTimeMillis();

        float currentThreshold = thresholds[sensitivityIndex];

        // Rearmar cuando la mano se estabiliza ligeramente
        if (!isArmed && linearAccel < currentThreshold * 0.45f) {
            isArmed = true;
        }

        // Si levanta la mano con fuerza mayor al umbral y pasó el tiempo de enfriamiento
        if (isArmed && (now - lastTriggerTime > COOLDOWN_MS) && linearAccel > currentThreshold) {
            isArmed = false;
            fireRepulsor();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
