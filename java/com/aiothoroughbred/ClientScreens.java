package com.aiothoroughbred;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClientScreens {

    public static void handleOpenStarterConsole(ModNetwork.OpenStarterConsolePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new StarterConsoleScreen(msg));
    }

    public static void handleOpenRacingConsole(ModNetwork.OpenRacingConsolePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new RacingConsoleScreen(msg));
    }

    public static void handleOpenDeathsScreen(ModNetwork.OpenDeathsScreenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new DeceasedHorsesScreen(msg.entries));
    }

    public static void handleOpenScheduleScreen(ModNetwork.OpenScheduleScreenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RacingScheduleScreen existing) {
            existing.updateData(msg);
        } else {
            mc.setScreen(new RacingScheduleScreen(msg));
        }
    }

    public static void handleOpenTimeFlowScreen(int currentMode, int customDays, boolean isFirstJoinPrompt) {
        handleOpenTimeFlowScreen(currentMode, customDays, isFirstJoinPrompt, false);
    }

    public static void handleOpenTimeFlowScreen(int currentMode, int customDays, boolean isFirstJoinPrompt, boolean alreadyConfigured) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new WorldTimeFlowScreen(currentMode, customDays, isFirstJoinPrompt, alreadyConfigured));
    }

    public static void handleOpenPedigreeScreen(CompoundTag horseDataTag) {
        Minecraft mc = Minecraft.getInstance();
        ThoroughbredData data = new ThoroughbredData();
        data.loadFromNBT(horseDataTag);
        Screen parent = (mc.screen instanceof ThoroughbredStatScreen) ? mc.screen : null;
        mc.setScreen(new PedigreeTreeScreen(data, parent));
    }

    public static void handleOpenGeldingScreen(int horseId, String horseName) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new GeldingConfirmationScreen(horseId, horseName));
    }

    public static void handleOpenOwnerScreen(ModNetwork.OpenOwnerScreenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HorseOwnerScreen existing) {
            existing.updateFromPacket(msg);
        } else {
            mc.setScreen(new HorseOwnerScreen(msg));
        }
    }

    public static void handleOpenStudbookScreen(ModNetwork.OpenStudbookScreenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new StudbookBrowserScreen(msg));
    }

    public static void handleOpenRaceResultsScreen(ModNetwork.OpenRaceResultsScreenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RaceResultsAdminScreen existing) {
            existing.updateFromPacket(msg);
        } else {
            mc.setScreen(new RaceResultsAdminScreen(msg));
        }
    }

    public static void handleOpenScreen(int horseId, boolean alreadyRegistered, CompoundTag horseDataTag) {
        Minecraft mc = Minecraft.getInstance();
        if (alreadyRegistered) {
            ThoroughbredData data = new ThoroughbredData();
            data.loadFromNBT(horseDataTag);
            Screen parent = (horseDataTag != null && horseDataTag.getBoolean("_FromStudbookBrowser") && mc.screen instanceof StudbookBrowserScreen)
                    ? mc.screen
                    : null;
            mc.setScreen(new ThoroughbredStatScreen(horseId, data, parent));
        } else {
            ThoroughbredData pendingFoal = null;
            String crown1 = "";
            boolean crown1Prefix = true;
            String crown2 = "";
            boolean crown2Prefix = true;
            if (horseDataTag != null && !horseDataTag.isEmpty()) {
                crown1 = horseDataTag.getString("_OwnerCrown1");
                crown1Prefix = !horseDataTag.contains("_OwnerCrown1Prefix") || horseDataTag.getBoolean("_OwnerCrown1Prefix");
                crown2 = horseDataTag.getString("_OwnerCrown2");
                crown2Prefix = !horseDataTag.contains("_OwnerCrown2Prefix") || horseDataTag.getBoolean("_OwnerCrown2Prefix");
                ThoroughbredData temp = new ThoroughbredData();
                temp.loadFromNBT(horseDataTag);
                if (temp.isBredFoalPendingName()) {
                    pendingFoal = temp;
                }
            }
            mc.setScreen(new RegistrationScreen(horseId, pendingFoal, crown1, crown1Prefix, crown2, crown2Prefix));
        }
    }

    public static void handleRegistrationResult(boolean success, String errorMessage, int horseId, CompoundTag horseDataTag) {
        Minecraft mc = Minecraft.getInstance();
        if (success) {
            ThoroughbredData data = new ThoroughbredData();
            data.loadFromNBT(horseDataTag);
            mc.setScreen(new ThoroughbredStatScreen(horseId, data));
        } else if (mc.screen instanceof RegistrationScreen regScreen) {
            regScreen.showServerError(errorMessage);
        }
    }

    // =========================================================================
    // SCREEN 1: Thoroughbred Registration GUI (Language, Live Name Check, Gender)
    // =========================================================================
    public static class RegistrationScreen extends Screen {
        private final int horseId;
        private final ThoroughbredData bredFoalData;
        private final String crownName1;
        private final boolean crown1Prefix;
        private final String crownName2;
        private final boolean crown2Prefix;
        private NameValidator.NamingLanguage selectedLanguage = NameValidator.NamingLanguage.ENGLISH;
        private String selectedGender = "Male";

        private EditBox nameBox;
        private Button englishBtn;
        private Button chineseBtn;
        private Button japaneseBtn;
        private Button crown1Btn;
        private Button crown2Btn;
        private Button maleBtn;
        private Button femaleBtn;
        private Button registerBtn;

        private String currentWarning = "";
        private boolean isNameValid = false;

        public RegistrationScreen(int horseId) {
            this(horseId, null, "", true, "", true);
        }

        public RegistrationScreen(int horseId, ThoroughbredData bredFoalData) {
            this(horseId, bredFoalData, "", true, "", true);
        }

        public RegistrationScreen(int horseId, ThoroughbredData bredFoalData,
                                  String crownName1, boolean crown1Prefix,
                                  String crownName2, boolean crown2Prefix) {
            super(Component.literal("Thoroughbred Registration"));
            this.horseId = horseId;
            this.bredFoalData = bredFoalData;
            this.crownName1 = crownName1 != null ? crownName1.trim() : "";
            this.crown1Prefix = crown1Prefix;
            this.crownName2 = crownName2 != null ? crownName2.trim() : "";
            this.crown2Prefix = crown2Prefix;
            if (bredFoalData != null) {
                this.selectedGender = "Female".equalsIgnoreCase(bredFoalData.getGender()) ? "Female" : "Male";
            }
        }

        private boolean hasAnyCrownNames() {
            return !this.crownName1.isEmpty() || !this.crownName2.isEmpty();
        }

        private void applyCrownNameToInput(String crown, boolean isPrefix) {
            if (this.nameBox == null || crown == null || crown.isBlank()) return;
            String cur = this.nameBox.getValue() != null ? this.nameBox.getValue().trim() : "";
            String cTrim = crown.trim();
            boolean cjk = this.selectedLanguage != NameValidator.NamingLanguage.ENGLISH;
            String sep = cjk ? "" : " ";
            String updated;
            if (cur.isEmpty()) {
                updated = cTrim + (isPrefix && !cjk ? " " : "");
            } else if (isPrefix) {
                if (cur.toLowerCase(Locale.ROOT).startsWith(cTrim.toLowerCase(Locale.ROOT))) {
                    return;
                }
                updated = cTrim + sep + cur;
            } else {
                if (cur.toLowerCase(Locale.ROOT).endsWith(cTrim.toLowerCase(Locale.ROOT))) {
                    return;
                }
                updated = cur + sep + cTrim;
            }
            this.nameBox.setValue(updated);
            this.nameBox.moveCursorToEnd();
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int panelH = hasAnyCrownNames() ? 228 : 210;
            int topY = (this.height - panelH) / 2;

            int langY = topY + 34;
            this.englishBtn = AutoFitButton.create(Component.literal("English"), btn -> selectLanguage(NameValidator.NamingLanguage.ENGLISH))
                    .bounds(centerX - 140, langY, 90, 20).build();
            this.chineseBtn = AutoFitButton.create(Component.literal("Chinese"), btn -> selectLanguage(NameValidator.NamingLanguage.CHINESE))
                    .bounds(centerX - 45, langY, 90, 20).build();
            this.japaneseBtn = AutoFitButton.create(Component.literal("Japanese"), btn -> selectLanguage(NameValidator.NamingLanguage.JAPANESE))
                    .bounds(centerX + 50, langY, 90, 20).build();

            this.addRenderableWidget(this.englishBtn);
            this.addRenderableWidget(this.chineseBtn);
            this.addRenderableWidget(this.japaneseBtn);

            int boxY = topY + 74;
            String previousText = (this.nameBox != null) ? this.nameBox.getValue() : "";
            this.nameBox = new EditBox(this.font, centerX - 110, boxY, 220, 20, Component.literal("Horse Name"));
            this.nameBox.setMaxLength(64);
            this.nameBox.setValue(previousText);
            this.nameBox.setResponder(this::onNameChanged);
            this.addRenderableWidget(this.nameBox);

            int extraCrownOffset = 0;
            if (hasAnyCrownNames()) {
                extraCrownOffset = 20;
                int crownY = boxY + 22;
                boolean has1 = !this.crownName1.isEmpty();
                boolean has2 = !this.crownName2.isEmpty();
                if (has1 && has2) {
                    String lbl1 = "§6👑 " + (this.crown1Prefix ? (this.crownName1 + " …") : ("… " + this.crownName1));
                    String lbl2 = "§6👑 " + (this.crown2Prefix ? (this.crownName2 + " …") : ("… " + this.crownName2));
                    this.crown1Btn = AutoFitButton.create(Component.literal(lbl1), btn -> applyCrownNameToInput(this.crownName1, this.crown1Prefix))
                            .bounds(centerX - 110, crownY, 108, 18).build();
                    this.crown2Btn = AutoFitButton.create(Component.literal(lbl2), btn -> applyCrownNameToInput(this.crownName2, this.crown2Prefix))
                            .bounds(centerX + 2, crownY, 108, 18).build();
                    this.addRenderableWidget(this.crown1Btn);
                    this.addRenderableWidget(this.crown2Btn);
                } else if (has1) {
                    String lbl1 = "§6👑 Insert Crown Name: §f" + (this.crown1Prefix ? (this.crownName1 + " [Prefix]") : ("[Suffix] " + this.crownName1));
                    this.crown1Btn = AutoFitButton.create(Component.literal(lbl1), btn -> applyCrownNameToInput(this.crownName1, this.crown1Prefix))
                            .bounds(centerX - 110, crownY, 220, 18).build();
                    this.addRenderableWidget(this.crown1Btn);
                } else {
                    String lbl2 = "§6👑 Insert Crown Name: §f" + (this.crown2Prefix ? (this.crownName2 + " [Prefix]") : ("[Suffix] " + this.crownName2));
                    this.crown2Btn = AutoFitButton.create(Component.literal(lbl2), btn -> applyCrownNameToInput(this.crownName2, this.crown2Prefix))
                            .bounds(centerX - 110, crownY, 220, 18).build();
                    this.addRenderableWidget(this.crown2Btn);
                }
            }

            int genderY = topY + 132 + extraCrownOffset;
            this.maleBtn = AutoFitButton.create(Component.literal("Male"), btn -> selectGender("Male"))
                    .bounds(centerX - 105, genderY, 100, 20).build();
            this.femaleBtn = AutoFitButton.create(Component.literal("Female"), btn -> selectGender("Female"))
                    .bounds(centerX + 5, genderY, 100, 20).build();

            if (this.bredFoalData != null) {
                this.maleBtn.active = false;
                this.femaleBtn.active = false;
            }
            this.addRenderableWidget(this.maleBtn);
            this.addRenderableWidget(this.femaleBtn);

            int bottomY = topY + 174 + extraCrownOffset;
            this.registerBtn = AutoFitButton.create(Component.literal("Register Thoroughbred"), btn -> submitRegistration())
                    .bounds(centerX - 115, bottomY, 140, 20).build();
            Button cancelBtn = AutoFitButton.create(Component.literal("Cancel"), btn -> this.onClose())
                    .bounds(centerX + 35, bottomY, 80, 20).build();

            this.addRenderableWidget(this.registerBtn);
            this.addRenderableWidget(cancelBtn);

            this.setInitialFocus(this.nameBox);
            updateButtonLabels();
            onNameChanged(this.nameBox.getValue());
        }

        private void selectLanguage(NameValidator.NamingLanguage lang) {
            this.selectedLanguage = lang;
            updateButtonLabels();
            onNameChanged(this.nameBox.getValue());
        }

        private void selectGender(String gender) {
            this.selectedGender = gender;
            updateButtonLabels();
        }

        private void updateButtonLabels() {
            this.englishBtn.setMessage(Component.literal(this.selectedLanguage == NameValidator.NamingLanguage.ENGLISH ? "§e[English]" : "English"));
            this.chineseBtn.setMessage(Component.literal(this.selectedLanguage == NameValidator.NamingLanguage.CHINESE ? "§e[Chinese]" : "Chinese"));
            this.japaneseBtn.setMessage(Component.literal(this.selectedLanguage == NameValidator.NamingLanguage.JAPANESE ? "§e[Japanese]" : "Japanese"));

            this.maleBtn.setMessage(Component.literal("Male".equals(this.selectedGender) ? "§b[Male (Stallion)]" : "Male (Stallion)"));
            this.femaleBtn.setMessage(Component.literal("Female".equals(this.selectedGender) ? "§d[Female (Mare)]" : "Female (Mare)"));
        }

        private void onNameChanged(String currentText) {
            if (currentText == null || currentText.isEmpty()) {
                this.isNameValid = false;
                this.currentWarning = "";
                this.nameBox.setTextColor(0xFFFFFF);
                if (this.registerBtn != null) {
                    this.registerBtn.active = false;
                }
                return;
            }

            NameValidator.ValidationResult result = NameValidator.validate(currentText, this.selectedLanguage);
            this.isNameValid = result.isValid();
            if (!result.isValid()) {
                this.nameBox.setTextColor(0xFF5555);
                this.currentWarning = result.getWarningMessage();
            } else {
                this.nameBox.setTextColor(0xFFFFFF);
                this.currentWarning = "";
            }

            if (this.registerBtn != null) {
                this.registerBtn.active = this.isNameValid;
            }
        }

        public void showServerError(String message) {
            this.isNameValid = false;
            this.currentWarning = message;
            if (this.nameBox != null) {
                this.nameBox.setTextColor(0xFF5555);
            }
            if (this.registerBtn != null) {
                this.registerBtn.active = false;
            }
        }

        private void submitRegistration() {
            if (!this.isNameValid || this.nameBox == null) {
                return;
            }
            String enteredName = this.nameBox.getValue();
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.RegisterHorsePacket(
                    this.horseId,
                    enteredName,
                    this.selectedLanguage.ordinal(),
                    this.selectedGender
            ));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int extraCrownOffset = hasAnyCrownNames() ? 20 : 0;
            int panelH = 206 + extraCrownOffset;
            int topY = (this.height - (210 + extraCrownOffset)) / 2;

            graphics.fill(centerX - 165, topY, centerX + 165, topY + panelH, 0xCC10141C);
            graphics.fill(centerX - 165, topY, centerX + 165, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - 165, topY + panelH - 2, centerX + 165, topY + panelH, 0xFFD4AF37);

            graphics.drawCenteredString(this.font, "§6§l=== Thoroughbred Registry Form ===", centerX, topY + 8, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "1. Select Naming Language Rule:", centerX, topY + 22, 0xCCCCCC);
            graphics.drawCenteredString(this.font, "2. Enter Unique Horse Name (Optional Crown Buttons Below):", centerX, topY + 61, 0xCCCCCC);

            if (!this.currentWarning.isEmpty()) {
                graphics.drawCenteredString(this.font, this.currentWarning, centerX, topY + 100 + extraCrownOffset, 0xFF5555);
            } else if (this.isNameValid) {
                graphics.drawCenteredString(this.font, "Name fits naming rules", centerX, topY + 100 + extraCrownOffset, 0x55FF55);
            }

            if (this.bredFoalData != null) {
                graphics.drawCenteredString(this.font, "3. Inherited Sex (Potential: " + this.bredFoalData.getHereditaryPoints() + "% | 100/100 HP):", centerX, topY + 118 + extraCrownOffset, 0xFFAA00);
            } else {
                graphics.drawCenteredString(this.font, "3. Select Gender:", centerX, topY + 118 + extraCrownOffset, 0xCCCCCC);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // TEXT WRAPPING & SCALING HELPERS FOR LARGE HUD / STAT / PEDIGREE SCREENS
    // =========================================================================
    public static List<String> wrapFormattedTextToWidth(net.minecraft.client.gui.Font font, String rawText, int maxWidth) {
        String text = ThoroughbredData.applyUiTranslations(rawText);
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        int safeWidth = Math.max(40, maxWidth);
        if (font.width(text) <= safeWidth) {
            out.add(text);
            return out;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        String activeFormat = "";

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? (activeFormat + word) : (currentLine + " " + word);
            if (font.width(candidate) <= safeWidth) {
                if (currentLine.isEmpty()) {
                    currentLine.append(activeFormat).append(word);
                } else {
                    currentLine.append(" ").append(word);
                }
                activeFormat = extractLastFormatCodes(currentLine.toString());
            } else {
                if (!currentLine.isEmpty()) {
                    out.add(currentLine.toString());
                    activeFormat = extractLastFormatCodes(currentLine.toString());
                    currentLine.setLength(0);
                }
                String formattedWord = activeFormat + word;
                if (font.width(formattedWord) <= safeWidth) {
                    currentLine.append(formattedWord);
                } else {
                    // Split very long single token across lines preserving format codes
                    StringBuilder chunk = new StringBuilder(activeFormat);
                    for (int i = 0; i < word.length(); i++) {
                        char c = word.charAt(i);
                        if (c == '§' && i + 1 < word.length()) {
                            chunk.append(c).append(word.charAt(i + 1));
                            i++;
                            continue;
                        }
                        if (font.width(chunk.toString() + c) > safeWidth && chunk.length() > activeFormat.length()) {
                            out.add(chunk.toString());
                            activeFormat = extractLastFormatCodes(chunk.toString());
                            chunk.setLength(0);
                            chunk.append(activeFormat);
                        }
                        chunk.append(c);
                    }
                    if (chunk.length() > activeFormat.length()) {
                        currentLine.append(chunk);
                    }
                }
                activeFormat = extractLastFormatCodes(currentLine.toString());
            }
        }
        if (!currentLine.isEmpty()) {
            out.add(currentLine.toString());
        }
        if (out.isEmpty()) {
            out.add(text);
        }
        return out;
    }

    private static String extractLastFormatCodes(String s) {
        String colorCode = "";
        StringBuilder styleCodes = new StringBuilder();
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '§') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    colorCode = "§" + code;
                    styleCodes.setLength(0);
                } else if (code == 'r') {
                    colorCode = "";
                    styleCodes.setLength(0);
                } else if (code >= 'k' && code <= 'o') {
                    styleCodes.append("§").append(code);
                }
                i++;
            }
        }
        return colorCode + styleCodes;
    }

    public static void drawScaledCenteredString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                                String rawText, int centerX, int y, int maxWidth, int color) {
        String text = ThoroughbredData.applyUiTranslations(rawText);
        int w = font.width(text);
        if (w <= maxWidth || w <= 0) {
            graphics.drawCenteredString(font, text, centerX, y, color);
            return;
        }
        float scale = Math.max(0.45F, (float) maxWidth / (float) w);
        graphics.pose().pushPose();
        graphics.pose().translate((float) centerX, (float) y + (1.0F - scale) * 4.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    public static void drawScaledLeftString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                            String rawText, int x, int y, int maxWidth, int color) {
        String text = ThoroughbredData.applyUiTranslations(rawText);
        int w = font.width(text);
        if (w <= maxWidth || w <= 0) {
            graphics.drawString(font, text, x, y, color);
            return;
        }
        float scale = Math.max(0.45F, (float) maxWidth / (float) w);
        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y + (1.0F - scale) * 4.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    /**
     * Universal auto-fitting button widget that dynamically scales (or wraps onto 2 micro-lines)
     * any button label so no text now or in the future is ever cut off or truncated.
     */
    public static class AutoFitButton extends Button {
        public AutoFitButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public static Builder create(Component message, OnPress onPress) {
            return new Builder(message, onPress);
        }

        public static class Builder {
            private final Component message;
            private final OnPress onPress;
            private int x;
            private int y;
            private int width = 150;
            private int height = 20;

            public Builder(Component message, OnPress onPress) {
                this.message = message;
                this.onPress = onPress;
            }

            public Builder bounds(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
                return this;
            }

            public Builder pos(int x, int y) {
                this.x = x;
                this.y = y;
                return this;
            }

            public Builder size(int width, int height) {
                this.width = width;
                this.height = height;
                return this;
            }

            public AutoFitButton build() {
                return new AutoFitButton(this.x, this.y, this.width, this.height, this.message, this.onPress);
            }
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Component origMsg = this.getMessage();
            this.setMessage(Component.empty());
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            this.setMessage(origMsg);

            if (origMsg == null) return;
            String raw = origMsg.getString();
            if (raw == null || raw.isEmpty()) return;

            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String text = ThoroughbredData.applyUiTranslations(raw);
            int availW = Math.max(8, this.getWidth() - 6);
            int textW = font.width(text);
            int baseColor = this.active ? 0xFFFFFF : 0xA0A0A0;
            int centerX = this.getX() + this.getWidth() / 2;

            if (textW <= availW || textW <= 0) {
                int textY = this.getY() + (this.getHeight() - 8) / 2;
                graphics.drawCenteredString(font, text, centerX, textY, baseColor);
                return;
            }

            float scale = (float) availW / (float) textW;
            if (scale >= 0.58F || this.getHeight() < 15) {
                float clampedScale = Math.max(0.45F, scale);
                float drawY = this.getY() + (this.getHeight() - 8.0F * clampedScale) * 0.5F;
                graphics.pose().pushPose();
                graphics.pose().translate((float) centerX, drawY, 0.0F);
                graphics.pose().scale(clampedScale, clampedScale, 1.0F);
                graphics.drawCenteredString(font, text, 0, 0, baseColor);
                graphics.pose().popPose();
            } else {
                float twoLineScale = Math.min(0.72F, Math.max(0.50F, (float) this.getHeight() / 22.0F));
                int wrapW = Math.max(20, Math.round(availW / twoLineScale));
                List<String> lines = wrapFormattedTextToWidth(font, text, wrapW);
                String line1 = lines.get(0);
                String line2 = lines.size() > 1 ? String.join(" ", lines.subList(1, lines.size())) : "";
                int maxLineW = Math.max(font.width(line1), font.width(line2));
                if (maxLineW > wrapW && maxLineW > 0) {
                    twoLineScale = Math.max(0.42F, (float) availW / (float) maxLineW);
                }
                float totalH = line2.isEmpty() ? (8.0F * twoLineScale) : (17.0F * twoLineScale);
                float startY = this.getY() + Math.max(1.0F, (this.getHeight() - totalH) * 0.5F);
                graphics.pose().pushPose();
                graphics.pose().translate((float) centerX, startY, 0.0F);
                graphics.pose().scale(twoLineScale, twoLineScale, 1.0F);
                graphics.drawCenteredString(font, line1, 0, 0, baseColor);
                if (!line2.isEmpty()) {
                    graphics.drawCenteredString(font, line2, 0, 9, baseColor);
                }
                graphics.pose().popPose();
            }
        }
    }

    // =========================================================================
    // SCREEN 2: Scrollable Thoroughbred Stat Card + Career Record & Finishes
    // =========================================================================
    public static class ThoroughbredStatScreen extends Screen {
        private final int horseId;
        private final ThoroughbredData data;
        private final Screen parentScreen;
        private int scrollPixels = 0;

        private Button weightedTackBtn;
        private Button blinkersBtn;
        private Button pedigreeBtn;
        private Button scrollUpBtn;
        private Button scrollDownBtn;

        public ThoroughbredStatScreen(int horseId, ThoroughbredData data) {
            this(horseId, data, null);
        }

        public ThoroughbredStatScreen(int horseId, ThoroughbredData data, Screen parentScreen) {
            super(Component.literal("Thoroughbred Profile"));
            this.horseId = horseId;
            this.data = data;
            this.parentScreen = parentScreen;
        }

        private int getCardHalfW() {
            return Math.min((this.width - 12) / 2, 218); // Up to 436px wide!
        }

        private int getCardHeight() {
            return Math.min(this.height - 10, 248); // Up to 248px tall!
        }

        private int getHeaderExtraLines() {
            int maxHeaderW = getCardHalfW() * 2 - 20;
            String ownerTag = this.data.hasOwner()
                    ? ("  §7|  §bOwner: §f" + this.data.getOwnerName() + (this.data.isLocked() ? " §c[LOCKED]" : ""))
                    : "  §7|  §8[Unclaimed]";
            String combinedTitle = "§6§l" + this.data.getName() + ownerTag;
            if (this.font.width(combinedTitle) <= maxHeaderW) {
                return 0;
            }
            List<String> nameLines = wrapFormattedTextToWidth(this.font, "§6§l" + this.data.getName(), maxHeaderW);
            return Math.max(1, nameLines.size());
        }

        private int getViewportTopOffset() {
            return 45 + (getHeaderExtraLines() * 10);
        }

        private int getViewportBottomOffset() {
            return getCardHeight() - 33;
        }

        private int getViewportHeight() {
            return Math.max(80, getViewportBottomOffset() - getViewportTopOffset());
        }

        private int getMaxTextWidth() {
            return (getCardHalfW() * 2) - 44;
        }

        private String resolveHorseCoatDisplay() {
            if (this.data.isPureWhiteCoat()) {
                return "§f§lPure White";
            }
            if (this.minecraft != null && this.minecraft.level != null) {
                net.minecraft.world.entity.Entity e = this.minecraft.level.getEntity(this.horseId);
                if (e instanceof net.minecraft.world.entity.animal.horse.Horse h) {
                    String varName = switch (h.getVariant()) {
                        case BROWN -> "Bay";
                        case DARK_BROWN -> "Dark Bay";
                        case CHESTNUT -> "Chestnut";
                        case BLACK -> "Black";
                        case WHITE -> "Gray";
                        case GRAY -> "Dapple Gray";
                        case CREAMY -> "Creamy";
                    };
                    return "§f" + varName;
                }
            }
            return "§7Standard";
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int cardHalfW = getCardHalfW();
            int cardH = getCardHeight();
            int topY = (this.height - cardH) / 2;
            int viewportTop = topY + getViewportTopOffset();
            int viewportBottom = topY + getViewportBottomOffset();
            int btnY = topY + cardH - 26;

            int leftBtnEdge = centerX - cardHalfW + 8;
            int totalBtnSpan = (cardHalfW * 2) - 16;
            int w1 = (int) (totalBtnSpan * 0.28);
            int w2 = (int) (totalBtnSpan * 0.25);
            int w3 = (int) (totalBtnSpan * 0.24);
            int w4 = totalBtnSpan - w1 - w2 - w3 - 12;

            this.weightedTackBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.data.setWeightedTack(!this.data.hasWeightedTack());
                updateGearButtons();
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleTrainingGearPacket(
                        this.horseId, this.data.hasWeightedTack(), this.data.hasBlinkersHood()
                ));
            }).bounds(leftBtnEdge, btnY, w1, 20).build();

            this.blinkersBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.data.setBlinkersHood(!this.data.hasBlinkersHood());
                updateGearButtons();
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleTrainingGearPacket(
                        this.horseId, this.data.hasWeightedTack(), this.data.hasBlinkersHood()
                ));
            }).bounds(leftBtnEdge + w1 + 4, btnY, w2, 20).build();

            this.pedigreeBtn = AutoFitButton.create(Component.literal("§6📜 " + ThoroughbredData.tr("Pedigree", "血統表", "血统表", "血統表")), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new PedigreeTreeScreen(this.data, this));
                }
            }).bounds(leftBtnEdge + w1 + w2 + 8, btnY, w3, 20).build();

            Button closeBtn = AutoFitButton.create(
                    Component.literal(this.parentScreen != null
                            ? ("◄ " + ThoroughbredData.tr("Studbook", "血統書", "血统书", "血統書"))
                            : ThoroughbredData.tr("Close", "關閉", "关闭", "閉じる")),
                    btn -> {
                        if (this.parentScreen != null && this.minecraft != null) {
                            this.minecraft.setScreen(this.parentScreen);
                        } else {
                            this.onClose();
                        }
                    })
                    .bounds(leftBtnEdge + w1 + w2 + w3 + 12, btnY, w4, 20).build();
            if (this.horseId < 0) {
                this.weightedTackBtn.active = false;
                this.blinkersBtn.active = false;
            }

            int scrollBtnX = centerX + cardHalfW - 22;
            this.scrollUpBtn = AutoFitButton.create(Component.literal("▲"), btn -> scrollByPixels(-24))
                    .bounds(scrollBtnX, viewportTop + 2, 18, 18).build();
            this.scrollDownBtn = AutoFitButton.create(Component.literal("▼"), btn -> scrollByPixels(24))
                    .bounds(scrollBtnX, viewportBottom - 20, 18, 18).build();

            this.addRenderableWidget(this.weightedTackBtn);
            this.addRenderableWidget(this.blinkersBtn);
            this.addRenderableWidget(this.pedigreeBtn);
            this.addRenderableWidget(closeBtn);
            this.addRenderableWidget(this.scrollUpBtn);
            this.addRenderableWidget(this.scrollDownBtn);

            updateGearButtons();
            updateScrollButtons();
        }

        private int renderOrMeasureContent(GuiGraphics graphics, int leftX, int rightContentX, int contentCenterX, int startY, boolean draw) {
            int maxTextW = rightContentX - (leftX + 6);
            int curY = startY;

            if (draw) {
                drawStatRow(graphics, leftX, rightContentX, curY,      ThoroughbredData.tr("Speed", "速度", "速度", "スピード"),   this.data.getSpeedCurrent(),   this.data.getSpeedMax(),   0xFF55B4FF);
                drawStatRow(graphics, leftX, rightContentX, curY + 14, ThoroughbredData.tr("Stamina", "耐力", "耐力", "スタミナ"), this.data.getStaminaCurrent(), this.data.getStaminaMax(), 0xFFFF9933);
                drawStatRow(graphics, leftX, rightContentX, curY + 28, ThoroughbredData.tr("Power", "力量", "力量", "パワー"),   this.data.getPowerCurrent(),   this.data.getPowerMax(),   0xFFFF5555);
                drawStatRow(graphics, leftX, rightContentX, curY + 42, ThoroughbredData.tr("Guts", "根性", "根性", "根性"),    this.data.getGutsCurrent(),    this.data.getGutsMax(),    0xFFFF66B2);
                drawStatRow(graphics, leftX, rightContentX, curY + 56, ThoroughbredData.tr("Wits", "智力", "智力", "賢さ"),    this.data.getWitsCurrent(),    this.data.getWitsMax(),    0xFF55FF7F);

                graphics.drawCenteredString(this.font, "§6= " + ThoroughbredData.tr("TRACK APTITUDE", "場地適性", "场地适性", "馬場適性") + " =", contentCenterX, curY + 74, 0xFFFFFF);
                graphics.drawCenteredString(this.font, this.data.getTrackAptitudeBar(), contentCenterX, curY + 86, 0x55FF55);

                graphics.drawCenteredString(this.font, "§6= " + ThoroughbredData.tr("DISTANCE APTITUDE", "距離適性", "距离适性", "距離適性") + " =", contentCenterX, curY + 102, 0xFFFFFF);
                graphics.drawCenteredString(this.font, this.data.getDistanceAptitudeBar(), contentCenterX, curY + 114, 0x55FFFF);

                graphics.drawCenteredString(this.font, "§6= " + ThoroughbredData.tr("RUNNING STRATEGY", "跑法腳質", "跑法脚质", "脚質適性") + " =", contentCenterX, curY + 130, 0xFFFFFF);
                graphics.drawCenteredString(this.font, this.data.getStrategyAptitudeBar(), contentCenterX, curY + 142, 0x55FF55);

                String bonusInfo = String.format(Locale.ROOT,
                        "§7Bond/Temp EXP: §e%.2fx §7| Mood EXP: §e%.2fx §7| Scroll ▼ for Health & Finishes",
                        this.data.getTemperamentBondExpMultiplier(), this.data.getMoodExpMultiplier());
                drawScaledCenteredString(graphics, this.font, bonusInfo, contentCenterX, curY + 158, maxTextW, 0xAAAAAA);
            }
            curY += 172;

            // =================================================================
            // SECTION: HEALTH, AILMENTS & INJURIES (Multi-line wrapped!)
            // =================================================================
            if (draw) {
                graphics.fill(leftX + 6, curY, rightContentX, curY + 1, 0x44D4AF37);
                graphics.drawCenteredString(this.font, "§6§l= " + ThoroughbredData.tr("HEALTH, AILMENTS & INJURIES", "健康、疾病與傷患", "健康、疾病与伤患", "健康・疾病・故障状態") + " =", contentCenterX, curY + 5, 0xFFFFFF);
            }
            curY += 17;

            List<String> ailmentLines = wrapFormattedTextToWidth(this.font, "§eAilments: " + this.data.getFormattedAilmentsSummary(), maxTextW);
            for (String ln : ailmentLines) {
                if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                curY += 12;
            }

            String careerStatusBadge = this.data.hasPoorPrognosis()
                    ? "§4§lPOOR PROGNOSIS"
                    : (this.data.isCareerEnded() ? "§c§lRETIRED FROM INJURY"
                    : (this.data.isRetired() ? "§6§lRETIRED"
                    : (this.data.isFoal() ? "§e§lFOAL (0yo — Growing)"
                    : (this.data.isYearling() ? "§6§lYEARLING (1yo — Unbroken)" : "§aActive (2yo+)"))));
            String combinedInjStatus = "§eInjury: " + this.data.getFormattedInjurySummary() + "  §7|  §eStatus: " + careerStatusBadge;
            if (this.font.width(combinedInjStatus) <= maxTextW) {
                if (draw) graphics.drawCenteredString(this.font, combinedInjStatus, contentCenterX, curY, 0xFFFFFF);
                curY += 14;
            } else {
                for (String ln : wrapFormattedTextToWidth(this.font, "§eInjury: " + this.data.getFormattedInjurySummary(), maxTextW)) {
                    if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                    curY += 12;
                }
                for (String ln : wrapFormattedTextToWidth(this.font, "§eStatus: " + careerStatusBadge, maxTextW)) {
                    if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                    curY += 12;
                }
                curY += 2;
            }

            // =================================================================
            // SECTION: GENETICS, TRAITS & BREEDING (Multi-line wrapped!)
            // =================================================================
            if (draw) {
                graphics.fill(leftX + 6, curY, rightContentX, curY + 1, 0x44D4AF37);
                graphics.drawCenteredString(this.font, "§6§l= " + ThoroughbredData.tr("GENETICS, TRAITS & BREEDING", "遺傳基因、特性與配種", "遗传基因、特性与配种", "遺伝・特性・繁殖情報") + " =", contentCenterX, curY + 5, 0xFFFFFF);
            }
            curY += 17;

            String hpAndCoatLine = "§e" + ThoroughbredData.tr("Hereditary Potential", "遺傳潛能", "遗传潜能", "遺伝ポテンシャル") + ": " + this.data.getFormattedHereditaryPoints()
                    + "  §7|  §e" + ThoroughbredData.tr("Coat", "毛色", "毛色", "毛色") + ": " + resolveHorseCoatDisplay();
            for (String ln : wrapFormattedTextToWidth(this.font, hpAndCoatLine, maxTextW)) {
                if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                curY += 12;
            }

            String crossLine = "§e" + ThoroughbredData.tr("Cross", "交配模式", "交配模式", "配合") + ": §f" + this.data.getInbreedingStatus();
            for (String ln : wrapFormattedTextToWidth(this.font, crossLine, maxTextW)) {
                if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                curY += 12;
            }

            String traitsFull = "§e" + ThoroughbredData.tr("Traits", "特徵特性", "特征特性", "特性") + ": " + this.data.getFormattedTraitsSummary();
            for (String ln : wrapFormattedTextToWidth(this.font, traitsFull, maxTextW)) {
                if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                curY += 12;
            }

            // Sire & Dam names: keep on 1 line if they fit, or wrap onto separate lines so long horse names are never omitted!
            String combinedParents = "§7Sire: §b" + this.data.getSireName() + "  §7|  §7Dam: §d" + this.data.getDamName();
            if (this.font.width(combinedParents) <= maxTextW) {
                if (draw) graphics.drawCenteredString(this.font, combinedParents, contentCenterX, curY, 0xFFFFFF);
                curY += 12;
            } else {
                for (String ln : wrapFormattedTextToWidth(this.font, "§7Sire: §b" + this.data.getSireName(), maxTextW)) {
                    if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                    curY += 12;
                }
                for (String ln : wrapFormattedTextToWidth(this.font, "§7Dam: §d" + this.data.getDamName(), maxTextW)) {
                    if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                    curY += 12;
                }
            }

            String breedingStatusFull = "§eLife / Breeding: " + this.data.getFormattedBreedingStatus();
            for (String ln : wrapFormattedTextToWidth(this.font, breedingStatusFull, maxTextW)) {
                if (draw) graphics.drawCenteredString(this.font, ln, contentCenterX, curY, 0xFFFFFF);
                curY += 12;
            }
            curY += 4;

            // =================================================================
            // SECTION: CAREER RECORD & FINISHES (Multi-line wrapped race names!)
            // =================================================================
            if (draw) {
                graphics.fill(leftX + 6, curY, rightContentX, curY + 1, 0x44D4AF37);
                graphics.drawCenteredString(this.font, "§6§l= " + ThoroughbredData.tr("CAREER RECORD & FINISHES", "生涯戰績與賽果", "生涯战绩与赛果", "競走成績・戦績") + " =", contentCenterX, curY + 5, 0xFFFFFF);
                graphics.drawCenteredString(this.font, this.data.getCareerRecordSummary(), contentCenterX, curY + 17, 0xFFFFFF);
            }
            curY += 31;

            List<ThoroughbredData.CareerRaceRecord> history = this.data.getCareerHistory();
            if (history.isEmpty()) {
                if (draw) {
                    graphics.fill(leftX + 6, curY, rightContentX, curY + 26, 0xAA161C24);
                    graphics.fill(leftX + 6, curY, leftX + 8, curY + 26, 0xFF555555);
                    graphics.drawCenteredString(this.font,
                            "§7No official race finishes yet (Unraced Debut/Maiden).",
                            contentCenterX, curY + 9, 0x888888);
                }
                curY += 32;
            } else {
                for (int i = history.size() - 1; i >= 0; i--) {
                    ThoroughbredData.CareerRaceRecord r = history.get(i);
                    String placeRight;
                    if (r.placement >= 100 || "DSQ".equalsIgnoreCase(r.finishTime)) {
                        placeRight = "§4§lDSQ §7/ " + r.totalRunners;
                    } else if (r.placement == 99 || "DNF".equalsIgnoreCase(r.finishTime)) {
                        placeRight = "§c§lDNF §7/ " + r.totalRunners;
                    } else {
                        placeRight = r.getFormattedPlacement() + " §7/ " + r.totalRunners;
                    }
                    int placeW = this.font.width(placeRight);
                    int availRaceTitleW = Math.max(120, (rightContentX - 8 - placeW) - (leftX + 12));

                    String line1Left = r.getFormattedGradeBadge() + " §f§l" + r.raceName
                            + (r.trackSummary.isEmpty() ? "" : (" §7(" + r.trackSummary + ")"));
                    List<String> wrappedRaceLines = wrapFormattedTextToWidth(this.font, line1Left, availRaceTitleW);
                    int extraTitleRows = Math.max(0, wrappedRaceLines.size() - 1);
                    int rowH = 30 + (extraTitleRows * 10);

                    if (draw) {
                        int rowBg = (r.placement == 1) ? 0xCC242012 : 0xCC161C24;
                        int accent = r.getPlacementAccentColor();
                        graphics.fill(leftX + 6, curY, rightContentX, curY + rowH - 3, rowBg);
                        graphics.fill(leftX + 6, curY, leftX + 8, curY + rowH - 3, accent);

                        for (int lIdx = 0; lIdx < wrappedRaceLines.size(); lIdx++) {
                            graphics.drawString(this.font, wrappedRaceLines.get(lIdx), leftX + 12, curY + 4 + (lIdx * 10), 0xFFFFFF);
                        }
                        graphics.drawString(this.font, placeRight, rightContentX - 4 - placeW, curY + 4, 0xFFFFFF);

                        String line2 = String.format(Locale.ROOT,
                                "§7Time: §e%s  §7|  Margin: §f%s  §7|  Purse: §a+$%,d",
                                r.finishTime, r.margin, r.earnings);
                        graphics.drawString(this.font, line2, leftX + 12, curY + 15 + (extraTitleRows * 10), 0xCCCCCC);
                    }
                    curY += rowH;
                }
                curY += 6;
            }

            return curY - startY;
        }

        private int getTotalContentHeight() {
            int centerX = this.width / 2;
            int cardHalfW = getCardHalfW();
            int leftX = centerX - cardHalfW + 10;
            int rightContentX = centerX + cardHalfW - 28;
            int contentCenterX = (leftX + rightContentX) / 2;
            return renderOrMeasureContent(null, leftX, rightContentX, contentCenterX, 0, false);
        }

        private int getMaxScrollPixels() {
            return Math.max(0, getTotalContentHeight() - getViewportHeight());
        }

        private void scrollByPixels(int deltaPx) {
            this.scrollPixels = Mth.clamp(this.scrollPixels + deltaPx, 0, getMaxScrollPixels());
            updateScrollButtons();
        }

        private void updateScrollButtons() {
            int maxScroll = getMaxScrollPixels();
            this.scrollPixels = Mth.clamp(this.scrollPixels, 0, maxScroll);
            if (this.scrollUpBtn != null) {
                this.scrollUpBtn.active = this.scrollPixels > 0;
            }
            if (this.scrollDownBtn != null) {
                this.scrollDownBtn.active = this.scrollPixels < maxScroll;
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (delta > 0) {
                scrollByPixels(-22);
                return true;
            } else if (delta < 0) {
                scrollByPixels(22);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        private void updateGearButtons() {
            if (this.weightedTackBtn != null) {
                this.weightedTackBtn.setMessage(Component.literal(
                        this.data.hasWeightedTack()
                                ? "§e" + ThoroughbredData.tr("Weighted Pad: ON", "負重鞍墊: 開啟", "负重鞍垫: 开启", "重量パッド: ON")
                                : "§7" + ThoroughbredData.tr("Weighted Pad: OFF", "負重鞍墊: 關閉", "负重鞍垫: 关闭", "重量パッド: OFF")
                ));
            }
            if (this.blinkersBtn != null) {
                this.blinkersBtn.setMessage(Component.literal(
                        this.data.hasBlinkersHood()
                                ? "§b" + ThoroughbredData.tr("Blinkers: ON", "眼罩: 開啟", "眼罩: 开启", "ブリンカー: ON")
                                : "§7" + ThoroughbredData.tr("Blinkers: OFF", "眼罩: 關閉", "眼罩: 关闭", "ブリンカー: OFF")
                ));
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int cardHalfW = getCardHalfW();
            int cardH = getCardHeight();
            int topY = (this.height - cardH) / 2;
            int leftX = centerX - cardHalfW + 10;
            int rightContentX = centerX + cardHalfW - 28;
            int contentCenterX = (leftX + rightContentX) / 2;
            int maxHeaderW = cardHalfW * 2 - 20;

            graphics.fill(centerX - cardHalfW, topY, centerX + cardHalfW, topY + cardH, 0xDD10141C);
            graphics.fill(centerX - cardHalfW, topY, centerX + cardHalfW, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - cardHalfW, topY + cardH - 2, centerX + cardHalfW, topY + cardH, 0xFFD4AF37);

            if (this.data.hasOwnerSilk()) {
                drawJockeySilkIcon(graphics, centerX - cardHalfW + 8, topY + 6, 22,
                        StudbookSavedData.JockeySilkConfig.fromCompactCode(this.data.getOwnerSilkCode()));
            }

            String ownerTag = this.data.hasOwner()
                    ? ("§bOwner: §f" + this.data.getOwnerName() + (this.data.isLocked() ? " §c[LOCKED]" : ""))
                    : "§8[Unclaimed]";
            String singleLineTitle = "§6§l" + this.data.getName() + "  §7|  " + ownerTag;
            String genderText = this.data.isGelding()
                    ? "§3Gelding"
                    : ("Female".equalsIgnoreCase(this.data.getGender()) ? "§dMare / Filly" : "§bStallion / Colt");

            int headerY = topY + 6;
            if (this.font.width(singleLineTitle) <= maxHeaderW) {
                graphics.drawCenteredString(this.font, singleLineTitle, centerX, headerY, 0xFFFFFF);
                headerY += 12;
                String subtitle = genderText
                        + "  §7|  §eTemp: " + this.data.getFormattedTemperament()
                        + "  §7|  §ePers: " + this.data.getFormattedPersonality();
                drawScaledCenteredString(graphics, this.font, subtitle, centerX, headerY, maxHeaderW, 0xFFFFFF);
                headerY += 12;
            } else {
                // Multi-line wrapped horse name in the header so long horse names fit on new lines!
                List<String> wrappedName = wrapFormattedTextToWidth(this.font, "§6§l" + this.data.getName(), maxHeaderW);
                for (String nameLn : wrappedName) {
                    graphics.drawCenteredString(this.font, nameLn, centerX, headerY, 0xFFFFFF);
                    headerY += 10;
                }
                String subAndOwner = ownerTag + "  §7|  " + genderText
                        + "  §7|  §eTemp: " + this.data.getFormattedTemperament()
                        + "  §7|  §ePers: " + this.data.getFormattedPersonality();
                drawScaledCenteredString(graphics, this.font, subAndOwner, centerX, headerY + 1, maxHeaderW, 0xFFFFFF);
                headerY += 12;
            }

            String careSummary = String.format(Locale.ROOT,
                    "§eMood: %s §7(%.0f)  §6HNG: §f%.0f%%  §bTHR: §f%.0f%%  §cFTG: §f%.0f%%  §dBND: §f%.1f%%",
                    this.data.getFormattedMoodTier(), this.data.getMood(),
                    this.data.getHunger(), this.data.getThirst(), this.data.getFatigue(), this.data.getBond());
            drawScaledCenteredString(graphics, this.font, careSummary, centerX, headerY, maxHeaderW, 0xFFFFFF);

            int viewportTop = topY + getViewportTopOffset();
            int viewportBottom = topY + getViewportBottomOffset();

            graphics.fill(centerX - cardHalfW + 6, viewportTop - 2, centerX + cardHalfW - 6, viewportTop - 1, 0x55D4AF37);
            graphics.fill(centerX - cardHalfW + 6, viewportBottom + 1, centerX + cardHalfW - 6, viewportBottom + 2, 0x55D4AF37);

            graphics.enableScissor(centerX - cardHalfW + 4, viewportTop, rightContentX + 2, viewportBottom);

            int baseY = viewportTop + 4 - this.scrollPixels;
            renderOrMeasureContent(graphics, leftX, rightContentX, contentCenterX, baseY, true);

            graphics.disableScissor();

            int trackX = centerX + cardHalfW - 15;
            int trackTop = viewportTop + 22;
            int trackBottom = viewportBottom - 22;
            int trackH = Math.max(10, trackBottom - trackTop);
            graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF222832);

            int maxScroll = getMaxScrollPixels();
            if (maxScroll > 0) {
                int totalH = getTotalContentHeight();
                int thumbH = Math.max(14, (int) (trackH * ((double) getViewportHeight() / totalH)));
                int thumbY = trackTop + (int) ((trackH - thumbH) * ((double) this.scrollPixels / maxScroll));
                graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFFD4AF37);
            } else {
                graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF555555);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void drawStatRow(GuiGraphics graphics, int leftX, int rightContentX, int y, String label, int current, int max, int barColor) {
            String displayLabel = (max > 1000) ? ("§f" + label + " §d[2x]§f:") : ("§f" + label + ":");
            graphics.drawString(this.font, displayLabel, leftX + 8, y, 0xFFFFFF);
            String numbers = ThoroughbredData.formatStatPair(current, max);
            graphics.drawString(this.font, numbers, leftX + 70, y, 0xFFE680);

            int barX = leftX + 152;
            int barWidth = Math.max(120, rightContentX - barX - 6);
            graphics.fill(barX, y + 1, barX + barWidth, y + 8, 0xFF2A2A2A);

            double scaleDenom = (max > 1000 || current > 1000) ? 2000.0 : 1000.0;
            int maxFill = Mth.clamp((int) Math.round(barWidth * (max / scaleDenom)), 0, barWidth);
            graphics.fill(barX, y + 1, barX + maxFill, y + 8, 0xFF555555);

            int currentFill = Mth.clamp((int) Math.round(barWidth * (current / scaleDenom)), 0, barWidth);
            graphics.fill(barX, y + 1, barX + currentFill, y + 8, barColor);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 3: Starter Control Console GUI (Practice Stalls)
    // =========================================================================
    public static class StarterConsoleScreen extends Screen {
        private final ModNetwork.OpenStarterConsolePacket data;
        private int selectedStall = 0;
        private int selectedHorse = -1;

        private Button assignBtn;
        private Button clearBtn;
        private Button loadBtn;
        private Button countdownBtn;
        private Button ejectBtn;

        public StarterConsoleScreen(ModNetwork.OpenStarterConsolePacket data) {
            super(Component.literal(ThoroughbredData.tr("Starter Control Console", "出閘控制台", "出闸控制台", "スターター制御コンソール")));
            this.data = data;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int topY = (this.height - 220) / 2;

            int btnY1 = topY + 155;
            this.assignBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Assign Horse", "指派賽駒", "指派赛驹", "枠順割当")), btn -> {
                if (selectedStall >= 0 && selectedStall < data.stalls.size() && selectedHorse >= 0 && selectedHorse < data.horses.size()) {
                    int gateNum = data.stalls.get(selectedStall).gateNumber;
                    int horseId = data.horses.get(selectedHorse).horseId;
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.AssignGateHorsePacket(data.consolePos, gateNum, horseId));
                }
            }).bounds(centerX - 160, btnY1, 100, 20).build();

            this.clearBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Clear Stall", "清空閘位", "清空闸位", "枠を空にする")), btn -> {
                if (selectedStall >= 0 && selectedStall < data.stalls.size()) {
                    int gateNum = data.stalls.get(selectedStall).gateNumber;
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.AssignGateHorsePacket(data.consolePos, gateNum, -1));
                }
            }).bounds(centerX - 50, btnY1, 95, 20).build();

            this.loadBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Load into Gate", "入閘進入", "入闸进入", "ゲートイン")), btn -> {
                if (selectedStall >= 0 && selectedStall < data.stalls.size()) {
                    int gateNum = data.stalls.get(selectedStall).gateNumber;
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.LoadGateHorsePacket(data.consolePos, gateNum));
                }
            }).bounds(centerX + 55, btnY1, 105, 20).build();

            int btnY2 = topY + 180;
            this.countdownBtn = AutoFitButton.create(Component.literal("§a§l▶ " + ThoroughbredData.tr("START COUNTDOWN", "開始起步倒數", "开始起步倒数", "スタートカウントダウン")), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.StartGateCountdownPacket(data.consolePos));
                this.onClose();
            }).bounds(centerX - 160, btnY2, 160, 20).build();

            this.ejectBtn = AutoFitButton.create(Component.literal("§c§l" + ThoroughbredData.tr("EMERGENCY EJECT", "緊急退出開閘", "紧急退出开闸", "緊急ゲート開放・退避")), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.EmergencyEjectPacket(data.consolePos));
            }).bounds(centerX + 10, btnY2, 150, 20).build();

            this.addRenderableWidget(this.assignBtn);
            this.addRenderableWidget(this.clearBtn);
            this.addRenderableWidget(this.loadBtn);
            this.addRenderableWidget(this.countdownBtn);
            this.addRenderableWidget(this.ejectBtn);

            updateButtons();
        }

        private void updateButtons() {
            boolean hasStall = selectedStall >= 0 && selectedStall < data.stalls.size();
            boolean hasHorse = selectedHorse >= 0 && selectedHorse < data.horses.size();
            boolean stallAssigned = hasStall && data.stalls.get(selectedStall).assignedHorseId != -1;

            if (this.assignBtn != null) this.assignBtn.active = hasStall && hasHorse;
            if (this.clearBtn != null) this.clearBtn.active = hasStall && stallAssigned;
            if (this.loadBtn != null) this.loadBtn.active = hasStall && stallAssigned;

            boolean anyAssigned = data.stalls.stream().anyMatch(s -> s.assignedHorseId != -1);
            if (this.countdownBtn != null) this.countdownBtn.active = anyAssigned && !data.countdownActive;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int centerX = this.width / 2;
            int topY = (this.height - 220) / 2;

            int leftColX = centerX - 160;
            int rightColX = centerX + 5;
            int listY = topY + 36;

            for (int i = 0; i < Math.min(5, data.stalls.size()); i++) {
                int itemY = listY + (i * 22);
                if (mouseX >= leftColX && mouseX <= leftColX + 150 && mouseY >= itemY && mouseY <= itemY + 20) {
                    this.selectedStall = i;
                    updateButtons();
                    return true;
                }
            }

            for (int i = 0; i < Math.min(5, data.horses.size()); i++) {
                int itemY = listY + (i * 22);
                if (mouseX >= rightColX && mouseX <= rightColX + 155 && mouseY >= itemY && mouseY <= itemY + 20) {
                    this.selectedHorse = i;
                    updateButtons();
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int topY = (this.height - 220) / 2;

            graphics.fill(centerX - 170, topY, centerX + 170, topY + 212, 0xEE0D1117);
            graphics.fill(centerX - 170, topY, centerX + 170, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - 170, topY + 210, centerX + 170, topY + 212, 0xFFD4AF37);

            graphics.drawCenteredString(this.font, "§6§l=== " + ThoroughbredData.tr("Starter Control Console", "出閘控制台", "出闸控制台", "スターター制御コンソール") + " ===", centerX, topY + 8, 0xFFFFFF);

            int leftColX = centerX - 160;
            int rightColX = centerX + 5;
            int listY = topY + 36;

            graphics.drawString(this.font, "§e" + ThoroughbredData.tr("Starting Stalls", "起步閘位", "起步闸位", "発走枠") + " (" + data.stalls.size() + ")", leftColX, topY + 24, 0xFFFFFF);
            graphics.drawString(this.font, "§b" + ThoroughbredData.tr("Nearby Horses", "附近純種馬", "附近纯种马", "付近の競走馬") + " (" + data.horses.size() + ")", rightColX, topY + 24, 0xFFFFFF);

            if (data.stalls.isEmpty()) {
                graphics.drawString(this.font, "§7" + ThoroughbredData.tr("No starting gates nearby!", "附近無起步閘！", "附近无起步闸！", "付近に発走ゲートがありません！"), leftColX, listY + 10, 0x888888);
            } else {
                for (int i = 0; i < Math.min(5, data.stalls.size()); i++) {
                    ModNetwork.OpenStarterConsolePacket.StallEntry s = data.stalls.get(i);
                    int itemY = listY + (i * 22);
                    boolean isSel = (i == this.selectedStall);

                    int bgColor = isSel ? 0xFF2A3D52 : 0xFF161C24;
                    int borderColor = isSel ? 0xFF00E5FF : 0xFF334455;
                    graphics.fill(leftColX, itemY, leftColX + 150, itemY + 20, bgColor);
                    graphics.fill(leftColX, itemY, leftColX + 2, itemY + 20, borderColor);

                    String emptyStr = "§7[" + ThoroughbredData.tr("Empty", "空位", "空位", "空き") + "]";
                    String horseName = s.assignedHorseId != -1 ? s.assignedHorseName : emptyStr;
                    String stallTag = ThoroughbredData.tr("Gate #", "閘位 #", "闸位 #", "枠番 #") + s.gateNumber + ": " + horseName;
                    drawScaledLeftString(graphics, this.font, stallTag, leftColX + 5, itemY + 2, 142, 0xFFFFFF);

                    String status = s.inPosition ? ("§a● " + ThoroughbredData.tr("In Gate", "已入閘", "已入闸", "ゲートイン完了"))
                            : (s.assignedHorseId != -1 ? ("§e○ " + ThoroughbredData.tr("Assigned", "已指派", "已指派", "枠順指定済")) : ("§7" + ThoroughbredData.tr("Empty", "空位", "空位", "空き")));
                    drawScaledLeftString(graphics, this.font, status, leftColX + 5, itemY + 11, 142, 0xAAAAAA);
                }
            }

            if (data.horses.isEmpty()) {
                drawScaledLeftString(graphics, this.font, "§7" + ThoroughbredData.tr("No thoroughbreds nearby", "附近無純種馬", "附近无纯种马", "付近にサラブレッドがいません"), rightColX, listY + 10, 150, 0x888888);
            } else {
                for (int i = 0; i < Math.min(5, data.horses.size()); i++) {
                    ModNetwork.OpenStarterConsolePacket.HorseEntry h = data.horses.get(i);
                    int itemY = listY + (i * 22);
                    boolean isSel = (i == this.selectedHorse);

                    int bgColor = isSel ? 0xFF2A3D52 : 0xFF161C24;
                    int borderColor = isSel ? 0xFF00E5FF : 0xFF334455;
                    graphics.fill(rightColX, itemY, rightColX + 155, itemY + 20, bgColor);
                    graphics.fill(rightColX, itemY, rightColX + 2, itemY + 20, borderColor);

                    String hLabel = String.format(Locale.ROOT, "%s §7(%s, %dyo, %.1fm)", h.name, ThoroughbredData.localizeGender(h.gender), h.equineAgeYears, h.distance);
                    drawScaledLeftString(graphics, this.font, hLabel, rightColX + 5, itemY + 2, 147, 0xFFFFFF);

                    String riderTag = h.isRidden ? "§d" + h.riderName : ("§7" + ThoroughbredData.tr("Unridden", "無騎師", "无骑师", "無人"));
                    drawScaledLeftString(graphics, this.font, riderTag, rightColX + 5, itemY + 11, 147, 0xAAAAAA);
                }
            }

            if (data.countdownActive) {
                drawScaledCenteredString(graphics, this.font, "§e§l● " + ThoroughbredData.tr("GATES ARMED", "起步閘待命發走", "起步闸待命发走", "ゲートスタンバイ完了"), centerX, topY + 144, 324, 0xFFAA00);
            } else {
                drawScaledCenteredString(graphics, this.font, "§7" + ThoroughbredData.tr("Select stall & horse to assign, then start countdown.", "選擇閘位與賽駒指派，隨後啟動倒數。", "选择闸位与赛驹指派，随后启动倒数。", "枠と馬を選択して割当後、カウントダウンを開始してください。"), centerX, topY + 144, 324, 0x888888);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 4: Scrollable Deceased Horses GUI (/tb deaths)
    // =========================================================================
    public static class DeceasedHorsesScreen extends Screen {
        private static final int VISIBLE_ROWS = 4;
        private static final int ROW_HEIGHT = 38;

        private final List<ModNetwork.OpenDeathsScreenPacket.DeathEntry> entries;
        private int scrollOffset = 0;

        private final List<Button> rowActionButtons = new ArrayList<>();
        private Button scrollUpBtn;
        private Button scrollDownBtn;

        public DeceasedHorsesScreen(List<ModNetwork.OpenDeathsScreenPacket.DeathEntry> entries) {
            super(Component.literal("Recently Deceased Thoroughbreds"));
            this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int topY = (this.height - 230) / 2;
            int listTopY = topY + 40;

            this.rowActionButtons.clear();
            for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
                final int rowSlot = slot;
                int rowY = listTopY + (slot * ROW_HEIGHT);
                Button actionBtn = AutoFitButton.create(Component.literal("§aRevive"), btn -> {
                    int idx = this.scrollOffset + rowSlot;
                    if (idx >= 0 && idx < this.entries.size()) {
                        ModNetwork.OpenDeathsScreenPacket.DeathEntry d = this.entries.get(idx);
                        if (d.permanentlyPurged && !d.horseUUID.isEmpty()) {
                            this.entries.remove(idx);
                            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, getMaxScroll());
                            updateRowButtons();
                            ModNetwork.CHANNEL.sendToServer(new ModNetwork.RemoveDeathRecordPacket(d.horseUUID));
                        } else if (!d.permanentlyPurged && !d.revived && !d.horseUUID.isEmpty()) {
                            d.revived = true;
                            updateRowButtons();
                            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ReviveHorsePacket(d.horseUUID));
                        }
                    }
                }).bounds(centerX + 105, rowY + 8, 62, 20).build();
                this.rowActionButtons.add(actionBtn);
                this.addRenderableWidget(actionBtn);
            }

            this.scrollUpBtn = AutoFitButton.create(Component.literal("▲"), btn -> scrollBy(-1))
                    .bounds(centerX + 172, listTopY, 18, 20).build();
            this.scrollDownBtn = AutoFitButton.create(Component.literal("▼"), btn -> scrollBy(1))
                    .bounds(centerX + 172, listTopY + (VISIBLE_ROWS * ROW_HEIGHT) - 22, 18, 20).build();

            Button closeBtn = AutoFitButton.create(Component.literal("Close"), btn -> this.onClose())
                    .bounds(centerX - 45, topY + 198, 90, 20).build();

            this.addRenderableWidget(this.scrollUpBtn);
            this.addRenderableWidget(this.scrollDownBtn);
            this.addRenderableWidget(closeBtn);

            updateRowButtons();
        }

        private int getMaxScroll() {
            return Math.max(0, this.entries.size() - VISIBLE_ROWS);
        }

        private void scrollBy(int delta) {
            this.scrollOffset = Mth.clamp(this.scrollOffset + delta, 0, getMaxScroll());
            updateRowButtons();
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (delta > 0) {
                scrollBy(-1);
                return true;
            } else if (delta < 0) {
                scrollBy(1);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        private void updateRowButtons() {
            for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
                Button btn = this.rowActionButtons.get(slot);
                int idx = this.scrollOffset + slot;
                if (idx >= 0 && idx < this.entries.size()) {
                    ModNetwork.OpenDeathsScreenPacket.DeathEntry d = this.entries.get(idx);
                    btn.visible = true;
                    if (d.permanentlyPurged) {
                        btn.active = true;
                        btn.setMessage(Component.literal("§cRemove"));
                    } else if (d.revived) {
                        btn.active = false;
                        btn.setMessage(Component.literal("§7Revived"));
                    } else {
                        btn.active = true;
                        btn.setMessage(Component.literal("§aRevive"));
                    }
                } else {
                    btn.visible = false;
                }
            }

            if (this.scrollUpBtn != null) {
                this.scrollUpBtn.active = this.scrollOffset > 0;
            }
            if (this.scrollDownBtn != null) {
                this.scrollDownBtn.active = this.scrollOffset < getMaxScroll();
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int topY = (this.height - 230) / 2;

            graphics.fill(centerX - 195, topY, centerX + 195, topY + 226, 0xDD10141C);
            graphics.fill(centerX - 195, topY, centerX + 195, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - 195, topY + 224, centerX + 195, topY + 226, 0xFFD4AF37);

            graphics.drawCenteredString(this.font,
                    "§6§l=== Recently Deceased Thoroughbreds (" + this.entries.size() + ") ===",
                    centerX, topY + 7, 0xFFFFFF);
            graphics.drawCenteredString(this.font,
                    "§7Scroll with mouse wheel or ▲/▼.",
                    centerX, topY + 18, 0xAAAAAA);
            graphics.drawCenteredString(this.font,
                    "§7Revived logs clear after 8s; Purged logs stay until removed.",
                    centerX, topY + 28, 0x888888);

            int listLeftX = centerX - 185;
            int listTopY = topY + 40;

            if (this.entries.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7No deceased Thoroughbreds on record.", centerX, listTopY + 65, 0x888888);
            } else {
                for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
                    int idx = this.scrollOffset + slot;
                    if (idx >= this.entries.size()) break;

                    ModNetwork.OpenDeathsScreenPacket.DeathEntry d = this.entries.get(idx);
                    int rowY = listTopY + (slot * ROW_HEIGHT);

                    int rowBg = d.permanentlyPurged ? 0xCC241215 : (d.revived ? 0xCC122418 : 0xCC161C24);
                    int accentColor = d.permanentlyPurged ? 0xFFAA2222 : (d.revived ? 0xFF55FF55 : 0xFFD4AF37);

                    graphics.fill(listLeftX, rowY, centerX + 170, rowY + ROW_HEIGHT - 3, rowBg);
                    graphics.fill(listLeftX, rowY, listLeftX + 2, rowY + ROW_HEIGHT - 3, accentColor);

                    String shortUuid = d.horseUUID.length() >= 8 ? d.horseUUID.substring(0, 8) : d.horseUUID;
                    String statusBadge = d.permanentlyPurged ? " §4§l[PURGED]" : (d.revived ? " §a[REVIVED]" : "");
                    String line1 = String.format(Locale.ROOT, "§e§l%s%s §7| Owner: §b%s §7| §8[%s]",
                            d.horseName, statusBadge, d.ownerName, shortUuid);
                    graphics.drawString(this.font, line1, listLeftX + 6, rowY + 4, 0xFFFFFF);

                    String line2 = "§7Died: §f" + d.deathDate;
                    graphics.drawString(this.font, line2, listLeftX + 6, rowY + 14, 0xCCCCCC);

                    String rawCause = d.deathReason != null ? d.deathReason : "";
                    drawScaledLeftString(graphics, this.font, "§c" + rawCause, listLeftX + 6, rowY + 24, 282, 0xFF7777);
                }

                int trackX = centerX + 179;
                int trackTop = listTopY + 22;
                int trackBottom = listTopY + (VISIBLE_ROWS * ROW_HEIGHT) - 24;
                int trackH = Math.max(10, trackBottom - trackTop);
                graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF222832);

                int maxScroll = getMaxScroll();
                if (maxScroll > 0) {
                    int thumbH = Math.max(12, (int) (trackH * ((double) VISIBLE_ROWS / this.entries.size())));
                    int thumbY = trackTop + (int) ((trackH - thumbH) * ((double) this.scrollOffset / maxScroll));
                    graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFFD4AF37);
                } else {
                    graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF555555);
                }
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 5: Racing Console GUI (Base Types, Volume Control, Base Type Switch)
    // =========================================================================
    public static class RacingConsoleScreen extends Screen {
        private static final int VIEW_MAIN = 0;
        private static final int VIEW_SAVE_BASE = 1;
        private static final int VIEW_EDIT_BASE = 2;
        private static final int VIEW_CREATE_RACE = 3;
        private static final int VIEW_EDIT_RACE = 4;

        private final ModNetwork.OpenRacingConsolePacket data;
        private int subView = VIEW_MAIN;

        private int selectedBaseIndex = 0;
        private int selectedRaceIndex = 0;
        private int selectedStallNumber = 1;
        private int selectedHorseIndex = 0;

        // Top Bar Widgets
        private EditBox courseNameBox;

        // Save / Edit Base Type Modal State
        private String newBaseSurface = "Dirt";
        private int newBaseDistance = 2400;
        private boolean flipGateFacingOnSave = false;

        // Create / Edit Race Modal State (includes Purses, Restrictions, Fanfare & Custom Music!)
        private EditBox newRaceNameBox;
        private String savedRaceNameText = "";
        private EditBox newRacePurseBox;
        private String savedPurseText = "5000";
        private boolean isPurseValid = true;
        private Button saveRaceBtnRef;
        private int newRaceGradeIdx = 4; // Default G2
        private int newRaceFanfareIdx = 0;
        private int newRaceVolume = 50;  // 5% to 100%, default 50%
        private long newRacePurse = 5000L;
        private long newRaceReqEarnings = 0L;
        private int newRaceAgeReq = 2; // Default: 3-Years-Old and Up
        private int newRaceGenderReq = 0;
        private String newRaceSeriesTag = "";
        private EditBox newRaceSeriesTagBox;
        private int newRaceMusicIdx = 0;
        private int newRaceMusicVol = 50;
        private int newRaceSpurtIdx = 0;
        private int newRaceSpurtVol = 50;
        private int targetBaseIdxForRace = 0;
        private String editingRaceId = "";

        // Step 16: Editable 3-Act G1 Broadcast Choreography & Timeline Studio State
        private boolean g1FxTabActive = false;
        private int g1IntroIdx = 0;
        private int g1EmblemIdx = 0;
        private int g1PyroIdx = 0;
        private int g1ThemeIdx = 0;
        private int g1Act2Idx = 0;       // 0 = None (Skip Act 2), 1..10 = Bridge FX
        private int g1DropIdx = 0;       // 0..7 = Climax Drop Transition
        private int g1TrophyIdx = 0;     // 0..3 = Trophy Shape
        private int g1Act1Tenths = 35;   // Default 3.5s
        private int g1Act2Tenths = 25;   // Default 2.5s (when Act 2 is enabled)
        private int g1HoldTenths = 20;   // Default +2.0s post-fanfare hold
        private int g1SlamSpeedIdx = 1;  // Default Normal (0.45s)
        private int g1ShowNamePreClimax = 0;  // 0 = Climax Only, 1 = Show in Act 1-2
        private int g1ShowTrackPreClimax = 0; // 0 = Climax Only, 1 = Show in Act 1-2
        private int g1ClimaxBadgeStyle = 0;   // 0 = "★ G1 ★" [Default], 1 = "★ GRADE 1 ★"
        private int g1PreviewLockMode = 0;    // 0 = Auto Cycle, 1 = Lock Act 1, 2 = Lock Act 2, 3 = Lock Climax

        private String getG1PresetString() {
            return this.g1IntroIdx + "|" + this.g1EmblemIdx + "|" + this.g1PyroIdx + "|" + this.g1ThemeIdx
                    + "|" + this.g1Act2Idx + "|" + this.g1DropIdx + "|" + this.g1TrophyIdx
                    + "|" + this.g1Act1Tenths + "|" + this.g1Act2Tenths + "|" + this.g1HoldTenths
                    + "|" + this.g1SlamSpeedIdx + "|" + this.g1ShowNamePreClimax + "|" + this.g1ShowTrackPreClimax
                    + "|" + this.g1ClimaxBadgeStyle;
        }

        private void loadG1PresetString(String preset) {
            int[] parsed = RacingManager.parseG1Preset(preset);
            this.g1IntroIdx           = parsed[0];
            this.g1EmblemIdx          = parsed[1];
            this.g1PyroIdx            = parsed[2];
            this.g1ThemeIdx           = parsed[3];
            this.g1Act2Idx            = parsed[4];
            this.g1DropIdx            = parsed[5];
            this.g1TrophyIdx          = parsed[6];
            this.g1Act1Tenths         = parsed[7];
            this.g1Act2Tenths         = parsed[8];
            this.g1HoldTenths         = parsed[9];
            this.g1SlamSpeedIdx       = parsed[10];
            this.g1ShowNamePreClimax  = parsed[11];
            this.g1ShowTrackPreClimax = parsed[12];
            this.g1ClimaxBadgeStyle   = parsed[13];
            this.g1PreviewLockMode = 0;
        }

        private String clipBtnText(String text, int maxPx) {
            return text != null ? text : "";
        }

        public RacingConsoleScreen(ModNetwork.OpenRacingConsolePacket data) {
            super(Component.literal(ThoroughbredData.tr("Racing Console", "賽事控制台", "赛事控制台", "レーシングコンソール")));
            this.data = data;
        }

        @Override
        protected void init() {
            this.clearWidgets();

            int centerX = this.width / 2;
            int topY = (this.height - 236) / 2;

            if (this.subView == VIEW_SAVE_BASE || this.subView == VIEW_EDIT_BASE) {
                initSaveOrEditBaseView(centerX, topY, this.subView == VIEW_EDIT_BASE);
                return;
            }
            if (this.subView == VIEW_CREATE_RACE || this.subView == VIEW_EDIT_RACE) {
                initCreateOrEditRaceView(centerX, topY, this.subView == VIEW_EDIT_RACE);
                return;
            }

            // =================================================================
            // MAIN VIEW WIDGETS
            // =================================================================
            this.courseNameBox = new EditBox(this.font, centerX - 132, topY + 22, 92, 16, Component.literal(ThoroughbredData.tr("Racecourse", "競馬場", "赛马场", "競馬場")));
            this.courseNameBox.setMaxLength(32);
            this.courseNameBox.setValue(data.config.racecourseName);
            this.addRenderableWidget(this.courseNameBox);

            Button saveCourseBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Set Name", "設定名稱", "设定名称", "名称設定")), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                        data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_SET_COURSE_NAME,
                        this.courseNameBox.getValue(), "", "", "", 0, 0
                ));
            }).bounds(centerX - 36, topY + 20, 56, 20).build();
            this.addRenderableWidget(saveCourseBtn);

            if (data.inBuildMode) {
                if (data.buildReadyToSave) {
                    Button saveBaseModalBtn = AutoFitButton.create(Component.literal("§a§l" + ThoroughbredData.tr("Save Race Base Type", "儲存賽道規格", "保存赛道规格", "コース規格を保存")), btn -> {
                        this.newBaseSurface = "Dirt";
                        this.newBaseDistance = 2400;
                        this.flipGateFacingOnSave = false;
                        this.subView = VIEW_SAVE_BASE;
                        this.init();
                    }).bounds(centerX + 26, topY + 20, 118, 20).build();
                    this.addRenderableWidget(saveBaseModalBtn);
                }
                Button cancelBuildBtn = AutoFitButton.create(Component.literal("§c" + ThoroughbredData.tr("Cancel", "取消", "取消", "キャンセル")), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_CANCEL_BASE_BUILD,
                            "", "", "", "", 0, 0
                    ));
                }).bounds(centerX + 148, topY + 20, 48, 20).build();
                this.addRenderableWidget(cancelBuildBtn);
            } else {
                Button createBaseBtn = AutoFitButton.create(Component.literal("§e" + ThoroughbredData.tr("+ Create Race Base Type", "+ 新增賽道規格", "+ 新增赛道规格", "+ コース規格を作成")), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_START_BASE_BUILD,
                            "", "", "", "", 0, 0
                    ));
                    this.onClose();
                }).bounds(centerX + 46, topY + 20, 150, 20).build();
                this.addRenderableWidget(createBaseBtn);
            }

            List<RacingManager.RaceBaseType> bases = data.config.baseTypes;
            if (!bases.isEmpty()) {
                this.selectedBaseIndex = Mth.clamp(this.selectedBaseIndex, 0, bases.size() - 1);
                RacingManager.RaceBaseType selBase = bases.get(this.selectedBaseIndex);
                this.selectedStallNumber = Mth.clamp(this.selectedStallNumber, 1, Math.max(1, selBase.startGatePositions.size()));

                // Action Row for Selected Base Type: [Test Race] [+ New Race] [Edit Base] [Del]
                int actY = topY + 58;
                Button testRaceBtn = AutoFitButton.create(Component.literal("§b▶ " + ThoroughbredData.tr("Test Race", "測試賽", "测试赛", "テスト走")), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_START_TEST_RACE,
                            selBase.baseId, "", "", "", 0, 0
                    ));
                    this.onClose();
                }).bounds(centerX - 68, actY, 68, 18).build();

                Button newRaceBtn = AutoFitButton.create(Component.literal("§a" + ThoroughbredData.tr("+ New Race", "+ 新賽事", "+ 新赛事", "+ 新規レース")), btn -> {
                    this.editingRaceId = "";
                    this.newRaceNameBox = null;
                    this.savedRaceNameText = data.config.racecourseName + " Stakes";
                    this.newRacePurseBox = null;
                    this.savedPurseText = "5000";
                    this.isPurseValid = true;
                    this.newRaceGradeIdx = 4; // G2
                    this.newRaceFanfareIdx = 0;
                    this.newRaceVolume = 50;
                    this.newRacePurse = 5000L;
                    this.newRaceReqEarnings = 0L;
                    this.newRaceAgeReq = 2;
                    this.newRaceGenderReq = 0;
                    this.newRaceSeriesTag = "";
                    this.newRaceSeriesTagBox = null;
                    this.newRaceMusicIdx = 0;
                    this.newRaceMusicVol = 50;
                    this.newRaceSpurtIdx = 0;
                    this.newRaceSpurtVol = 50;
                    this.g1FxTabActive = false;
                    this.loadG1PresetString(RacingManager.DEFAULT_G1_PRESET);
                    this.targetBaseIdxForRace = this.selectedBaseIndex;
                    this.subView = VIEW_CREATE_RACE;
                    this.init();
                }).bounds(centerX + 2, actY, 72, 18).build();

                Button editBaseBtn = AutoFitButton.create(Component.literal("§e✎ " + ThoroughbredData.tr("Edit Base", "編輯賽道", "编辑赛道", "規格編集")), btn -> {
                    this.newBaseSurface = selBase.surface;
                    this.newBaseDistance = selBase.distanceMeters;
                    this.flipGateFacingOnSave = false;
                    this.subView = VIEW_EDIT_BASE;
                    this.init();
                }).bounds(centerX + 76, actY, 72, 18).build();

                Button delBaseBtn = AutoFitButton.create(Component.literal("§c" + ThoroughbredData.tr("Del", "刪除", "删除", "削除")), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_DELETE_BASE_TYPE,
                            selBase.baseId, "", "", "", 0, 0
                    ));
                }).bounds(centerX + 150, actY, 46, 18).build();

                this.addRenderableWidget(testRaceBtn);
                this.addRenderableWidget(newRaceBtn);
                this.addRenderableWidget(editBaseBtn);
                this.addRenderableWidget(delBaseBtn);

                // Saved Races Buttons: [🚩 Start Race] [✎ Edit] [Del]
                int botBtnY = topY + 184;
                boolean hasSelRace = !selBase.races.isEmpty() && selectedRaceIndex >= 0 && selectedRaceIndex < selBase.races.size();

                Button startRaceBtn = AutoFitButton.create(Component.literal("§6§l🚩 " + ThoroughbredData.tr("Start Race", "開始賽事", "开始赛事", "レース発走")), btn -> {
                    if (hasSelRace) {
                        RacingManager.RaceDefinition r = selBase.races.get(selectedRaceIndex);
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                                data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_START_OFFICIAL_RACE,
                                selBase.baseId, r.raceId, "", "", 0, 0
                        ));
                        this.onClose();
                    }
                }).bounds(centerX - 68, botBtnY, 82, 18).build();
                startRaceBtn.active = hasSelRace;
                this.addRenderableWidget(startRaceBtn);

                Button editRaceBtn = AutoFitButton.create(Component.literal("§e✎ " + ThoroughbredData.tr("Edit", "編輯", "编辑", "編集")), btn -> {
                    if (hasSelRace) {
                        RacingManager.RaceDefinition r = selBase.races.get(selectedRaceIndex);
                        this.editingRaceId = r.raceId;
                        this.newRaceNameBox = null;
                        this.savedRaceNameText = r.raceName;
                        this.newRacePurseBox = null;
                        this.savedPurseText = String.valueOf(Math.max(100L, (r.firstPlacePurse / 10L) * 10L));
                        this.isPurseValid = true;
                        this.newRaceGradeIdx = 0;
                        for (int i = 0; i < RacingManager.RACE_GRADES.length; i++) {
                            if (RacingManager.RACE_GRADES[i].equalsIgnoreCase(r.grade)) {
                                this.newRaceGradeIdx = i;
                                break;
                            }
                        }
                        this.newRaceFanfareIdx = 0;
                        for (int i = 0; i < data.availableFanfares.size(); i++) {
                            if (data.availableFanfares.get(i).equalsIgnoreCase(r.fanfareFile)) {
                                this.newRaceFanfareIdx = i;
                                break;
                            }
                        }
                        this.newRaceVolume = Mth.clamp(r.fanfareVolume, 5, 100);
                        this.newRacePurse = r.firstPlacePurse;
                        this.newRaceReqEarnings = r.requiredEarnings;
                        this.newRaceAgeReq = Mth.clamp(r.ageRequirement, 0, 3);
                        this.newRaceGenderReq = Mth.clamp(r.genderRequirement, 0, 1);
                        this.newRaceSeriesTag = r.raceSeriesTag != null ? r.raceSeriesTag : "";
                        this.newRaceSeriesTagBox = null;
                        this.newRaceMusicIdx = 0;
                        for (int mi = 0; mi < data.availableMusic.size(); mi++) {
                            if (data.availableMusic.get(mi).equalsIgnoreCase(r.raceMusicFile)) {
                                this.newRaceMusicIdx = mi;
                                break;
                            }
                        }
                        this.newRaceMusicVol = Mth.clamp(r.raceMusicVolume, 5, 100);
                        this.newRaceSpurtIdx = 0;
                        for (int si = 0; si < data.availableMusic.size(); si++) {
                            if (data.availableMusic.get(si).equalsIgnoreCase(r.spurtMusicFile)) {
                                this.newRaceSpurtIdx = si;
                                break;
                            }
                        }
                        this.newRaceSpurtVol = Mth.clamp(r.spurtMusicVolume, 5, 100);
                        this.g1FxTabActive = false;
                        this.loadG1PresetString(r.g1EffectPreset);
                        this.targetBaseIdxForRace = this.selectedBaseIndex;
                        this.subView = VIEW_EDIT_RACE;
                        this.init();
                    }
                }).bounds(centerX + 16, botBtnY, 42, 18).build();
                editRaceBtn.active = hasSelRace;
                this.addRenderableWidget(editRaceBtn);

                Button delRaceBtn = AutoFitButton.create(Component.literal("§c" + ThoroughbredData.tr("Del", "刪除", "删除", "削除")), btn -> {
                    if (hasSelRace) {
                        RacingManager.RaceDefinition r = selBase.races.get(selectedRaceIndex);
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                                data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_DELETE_RACE,
                                selBase.baseId, r.raceId, "", "", 0, 0
                        ));
                    }
                }).bounds(centerX + 60, botBtnY, 28, 18).build();
                delRaceBtn.active = hasSelRace;
                this.addRenderableWidget(delRaceBtn);

                // Stall Assignment Controls (Optional Reservation)
                Button assignHorseBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Assign", "指派", "指派", "割当")), btn -> {
                    if (!data.nearbyHorses.isEmpty() && selectedHorseIndex >= 0 && selectedHorseIndex < data.nearbyHorses.size()) {
                        int hId = data.nearbyHorses.get(selectedHorseIndex).horseId;
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                                data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_ASSIGN_STALL,
                                "", "", "", "", selectedStallNumber, hId
                        ));
                    }
                }).bounds(centerX + 92, botBtnY, 50, 18).build();
                boolean hasSelHorse = !data.nearbyHorses.isEmpty() && selectedHorseIndex >= 0 && selectedHorseIndex < data.nearbyHorses.size();
                boolean selBarred = false;
                if (hasSelHorse && hasSelRace) {
                    ModNetwork.OpenStarterConsolePacket.HorseEntry selH = data.nearbyHorses.get(selectedHorseIndex);
                    RacingManager.RaceDefinition selR = selBase.races.get(selectedRaceIndex);
                    selBarred = !RacingManager.matchesAgeRequirement(selH.equineAgeYears, selR.ageRequirement)
                            || !RacingManager.matchesGenderRequirement(selH.gender, selR.genderRequirement);
                }
                assignHorseBtn.active = hasSelHorse && !selBarred;
                this.addRenderableWidget(assignHorseBtn);

                Button clearAssignBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Clear", "清空", "清空", "解除")), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_ASSIGN_STALL,
                            "", "", "", "", selectedStallNumber, -1
                    ));
                }).bounds(centerX + 144, botBtnY, 52, 18).build();
                this.addRenderableWidget(clearAssignBtn);
            }

            // Bottom Bar: [Cancel Active Race] [📁 Fanfare Folder] [↻ Refresh] [Close]
            if (data.activeRaceRunning) {
                Button cancelRaceBtn = AutoFitButton.create(Component.literal("§c§l✖ " + ThoroughbredData.tr("Cancel Active Race", "取消進行中賽事", "取消进行中赛事", "開催中レース中止")), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_CANCEL_ACTIVE_RACE,
                            "", "", "", "", 0, 0
                    ));
                }).bounds(centerX - 196, topY + 208, 124, 20).build();
                this.addRenderableWidget(cancelRaceBtn);
            }

            Button openFanfareFolderBtn = AutoFitButton.create(Component.literal("§b📁 " + ThoroughbredData.tr("Fanfare Folder", "號角資料夾", "号角文件夹", "ファンファーレフォルダ")), btn -> {
                openFanfaresDirectoryAndRefresh();
            }).bounds(centerX - 66, topY + 208, 112, 20).build();
            this.addRenderableWidget(openFanfareFolderBtn);

            Button refreshBtn = AutoFitButton.create(Component.literal("§e↻ " + ThoroughbredData.tr("Refresh", "重新整理", "刷新", "更新")), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                        data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_REFRESH_CONSOLE,
                        "", "", "", "", 0, 0
                ));
            }).bounds(centerX + 50, topY + 208, 62, 20).build();
            this.addRenderableWidget(refreshBtn);

            Button closeBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Close", "關閉", "关闭", "閉じる")), btn -> this.onClose())
                    .bounds(centerX + 116, topY + 208, 80, 20).build();
            this.addRenderableWidget(closeBtn);
        }

        private void openFanfaresDirectoryAndRefresh() {
            try {
                File folder = RacingManager.getFanfareFolderPath().toFile();
                Util.getPlatform().openFile(folder);
                File musicFolder = RacingManager.getMusicFolderPath().toFile();
                Util.getPlatform().openFile(musicFolder);
            } catch (Exception ignored) {
            }
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                    data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_REFRESH_CONSOLE,
                    "", "", "", "", 0, 0
            ));
        }

        private void initSaveOrEditBaseView(int centerX, int topY, boolean isEdit) {
            Button surfBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Track Type: ", "場地類型: ", "场地类型: ", "馬場種別: ") + "§e" + ThoroughbredData.tr(newBaseSurface, newBaseSurface, newBaseSurface, newBaseSurface)), btn -> {
                this.newBaseSurface = "Dirt".equalsIgnoreCase(this.newBaseSurface) ? "Turf" : "Dirt";
                this.init();
            }).bounds(centerX - 100, topY + 60, 200, 20).build();

            Button minus200 = AutoFitButton.create(Component.literal("-200m"), btn -> {
                this.newBaseDistance = Math.max(400, this.newBaseDistance - 200);
                this.init();
            }).bounds(centerX - 110, topY + 96, 48, 20).build();

            Button minus100 = AutoFitButton.create(Component.literal("-100m"), btn -> {
                this.newBaseDistance = Math.max(400, this.newBaseDistance - 100);
                this.init();
            }).bounds(centerX - 58, topY + 96, 48, 20).build();

            Button plus100 = AutoFitButton.create(Component.literal("+100m"), btn -> {
                this.newBaseDistance = Math.min(10000, this.newBaseDistance + 100);
                this.init();
            }).bounds(centerX + 10, topY + 96, 48, 20).build();

            Button plus200 = AutoFitButton.create(Component.literal("+200m"), btn -> {
                this.newBaseDistance = Math.min(10000, this.newBaseDistance + 200);
                this.init();
            }).bounds(centerX + 62, topY + 96, 48, 20).build();

            if (isEdit && !data.config.baseTypes.isEmpty()) {
                RacingManager.RaceBaseType selBase = data.config.baseTypes.get(selectedBaseIndex);
                String currentFacing = (flipGateFacingOnSave ? selBase.gateFacing.getOpposite() : selBase.gateFacing)
                        .getName().toUpperCase(Locale.ROOT);
                Button flipBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Gate Direction: ", "閘門朝向: ", "闸门朝向: ", "ゲート向き: ") + "§b" + currentFacing + " §7" + ThoroughbredData.tr("[⇄ Flip 180°]", "[⇄ 翻轉 180°]", "[⇄ 翻转 180°]", "[⇄ 180°反転]")), btn -> {
                    this.flipGateFacingOnSave = !this.flipGateFacingOnSave;
                    this.init();
                }).bounds(centerX - 110, topY + 122, 220, 20).build();
                this.addRenderableWidget(flipBtn);
            }

            Button confirmBtn = AutoFitButton.create(Component.literal(isEdit
                    ? ("§a§l" + ThoroughbredData.tr("Save Base Changes", "儲存賽道變更", "保存赛道变更", "コース変更を保存"))
                    : ("§a§l" + ThoroughbredData.tr("Save Race Base Type", "儲存賽道規格", "保存赛道规格", "コース規格を保存"))), btn -> {
                if (isEdit && !data.config.baseTypes.isEmpty()) {
                    RacingManager.RaceBaseType selBase = data.config.baseTypes.get(selectedBaseIndex);
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_EDIT_BASE_TYPE,
                            selBase.baseId, this.newBaseSurface, "", "",
                            this.newBaseDistance, this.flipGateFacingOnSave ? 1 : 0
                    ));
                } else {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                            data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_SAVE_BASE_TYPE,
                            this.newBaseSurface, "", "", "", this.newBaseDistance, 0
                    ));
                }
                this.subView = VIEW_MAIN;
            }).bounds(centerX - 110, topY + 188, 135, 20).build();

            Button backBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Back", "返回", "返回", "戻る")), btn -> {
                this.subView = VIEW_MAIN;
                this.init();
            }).bounds(centerX + 35, topY + 188, 75, 20).build();

            this.addRenderableWidget(surfBtn);
            this.addRenderableWidget(minus200);
            this.addRenderableWidget(minus100);
            this.addRenderableWidget(plus100);
            this.addRenderableWidget(plus200);
            this.addRenderableWidget(confirmBtn);
            this.addRenderableWidget(backBtn);
        }

        private boolean validateAndApplyPurseText(String raw) {
            if (raw == null) return false;
            String clean = raw.replace("$", "").replace(",", "").trim();
            if (clean.isEmpty() || !clean.matches("\\d+")) {
                return false;
            }
            try {
                long val = Long.parseLong(clean);
                if (val < 100L || (val % 10L) != 0L) {
                    return false;
                }
                this.newRacePurse = val;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private void initCreateOrEditRaceView(int centerX, int topY, boolean isEdit) {
            List<RacingManager.RaceBaseType> bases = data.config.baseTypes;
            this.targetBaseIdxForRace = Mth.clamp(this.targetBaseIdxForRace, 0, Math.max(0, bases.size() - 1));
            RacingManager.RaceBaseType targetBase = bases.get(this.targetBaseIdxForRace);

            // Preserve any text typed into EditBoxes before rebuilding widgets
            if (this.newRaceNameBox != null && !this.newRaceNameBox.getValue().isEmpty()) {
                this.savedRaceNameText = this.newRaceNameBox.getValue();
            } else if (this.savedRaceNameText == null || this.savedRaceNameText.isEmpty()) {
                this.savedRaceNameText = data.config.racecourseName + " Stakes";
            }
            if (this.newRacePurseBox != null) {
                this.savedPurseText = this.newRacePurseBox.getValue();
            }
            if (this.newRaceSeriesTagBox != null) {
                this.newRaceSeriesTag = this.newRaceSeriesTagBox.getValue().trim();
            }
            this.isPurseValid = validateAndApplyPurseText(this.savedPurseText);

            String grade = RacingManager.RACE_GRADES[this.newRaceGradeIdx % RacingManager.RACE_GRADES.length];
            boolean isG1 = "G1".equalsIgnoreCase(grade);
            if (!isG1) {
                this.g1FxTabActive = false;
            }

            // Step 16: Top Tab Switcher when Grade is G1 ([⚙ Race Settings] vs [✨ G1 Special FX Studio])
            if (isG1) {
                Button settingsTabBtn = AutoFitButton.create(
                        Component.literal(!this.g1FxTabActive
                                ? ("§e§l" + ThoroughbredData.tr("[⚙ Race Settings]", "[⚙ 賽事設定]", "[⚙ 赛事设定]", "[⚙ レース設定]"))
                                : ("§7" + ThoroughbredData.tr("⚙ Race Settings", "⚙ 賽事設定", "⚙ 赛事设定", "⚙ レース設定"))),
                        btn -> {
                            this.g1FxTabActive = false;
                            this.init();
                        }).bounds(centerX - 130, topY + 15, 126, 13).build();

                Button g1FxTabBtn = AutoFitButton.create(
                        Component.literal(this.g1FxTabActive
                                ? ("§b§l" + ThoroughbredData.tr("[✨ G1 Special FX Studio]", "[✨ G1 特效工作室]", "[✨ G1 特效工作室]", "[✨ G1 演出スタジオ]"))
                                : ("§b" + ThoroughbredData.tr("✨ G1 Special FX Studio", "✨ G1 特效工作室", "✨ G1 特效工作室", "✨ G1 演出スタジオ"))),
                        btn -> {
                            this.g1FxTabActive = true;
                            this.init();
                        }).bounds(centerX + 4, topY + 15, 136, 13).build();

                this.addRenderableWidget(settingsTabBtn);
                this.addRenderableWidget(g1FxTabBtn);
            }

            if (this.g1FxTabActive && isG1) {
                // =============================================================
                // TAB 2: 2-COLUMN G1 BROADCAST CHOREOGRAPHY & TIMELINE STUDIO
                // =============================================================
                int leftX = centerX - 194;
                int rightX = centerX + 4;
                int colW = 190;

                // --- LEFT COLUMN: VISUAL LAYER SELECTORS & PRE-CLIMAX TEXT TOGGLES ---
                String introName = RacingManager.G1_INTROS[this.g1IntroIdx % RacingManager.G1_INTROS.length];
                Button introBtn = AutoFitButton.create(Component.literal(clipBtnText("1. Act 1 FX: §e" + introName, colW - 8)), btn -> {
                    this.g1IntroIdx = (this.g1IntroIdx + 1) % RacingManager.G1_INTROS.length;
                    this.init();
                }).bounds(leftX, topY + 30, colW, 14).build();

                String bridgeName = RacingManager.G1_BRIDGES[this.g1Act2Idx % RacingManager.G1_BRIDGES.length];
                Button bridgeBtn = AutoFitButton.create(Component.literal(clipBtnText("2. Act 2 FX: " + (this.g1Act2Idx == 0 ? "§8" : "§a") + bridgeName, colW - 8)), btn -> {
                    this.g1Act2Idx = (this.g1Act2Idx + 1) % RacingManager.G1_BRIDGES.length;
                    this.init();
                }).bounds(leftX, topY + 45, colW, 14).build();

                String dropName = RacingManager.G1_DROPS[this.g1DropIdx % RacingManager.G1_DROPS.length];
                Button dropBtn = AutoFitButton.create(Component.literal(clipBtnText("3. Drop Style: §6" + dropName, colW - 8)), btn -> {
                    this.g1DropIdx = (this.g1DropIdx + 1) % RacingManager.G1_DROPS.length;
                    this.init();
                }).bounds(leftX, topY + 60, colW, 14).build();

                String emblemName = RacingManager.G1_EMBLEMS[this.g1EmblemIdx % RacingManager.G1_EMBLEMS.length];
                Button emblemBtn = AutoFitButton.create(Component.literal(clipBtnText("4. Crest: §b" + emblemName, colW - 8)), btn -> {
                    this.g1EmblemIdx = (this.g1EmblemIdx + 1) % RacingManager.G1_EMBLEMS.length;
                    this.init();
                }).bounds(leftX, topY + 75, colW, 14).build();

                String trophyName = RacingManager.G1_TROPHIES[this.g1TrophyIdx % RacingManager.G1_TROPHIES.length];
                Button trophyBtn = AutoFitButton.create(Component.literal(clipBtnText("Cup: §f" + trophyName, 88)), btn -> {
                    this.g1TrophyIdx = (this.g1TrophyIdx + 1) % RacingManager.G1_TROPHIES.length;
                    this.init();
                }).bounds(leftX, topY + 90, 94, 14).build();

                String themeName = RacingManager.G1_THEMES[this.g1ThemeIdx % RacingManager.G1_THEMES.length];
                Button themeBtn = AutoFitButton.create(Component.literal(clipBtnText("Theme: §6" + themeName, 88)), btn -> {
                    this.g1ThemeIdx = (this.g1ThemeIdx + 1) % RacingManager.G1_THEMES.length;
                    this.init();
                }).bounds(leftX + 96, topY + 90, 94, 14).build();

                String preNameLabel = (this.g1ShowNamePreClimax > 0) ? "§aAct 1-2" : "§eClimax";
                Button preNameBtn = AutoFitButton.create(Component.literal(clipBtnText("Name: " + preNameLabel, 88)), btn -> {
                    this.g1ShowNamePreClimax = (this.g1ShowNamePreClimax == 0) ? 1 : 0;
                    this.init();
                }).bounds(leftX, topY + 105, 94, 14).build();

                String preTrackLabel = (this.g1ShowTrackPreClimax > 0) ? "§aAct 1-2" : "§eClimax";
                Button preTrackBtn = AutoFitButton.create(Component.literal(clipBtnText("Track: " + preTrackLabel, 88)), btn -> {
                    this.g1ShowTrackPreClimax = (this.g1ShowTrackPreClimax == 0) ? 1 : 0;
                    this.init();
                }).bounds(leftX + 96, topY + 105, 94, 14).build();

                String pyroName = RacingManager.G1_PYROS[this.g1PyroIdx % RacingManager.G1_PYROS.length];
                Button pyroBtn = AutoFitButton.create(Component.literal(clipBtnText("5. Climax Pyro: §d" + pyroName, colW - 8)), btn -> {
                    this.g1PyroIdx = (this.g1PyroIdx + 1) % RacingManager.G1_PYROS.length;
                    this.init();
                }).bounds(leftX, topY + 120, colW, 14).build();

                this.addRenderableWidget(introBtn);
                this.addRenderableWidget(bridgeBtn);
                this.addRenderableWidget(dropBtn);
                this.addRenderableWidget(emblemBtn);
                this.addRenderableWidget(trophyBtn);
                this.addRenderableWidget(themeBtn);
                this.addRenderableWidget(preNameBtn);
                this.addRenderableWidget(preTrackBtn);
                this.addRenderableWidget(pyroBtn);

                // --- RIGHT COLUMN: FANFARE SYNC, TIMING & PRE-CLIMAX TRACK TOGGLE ---
                String chosenFanfare = data.availableFanfares.get(this.newRaceFanfareIdx % data.availableFanfares.size());
                int fanfareTicks = RacingManager.getFanfareDurationTicks(chosenFanfare);
                float fanfareSec = fanfareTicks / 20.0F;
                Button quickFanfareBtn = AutoFitButton.create(
                        Component.literal(clipBtnText(String.format(Locale.ROOT, "♪ §b%s §7(%.1fs) [⇄]", chosenFanfare, fanfareSec), colW - 8)),
                        btn -> {
                            this.newRaceFanfareIdx = (this.newRaceFanfareIdx + 1) % data.availableFanfares.size();
                            this.init();
                        }).bounds(rightX, topY + 30, colW, 14).build();
                this.addRenderableWidget(quickFanfareBtn);

                // Row 2 Right: Act 1 Duration Stepper Buttons (-1.0s, -0.5s, +0.5s, +1.0s)
                int stepBaseX = rightX + 76;
                Button a1Sub10 = AutoFitButton.create(Component.literal("-1s"), btn -> {
                    this.g1Act1Tenths = Math.max(0, this.g1Act1Tenths - 10);
                    this.init();
                }).bounds(stepBaseX, topY + 45, 27, 14).build();
                Button a1Sub5 = AutoFitButton.create(Component.literal("-.5"), btn -> {
                    this.g1Act1Tenths = Math.max(0, this.g1Act1Tenths - 5);
                    this.init();
                }).bounds(stepBaseX + 29, topY + 45, 27, 14).build();
                Button a1Add5 = AutoFitButton.create(Component.literal("+.5"), btn -> {
                    this.g1Act1Tenths = Math.min(300, this.g1Act1Tenths + 5);
                    this.init();
                }).bounds(stepBaseX + 58, topY + 45, 27, 14).build();
                Button a1Add10 = AutoFitButton.create(Component.literal("+1s"), btn -> {
                    this.g1Act1Tenths = Math.min(300, this.g1Act1Tenths + 10);
                    this.init();
                }).bounds(stepBaseX + 87, topY + 45, 27, 14).build();
                this.addRenderableWidget(a1Sub10);
                this.addRenderableWidget(a1Sub5);
                this.addRenderableWidget(a1Add5);
                this.addRenderableWidget(a1Add10);

                // Row 3 Right: Act 2 Duration Stepper Buttons (-1.0s, -0.5s, +0.5s, +1.0s)
                boolean act2Enabled = this.g1Act2Idx > 0;
                Button a2Sub10 = AutoFitButton.create(Component.literal("-1s"), btn -> {
                    this.g1Act2Tenths = Math.max(5, this.g1Act2Tenths - 10);
                    this.init();
                }).bounds(stepBaseX, topY + 60, 27, 14).build();
                Button a2Sub5 = AutoFitButton.create(Component.literal("-.5"), btn -> {
                    this.g1Act2Tenths = Math.max(5, this.g1Act2Tenths - 5);
                    this.init();
                }).bounds(stepBaseX + 29, topY + 60, 27, 14).build();
                Button a2Add5 = AutoFitButton.create(Component.literal("+.5"), btn -> {
                    this.g1Act2Tenths = Math.min(300, this.g1Act2Tenths + 5);
                    this.init();
                }).bounds(stepBaseX + 58, topY + 60, 27, 14).build();
                Button a2Add10 = AutoFitButton.create(Component.literal("+1s"), btn -> {
                    this.g1Act2Tenths = Math.min(300, this.g1Act2Tenths + 10);
                    this.init();
                }).bounds(stepBaseX + 87, topY + 60, 27, 14).build();
                a2Sub10.active = act2Enabled;
                a2Sub5.active = act2Enabled;
                a2Add5.active = act2Enabled;
                a2Add10.active = act2Enabled;
                this.addRenderableWidget(a2Sub10);
                this.addRenderableWidget(a2Sub5);
                this.addRenderableWidget(a2Add5);
                this.addRenderableWidget(a2Add10);

                // Row 4 Right: Post-Fanfare Hold Stepper Buttons (-1.0s, -0.5s, +0.5s, +1.0s)
                Button hSub10 = AutoFitButton.create(Component.literal("-1s"), btn -> {
                    this.g1HoldTenths = Math.max(0, this.g1HoldTenths - 10);
                    this.init();
                }).bounds(stepBaseX, topY + 75, 27, 14).build();
                Button hSub5 = AutoFitButton.create(Component.literal("-.5"), btn -> {
                    this.g1HoldTenths = Math.max(0, this.g1HoldTenths - 5);
                    this.init();
                }).bounds(stepBaseX + 29, topY + 75, 27, 14).build();
                Button hAdd5 = AutoFitButton.create(Component.literal("+.5"), btn -> {
                    this.g1HoldTenths = Math.min(150, this.g1HoldTenths + 5);
                    this.init();
                }).bounds(stepBaseX + 58, topY + 75, 27, 14).build();
                Button hAdd10 = AutoFitButton.create(Component.literal("+1s"), btn -> {
                    this.g1HoldTenths = Math.min(150, this.g1HoldTenths + 10);
                    this.init();
                }).bounds(stepBaseX + 87, topY + 75, 27, 14).build();
                this.addRenderableWidget(hSub10);
                this.addRenderableWidget(hSub5);
                this.addRenderableWidget(hAdd5);
                this.addRenderableWidget(hAdd10);

                // Row 5 Right: Title Slam Speed & Preview Phase Lock
                String slamLabel = RacingManager.G1_SLAM_SPEEDS[this.g1SlamSpeedIdx % RacingManager.G1_SLAM_SPEEDS.length];
                Button slamBtn = AutoFitButton.create(Component.literal(clipBtnText("Slam: §e" + slamLabel, 88)), btn -> {
                    this.g1SlamSpeedIdx = (this.g1SlamSpeedIdx + 1) % RacingManager.G1_SLAM_SPEEDS.length;
                    this.init();
                }).bounds(rightX, topY + 90, 94, 14).build();

                String[] viewModes = {"Auto Cycle", "Lock Act 1", "Lock Act 2", "Lock Climax"};
                String viewLabel = viewModes[this.g1PreviewLockMode % viewModes.length];
                Button viewLockBtn = AutoFitButton.create(Component.literal(clipBtnText("View: §b" + viewLabel, 88)), btn -> {
                    this.g1PreviewLockMode = (this.g1PreviewLockMode + 1) % 4;
                    if (this.g1PreviewLockMode == 2 && this.g1Act2Idx == 0) {
                        this.g1PreviewLockMode = 3;
                    }
                    this.init();
                }).bounds(rightX + 96, topY + 90, 94, 14).build();
                this.addRenderableWidget(slamBtn);
                this.addRenderableWidget(viewLockBtn);

                // Row 6 Right: Climax Badge Text Toggle ("★ G1 ★" vs "★ GRADE 1 ★")
                String climaxBadgeLabel = (this.g1ClimaxBadgeStyle > 0) ? "§e★ GRADE 1 ★" : "§b★ G1 ★";
                Button climaxBadgeBtn = AutoFitButton.create(Component.literal(clipBtnText("Climax Badge: " + climaxBadgeLabel, colW - 8)), btn -> {
                    this.g1ClimaxBadgeStyle = (this.g1ClimaxBadgeStyle == 0) ? 1 : 0;
                    this.init();
                }).bounds(rightX, topY + 105, colW, 14).build();
                this.addRenderableWidget(climaxBadgeBtn);

                // Row 7 Right: Test Full G1 Broadcast & Audio + Stop
                Button testG1FxBtn = AutoFitButton.create(Component.literal("§a§l▶ Test Full G1 Broadcast"), btn -> {
                    int clipTicks = RacingManager.playClientFanfareAudio(chosenFanfare, this.newRaceVolume);
                    int dur = clipTicks > 0 ? clipTicks : fanfareTicks;
                    String rTitle = (this.savedRaceNameText != null && !this.savedRaceNameText.isBlank())
                            ? this.savedRaceNameText.trim() : "Official Stakes";
                    String bText = targetBase.getStructuredBottomName(data.config.racecourseName);
                    ThoroughbredRunEngine.triggerClientRaceLogoDisplay(
                            "G1", rTitle, bText, dur, clipTicks > 0, getG1PresetString());
                }).bounds(rightX, topY + 120, 146, 14).build();

                Button stopG1FxBtn = AutoFitButton.create(Component.literal("§c■ Stop"), btn -> {
                    RacingManager.stopClientFanfareAudio();
                    ThoroughbredRunEngine.clearClientRaceLogoDisplay();
                }).bounds(rightX + 148, topY + 120, 42, 14).build();

                this.addRenderableWidget(testG1FxBtn);
                this.addRenderableWidget(stopG1FxBtn);
            } else {
                // =============================================================
                // TAB 1: STANDARD RACE SETTINGS TAB (WITH AGE, SEX & SERIES TAGS!)
                // =============================================================
                this.newRaceNameBox = new EditBox(this.font, centerX - 110, topY + 29, 220, 14, Component.literal(ThoroughbredData.tr("Race Name", "賽事名稱", "赛事名称", "レース名")));
                this.newRaceNameBox.setMaxLength(48);
                this.newRaceNameBox.setValue(this.savedRaceNameText);
                this.newRaceNameBox.setResponder(val -> this.savedRaceNameText = val);
                this.addRenderableWidget(this.newRaceNameBox);

                // Row 2: Base Switcher & Grade Selector
                String baseShort = ThoroughbredData.tr(targetBase.surface, targetBase.surface, targetBase.surface, targetBase.surface) + " " + targetBase.distanceMeters + "m";
                Button switchBaseBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Base: ", "賽道: ", "赛道: ", "コース: ") + "§6" + baseShort + " §7[⇄]"), btn -> {
                    if (!bases.isEmpty()) {
                        this.targetBaseIdxForRace = (this.targetBaseIdxForRace + 1) % bases.size();
                        this.init();
                    }
                }).bounds(centerX - 110, topY + 45, 118, 15).build();

                Button gradeBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Grade: ", "級別: ", "级别: ", "格付: ") + "§e" + grade), btn -> {
                    this.newRaceGradeIdx = (this.newRaceGradeIdx + 1) % RacingManager.RACE_GRADES.length;
                    this.init();
                }).bounds(centerX + 12, topY + 45, 98, 15).build();

                // Row 3: Free-Typed 1st Place Purse & Required Career Earnings
                this.newRacePurseBox = new EditBox(this.font, centerX - 78, topY + 62, 76, 15, Component.literal(ThoroughbredData.tr("1st Place Purse", "頭馬獎金", "头马奖金", "1着賞金")));
                this.newRacePurseBox.setMaxLength(12);
                this.newRacePurseBox.setValue(this.savedPurseText);
                this.newRacePurseBox.setTextColor(this.isPurseValid ? 0x55FF55 : 0xFF5555);
                this.newRacePurseBox.setResponder(val -> {
                    this.savedPurseText = val;
                    this.isPurseValid = validateAndApplyPurseText(val);
                    this.newRacePurseBox.setTextColor(this.isPurseValid ? 0x55FF55 : 0xFF5555);
                    if (this.saveRaceBtnRef != null) {
                        this.saveRaceBtnRef.active = this.isPurseValid;
                    }
                });
                this.addRenderableWidget(this.newRacePurseBox);

                long[] reqSteps = {0L, 5000L, 10000L, 25000L, 50000L, 100000L, 250000L};
                String reqLabel = this.newRaceReqEarnings <= 0L
                        ? (ThoroughbredData.tr("Req: ", "獎金門檻: ", "奖金门槛: ", "収得条件: ") + "§7" + ThoroughbredData.tr("None ($0)", "無 ($0)", "无 ($0)", "なし ($0)"))
                        : String.format(Locale.ROOT, ThoroughbredData.tr("Req: ", "獎金門檻: ", "奖金门槛: ", "収得条件: ") + "§e$%,d", this.newRaceReqEarnings);
                Button reqBtn = AutoFitButton.create(Component.literal(reqLabel), btn -> {
                    int nextIdx = 0;
                    for (int i = 0; i < reqSteps.length; i++) {
                        if (this.newRaceReqEarnings == reqSteps[i]) {
                            nextIdx = (i + 1) % reqSteps.length;
                            break;
                        }
                    }
                    this.newRaceReqEarnings = reqSteps[nextIdx];
                    this.init();
                }).bounds(centerX + 2, topY + 62, 108, 15).build();

                // Row 4: Age Requirement (4 Real-Life Divisions) & Gender Requirement (Automatic Barring)
                String ageLabel = RacingManager.getAgeRequirementLabel(this.newRaceAgeReq);
                Button ageReqBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Age: ", "馬齡: ", "马龄: ", "馬齢: ") + "§b" + ageLabel), btn -> {
                    this.newRaceAgeReq = (this.newRaceAgeReq + 1) % 4;
                    this.init();
                }).bounds(centerX - 110, topY + 79, 114, 15).build();

                String genLabel = RacingManager.getGenderRequirementLabel(this.newRaceGenderReq);
                Button genReqBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Sex: ", "性別: ", "性别: ", "性別: ") + "§d" + genLabel), btn -> {
                    this.newRaceGenderReq = (this.newRaceGenderReq + 1) % 2;
                    this.init();
                }).bounds(centerX + 6, topY + 79, 104, 15).build();

                // Row 5: Series Tag Preset Selector + Custom Series Tag EditBox (Type any custom tag or cycle defaults!)
                List<String> availSeriesTags = RacingManager.getAvailableSeriesTags();
                String seriesLabel = (this.newRaceSeriesTag == null || this.newRaceSeriesTag.isBlank())
                        ? ("§7" + ThoroughbredData.tr("None", "無", "无", "なし"))
                        : ("§6" + RacingManager.getLocalizedSeriesTag(this.newRaceSeriesTag));
                Button seriesTagBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Series: ", "系列: ", "系列: ", "シリーズ: ") + seriesLabel + " §7[⇄]"), btn -> {
                    List<String> tags = RacingManager.getAvailableSeriesTags();
                    int curIdx = 0;
                    String curVal = this.newRaceSeriesTag != null ? this.newRaceSeriesTag.trim() : "";
                    for (int i = 0; i < tags.size(); i++) {
                        if (tags.get(i).equalsIgnoreCase(curVal)) {
                            curIdx = i;
                            break;
                        }
                    }
                    this.newRaceSeriesTag = tags.get((curIdx + 1) % tags.size());
                    if (this.newRaceSeriesTagBox != null) {
                        this.newRaceSeriesTagBox.setValue(this.newRaceSeriesTag);
                    }
                    this.init();
                }).bounds(centerX - 110, topY + 96, 114, 15).build();

                this.newRaceSeriesTagBox = new EditBox(this.font, centerX + 6, topY + 96, 104, 15,
                        Component.literal(ThoroughbredData.tr("Series Tag", "系列賽標籤", "系列赛标签", "シリーズ冠")));
                this.newRaceSeriesTagBox.setMaxLength(36);
                this.newRaceSeriesTagBox.setHint(Component.literal("§8" + ThoroughbredData.tr("Custom Tag...", "自訂系列標籤...", "自订系列标签...", "カスタムタグ...")));
                this.newRaceSeriesTagBox.setValue(this.newRaceSeriesTag != null ? this.newRaceSeriesTag : "");
                this.newRaceSeriesTagBox.setResponder(val -> this.newRaceSeriesTag = val != null ? val.trim() : "");
                this.addRenderableWidget(this.newRaceSeriesTagBox);

                // Row 6: Fanfare Selector & Volume Controls
                String fanfare = data.availableFanfares.get(this.newRaceFanfareIdx % data.availableFanfares.size());
                Button fanfareBtn = AutoFitButton.create(Component.literal("♪ §b" + fanfare), btn -> {
                    this.newRaceFanfareIdx = (this.newRaceFanfareIdx + 1) % data.availableFanfares.size();
                    this.init();
                }).bounds(centerX - 110, topY + 113, 126, 15).build();

                Button fanfareVolMinus = AutoFitButton.create(Component.literal("-"), btn -> {
                    this.newRaceVolume = Math.max(5, this.newRaceVolume - 10);
                    this.init();
                }).bounds(centerX + 18, topY + 113, 16, 15).build();

                Button fanfareVolPlus = AutoFitButton.create(Component.literal("+"), btn -> {
                    this.newRaceVolume = Math.min(100, this.newRaceVolume + 10);
                    this.init();
                }).bounds(centerX + 58, topY + 113, 16, 15).build();

                Button fanfareTestBtn = AutoFitButton.create(Component.literal("§a🔊"), btn -> {
                    String chosenFanfare = data.availableFanfares.get(this.newRaceFanfareIdx % data.availableFanfares.size());
                    RacingManager.playClientFanfareAudio(chosenFanfare, this.newRaceVolume);
                }).bounds(centerX + 76, topY + 113, 18, 15).build();

                Button fanfareStopBtn = AutoFitButton.create(Component.literal("§c■"), btn -> {
                    RacingManager.stopClientFanfareAudio();
                }).bounds(centerX + 96, topY + 113, 14, 15).build();

                // Row 7: Race Theme Music Selector & Volume Controls
                String raceMus = data.availableMusic.get(this.newRaceMusicIdx % data.availableMusic.size());
                Button raceMusBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Theme: ", "配樂: ", "配乐: ", "BGM: ") + "§e" + raceMus), btn -> {
                    this.newRaceMusicIdx = (this.newRaceMusicIdx + 1) % data.availableMusic.size();
                    this.init();
                }).bounds(centerX - 110, topY + 129, 126, 15).build();

                Button musVolMinus = AutoFitButton.create(Component.literal("-"), btn -> {
                    this.newRaceMusicVol = Math.max(5, this.newRaceMusicVol - 10);
                    this.init();
                }).bounds(centerX + 18, topY + 129, 16, 15).build();

                Button musVolPlus = AutoFitButton.create(Component.literal("+"), btn -> {
                    this.newRaceMusicVol = Math.min(100, this.newRaceMusicVol + 10);
                    this.init();
                }).bounds(centerX + 58, topY + 129, 16, 15).build();

                Button musTestBtn = AutoFitButton.create(Component.literal("§a🔊"), btn -> {
                    String chosen = data.availableMusic.get(this.newRaceMusicIdx % data.availableMusic.size());
                    RacingManager.playClientRaceMusic(chosen, this.newRaceMusicVol, false);
                }).bounds(centerX + 76, topY + 129, 18, 15).build();

                Button musStopBtn = AutoFitButton.create(Component.literal("§c■"), btn -> {
                    RacingManager.stopClientRaceMusic();
                }).bounds(centerX + 96, topY + 129, 14, 15).build();

                // Row 8: 600m Final Spurt Music Selector & Volume Controls
                String spurtMus = data.availableMusic.get(this.newRaceSpurtIdx % data.availableMusic.size());
                Button spurtMusBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Spurt: ", "衝刺: ", "冲刺: ", "スパート: ") + "§c" + spurtMus), btn -> {
                    this.newRaceSpurtIdx = (this.newRaceSpurtIdx + 1) % data.availableMusic.size();
                    this.init();
                }).bounds(centerX - 110, topY + 145, 126, 15).build();

                Button spurtVolMinus = AutoFitButton.create(Component.literal("-"), btn -> {
                    this.newRaceSpurtVol = Math.max(5, this.newRaceSpurtVol - 10);
                    this.init();
                }).bounds(centerX + 18, topY + 145, 16, 15).build();

                Button spurtVolPlus = AutoFitButton.create(Component.literal("+"), btn -> {
                    this.newRaceSpurtVol = Math.min(100, this.newRaceSpurtVol + 10);
                    this.init();
                }).bounds(centerX + 58, topY + 145, 16, 15).build();

                Button spurtTestBtn = AutoFitButton.create(Component.literal("§a🔊"), btn -> {
                    String chosen = data.availableMusic.get(this.newRaceSpurtIdx % data.availableMusic.size());
                    RacingManager.playClientSpurtMusic(chosen, this.newRaceSpurtVol);
                }).bounds(centerX + 76, topY + 145, 18, 15).build();

                Button spurtStopBtn = AutoFitButton.create(Component.literal("§c■"), btn -> {
                    RacingManager.stopClientSpurtMusic();
                }).bounds(centerX + 96, topY + 145, 14, 15).build();

                this.addRenderableWidget(switchBaseBtn);
                this.addRenderableWidget(gradeBtn);
                this.addRenderableWidget(reqBtn);
                this.addRenderableWidget(ageReqBtn);
                this.addRenderableWidget(genReqBtn);
                this.addRenderableWidget(seriesTagBtn);
                this.addRenderableWidget(fanfareBtn);
                this.addRenderableWidget(fanfareVolMinus);
                this.addRenderableWidget(fanfareVolPlus);
                this.addRenderableWidget(fanfareTestBtn);
                this.addRenderableWidget(fanfareStopBtn);
                this.addRenderableWidget(raceMusBtn);
                this.addRenderableWidget(musVolMinus);
                this.addRenderableWidget(musVolPlus);
                this.addRenderableWidget(musTestBtn);
                this.addRenderableWidget(musStopBtn);
                this.addRenderableWidget(spurtMusBtn);
                this.addRenderableWidget(spurtVolMinus);
                this.addRenderableWidget(spurtVolPlus);
                this.addRenderableWidget(spurtTestBtn);
                this.addRenderableWidget(spurtStopBtn);
            }

            // Bottom Row: Save & Back Buttons (accessible from both tabs!)
            Button saveBtn = AutoFitButton.create(Component.literal(isEdit
                    ? ("§a§l" + ThoroughbredData.tr("Save Race Changes", "儲存賽事變更", "保存赛事变更", "レース変更を保存"))
                    : ("§a§l" + ThoroughbredData.tr("Create Race", "建立賽事", "创建赛事", "レース作成"))), btn -> {
                RacingManager.stopClientFanfareAudio();
                RacingManager.stopAllClientRaceMusic();
                ThoroughbredRunEngine.clearClientRaceLogoDisplay();
                if (!bases.isEmpty()) {
                    RacingManager.RaceBaseType sourceBase = bases.get(selectedBaseIndex);
                    RacingManager.RaceBaseType destBase = bases.get(this.targetBaseIdxForRace);
                    String chosenGrade = RacingManager.RACE_GRADES[this.newRaceGradeIdx % RacingManager.RACE_GRADES.length];
                    String chosenFanfare = data.availableFanfares.get(this.newRaceFanfareIdx % data.availableFanfares.size());
                    String chosenMusic = data.availableMusic.get(this.newRaceMusicIdx % data.availableMusic.size());
                    String chosenSpurt = data.availableMusic.get(this.newRaceSpurtIdx % data.availableMusic.size());
                    String raceNameVal = (this.newRaceNameBox != null && !this.newRaceNameBox.getValue().isBlank())
                            ? this.newRaceNameBox.getValue() : this.savedRaceNameText;

                    if (isEdit && !this.editingRaceId.isEmpty()) {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                                data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_EDIT_RACE,
                                sourceBase.baseId, this.editingRaceId,
                                destBase.baseId + "|" + chosenGrade + "|" + raceNameVal,
                                chosenFanfare, this.newRaceVolume, 0,
                                this.newRacePurse, this.newRaceReqEarnings,
                                chosenMusic, chosenSpurt,
                                this.newRaceMusicVol, this.newRaceSpurtVol,
                                getG1PresetString(),
                                this.newRaceAgeReq, this.newRaceGenderReq, (this.newRaceSeriesTagBox != null ? this.newRaceSeriesTagBox.getValue().trim() : this.newRaceSeriesTag)
                        ));
                        this.selectedBaseIndex = this.targetBaseIdxForRace;
                    } else {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RacingConsoleActionPacket(
                                data.consolePos, ModNetwork.RacingConsoleActionPacket.ACTION_CREATE_RACE,
                                destBase.baseId, raceNameVal, chosenGrade,
                                chosenFanfare, this.newRaceVolume, 0,
                                this.newRacePurse, this.newRaceReqEarnings,
                                chosenMusic, chosenSpurt,
                                this.newRaceMusicVol, this.newRaceSpurtVol,
                                getG1PresetString(),
                                this.newRaceAgeReq, this.newRaceGenderReq, (this.newRaceSeriesTagBox != null ? this.newRaceSeriesTagBox.getValue().trim() : this.newRaceSeriesTag)
                        ));
                        this.selectedBaseIndex = this.targetBaseIdxForRace;
                    }
                    this.subView = VIEW_MAIN;
                }
            }).bounds(centerX - 110, topY + 206, 130, 18).build();
            saveBtn.active = this.isPurseValid;
            this.saveRaceBtnRef = saveBtn;

            Button backBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Back", "返回", "返回", "戻る")), btn -> {
                RacingManager.stopClientFanfareAudio();
                RacingManager.stopAllClientRaceMusic();
                ThoroughbredRunEngine.clearClientRaceLogoDisplay();
                this.subView = VIEW_MAIN;
                this.init();
            }).bounds(centerX + 30, topY + 206, 80, 18).build();

            this.addRenderableWidget(saveBtn);
            this.addRenderableWidget(backBtn);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.subView == VIEW_MAIN && !data.config.baseTypes.isEmpty()) {
                int centerX = this.width / 2;
                int topY = (this.height - 236) / 2;

                int tabX = centerX - 196;
                int tabY = topY + 58;
                List<RacingManager.RaceBaseType> bases = data.config.baseTypes;
                for (int i = 0; i < Math.min(7, bases.size()); i++) {
                    int iy = tabY + (i * 20);
                    if (mouseX >= tabX && mouseX <= tabX + 122 && mouseY >= iy && mouseY <= iy + 18) {
                        this.selectedBaseIndex = i;
                        this.selectedRaceIndex = 0;
                        this.init();
                        return true;
                    }
                }

                RacingManager.RaceBaseType selBase = bases.get(selectedBaseIndex);
                int raceX = centerX - 68;
                int raceListY = topY + 94;
                for (int i = 0; i < Math.min(4, selBase.races.size()); i++) {
                    int iy = raceListY + (i * 21);
                    if (mouseX >= raceX && mouseX <= raceX + 154 && mouseY >= iy && mouseY <= iy + 19) {
                        this.selectedRaceIndex = i;
                        this.init();
                        return true;
                    }
                }

                int stallX = centerX + 92;
                int stallY = topY + 94;
                int maxStalls = Math.min(4, selBase.startGatePositions.size());
                for (int i = 0; i < maxStalls; i++) {
                    int iy = stallY + (i * 11);
                    if (mouseX >= stallX && mouseX <= stallX + 104 && mouseY >= iy && mouseY <= iy + 10) {
                        this.selectedStallNumber = i + 1;
                        return true;
                    }
                }

                int horseY = topY + 146;
                for (int i = 0; i < Math.min(3, data.nearbyHorses.size()); i++) {
                    int iy = horseY + (i * 11);
                    if (mouseX >= stallX && mouseX <= stallX + 104 && mouseY >= iy && mouseY <= iy + 10) {
                        this.selectedHorseIndex = i;
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int topY = (this.height - 236) / 2;

            graphics.fill(centerX - 204, topY, centerX + 204, topY + 234, 0xEE0D1117);
            graphics.fill(centerX - 204, topY, centerX + 204, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - 204, topY + 232, centerX + 204, topY + 234, 0xFFD4AF37);

            graphics.drawCenteredString(this.font, "§6§l=== " + ThoroughbredData.tr("Official Racing Console", "官方賽事控制台", "官方赛事控制台", "公式レーシングコンソール") + " ===", centerX, topY + 6, 0xFFFFFF);

            if (this.subView == VIEW_SAVE_BASE || this.subView == VIEW_EDIT_BASE) {
                renderSaveOrEditBaseModal(graphics, centerX, topY, this.subView == VIEW_EDIT_BASE);
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }
            if (this.subView == VIEW_CREATE_RACE || this.subView == VIEW_EDIT_RACE) {
                renderCreateOrEditRaceModal(graphics, centerX, topY, this.subView == VIEW_EDIT_RACE, partialTick);
                super.render(graphics, mouseX, mouseY, partialTick);
                ThoroughbredRunEngine.renderG1SpecialEffectOverlayIfActive(graphics, this.minecraft, this.width, this.height, partialTick);
                return;
            }

            graphics.drawString(this.font, "§e" + ThoroughbredData.tr("Course:", "馬場:", "马场:", "競馬場:"), centerX - 196, topY + 26, 0xFFFFFF);

            if (data.inBuildMode && !data.buildReadyToSave) {
                String buildStatus = String.format(Locale.ROOT,
                        "§e" + ThoroughbredData.tr("Building:", "規劃中:", "规划中:", "作成中:") + " §f%dG §7| §f%dCP §7| Fin:%s",
                        data.buildGateCount, data.buildCheckpointCount, data.buildHasFinishLine ? ("§a" + ThoroughbredData.tr("Yes", "是", "是", "済")) : ("§c" + ThoroughbredData.tr("No", "否", "否", "未")));
                graphics.drawString(this.font, buildStatus, centerX + 24, topY + 26, 0xFFFFFF);
            }

            graphics.fill(centerX - 198, topY + 44, centerX + 198, topY + 45, 0x55D4AF37);

            List<RacingManager.RaceBaseType> bases = data.config.baseTypes;
            if (bases.isEmpty()) {
                graphics.drawCenteredString(this.font,
                        "§7" + ThoroughbredData.tr("No Race Base Types configured on this console yet.", "此控制台尚未配置任何賽道規格。", "此控制台尚未配置任何赛道规格。", "このコンソールにはまだコース規格が設定されていません。"),
                        centerX, topY + 98, 0xAAAAAA);
                graphics.drawCenteredString(this.font,
                        "§e" + ThoroughbredData.tr("Click [+ Create Race Base Type] above to receive a Checkpoint Marker!", "點擊上方 [+ 新增賽道規格] 領取檢查點標記器！", "点击上方 [+ 新增赛道规格] 领取检查点标记器！", "上の [+ コース規格を作成] を押してマーカーを受け取ってください！"),
                        centerX, topY + 114, 0xFFD800);
                graphics.drawCenteredString(this.font,
                        "§7" + ThoroughbredData.tr("Use [M] to switch between Starting Point, Checkpoint Line (2 clicks), and Finish Line modes.", "按 [M] 切換起跑閘位、檢查線(點擊兩端)與終點線模式。", "按 [M] 切换起跑闸位、检查线(点击两端)与终点线模式。", "[M]キーで発走枠・チェックライン(2点指定)・決勝線モードを切替えます。"),
                        centerX, topY + 128, 0x888888);
            } else {
                int tabX = centerX - 196;
                graphics.drawString(this.font, "§6" + ThoroughbredData.tr("Base Types", "賽道規格", "赛道规格", "コース規格") + " (" + bases.size() + ")", tabX, topY + 48, 0xFFFFFF);
                int tabY = topY + 58;
                for (int i = 0; i < Math.min(7, bases.size()); i++) {
                    RacingManager.RaceBaseType b = bases.get(i);
                    int iy = tabY + (i * 20);
                    boolean isSel = (i == this.selectedBaseIndex);
                    graphics.fill(tabX, iy, tabX + 122, iy + 18, isSel ? 0xFF283B52 : 0xFF161C24);
                    graphics.fill(tabX, iy, tabX + 2, iy + 18, isSel ? 0xFFD4AF37 : 0xFF445566);

                    String surfLoc = ThoroughbredData.tr(b.surface, b.surface, b.surface, b.surface);
                    String label = surfLoc + " " + b.distanceMeters + "m §7(" + b.startGatePositions.size() + "G)";
                    graphics.drawString(this.font, isSel ? ("§e" + label) : ("§f" + label), tabX + 5, iy + 5, 0xFFFFFF);
                }

                RacingManager.RaceBaseType selBase = bases.get(this.selectedBaseIndex);
                String structuredName = selBase.getStructuredBottomName(data.config.racecourseName);
                graphics.drawString(this.font,
                        "§e" + ThoroughbredData.tr("Base: ", "賽道: ", "赛道: ", "コース: ") + "§f§l" + structuredName + " §7(" + ThoroughbredData.tr("Gates: ", "閘向: ", "闸向: ", "枠向: ") + selBase.gateFacing.getName().toUpperCase(Locale.ROOT) + ")",
                        centerX - 68, topY + 48, 0xFFFFFF);

                int raceX = centerX - 68;
                graphics.drawString(this.font, "§6" + ThoroughbredData.tr("Races", "賽事列表", "赛事列表", "レース一覧") + " (" + selBase.races.size() + ")", raceX, topY + 82, 0xFFFFFF);
                int raceListY = topY + 94;
                if (selBase.races.isEmpty()) {
                    graphics.drawString(this.font, "§7" + ThoroughbredData.tr("No races yet.", "尚無賽事。", "尚无赛事。", "レース未登録。"), raceX, raceListY + 12, 0x888888);
                    graphics.drawString(this.font, "§7" + ThoroughbredData.tr("Click '+ New Race'!", "請點擊「+ 新賽事」！", "请点击“+ 新赛事”！", "「+ 新規レース」を押してください！"), raceX, raceListY + 24, 0x888888);
                } else {
                    for (int i = 0; i < Math.min(4, selBase.races.size()); i++) {
                        RacingManager.RaceDefinition r = selBase.races.get(i);
                        int iy = raceListY + (i * 21);
                        boolean isSel = (i == this.selectedRaceIndex);
                        graphics.fill(raceX, iy, raceX + 154, iy + 19, isSel ? 0xFF2A3D52 : 0xFF161C24);
                        graphics.fill(raceX, iy, raceX + 2, iy + 19, isSel ? 0xFF00E5FF : 0xFF334455);

                        String seriesBadge = (r.raceSeriesTag != null && !r.raceSeriesTag.isBlank())
                                ? (" §6[" + RacingManager.getLocalizedSeriesTag(r.raceSeriesTag) + "]")
                                : "";
                        String line1 = "§e[" + r.grade + "] §f" + r.raceName + seriesBadge;
                        drawScaledLeftString(graphics, this.font, line1, raceX + 5, iy + 2, 146, 0xFFFFFF);
                        String condSummary = "§b" + RacingManager.getAgeRequirementLabel(r.ageRequirement)
                                + (r.genderRequirement == 1 ? (" §d[" + RacingManager.getGenderRequirementLabel(r.genderRequirement) + "]") : "")
                                + " §7| §a$" + String.format(Locale.ROOT, "%,d", r.firstPlacePurse);
                        drawScaledLeftString(graphics, this.font, condSummary, raceX + 5, iy + 10, 146, 0xAAAAAA);
                    }
                }

                int stallX = centerX + 92;
                graphics.drawString(this.font, "§b" + ThoroughbredData.tr("Stalls", "閘位", "闸位", "枠番") + " (" + selBase.startGatePositions.size() + ")", stallX, topY + 82, 0xFFFFFF);
                int stallY = topY + 94;
                int maxStalls = Math.min(4, selBase.startGatePositions.size());
                boolean hasRace = !selBase.races.isEmpty() && selectedRaceIndex >= 0 && selectedRaceIndex < selBase.races.size();
                long reqEarnings = hasRace ? selBase.races.get(selectedRaceIndex).requiredEarnings : 0L;

                for (int i = 0; i < maxStalls; i++) {
                    int gNum = i + 1;
                    int iy = stallY + (i * 11);
                    boolean isSel = (gNum == this.selectedStallNumber);
                    graphics.fill(stallX, iy, stallX + 104, iy + 10, isSel ? 0xFF2A3D52 : 0xFF161C24);

                    Integer hId = data.preAssignments.get(gNum);
                    String hName = "§7" + ThoroughbredData.tr("Open", "空位", "空位", "空き");

                    if (hId != null) {
                        for (ModNetwork.OpenStarterConsolePacket.HorseEntry he : data.nearbyHorses) {
                            if (he.horseId == hId) {
                                boolean ageBarred = hasRace && !RacingManager.matchesAgeRequirement(he.equineAgeYears, selBase.races.get(selectedRaceIndex).ageRequirement);
                                boolean genBarred = hasRace && !RacingManager.matchesGenderRequirement(he.gender, selBase.races.get(selectedRaceIndex).genderRequirement);
                                boolean isBarred = ageBarred || genBarred;
                                String prefix = isBarred ? ("§c[" + ThoroughbredData.tr("BARRED", "不符資格", "不符资格", "出走不可") + "] ") : (reqEarnings > 0L ? "§e" : "§a");
                                hName = prefix + he.name;
                                break;
                            }
                        }
                    }
                    String txt = "#" + gNum + ": " + hName;
                    drawScaledLeftString(graphics, this.font, txt, stallX + 3, iy + 1, 98, 0xFFFFFF);
                }

                drawScaledLeftString(graphics, this.font, "§b" + ThoroughbredData.tr("Nearby Horses", "附近純種馬", "附近纯种马", "付近の競走馬"), stallX, topY + 136, 104, 0xFFFFFF);
                int horseY = topY + 146;
                for (int i = 0; i < Math.min(3, data.nearbyHorses.size()); i++) {
                    ModNetwork.OpenStarterConsolePacket.HorseEntry he = data.nearbyHorses.get(i);
                    int iy = horseY + (i * 11);
                    boolean isSel = (i == this.selectedHorseIndex);
                    graphics.fill(stallX, iy, stallX + 104, iy + 10, isSel ? 0xFF2A3D52 : 0xFF161C24);
                    boolean ageBarred = hasRace && !RacingManager.matchesAgeRequirement(he.equineAgeYears, selBase.races.get(selectedRaceIndex).ageRequirement);
                    boolean genBarred = hasRace && !RacingManager.matchesGenderRequirement(he.gender, selBase.races.get(selectedRaceIndex).genderRequirement);
                    boolean isBarred = ageBarred || genBarred;
                    String prefix = isBarred ? ("§c[" + ThoroughbredData.tr("BARRED", "不符資格", "不符资格", "出走不可") + "] ") : (isSel ? "§e" : "§f");
                    String txt = prefix + he.name + " §7(" + he.equineAgeYears + "yo)";
                    drawScaledLeftString(graphics, this.font, txt, stallX + 3, iy + 1, 98, 0xFFFFFF);
                }
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void renderSaveOrEditBaseModal(GuiGraphics graphics, int centerX, int topY, boolean isEdit) {
            graphics.drawCenteredString(this.font,
                    isEdit ? ("§e§l" + ThoroughbredData.tr("Edit Race Base Type", "編輯賽道規格", "编辑赛道规格", "コース規格の編集"))
                           : ("§a§l" + ThoroughbredData.tr("Save New Race Base Type", "儲存新賽道規格", "保存新赛道规格", "新規コース規格の保存")),
                    centerX, topY + 24, 0xFFFFFF);
            if (!isEdit) {
                graphics.drawCenteredString(this.font,
                        String.format(Locale.ROOT, "§7" + ThoroughbredData.tr("Course Layout: §e%d Stalls §7| §b%d Checkpoints §7| §dFinish Line Ready", "賽道配置: §e%d 閘位 §7| §b%d 檢查點 §7| §d終點線就緒", "赛道配置: §e%d 闸位 §7| §b%d 检查点 §7| §d终点线就绪", "コース構成: §e%d ゲート §7| §b%d チェックポイント §7| §d決勝線完了"),
                                data.buildGateCount, data.buildCheckpointCount),
                        centerX, topY + 38, 0xCCCCCC);
            }

            graphics.drawCenteredString(this.font, "§e" + ThoroughbredData.tr("Race Distance: ", "賽道途程: ", "赛道途程: ", "コース距離: ") + "§f§l" + this.newBaseDistance + "m", centerX, topY + 84, 0xFFFFFF);

            String surfLoc = ThoroughbredData.tr(this.newBaseSurface, this.newBaseSurface, this.newBaseSurface, this.newBaseSurface);
            String previewBottom = data.config.racecourseName + " " + surfLoc + " " + this.newBaseDistance + "m";
            graphics.fill(centerX - 120, topY + 148, centerX + 120, topY + 180, 0xFF161C24);
            graphics.drawCenteredString(this.font, "§7" + ThoroughbredData.tr("Structured Base Race Type Name Preview:", "賽道規格名稱預覽:", "赛道规格名称预览:", "コース規格プレビュー:"), centerX, topY + 153, 0xAAAAAA);
            graphics.drawCenteredString(this.font, "§6§l" + previewBottom, centerX, topY + 166, 0xFFFFFF);
        }

        private void renderCreateOrEditRaceModal(GuiGraphics graphics, int centerX, int topY, boolean isEdit, float partialTick) {
            List<RacingManager.RaceBaseType> bases = data.config.baseTypes;
            this.targetBaseIdxForRace = Mth.clamp(this.targetBaseIdxForRace, 0, Math.max(0, bases.size() - 1));
            RacingManager.RaceBaseType targetBase = bases.get(this.targetBaseIdxForRace);

            String bottomText = targetBase.getStructuredBottomName(data.config.racecourseName);
            String chosenGrade = RacingManager.RACE_GRADES[this.newRaceGradeIdx % RacingManager.RACE_GRADES.length];
            boolean isG1 = "G1".equalsIgnoreCase(chosenGrade);
            String raceTitle = (this.newRaceNameBox != null && !this.newRaceNameBox.getValue().isBlank())
                    ? this.newRaceNameBox.getValue().trim()
                    : ((this.savedRaceNameText != null && !this.savedRaceNameText.isBlank()) ? this.savedRaceNameText.trim() : "Official Stakes");

            if (!isG1) {
                graphics.drawCenteredString(this.font,
                        (isEdit ? ("§e§l" + ThoroughbredData.tr("Edit Race (", "編輯賽事 (", "编辑赛事 (", "レース編集 ("))
                                : ("§a§l" + ThoroughbredData.tr("Create New Race (", "建立新賽事 (", "创建新赛事 (", "新規レース作成 ("))) + bottomText + ")",
                        centerX, topY + 16, 0xFFFFFF);
            }

            if (isG1 && this.g1FxTabActive) {
                int rightX = centerX + 4;
                // Draw Phase Duration Labels on the left of each stepper row in Right Column
                graphics.drawString(this.font, String.format(Locale.ROOT, "§eAct 1: §f%.1fs", this.g1Act1Tenths / 10.0F),
                        rightX + 2, topY + 48, 0xFFFFFF);
                String act2Str = (this.g1Act2Idx > 0)
                        ? String.format(Locale.ROOT, "§aAct 2: §f%.1fs", this.g1Act2Tenths / 10.0F)
                        : "§8Act 2: Skip";
                graphics.drawString(this.font, act2Str, rightX + 2, topY + 63, 0xFFFFFF);
                graphics.drawString(this.font, String.format(Locale.ROOT, "§bHold: §f+%.1fs", this.g1HoldTenths / 10.0F),
                        rightX + 2, topY + 78, 0xFFFFFF);

                String chosenFanfare = data.availableFanfares.get(this.newRaceFanfareIdx % data.availableFanfares.size());
                int fanfareTicks = RacingManager.getFanfareDurationTicks(chosenFanfare);

                // Draw Wide 3-Phase Timeline Bar & Live Animated Preview Viewport (topY + 136 to topY + 201)
                ThoroughbredRunEngine.renderG1StudioPreviewAndTimeline(
                        graphics, this.minecraft,
                        centerX, topY + 136,
                        raceTitle, bottomText,
                        getG1PresetString(), fanfareTicks,
                        this.g1PreviewLockMode, partialTick
                );
                return;
            }

            // 1st Place Purse EditBox Label
            graphics.drawString(this.font, "§a" + ThoroughbredData.tr("1st $:", "頭馬$:", "头马$:", "1着$:"), centerX - 110, topY + 65, 0xFFFFFF);

            // Volume displays next to volume +/-
            graphics.drawCenteredString(this.font, this.newRaceVolume + "%", centerX + 46, topY + 117, 0xFFFFFF);
            graphics.drawCenteredString(this.font, this.newRaceMusicVol + "%", centerX + 46, topY + 133, 0xFFFFFF);
            graphics.drawCenteredString(this.font, this.newRaceSpurtVol + "%", centerX + 46, topY + 149, 0xFFFFFF);

            // Compact Live Race Logo, Conditions & Purse Preview Box!
            graphics.fill(centerX - 110, topY + 162, centerX + 110, topY + 203, 0xFF141923);
            graphics.fill(centerX - 110, topY + 162, centerX + 110, topY + 163, 0xFFD4AF37);
            String seriesText = (this.newRaceSeriesTag != null && !this.newRaceSeriesTag.isBlank())
                    ? (" §6[" + RacingManager.getLocalizedSeriesTag(this.newRaceSeriesTag) + "]")
                    : "";
            String gradeBadge = (isG1 ? "§b§lG1 ✨ " : ("§d§l" + chosenGrade + " "))
                    + "§f§l" + raceTitle + seriesText;
            drawScaledCenteredString(graphics, this.font, gradeBadge, centerX, topY + 165, 214, 0xFFFFFF);

            String condLine = "§e" + bottomText + " §7| §b" + RacingManager.getAgeRequirementLabel(this.newRaceAgeReq)
                    + " §7| §d" + RacingManager.getGenderRequirementLabel(this.newRaceGenderReq);
            drawScaledCenteredString(graphics, this.font, condLine, centerX, topY + 177, 214, 0xFFE680);

            if (this.isPurseValid) {
                String pursePreview = String.format(Locale.ROOT, "§a" + ThoroughbredData.tr("Purse", "獎金", "奖金", "賞金") + ": $%,d §7(2nd: $%,d, 3rd: $%,d)",
                        this.newRacePurse, (long)(this.newRacePurse * 0.40), (long)(this.newRacePurse * 0.25));
                drawScaledCenteredString(graphics, this.font, pursePreview, centerX, topY + 190, 214, 0x55FF55);
            } else {
                drawScaledCenteredString(graphics, this.font, "§c" + ThoroughbredData.tr("Min $100 & multiple of 10 required!", "須滿 $100 且為 10 的倍數！", "须满 $100 且为 10 的倍数！", "最低$100かつ10の倍数が必要です！"), centerX, topY + 190, 214, 0xFF5555);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 6: Official Racing Schedule & 9-Variant Parimutuel Betting GUI (/tb schedule)
    // =========================================================================
    public static class RacingScheduleScreen extends Screen {
        private static final int SUBVIEW_MAIN = 0;
        private static final int SUBVIEW_BET_BUILDER = 1;
        private static final int SUBVIEW_REGISTER = 2;
        private static final int SUBVIEW_ADMIN_ADD = 3;
        private static final int SUBVIEW_MY_BETS = 4;

        private ModNetwork.OpenScheduleScreenPacket data;
        private int subView = SUBVIEW_MAIN;

        private int selectedScheduleIdx = 0;
        private int scheduleScrollOffset = 0;
        private int runnerScrollOffset = 0;

        // Subview 2: Register Owned Horse state
        private int selectedOwnedHorseIdx = 0;
        private int ownedHorseScrollOffset = 0;

        // Subview 3: Admin Add Race to Schedule state
        private int selectedConsoleRaceIdx = 0;
        private int consoleRaceScrollOffset = 0;
        private EditBox scheduleDateBox;
        private String savedDateText = "Saturday 8:00 PM";

        // Subview 1: 9-Variant Betting Ticket Builder state
        private int selectedBetTypeIdx = 0; // Index in StudbookSavedData.ALL_BET_TYPES
        private final List<String> ticketPicks = new ArrayList<>();
        private int betRunnerScrollOffset = 0;
        private EditBox betAmountBox;
        private String savedBetAmountText = "100";
        private Button submitBetBtnRef;

        // Subview 4: My Bets scroll state
        private int myBetsScrollOffset = 0;

        public RacingScheduleScreen(ModNetwork.OpenScheduleScreenPacket data) {
            super(Component.literal("Official Racing Schedule & Betting"));
            this.data = data;
        }

        public void updateData(ModNetwork.OpenScheduleScreenPacket newData) {
            this.data = newData;
            if (!this.data.schedules.isEmpty()) {
                this.selectedScheduleIdx = Mth.clamp(this.selectedScheduleIdx, 0, this.data.schedules.size() - 1);
            } else {
                this.selectedScheduleIdx = 0;
            }
            this.init();
        }

        private StudbookSavedData.ScheduledRace getSelectedSchedule() {
            if (data == null || data.schedules.isEmpty()) return null;
            selectedScheduleIdx = Mth.clamp(selectedScheduleIdx, 0, data.schedules.size() - 1);
            return data.schedules.get(selectedScheduleIdx);
        }

        private String clipText(String text, int maxWidth) {
            return text != null ? text : "";
        }

        @Override
        protected void init() {
            this.clearWidgets();
            int centerX = this.width / 2;
            int topY = (this.height - 236) / 2;

            if (this.subView == SUBVIEW_BET_BUILDER) {
                initBetBuilderView(centerX, topY);
                return;
            }
            if (this.subView == SUBVIEW_REGISTER) {
                initRegisterHorseView(centerX, topY);
                return;
            }
            if (this.subView == SUBVIEW_ADMIN_ADD) {
                initAdminAddView(centerX, topY);
                return;
            }
            if (this.subView == SUBVIEW_MY_BETS) {
                initMyBetsView(centerX, topY);
                return;
            }

            // =================================================================
            // MAIN SCHEDULE VIEW WIDGETS
            // =================================================================
            StudbookSavedData.ScheduledRace sel = getSelectedSchedule();

            // Top bar buttons
            Button myBetsBtn = AutoFitButton.create(Component.literal("§e📋 My Bets"), btn -> {
                this.myBetsScrollOffset = 0;
                this.subView = SUBVIEW_MY_BETS;
                this.init();
            }).bounds(centerX - 196, topY + 20, 74, 18).build();
            this.addRenderableWidget(myBetsBtn);

            if (data.isAdmin) {
                Button adminAddBtn = AutoFitButton.create(Component.literal("§a+ Schedule a Race"), btn -> {
                    this.selectedConsoleRaceIdx = 0;
                    this.consoleRaceScrollOffset = 0;
                    this.subView = SUBVIEW_ADMIN_ADD;
                    this.init();
                }).bounds(centerX + 86, topY + 20, 110, 18).build();
                this.addRenderableWidget(adminAddBtn);
            }

            if (sel != null) {
                // Scroll buttons for Registered Runners list (Right Column)
                int maxRunnerScroll = Math.max(0, sel.registeredHorses.size() - 5);
                this.runnerScrollOffset = Mth.clamp(this.runnerScrollOffset, 0, maxRunnerScroll);

                Button runUpBtn = AutoFitButton.create(Component.literal("▲"), btn -> {
                    this.runnerScrollOffset = Math.max(0, this.runnerScrollOffset - 1);
                    this.init();
                }).bounds(centerX + 180, topY + 102, 16, 16).build();
                runUpBtn.active = this.runnerScrollOffset > 0;

                Button runDownBtn = AutoFitButton.create(Component.literal("▼"), btn -> {
                    this.runnerScrollOffset = Math.min(maxRunnerScroll, this.runnerScrollOffset + 1);
                    this.init();
                }).bounds(centerX + 180, topY + 158, 16, 16).build();
                runDownBtn.active = this.runnerScrollOffset < maxRunnerScroll;

                this.addRenderableWidget(runUpBtn);
                this.addRenderableWidget(runDownBtn);

                // Admin per-row [✖] Kick/Reject horse buttons (when registration is open or closed before race start)
                if (data.isAdmin && !"RUNNING".equalsIgnoreCase(sel.status) && !"FINISHED".equalsIgnoreCase(sel.status)) {
                    for (int slot = 0; slot < 5; slot++) {
                        int rIdx = this.runnerScrollOffset + slot;
                        if (rIdx >= sel.registeredHorses.size()) break;
                        StudbookSavedData.ScheduledRace.RegistrationEntry reg = sel.registeredHorses.get(rIdx);
                        int rowY = topY + 102 + (slot * 14);
                        Button kickBtn = AutoFitButton.create(Component.literal("§c✖"), btn -> {
                            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                                    ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_KICK_HORSE,
                                    sel.scheduleId, reg.horseName, "", 0L
                            ));
                        }).bounds(centerX + 162, rowY, 16, 13).build();
                        this.addRenderableWidget(kickBtn);
                    }
                }

                // Player Action Row (Y = topY + 182)
                int pBtnY = topY + 182;
                Button regHorseBtn = AutoFitButton.create(Component.literal("§a🐎 Register Horse"), btn -> {
                    this.selectedOwnedHorseIdx = 0;
                    this.ownedHorseScrollOffset = 0;
                    this.subView = SUBVIEW_REGISTER;
                    this.init();
                }).bounds(centerX - 58, pBtnY, 96, 18).build();
                regHorseBtn.active = sel.registrationOpen;
                this.addRenderableWidget(regHorseBtn);

                boolean ownsRegisteredHorse = sel.registeredHorses.stream()
                        .anyMatch(r -> r.ownerName.equalsIgnoreCase(data.playerName));
                Button unregBtn = AutoFitButton.create(Component.literal("§eUnregister"), btn -> {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                            ModNetwork.ScheduleGuiActionPacket.ACTION_UNREGISTER_HORSE,
                            sel.scheduleId, "", "", 0L
                    ));
                }).bounds(centerX + 42, pBtnY, 64, 18).build();
                unregBtn.active = sel.registrationOpen && ownsRegisteredHorse;
                this.addRenderableWidget(unregBtn);

                boolean bettingOpen = sel.isBettingCurrentlyOpen();
                Button placeBetBtn = AutoFitButton.create(Component.literal(bettingOpen ? "§6§l🎫 Place Bet" : "§7🎫 Bets Closed"), btn -> {
                    this.selectedBetTypeIdx = 0;
                    this.ticketPicks.clear();
                    this.betRunnerScrollOffset = 0;
                    this.subView = SUBVIEW_BET_BUILDER;
                    this.init();
                }).bounds(centerX + 110, pBtnY, 86, 18).build();
                placeBetBtn.active = bettingOpen && sel.registeredHorses.size() >= 2;
                this.addRenderableWidget(placeBetBtn);

                // Admin Schedule Control Row (Y = topY + 206)
                if (data.isAdmin) {
                    boolean canToggleReg = !"RUNNING".equalsIgnoreCase(sel.status)
                            && !"FINISHED".equalsIgnoreCase(sel.status)
                            && !"CANCELLED".equalsIgnoreCase(sel.status);
                    String toggleLabel = sel.registrationOpen
                            ? "§e🔒 Close Reg & Open Bets"
                            : "§a🔓 Re-open Reg (Pause Bets)";
                    Button toggleRegBtn = AutoFitButton.create(Component.literal(toggleLabel), btn -> {
                        int act = sel.registrationOpen
                                ? ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_CLOSE_REG
                                : ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_OPEN_REG;
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                                act, sel.scheduleId, "", "", 0L
                        ));
                    }).bounds(centerX - 196, topY + 206, 148, 20).build();
                    toggleRegBtn.active = canToggleReg;
                    this.addRenderableWidget(toggleRegBtn);

                    Button cancelSchedBtn = AutoFitButton.create(Component.literal("§cCancel/Refund"), btn -> {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                                ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_CANCEL_SCHEDULE,
                                sel.scheduleId, "", "", 0L
                        ));
                    }).bounds(centerX - 44, topY + 206, 82, 20).build();
                    cancelSchedBtn.active = !"FINISHED".equalsIgnoreCase(sel.status) && !"CANCELLED".equalsIgnoreCase(sel.status);
                    this.addRenderableWidget(cancelSchedBtn);

                    Button delSchedBtn = AutoFitButton.create(Component.literal("§4Del"), btn -> {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                                ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_REMOVE_SCHEDULE,
                                sel.scheduleId, "", "", 0L
                        ));
                    }).bounds(centerX + 42, topY + 206, 36, 20).build();
                    this.addRenderableWidget(delSchedBtn);
                }
            }

            Button refreshBtn = AutoFitButton.create(Component.literal("§e↻ Refresh"), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                        ModNetwork.ScheduleGuiActionPacket.ACTION_REFRESH, "", "", "", 0L
                ));
            }).bounds(centerX + 82, topY + 206, 56, 20).build();
            this.addRenderableWidget(refreshBtn);

            Button closeBtn = AutoFitButton.create(Component.literal("Close"), btn -> this.onClose())
                    .bounds(centerX + 142, topY + 206, 54, 20).build();
            this.addRenderableWidget(closeBtn);
        }

        // =====================================================================
        // SUBVIEW 1: 9-VARIANT PARIMUTUEL BETTING TICKET BUILDER
        // =====================================================================
        private void initBetBuilderView(int centerX, int topY) {
            StudbookSavedData.ScheduledRace sel = getSelectedSchedule();
            if (sel == null) {
                this.subView = SUBVIEW_MAIN;
                this.init();
                return;
            }

            int numRunners = sel.registeredHorses.size();
            String[] betTypes = StudbookSavedData.ALL_BET_TYPES;

            // Ensure current selectedBetTypeIdx is valid for this field size; if not, reset to Win (0)
            if (numRunners < StudbookSavedData.getMinRunnersForBetType(betTypes[selectedBetTypeIdx])) {
                selectedBetTypeIdx = 0;
            }
            String activeBetType = betTypes[selectedBetTypeIdx];
            int reqPicks = StudbookSavedData.getRequiredPicksForBetType(activeBetType);
            while (this.ticketPicks.size() > reqPicks) {
                this.ticketPicks.remove(this.ticketPicks.size() - 1);
            }

            // Row 1 of Bet Types (5 buttons): Win, Place, Quinella, Exacta, Wide (width 74, gap 3 -> 382px total)
            int row1Y = topY + 29;
            for (int i = 0; i < 5; i++) {
                final int bIdx = i;
                String bName = betTypes[i];
                int minReq = StudbookSavedData.getMinRunnersForBetType(bName);
                boolean allowed = numRunners >= minReq;
                boolean isSel = (this.selectedBetTypeIdx == i);
                String label = isSel ? ("§e§l[" + bName + "]") : (allowed ? bName : ("§8" + bName + " (" + minReq + "+)"));
                Button bBtn = AutoFitButton.create(Component.literal(label), btn -> {
                    this.selectedBetTypeIdx = bIdx;
                    this.init();
                }).bounds(centerX - 191 + (i * 77), row1Y, 74, 15).build();
                bBtn.active = allowed;
                this.addRenderableWidget(bBtn);
            }

            // Row 2 of Bet Types (4 buttons): Trio, Trifecta, First 4, Quartet (width 93, gap 3 -> 381px total)
            int row2Y = topY + 46;
            for (int i = 5; i < 9; i++) {
                final int bIdx = i;
                String bName = betTypes[i];
                int minReq = StudbookSavedData.getMinRunnersForBetType(bName);
                boolean allowed = numRunners >= minReq;
                boolean isSel = (this.selectedBetTypeIdx == i);
                String label = isSel ? ("§e§l[" + bName + "]") : (allowed ? bName : ("§8" + bName + " (" + minReq + "+)"));
                Button bBtn = AutoFitButton.create(Component.literal(label), btn -> {
                    this.selectedBetTypeIdx = bIdx;
                    this.init();
                }).bounds(centerX - 191 + ((i - 5) * 96), row2Y, 93, 15).build();
                bBtn.active = allowed;
                this.addRenderableWidget(bBtn);
            }

            // Left Column: Clickable Runner Buttons to Add/Remove from Ticket Picks!
            int maxScroll = Math.max(0, numRunners - 5);
            this.betRunnerScrollOffset = Mth.clamp(this.betRunnerScrollOffset, 0, maxScroll);

            int listX = centerX - 191;
            int listTopY = topY + 102;
            for (int slot = 0; slot < Math.min(5, numRunners); slot++) {
                int rIdx = this.betRunnerScrollOffset + slot;
                if (rIdx >= numRunners) break;
                StudbookSavedData.ScheduledRace.RegistrationEntry reg = sel.registeredHorses.get(rIdx);
                int pickOrder = this.ticketPicks.indexOf(reg.horseName);
                String prefix = (pickOrder >= 0) ? ("§a§l[#" + (pickOrder + 1) + "] ") : ("§7" + (rIdx + 1) + ". ");
                String btnTxt = clipText(prefix + (reg.qualified ? "§f" : "§c") + reg.horseName, 152);

                Button horsePickBtn = AutoFitButton.create(Component.literal(btnTxt), btn -> {
                    if (this.ticketPicks.contains(reg.horseName)) {
                        this.ticketPicks.remove(reg.horseName);
                    } else if (this.ticketPicks.size() < reqPicks) {
                        this.ticketPicks.add(reg.horseName);
                    }
                    this.init();
                }).bounds(listX, listTopY + (slot * 19), 160, 17).build();
                this.addRenderableWidget(horsePickBtn);
            }

            Button scrollUp = AutoFitButton.create(Component.literal("▲"), btn -> {
                this.betRunnerScrollOffset = Math.max(0, this.betRunnerScrollOffset - 1);
                this.init();
            }).bounds(listX + 162, listTopY, 16, 17).build();
            scrollUp.active = this.betRunnerScrollOffset > 0;

            Button scrollDown = AutoFitButton.create(Component.literal("▼"), btn -> {
                this.betRunnerScrollOffset = Math.min(maxScroll, this.betRunnerScrollOffset + 1);
                this.init();
            }).bounds(listX + 162, listTopY + 76, 16, 17).build();
            scrollDown.active = this.betRunnerScrollOffset < maxScroll;

            this.addRenderableWidget(scrollUp);
            this.addRenderableWidget(scrollDown);

            // Right Column: Clear Picks, Bet Amount EditBox, Quick Add Buttons & Submit Ticket
            Button clearPicksBtn = AutoFitButton.create(Component.literal("§eClear"), btn -> {
                this.ticketPicks.clear();
                this.init();
            }).bounds(centerX + 135, topY + 87, 56, 14).build();
            clearPicksBtn.active = !this.ticketPicks.isEmpty();
            this.addRenderableWidget(clearPicksBtn);

            if (this.betAmountBox != null) {
                this.savedBetAmountText = this.betAmountBox.getValue();
            }
            this.betAmountBox = new EditBox(this.font, centerX + 32, topY + 154, 68, 16, Component.literal("Bet Amount"));
            this.betAmountBox.setMaxLength(12);
            this.betAmountBox.setValue(this.savedBetAmountText);
            this.betAmountBox.setResponder(val -> {
                this.savedBetAmountText = val;
                if (this.submitBetBtnRef != null) {
                    long s = parseBetStake();
                    this.submitBetBtnRef.active = (this.ticketPicks.size() == reqPicks) && (s >= 10L) && (s <= data.playerBalance);
                }
            });
            this.addRenderableWidget(this.betAmountBox);

            // Quick Bet Amount Buttons: [$10 Min] [+$100] [+$500] [+$1K] (fits centerX - 4 to centerX + 191)
            Button set100 = AutoFitButton.create(Component.literal("$10 Min"), btn -> {
                this.savedBetAmountText = "10";
                this.betAmountBox.setValue("10");
            }).bounds(centerX - 4, topY + 173, 46, 16).build();

            Button add500 = AutoFitButton.create(Component.literal("+$100"), btn -> addBetStake(100L))
                    .bounds(centerX + 44, topY + 173, 47, 16).build();

            Button add1000 = AutoFitButton.create(Component.literal("+$500"), btn -> addBetStake(500L))
                    .bounds(centerX + 93, topY + 173, 47, 16).build();

            Button add5000 = AutoFitButton.create(Component.literal("+$1K"), btn -> addBetStake(1000L))
                    .bounds(centerX + 142, topY + 173, 49, 16).build();

            this.addRenderableWidget(set100);
            this.addRenderableWidget(add500);
            this.addRenderableWidget(add1000);
            this.addRenderableWidget(add5000);

            Button submitBetBtn = AutoFitButton.create(Component.literal("§a§l✔ Place Bet Ticket"), btn -> {
                long stake = parseBetStake();
                if (stake >= 10L && this.ticketPicks.size() == reqPicks) {
                    String joinedPicks = String.join("|", this.ticketPicks);
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                            ModNetwork.ScheduleGuiActionPacket.ACTION_PLACE_BET,
                            sel.scheduleId, activeBetType, joinedPicks, stake
                    ));
                    this.subView = SUBVIEW_MAIN;
                }
            }).bounds(centerX - 110, topY + 206, 130, 20).build();
            submitBetBtn.active = (this.ticketPicks.size() == reqPicks) && (parseBetStake() >= 10L) && (parseBetStake() <= data.playerBalance);
            this.submitBetBtnRef = submitBetBtn;
            this.addRenderableWidget(submitBetBtn);

            Button backBtn = AutoFitButton.create(Component.literal("Back"), btn -> {
                this.subView = SUBVIEW_MAIN;
                this.init();
            }).bounds(centerX + 30, topY + 206, 80, 20).build();
            this.addRenderableWidget(backBtn);
        }

        private long parseBetStake() {
            try {
                String clean = (this.betAmountBox != null ? this.betAmountBox.getValue() : this.savedBetAmountText)
                        .replaceAll("[^0-9]", "");
                return clean.isEmpty() ? 0L : Long.parseLong(clean);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }

        private void addBetStake(long delta) {
            long next = Math.max(10L, parseBetStake() + delta);
            if (data.playerBalance > 0L) {
                next = Math.min(next, data.playerBalance);
            }
            this.savedBetAmountText = String.valueOf(next);
            if (this.betAmountBox != null) {
                this.betAmountBox.setValue(this.savedBetAmountText);
            }
        }

        // =====================================================================
        // SUBVIEW 2: SELECT OWNED HORSE TO REGISTER
        // =====================================================================
        private void initRegisterHorseView(int centerX, int topY) {
            StudbookSavedData.ScheduledRace sel = getSelectedSchedule();
            if (sel == null) {
                this.subView = SUBVIEW_MAIN;
                this.init();
                return;
            }

            List<String> owned = data.ownedHorseNames;
            int maxScroll = Math.max(0, owned.size() - 5);
            this.ownedHorseScrollOffset = Mth.clamp(this.ownedHorseScrollOffset, 0, maxScroll);
            this.selectedOwnedHorseIdx = Mth.clamp(this.selectedOwnedHorseIdx, 0, Math.max(0, owned.size() - 1));

            for (int slot = 0; slot < Math.min(5, owned.size()); slot++) {
                final int hIdx = this.ownedHorseScrollOffset + slot;
                if (hIdx >= owned.size()) break;
                String hName = owned.get(hIdx);
                boolean isSel = (hIdx == this.selectedOwnedHorseIdx);
                boolean isRidden = hName.equalsIgnoreCase(data.riddenHorseName);
                String btnLabel = (isSel ? "§e§l▶ " : "§f") + hName + (isRidden ? " §a(Ridden)" : "");

                Button hBtn = AutoFitButton.create(Component.literal(btnLabel), btn -> {
                    this.selectedOwnedHorseIdx = hIdx;
                    this.init();
                }).bounds(centerX - 110, topY + 64 + (slot * 22), 200, 19).build();
                this.addRenderableWidget(hBtn);
            }

            Button confirmBtn = AutoFitButton.create(Component.literal("§a§l✔ Register Selected Horse"), btn -> {
                String chosenHorse = (!owned.isEmpty() && selectedOwnedHorseIdx >= 0 && selectedOwnedHorseIdx < owned.size())
                        ? owned.get(selectedOwnedHorseIdx)
                        : data.riddenHorseName;
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                        ModNetwork.ScheduleGuiActionPacket.ACTION_REGISTER_HORSE,
                        sel.scheduleId, chosenHorse, "", 0L
                ));
                this.subView = SUBVIEW_MAIN;
            }).bounds(centerX - 115, topY + 196, 145, 20).build();
            confirmBtn.active = !owned.isEmpty() || !data.riddenHorseName.isEmpty();
            this.addRenderableWidget(confirmBtn);

            Button backBtn = AutoFitButton.create(Component.literal("Back"), btn -> {
                this.subView = SUBVIEW_MAIN;
                this.init();
            }).bounds(centerX + 38, topY + 196, 75, 20).build();
            this.addRenderableWidget(backBtn);
        }

        // =====================================================================
        // SUBVIEW 3: ADMIN SCHEDULE A RACE FROM ANY RACING CONSOLE
        // =====================================================================
        private void initAdminAddView(int centerX, int topY) {
            List<ModNetwork.OpenScheduleScreenPacket.ConsoleRaceOption> cRaces = data.consoleRaces;
            int maxScroll = Math.max(0, cRaces.size() - 4);
            this.consoleRaceScrollOffset = Mth.clamp(this.consoleRaceScrollOffset, 0, maxScroll);
            this.selectedConsoleRaceIdx = Mth.clamp(this.selectedConsoleRaceIdx, 0, Math.max(0, cRaces.size() - 1));

            for (int slot = 0; slot < Math.min(4, cRaces.size()); slot++) {
                final int cIdx = this.consoleRaceScrollOffset + slot;
                if (cIdx >= cRaces.size()) break;
                ModNetwork.OpenScheduleScreenPacket.ConsoleRaceOption opt = cRaces.get(cIdx);
                boolean isSel = (cIdx == this.selectedConsoleRaceIdx);
                String label = clipText(String.format(Locale.ROOT, "%s[%s] §f%s §7(%s | $%,d)",
                        isSel ? "§e§l▶ " : "§7", opt.grade, opt.raceName, opt.trackSummary, opt.firstPlacePurse), 286);

                Button raceOptBtn = AutoFitButton.create(Component.literal(label), btn -> {
                    this.selectedConsoleRaceIdx = cIdx;
                    this.init();
                }).bounds(centerX - 160, topY + 46 + (slot * 22), 296, 19).build();
                this.addRenderableWidget(raceOptBtn);
            }

            Button upBtn = AutoFitButton.create(Component.literal("▲"), btn -> {
                this.consoleRaceScrollOffset = Math.max(0, this.consoleRaceScrollOffset - 1);
                this.init();
            }).bounds(centerX + 140, topY + 46, 18, 20).build();
            upBtn.active = this.consoleRaceScrollOffset > 0;

            Button downBtn = AutoFitButton.create(Component.literal("▼"), btn -> {
                this.consoleRaceScrollOffset = Math.min(maxScroll, this.consoleRaceScrollOffset + 1);
                this.init();
            }).bounds(centerX + 140, topY + 112, 18, 20).build();
            downBtn.active = this.consoleRaceScrollOffset < maxScroll;

            this.addRenderableWidget(upBtn);
            this.addRenderableWidget(downBtn);

            if (this.scheduleDateBox != null) {
                this.savedDateText = this.scheduleDateBox.getValue();
            }
            this.scheduleDateBox = new EditBox(this.font, centerX - 110, topY + 158, 220, 18, Component.literal("Date / Time"));
            this.scheduleDateBox.setMaxLength(48);
            this.scheduleDateBox.setValue(this.savedDateText);
            this.scheduleDateBox.setResponder(v -> this.savedDateText = v);
            this.addRenderableWidget(this.scheduleDateBox);

            Button createBtn = AutoFitButton.create(Component.literal("§a§l✔ Open Race Registration"), btn -> {
                if (!cRaces.isEmpty() && selectedConsoleRaceIdx >= 0 && selectedConsoleRaceIdx < cRaces.size()) {
                    ModNetwork.OpenScheduleScreenPacket.ConsoleRaceOption chosen = cRaces.get(selectedConsoleRaceIdx);
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ScheduleGuiActionPacket(
                            ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_ADD_SCHEDULE,
                            "", chosen.raceId, this.scheduleDateBox.getValue(), 0L
                    ));
                    this.subView = SUBVIEW_MAIN;
                }
            }).bounds(centerX - 125, topY + 196, 155, 20).build();
            createBtn.active = !cRaces.isEmpty();
            this.addRenderableWidget(createBtn);

            Button backBtn = AutoFitButton.create(Component.literal("Back"), btn -> {
                this.subView = SUBVIEW_MAIN;
                this.init();
            }).bounds(centerX + 40, topY + 196, 80, 20).build();
            this.addRenderableWidget(backBtn);
        }

        // =====================================================================
        // SUBVIEW 4: MY ACTIVE BET TICKETS
        // =====================================================================
        private void initMyBetsView(int centerX, int topY) {
            Button backBtn = AutoFitButton.create(Component.literal("Back to Schedule"), btn -> {
                this.subView = SUBVIEW_MAIN;
                this.init();
            }).bounds(centerX - 55, topY + 202, 110, 20).build();
            this.addRenderableWidget(backBtn);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.subView == SUBVIEW_MAIN && !data.schedules.isEmpty()) {
                int centerX = this.width / 2;
                int topY = (this.height - 236) / 2;
                int leftX = centerX - 196;
                int listY = topY + 56;
                for (int slot = 0; slot < Math.min(6, data.schedules.size()); slot++) {
                    int idx = this.scheduleScrollOffset + slot;
                    if (idx >= data.schedules.size()) break;
                    int iy = listY + (slot * 24);
                    if (mouseX >= leftX && mouseX <= leftX + 130 && mouseY >= iy && mouseY <= iy + 22) {
                        this.selectedScheduleIdx = idx;
                        this.runnerScrollOffset = 0;
                        this.init();
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int topY = (this.height - 236) / 2;

            graphics.fill(centerX - 204, topY, centerX + 204, topY + 234, 0xEE0D1117);
            graphics.fill(centerX - 204, topY, centerX + 204, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - 204, topY + 232, centerX + 204, topY + 234, 0xFFD4AF37);

            if (this.subView == SUBVIEW_BET_BUILDER) {
                renderBetBuilderModal(graphics, centerX, topY);
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }
            if (this.subView == SUBVIEW_REGISTER) {
                renderRegisterHorseModal(graphics, centerX, topY);
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }
            if (this.subView == SUBVIEW_ADMIN_ADD) {
                renderAdminAddModal(graphics, centerX, topY);
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }
            if (this.subView == SUBVIEW_MY_BETS) {
                renderMyBetsModal(graphics, centerX, topY);
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }

            graphics.drawCenteredString(this.font, "§6§l=== Official Racing Schedule & Parimutuel Betting ===", centerX, topY + 6, 0xFFFFFF);

            // Balance Header
            String balStr = String.format(Locale.ROOT, "§6Bank Balance: §a$%,d", data.playerBalance);
            graphics.drawString(this.font, balStr, centerX - 116, topY + 25, 0xFFFFFF);

            graphics.fill(centerX - 198, topY + 42, centerX + 198, topY + 43, 0x55D4AF37);

            if (data.schedules.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7No races are currently scheduled on the board.", centerX, topY + 102, 0xAAAAAA);
                if (data.isAdmin) {
                    graphics.drawCenteredString(this.font, "§eClick §a[+ Schedule a Race] §eabove to open race registrations!", centerX, topY + 118, 0xFFFFFF);
                } else {
                    graphics.drawCenteredString(this.font, "§7Check back once a race host opens race registrations.", centerX, topY + 118, 0x888888);
                }
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }

            // Left Column: Scheduled Races List
            int leftX = centerX - 196;
            graphics.drawString(this.font, "§6Scheduled Races (" + data.schedules.size() + ")", leftX, topY + 46, 0xFFFFFF);
            int listY = topY + 56;
            for (int slot = 0; slot < Math.min(6, data.schedules.size()); slot++) {
                int idx = this.scheduleScrollOffset + slot;
                if (idx >= data.schedules.size()) break;
                StudbookSavedData.ScheduledRace s = data.schedules.get(idx);
                int iy = listY + (slot * 24);
                boolean isSel = (idx == this.selectedScheduleIdx);

                graphics.fill(leftX, iy, leftX + 130, iy + 22, isSel ? 0xFF283B52 : 0xFF161C24);
                graphics.fill(leftX, iy, leftX + 2, iy + 22, isSel ? 0xFFD4AF37 : 0xFF445566);

                String line1 = "§e[" + s.grade + "] §f" + s.raceName;
                drawScaledLeftString(graphics, this.font, line1, leftX + 5, iy + 2, 122, 0xFFFFFF);

                String badge = switch (s.status.toUpperCase(Locale.ROOT)) {
                    case "OPEN" -> "§a● REG OPEN";
                    case "CLOSED" -> "§e§l● BETS OPEN";
                    case "ARMED", "RUNNING" -> "§c● LOCKED";
                    case "FINISHED" -> "§7● FINISHED";
                    case "CANCELLED" -> "§4● CANCELLED";
                    default -> "§7" + s.status;
                };
                String line2 = badge + " §7(" + s.registeredHorses.size() + "🐎)";
                graphics.drawString(this.font, line2, leftX + 5, iy + 12, 0xCCCCCC);
            }

            // Right Column: Selected Scheduled Race Details & Runners (rightX = centerX - 58, max width 252px)
            StudbookSavedData.ScheduledRace sel = getSelectedSchedule();
            if (sel != null) {
                int rightX = centerX - 58;
                int maxRightW = 252;

                String titleLine = String.format(Locale.ROOT, "§e[%s] §6§l%s §7(%s)", sel.grade, sel.raceName, sel.scheduleId);
                drawScaledLeftString(graphics, this.font, titleLine, rightX, topY + 46, maxRightW, 0xFFFFFF);

                String courseLine = "§7Course: §f" + sel.trackSummary + " §7| Date: §b" + sel.scheduledDate;
                drawScaledLeftString(graphics, this.font, courseLine, rightX, topY + 57, maxRightW, 0xCCCCCC);

                String tagPrefix = (sel.raceSeriesTag != null && !sel.raceSeriesTag.isBlank())
                        ? ("§6★ " + RacingManager.getLocalizedSeriesTag(sel.raceSeriesTag) + " ★ ") : "";
                String ageReqTxt = "§e" + RacingManager.getAgeRequirementLabel(sel.ageRequirement)
                        + (sel.genderRequirement == 1 ? (" §d[" + RacingManager.getGenderRequirementLabel(sel.genderRequirement) + "]") : "");
                String purseLine = String.format(Locale.ROOT,
                        "§71st: §a$%,d §7| %s §7| %s",
                        sel.firstPlacePurse, ageReqTxt, tagPrefix.isEmpty() ? ("Pool: §a$" + String.format(Locale.ROOT, "%,d", sel.getTotalBetPool())) : tagPrefix);
                drawScaledLeftString(graphics, this.font, purseLine, rightX, topY + 68, maxRightW, 0xCCCCCC);

                String phaseNotice = sel.registrationOpen
                        ? "§a[Registration OPEN] §7Bets open when Reg closes."
                        : (sel.isBettingCurrentlyOpen()
                                ? "§e§l[Reg CLOSED — BETTING IS NOW OPEN!]"
                                : "§c[Race Locked / Concluded]");
                drawScaledLeftString(graphics, this.font, phaseNotice, rightX, topY + 79, maxRightW, 0xFFFFFF);

                graphics.drawString(this.font, "§6Registered Horses (" + sel.registeredHorses.size() + ") — Unqualified in §cRED§6:", rightX, topY + 91, 0xFFFFFF);

                int rBoxTop = topY + 101;
                graphics.fill(rightX, rBoxTop, centerX + 178, rBoxTop + 74, 0xAA121720);
                if (sel.registeredHorses.isEmpty()) {
                    graphics.drawCenteredString(this.font, "§7No horses registered yet.", rightX + 115, rBoxTop + 32, 0x888888);
                } else {
                    for (int slot = 0; slot < 5; slot++) {
                        int rIdx = this.runnerScrollOffset + slot;
                        if (rIdx >= sel.registeredHorses.size()) break;
                        StudbookSavedData.ScheduledRace.RegistrationEntry reg = sel.registeredHorses.get(rIdx);
                        int ry = rBoxTop + 2 + (slot * 14);

                        String rowStr;
                        if (reg.qualified) {
                            rowStr = String.format(Locale.ROOT, "§f%d. §a%s §7(%s | $%,d) §eWin: %s",
                                    rIdx + 1, reg.horseName, reg.ownerName, reg.careerEarnings, sel.getFormattedOdds(reg.horseName));
                        } else {
                            rowStr = String.format(Locale.ROOT, "§f%d. §c§l%s §7(%s | §c$%,d§7) §c[UNQUALIFIED]",
                                    rIdx + 1, reg.horseName, reg.ownerName, reg.careerEarnings);
                        }
                        int maxW = data.isAdmin ? 214 : 230;
                        drawScaledLeftString(graphics, this.font, rowStr, rightX + 4, ry + 2, maxW, 0xFFFFFF);
                    }
                }
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void renderBetBuilderModal(GuiGraphics graphics, int centerX, int topY) {
            StudbookSavedData.ScheduledRace sel = getSelectedSchedule();
            if (sel == null) return;

            String activeBetType = StudbookSavedData.ALL_BET_TYPES[selectedBetTypeIdx];
            int reqPicks = StudbookSavedData.getRequiredPicksForBetType(activeBetType);
            boolean ordered = StudbookSavedData.isBetTypeOrderSpecific(activeBetType);
            int numRunners = sel.registeredHorses.size();
            double mult = StudbookSavedData.calculateFieldSizeMultiplier(activeBetType, numRunners);

            graphics.drawCenteredString(this.font, "§6§l=== Parimutuel Betting Ticket Builder ===", centerX, topY + 6, 0xFFFFFF);
            String subHeader = String.format(Locale.ROOT,
                    "§e%s §7(%d Approved Horses)  |  §6Balance: §a$%,d",
                    sel.raceName, numRunners, data.playerBalance);
            drawScaledCenteredString(graphics, this.font, subHeader, centerX, topY + 17, 382, 0xFFFFFF);

            // Description & Dynamic Field-Size Multiplier Banner (topY + 63 to topY + 85, well clear of topY + 90 headers)
            graphics.fill(centerX - 191, topY + 63, centerX + 191, topY + 85, 0xFF161D28);
            String descLine = "§b" + activeBetType + ": §f" + StudbookSavedData.getBetTypeDescription(activeBetType);
            drawScaledLeftString(graphics, this.font, descLine, centerX - 187, topY + 65, 374, 0xFFFFFF);

            String multLine = String.format(Locale.ROOT,
                    "§7Field-Size Multiplier (%d Horses): §a§l%.1fx §7| %s",
                    numRunners, mult, ordered ? "§eExact Finish Order" : "§aAny Finish Order");
            drawScaledLeftString(graphics, this.font, multLine, centerX - 187, topY + 75, 374, 0xCCCCCC);

            // Column Headers at topY + 90 (15px below topY + 75 -> zero overlap!)
            graphics.drawString(this.font, "§61. Click Horses to Pick:", centerX - 191, topY + 90, 0xFFFFFF);

            int rightX = centerX - 4;
            graphics.drawString(this.font, ordered ? "§62. Exact Order:" : "§62. Ticket Picks:", rightX, topY + 90, 0xFFFFFF);

            // Right Column: Selected Ticket Picks Box (centerX - 4 to centerX + 191, width 195px)
            graphics.fill(rightX, topY + 102, centerX + 191, topY + 150, 0xFF141A24);
            for (int i = 0; i < reqPicks; i++) {
                String slotLabel = ordered ? ("#" + (i + 1) + ": ") : ("Pick " + (i + 1) + ": ");
                String pickedHorse = (i < this.ticketPicks.size()) ? ("§a§l" + this.ticketPicks.get(i)) : "§8[Click horse on left]";
                String pickLine = "§e" + slotLabel + pickedHorse;
                drawScaledLeftString(graphics, this.font, pickLine, rightX + 5, topY + 105 + (i * 11), 185, 0xFFFFFF);
            }

            graphics.drawString(this.font, "§eStake:", rightX, topY + 158, 0xFFFFFF);
            long stake = parseBetStake();
            long estWin = Math.round(stake * mult);
            String estStr = String.format(Locale.ROOT, "§7Est: §a$%,d+", estWin);
            drawScaledLeftString(graphics, this.font, estStr, centerX + 104, topY + 158, 85, 0x55FF55);
        }

        private void renderRegisterHorseModal(GuiGraphics graphics, int centerX, int topY) {
            StudbookSavedData.ScheduledRace sel = getSelectedSchedule();
            if (sel == null) return;

            drawScaledCenteredString(graphics, this.font, "§a§lRegister Horse — " + sel.raceName + " [" + sel.grade + "]", centerX, topY + 16, 382, 0xFFFFFF);
            String reqStr = sel.requiredEarnings <= 0L ? "None ($0)" : String.format(Locale.ROOT, "$%,d (Unqualified show in RED)", sel.requiredEarnings);
            drawScaledCenteredString(graphics, this.font, "§7Required Career Earnings: §e" + reqStr, centerX, topY + 32, 382, 0xCCCCCC);
            String ageReq = "§eAge: " + RacingManager.getAgeRequirementLabel(sel.ageRequirement)
                    + (sel.genderRequirement == 1 ? (" §7| §d" + RacingManager.getGenderRequirementLabel(sel.genderRequirement)) : "")
                    + ((sel.raceSeriesTag != null && !sel.raceSeriesTag.isBlank()) ? (" §7| §6★ " + RacingManager.getLocalizedSeriesTag(sel.raceSeriesTag) + " ★") : "");
            drawScaledCenteredString(graphics, this.font, ageReq, centerX, topY + 44, 382, 0xFFFFFF);
            if (StudbookSavedData.isDebutOrMaidenGrade(sel.grade)) {
                graphics.drawCenteredString(this.font, "§eNote: Previous race winners cannot enter Debut/Maiden races.", centerX, topY + 46, 0xFFAA00);
            }

            if (data.ownedHorseNames.isEmpty() && data.riddenHorseName.isEmpty()) {
                graphics.drawCenteredString(this.font, "§cYou do not own any claimed Thoroughbreds yet!", centerX, topY + 110, 0xFF5555);
                graphics.drawCenteredString(this.font, "§7Ride your Thoroughbred and run §e/tb claim §7first.", centerX, topY + 124, 0xAAAAAA);
            }
        }

        private void renderAdminAddModal(GuiGraphics graphics, int centerX, int topY) {
            graphics.drawCenteredString(this.font, "§a§lSchedule a Console Race for Registration", centerX, topY + 14, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "§71. Select a Race from your Racing Consoles below:", centerX, topY + 30, 0xCCCCCC);

            if (data.consoleRaces.isEmpty()) {
                graphics.drawCenteredString(this.font, "§cNo races found! Create a race on a Racing Console first.", centerX, topY + 84, 0xFF5555);
            }

            graphics.drawCenteredString(this.font, "§72. Scheduled Race Date / Time (Bets open when Reg closes):", centerX, topY + 144, 0xCCCCCC);
        }

        private void renderMyBetsModal(GuiGraphics graphics, int centerX, int topY) {
            graphics.drawCenteredString(this.font, "§6§l=== My Active Parimutuel Betting Tickets ===", centerX, topY + 14, 0xFFFFFF);

            List<String> lines = new ArrayList<>();
            for (StudbookSavedData.ScheduledRace s : data.schedules) {
                int numRunners = Math.max(2, s.registeredHorses.size());
                for (StudbookSavedData.ScheduledRace.BetEntry b : s.bets) {
                    if (b.bettorName.equalsIgnoreCase(data.playerName)) {
                        double mult = StudbookSavedData.calculateFieldSizeMultiplier(b.betType, numRunners);
                        lines.add(String.format(Locale.ROOT,
                                "§e[%s] %s §7| §b%s: §6%s §7| Stake: §a$%,d §7(Est: §a$%,d+ §7@ %.1fx) [%s]",
                                s.scheduleId, s.raceName, b.betType, b.getFormattedPicks(), b.amount, Math.round(b.amount * mult), mult, s.status));
                    }
                }
            }

            if (lines.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7You have not placed any betting tickets yet.", centerX, topY + 105, 0xAAAAAA);
            } else {
                int startY = topY + 38;
                for (int i = 0; i < Math.min(9, lines.size()); i++) {
                    drawScaledLeftString(graphics, this.font, lines.get(i), centerX - 191, startY + (i * 16), 382, 0xFFFFFF);
                }
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 7: 4-Generation Pedigree Tree GUI (30 Ancestors, HP & Inbreeding)
    // =========================================================================
    public static class PedigreeTreeScreen extends Screen {
        private final ThoroughbredData data;
        private final Screen parentScreen;
        private int viewTab = 0; // 0 = Full 4-Gen Bracket, 1 = Sire Line (Gen 1-4), 2 = Dam Line (Gen 1-4)

        private Button tabFullBtn;
        private Button tabSireBtn;
        private Button tabDamBtn;

        private String hoveredTooltipTitle = null;
        private String hoveredTooltipSub = null;

        public PedigreeTreeScreen(ThoroughbredData data, Screen parentScreen) {
            super(Component.literal(ThoroughbredData.tr("4-Generation Pedigree Tree", "四代血統譜", "四代血统谱", "四代血統表")));
            this.data = data;
            this.parentScreen = parentScreen;
        }

        private int getPanelHalfW() {
            return Math.min((this.width - 10) / 2, 224); // Up to 448px wide!
        }

        private int getPanelHeight() {
            return Math.min(this.height - 8, 254); // Up to 254px tall!
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int halfW = getPanelHalfW();
            int panelH = getPanelHeight();
            int topY = (this.height - panelH) / 2;
            int btnY = topY + panelH - 23;

            int leftEdge = centerX - halfW + 8;
            int totalSpan = (halfW * 2) - 16;
            int w1 = (int) (totalSpan * 0.28);
            int w2 = (int) (totalSpan * 0.25);
            int w3 = (int) (totalSpan * 0.25);
            int w4 = totalSpan - w1 - w2 - w3 - 12;

            this.tabFullBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.viewTab = 0;
                updateTabButtons();
            }).bounds(leftEdge, btnY, w1, 19).build();

            this.tabSireBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.viewTab = 1;
                updateTabButtons();
            }).bounds(leftEdge + w1 + 4, btnY, w2, 19).build();

            this.tabDamBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.viewTab = 2;
                updateTabButtons();
            }).bounds(leftEdge + w1 + w2 + 8, btnY, w3, 19).build();

            String closeLabel = (this.parentScreen != null) ? "◄ Back" : "Close";
            Button closeBtn = AutoFitButton.create(Component.literal(closeLabel), btn -> {
                if (this.parentScreen != null && this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                } else {
                    this.onClose();
                }
            }).bounds(leftEdge + w1 + w2 + w3 + 12, btnY, w4, 19).build();

            this.addRenderableWidget(this.tabFullBtn);
            this.addRenderableWidget(this.tabSireBtn);
            this.addRenderableWidget(this.tabDamBtn);
            this.addRenderableWidget(closeBtn);

            updateTabButtons();
        }

        private void updateTabButtons() {
            if (this.tabFullBtn != null) {
                this.tabFullBtn.setMessage(Component.literal(this.viewTab == 0 ? "§e[All 4 Gens (30)]" : "All 4 Gens (30)"));
            }
            if (this.tabSireBtn != null) {
                this.tabSireBtn.setMessage(Component.literal(this.viewTab == 1 ? "§b[Sire Line Zoom]" : "Sire Line Zoom"));
            }
            if (this.tabDamBtn != null) {
                this.tabDamBtn.setMessage(Component.literal(this.viewTab == 2 ? "§d[Dam Line Zoom]" : "Dam Line Zoom"));
            }
        }

        private boolean isDuplicateAncestor(String name) {
            if (name == null || name.isEmpty() || name.startsWith("Unknown") || name.contains("Wild") || name.contains("Eclipse") || name.contains("Phalaris")) {
                return false;
            }
            int count = 0;
            for (int i = 0; i < ThoroughbredData.PEDIGREE_SIZE; i++) {
                if (name.equalsIgnoreCase(this.data.getPedigreeAncestor(i))) {
                    count++;
                }
            }
            return count >= 2;
        }

        /**
         * Draws a pedigree ancestor cell, wrapping long horse names onto multiple lines when cell height permits
         * (Gen 1, Gen 2, Gen 3 in Full View, and all 4 Generations in Sire/Dam Zoom View) or scaling smoothly
         * in compact Gen 4 cells so long horse names are never omitted!
         */
        private void drawPedigreeCell(GuiGraphics g, int x, int y, int w, int h, int slotIndex, boolean isSireSide, int mouseX, int mouseY) {
            String name = this.data.getPedigreeAncestor(slotIndex);
            int hp = this.data.getPedigreeAncestorHP(slotIndex);
            boolean dup = isDuplicateAncestor(name);
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

            int bgColor = dup ? (hovered ? 0xEE4E3E16 : 0xDD3A2E10)
                    : (isSireSide ? (hovered ? 0xEE1A3048 : 0xDD122234) : (hovered ? 0xEE3E1C34 : 0xDD2E1426));
            int borderColor = dup ? 0xFFFFAA00 : (isSireSide ? 0xFF4499FF : 0xFFFF66B2);

            g.fill(x, y, x + w, y + h, bgColor);
            g.fill(x, y, x + 2, y + h, borderColor);

            if (hovered) {
                int genNum = ThoroughbredData.getGenerationForPedigreeIndex(slotIndex);
                this.hoveredTooltipTitle = (dup ? "§6★ " : (isSireSide ? "§b" : "§d")) + name + " §7(" + ThoroughbredData.tr("Gen", "第", "第", "第") + " " + genNum + ThoroughbredData.tr(" ", "代", "代", "世代") + ")";
                this.hoveredTooltipSub = "§e" + ThoroughbredData.tr("Hereditary Potential", "遺傳潛能", "遗传潜能", "遺伝ポテンシャル") + ": §f" + hp + "% §7(100/100 HP)" + (dup ? " §6[" + ThoroughbredData.tr("Linebred Shared Ancestor", "同血線重疊祖先", "同血线重叠祖先", "ラインブリード共通祖先") + "]" : "");
            }

            String prefix = dup ? "§6★ " : (isSireSide ? "§b" : "§d");
            String hpTag = " §7[" + hp + "]";
            String singleLine = prefix + name + hpTag;
            int innerW = w - 6;

            // Case 1: Fits on 1 line at standard 1.0x scale
            if (this.font.width(singleLine) <= innerW && h >= 9) {
                int textY = y + Math.max(1, (h - 8) / 2);
                g.drawString(this.font, singleLine, x + 4, textY, 0xFFFFFF, false);
                return;
            }

            // Case 2: Tall cell (h >= 16, e.g. Gen 1, Gen 2, Gen 3, or Zoom View) -> Wrap long horse name onto new lines!
            if (h >= 16) {
                float scale = (h >= 22) ? 0.85F : 0.74F;
                int scaledInnerW = Math.round(innerW / scale);
                List<String> wrappedLines = wrapFormattedTextToWidth(this.font, prefix + name + hpTag, scaledInnerW);
                int maxLines = (h >= 28) ? 3 : 2;
                int lineCount = Math.min(maxLines, wrappedLines.size());
                float lineStepPx = 8.5F * scale;
                float totalTextH = lineCount * lineStepPx;
                float startY = y + Math.max(1.0F, (h - totalTextH) * 0.5F);

                g.pose().pushPose();
                g.pose().translate(x + 4.0F, startY, 0.0F);
                g.pose().scale(scale, scale, 1.0F);
                for (int i = 0; i < lineCount; i++) {
                    String ln = wrappedLines.get(i);
                    if (i == lineCount - 1 && wrappedLines.size() > lineCount) {
                        // Append remaining text on final line if needed
                        StringBuilder rem = new StringBuilder(ln);
                        for (int k = i + 1; k < wrappedLines.size(); k++) {
                            rem.append(" ").append(wrappedLines.get(k));
                        }
                        ln = rem.toString();
                    }
                    int lw = this.font.width(ln);
                    if (lw > scaledInnerW) {
                        // Scale down this line slightly if still wider
                        float subScale = Math.max(0.75F, (float) scaledInnerW / (float) lw);
                        g.pose().pushPose();
                        g.pose().translate(0.0F, i * 9.0F, 0.0F);
                        g.pose().scale(subScale, subScale, 1.0F);
                        g.drawString(this.font, ln, 0, 0, 0xFFFFFF, false);
                        g.pose().popPose();
                    } else {
                        g.drawString(this.font, ln, 0, Math.round(i * 9.0F), 0xFFFFFF, false);
                    }
                }
                g.pose().popPose();
                return;
            }

            // Case 3: Compact cell (Gen 4 in Full 30-Ancestor View, h == 10):
            // Scale down smoothly or wrap onto 2 micro-lines so the full name + [HP] fits inside the box without omitting characters!
            int rawW = this.font.width(singleLine);
            float fitScale = Mth.clamp((float) innerW / (float) Math.max(1, rawW), 0.54F, 0.90F);
            if (fitScale >= 0.62F) {
                float ty = y + Math.max(1.0F, (h - 8.0F * fitScale) * 0.5F);
                g.pose().pushPose();
                g.pose().translate(x + 3.5F, ty, 0.0F);
                g.pose().scale(fitScale, fitScale, 1.0F);
                g.drawString(this.font, singleLine, 0, 0, 0xFFFFFF, false);
                g.pose().popPose();
            } else {
                // Very long name in compact Gen 4 cell: wrap across 2 micro-lines at 0.55x scale!
                float microScale = 0.55F;
                int scaledW = Math.round(innerW / microScale);
                List<String> lines = wrapFormattedTextToWidth(this.font, singleLine, scaledW);
                g.pose().pushPose();
                g.pose().translate(x + 3.5F, y + 0.8F, 0.0F);
                g.pose().scale(microScale, microScale, 1.0F);
                g.drawString(this.font, lines.get(0), 0, 0, 0xFFFFFF, false);
                if (lines.size() > 1) {
                    String line2 = String.join(" ", lines.subList(1, lines.size()));
                    g.drawString(this.font, line2, 0, 8, 0xFFFFFF, false);
                }
                g.pose().popPose();
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            this.hoveredTooltipTitle = null;
            this.hoveredTooltipSub = null;

            int centerX = this.width / 2;
            int halfW = getPanelHalfW();
            int panelH = getPanelHeight();
            int topY = (this.height - panelH) / 2;
            int leftX = centerX - halfW;
            int rightX = centerX + halfW;
            int maxHeaderTextW = (halfW * 2) - 16;

            graphics.fill(leftX, topY, rightX, topY + panelH, 0xEE0D1118);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFD4AF37);
            graphics.fill(leftX, topY + panelH - 2, rightX, topY + panelH, 0xFFD4AF37);

            // Header (scaled if needed so zero text ever bleeds outside the gold border!)
            String coatTag = this.data.isPureWhiteCoat() ? " §f[Pure White]" : "";
            String titleStr = "§6§l=== 4-Generation Pedigree Tree: §f§l" + this.data.getName() + coatTag + " §6§l===";
            drawScaledCenteredString(graphics, this.font, titleStr, centerX, topY + 5, maxHeaderTextW, 0xFFFFFF);

            String summaryLine = "§eHereditary Potential: " + this.data.getFormattedHereditaryPoints()
                    + "  §7|  §eCross: §f" + this.data.getInbreedingStatus();
            drawScaledCenteredString(graphics, this.font, summaryLine, centerX, topY + 15, maxHeaderTextW, 0xFFFFFF);

            String traitLine = "§eTraits: " + this.data.getFormattedTraitsSummary();
            drawScaledCenteredString(graphics, this.font, traitLine, centerX, topY + 25, maxHeaderTextW, 0xFFFFFF);

            // Clean separator line between header and Generation column headers (prevents any overlap!)
            graphics.fill(leftX + 6, topY + 35, rightX - 6, topY + 36, 0x44D4AF37);

            int treeTop = topY + 48;
            int totalInnerW = (halfW * 2) - 12;
            int colGap = 4;
            int colW = (totalInnerW - (colGap * 3)) / 4;
            int col1X = leftX + 6;
            int col2X = col1X + colW + colGap;
            int col3X = col2X + colW + colGap;
            int col4X = col3X + colW + colGap;

            graphics.drawString(this.font, "§6Gen 1 (50% HP)", col1X + 2, topY + 38, 0xFFFFFF, false);
            graphics.drawString(this.font, "§6Gen 2 (25% HP)", col2X + 2, topY + 38, 0xFFFFFF, false);
            graphics.drawString(this.font, "§6Gen 3 (12.5%)", col3X + 2, topY + 38, 0xFFFFFF, false);
            graphics.drawString(this.font, "§6Gen 4 (6.25%)", col4X + 2, topY + 38, 0xFFFFFF, false);

            int availTreeH = (topY + panelH - 26) - treeTop; // ~180px

            if (this.viewTab == 0) {
                // Full 4-Generation 30-Ancestor Bracket
                int gen4Pitch = Math.max(10, availTreeH / 16);
                int gen4CellH = Math.max(9, gen4Pitch - 1);
                int gen3Pitch = gen4Pitch * 2;
                int gen3CellH = Math.max(17, gen3Pitch - 3);
                int gen2Pitch = gen4Pitch * 4;
                int gen2CellH = Math.max(24, gen2Pitch - 16);
                int gen1Pitch = gen4Pitch * 8;
                int gen1CellH = Math.max(30, 32);

                // Gen 1 (slots 0..1) — Tall 32px multi-line cells!
                int g1Y0 = treeTop + (gen1Pitch - gen1CellH) / 2;
                int g1Y1 = treeTop + gen1Pitch + (gen1Pitch - gen1CellH) / 2;
                drawPedigreeCell(graphics, col1X, g1Y0, colW, gen1CellH, 0, true, mouseX, mouseY);
                drawPedigreeCell(graphics, col1X, g1Y1, colW, gen1CellH, 1, false, mouseX, mouseY);

                // Gen 2 (slots 2..5) — Tall 24px multi-line cells!
                for (int i = 0; i < 4; i++) {
                    int slot = 2 + i;
                    int cellY = treeTop + (i * gen2Pitch) + (gen2Pitch - gen2CellH) / 2;
                    drawPedigreeCell(graphics, col2X, cellY, colW, gen2CellH, slot, (i % 2 == 0), mouseX, mouseY);
                }

                // Gen 3 (slots 6..13) — 18px 2-line cells!
                for (int i = 0; i < 8; i++) {
                    int slot = 6 + i;
                    int cellY = treeTop + (i * gen3Pitch) + (gen3Pitch - gen3CellH) / 2;
                    drawPedigreeCell(graphics, col3X, cellY, colW, gen3CellH, slot, (i % 2 == 0), mouseX, mouseY);
                }

                // Gen 4 (slots 14..29) — 16 rows
                for (int i = 0; i < 16; i++) {
                    int slot = 14 + i;
                    int cellY = treeTop + (i * gen4Pitch);
                    drawPedigreeCell(graphics, col4X, cellY, colW, gen4CellH, slot, (i % 2 == 0), mouseX, mouseY);
                }
            } else {
                // Zoomed Half-Tree (1 = Sire Side, 2 = Dam Side) -> 8 spacious multi-line rows in Gen 4!
                boolean sireBranch = (this.viewTab == 1);
                int gen1Slot = sireBranch ? 0 : 1;
                int gen2Start = sireBranch ? 2 : 4;
                int gen3Start = sireBranch ? 6 : 10;
                int gen4Start = sireBranch ? 14 : 22;

                int gen4Pitch = Math.max(20, availTreeH / 8);
                int gen4CellH = Math.max(18, gen4Pitch - 2);
                int gen3Pitch = gen4Pitch * 2;
                int gen3CellH = 26;
                int gen2Pitch = gen4Pitch * 4;
                int gen2CellH = 30;
                int gen1CellH = 36;

                drawPedigreeCell(graphics, col1X, treeTop + (availTreeH - gen1CellH) / 2, colW, gen1CellH, gen1Slot, sireBranch, mouseX, mouseY);

                for (int i = 0; i < 2; i++) {
                    int cellY = treeTop + (i * gen2Pitch) + (gen2Pitch - gen2CellH) / 2;
                    drawPedigreeCell(graphics, col2X, cellY, colW, gen2CellH, gen2Start + i, (i % 2 == 0), mouseX, mouseY);
                }
                for (int i = 0; i < 4; i++) {
                    int cellY = treeTop + (i * gen3Pitch) + (gen3Pitch - gen3CellH) / 2;
                    drawPedigreeCell(graphics, col3X, cellY, colW, gen3CellH, gen3Start + i, (i % 2 == 0), mouseX, mouseY);
                }
                for (int i = 0; i < 8; i++) {
                    int cellY = treeTop + (i * gen4Pitch) + (gen4Pitch - gen4CellH) / 2;
                    drawPedigreeCell(graphics, col4X, cellY, colW, gen4CellH, gen4Start + i, (i % 2 == 0), mouseX, mouseY);
                }
            }

            super.render(graphics, mouseX, mouseY, partialTick);

            if (this.hoveredTooltipTitle != null) {
                String t1 = this.hoveredTooltipTitle;
                String t2 = this.hoveredTooltipSub != null ? this.hoveredTooltipSub : "";
                int tw = Math.max(this.font.width(t1), this.font.width(t2)) + 10;
                int th = t2.isEmpty() ? 16 : 26;
                int tx = Math.min(mouseX + 8, this.width - tw - 4);
                int ty = Math.max(4, mouseY - th - 4);
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 400.0F);
                graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFFD4AF37);
                graphics.fill(tx, ty, tx + tw, ty + th, 0xF010141C);
                graphics.drawString(this.font, t1, tx + 5, ty + 4, 0xFFFFFF, false);
                if (!t2.isEmpty()) {
                    graphics.drawString(this.font, t2, tx + 5, ty + 14, 0xCCCCCC, false);
                }
                graphics.pose().popPose();
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 8: World Equine Time Flow Selector GUI (First-Join & /tb timeflow)
    // =========================================================================
    public static class WorldTimeFlowScreen extends Screen {
        private int selectedMode;
        private int customDays;
        private final boolean isFirstJoinPrompt;
        private final boolean alreadyConfigured;

        private Button mode1YearBtn;
        private Button mode4MonthBtn;
        private Button mode3MonthBtn;
        private Button modeCustomBtn;
        private EditBox customDaysBox;
        private Button applyBtn;

        public WorldTimeFlowScreen(int currentMode, int customDays, boolean isFirstJoinPrompt) {
            this(currentMode, customDays, isFirstJoinPrompt, false);
        }

        public WorldTimeFlowScreen(int currentMode, int customDays, boolean isFirstJoinPrompt, boolean alreadyConfigured) {
            super(Component.literal("World Equine Time Flow Configuration"));
            this.selectedMode = currentMode;
            this.customDays = Math.max(12, customDays);
            this.isFirstJoinPrompt = isFirstJoinPrompt;
            this.alreadyConfigured = alreadyConfigured;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int topY = (this.height - 234) / 2;

            this.mode1YearBtn = AutoFitButton.create(Component.empty(), btn -> selectMode(StudbookSavedData.TIMEFLOW_1_YEAR))
                    .bounds(centerX - 175, topY + 46, 350, 20).build();
            this.mode4MonthBtn = AutoFitButton.create(Component.empty(), btn -> selectMode(StudbookSavedData.TIMEFLOW_4_MONTH))
                    .bounds(centerX - 175, topY + 70, 350, 20).build();
            this.mode3MonthBtn = AutoFitButton.create(Component.empty(), btn -> selectMode(StudbookSavedData.TIMEFLOW_3_MONTH))
                    .bounds(centerX - 175, topY + 94, 350, 20).build();
            this.modeCustomBtn = AutoFitButton.create(Component.empty(), btn -> selectMode(StudbookSavedData.TIMEFLOW_CUSTOM))
                    .bounds(centerX - 175, topY + 118, 235, 20).build();

            this.customDaysBox = new EditBox(this.font, centerX + 66, topY + 118, 109, 20, Component.literal("Custom Days"));
            this.customDaysBox.setMaxLength(5);
            this.customDaysBox.setValue(String.valueOf(this.customDays));
            this.customDaysBox.setEditable(!this.alreadyConfigured);
            this.customDaysBox.active = !this.alreadyConfigured;
            this.customDaysBox.setResponder(val -> {
                if (this.alreadyConfigured) return;
                try {
                    int parsed = Integer.parseInt(val.trim());
                    if (parsed >= 12 && parsed <= 3650) {
                        this.customDays = parsed;
                    }
                } catch (NumberFormatException ignored) {
                }
            });

            String applyLabel = this.alreadyConfigured
                    ? "§7🔒 Equine Time Flow Permanently Locked"
                    : "§a§l✔ Save & Apply Equine Time Flow";
            this.applyBtn = AutoFitButton.create(Component.literal(applyLabel), btn -> {
                if (this.alreadyConfigured) return;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new TimeFlowConfirmScreen(this, this.selectedMode, this.customDays));
                }
            }).bounds(centerX - 175, topY + 204, 240, 20).build();
            this.applyBtn.active = !this.alreadyConfigured;

            Button closeBtn = AutoFitButton.create(Component.literal(this.alreadyConfigured ? "Close" : "Later / Close"), btn -> this.onClose())
                    .bounds(centerX + 75, topY + 204, 100, 20).build();

            this.addRenderableWidget(this.mode1YearBtn);
            this.addRenderableWidget(this.mode4MonthBtn);
            this.addRenderableWidget(this.mode3MonthBtn);
            this.addRenderableWidget(this.modeCustomBtn);
            this.addRenderableWidget(this.customDaysBox);
            this.addRenderableWidget(this.applyBtn);
            this.addRenderableWidget(closeBtn);

            updateModeButtons();
        }

        private void selectMode(int mode) {
            if (this.alreadyConfigured) return;
            this.selectedMode = mode;
            updateModeButtons();
        }

        private void updateModeButtons() {
            if (this.mode1YearBtn != null) {
                boolean sel = (this.selectedMode == StudbookSavedData.TIMEFLOW_1_YEAR);
                this.mode1YearBtn.active = !this.alreadyConfigured;
                String prefix = sel ? (this.alreadyConfigured ? "§a§l[LOCKED ACTIVE] " : "§e§l[SELECTED] ") : (this.alreadyConfigured ? "§8" : "§7");
                String body = this.alreadyConfigured && !sel
                        ? "§81-Year Calendar (Default: 365/366 MC Days per Equine Year)"
                        : "§f1-Year Calendar (Default: 365/366 MC Days per Equine Year)";
                this.mode1YearBtn.setMessage(Component.literal(prefix + body));
            }
            if (this.mode4MonthBtn != null) {
                boolean sel = (this.selectedMode == StudbookSavedData.TIMEFLOW_4_MONTH);
                this.mode4MonthBtn.active = !this.alreadyConfigured;
                String prefix = sel ? (this.alreadyConfigured ? "§a§l[LOCKED ACTIVE] " : "§e§l[SELECTED] ") : (this.alreadyConfigured ? "§8" : "§7");
                String body = this.alreadyConfigured && !sel
                        ? "§84-Month Seasonal Cycle (~120-123 MC Days, Calendar-Varied)"
                        : "§b4-Month Seasonal Cycle (~120-123 MC Days, Calendar-Varied)";
                this.mode4MonthBtn.setMessage(Component.literal(prefix + body));
            }
            if (this.mode3MonthBtn != null) {
                boolean sel = (this.selectedMode == StudbookSavedData.TIMEFLOW_3_MONTH);
                this.mode3MonthBtn.active = !this.alreadyConfigured;
                String prefix = sel ? (this.alreadyConfigured ? "§a§l[LOCKED ACTIVE] " : "§e§l[SELECTED] ") : (this.alreadyConfigured ? "§8" : "§7");
                String body = this.alreadyConfigured && !sel
                        ? "§83-Month Seasonal Cycle (~90-92 MC Days, Calendar-Varied)"
                        : "§d3-Month Seasonal Cycle (~90-92 MC Days, Calendar-Varied)";
                this.mode3MonthBtn.setMessage(Component.literal(prefix + body));
            }
            if (this.modeCustomBtn != null) {
                boolean sel = (this.selectedMode == StudbookSavedData.TIMEFLOW_CUSTOM);
                this.modeCustomBtn.active = !this.alreadyConfigured;
                String prefix = sel ? (this.alreadyConfigured ? "§a§l[LOCKED ACTIVE] " : "§e§l[SELECTED] ") : (this.alreadyConfigured ? "§8" : "§7");
                String body = this.alreadyConfigured && !sel
                        ? "§8Custom Equine Year Length (Days):"
                        : "§6Custom Equine Year Length (Days):";
                this.modeCustomBtn.setMessage(Component.literal(prefix + body));
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int topY = (this.height - 234) / 2;

            graphics.fill(centerX - 190, topY, centerX + 190, topY + 234, 0xEE10141C);
            graphics.fill(centerX - 190, topY, centerX + 190, topY + 2, 0xFFD4AF37);
            graphics.fill(centerX - 190, topY + 232, centerX + 190, topY + 234, 0xFFD4AF37);

            String title = this.alreadyConfigured
                    ? "§6§l=== World Equine Time Flow (Permanently Locked) ==="
                    : (this.isFirstJoinPrompt
                        ? "§6§l=== Welcome! Select World Equine Time Flow ==="
                        : "§6§l=== World Equine Time Flow & Breeding Calendar ===");
            drawScaledCenteredString(graphics, this.font, title, centerX, topY + 8, 360, 0xFFFFFF);

            String sub = this.alreadyConfigured
                    ? "§c🔒 Locked for this world — create a new world to use a different Time Flow."
                    : "§e⚠ Once saved, Equine Time Flow is permanent for this world & unlocks /tb owner!";
            drawScaledCenteredString(graphics, this.font, sub, centerX, topY + 22, 360, 0xCCCCCC);

            // Live timing breakdown box
            graphics.fill(centerX - 175, topY + 144, centerX + 175, topY + 196, 0xFF161D28);
            int previewDays = switch (this.selectedMode) {
                case StudbookSavedData.TIMEFLOW_4_MONTH -> 122;
                case StudbookSavedData.TIMEFLOW_3_MONTH -> 91;
                case StudbookSavedData.TIMEFLOW_CUSTOM -> Math.max(12, this.customDays);
                default -> 365;
            };
            int gestMin = Math.max(1, (int) Math.round(previewDays * (320.0 / 365.0)));
            int gestMax = Math.max(gestMin, (int) Math.round(previewDays * (355.0 / 365.0)));

            drawScaledLeftString(graphics, this.font,
                    "§e• Mare 'In Heat' Window: §dEquine Months 4–10 (April–October)",
                    centerX - 168, topY + 148, 336, 0xFFFFFF);
            drawScaledLeftString(graphics, this.font,
                    String.format(Locale.ROOT, "§e• Mare Pregnancy (Gestation): §f~%d to %d Days §7(Real-life variation)", gestMin, gestMax),
                    centerX - 168, topY + 159, 336, 0xFFFFFF);
            drawScaledLeftString(graphics, this.font,
                    String.format(Locale.ROOT, "§e• Foal -> Yearling (1.0 Equine Yr): §a~%d Days §7(Unbroken 1yo)", previewDays),
                    centerX - 168, topY + 170, 336, 0xFFFFFF);
            drawScaledLeftString(graphics, this.font,
                    String.format(Locale.ROOT, "§e• Yearling -> 2yo Racehorse (2.0 Equine Yrs): §a~%d Days Total", previewDays * 2),
                    centerX - 168, topY + 181, 336, 0xFFFFFF);

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    public static class TimeFlowConfirmScreen extends Screen {
        private static final String WARNING_TEXT =
                "Once you confirm this World Equine Time Flow, you CANNOT change it anymore in this world! The only way to switch to a different Equine Time Flow later is to create a new world. Are you sure you want to permanently lock in this choice?";

        private final Screen parentScreen;
        private final int chosenMode;
        private final int chosenCustomDays;

        public TimeFlowConfirmScreen(Screen parentScreen, int chosenMode, int chosenCustomDays) {
            super(Component.literal("Confirm Permanent World Equine Time Flow"));
            this.parentScreen = parentScreen;
            this.chosenMode = chosenMode;
            this.chosenCustomDays = chosenCustomDays;
        }

        private String getModeSummary() {
            return switch (this.chosenMode) {
                case StudbookSavedData.TIMEFLOW_4_MONTH -> "4-Month Seasonal Cycle (~120–123 Days)";
                case StudbookSavedData.TIMEFLOW_3_MONTH -> "3-Month Seasonal Cycle (~90–92 Days)";
                case StudbookSavedData.TIMEFLOW_CUSTOM -> "Custom Cycle (" + this.chosenCustomDays + " Days per Equine Year)";
                default -> "1-Year Calendar (365/366 Days per Equine Year)";
            };
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int boxH = 160;
            int topY = (this.height - boxH) / 2;
            int btnY = topY + boxH - 28;

            this.addRenderableWidget(AutoFitButton.create(Component.literal("§a§l✔ Confirm & Lock Permanently"), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.SetTimeFlowPacket(this.chosenMode, this.chosenCustomDays));
                this.onClose();
            }).bounds(centerX - 155, btnY, 185, 20).build());

            this.addRenderableWidget(AutoFitButton.create(Component.literal("◀ Go Back"), btn -> {
                if (this.minecraft != null && this.parentScreen != null) {
                    this.minecraft.setScreen(this.parentScreen);
                } else {
                    this.onClose();
                }
            }).bounds(centerX + 38, btnY, 117, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int boxW = Math.min(this.width - 20, 350);
            int boxH = 160;
            int leftX = centerX - boxW / 2;
            int rightX = centerX + boxW / 2;
            int topY = (this.height - boxH) / 2;
            int bottomY = topY + boxH;

            graphics.fill(leftX, topY, rightX, bottomY, 0xEE121620);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFFFAA00);
            graphics.fill(leftX, bottomY - 2, rightX, bottomY, 0xFFFFAA00);
            graphics.fill(leftX, topY, leftX + 2, bottomY, 0xFFFFAA00);
            graphics.fill(rightX - 2, topY, rightX, bottomY, 0xFFFFAA00);

            drawScaledCenteredString(graphics, this.font,
                    "§e§l⚠ Permanent World Equine Time Flow Warning", centerX, topY + 10, boxW - 20, 0xFFFFFF);
            drawScaledCenteredString(graphics, this.font,
                    "§bSelected: §f§l" + getModeSummary(), centerX, topY + 24, boxW - 20, 0xFFFFFF);
            graphics.fill(leftX + 12, topY + 37, rightX - 12, topY + 38, 0x66FFAA00);

            List<String> lines = wrapFormattedTextToWidth(this.font, "§f" + WARNING_TEXT, boxW - 28);
            int textY = topY + 46;
            for (String line : lines) {
                graphics.drawCenteredString(this.font, line, centerX, textY, 0xFFE6E6);
                textY += 12;
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 8: Gelding Warning HUD (3-Second Safety Lock on "Proceed!" Button)
    // =========================================================================
    public static class GeldingConfirmationScreen extends Screen {
        private static final String GELDING_WARNING_TEXT =
                "If you geld your stallion, it will gain minor bonuses to its speed and power, make its temperament milder, at the cost of its breeding career! Do you proceed, even after you have learned of the consequences?";
        private static final long PROCEED_LOCK_MILLIS = 3000L; // 3 seconds

        private final int horseId;
        private final String horseName;
        private final long openedAtMillis;
        private Button proceedBtn;
        private Button noBtn;

        public GeldingConfirmationScreen(int horseId, String horseName) {
            super(Component.literal("Gelding Confirmation"));
            this.horseId = horseId;
            this.horseName = horseName != null ? horseName : "Stallion";
            this.openedAtMillis = Util.getMillis();
        }

        private int getBoxWidth() {
            return Math.min(this.width - 20, 340);
        }

        private int getBoxHeight() {
            return Math.min(this.height - 20, 152);
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int boxW = getBoxWidth();
            int boxH = getBoxHeight();
            int topY = (this.height - boxH) / 2;
            int btnY = topY + boxH - 28;

            this.proceedBtn = AutoFitButton.create(Component.literal("Proceed! (3s)"), btn -> {
                if (Util.getMillis() - this.openedAtMillis >= PROCEED_LOCK_MILLIS) {
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ConfirmGeldingPacket(this.horseId));
                    this.onClose();
                }
            }).bounds(centerX - 115, btnY, 108, 20).build();
            this.proceedBtn.active = false;

            this.noBtn = AutoFitButton.create(Component.literal("No!"), btn -> this.onClose())
                    .bounds(centerX + 7, btnY, 108, 20).build();

            this.addRenderableWidget(this.proceedBtn);
            this.addRenderableWidget(this.noBtn);
        }

        @Override
        public void tick() {
            super.tick();
            updateProceedButtonState();
        }

        private void updateProceedButtonState() {
            if (this.proceedBtn == null) return;
            long elapsed = Util.getMillis() - this.openedAtMillis;
            if (elapsed >= PROCEED_LOCK_MILLIS) {
                this.proceedBtn.active = true;
                this.proceedBtn.setMessage(Component.literal("§c§lProceed!"));
            } else {
                this.proceedBtn.active = false;
                int secsLeft = (int) Math.ceil((PROCEED_LOCK_MILLIS - elapsed) / 1000.0);
                this.proceedBtn.setMessage(Component.literal("§7Proceed! (" + Math.max(1, secsLeft) + "s)"));
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            updateProceedButtonState();

            int centerX = this.width / 2;
            int boxW = getBoxWidth();
            int boxH = getBoxHeight();
            int leftX = centerX - (boxW / 2);
            int rightX = centerX + (boxW / 2);
            int topY = (this.height - boxH) / 2;
            int bottomY = topY + boxH;

            graphics.fill(leftX, topY, rightX, bottomY, 0xEE121620);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFFF5555);
            graphics.fill(leftX, bottomY - 2, rightX, bottomY, 0xFFFF5555);
            graphics.fill(leftX, topY, leftX + 2, bottomY, 0xFFFF5555);
            graphics.fill(rightX - 2, topY, rightX, bottomY, 0xFFFF5555);

            String title = "§c§l⚠ Gelding Warning: §6§l" + this.horseName;
            drawScaledCenteredString(graphics, this.font, title, centerX, topY + 10, boxW - 20, 0xFFFFFF);
            graphics.fill(leftX + 12, topY + 24, rightX - 12, topY + 25, 0x66FF5555);

            List<String> wrappedLines = wrapFormattedTextToWidth(this.font, "§f" + GELDING_WARNING_TEXT, boxW - 28);
            int textY = topY + 34;
            for (String line : wrappedLines) {
                graphics.drawCenteredString(this.font, line, centerX, textY, 0xFFE6E6);
                textY += 12;
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // JOCKEY SILK & CAP RENDERER + CUSTOM DISK PNG MASK SYSTEM
    // (Exports & reads editable PNG pattern masks in aiothoroughbred_textures/jockey_silks/)
    // =========================================================================
    private static final java.util.Map<Integer, boolean[][]> CUSTOM_BODY_MASKS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, boolean[][]> CUSTOM_SLEEVE_MASKS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, boolean[][]> CUSTOM_CAP_MASKS = new java.util.HashMap<>();
    private static boolean silkTemplatesInitialized = false;

    public static java.nio.file.Path getJockeySilkTexturesFolder() {
        java.nio.file.Path dir = RacingManager.getTexturesFolderPath().resolve("jockey_silks");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    public static void ensureJockeySilkTemplatesExported() {
        if (silkTemplatesInitialized) return;
        silkTemplatesInitialized = true;
        try {
            java.nio.file.Path dir = getJockeySilkTexturesFolder();
            java.nio.file.Path readme = dir.resolve("README_HOW_TO_EDIT_SILKS.txt");
            if (!java.nio.file.Files.exists(readme)) {
                String guide = "=== AIO Thoroughbred Mod — Editable Jockey Silk & Cap Textures ===\r\n\r\n"
                        + "You can freely edit any of the PNG pattern masks in this folder using Paint.NET, Aseprite, Photoshop, or Blockbench!\r\n"
                        + "- body_pattern_<id>.png   (8x12 grid: White pixels = Pattern Color, Black/Transparent = Body Base Color)\r\n"
                        + "- sleeve_pattern_<id>.png (4x10 grid: White pixels = Pattern Color, Black/Transparent = Sleeve Base Color)\r\n"
                        + "- cap_pattern_<id>.png    (8x8 grid:  White pixels = Pattern Color, Black/Transparent = Cap Base Color)\r\n\r\n"
                        + "After saving any PNG in this folder, click [Reload PNGs] inside the Jockey Silk Creation Studio (/tb owner)\r\n"
                        + "to immediately apply your custom pixel art to both the 2D GUI and the 3D Jockey Armor & Cap in-game!\r\n";
                java.nio.file.Files.writeString(readme, guide);
            }
            for (int i = 1; i < StudbookSavedData.JockeySilkConfig.BODY_PATTERN_NAMES.length; i++) {
                java.io.File f = dir.resolve("body_pattern_" + i + ".png").toFile();
                if (!f.exists()) {
                    writeDefaultMaskPng(f, 8, 12, i, 0);
                }
            }
            for (int i = 1; i < StudbookSavedData.JockeySilkConfig.SLEEVE_PATTERN_NAMES.length; i++) {
                java.io.File f = dir.resolve("sleeve_pattern_" + i + ".png").toFile();
                if (!f.exists()) {
                    writeDefaultMaskPng(f, 4, 10, i, 1);
                }
            }
            for (int i = 1; i < StudbookSavedData.JockeySilkConfig.CAP_PATTERN_NAMES.length; i++) {
                java.io.File f = dir.resolve("cap_pattern_" + i + ".png").toFile();
                if (!f.exists()) {
                    writeDefaultMaskPng(f, 8, 8, i, 2);
                }
            }
            reloadCustomJockeySilkMasks();
        } catch (Exception ignored) {
        }
    }

    private static void writeDefaultMaskPng(java.io.File file, int w, int h, int patternId, int type) {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean on = switch (type) {
                        case 0 -> evalBuiltinBodyPatternPixel(patternId, x, y, w, h);
                        case 1 -> evalBuiltinSleevePatternPixel(patternId, x, y, w, h);
                        default -> evalBuiltinCapPatternPixel(patternId, x, y, w, h);
                    };
                    img.setRGB(x, y, on ? 0xFFFFFFFF : 0xFF101014);
                }
            }
            javax.imageio.ImageIO.write(img, "PNG", file);
        } catch (Exception ignored) {
        }
    }

    public static void reloadCustomJockeySilkMasks() {
        CUSTOM_BODY_MASKS.clear();
        CUSTOM_SLEEVE_MASKS.clear();
        CUSTOM_CAP_MASKS.clear();
        try {
            java.nio.file.Path dir = getJockeySilkTexturesFolder();
            for (int i = 1; i < StudbookSavedData.JockeySilkConfig.BODY_PATTERN_NAMES.length; i++) {
                boolean[][] mask = loadMaskPng(dir.resolve("body_pattern_" + i + ".png").toFile());
                if (mask != null) CUSTOM_BODY_MASKS.put(i, mask);
            }
            for (int i = 1; i < StudbookSavedData.JockeySilkConfig.SLEEVE_PATTERN_NAMES.length; i++) {
                boolean[][] mask = loadMaskPng(dir.resolve("sleeve_pattern_" + i + ".png").toFile());
                if (mask != null) CUSTOM_SLEEVE_MASKS.put(i, mask);
            }
            for (int i = 1; i < StudbookSavedData.JockeySilkConfig.CAP_PATTERN_NAMES.length; i++) {
                boolean[][] mask = loadMaskPng(dir.resolve("cap_pattern_" + i + ".png").toFile());
                if (mask != null) CUSTOM_CAP_MASKS.put(i, mask);
            }
        } catch (Exception ignored) {
        }
        ThoroughbredRunEngine.clearJockeySilkTextureCache();
    }

    private static boolean[][] loadMaskPng(java.io.File file) {
        if (file == null || !file.isFile()) return null;
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return null;
            int w = img.getWidth();
            int h = img.getHeight();
            boolean[][] out = new boolean[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    out[y][x] = (a >= 80) && ((r + g + b) / 3 >= 128);
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static void drawJockeySilkIcon(GuiGraphics graphics, int x, int y, int size, StudbookSavedData.JockeySilkConfig cfg) {
        ensureJockeySilkTemplatesExported();
        if (cfg == null) cfg = new StudbookSavedData.JockeySilkConfig();
        int grid = 16;
        int bodyBase = StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.bodyColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)];
        int bodyPatCol = StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.bodyPatternColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)];
        int slvBase = StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.sleeveColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)];
        int slvPatCol = StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.sleevePatternColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)];
        int capBase = StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.capColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)];
        int capPatCol = StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.capPatternColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)];

        // Subtle dark backing frame
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xAA080B10);

        for (int gy = 0; gy < grid; gy++) {
            int py0 = y + (gy * size) / grid;
            int py1 = y + ((gy + 1) * size) / grid;
            if (py1 <= py0) py1 = py0 + 1;

            for (int gx = 0; gx < grid; gx++) {
                int px0 = x + (gx * size) / grid;
                int px1 = x + ((gx + 1) * size) / grid;
                if (px1 <= px0) px1 = px0 + 1;

                // 1. Jockey Cap + Brim at top (gy: 0..3)
                boolean isCapDome = (gy == 0 && gx >= 6 && gx <= 9)
                        || ((gy == 1 || gy == 2) && gx >= 5 && gx <= 10);
                boolean isCapBrim = (gy == 3 && gx >= 5 && gx <= 11);
                if (isCapDome || isCapBrim) {
                    if (isCapBrim) {
                        int brimCol = (cfg.capPattern == 7) ? capPatCol : 0xFF1E222A;
                        graphics.fill(px0, py0, px1, py1, brimCol);
                    } else {
                        int cx = gx - 5; // 0..5
                        int cy = gy;     // 0..2
                        boolean cPat = evalCapPatternPixel(cfg.capPattern, cx, cy, 6, 3);
                        graphics.fill(px0, py0, px1, py1, cPat ? capPatCol : capBase);
                    }
                    continue;
                }

                // 2. Jockey Silk Jersey Body & Sleeves (gy: 4..15)
                boolean isBody = (gx >= 4 && gx <= 11 && gy >= 4 && gy <= 15)
                        && !(gy == 4 && (gx == 7 || gx == 8)); // V-neck collar cutout
                boolean isLeftSleeve = (gx >= 1 && gx <= 3 && gy >= 5 && gy <= 13);
                boolean isRightSleeve = (gx >= 12 && gx <= 14 && gy >= 5 && gy <= 13);

                if (!isBody && !isLeftSleeve && !isRightSleeve) {
                    continue;
                }

                boolean isCollarTrim = (gy == 4 && (gx == 6 || gx == 9)) || (gy == 5 && (gx == 7 || gx == 8));
                if (isCollarTrim) {
                    graphics.fill(px0, py0, px1, py1, 0xFFDDDDDD);
                    continue;
                }

                if (isBody) {
                    int bx = gx - 4; // 0..7
                    int by = gy - 4; // 0..11
                    boolean pat = evalJraBodyPatternPixel(cfg.bodyPattern, bx, by, 8, 12);
                    graphics.fill(px0, py0, px1, py1, pat ? bodyPatCol : bodyBase);
                } else {
                    int sx = isLeftSleeve ? (gx - 1) : (gx - 12); // 0..2
                    int sy = gy - 5; // 0..8
                    boolean pat = evalJraSleevePatternPixel(cfg.sleevePattern, sx, sy, 3, 9);
                    graphics.fill(px0, py0, px1, py1, pat ? slvPatCol : slvBase);
                }
            }
        }
    }

    public static boolean evalJraBodyPatternPixel(int patternId, int x, int y, int w, int h) {
        if (patternId <= 0) return false;
        boolean[][] custom = CUSTOM_BODY_MASKS.get(patternId);
        if (custom != null && custom.length > 0 && custom[0].length > 0) {
            int my = Mth.clamp((y * custom.length) / Math.max(1, h), 0, custom.length - 1);
            int mx = Mth.clamp((x * custom[0].length) / Math.max(1, w), 0, custom[0].length - 1);
            return custom[my][mx];
        }
        return evalBuiltinBodyPatternPixel(patternId, x, y, w, h);
    }

    public static boolean evalBuiltinBodyPatternPixel(int patternId, int x, int y, int w, int h) {
        if (patternId <= 0) return false;
        double nx = (x + 0.5) / Math.max(1.0, w);
        double ny = (y + 0.5) / Math.max(1.0, h);
        return switch (patternId) {
            case 1 -> Math.abs(ny - 0.50) <= 0.12; // 1 Horizontal Hoop
            case 2 -> Math.abs(ny - 0.34) <= 0.09 || Math.abs(ny - 0.66) <= 0.09; // 2 Horizontal Hoops
            case 3 -> Math.abs(ny - 0.24) <= 0.07 || Math.abs(ny - 0.50) <= 0.07 || Math.abs(ny - 0.76) <= 0.07; // 3 Horizontal Hoops
            case 4 -> (x % 2) == 0; // Vertical Stripes
            case 5 -> Math.abs(nx - ny) <= 0.16; // Diagonal Sash
            case 6 -> Math.abs(nx - ny) <= 0.15 || Math.abs((1.0 - nx) - ny) <= 0.15; // Crossed Sashes
            case 7 -> (Math.abs(nx - 0.50) + Math.abs(ny - 0.50)) <= 0.34; // Large Diamond
            case 8 -> Math.abs(ny - 0.50) <= 0.20 && ((x + y) % 2 == 0); // Diamond Band
            case 9 -> ((x / 2 + y / 2) % 2 == 0) && (y % 2 == 1); // Scales (Uroko)
            case 10 -> ((x / 2) + (y / 2)) % 2 == 0; // Checkered (Koushi)
            case 11 -> (x % 3 == 1) && (y % 3 == 1); // Polka Dots
            case 12 -> (Math.abs(nx - 0.50) <= 0.14 && Math.abs(ny - 0.50) <= 0.26)
                    || (Math.abs(nx - 0.50) <= 0.28 && Math.abs(ny - 0.50) <= 0.11); // Star Emblem
            case 13 -> { // Chevron (V-Band)
                double vTarget = 0.36 + Math.abs(nx - 0.50) * 0.52;
                yield Math.abs(ny - vTarget) <= 0.11;
            }
            case 14 -> { // Double Chevron
                double v1 = 0.26 + Math.abs(nx - 0.50) * 0.48;
                double v2 = 0.56 + Math.abs(nx - 0.50) * 0.48;
                yield Math.abs(ny - v1) <= 0.09 || Math.abs(ny - v2) <= 0.09;
            }
            case 15 -> ny <= 0.24 && (nx <= 0.34 || nx >= 0.66); // Epaulettes
            case 16 -> ny >= 0.50; // Halved Top/Bottom
            case 17 -> { // Sawtooth Band (ノコギリ歯 / Zigzag teeth across chest)
                int toothOffset = (x % 2 == 0) ? 0 : 1;
                int centerRow = h / 2;
                yield y >= (centerRow - 2 + toothOffset) && y <= (centerRow + toothOffset);
            }
            case 18 -> { // Double Sawtooth (二段ノコギリ歯)
                int toothOffset = (x % 2 == 0) ? 0 : 1;
                int r1 = Math.max(2, h / 3 - 1);
                int r2 = Math.min(h - 3, (h * 2) / 3);
                yield (y == r1 + toothOffset || y == r1 - 1 + toothOffset)
                        || (y == r2 + toothOffset || y == r2 - 1 + toothOffset);
            }
            case 19 -> nx >= 0.50; // Vertical Halves (Left/Right split)
            case 20 -> (nx < 0.50) ^ (ny < 0.50); // Quartered (4-quadrant split)
            case 21 -> ((x + y) % 4 == 0) || (Math.abs((x % 4) - 2) + Math.abs((y % 4) - 2) <= 1); // Argyle Diamonds
            case 22 -> Math.abs(nx - 0.50) <= 0.14 || Math.abs(ny - 0.50) <= 0.12; // Cross Emblem
            case 23 -> { // Lightning Sash
                double shift = (ny < 0.50) ? (ny + 0.12) : (ny - 0.12);
                yield Math.abs(nx - shift) <= 0.16;
            }
            case 24 -> Math.abs(nx - 0.50) <= 0.22 && ((y % 4 == 1) || (y % 4 == 2 && Math.abs(nx - 0.50) <= 0.12)); // Triple Diamond
            case 25 -> { // Heart Emblem
                double dx = Math.abs(nx - 0.50);
                yield (ny >= 0.28 && ny <= 0.45 && dx >= 0.06 && dx <= 0.30)
                        || (ny > 0.45 && ny <= 0.72 && dx <= (0.75 - ny) * 0.95);
            }
            case 26 -> { // Crown Emblem
                yield (ny >= 0.52 && ny <= 0.68 && nx >= 0.20 && nx <= 0.80)
                        || (ny >= 0.32 && ny < 0.52 && nx >= 0.20 && nx <= 0.80 && (x % 2 == 0));
            }
            case 27 -> (y % 2) == 0; // Thin Hoops
            default -> false;
        };
    }

    public static boolean evalJraSleevePatternPixel(int patternId, int x, int y, int w, int h) {
        if (patternId <= 0) return false;
        boolean[][] custom = CUSTOM_SLEEVE_MASKS.get(patternId);
        if (custom != null && custom.length > 0 && custom[0].length > 0) {
            int my = Mth.clamp((y * custom.length) / Math.max(1, h), 0, custom.length - 1);
            int mx = Mth.clamp((x * custom[0].length) / Math.max(1, w), 0, custom[0].length - 1);
            return custom[my][mx];
        }
        return evalBuiltinSleevePatternPixel(patternId, x, y, w, h);
    }

    public static boolean evalBuiltinSleevePatternPixel(int patternId, int x, int y, int w, int h) {
        if (patternId <= 0) return false;
        double ny = (y + 0.5) / Math.max(1.0, h);
        return switch (patternId) {
            case 1 -> Math.abs(ny - 0.50) <= 0.14; // 1 Armlet Band
            case 2 -> Math.abs(ny - 0.32) <= 0.10 || Math.abs(ny - 0.68) <= 0.10; // 2 Armlet Bands
            case 3 -> Math.abs(ny - 0.22) <= 0.08 || Math.abs(ny - 0.50) <= 0.08 || Math.abs(ny - 0.78) <= 0.08; // 3 Armlet Bands
            case 4 -> (x % 2) == 1; // Vertical Sleeve Stripe
            case 5 -> ((x + y) % 2) == 0 && ny >= 0.25 && ny <= 0.75; // Diamond Sleeves
            case 6 -> (x == w / 2) && (y % 3 == 1); // Polka Dot Sleeves
            case 7 -> (x == w / 2) && (y == h / 2 || y == h / 2 - 1); // Star Sleeves
            case 8 -> { // Sawtooth Sleeves
                int tooth = (x % 2 == 0) ? 0 : 1;
                int mid = h / 2;
                yield y == mid + tooth || y == mid - 1 + tooth;
            }
            case 9 -> ny >= 0.50; // Halved Sleeves
            case 10 -> ((x + y) % 2) == 0; // Checkered Sleeves
            case 11 -> { // Chevron Sleeves
                int vOff = (x == w / 2) ? 1 : 0;
                int mid = h / 2;
                yield y == mid + vOff || y == mid - 1 + vOff;
            }
            case 12 -> y <= 1 || y >= h - 2; // Cuff Trim
            case 13 -> (y % 2) == 0; // Thin Striped Sleeves
            default -> false;
        };
    }

    public static boolean evalCapPatternPixel(int patternId, int x, int y, int w, int h) {
        if (patternId <= 0) return false;
        boolean[][] custom = CUSTOM_CAP_MASKS.get(patternId);
        if (custom != null && custom.length > 0 && custom[0].length > 0) {
            int my = Mth.clamp((y * custom.length) / Math.max(1, h), 0, custom.length - 1);
            int mx = Mth.clamp((x * custom[0].length) / Math.max(1, w), 0, custom[0].length - 1);
            return custom[my][mx];
        }
        return evalBuiltinCapPatternPixel(patternId, x, y, w, h);
    }

    public static boolean evalBuiltinCapPatternPixel(int patternId, int x, int y, int w, int h) {
        if (patternId <= 0) return false;
        double nx = (x + 0.5) / Math.max(1.0, w);
        double ny = (y + 0.5) / Math.max(1.0, h);
        return switch (patternId) {
            case 1 -> (nx < 0.50) ^ (ny < 0.50); // Quartered Cap
            case 2 -> (y % 2) == 1;              // Hooped Cap
            case 3 -> Math.abs(nx - 0.50) <= 0.22 && Math.abs(ny - 0.50) <= 0.35; // Star Cap
            case 4 -> (Math.abs(nx - 0.50) + Math.abs(ny - 0.50)) <= 0.42; // Diamond Cap
            case 5 -> ((x + y) % 2 == 0) && ny >= 0.35; // Sawtooth Cap
            case 6 -> ((x + y) % 2) == 0;        // Checkered Cap
            case 7 -> ny >= 0.70;                // Contrast Visor
            default -> false;
        };
    }

    // =========================================================================
    // SCREEN 9: Horse Owner Registration & Farm Profile GUI (/tb owner)
    // =========================================================================
    public static class HorseOwnerScreen extends Screen {
        private ModNetwork.OpenOwnerScreenPacket pkt;
        private String farmNameInput;
        private String crown1Input;
        private boolean crown1Prefix;
        private String crown2Input;
        private boolean crown2Prefix;
        private StudbookSavedData.JockeySilkConfig workingSilk;
        private String statusMessage;

        private EditBox farmNameBox;
        private EditBox crown1Box;
        private Button crown1ModeBtn;
        private EditBox crown2Box;
        private Button crown2ModeBtn;
        private Button openSilkStudioBtn;
        private Button saveOrRegisterBtn;

        public HorseOwnerScreen(ModNetwork.OpenOwnerScreenPacket msg) {
            super(Component.literal("Horse Owner Registration"));
            this.pkt = msg;
            this.farmNameInput = msg.farmName;
            this.crown1Input = msg.crownName1;
            this.crown1Prefix = msg.crown1Prefix;
            this.crown2Input = msg.crownName2;
            this.crown2Prefix = msg.crown2Prefix;
            this.workingSilk = StudbookSavedData.JockeySilkConfig.fromCompactCode(msg.silkCode);
            this.statusMessage = msg.statusMessage;
        }

        public void updateFromPacket(ModNetwork.OpenOwnerScreenPacket msg) {
            this.pkt = msg;
            this.farmNameInput = msg.farmName;
            this.crown1Input = msg.crownName1;
            this.crown1Prefix = msg.crown1Prefix;
            this.crown2Input = msg.crownName2;
            this.crown2Prefix = msg.crown2Prefix;
            this.workingSilk = StudbookSavedData.JockeySilkConfig.fromCompactCode(msg.silkCode);
            this.statusMessage = msg.statusMessage;
            if (this.farmNameBox != null) this.farmNameBox.setValue(this.farmNameInput);
            if (this.crown1Box != null) this.crown1Box.setValue(this.crown1Input);
            if (this.crown2Box != null) this.crown2Box.setValue(this.crown2Input);
            updateButtonsAndFeePreview();
        }

        public void setWorkingSilk(StudbookSavedData.JockeySilkConfig updatedSilk) {
            if (updatedSilk != null) {
                this.workingSilk = updatedSilk.copy();
                updateButtonsAndFeePreview();
            }
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 16, 410);
            int panelH = Math.min(this.height - 12, 238);
            int leftX = centerX - panelW / 2;
            int topY = (this.height - panelH) / 2;

            int colLeft = leftX + 14;
            int formW = panelW - 155;

            // 1. Farm / Horse Owner Alias Box
            this.farmNameBox = new EditBox(this.font, colLeft, topY + 62, formW, 18, Component.literal("Farm Name"));
            this.farmNameBox.setMaxLength(24);
            this.farmNameBox.setValue(this.farmNameInput);
            this.farmNameBox.setResponder(val -> {
                this.farmNameInput = val;
                updateButtonsAndFeePreview();
            });
            this.addRenderableWidget(this.farmNameBox);

            // 2. Optional Crown Name Slot 1 + Prefix/Suffix Toggle
            int crownBoxW = formW - 68;
            this.crown1Box = new EditBox(this.font, colLeft, topY + 108, crownBoxW, 18, Component.literal("Crown Name 1"));
            this.crown1Box.setMaxLength(14);
            this.crown1Box.setValue(this.crown1Input);
            this.crown1Box.setResponder(val -> {
                this.crown1Input = val;
                updateButtonsAndFeePreview();
            });
            this.addRenderableWidget(this.crown1Box);

            this.crown1ModeBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.crown1Prefix = !this.crown1Prefix;
                updateButtonsAndFeePreview();
            }).bounds(colLeft + crownBoxW + 4, topY + 108, 64, 18).build();
            this.addRenderableWidget(this.crown1ModeBtn);

            // 3. Optional Crown Name Slot 2 + Prefix/Suffix Toggle
            this.crown2Box = new EditBox(this.font, colLeft, topY + 144, crownBoxW, 18, Component.literal("Crown Name 2"));
            this.crown2Box.setMaxLength(14);
            this.crown2Box.setValue(this.crown2Input);
            this.crown2Box.setResponder(val -> {
                this.crown2Input = val;
                updateButtonsAndFeePreview();
            });
            this.addRenderableWidget(this.crown2Box);

            this.crown2ModeBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.crown2Prefix = !this.crown2Prefix;
                updateButtonsAndFeePreview();
            }).bounds(colLeft + crownBoxW + 4, topY + 144, 64, 18).build();
            this.addRenderableWidget(this.crown2ModeBtn);

            // 4. Right Column: Jockey Silk Studio Button
            int silkColX = leftX + panelW - 128;
            this.openSilkStudioBtn = AutoFitButton.create(Component.literal("§e🎨 " + ThoroughbredData.tr("Edit Jockey Silk", "編輯綵衣", "编辑彩衣", "勝負服を編集")), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new JockeySilkCreatorScreen(this, this.workingSilk.copy()));
                }
            }).bounds(silkColX, topY + 144, 114, 18).build();
            this.addRenderableWidget(this.openSilkStudioBtn);

            // 5. Bottom Action Buttons: Register / Save Changes + Close
            int btnY = topY + panelH - 25;
            this.saveOrRegisterBtn = AutoFitButton.create(Component.empty(), btn -> submitOwnerProfile())
                    .bounds(colLeft, btnY, panelW - 106, 20).build();
            Button closeBtn = AutoFitButton.create(Component.literal(ThoroughbredData.tr("Close", "關閉", "关闭", "閉じる")), btn -> this.onClose())
                    .bounds(leftX + panelW - 86, btnY, 72, 20).build();

            this.addRenderableWidget(this.saveOrRegisterBtn);
            this.addRenderableWidget(closeBtn);

            updateButtonsAndFeePreview();
        }

        private long computeCurrentRequiredFee(List<String> outItems) {
            if (!this.pkt.registered) {
                outItems.add(String.format(Locale.ROOT, "Initial License: $%,d", this.pkt.registrationFee));
                return this.pkt.registrationFee;
            }
            long fee = 0L;
            String cleanFarm = this.farmNameInput != null ? this.farmNameInput.trim() : "";
            String origFarm = this.pkt.farmName != null ? this.pkt.farmName.trim() : "";
            if (!cleanFarm.equals(origFarm)) {
                fee += this.pkt.farmRenameFee;
                outItems.add(String.format(Locale.ROOT, "Farm Name ($%,d)", this.pkt.farmRenameFee));
            }

            String c1 = this.crown1Input != null ? this.crown1Input.trim() : "";
            String origC1 = this.pkt.crownName1 != null ? this.pkt.crownName1.trim() : "";
            if (!c1.equals(origC1) || (!c1.isEmpty() && this.crown1Prefix != this.pkt.crown1Prefix)) {
                fee += this.pkt.crownEditFee;
                outItems.add(String.format(Locale.ROOT, "Crown #1 ($%,d)", this.pkt.crownEditFee));
            }

            String c2 = this.crown2Input != null ? this.crown2Input.trim() : "";
            String origC2 = this.pkt.crownName2 != null ? this.pkt.crownName2.trim() : "";
            if (!c2.equals(origC2) || (!c2.isEmpty() && this.crown2Prefix != this.pkt.crown2Prefix)) {
                fee += this.pkt.crownEditFee;
                outItems.add(String.format(Locale.ROOT, "Crown #2 ($%,d)", this.pkt.crownEditFee));
            }

            StudbookSavedData.JockeySilkConfig origSilk = StudbookSavedData.JockeySilkConfig.fromCompactCode(this.pkt.silkCode);
            if (!this.workingSilk.matches(origSilk)) {
                fee += this.pkt.silkEditFee;
                outItems.add(String.format(Locale.ROOT, "Jockey Silk ($%,d)", this.pkt.silkEditFee));
            }
            return fee;
        }

        private void updateButtonsAndFeePreview() {
            if (this.crown1ModeBtn != null) {
                this.crown1ModeBtn.setMessage(Component.literal(this.crown1Prefix ? "§b[Prefix]" : "§d[Suffix]"));
            }
            if (this.crown2ModeBtn != null) {
                this.crown2ModeBtn.setMessage(Component.literal(this.crown2Prefix ? "§b[Prefix]" : "§d[Suffix]"));
            }
            if (this.saveOrRegisterBtn != null) {
                String cleanFarm = this.farmNameInput != null ? this.farmNameInput.trim() : "";
                boolean validFarm = cleanFarm.length() >= 2 && cleanFarm.length() <= 24;
                List<String> items = new ArrayList<>();
                long fee = computeCurrentRequiredFee(items);

                if (!this.pkt.registered) {
                    this.saveOrRegisterBtn.active = validFarm;
                    this.saveOrRegisterBtn.setMessage(Component.literal(String.format(Locale.ROOT,
                            "§a§l✔ Pay $%,d & Officially Become a Horse Owner", fee)));
                } else {
                    boolean anyChanged = !items.isEmpty();
                    this.saveOrRegisterBtn.active = validFarm && anyChanged;
                    if (!anyChanged) {
                        this.saveOrRegisterBtn.setMessage(Component.literal("§7No Profile Changes to Save ($0)"));
                    } else {
                        this.saveOrRegisterBtn.setMessage(Component.literal(String.format(Locale.ROOT,
                                "§e§l✔ Save Changes ($%,d: %s)", fee, String.join(" + ", items))));
                    }
                }
            }
        }

        private void submitOwnerProfile() {
            String cleanFarm = this.farmNameInput != null ? this.farmNameInput.trim() : "";
            if (cleanFarm.length() < 2 || cleanFarm.length() > 24) {
                this.statusMessage = "§cFarm / Horse Owner Alias must be between 2 and 24 characters!";
                return;
            }
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.SaveOwnerProfilePacket(
                    cleanFarm,
                    this.crown1Input != null ? this.crown1Input.trim() : "",
                    this.crown1Prefix,
                    this.crown2Input != null ? this.crown2Input.trim() : "",
                    this.crown2Prefix,
                    this.workingSilk.toCompactCode()
            ));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 16, 410);
            int panelH = Math.min(this.height - 12, 238);
            int leftX = centerX - panelW / 2;
            int rightX = leftX + panelW;
            int topY = (this.height - panelH) / 2;
            int bottomY = topY + panelH;

            graphics.fill(leftX, topY, rightX, bottomY, 0xEE10141C);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFD4AF37);
            graphics.fill(leftX, bottomY - 2, rightX, bottomY, 0xFFD4AF37);

            String title = this.pkt.registered
                    ? ("§6§l=== " + ThoroughbredData.tr("Official Horse Owner & Farm Profile (/tb owner)", "馬主與牧場檔案 (/tb owner)", "马主与牧场档案 (/tb owner)", "馬主・牧場プロフィール (/tb owner)") + " ===")
                    : ("§6§l=== " + ThoroughbredData.tr("Horse Owner License Registration (/tb owner)", "馬主牌照登記 (/tb owner)", "马主执照登记 (/tb owner)", "馬主ライセンス登録 (/tb owner)") + " ===");
            drawScaledCenteredString(graphics, this.font, title, centerX, topY + 7, panelW - 20, 0xFFFFFF);

            // Top Banner: Status + Bank Balance + Career Summary (Scaled so it never bleeds past rightX - 10)
            graphics.fill(leftX + 10, topY + 20, rightX - 10, topY + 46, 0xFF161D28);
            String badge = this.pkt.registered ? "§a§l[✔ LICENSED HORSE OWNER]" : "§c§l[UNREGISTERED — Locked out of Registry Form]";
            String balStr = String.format(Locale.ROOT, "§eBank Balance: §a$%,d", this.pkt.playerBalance);
            drawScaledCenteredString(graphics, this.font, badge + "  §7|  " + balStr, centerX, topY + 24, panelW - 28, 0xFFFFFF);

            String careerLine = String.format(Locale.ROOT,
                    "§7Owned Horses: §f%d  §7|  Starts: §f%d §7(§e%d§7-§b%d§7-§c%d§7)  §7|  G1 Wins: §6%d  §7|  Earnings: §a$%,d",
                    this.pkt.ownedCount, this.pkt.totalStarts, this.pkt.totalWins, this.pkt.totalSeconds,
                    this.pkt.totalThirds, this.pkt.g1Wins, this.pkt.totalEarnings);
            drawScaledCenteredString(graphics, this.font, careerLine, centerX, topY + 35, panelW - 28, 0xCCCCCC);

            int colLeft = leftX + 14;
            int silkColX = leftX + panelW - 128;
            int maxLeftLabelW = Math.max(120, (silkColX - 8) - colLeft);

            String previewAlias = (this.farmNameInput != null && !this.farmNameInput.isBlank())
                    ? (this.farmNameInput.trim() + " (" + this.pkt.playerName + ")")
                    : ("<Farm Name> (" + this.pkt.playerName + ")");
            drawScaledLeftString(graphics, this.font,
                    "§e1. Farm / Owner Alias §7(Displays as: §b" + previewAlias + "§7):",
                    colLeft, topY + 51, maxLeftLabelW, 0xFFFFFF);

            drawScaledLeftString(graphics, this.font,
                    "§e2. Optional Crown Name #1 §7(Quick-fill on Registry Form):",
                    colLeft, topY + 96, maxLeftLabelW, 0xFFFFFF);
            drawScaledLeftString(graphics, this.font,
                    "§e3. Optional Crown Name #2 §7(Leave blank if unused):",
                    colLeft, topY + 132, maxLeftLabelW, 0xFFFFFF);

            // Right Column: Jockey Silk Preview Card
            graphics.fill(silkColX, topY + 51, silkColX + 114, topY + 140, 0xFF151B24);
            graphics.fill(silkColX, topY + 51, silkColX + 114, topY + 52, 0xFFD4AF37);
            graphics.drawCenteredString(this.font, "§6§lFarm Jockey Silk", silkColX + 57, topY + 55, 0xFFFFFF);
            drawJockeySilkIcon(graphics, silkColX + 27, topY + 68, 60, this.workingSilk);

            // Itemized Edit Fee Schedule Info Bar
            graphics.fill(leftX + 10, topY + 168, rightX - 10, topY + 206, 0xFF141A24);
            String feePolicyLine = String.format(Locale.ROOT,
                    "§7Fee Schedule — Initial License: §a$%,d §7| Edit Farm Name: §e$%,d §7| Edit Crown Name: §e$%,d ea §7| Edit Silk: §e$%,d",
                    this.pkt.registrationFee, this.pkt.farmRenameFee, this.pkt.crownEditFee, this.pkt.silkEditFee);
            drawScaledCenteredString(graphics, this.font, feePolicyLine, centerX, topY + 173, panelW - 28, 0xCCCCCC);

            if (this.statusMessage != null && !this.statusMessage.isEmpty()) {
                drawScaledCenteredString(graphics, this.font, this.statusMessage, centerX, topY + 189, panelW - 28, 0xFFFFFF);
            } else {
                String silkDesc = "§7Active Silk: §f" + this.workingSilk.getSummaryDescription();
                drawScaledCenteredString(graphics, this.font, silkDesc, centerX, topY + 189, panelW - 28, 0xAAAAAA);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 10: Jockey Silk & Cap Creation Studio
    // =========================================================================
    public static class JockeySilkCreatorScreen extends Screen {
        private final HorseOwnerScreen parentScreen;
        private final StudbookSavedData.JockeySilkConfig silk;

        public JockeySilkCreatorScreen(HorseOwnerScreen parentScreen, StudbookSavedData.JockeySilkConfig silk) {
            super(Component.literal("Jockey Silk & Cap Creation Studio"));
            this.parentScreen = parentScreen;
            this.silk = silk != null ? silk : new StudbookSavedData.JockeySilkConfig();
            ensureJockeySilkTemplatesExported();
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 16, 412);
            int panelH = Math.min(this.height - 8, 250);
            int leftX = centerX - panelW / 2;
            int topY = (this.height - panelH) / 2;

            int ctrlLeft = leftX + 12;
            int ctrlW = 240;
            int numColors = StudbookSavedData.JockeySilkConfig.COLOR_NAMES.length;
            int numBodyPats = StudbookSavedData.JockeySilkConfig.BODY_PATTERN_NAMES.length;
            int numSlvPats = StudbookSavedData.JockeySilkConfig.SLEEVE_PATTERN_NAMES.length;
            int numCapPats = StudbookSavedData.JockeySilkConfig.CAP_PATTERN_NAMES.length;

            int startY = topY + 30;
            int stepY = 20;

            // Row 1: Body Color
            addCycleRow(ctrlLeft, startY, ctrlW,
                    () -> this.silk.bodyColor = Math.floorMod(this.silk.bodyColor - 1, numColors),
                    () -> this.silk.bodyColor = Math.floorMod(this.silk.bodyColor + 1, numColors));
            // Row 2: Body Pattern
            addCycleRow(ctrlLeft, startY + stepY, ctrlW,
                    () -> this.silk.bodyPattern = Math.floorMod(this.silk.bodyPattern - 1, numBodyPats),
                    () -> this.silk.bodyPattern = Math.floorMod(this.silk.bodyPattern + 1, numBodyPats));
            // Row 3: Body Pattern Color
            addCycleRow(ctrlLeft, startY + stepY * 2, ctrlW,
                    () -> this.silk.bodyPatternColor = Math.floorMod(this.silk.bodyPatternColor - 1, numColors),
                    () -> this.silk.bodyPatternColor = Math.floorMod(this.silk.bodyPatternColor + 1, numColors));

            // Row 4: Sleeve Color
            addCycleRow(ctrlLeft, startY + stepY * 3 + 2, ctrlW,
                    () -> this.silk.sleeveColor = Math.floorMod(this.silk.sleeveColor - 1, numColors),
                    () -> this.silk.sleeveColor = Math.floorMod(this.silk.sleeveColor + 1, numColors));
            // Row 5: Sleeve Pattern
            addCycleRow(ctrlLeft, startY + stepY * 4 + 2, ctrlW,
                    () -> this.silk.sleevePattern = Math.floorMod(this.silk.sleevePattern - 1, numSlvPats),
                    () -> this.silk.sleevePattern = Math.floorMod(this.silk.sleevePattern + 1, numSlvPats));
            // Row 6: Sleeve Pattern Color
            addCycleRow(ctrlLeft, startY + stepY * 5 + 2, ctrlW,
                    () -> this.silk.sleevePatternColor = Math.floorMod(this.silk.sleevePatternColor - 1, numColors),
                    () -> this.silk.sleevePatternColor = Math.floorMod(this.silk.sleevePatternColor + 1, numColors));

            // Row 7: Cap Color
            addCycleRow(ctrlLeft, startY + stepY * 6 + 4, ctrlW,
                    () -> this.silk.capColor = Math.floorMod(this.silk.capColor - 1, numColors),
                    () -> this.silk.capColor = Math.floorMod(this.silk.capColor + 1, numColors));
            // Row 8: Cap Pattern
            addCycleRow(ctrlLeft, startY + stepY * 7 + 4, ctrlW,
                    () -> this.silk.capPattern = Math.floorMod(this.silk.capPattern - 1, numCapPats),
                    () -> this.silk.capPattern = Math.floorMod(this.silk.capPattern + 1, numCapPats));
            // Row 9: Cap Pattern Color
            addCycleRow(ctrlLeft, startY + stepY * 8 + 4, ctrlW,
                    () -> this.silk.capPatternColor = Math.floorMod(this.silk.capPatternColor - 1, numColors),
                    () -> this.silk.capPatternColor = Math.floorMod(this.silk.capPatternColor + 1, numColors));

            // Right Column Buttons under the preview box
            int previewX = leftX + panelW - 144;
            this.addRenderableWidget(AutoFitButton.create(Component.literal("§d🎲 Randomize Silk & Cap"), btn -> {
                java.util.Random r = new java.util.Random();
                this.silk.bodyColor = r.nextInt(numColors);
                this.silk.bodyPattern = r.nextInt(numBodyPats);
                this.silk.bodyPatternColor = (this.silk.bodyColor + 1 + r.nextInt(numColors - 1)) % numColors;
                this.silk.sleeveColor = r.nextInt(numColors);
                this.silk.sleevePattern = r.nextInt(numSlvPats);
                this.silk.sleevePatternColor = (this.silk.sleeveColor + 1 + r.nextInt(numColors - 1)) % numColors;
                this.silk.capColor = r.nextBoolean() ? this.silk.bodyColor : r.nextInt(numColors);
                this.silk.capPattern = r.nextInt(numCapPats);
                this.silk.capPatternColor = (this.silk.capColor + 1 + r.nextInt(numColors - 1)) % numColors;
            }).bounds(previewX, topY + 166, 132, 18).build());

            this.addRenderableWidget(AutoFitButton.create(Component.literal("§b📁 Silk Folder"), btn -> {
                ensureJockeySilkTemplatesExported();
                Util.getPlatform().openFile(getJockeySilkTexturesFolder().toFile());
            }).bounds(previewX, topY + 188, 65, 18).build());

            this.addRenderableWidget(AutoFitButton.create(Component.literal("§a🔄 Reload PNG"), btn -> {
                reloadCustomJockeySilkMasks();
            }).bounds(previewX + 67, topY + 188, 65, 18).build());

            // Bottom Apply & Cancel buttons
            int btnY = topY + panelH - 24;
            this.addRenderableWidget(AutoFitButton.create(Component.literal("§a§l✔ Apply Jockey Silk & Cap to Farm"), btn -> {
                ThoroughbredRunEngine.clearJockeySilkTextureCache();
                if (this.parentScreen != null) {
                    this.parentScreen.setWorkingSilk(this.silk);
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parentScreen);
                    }
                } else {
                    this.onClose();
                }
            }).bounds(ctrlLeft, btnY, panelW - 114, 18).build());

            this.addRenderableWidget(AutoFitButton.create(Component.literal("Cancel"), btn -> {
                if (this.parentScreen != null && this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                } else {
                    this.onClose();
                }
            }).bounds(leftX + panelW - 94, btnY, 82, 18).build());
        }

        private void addCycleRow(int x, int y, int width, Runnable onPrev, Runnable onNext) {
            this.addRenderableWidget(AutoFitButton.create(Component.literal("◀"), b -> onPrev.run())
                    .bounds(x, y, 20, 18).build());
            this.addRenderableWidget(AutoFitButton.create(Component.literal("▶"), b -> onNext.run())
                    .bounds(x + width - 20, y, 20, 18).build());
        }

        private void drawLayerSelectorBox(GuiGraphics graphics, int x, int y, int width, String label, String value, int swatchColor, boolean showSwatch) {
            graphics.fill(x + 22, y, x + width - 22, y + 18, 0xFF18202C);
            graphics.fill(x + 22, y, x + width - 22, y + 1, 0x55D4AF37);
            int innerW = width - 48;
            drawScaledCenteredString(graphics, this.font, "§7" + label + ": §f§l" + value,
                    x + width / 2 + (showSwatch ? 5 : 0), y + 5, innerW - (showSwatch ? 14 : 4), 0xFFFFFF);
            if (showSwatch) {
                int swX = x + 26;
                graphics.fill(swX - 1, y + 4, swX + 9, y + 14, 0xFFFFFFFF);
                graphics.fill(swX, y + 5, swX + 8, y + 13, swatchColor);
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 16, 412);
            int panelH = Math.min(this.height - 8, 250);
            int leftX = centerX - panelW / 2;
            int rightX = leftX + panelW;
            int topY = (this.height - panelH) / 2;
            int bottomY = topY + panelH;

            graphics.fill(leftX, topY, rightX, bottomY, 0xEE10141C);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFD4AF37);
            graphics.fill(leftX, bottomY - 2, rightX, bottomY, 0xFFD4AF37);

            drawScaledCenteredString(graphics, this.font,
                    "§6§l=== Jockey Silk & Cap Creation Studio ===", centerX, topY + 7, panelW - 20, 0xFFFFFF);
            drawScaledCenteredString(graphics, this.font,
                    "§7Customize Jersey Body, Sleeves & Jockey Cap (or edit PNG masks via 📁 Silk Folder):",
                    centerX, topY + 18, panelW - 20, 0xCCCCCC);

            int ctrlLeft = leftX + 12;
            int ctrlW = 240;
            int startY = topY + 30;
            int stepY = 20;
            String[] cNames = StudbookSavedData.JockeySilkConfig.COLOR_NAMES;
            int[] cArgb = StudbookSavedData.JockeySilkConfig.COLOR_ARGB;

            drawLayerSelectorBox(graphics, ctrlLeft, startY, ctrlW,
                    "Body Color", cNames[this.silk.bodyColor], cArgb[this.silk.bodyColor], true);
            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY, ctrlW,
                    "Body Pattern", StudbookSavedData.JockeySilkConfig.BODY_PATTERN_NAMES[this.silk.bodyPattern], 0, false);
            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 2, ctrlW,
                    "Body Pat. Color", this.silk.bodyPattern == 0 ? "N/A (Solid)" : cNames[this.silk.bodyPatternColor],
                    cArgb[this.silk.bodyPatternColor], this.silk.bodyPattern != 0);

            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 3 + 2, ctrlW,
                    "Sleeve Color", cNames[this.silk.sleeveColor], cArgb[this.silk.sleeveColor], true);
            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 4 + 2, ctrlW,
                    "Sleeve Pattern", StudbookSavedData.JockeySilkConfig.SLEEVE_PATTERN_NAMES[this.silk.sleevePattern], 0, false);
            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 5 + 2, ctrlW,
                    "Sleeve Pat. Color", this.silk.sleevePattern == 0 ? "N/A (Solid)" : cNames[this.silk.sleevePatternColor],
                    cArgb[this.silk.sleevePatternColor], this.silk.sleevePattern != 0);

            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 6 + 4, ctrlW,
                    "Jockey Cap Color", cNames[this.silk.capColor], cArgb[this.silk.capColor], true);
            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 7 + 4, ctrlW,
                    "Jockey Cap Pattern", StudbookSavedData.JockeySilkConfig.CAP_PATTERN_NAMES[this.silk.capPattern], 0, false);
            drawLayerSelectorBox(graphics, ctrlLeft, startY + stepY * 8 + 4, ctrlW,
                    "Cap Pat. Color", this.silk.capPattern == 0 ? "N/A (Solid)" : cNames[this.silk.capPatternColor],
                    cArgb[this.silk.capPatternColor], this.silk.capPattern != 0);

            // Large Live Silk & Cap Preview Box on the Right
            int previewX = leftX + panelW - 144;
            graphics.fill(previewX, topY + 30, previewX + 132, topY + 162, 0xFF161D28);
            graphics.fill(previewX, topY + 30, previewX + 132, topY + 31, 0xFFD4AF37);
            graphics.drawCenteredString(this.font, "§e§lLive Cap & Silk", previewX + 66, topY + 36, 0xFFFFFF);

            drawJockeySilkIcon(graphics, previewX + 20, topY + 52, 92, this.silk);

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 11: Global Thoroughbred Studbook Browser GUI (/tb studbook)
    // =========================================================================
    public static class StudbookBrowserScreen extends Screen {
        private final String currentCropTag;
        private final String timeFlowSummary;
        private final List<ModNetwork.OpenStudbookScreenPacket.StudbookHorseSummary> allHorses;
        private final List<String> cropOptions = new ArrayList<>();
        private final List<ModNetwork.OpenStudbookScreenPacket.StudbookHorseSummary> filteredHorses = new ArrayList<>();

        private int selectedCropIdx = 0; // 0 = "All Crops", 1+ = specific Year or Year (Season)
        private int roleFilter = 0;      // 0 = All, 1 = Active, 2 = Studs, 3 = Broodmares, 4 = Youngsters, 5 = Deceased
        private int sortMode = 0;        // 0 = Earnings, 1 = Wins, 2 = HP%, 3 = Name A-Z
        private String searchQuery = "";
        private int scrollOffset = 0;

        private EditBox searchBox;
        private Button prevCropBtn;
        private Button nextCropBtn;
        private Button sortBtn;
        private final List<Button> tabButtons = new ArrayList<>();

        private static final int ROW_HEIGHT = 24;

        public StudbookBrowserScreen(ModNetwork.OpenStudbookScreenPacket msg) {
            super(Component.literal("Official Thoroughbred Studbook"));
            this.currentCropTag = msg.currentCropTag != null ? msg.currentCropTag : "";
            this.timeFlowSummary = msg.timeFlowSummary != null ? msg.timeFlowSummary : "";
            this.allHorses = msg.horses != null ? msg.horses : new ArrayList<>();

            this.cropOptions.add("All Crops");
            java.util.Set<String> uniqueCrops = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
            if (!this.currentCropTag.isBlank()) {
                uniqueCrops.add(this.currentCropTag);
            }
            for (ModNetwork.OpenStudbookScreenPacket.StudbookHorseSummary h : this.allHorses) {
                if (h.cropTag != null && !h.cropTag.isBlank()) {
                    uniqueCrops.add(h.cropTag);
                }
            }
            this.cropOptions.addAll(uniqueCrops);
            rebuildFilteredList();
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 14, 436);
            int panelH = Math.min(this.height - 10, 246);
            int leftX = centerX - panelW / 2;
            int topY = (this.height - panelH) / 2;

            // 1. Crop / Year (Season) Selector Row
            this.prevCropBtn = AutoFitButton.create(Component.literal("◀"), btn -> {
                this.selectedCropIdx = Math.floorMod(this.selectedCropIdx - 1, this.cropOptions.size());
                this.scrollOffset = 0;
                rebuildFilteredList();
            }).bounds(leftX + 10, topY + 22, 20, 18).build();

            this.nextCropBtn = AutoFitButton.create(Component.literal("▶"), btn -> {
                this.selectedCropIdx = Math.floorMod(this.selectedCropIdx + 1, this.cropOptions.size());
                this.scrollOffset = 0;
                rebuildFilteredList();
            }).bounds(leftX + 150, topY + 22, 20, 18).build();

            this.addRenderableWidget(this.prevCropBtn);
            this.addRenderableWidget(this.nextCropBtn);

            // Search Box
            this.searchBox = new EditBox(this.font, leftX + 176, topY + 22, 152, 18, Component.literal("Search Studbook..."));
            this.searchBox.setMaxLength(40);
            this.searchBox.setValue(this.searchQuery);
            this.searchBox.setHint(Component.literal("§8Search Horse, Farm, Sire..."));
            this.searchBox.setResponder(val -> {
                this.searchQuery = val != null ? val.trim() : "";
                this.scrollOffset = 0;
                rebuildFilteredList();
            });
            this.addRenderableWidget(this.searchBox);

            // Sort Button
            this.sortBtn = AutoFitButton.create(Component.empty(), btn -> {
                this.sortMode = (this.sortMode + 1) % 4;
                rebuildFilteredList();
                updateSortAndTabButtons();
            }).bounds(leftX + 332, topY + 22, panelW - 342, 18).build();
            this.addRenderableWidget(this.sortBtn);

            // 2. Role Filter Tabs (All, Active, Studs, Broodmares, Youngsters, Deceased)
            this.tabButtons.clear();
            String[] tabLabels = {"All", "Active", "Studs", "Broodmares", "Youngsters", "Deceased"};
            int tabW = (panelW - 20) / tabLabels.length;
            for (int i = 0; i < tabLabels.length; i++) {
                final int tabIdx = i;
                Button tBtn = AutoFitButton.create(Component.literal(tabLabels[i]), btn -> {
                    this.roleFilter = tabIdx;
                    this.scrollOffset = 0;
                    rebuildFilteredList();
                    updateSortAndTabButtons();
                }).bounds(leftX + 10 + i * tabW, topY + 43, tabW - 2, 16).build();
                this.tabButtons.add(tBtn);
                this.addRenderableWidget(tBtn);
            }

            // Close Button
            this.addRenderableWidget(AutoFitButton.create(Component.literal("Close"), btn -> this.onClose())
                    .bounds(centerX - 45, topY + panelH - 23, 90, 18).build());

            updateSortAndTabButtons();
        }

        private void updateSortAndTabButtons() {
            if (this.sortBtn != null) {
                String sLabel = switch (this.sortMode) {
                    case 0 -> "§e" + ThoroughbredData.tr("Sort: Earnings", "排序: 獎金", "排序: 奖金", "順: 賞金");
                    case 1 -> "§e" + ThoroughbredData.tr("Sort: Wins", "排序: 勝場", "排序: 胜场", "順: 勝利数");
                    case 2 -> "§e" + ThoroughbredData.tr("Sort: HP%", "排序: 血統HP%", "排序: 血统HP%", "順: 血統HP%");
                    default -> "§e" + ThoroughbredData.tr("Sort: Name A-Z", "排序: 馬名", "排序: 马名", "順: 馬名");
                };
                this.sortBtn.setMessage(Component.literal(sLabel));
            }
            String[] tabLabels = {
                    ThoroughbredData.tr("All", "全部", "全部", "すべて"),
                    ThoroughbredData.tr("Active", "現役", "现役", "現役"),
                    ThoroughbredData.tr("Studs", "種馬", "种马", "種牡馬"),
                    ThoroughbredData.tr("Broodmares", "繁殖牝馬", "繁殖母马", "繁殖牝馬"),
                    ThoroughbredData.tr("Youngsters", "幼駒", "幼驹", "若駒"),
                    ThoroughbredData.tr("Deceased", "已故", "已故", "死去")
            };
            for (int i = 0; i < this.tabButtons.size() && i < tabLabels.length; i++) {
                boolean active = (this.roleFilter == i);
                this.tabButtons.get(i).setMessage(Component.literal((active ? "§6§l[" + tabLabels[i] + "]" : "§7" + tabLabels[i])));
            }
        }

        private void rebuildFilteredList() {
            this.filteredHorses.clear();
            String selectedCrop = (this.selectedCropIdx > 0 && this.selectedCropIdx < this.cropOptions.size())
                    ? this.cropOptions.get(this.selectedCropIdx)
                    : "";
            String q = this.searchQuery != null ? this.searchQuery.trim().toLowerCase(Locale.ROOT) : "";

            for (ModNetwork.OpenStudbookScreenPacket.StudbookHorseSummary h : this.allHorses) {
                if (!selectedCrop.isEmpty() && !selectedCrop.equalsIgnoreCase(h.cropTag)) {
                    continue;
                }
                if (this.roleFilter == 1 && (h.deceased || !h.roleTag.startsWith("Active"))) continue;
                if (this.roleFilter == 2 && (h.deceased || !"Stud".equalsIgnoreCase(h.roleTag))) continue;
                if (this.roleFilter == 3 && (h.deceased || !"Broodmare".equalsIgnoreCase(h.roleTag))) continue;
                if (this.roleFilter == 4 && (h.deceased || (!h.roleTag.contains("Foal") && !h.roleTag.contains("Yearling")))) continue;
                if (this.roleFilter == 5 && !h.deceased) continue;

                if (!q.isEmpty()) {
                    boolean match = h.horseName.toLowerCase(Locale.ROOT).contains(q)
                            || h.ownerName.toLowerCase(Locale.ROOT).contains(q)
                            || h.sireName.toLowerCase(Locale.ROOT).contains(q)
                            || h.damName.toLowerCase(Locale.ROOT).contains(q)
                            || h.cropTag.toLowerCase(Locale.ROOT).contains(q);
                    if (!match) continue;
                }
                this.filteredHorses.add(h);
            }

            this.filteredHorses.sort((a, b) -> switch (this.sortMode) {
                case 0 -> Long.compare(b.totalEarnings, a.totalEarnings);
                case 1 -> Integer.compare(b.wins, a.wins);
                case 2 -> Integer.compare(b.hereditaryPoints, a.hereditaryPoints);
                default -> a.horseName.compareToIgnoreCase(b.horseName);
            });
        }

        private int getVisibleRows(int panelH) {
            int listH = (panelH - 28) - 63;
            return Math.max(3, listH / ROW_HEIGHT);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            int panelH = Math.min(this.height - 10, 246);
            int maxScroll = Math.max(0, this.filteredHorses.size() - getVisibleRows(panelH));
            if (delta > 0) {
                this.scrollOffset = Mth.clamp(this.scrollOffset - 1, 0, maxScroll);
                return true;
            } else if (delta < 0) {
                this.scrollOffset = Mth.clamp(this.scrollOffset + 1, 0, maxScroll);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 14, 436);
            int panelH = Math.min(this.height - 10, 246);
            int leftX = centerX - panelW / 2;
            int topY = (this.height - panelH) / 2;
            int listTop = topY + 63;
            int visRows = getVisibleRows(panelH);

            if (mouseX >= leftX + 10 && mouseX <= leftX + panelW - 16 && mouseY >= listTop && mouseY < listTop + visRows * ROW_HEIGHT) {
                int clickedIdx = this.scrollOffset + (int) ((mouseY - listTop) / ROW_HEIGHT);
                if (clickedIdx >= 0 && clickedIdx < this.filteredHorses.size()) {
                    ModNetwork.OpenStudbookScreenPacket.StudbookHorseSummary target = this.filteredHorses.get(clickedIdx);
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.RequestStudbookHorseCardPacket(target.horseName));
                    return true;
                }
            }
            return false;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 14, 436);
            int panelH = Math.min(this.height - 10, 246);
            int leftX = centerX - panelW / 2;
            int rightX = leftX + panelW;
            int topY = (this.height - panelH) / 2;
            int bottomY = topY + panelH;

            graphics.fill(leftX, topY, rightX, bottomY, 0xEE10141C);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFD4AF37);
            graphics.fill(leftX, bottomY - 2, rightX, bottomY, 0xFFD4AF37);

            String title = String.format(Locale.ROOT,
                    "§6§l=== %s (%d / %d) ===",
                    ThoroughbredData.tr("Official Thoroughbred Studbook", "純血馬官方血統書", "纯血马官方血统书", "公式サラブレッド血統書"),
                    this.filteredHorses.size(), this.allHorses.size());
            drawScaledCenteredString(graphics, this.font, title, centerX, topY + 7, panelW - 20, 0xFFFFFF);

            // Crop Selector Box display
            graphics.fill(leftX + 32, topY + 22, leftX + 148, topY + 40, 0xFF18202C);
            String rawCrop = (this.selectedCropIdx >= 0 && this.selectedCropIdx < this.cropOptions.size())
                    ? this.cropOptions.get(this.selectedCropIdx)
                    : "All Crops";
            String cropLabel = "All Crops".equalsIgnoreCase(rawCrop)
                    ? ThoroughbredData.tr("All Crops", "全部世代", "全部世代", "全世代")
                    : rawCrop;
            drawScaledCenteredString(graphics, this.font, "§b📅 " + cropLabel, leftX + 90, topY + 27, 112, 0xFFFFFF);

            int listTop = topY + 63;
            int visRows = getVisibleRows(panelH);
            int listBottom = listTop + visRows * ROW_HEIGHT;
            graphics.fill(leftX + 10, listTop - 1, rightX - 10, listBottom + 1, 0xFF0B0E14);

            if (this.filteredHorses.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7No registered Thoroughbreds match the current Crop / Filter.", centerX, listTop + 34, 0x888888);
            } else {
                graphics.enableScissor(leftX + 11, listTop, rightX - 16, listBottom);
                for (int i = 0; i < visRows; i++) {
                    int idx = this.scrollOffset + i;
                    if (idx >= this.filteredHorses.size()) break;
                    ModNetwork.OpenStudbookScreenPacket.StudbookHorseSummary h = this.filteredHorses.get(idx);
                    int rowY = listTop + i * ROW_HEIGHT;
                    boolean hovered = mouseX >= leftX + 10 && mouseX <= rightX - 16 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1;

                    int bg = hovered ? 0xFF222C3C : ((i % 2 == 0) ? 0xFF141A24 : 0xFF11161F);
                    graphics.fill(leftX + 11, rowY, rightX - 16, rowY + ROW_HEIGHT - 1, bg);

                    int accent = h.deceased ? 0xFF773333 : (h.gelding ? 0xFF33AAAA : ("Female".equalsIgnoreCase(h.gender) ? 0xFFFF66B2 : 0xFF55B4FF));
                    graphics.fill(leftX + 11, rowY, leftX + 13, rowY + ROW_HEIGHT - 1, accent);

                    int textLeft = leftX + 17;
                    if (h.ownerSilkCode != null && !h.ownerSilkCode.isBlank()) {
                        drawJockeySilkIcon(graphics, leftX + 16, rowY + 3, 17,
                                StudbookSavedData.JockeySilkConfig.fromCompactCode(h.ownerSilkCode));
                        textLeft = leftX + 37;
                    }

                    String rightStats = String.format(Locale.ROOT, "§e%d%% HP §7| §f%d/%dW §7| §a$%,d",
                            h.hereditaryPoints, h.starts, h.wins, h.totalEarnings);
                    int rW = this.font.width(rightStats);
                    graphics.drawString(this.font, rightStats, rightX - 20 - rW, rowY + 3, 0xFFFFFF);

                    String sexBadge = h.gelding ? "§3[G]" : ("Female".equalsIgnoreCase(h.gender) ? "§d[F]" : "§b[M]");
                    String roleBadge = h.deceased ? "§c[Deceased]" : ("§7[" + h.roleTag + "]");
                    String line1 = sexBadge + " §6§l" + h.horseName + " §e[" + h.cropTag + "] " + roleBadge
                            + "  §7Owner: §f" + h.ownerName;
                    int maxLine1W = Math.max(60, (rightX - 24 - rW) - textLeft);
                    drawScaledLeftString(graphics, this.font, line1, textLeft, rowY + 3, maxLine1W, 0xFFFFFF);

                    String line2 = String.format(Locale.ROOT, "§7Bloodline: §b%s §7× §d%s  §8(Click to view full Stat Card & Pedigree)",
                            h.sireName, h.damName);
                    int maxLine2W = Math.max(80, (rightX - 20) - textLeft);
                    drawScaledLeftString(graphics, this.font, line2, textLeft, rowY + 13, maxLine2W, 0xAAAAAA);
                }
                graphics.disableScissor();
            }

            // Scrollbar
            int maxScroll = Math.max(0, this.filteredHorses.size() - visRows);
            int trackX = rightX - 14;
            int trackH = visRows * ROW_HEIGHT;
            graphics.fill(trackX, listTop, trackX + 3, listTop + trackH, 0xFF222832);
            if (maxScroll > 0) {
                int thumbH = Math.max(14, (int) (trackH * ((double) visRows / this.filteredHorses.size())));
                int thumbY = listTop + (int) ((trackH - thumbH) * ((double) this.scrollOffset / maxScroll));
                graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFD4AF37);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // =========================================================================
    // SCREEN 12: Admin Steward Race Results Review & Confirmation HUD (/tb results)
    // =========================================================================
    public static class RaceResultsAdminScreen extends Screen {
        private ModNetwork.OpenRaceResultsScreenPacket packet;
        private int scrollOffset = 0;
        private final List<Button> rowDsqButtons = new ArrayList<>();
        private Button confirmBtn;
        private static final int ROW_HEIGHT = 24;

        public RaceResultsAdminScreen(ModNetwork.OpenRaceResultsScreenPacket msg) {
            super(Component.literal("Steward Race Results Review"));
            this.packet = msg;
        }

        public void updateFromPacket(ModNetwork.OpenRaceResultsScreenPacket msg) {
            this.packet = msg;
            this.rebuildWidgets();
        }

        private int getVisibleRows(int panelH) {
            int listH = (panelH - 32) - 52;
            return Math.max(3, listH / ROW_HEIGHT);
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 14, 438);
            int panelH = Math.min(this.height - 10, 246);
            int leftX = centerX - panelW / 2;
            int rightX = leftX + panelW;
            int topY = (this.height - panelH) / 2;
            int listTop = topY + 50;
            int visRows = getVisibleRows(panelH);

            int maxScroll = Math.max(0, (this.packet != null ? this.packet.runners.size() : 0) - visRows);
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

            this.rowDsqButtons.clear();
            if (this.packet != null) {
                for (int i = 0; i < visRows; i++) {
                    int idx = this.scrollOffset + i;
                    if (idx >= this.packet.runners.size()) break;
                    ModNetwork.OpenRaceResultsScreenPacket.ResultRowEntry r = this.packet.runners.get(idx);
                    int rowY = listTop + i * ROW_HEIGHT;
                    final int gateNum = r.gateNumber;
                    String btnLabel = r.dsq
                            ? ("§e" + ThoroughbredData.tr("Restore", "恢復資格", "恢复资格", "失格解除"))
                            : ("§c" + ThoroughbredData.tr("DSQ", "取消資格", "取消资格", "失格処分"));
                    Button dsqBtn = AutoFitButton.create(Component.literal(btnLabel), btn -> {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RaceResultsActionPacket(
                                ModNetwork.RaceResultsActionPacket.ACTION_TOGGLE_DSQ, gateNum));
                    }).bounds(rightX - 70, rowY + 3, 52, 18).build();
                    dsqBtn.active = !this.packet.confirmed;
                    this.rowDsqButtons.add(dsqBtn);
                    this.addRenderableWidget(dsqBtn);
                }
            }

            int btnY = topY + panelH - 24;
            boolean alreadyConfirmed = this.packet != null && this.packet.confirmed;
            String confirmLabel = alreadyConfirmed
                    ? ("§a✔ " + ThoroughbredData.tr("Official Results Confirmed & Paid Out", "正式賽果已確認並派發獎金", "正式赛果已确认并派发奖金", "公式着順確定・賞金支払済"))
                    : ("§a§l✔ " + ThoroughbredData.tr("Accept & Confirm Official Results", "確認並公佈正式賽果 (派發獎金與戰績)", "确认并公布正式赛果 (派发奖金与战绩)", "着順確定・賞金および戦績を反映"));

            this.confirmBtn = AutoFitButton.create(Component.literal(confirmLabel), btn -> {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.RaceResultsActionPacket(
                        ModNetwork.RaceResultsActionPacket.ACTION_CONFIRM_RESULTS, 0));
            }).bounds(leftX + 10, btnY, panelW - 166, 18).build();
            this.confirmBtn.active = !alreadyConfirmed && this.packet != null && !this.packet.runners.isEmpty();
            this.addRenderableWidget(this.confirmBtn);

            this.addRenderableWidget(AutoFitButton.create(
                    Component.literal("§b📁 " + ThoroughbredData.tr("Lang Folder", "翻譯資料夾", "翻译文件夹", "翻訳フォルダ")),
                    btn -> {
                        ThoroughbredData.ensureTranslationFilesInitialized();
                        List<File> dirs = ThoroughbredData.getTranslationDirectories();
                        if (!dirs.isEmpty()) {
                            Util.getPlatform().openFile(dirs.get(0));
                        }
                    }).bounds(rightX - 152, btnY, 72, 18).build());

            this.addRenderableWidget(AutoFitButton.create(
                    Component.literal(ThoroughbredData.tr("Close", "關閉", "关闭", "閉じる")),
                    btn -> this.onClose()).bounds(rightX - 76, btnY, 66, 18).build());
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            int panelH = Math.min(this.height - 10, 246);
            int maxScroll = Math.max(0, (this.packet != null ? this.packet.runners.size() : 0) - getVisibleRows(panelH));
            if (delta > 0 && this.scrollOffset > 0) {
                this.scrollOffset = Mth.clamp(this.scrollOffset - 1, 0, maxScroll);
                this.rebuildWidgets();
                return true;
            } else if (delta < 0 && this.scrollOffset < maxScroll) {
                this.scrollOffset = Mth.clamp(this.scrollOffset + 1, 0, maxScroll);
                this.rebuildWidgets();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);

            int centerX = this.width / 2;
            int panelW = Math.min(this.width - 14, 438);
            int panelH = Math.min(this.height - 10, 246);
            int leftX = centerX - panelW / 2;
            int rightX = leftX + panelW;
            int topY = (this.height - panelH) / 2;
            int bottomY = topY + panelH;

            graphics.fill(leftX, topY, rightX, bottomY, 0xEE10141C);
            graphics.fill(leftX, topY, rightX, topY + 2, 0xFFD4AF37);
            graphics.fill(leftX, bottomY - 2, rightX, bottomY, 0xFFD4AF37);

            String headerTitle = "§6§l=== " + ThoroughbredData.tr(
                    "Steward Race Results Review",
                    "競賽董事賽果審議面板",
                    "竞赛董事赛果审议面板",
                    "裁決委員・着順審議パネル") + " (/tb results) ===";
            drawScaledCenteredString(graphics, this.font, headerTitle, centerX, topY + 7, panelW - 20, 0xFFFFFF);

            if (this.packet != null) {
                String statusBadge = this.packet.confirmed
                        ? ("§a§l[" + ThoroughbredData.tr("CONFIRMED", "確定", "确定", "確定") + "]")
                        : ("§6§l[" + ThoroughbredData.tr("INQUIRY / PENDING CONFIRMATION", "審議中 / 待確認", "审议中 / 待确认", "審議中・未確定") + "]");
                String subLine1 = String.format(Locale.ROOT,
                        "§b§l[%s] §f§l%s §7(%s)  %s",
                        this.packet.grade, this.packet.raceName, this.packet.trackSummary, statusBadge);
                drawScaledCenteredString(graphics, this.font, subLine1, centerX, topY + 19, panelW - 20, 0xFFFFFF);

                String subLine2 = this.packet.isTestRace
                        ? "§e[Test Race — No Purse or Career Record]"
                        : String.format(Locale.ROOT,
                        "§71st Purse: §a$%,d §7| Review finish order, click §c[DSQ] §7on rule-breakers, then click §a[Accept & Confirm]§7!",
                        this.packet.firstPlacePurse);
                drawScaledCenteredString(graphics, this.font, subLine2, centerX, topY + 31, panelW - 20, 0xCCCCCC);
            }

            int listTop = topY + 50;
            int visRows = getVisibleRows(panelH);
            int listBottom = listTop + visRows * ROW_HEIGHT;
            graphics.fill(leftX + 10, listTop - 1, rightX - 10, listBottom + 1, 0xFF0B0E14);

            if (this.packet == null || this.packet.runners.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7No runners recorded for this race.", centerX, listTop + 36, 0x888888);
            } else {
                int totalStalls = Math.max(1, this.packet.totalStalls);
                for (int i = 0; i < visRows; i++) {
                    int idx = this.scrollOffset + i;
                    if (idx >= this.packet.runners.size()) break;
                    ModNetwork.OpenRaceResultsScreenPacket.ResultRowEntry r = this.packet.runners.get(idx);
                    int rowY = listTop + i * ROW_HEIGHT;

                    int bg = r.dsq ? 0xFF281214 : (r.dnf ? 0xFF221618 : ((r.placement == 1) ? 0xFF242012 : ((i % 2 == 0) ? 0xFF141A24 : 0xFF11161F)));
                    graphics.fill(leftX + 11, rowY, rightX - 16, rowY + ROW_HEIGHT - 1, bg);

                    int accentCol = r.dsq ? 0xFFCC2222 : (r.dnf ? 0xFFFF5555 : switch (r.placement) {
                        case 1 -> 0xFFFFD700;
                        case 2 -> 0xFF55DDFF;
                        case 3 -> 0xFFFF9944;
                        default -> 0xFF556677;
                    });
                    graphics.fill(leftX + 11, rowY, leftX + 14, rowY + ROW_HEIGHT - 1, accentCol);

                    // 1. Placing Text
                    String placeStr;
                    if (r.dsq || r.placement >= 100) {
                        placeStr = "§4§lDSQ";
                    } else if (r.dnf || r.placement == 99) {
                        placeStr = "§c§lDNF";
                    } else {
                        placeStr = switch (r.placement) {
                            case 1 -> "§6§l" + ThoroughbredData.tr("1st", "1着", "第1", "1着");
                            case 2 -> "§b§l" + ThoroughbredData.tr("2nd", "2着", "第2", "2着");
                            case 3 -> "§c§l" + ThoroughbredData.tr("3rd", "3着", "第3", "3着");
                            default -> "§f" + r.placement + "th";
                        };
                    }
                    graphics.drawString(this.font, placeStr, leftX + 18, rowY + 7, 0xFFFFFF);

                    // 2. Gate Number Badge
                    int gateBoxX = leftX + 46;
                    int gateBoxY = rowY + 5;
                    graphics.fill(gateBoxX - 1, gateBoxY - 1, gateBoxX + 17, gateBoxY + 13, 0xFF000000);
                    graphics.fill(gateBoxX, gateBoxY, gateBoxX + 16, gateBoxY + 12, 0xFF243447);
                    drawScaledCenteredString(graphics, this.font, "§e#" + r.gateNumber, gateBoxX + 8, gateBoxY + 2, 15, 0xFFFFFF);

                    int textLeft = leftX + 68;
                    if (r.ownerSilkCode != null && !r.ownerSilkCode.isBlank()) {
                        drawJockeySilkIcon(graphics, leftX + 66, rowY + 3, 17,
                                StudbookSavedData.JockeySilkConfig.fromCompactCode(r.ownerSilkCode));
                        textLeft = leftX + 86;
                    }

                    int rightBtnLeft = rightX - 74;
                    String purseStr = r.projectedPurse > 0L
                            ? String.format(Locale.ROOT, "§a+$%,d", r.projectedPurse)
                            : "§8$0";
                    String timeAndPurse = String.format(Locale.ROOT, "§e%s §7(%s)  %s",
                            r.formattedTime, r.formattedMargin, purseStr);
                    int tpW = this.font.width(timeAndPurse);
                    graphics.drawString(this.font, timeAndPurse, rightBtnLeft - tpW - 6, rowY + 3, 0xFFFFFF);

                    String line1 = (r.dsq ? "§c§m" : "§f§l") + r.horseName;
                    int maxNameW = Math.max(50, (rightBtnLeft - tpW - 12) - textLeft);
                    drawScaledLeftString(graphics, this.font, line1, textLeft, rowY + 3, maxNameW, 0xFFFFFF);

                    String jockeyTag = (r.jockeyName != null && !r.jockeyName.isBlank()) ? ("  §7|  Jockey: §e" + r.jockeyName) : "";
                    String line2 = "§7Owner: §b" + r.ownerName + jockeyTag;
                    drawScaledLeftString(graphics, this.font, line2, textLeft, rowY + 13, Math.max(60, rightBtnLeft - 6 - textLeft), 0xCCCCCC);
                }
            }

            // Scrollbar
            int totalRows = this.packet != null ? this.packet.runners.size() : 0;
            int maxScroll = Math.max(0, totalRows - visRows);
            int trackX = rightX - 14;
            int trackH = visRows * ROW_HEIGHT;
            graphics.fill(trackX, listTop, trackX + 3, listTop + trackH, 0xFF222832);
            if (maxScroll > 0) {
                int thumbH = Math.max(14, (int) (trackH * ((double) visRows / totalRows)));
                int thumbY = listTop + (int) ((trackH - thumbH) * ((double) this.scrollOffset / maxScroll));
                graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFD4AF37);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}