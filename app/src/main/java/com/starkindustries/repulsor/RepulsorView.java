package com.starkindustries.repulsor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public class RepulsorView extends View {

    public interface OnRepulsorInteractionListener {
        void onCenterTap();
        void onToggleSensitivity();
    }

    public static final int STATE_IDLE = 0;
    public static final int STATE_CHARGED = 1;
    public static final int STATE_BLAST = 2;
    public static final int STATE_CANCEL = 3;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shockwavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final RectF btnRect = new RectF();

    private int currentState = STATE_IDLE;
    private float outerAngle = 0f;
    private float innerAngle = 0f;
    private float chargeLevel = 0f;     // 0.0 a 1.0
    private float blastWave = 0f;       // 0.0 a 1.0 para la onda expansiva del disparo
    private String sensitivityLabel = "SENSIBILIDAD: ALTA";
    private OnRepulsorInteractionListener listener;

    public RepulsorView(Context context) {
        super(context);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.BLACK);
        bgPaint.setStyle(Paint.Style.FILL);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.parseColor("#00E5FF"));

        shockwavePaint.setStyle(Paint.Style.STROKE);

        corePaint.setStyle(Paint.Style.FILL);
        corePaint.setColor(Color.parseColor("#E0FFFF"));

        glowPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.parseColor("#00E5FF"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        subTextPaint.setColor(Color.parseColor("#80D8FF"));
        subTextPaint.setTextAlign(Paint.Align.CENTER);

        btnPaint.setStyle(Paint.Style.STROKE);
        btnPaint.setStrokeWidth(2.5f);
        btnPaint.setColor(Color.parseColor("#00B8D4"));
    }

    public void setListener(OnRepulsorInteractionListener listener) {
        this.listener = listener;
    }

    public void setSensitivityLabel(String label) {
        this.sensitivityLabel = label;
        invalidate();
    }

    public void showChargeState() {
        currentState = STATE_CHARGED;
        chargeLevel = 1.0f;
        blastWave = 0f;
        invalidate();
    }

    public void showBlastState() {
        currentState = STATE_BLAST;
        chargeLevel = 1.0f;
        blastWave = 0.05f;
        invalidate();
    }

    public void showCancelState() {
        currentState = STATE_CANCEL;
        blastWave = 0f;
        invalidate();
    }

    public int getCurrentState() {
        return currentState;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float minDim = Math.min(w, h);
        float maxRadius = minDim / 2f;

        // 1. Fondo negro puro para pantalla AMOLED
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Iluminación ambiental según estado
        if (currentState == STATE_BLAST && blastWave < 0.6f) {
            int flashAlpha = (int) ((1f - (blastWave / 0.6f)) * 220);
            canvas.drawColor(Color.argb(flashAlpha, 255, 248, 225));
        } else if (chargeLevel > 0.02f) {
            int alpha = (int) (chargeLevel * 115);
            canvas.drawColor(Color.argb(alpha, 0, 229, 255));
        }

        // 2. Anillo perimetral exterior segmentado
        float outerRadius = maxRadius * 0.88f;
        ringPaint.setStrokeWidth(minDim * 0.018f);
        if (currentState == STATE_BLAST) {
            ringPaint.setColor(Color.parseColor("#FFD54F"));
        } else {
            ringPaint.setColor(Color.argb(150, 0, 229, 255));
        }
        arcRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius);

        canvas.save();
        canvas.rotate(outerAngle, cx, cy);
        for (int i = 0; i < 12; i++) {
            canvas.drawArc(arcRect, i * 30f + 4f, 22f, false, ringPaint);
        }
        canvas.restore();

        // 3. Anillo intermedio de bobinas del Reactor Arc (10 segmentos)
        float midRadius = maxRadius * (0.64f + (currentState == STATE_BLAST ? blastWave * 0.08f : 0f));
        ringPaint.setStrokeWidth(minDim * 0.055f);
        if (currentState == STATE_BLAST) {
            ringPaint.setColor(Color.parseColor("#FFF59D"));
        } else if (currentState == STATE_CHARGED) {
            ringPaint.setColor(Color.WHITE);
        } else {
            ringPaint.setColor(Color.parseColor("#00E5FF"));
        }
        arcRect.set(cx - midRadius, cy - midRadius, cx + midRadius, cy + midRadius);

        canvas.save();
        canvas.rotate(-innerAngle, cx, cy);
        for (int i = 0; i < 10; i++) {
            canvas.drawArc(arcRect, i * 36f + 5f, 26f, false, ringPaint);
        }
        canvas.restore();

        // 4. Anillo interior de contención
        float innerRingRadius = maxRadius * 0.44f;
        ringPaint.setStrokeWidth(minDim * 0.012f);
        ringPaint.setColor(Color.parseColor("#80D8FF"));
        canvas.drawCircle(cx, cy, innerRingRadius, ringPaint);

        // 5. Onda expansiva de choque cuando se ejecuta el DISPARO
        if (currentState == STATE_BLAST && blastWave > 0f) {
            float waveRadius1 = maxRadius * (0.25f + blastWave * 0.72f);
            int waveAlpha = (int) ((1f - blastWave) * 255);
            shockwavePaint.setStrokeWidth(minDim * (0.08f * (1f - blastWave) + 0.01f));
            shockwavePaint.setColor(Color.argb(waveAlpha, 255, 255, 255));
            canvas.drawCircle(cx, cy, waveRadius1, shockwavePaint);

            float waveRadius2 = maxRadius * (0.15f + blastWave * 0.55f);
            shockwavePaint.setStrokeWidth(minDim * 0.03f);
            shockwavePaint.setColor(Color.argb(waveAlpha, 0, 229, 255));
            canvas.drawCircle(cx, cy, waveRadius2, shockwavePaint);
        }

        // 6. Resplandor radial y núcleo central de plasma
        float pulse = (currentState == STATE_CHARGED)
                ? (float) (0.03f * Math.sin(System.currentTimeMillis() * 0.025))
                : 0f;
        float coreBaseRadius = maxRadius * (0.23f + 0.16f * chargeLevel + pulse);
        float glowRadius = coreBaseRadius * (1.8f + 0.85f * chargeLevel);

        int centerGlowColor = (currentState == STATE_BLAST)
                ? Color.argb(255, 255, 249, 196)
                : Color.argb((int) (190 + 65 * chargeLevel), 0, 229, 255);

        RadialGradient gradient = new RadialGradient(
                cx, cy, Math.max(glowRadius, 1f),
                new int[]{
                        Color.WHITE,
                        centerGlowColor,
                        Color.argb(0, 0, 184, 212)
                },
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, glowRadius, glowPaint);

        corePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, coreBaseRadius * 0.66f, corePaint);

        // 7. Textos de estado HUD (Superior)
        textPaint.setTextSize(minDim * 0.048f);
        String statusText;
        if (currentState == STATE_BLAST) {
            statusText = "💥 ¡DISPARO DE PLASMA! 💥";
            textPaint.setColor(Color.parseColor("#FFE082"));
        } else if (currentState == STATE_CHARGED) {
            statusText = "⚡ LISTO: EMPUJA P/ DISPARAR ⚡";
            textPaint.setColor(Color.WHITE);
        } else if (currentState == STATE_CANCEL) {
            statusText = "🔉 CANCELANDO CARGA...";
            textPaint.setColor(Color.parseColor("#80D8FF"));
        } else {
            statusText = "MK-85 REPULSOR";
            textPaint.setColor(Color.parseColor("#00E5FF"));
        }
        canvas.drawText(statusText, cx, cy - maxRadius * 0.69f, textPaint);

        // 8. Botón inferior de sensibilidad
        subTextPaint.setTextSize(minDim * 0.041f);
        float btnW = minDim * 0.54f;
        float btnH = minDim * 0.11f;
        float btnTop = cy + maxRadius * 0.66f;
        btnRect.set(cx - btnW / 2f, btnTop, cx + btnW / 2f, btnTop + btnH);
        canvas.drawRoundRect(btnRect, btnH / 2f, btnH / 2f, btnPaint);
        canvas.drawText(sensitivityLabel, cx, btnTop + btnH * 0.68f, subTextPaint);

        // 9. Dinámica de animación de anillos y estados (60 FPS)
        if (currentState == STATE_CHARGED) {
            // En estado cargado, se mantiene al 100% girando rápido esperando disparo o bajada de mano
            chargeLevel = 1.0f;
            outerAngle = (outerAngle + 4.8f) % 360f;
            innerAngle = (innerAngle + 7.5f) % 360f;
        } else if (currentState == STATE_BLAST) {
            outerAngle = (outerAngle + 9.0f) % 360f;
            innerAngle = (innerAngle + 12.0f) % 360f;
            blastWave += 0.042f;
            chargeLevel = Math.max(0f, 1.0f - blastWave);
            if (blastWave >= 1.0f) {
                blastWave = 0f;
                chargeLevel = 0f;
                currentState = STATE_IDLE;
            }
        } else if (currentState == STATE_CANCEL) {
            // Gira en reversa mientras se descarga el repulsor
            outerAngle = (outerAngle - 3.2f + 360f) % 360f;
            innerAngle = (innerAngle - 5.0f + 360f) % 360f;
            chargeLevel = Math.max(0f, chargeLevel - 0.025f);
            if (chargeLevel <= 0f) {
                currentState = STATE_IDLE;
            }
        } else {
            outerAngle = (outerAngle + 0.6f) % 360f;
            innerAngle = (innerAngle + 1.1f) % 360f;
        }

        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            // Tocar el botón inferior cambia sensibilidad
            if (btnRect.contains(x, y) || y > getHeight() * 0.75f) {
                if (listener != null) {
                    listener.onToggleSensitivity();
                }
                return true;
            }

            // Tocar el centro del Reactor Arc
            if (listener != null) {
                listener.onCenterTap();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
