package com.sandhyasofttech.gstbillingpro.soldproduct;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.*;
import com.sandhyasofttech.gstbillingpro.R;
import com.sandhyasofttech.gstbillingpro.invoice.Invoice;
import com.sandhyasofttech.gstbillingpro.invoice.InvoiceItem;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SoldProductsActivity extends AppCompatActivity {

    private RecyclerView rvSoldProducts;
    private SoldProductAdapter adapter;
    private List<SoldProductEntry> soldProductList = new ArrayList<>();
    private List<SoldProductEntry> filteredList = new ArrayList<>();

    private SearchView searchView;
    private LinearLayout emptyStateView;
    private TextView tvEmptyMessage;
    private TabLayout tabDateFilter;
    private Toolbar toolbar;

    private DatabaseReference invoicesRef;
    private String userMobile;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sold_products);

        initViews();
        setupToolbar();
        setupRecyclerView();
        getUserMobileAndLoadData();
        setupSearch();
    }

    private void initViews() {
        rvSoldProducts = findViewById(R.id.rvSoldProducts);
        searchView = findViewById(R.id.searchSoldProducts);
        emptyStateView = findViewById(R.id.emptyStateView);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        tabDateFilter = findViewById(R.id.tabDateFilter);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Sold Invoices");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupRecyclerView() {
        rvSoldProducts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SoldProductAdapter(filteredList);
        rvSoldProducts.setAdapter(adapter);
    }

    private void getUserMobileAndLoadData() {
        userMobile = getSharedPreferences("APP_PREFS", MODE_PRIVATE).getString("USER_MOBILE", null);
        if (userMobile == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        invoicesRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile).child("invoices");
        setupTabs();
        loadSoldProducts();
    }

    private void setupTabs() {
        tabDateFilter.addTab(tabDateFilter.newTab().setText("Today"));
        tabDateFilter.addTab(tabDateFilter.newTab().setText("Yesterday"));
        tabDateFilter.addTab(tabDateFilter.newTab().setText("All"));
        tabDateFilter.selectTab(tabDateFilter.getTabAt(0));

        tabDateFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                applyTabFilter(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                applyTabFilter(tab.getPosition());
            }
        });
    }

    private void loadSoldProducts() {
        invoicesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                soldProductList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Invoice invoice = ds.getValue(Invoice.class);
                        if (invoice != null && invoice.items != null && !invoice.items.isEmpty()) {
                            for (InvoiceItem item : invoice.items) {
                                SoldProductEntry entry = new SoldProductEntry(
                                        invoice.invoiceNumber != null ? invoice.invoiceNumber : "",
                                        invoice.invoiceDate != null ? invoice.invoiceDate : "",
                                        invoice.customerName != null ? invoice.customerName : "",
                                        item.productName != null ? item.productName : "",
                                        item.quantity
                                );
                                soldProductList.add(entry);
                            }
                        }
                    }
                }

                // Apply the current tab filter
                int selectedTabPosition = tabDateFilter.getSelectedTabPosition();
                applyTabFilter(selectedTabPosition);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SoldProductsActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
                updateEmptyState(true, "Failed to load data. Please try again.");
            }
        });
    }

    private void applyTabFilter(int position) {
        LocalDate today = LocalDate.now();
        LocalDate filterDate = null;

        if (position == 0) {
            filterDate = today;
            updateEmptyMessage("No products sold today");
        } else if (position == 1) {
            filterDate = today.minusDays(1);
            updateEmptyMessage("No products sold yesterday");
        } else {
            updateEmptyMessage("No products sold yet");
        }

        filteredList.clear();

        if (position == 2) {
            // All - show everything
            filteredList.addAll(soldProductList);
        } else if (filterDate != null) {
            // Filter by specific date
            for (SoldProductEntry entry : soldProductList) {
                if (entry.isForDate(filterDate)) {
                    filteredList.add(entry);
                }
            }
        }

        // Apply search filter if there's a query
        String query = searchView.getQuery().toString();
        if (!query.isEmpty()) {
            applySearchFilter(query);
        } else {
            updateAdapterAndEmptyState(filteredList);
        }
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applySearchFilter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applySearchFilter(newText);
                return true;
            }
        });
    }

    private void applySearchFilter(String query) {
        query = query.toLowerCase(Locale.ROOT).trim();

        if (query.isEmpty()) {
            // Reset to tab-filtered list
            updateAdapterAndEmptyState(filteredList);
            return;
        }

        // First, get the base list from current tab filter
        List<SoldProductEntry> baseList = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int position = tabDateFilter.getSelectedTabPosition();

        if (position == 0) {
            for (SoldProductEntry entry : soldProductList) {
                if (entry.isForDate(today)) {
                    baseList.add(entry);
                }
            }
        } else if (position == 1) {
            LocalDate yesterday = today.minusDays(1);
            for (SoldProductEntry entry : soldProductList) {
                if (entry.isForDate(yesterday)) {
                    baseList.add(entry);
                }
            }
        } else {
            baseList.addAll(soldProductList);
        }

        // Now apply search filter
        List<SoldProductEntry> searchResults = new ArrayList<>();
        for (SoldProductEntry entry : baseList) {
            boolean matches = entry.productName.toLowerCase(Locale.ROOT).contains(query)
                    || entry.customerName.toLowerCase(Locale.ROOT).contains(query)
                    || entry.invoiceDate.contains(query)
                    || entry.invoiceNumber.toLowerCase(Locale.ROOT).contains(query);

            if (matches) {
                searchResults.add(entry);
            }
        }

        updateAdapterAndEmptyState(searchResults);
    }

    private void updateAdapterAndEmptyState(List<SoldProductEntry> list) {
        adapter.updateList(list);
        boolean isEmpty = list.isEmpty();
        updateEmptyState(isEmpty, getEmptyMessageForCurrentTab());
    }

    private void updateEmptyState(boolean isEmpty, String message) {
        if (isEmpty) {
            rvSoldProducts.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
            tvEmptyMessage.setText(message);

            // Add fade-in animation
            emptyStateView.startAnimation(
                    AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            );
        } else {
            rvSoldProducts.setVisibility(View.VISIBLE);
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void updateEmptyMessage(String message) {
        tvEmptyMessage.setText(message);
    }

    private String getEmptyMessageForCurrentTab() {
        int position = tabDateFilter.getSelectedTabPosition();
        String searchQuery = searchView.getQuery().toString();

        if (!searchQuery.isEmpty()) {
            return "No products match \"" + searchQuery + "\"";
        }

        switch (position) {
            case 0: return "No products sold today";
            case 1: return "No products sold yesterday";
            default: return "No products sold yet";
        }
    }
}