package com.sandhyasofttech.gstbillingpro.Activity;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sandhyasofttech.gstbillingpro.Adapter.CustomFieldAdapter;
import com.sandhyasofttech.gstbillingpro.R;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CustomFieldsActivity extends AppCompatActivity implements CustomFieldAdapter.OnFieldInteractionListener { // <-- IMPLEMENT THE NEW INTERFACE

    private RecyclerView rvCustomFields;
    private CustomFieldAdapter adapter;
    private List<String> customFields;
    private SharedPreferences prefs;
    private Gson gson;
    private boolean keepDefaultFields;
    private String primaryField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_fields);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Custom Fields");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        gson = new Gson();
        loadCustomFields();
        keepDefaultFields = prefs.getBoolean("KEEP_DEFAULT_FIELDS", true);
        primaryField = prefs.getString("PRIMARY_FIELD", null);

        rvCustomFields = findViewById(R.id.rvCustomFields);
        rvCustomFields.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomFieldAdapter(customFields, primaryField, this);
        rvCustomFields.setAdapter(adapter);

        FloatingActionButton fabAddField = findViewById(R.id.fabAddField);
        fabAddField.setOnClickListener(view -> showAddFieldDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_custom_fields, menu);
        MenuItem item = menu.findItem(R.id.action_show_defaults);
        item.setChecked(keepDefaultFields);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_show_defaults) {
            keepDefaultFields = !item.isChecked();
            item.setChecked(keepDefaultFields);
            prefs.edit().putBoolean("KEEP_DEFAULT_FIELDS", keepDefaultFields).apply();
            String message = keepDefaultFields ? "Default fields will be shown" : "Default fields will be hidden";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadCustomFields() {
        String json = prefs.getString("CUSTOM_FIELDS", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            customFields = gson.fromJson(json, type);
        } else {
            customFields = new ArrayList<>();
        }
    }

    private void saveCustomFields() {
        String json = gson.toJson(customFields);
        prefs.edit().putString("CUSTOM_FIELDS", json).apply();
    }

    private void showAddFieldDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Custom Field");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String fieldName = input.getText().toString().trim();
            if (!fieldName.isEmpty()) {
                customFields.add(fieldName);
                adapter.notifyItemInserted(customFields.size() - 1);
                saveCustomFields();

            } else {
                Toast.makeText(this, "Field name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onDeleteClick(int position) {
        String deletedField = customFields.get(position);
        customFields.remove(position);
        adapter.notifyItemRemoved(position);
        saveCustomFields();

        if (deletedField.equals(primaryField)) {
            primaryField = null;
            prefs.edit().remove("PRIMARY_FIELD").apply();
        }
    }

    @Override
    public void onPrimaryFieldSelected(String fieldName) {
        primaryField = fieldName;
        prefs.edit().putString("PRIMARY_FIELD", primaryField).apply();
    }
}
