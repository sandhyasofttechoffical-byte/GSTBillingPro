//
//package com.sandhyasofttech.gstbillingpro.Activity;
//
//import android.Manifest;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.content.pm.PackageManager;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Rect;
//import android.graphics.Typeface;
//import android.graphics.pdf.PdfDocument;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Bundle;
//import android.util.Log;
//import android.widget.ImageView;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.core.content.FileProvider;
//
//import com.google.android.material.button.MaterialButton;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//import com.sandhyasofttech.gstbillingpro.R;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.Locale;
//
//public class ShareExportActivity extends AppCompatActivity {
//
//    private static final int PERMISSION_REQUEST_CODE = 100;
//    private static final String TAG = "ShareExportActivity";
//
//    // Page dimensions (A4)
//    private static final int PAGE_WIDTH = 595;
//    private static final int PAGE_HEIGHT = 842;
//    private static final int MARGIN = 40;
//    private static final int HEADER_HEIGHT = 35;
//    private static final int ROW_HEIGHT = 30;
//    private static final int CELL_PADDING = 8;
//
//    // Colors
//    private static final int PRIMARY_COLOR = Color.rgb(41, 128, 185);
//    private static final int HEADER_BG = Color.rgb(52, 73, 94);
//    private static final int ALT_ROW_BG = Color.rgb(236, 240, 241);
//    private static final int BORDER_COLOR = Color.rgb(189, 195, 199);
//    private static final int TEXT_PRIMARY = Color.rgb(44, 62, 80);
//    private static final int TEXT_SECONDARY = Color.rgb(127, 140, 141);
//
//    private MaterialButton btnGenerateInvoicesPdf, btnGenerateCustomersPdf, btnGenerateProductsPdf;
//    private TextView tvStatus;
//    private ImageView imgBack;
//
//    private String userMobile;
//    private DatabaseReference userRef;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_share_export);
//
//        imgBack = findViewById(R.id.imgBack);
//        btnGenerateInvoicesPdf = findViewById(R.id.btnGenerateInvoicesPdf);
//        btnGenerateCustomersPdf = findViewById(R.id.btnGenerateCustomersPdf);
//        btnGenerateProductsPdf = findViewById(R.id.btnGenerateProductsPdf);
//        tvStatus = findViewById(R.id.tvStatus);
//
//        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
//        userMobile = prefs.getString("USER_MOBILE", null);
//
//        if (userMobile == null || userMobile.isEmpty()) {
//            Toast.makeText(this, "Error: User not logged in.", Toast.LENGTH_LONG).show();
//            tvStatus.setText("Cannot generate reports. Please log in again.");
//            btnGenerateInvoicesPdf.setEnabled(false);
//            btnGenerateCustomersPdf.setEnabled(false);
//            btnGenerateProductsPdf.setEnabled(false);
//            return;
//        }
//
//        userRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile);
//
//        imgBack.setOnClickListener(v -> finish());
//        btnGenerateInvoicesPdf.setOnClickListener(v -> checkPermissionAndGenerate("invoices"));
//        btnGenerateCustomersPdf.setOnClickListener(v -> checkPermissionAndGenerate("customers"));
//        btnGenerateProductsPdf.setOnClickListener(v -> checkPermissionAndGenerate("products"));
//
//        tvStatus.setText("Ready to generate PDF reports.");
//    }
//
//    private void checkPermissionAndGenerate(String dataType) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (ContextCompat.checkSelfPermission(this,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
//                tvStatus.setText("Storage permission needed. Please grant permission and press the button again.");
//                return;
//            }
//        }
//        generatePdfForType(dataType);
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                tvStatus.setText("Permission granted. You can now generate the PDF.");
//                Toast.makeText(this, "Permission granted. Please press the button again.", Toast.LENGTH_SHORT).show();
//            } else {
//                tvStatus.setText("Storage permission was denied.");
//                Toast.makeText(this, "Storage permission denied. Cannot generate PDF.", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//
//    private void generatePdfForType(String dataType) {
//        tvStatus.setText("Fetching " + dataType + " data...");
//        DatabaseReference dataRef = userRef.child(dataType);
//
//        dataRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) {
//                    tvStatus.setText("No data found for " + dataType + ".");
//                    return;
//                }
//
//                try {
//                    generatePdfDocument(dataType, snapshot);
//                } catch (Exception e) {
//                    tvStatus.setText("Error generating PDF: " + e.getMessage());
//                    Log.e(TAG, "PDF Generation Error", e);
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                tvStatus.setText("Firebase data fetch error: " + error.getMessage());
//                Log.e(TAG, "Firebase Error", error.toException());
//            }
//        });
//    }
//
//    private void generatePdfDocument(String dataType, DataSnapshot snapshot) throws IOException {
//        PdfDocument pdfDocument = new PdfDocument();
//
//        // Prepare table data
//        TableData tableData = prepareTableData(dataType, snapshot);
//
//        // Calculate optimal column widths
//        int[] columnWidths = calculateColumnWidths(tableData);
//
//        // Generate pages
//        int currentPage = 1;
//        int rowIndex = 0;
//        int totalRows = tableData.rows.size();
//
//        while (rowIndex < totalRows) {
//            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create();
//            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
//            Canvas canvas = page.getCanvas();
//
//            int y = MARGIN;
//
//            // Draw header on first page
//            if (currentPage == 1) {
//                y = drawPdfHeader(canvas, dataType);
//            } else {
//                y += 20;
//            }
//
//            // Draw table header
//            y = drawTableHeader(canvas, tableData.headers, columnWidths, y);
//
//            // Draw rows that fit on this page
//            int pageBottom = PAGE_HEIGHT - MARGIN - 60;
//            while (rowIndex < totalRows && y + ROW_HEIGHT < pageBottom) {
//                y = drawTableRow(canvas, tableData.rows.get(rowIndex), columnWidths, y, rowIndex % 2 == 0);
//                rowIndex++;
//            }
//
//            // Draw footer
//            drawPageFooter(canvas, currentPage, rowIndex, totalRows);
//
//            pdfDocument.finishPage(page);
//            currentPage++;
//        }
//
//        // Save PDF
//        savePdf(pdfDocument, dataType);
//    }
//
//    private TableData prepareTableData(String dataType, DataSnapshot snapshot) {
//        TableData tableData = new TableData();
//
//        if ("invoices".equals(dataType)) {
//            tableData.headers = new String[]{"Invoice No.", "Customer Name", "Date", "Total Amount"};
//            for (DataSnapshot ds : snapshot.getChildren()) {
//                String[] row = new String[4];
//                row[0] = getString(ds, "invoiceNumber");
//                row[1] = getString(ds, "customerName");
//                row[2] = getString(ds, "invoiceDate");
//                row[3] = formatCurrency(getDouble(ds, "grandTotal"));
//                tableData.rows.add(row);
//            }
//        } else if ("customers".equals(dataType)) {
//            tableData.headers = new String[]{"Customer Name", "Phone Number", "Email Address", "GSTIN"};
//            for (DataSnapshot ds : snapshot.getChildren()) {
//                String[] row = new String[4];
//                row[0] = getString(ds, "name");
//                row[1] = getString(ds, "phone");
//                row[2] = getString(ds, "email");
//                row[3] = getString(ds, "gstin");
//                tableData.rows.add(row);
//            }
//        } else { // products
//            tableData.headers = new String[]{"Product Name", "Price", "HSN Code", "Stock Qty"};
//            for (DataSnapshot ds : snapshot.getChildren()) {
//                String[] row = new String[4];
//                row[0] = getString(ds, "name");
//                row[1] = formatCurrency(getDouble(ds, "price"));
//                row[2] = getString(ds, "hsnCode");
//                row[3] = String.valueOf(getInt(ds, "stockQuantity"));
//                tableData.rows.add(row);
//            }
//        }
//
//        return tableData;
//    }
//
//    private int[] calculateColumnWidths(TableData tableData) {
//        int tableWidth = PAGE_WIDTH - (2 * MARGIN);
//        int numColumns = tableData.headers.length;
//        int[] widths = new int[numColumns];
//
//        // Initialize with minimum widths based on headers
//        Paint measurePaint = new Paint();
//        measurePaint.setTextSize(12f);
//
//        for (int i = 0; i < numColumns; i++) {
//            widths[i] = (int) measurePaint.measureText(tableData.headers[i]) + (2 * CELL_PADDING);
//
//            // Check all rows for maximum width needed
//            for (String[] row : tableData.rows) {
//                int textWidth = (int) measurePaint.measureText(row[i]) + (2 * CELL_PADDING);
//                if (textWidth > widths[i]) {
//                    widths[i] = textWidth;
//                }
//            }
//        }
//
//        // Adjust if total width exceeds table width
//        int totalWidth = 0;
//        for (int w : widths) totalWidth += w;
//
//        if (totalWidth > tableWidth) {
//            // Proportionally reduce all columns
//            float ratio = (float) tableWidth / totalWidth;
//            for (int i = 0; i < numColumns; i++) {
//                widths[i] = (int) (widths[i] * ratio);
//            }
//        } else {
//            // Distribute extra space proportionally
//            int extraSpace = tableWidth - totalWidth;
//            int spacePerColumn = extraSpace / numColumns;
//            for (int i = 0; i < numColumns; i++) {
//                widths[i] += spacePerColumn;
//            }
//        }
//
//        return widths;
//    }
//
//    private int drawPdfHeader(Canvas canvas, String dataType) {
//        int y = MARGIN;
//
//        // Title
//        Paint titlePaint = new Paint();
//        titlePaint.setColor(PRIMARY_COLOR);
//        titlePaint.setTextSize(28f);
//        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        titlePaint.setAntiAlias(true);
//
//        canvas.drawText("GST BILLING PRO", MARGIN, y + 30, titlePaint);
//
//        // Subtitle
//        Paint subtitlePaint = new Paint();
//        subtitlePaint.setColor(TEXT_SECONDARY);
//        subtitlePaint.setTextSize(14f);
//        subtitlePaint.setAntiAlias(true);
//
//        String reportType = capitalize(dataType) + " Report";
//        canvas.drawText(reportType, MARGIN, y + 55, subtitlePaint);
//
//        // Date and User Info
//        Paint infoPaint = new Paint();
//        infoPaint.setColor(TEXT_SECONDARY);
//        infoPaint.setTextSize(11f);
//        infoPaint.setAntiAlias(true);
//
//        String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
//        canvas.drawText("Generated: " + date, MARGIN, y + 75, infoPaint);
//        canvas.drawText("User: " + userMobile, PAGE_WIDTH - MARGIN - 150, y + 75, infoPaint);
//
//        // Horizontal line
//        Paint linePaint = new Paint();
//        linePaint.setColor(BORDER_COLOR);
//        linePaint.setStrokeWidth(2f);
//        canvas.drawLine(MARGIN, y + 85, PAGE_WIDTH - MARGIN, y + 85, linePaint);
//
//        return y + 100;
//    }
//
//    private int drawTableHeader(Canvas canvas, String[] headers, int[] columnWidths, int y) {
//        Paint bgPaint = new Paint();
//        bgPaint.setColor(HEADER_BG);
//
//        Paint textPaint = new Paint();
//        textPaint.setColor(Color.WHITE);
//        textPaint.setTextSize(12f);
//        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        textPaint.setAntiAlias(true);
//
//        Paint borderPaint = new Paint();
//        borderPaint.setColor(BORDER_COLOR);
//        borderPaint.setStrokeWidth(1.5f);
//        borderPaint.setStyle(Paint.Style.STROKE);
//
//        int tableWidth = 0;
//        for (int w : columnWidths) tableWidth += w;
//
//        // Draw header background
//        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + HEADER_HEIGHT, bgPaint);
//
//        // Draw header text
//        int x = MARGIN;
//        Rect textBounds = new Rect();
//        for (int i = 0; i < headers.length; i++) {
//            textPaint.getTextBounds(headers[i], 0, headers[i].length(), textBounds);
//            float textX = x + CELL_PADDING;
//            float textY = y + (HEADER_HEIGHT + textBounds.height()) / 2f;
//            canvas.drawText(headers[i], textX, textY, textPaint);
//
//            // Draw vertical separator
//            if (i < headers.length - 1) {
//                canvas.drawLine(x + columnWidths[i], y, x + columnWidths[i], y + HEADER_HEIGHT, borderPaint);
//            }
//
//            x += columnWidths[i];
//        }
//
//        // Draw border around header
//        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + HEADER_HEIGHT, borderPaint);
//
//        return y + HEADER_HEIGHT;
//    }
//
//    private int drawTableRow(Canvas canvas, String[] rowData, int[] columnWidths, int y, boolean alternate) {
//        Paint bgPaint = new Paint();
//        bgPaint.setColor(alternate ? ALT_ROW_BG : Color.WHITE);
//
//        Paint textPaint = new Paint();
//        textPaint.setColor(TEXT_PRIMARY);
//        textPaint.setTextSize(11f);
//        textPaint.setAntiAlias(true);
//
//        Paint borderPaint = new Paint();
//        borderPaint.setColor(BORDER_COLOR);
//        borderPaint.setStrokeWidth(1f);
//        borderPaint.setStyle(Paint.Style.STROKE);
//
//        int tableWidth = 0;
//        for (int w : columnWidths) tableWidth += w;
//
//        // Draw row background
//        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + ROW_HEIGHT, bgPaint);
//
//        // Draw cell text
//        int x = MARGIN;
//        Rect textBounds = new Rect();
//        for (int i = 0; i < rowData.length; i++) {
//            String text = rowData[i] != null ? rowData[i] : "";
//
//            // Truncate if too long
//            float maxWidth = columnWidths[i] - (2 * CELL_PADDING);
//            String displayText = truncateText(text, textPaint, maxWidth);
//
//            textPaint.getTextBounds(displayText, 0, displayText.length(), textBounds);
//            float textX = x + CELL_PADDING;
//            float textY = y + (ROW_HEIGHT + textBounds.height()) / 2f;
//
//            // Right-align currency values
//            if (displayText.startsWith("₹")) {
//                float textWidth = textPaint.measureText(displayText);
//                textX = x + columnWidths[i] - textWidth - CELL_PADDING;
//            }
//
//            canvas.drawText(displayText, textX, textY, textPaint);
//
//            // Draw vertical separator
//            if (i < rowData.length - 1) {
//                canvas.drawLine(x + columnWidths[i], y, x + columnWidths[i], y + ROW_HEIGHT, borderPaint);
//            }
//
//            x += columnWidths[i];
//        }
//
//        // Draw row borders
//        canvas.drawLine(MARGIN, y, MARGIN + tableWidth, y, borderPaint);
//        canvas.drawLine(MARGIN, y + ROW_HEIGHT, MARGIN + tableWidth, y + ROW_HEIGHT, borderPaint);
//        canvas.drawLine(MARGIN, y, MARGIN, y + ROW_HEIGHT, borderPaint);
//        canvas.drawLine(MARGIN + tableWidth, y, MARGIN + tableWidth, y + ROW_HEIGHT, borderPaint);
//
//        return y + ROW_HEIGHT;
//    }
//
//    private void drawPageFooter(Canvas canvas, int pageNum, int currentRow, int totalRows) {
//        Paint footerPaint = new Paint();
//        footerPaint.setColor(TEXT_SECONDARY);
//        footerPaint.setTextSize(10f);
//        footerPaint.setAntiAlias(true);
//
//        int y = PAGE_HEIGHT - MARGIN;
//
//        // Page number
//        String pageText = "Page " + pageNum + " | Records: " + currentRow + " of " + totalRows;
//        canvas.drawText(pageText, MARGIN, y, footerPaint);
//
//        // Footer info
//        String footerText = "© 2025 GST Billing Pro | Generated by Android App";
//        float textWidth = footerPaint.measureText(footerText);
//        canvas.drawText(footerText, PAGE_WIDTH - MARGIN - textWidth, y, footerPaint);
//    }
//
//    private void savePdf(PdfDocument pdfDocument, String dataType) throws IOException {
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        File dir = new File(getExternalFilesDir(null), "GSTBillingPDFs");
//        if (!dir.exists()) {
//            if (!dir.mkdirs()) {
//                Log.e(TAG, "Failed to create directory for PDF reports.");
//            }
//        }
//        File file = new File(dir, dataType + "_report_" + timeStamp + ".pdf");
//
//        try (FileOutputStream fos = new FileOutputStream(file)) {
//            pdfDocument.writeTo(fos);
//        }
//
//        pdfDocument.close();
//
//        tvStatus.setText("PDF saved successfully!");
//        Toast.makeText(this, capitalize(dataType) + " report generated.", Toast.LENGTH_LONG).show();
//        openPdfFile(file);
//    }
//
//    private void openPdfFile(File file) {
//        try {
//            Uri pdfUri = FileProvider.getUriForFile(this,
//                    "com.sandhyasofttech.gstbillingpro.fileprovider", file);
//
//            Intent intent = new Intent(Intent.ACTION_VIEW);
//            intent.setDataAndType(pdfUri, "application/pdf");
//            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//            startActivity(Intent.createChooser(intent, "Open PDF Report"));
//        } catch (Exception e) {
//            Toast.makeText(this, "No application found to open PDF files.", Toast.LENGTH_SHORT).show();
//            Log.e(TAG, "PDF Open Error", e);
//        }
//    }
//
//    // Helper methods
//    private String getString(DataSnapshot ds, String key) {
//        String value = ds.child(key).getValue(String.class);
//        return value != null ? value : "";
//    }
//
//    private double getDouble(DataSnapshot ds, String key) {
//        Double value = ds.child(key).getValue(Double.class);
//        return value != null ? value : 0.0;
//    }
//
//    private int getInt(DataSnapshot ds, String key) {
//        Integer value = ds.child(key).getValue(Integer.class);
//        return value != null ? value : 0;
//    }
//
//    private String formatCurrency(double amount) {
//        return String.format(Locale.getDefault(), "₹%.2f", amount);
//    }
//
//    private String truncateText(String text, Paint paint, float maxWidth) {
//        if (paint.measureText(text) <= maxWidth) {
//            return text;
//        }
//
//        String ellipsis = "...";
//        float ellipsisWidth = paint.measureText(ellipsis);
//
//        int len = text.length();
//        while (len > 0) {
//            String truncated = text.substring(0, len);
//            if (paint.measureText(truncated) + ellipsisWidth <= maxWidth) {
//                return truncated + ellipsis;
//            }
//            len--;
//        }
//        return ellipsis;
//    }
//
//    private String capitalize(String s) {
//        if (s == null || s.isEmpty()) {
//            return s;
//        }
//        return s.substring(0, 1).toUpperCase() + s.substring(1);
//    }
//
//    // Inner class to hold table data
//    private static class TableData {
//        String[] headers;
//        List<String[]> rows = new ArrayList<>();
//    }
//}





package com.sandhyasofttech.gstbillingpro.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShareExportActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "ShareExportActivity";

    // Page dimensions (A4)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int HEADER_HEIGHT = 35;
    private static final int ROW_HEIGHT = 30;
    private static final int CELL_PADDING = 8;

    // Colors - Professional Blue & Black Scheme
    private static final int PRIMARY_COLOR = Color.rgb(0, 102, 204);      // Vibrant Blue
    private static final int HEADER_BG = Color.rgb(0, 51, 153);            // Deep Blue
    private static final int ALT_ROW_BG = Color.rgb(240, 248, 255);        // Light Blue Tint
    private static final int BORDER_COLOR = Color.rgb(200, 200, 200);      // Light Gray
    private static final int TEXT_PRIMARY = Color.BLACK;                    // Pure Black
    private static final int TEXT_SECONDARY = Color.rgb(100, 100, 100);    // Dark Gray

    private MaterialButton btnGenerateInvoicesPdf, btnGenerateCustomersPdf, btnGenerateProductsPdf;
    private TextView tvStatus;
    private ImageView imgBack;

    private String userMobile;
    private DatabaseReference userRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_export);

        imgBack = findViewById(R.id.imgBack);
        btnGenerateInvoicesPdf = findViewById(R.id.btnGenerateInvoicesPdf);
        btnGenerateCustomersPdf = findViewById(R.id.btnGenerateCustomersPdf);
        btnGenerateProductsPdf = findViewById(R.id.btnGenerateProductsPdf);
        tvStatus = findViewById(R.id.tvStatus);

        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);

        if (userMobile == null || userMobile.isEmpty()) {
            Toast.makeText(this, "Error: User not logged in.", Toast.LENGTH_LONG).show();
            tvStatus.setText("Cannot generate reports. Please log in again.");
            btnGenerateInvoicesPdf.setEnabled(false);
            btnGenerateCustomersPdf.setEnabled(false);
            btnGenerateProductsPdf.setEnabled(false);
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile);

        imgBack.setOnClickListener(v -> finish());
        btnGenerateInvoicesPdf.setOnClickListener(v -> checkPermissionAndGenerate("invoices"));
        btnGenerateCustomersPdf.setOnClickListener(v -> checkPermissionAndGenerate("customers"));
        btnGenerateProductsPdf.setOnClickListener(v -> checkPermissionAndGenerate("products"));

        tvStatus.setText("Ready to generate PDF reports.");
    }

    private void checkPermissionAndGenerate(String dataType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                tvStatus.setText("Storage permission needed. Please grant permission and press the button again.");
                return;
            }
        }
        generatePdfForType(dataType);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("Permission granted. You can now generate the PDF.");
                Toast.makeText(this, "Permission granted. Please press the button again.", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("Storage permission was denied.");
                Toast.makeText(this, "Storage permission denied. Cannot generate PDF.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void generatePdfForType(String dataType) {
        tvStatus.setText("Fetching " + dataType + " data...");
        DatabaseReference dataRef = userRef.child(dataType);

        dataRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    tvStatus.setText("No data found for " + dataType + ".");
                    return;
                }

                try {
                    generatePdfDocument(dataType, snapshot);
                } catch (Exception e) {
                    tvStatus.setText("Error generating PDF: " + e.getMessage());
                    Log.e(TAG, "PDF Generation Error", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvStatus.setText("Firebase data fetch error: " + error.getMessage());
                Log.e(TAG, "Firebase Error", error.toException());
            }
        });
    }

    private void generatePdfDocument(String dataType, DataSnapshot snapshot) throws IOException {
        PdfDocument pdfDocument = new PdfDocument();

        // Prepare table data
        TableData tableData = prepareTableData(dataType, snapshot);

        // Calculate optimal column widths
        int[] columnWidths = calculateColumnWidths(tableData);

        // Generate pages
        int currentPage = 1;
        int rowIndex = 0;
        int totalRows = tableData.rows.size();

        while (rowIndex < totalRows) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = MARGIN;

            // Draw header on first page
            if (currentPage == 1) {
                y = drawPdfHeader(canvas, dataType);
            } else {
                y += 20;
            }

            // Draw table header
            y = drawTableHeader(canvas, tableData.headers, columnWidths, y);

            // Draw rows that fit on this page
            int pageBottom = PAGE_HEIGHT - MARGIN - 60;
            while (rowIndex < totalRows && y + ROW_HEIGHT < pageBottom) {
                y = drawTableRow(canvas, tableData.rows.get(rowIndex), columnWidths, y, rowIndex % 2 == 0);
                rowIndex++;
            }

            // Draw footer
            drawPageFooter(canvas, currentPage, rowIndex, totalRows);

            pdfDocument.finishPage(page);
            currentPage++;
        }

        // Save PDF
        savePdf(pdfDocument, dataType);
    }

    private TableData prepareTableData(String dataType, DataSnapshot snapshot) {
        TableData tableData = new TableData();

        if ("invoices".equals(dataType)) {
            tableData.headers = new String[]{"Invoice No.", "Customer Name", "Date", "Total Amount"};
            for (DataSnapshot ds : snapshot.getChildren()) {
                String[] row = new String[4];
                row[0] = getString(ds, "invoiceNumber");
                row[1] = getString(ds, "customerName");
                row[2] = getString(ds, "invoiceDate");
                row[3] = formatCurrency(getDouble(ds, "grandTotal"));
                tableData.rows.add(row);
            }
        } else if ("customers".equals(dataType)) {
            tableData.headers = new String[]{"Customer Name", "Phone Number", "Email Address", "GSTIN"};
            for (DataSnapshot ds : snapshot.getChildren()) {
                String[] row = new String[4];
                row[0] = getString(ds, "name");
                row[1] = getString(ds, "phone");
                row[2] = getString(ds, "email");
                row[3] = getString(ds, "gstin");
                tableData.rows.add(row);
            }
        } else { // products
            tableData.headers = new String[]{"Product Name", "Price", "HSN Code", "Stock Qty"};
            for (DataSnapshot ds : snapshot.getChildren()) {
                String[] row = new String[4];
                row[0] = getString(ds, "name");
                row[1] = formatCurrency(getDouble(ds, "price"));
                row[2] = getString(ds, "hsnCode");
                row[3] = String.valueOf(getInt(ds, "stockQuantity"));
                tableData.rows.add(row);
            }
        }

        return tableData;
    }

    private int[] calculateColumnWidths(TableData tableData) {
        int tableWidth = PAGE_WIDTH - (2 * MARGIN);
        int numColumns = tableData.headers.length;
        int[] widths = new int[numColumns];

        // Initialize with minimum widths based on headers
        Paint measurePaint = new Paint();
        measurePaint.setTextSize(12f);

        for (int i = 0; i < numColumns; i++) {
            widths[i] = (int) measurePaint.measureText(tableData.headers[i]) + (2 * CELL_PADDING);

            // Check all rows for maximum width needed
            for (String[] row : tableData.rows) {
                int textWidth = (int) measurePaint.measureText(row[i]) + (2 * CELL_PADDING);
                if (textWidth > widths[i]) {
                    widths[i] = textWidth;
                }
            }
        }

        // Adjust if total width exceeds table width
        int totalWidth = 0;
        for (int w : widths) totalWidth += w;

        if (totalWidth > tableWidth) {
            // Proportionally reduce all columns
            float ratio = (float) tableWidth / totalWidth;
            for (int i = 0; i < numColumns; i++) {
                widths[i] = (int) (widths[i] * ratio);
            }
        } else {
            // Distribute extra space proportionally
            int extraSpace = tableWidth - totalWidth;
            int spacePerColumn = extraSpace / numColumns;
            for (int i = 0; i < numColumns; i++) {
                widths[i] += spacePerColumn;
            }
        }

        return widths;
    }

    private int drawPdfHeader(Canvas canvas, String dataType) {
        int y = MARGIN;
        int centerX = PAGE_WIDTH / 2;

        // Company Title - Centered
        Paint titlePaint = new Paint();
        titlePaint.setColor(PRIMARY_COLOR);
        titlePaint.setTextSize(32f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setAntiAlias(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("श्री स्वामी समर्थ ट्रेडर्स", centerX, y + 35, titlePaint);

        // Report Type - Centered
        Paint subtitlePaint = new Paint();
        subtitlePaint.setColor(TEXT_PRIMARY);
        subtitlePaint.setTextSize(18f);
        subtitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        subtitlePaint.setAntiAlias(true);
        subtitlePaint.setTextAlign(Paint.Align.CENTER);

        String reportType = capitalize(dataType) + " Report";
        canvas.drawText(reportType, centerX, y + 65, subtitlePaint);

        // Horizontal line
        Paint linePaint = new Paint();
        linePaint.setColor(PRIMARY_COLOR);
        linePaint.setStrokeWidth(3f);
        canvas.drawLine(MARGIN + 50, y + 80, PAGE_WIDTH - MARGIN - 50, y + 80, linePaint);

        // Date and User Info - Left and Right aligned
        Paint infoPaint = new Paint();
        infoPaint.setColor(TEXT_SECONDARY);
        infoPaint.setTextSize(11f);
        infoPaint.setAntiAlias(true);

        String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        canvas.drawText("Generated: " + date, MARGIN, y + 105, infoPaint);

        infoPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("User: " + userMobile, PAGE_WIDTH - MARGIN, y + 105, infoPaint);

        return y + 125;
    }

    private int drawTableHeader(Canvas canvas, String[] headers, int[] columnWidths, int y) {
        Paint bgPaint = new Paint();
        bgPaint.setColor(HEADER_BG);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(13f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        Paint borderPaint = new Paint();
        borderPaint.setColor(HEADER_BG);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setStyle(Paint.Style.STROKE);

        int tableWidth = 0;
        for (int w : columnWidths) tableWidth += w;

        // Draw header background
        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + HEADER_HEIGHT, bgPaint);

        // Draw header text - Centered in each column
        int x = MARGIN;
        Rect textBounds = new Rect();
        for (int i = 0; i < headers.length; i++) {
            textPaint.getTextBounds(headers[i], 0, headers[i].length(), textBounds);
            float textX = x + (columnWidths[i] / 2f);
            float textY = y + (HEADER_HEIGHT + textBounds.height()) / 2f;
            canvas.drawText(headers[i], textX, textY, textPaint);

            // Draw vertical separator
            if (i < headers.length - 1) {
                Paint separatorPaint = new Paint();
                separatorPaint.setColor(Color.WHITE);
                separatorPaint.setStrokeWidth(1.5f);
                separatorPaint.setAlpha(100);
                canvas.drawLine(x + columnWidths[i], y + 5, x + columnWidths[i], y + HEADER_HEIGHT - 5, separatorPaint);
            }

            x += columnWidths[i];
        }

        // Draw border around header
        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + HEADER_HEIGHT, borderPaint);

        return y + HEADER_HEIGHT;
    }

    private int drawTableRow(Canvas canvas, String[] rowData, int[] columnWidths, int y, boolean alternate) {
        Paint bgPaint = new Paint();
        bgPaint.setColor(alternate ? ALT_ROW_BG : Color.WHITE);

        Paint textPaint = new Paint();
        textPaint.setColor(TEXT_PRIMARY);
        textPaint.setTextSize(11f);
        textPaint.setAntiAlias(true);

        Paint borderPaint = new Paint();
        borderPaint.setColor(BORDER_COLOR);
        borderPaint.setStrokeWidth(1f);
        borderPaint.setStyle(Paint.Style.STROKE);

        int tableWidth = 0;
        for (int w : columnWidths) tableWidth += w;

        // Draw row background
        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + ROW_HEIGHT, bgPaint);

        // Draw cell text
        int x = MARGIN;
        Rect textBounds = new Rect();
        for (int i = 0; i < rowData.length; i++) {
            String text = rowData[i] != null ? rowData[i] : "";

            // Truncate if too long
            float maxWidth = columnWidths[i] - (2 * CELL_PADDING);
            String displayText = truncateText(text, textPaint, maxWidth);

            textPaint.getTextBounds(displayText, 0, displayText.length(), textBounds);
            float textX = x + CELL_PADDING;
            float textY = y + (ROW_HEIGHT + textBounds.height()) / 2f;

            // Right-align currency values
            if (displayText.startsWith("₹")) {
                float textWidth = textPaint.measureText(displayText);
                textX = x + columnWidths[i] - textWidth - CELL_PADDING;
            }

            canvas.drawText(displayText, textX, textY, textPaint);

            // Draw vertical separator
            if (i < rowData.length - 1) {
                canvas.drawLine(x + columnWidths[i], y, x + columnWidths[i], y + ROW_HEIGHT, borderPaint);
            }

            x += columnWidths[i];
        }

        // Draw row borders
        canvas.drawLine(MARGIN, y, MARGIN + tableWidth, y, borderPaint);
        canvas.drawLine(MARGIN, y + ROW_HEIGHT, MARGIN + tableWidth, y + ROW_HEIGHT, borderPaint);
        canvas.drawLine(MARGIN, y, MARGIN, y + ROW_HEIGHT, borderPaint);
        canvas.drawLine(MARGIN + tableWidth, y, MARGIN + tableWidth, y + ROW_HEIGHT, borderPaint);

        return y + ROW_HEIGHT;
    }

    private void drawPageFooter(Canvas canvas, int pageNum, int currentRow, int totalRows) {
        Paint footerPaint = new Paint();
        footerPaint.setColor(TEXT_SECONDARY);
        footerPaint.setTextSize(10f);
        footerPaint.setAntiAlias(true);

        int y = PAGE_HEIGHT - MARGIN;

        // Page number
        String pageText = "Page " + pageNum + " | Records: " + currentRow + " of " + totalRows;
        canvas.drawText(pageText, MARGIN, y, footerPaint);

        // Footer info
        String footerText = "© 2025 श्री स्वामी समर्थ ट्रेडर्स | Generated by Android App";
        float textWidth = footerPaint.measureText(footerText);
        canvas.drawText(footerText, PAGE_WIDTH - MARGIN - textWidth, y, footerPaint);
    }

    private void savePdf(PdfDocument pdfDocument, String dataType) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File dir = new File(getExternalFilesDir(null), "GSTBillingPDFs");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create directory for PDF reports.");
            }
        }
        File file = new File(dir, dataType + "_report_" + timeStamp + ".pdf");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            pdfDocument.writeTo(fos);
        }

        pdfDocument.close();

        tvStatus.setText("PDF saved successfully!");
        Toast.makeText(this, capitalize(dataType) + " report generated.", Toast.LENGTH_LONG).show();
        openPdfFile(file);
    }

    private void openPdfFile(File file) {
        try {
            Uri pdfUri = FileProvider.getUriForFile(this,
                    "com.sandhyasofttech.gstbillingpro.fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "Open PDF Report"));
        } catch (Exception e) {
            Toast.makeText(this, "No application found to open PDF files.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "PDF Open Error", e);
        }
    }

    // Helper methods
    private String getString(DataSnapshot ds, String key) {
        String value = ds.child(key).getValue(String.class);
        return value != null ? value : "";
    }

    private double getDouble(DataSnapshot ds, String key) {
        Double value = ds.child(key).getValue(Double.class);
        return value != null ? value : 0.0;
    }

    private int getInt(DataSnapshot ds, String key) {
        Integer value = ds.child(key).getValue(Integer.class);
        return value != null ? value : 0;
    }

    private String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "₹%.2f", amount);
    }

    private String truncateText(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = paint.measureText(ellipsis);

        int len = text.length();
        while (len > 0) {
            String truncated = text.substring(0, len);
            if (paint.measureText(truncated) + ellipsisWidth <= maxWidth) {
                return truncated + ellipsis;
            }
            len--;
        }
        return ellipsis;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // Inner class to hold table data
    private static class TableData {
        String[] headers;
        List<String[]> rows = new ArrayList<>();
    }
}