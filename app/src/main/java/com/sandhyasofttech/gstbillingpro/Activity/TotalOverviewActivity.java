package com.sandhyasofttech.gstbillingpro.Activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.Adapter.RecentInvoiceAdapter;
import com.sandhyasofttech.gstbillingpro.Model.RecentInvoiceItem;
import com.sandhyasofttech.gstbillingpro.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class TotalOverviewActivity extends AppCompatActivity {

    // Date range
    private String startDate, endDate;
    private TextView tvRangeTitle, tvRangeSubtitle;
    private MaterialCardView cardDateRange;

    // KPI views
    private TextView tvSalesAmount, tvReceivedAmount, tvPendingAmount, tvInvoicesCount,
            tvSalesGrowth, tvPendingHint, tvProductsCount, tvSalesLabel, tvReceivedLabel,
            tvPendingLabel, tvInvoicesLabel, tvChartTitle, tvRecentTitle;

    private MaterialCardView cardSales, cardReceived, cardPending, cardInvoices;

    // List views
    private RecyclerView rvRecent;
    private View layoutEmpty;
    private ProgressBar progressBar;

    // Filter chips
    private ChipGroup chipGroupFilter;
    private Chip chipAll, chipPaid, chipPartial, chipPending;

    // Charts
    private BarChart chartSalesBar;
    private PieChart chartPaymentPie;

    // Firebase
    private DatabaseReference invoicesRef;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    // Data
    private final ArrayList<RecentInvoiceItem> allInvoicesList = new ArrayList<>();
    private double maxPending = 0;
    private int pendingCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_total_overview);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        initViews();
        setupToolbar();
        setupDateRange(); // Sets today by default
        setupFirebase();
        setupCharts();
        setupCardClicks();
        setupFilterChips();

        loadRangeData(); // Load today's data first
    }

    private void initViews() {
        // Date range header
        cardDateRange = findViewById(R.id.cardDateRange);
        tvRangeTitle = findViewById(R.id.tvRangeTitle);
        tvRangeSubtitle = findViewById(R.id.tvRangeSubtitle);

        // KPI TextViews
        tvSalesAmount = findViewById(R.id.tvSalesAmount);
        tvSalesLabel = findViewById(R.id.tvSalesLabel);
        tvSalesGrowth = findViewById(R.id.tvSalesGrowth);
        tvReceivedAmount = findViewById(R.id.tvReceivedAmount);
        tvReceivedLabel = findViewById(R.id.tvReceivedLabel);
        tvPendingAmount = findViewById(R.id.tvPendingAmount);
        tvPendingLabel = findViewById(R.id.tvPendingLabel);
        tvPendingHint = findViewById(R.id.tvPendingHint);
        tvInvoicesCount = findViewById(R.id.tvInvoicesCount);
        tvInvoicesLabel = findViewById(R.id.tvInvoicesLabel);
        tvProductsCount = findViewById(R.id.tvProductsCount);

        tvChartTitle = findViewById(R.id.tvChartTitle);
        tvRecentTitle = findViewById(R.id.tvRecentTitle);

        // Cards
        cardSales = findViewById(R.id.cardSales);
        cardReceived = findViewById(R.id.cardReceived);
        cardPending = findViewById(R.id.cardPending);
        cardInvoices = findViewById(R.id.cardInvoices);

        // List + progress
        rvRecent = findViewById(R.id.rvRecent);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        progressBar = findViewById(R.id.progressOverview);
        rvRecent.setLayoutManager(new LinearLayoutManager(this));

        // Filter chips
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        chipAll = findViewById(R.id.chipAll);
        chipPaid = findViewById(R.id.chipPaid);
        chipPartial = findViewById(R.id.chipPartial);
        chipPending = findViewById(R.id.chipPending);

        // Charts
        chartSalesBar = findViewById(R.id.chartSalesBar);
        chartPaymentPie = findViewById(R.id.chartPaymentPie);
    }

    private void setupDateRange() {
        // Default: Today
        Date today = new Date();
        startDate = endDate = dateFmt.format(today);
        updateDateDisplay("Today", displayFmt.format(today));

        cardDateRange.setOnClickListener(v -> showSimpleDatePicker());
    }

    private void showSimpleDatePicker() {
        // Simple dropdown with common options + custom
        String[] options = {
                "Today",
                "Yesterday",
                "Last 7 Days",
                "This Month",
                "Custom Range"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Date Range")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Today
                            Date today = new Date();
                            startDate = endDate = dateFmt.format(today);
                            updateDateDisplay("Today", displayFmt.format(today));
                            break;

                        case 1: // Yesterday
                            Calendar yesterday = Calendar.getInstance();
                            yesterday.add(Calendar.DAY_OF_YEAR, -1);
                            startDate = endDate = dateFmt.format(yesterday.getTime());
                            updateDateDisplay("Yesterday", displayFmt.format(yesterday.getTime()));
                            break;

                        case 2: // Last 7 Days
                            Calendar end7 = Calendar.getInstance();
                            Calendar start7 = Calendar.getInstance();
                            start7.add(Calendar.DAY_OF_YEAR, -7);
                            startDate = dateFmt.format(start7.getTime());
                            endDate = dateFmt.format(end7.getTime());
                            updateDateDisplay("Last 7 Days", displayFmt.format(end7.getTime()));
                            break;

                        case 3: // This Month
                            Calendar endMonth = Calendar.getInstance();
                            Calendar startMonth = Calendar.getInstance();
                            startMonth.set(Calendar.DAY_OF_MONTH, 1);
                            startDate = dateFmt.format(startMonth.getTime());
                            endDate = dateFmt.format(endMonth.getTime());
                            updateDateDisplay("This Month", displayFmt.format(endMonth.getTime()));
                            break;

                        case 4: // Custom → Simple date picker
                            showCustomDatePicker();
                            return;
                    }
                    loadRangeData(); // Reload data
                })
                .show();
    }
    private void showCustomDatePicker() {
        // Simple single date picker first
        Calendar today = Calendar.getInstance();

        com.google.android.material.datepicker.MaterialDatePicker<Long> picker =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                        .setTitleText("Select Date")
                        .setSelection(today.getTimeInMillis())
                        .build();

        picker.addOnPositiveButtonClickListener(dateMillis -> {
            startDate = endDate = dateFmt.format(new Date(dateMillis));
            updateDateDisplay(getSingleDayTitle(startDate), displayFmt.format(new Date(dateMillis)));
            loadRangeData();
        });

        picker.show(getSupportFragmentManager(), "SINGLE_DATE_PICKER");
    }


    private String getSingleDayTitle(String dateStr) {
        try {
            Date date = dateFmt.parse(dateStr);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (date.equals(today.getTime())) {
                return "Today";
            } else if (date.equals(yesterday.getTime())) {
                return "Yesterday";
            } else {
                return displayFmt.format(date).substring(0, 11); // "18 Dec 2025"
            }
        } catch (Exception e) {
            return dateStr;
        }
    }

    private void setupFirebase() {
        SharedPreferences prefs = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE);
        String userMobile = prefs.getString("USER_MOBILE", null);

        if (userMobile == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        invoicesRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile)
                .child("invoices");
    }

    private void loadRangeData() {
        progressBar.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        rvRecent.setVisibility(View.GONE);
        allInvoicesList.clear();
        maxPending = 0;
        pendingCount = 0;

        invoicesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double totalSales = 0, totalReceived = 0, totalPending = 0;
                long invoiceCount = 0;
                Set<String> productIds = new HashSet<>();
                ArrayList<Double> invoiceAmounts = new ArrayList<>();

                for (DataSnapshot invSnap : snapshot.getChildren()) {
                    String invDate = invSnap.child("invoiceDate").getValue(String.class);

                    // Filter by date range
                    if (invDate == null || invDate.compareTo(startDate) < 0 || invDate.compareTo(endDate) > 0) {
                        continue;
                    }

                    invoiceCount++;
                    Double grandTotal = invSnap.child("grandTotal").getValue(Double.class);
                    Double paidAmount = invSnap.child("paidAmount").getValue(Double.class);
                    Double pending = invSnap.child("pendingAmount").getValue(Double.class);
                    String invoiceNo = invSnap.child("invoiceNumber").getValue(String.class);
                    String customerName = invSnap.child("customerName").getValue(String.class);
                    String customerPhone = invSnap.child("customerPhone").getValue(String.class);

                    if (grandTotal != null) {
                        totalSales += grandTotal;
                        invoiceAmounts.add(grandTotal);
                    }
                    if (paidAmount != null) totalReceived += paidAmount;
                    if (pending != null && pending > 0) {
                        totalPending += pending;
                        pendingCount++;
                        if (pending > maxPending) maxPending = pending;
                    }

                    // Products
                    for (DataSnapshot item : invSnap.child("items").getChildren()) {
                        String productId = item.child("productId").getValue(String.class);
                        if (productId != null) productIds.add(productId);
                    }

                    // Add to list
                    if (invoiceNo != null && customerName != null && grandTotal != null) {
                        allInvoicesList.add(new RecentInvoiceItem(
                                invoiceNo, customerPhone != null ? customerPhone : "",
                                customerName, grandTotal, pending != null ? pending : 0, invDate
                        ));
                    }
                }

                // Sort newest first
                Collections.reverse(allInvoicesList);

                bindOverview(totalSales, totalReceived, totalPending, invoiceCount, productIds.size(), invoiceAmounts);
                updateCharts(invoiceAmounts, totalReceived, totalPending);
                updateList(allInvoicesList); // Initially show all

                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TotalOverviewActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindOverview(double sales, double received, double pending, long invoices, int products, ArrayList<Double> amounts) {
        tvSalesAmount.setText(formatCurrency((long) sales));
        tvReceivedAmount.setText(formatCurrency((long) received));
        tvPendingAmount.setText(formatCurrency((long) pending));
        tvInvoicesCount.setText(String.valueOf(invoices));
        tvProductsCount.setText(products + " Products");
        tvProductsCount.setVisibility(products > 0 ? View.VISIBLE : View.GONE);

        tvSalesGrowth.setText(sales > 0 ? "+12%" : "No sales");

        if (pendingCount > 0) {
            tvPendingHint.setText(pendingCount + " invoices");
            tvPendingHint.setVisibility(View.VISIBLE);
        } else {
            tvPendingHint.setVisibility(View.GONE);
        }

        // Dynamic titles based on range
        tvSalesLabel.setText(startDate.equals(endDate) ? "Today's Sales" : "Total Sales");
        tvReceivedLabel.setText(startDate.equals(endDate) ? "Received Today" : "Total Received");
        tvPendingLabel.setText(startDate.equals(endDate) ? "Pending Today" : "Total Pending");
        tvInvoicesLabel.setText(startDate.equals(endDate) ? "Today's Invoices" : "Total Invoices");

        tvChartTitle.setText(startDate.equals(endDate) ? "Today's Sales & Payments" : "Sales & Payments");
        tvRecentTitle.setText(startDate.equals(endDate) ? "Today's Invoices" : "Recent Invoices");
    }

    private void updateCharts(ArrayList<Double> invoiceAmounts, double received, double pending) {
        updateSalesBar(invoiceAmounts);
        updatePaymentPie(received, pending);
    }

    private void updateSalesBar(ArrayList<Double> amounts) {
        if (chartSalesBar == null || amounts.isEmpty()) return;

        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            entries.add(new BarEntry(i, amounts.get(i).floatValue()));
        }

        BarDataSet set = new BarDataSet(entries, "Invoices");
        set.setColors(ColorTemplate.MATERIAL_COLORS);
        set.setValueTextSize(9f);

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        chartSalesBar.setData(data);
        chartSalesBar.getXAxis().setLabelCount(Math.min(entries.size(), 5));
        chartSalesBar.invalidate();
    }

    private void updatePaymentPie(double received, double pending) {
        if (chartPaymentPie == null) return;

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (received > 0) entries.add(new PieEntry((float) received, "Received"));
        if (pending > 0) entries.add(new PieEntry((float) pending, "Pending"));

        if (entries.isEmpty()) {
            chartPaymentPie.clear();
            return;
        }

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(ColorTemplate.MATERIAL_COLORS);
        set.setSliceSpace(2f);
        set.setValueTextSize(10f);

        PieData data = new PieData(set);
        chartPaymentPie.setData(data);
        chartPaymentPie.invalidate();
    }

    private void setupFilterChips() {
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            ArrayList<RecentInvoiceItem> filtered = new ArrayList<>();

            for (RecentInvoiceItem item : allInvoicesList) {
                double pending = item.getPendingAmount();
                double grandTotal = item.getGrandTotal();

                if (checkedId == R.id.chipPaid) {
                    if (pending <= 0) filtered.add(item);
                } else if (checkedId == R.id.chipPartial) {
                    if (pending > 0 && pending < grandTotal) filtered.add(item);
                } else if (checkedId == R.id.chipPending) {
                    if (pending > 0) filtered.add(item);
                } else { // All
                    filtered.add(item);
                }
            }
            updateList(filtered);
        });

        chipAll.setChecked(true);
    }

    private void updateList(ArrayList<RecentInvoiceItem> list) {
        if (list.isEmpty()) {
            rvRecent.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            rvRecent.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            rvRecent.setAdapter(new RecentInvoiceAdapter(list));
        }
    }

    private void setupCharts() {
        if (chartSalesBar != null) {
            chartSalesBar.setNoDataText("No sales data");
            chartSalesBar.getDescription().setEnabled(false);
            chartSalesBar.getAxisRight().setEnabled(false);
            chartSalesBar.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            chartSalesBar.getLegend().setEnabled(false);
        }

        if (chartPaymentPie != null) {
            chartPaymentPie.setNoDataText("No payment data");
            chartPaymentPie.getDescription().setEnabled(false);
            chartPaymentPie.setUsePercentValues(true);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarOverview);
        toolbar.setTitle("Overview");
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupCardClicks() {
        cardSales.setOnClickListener(v ->
                Toast.makeText(this, "Sales details", Toast.LENGTH_SHORT).show());
        cardReceived.setOnClickListener(v ->
                Toast.makeText(this, "Payment details", Toast.LENGTH_SHORT).show());
        cardPending.setOnClickListener(v ->
                Toast.makeText(this, "Pending details", Toast.LENGTH_SHORT).show());
        cardInvoices.setOnClickListener(v ->
                Toast.makeText(this, "Invoice details", Toast.LENGTH_SHORT).show());
    }



    private void updateDateDisplay(String title, String subtitle) {
        tvRangeTitle.setText(title);
        tvRangeSubtitle.setText(subtitle);
    }

    private String formatCurrency(long amount) {
        return "₹" + String.format("%,d", amount);
    }
}
