package com.nfc.wallet.util;

import android.content.Context;
import android.util.Log;

import com.nfc.wallet.model.CardModel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages storage of NFC cards as individual plain text files.
 * Each card is saved to its own .txt file for easy inspection and debugging.
 * Storage location: internal files dir / cards /
 */
public class CardStorageManager {

    private static final String TAG = "CardStorageManager";
    private static final String CARDS_DIR = "cards";
    private static final String CARD_EXT = ".txt";

    private final Context context;
    private final File cardsDirectory;

    public CardStorageManager(Context context) {
        this.context = context;
        this.cardsDirectory = new File(context.getFilesDir(), CARDS_DIR);
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        if (!cardsDirectory.exists()) {
            boolean created = cardsDirectory.mkdirs();
            Log.d(TAG, "Cards directory created: " + created + " at " + cardsDirectory.getAbsolutePath());
        }
    }

    /**
     * Saves a card as an individual text file.
     * File name: <label>_<id>.txt (sanitized)
     */
    public boolean saveCard(CardModel card) {
        if (card == null) return false;
        try {
            String fileName = buildFileName(card);
            File cardFile = new File(cardsDirectory, fileName);
            card.setFilePath(cardFile.getAbsolutePath());

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(cardFile))) {
                writer.write(card.toTextFormat());
            }
            Log.d(TAG, "Card saved: " + cardFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving card: " + card.getId(), e);
            return false;
        }
    }

    /**
     * Loads a card from a text file path.
     * Only loads files that are within the cards directory to prevent path traversal.
     */
    public CardModel loadCard(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        File file = new File(filePath);
        // Restrict to files within the cards directory
        try {
            String canonicalPath = file.getCanonicalPath();
            String canonicalCardsDir = cardsDirectory.getCanonicalPath();
            if (!canonicalPath.startsWith(canonicalCardsDir + File.separator)
                    && !canonicalPath.equals(canonicalCardsDir)) {
                Log.w(TAG, "Rejected path outside cards directory: " + canonicalPath);
                return null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not resolve canonical path: " + filePath);
            return null;
        }
        if (!file.exists() || !file.isFile()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            CardModel card = CardModel.fromTextFormat(sb.toString());
            card.setFilePath(file.getAbsolutePath());
            return card;
        } catch (IOException e) {
            Log.e(TAG, "Error loading card from: " + filePath, e);
            return null;
        }
    }

    /**
     * Loads all saved cards from the cards directory.
     */
    public List<CardModel> loadAllCards() {
        List<CardModel> cards = new ArrayList<>();
        File[] files = cardsDirectory.listFiles((dir, name) -> name.endsWith(CARD_EXT));
        if (files == null) return cards;

        for (File file : files) {
            CardModel card = loadCard(file.getAbsolutePath());
            if (card != null) {
                cards.add(card);
            }
        }
        // Sort by timestamp descending (newest first)
        cards.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return cards;
    }

    /**
     * Deletes a saved card file.
     */
    public boolean deleteCard(CardModel card) {
        if (card == null || card.getFilePath().isEmpty()) return false;
        File file = new File(card.getFilePath());
        boolean deleted = file.delete();
        Log.d(TAG, "Card deleted: " + deleted + " - " + card.getFilePath());
        return deleted;
    }

    /**
     * Deletes a saved card file by file path.
     */
    public boolean deleteCard(String filePath) {
        File file = new File(filePath);
        return file.delete();
    }

    /**
     * Updates an existing card file (overwrites).
     */
    public boolean updateCard(CardModel card) {
        if (card == null) return false;
        // If file path is set and exists, overwrite it
        if (!card.getFilePath().isEmpty()) {
            File existing = new File(card.getFilePath());
            if (existing.exists()) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(existing))) {
                    writer.write(card.toTextFormat());
                    Log.d(TAG, "Card updated: " + existing.getAbsolutePath());
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "Error updating card", e);
                    return false;
                }
            }
        }
        // Otherwise save as new
        return saveCard(card);
    }

    /**
     * Returns raw text content of a card file.
     */
    public String getRawCardText(CardModel card) {
        if (card == null || card.getFilePath().isEmpty()) return "";
        File file = new File(card.getFilePath());
        if (!file.exists()) return "";
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading raw card text", e);
            return "";
        }
    }

    /**
     * Returns the cards directory path.
     */
    public String getCardsDirPath() {
        return cardsDirectory.getAbsolutePath();
    }

    /**
     * Returns list of all card file paths.
     */
    public List<String> getAllCardFilePaths() {
        List<String> paths = new ArrayList<>();
        File[] files = cardsDirectory.listFiles((dir, name) -> name.endsWith(CARD_EXT));
        if (files != null) {
            for (File f : files) {
                paths.add(f.getAbsolutePath());
            }
        }
        return paths;
    }

    /**
     * Returns count of saved cards.
     */
    public int getCardCount() {
        File[] files = cardsDirectory.listFiles((dir, name) -> name.endsWith(CARD_EXT));
        return files != null ? files.length : 0;
    }

    /**
     * Builds a sanitized file name for a card.
     */
    private String buildFileName(CardModel card) {
        String label = card.getLabel();
        String sanitizedLabel = label.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitizedLabel.length() > 32) {
            sanitizedLabel = sanitizedLabel.substring(0, 32);
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date(card.getTimestamp()));
        return sanitizedLabel + "_" + timestamp + CARD_EXT;
    }

    /**
     * Exports a card text to a string suitable for sharing.
     */
    public String exportCardToString(CardModel card) {
        return card.toTextFormat();
    }

    /**
     * Imports a card from a raw text string.
     */
    public CardModel importCardFromString(String text) {
        CardModel card = CardModel.fromTextFormat(text);
        // Assign a fresh ID and timestamp to avoid collisions
        card.setId(String.valueOf(System.currentTimeMillis()));
        card.setTimestamp(System.currentTimeMillis());
        saveCard(card);
        return card;
    }
}
