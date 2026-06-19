package com.sandhyasofttech.gstbillingpro.custmore;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.sandhyasofttech.gstbillingpro.R;

public class AddCustomerActivity extends AppCompatActivity {

    public static final String EXTRA_IS_EDIT = "isEdit";
    public static final String EXTRA_CUSTOMER_ID = "customerId"; // phone used as id
    public static final String EXTRA_CUSTOMER_NAME = "customerName";
    public static final String EXTRA_CUSTOMER_PHONE = "customerPhone";
    public static final String EXTRA_CUSTOMER_EMAIL = "customerEmail";
    public static final String EXTRA_CUSTOMER_GSTIN = "customerGstin";
    public static final String EXTRA_CUSTOMER_ADDRESS = "customerAddress";

    private EditText etName, etPhone, etEmail, etGstin, etAddress;
    private Button btnSave;

    private boolean isEdit = false;
    private String originalPhone = null;
    private DatabaseReference customersRef;
    private String userMobile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_customer);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        etName = findViewById(R.id.etCustomerName);
        etPhone = findViewById(R.id.etCustomerPhone);
        etEmail = findViewById(R.id.etCustomerEmail);
        etGstin = findViewById(R.id.etCustomerGstin);
        etAddress = findViewById(R.id.etCustomerAddress);
        btnSave = findViewById(R.id.btnSaveCustomer);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);

        if (userMobile == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        customersRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile)
                .child("customers");

        isEdit = getIntent().getBooleanExtra(EXTRA_IS_EDIT, false);

        if (isEdit) {
            originalPhone = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
            etName.setText(getIntent().getStringExtra(EXTRA_CUSTOMER_NAME));
            etPhone.setText(originalPhone);
            etEmail.setText(getIntent().getStringExtra(EXTRA_CUSTOMER_EMAIL));
            etGstin.setText(getIntent().getStringExtra(EXTRA_CUSTOMER_GSTIN));
            etAddress.setText(getIntent().getStringExtra(EXTRA_CUSTOMER_ADDRESS));
            btnSave.setText("Update Customer");
            etPhone.setEnabled(false);  // phone cannot be changed because it's key
        } else {
            btnSave.setText("Add Customer");
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String gstin = etGstin.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone) || TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Name, Phone and Email are required", Toast.LENGTH_SHORT).show();
                return;
            }

            Customer customer = new Customer(phone, name, phone, email, gstin, address);

            if (isEdit) {
                customersRef.child(originalPhone).setValue(customer)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Customer updated", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                // Check duplicate phone number
                customersRef.child(phone).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Toast.makeText(this, "Customer with this mobile already exists!", Toast.LENGTH_SHORT).show();
                    } else {
                        customersRef.child(phone).setValue(customer)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Customer added", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Add failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).addOnFailureListener(e -> Toast.makeText(this, "Error checking customer: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
}
