package com.sandhyasofttech.gstbillingpro.Registration;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.sandhyasofttech.gstbillingpro.R;

public class RegistrationActivity extends AppCompatActivity {

    private EditText businessName, businessType, ownerName, gstin, mobile, email, address, pin;
    private MaterialButton registerBtn;
    private DatabaseReference databaseUsers;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Registration");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        businessName = findViewById(R.id.businessName);
        businessType = findViewById(R.id.businessType);
        ownerName    = findViewById(R.id.ownerName);
        gstin        = findViewById(R.id.gstin);
        mobile       = findViewById(R.id.mobile);
        email        = findViewById(R.id.email);
        address      = findViewById(R.id.address);
        pin          = findViewById(R.id.pin);
        registerBtn  = findViewById(R.id.registerBtn);

        databaseUsers = FirebaseDatabase.getInstance().getReference("users");

        // Animations only — no click listener here
        playEntranceAnimations();

        // ── Single click listener ──────────────────────────────────
        registerBtn.setOnClickListener(v -> {
            String bName = businessName.getText().toString().trim();
            String bType = businessType.getText().toString().trim();
            String oName = ownerName.getText().toString().trim();
            String gst   = gstin.getText().toString().trim();
            String mob   = mobile.getText().toString().trim();
            String mail  = email.getText().toString().trim();
            String addr  = address.getText().toString().trim();
            String p     = pin.getText().toString().trim();

            // Validation — only mobile and PIN required
            if (mob.isEmpty()) {
                mobile.setError("Mobile number is required");
                mobile.requestFocus();
                return;
            }
            if (mob.length() != 10) {
                mobile.setError("Enter valid 10-digit mobile number");
                mobile.requestFocus();
                return;
            }
            if (p.isEmpty()) {
                pin.setError("PIN is required");
                pin.requestFocus();
                return;
            }
            if (p.length() != 4) {
                pin.setError("Enter 4-digit PIN");
                pin.requestFocus();
                return;
            }

            // Disable button to prevent double-tap during Firebase check
            registerBtn.setEnabled(false);

            // Check if mobile already registered
            databaseUsers.child(mob).child("info").get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            registerBtn.setEnabled(true);
                            mobile.setError("This mobile number is already registered");
                            mobile.requestFocus();
                            Toast.makeText(RegistrationActivity.this,
                                    "Account already exists for this mobile number",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            User user = new User(bName, bType, oName, gst, mob, mail, addr, p, false);
                            databaseUsers.child(mob).child("info").setValue(user)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(RegistrationActivity.this,
                                                "Registration successful", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        registerBtn.setEnabled(true);
                                        Toast.makeText(RegistrationActivity.this,
                                                "Registration failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        registerBtn.setEnabled(true);
                        Toast.makeText(RegistrationActivity.this,
                                "Network error. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    });
        });
        // ──────────────────────────────────────────────────────────
    }

    private void playEntranceAnimations() {
        View cardBusiness = findViewById(R.id.cardBusinessDetails);
        View cardContact  = findViewById(R.id.cardContactDetails);
        View cardSecurity = findViewById(R.id.cardSecurity);

        if (cardBusiness != null)
            cardBusiness.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_1));
        if (cardContact != null)
            cardContact.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_2));
        if (cardSecurity != null)
            cardSecurity.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_3));
        if (registerBtn != null)
            registerBtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_button_pop)); // ← was missing
    }

    public static class User {
        public String businessName, businessType, ownerName, gstin;
        public String mobile, email, address, pin;
        public boolean status;

        public User() { }

        public User(String businessName, String businessType, String ownerName, String gstin,
                    String mobile, String email, String address, String pin, boolean status) {
            this.businessName = businessName;
            this.businessType = businessType;
            this.ownerName    = ownerName;
            this.gstin        = gstin;
            this.mobile       = mobile;
            this.email        = email;
            this.address      = address;
            this.pin          = pin;
            this.status       = status;
        }
    }
}