package com.example.reminder.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.example.reminder.utils.ThemeManager;

/**
 * Custom compact horizontal pill-shaped sliding theme toggle.
 * Light mode: White pill, thin outline, white circular thumb on LEFT, ☀️ on RIGHT.
 * Dark mode: Dark graphite pill (#252C24), white circular thumb on RIGHT, 🌙 on LEFT.
 */
public class ThemeToggleView extends View implements ThemeManager.ThemeChangeListener {

    private static final String EMOJI_SUN = "☀️";
    private static final String EMOJI_MOON = "🌙";

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint emojiPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final RectF pillBounds = new RectF();
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    private final Interpolator interpolator = new FastOutSlowInInterpolator();

    private float progress = 0f; // 0 = Light, 1 = Dark
    private ValueAnimator animator;
    private long lastClickTime = 0;

    // Palette Colors
    private final int lightBgColor = 0xFFFFFFFF;
    private final int darkBgColor = 0xFF1C1E1C;
    private final int lightBorderColor = 0xFF191D19;
    private final int darkBorderColor = 0x00000000;
    private final int thumbColor = 0xFFFFFFFF;
    private final int thumbShadowColor = 0x22000000;

    private float density;

    public ThemeToggleView(Context context) {
        super(context);
        init();
    }

    public ThemeToggleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeToggleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.2f * density);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(thumbColor);

        thumbShadowPaint.setStyle(Paint.Style.FILL);
        thumbShadowPaint.setColor(thumbShadowColor);

        emojiPaint.setTextAlign(Paint.Align.CENTER);
        emojiPaint.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);

        boolean isDark = ThemeManager.isDarkActive(getContext());
        progress = isDark ? 1f : 0f;

        setClickable(true);
        setFocusable(true);
        setContentDescription(isDark ? "Switch to Light Mode" : "Switch to Dark Mode");

        setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 350) return;
            lastClickTime = now;

            animateToState(!ThemeManager.isDarkActive(getContext()), true);
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ThemeManager.registerListener(this);
        syncState(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.unregisterListener(this);
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    @Override
    public void onThemeChanged(int mode, boolean isDarkActive) {
        post(() -> animateToState(isDarkActive, false));
    }

    public void syncState(boolean animate) {
        boolean isDark = ThemeManager.isDarkActive(getContext());
        animateToState(isDark, false);
    }

    private void animateToState(boolean targetDark, boolean triggerThemeChange) {
        float target = targetDark ? 1f : 0f;
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(260);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (triggerThemeChange) {
                    ThemeManager.setThemeMode(getContext(), targetDark ? ThemeManager.MODE_DARK : ThemeManager.MODE_LIGHT);
                }
            }
        });
        animator.start();
        setContentDescription(targetDark ? "Switch to Light Mode" : "Switch to Dark Mode");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultWidth = (int) (64 * density);
        int defaultHeight = (int) (32 * density);

        int width = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float strokeWidth = borderPaint.getStrokeWidth();
        float halfStroke = strokeWidth / 2f;
        pillBounds.set(halfStroke, halfStroke, width - halfStroke, height - halfStroke);
        float radius = (height - strokeWidth) / 2f;

        // 1. Draw Pill Background
        int currentBg = (int) argbEvaluator.evaluate(progress, lightBgColor, darkBgColor);
        bgPaint.setColor(currentBg);
        canvas.drawRoundRect(pillBounds, radius, radius, bgPaint);

        // 2. Draw Pill Border (Light mode has thin border, dark mode fades it out)
        int currentBorder = (int) argbEvaluator.evaluate(progress, lightBorderColor, darkBorderColor);
        if (Color.alpha(currentBorder) > 0) {
            borderPaint.setColor(currentBorder);
            canvas.drawRoundRect(pillBounds, radius, radius, borderPaint);
        }

        // 3. Thumb & Emoji Calculations
        float padding = 3.5f * density;
        float thumbRadius = (height / 2f) - padding;
        float minX = padding + thumbRadius;
        float maxX = width - padding - thumbRadius;
        float thumbX = minX + (maxX - minX) * progress;
        float centerY = height / 2f;

        // 4. Draw Emojis with cross-fade & scale
        float emojiY = centerY - ((emojiPaint.descent() + emojiPaint.ascent()) / 2f);

        // Sun Emoji (on the RIGHT side at maxX)
        float sunAlpha = Math.max(0f, Math.min(1f, 1f - (progress * 1.5f)));
        if (sunAlpha > 0.05f) {
            canvas.save();
            float sunScale = 0.6f + (0.4f * sunAlpha);
            canvas.scale(sunScale, sunScale, maxX, centerY);
            emojiPaint.setAlpha((int) (sunAlpha * 255));
            canvas.drawText(EMOJI_SUN, maxX, emojiY, emojiPaint);
            canvas.restore();
        }

        // Moon Emoji (on the LEFT side at minX)
        float moonAlpha = Math.max(0f, Math.min(1f, (progress - 0.33f) * 1.5f));
        if (moonAlpha > 0.05f) {
            canvas.save();
            float moonScale = 0.6f + (0.4f * moonAlpha);
            canvas.scale(moonScale, moonScale, minX, centerY);
            emojiPaint.setAlpha((int) (moonAlpha * 255));
            canvas.drawText(EMOJI_MOON, minX, emojiY, emojiPaint);
            canvas.restore();
        }

        // 5. Draw Circular Thumb
        // Draw subtle shadow
        canvas.drawCircle(thumbX, centerY + (0.8f * density), thumbRadius, thumbShadowPaint);
        // Draw thumb circle
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint);
    }
}
