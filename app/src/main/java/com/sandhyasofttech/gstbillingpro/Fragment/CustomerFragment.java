package com.sandhyasofttech.gstbillingpro.Fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sandhyasofttech.gstbillingpro.R;
import com.sandhyasofttech.gstbillingpro.custmore.AddCustomerActivity;
import com.sandhyasofttech.gstbillingpro.custmore.Customer;
import com.sandhyasofttech.gstbillingpro.custmore.CustomerAdapter;

import java.util.ArrayList;
import java.util.List;

public class CustomerFragment extends Fragment {

    private RecyclerView recyclerView;
    private FloatingActionButton fabAddCustomer;
    private SearchView searchView;
    private LinearLayout emptyStateView;

    private CustomerAdapter adapter;
    private final List<Customer> customersList = new ArrayList<>();
    private DatabaseReference customersRef;
    private String userMobile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewCustomers);
        fabAddCustomer = view.findViewById(R.id.fabAddCustomer);
        searchView = view.findViewById(R.id.searchViewCustomer);
        emptyStateView = view.findViewById(R.id.emptyStateView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        SharedPreferences prefs = requireContext().getSharedPreferences("APP_PREFS", requireContext().MODE_PRIVATE);
        userMobile = prefs.getString("USER_MOBILE", null);
        if (userMobile == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        customersRef = FirebaseDatabase.getInstance().getReference("users").child(userMobile).child("customers");

        adapter = new CustomerAdapter(requireContext(), customersList, new CustomerAdapter.OnCustomerActionListener() {
            @Override
            public void onEdit(Customer customer) {
                Intent intent = new Intent(getActivity(), AddCustomerActivity.class);
                intent.putExtra(AddCustomerActivity.EXTRA_IS_EDIT, true);
                intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_ID, customer.phone);
                intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_NAME, customer.name);
                intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_PHONE, customer.phone);
                intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_EMAIL, customer.email);
                intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_GSTIN, customer.gstin);
                intent.putExtra(AddCustomerActivity.EXTRA_CUSTOMER_ADDRESS, customer.address);
                startActivity(intent);
            }

            @Override
            public void onDelete(Customer customer) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Customer")
                        .setMessage("Delete " + customer.name + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            customersRef.child(customer.phone).removeValue()
                                    .addOnSuccessListener(a -> Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        recyclerView.setAdapter(adapter);

        fabAddCustomer.setOnClickListener(v -> startActivity(new Intent(getActivity(), AddCustomerActivity.class)));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });

        loadCustomers();

        return view;
    }

    private void loadCustomers() {
        customersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Customer> temp = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Customer c = snap.getValue(Customer.class);
                    if (c != null) {
                        if (c.phone == null || c.phone.isEmpty()) c.phone = snap.getKey();
                        temp.add(c);
                    }
                }

                // Update adapter with data
                adapter.updateData(temp);

                // Update empty state based on data size
                updateEmptyState(temp.isEmpty());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                // Show empty state on error
                updateEmptyState(true);
            }
        });
    }

    /**
     * Updates the visibility of RecyclerView and Empty State View
     * @param isEmpty true if no customers available
     */
    private void updateEmptyState(boolean isEmpty) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (isEmpty) {
                // Show empty state with animation
                recyclerView.setVisibility(View.GONE);
                emptyStateView.setVisibility(View.VISIBLE);

                // Add fade-in animation
                emptyStateView.startAnimation(
                        AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_in)
                );
            } else {
                // Show recycler view
                recyclerView.setVisibility(View.VISIBLE);
                emptyStateView.setVisibility(View.GONE);
            }
        });
    }
}