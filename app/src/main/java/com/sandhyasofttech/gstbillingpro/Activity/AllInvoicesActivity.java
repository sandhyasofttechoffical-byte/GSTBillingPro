package com.sandhyasofttech.gstbillingpro.Activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.Adapter.AllInvoicesAdapter;
import com.sandhyasofttech.gstbillingpro.Model.RecentInvoiceItem;
import com.sandhyasofttech.gstbillingpro.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class AllInvoicesActivity extends AppCompatActivity {

    private RecyclerView rvAllInvoices;
    private SearchView etSearchInvoice;
    private TextView tvFilter;

    private DatabaseReference userRef;
    private final ArrayList<RecentInvoiceItem> fullList = new ArrayList<>();
    private final ArrayList<RecentInvoiceItem> filteredList = new ArrayList<>();

    private AllInvoicesAdapter adapter;
    private String userMobile;

    // Filter states
    private String currentFilter = "ALL"; // ALL, PENDING, PAID
    private boolean isDateFilterActive = false;
    private Date customStartDate = null;
    private Date customEndDate = null;

    // Bottom sheet views
    private ImageView ivCheckAll, ivCheckPending, ivCheckPaid, ivCheckCustomDate;
    private TextView tvSelectedDateRange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_invoices);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        // ================= TOOLBAR =================
        Toolbar toolbar = findViewById(R.id.toolbarAllInvoices);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // ================= VIEWS =================
        rvAllInvoices = findViewById(R.id.rvAllInvoices);
        etSearchInvoice = findViewById(R.id.etSearchInvoice);
        tvFilter = findViewById(R.id.tvFilter);

        // ================= SESSION =================
        userMobile = getSharedPreferences("APP_PREFS", MODE_PRIVATE)
                .getString("USER_MOBILE", null);

        if (userMobile == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ================= FIREBASE =================
        userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile);

        // ================= RECYCLER VIEW =================
        rvAllInvoices.setLayoutManager(new LinearLayoutManager(this));
        rvAllInvoices.setHasFixedSize(true);

        adapter = new AllInvoicesAdapter(filteredList, invoiceNo -> {
            Intent intent = new Intent(AllInvoicesActivity.this, InvDetailsActivity.class);
            intent.putExtra("invoiceNumber", invoiceNo);
            startActivity(intent);
        });

        rvAllInvoices.setAdapter(adapter);

        // ================= LOAD DATA =================
        loadAllInvoices();

        // ================= SEARCH =================
        etSearchInvoice.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilters();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilters();
                return false;
            }
        });

        // ================= FILTER CLICK =================
        tvFilter.setOnClickListener(v -> showFilterBottomSheet());
    }

    // ================= LOAD ALL INVOICES =================
    private void loadAllInvoices() {
        userRef.child("invoices")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        fullList.clear();

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String invoiceNo = ds.child("invoiceNumber").getValue(String.class);
                            String customerName = ds.child("customerName").getValue(String.class);
                            String date = ds.child("invoiceDate").getValue(String.class);
                            Double total = ds.child("grandTotal").getValue(Double.class);
                            Double pending = ds.child("pendingAmount").getValue(Double.class);
                            String customerId = ds.child("customerPhone").getValue(String.class);

                            // Calculate pending amount if not stored
                            if (pending == null) {
                                Double paidAmount = ds.child("paidAmount").getValue(Double.class);
                                if (paidAmount != null && total != null) {
                                    pending = total - paidAmount;
                                } else {
                                    pending = 0.0;
                                }
                            }

                            if (invoiceNo != null && customerName != null && total != null) {
                                fullList.add(new RecentInvoiceItem(
                                        invoiceNo,
                                        customerId != null ? customerId : "",
                                        customerName,
                                        total,
                                        pending,
                                        date != null ? date : ""
                                ));

                                Log.d("LoadInvoices", "Loaded: " + invoiceNo + " | Date: " + date + " | Pending: " + pending);
                            }
                        }

                        Collections.reverse(fullList);
                        applyFilters();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(AllInvoicesActivity.this,
                                "Failed to load invoices: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ================= APPLY FILTERS =================
    private void applyFilters() {
        filteredList.clear();

        String searchQuery = etSearchInvoice.getQuery().toString().toLowerCase(Locale.ROOT).trim();

        for (RecentInvoiceItem item : fullList) {
            // Apply status filter
            boolean matchesStatus = false;
            switch (currentFilter) {
                case "ALL":
                    matchesStatus = true;
                    break;
                case "PENDING":
                    matchesStatus = item.pendingAmount > 0;
                    break;
                case "PAID":
                    matchesStatus = item.pendingAmount == 0;
                    break;
            }

            // Apply date filter
            boolean matchesDate = true;
            if (isDateFilterActive && customStartDate != null && customEndDate != null) {
                matchesDate = isInvoiceInDateRange(item.date);
            }

            // Apply search query
            boolean matchesSearch = searchQuery.isEmpty() ||
                    item.invoiceNo.toLowerCase(Locale.ROOT).contains(searchQuery) ||
                    item.customerName.toLowerCase(Locale.ROOT).contains(searchQuery);

            if (matchesStatus && matchesDate && matchesSearch) {
                filteredList.add(item);
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No invoices found", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= CHECK DATE RANGE (FIXED FOR yyyy-MM-dd) =================
    private boolean isInvoiceInDateRange(String invoiceDateStr) {
        if (invoiceDateStr == null || invoiceDateStr.isEmpty()) {
            return false;
        }

        try {
            // Primary format from Firebase: yyyy-MM-dd
            SimpleDateFormat firebaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date invoiceDate = firebaseDateFormat.parse(invoiceDateStr);

            if (invoiceDate == null) {
                Log.e("DateFilter", "Could not parse date: " + invoiceDateStr);
                return false;
            }

            // Get calendar instances for comparison
            Calendar invoiceCal = Calendar.getInstance();
            invoiceCal.setTime(invoiceDate);
            // Set to noon to avoid any timezone issues
            invoiceCal.set(Calendar.HOUR_OF_DAY, 12);
            invoiceCal.set(Calendar.MINUTE, 0);
            invoiceCal.set(Calendar.SECOND, 0);
            invoiceCal.set(Calendar.MILLISECOND, 0);

            Calendar startCal = Calendar.getInstance();
            startCal.setTime(customStartDate);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(customEndDate);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            // Check if invoice date is between start and end (inclusive)
            boolean inRange = !invoiceCal.before(startCal) && !invoiceCal.after(endCal);

            // Debug logging
            SimpleDateFormat debugFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Log.d("DateFilter", "Invoice: " + invoiceDateStr +
                    " | Parsed: " + debugFormat.format(invoiceCal.getTime()) +
                    " | Start: " + debugFormat.format(startCal.getTime()) +
                    " | End: " + debugFormat.format(endCal.getTime()) +
                    " | InRange: " + inRange);

            return inRange;

        } catch (ParseException e) {
            Log.e("DateFilter", "Error parsing date: " + invoiceDateStr + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            Log.e("DateFilter", "Error checking date range: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================= SHOW FILTER BOTTOM SHEET =================
    private void showFilterBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_filter, null);
        bottomSheet.setContentView(sheetView);

        // Get all views
        LinearLayout layoutAll = sheetView.findViewById(R.id.tvFilterAll);
        LinearLayout layoutPending = sheetView.findViewById(R.id.tvFilterPending);
        LinearLayout layoutPaid = sheetView.findViewById(R.id.tvFilterPaid);
        LinearLayout layoutCustomDate = sheetView.findViewById(R.id.tvFilterCustomDate);

        ivCheckAll = sheetView.findViewById(R.id.ivCheckAll);
        ivCheckPending = sheetView.findViewById(R.id.ivCheckPending);
        ivCheckPaid = sheetView.findViewById(R.id.ivCheckPaid);
        ivCheckCustomDate = sheetView.findViewById(R.id.ivCheckCustomDate);
        tvSelectedDateRange = sheetView.findViewById(R.id.tvSelectedDateRange);

        MaterialButton btnReset = sheetView.findViewById(R.id.btnResetFilter);
        MaterialButton btnApply = sheetView.findViewById(R.id.btnApplyFilter);

        // Update UI based on current filters
        updateFilterUI();

        // Status filter clicks
        layoutAll.setOnClickListener(v -> {
            currentFilter = "ALL";
            updateFilterUI();
        });

        layoutPending.setOnClickListener(v -> {
            currentFilter = "PENDING";
            updateFilterUI();
        });

        layoutPaid.setOnClickListener(v -> {
            currentFilter = "PAID";
            updateFilterUI();
        });

        // Custom date range click
        layoutCustomDate.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showDateRangePicker();
        });

        // Reset button
        btnReset.setOnClickListener(v -> {
            currentFilter = "ALL";
            isDateFilterActive = false;
            customStartDate = null;
            customEndDate = null;
            updateFilterUI();
            applyFilters();
            Toast.makeText(this, "Filters reset", Toast.LENGTH_SHORT).show();
        });

        // Apply button
        btnApply.setOnClickListener(v -> {
            applyFilters();
            bottomSheet.dismiss();

            String message = "Showing " + currentFilter.toLowerCase();
            if (isDateFilterActive) {
                message += " invoices in date range";
            } else {
                message += " invoices";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        bottomSheet.show();
    }

    // ================= UPDATE FILTER UI =================
    private void updateFilterUI() {
        // Status checkmarks
        if (ivCheckAll != null) ivCheckAll.setVisibility(currentFilter.equals("ALL") ? View.VISIBLE : View.GONE);
        if (ivCheckPending != null) ivCheckPending.setVisibility(currentFilter.equals("PENDING") ? View.VISIBLE : View.GONE);
        if (ivCheckPaid != null) ivCheckPaid.setVisibility(currentFilter.equals("PAID") ? View.VISIBLE : View.GONE);

        // Date checkmark and text
        if (ivCheckCustomDate != null) ivCheckCustomDate.setVisibility(isDateFilterActive ? View.VISIBLE : View.GONE);

        if (tvSelectedDateRange != null) {
            if (isDateFilterActive && customStartDate != null && customEndDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                tvSelectedDateRange.setText(sdf.format(customStartDate) + " - " + sdf.format(customEndDate));
            } else {
                tvSelectedDateRange.setText("Select date range");
            }
        }
    }

    // ================= DATE RANGE PICKER =================
    private void showDateRangePicker() {
        Calendar calendar = Calendar.getInstance();

        // Pick start date
        DatePickerDialog startDatePicker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar startCal = Calendar.getInstance();
                    startCal.set(year, month, dayOfMonth, 0, 0, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                    customStartDate = startCal.getTime();

                    // Pick end date
                    DatePickerDialog endDatePicker = new DatePickerDialog(this,
                            (v, y, m, d) -> {
                                Calendar endCal = Calendar.getInstance();
                                endCal.set(y, m, d, 23, 59, 59);
                                endCal.set(Calendar.MILLISECOND, 999);
                                customEndDate = endCal.getTime();

                                isDateFilterActive = true;

                                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                                Toast.makeText(this,
                                        "Date range: " + sdf.format(customStartDate) + " to " + sdf.format(customEndDate),
                                        Toast.LENGTH_LONG).show();

                                // Automatically apply the filter
                                applyFilters();
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH));

                    endDatePicker.setTitle("Select End Date");
                    endDatePicker.getDatePicker().setMinDate(startCal.getTimeInMillis());
                    endDatePicker.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        startDatePicker.setTitle("Select Start Date");
        startDatePicker.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userMobile != null) {
            loadAllInvoices();
        }
    }
}