package com.sandhyasofttech.gstbillingpro.custmore;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.R;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CustomerDetailsActivity extends AppCompatActivity {

    private static final String TAG = "CustomerDetails";
    private static final int PERMISSION_CALL = 101;

    // Professional Color Scheme
    private static final Color COLOR_PRIMARY = new DeviceRgb(33, 33, 33);        // Dark Gray
    private static final Color COLOR_SECONDARY = new DeviceRgb(66, 66, 66);      // Medium Gray
    private static final Color COLOR_LIGHT_BG = new DeviceRgb(245, 245, 245);    // Light Gray
    private static final Color COLOR_BORDER = new DeviceRgb(200, 200, 200);      // Border Gray
    private static final Color COLOR_TABLE_HEADER = new DeviceRgb(240, 240, 240); // Table Header
    private static final Color COLOR_TEXT = ColorConstants.BLACK;
    private static final Color COLOR_TEXT_LIGHT = new DeviceRgb(100, 100, 100);

    // Views
    private TextView tvName, tvPhone, tvEmail, tvGstin, tvAddress;
    private MaterialButton btnCall, btnWhatsApp, btnEdit, btnDelete, btnExportPdf;
    private RecyclerView rvProducts, rvInvoices;

    // Portfolio views
    private TextView tvTotalInvoices, tvPaidCount, tvUnpaidCount, tvAmountSummary;

    // Data
    private String customerPhone, customerName, userMobile;
    private DatabaseReference userRef, invoicesRef;

    private final List<String> productNames = new ArrayList<>();
    private final List<InvoiceSummary> invoiceList = new ArrayList<>();
    private ProductAdapter productAdapter;
    private InvoiceAdapter invoiceAdapter;

    // Counters
    private int totalInvoices = 0, paidCount = 0, partialCount = 0, unpaidCount = 0;
    private double totalAmount = 0, totalPaid = 0, totalPending = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_details);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        initViews();
        getUserMobile();
        setupFirebase();
        loadCustomerData();
        setupToolbar();
        setupButtons();
        setupRecyclers();
        loadRelatedData();
    }

    private void initViews() {
        tvName = findViewById(R.id.tvDetailName);
        tvPhone = findViewById(R.id.tvDetailPhone);
        tvEmail = findViewById(R.id.tvDetailEmail);
        tvGstin = findViewById(R.id.tvDetailGstin);
        tvAddress = findViewById(R.id.tvDetailAddress);
        btnCall = findViewById(R.id.btnCall);
        btnWhatsApp = findViewById(R.id.btnWhatsApp);
        btnEdit = findViewById(R.id.btnEdit);
        btnDelete = findViewById(R.id.btnDelete);
        btnExportPdf = findViewById(R.id.btnExportPdf);
        rvProducts = findViewById(R.id.rvProducts);
        rvInvoices = findViewById(R.id.rvInvoices);

        tvTotalInvoices = findViewById(R.id.tvTotalInvoices);
        tvPaidCount = findViewById(R.id.tvPaidCount);
        tvUnpaidCount = findViewById(R.id.tvUnpaidCount);
        tvAmountSummary = findViewById(R.id.tvAmountSummary);
    }

    private void getUserMobile() {
        userMobile = getSharedPreferences("APP_PREFS", MODE_PRIVATE).getString("USER_MOBILE", null);
        if (userMobile == null) {
            Toast.makeText(this, "Session expired", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupFirebase() {
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile);
        invoicesRef = userRef.child("invoices");
    }

    private void loadCustomerData() {
        customerName = getIntent().getStringExtra("customer_name");
        customerPhone = getIntent().getStringExtra("customer_phone");
        String email = getIntent().getStringExtra("customer_email");
        String gstin = getIntent().getStringExtra("customer_gstin");
        String address = getIntent().getStringExtra("customer_address");

        tvName.setText(customerName != null ? customerName : "N/A");
        tvPhone.setText(customerPhone != null ? customerPhone : "N/A");
        tvEmail.setText(email != null && !email.isEmpty() ? email : "Not provided");
        tvGstin.setText(gstin != null && !gstin.isEmpty() ? "GSTIN: " + gstin : "GSTIN: Not provided");
        tvAddress.setText(address != null && !address.isEmpty() ? address : "Address not provided");
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Customer Information");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupButtons() {
        btnCall.setOnClickListener(v -> makeCall());
        btnWhatsApp.setOnClickListener(v -> openWhatsApp());
        btnEdit.setOnClickListener(v -> editCustomer());
        btnDelete.setOnClickListener(v -> deleteCustomer());
        btnExportPdf.setOnClickListener(v -> exportToPdf());
    }

    private void makeCall() {
        if (customerPhone == null) return;
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + customerPhone));
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CALL_PHONE}, PERMISSION_CALL);
        }
    }

    private void openWhatsApp() {
        if (customerPhone == null) return;
        String phone = customerPhone.replaceAll("[^0-9]", "");
        String url = "https://api.whatsapp.com/send?phone=91" + phone;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void editCustomer() {
        Intent intent = new Intent(this, AddCustomerActivity.class);
        intent.putExtra(AddCustomerActivity.EXTRA_IS_EDIT, true);
        intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_ID, customerPhone);
        intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_NAME, customerName);
        intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_PHONE, customerPhone);
        intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_EMAIL, tvEmail.getText().toString());
        intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_GSTIN, tvGstin.getText().toString().replace("GSTIN: ", ""));
        intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_ADDRESS, tvAddress.getText().toString());
        startActivity(intent);
        finish();
    }

    private void deleteCustomer() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Customer")
                .setMessage("Delete " + customerName + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    userRef.child("customers").child(customerPhone).removeValue()
                            .addOnSuccessListener(a -> {
                                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

//    private void exportToPdf() {
//        try {
//            String businessName = getBusinessName();
//            String fileName = customerName.replaceAll("[^a-zA-Z0-9]", "_") + "_Portfolio.pdf";
//
//            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
//            PdfWriter writer = new PdfWriter(file);
//            PdfDocument pdf = new PdfDocument(writer);
//            pdf.setDefaultPageSize(PageSize.A4);
//
//            // Add header and footer event handler
//            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new HeaderFooterEventHandler(businessName));
//
//            Document doc = new Document(pdf);
//            doc.setMargins(80, 40, 70, 40); // top, right, bottom, left
//
//            PdfFont fontRegular = PdfFontFactory.createFont("Helvetica");
//            PdfFont fontBold = PdfFontFactory.createFont("Helvetica-Bold");
//
//            String nowDate = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
//            String nowTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
//
//            // ═══════════════════════════════════════════════════════════
//            // DOCUMENT TITLE
//            // ═══════════════════════════════════════════════════════════
//            doc.add(new Paragraph("CUSTOMER PORTFOLIO REPORT")
//                    .setFont(fontBold)
//                    .setFontSize(20)
//                    .setFontColor(COLOR_PRIMARY)
//                    .setTextAlignment(TextAlignment.CENTER)
//                    .setMarginBottom(5));
//
//            doc.add(new Paragraph("Generated on " + nowDate + " at " + nowTime)
//                    .setFont(fontRegular)
//                    .setFontSize(9)
//                    .setFontColor(COLOR_TEXT_LIGHT)
//                    .setTextAlignment(TextAlignment.CENTER)
//                    .setMarginBottom(20));
//
//            // Divider line
//            SolidLine line = new SolidLine(1f);
//            line.setColor(COLOR_BORDER);
//            doc.add(new LineSeparator(line).setMarginBottom(20));
//
//            // ═══════════════════════════════════════════════════════════
//            // SECTION 1: CUSTOMER INFORMATION
//            // ═══════════════════════════════════════════════════════════
//            addSectionHeader(doc, "CUSTOMER INFORMATION", fontBold);
//
//            Table custTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
//                    .useAllAvailableWidth()
//                    .setMarginBottom(20);
//
//            addInfoRow(custTable, "Customer Name", customerName != null ? customerName : "N/A", fontBold, fontRegular);
//            addInfoRow(custTable, "Phone Number", customerPhone != null ? customerPhone : "N/A", fontBold, fontRegular);
//            addInfoRow(custTable, "Email Address", tvEmail.getText().toString(), fontBold, fontRegular);
//            addInfoRow(custTable, "GSTIN", tvGstin.getText().toString().replace("GSTIN: ", ""), fontBold, fontRegular);
//            addInfoRow(custTable, "Address", tvAddress.getText().toString(), fontBold, fontRegular);
//
//            doc.add(custTable);
//
//            // ═══════════════════════════════════════════════════════════
//            // SECTION 2: PORTFOLIO SUMMARY
//            // ═══════════════════════════════════════════════════════════
//            addSectionHeader(doc, "PORTFOLIO SUMMARY", fontBold);
//
//            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}))
//                    .useAllAvailableWidth()
//                    .setMarginBottom(15);
//
//            // Header Row
//            summaryTable.addCell(createHeaderCell("Total Invoices", fontBold));
//            summaryTable.addCell(createHeaderCell("Paid Invoices", fontBold));
//            summaryTable.addCell(createHeaderCell("Pending Invoices", fontBold));
//            summaryTable.addCell(createHeaderCell("Total Amount", fontBold));
//
//            // Data Row 1
//            summaryTable.addCell(createDataCell(String.valueOf(totalInvoices), fontRegular));
//            summaryTable.addCell(createDataCell(String.valueOf(paidCount), fontRegular));
//            summaryTable.addCell(createDataCell(String.valueOf(unpaidCount), fontRegular));
//            summaryTable.addCell(createDataCell("₹" + formatAmount(totalAmount), fontRegular));
//
//            // Header Row 2
//            summaryTable.addCell(createHeaderCell("Total Paid", fontBold));
//            summaryTable.addCell(createHeaderCell("Total Pending", fontBold));
//            summaryTable.addCell(createHeaderCell("Average Invoice", fontBold));
//            summaryTable.addCell(createHeaderCell("Collection Rate", fontBold));
//
//            // Data Row 2
//            summaryTable.addCell(createDataCell("₹" + formatAmount(totalPaid), fontRegular));
//            summaryTable.addCell(createDataCell("₹" + formatAmount(totalPending), fontRegular));
//
//            double avgInvoice = totalInvoices > 0 ? totalAmount / totalInvoices : 0;
//            summaryTable.addCell(createDataCell("₹" + formatAmount(avgInvoice), fontRegular));
//
//            double collectionRate = totalAmount > 0 ? (totalPaid / totalAmount * 100) : 0;
//            summaryTable.addCell(createDataCell(String.format("%.1f%%", collectionRate), fontRegular));
//
//            doc.add(summaryTable);
//
//            // ═══════════════════════════════════════════════════════════
//            // SECTION 3: PRODUCTS PURCHASED
//            // ═══════════════════════════════════════════════════════════
//            if (!productNames.isEmpty()) {
//                addSectionHeader(doc, "PRODUCTS PURCHASED", fontBold);
//
//                Table prodTable = new Table(UnitValue.createPercentArray(new float[]{5, 95}))
//                        .useAllAvailableWidth()
//                        .setMarginBottom(20);
//
//                int counter = 1;
//                for (String productName : productNames) {
//                    prodTable.addCell(createProductNumberCell(String.valueOf(counter++), fontRegular));
//                    prodTable.addCell(createProductNameCell(productName, fontRegular));
//                }
//
//                doc.add(prodTable);
//            }
//
//            // ═══════════════════════════════════════════════════════════
//            // SECTION 4: INVOICE DETAILS - FIXED TABLE FORMAT
//            // ═══════════════════════════════════════════════════════════
//            if (!invoiceList.isEmpty()) {
//                addSectionHeader(doc, "INVOICE DETAILS", fontBold);
//
//                // Create table with proper column widths
//                float[] columnWidths = {10f, 20f, 18f, 17f, 17f, 18f};
//                Table invTable = new Table(UnitValue.createPercentArray(columnWidths))
//                        .useAllAvailableWidth()
//                        .setMarginBottom(20);
//
//                // Add Table Headers with proper border
//                Cell headerSr = new Cell()
//                        .add(new Paragraph("Sr."))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(ColorConstants.WHITE)
//                        .setBackgroundColor(COLOR_PRIMARY)
//                        .setTextAlignment(TextAlignment.CENTER)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
//                        .setPadding(10);
//                invTable.addHeaderCell(headerSr);
//
//                Cell headerInvoice = new Cell()
//                        .add(new Paragraph("Invoice No."))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(ColorConstants.WHITE)
//                        .setBackgroundColor(COLOR_PRIMARY)
//                        .setTextAlignment(TextAlignment.CENTER)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
//                        .setPadding(10);
//                invTable.addHeaderCell(headerInvoice);
//
//                Cell headerDate = new Cell()
//                        .add(new Paragraph("Date"))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(ColorConstants.WHITE)
//                        .setBackgroundColor(COLOR_PRIMARY)
//                        .setTextAlignment(TextAlignment.CENTER)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
//                        .setPadding(10);
//                invTable.addHeaderCell(headerDate);
//
//                Cell headerAmount = new Cell()
//                        .add(new Paragraph("Amount"))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(ColorConstants.WHITE)
//                        .setBackgroundColor(COLOR_PRIMARY)
//                        .setTextAlignment(TextAlignment.CENTER)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
//                        .setPadding(10);
//                invTable.addHeaderCell(headerAmount);
//
//                Cell headerPaid = new Cell()
//                        .add(new Paragraph("Paid"))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(ColorConstants.WHITE)
//                        .setBackgroundColor(COLOR_PRIMARY)
//                        .setTextAlignment(TextAlignment.CENTER)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
//                        .setPadding(10);
//                invTable.addHeaderCell(headerPaid);
//
//                Cell headerBalance = new Cell()
//                        .add(new Paragraph("Balance"))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(ColorConstants.WHITE)
//                        .setBackgroundColor(COLOR_PRIMARY)
//                        .setTextAlignment(TextAlignment.CENTER)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
//                        .setPadding(10);
//                invTable.addHeaderCell(headerBalance);
//
//                // Initialize totals
//                double tableTotalAmount = 0;
//                double tableTotalPaid = 0;
//                double tableTotalBalance = 0;
//                int srNo = 1;
//
//                // Add data rows with consistent styling
//                for (InvoiceSummary invoice : invoiceList) {
//                    double invoiceTotal = invoice.total;
//                    double invoicePaid = invoice.paidAmount;
//                    double invoiceBalance = invoice.pendingAmount;
//
//                    // Accumulate totals
//                    tableTotalAmount += invoiceTotal;
//                    tableTotalPaid += invoicePaid;
//                    tableTotalBalance += invoiceBalance;
//
//                    // Sr. No Cell
//                    Cell cellSr = new Cell()
//                            .add(new Paragraph(String.valueOf(srNo++)))
//                            .setFont(fontRegular)
//                            .setFontSize(9)
//                            .setFontColor(COLOR_TEXT)
//                            .setTextAlignment(TextAlignment.CENTER)
//                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
//                            .setPadding(8);
//                    invTable.addCell(cellSr);
//
//                    // Invoice Number Cell
//                    Cell cellInvoice = new Cell()
//                            .add(new Paragraph(invoice.number != null ? invoice.number : "N/A"))
//                            .setFont(fontRegular)
//                            .setFontSize(9)
//                            .setFontColor(COLOR_TEXT)
//                            .setTextAlignment(TextAlignment.LEFT)
//                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
//                            .setPaddingLeft(8)
//                            .setPaddingRight(5)
//                            .setPaddingTop(8)
//                            .setPaddingBottom(8);
//                    invTable.addCell(cellInvoice);
//
//                    // Date Cell
//                    Cell cellDate = new Cell()
//                            .add(new Paragraph(invoice.date != null ? invoice.date : "N/A"))
//                            .setFont(fontRegular)
//                            .setFontSize(9)
//                            .setFontColor(COLOR_TEXT)
//                            .setTextAlignment(TextAlignment.LEFT)
//                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
//                            .setPaddingLeft(8)
//                            .setPaddingRight(5)
//                            .setPaddingTop(8)
//                            .setPaddingBottom(8);
//                    invTable.addCell(cellDate);
//
//                    // Amount Cell
//                    Cell cellAmount = new Cell()
//                            .add(new Paragraph("₹" + formatAmount(invoiceTotal)))
//                            .setFont(fontRegular)
//                            .setFontSize(9)
//                            .setFontColor(COLOR_TEXT)
//                            .setTextAlignment(TextAlignment.RIGHT)
//                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
//                            .setPaddingLeft(5)
//                            .setPaddingRight(8)
//                            .setPaddingTop(8)
//                            .setPaddingBottom(8);
//                    invTable.addCell(cellAmount);
//
//                    // Paid Cell
//                    Cell cellPaid = new Cell()
//                            .add(new Paragraph("₹" + formatAmount(invoicePaid)))
//                            .setFont(fontRegular)
//                            .setFontSize(9)
//                            .setFontColor(COLOR_TEXT)
//                            .setTextAlignment(TextAlignment.RIGHT)
//                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
//                            .setPaddingLeft(5)
//                            .setPaddingRight(8)
//                            .setPaddingTop(8)
//                            .setPaddingBottom(8);
//                    invTable.addCell(cellPaid);
//
//                    // Balance Cell
//                    Cell cellBalance = new Cell()
//                            .add(new Paragraph("₹" + formatAmount(invoiceBalance)))
//                            .setFont(fontRegular)
//                            .setFontSize(9)
//                            .setFontColor(COLOR_TEXT)
//                            .setTextAlignment(TextAlignment.RIGHT)
//                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
//                            .setPaddingLeft(5)
//                            .setPaddingRight(8)
//                            .setPaddingTop(8)
//                            .setPaddingBottom(8);
//                    invTable.addCell(cellBalance);
//                }
//
//                // Add Total Row with proper styling
//                Cell totalSr = new Cell()
//                        .add(new Paragraph(""))
//                        .setFont(fontBold)
//                        .setFontSize(10)
////                        .setBackgroundColor(COLOR_LIGHT_BG)
////                        .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
//                        .setPadding(10);
//                invTable.addCell(totalSr);
//
//                Cell totalInvoice = new Cell()
//                        .add(new Paragraph(""))
//                        .setFont(fontBold)
//                        .setFontSize(10)
////                        .setBackgroundColor(COLOR_LIGHT_BG)
////                        .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
//                        .setPadding(10);
//                invTable.addCell(totalInvoice);
//
//                Cell totalLabel = new Cell()
//                        .add(new Paragraph("TOTAL"))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(COLOR_PRIMARY)
//                        .setBackgroundColor(COLOR_LIGHT_BG)
//                        .setTextAlignment(TextAlignment.RIGHT)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
//                        .setPaddingRight(8)
//                        .setPadding(10);
//                invTable.addCell(totalLabel);
//
//                Cell totalAmount = new Cell()
//                        .add(new Paragraph("₹" + formatAmount(tableTotalAmount)))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(COLOR_PRIMARY)
//                        .setBackgroundColor(COLOR_LIGHT_BG)
//                        .setTextAlignment(TextAlignment.RIGHT)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
//                        .setPaddingRight(8)
//                        .setPadding(10);
//                invTable.addCell(totalAmount);
//
//                Cell totalPaid = new Cell()
//                        .add(new Paragraph("₹" + formatAmount(tableTotalPaid)))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(COLOR_PRIMARY)
//                        .setBackgroundColor(COLOR_LIGHT_BG)
//                        .setTextAlignment(TextAlignment.RIGHT)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
//                        .setPaddingRight(8)
//                        .setPadding(10);
//                invTable.addCell(totalPaid);
//
//                Cell totalBalance = new Cell()
//                        .add(new Paragraph("₹" + formatAmount(tableTotalBalance)))
//                        .setFont(fontBold)
//                        .setFontSize(10)
//                        .setFontColor(COLOR_PRIMARY)
//                        .setBackgroundColor(COLOR_LIGHT_BG)
//                        .setTextAlignment(TextAlignment.RIGHT)
//                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
//                        .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
//                        .setPaddingRight(8)
//                        .setPadding(10);
//                invTable.addCell(totalBalance);
//
//                doc.add(invTable);
//            }
//
//            // Add end note
//            doc.add(new Paragraph("\n"));
//            SolidLine endLine = new SolidLine(0.5f);
//            endLine.setColor(COLOR_BORDER);
//            doc.add(new LineSeparator(endLine).setMarginTop(10).setMarginBottom(10));
//            doc.add(new Paragraph("This is a computer-generated document and does not require a signature.")
//                    .setFont(fontRegular)
//                    .setFontSize(8)
//                    .setFontColor(COLOR_TEXT_LIGHT)
//                    .setTextAlignment(TextAlignment.CENTER)
//                    .setItalic());
//
//            doc.close();
//
//            // Share PDF
//            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
//            Intent share = new Intent(Intent.ACTION_SEND);
//            share.setType("application/pdf");
//            share.putExtra(Intent.EXTRA_STREAM, uri);
//            share.putExtra(Intent.EXTRA_SUBJECT, "Customer Portfolio - " + customerName);
//            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//            startActivity(Intent.createChooser(share, "Share Customer Portfolio"));
//
//            Toast.makeText(this, "PDF generated successfully!", Toast.LENGTH_SHORT).show();
//
//        } catch (Exception e) {
//            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
//            Log.e(TAG, "PDF Generation Error", e);
//        }
//    }
    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS FOR PDF STYLING
    // ═══════════════════════════════════════════════════════════════════




// ═══════════════════════════════════════════════════════════════════
// UPDATED PDF EXPORT METHOD WITH BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════

private File generatedPdfFile = null;

    private void exportToPdf() {
        // Generate PDF first
        try {
            String businessName = getBusinessName();
            String fileName = customerName.replaceAll("[^a-zA-Z0-9]", "_") + "_Portfolio.pdf";

            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);

            // Add header and footer event handler
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new HeaderFooterEventHandler(businessName));

            Document doc = new Document(pdf);
            doc.setMargins(80, 40, 70, 40); // top, right, bottom, left

            PdfFont fontRegular = PdfFontFactory.createFont("Helvetica");
            PdfFont fontBold = PdfFontFactory.createFont("Helvetica-Bold");

            String nowDate = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
            String nowTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

            // ═══════════════════════════════════════════════════════════
            // DOCUMENT TITLE
            // ═══════════════════════════════════════════════════════════
            doc.add(new Paragraph("CUSTOMER PORTFOLIO REPORT")
                    .setFont(fontBold)
                    .setFontSize(20)
                    .setFontColor(COLOR_PRIMARY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5));

            doc.add(new Paragraph("Generated on " + nowDate + " at " + nowTime)
                    .setFont(fontRegular)
                    .setFontSize(9)
                    .setFontColor(COLOR_TEXT_LIGHT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            // Divider line
            SolidLine line = new SolidLine(1f);
            line.setColor(COLOR_BORDER);
            doc.add(new LineSeparator(line).setMarginBottom(20));

            // ═══════════════════════════════════════════════════════════
            // SECTION 1: CUSTOMER INFORMATION
            // ═══════════════════════════════════════════════════════════
            addSectionHeader(doc, "CUSTOMER INFORMATION", fontBold);

            Table custTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                    .useAllAvailableWidth()
                    .setMarginBottom(20);

            addInfoRow(custTable, "Customer Name", customerName != null ? customerName : "N/A", fontBold, fontRegular);
            addInfoRow(custTable, "Phone Number", customerPhone != null ? customerPhone : "N/A", fontBold, fontRegular);
            addInfoRow(custTable, "Email Address", tvEmail.getText().toString(), fontBold, fontRegular);
            addInfoRow(custTable, "GSTIN", tvGstin.getText().toString().replace("GSTIN: ", ""), fontBold, fontRegular);
            addInfoRow(custTable, "Address", tvAddress.getText().toString(), fontBold, fontRegular);

            doc.add(custTable);

            // ═══════════════════════════════════════════════════════════
            // SECTION 2: PORTFOLIO SUMMARY
            // ═══════════════════════════════════════════════════════════
            addSectionHeader(doc, "PORTFOLIO SUMMARY", fontBold);

            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);

            // Header Row
            summaryTable.addCell(createHeaderCell("Total Invoices", fontBold));
            summaryTable.addCell(createHeaderCell("Paid Invoices", fontBold));
            summaryTable.addCell(createHeaderCell("Pending Invoices", fontBold));
            summaryTable.addCell(createHeaderCell("Total Amount", fontBold));

            // Data Row 1
            summaryTable.addCell(createDataCell(String.valueOf(totalInvoices), fontRegular));
            summaryTable.addCell(createDataCell(String.valueOf(paidCount), fontRegular));
            summaryTable.addCell(createDataCell(String.valueOf(unpaidCount), fontRegular));
            summaryTable.addCell(createDataCell("₹" + formatAmount(totalAmount), fontRegular));

            // Header Row 2
            summaryTable.addCell(createHeaderCell("Total Paid", fontBold));
            summaryTable.addCell(createHeaderCell("Total Pending", fontBold));
            summaryTable.addCell(createHeaderCell("Average Invoice", fontBold));
            summaryTable.addCell(createHeaderCell("Collection Rate", fontBold));

            // Data Row 2
            summaryTable.addCell(createDataCell("₹" + formatAmount(totalPaid), fontRegular));
            summaryTable.addCell(createDataCell("₹" + formatAmount(totalPending), fontRegular));

            double avgInvoice = totalInvoices > 0 ? totalAmount / totalInvoices : 0;
            summaryTable.addCell(createDataCell("₹" + formatAmount(avgInvoice), fontRegular));

            double collectionRate = totalAmount > 0 ? (totalPaid / totalAmount * 100) : 0;
            summaryTable.addCell(createDataCell(String.format("%.1f%%", collectionRate), fontRegular));

            doc.add(summaryTable);

            // ═══════════════════════════════════════════════════════════
            // SECTION 3: PRODUCTS PURCHASED
            // ═══════════════════════════════════════════════════════════
            if (!productNames.isEmpty()) {
                addSectionHeader(doc, "PRODUCTS PURCHASED", fontBold);

                Table prodTable = new Table(UnitValue.createPercentArray(new float[]{5, 95}))
                        .useAllAvailableWidth()
                        .setMarginBottom(20);

                int counter = 1;
                for (String productName : productNames) {
                    prodTable.addCell(createProductNumberCell(String.valueOf(counter++), fontRegular));
                    prodTable.addCell(createProductNameCell(productName, fontRegular));
                }

                doc.add(prodTable);
            }

            // ═══════════════════════════════════════════════════════════
            // SECTION 4: INVOICE DETAILS
            // ═══════════════════════════════════════════════════════════
            if (!invoiceList.isEmpty()) {
                addSectionHeader(doc, "INVOICE DETAILS", fontBold);

                // Create table with proper column widths
                float[] columnWidths = {10f, 20f, 18f, 17f, 17f, 18f};
                Table invTable = new Table(UnitValue.createPercentArray(columnWidths))
                        .useAllAvailableWidth()
                        .setMarginBottom(20);

                // Add Table Headers
                Cell headerSr = new Cell()
                        .add(new Paragraph("Sr."))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
                        .setPadding(10);
                invTable.addHeaderCell(headerSr);

                Cell headerInvoice = new Cell()
                        .add(new Paragraph("Invoice No."))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
                        .setPadding(10);
                invTable.addHeaderCell(headerInvoice);

                Cell headerDate = new Cell()
                        .add(new Paragraph("Date"))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
                        .setPadding(10);
                invTable.addHeaderCell(headerDate);

                Cell headerAmount = new Cell()
                        .add(new Paragraph("Amount"))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
                        .setPadding(10);
                invTable.addHeaderCell(headerAmount);

                Cell headerPaid = new Cell()
                        .add(new Paragraph("Paid"))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
                        .setPadding(10);
                invTable.addHeaderCell(headerPaid);

                Cell headerBalance = new Cell()
                        .add(new Paragraph("Balance"))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(ColorConstants.WHITE, 1f))
                        .setPadding(10);
                invTable.addHeaderCell(headerBalance);

                // Initialize totals
                double tableTotalAmount = 0;
                double tableTotalPaid = 0;
                double tableTotalBalance = 0;
                int srNo = 1;

                // Add data rows
                for (InvoiceSummary invoice : invoiceList) {
                    double invoiceTotal = invoice.total;
                    double invoicePaid = invoice.paidAmount;
                    double invoiceBalance = invoice.pendingAmount;

                    tableTotalAmount += invoiceTotal;
                    tableTotalPaid += invoicePaid;
                    tableTotalBalance += invoiceBalance;

                    // Sr. No Cell
                    Cell cellSr = new Cell()
                            .add(new Paragraph(String.valueOf(srNo++)))
                            .setFont(fontRegular)
                            .setFontSize(9)
                            .setFontColor(COLOR_TEXT)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setPadding(8);
                    invTable.addCell(cellSr);

                    // Invoice Number Cell
                    Cell cellInvoice = new Cell()
                            .add(new Paragraph(invoice.number != null ? invoice.number : "N/A"))
                            .setFont(fontRegular)
                            .setFontSize(9)
                            .setFontColor(COLOR_TEXT)
                            .setTextAlignment(TextAlignment.LEFT)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setPaddingLeft(8)
                            .setPaddingRight(5)
                            .setPaddingTop(8)
                            .setPaddingBottom(8);
                    invTable.addCell(cellInvoice);

                    // Date Cell
                    Cell cellDate = new Cell()
                            .add(new Paragraph(invoice.date != null ? invoice.date : "N/A"))
                            .setFont(fontRegular)
                            .setFontSize(9)
                            .setFontColor(COLOR_TEXT)
                            .setTextAlignment(TextAlignment.LEFT)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setPaddingLeft(8)
                            .setPaddingRight(5)
                            .setPaddingTop(8)
                            .setPaddingBottom(8);
                    invTable.addCell(cellDate);

                    // Amount Cell
                    Cell cellAmount = new Cell()
                            .add(new Paragraph("₹" + formatAmount(invoiceTotal)))
                            .setFont(fontRegular)
                            .setFontSize(9)
                            .setFontColor(COLOR_TEXT)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setPaddingLeft(5)
                            .setPaddingRight(8)
                            .setPaddingTop(8)
                            .setPaddingBottom(8);
                    invTable.addCell(cellAmount);

                    // Paid Cell
                    Cell cellPaid = new Cell()
                            .add(new Paragraph("₹" + formatAmount(invoicePaid)))
                            .setFont(fontRegular)
                            .setFontSize(9)
                            .setFontColor(COLOR_TEXT)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setPaddingLeft(5)
                            .setPaddingRight(8)
                            .setPaddingTop(8)
                            .setPaddingBottom(8);
                    invTable.addCell(cellPaid);

                    // Balance Cell
                    Cell cellBalance = new Cell()
                            .add(new Paragraph("₹" + formatAmount(invoiceBalance)))
                            .setFont(fontRegular)
                            .setFontSize(9)
                            .setFontColor(COLOR_TEXT)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setPaddingLeft(5)
                            .setPaddingRight(8)
                            .setPaddingTop(8)
                            .setPaddingBottom(8);
                    invTable.addCell(cellBalance);
                }

                // Add Total Row
                Cell totalSr = new Cell()
                        .add(new Paragraph(""))
                        .setFont(fontRegular)
                        .setFontSize(10)
                        .setBackgroundColor(COLOR_LIGHT_BG)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPadding(10);
                invTable.addCell(totalSr);

                Cell totalInvoice = new Cell()
                        .add(new Paragraph(""))
                        .setFont(fontRegular)
                        .setFontSize(10)
                        .setBackgroundColor(COLOR_LIGHT_BG)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPadding(10);
                invTable.addCell(totalInvoice);

                Cell totalLabel = new Cell()
                        .add(new Paragraph("TOTAL"))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY)
                        .setBackgroundColor(COLOR_LIGHT_BG)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPaddingRight(8)
                        .setPadding(10);
                invTable.addCell(totalLabel);

                Cell totalAmount = new Cell()
                        .add(new Paragraph("₹" + formatAmount(tableTotalAmount)))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY)
                        .setBackgroundColor(COLOR_LIGHT_BG)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPaddingRight(8)
                        .setPadding(10);
                invTable.addCell(totalAmount);

                Cell totalPaid = new Cell()
                        .add(new Paragraph("₹" + formatAmount(tableTotalPaid)))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY)
                        .setBackgroundColor(COLOR_LIGHT_BG)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPaddingRight(8)
                        .setPadding(10);
                invTable.addCell(totalPaid);

                Cell totalBalance = new Cell()
                        .add(new Paragraph("₹" + formatAmount(tableTotalBalance)))
                        .setFont(fontBold)
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY)
                        .setBackgroundColor(COLOR_LIGHT_BG)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPaddingRight(8)
                        .setPadding(10);
                invTable.addCell(totalBalance);

                doc.add(invTable);
            }

            // Add end note
            doc.add(new Paragraph("\n"));
            SolidLine endLine = new SolidLine(0.5f);
            endLine.setColor(COLOR_BORDER);
            doc.add(new LineSeparator(endLine).setMarginTop(10).setMarginBottom(10));
            doc.add(new Paragraph("This is a computer-generated document and does not require a signature.")
                    .setFont(fontRegular)
                    .setFontSize(8)
                    .setFontColor(COLOR_TEXT_LIGHT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setItalic());

            doc.close();

            // Store the generated file
            generatedPdfFile = file;

            Toast.makeText(this, "PDF generated successfully!", Toast.LENGTH_SHORT).show();

            // Show bottom sheet with sharing options
            showPdfShareBottomSheet();

        } catch (Exception e) {
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "PDF Generation Error", e);
        }
    }

// ═══════════════════════════════════════════════════════════════════
// BOTTOM SHEET FOR PDF SHARING OPTIONS
// ═══════════════════════════════════════════════════════════════════


    private void showPdfShareBottomSheet() {
        if (generatedPdfFile == null || !generatedPdfFile.exists()) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create bottom sheet dialog
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_pdf_share, null);
        bottomSheet.setContentView(view);

        // Get views from bottom sheet
        TextView tvTitle = view.findViewById(R.id.tvBottomSheetTitle);
        MaterialButton btnWhatsApp = view.findViewById(R.id.btnShareWhatsApp);
        MaterialButton btnEmail = view.findViewById(R.id.btnShareEmail);
        MaterialButton btnOthers = view.findViewById(R.id.btnShareOthers);
        MaterialButton btnView = view.findViewById(R.id.btnViewPdf);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        tvTitle.setText("Share Customer Portfolio");

        // WhatsApp Share
        btnWhatsApp.setOnClickListener(v -> {
            shareViaWhatsApp();
            bottomSheet.dismiss();
        });

        // Email Share
        btnEmail.setOnClickListener(v -> {
            shareViaEmail();
            bottomSheet.dismiss();
        });

        // Other Apps Share
        btnOthers.setOnClickListener(v -> {
            shareViaOtherApps();
            bottomSheet.dismiss();
        });

        // View PDF
        btnView.setOnClickListener(v -> {
            viewPdf();
            bottomSheet.dismiss();
        });

        // Cancel
        btnCancel.setOnClickListener(v -> bottomSheet.dismiss());

        bottomSheet.show();
    }

// ═══════════════════════════════════════════════════════════════════
// SHARING METHODS
// ═══════════════════════════════════════════════════════════════════

    private void shareViaWhatsApp() {
        if (generatedPdfFile == null) return;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", generatedPdfFile);

            // Prepare message text
            String messageText = "📄 *Customer Portfolio Report*\n\n" +
                    "Customer: " + customerName + "\n" +
                    "📊 Total Invoices: " + totalInvoices + "\n" +
                    "💰 Total Amount: ₹" + String.format("%,.2f", totalAmount) + "\n" +
                    "✅ Paid: ₹" + String.format("%,.2f", totalPaid) + "\n" +
                    "⏳ Pending: ₹" + String.format("%,.2f", totalPending);

            // If customer has phone number, open chat directly with attachment
            if (customerPhone != null && !customerPhone.isEmpty()) {
                String phone = customerPhone.replaceAll("[^0-9]", "");

                // Remove leading zeros if any
                if (phone.startsWith("0")) {
                    phone = phone.substring(1);
                }

                // Add country code if not present (assuming India +91)
                if (!phone.startsWith("91") && phone.length() == 10) {
                    phone = "91" + phone;
                }

                try {
                    // Method 1: Try to open chat with attachment using ACTION_SEND
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("application/pdf");
                    sendIntent.setPackage("com.whatsapp");
                    sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, messageText);
                    sendIntent.putExtra("jid", phone + "@s.whatsapp.net");
                    sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(sendIntent);
                    Toast.makeText(this, "Opening chat with " + customerName, Toast.LENGTH_SHORT).show();

                } catch (Exception e1) {
                    try {
                        // Method 2: Open chat first, then user manually shares
                        Intent chatIntent = new Intent(Intent.ACTION_VIEW);
                        String url = "https://wa.me/" + phone + "?text=" + Uri.encode(messageText);
                        chatIntent.setData(Uri.parse(url));
                        chatIntent.setPackage("com.whatsapp");
                        startActivity(chatIntent);

                        // Show instruction to share PDF
                        new AlertDialog.Builder(this)
                                .setTitle("Share PDF")
                                .setMessage("Chat opened! Now tap the attachment icon (📎) to share the PDF from your device's Documents folder:\n\n" + generatedPdfFile.getName())
                                .setPositiveButton("OK", null)
                                .show();

                    } catch (Exception e2) {
                        // Method 3: Fallback to share picker
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("application/pdf");
                        shareIntent.setPackage("com.whatsapp");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent.putExtra(Intent.EXTRA_TEXT, messageText);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "Share to WhatsApp Contact"));
                    }
                }

            } else {
                // No phone number, use standard share
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/pdf");
                shareIntent.setPackage("com.whatsapp");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, messageText);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share to WhatsApp Contact"));
            }

        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "WhatsApp not found", e);
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing to WhatsApp: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "WhatsApp share error", e);
        }
    }

    private void shareViaEmail() {
        if (generatedPdfFile == null) return;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", generatedPdfFile);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Customer Portfolio - " + customerName);

            String emailBody = "Dear " + customerName + ",\n\n" +
                    "Please find attached your portfolio report.\n\n" +
                    "Summary:\n" +
                    "• Total Invoices: " + totalInvoices + "\n" +
                    "• Total Amount: ₹" + String.format("%,.2f", totalAmount) + "\n" +
                    "• Paid Amount: ₹" + String.format("%,.2f", totalPaid) + "\n" +
                    "• Pending Amount: ₹" + String.format("%,.2f", totalPending) + "\n\n" +
                    "Best regards";

            intent.putExtra(Intent.EXTRA_TEXT, emailBody);

            // Pre-fill customer email if available
            String customerEmail = tvEmail.getText().toString();
            if (customerEmail != null && !customerEmail.isEmpty() && !customerEmail.equals("Not provided")) {
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{customerEmail});
            }

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Send Email"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing via email", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Email share error", e);
        }
    }

    private void shareViaOtherApps() {
        if (generatedPdfFile == null) return;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", generatedPdfFile);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Customer Portfolio - " + customerName);
            intent.putExtra(Intent.EXTRA_TEXT, "Customer Portfolio Report for " + customerName);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "Share PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing PDF", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Share error", e);
        }
    }

    private void viewPdf() {
        if (generatedPdfFile == null) return;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", generatedPdfFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No PDF viewer found. Please install a PDF reader app.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "View PDF error", e);
        }
    }

    private void addSectionHeader(Document doc, String title, PdfFont font) {
        doc.add(new Paragraph(title)
                .setFont(font)
                .setFontSize(12)
                .setFontColor(COLOR_PRIMARY)
                .setBackgroundColor(COLOR_LIGHT_BG)
                .setPadding(8)
                .setMarginTop(5)
                .setMarginBottom(10));
    }

    private void addInfoRow(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        if (value == null || value.isEmpty() || value.equals("Not provided")) {
            value = "—";
        }

        table.addCell(new Cell()
                .add(new Paragraph(label))
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(COLOR_SECONDARY)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPaddingTop(8)
                .setPaddingBottom(8)
                .setPaddingLeft(5));

        table.addCell(new Cell()
                .add(new Paragraph(value))
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(COLOR_TEXT)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPaddingTop(8)
                .setPaddingBottom(8)
                .setPaddingLeft(10));
    }

    private Cell createHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text))
                .setFont(font)
                .setFontSize(10)
                .setFontColor(COLOR_PRIMARY)
                .setBackgroundColor(COLOR_TABLE_HEADER)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(COLOR_BORDER, 1f))
                .setPadding(8);
    }

    private Cell createDataCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text))
                .setFont(font)
                .setFontSize(10)
                .setFontColor(COLOR_TEXT)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8);
    }

    private Cell createProductNumberCell(String number, PdfFont font) {
        return new Cell()
                .add(new Paragraph(number + "."))
                .setFont(font)
                .setFontSize(10)
                .setFontColor(COLOR_SECONDARY)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPaddingTop(6)
                .setPaddingBottom(6)
                .setPaddingLeft(5)
                .setTextAlignment(TextAlignment.RIGHT);
    }

    private Cell createProductNameCell(String name, PdfFont font) {
        return new Cell()
                .add(new Paragraph(name))
                .setFont(font)
                .setFontSize(10)
                .setFontColor(COLOR_TEXT)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPaddingTop(6)
                .setPaddingBottom(6)
                .setPaddingLeft(10);
    }

    private Cell createInvoiceHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text))
                .setFont(font)
                .setFontSize(10)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(COLOR_PRIMARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setBorder(Border.NO_BORDER)
                .setPadding(10);
    }

    private Cell createInvoiceDataCell(String text, PdfFont font, TextAlignment alignment) {
        return new Cell()
                .add(new Paragraph(text))
                .setFont(font)
                .setFontSize(9)
                .setFontColor(COLOR_TEXT)
                .setTextAlignment(alignment)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPaddingTop(8)
                .setPaddingBottom(8)
                .setPaddingLeft(5)
                .setPaddingRight(5);
    }

    private Cell createInvoiceTotalCell(String text, PdfFont font, TextAlignment alignment) {
        return new Cell()
                .add(new Paragraph(text))
                .setFont(font)
                .setFontSize(10)
                .setFontColor(COLOR_PRIMARY)
                .setBackgroundColor(COLOR_LIGHT_BG)
                .setTextAlignment(alignment)
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setPadding(10);
    }

    private String formatAmount(double amount) {
        return String.format("%,.2f", amount);
    }

    private String getBusinessName() {
        // Try to get from SharedPreferences or Firebase
        String name = getSharedPreferences("APP_PREFS", MODE_PRIVATE)
                .getString("BUSINESS_NAME", "श्री स्वामी समर्थ ट्रेडर्स");
        return name;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HEADER AND FOOTER EVENT HANDLER
    // ═══════════════════════════════════════════════════════════════════

    private class HeaderFooterEventHandler implements IEventHandler {
        private final String businessName;

        public HeaderFooterEventHandler(String businessName) {
            this.businessName = businessName;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            int pageNumber = pdfDoc.getPageNumber(docEvent.getPage());
            Rectangle pageSize = docEvent.getPage().getPageSize();

            PdfCanvas canvas = new PdfCanvas(docEvent.getPage());

            try {
                PdfFont fontBold = PdfFontFactory.createFont("Helvetica-Bold");
                PdfFont fontRegular = PdfFontFactory.createFont("Helvetica");

                // ═══════ HEADER ═══════
                float headerY = pageSize.getTop() - 30;

                // Business Name (Left)
                canvas.beginText()
                        .setFontAndSize(fontBold, 11)
                        .setColor(COLOR_PRIMARY, true)
                        .moveText(40, headerY)
                        .showText(businessName)
                        .endText();

                // Page Number (Right)
                String pageText = "Page " + pageNumber;
                float pageTextWidth = fontRegular.getWidth(pageText, 9);
                canvas.beginText()
                        .setFontAndSize(fontRegular, 9)
                        .setColor(COLOR_TEXT_LIGHT, true)
                        .moveText(pageSize.getRight() - 40 - pageTextWidth, headerY)
                        .showText(pageText)
                        .endText();

                // Header Line
                canvas.setStrokeColor(COLOR_BORDER)
                        .setLineWidth(0.5f)
                        .moveTo(40, headerY - 8)
                        .lineTo(pageSize.getRight() - 40, headerY - 8)
                        .stroke();

                // ═══════ FOOTER ═══════
                float footerY = 35;

                // Footer Text
                String footerText = "Customer Portfolio Report | Generated by " + businessName;
                float footerTextWidth = fontRegular.getWidth(footerText, 8);
                float footerX = (pageSize.getWidth() - footerTextWidth) / 2;

                // Footer Line
                canvas.setStrokeColor(COLOR_BORDER)
                        .setLineWidth(0.5f)
                        .moveTo(40, footerY + 15)
                        .lineTo(pageSize.getRight() - 40, footerY + 15)
                        .stroke();

                canvas.beginText()
                        .setFontAndSize(fontRegular, 8)
                        .setColor(COLOR_TEXT_LIGHT, true)
                        .moveText(footerX, footerY)
                        .showText(footerText)
                        .endText();

                canvas.release();

            } catch (Exception e) {
                Log.e(TAG, "Header/Footer error: ", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RECYCLER VIEW SETUP AND ADAPTERS
    // ═══════════════════════════════════════════════════════════════════

    private void setupRecyclers() {
        productAdapter = new ProductAdapter(productNames);
        rvProducts.setLayoutManager(new LinearLayoutManager(this));
        rvProducts.setAdapter(productAdapter);

        invoiceAdapter = new InvoiceAdapter(invoiceList, id -> {
            Intent intent = new Intent(this, InvoiceDetailsActivity.class);
            intent.putExtra("invoice_id", id);
            startActivity(intent);
        });
        rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        rvInvoices.setAdapter(invoiceAdapter);
    }

    private void loadRelatedData() {
        loadProductsUsed();
        loadInvoices();
    }

    private void loadProductsUsed() {
        invoicesRef.addValueEventListener(new ValueEventListener() {
            final Set<String> unique = new HashSet<>();

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                unique.clear();
                for (DataSnapshot inv : snapshot.getChildren()) {
                    String custId = inv.child("customerId").getValue(String.class);
                    String custName = inv.child("customerName").getValue(String.class);
                    if ((customerPhone != null && customerPhone.equals(custId)) ||
                            (customerName != null && customerName.equals(custName))) {
                        for (DataSnapshot item : inv.child("items").getChildren()) {
                            String name = item.child("productName").getValue(String.class);
                            if (name != null) unique.add(name.trim());
                        }
                    }
                }
                productNames.clear();
                productNames.addAll(unique);
                Collections.sort(productNames);
                productAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError e) {
            }
        });
    }

    private void loadInvoices() {
        invoicesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                invoiceList.clear();
                totalInvoices = 0;
                paidCount = 0;
                unpaidCount = 0;
                totalAmount = 0;
                totalPaid = 0;
                totalPending = 0;

                for (DataSnapshot inv : snapshot.getChildren()) {
                    String custId = inv.child("customerId").getValue(String.class);
                    String custName = inv.child("customerName").getValue(String.class);

                    if ((customerPhone != null && customerPhone.equals(custId)) ||
                            (customerName != null && customerName.equals(custName))) {

                        String no = inv.child("invoiceNumber").getValue(String.class);
                        String date = inv.child("invoiceDate").getValue(String.class);
                        Double total = inv.child("grandTotal").getValue(Double.class);
                        Double paid = inv.child("paidAmount").getValue(Double.class);

                        Double pending = getPendingAmount(inv, total, paid);
                        String status = inv.child("paymentStatus").getValue(String.class);
                        String id = inv.getKey();

                        if (no != null && date != null && total != null && id != null) {
                            double paidAmt = (paid != null) ? paid.doubleValue() : 0;
                            double pendingAmt = (pending != null) ? pending.doubleValue() : 0;

                            invoiceList.add(new InvoiceSummary(no, date, total, paidAmt, pendingAmt, id));

                            totalInvoices++;
                            totalAmount += total.doubleValue();
                            if (paid != null) totalPaid += paid.doubleValue();
                            if (pending != null && pending > 0) totalPending += pending.doubleValue();

                            if (isPaidInvoice(total, paid, status)) {
                                paidCount++;
                            } else if (pending != null && pending > 0) {
                                unpaidCount++;
                            }
                        }
                    }
                }

                updatePortfolioUI();
                invoiceAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError e) {
            }
        });
    }

    private Double getPendingAmount(DataSnapshot inv, Double total, Double paid) {
        Double pending = inv.child("pendingAmount").getValue(Double.class);
        if (pending == null) pending = inv.child("pending").getValue(Double.class);
        if (pending == null && total != null && paid != null) {
            pending = total - paid;
        }
        return pending;
    }

    private boolean isPaidInvoice(Double total, Double paid, String status) {
        if ("Paid".equalsIgnoreCase(status)) return true;
        if (total != null && paid != null && paid >= total) return true;
        return false;
    }

    private void updatePortfolioUI() {
        if (tvTotalInvoices == null || tvAmountSummary == null) return;

        tvTotalInvoices.setText(String.valueOf(totalInvoices));
        tvPaidCount.setText(String.valueOf(paidCount));
        tvUnpaidCount.setText(String.valueOf(unpaidCount));

        String summary = "Total: ₹" + String.format("%,.0f", totalAmount) +
                " | Paid: ₹" + String.format("%,.0f", totalPaid) +
                " | Pending: ₹" + String.format("%,.0f", totalPending);
        tvAmountSummary.setText(summary);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES - ADAPTERS
    // ═══════════════════════════════════════════════════════════════════

    static class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<String> list;

        ProductAdapter(List<String> list) {
            this.list = list;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(
                    android.view.LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_product_simple, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int pos) {
            ((TextView) holder.itemView.findViewById(R.id.tvProductName)).setText("• " + list.get(pos));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    static class InvoiceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<InvoiceSummary> list;
        final OnClick click;

        interface OnClick {
            void onClick(String id);
        }

        InvoiceAdapter(List<InvoiceSummary> list, OnClick click) {
            this.list = list;
            this.click = click;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_invoice_summary, parent, false);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int pos) {
            InvoiceSummary i = list.get(pos);
            ((TextView) holder.itemView.findViewById(R.id.tvInvoiceNo)).setText(i.number);
            ((TextView) holder.itemView.findViewById(R.id.tvInvoiceDate)).setText(i.date);
            ((TextView) holder.itemView.findViewById(R.id.tvInvoiceTotal)).setText("₹" + String.format("%,.0f", i.total));
            holder.itemView.setOnClickListener(v -> click.onClick(i.id));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    static class InvoiceSummary {
        String number, date, id;
        double total, paidAmount, pendingAmount;

        InvoiceSummary(String n, String d, double t, String id) {
            this(n, d, t, 0, t, id);
        }

        InvoiceSummary(String n, String d, double t, double paid, double pending, String id) {
            number = n;
            date = d;
            total = t;
            paidAmount = paid;
            pendingAmount = pending;
            this.id = id;
        }
    }
}