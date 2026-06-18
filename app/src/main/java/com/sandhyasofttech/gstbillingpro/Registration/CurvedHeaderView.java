package com.sandhyasofttech.gstbillingpro.Registration;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws a blue gradient panel with a smooth concave curve along the bottom
 * edge. Used as the header behind the logo on the Login screen. The curve
 * is redrawn relative to the view's actual width/height, so it scales
 * correctly on any device instead of being locked to a fixed viewport like
 * a static vector drawable would be.
 */
public class CurvedHeaderView extends View {

    private Paint paint;
    private Path path;

    public CurvedHeaderView(Context context) {
        super(context);
        init();
    }

    public CurvedHeaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CurvedHeaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        path = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Gradient diagonally across the panel: dark blue -> primary blue -> light blue
        paint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0xFF0D47A1, 0xFF1565C0, 0xFF42A5F5},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));

        // Flat rectangle for ~78% of height, then a smooth curve dipping
        // down to ~100% height in the center and rising back up at the edges.
        float flatBottom = h * 0.78f;
        path.reset();
        path.moveTo(0, 0);
        path.lineTo(w, 0);
        path.lineTo(w, flatBottom);
        path.cubicTo(
                w * 0.75f, h,
                w * 0.25f, h,
                0, flatBottom
        );
        path.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
    }
}