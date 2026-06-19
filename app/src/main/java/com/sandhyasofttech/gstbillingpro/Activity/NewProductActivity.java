package com.sandhyasofttech.gstbillingpro.Activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sandhyasofttech.gstbillingpro.Adapter.UnitAdapter;
import com.sandhyasofttech.gstbillingpro.Model.Product;
import com.sandhyasofttech.gstbillingpro.R;

import java.lang.reflect.Type;
import java.util.*;

public class NewProductActivity extends AppCompatActivity {

    private TextInputEditText etProductName, etHSNCode, etPrice,
            etGSTRate, etStockQuantity;
    private TextInputLayout tilProductName, tilHSNCode,
            tilPrice, tilGSTRate, tilStockQuantity, tilUnit;

    private MaterialButton btnSaveProduct, btnAddYours;
    private LinearLayout llCustomFieldsContainer;

    private String userMobile;
    private DatabaseReference productsRef;
    private AutoCompleteTextView  etUnit;
    private List<String> customFields;
    private Map<String, TextInputEditText> customFieldEditTexts;
    private boolean keepDefaultFields;
    private String primaryField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_product);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etProductName = findViewById(R.id.etProductName);
        etHSNCode = findViewById(R.id.etHSNCode);
        etPrice = findViewById(R.id.etPrice);
        etGSTRate = findViewById(R.id.etGSTRate);
        etStockQuantity = findViewById(R.id.etStockQuantity);
        etUnit = findViewById(R.id.etUnit);

        tilProductName = findViewById(R.id.tilProductName);
        tilHSNCode = findViewById(R.id.tilHSNCode);
        tilPrice = findViewById(R.id.tilPrice);
        tilGSTRate = findViewById(R.id.tilGSTRate);
        tilStockQuantity = findViewById(R.id.tilStockQuantity);
        tilUnit = findViewById(R.id.tilUnit);

        btnSaveProduct = findViewById(R.id.btnSaveProduct);
        btnAddYours = findViewById(R.id.customfields);
        llCustomFieldsContainer = findViewById(R.id.llCustomFieldsContainer);

        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);
        keepDefaultFields = prefs.getBoolean("KEEP_DEFAULT_FIELDS", true);
        primaryField = prefs.getString("PRIMARY_FIELD", null);

        productsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile)
                .child("products");

        etUnit.setOnClickListener(v -> showUnitBottomSheet());

        loadCustomFields(prefs);
        updateDefaultFieldsVisibility();
        addCustomFieldViews();

        btnSaveProduct.setOnClickListener(v -> saveProduct());

        btnAddYours.setOnClickListener(v ->
                startActivity(new Intent(this, CustomFieldsActivity.class)));
    }

    private void loadCustomFields(SharedPreferences prefs) {
        Gson gson = new Gson();
        String json = prefs.getString("CUSTOM_FIELDS", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            customFields = gson.fromJson(json, type);
        } else {
            customFields = new ArrayList<>();
        }
    }

    private void updateDefaultFieldsVisibility() {
        if (!keepDefaultFields) {
            tilProductName.setVisibility(View.GONE);
            tilHSNCode.setVisibility(View.GONE);
            tilPrice.setVisibility(View.GONE);
            tilGSTRate.setVisibility(View.GONE);
            tilStockQuantity.setVisibility(View.GONE);
        }
    }

    private List<String> getAllUnits() {
        return Arrays.asList(
                "Piece (Pc)", "Kilogram (Kg)", "Gram (g)", "Liter (L)",
                "Meter (m)", "Box", "Bag", "Packet", "Dozen",
                "Set", "Pair", "Roll", "Bottle", "Can"
        );
    }

    private void showUnitBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_unit, null);
        dialog.setContentView(view);

        RecyclerView rv = view.findViewById(R.id.rvUnits);
        TextInputEditText etSearch = view.findViewById(R.id.etSearchUnit);

        List<String> allUnits = getAllUnits();
        List<String> filtered = new ArrayList<>(allUnits);

        UnitAdapter adapter = new UnitAdapter(filtered, unit -> {
            etUnit.setText(unit);
            dialog.dismiss();
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
            @Override
            public void onTextChanged(CharSequence s,int a,int b,int c){
                filtered.clear();
                for (String u : allUnits) {
                    if (u.toLowerCase().contains(s.toString().toLowerCase())) {
                        filtered.add(u);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        dialog.show();
    }

    private void addCustomFieldViews() {
        customFieldEditTexts = new HashMap<>();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String field : customFields) {
            TextInputLayout til = (TextInputLayout)
                    inflater.inflate(R.layout.custom_field_input, llCustomFieldsContainer, false);
            TextInputEditText et = til.findViewById(R.id.etCustomField);
            til.setHint(field);
            llCustomFieldsContainer.addView(til);
            customFieldEditTexts.put(field, et);
        }
    }

    private void saveProduct() {

        String name = etProductName.getText().toString().trim();
        String hsn = etHSNCode.getText().toString().trim();      // optional
        String priceStr = etPrice.getText().toString().trim();
        String gstStr = etGSTRate.getText().toString().trim();   // optional
        String stockStr = etStockQuantity.getText().toString().trim();
        String unit = etUnit.getText().toString().trim();

        // ✅ Required validations
        if (TextUtils.isEmpty(name)) { tilProductName.setError("Required"); return; }
        tilProductName.setError(null);

        if (TextUtils.isEmpty(priceStr)) { tilPrice.setError("Required"); return; }
        tilPrice.setError(null);

        if (TextUtils.isEmpty(stockStr)) { tilStockQuantity.setError("Required"); return; }
        tilStockQuantity.setError(null);

        if (TextUtils.isEmpty(unit)) { tilUnit.setError("Select unit"); return; }
        tilUnit.setError(null);

        // ❌ REMOVE these lines (HSN + GST not compulsory)
        // if (TextUtils.isEmpty(gstStr)) { tilGSTRate.setError("Required"); return; }
        // if (TextUtils.isEmpty(hsn)) { tilHSNCode.setError("Required"); return; }

        // ✅ Optional: clear errors
        tilGSTRate.setError(null);
        tilHSNCode.setError(null);

        double price = Double.parseDouble(priceStr);

        // ✅ GST optional: default 0 if empty
        double gst = 0;
        if (!TextUtils.isEmpty(gstStr)) {
            try {
                gst = Double.parseDouble(gstStr);
            } catch (NumberFormatException e) {
                tilGSTRate.setError("Invalid GST");
                return;
            }
        }

        int stock = Integer.parseInt(stockStr);
        String productId = UUID.randomUUID().toString();

        Product product = new Product(
                productId,
                name,
                hsn,     // can be empty string
                price,
                gst,     // 0 if user left blank
                stock,
                unit
        );

        productsRef.child(productId).setValue(product)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Product saved successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save product", Toast.LENGTH_SHORT).show());
    }
}
