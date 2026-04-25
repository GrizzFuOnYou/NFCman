package com.nfc.wallet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    public interface OnClickListener {
        void onClick(int position);
    }

    public interface OnLongClickListener {
        boolean onLongClick(int position);
    }

    private final List<CardModel> cards;
    private final OnClickListener clickListener;
    private final OnLongClickListener longClickListener;

    public CardAdapter(List<CardModel> cards, OnClickListener clickListener, OnLongClickListener longClickListener) {
        this.cards = cards;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        CardModel card = cards.get(position);
        holder.tvName.setText(card.name != null && !card.name.isEmpty() ? card.name : "Unnamed Card");
        holder.tvUid.setText("UID: " + (card.uid != null ? card.uid : "N/A"));
        String typeCompany = (card.cardType != null ? card.cardType : "") +
                (!card.company.isEmpty() && !card.company.equals("Unknown") ? " · " + card.company : "");
        holder.tvTypeCompany.setText(typeCompany.isEmpty() ? "Unknown" : typeCompany);
        holder.tvDate.setText(card.scanDate != null ? card.scanDate : "");

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(holder.getAdapterPosition());
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) return longClickListener.onLongClick(holder.getAdapterPosition());
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return cards != null ? cards.size() : 0;
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvUid, tvTypeCompany, tvDate;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_card_name);
            tvUid = itemView.findViewById(R.id.tv_card_uid);
            tvTypeCompany = itemView.findViewById(R.id.tv_card_type_company);
            tvDate = itemView.findViewById(R.id.tv_card_date);
        }
    }
}
