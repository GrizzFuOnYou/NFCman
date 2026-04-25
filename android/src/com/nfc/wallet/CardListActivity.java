package com.nfc.wallet;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CardListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CardAdapter adapter;
    private TextView tvEmpty;
    private EditText etSearch;
    private CardManager cardManager;
    private List<CardModel> allCards = new ArrayList<>();
    private List<CardModel> filteredCards = new ArrayList<>();

    private int selectedPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_list);

        cardManager = new CardManager(this);

        recyclerView = findViewById(R.id.recycler_view);
        tvEmpty = findViewById(R.id.tv_empty);
        etSearch = findViewById(R.id.et_search);
        FloatingActionButton fab = findViewById(R.id.fab_add);

        adapter = new CardAdapter(filteredCards, this::onCardClick, this::onCardLongClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fab.setOnClickListener(v ->
                startActivity(new Intent(this, ManualEntryActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterCards(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        registerForContextMenu(recyclerView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCards();
    }

    private void loadCards() {
        new Thread(() -> {
            List<CardModel> cards = cardManager.listCards();
            runOnUiThread(() -> {
                allCards = cards;
                filterCards(etSearch.getText() != null ? etSearch.getText().toString() : "");
            });
        }).start();
    }

    private void filterCards(String query) {
        filteredCards.clear();
        String q = query.toLowerCase().trim();
        for (CardModel card : allCards) {
            if (q.isEmpty()
                    || card.name.toLowerCase().contains(q)
                    || card.uid.toLowerCase().contains(q)
                    || card.cardType.toLowerCase().contains(q)
                    || card.company.toLowerCase().contains(q)) {
                filteredCards.add(card);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredCards.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(filteredCards.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void onCardClick(int position) {
        if (position < 0 || position >= filteredCards.size()) return;
        CardModel card = filteredCards.get(position);
        Intent intent = new Intent(this, CardDetailActivity.class);
        intent.putExtra("card_filename", card.getSafeFilename());
        startActivity(intent);
    }

    private boolean onCardLongClick(int position) {
        selectedPosition = position;
        return false; // let context menu open
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        menu.setHeaderTitle("Card Options");
        menu.add(0, 1, 0, "Edit");
        menu.add(0, 2, 0, "Delete");
        menu.add(0, 3, 0, "Emulate");
        menu.add(0, 4, 0, "Export TXT");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedPosition < 0 || selectedPosition >= filteredCards.size()) return false;
        CardModel card = filteredCards.get(selectedPosition);
        switch (item.getItemId()) {
            case 1: editCard(card); return true;
            case 2: deleteCard(card); return true;
            case 3: emulateCard(card); return true;
            case 4: exportCard(card); return true;
        }
        return super.onContextItemSelected(item);
    }

    private void editCard(CardModel card) {
        Intent intent = new Intent(this, ManualEntryActivity.class);
        intent.putExtra("card_name", card.name);
        intent.putExtra("card_uid", card.uid);
        intent.putExtra("card_type", card.cardType);
        intent.putExtra("card_company", card.company);
        intent.putExtra("card_aid", card.aid);
        intent.putExtra("card_atr", card.atr);
        intent.putExtra("card_apdu_response", card.apduSelectResponse);
        intent.putExtra("card_notes", card.notes);
        startActivity(intent);
    }

    private void deleteCard(CardModel card) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Card")
                .setMessage("Delete \"" + card.name + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean ok = cardManager.deleteCard(card.getSafeFilename());
                    if (ok) {
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        loadCards();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void emulateCard(CardModel card) {
        Intent intent = new Intent(this, EmulationActivity.class);
        intent.putExtra("card_filename", card.getSafeFilename());
        startActivity(intent);
    }

    private void exportCard(CardModel card) {
        java.io.File exportDir = new java.io.File(getExternalFilesDir(null), "exported");
        java.io.File exportFile = new java.io.File(exportDir, card.getSafeFilename());
        boolean ok = cardManager.exportCard(card, exportFile);
        if (ok) {
            Toast.makeText(this, "Exported to: " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }
}
