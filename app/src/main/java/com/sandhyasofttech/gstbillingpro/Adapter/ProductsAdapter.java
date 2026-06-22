package com.sandhyasofttech.gstbillingpro.Adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sandhyasofttech.gstbillingpro.Activity.ProductDetailsActivity;
import com.sandhyasofttech.gstbillingpro.Model.Product;
import com.sandhyasofttech.gstbillingpro.R;

import java.util.List;

public class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.ProductViewHolder> {

    private List<Product> productList;

    // Distinct colors for avatar backgrounds
    private static final int[] AVATAR_COLORS = {
            0xFF4A6FA5, 0xFF7C3AED, 0xFF059669,
            0xFFDB2777, 0xFFD97706, 0xFF0891B2,
            0xFFDC2626, 0xFF65A30D, 0xFF7C3AED
    };

    public ProductsAdapter(List<Product> productList) {
        this.productList = productList;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);

        // Product name
        holder.tvName.setText(product.getName());

        // ── Initial badge ──────────────────────────────────────────
        String name = product.getName();
        String initial = (name != null && !name.isEmpty())
                ? String.valueOf(name.charAt(0)).toUpperCase()
                : "?";
        holder.tvProductInitial.setText(initial);

        // Pick a color deterministically from the name so it's stable across scrolls
        int colorIndex = (name != null && !name.isEmpty())
                ? Math.abs(name.hashCode()) % AVATAR_COLORS.length
                : 0;
        holder.tvProductInitial.setBackgroundColor(AVATAR_COLORS[colorIndex]);
        // ──────────────────────────────────────────────────────────

        // Quantity + Unit
        String unit = product.getUnit();
        String qtyText = (unit != null && !unit.isEmpty())
                ? product.getEffectiveQuantity() + " " + unit
                : String.valueOf(product.getEffectiveQuantity());
        holder.tvQuantity.setText(qtyText);

        // Click → ProductDetailsActivity
        holder.itemView.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();
            Intent intent = new Intent(context, ProductDetailsActivity.class);
            intent.putExtra("product", product);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {

        TextView tvName, tvQuantity, tvProductInitial;
        ImageView ivForwardArrow;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName           = itemView.findViewById(R.id.tvProductName);
            tvQuantity       = itemView.findViewById(R.id.tvQuantity);
            tvProductInitial = itemView.findViewById(R.id.tvProductInitial);  // ← was missing
            ivForwardArrow   = itemView.findViewById(R.id.ivForwardArrow);
        }
    }
}