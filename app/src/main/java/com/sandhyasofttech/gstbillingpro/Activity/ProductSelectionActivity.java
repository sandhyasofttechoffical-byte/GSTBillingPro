package com.sandhyasofttech.gstbillingpro.Activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.Adapter.ProductSelectionAdapter;
import com.sandhyasofttech.gstbillingpro.Model.Product;
import com.sandhyasofttech.gstbillingpro.Model.CartItem;
import com.sandhyasofttech.gstbillingpro.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductSelectionActivity extends AppCompatActivity {

    private RecyclerView rvProducts;
    private EditText etSearch;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvCustomerName, tvSelectedCount, tvSelectedTotal;
    private MaterialButton btnProceed;
    private View cvSummary;

    private ProductSelectionAdapter productAdapter;
    private List<Product> productList = new ArrayList<>();
    private List<Product> filteredList = new ArrayList<>();

    private DatabaseReference productsRef;
    private String userMobile, customerName, customerPhone, customerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_selection);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        // Get customer details
        customerName = getIntent().getStringExtra("CUSTOMER_NAME");
        customerPhone = getIntent().getStringExtra("CUSTOMER_PHONE");
        customerAddress = getIntent().getStringExtra("CUSTOMER_ADDRESS");

        // Initialize views
        initializeViews();

        // Get user mobile
        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);

        if (userMobile == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        productsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile)
                .child("products");

        // Setup
        setupProductRecyclerView();
        setupSearch();
        loadProducts();

        // Buttons
        setupButtons();

        // Initial state
        updateSummary();
    }

    private void initializeViews() {
        rvProducts = findViewById(R.id.rvProducts);
        etSearch = findViewById(R.id.etSearch);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvSelectedCount = findViewById(R.id.tvCartCount);
        tvSelectedTotal = findViewById(R.id.tvCartTotal);
        btnProceed = findViewById(R.id.btnProceed);
        cvSummary = findViewById(R.id.cvCart);

        tvCustomerName.setText("Customer: " + customerName);
    }

    private void setupButtons() {
        btnProceed.setOnClickListener(v -> proceedToPayment());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupProductRecyclerView() {
        productAdapter = new ProductSelectionAdapter(filteredList, this::updateSummary);
        rvProducts.setLayoutManager(new LinearLayoutManager(this));
        rvProducts.setAdapter(productAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterProducts(String query) {
        filteredList.clear();

        if (query.isEmpty()) {
            filteredList.addAll(productList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (Product product : productList) {
                if (product.getName() != null &&
                        product.getName().toLowerCase().contains(lowerQuery)) {
                    filteredList.add(product);
                }
            }
        }

        productAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void loadProducts() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        productsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productList.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Product product = ds.getValue(Product.class);
                    if (product != null && product.getEffectiveQuantity() > 0) {
                        productList.add(product);
                    }
                }

                filteredList.clear();
                filteredList.addAll(productList);
                productAdapter.notifyDataSetChanged();

                progressBar.setVisibility(View.GONE);
                updateEmptyView();
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ProductSelectionActivity.this,
                        "Failed to load products: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                updateEmptyView();
            }
        });
    }

    private void updateSummary() {
        Map<String, ProductSelectionAdapter.SelectedProduct> selectedProducts =
                productAdapter.getSelectedProducts();

        int selectedCount = selectedProducts.size();
        double total = 0;

        // Calculate total
        for (ProductSelectionAdapter.SelectedProduct selected : selectedProducts.values()) {
            total += selected.quantity * selected.price;
        }

        // Update UI
        tvSelectedCount.setText(selectedCount + " selected");
        tvSelectedTotal.setText(String.format(Locale.getDefault(), "Total: ₹%.2f", total));

        // Show/hide summary
        cvSummary.setVisibility(selectedCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void proceedToPayment() {
        Map<String, ProductSelectionAdapter.SelectedProduct> selectedProducts =
                productAdapter.getSelectedProducts();

        if (selectedProducts.isEmpty()) {
            Toast.makeText(this, "Please select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create cart items from selected products
        ArrayList<CartItem> cartItems = new ArrayList<>();
        double cartTotal = 0;

        for (Product product : productList) {
            ProductSelectionAdapter.SelectedProduct selected =
                    selectedProducts.get(product.getProductId());

            if (selected != null) {
                CartItem item = new CartItem(
                        product.getProductId(),
                        product.getName(),
                        selected.quantity,
                        selected.price,
                        product.getGstRate(),
                        product.getEffectiveQuantity(),
                        product.getUnit()
                );
                cartItems.add(item);
                cartTotal += item.getTaxableValue();
            }
        }

        // Validate we have items
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "No valid items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Proceed to payment
        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra("CUSTOMER_NAME", customerName);
        intent.putExtra("CUSTOMER_PHONE", customerPhone);
        intent.putExtra("CUSTOMER_ADDRESS", customerAddress);
        intent.putExtra("CART_ITEMS", cartItems);
        intent.putExtra("CART_TOTAL", cartTotal);
        startActivity(intent);
        finish();
    }

    private void updateEmptyView() {
        if (filteredList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(productList.isEmpty() ?
                    "No products available" :
                    "No results found");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}