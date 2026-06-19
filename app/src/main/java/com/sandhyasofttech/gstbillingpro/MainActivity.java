package com.sandhyasofttech.gstbillingpro;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.sandhyasofttech.gstbillingpro.Activity.PendingPaymentsActivity;
import com.sandhyasofttech.gstbillingpro.Fragment.CustomerFragment;
import com.sandhyasofttech.gstbillingpro.Fragment.HomeFragment;
import com.sandhyasofttech.gstbillingpro.Fragment.InvoiceBillingFragment;
import com.sandhyasofttech.gstbillingpro.Fragment.ProductFragment;
import com.sandhyasofttech.gstbillingpro.Fragment.SettingsFragment;
import com.sandhyasofttech.gstbillingpro.Registration.LoginActivity;
import com.sandhyasofttech.gstbillingpro.soldproduct.SoldProductsActivity;

import java.util.HashMap;
import java.util.Map;import com.sandhyasofttech.gstbillingpro.Activity.TotalOverviewActivity;


public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private boolean isUpdating = false;
    private final Map<Integer, String> titleMap = new HashMap<>();
    private int previousFragmentId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🔹 Title Map
        titleMap.put(R.id.nav_home, "Home");
        titleMap.put(R.id.nav_invoice, "Invoice");
        titleMap.put(R.id.nav_customer, "Customer");
        titleMap.put(R.id.nav_product, "Product");
        titleMap.put(R.id.nav_soldproduct, "Sold Products");
        titleMap.put(R.id.nav_TodaysOverview, "TodaysOverview");
        titleMap.put(R.id.nav_pending_amount, "Pending Amount");
        titleMap.put(R.id.nav_settings, "Settings");

        // 🔹 Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 🔹 Drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navView = findViewById(R.id.navigation_view);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        bottomNav.setItemIconTintList(null);

        // 🔹 Drawer Click
        navView.setNavigationItemSelectedListener(item -> {
            if (isUpdating) return false;

            int id = item.getItemId();

            if (id == R.id.nav_logout) {
                showLogoutDialog();
                return true;
            }

            selectFragment(id);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // 🔹 Bottom Navigation Click
        bottomNav.setOnItemSelectedListener(item -> {
            if (isUpdating) return false;
            selectFragment(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            selectFragment(R.id.nav_home);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blue_primary));
        }
    }

    // 🔹 Logout
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (d, w) -> {
                    getSharedPreferences("APP_PREFS", MODE_PRIVATE)
                            .edit().clear().apply();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    // 🔹 Fragment / Activity Selection
    private void selectFragment(@IdRes int itemId) {

        // 🔥 SOLD PRODUCTS (Activity)
        if (itemId == R.id.nav_soldproduct) {
            startActivity(new Intent(this, SoldProductsActivity.class));
            return;
        }
        if (itemId == R.id.nav_TodaysOverview) {
            startActivity(new Intent(this, TotalOverviewActivity.class));
            return;
        }

        // 🔥 PENDING AMOUNT (Activity)
        if (itemId == R.id.nav_pending_amount) {
            startActivity(new Intent(this, PendingPaymentsActivity.class));
            return;
        }

        Fragment fragment = getFragment(itemId);
        if (fragment == null) return;

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        if (itemId != previousFragmentId) {
            if (isForwardNavigation(itemId)) {
                transaction.setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                );
            } else {
                transaction.setCustomAnimations(
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                );
            }
        }

        transaction.replace(R.id.fragment_container, fragment);

        if (itemId != R.id.nav_home) {
            transaction.addToBackStack(null);
        }

        transaction.commit();
        fm.executePendingTransactions();

        syncNavigation(itemId);
        previousFragmentId = itemId;
    }

    private Fragment getFragment(int id) {
        if (id == R.id.nav_home) return new HomeFragment();
        if (id == R.id.nav_invoice) return new InvoiceBillingFragment();
        if (id == R.id.nav_customer) return new CustomerFragment();
        if (id == R.id.nav_product) return new ProductFragment();
        if (id == R.id.nav_settings) return new SettingsFragment();
        return null;
    }

    private boolean isForwardNavigation(int newId) {
        return getMenuIndex(newId) > getMenuIndex(previousFragmentId);
    }

    private int getMenuIndex(int id) {
        if (id == R.id.nav_home) return 0;
        if (id == R.id.nav_invoice) return 1;
        if (id == R.id.nav_customer) return 2;
        if (id == R.id.nav_product) return 3;
        if (id == R.id.nav_settings) return 4;
        return 0;
    }

    // 🔹 Sync Drawer + Bottom Nav
    public void syncNavigation(int itemId) {
        if (isUpdating) return;
        isUpdating = true;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        NavigationView navView = findViewById(R.id.navigation_view);

        bottomNav.post(() -> {

            if (isBottomNavItem(itemId) && bottomNav.getSelectedItemId() != itemId) {
                bottomNav.setSelectedItemId(itemId);
            }

            if (navView.getCheckedItem() == null ||
                    navView.getCheckedItem().getItemId() != itemId) {
                navView.setCheckedItem(itemId);
            }

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(titleMap.get(itemId));
            }

            isUpdating = false;
        });
    }

    private boolean isBottomNavItem(int id) {
        return id == R.id.nav_home ||
                id == R.id.nav_invoice ||
                id == R.id.nav_customer ||
                id == R.id.nav_product ||
                id == R.id.nav_settings;
    }

    // 🔹 Back Button
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            syncNavigation(getCurrentNavId());
        } else {
            super.onBackPressed();
        }
    }

    private int getCurrentNavId() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getFragments().isEmpty()) return R.id.nav_home;

        Fragment top = fm.getFragments().get(fm.getFragments().size() - 1);
        String tag = top.getClass().getSimpleName();

        if ("HomeFragment".equals(tag)) return R.id.nav_home;
        if ("InvoiceBillingFragment".equals(tag)) return R.id.nav_invoice;
        if ("CustomerFragment".equals(tag)) return R.id.nav_customer;
        if ("ProductFragment".equals(tag)) return R.id.nav_product;
        if ("SettingsFragment".equals(tag)) return R.id.nav_settings;

        return R.id.nav_home;
    }
}
