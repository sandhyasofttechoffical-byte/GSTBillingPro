package com.sandhyasofttech.gstbillingpro.Activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.*;
import com.sandhyasofttech.gstbillingpro.Adapter.PaymentSummaryAdapter;
import com.sandhyasofttech.gstbillingpro.Model.CartItem;
import com.sandhyasofttech.gstbillingpro.R;
import com.sandhyasofttech.gstbillingpro.invoice.Invoice;
import com.sandhyasofttech.gstbillingpro.invoice.InvoiceItem;
import com.sandhyasofttech.gstbillingpro.invoice.GstCalculationUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PaymentActivity extends AppCompatActivity {

    private TextView tvCustomerName, tvSubtotal, tvTax, tvGrandTotal;
    private TextView tvAmountPaid, tvBalance, tvPendingAmount;
    private EditText etPaidAmount;
    private RadioGroup rgPaymentStatus, rgPaymentMode;
    private RadioButton rbCash, rbOnline;
    private MaterialButton btnGenerateInvoice;
    private RecyclerView rvSummary;
    private boolean isGstEnabled = false;   // 🔥 GST OFF by default
    private boolean isIntraState = true;
    private String customerName, customerPhone, customerAddress, userMobile;
    private ArrayList<CartItem> cartItems;
    private double cartTotal, totalTaxable, totalCGST, totalSGST, totalIGST, grandTotal;
    private double paidAmount = 0, pendingAmount = 0;
    private String paymentMode = "Cash"; // Default payment mode

    private DatabaseReference usersRef, invoicesRef, infoRef;
    private String businessName = "Your Business", businessGstin = "", businessAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        // 🔥 1. Get user mobile FIRST
        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);
        if (userMobile == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔥 2. Initialize Firebase FIRST
        usersRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile);
        invoicesRef = usersRef.child("invoices");
        infoRef = usersRef.child("info");

        // 🔥 3. Initialize ALL views
        initViews();

        // 🔥 4. Check edit mode BEFORE getting intent data
        boolean isEditMode = getIntent().getBooleanExtra("EDIT_MODE", false);
        String editInvoiceNumber = getIntent().getStringExtra("INVOICE_NUMBER");

        if (isEditMode && editInvoiceNumber != null) {
            btnGenerateInvoice.setText("Update Invoice");
            loadInvoiceForEdit(editInvoiceNumber);
        } else {
            // New invoice flow
            customerName = getIntent().getStringExtra("CUSTOMER_NAME");
            customerPhone = getIntent().getStringExtra("CUSTOMER_PHONE");
            customerAddress = getIntent().getStringExtra("CUSTOMER_ADDRESS");
            cartItems = (ArrayList<CartItem>) getIntent().getSerializableExtra("CART_ITEMS");
            cartTotal = getIntent().getDoubleExtra("CART_TOTAL", 0);
            if (customerName == null || customerName.isEmpty()) {
                Toast.makeText(PaymentActivity.this, "Invoice data invalid", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            tvCustomerName.setText("Bill To: " + customerName);
            calculateTotals();
            setupSummaryRecycler();
        }

        // 🔥 5. Setup listeners
        setupListeners();
        fetchBusinessInfo();
    }
    // 🔥 NEW: Initialize views method
    private void initViews() {
        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvTax = findViewById(R.id.tvTax);
        tvGrandTotal = findViewById(R.id.tvGrandTotal);
        tvAmountPaid = findViewById(R.id.tvAmountPaid);
        tvBalance = findViewById(R.id.tvBalance);
        tvPendingAmount = findViewById(R.id.tvPendingAmount);
        etPaidAmount = findViewById(R.id.etPaidAmount);
        rgPaymentStatus = findViewById(R.id.rgPaymentStatus);
        rgPaymentMode = findViewById(R.id.rgPaymentMode);
        rbCash = findViewById(R.id.rbCash);
        rbOnline = findViewById(R.id.rbOnline);
        btnGenerateInvoice = findViewById(R.id.btnGenerateInvoice);
        rvSummary = findViewById(R.id.rvSummary);
    }

    // 🔥 NEW: Load invoice for editing
    private void loadInvoiceForEdit(String invoiceNumber) {
        tvCustomerName.setText("Loading...");
        invoicesRef.child(invoiceNumber).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Customer details
                    customerName = snapshot.child("customerName").getValue(String.class);
                    customerPhone = snapshot.child("customerPhone").getValue(String.class);
                    customerAddress = snapshot.child("customerAddress").getValue(String.class);

                    tvCustomerName.setText("Bill To: " + customerName);

                    // Financial details
                    totalTaxable = snapshot.child("totalTaxableValue").getValue(Double.class) != null ?
                            snapshot.child("totalTaxableValue").getValue(Double.class) : 0;
                    grandTotal = snapshot.child("grandTotal").getValue(Double.class) != null ?
                            snapshot.child("grandTotal").getValue(Double.class) : 0;
                    paidAmount = snapshot.child("paidAmount").getValue(Double.class) != null ?
                            snapshot.child("paidAmount").getValue(Double.class) : 0;
                    pendingAmount = grandTotal - paidAmount;
                    paymentMode = snapshot.child("paymentMode").getValue(String.class) != null ?
                            snapshot.child("paymentMode").getValue(String.class) : "Cash";

                    // Update UI
                    updateUIFromData();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PaymentActivity.this, "Load failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 🔥 NEW: Update UI with loaded data
    private void updateUIFromData() {
        tvSubtotal.setText(String.format(Locale.getDefault(), "₹%.2f", totalTaxable));
        tvGrandTotal.setText(String.format(Locale.getDefault(), "₹%.2f", grandTotal));

        // 🔥 FIX: Set ONLY numeric value (no ₹ symbol)
        etPaidAmount.setText(String.format(Locale.getDefault(), "%.2f", paidAmount));
        tvPendingAmount.setText(String.format(Locale.getDefault(), "₹%.2f", pendingAmount));

        // Set payment mode radio
        if ("Cash".equalsIgnoreCase(paymentMode)) {
            rbCash.setChecked(true);
        } else {
            rbOnline.setChecked(true);
        }

        // 🔥 Trigger calculation AFTER setting text
        etPaidAmount.post(() -> calculatePayment());
    }

    // 🔥 NEW: Setup all listeners
    private void setupListeners() {
        rgPaymentMode.setOnCheckedChangeListener((group, checkedId) -> {
            paymentMode = checkedId == R.id.rbCash ? "Cash" : "Online";
        });

        etPaidAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculatePayment();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        rgPaymentStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbFullyPaid) {
                // 🔥 FIX: Set ONLY numeric value
                etPaidAmount.setText(String.format(Locale.getDefault(), "%.2f", grandTotal));
                etPaidAmount.setEnabled(false);
            } else {
                etPaidAmount.setEnabled(true);
            }
            calculatePayment(); // Always recalculate
        });


        btnGenerateInvoice.setOnClickListener(v -> generateInvoice());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }


    private void fetchBusinessInfo() {
        infoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    businessName = snapshot.child("businessName").getValue(String.class);
                    businessGstin = snapshot.child("gstin").getValue(String.class);
                    businessAddress = snapshot.child("address").getValue(String.class);
                    if (businessName == null) businessName = "Your Business";
                    if (businessGstin == null) businessGstin = "";
                    if (businessAddress == null) businessAddress = "";
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void calculateTotals() {

        totalTaxable = 0;
        totalCGST = 0;
        totalSGST = 0;
        totalIGST = 0;

        for (CartItem item : cartItems) {
            double taxable = item.getTaxableValue();
            totalTaxable += taxable;

            if (isGstEnabled) {
                GstCalculationUtil.GstDetails gst =
                        GstCalculationUtil.calculateGst(
                                taxable,
                                item.getTaxPercent(),
                                isIntraState
                        );

                totalCGST += gst.cgst;
                totalSGST += gst.sgst;
                totalIGST += gst.igst;
            }
        }

        // ✅ Grand Total Logic
        grandTotal = totalTaxable;
        if (isGstEnabled) {
            grandTotal += totalCGST + totalSGST + totalIGST;
        }

        // UI update
        tvSubtotal.setText(String.format(Locale.getDefault(), "₹%.2f", totalTaxable));

        if (isGstEnabled) {
            tvTax.setVisibility(View.VISIBLE);
            tvTax.setText(String.format(
                    Locale.getDefault(),
                    "CGST: ₹%.2f | SGST: ₹%.2f | IGST: ₹%.2f",
                    totalCGST, totalSGST, totalIGST
            ));
        } else {
            tvTax.setVisibility(View.GONE);
        }

        tvGrandTotal.setText(String.format(Locale.getDefault(), "₹%.2f", grandTotal));

        pendingAmount = grandTotal;
        tvPendingAmount.setText(String.format(Locale.getDefault(), "₹%.2f", pendingAmount));
    }

    private void setupSummaryRecycler() {
        PaymentSummaryAdapter adapter = new PaymentSummaryAdapter(cartItems);
        rvSummary.setLayoutManager(new LinearLayoutManager(this));
        rvSummary.setAdapter(adapter);
    }

    private void calculatePayment() {
        String paidStr = etPaidAmount.getText().toString().trim();
        // 🔥 FIX: Remove ₹ symbol and parse safely
        paidStr = paidStr.replace("₹", "").replace(",", "");

        if (paidStr.isEmpty()) {
            paidAmount = 0;
        } else {
            try {
                paidAmount = Double.parseDouble(paidStr);
            } catch (NumberFormatException e) {
                paidAmount = 0;
            }
        }

        double balance = paidAmount - grandTotal;
        pendingAmount = Math.max(0, grandTotal - paidAmount);

        tvAmountPaid.setText(String.format(Locale.getDefault(), "₹%.2f", paidAmount));
        tvBalance.setText(String.format(Locale.getDefault(),
                balance >= 0 ? "₹%.2f" : "-₹%.2f", Math.abs(balance)));
        tvPendingAmount.setText(String.format(Locale.getDefault(), "₹%.2f", pendingAmount));
    }


    private void generateInvoice() {


        if (etPaidAmount.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter paid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔥 CHECK EDIT MODE FIRST
        boolean isEditMode = getIntent().getBooleanExtra("EDIT_MODE", false);
        String invoiceNumber = isEditMode ?
                getIntent().getStringExtra("INVOICE_NUMBER") : generateInvoiceNumber();

        String invoiceDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String invoiceTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        long timestamp = System.currentTimeMillis();

        // 🔥 HANDLE ITEMS - Edit mode uses existing, new uses cartItems
        ArrayList<InvoiceItem> invoiceItems;
        if (isEditMode) {
            // For edit, use stored items or skip if not loaded
            invoiceItems = new ArrayList<>(); // Load from Firebase in loadInvoiceForEdit
        } else {
            invoiceItems = new ArrayList<>();
            if (cartItems != null) {
                for (CartItem cart : cartItems) {
                    invoiceItems.add(new InvoiceItem(
                            cart.getProductId(),
                            cart.getProductName(),
                            cart.getQuantity(),
                            cart.getRate(),
                            cart.getTaxPercent()
                    ));
                }
            }
        }

        // Update paidAmount from EditText
        String paidStr = etPaidAmount.getText().toString().trim();
        paidStr = paidStr.replace("₹", "").replace(",", "");

        paidAmount = Double.parseDouble(paidStr);  // 🔥 FIX #1

//        paidAmount = paidStr.isEmpty() ? 0 : Double.parseDouble(paidStr);
        // Determine payment status
        String paymentStatus;
        if (paidAmount >= grandTotal) {
            paymentStatus = "Paid";
            pendingAmount = 0;
        } else if (paidAmount > 0) {
            paymentStatus = "Partial";
            pendingAmount = grandTotal - paidAmount;
        } else {
            paymentStatus = "Pending";
            pendingAmount = grandTotal;
        }

        // 🔥 CREATE INVOICE DATA MAP (No Invoice object needed)
        Map<String, Object> invoiceData = new HashMap<>();
        invoiceData.put("invoiceNumber", invoiceNumber);
        invoiceData.put("customerPhone", customerPhone);
        invoiceData.put("customerName", customerName);
        invoiceData.put("customerAddress", customerAddress);
        invoiceData.put("invoiceDate", invoiceDate);
        invoiceData.put("invoiceTime", invoiceTime);
        invoiceData.put("timestamp", timestamp);
        invoiceData.put("items", invoiceItems);
        invoiceData.put("totalTaxableValue", totalTaxable);
        invoiceData.put("totalCGST", totalCGST);
        invoiceData.put("totalSGST", totalSGST);
        invoiceData.put("totalIGST", totalIGST);
        invoiceData.put("gstEnabled", isGstEnabled);
        invoiceData.put("grandTotal", grandTotal);
        invoiceData.put("businessName", businessName);
        invoiceData.put("businessAddress", businessAddress);
        invoiceData.put("paidAmount", paidAmount);
        invoiceData.put("pendingAmount", pendingAmount);
        invoiceData.put("paymentStatus", paymentStatus);
        invoiceData.put("paymentMode", paymentMode);
        invoiceData.put("paymentDate", invoiceDate);

        // 🔥 SAVE/UPDATE TO FIREBASE
        invoicesRef.child(invoiceNumber).setValue(invoiceData)
                .addOnSuccessListener(aVoid -> {
                    if (isEditMode) {
                        Toast.makeText(this, "Invoice Updated Successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        // New invoice - continue with history, pending, stocks, PDF
                        addInvoiceHistoryAtCreation(invoiceNumber, paidAmount, paidAmount, pendingAmount, paymentMode);

                        if (!paymentStatus.equals("Paid")) {
                            savePendingPayment(invoiceNumber, customerName, customerPhone, grandTotal, paidAmount, pendingAmount, paymentMode);
                        }

                        updateProductStocks();

                        // Create Invoice object for PDF only
                        Invoice invoice = new Invoice(invoiceNumber, customerPhone, customerName, invoiceDate, invoiceItems, totalTaxable, totalCGST, totalSGST, totalIGST, grandTotal, businessName, businessAddress);
                        invoice.paidAmount = paidAmount;
                        invoice.pendingAmount = pendingAmount;
                        invoice.paymentStatus = paymentStatus;

                        File pdfFile = generatePdf(invoice);
                        if (pdfFile != null) {
                            showPostSaveDialog(pdfFile);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void savePendingPayment(String invoiceNumber, String customerName,
                                    String customerPhone, double totalAmount,
                                    double paidAmount, double pendingAmount,
                                    String paymentMode) {  // 🔥 NEW parameter
        DatabaseReference pendingPaymentsRef = usersRef.child("pendingPayments");

        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("invoiceNumber", invoiceNumber);
        paymentData.put("customerName", customerName);
        paymentData.put("customerPhone", customerPhone);
        paymentData.put("totalAmount", totalAmount);
        paymentData.put("paidAmount", paidAmount);
        paymentData.put("pendingAmount", pendingAmount);
        paymentData.put("paymentStatus", pendingAmount > 0 ? "Partial" : "Pending");
        paymentData.put("paymentMode", paymentMode);  // 🔥 NEW: Save payment mode
        paymentData.put("lastPaymentDate", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        paymentData.put("timestamp", System.currentTimeMillis());

        pendingPaymentsRef.child(invoiceNumber).setValue(paymentData);
    }

    private void updateProductStocks() {
        for (CartItem item : cartItems) {
            DatabaseReference productRef = usersRef.child("products").child(item.getProductId());
            productRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                    Integer currentStock = mutableData.child("stockQuantity").getValue(Integer.class);
                    if (currentStock == null) return Transaction.success(mutableData);
                    int newStock = currentStock - (int) item.getQuantity();
                    mutableData.child("stockQuantity").setValue(Math.max(0, newStock));
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {}
            });
        }
    }

    private String generateInvoiceNumber() {
        String datePart = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.getDefault());
        return "INV-" + datePart + "-" + random;
    }


    private File generatePdf(Invoice invoice) {
        try {
            File invoiceDir = new File(getFilesDir(), "Invoices");
            if (!invoiceDir.exists()) invoiceDir.mkdirs();
            File file = new File(invoiceDir, invoice.invoiceNumber + ".pdf");

            PdfDocument pdf = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Paint objects
            Paint headerPaint = new Paint();
            headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            headerPaint.setTextSize(22);
            headerPaint.setColor(0xFF1565C0); // Blue color

            Paint subHeaderPaint = new Paint();
            subHeaderPaint.setTypeface(Typeface.DEFAULT_BOLD);
            subHeaderPaint.setTextSize(14);
            subHeaderPaint.setColor(0xFF263238);

            Paint boldPaint = new Paint();
            boldPaint.setTypeface(Typeface.DEFAULT_BOLD);
            boldPaint.setTextSize(11);
            boldPaint.setColor(0xFF37474F);

            Paint regularPaint = new Paint();
            regularPaint.setTextSize(10);
            regularPaint.setColor(0xFF546E7A);

            Paint smallPaint = new Paint();
            smallPaint.setTextSize(9);
            smallPaint.setColor(0xFF78909C);

            Paint tableBorderPaint = new Paint();
            tableBorderPaint.setStyle(Paint.Style.STROKE);
            tableBorderPaint.setStrokeWidth(1);
            tableBorderPaint.setColor(0xFFCFD8DC);

            Paint tableHeaderBg = new Paint();
            tableHeaderBg.setStyle(Paint.Style.FILL);
            tableHeaderBg.setColor(0xFFE3F2FD);

            int yPos = 40;
            int margin = 40;
            int pageWidth = pageInfo.getPageWidth();
            int contentWidth = pageWidth - (2 * margin);

            // ==================== HEADER SECTION ====================
            // Business Name (Large)
            canvas.drawText(businessName, margin, yPos, headerPaint);
            yPos += 25;

            // Business Address
            if (businessAddress != null && !businessAddress.isEmpty()) {
                canvas.drawText(businessAddress, margin, yPos, regularPaint);
                yPos += 14;
            }

            // Business GSTIN
            if (businessGstin != null && !businessGstin.isEmpty()) {
                canvas.drawText("GSTIN: " + businessGstin, margin, yPos, regularPaint);
                yPos += 14;
            }

            // Add contact details if available
            canvas.drawText("Phone: " + (userMobile != null ? userMobile : ""), margin, yPos, regularPaint);
            yPos += 25;

            // Horizontal line separator
            Paint thickLine = new Paint();
            thickLine.setStrokeWidth(2);
            thickLine.setColor(0xFF1565C0);
            canvas.drawLine(margin, yPos, pageWidth - margin, yPos, thickLine);
            yPos += 20;

            // ==================== INVOICE TITLE & INFO ====================
            // "TAX INVOICE" or "INVOICE" title
            String invoiceTitle = isGstEnabled ? "TAX INVOICE" : "INVOICE";
            subHeaderPaint.setTextSize(16);
            canvas.drawText(invoiceTitle, margin, yPos, subHeaderPaint);

            // Invoice Number (right aligned)
            String invNoText = "Invoice #: " + invoice.invoiceNumber;
            subHeaderPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(invNoText, pageWidth - margin, yPos, subHeaderPaint);
            subHeaderPaint.setTextAlign(Paint.Align.LEFT);
            yPos += 18;

            // Invoice Date (right aligned)
            regularPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Date: " + invoice.invoiceDate, pageWidth - margin, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.LEFT);
            yPos += 25;

            // ==================== BILLING INFO SECTION ====================
            // Create a bordered box for billing details
            int boxTop = yPos;
            int boxHeight = 85;

            // Draw box background
            Paint boxBg = new Paint();
            boxBg.setStyle(Paint.Style.FILL);
            boxBg.setColor(0xFFFAFBFC);
            canvas.drawRect(margin, boxTop, pageWidth - margin, boxTop + boxHeight, boxBg);

            // Draw box border
            canvas.drawRect(margin, boxTop, pageWidth - margin, boxTop + boxHeight, tableBorderPaint);

            yPos = boxTop + 15;

            // "Bill To:" header
            boldPaint.setTextSize(12);
            canvas.drawText("BILL TO:", margin + 10, yPos, boldPaint);
            yPos += 18;

            // Customer Name
            regularPaint.setTextSize(11);
            regularPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(invoice.customerName, margin + 10, yPos, regularPaint);
            regularPaint.setTypeface(Typeface.DEFAULT);
            yPos += 14;

            // Customer Address
            if (customerAddress != null && !customerAddress.isEmpty()) {
                regularPaint.setTextSize(10);
                canvas.drawText(customerAddress, margin + 10, yPos, regularPaint);
                yPos += 14;
            }

            // Customer Phone
            canvas.drawText("Phone: " + customerPhone, margin + 10, yPos, regularPaint);

            yPos = boxTop + boxHeight + 25;

            // ==================== ITEMS TABLE ====================
            int tableTop = yPos;
            int tableLeft = margin;
            int tableWidth = contentWidth;

            // Column widths
            int col1Width = (int)(tableWidth * 0.40); // Item Name - 40%
            int col2Width = (int)(tableWidth * 0.15); // Quantity - 15%
            int col3Width = (int)(tableWidth * 0.20); // Rate - 20%
            int col4Width = (int)(tableWidth * 0.25); // Amount - 25%

            // Draw table header background
            canvas.drawRect(tableLeft, yPos - 15, tableLeft + tableWidth, yPos + 5, tableHeaderBg);

            // Draw table header borders
            canvas.drawLine(tableLeft, yPos - 15, tableLeft + tableWidth, yPos - 15, tableBorderPaint);
            canvas.drawLine(tableLeft, yPos + 5, tableLeft + tableWidth, yPos + 5, tableBorderPaint);
            canvas.drawLine(tableLeft, yPos - 15, tableLeft, yPos + 5, tableBorderPaint);
            canvas.drawLine(tableLeft + tableWidth, yPos - 15, tableLeft + tableWidth, yPos + 5, tableBorderPaint);

            // Table headers
            boldPaint.setTextSize(11);
            canvas.drawText("ITEM DESCRIPTION", tableLeft + 10, yPos, boldPaint);

            boldPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("QTY", tableLeft + col1Width + (col2Width / 2), yPos, boldPaint);
            canvas.drawText("RATE", tableLeft + col1Width + col2Width + (col3Width / 2), yPos, boldPaint);

            boldPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("AMOUNT", tableLeft + tableWidth - 10, yPos, boldPaint);
            boldPaint.setTextAlign(Paint.Align.LEFT);

            yPos += 5;
            int tableContentStart = yPos;

            // Vertical lines for table header
            canvas.drawLine(tableLeft + col1Width, yPos - 20, tableLeft + col1Width, yPos, tableBorderPaint);
            canvas.drawLine(tableLeft + col1Width + col2Width, yPos - 20, tableLeft + col1Width + col2Width, yPos, tableBorderPaint);
            canvas.drawLine(tableLeft + col1Width + col2Width + col3Width, yPos - 20, tableLeft + col1Width + col2Width + col3Width, yPos, tableBorderPaint);

            yPos += 15;

            // Table items
            int itemStartY = yPos;
            regularPaint.setTextSize(10);

            for (InvoiceItem item : invoice.items) {
                int rowTop = yPos - 10;
                int rowHeight = 25;

                // Draw row borders
                canvas.drawLine(tableLeft, rowTop, tableLeft + tableWidth, rowTop, tableBorderPaint);
                canvas.drawLine(tableLeft, rowTop, tableLeft, rowTop + rowHeight, tableBorderPaint);
                canvas.drawLine(tableLeft + tableWidth, rowTop, tableLeft + tableWidth, rowTop + rowHeight, tableBorderPaint);

                // Vertical separators
                canvas.drawLine(tableLeft + col1Width, rowTop, tableLeft + col1Width, rowTop + rowHeight, tableBorderPaint);
                canvas.drawLine(tableLeft + col1Width + col2Width, rowTop, tableLeft + col1Width + col2Width, rowTop + rowHeight, tableBorderPaint);
                canvas.drawLine(tableLeft + col1Width + col2Width + col3Width, rowTop, tableLeft + col1Width + col2Width + col3Width, rowTop + rowHeight, tableBorderPaint);

                // Item name
                canvas.drawText(trimText(item.productName, 35), tableLeft + 10, yPos, regularPaint);

                // Quantity (centered)
                regularPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", item.quantity),
                        tableLeft + col1Width + (col2Width / 2), yPos, regularPaint);

                // Rate (centered)
                canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", item.rate),
                        tableLeft + col1Width + col2Width + (col3Width / 2), yPos, regularPaint);

                // Amount (right aligned)
                regularPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", item.getTaxableValue()),
                        tableLeft + tableWidth - 10, yPos, regularPaint);
                regularPaint.setTextAlign(Paint.Align.LEFT);

                yPos += rowHeight;
            }

            // Bottom border of last row
            canvas.drawLine(tableLeft, yPos - 10, tableLeft + tableWidth, yPos - 10, tableBorderPaint);

            yPos += 20;

            // ==================== TOTALS SECTION ====================
            int totalsBoxWidth = 250;
            int totalsLeft = pageWidth - margin - totalsBoxWidth;
            int totalsTop = yPos;

            // Subtotal
            regularPaint.setTextSize(11);
            canvas.drawText("Subtotal:", totalsLeft, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.totalTaxableValue),
                    pageWidth - margin - 10, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.LEFT);
            yPos += 18;

            // GST Details (if enabled)
            if (isGstEnabled) {
                smallPaint.setTextSize(10);

                if (invoice.totalCGST > 0) {
                    canvas.drawText("CGST:", totalsLeft, yPos, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.totalCGST),
                            pageWidth - margin - 10, yPos, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.LEFT);
                    yPos += 16;
                }

                if (invoice.totalSGST > 0) {
                    canvas.drawText("SGST:", totalsLeft, yPos, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.totalSGST),
                            pageWidth - margin - 10, yPos, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.LEFT);
                    yPos += 16;
                }

                if (invoice.totalIGST > 0) {
                    canvas.drawText("IGST:", totalsLeft, yPos, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.totalIGST),
                            pageWidth - margin - 10, yPos, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.LEFT);
                    yPos += 16;
                }
                yPos += 5;
            }

            // Separator line
            canvas.drawLine(totalsLeft, yPos, pageWidth - margin, yPos, tableBorderPaint);
            yPos += 18;

            // Grand Total (highlighted)
            Paint grandTotalBg = new Paint();
            grandTotalBg.setStyle(Paint.Style.FILL);
            grandTotalBg.setColor(0xFFE3F2FD);
            canvas.drawRect(totalsLeft - 10, yPos - 15, pageWidth - margin, yPos + 8, grandTotalBg);

            boldPaint.setTextSize(13);
            boldPaint.setColor(0xFF1565C0);
            canvas.drawText("GRAND TOTAL:", totalsLeft, yPos, boldPaint);
            boldPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.grandTotal),
                    pageWidth - margin - 10, yPos, boldPaint);
            boldPaint.setTextAlign(Paint.Align.LEFT);
            boldPaint.setColor(0xFF37474F);
            yPos += 30;

            // ==================== PAYMENT DETAILS ====================
            regularPaint.setTextSize(11);

            canvas.drawText("Paid Amount:", totalsLeft, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.paidAmount),
                    pageWidth - margin - 10, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.LEFT);
            yPos += 16;

            canvas.drawText("Payment Mode:", totalsLeft, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(paymentMode, pageWidth - margin - 10, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.LEFT);
            yPos += 16;

            canvas.drawText("Balance Due:", totalsLeft, yPos, regularPaint);
            regularPaint.setTextAlign(Paint.Align.RIGHT);
            Paint balancePaint = new Paint(regularPaint);
            balancePaint.setColor(invoice.pendingAmount > 0 ? 0xFFE53935 : 0xFF43A047);
            balancePaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", invoice.pendingAmount),
                    pageWidth - margin - 10, yPos, balancePaint);
            regularPaint.setTextAlign(Paint.Align.LEFT);
            yPos += 16;

            // Payment Status Badge
            canvas.drawText("Status:", totalsLeft, yPos, regularPaint);

            Paint statusPaint = new Paint();
            statusPaint.setTextSize(10);
            statusPaint.setTypeface(Typeface.DEFAULT_BOLD);
            statusPaint.setTextAlign(Paint.Align.RIGHT);

            if ("Paid".equals(invoice.paymentStatus)) {
                statusPaint.setColor(0xFF43A047);
            } else if ("Partial".equals(invoice.paymentStatus)) {
                statusPaint.setColor(0xFFFB8C00);
            } else {
                statusPaint.setColor(0xFFE53935);
            }

            canvas.drawText(invoice.paymentStatus.toUpperCase(), pageWidth - margin - 10, yPos, statusPaint);

            yPos += 40;

            // ==================== TERMS & CONDITIONS ====================
//            if (yPos < 720) {
//                smallPaint.setTextSize(9);
//                smallPaint.setColor(0xFF78909C);
//                canvas.drawText("Terms & Conditions:", margin, yPos, smallPaint);
//                yPos += 14;
//
//                smallPaint.setTextSize(8);
//                canvas.drawText("1. Payment is due within 30 days of invoice date.", margin, yPos, smallPaint);
//                yPos += 12;
//                canvas.drawText("2. Please make payment to the account details provided.", margin, yPos, smallPaint);
//                yPos += 12;
//                canvas.drawText("3. For any queries, please contact us.", margin, yPos, smallPaint);
//            }

            // ==================== FOOTER ====================
            yPos = 800; // Fixed footer position

            // Footer separator line
            canvas.drawLine(margin, yPos - 10, pageWidth - margin, yPos - 10, tableBorderPaint);

            smallPaint.setTextSize(9);
            smallPaint.setColor(0xFF90A4AE);
            smallPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Thank you for your business!", pageWidth / 2, yPos, smallPaint);
            yPos += 12;

            smallPaint.setTextSize(8);
            canvas.drawText("This is a computer generated invoice and does not require a signature.",
                    pageWidth / 2, yPos, smallPaint);

            pdf.finishPage(page);
            pdf.writeTo(new FileOutputStream(file));
            pdf.close();

            return file;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "PDF generation failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }
    private String trimText(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private void showPostSaveDialog(File pdfFile) {
        CharSequence[] options = {"View PDF", "Share on WhatsApp", "Done"};

        new AlertDialog.Builder(this)
                .setTitle("Invoice Generated!")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openPdf(pdfFile);
                            break;
                        case 1:
                            shareOnWhatsApp(pdfFile);
                            break;
                        case 2:
                            navigateToHome();
                            break;
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void openPdf(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Install a PDF viewer", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareOnWhatsApp(File pdfFile) {
        String phone = customerPhone.replaceAll("[^0-9]", "");
        if (!phone.startsWith("91")) phone = "91" + phone;

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage("com.whatsapp");
        intent.putExtra("jid", phone + "@s.whatsapp.net");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, CustomerSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void addInvoiceHistoryAtCreation(String invoiceNumber,
                                             double paidNow,
                                             double totalPaid,
                                             double pendingAfter,
                                             String paymentMode) {  // 🔥 NEW parameter

        DatabaseReference historyRef = invoicesRef.child(invoiceNumber).child("history");

        String historyId = historyRef.push().getKey();
        if (historyId == null) return;

        Map<String, Object> history = new HashMap<>();
        history.put("paidNow", paidNow);
        history.put("totalPaid", totalPaid);
        history.put("pendingAfter", pendingAfter);
        history.put("paymentMode", paymentMode);  // 🔥 NEW: Save payment mode in history
        history.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        history.put("time", new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()));
        history.put("timestamp", System.currentTimeMillis());  // 🔥 FIX #3

        historyRef.child(historyId).setValue(history);
    }
}