package com.sandhyasofttech.gstbillingpro.custmore;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.R;
import java.util.ArrayList;
import java.util.List;

public class InvoiceDetailsActivity extends AppCompatActivity {

    private static final String TAG = "InvoiceDetails";

    private TextView tvInvoiceNo, tvDate, tvCustomer, tvCompany, tvGrandTotal;
    private RecyclerView rvItems;

    private String userMobile, invoiceId;
    private DatabaseReference invoiceRef;

    private final List<InvoiceItem> itemList = new ArrayList<>();
    private ItemAdapter itemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_details);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        invoiceId = getIntent().getStringExtra("invoice_id");

        if (invoiceId == null || invoiceId.isEmpty()) {
            Toast.makeText(this, "Invalid invoice", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userMobile = getSharedPreferences("APP_PREFS", MODE_PRIVATE).getString("USER_MOBILE", null);
        if (userMobile == null) {
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        invoiceRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userMobile)
                .child("invoices")
                .child(invoiceId);

        initViews();
        setupToolbar();
        setupRecycler();
        loadInvoice();
    }

    private void initViews() {
        tvInvoiceNo = findViewById(R.id.tvInvoiceNo);
        tvDate = findViewById(R.id.tvDate);
        tvCustomer = findViewById(R.id.tvCustomer);
        tvCompany = findViewById(R.id.tvCompany);
        tvGrandTotal = findViewById(R.id.tvGrandTotal);
        rvItems = findViewById(R.id.rvItems);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Invoice Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupRecycler() {
        itemAdapter = new ItemAdapter(itemList);
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        rvItems.setAdapter(itemAdapter);
    }

    private void loadInvoice() {
        invoiceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(InvoiceDetailsActivity.this, "Invoice not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // === Header ===
                String invNo = snapshot.child("invoiceNumber").getValue(String.class);
                String date = snapshot.child("invoiceDate").getValue(String.class);
                String custName = snapshot.child("customerName").getValue(String.class);
                String company = snapshot.child("companyLogoUrl").getValue(String.class);
                Double grandTotal = snapshot.child("grandTotal").getValue(Double.class);

                tvInvoiceNo.setText(invNo != null ? "Invoice #" + invNo : "N/A");
                tvDate.setText(date != null ? "Date: " + date : "N/A");
                tvCustomer.setText(custName != null ? "Customer: " + custName : "Customer: N/A");
                tvCompany.setText(company != null ? "Company: " + company : "Company: N/A");
                tvGrandTotal.setText(grandTotal != null ?
                        "Grand Total: ₹" + String.format("%,.0f", grandTotal) : "₹0");

                // === Items - NO TAX CALCULATION ===
                itemList.clear();
                DataSnapshot itemsSnap = snapshot.child("items");
                for (DataSnapshot item : itemsSnap.getChildren()) {
                    String productName = item.child("productName").getValue(String.class);
                    Double quantity = item.child("quantity").getValue(Double.class);
                    Double rate = item.child("rate").getValue(Double.class);

                    if (productName != null && quantity != null && rate != null) {
                        // 🔥 SIMPLE: Total = Qty * Rate (NO TAX)
                        double lineTotal = quantity * rate;
                        itemList.add(new InvoiceItem(
                                productName,
                                quantity,
                                rate,
                                lineTotal
                        ));
                    }
                }

                Log.d(TAG, "Loaded " + itemList.size() + " items for invoice: " + invoiceId);
                itemAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());
                Toast.makeText(InvoiceDetailsActivity.this, "Failed to load", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ────────────────────── SIMPLIFIED ADAPTER ──────────────────────
    static class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private final List<InvoiceItem> items;

        ItemAdapter(List<InvoiceItem> items) {
            this.items = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_invoice_detail, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            InvoiceItem item = items.get(position);
            holder.tvProductName.setText(item.productName);
            holder.tvQty.setText("Qty: " + String.format("%.2f", item.quantity));
            holder.tvRate.setText("Rate: ₹" + String.format("%,.0f", item.rate));
            holder.tvLineTotal.setText("Total: ₹" + String.format("%,.0f", item.lineTotal));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvProductName, tvQty, tvRate, tvLineTotal;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                tvProductName = itemView.findViewById(R.id.tvProductName);
                tvQty = itemView.findViewById(R.id.tvQty);
                tvRate = itemView.findViewById(R.id.tvRate);
                tvLineTotal = itemView.findViewById(R.id.tvLineTotal);
            }
        }
    }

    // ────────────────────── SIMPLIFIED MODEL ──────────────────────
    static class InvoiceItem {
        String productName;
        double quantity, rate, lineTotal;

        InvoiceItem(String name, double qty, double rate, double lineTotal) {
            this.productName = name;
            this.quantity = qty;
            this.rate = rate;
            this.lineTotal = lineTotal;
        }
    }
}
