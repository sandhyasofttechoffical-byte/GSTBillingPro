package com.sandhyasofttech.gstbillingpro.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.Activity.AllInvoicesActivity;
import com.sandhyasofttech.gstbillingpro.Activity.PendingPaymentsActivity;
import com.sandhyasofttech.gstbillingpro.Activity.ShareExportActivity;
import com.sandhyasofttech.gstbillingpro.Activity.TotalOverviewActivity;
import com.sandhyasofttech.gstbillingpro.Adapter.RecentInvoiceAdapter;
import com.sandhyasofttech.gstbillingpro.MainActivity;
import com.sandhyasofttech.gstbillingpro.Model.RecentInvoiceItem;
import com.sandhyasofttech.gstbillingpro.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {

    private TextView tvTodaysSales, tvMonthSales;
    private MaterialButton btnNewInvoice, btnAddCustomer, btnShareExport, btnViewAllInvoices, btnCreateFirstInvoice;
    private RecyclerView rvRecentActivity;
    private TextView tvTotalCustomers, tvTotalProducts, tvLastBackup, tvPendingAmount;
    private ExtendedFloatingActionButton fabNewInvoice;
    private NestedScrollView scrollView;
    private View layoutPending, layoutTodaySales;
    private LinearLayout emptyStateView;

    private String userMobile;
    private DatabaseReference userRef, productsRef, invoicesRef;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM", Locale.US);
    private final Set<String> uniqueProductIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // === FIND ALL VIEWS ===
        tvTodaysSales = view.findViewById(R.id.tvTodaysSales);
        tvMonthSales = view.findViewById(R.id.tvMonthSales);
        btnNewInvoice = view.findViewById(R.id.btnNewInvoice);
        btnAddCustomer = view.findViewById(R.id.btnAddCustomer);
        btnShareExport = view.findViewById(R.id.btnShareExport);
        btnViewAllInvoices = view.findViewById(R.id.btnViewAllInvoices);
        rvRecentActivity = view.findViewById(R.id.rvRecentActivity);
        tvTotalCustomers = view.findViewById(R.id.tvTotalCustomers);
        tvTotalProducts = view.findViewById(R.id.tvTotalProducts);
        tvLastBackup = view.findViewById(R.id.tvLastBackup);
        fabNewInvoice = view.findViewById(R.id.fabNewInvoice);
        scrollView = view.findViewById(R.id.scrollView);
        tvPendingAmount = view.findViewById(R.id.tvPendingAmount);
        layoutPending = view.findViewById(R.id.layoutPending);
        layoutTodaySales = view.findViewById(R.id.layoutTodaySales);

        // Empty state views
        emptyStateView = view.findViewById(R.id.emptyStateView);

        // === USER SESSION CHECK ===
        SharedPreferences prefs = requireActivity().getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);
        if (userMobile == null) {
            Toast.makeText(getContext(), "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        layoutPending.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), PendingPaymentsActivity.class);
            startActivity(intent);
        });

        // === FIREBASE REFERENCES ===
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile);
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        invoicesRef = userRef.child("invoices");

        // === LOAD ALL DATA ===
        loadDynamicSalesAndProducts();
        loadRecentInvoices();
        listenToCustomerCount();
        listenToInvoiceCount();
        loadLastBackup();

        // === BUTTON CLICKS ===
        btnNewInvoice.setOnClickListener(v -> openInvoiceFragment());
        btnAddCustomer.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).syncNavigation(R.id.nav_customer);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, new CustomerFragment())
                    .addToBackStack(null)
                    .commit();
        });

        layoutTodaySales.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), TotalOverviewActivity.class);
            intent.putExtra("FILTER_TYPE", "TODAY");
            startActivity(intent);
        });

        btnShareExport.setOnClickListener(v -> startActivity(new Intent(getContext(), ShareExportActivity.class)));
        btnViewAllInvoices.setOnClickListener(v -> startActivity(new Intent(getContext(), AllInvoicesActivity.class)));


        // === FAB CLICK ===
        if (fabNewInvoice != null) {
            fabNewInvoice.setOnClickListener(v -> openInvoiceFragment());
        }

        if (scrollView != null && fabNewInvoice != null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            final long DELAY_SHOW = 1300L;

            final Runnable showFabRunnable = () -> {
                if (!fabNewInvoice.isShown()) {
                    fabNewInvoice.show();
                }
            };

            scrollView.setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {

                        handler.removeCallbacks(showFabRunnable);

                        if (scrollY > oldScrollY + 25 && scrollY > 500) {
                            if (fabNewInvoice.isShown()) {
                                fabNewInvoice.hide();
                            }
                        } else if (scrollY < oldScrollY - 25) {
                            if (!fabNewInvoice.isShown()) {
                                fabNewInvoice.show();
                            }
                        }

                        handler.postDelayed(showFabRunnable, DELAY_SHOW);
                    });
        }

        if (fabNewInvoice != null) {
            fabNewInvoice.postDelayed(() -> fabNewInvoice.show(), 400);
        }
    }

    // === OPEN INVOICE FRAGMENT ===
    private void openInvoiceFragment() {
        ((MainActivity) requireActivity()).syncNavigation(R.id.nav_invoice);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, new InvoiceBillingFragment())
                .addToBackStack(null)
                .commit();
    }

    // === LOAD TODAY & MONTH SALES ===
    private void loadDynamicSalesAndProducts() {
        String today = dateFmt.format(new Date());
        String currentMonth = monthFmt.format(new Date());

        invoicesRef.addValueEventListener(new ValueEventListener() {

            double todaySale = 0.0;
            double monthSale = 0.0;
            double totalPending = 0.0;

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                todaySale = 0;
                monthSale = 0;
                totalPending = 0;
                uniqueProductIds.clear();

                for (DataSnapshot invSnap : snapshot.getChildren()) {

                    String invDate = invSnap.child("invoiceDate").getValue(String.class);
                    Double grandTotal = invSnap.child("grandTotal").getValue(Double.class);
                    Double pending = invSnap.child("pendingAmount").getValue(Double.class);

                    if (grandTotal == null) continue;

                    if (invDate != null) {
                        if (invDate.equals(today)) todaySale += grandTotal;
                        if (invDate.startsWith(currentMonth)) monthSale += grandTotal;
                    }

                    if (pending != null && pending > 0) {
                        totalPending += pending;
                    }

                    for (DataSnapshot item : invSnap.child("items").getChildren()) {
                        String productId = item.child("productId").getValue(String.class);
                        if (productId != null) uniqueProductIds.add(productId);
                    }
                }

                if (tvTodaysSales != null)
                    tvTodaysSales.setText(formatCurrency((long) todaySale));

                if (tvMonthSales != null)
                    tvMonthSales.setText(formatCurrency((long) monthSale));

                if (tvPendingAmount != null)
                    tvPendingAmount.setText(formatCurrency((long) totalPending));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(),
                        "Failed to load dashboard data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // === LOAD RECENT 10 INVOICES ===
    private void loadRecentInvoices() {
        invoicesRef.limitToLast(10)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        ArrayList<RecentInvoiceItem> list = new ArrayList<>();

                        for (DataSnapshot ds : snapshot.getChildren()) {

                            String invoiceNo =
                                    ds.child("invoiceNumber").getValue(String.class);

                            String customerName =
                                    ds.child("customerName").getValue(String.class);

                            String customerPhone =
                                    ds.child("customerPhone").getValue(String.class);

                            Double grandTotal =
                                    ds.child("grandTotal").getValue(Double.class);

                            double pendingAmount = 0;
                            Object pendingObj = ds.child("pendingAmount").getValue();
                            if (pendingObj instanceof Number) {
                                pendingAmount = ((Number) pendingObj).doubleValue();
                            }

                            String date =
                                    ds.child("invoiceDate").getValue(String.class);

                            if (invoiceNo != null &&
                                    customerName != null &&
                                    grandTotal != null &&
                                    date != null) {

                                list.add(new RecentInvoiceItem(
                                        invoiceNo,
                                        customerPhone == null ? "" : customerPhone,
                                        customerName,
                                        grandTotal,
                                        pendingAmount,
                                        date
                                ));
                            }
                        }

                        Collections.reverse(list);

                        // 🔥 UPDATE EMPTY STATE BASED ON LIST SIZE
                        updateEmptyState(list.isEmpty());

                        // 🔥 IMPORTANT
                        rvRecentActivity.setLayoutManager(
                                new LinearLayoutManager(getContext())
                        );
                        rvRecentActivity.setAdapter(
                                new RecentInvoiceAdapter(list)
                        );
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(),
                                "Failed to load recent invoices",
                                Toast.LENGTH_SHORT).show();
                        // Show empty state on error too
                        updateEmptyState(true);
                    }
                });
    }

    // === UPDATE EMPTY STATE VISIBILITY ===
    private void updateEmptyState(boolean isEmpty) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (isEmpty) {
                // Show empty state with animation
                rvRecentActivity.setVisibility(View.GONE);
                emptyStateView.setVisibility(View.VISIBLE);

                // Add fade-in animation
                emptyStateView.startAnimation(
                        AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_in)
                );
            } else {
                // Show recycler view
                rvRecentActivity.setVisibility(View.VISIBLE);
                emptyStateView.setVisibility(View.GONE);
            }
        });
    }

    // === TOTAL CUSTOMERS ===
    private void listenToCustomerCount() {
        userRef.child("customers").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (tvTotalCustomers != null) tvTotalCustomers.setText(" " + count);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (tvTotalCustomers != null) tvTotalCustomers.setText(" Error");
            }
        });
    }

    // === TOTAL INVOICES ===
    private void listenToInvoiceCount() {
        invoicesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (tvTotalProducts != null) tvTotalProducts.setText(" " + count);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (tvTotalProducts != null) tvTotalProducts.setText(" Error");
            }
        });
    }

    // === LAST BACKUP TIME ===
    private void loadLastBackup() {
        userRef.child("summary").child("lastBackup").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long timestamp = snapshot.getValue(Long.class);
                String text = (timestamp == null || timestamp == 0)
                        ? "Last Backup: Never"
                        : "Last Backup: " + formatBackupTime(timestamp);
                if (tvLastBackup != null) tvLastBackup.setText(text);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (tvLastBackup != null) tvLastBackup.setText("Last Backup: Error");
            }
        });
    }

    // === FORMAT BACKUP TIME ===
    private String formatBackupTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long minute = 60 * 1000;
        long hour = 60 * minute;
        long day = 24 * hour;

        if (diff < hour) {
            long mins = diff / minute;
            return mins <= 1 ? "Just now" : mins + " mins ago";
        } else if (diff < day) {
            long hours = diff / hour;
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        } else if (diff < 2 * day) {
            return "Yesterday";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    // === FORMAT CURRENCY ===
    private String formatCurrency(long amount) {
        return "₹" + String.format("%,d", amount);
    }
}