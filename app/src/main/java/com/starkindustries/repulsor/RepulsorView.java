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
        void onManualTrigger();
        void onToggleSensitivity();
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final RectF btnRect = new RectF();

    private float outerAngle = 0f;
    private float innerAngle = 0f;
    private float chargeLevel = 0f; // 0.0 (idle) a 1.0 (full charge)
    private boolean isCharging = false;
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

    public void triggerChargeEffect() {
        isCharging = true;
        chargeLevel = 1.0f;
        invalidate();
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

        // 1. Fondo negro puro para pantalla AMOLED del Galaxy Watch 6
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Destello ambiental cuando está cargando el repulsor
        if (chargeLevel > 0.02f) {
            int alpha = (int) (chargeLevel * 130);
            canvas.drawColor(Color.argb(alpha, 0, 229, 255));
        }

        // 2. Anillo perimetral exterior segmentado (HUD Stark)
        float outerRadius = maxRadius * 0.88f;
        ringPaint.setStrokeWidth(minDim * 0.018f);
        ringPaint.setColor(Color.argb(140, 0, 229, 255));
        arcRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius);

        canvas.save();
        canvas.rotate(outerAngle, cx, cy);
        for (int i = 0; i < 12; i++) {
            canvas.drawArc(arcRect, i * 30f + 4f, 22f, false, ringPaint);
        }
        canvas.restore();

        // 3. Anillo intermedio de bobinas del Reactor Arc (10 segmentos)
        float midRadius = maxRadius * 0.64f;
        ringPaint.setStrokeWidth(minDim * 0.055f);
        int coilColor = isCharging ? Color.parseColor("#FFFFFF") : Color.parseColor("#00E5FF");
        ringPaint.setColor(coilColor);
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

        // 5. Resplandor radial y núcleo central de plasma
        float coreBaseRadius = maxRadius * (0.24f + 0.16f * chargeLevel);
        float glowRadius = coreBaseRadius * (1.8f + 0.8f * chargeLevel);

        RadialGradient gradient = new RadialGradient(
                cx, cy, Math.max(glowRadius, 1f),
                new int[]{
                        Color.argb(255, 224, 255, 255),
                        Color.argb((int) (190 + 65 * chargeLevel), 0, 229, 255),
                        Color.argb(0, 0, 184, 212)
                },
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, glowRadius, glowPaint);

        corePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, coreBaseRadius * 0.65f, corePaint);

        // 6. Textos HUD (Superior e Inferior)
        textPaint.setTextSize(minDim * 0.052f);
        String statusText = isCharging ? "⚡ CARGANDO PLASMA ⚡" : "MK-85 REPULSOR";
        textPaint.setColor(isCharging ? Color.WHITE : Color.parseColor("#00E5FF"));
        canvas.drawText(statusText, cx, cy - maxRadius * 0.69f, textPaint);

        // Botón inferior de sensibilidad
        subTextPaint.setTextSize(minDim * 0.042f);
        float btnW = minDim * 0.54f;
        float btnH = minDim * 0.11f;
        float btnTop = cy + maxRadius * 0.66f;
        btnRect.set(cx - btnW / 2f, btnTop, cx + btnW / 2f, btnTop + btnH);
        canvas.drawRoundRect(btnRect, btnH / 2f, btnH / 2f, btnPaint);
        canvas.drawText(sensitivityLabel, cx, btnTop + btnH * 0.68f, subTextPaint);

        // 7. Animación continua fluida (60 FPS)
        float speedMultiplier = 1f + (chargeLevel * 7f);
        outerAngle = (outerAngle + 0.6f * speedMultiplier) % 360f;
        innerAngle = (innerAngle + 1.1f * speedMultiplier) % 360f;

        if (chargeLevel > 0f) {
            chargeLevel = Math.max(0f, chargeLevel - 0.022f);
            if (chargeLevel == 0f) {
                isCharging = false;
            }
        }

        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            // Si toca el botón inferior de sensibilidad
            if (btnRect.contains(x, y) || y > getHeight() * 0.75f) {
                if (listener != null) {
                    listener.onToggleSensitivity();
                }
                return true;
            }

            // Si toca cualquier parte del Reactor Arc -> Dispara el sonido manualmente
            if (listener != null) {
                listener.onManualTrigger();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
