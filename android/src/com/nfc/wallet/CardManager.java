package com.nfc.wallet;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CardManager {
    private static final String TAG = "CardManager";
    private static final String CARDS_DIR = "cards";

    private final File cardsDir;

    public CardManager(Context context) {
        cardsDir = new File(context.getFilesDir(), CARDS_DIR);
        if (!cardsDir.exists()) {
            cardsDir.mkdirs();
        }
    }

    public boolean saveCard(CardModel card) {
        if (card == null) return false;
        String filename = card.getSafeFilename();
        File file = new File(cardsDir, filename);
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(card.toTxt());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "saveCard failed: " + e.getMessage());
            return false;
        }
    }

    public CardModel loadCard(String filename) {
        File file = new File(cardsDir, filename);
        if (!file.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return CardModel.fromTxt(sb.toString());
        } catch (IOException e) {
            Log.e(TAG, "loadCard failed: " + e.getMessage());
            return null;
        }
    }

    public List<CardModel> listCards() {
        List<CardModel> cards = new ArrayList<>();
        File[] files = cardsDir.listFiles();
        if (files == null) return cards;
        for (File f : files) {
            if (f.getName().endsWith(".txt")) {
                CardModel card = loadCard(f.getName());
                if (card != null) cards.add(card);
            }
        }
        return cards;
    }

    public List<String> listCardFilenames() {
        List<String> names = new ArrayList<>();
        File[] files = cardsDir.listFiles();
        if (files == null) return names;
        for (File f : files) {
            if (f.getName().endsWith(".txt")) {
                names.add(f.getName());
            }
        }
        return names;
    }

    public boolean deleteCard(String filename) {
        File file = new File(cardsDir, filename);
        return file.exists() && file.delete();
    }

    public boolean exportCard(CardModel card, File dest) {
        if (card == null || dest == null) return false;
        try {
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            try (FileWriter fw = new FileWriter(dest)) {
                fw.write(card.toTxt());
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "exportCard failed: " + e.getMessage());
            return false;
        }
    }

    public String getRawContent(String filename) {
        File file = new File(cardsDir, filename);
        if (!file.exists()) return "";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    public int getCardCount() {
        File[] files = cardsDir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.getName().endsWith(".txt")) count++;
        }
        return count;
    }
}
