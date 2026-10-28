package com.aiothoroughbred;

public class NameValidator {

    public enum NamingLanguage {
        ENGLISH("English"),
        CHINESE("Chinese"),
        JAPANESE("Japanese");

        private final String displayName;

        NamingLanguage(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String warningMessage;

        public ValidationResult(boolean valid, String warningMessage) {
            this.valid = valid;
            this.warningMessage = warningMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getWarningMessage() {
            return warningMessage;
        }
    }

    public static ValidationResult validate(String input, NamingLanguage language) {
        if (input == null || input.isEmpty()) {
            return new ValidationResult(false, "");
        }

        switch (language) {
            case ENGLISH:
                return validateEnglish(input);
            case CHINESE:
                return validateChinese(input);
            case JAPANESE:
                return validateJapanese(input);
            default:
                return new ValidationResult(false, "Name doesn't fit the naming rules.");
        }
    }

    private static ValidationResult validateEnglish(String input) {
        // Disallow leading/trailing spaces or double spaces
        if (input.startsWith(" ") || input.endsWith(" ") || input.contains("  ")) {
            return new ValidationResult(false, "Name doesn't fit the naming rules (no extra spaces).");
        }

        // Total length: 2 to 18 characters (IFHA / Jockey Club standard)
        if (input.length() < 2 || input.length() > 18) {
            return new ValidationResult(false, "Name doesn't fit the naming rules (2-18 characters).");
        }

        // Only English letters, spaces, and apostrophes allowed
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean isLetter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean isSpace = (c == ' ');
            boolean isApostrophe = (c == '\'' || c == '’');
            if (!isLetter && !isSpace && !isApostrophe) {
                return new ValidationResult(false, "Name doesn't fit the naming rules (English letters only).");
            }
        }

        // Maximum 4 words (HKJC & International rule)
        String[] words = input.split(" ");
        if (words.length < 1 || words.length > 4) {
            return new ValidationResult(false, "Name doesn't fit the naming rules (max 4 words).");
        }

        return new ValidationResult(true, "");
    }

    private static ValidationResult validateChinese(String input) {
        // HKJC rule: 2 to 4 Chinese characters
        int codePointCount = input.codePointCount(0, input.length());
        if (codePointCount < 2 || codePointCount > 4) {
            return new ValidationResult(false, "Name doesn't fit the naming rules (2-4 Chinese characters).");
        }

        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            boolean isCjk = (cp >= 0x4E00 && cp <= 0x9FFF)
                    || (cp >= 0x3400 && cp <= 0x4DBF)
                    || (cp >= 0x20000 && cp <= 0x2A6DF);
            if (!isCjk) {
                return new ValidationResult(false, "Name doesn't fit the naming rules (Chinese characters only).");
            }
            i += Character.charCount(cp);
        }

        return new ValidationResult(true, "");
    }

    private static ValidationResult validateJapanese(String input) {
        // Official rule: 2 to 9 Katakana characters only (no Hiragana, no Kanji, no spaces)
        if (input.length() < 2 || input.length() > 9) {
            return new ValidationResult(false, "Name doesn't fit the naming rules (2-9 Katakana characters).");
        }

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Full-width Katakana letters (ァ-ヶ: \u30A1-\u30F6) and prolonged sound mark (ー: \u30FC)
            boolean isKatakana = (c >= '\u30A1' && c <= '\u30F6') || (c == '\u30FC');
            if (!isKatakana) {
                return new ValidationResult(false, "Name doesn't fit the naming rules (Katakana only, no Kanji/Hiragana).");
            }
        }

        return new ValidationResult(true, "");
    }
}