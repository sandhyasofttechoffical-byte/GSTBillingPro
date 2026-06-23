package com.sandhyasofttech.gstbillingpro.Activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CustomerReportActivity extends AppCompatActivity {

    private static final String TAG = "CustomerReportActivity";

    // ── Page constants (A4) ───────────────────────────────────────
    private static final int PW = 595, PH = 842, M = 36;
    private static final int ROW_H = 22;

    // ── Brand colors ──────────────────────────────────────────────
    private static final int C_BLUE      = Color.rgb(25,  90,  180);
    private static final int C_BLUE_DARK = Color.rgb(12,  45,  110);
    private static final int C_DARK      = Color.rgb(10,  33,  55);
    private static final int C_GRAY      = Color.rgb(90,  112, 136);
    private static final int C_BORDER    = Color.rgb(210, 220, 235);
    private static final int C_ALT       = Color.rgb(246, 249, 255);
    private static final int C_RED       = Color.rgb(185, 28,  28);
    private static final int C_GREEN     = Color.rgb(21,  110, 50);
    private static final int C_ORANGE    = Color.rgb(190, 75,  0);
    private static final int C_WHITE     = Color.WHITE;

    // ── Views ─────────────────────────────────────────────────────
    private TextView tvStatus;
    private TextView tvDateRange;

    // ── Data ──────────────────────────────────────────────────────
    private DatabaseReference userRef;
    private String userMobile;
    private String bizName = "", bizAddress = "", bizGstin = "",
            bizPhone = "", bizOwner = "", bizType = "";

    // ── Date filter ───────────────────────────────────────────────
    private String filterFrom = "";   // "yyyy-MM-dd" or ""
    private String filterTo   = "";

    private final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat SDF_DISPLAY = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_report);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }

        tvStatus    = findViewById(R.id.tvStatus);
        tvDateRange = findViewById(R.id.tvDateRange);

        findViewById(R.id.imgBack).setOnClickListener(v -> finish());

        // Date filter buttons
        findViewById(R.id.btnFromDate).setOnClickListener(v -> pickDate(true));
        findViewById(R.id.btnToDate).setOnClickListener(v -> pickDate(false));
        findViewById(R.id.btnClearFilter).setOnClickListener(v -> clearDateFilter());

        // Report cards
        findViewById(R.id.cardAllInvoices).setOnClickListener(v ->
                fetchBizInfoThen(() -> generateReport("invoices")));
        findViewById(R.id.cardPendingReport).setOnClickListener(v ->
                fetchBizInfoThen(() -> generateReport("pendingPayments")));
        findViewById(R.id.cardCompletedReport).setOnClickListener(v ->
                fetchBizInfoThen(() -> generateReport("completedPayments")));
        findViewById(R.id.cardCustomerStatement).setOnClickListener(v ->
                fetchBizInfoThen(this::pickCustomerStatement));
        findViewById(R.id.cardCustomerPending).setOnClickListener(v ->
                fetchBizInfoThen(this::pickCustomerPending));

        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);
        if (userMobile == null) { finish(); return; }
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile);

        updateDateLabel();
    }

    // ══════════════════════════════════════════════════════════════
    //  DATE FILTER
    // ══════════════════════════════════════════════════════════════
    private void pickDate(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            String date = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m+1, d);
            if (isFrom) filterFrom = date;
            else        filterTo   = date;
            updateDateLabel();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void clearDateFilter() {
        filterFrom = ""; filterTo = "";
        updateDateLabel();
        setStatus("Date filter cleared — showing all records");
    }

    private void updateDateLabel() {
        if (filterFrom.isEmpty() && filterTo.isEmpty()) {
            tvDateRange.setText("All dates");
        } else {
            String from = filterFrom.isEmpty() ? "Start" : fmtDate(filterFrom);
            String to   = filterTo.isEmpty()   ? "Today" : fmtDate(filterTo);
            tvDateRange.setText(from + "  →  " + to);
        }
    }

    private String fmtDate(String raw) {
        try { return SDF_DISPLAY.format(SDF.parse(raw)); }
        catch (ParseException e) { return raw; }
    }

    /** Returns true if the given "yyyy-MM-dd" string falls within the chosen range */
    private boolean inRange(String dateStr) {
        if (filterFrom.isEmpty() && filterTo.isEmpty()) return true;
        try {
            Date d    = SDF.parse(dateStr);
            Date from = filterFrom.isEmpty() ? null : SDF.parse(filterFrom);
            Date to   = filterTo.isEmpty()   ? null : SDF.parse(filterTo);
            if (from != null && d.before(from)) return false;
            if (to   != null && d.after(to))   return false;
            return true;
        } catch (ParseException e) { return true; }
    }

    // ══════════════════════════════════════════════════════════════
    //  BIZ INFO FETCH (once)
    // ══════════════════════════════════════════════════════════════
    private void fetchBizInfoThen(Runnable action) {
        if (!bizName.isEmpty()) { action.run(); return; }
        setStatus("Loading business info…");
        userRef.child("info").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                bizName    = safeStr(s, "businessName");
                bizAddress = safeStr(s, "address");
                bizGstin   = safeStr(s, "gstin");
                bizPhone   = safeStr(s, "mobile");
                bizOwner   = safeStr(s, "ownerName");
                bizType    = safeStr(s, "businessType");
                if (bizName.isEmpty()) bizName = "Your Business";
                action.run();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                setStatus("Error: " + e.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  SIMPLE REPORT GENERATOR
    // ══════════════════════════════════════════════════════════════
    private void generateReport(String node) {
        setStatus("Fetching data…");
        userRef.child(node).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) { setStatus("No data found."); return; }
                try {
                    File pdf = buildSimplePdf(node, snap);
                    setStatus("PDF ready!");
                    showOptions(pdf);
                } catch (Exception e) {
                    setStatus("Error: " + e.getMessage());
                    Log.e(TAG, "PDF error", e);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                setStatus("Error: " + e.getMessage());
            }
        });
    }

    private File buildSimplePdf(String node, DataSnapshot snap) throws IOException {

        // ── Define columns and extract rows ───────────────────────
        String   reportTitle;
        String[] headers;
        List<String[]> rows = new ArrayList<>();
        int accentColor;

        switch (node) {
            case "invoices":
                reportTitle = "All Invoices Report";
                accentColor = C_BLUE;
                // EXACT COLUMN ORDER — headers[i] maps to row[i]
                headers = new String[]{
                        "Invoice No.", "Customer Name", "Date",
                        "Grand Total", "Paid", "Pending", "Status"
                };
                for (DataSnapshot ds : snap.getChildren()) {
                    String dateStr = safeStr(ds, "invoiceDate");
                    if (!inRange(dateStr)) continue;
                    // row[i] MUST match headers[i] exactly
                    rows.add(new String[]{
                            safeStr(ds, "invoiceNumber"),   // 0 Invoice No.
                            safeStr(ds, "customerName"),    // 1 Customer Name
                            dateStr,                        // 2 Date
                            cur(safeD(ds, "grandTotal")),   // 3 Grand Total
                            cur(safeD(ds, "paidAmount")),   // 4 Paid
                            cur(safeD(ds, "pendingAmount")),// 5 Pending
                            safeStr(ds, "paymentStatus")    // 6 Status
                    });
                }
                break;

            case "pendingPayments":
                reportTitle = "Pending Payments Report";
                accentColor = C_ORANGE;
                headers = new String[]{
                        "Invoice No.", "Customer Name", "Phone",
                        "Total Amount", "Paid", "Due Amount", "Last Date"
                };
                for (DataSnapshot ds : snap.getChildren()) {
                    String dateStr = safeStr(ds, "lastPaymentDate");
                    if (!inRange(dateStr)) continue;
                    rows.add(new String[]{
                            safeStr(ds, "invoiceNumber"),      // 0
                            safeStr(ds, "customerName"),       // 1
                            safeStr(ds, "customerPhone"),      // 2
                            cur(safeD(ds, "totalAmount")),     // 3
                            cur(safeD(ds, "paidAmount")),      // 4
                            cur(safeD(ds, "pendingAmount")),   // 5
                            dateStr                            // 6
                    });
                }
                break;

            default: // completedPayments
                reportTitle = "Completed Payments Report";
                accentColor = C_GREEN;
                headers = new String[]{
                        "Invoice No.", "Customer Name",
                        "Total Amount", "Paid Amount", "Completion Date", "Status"
                };
                for (DataSnapshot ds : snap.getChildren()) {
                    String dateStr = safeStr(ds, "completionDate");
                    if (!inRange(dateStr)) continue;
                    rows.add(new String[]{
                            safeStr(ds, "invoiceNumber"),    // 0
                            safeStr(ds, "customerName"),     // 1
                            cur(safeD(ds, "totalAmount")),   // 2
                            cur(safeD(ds, "paidAmount")),    // 3
                            dateStr,                         // 4
                            safeStr(ds, "paymentStatus")     // 5
                    });
                }
                break;
        }

        if (rows.isEmpty()) {
            setStatus("No records match the selected date range.");
            return null;
        }

        // ── Calculate FIXED column widths matching header count ───
        // This is the key fix: colW[i] is computed from headers[i]
        // and all row cells at index i — never mismatches
        int[] colW = calcColWidths(headers, rows, PW - 2 * M);

        // ── Build PDF ─────────────────────────────────────────────
        PdfDocument pdf  = new PdfDocument();
        int pageNum = 1, ri = 0, total = rows.size();

        while (ri < total) {
            PdfDocument.Page pg = startPage(pdf, pageNum);
            Canvas c = pg.getCanvas();
            int y = (pageNum == 1)
                    ? drawHeader(c, reportTitle, accentColor)
                    : M + 10;

            // Summary line on first page
            if (pageNum == 1) {
                y = drawReportMeta(c, total, y);
            }

            y = drawTableHeader(c, headers, colW, y, accentColor);

            int bottom = PH - M - 45;
            while (ri < total && y + ROW_H <= bottom) {
                y = drawRow(c, rows.get(ri), colW, y, ri % 2 == 0, accentColor);
                ri++;
            }

            drawFooter(c, pageNum, ri, total);
            pdf.finishPage(pg);
            pageNum++;
        }

        return savePdf(pdf, node);
    }

    // ══════════════════════════════════════════════════════════════
    //  CUSTOMER STATEMENT PDF
    // ══════════════════════════════════════════════════════════════
    private void pickCustomerStatement() {
        setStatus("Loading invoices…");
        userRef.child("invoices").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                LinkedHashMap<String, List<DataSnapshot>> map = groupByCustomer(snap, "customerName", "invoiceDate");
                if (map.isEmpty()) { setStatus("No invoices found."); return; }
                showCustomerPicker(map, (name, list) -> {
                    try {
                        File pdf = buildStatementPdf(name, list);
                        if (pdf != null) { setStatus("Statement ready!"); showOptions(pdf); }
                    } catch (IOException e) { setStatus("Error: " + e.getMessage()); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                setStatus("Error: " + e.getMessage());
            }
        });
    }

    private File buildStatementPdf(String customerName,
                                   List<DataSnapshot> invoices) throws IOException {

        // ── Exact headers — MUST match row array indexes ──────────
        String[] headers = {
                "#", "Invoice No.", "Date", "Total", "Paid", "Pending", "Mode", "Status"
        };
        //                  0      1           2       3       4       5         6       7

        List<String[]> rows = new ArrayList<>();
        double sumTotal = 0, sumPaid = 0, sumPending = 0;
        String cPhone = "", cAddr = "";
        int n = 1;

        for (DataSnapshot ds : invoices) {
            String dateStr = safeStr(ds, "invoiceDate");
            if (!inRange(dateStr)) continue;

            double gt = safeD(ds, "grandTotal");
            double pa = safeD(ds, "paidAmount");
            double pe = safeD(ds, "pendingAmount");
            sumTotal   += gt;
            sumPaid    += pa;
            sumPending += pe;
            if (cPhone.isEmpty()) cPhone = safeStr(ds, "customerPhone");
            if (cAddr.isEmpty())  cAddr  = safeStr(ds, "customerAddress");

            rows.add(new String[]{
                    String.valueOf(n++),               // 0  #
                    safeStr(ds, "invoiceNumber"),       // 1  Invoice No.
                    dateStr,                            // 2  Date
                    cur(gt),                            // 3  Total
                    cur(pa),                            // 4  Paid
                    cur(pe),                            // 5  Pending
                    safeStr(ds, "paymentMode"),         // 6  Mode
                    safeStr(ds, "paymentStatus")        // 7  Status
            });
        }

        if (rows.isEmpty()) {
            setStatus("No records in selected date range.");
            return null;
        }

        int[] colW = calcColWidths(headers, rows, PW - 2 * M);

        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page pg = startPage(pdf, 1);
        Canvas c = pg.getCanvas();

        int y = drawHeader(c, "Customer Invoice Statement", C_BLUE);
        y = drawCustomerBlock(c, customerName, cPhone, cAddr, y);
        y = drawSummaryBoxes(c,
                new String[]{"TOTAL INVOICED", "TOTAL PAID", "BALANCE DUE"},
                new String[]{cur(sumTotal), cur(sumPaid), cur(sumPending)},
                new int[]{C_BLUE, C_GREEN, sumPending > 0 ? C_RED : C_GREEN}, y);
        y = drawTableHeader(c, headers, colW, y, C_BLUE);

        int ri = 0, total = rows.size(), pageNum = 1;
        while (ri < total) {
            if (y + ROW_H > PH - M - 45) {
                drawFooter(c, pageNum, ri, total);
                pdf.finishPage(pg);
                pageNum++;
                pg = startPage(pdf, pageNum);
                c = pg.getCanvas();
                y = M + 10;
                y = drawTableHeader(c, headers, colW, y, C_BLUE);
            }
            y = drawRow(c, rows.get(ri), colW, y, ri % 2 == 0, C_BLUE);
            ri++;
        }

        // Grand total row
        y = drawGrandTotal(c, colW, new int[]{3, 4, 5},
                new double[]{sumTotal, sumPaid, sumPending},
                new int[]{C_DARK, C_GREEN, sumPending > 0 ? C_RED : C_GREEN}, y);

        drawFooter(c, pageNum, total, total);
        pdf.finishPage(pg);

        String safe = customerName.replaceAll("[^a-zA-Z0-9]", "_");
        return savePdf(pdf, "statement_" + safe);
    }

    // ══════════════════════════════════════════════════════════════
    //  CUSTOMER PENDING PDF
    // ══════════════════════════════════════════════════════════════
    private void pickCustomerPending() {
        setStatus("Loading pending…");
        userRef.child("pendingPayments").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                LinkedHashMap<String, List<DataSnapshot>> map = groupByCustomer(snap, "customerName", "lastPaymentDate");
                if (map.isEmpty()) { setStatus("No pending found."); return; }
                showCustomerPicker(map, (name, list) -> {
                    try {
                        File pdf = buildPendingPdf(name, list);
                        if (pdf != null) { setStatus("Report ready!"); showOptions(pdf); }
                    } catch (IOException e) { setStatus("Error: " + e.getMessage()); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                setStatus("Error: " + e.getMessage());
            }
        });
    }

    private File buildPendingPdf(String customerName,
                                 List<DataSnapshot> list) throws IOException {

        String[] headers = {
                "Invoice No.", "Total Amount", "Paid", "Due Amount", "Mode", "Last Date"
        };
        //                0             1          2       3          4       5

        List<String[]> rows = new ArrayList<>();
        double sumTotal = 0, sumPaid = 0, sumDue = 0;
        String cPhone = "";

        for (DataSnapshot ds : list) {
            String dateStr = safeStr(ds, "lastPaymentDate");
            if (!inRange(dateStr)) continue;
            double ta = safeD(ds, "totalAmount");
            double pa = safeD(ds, "paidAmount");
            double pe = safeD(ds, "pendingAmount");
            sumTotal += ta; sumPaid += pa; sumDue += pe;
            if (cPhone.isEmpty()) cPhone = safeStr(ds, "customerPhone");
            rows.add(new String[]{
                    safeStr(ds, "invoiceNumber"), // 0
                    cur(ta),                      // 1
                    cur(pa),                      // 2
                    cur(pe),                      // 3
                    safeStr(ds, "paymentMode"),   // 4
                    dateStr                       // 5
            });
        }

        if (rows.isEmpty()) { setStatus("No records in range."); return null; }

        int[] colW = calcColWidths(headers, rows, PW - 2 * M);
        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page pg = startPage(pdf, 1);
        Canvas c = pg.getCanvas();

        int y = drawHeader(c, "Customer Pending Dues Report", C_ORANGE);
        y = drawCustomerBlock(c, customerName, cPhone, "", y);
        y = drawSummaryBoxes(c,
                new String[]{"TOTAL BILLED","TOTAL PAID","TOTAL DUE"},
                new String[]{cur(sumTotal), cur(sumPaid), cur(sumDue)},
                new int[]{C_BLUE, C_GREEN, C_RED}, y);
        y = drawTableHeader(c, headers, colW, y, C_ORANGE);
        for (int ri = 0; ri < rows.size(); ri++)
            y = drawRow(c, rows.get(ri), colW, y, ri % 2 == 0, C_ORANGE);
        y = drawGrandTotal(c, colW, new int[]{1, 2, 3},
                new double[]{sumTotal, sumPaid, sumDue},
                new int[]{C_DARK, C_GREEN, C_RED}, y);
        drawFooter(c, 1, rows.size(), rows.size());
        pdf.finishPage(pg);

        String safe = customerName.replaceAll("[^a-zA-Z0-9]", "_");
        return savePdf(pdf, "pending_" + safe);
    }

    // ══════════════════════════════════════════════════════════════
    //  PDF DRAWING PRIMITIVES
    // ══════════════════════════════════════════════════════════════

    /** Modern gradient header with business info */
    private int drawHeader(Canvas c, String reportTitle, int accent) {
        int y = 0;

        // Full-width gradient banner
        Paint gradPaint = new Paint();
        gradPaint.setShader(new LinearGradient(
                0, 0, PW, 90,
                C_BLUE_DARK, Color.rgb(40, 100, 200),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, PW, 90, gradPaint);

        // Business name — large white bold centered
        Paint pBiz = mk(22f, C_WHITE, true, Paint.Align.CENTER);
        c.drawText(bizName, PW / 2f, 32, pBiz);

        // Owner + Type on same line
        String sub = "";
        if (!bizOwner.isEmpty()) sub += bizOwner;
        if (!bizType.isEmpty())  sub += (sub.isEmpty() ? "" : "  •  ") + bizType;
        if (!sub.isEmpty()) {
            c.drawText(sub, PW / 2f, 50, mk(9f, Color.rgb(180,210,255), false, Paint.Align.CENTER));
        }

        // Phone | GSTIN
        List<String> info = new ArrayList<>();
        if (!bizPhone.isEmpty()) info.add("📞 " + bizPhone);
        if (!bizGstin.isEmpty()) info.add("GST: " + bizGstin);
        if (!info.isEmpty()) {
            c.drawText(String.join("   |   ", info),
                    PW / 2f, 66, mk(8.5f, Color.rgb(200,225,255), false, Paint.Align.CENTER));
        }

        y = 90;

        // Address pill (dark bar below gradient)
        if (!bizAddress.isEmpty()) {
            Paint aBg = new Paint(); aBg.setColor(C_BLUE_DARK); aBg.setStyle(Paint.Style.FILL);
            c.drawRect(0, y, PW, y + 20, aBg);
            // Bullet circles
            Paint dot = new Paint(); dot.setColor(Color.rgb(180,210,255));
            dot.setStyle(Paint.Style.FILL);
            c.drawCircle(M - 8, y + 10, 3f, dot);
            c.drawCircle(PW - M + 8, y + 10, 3f, dot);
            c.drawText(bizAddress, PW / 2f, y + 14,
                    mk(8.5f, C_WHITE, false, Paint.Align.CENTER));
            y += 20;
        }

        y += 10;

        // Report title with accent left bar
        Paint lbar = new Paint(); lbar.setColor(accent); lbar.setStrokeWidth(3f);
        c.drawLine(M, y, M, y + 16, lbar);
        c.drawText(reportTitle.toUpperCase(), M + 8, y + 12,
                mk(12f, C_DARK, true, Paint.Align.LEFT));

        // Date range on right
        String rangeLabel = (filterFrom.isEmpty() && filterTo.isEmpty())
                ? "All Dates"
                : fmtDate(filterFrom.isEmpty() ? "Start" : filterFrom)
                + " → " + fmtDate(filterTo.isEmpty() ? "Today" : filterTo);
        c.drawText("Period: " + rangeLabel, PW - M, y + 12,
                mk(8f, C_GRAY, false, Paint.Align.RIGHT));
        y += 20;

        // Gen date
        String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date());
        c.drawText("Generated: " + date + "   |   User: " + userMobile,
                PW - M, y, mk(7.5f, C_GRAY, false, Paint.Align.RIGHT));
        y += 10;

        return y;
    }

    private int drawReportMeta(Canvas c, int total, int y) {
        String meta = total + " record" + (total == 1 ? "" : "s") + " found";
        c.drawText(meta, M, y + 8, mk(8.5f, C_GRAY, false, Paint.Align.LEFT));
        return y + 14;
    }

    private int drawCustomerBlock(Canvas c, String name, String phone,
                                  String addr, int y) {
        int lines = 1 + (phone.isEmpty() ? 0 : 1) + (addr.isEmpty() ? 0 : 1);
        int bh = lines * 14 + 14;

        Paint bg = new Paint(); bg.setColor(C_ALT); bg.setStyle(Paint.Style.FILL);
        c.drawRect(M, y, PW - M, y + bh, bg);
        Paint bd = new Paint(); bd.setColor(C_BORDER); bd.setStrokeWidth(0.8f);
        bd.setStyle(Paint.Style.STROKE);
        c.drawRect(M, y, PW - M, y + bh, bd);

        // Left accent bar
        Paint bar = new Paint(); bar.setColor(C_BLUE); bar.setStyle(Paint.Style.FILL);
        c.drawRect(M, y, M + 3, y + bh, bar);

        int ty = y + 13;
        c.drawText(name, M + 10, ty, mk(12f, C_DARK, true, Paint.Align.LEFT));
        if (!phone.isEmpty()) { ty += 14; c.drawText("Phone: " + phone, M + 10, ty, mk(9f, C_GRAY, false, Paint.Align.LEFT)); }
        if (!addr.isEmpty())  { ty += 14; c.drawText("Address: " + addr,  M + 10, ty, mk(9f, C_GRAY, false, Paint.Align.LEFT)); }

        return y + bh + 8;
    }

    private int drawSummaryBoxes(Canvas c, String[] labels, String[] values,
                                 int[] colors, int y) {
        int tw = PW - 2 * M;
        int gap = 6, bw = (tw - gap * 2) / 3, bh = 42;

        for (int i = 0; i < 3; i++) {
            int bx = M + i * (bw + gap);

            // Box fill
            Paint bg = new Paint(); bg.setStyle(Paint.Style.FILL);
            bg.setColor(blendWhite(colors[i], 0.07f));
            RectF rf = new RectF(bx, y, bx + bw, y + bh);
            c.drawRoundRect(rf, 6, 6, bg);

            // Top accent bar
            Paint top = new Paint(); top.setColor(colors[i]); top.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(bx, y, bx + bw, y + 3), 3, 3, top);

            // Label
            c.drawText(labels[i], bx + bw / 2f, y + 16,
                    mk(7.5f, C_GRAY, false, Paint.Align.CENTER));

            // Value
            c.drawText(values[i], bx + bw / 2f, y + 34,
                    mk(12f, colors[i], true, Paint.Align.CENTER));

            // Border
            Paint bd = new Paint(); bd.setStyle(Paint.Style.STROKE);
            bd.setColor(colors[i]); bd.setAlpha(60); bd.setStrokeWidth(0.8f);
            c.drawRoundRect(rf, 6, 6, bd);
        }
        return y + bh + 10;
    }

    /**
     * KEY FIX: draws header cells strictly using colW[i] width for header[i] text.
     * Returns y after the header row.
     */
    private int drawTableHeader(Canvas c, String[] headers, int[] colW, int y, int accent) {
        int tw = sum(colW);

        // Header background
        Paint bg = new Paint(); bg.setColor(accent); bg.setStyle(Paint.Style.FILL);
        c.drawRect(M, y, M + tw, y + ROW_H, bg);

        // Draw each header cell text at exactly the correct x position
        int x = M;
        for (int i = 0; i < headers.length; i++) {
            // Left-pad text inside cell
            c.drawText(
                    truncate(headers[i], mk(9.5f, C_WHITE, true, Paint.Align.LEFT), colW[i] - 8),
                    x + 5, y + ROW_H - 6,
                    mk(9.5f, C_WHITE, true, Paint.Align.LEFT)
            );
            // Vertical separator (except last)
            if (i < headers.length - 1) {
                Paint sep = new Paint(); sep.setColor(Color.WHITE); sep.setAlpha(60);
                sep.setStrokeWidth(0.5f);
                c.drawLine(x + colW[i], y + 3, x + colW[i], y + ROW_H - 3, sep);
            }
            x += colW[i];  // ← advance x by EXACTLY this column's width
        }

        // Bottom border
        Paint line = new Paint(); line.setColor(C_BORDER); line.setStrokeWidth(0.5f);
        c.drawLine(M, y + ROW_H, M + tw, y + ROW_H, line);

        return y + ROW_H;
    }

    /**
     * KEY FIX: draws data row cells at exactly colW[i] x offsets — same logic as header.
     */
    private int drawRow(Canvas c, String[] cells, int[] colW,
                        int y, boolean alt, int accent) {
        int tw = sum(colW);

        // Alternating background
        if (alt) {
            Paint bg = new Paint(); bg.setColor(C_ALT); bg.setStyle(Paint.Style.FILL);
            c.drawRect(M, y, M + tw, y + ROW_H, bg);
        }

        int x = M;
        for (int i = 0; i < colW.length; i++) {
            String txt = (i < cells.length && cells[i] != null) ? cells[i] : "";

            Paint p = mk(9f, C_DARK, false, Paint.Align.LEFT);

            // Status coloring
            if      (txt.equalsIgnoreCase("Paid"))    p.setColor(C_GREEN);
            else if (txt.equalsIgnoreCase("Partial"))  p.setColor(C_ORANGE);
            else if (txt.equalsIgnoreCase("Pending"))  p.setColor(C_RED);

            float cellInner = colW[i] - 8;

            if (txt.startsWith("₹")) {
                // Right-align currency inside cell
                Paint pr = mk(9f, p.getColor(), false, Paint.Align.RIGHT);
                c.drawText(truncate(txt, pr, cellInner),
                        x + colW[i] - 4, y + ROW_H - 6, pr);
            } else {
                c.drawText(truncate(txt, p, cellInner),
                        x + 5, y + ROW_H - 6, p);
            }

            // Vertical grid line
            if (i < colW.length - 1) {
                Paint vl = new Paint(); vl.setColor(C_BORDER); vl.setStrokeWidth(0.4f);
                c.drawLine(x + colW[i], y, x + colW[i], y + ROW_H, vl);
            }

            x += colW[i];  // ← advance by this column's width
        }

        // Outer borders
        Paint bl = new Paint(); bl.setColor(C_BORDER); bl.setStrokeWidth(0.4f);
        c.drawLine(M,      y,         M,      y + ROW_H, bl);
        c.drawLine(M + tw, y,         M + tw, y + ROW_H, bl);
        c.drawLine(M,      y + ROW_H, M + tw, y + ROW_H, bl);

        return y + ROW_H;
    }

    private int drawGrandTotal(Canvas c, int[] colW, int[] colIndexes,
                               double[] amounts, int[] colors, int y) {
        int tw = sum(colW);

        Paint bg = new Paint(); bg.setColor(blendWhite(C_BLUE, 0.10f));
        bg.setStyle(Paint.Style.FILL);
        c.drawRect(M, y, M + tw, y + ROW_H + 2, bg);

        c.drawText("GRAND TOTAL", M + 5, y + ROW_H - 5,
                mk(9f, C_BLUE_DARK, true, Paint.Align.LEFT));

        // Cumulative x positions for each column index
        int[] cumX = new int[colW.length + 1];
        cumX[0] = M;
        for (int i = 0; i < colW.length; i++) cumX[i+1] = cumX[i] + colW[i];

        for (int k = 0; k < colIndexes.length; k++) {
            int ci = colIndexes[k];
            // Right-align value inside the cell ending at cumX[ci+1]
            Paint pr = mk(9.5f, colors[k], true, Paint.Align.RIGHT);
            c.drawText(cur(amounts[k]), cumX[ci + 1] - 4, y + ROW_H - 5, pr);
        }

        Paint bd = new Paint(); bd.setColor(C_BORDER); bd.setStrokeWidth(0.8f);
        bd.setStyle(Paint.Style.STROKE);
        c.drawRect(M, y, M + tw, y + ROW_H + 2, bd);

        return y + ROW_H + 2 + 8;
    }

    private void drawFooter(Canvas c, int page, int shown, int total) {
        int fy = PH - M;
        Paint line = new Paint(); line.setColor(C_BORDER); line.setStrokeWidth(0.8f);
        c.drawLine(M, fy - 14, PW - M, fy - 14, line);

        c.drawText("Page " + page, M, fy, mk(8f, C_GRAY, false, Paint.Align.LEFT));
        c.drawText(shown + " of " + total + " records", PW / 2f, fy,
                mk(8f, C_GRAY, false, Paint.Align.CENTER));
        c.drawText(bizName + "  |  GST Bill Pro", PW - M, fy,
                mk(8f, C_GRAY, false, Paint.Align.RIGHT));
    }

    // ══════════════════════════════════════════════════════════════
    //  COLUMN WIDTH — THE ROOT FIX
    //  colW[i] is calculated using header[i] and all row cells at
    //  index i — guaranteed alignment because header and row drawing
    //  both advance x by colW[i] in the same order.
    // ══════════════════════════════════════════════════════════════
    private int[] calcColWidths(String[] headers, List<String[]> rows, int tableW) {
        int n = headers.length;
        int[] w = new int[n];
        Paint mp = mk(9.5f, Color.BLACK, false, Paint.Align.LEFT);

        for (int i = 0; i < n; i++) {
            // Start with header text width
            w[i] = (int) mp.measureText(headers[i]) + 14;
            // Expand if any data cell is wider
            for (String[] row : rows) {
                if (i < row.length) {
                    int rw = (int) mp.measureText(row[i] != null ? row[i] : "") + 14;
                    if (rw > w[i]) w[i] = rw;
                }
            }
            w[i] = Math.max(w[i], 30); // minimum cell width
        }

        // Scale to fit tableW exactly
        int total = sum(w);
        if (total != tableW) {
            float ratio = (float) tableW / total;
            int used = 0;
            for (int i = 0; i < n - 1; i++) {
                w[i] = Math.max(25, (int)(w[i] * ratio));
                used += w[i];
            }
            w[n - 1] = tableW - used; // last column takes remainder
        }
        return w;
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════
    private PdfDocument.Page startPage(PdfDocument pdf, int num) {
        return pdf.startPage(new PdfDocument.PageInfo.Builder(PW, PH, num).create());
    }

    private File savePdf(PdfDocument pdf, String tag) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File dir  = new File(getExternalFilesDir(null), "GSTReports");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, tag + "_" + ts + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(file)) { pdf.writeTo(fos); }
        pdf.close();
        return file;
    }

    private void showOptions(File pdf) {
        if (pdf == null) return;
        new AlertDialog.Builder(this)
                .setTitle("PDF Ready")
                .setItems(new String[]{"📄  View PDF", "📤  Share PDF", "💬  Share on WhatsApp"},
                        (d, i) -> {
                            switch (i) {
                                case 0: openPdf(pdf); break;
                                case 1: sharePdf(pdf, false); break;
                                case 2: sharePdf(pdf, true); break;
                            }
                        })
                .setNegativeButton("Close", null).show();
    }

    private void openPdf(File f) {
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent in = new Intent(Intent.ACTION_VIEW);
            in.setDataAndType(u, "application/pdf");
            in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(Intent.createChooser(in, "Open PDF"));
        } catch (Exception e) { Toast.makeText(this, "Install a PDF viewer", Toast.LENGTH_SHORT).show(); }
    }

    private void sharePdf(File f, boolean whatsapp) {
        Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        Intent in = new Intent(Intent.ACTION_SEND);
        in.setType("application/pdf");
        in.putExtra(Intent.EXTRA_STREAM, u);
        in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (whatsapp) in.setPackage("com.whatsapp");
        try { startActivity(Intent.createChooser(in, "Share PDF")); }
        catch (Exception e) { Toast.makeText(this, "App not available", Toast.LENGTH_SHORT).show(); }
    }

    private LinkedHashMap<String, List<DataSnapshot>> groupByCustomer(
            DataSnapshot snap, String nameKey, String dateKey) {
        LinkedHashMap<String, List<DataSnapshot>> map = new LinkedHashMap<>();
        for (DataSnapshot ds : snap.getChildren()) {
            if (!inRange(safeStr(ds, dateKey))) continue;
            String name = safeStr(ds, nameKey);
            if (name.isEmpty()) name = "Unknown";
            map.computeIfAbsent(name, k -> new ArrayList<>()).add(ds);
        }
        return map;
    }

    interface CustomerCallback { void onSelect(String name, List<DataSnapshot> list); }

    private void showCustomerPicker(LinkedHashMap<String, List<DataSnapshot>> map,
                                    CustomerCallback cb) {
        String[] names = map.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Select Customer")
                .setItems(names, (d, i) -> cb.onSelect(names[i], map.get(names[i])))
                .setNegativeButton("Cancel", null).show();
    }

    private Paint mk(float sz, int color, boolean bold, Paint.Align align) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sz);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
        p.setTextAlign(align);
        return p;
    }

    private int blendWhite(int color, float alpha) {
        return Color.rgb(
                (int)(Color.red(color)   * alpha + 255 * (1 - alpha)),
                (int)(Color.green(color) * alpha + 255 * (1 - alpha)),
                (int)(Color.blue(color)  * alpha + 255 * (1 - alpha)));
    }

    private String truncate(String text, Paint p, float maxW) {
        if (text == null) return "";
        if (p.measureText(text) <= maxW) return text;
        while (text.length() > 1 && p.measureText(text + "…") > maxW)
            text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private int sum(int[] a) { int s = 0; for (int v : a) s += v; return s; }

    private String safeStr(DataSnapshot ds, String key) {
        Object v = ds.child(key).getValue();
        return v != null ? v.toString().trim() : "";
    }

    private double safeD(DataSnapshot ds, String key) {
        Object v = ds.child(key).getValue();
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }

    private String cur(double v) {
        return String.format(Locale.getDefault(), "₹%.2f", v);
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> { if (tvStatus != null) tvStatus.setText(msg); });
    }
}