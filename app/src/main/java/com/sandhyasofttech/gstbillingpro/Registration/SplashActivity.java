package com.sandhyasofttech.gstbillingpro.Registration;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.MainActivity;
import com.sandhyasofttech.gstbillingpro.R;

public class SplashActivity extends AppCompatActivity {

    private static final long POST_ANIMATION_DELAY = 1500;

    private Handler dotHandler;
    private Runnable dotPulseRunnable;
    private View dot1, dot2, dot3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        final View logoGlow = findViewById(R.id.logoGlow);
        final View logoCard = findViewById(R.id.logoCard);
        final ImageView logo = findViewById(R.id.companyLogo);
        final TextView appName = findViewById(R.id.appName);
        final TextView tagline = findViewById(R.id.tagline);
        final TextView companyName = findViewById(R.id.companyName);
        final LinearLayout loadingDots = findViewById(R.id.loadingDots);
        final LinearLayout footerContainer = findViewById(R.id.footerContainer);

        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);

        // Logo card starts small + faded, glow ring starts fully scaled down
        logoCard.setScaleX(0.6f);
        logoCard.setScaleY(0.6f);
        logoCard.setAlpha(0f);
        logoGlow.setScaleX(0.4f);
        logoGlow.setScaleY(0.4f);
        logoGlow.setAlpha(0f);

        // Glow ring expands outward first, slightly behind the logo card
        ObjectAnimator glowScaleX = ObjectAnimator.ofFloat(logoGlow, "scaleX", 0.4f, 1f);
        ObjectAnimator glowScaleY = ObjectAnimator.ofFloat(logoGlow, "scaleY", 0.4f, 1f);
        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(logoGlow, "alpha", 0f, 1f);

        // Logo card pops in with a slight overshoot for a lively, modern feel
        ObjectAnimator cardScaleX = ObjectAnimator.ofFloat(logoCard, "scaleX", 0.6f, 1f);
        ObjectAnimator cardScaleY = ObjectAnimator.ofFloat(logoCard, "scaleY", 0.6f, 1f);
        ObjectAnimator cardAlpha = ObjectAnimator.ofFloat(logoCard, "alpha", 0f, 1f);

        AnimatorSet glowSet = new AnimatorSet();
        glowSet.playTogether(glowScaleX, glowScaleY, glowAlpha);
        glowSet.setDuration(650);
        glowSet.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet cardSet = new AnimatorSet();
        cardSet.playTogether(cardScaleX, cardScaleY, cardAlpha);
        cardSet.setDuration(700);
        cardSet.setInterpolator(new OvershootInterpolator(1.4f));

        AnimatorSet logoEntrance = new AnimatorSet();
        logoEntrance.playTogether(glowSet, cardSet);

        // Once the logo settles, animate the text + dots + footer in sequence
        logoEntrance.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animateTextView(appName, 0);
                animateTextView(tagline, 250);
                animateFadeIn(loadingDots, 500);
                startDotPulseLoop();
                animateFadeIn(footerContainer, 650);

                new Handler(Looper.getMainLooper()).postDelayed(
                        SplashActivity.this::navigateNext, POST_ANIMATION_DELAY);
            }
        });

        logoEntrance.start();
    }

    private void animateTextView(TextView textView, long delay) {
        textView.setAlpha(0f);
        textView.setTranslationY(30f);
        textView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void animateFadeIn(View view, long delay) {
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setStartDelay(delay)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /**
     * Pulses the three loading dots in a staggered wave, giving the user a
     * sense of progress while the splash holds on screen. Stops automatically
     * when the activity navigates away (handler is removed in onDestroy).
     */
    private void startDotPulseLoop() {
        dotHandler = new Handler(Looper.getMainLooper());
        dotPulseRunnable = new Runnable() {
            @Override
            public void run() {
                pulseDot(dot1, 0);
                pulseDot(dot2, 150);
                pulseDot(dot3, 300);
                dotHandler.postDelayed(this, 900);
            }
        };
        dotHandler.post(dotPulseRunnable);
    }

    private void pulseDot(View dot, long delay) {
        dot.animate()
                .alpha(1f)
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setStartDelay(delay)
                .setDuration(300)
                .withEndAction(() -> dot.animate()
                        .alpha(0.4f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start())
                .start();
    }

    private void navigateNext() {

        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("IS_LOGGED_IN", false);
        String mobile = prefs.getString("USER_MOBILE", null);

        if (!isLoggedIn || mobile == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 🔥 VERIFY STATUS FROM FIREBASE AGAIN
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(mobile)
                .child("info")
                .child("status")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        Boolean status = snapshot.getValue(Boolean.class);

                        if (status != null && status) {
                            // ✅ ALLOW
                            startActivity(new Intent(SplashActivity.this, MainActivity.class));
                        } else {
                            // ❌ BLOCK
                            prefs.edit().clear().apply();
                            Toast.makeText(SplashActivity.this,
                                    "Your account is inactive. Please contact admin.",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                        }
                        finish();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                        finish();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dotHandler != null && dotPulseRunnable != null) {
            dotHandler.removeCallbacks(dotPulseRunnable);
        }
    }
}