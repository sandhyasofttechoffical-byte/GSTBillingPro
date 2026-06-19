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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.Adapter.CustomerAdapter;
import com.sandhyasofttech.gstbillingpro.R;
import com.sandhyasofttech.gstbillingpro.custmore.AddCustomerActivity;
import com.sandhyasofttech.gstbillingpro.custmore.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerSelectionActivity extends AppCompatActivity {

    private RecyclerView rvCustomers;
    private EditText etSearch;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FloatingActionButton fabAddCustomer;

    private CustomerAdapter customerAdapter;
    private List<Customer> customerList = new ArrayList<>();
    private List<Customer> filteredList = new ArrayList<>();

    private DatabaseReference customersRef;
    private String userMobile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_selection);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        // Initialize views
        rvCustomers = findViewById(R.id.rvCustomers);
        etSearch = findViewById(R.id.etSearch);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        fabAddCustomer = findViewById(R.id.fabAddCustomer);

        // Get user mobile
        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);

        if (userMobile == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        customersRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile)
                .child("customers");

        // Setup RecyclerView
        setupRecyclerView();

        // Setup search functionality
        setupSearch();

        // Load customers
        loadCustomers();

        // Add customer button
        fabAddCustomer.setOnClickListener(v -> {
            Intent intent = new Intent(CustomerSelectionActivity.this, AddCustomerActivity.class);
            startActivity(intent);
        });
      // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        customerAdapter = new CustomerAdapter(filteredList, customer -> {
            // Customer selected, move to product selection
            Intent intent = new Intent(CustomerSelectionActivity.this, ProductSelectionActivity.class);
            intent.putExtra("CUSTOMER_NAME", customer.name);
            intent.putExtra("CUSTOMER_PHONE", customer.phone);
            intent.putExtra("CUSTOMER_ADDRESS", customer.address);
            startActivity(intent);
        });

        rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        rvCustomers.setAdapter(customerAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCustomers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterCustomers(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(customerList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (Customer customer : customerList) {
                if (customer.name.toLowerCase().contains(lowerQuery) ||
                    customer.phone.contains(query)) {
                    filteredList.add(customer);
                }
            }
        }
        customerAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void loadCustomers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        customersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                customerList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Customer customer = ds.getValue(Customer.class);
                    if (customer != null) {
                        customerList.add(customer);
                    }
                }
                filteredList.clear();
                filteredList.addAll(customerList);
                customerAdapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                updateEmptyView();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(CustomerSelectionActivity.this, 
                    "Failed to load customers", Toast.LENGTH_SHORT).show();
                updateEmptyView();
            }
        });
    }

    private void updateEmptyView() {
        if (filteredList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(customerList.isEmpty() ? 
                "No customers found. Add your first customer!" : 
                "No results found for your search.");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}