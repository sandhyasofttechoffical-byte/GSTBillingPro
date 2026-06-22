package com.sandhyasofttech.gstbillingpro.Activity;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.sandhyasofttech.gstbillingpro.Model.Product;
import com.sandhyasofttech.gstbillingpro.R;

import java.util.HashMap;
import java.util.Map;

public class ProductDetailsActivity extends AppCompatActivity {

    private Product product;
    private DatabaseReference productRef;
    private TextView tvProductInitial;
    // Add this field at the top of the class
    private TextView tvProductName;
    private TextView tvProductId;
    private TextView tvCustomerPhone;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        tvProductInitial = findViewById(R.id.tvProductInitial);
        tvProductId = findViewById(R.id.tvProductId);
        tvCustomerPhone = findViewById(R.id.tvCustomerPhone);
// In onCreate(), after tvCustomerPhone = findViewById(...)
        tvProductName = findViewById(R.id.tvProductName);
        product = (Product) getIntent().getSerializableExtra("product");

// Add null check
        if (product == null) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String userMobile = getSharedPreferences("APP_PREFS", MODE_PRIVATE).getString("USER_MOBILE", null);
        productRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile).child("products").child(product.getProductId());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(product.getName());
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        displayProductDetails();

        // Staggered entrance animation for the section cards + edit button
        playEntranceAnimations();
    }

    private void displayProductDetails() {
        CardView cvCustomer = findViewById(R.id.cvCustomer);
        TextView tvCustomerName = findViewById(R.id.tvCustomerName);
        LinearLayout llDefaultFieldsContainer = findViewById(R.id.llDefaultFieldsContainer);
        LinearLayout llCustomFieldsContainer = findViewById(R.id.llCustomFieldsContainer);
        CardView cvDefaultFields = findViewById(R.id.cvDefaultFields);
        CardView cvCustomFields = findViewById(R.id.cvCustomFields);

        // ── Header card ──────────────────────────────────────────────
        if (product.getName() != null && !product.getName().isEmpty()) {
            tvProductInitial.setText(String.valueOf(product.getName().charAt(0)).toUpperCase());
            tvProductName.setText(product.getName());   // ← was never set before
        }

        if (product.getProductId() != null) {
            String shortId = product.getProductId();
            if (shortId.length() > 8) shortId = shortId.substring(0, 8);
            tvProductId.setText("ID: " + shortId);
        }
        // ─────────────────────────────────────────────────────────────

        // Customer card
        if (!TextUtils.isEmpty(product.getCustomerName())) {
            cvCustomer.setVisibility(View.VISIBLE);
            tvCustomerName.setText(product.getCustomerName());
            tvCustomerPhone.setVisibility(View.GONE);
        } else {
            cvCustomer.setVisibility(View.GONE);
        }

        // Clear containers
        llDefaultFieldsContainer.removeAllViews();
        llCustomFieldsContainer.removeAllViews();

        // ── Default fields — show if name exists, not just price > 0 ──
        boolean hasDefaultData = product.getName() != null && !product.getName().isEmpty();
        cvDefaultFields.setVisibility(hasDefaultData ? View.VISIBLE : View.GONE);

        if (hasDefaultData) {
            // Re-add the section title header since removeAllViews() wiped it
            // Add rows below the static header that stays in XML
            if (!TextUtils.isEmpty(product.getHsnCode())) {
                addDetailRow(llDefaultFieldsContainer, "HSN/SAC Code", product.getHsnCode());
            }
            if (product.getPrice() > 0) {
                addDetailRow(llDefaultFieldsContainer, "Price",
                        String.format("₹%.2f", product.getPrice()));
                addDetailRow(llDefaultFieldsContainer, "GST Rate",
                        String.format("%.1f%%", product.getGstRate()));
            }
            String quantityText = product.getStockQuantity() + " " +
                    (product.getUnit() != null && !product.getUnit().isEmpty()
                            ? product.getUnit() : "pcs");
            addDetailRow(llDefaultFieldsContainer, "Stock Quantity", quantityText);
        }
        // ─────────────────────────────────────────────────────────────

        // Custom fields
        if (product.getCustomFields() != null && !product.getCustomFields().isEmpty()) {
            cvCustomFields.setVisibility(View.VISIBLE);
            for (Map.Entry<String, String> entry : product.getCustomFields().entrySet()) {
                addDetailRow(llCustomFieldsContainer, entry.getKey(),
                        entry.getValue() != null ? entry.getValue() : "-");
            }
        } else {
            cvCustomFields.setVisibility(View.GONE);
        }
    }    private void addDetailRow(LinearLayout container, String label, String value) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_detail_row, container, false);
        TextView tvLabel = rowView.findViewById(R.id.tvLabel);
        TextView tvValue = rowView.findViewById(R.id.tvValue);

        tvLabel.setText(label);
        tvValue.setText(value);

        // Add margin between rows
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 8; // 8dp margin at bottom
        rowView.setLayoutParams(params);

        container.addView(rowView);
    }

    /**
     * Plays a staggered fade + slide-up entrance animation across the four
     * section cards, followed by a pop-in animation on the Edit button.
     * Purely cosmetic — does not touch any existing view IDs or logic.
     */
    private void playEntranceAnimations() {
        View cardHeader = findViewById(R.id.cvProductHeader);
        View cardCustomer = findViewById(R.id.cvCustomer);
        View cardDefaultFields = findViewById(R.id.cvDefaultFields);
        View cardCustomFields = findViewById(R.id.cvCustomFields);
        View editButton = findViewById(R.id.btnEditProduct);

        if (cardHeader != null) {
            cardHeader.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_1));
        }
        if (cardCustomer != null && cardCustomer.getVisibility() == View.VISIBLE) {
            cardCustomer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_2));
        }
        if (cardDefaultFields != null && cardDefaultFields.getVisibility() == View.VISIBLE) {
            cardDefaultFields.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_3));
        }
        if (cardCustomFields != null && cardCustomFields.getVisibility() == View.VISIBLE) {
            cardCustomFields.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_pd_card_4));
        }
        if (editButton != null) {
            editButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_button_pop));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_product_details, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            showEditDialog();
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            showDeleteConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage("Are you sure you want to delete this product?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    productRef.removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(ProductDetailsActivity.this, "Product deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(ProductDetailsActivity.this, "Failed to delete product", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_product, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        LinearLayout llDefaultFieldsContainer = dialogView.findViewById(R.id.llDefaultFieldsContainer);
        LinearLayout llCustomFieldsContainer = dialogView.findViewById(R.id.llCustomFieldsContainer);

        boolean hasDefaultFields = product.getPrice() > 0;
        llDefaultFieldsContainer.setVisibility(hasDefaultFields ? View.VISIBLE : View.GONE);

        EditText etName = dialogView.findViewById(R.id.etProductName);
        EditText etHsn = dialogView.findViewById(R.id.etHsn);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etGst = dialogView.findViewById(R.id.etGst);
        EditText etQuantity = dialogView.findViewById(R.id.etQuantity);
        EditText etUnit = dialogView.findViewById(R.id.etProductUnit);

// Add null check for unit
        if (product.getUnit() == null) {
            product.setUnit("");
        }
        if(hasDefaultFields) {
            etName.setText(product.getName());
            etHsn.setText(product.getHsnCode());
            etPrice.setText(String.valueOf(product.getPrice()));
            etGst.setText(String.valueOf(product.getGstRate()));
            etQuantity.setText(String.valueOf(product.getStockQuantity()));
            etUnit.setText(product.getUnit());
        }

        Map<String, EditText> customFieldEditTexts = new HashMap<>();
        if (product.getCustomFields() != null) {
            for (Map.Entry<String, String> entry : product.getCustomFields().entrySet()) {
                EditText customField = new EditText(this);
                customField.setHint(entry.getKey());
                customField.setText(entry.getValue());
                llCustomFieldsContainer.addView(customField);
                customFieldEditTexts.put(entry.getKey(), customField);
            }
        }

        dialogView.findViewById(R.id.btnUpdate).setOnClickListener(v -> {
            if (hasDefaultFields) {
                product.setName(etName.getText().toString().trim());
                product.setHsnCode(etHsn.getText().toString().trim());
                product.setPrice(Double.parseDouble(etPrice.getText().toString()));
                product.setGstRate(Double.parseDouble(etGst.getText().toString()));
                product.setStockQuantity(Integer.parseInt(etQuantity.getText().toString()));
                product.setUnit(etUnit.getText().toString().trim());
            }

            if (product.getCustomFields() != null) {
                Map<String, String> updatedCustomFields = new HashMap<>();
                for (Map.Entry<String, EditText> entry : customFieldEditTexts.entrySet()) {
                    updatedCustomFields.put(entry.getKey(), entry.getValue().getText().toString().trim());
                }
                product.setCustomFields(updatedCustomFields);
            }

            productRef.setValue(product).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Product updated", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    recreate(); // To refresh the details view
                } else {
                    Toast.makeText(this, "Failed to update product", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }
}