package com.sandhyasofttech.gstbillingpro.Registration;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.graphics.Color;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.MainActivity;
import com.sandhyasofttech.gstbillingpro.R;

public class LoginActivity extends AppCompatActivity {

    private EditText phoneInput, pinInput;
    private MaterialButton loginBtn;
    private TextView registrationLink;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        phoneInput = findViewById(R.id.phoneInput);
        pinInput = findViewById(R.id.pinInput);
        loginBtn = findViewById(R.id.loginBtn);
        registrationLink = findViewById(R.id.registrationLink);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users");

        // Staggered entrance animation: logo -> title -> form card -> link
        playEntranceAnimations();

        loginBtn.setOnClickListener(v -> {
            String mobile = phoneInput.getText().toString().trim();
            String pin = pinInput.getText().toString().trim();

            if (mobile.isEmpty() || mobile.length() != 10) {
                phoneInput.setError("Enter valid 10-digit mobile number");
                phoneInput.requestFocus();
                return;
            }
            if (pin.isEmpty() || pin.length() != 4) {
                pinInput.setError("Enter 4-digit PIN");
                pinInput.requestFocus();
                return;
            }

            // Read from Firebase under mobile / info / pin
            usersRef.child(mobile).child("info").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String storedPin = snapshot.child("pin").getValue(String.class);
                        Boolean status = snapshot.child("status").getValue(Boolean.class);

                        if (storedPin != null && storedPin.equals(pin)) {

                            if (status != null && status) {
                                // ✅ STATUS = TRUE → LOGIN ALLOWED

                                SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
                                prefs.edit()
                                        .putBoolean("IS_LOGGED_IN", true)
                                        .putString("USER_MOBILE", mobile)
                                        .apply();

                                Toast.makeText(LoginActivity.this,
                                        "Login successful ✔️", Toast.LENGTH_SHORT).show();

                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();

                            } else {
                                // ❌ STATUS = FALSE → FORCE LOGOUT
                                SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
                                prefs.edit().clear().apply();

                                Toast.makeText(LoginActivity.this,
                                        "Your account is not activated yet.\nPlease contact admin.",
                                        Toast.LENGTH_LONG).show();

                            }

                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Incorrect PIN", Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Toast.makeText(LoginActivity.this, "User not found, please register first", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Toast.makeText(LoginActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Link to open registration screen
        registrationLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
        });
    }

    /**
     * Plays a staggered entrance animation: the curved blue header drops
     * down from the top, the brand block (logo + title + subtitle) pops in
     * once the header settles, then the form card rises up into its
     * overlapping position, and finally the registration link fades in.
     * Purely cosmetic — no view IDs or existing logic are touched.
     */
    private void playEntranceAnimations() {
        View curvedHeader = findViewById(R.id.curvedHeader);
        View brandBlock = findViewById(R.id.brandBlock);
        LinearLayout formCard = findViewById(R.id.loginFormCard);
        View link = registrationLink;

        if (curvedHeader != null) {
            curvedHeader.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_header_drop));
        }
        if (brandBlock != null) {
            brandBlock.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_brand_pop));
        }
        if (formCard != null) {
            formCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_form_card));
        }
        if (link != null) {
            link.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_link_fade));
        }
    }
}