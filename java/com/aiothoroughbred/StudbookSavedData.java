package com.aiothoroughbred;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class StudbookSavedData extends SavedData {
    private static final String DATA_NAME = "aiothoroughbred_studbook";

    private final Set<String> registeredNames = new HashSet<>();
    private final Map<String, HorseBackupRecord> livingHorsesByName = new LinkedHashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    private final Map<UUID, Long> playerBalances = new HashMap<>();
    private final Map<String, RacingManager.ConsoleRaceConfig> racingConsoles = new LinkedHashMap<>();

    // Step 15: Persistent Scheduled Races, Registrations & Parimutuel Betting System
    private final Map<String, ScheduledRace> scheduledRaces = new LinkedHashMap<>();
    private int nextScheduleNumber = 1;

    // Step 18: World Time Flow Presets & Permanent 4-Generation Pedigree Archive
    public static final int TIMEFLOW_1_YEAR  = 0; // 365 days (366 on leap years)
    public static final int TIMEFLOW_4_MONTH = 1; // ~120-123 days (4 calendar months = 1 Equine Year)
    public static final int TIMEFLOW_3_MONTH = 2; // ~90-92 days (3 calendar months = 1 Equine Year)
    public static final int TIMEFLOW_CUSTOM  = 3; // Custom days per Equine Year

    private boolean timeFlowConfigured = false;
    private int timeFlowMode = TIMEFLOW_1_YEAR;
    private int customEquineYearDays = 60;
    private boolean geldingAdminOnly = false;
    private final Map<String, PedigreeArchiveRecord> pedigreeArchiveByName = new LinkedHashMap<>();
    private final Map<String, List<BlockPos>> numberSignLinks = new LinkedHashMap<>();

    // Step 21: Horse Owner Registration (/tb owner), Configurable Itemized Fees & Official 4-Layer Jockey Silks
    private long ownerRegistrationFee = 10_000L; // Default $10,000 initial registration fee
    private long ownerFarmRenameFee   = 20_000L; // Default $20,000 farm name edit fee (double initial registration)
    private long ownerCrownEditFee    = 1_000L;  // Default $1,000 per crown name edited
    private long ownerSilkEditFee     = 2_500L;  // Default $2,500 jockey silk edit fee
    private final Map<UUID, HorseOwnerProfile> ownerProfiles = new LinkedHashMap<>();

    /**
     * Official 4-Layer Jockey Silk System (inspired by https://kurisaka.cloudfree.jp/dot/):
     * Layer 1: Body Base Color (13 official Official colors)
     * Layer 2: Body Pattern (17 Official patterns) + Body Pattern Color
     * Layer 3: Sleeve Base Color (13 official Official colors)
     * Layer 4: Sleeve Pattern (8 Official patterns) + Sleeve Pattern Color
     */
    public static class JockeySilkConfig {
        public static final String[] COLOR_NAMES = {
                "White", "Black", "Red", "Blue", "Yellow", "Green", "Orange",
                "Pink", "Purple", "Brown", "Maroon", "Light Blue", "Grey"
        };

        public static final int[] COLOR_ARGB = {
                0xFFF4F6F8, // 0: White (白)
                0xFF1C1E24, // 1: Black (黒)
                0xFFD92626, // 2: Red (赤)
                0xFF1E56D9, // 3: Blue (青)
                0xFFF5D000, // 4: Yellow (黄)
                0xFF1E9E44, // 5: Green (緑)
                0xFFF2791A, // 6: Orange (橙)
                0xFFF472B6, // 7: Pink (桃)
                0xFF7E3AF2, // 8: Purple (紫)
                0xFF78472A, // 9: Brown (茶)
                0xFF7A1C32, // 10: Maroon / Enji (臙脂)
                0xFF4CC9F0, // 11: Light Blue / Mizuiro (水色)
                0xFF8C96A6  // 12: Grey / Nezumiiro (鼠)
        };

        public static final String[] BODY_PATTERN_NAMES = {
                "None (Solid)",        // 0
                "1 Horizontal Hoop",   // 1
                "2 Horizontal Hoops",  // 2
                "3 Horizontal Hoops",  // 3
                "Vertical Stripes",    // 4
                "Diagonal Sash",       // 5
                "Crossed Sashes",      // 6
                "Large Diamond",       // 7
                "Diamond Band",        // 8
                "Scales (Uroko)",      // 9
                "Checkered (Koushi)",  // 10
                "Polka Dots",          // 11
                "Star Emblem",         // 12
                "Chevron (V-Band)",    // 13
                "Double Chevron",      // 14
                "Epaulettes",          // 15
                "Halved Top/Bottom",   // 16
                "Sawtooth Band",       // 17 (ノコギリ歯 / Zigzag teeth)
                "Double Sawtooth",     // 18 (二段ノコギリ歯)
                "Vertical Halves",     // 19 (左右別)
                "Quartered",           // 20 (四つ割)
                "Argyle Diamonds",     // 21 (アーガイル格子)
                "Cross Emblem",        // 22 (十字)
                "Lightning Sash",      // 23 (稲妻襷)
                "Triple Diamond",      // 24 (縦三連ダイヤ)
                "Heart Emblem",        // 25 (ハート紋)
                "Crown Emblem",        // 26 (王冠紋)
                "Thin Hoops"           // 27 (細横縞)
        };

        public static final String[] SLEEVE_PATTERN_NAMES = {
                "None (Solid)",        // 0
                "1 Armlet Band",       // 1
                "2 Armlet Bands",      // 2
                "3 Armlet Bands",      // 3
                "Vertical Stripe",     // 4
                "Diamond Sleeves",     // 5
                "Polka Dot Sleeves",   // 6
                "Star Sleeves",        // 7
                "Sawtooth Sleeves",    // 8
                "Halved Sleeves",      // 9
                "Checkered Sleeves",   // 10
                "Chevron Sleeves",     // 11
                "Cuff Trim",           // 12
                "Thin Striped Sleeves" // 13
        };

        public static final String[] CAP_PATTERN_NAMES = {
                "Solid Cap",           // 0
                "Quartered Cap",       // 1
                "Hooped Cap",          // 2
                "Star Cap",            // 3
                "Diamond Cap",         // 4
                "Sawtooth Cap",        // 5
                "Checkered Cap",       // 6
                "Contrast Visor"       // 7
        };

        public int bodyColor = 3;          // Default Blue
        public int bodyPattern = 17;       // Default Sawtooth Band / Diagonal Sash
        public int bodyPatternColor = 0;   // Default White
        public int sleeveColor = 0;        // Default White
        public int sleevePattern = 2;      // Default 2 Armlet Bands
        public int sleevePatternColor = 3; // Default Blue
        public int capColor = 3;           // Default Blue Cap
        public int capPattern = 1;         // Default Quartered Cap
        public int capPatternColor = 0;    // Default White Cap Pattern

        public JockeySilkConfig() {
        }

        public JockeySilkConfig(int bodyColor, int bodyPattern, int bodyPatternColor,
                                int sleeveColor, int sleevePattern, int sleevePatternColor) {
            this(bodyColor, bodyPattern, bodyPatternColor, sleeveColor, sleevePattern, sleevePatternColor,
                    bodyColor, 1, bodyPatternColor);
        }

        public JockeySilkConfig(int bodyColor, int bodyPattern, int bodyPatternColor,
                                int sleeveColor, int sleevePattern, int sleevePatternColor,
                                int capColor, int capPattern, int capPatternColor) {
            this.bodyColor = Mth.clamp(bodyColor, 0, COLOR_NAMES.length - 1);
            this.bodyPattern = Mth.clamp(bodyPattern, 0, BODY_PATTERN_NAMES.length - 1);
            this.bodyPatternColor = Mth.clamp(bodyPatternColor, 0, COLOR_NAMES.length - 1);
            this.sleeveColor = Mth.clamp(sleeveColor, 0, COLOR_NAMES.length - 1);
            this.sleevePattern = Mth.clamp(sleevePattern, 0, SLEEVE_PATTERN_NAMES.length - 1);
            this.sleevePatternColor = Mth.clamp(sleevePatternColor, 0, COLOR_NAMES.length - 1);
            this.capColor = Mth.clamp(capColor, 0, COLOR_NAMES.length - 1);
            this.capPattern = Mth.clamp(capPattern, 0, CAP_PATTERN_NAMES.length - 1);
            this.capPatternColor = Mth.clamp(capPatternColor, 0, COLOR_NAMES.length - 1);
        }

        public JockeySilkConfig copy() {
            return new JockeySilkConfig(bodyColor, bodyPattern, bodyPatternColor,
                    sleeveColor, sleevePattern, sleevePatternColor,
                    capColor, capPattern, capPatternColor);
        }

        public String toCompactCode() {
            return bodyColor + "," + bodyPattern + "," + bodyPatternColor + ","
                    + sleeveColor + "," + sleevePattern + "," + sleevePatternColor + ","
                    + capColor + "," + capPattern + "," + capPatternColor;
        }

        public static JockeySilkConfig fromCompactCode(String code) {
            JockeySilkConfig cfg = new JockeySilkConfig();
            if (code == null || code.isBlank()) return cfg;
            String[] p = code.trim().split(",");
            if (p.length >= 6) {
                try {
                    cfg.bodyColor = Mth.clamp(Integer.parseInt(p[0].trim()), 0, COLOR_NAMES.length - 1);
                    cfg.bodyPattern = Mth.clamp(Integer.parseInt(p[1].trim()), 0, BODY_PATTERN_NAMES.length - 1);
                    cfg.bodyPatternColor = Mth.clamp(Integer.parseInt(p[2].trim()), 0, COLOR_NAMES.length - 1);
                    cfg.sleeveColor = Mth.clamp(Integer.parseInt(p[3].trim()), 0, COLOR_NAMES.length - 1);
                    cfg.sleevePattern = Mth.clamp(Integer.parseInt(p[4].trim()), 0, SLEEVE_PATTERN_NAMES.length - 1);
                    cfg.sleevePatternColor = Mth.clamp(Integer.parseInt(p[5].trim()), 0, COLOR_NAMES.length - 1);
                    if (p.length >= 9) {
                        cfg.capColor = Mth.clamp(Integer.parseInt(p[6].trim()), 0, COLOR_NAMES.length - 1);
                        cfg.capPattern = Mth.clamp(Integer.parseInt(p[7].trim()), 0, CAP_PATTERN_NAMES.length - 1);
                        cfg.capPatternColor = Mth.clamp(Integer.parseInt(p[8].trim()), 0, COLOR_NAMES.length - 1);
                    } else {
                        cfg.capColor = cfg.bodyColor;
                        cfg.capPattern = 0;
                        cfg.capPatternColor = cfg.bodyPatternColor;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return cfg;
        }

        public boolean matches(JockeySilkConfig other) {
            if (other == null) return false;
            return this.bodyColor == other.bodyColor
                    && this.bodyPattern == other.bodyPattern
                    && (this.bodyPattern == 0 || this.bodyPatternColor == other.bodyPatternColor)
                    && this.sleeveColor == other.sleeveColor
                    && this.sleevePattern == other.sleevePattern
                    && (this.sleevePattern == 0 || this.sleevePatternColor == other.sleevePatternColor)
                    && this.capColor == other.capColor
                    && this.capPattern == other.capPattern
                    && (this.capPattern == 0 || this.capPatternColor == other.capPatternColor);
        }

        public String getSummaryDescription() {
            String bodyStr = COLOR_NAMES[Mth.clamp(bodyColor, 0, COLOR_NAMES.length - 1)];
            if (bodyPattern > 0 && bodyPattern < BODY_PATTERN_NAMES.length) {
                bodyStr += " w/ " + COLOR_NAMES[Mth.clamp(bodyPatternColor, 0, COLOR_NAMES.length - 1)]
                        + " " + BODY_PATTERN_NAMES[bodyPattern];
            }
            String slvStr = COLOR_NAMES[Mth.clamp(sleeveColor, 0, COLOR_NAMES.length - 1)] + " Sleeves";
            if (sleevePattern > 0 && sleevePattern < SLEEVE_PATTERN_NAMES.length) {
                slvStr += " (" + COLOR_NAMES[Mth.clamp(sleevePatternColor, 0, COLOR_NAMES.length - 1)]
                        + " " + SLEEVE_PATTERN_NAMES[sleevePattern] + ")";
            }
            String capStr = COLOR_NAMES[Mth.clamp(capColor, 0, COLOR_NAMES.length - 1)] + " Cap";
            if (capPattern > 0 && capPattern < CAP_PATTERN_NAMES.length) {
                capStr += " (" + COLOR_NAMES[Mth.clamp(capPatternColor, 0, COLOR_NAMES.length - 1)]
                        + " " + CAP_PATTERN_NAMES[capPattern] + ")";
            }
            return bodyStr + ", " + slvStr + ", " + capStr;
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putInt("BodyColor", bodyColor);
            t.putInt("BodyPattern", bodyPattern);
            t.putInt("BodyPatternColor", bodyPatternColor);
            t.putInt("SleeveColor", sleeveColor);
            t.putInt("SleevePattern", sleevePattern);
            t.putInt("SleevePatternColor", sleevePatternColor);
            t.putInt("CapColor", capColor);
            t.putInt("CapPattern", capPattern);
            t.putInt("CapPatternColor", capPatternColor);
            return t;
        }

        public static JockeySilkConfig fromNBT(CompoundTag t) {
            if (t == null || t.isEmpty()) return new JockeySilkConfig();
            int bc = t.getInt("BodyColor");
            int bp = t.getInt("BodyPattern");
            int bpc = t.getInt("BodyPatternColor");
            int sc = t.getInt("SleeveColor");
            int sp = t.getInt("SleevePattern");
            int spc = t.getInt("SleevePatternColor");
            int cc = t.contains("CapColor") ? t.getInt("CapColor") : bc;
            int cp = t.contains("CapPattern") ? t.getInt("CapPattern") : 0;
            int cpc = t.contains("CapPatternColor") ? t.getInt("CapPatternColor") : bpc;
            return new JockeySilkConfig(bc, bp, bpc, sc, sp, spc, cc, cp, cpc);
        }
    }

    public static class HorseOwnerProfile {
        public UUID playerUUID;
        public String playerName = "";
        public String farmName = "";
        public String crownName1 = "";
        public boolean crown1Prefix = true;
        public String crownName2 = "";
        public boolean crown2Prefix = true;
        public JockeySilkConfig silkConfig = new JockeySilkConfig();
        public long registeredAtMillis = 0L;

        public String getFormattedOwnerDisplay(String fallbackPlayerName) {
            String pName = (playerName != null && !playerName.isBlank())
                    ? playerName
                    : (fallbackPlayerName != null ? fallbackPlayerName : "Owner");
            if (farmName == null || farmName.isBlank()) {
                return pName;
            }
            return farmName.trim() + " (" + pName + ")";
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            if (playerUUID != null) t.putUUID("PlayerUUID", playerUUID);
            t.putString("PlayerName", playerName != null ? playerName : "");
            t.putString("FarmName", farmName != null ? farmName : "");
            t.putString("CrownName1", crownName1 != null ? crownName1 : "");
            t.putBoolean("Crown1Prefix", crown1Prefix);
            t.putString("CrownName2", crownName2 != null ? crownName2 : "");
            t.putBoolean("Crown2Prefix", crown2Prefix);
            t.put("SilkConfig", silkConfig != null ? silkConfig.toNBT() : new JockeySilkConfig().toNBT());
            t.putLong("RegisteredAtMillis", registeredAtMillis);
            return t;
        }

        public static HorseOwnerProfile fromNBT(CompoundTag t) {
            HorseOwnerProfile p = new HorseOwnerProfile();
            if (t.hasUUID("PlayerUUID")) p.playerUUID = t.getUUID("PlayerUUID");
            p.playerName = t.getString("PlayerName");
            p.farmName = t.getString("FarmName");
            p.crownName1 = t.getString("CrownName1");
            p.crown1Prefix = !t.contains("Crown1Prefix") || t.getBoolean("Crown1Prefix");
            p.crownName2 = t.getString("CrownName2");
            p.crown2Prefix = !t.contains("Crown2Prefix") || t.getBoolean("Crown2Prefix");
            p.silkConfig = t.contains("SilkConfig", Tag.TAG_COMPOUND)
                    ? JockeySilkConfig.fromNBT(t.getCompound("SilkConfig"))
                    : new JockeySilkConfig();
            p.registeredAtMillis = t.getLong("RegisteredAtMillis");
            return p;
        }
    }

    public static class PedigreeArchiveRecord {
        public String horseName = "";
        public String gender = "Male";
        public int hereditaryPoints = 68;
        public boolean pureWhiteCoat = false;
        public String inbreedingSummary = "Outcross (No Inbreeding)";
        public List<String> geneticTraits = new ArrayList<>();
        public String[] pedigreeTree = new String[ThoroughbredData.PEDIGREE_SIZE];
        public int[] pedigreeHP = new int[ThoroughbredData.PEDIGREE_SIZE];

        public PedigreeArchiveRecord() {
            for (int i = 0; i < ThoroughbredData.PEDIGREE_SIZE; i++) {
                this.pedigreeTree[i] = "";
                this.pedigreeHP[i] = 65;
            }
        }

        public static PedigreeArchiveRecord fromThoroughbredData(ThoroughbredData d) {
            PedigreeArchiveRecord r = new PedigreeArchiveRecord();
            r.horseName = d.getName();
            r.gender = d.getGender();
            r.hereditaryPoints = d.getHereditaryPoints();
            r.pureWhiteCoat = d.isPureWhiteCoat();
            r.inbreedingSummary = d.getInbreedingSummary();
            r.geneticTraits = new ArrayList<>(d.getGeneticTraits());
            System.arraycopy(d.getPedigreeTree(), 0, r.pedigreeTree, 0, ThoroughbredData.PEDIGREE_SIZE);
            System.arraycopy(d.getPedigreeHP(), 0, r.pedigreeHP, 0, ThoroughbredData.PEDIGREE_SIZE);
            return r;
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("HorseName", horseName != null ? horseName : "");
            t.putString("Gender", gender != null ? gender : "Male");
            t.putInt("HereditaryPoints", hereditaryPoints);
            t.putBoolean("PureWhiteCoat", pureWhiteCoat);
            t.putString("InbreedingSummary", inbreedingSummary != null ? inbreedingSummary : "");
            ListTag trList = new ListTag();
            for (String tr : geneticTraits) {
                trList.add(StringTag.valueOf(tr));
            }
            t.put("GeneticTraits", trList);
            ListTag pedList = new ListTag();
            for (int i = 0; i < ThoroughbredData.PEDIGREE_SIZE; i++) {
                pedList.add(StringTag.valueOf(pedigreeTree[i] != null ? pedigreeTree[i] : ""));
            }
            t.put("PedigreeTree", pedList);
            t.putIntArray("PedigreeHP", pedigreeHP.clone());
            return t;
        }

        public static PedigreeArchiveRecord fromNBT(CompoundTag t) {
            PedigreeArchiveRecord r = new PedigreeArchiveRecord();
            r.horseName = t.getString("HorseName");
            r.gender = t.contains("Gender") ? t.getString("Gender") : "Male";
            r.hereditaryPoints = t.contains("HereditaryPoints") ? t.getInt("HereditaryPoints") : 68;
            r.pureWhiteCoat = t.getBoolean("PureWhiteCoat");
            r.inbreedingSummary = t.getString("InbreedingSummary");
            if (t.contains("GeneticTraits", Tag.TAG_LIST)) {
                ListTag trList = t.getList("GeneticTraits", Tag.TAG_STRING);
                for (int i = 0; i < trList.size(); i++) {
                    r.geneticTraits.add(trList.getString(i));
                }
            }
            if (t.contains("PedigreeTree", Tag.TAG_LIST)) {
                ListTag pedList = t.getList("PedigreeTree", Tag.TAG_STRING);
                for (int i = 0; i < Math.min(ThoroughbredData.PEDIGREE_SIZE, pedList.size()); i++) {
                    r.pedigreeTree[i] = pedList.getString(i);
                }
            }
            if (t.contains("PedigreeHP", Tag.TAG_INT_ARRAY)) {
                int[] hpArr = t.getIntArray("PedigreeHP");
                for (int i = 0; i < Math.min(ThoroughbredData.PEDIGREE_SIZE, hpArr.length); i++) {
                    r.pedigreeHP[i] = hpArr[i];
                }
            }
            return r;
        }

        public void applyToData(ThoroughbredData d) {
            if (d == null) return;
            if (this.horseName != null && !this.horseName.isEmpty()) {
                d.setName(this.horseName);
            }
            if (this.gender != null && !this.gender.isEmpty()) {
                d.setGender(this.gender);
            }
            d.setHereditaryPoints(this.hereditaryPoints);
            d.setPureWhiteCoat(this.pureWhiteCoat);
            d.clearGeneticTraits();
            if (this.geneticTraits != null) {
                for (String tr : this.geneticTraits) {
                    d.addGeneticTrait(tr);
                }
            }
            if (this.pedigreeTree != null) {
                System.arraycopy(this.pedigreeTree, 0, d.getPedigreeTree(), 0,
                        Math.min(this.pedigreeTree.length, ThoroughbredData.PEDIGREE_SIZE));
            }
            if (this.pedigreeHP != null) {
                System.arraycopy(this.pedigreeHP, 0, d.getPedigreeHP(), 0,
                        Math.min(this.pedigreeHP.length, ThoroughbredData.PEDIGREE_SIZE));
            }
        }
    }

    public static class HorseBackupRecord {
        public UUID horseUUID;
        public String horseName = "";
        public String ownerUUID = "";
        public String ownerName = "";
        public boolean locked = false;
        public boolean inRace = false;

        public double lastX = 0.0;
        public double lastY = 0.0;
        public double lastZ = 0.0;
        public String lastDimension = "minecraft:overworld";

        public boolean hasStable = false;
        public double stableX = 0.0;
        public double stableY = 0.0;
        public double stableZ = 0.0;
        public String stableDimension = "minecraft:overworld";

        public int horseVariant = 0;
        public boolean saddled = false;
        public CompoundTag thoroughbredNbt = new CompoundTag();

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            if (horseUUID != null) t.putUUID("HorseUUID", horseUUID);
            t.putString("HorseName", horseName);
            t.putString("OwnerUUID", ownerUUID);
            t.putString("OwnerName", ownerName);
            t.putBoolean("Locked", locked);
            t.putBoolean("InRace", inRace);
            t.putDouble("LastX", lastX);
            t.putDouble("LastY", lastY);
            t.putDouble("LastZ", lastZ);
            t.putString("LastDimension", lastDimension);
            t.putBoolean("HasStable", hasStable);
            t.putDouble("StableX", stableX);
            t.putDouble("StableY", stableY);
            t.putDouble("StableZ", stableZ);
            t.putString("StableDimension", stableDimension);
            t.putInt("HorseVariant", horseVariant);
            t.putBoolean("Saddled", saddled);
            t.put("ThoroughbredNbt", thoroughbredNbt.copy());
            return t;
        }

        public static HorseBackupRecord fromNBT(CompoundTag t) {
            HorseBackupRecord r = new HorseBackupRecord();
            if (t.hasUUID("HorseUUID")) r.horseUUID = t.getUUID("HorseUUID");
            r.horseName = t.getString("HorseName");
            r.ownerUUID = t.getString("OwnerUUID");
            r.ownerName = t.getString("OwnerName");
            r.locked = t.getBoolean("Locked");
            r.inRace = t.getBoolean("InRace");
            r.lastX = t.getDouble("LastX");
            r.lastY = t.getDouble("LastY");
            r.lastZ = t.getDouble("LastZ");
            r.lastDimension = t.contains("LastDimension") ? t.getString("LastDimension") : "minecraft:overworld";
            r.hasStable = t.getBoolean("HasStable");
            r.stableX = t.getDouble("StableX");
            r.stableY = t.getDouble("StableY");
            r.stableZ = t.getDouble("StableZ");
            r.stableDimension = t.contains("StableDimension") ? t.getString("StableDimension") : "minecraft:overworld";
            r.horseVariant = t.getInt("HorseVariant");
            r.saddled = t.getBoolean("Saddled");
            r.thoroughbredNbt = t.getCompound("ThoroughbredNbt").copy();
            return r;
        }
    }

    public static class DeathRecord {
        public UUID horseUUID;
        public String horseName = "";
        public String ownerUUID = "";
        public String ownerName = "";
        public String deathDate = "";
        public String deathReason = "";
        public boolean permanentlyPurged = false;
        public boolean revived = false;
        public long revivedAtMillis = 0L;

        public int horseVariant = 0;
        public boolean saddled = false;
        public CompoundTag thoroughbredNbt = new CompoundTag();

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            if (horseUUID != null) t.putUUID("HorseUUID", horseUUID);
            t.putString("HorseName", horseName);
            t.putString("OwnerUUID", ownerUUID);
            t.putString("OwnerName", ownerName);
            t.putString("DeathDate", deathDate);
            t.putString("DeathReason", deathReason);
            t.putBoolean("PermanentlyPurged", permanentlyPurged);
            t.putBoolean("Revived", revived);
            t.putLong("RevivedAtMillis", revivedAtMillis);
            t.putInt("HorseVariant", horseVariant);
            t.putBoolean("Saddled", saddled);
            t.put("ThoroughbredNbt", thoroughbredNbt.copy());
            return t;
        }

        public static DeathRecord fromNBT(CompoundTag t) {
            DeathRecord r = new DeathRecord();
            if (t.hasUUID("HorseUUID")) r.horseUUID = t.getUUID("HorseUUID");
            r.horseName = t.getString("HorseName");
            r.ownerUUID = t.getString("OwnerUUID");
            r.ownerName = t.getString("OwnerName");
            r.deathDate = t.getString("DeathDate");
            r.deathReason = t.getString("DeathReason");
            r.permanentlyPurged = t.getBoolean("PermanentlyPurged");
            r.revived = t.getBoolean("Revived");
            r.revivedAtMillis = t.getLong("RevivedAtMillis");
            if (r.revived && r.revivedAtMillis <= 0L) {
                r.revivedAtMillis = System.currentTimeMillis();
            }
            r.horseVariant = t.getInt("HorseVariant");
            r.saddled = t.getBoolean("Saddled");
            r.thoroughbredNbt = t.getCompound("ThoroughbredNbt").copy();
            return r;
        }
    }

    // =========================================================================
    // STEP 15: SCHEDULED RACE, REGISTRATION & 9-VARIANT BETTING SYSTEM
    // =========================================================================
    public static final String BET_WIN      = "Win";
    public static final String BET_PLACE    = "Place";
    public static final String BET_QUINELLA = "Quinella";
    public static final String BET_EXACTA   = "Exacta";
    public static final String BET_WIDE     = "Wide";
    public static final String BET_TRIO     = "Trio";
    public static final String BET_TRIFECTA = "Trifecta";
    public static final String BET_FIRST_4  = "First 4";
    public static final String BET_QUARTET  = "Quartet";

    public static final String[] ALL_BET_TYPES = {
            BET_WIN, BET_PLACE, BET_QUINELLA, BET_EXACTA, BET_WIDE,
            BET_TRIO, BET_TRIFECTA, BET_FIRST_4, BET_QUARTET
    };

    public static int getRequiredPicksForBetType(String betType) {
        if (betType == null) return 1;
        return switch (betType.trim().toLowerCase(Locale.ROOT)) {
            case "win", "place" -> 1;
            case "quinella", "exacta", "wide" -> 2;
            case "trio", "trifecta" -> 3;
            case "first 4", "first_4", "first4", "quartet" -> 4;
            default -> 1;
        };
    }

    public static int getMinRunnersForBetType(String betType) {
        if (betType == null) return 2;
        return switch (betType.trim().toLowerCase(Locale.ROOT)) {
            case "wide" -> 7; // Disabled on races with less than 7 horses!
            case "first 4", "first_4", "first4", "quartet" -> 6; // Disabled on races with less than 6 horses!
            case "place", "trio", "trifecta" -> 3;
            default -> 2;
        };
    }

    public static boolean isBetTypeOrderSpecific(String betType) {
        if (betType == null) return true;
        return switch (betType.trim().toLowerCase(Locale.ROOT)) {
            case "win", "exacta", "trifecta", "quartet" -> true;
            default -> false;
        };
    }

    public static String getBetTypeDescription(String betType) {
        if (betType == null) return "";
        return switch (betType.trim().toLowerCase(Locale.ROOT)) {
            case "win" -> "Pick 1 horse to finish 1st.";
            case "place" -> "Pick 1 horse to finish in the Top 3.";
            case "quinella" -> "Pick 2 horses to finish Top 2 (any order).";
            case "exacta" -> "Pick 2 horses to finish 1st & 2nd (exact order).";
            case "wide" -> "Pick 2 horses to finish Top 3 (any order, 7+ horses).";
            case "trio" -> "Pick 3 horses to finish Top 3 (any order).";
            case "trifecta" -> "Pick 3 horses to finish 1st, 2nd, 3rd (exact order).";
            case "first 4", "first_4", "first4" -> "Pick 4 horses to finish Top 4 (any order, 6+ horses).";
            case "quartet" -> "Pick 4 horses to finish 1st-4th (exact order, 6+ horses).";
            default -> "";
        };
    }

    /**
     * Scales the payout multiplier dynamically with BOTH the bet type difficulty AND the number of horses (N) in the race!
     * More horses in a race = significantly higher possible gambling payouts!
     */
    public static double calculateFieldSizeMultiplier(String betType, int numHorses) {
        int n = Math.max(2, numHorses);
        if (betType == null) return 1.8;
        return switch (betType.trim().toLowerCase(Locale.ROOT)) {
            case "win" -> Math.max(1.5, Math.round((0.85 * n) * 10.0) / 10.0);
            case "place" -> Math.max(1.2, Math.round((0.38 * n) * 10.0) / 10.0);
            case "quinella" -> {
                double combos = (n * (n - 1)) / 2.0;
                yield Math.max(2.2, Math.round((1.2 + combos * 0.45) * 10.0) / 10.0);
            }
            case "exacta" -> {
                double perms = n * (n - 1);
                yield Math.max(3.5, Math.round((1.5 + perms * 0.45) * 10.0) / 10.0);
            }
            case "wide" -> {
                double combos = (n * (n - 1)) / 6.0;
                yield Math.max(1.8, Math.round((1.1 + combos * 0.55) * 10.0) / 10.0);
            }
            case "trio" -> {
                double combos = (n * (n - 1) * Math.max(1, n - 2)) / 6.0;
                yield Math.max(3.0, Math.round((1.8 + combos * 0.38) * 10.0) / 10.0);
            }
            case "trifecta" -> {
                double perms = n * (n - 1) * Math.max(1, n - 2);
                yield Math.max(6.0, Math.round((3.0 + perms * 0.32) * 10.0) / 10.0);
            }
            case "first 4", "first_4", "first4" -> {
                double combos = (n * (n - 1) * Math.max(1, n - 2) * Math.max(1, n - 3)) / 24.0;
                yield Math.max(7.5, Math.round((4.0 + combos * 0.35) * 10.0) / 10.0);
            }
            case "quartet" -> {
                double perms = n * (n - 1) * Math.max(1, n - 2) * Math.max(1, n - 3);
                yield Math.max(20.0, Math.round((10.0 + perms * 0.18) * 10.0) / 10.0);
            }
            default -> 2.0;
        };
    }

    public static class ScheduledRace {
        public String scheduleId = "SCH-1";
        public String consoleDimension = "minecraft:overworld";
        public BlockPos consolePos = BlockPos.ZERO;
        public String baseId = "";
        public String raceId = "";
        public String raceName = "Official Stakes";
        public String grade = "Open";
        public String trackSummary = "";
        public long firstPlacePurse = 5000L;
        public long requiredEarnings = 0L;
        public int ageRequirement = 2;    // 0: 2yo Only, 1: 3yo Only, 2: 3yo+ (Default), 3: 4yo+
        public int genderRequirement = 0; // 0: Open (All Sexes), 1: Fillies & Mares Only
        public String raceSeriesTag = "";
        public String scheduledDate = "TBD";
        public String status = "OPEN"; // "OPEN" (Reg Open, Betting Closed), "CLOSED" (Reg Closed, Betting Open), "RUNNING" (Betting Locked), "FINISHED", "CANCELLED"
        public boolean registrationOpen = true;
        public boolean bettingLocked = false;

        public boolean isBettingCurrentlyOpen() {
            return !registrationOpen && !bettingLocked && "CLOSED".equalsIgnoreCase(status);
        }

        public static class RegistrationEntry {
            public UUID horseUUID;
            public String horseName = "";
            public UUID ownerUUID;
            public String ownerName = "";
            public long careerEarnings = 0L;
            public boolean qualified = true;

            public CompoundTag toNBT() {
                CompoundTag t = new CompoundTag();
                if (horseUUID != null) t.putUUID("HorseUUID", horseUUID);
                t.putString("HorseName", horseName != null ? horseName : "");
                if (ownerUUID != null) t.putUUID("OwnerUUID", ownerUUID);
                t.putString("OwnerName", ownerName != null ? ownerName : "");
                t.putLong("CareerEarnings", careerEarnings);
                t.putBoolean("Qualified", qualified);
                return t;
            }

            public static RegistrationEntry fromNBT(CompoundTag t) {
                RegistrationEntry e = new RegistrationEntry();
                if (t.hasUUID("HorseUUID")) e.horseUUID = t.getUUID("HorseUUID");
                e.horseName = t.getString("HorseName");
                if (t.hasUUID("OwnerUUID")) e.ownerUUID = t.getUUID("OwnerUUID");
                e.ownerName = t.getString("OwnerName");
                e.careerEarnings = t.getLong("CareerEarnings");
                e.qualified = t.getBoolean("Qualified");
                return e;
            }
        }

        public static class BetEntry {
            public UUID bettorUUID;
            public String bettorName = "";
            public String betType = BET_WIN;
            public List<String> picks = new ArrayList<>(); // 1 to 4 horse names
            public String horseName = ""; // Primary/formatted picks summary for compatibility
            public long amount = 0L;

            public String getFormattedPicks() {
                if (picks == null || picks.isEmpty()) return horseName;
                String sep = isBetTypeOrderSpecific(betType) ? " → " : " + ";
                return String.join(sep, picks);
            }

            public CompoundTag toNBT() {
                CompoundTag t = new CompoundTag();
                if (bettorUUID != null) t.putUUID("BettorUUID", bettorUUID);
                t.putString("BettorName", bettorName != null ? bettorName : "");
                t.putString("BetType", betType != null ? betType : BET_WIN);
                t.putString("HorseName", horseName != null ? horseName : "");
                ListTag pickList = new ListTag();
                if (picks != null) {
                    for (String p : picks) {
                        pickList.add(StringTag.valueOf(p));
                    }
                }
                t.put("Picks", pickList);
                t.putLong("Amount", amount);
                return t;
            }

            public static BetEntry fromNBT(CompoundTag t) {
                BetEntry b = new BetEntry();
                if (t.hasUUID("BettorUUID")) b.bettorUUID = t.getUUID("BettorUUID");
                b.bettorName = t.getString("BettorName");
                b.betType = t.contains("BetType") ? t.getString("BetType") : BET_WIN;
                b.horseName = t.getString("HorseName");
                b.picks = new ArrayList<>();
                if (t.contains("Picks", Tag.TAG_LIST)) {
                    ListTag pl = t.getList("Picks", Tag.TAG_STRING);
                    for (int i = 0; i < pl.size(); i++) {
                        b.picks.add(pl.getString(i));
                    }
                }
                if (b.picks.isEmpty() && !b.horseName.isEmpty()) {
                    b.picks.add(b.horseName);
                }
                b.amount = t.getLong("Amount");
                return b;
            }
        }

        public final List<RegistrationEntry> registeredHorses = new ArrayList<>();
        public final List<BetEntry> bets = new ArrayList<>();

        public long getTotalBetPool() {
            long total = 0L;
            for (BetEntry b : bets) total += b.amount;
            return total;
        }

        public long getHorseBetPool(String hName) {
            long total = 0L;
            for (BetEntry b : bets) {
                if (BET_WIN.equalsIgnoreCase(b.betType) && !b.picks.isEmpty() && b.picks.get(0).equalsIgnoreCase(hName.trim())) {
                    total += b.amount;
                } else if (b.horseName.equalsIgnoreCase(hName.trim())) {
                    total += b.amount;
                }
            }
            return total;
        }

        public String getFormattedOdds(String hName) {
            double baseMult = calculateFieldSizeMultiplier(BET_WIN, Math.max(2, registeredHorses.size()));
            long horsePool = getHorseBetPool(hName);
            long totalPool = getTotalBetPool();
            if (horsePool > 0L && totalPool > horsePool) {
                double poolOdds = (double) totalPool / horsePool;
                baseMult = Math.max(baseMult, poolOdds);
            }
            return String.format(Locale.ROOT, "%.1fx", baseMult);
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("ScheduleId", scheduleId);
            t.putString("ConsoleDimension", consoleDimension);
            t.putInt("ConsoleX", consolePos.getX());
            t.putInt("ConsoleY", consolePos.getY());
            t.putInt("ConsoleZ", consolePos.getZ());
            t.putString("BaseId", baseId);
            t.putString("RaceId", raceId);
            t.putString("RaceName", raceName);
            t.putString("Grade", grade);
            t.putString("TrackSummary", trackSummary);
            t.putLong("FirstPlacePurse", firstPlacePurse);
            t.putLong("RequiredEarnings", requiredEarnings);
            t.putInt("AgeRequirement", ageRequirement);
            t.putInt("GenderRequirement", genderRequirement);
            t.putString("SeriesTag", raceSeriesTag != null ? raceSeriesTag : "");
            t.putString("ScheduledDate", scheduledDate);
            t.putString("Status", status);
            t.putBoolean("RegistrationOpen", registrationOpen);
            t.putBoolean("BettingLocked", bettingLocked);

            ListTag regList = new ListTag();
            for (RegistrationEntry r : registeredHorses) {
                regList.add(r.toNBT());
            }
            t.put("RegisteredHorses", regList);

            ListTag betList = new ListTag();
            for (BetEntry b : bets) {
                betList.add(b.toNBT());
            }
            t.put("Bets", betList);
            return t;
        }

        public static ScheduledRace fromNBT(CompoundTag t) {
            ScheduledRace s = new ScheduledRace();
            s.scheduleId = t.getString("ScheduleId");
            s.consoleDimension = t.getString("ConsoleDimension");
            s.consolePos = new BlockPos(t.getInt("ConsoleX"), t.getInt("ConsoleY"), t.getInt("ConsoleZ"));
            s.baseId = t.getString("BaseId");
            s.raceId = t.getString("RaceId");
            s.raceName = t.getString("RaceName");
            s.grade = t.getString("Grade");
            s.trackSummary = t.getString("TrackSummary");
            s.firstPlacePurse = t.contains("FirstPlacePurse") ? t.getLong("FirstPlacePurse") : 5000L;
            s.requiredEarnings = t.contains("RequiredEarnings") ? t.getLong("RequiredEarnings") : 0L;
            s.ageRequirement = t.contains("AgeRequirement") ? Mth.clamp(t.getInt("AgeRequirement"), 0, 3) : 2;
            s.genderRequirement = t.contains("GenderRequirement") ? Mth.clamp(t.getInt("GenderRequirement"), 0, 1) : 0;
            s.raceSeriesTag = t.contains("SeriesTag") ? t.getString("SeriesTag") : "";
            s.scheduledDate = t.getString("ScheduledDate");
            s.status = t.getString("Status");
            s.registrationOpen = t.getBoolean("RegistrationOpen");
            s.bettingLocked = t.getBoolean("BettingLocked");

            ListTag regList = t.getList("RegisteredHorses", Tag.TAG_COMPOUND);
            for (int i = 0; i < regList.size(); i++) {
                s.registeredHorses.add(RegistrationEntry.fromNBT(regList.getCompound(i)));
            }

            ListTag betList = t.getList("Bets", Tag.TAG_COMPOUND);
            for (int i = 0; i < betList.size(); i++) {
                s.bets.add(BetEntry.fromNBT(betList.getCompound(i)));
            }
            return s;
        }
    }

    public StudbookSavedData() {
    }

    public static StudbookSavedData load(CompoundTag tag) {
        StudbookSavedData data = new StudbookSavedData();
        ListTag list = tag.getList("RegisteredNames", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.registeredNames.add(list.getString(i));
        }

        ListTag backupList = tag.getList("LivingHorses", Tag.TAG_COMPOUND);
        for (int i = 0; i < backupList.size(); i++) {
            HorseBackupRecord rec = HorseBackupRecord.fromNBT(backupList.getCompound(i));
            if (!rec.horseName.isEmpty()) {
                data.livingHorsesByName.put(normalizeName(rec.horseName), rec);
            }
        }

        ListTag deathsList = tag.getList("DeathRecords", Tag.TAG_COMPOUND);
        for (int i = 0; i < deathsList.size(); i++) {
            data.deathRecords.add(DeathRecord.fromNBT(deathsList.getCompound(i)));
        }

        ListTag balancesList = tag.getList("PlayerBalances", Tag.TAG_COMPOUND);
        for (int i = 0; i < balancesList.size(); i++) {
            CompoundTag bTag = balancesList.getCompound(i);
            if (bTag.hasUUID("PlayerUUID")) {
                UUID playerUuid = bTag.getUUID("PlayerUUID");
                long balance = Math.max(0L, bTag.getLong("Balance"));
                data.playerBalances.put(playerUuid, balance);
            }
        }

        ListTag consolesList = tag.getList("RacingConsoles", Tag.TAG_COMPOUND);
        for (int i = 0; i < consolesList.size(); i++) {
            RacingManager.ConsoleRaceConfig cfg = RacingManager.ConsoleRaceConfig.fromNBT(consolesList.getCompound(i));
            data.racingConsoles.put(consoleKey(cfg.dimension, cfg.consolePos), cfg);
        }

        if (tag.contains("NextScheduleNumber")) {
            data.nextScheduleNumber = tag.getInt("NextScheduleNumber");
        }
        ListTag schedulesList = tag.getList("ScheduledRaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < schedulesList.size(); i++) {
            ScheduledRace sched = ScheduledRace.fromNBT(schedulesList.getCompound(i));
            data.scheduledRaces.put(sched.scheduleId.toUpperCase(Locale.ROOT), sched);
        }

        data.timeFlowConfigured = tag.getBoolean("TimeFlowConfigured");
        data.timeFlowMode = tag.contains("TimeFlowMode") ? Mth.clamp(tag.getInt("TimeFlowMode"), 0, 3) : TIMEFLOW_1_YEAR;
        data.customEquineYearDays = tag.contains("CustomEquineYearDays") ? Math.max(1, tag.getInt("CustomEquineYearDays")) : 60;
        data.geldingAdminOnly = tag.getBoolean("GeldingAdminOnly");

        if (tag.contains("PedigreeArchive", Tag.TAG_LIST)) {
            ListTag pedArchList = tag.getList("PedigreeArchive", Tag.TAG_COMPOUND);
            for (int i = 0; i < pedArchList.size(); i++) {
                PedigreeArchiveRecord par = PedigreeArchiveRecord.fromNBT(pedArchList.getCompound(i));
                if (!par.horseName.isEmpty()) {
                    data.pedigreeArchiveByName.put(normalizeName(par.horseName), par);
                }
            }
        }

        if (tag.contains("NumberSignLinks", Tag.TAG_LIST)) {
            ListTag signLinksList = tag.getList("NumberSignLinks", Tag.TAG_COMPOUND);
            for (int i = 0; i < signLinksList.size(); i++) {
                CompoundTag sTag = signLinksList.getCompound(i);
                String key = sTag.getString("SignKey");
                if (!key.isEmpty()) {
                    ListTag gatesTag = sTag.getList("Gates", Tag.TAG_COMPOUND);
                    List<BlockPos> gates = new ArrayList<>();
                    for (int g = 0; g < gatesTag.size(); g++) {
                        CompoundTag gTag = gatesTag.getCompound(g);
                        gates.add(new BlockPos(gTag.getInt("X"), gTag.getInt("Y"), gTag.getInt("Z")));
                    }
                    data.numberSignLinks.put(key, gates);
                }
            }
        }

        if (tag.contains("OwnerRegistrationFee")) {
            data.ownerRegistrationFee = Math.max(0L, tag.getLong("OwnerRegistrationFee"));
        }
        if (tag.contains("OwnerFarmRenameFee")) {
            data.ownerFarmRenameFee = Math.max(0L, tag.getLong("OwnerFarmRenameFee"));
        }
        if (tag.contains("OwnerCrownEditFee")) {
            data.ownerCrownEditFee = Math.max(0L, tag.getLong("OwnerCrownEditFee"));
        }
        if (tag.contains("OwnerSilkEditFee")) {
            data.ownerSilkEditFee = Math.max(0L, tag.getLong("OwnerSilkEditFee"));
        }
        if (tag.contains("OwnerProfiles", Tag.TAG_LIST)) {
            ListTag profList = tag.getList("OwnerProfiles", Tag.TAG_COMPOUND);
            for (int i = 0; i < profList.size(); i++) {
                HorseOwnerProfile prof = HorseOwnerProfile.fromNBT(profList.getCompound(i));
                if (prof.playerUUID != null && !prof.farmName.isBlank()) {
                    data.ownerProfiles.put(prof.playerUUID, prof);
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String name : registeredNames) {
            list.add(StringTag.valueOf(name));
        }
        tag.put("RegisteredNames", list);

        ListTag backupList = new ListTag();
        for (HorseBackupRecord rec : livingHorsesByName.values()) {
            backupList.add(rec.toNBT());
        }
        tag.put("LivingHorses", backupList);

        ListTag deathsList = new ListTag();
        for (DeathRecord d : deathRecords) {
            deathsList.add(d.toNBT());
        }
        tag.put("DeathRecords", deathsList);

        ListTag balancesList = new ListTag();
        for (Map.Entry<UUID, Long> entry : playerBalances.entrySet()) {
            CompoundTag bTag = new CompoundTag();
            bTag.putUUID("PlayerUUID", entry.getKey());
            bTag.putLong("Balance", Math.max(0L, entry.getValue()));
            balancesList.add(bTag);
        }
        tag.put("PlayerBalances", balancesList);

        ListTag consolesList = new ListTag();
        for (RacingManager.ConsoleRaceConfig cfg : racingConsoles.values()) {
            consolesList.add(cfg.toNBT());
        }
        tag.put("RacingConsoles", consolesList);

        tag.putInt("NextScheduleNumber", nextScheduleNumber);
        ListTag schedulesList = new ListTag();
        for (ScheduledRace sched : scheduledRaces.values()) {
            schedulesList.add(sched.toNBT());
        }
        tag.put("ScheduledRaces", schedulesList);

        tag.putBoolean("TimeFlowConfigured", timeFlowConfigured);
        tag.putInt("TimeFlowMode", timeFlowMode);
        tag.putInt("CustomEquineYearDays", customEquineYearDays);
        tag.putBoolean("GeldingAdminOnly", geldingAdminOnly);

        ListTag pedArchList = new ListTag();
        for (PedigreeArchiveRecord par : pedigreeArchiveByName.values()) {
            pedArchList.add(par.toNBT());
        }
        tag.put("PedigreeArchive", pedArchList);

        ListTag signLinksList = new ListTag();
        for (Map.Entry<String, List<BlockPos>> entry : numberSignLinks.entrySet()) {
            CompoundTag sTag = new CompoundTag();
            sTag.putString("SignKey", entry.getKey());
            ListTag gatesTag = new ListTag();
            for (BlockPos gp : entry.getValue()) {
                CompoundTag gTag = new CompoundTag();
                gTag.putInt("X", gp.getX());
                gTag.putInt("Y", gp.getY());
                gTag.putInt("Z", gp.getZ());
                gatesTag.add(gTag);
            }
            sTag.put("Gates", gatesTag);
            signLinksList.add(sTag);
        }
        tag.put("NumberSignLinks", signLinksList);

        tag.putLong("OwnerRegistrationFee", ownerRegistrationFee);
        tag.putLong("OwnerFarmRenameFee", ownerFarmRenameFee);
        tag.putLong("OwnerCrownEditFee", ownerCrownEditFee);
        tag.putLong("OwnerSilkEditFee", ownerSilkEditFee);
        ListTag profList = new ListTag();
        for (HorseOwnerProfile prof : ownerProfiles.values()) {
            profList.add(prof.toNBT());
        }
        tag.put("OwnerProfiles", profList);

        return tag;
    }

    public static StudbookSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                StudbookSavedData::load,
                StudbookSavedData::new,
                DATA_NAME
        );
    }

    // =========================================================================
    // RACING CONSOLE PERSISTENCE & GLOBAL RACE RECORD ERASURE
    // =========================================================================
    private static String consoleKey(String dimension, BlockPos pos) {
        return (dimension != null ? dimension : "minecraft:overworld") + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public RacingManager.ConsoleRaceConfig getOrCreateRacingConsole(String dimension, BlockPos pos) {
        String key = consoleKey(dimension, pos);
        RacingManager.ConsoleRaceConfig cfg = racingConsoles.get(key);
        if (cfg == null) {
            cfg = new RacingManager.ConsoleRaceConfig();
            cfg.dimension = dimension != null ? dimension : "minecraft:overworld";
            cfg.consolePos = pos.immutable();
            cfg.racecourseName = "Racecourse";
            racingConsoles.put(key, cfg);
            this.setDirty();
        }
        return cfg;
    }

    public void removeRacingConsole(String dimension, BlockPos pos) {
        if (racingConsoles.remove(consoleKey(dimension, pos)) != null) {
            this.setDirty();
        }
    }

    public Collection<RacingManager.ConsoleRaceConfig> getAllRacingConsoles() {
        return racingConsoles.values();
    }

    public int eraseRaceRecordsGlobally(MinecraftServer server, String deletedRaceId, String deletedRaceName) {
        int horsesUpdated = 0;

        if (server != null) {
            for (ServerLevel lvl : server.getAllLevels()) {
                for (Entity entity : lvl.getAllEntities()) {
                    if (entity instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                        ThoroughbredData d = ThoroughbredData.fromHorse(horse);
                        if (d.removeCareerRecordsByRace(deletedRaceId, deletedRaceName)) {
                            d.saveToHorse(horse);
                            horsesUpdated++;
                        }
                    }
                }
            }
        }

        boolean backupChanged = false;
        for (HorseBackupRecord rec : livingHorsesByName.values()) {
            if (rec.thoroughbredNbt != null && !rec.thoroughbredNbt.isEmpty()) {
                ThoroughbredData d = new ThoroughbredData();
                d.loadFromNBT(rec.thoroughbredNbt);
                if (d.removeCareerRecordsByRace(deletedRaceId, deletedRaceName)) {
                    rec.thoroughbredNbt = d.toNBT();
                    backupChanged = true;
                }
            }
        }

        for (DeathRecord dRec : deathRecords) {
            if (dRec.thoroughbredNbt != null && !dRec.thoroughbredNbt.isEmpty()) {
                ThoroughbredData d = new ThoroughbredData();
                d.loadFromNBT(dRec.thoroughbredNbt);
                if (d.removeCareerRecordsByRace(deletedRaceId, deletedRaceName)) {
                    dRec.thoroughbredNbt = d.toNBT();
                    backupChanged = true;
                }
            }
        }

        if (backupChanged) {
            this.setDirty();
        }
        return horsesUpdated;
    }

    // =========================================================================
    // STEP 15: RACING SCHEDULE & 9-VARIANT BETTING MANAGEMENT METHODS
    // =========================================================================
    public ScheduledRace createScheduledRace(String dim, BlockPos consolePos, String baseId, String raceId,
                                             String raceName, String grade, String trackSummary,
                                             long purse, long requiredEarnings, String dateStr) {
        return createScheduledRace(dim, consolePos, baseId, raceId, raceName, grade, trackSummary, purse, requiredEarnings, dateStr, 0, 0, "");
    }

    public ScheduledRace createScheduledRace(String dim, BlockPos consolePos, String baseId, String raceId,
                                             String raceName, String grade, String trackSummary,
                                             long purse, long requiredEarnings, String dateStr,
                                             int ageRequirement, int genderRequirement, String raceSeriesTag) {
        ScheduledRace sched = new ScheduledRace();
        sched.scheduleId = "SCH-" + (nextScheduleNumber++);
        sched.consoleDimension = dim;
        sched.consolePos = consolePos.immutable();
        sched.baseId = baseId;
        sched.raceId = raceId;
        sched.raceName = raceName;
        sched.grade = grade;
        sched.trackSummary = trackSummary;
        sched.firstPlacePurse = Math.max(0L, purse);
        sched.requiredEarnings = Math.max(0L, requiredEarnings);
        sched.ageRequirement = Mth.clamp(ageRequirement, 0, 3);
        sched.genderRequirement = Mth.clamp(genderRequirement, 0, 1);
        sched.raceSeriesTag = raceSeriesTag != null ? raceSeriesTag : "";
        sched.scheduledDate = (dateStr != null && !dateStr.isBlank()) ? dateStr.trim() : "TBD";
        sched.status = "OPEN"; // Registration Open; Betting is NOT open until registrations close!
        sched.registrationOpen = true;
        sched.bettingLocked = false;

        scheduledRaces.put(sched.scheduleId.toUpperCase(Locale.ROOT), sched);
        this.setDirty();
        return sched;
    }

    public ScheduledRace getScheduledRace(String scheduleId) {
        if (scheduleId == null) return null;
        return scheduledRaces.get(scheduleId.trim().toUpperCase(Locale.ROOT));
    }

    public ScheduledRace findScheduledRaceByRaceId(BlockPos consolePos, String raceId) {
        if (consolePos == null || raceId == null) return null;
        for (ScheduledRace s : scheduledRaces.values()) {
            if (s.consolePos.equals(consolePos) && s.raceId.equals(raceId)
                    && !"FINISHED".equalsIgnoreCase(s.status) && !"CANCELLED".equalsIgnoreCase(s.status)) {
                return s;
            }
        }
        return null;
    }

    public Collection<ScheduledRace> getAllScheduledRaces() {
        return scheduledRaces.values();
    }

    public boolean openScheduleRegistration(String scheduleId) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null || "FINISHED".equalsIgnoreCase(s.status) || "CANCELLED".equalsIgnoreCase(s.status) || "RUNNING".equalsIgnoreCase(s.status)) {
            return false;
        }
        s.registrationOpen = true;
        s.status = "OPEN";
        this.setDirty();
        return true;
    }

    public boolean closeScheduleRegistration(String scheduleId) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null || "FINISHED".equalsIgnoreCase(s.status) || "CANCELLED".equalsIgnoreCase(s.status) || "RUNNING".equalsIgnoreCase(s.status)) {
            return false;
        }
        s.registrationOpen = false;
        s.status = "CLOSED"; // Closing registration opens the betting window!
        this.setDirty();
        return true;
    }

    public static boolean isDebutOrMaidenGrade(String grade) {
        if (grade == null) return false;
        String lower = grade.trim().toLowerCase(Locale.ROOT);
        return lower.contains("debut") || lower.contains("maiden");
    }

    public String registerHorseForSchedule(String scheduleId, Horse horse, ThoroughbredData data, Player owner) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null) {
            return "Schedule not found!";
        }
        if (!s.registrationOpen) {
            return "Registration is CLOSED for this race!";
        }
        if ("ARMED".equalsIgnoreCase(s.status) || "RUNNING".equalsIgnoreCase(s.status) || s.bettingLocked) {
            return "This race has already been started!";
        }
        if (data.isUnderTwoEquineYears()) {
            String stage = data.isFoal() ? "a Foal (under 1 Equine Year old)" : "a Yearling (1 Equine Year old)";
            return data.getName() + " is still " + stage + " and cannot enter races until it reaches 2 Equine Years old!";
        }
        if (data.isRetired()) {
            return data.getName() + " is retired for breeding (Stud/Broodmare) and can no longer enter official races!";
        }
        if (data.hasPoorPrognosis() || data.hasActiveInjury() || data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
            return data.getName() + " is currently lame/injured (" + (data.hasActiveInjury() ? data.getActiveInjury() : "Lameness") + ") and cannot be entered into a race!";
        }

        // Debut / Maiden Paradox Rule: A horse that has already won a Debut/Maiden (or any official race) cannot enter Debut/Maiden races!
        if (isDebutOrMaidenGrade(s.grade) && data.hasWonDebutOrMaiden()) {
            return data.getName() + " has already won a race (graduated from Debut/Maiden) and cannot enter Debut/Maiden races!";
        }

        int equineAge = data.getEquineAgeYears(this);
        if (!RacingManager.matchesAgeRequirement(equineAge, s.ageRequirement)) {
            return data.getName() + " (" + equineAge + "yo) " + ThoroughbredData.tr("does not meet the age requirement", "不符合此賽事的年齡條件", "不符合此赛事的年龄条件", "は馬齢条件を満たしていません")
                    + " (" + RacingManager.getAgeRequirementLabel(s.ageRequirement) + ")!";
        }
        if (!RacingManager.matchesGenderRequirement(data.getGender(), s.genderRequirement)) {
            return data.getName() + " (" + ThoroughbredData.localizeGender(data.getGender()) + ") "
                    + ThoroughbredData.tr("is barred by gender requirement (Fillies & Mares Only)!", "受限於性別限制無法參賽（僅限雌馬/牝馬）！", "受限于性别限制无法参赛（仅限雌马/牝马）！", "は性別条件により出走できません（牝馬限定）！");
        }

        for (ScheduledRace.RegistrationEntry e : s.registeredHorses) {
            if (e.horseName.equalsIgnoreCase(data.getName()) || (e.horseUUID != null && e.horseUUID.equals(horse.getUUID()))) {
                return data.getName() + " is already registered in this race!";
            }
        }

        ScheduledRace.RegistrationEntry reg = new ScheduledRace.RegistrationEntry();
        reg.horseUUID = horse.getUUID();
        reg.horseName = data.getName();
        reg.ownerUUID = owner.getUUID();
        reg.ownerName = getFormattedOwnerName(owner.getUUID(), owner.getName().getString());
        reg.careerEarnings = data.getTotalEarnings();
        reg.qualified = (s.requiredEarnings <= 0L) || (reg.careerEarnings >= s.requiredEarnings);

        s.registeredHorses.add(reg);
        this.setDirty();
        return reg.qualified ? "QUALIFIED" : "UNQUALIFIED";
    }

    public boolean unregisterHorseFromSchedule(String scheduleId, UUID horseUUID) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null || !s.registrationOpen) return false;
        boolean removed = s.registeredHorses.removeIf(e -> e.horseUUID != null && e.horseUUID.equals(horseUUID));
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public boolean adminRemoveHorseFromSchedule(String scheduleId, String horseName) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null || horseName == null) return false;
        boolean removed = s.registeredHorses.removeIf(e -> e.horseName.equalsIgnoreCase(horseName.trim()));
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public String placeBet(String scheduleId, ServerPlayer bettor, String horseName, long amount) {
        return placeVariantBet(scheduleId, bettor, BET_WIN, List.of(horseName), amount);
    }

    public String placeVariantBet(String scheduleId, ServerPlayer bettor, String betType, List<String> rawPicks, long amount) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null) {
            return "Schedule not found!";
        }
        if (s.registrationOpen) {
            return "Betting is not open yet! Registrations must be closed and horses approved by an admin first.";
        }
        if (s.bettingLocked || "ARMED".equalsIgnoreCase(s.status) || "RUNNING".equalsIgnoreCase(s.status) || "FINISHED".equalsIgnoreCase(s.status) || "CANCELLED".equalsIgnoreCase(s.status)) {
            return "Betting is LOCKED for this race!";
        }
        if (amount < 10L) {
            return "Minimum bet is $10 (1 Iron Coin)!";
        }
        if (getBalance(bettor.getUUID()) < amount) {
            return "Insufficient funds! You do not have $" + amount + " in your account.";
        }

        int runnerCount = s.registeredHorses.size();
        int minRunners = getMinRunnersForBetType(betType);
        if (runnerCount < minRunners) {
            return betType + " bets require at least " + minRunners + " horses in the race (currently " + runnerCount + ")!";
        }

        int reqPicks = getRequiredPicksForBetType(betType);
        if (rawPicks == null || rawPicks.size() != reqPicks) {
            return betType + " requires selecting exactly " + reqPicks + " horse(s)!";
        }

        List<String> canonicalPicks = new ArrayList<>();
        Set<String> seenLower = new HashSet<>();
        for (String pick : rawPicks) {
            if (pick == null || pick.isBlank()) {
                return "Invalid horse selection!";
            }
            String matchedName = null;
            for (ScheduledRace.RegistrationEntry reg : s.registeredHorses) {
                if (reg.horseName.equalsIgnoreCase(pick.trim())) {
                    matchedName = reg.horseName;
                    break;
                }
            }
            if (matchedName == null) {
                return "Horse '" + pick + "' is not an approved runner in this race!";
            }
            if (!seenLower.add(matchedName.toLowerCase(Locale.ROOT))) {
                return "You cannot select the same horse (" + matchedName + ") twice on the same ticket!";
            }
            canonicalPicks.add(matchedName);
        }

        if (!tryDeductBalance(bettor.getUUID(), amount)) {
            return "Transaction failed!";
        }

        ScheduledRace.BetEntry bet = new ScheduledRace.BetEntry();
        bet.bettorUUID = bettor.getUUID();
        bet.bettorName = bettor.getName().getString();
        bet.betType = betType;
        bet.picks = canonicalPicks;
        bet.horseName = bet.getFormattedPicks();
        bet.amount = amount;
        s.bets.add(bet);
        this.setDirty();
        return "SUCCESS";
    }

    public void lockBettingForRace(BlockPos consolePos, String raceId) {
        ScheduledRace s = findScheduledRaceByRaceId(consolePos, raceId);
        if (s != null) {
            s.bettingLocked = true;
            s.registrationOpen = false;
            s.status = "RUNNING";
            this.setDirty();
        }
    }

    public boolean cancelSchedule(String scheduleId) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null) return false;
        s.status = "CANCELLED";
        s.registrationOpen = false;
        s.bettingLocked = true;

        // Refund 100% of bets
        for (ScheduledRace.BetEntry b : s.bets) {
            if (b.bettorUUID != null && b.amount > 0L) {
                addBalance(b.bettorUUID, b.amount);
            }
        }
        this.setDirty();
        return true;
    }

    public boolean removeSchedule(String scheduleId) {
        ScheduledRace s = scheduledRaces.remove(scheduleId.trim().toUpperCase(Locale.ROOT));
        if (s != null) {
            if (!"FINISHED".equalsIgnoreCase(s.status) && !"CANCELLED".equalsIgnoreCase(s.status)) {
                for (ScheduledRace.BetEntry b : s.bets) {
                    if (b.bettorUUID != null && b.amount > 0L) {
                        addBalance(b.bettorUUID, b.amount);
                    }
                }
            }
            this.setDirty();
            return true;
        }
        return false;
    }

    public static boolean isBetTicketWinner(ScheduledRace.BetEntry bet, List<String> finishOrderNames) {
        if (bet == null || bet.picks == null || bet.picks.isEmpty() || finishOrderNames == null || finishOrderNames.isEmpty()) {
            return false;
        }
        List<String> picks = bet.picks;
        String type = (bet.betType != null ? bet.betType.trim().toLowerCase(Locale.ROOT) : "win");

        return switch (type) {
            case "win" -> finishOrderNames.size() >= 1 && finishOrderNames.get(0).equalsIgnoreCase(picks.get(0));
            case "place" -> {
                int limit = Math.min(3, finishOrderNames.size());
                for (int i = 0; i < limit; i++) {
                    if (finishOrderNames.get(i).equalsIgnoreCase(picks.get(0))) yield true;
                }
                yield false;
            }
            case "quinella" -> {
                if (finishOrderNames.size() < 2 || picks.size() < 2) yield false;
                yield containsAllIgnoreCase(finishOrderNames.subList(0, 2), picks.subList(0, 2));
            }
            case "exacta" -> {
                if (finishOrderNames.size() < 2 || picks.size() < 2) yield false;
                yield finishOrderNames.get(0).equalsIgnoreCase(picks.get(0))
                        && finishOrderNames.get(1).equalsIgnoreCase(picks.get(1));
            }
            case "wide" -> {
                if (finishOrderNames.size() < 3 || picks.size() < 2) yield false;
                yield containsAllIgnoreCase(finishOrderNames.subList(0, 3), picks.subList(0, 2));
            }
            case "trio" -> {
                if (finishOrderNames.size() < 3 || picks.size() < 3) yield false;
                yield containsAllIgnoreCase(finishOrderNames.subList(0, 3), picks.subList(0, 3));
            }
            case "trifecta" -> {
                if (finishOrderNames.size() < 3 || picks.size() < 3) yield false;
                yield finishOrderNames.get(0).equalsIgnoreCase(picks.get(0))
                        && finishOrderNames.get(1).equalsIgnoreCase(picks.get(1))
                        && finishOrderNames.get(2).equalsIgnoreCase(picks.get(2));
            }
            case "first 4", "first_4", "first4" -> {
                if (finishOrderNames.size() < 4 || picks.size() < 4) yield false;
                yield containsAllIgnoreCase(finishOrderNames.subList(0, 4), picks.subList(0, 4));
            }
            case "quartet" -> {
                if (finishOrderNames.size() < 4 || picks.size() < 4) yield false;
                yield finishOrderNames.get(0).equalsIgnoreCase(picks.get(0))
                        && finishOrderNames.get(1).equalsIgnoreCase(picks.get(1))
                        && finishOrderNames.get(2).equalsIgnoreCase(picks.get(2))
                        && finishOrderNames.get(3).equalsIgnoreCase(picks.get(3));
            }
            default -> false;
        };
    }

    private static boolean containsAllIgnoreCase(List<String> topFinishers, List<String> targetPicks) {
        for (String pick : targetPicks) {
            boolean found = false;
            for (String fin : topFinishers) {
                if (fin.equalsIgnoreCase(pick)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    public void processBettingPayouts(String scheduleId, List<String> finishOrderNames, ServerLevel level) {
        ScheduledRace s = getScheduledRace(scheduleId);
        if (s == null) return;
        s.status = "FINISHED";
        s.bettingLocked = true;
        s.registrationOpen = false;
        if (s.bets.isEmpty() || finishOrderNames == null || finishOrderNames.isEmpty()) {
            this.setDirty();
            return;
        }

        int numHorses = Math.max(finishOrderNames.size(), s.registeredHorses.size());
        long totalPool = s.getTotalBetPool();

        // Calculate losing pool to distribute as a parimutuel bonus on top of field-size odds!
        long winningBetStakes = 0L;
        for (ScheduledRace.BetEntry b : s.bets) {
            if (isBetTicketWinner(b, finishOrderNames)) {
                winningBetStakes += b.amount;
            }
        }
        long losingPool = Math.max(0L, totalPool - winningBetStakes);

        for (ScheduledRace.BetEntry b : s.bets) {
            if (isBetTicketWinner(b, finishOrderNames)) {
                double fieldMult = calculateFieldSizeMultiplier(b.betType, numHorses);
                long basePayout = Math.round(b.amount * fieldMult);
                long parimutuelBonus = (winningBetStakes > 0L)
                        ? Math.round(((double) b.amount / winningBetStakes) * losingPool)
                        : 0L;
                long finalPayout = Math.max(basePayout, b.amount + parimutuelBonus);

                if (finalPayout > 0L && b.bettorUUID != null) {
                    addBalance(b.bettorUUID, finalPayout);
                    if (level != null && level.getServer() != null) {
                        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(b.bettorUUID);
                        if (sp != null) {
                            sp.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                    "§6§l[BETTING WINNER!] §fYour §e%s §fticket (§6%s§f, §a$%,d §7@ %.1fx§f) won in §e%s§f! Payout: §a§l+$%,d §7(Balance: §a$%,d§7)!",
                                    b.betType, b.getFormattedPicks(), b.amount, fieldMult, s.raceName, finalPayout, getBalance(b.bettorUUID))));
                        }
                    }
                }
            } else {
                if (level != null && level.getServer() != null && b.bettorUUID != null) {
                    ServerPlayer sp = level.getServer().getPlayerList().getPlayer(b.bettorUUID);
                    if (sp != null) {
                        sp.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                "§7[Bet Result] Your %s ticket (%s, $%,d) did not hit in %s.",
                                b.betType, b.getFormattedPicks(), b.amount, s.raceName)));
                    }
                }
            }
        }
        this.setDirty();
    }

    // =========================================================================
    // RACING ECONOMY & BANK BALANCE METHODS
    // =========================================================================
    public long getBalance(UUID playerUUID) {
        if (playerUUID == null) return 0L;
        return Math.max(0L, playerBalances.getOrDefault(playerUUID, 0L));
    }

    public void setBalance(UUID playerUUID, long amount) {
        if (playerUUID == null) return;
        playerBalances.put(playerUUID, Math.max(0L, amount));
        this.setDirty();
    }

    public long addBalance(UUID playerUUID, long delta) {
        if (playerUUID == null) return 0L;
        long next = Math.max(0L, getBalance(playerUUID) + delta);
        setBalance(playerUUID, next);
        return next;
    }

    public boolean tryDeductBalance(UUID playerUUID, long amount) {
        if (playerUUID == null || amount < 0L) return false;
        long current = getBalance(playerUUID);
        if (current < amount) {
            return false;
        }
        setBalance(playerUUID, current - amount);
        return true;
    }

    // =========================================================================
    // STUDBOOK & HORSE BACKUP METHODS
    // =========================================================================
    public static String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isNameTaken(String rawName) {
        String normalized = normalizeName(rawName);
        if (normalized.isEmpty()) {
            return false;
        }
        return registeredNames.contains(normalized);
    }

    public boolean registerName(String rawName) {
        String normalized = normalizeName(rawName);
        if (normalized.isEmpty() || registeredNames.contains(normalized)) {
            return false;
        }
        registeredNames.add(normalized);
        this.setDirty();
        return true;
    }

    public void renameHorseRecord(String oldRawName, String newRawName) {
        String oldKey = normalizeName(oldRawName);
        String newKey = normalizeName(newRawName);
        if (!oldKey.isEmpty() && !oldKey.equals(newKey)) {
            registeredNames.remove(oldKey);
            HorseBackupRecord oldLiving = livingHorsesByName.remove(oldKey);
            if (oldLiving != null) {
                oldLiving.horseName = newRawName.trim();
                livingHorsesByName.put(newKey, oldLiving);
            }
            PedigreeArchiveRecord oldArch = pedigreeArchiveByName.remove(oldKey);
            if (oldArch != null) {
                oldArch.horseName = newRawName.trim();
                pedigreeArchiveByName.put(newKey, oldArch);
            }
        }
        if (!newKey.isEmpty()) {
            registeredNames.add(newKey);
        }
        this.setDirty();
    }

    /**
     * Generates a foal's birth name according to the active World Equine Time Flow:
     * - 1-Year Time Flow: "[Mare Name] [Year]" (e.g., "Gugugege 2027")
     * - 4-Month Time Flow: "[Mare Name] [Year] S[1..3]" (e.g., "Gugugege 2026 S2")
     * - 3-Month Time Flow: "[Mare Name] [Year] S[1..4]" (e.g., "Gugugege 2026 S2")
     * - Custom Time Flow: "[Mare Name] [Year] S[Season]" (or "[Mare Name] [Year]" if custom cycle >= 365 days)
     */
    public String generateDefaultFoalName(String rawMareName) {
        String mareName = (rawMareName == null || rawMareName.isBlank()) ? "Broodmare" : rawMareName.trim();
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        String baseFoalName;
        switch (this.timeFlowMode) {
            case TIMEFLOW_1_YEAR -> baseFoalName = mareName + " " + year;
            case TIMEFLOW_4_MONTH -> {
                int season = ((today.getMonthValue() - 1) / 4) + 1;
                baseFoalName = mareName + " " + year + " S" + season;
            }
            case TIMEFLOW_3_MONTH -> {
                int season = ((today.getMonthValue() - 1) / 3) + 1;
                baseFoalName = mareName + " " + year + " S" + season;
            }
            case TIMEFLOW_CUSTOM -> {
                int customDays = Math.max(1, this.customEquineYearDays);
                if (customDays >= 365) {
                    baseFoalName = mareName + " " + year;
                } else {
                    int season = ((today.getDayOfYear() - 1) / customDays) + 1;
                    baseFoalName = mareName + " " + year + " S" + season;
                }
            }
            default -> baseFoalName = mareName + " " + year;
        }

        String candidate = baseFoalName;
        int dupIndex = 2;
        while (isNameTaken(candidate)) {
            candidate = baseFoalName + " (" + dupIndex + ")";
            dupIndex++;
        }
        return candidate;
    }

    public void updateHorseRecord(Horse horse, ThoroughbredData data) {
        if (horse == null || data == null || !data.isRegistered() || data.getName().isEmpty()) {
            return;
        }
        String key = normalizeName(data.getName());
        HorseBackupRecord rec = livingHorsesByName.computeIfAbsent(key, k -> new HorseBackupRecord());
        rec.horseUUID = horse.getUUID();
        rec.horseName = data.getName();
        rec.ownerUUID = data.getOwnerUUID();
        rec.ownerName = data.getOwnerName();
        rec.locked = data.isLocked();
        rec.inRace = data.isInRace();
        rec.lastX = horse.getX();
        rec.lastY = horse.getY();
        rec.lastZ = horse.getZ();
        rec.lastDimension = horse.level().dimension().location().toString();
        rec.hasStable = data.hasStable();
        rec.stableX = data.getStableX();
        rec.stableY = data.getStableY();
        rec.stableZ = data.getStableZ();
        rec.stableDimension = data.getStableDimension();
        rec.horseVariant = (horse.getVariant().getId() & 255) | ((horse.getMarkings().getId() << 8) & 0xFF00);
        rec.saddled = horse.isSaddled();
        rec.thoroughbredNbt = data.toNBT();
        this.pedigreeArchiveByName.put(key, PedigreeArchiveRecord.fromThoroughbredData(data));
        this.setDirty();
    }

    public HorseBackupRecord getLivingHorseByName(String rawName) {
        return livingHorsesByName.get(normalizeName(rawName));
    }

    public Collection<HorseBackupRecord> getAllLivingHorses() {
        return livingHorsesByName.values();
    }

    public void recordHorseDeath(Horse horse, ThoroughbredData data, String reason, boolean permanentlyPurged) {
        String key = normalizeName(data.getName());
        livingHorsesByName.remove(key);

        deathRecords.removeIf(d -> d.horseUUID != null && d.horseUUID.equals(horse.getUUID()));

        DeathRecord d = new DeathRecord();
        d.horseUUID = horse.getUUID();
        d.horseName = data.getName();
        d.ownerUUID = data.getOwnerUUID();
        d.ownerName = data.hasOwner() ? data.getOwnerName() : "Unclaimed";
        d.deathDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        d.deathReason = reason != null ? reason : "Unknown";
        d.permanentlyPurged = permanentlyPurged;
        d.revived = false;
        d.revivedAtMillis = 0L;
        d.horseVariant = (horse.getVariant().getId() & 255) | ((horse.getMarkings().getId() << 8) & 0xFF00);
        d.saddled = horse.isSaddled();
        d.thoroughbredNbt = data != null ? data.toNBT() : new CompoundTag();

        deathRecords.add(d);
        if (data != null && !data.getName().isEmpty()) {
            this.pedigreeArchiveByName.put(key, PedigreeArchiveRecord.fromThoroughbredData(data));
        }
        this.setDirty();
    }

    public void cleanupExpiredRevivedRecords() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (DeathRecord d : deathRecords) {
            if (d.revived && d.revivedAtMillis <= 0L) {
                d.revivedAtMillis = now;
                changed = true;
            }
        }
        boolean removed = deathRecords.removeIf(d -> !d.permanentlyPurged && d.revived && d.revivedAtMillis > 0L && (now - d.revivedAtMillis >= 8000L));
        if (changed || removed) {
            this.setDirty();
        }
    }

    public boolean removeDeathRecordByUUID(UUID uuid) {
        if (uuid == null) return false;
        boolean removed = deathRecords.removeIf(d -> d.horseUUID != null && d.horseUUID.equals(uuid));
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public List<DeathRecord> getDeathRecords() {
        cleanupExpiredRevivedRecords();
        return deathRecords;
    }

    public DeathRecord findDeathRecordByUUID(UUID uuid) {
        cleanupExpiredRevivedRecords();
        for (int i = deathRecords.size() - 1; i >= 0; i--) {
            DeathRecord d = deathRecords.get(i);
            if (d.horseUUID != null && d.horseUUID.equals(uuid)) {
                return d;
            }
        }
        return null;
    }

    // =========================================================================
    // STEP 18: WORLD TIME FLOW SYSTEM & CALENDAR-VARIATION EQUINE YEAR ENGINE
    // =========================================================================
    public boolean isTimeFlowConfigured() {
        return this.timeFlowConfigured;
    }

    public void setTimeFlowConfiguration(int mode, int customDays) {
        this.timeFlowMode = Mth.clamp(mode, 0, 3);
        this.customEquineYearDays = Math.max(1, customDays);
        this.timeFlowConfigured = true;
        this.setDirty();
    }

    public int getTimeFlowMode() {
        return this.timeFlowMode;
    }

    public int getCustomEquineYearDays() {
        return this.customEquineYearDays;
    }

    /**
     * Returns the exact length (in real-life days) of the current Equine Year,
     * honoring real-life calendar month variations and leap years:
     * - 1-Year Cycle: 365 days (366 on leap years)
     * - 4-Month Cycle: Jan-Apr (120/121d), May-Aug (123d), Sep-Dec (122d)
     * - 3-Month Cycle: Jan-Mar (90/91d), Apr-Jun (91d), Jul-Sep (92d), Oct-Dec (92d)
     * - Custom Cycle: customEquineYearDays
     */
    public int getCurrentEquineYearDays() {
        LocalDate today = LocalDate.now();
        return switch (this.timeFlowMode) {
            case TIMEFLOW_1_YEAR -> today.lengthOfYear();
            case TIMEFLOW_4_MONTH -> {
                int m = today.getMonthValue();
                int startMonth = ((m - 1) / 4) * 4 + 1; // 1, 5, or 9
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                LocalDate end = start.plusMonths(4);
                yield (int) ChronoUnit.DAYS.between(start, end);
            }
            case TIMEFLOW_3_MONTH -> {
                int m = today.getMonthValue();
                int startMonth = ((m - 1) / 3) * 3 + 1; // 1, 4, 7, or 10
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                LocalDate end = start.plusMonths(3);
                yield (int) ChronoUnit.DAYS.between(start, end);
            }
            case TIMEFLOW_CUSTOM -> Math.max(1, this.customEquineYearDays);
            default -> today.lengthOfYear();
        };
    }

    /**
     * Returns the 1-indexed day within the current Equine Year (1 .. getCurrentEquineYearDays()).
     */
    public int getCurrentEquineDayOfYear() {
        LocalDate today = LocalDate.now();
        int totalDays = getCurrentEquineYearDays();
        return switch (this.timeFlowMode) {
            case TIMEFLOW_1_YEAR -> today.getDayOfYear();
            case TIMEFLOW_4_MONTH -> {
                int m = today.getMonthValue();
                int startMonth = ((m - 1) / 4) * 4 + 1;
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                yield Mth.clamp((int) ChronoUnit.DAYS.between(start, today) + 1, 1, totalDays);
            }
            case TIMEFLOW_3_MONTH -> {
                int m = today.getMonthValue();
                int startMonth = ((m - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                yield Mth.clamp((int) ChronoUnit.DAYS.between(start, today) + 1, 1, totalDays);
            }
            case TIMEFLOW_CUSTOM -> {
                long epochDay = today.toEpochDay();
                yield (int) (Math.floorMod(epochDay, totalDays) + 1);
            }
            default -> today.getDayOfYear();
        };
    }

    /**
     * Maps the current progress through the Equine Year to an Equine Month (1 = Jan .. 12 = Dec).
     * In the 1-Year cycle, this is the exact real-life calendar month (1..12).
     * In the 3-Month, 4-Month, and Custom cycles, this maps proportionally across the cycle's days!
     */
    public int getCurrentEquineMonth() {
        if (this.timeFlowMode == TIMEFLOW_1_YEAR) {
            return LocalDate.now().getMonthValue();
        }
        int totalDays = Math.max(1, getCurrentEquineYearDays());
        int dayOfYear = getCurrentEquineDayOfYear();
        double progress = (dayOfYear - 0.5) / totalDays;
        return Mth.clamp((int) Math.floor(progress * 12.0) + 1, 1, 12);
    }

    public static String getEquineMonthName(int month) {
        String[] names = {
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        };
        return names[Mth.clamp(month - 1, 0, 11)];
    }

    /**
     * Mares naturally go into heat from Equine April (Month 4) through Equine October (Month 10).
     */
    public boolean isCurrentlyBreedingSeasonAprilToOctober() {
        int eqMonth = getCurrentEquineMonth();
        return eqMonth >= 4 && eqMonth <= 10;
    }

    /**
     * Rolls mare gestation duration with realistic variation (87.67% to 97.26% of an Equine Year,
     * corresponding to 320-355 days in a 365-day year so some foals arrive slightly before spring!).
     */
    public long rollGestationDurationMillis(RandomSource random) {
        int eqYearDays = getCurrentEquineYearDays();
        double fraction = 0.8767 + random.nextDouble() * (0.9726 - 0.8767);
        double days = Math.max(0.5, eqYearDays * fraction);
        return Math.round(days * 86_400_000.0);
    }

    /**
     * Foals grow into trainable adults at 1.0 Equine Year old!
     */
    public long getFoalGrowthDurationMillis() {
        return (long) getCurrentEquineYearDays() * 86_400_000L;
    }

    public String getFormattedTimeFlowSummary() {
        int totalDays = getCurrentEquineYearDays();
        int curDay = getCurrentEquineDayOfYear();
        int eqMonth = getCurrentEquineMonth();
        boolean inSeason = isCurrentlyBreedingSeasonAprilToOctober();
        String modeLabel = switch (this.timeFlowMode) {
            case TIMEFLOW_1_YEAR -> "1-Year Real-Life Calendar (" + totalDays + "d)";
            case TIMEFLOW_4_MONTH -> "4-Month Seasonal Cycle (" + totalDays + "d this cycle)";
            case TIMEFLOW_3_MONTH -> "3-Month Seasonal Cycle (" + totalDays + "d this cycle)";
            case TIMEFLOW_CUSTOM -> "Custom Cycle (" + totalDays + "d per Equine Year)";
            default -> "1-Year Cycle";
        };
        return String.format(Locale.ROOT,
                "§e%s §7| Day §f%d/%d §7(Equine §b%s§7) | Heat Season (Apr-Oct): %s",
                modeLabel, curDay, totalDays, getEquineMonthName(eqMonth),
                inSeason ? "§a§lACTIVE (In Heat)" : "§7Inactive (Winter Off-Season)");
    }

    public PedigreeArchiveRecord getPedigreeRecord(String horseName) {
        if (horseName == null || horseName.isBlank()) return null;
        return this.pedigreeArchiveByName.get(normalizeName(horseName));
    }

    public void archiveHorsePedigree(ThoroughbredData data) {
        if (data == null || data.getName().isBlank()) return;
        this.pedigreeArchiveByName.put(normalizeName(data.getName()), PedigreeArchiveRecord.fromThoroughbredData(data));
        this.setDirty();
    }

    public boolean isGeldingAdminOnly() {
        return this.geldingAdminOnly;
    }

    public void setGeldingAdminOnly(boolean adminOnly) {
        this.geldingAdminOnly = adminOnly;
        this.setDirty();
    }

    // =========================================================================
    // STEP 20: STARTING GATE NUMBER SIGN -> MAIN GATE PAIRING PERSISTENCE
    // =========================================================================
    public void setNumberSignLink(String dimension, BlockPos signPos, List<BlockPos> gatePositions) {
        if (signPos == null) return;
        String key = consoleKey(dimension, signPos);
        List<BlockPos> clean = new ArrayList<>();
        if (gatePositions != null) {
            for (BlockPos p : gatePositions) {
                if (p != null && !clean.contains(p.immutable())) {
                    clean.add(p.immutable());
                }
            }
        }
        // Ensure none of these claimed Main Gates remain attached to an older Number Sign
        for (Map.Entry<String, List<BlockPos>> entry : numberSignLinks.entrySet()) {
            if (!entry.getKey().equals(key)) {
                entry.getValue().removeAll(clean);
            }
        }
        numberSignLinks.put(key, clean);
        this.setDirty();
    }

    public List<BlockPos> getLinkedGatesForSign(String dimension, BlockPos signPos) {
        if (signPos == null) return Collections.emptyList();
        List<BlockPos> list = numberSignLinks.get(consoleKey(dimension, signPos));
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    public void removeNumberSignLink(String dimension, BlockPos signPos) {
        if (signPos == null) return;
        if (numberSignLinks.remove(consoleKey(dimension, signPos)) != null) {
            this.setDirty();
        }
    }

    public void removeMainGateFromAllLinks(String dimension, BlockPos gatePos) {
        if (gatePos == null) return;
        BlockPos imm = gatePos.immutable();
        String prefix = (dimension == null ? "" : dimension) + "|";
        boolean changed = false;
        for (Map.Entry<String, List<BlockPos>> entry : numberSignLinks.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().remove(imm)) {
                changed = true;
            }
        }
        if (changed) {
            this.setDirty();
        }
    }

    public BlockPos findSignForMainGate(String dimension, BlockPos gatePos) {
        if (gatePos == null) return null;
        BlockPos imm = gatePos.immutable();
        String prefix = (dimension == null ? "" : dimension) + "|";
        for (Map.Entry<String, List<BlockPos>> entry : numberSignLinks.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().contains(imm)) {
                String coords = entry.getKey().substring(prefix.length());
                String[] parts = coords.split(",");
                if (parts.length == 3) {
                    try {
                        return new BlockPos(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    // =========================================================================
    // STEP 21: HORSE OWNER REGISTRATION, ITEMIZED FEES, JOCKEY SILKS & STUDBOOK CROP TAGS
    // =========================================================================
    public long getOwnerRegistrationFee() {
        return this.ownerRegistrationFee;
    }

    public void setOwnerRegistrationFee(long fee) {
        this.ownerRegistrationFee = Math.max(0L, fee);
        this.setDirty();
    }

    public long getOwnerFarmRenameFee() {
        return this.ownerFarmRenameFee;
    }

    public void setOwnerFarmRenameFee(long fee) {
        this.ownerFarmRenameFee = Math.max(0L, fee);
        this.setDirty();
    }

    public long getOwnerCrownEditFee() {
        return this.ownerCrownEditFee;
    }

    public void setOwnerCrownEditFee(long fee) {
        this.ownerCrownEditFee = Math.max(0L, fee);
        this.setDirty();
    }

    public long getOwnerSilkEditFee() {
        return this.ownerSilkEditFee;
    }

    public void setOwnerSilkEditFee(long fee) {
        this.ownerSilkEditFee = Math.max(0L, fee);
        this.setDirty();
    }

    public boolean isRegisteredHorseOwner(UUID playerUUID) {
        if (playerUUID == null) return false;
        HorseOwnerProfile prof = this.ownerProfiles.get(playerUUID);
        return prof != null && prof.farmName != null && !prof.farmName.isBlank();
    }

    public HorseOwnerProfile getOwnerProfile(UUID playerUUID) {
        if (playerUUID == null) return null;
        return this.ownerProfiles.get(playerUUID);
    }

    public void putOwnerProfile(HorseOwnerProfile profile) {
        if (profile == null || profile.playerUUID == null) return;
        this.ownerProfiles.put(profile.playerUUID, profile);
        this.setDirty();
    }

    public Collection<HorseOwnerProfile> getAllOwnerProfiles() {
        return this.ownerProfiles.values();
    }

    public boolean isFarmNameTakenByOther(String candidateFarmName, UUID currentPlayerUUID) {
        if (candidateFarmName == null || candidateFarmName.isBlank()) return false;
        String norm = normalizeName(candidateFarmName);
        for (HorseOwnerProfile prof : this.ownerProfiles.values()) {
            if (prof.playerUUID != null && !prof.playerUUID.equals(currentPlayerUUID)) {
                if (normalizeName(prof.farmName).equals(norm)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns "<FarmName> (<PlayerName>)" if the player is a registered Horse Owner,
     * or falls back to rawPlayerName for pre-existing claimed horses (Failsafe).
     */
    public String getFormattedOwnerName(UUID playerUUID, String rawPlayerName) {
        String cleanPlayer = (rawPlayerName != null && !rawPlayerName.isBlank()) ? rawPlayerName.trim() : "Owner";
        if (playerUUID == null) return cleanPlayer;
        HorseOwnerProfile prof = this.ownerProfiles.get(playerUUID);
        if (prof != null && prof.farmName != null && !prof.farmName.isBlank()) {
            if (prof.playerName == null || prof.playerName.isBlank()) {
                prof.playerName = cleanPlayer;
            }
            return prof.getFormattedOwnerDisplay(cleanPlayer);
        }
        return cleanPlayer;
    }

    public String getOwnerSilkCompactCode(UUID playerUUID) {
        if (playerUUID == null) return "";
        HorseOwnerProfile prof = this.ownerProfiles.get(playerUUID);
        if (prof != null && prof.silkConfig != null) {
            return prof.silkConfig.toCompactCode();
        }
        return "";
    }

    /**
     * Computes the current Studbook Crop/Season tag based on the active World Equine Time Flow:
     * - 1-Year Time Flow: "YYYY" (e.g., "2026")
     * - 4-Month Time Flow: "YYYY S1..S3" (e.g., "2026 S2")
     * - 3-Month Time Flow: "YYYY S1..S4" (e.g., "2026 S2")
     * - Custom Time Flow: "YYYY S<N>" (or "YYYY" if customEquineYearDays >= 365)
     */
    public String computeCurrentCropTag() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        return switch (this.timeFlowMode) {
            case TIMEFLOW_1_YEAR -> String.valueOf(year);
            case TIMEFLOW_4_MONTH -> {
                int season = ((today.getMonthValue() - 1) / 4) + 1;
                yield year + " S" + season;
            }
            case TIMEFLOW_3_MONTH -> {
                int season = ((today.getMonthValue() - 1) / 3) + 1;
                yield year + " S" + season;
            }
            case TIMEFLOW_CUSTOM -> {
                int customDays = Math.max(1, this.customEquineYearDays);
                if (customDays >= 365) {
                    yield String.valueOf(year);
                } else {
                    int season = ((today.getDayOfYear() - 1) / customDays) + 1;
                    yield year + " S" + season;
                }
            }
            default -> String.valueOf(year);
        };
    }

    /**
     * Cascades an owner's updated "<FarmName> (<PlayerName>)" display name and Jockey Silk code
     * across all loaded horses in all dimensions, all living horse backups, all death records, and all scheduled races!
     * Also serves as the failsafe that upgrades any pre-existing claimed horses from "PlayerName" to "FarmName (PlayerName)".
     */
    public int cascadeOwnerProfileToAllHorses(MinecraftServer server, UUID ownerUUID) {
        if (ownerUUID == null) return 0;
        HorseOwnerProfile prof = this.ownerProfiles.get(ownerUUID);
        if (prof == null) return 0;

        String uuidStr = ownerUUID.toString();
        String formattedOwner = prof.getFormattedOwnerDisplay(prof.playerName);
        String silkCode = prof.silkConfig != null ? prof.silkConfig.toCompactCode() : "";
        int updatedCount = 0;

        if (server != null) {
            for (ServerLevel lvl : server.getAllLevels()) {
                for (Entity entity : lvl.getAllEntities()) {
                    if (entity instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                        ThoroughbredData d = ThoroughbredData.fromHorse(horse);
                        if (d.isOwnedBy(ownerUUID)) {
                            d.setOwner(ownerUUID, formattedOwner);
                            d.setOwnerSilkCode(silkCode);
                            d.saveToHorse(horse);
                            updatedCount++;
                        }
                    }
                }
            }
        }

        for (HorseBackupRecord rec : this.livingHorsesByName.values()) {
            if (uuidStr.equalsIgnoreCase(rec.ownerUUID)) {
                rec.ownerName = formattedOwner;
                if (rec.thoroughbredNbt != null && !rec.thoroughbredNbt.isEmpty()) {
                    ThoroughbredData d = new ThoroughbredData();
                    d.loadFromNBT(rec.thoroughbredNbt);
                    d.setOwner(ownerUUID, formattedOwner);
                    d.setOwnerSilkCode(silkCode);
                    rec.thoroughbredNbt = d.toNBT();
                }
            }
        }

        for (DeathRecord dRec : this.deathRecords) {
            if (uuidStr.equalsIgnoreCase(dRec.ownerUUID)) {
                dRec.ownerName = formattedOwner;
                if (dRec.thoroughbredNbt != null && !dRec.thoroughbredNbt.isEmpty()) {
                    ThoroughbredData d = new ThoroughbredData();
                    d.loadFromNBT(dRec.thoroughbredNbt);
                    d.setOwner(ownerUUID, formattedOwner);
                    d.setOwnerSilkCode(silkCode);
                    dRec.thoroughbredNbt = d.toNBT();
                }
            }
        }

        for (ScheduledRace sched : this.scheduledRaces.values()) {
            for (ScheduledRace.RegistrationEntry reg : sched.registeredHorses) {
                if (ownerUUID.equals(reg.ownerUUID)) {
                    reg.ownerName = formattedOwner;
                }
            }
        }

        this.setDirty();
        return Math.max(updatedCount, countOwnedLivingHorses(ownerUUID));
    }

    public int countOwnedLivingHorses(UUID ownerUUID) {
        if (ownerUUID == null) return 0;
        String target = ownerUUID.toString();
        int count = 0;
        for (HorseBackupRecord rec : this.livingHorsesByName.values()) {
            if (target.equalsIgnoreCase(rec.ownerUUID)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the baseline daily Death's Door chance (in decimal, e.g. 0.01 = 1%)
     * for the default 1-Year Time Flow (365/366 real-life days per Equine Year):
     *   - Under 25yo: 0%
     *   - 25yo: 0.005% (0.00005)
     *   - 26yo: 0.01%  (0.00010)
     *   - 27yo: 0.05%  (0.00050)
     *   - 28yo: 0.1%   (0.00100)
     *   - 29yo: 0.5%   (0.00500)
     *   - 30yo: 1%     (0.01000)
     *   - 31yo+: +1% per year above 30 (e.g. 31yo = 2%, 32yo = 3%, ..., capped at 75%)
     */
    public static double getOneYearBaselineDeathsDoorChance(int ageYears) {
        if (ageYears < 25) return 0.0;
        switch (ageYears) {
            case 25: return 0.00005; // 0.005%
            case 26: return 0.00010; // 0.01%
            case 27: return 0.05 / 100.0; // 0.05%
            case 28: return 0.10 / 100.0; // 0.1%
            case 29: return 0.50 / 100.0; // 0.5%
            case 30: return 1.00 / 100.0; // 1.0%
            default:
                return Math.min(0.75, 0.01000 + (ageYears - 30) * 0.01000);
        }
    }

    /**
     * Returns the daily Death's Door chance (in decimal, e.g. 0.02 = 2%)
     * for the 3-Month and 4-Month Time Flows:
     *   - Under 25yo: 0%
     *   - 25yo: 2%  (0.02)
     *   - 26yo: 4%  (0.04)
     *   - 27yo: 7%  (0.07)
     *   - 28yo: 12% (0.12)
     *   - 29yo: 18% (0.18)
     *   - 30yo: 25% (0.25)
     *   - 31yo+: +10% per year above 30 (e.g. 31yo = 35%, 32yo = 45%, ..., capped at 75%)
     */
    public static double getThreeFourMonthDeathsDoorChance(int ageYears) {
        if (ageYears < 25) return 0.0;
        switch (ageYears) {
            case 25: return 0.02;
            case 26: return 0.04;
            case 27: return 0.07;
            case 28: return 0.12;
            case 29: return 0.18;
            case 30: return 0.25;
            default:
                return Math.min(0.75, 0.25 + (ageYears - 30) * 0.10);
        }
    }

    /**
     * Dynamically calculates the daily real-life Death's Door probability (in decimal)
     * for the given horse age based on the world's active Equine Time Flow:
     *   - TIMEFLOW_1_YEAR: uses the 1-Year baseline table (0.005% at 25yo up to 1% at 30yo, +1%/yr at 31yo+).
     *   - TIMEFLOW_3_MONTH / TIMEFLOW_4_MONTH: keeps the 3/4-Month table (2% at 25yo up to 25% at 30yo, +10%/yr at 31yo+).
     *   - TIMEFLOW_CUSTOM: dynamically tones the probability based on customEquineYearDays:
     *       * If customDays >= 365: scales inversely from the 1-Year baseline (oneYearChance * (365.0 / customDays)).
     *       * If 90 <= customDays <= 123: uses the 3/4-Month table.
     *       * If 123 < customDays < 365: smoothly interpolates log-linearly between the 3/4-Month table (at 122d)
     *         and the 1-Year baseline table (at 365d).
     *       * If customDays < 90: scales up proportionally from the 3/4-Month table (threeFourChance * (90.0 / customDays)),
     *         capped at 75% (0.75).
     */
    public double calculateDailyDeathsDoorChance(int ageYears) {
        if (ageYears < 25) return 0.0;
        double oneYearChance = getOneYearBaselineDeathsDoorChance(ageYears);
        double threeFourChance = getThreeFourMonthDeathsDoorChance(ageYears);

        if (this.timeFlowMode == TIMEFLOW_1_YEAR) {
            return oneYearChance;
        }
        if (this.timeFlowMode == TIMEFLOW_3_MONTH || this.timeFlowMode == TIMEFLOW_4_MONTH) {
            return threeFourChance;
        }
        // TIMEFLOW_CUSTOM (or any custom day duration)
        double customDays = Math.max(1.0, (double) this.customEquineYearDays);
        if (customDays >= 365.0) {
            return Math.min(0.75, oneYearChance * (365.0 / customDays));
        } else if (customDays >= 90.0 && customDays <= 123.0) {
            return threeFourChance;
        } else if (customDays > 123.0) {
            // Smooth log-linear interpolation between 122 days (threeFourChance) and 365 days (oneYearChance)
            double t = (customDays - 122.0) / (365.0 - 122.0);
            t = Math.max(0.0, Math.min(1.0, t));
            double logStart = Math.log(Math.max(1e-8, threeFourChance));
            double logEnd = Math.log(Math.max(1e-8, oneYearChance));
            return Math.min(0.75, Math.exp(logStart + t * (logEnd - logStart)));
        } else {
            // Faster than 3-month time flow (< 90 real-life days per equine year): scale up from 90 days
            return Math.min(0.75, threeFourChance * (90.0 / customDays));
        }
    }
}