package com.sandhyasofttech.gstbillingpro.Fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sandhyasofttech.gstbillingpro.Activity.FieldMappingActivity;
import com.sandhyasofttech.gstbillingpro.Activity.NewProductActivity;
import com.sandhyasofttech.gstbillingpro.Adapter.ProductsAdapter;
import com.sandhyasofttech.gstbillingpro.Model.Product;
import com.sandhyasofttech.gstbillingpro.R;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductFragment extends Fragment {

    private static final String TAG = "ProductFragment";

    private RecyclerView rvProducts;
    private FloatingActionButton fabAddProduct;
    private MaterialButton btnImportProducts;
    private SearchView searchView;
    private TextView tvTotalProducts;

    private ProductsAdapter adapter;
    private final List<Product> productList = new ArrayList<>();
    private final List<Product> filteredList = new ArrayList<>();

    private DatabaseReference productsRef;
    private String userMobile;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getHeaderAndGoToMapping(uri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> fieldMappingLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra("fileUri");
                    importDataFromFile(uri);
                }
            });


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvProducts = view.findViewById(R.id.rvProducts);
        fabAddProduct = view.findViewById(R.id.fabAddProduct);
        btnImportProducts = view.findViewById(R.id.btnImportProducts);
        searchView = (androidx.appcompat.widget.SearchView) view.findViewById(R.id.searchView);
        tvTotalProducts = view.findViewById(R.id.tvTotalProducts);

        SharedPreferences prefs = requireActivity().getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);

        if (userMobile == null || userMobile.isEmpty()) {
            showToast("User not logged in. Cannot load data.");
            return;
        }

        productsRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile).child("products");
        setupRecyclerView();
        loadProducts();
        setupListeners();
    }

    private void setupRecyclerView() {
        adapter = new ProductsAdapter(filteredList);
        rvProducts.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProducts.setAdapter(adapter);
    }

    private void setupListeners() {
        fabAddProduct.setOnClickListener(v -> startActivity(new Intent(getContext(), NewProductActivity.class)));
        btnImportProducts.setOnClickListener(v -> showImportInstructionsDialog());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterProducts(newText);
                return true;
            }
        });
    }

    private void showImportInstructionsDialog() {
        String instructions = "This feature allows you to import products from an Excel (.xls, .xlsx) or CSV (.csv) file.\n\n" +
                "1. Your file MUST have a header row (the first row should contain titles like \"Product Name\", \"Price\", etc).\n\n" +
                "2. If you have a PDF file, please convert it to Excel or CSV first using an online tool or your computer's software.";

        new AlertDialog.Builder(requireContext())
                .setTitle("Import Instructions")
                .setMessage(instructions)
                .setPositiveButton("Choose File", (dialog, which) -> openFilePicker())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv"});
        filePickerLauncher.launch(Intent.createChooser(intent, "Select an Excel or CSV file"));
    }

    private void getHeaderAndGoToMapping(Uri uri) {
        executor.execute(() -> {
            ArrayList<String> header = new ArrayList<>();
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri)) {
                if (inputStream == null) throw new IOException("Unable to open input stream for URI");

                String fileName = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "";

                if (fileName.toLowerCase().endsWith(".csv")) {
                    try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
                        String[] headerRow = reader.readNext();
                        if (headerRow != null) {
                            header.addAll(Arrays.asList(headerRow));
                        }
                    }
                } else if (fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls")) {
                    try (Workbook workbook = WorkbookFactory.create(inputStream)) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row headerRow = sheet.getRow(0);
                        if (headerRow != null) {
                            for (Cell cell : headerRow) {
                                header.add(getCellValueAsString(cell));
                            }
                        }
                    }
                }

                handler.post(() -> {
                    if (header.isEmpty()) {
                        showToast("Could not read a header row from the file. Please ensure the first row contains column titles.");
                        return;
                    }

                    Intent intent = new Intent(getContext(), FieldMappingActivity.class);
                    intent.putExtra("fileUri", uri);
                    intent.putStringArrayListExtra("fileColumns", header);
                    fieldMappingLauncher.launch(intent);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error reading file header", e);
                handler.post(() -> showToast("Error reading file: " + e.getMessage()));
            }
        });
    }

    private void importDataFromFile(final Uri uri) {
        executor.execute(() -> {
            List<Product> importedProducts = new ArrayList<>();
            String errorMsg = null;

            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri)) {
                if (inputStream == null) throw new IOException("Unable to open input stream for URI");

                String fileName = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "";
                Map<String, Integer> mapping = loadSavedMapping();

                if (fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls")) {
                    importedProducts = parseExcel(inputStream, mapping);
                } else if (fileName.toLowerCase().endsWith(".csv")) {
                    importedProducts = parseCsv(inputStream, mapping);
                }

            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                errorMsg = "Import failed: " + e.getMessage();
            }

            final String finalErrorMsg = errorMsg;
            final List<Product> finalImportedProducts = importedProducts;
            handler.post(() -> {
                if (finalErrorMsg != null) {
                    showToast(finalErrorMsg);
                } else if (finalImportedProducts.isEmpty()) {
                    showToast("No valid products found in the file.");
                } else {
                    uploadProductsToFirebase(finalImportedProducts);
                }
            });
        });
    }

    private List<Product> parseExcel(InputStream inputStream, Map<String, Integer> mapping) throws IOException {
        List<Product> list = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return list;

            List<String> header = new ArrayList<>();
            for (Cell cell : headerRow) {
                header.add(getCellValueAsString(cell));
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                Product p = createProductFromRow(row, mapping, header);
                if (p != null && p.getName() != null && !p.getName().isEmpty()) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    private List<Product> parseCsv(InputStream inputStream, Map<String, Integer> mapping) throws IOException, CsvValidationException {
        List<Product> list = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream))) {
            String[] header = csvReader.readNext();
            if (header == null) return list;

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                Product p = createProductFromLine(line, mapping, Arrays.asList(header));
                if (p != null && p.getName() != null && !p.getName().isEmpty()) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    private Product createProductFromRow(Row row, Map<String, Integer> mapping, List<String> header) {
        Product p = new Product();
        p.setProductId(UUID.randomUUID().toString());
        Map<String, String> customFields = new HashMap<>();

        Set<Integer> mappedColumnIndexes = new HashSet<>();

        // 1. Process explicitly mapped fields
        for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
            String appField = entry.getKey();
            int columnIndex = entry.getValue() - 1; // -1 for "-- Not Mapped --"
            if (columnIndex < 0) continue;

            mappedColumnIndexes.add(columnIndex);
            String value = getCellValueAsString(row.getCell(columnIndex));

            switch (appField) {
                case "Product Name": p.setName(value); break;
                case "HSN Code": p.setHsnCode(value); break;
                case "Price": p.setPrice(parseDouble(value)); break;
                case "GST Rate": p.setGstRate(parseDouble(value)); break;
                case "Stock Quantity": p.setStockQuantity(parseInt(value)); break;
                case "Unit": p.setUnit(value); break;
                default: customFields.put(appField, value); break;
            }
        }

        // 2. Catch-all for unmapped fields
        for (int i = 0; i < header.size(); i++) {
            if (!mappedColumnIndexes.contains(i)) {
                String headerName = header.get(i);
                String value = getCellValueAsString(row.getCell(i));
                if (value != null && !value.isEmpty()) {
                    customFields.put(headerName, value);
                }
            }
        }

        p.setCustomFields(customFields);
        return p;
    }

    private Product createProductFromLine(String[] line, Map<String, Integer> mapping, List<String> header) {
        Product p = new Product();
        p.setProductId(UUID.randomUUID().toString());
        Map<String, String> customFields = new HashMap<>();

        Set<Integer> mappedColumnIndexes = new HashSet<>();

        // 1. Process explicitly mapped fields
        for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
            String appField = entry.getKey();
            int columnIndex = entry.getValue() - 1; // -1 for "-- Not Mapped --"
            if (columnIndex < 0 || columnIndex >= line.length) continue;

            mappedColumnIndexes.add(columnIndex);
            String value = line[columnIndex].trim();

            switch (appField) {
                case "Product Name": p.setName(value); break;
                case "HSN Code": p.setHsnCode(value); break;
                case "Price": p.setPrice(parseDouble(value)); break;
                case "GST Rate": p.setGstRate(parseDouble(value)); break;
                case "Stock Quantity": p.setStockQuantity(parseInt(value)); break;
                case "Unit": p.setUnit(value); break;
                default: customFields.put(appField, value); break;
            }
        }

        // 2. Catch-all for unmapped fields
        for (int i = 0; i < header.size(); i++) {
            if (!mappedColumnIndexes.contains(i)) {
                if (i < line.length) {
                    String headerName = header.get(i);
                    String value = line[i].trim();
                    if (!value.isEmpty()) {
                        customFields.put(headerName, value);
                    }
                }
            }
        }

        p.setCustomFields(customFields);
        return p;
    }

    private Map<String, Integer> loadSavedMapping() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("FIELD_MAPPING", null);
        if (json != null) {
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            return gson.fromJson(json, type);
        }
        return new HashMap<>();
    }

    private void uploadProductsToFirebase(List<Product> productsToUpload) {
        if (productsToUpload.isEmpty()) {
            showToast("No new products to import.");
            return;
        }
        Set<String> existingProductNames = new HashSet<>();
        for (Product p : productList) {
            if (p.getName() != null) {
                existingProductNames.add(p.getName().toLowerCase());
            }
        }

        Map<String, Object> batchUpdate = new HashMap<>();
        int newProductsCount = 0;
        for (Product product : productsToUpload) {
            if (product.getName() != null && !product.getName().isEmpty() && !existingProductNames.contains(product.getName().toLowerCase())) {
                batchUpdate.put(product.getProductId(), product);
                existingProductNames.add(product.getName().toLowerCase());
                newProductsCount++;
            }
        }

        if (batchUpdate.isEmpty()) {
            showToast("All products in the file already exist or have no name.");
            return;
        }

        int finalNewProductsCount = newProductsCount;
        productsRef.updateChildren(batchUpdate)
                .addOnSuccessListener(aVoid -> showToast(finalNewProductsCount + " new products imported successfully."))
                .addOnFailureListener(e -> showToast("Database error: Failed to upload products."));
    }

    private void loadProducts() {
        productsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Product p = ds.getValue(Product.class);
                    if (p != null) {
                        productList.add(p);
                    }
                }
                filterProducts(searchView.getQuery().toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read products.", error.toException());
                showToast("Failed to load products: " + error.getMessage());
            }
        });
    }

    private void filterProducts(String query) {
        filteredList.clear();
        if (query == null || query.isEmpty()) {
            filteredList.addAll(productList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Product product : productList) {
                if (product.getName() != null && product.getName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(product);
                }
            }
        }
        tvTotalProducts.setText("Total Products: " + filteredList.size());
        adapter.notifyDataSetChanged();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: double val = cell.getNumericCellValue(); return val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }
    private double parseDouble(String s) { if (s == null || s.isEmpty()) return 0.0; try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; } }
    private int parseInt(String s) { if (s == null || s.isEmpty()) return 0; try { return (int) Double.parseDouble(s); } catch (NumberFormatException e) { return 0; } }
    private void showToast(String message) { if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show(); }
}
