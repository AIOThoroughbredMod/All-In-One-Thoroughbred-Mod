package com.aiothoroughbred;

import com.aiothoroughbred.block.ModBlocks;
import com.aiothoroughbred.block.StartingGateDoorBlock;
import com.aiothoroughbred.item.ModItems;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Vector3f;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RacingManager {

    public static final String[] RACE_GRADES = {
            "Debut/Maiden",
            "Pre-Open",
            "Open",
            "Listed",
            "G3",
            "G2",
            "G1"
    };

    // =========================================================================
    // STEP 16: G1 BROADCAST CHOREOGRAPHY & TIMELINE STUDIO PRESETS
    // Preset format (13 fields):
    //   0: introIdx           (0..9  — Act 1 Opening FX)
    //   1: emblemIdx          (0..9  — Centerpiece Vanilla Block/Item Emblem)
    //   2: pyroIdx            (0..9  — Climax Screen & 3D Gate Pyrotechnics)
    //   3: themeIdx           (0..7  — Broadcast Color & Ribbon Theme)
    //   4: act2Idx            (0..10 — Act 2 Mid-Fanfare Bridge FX; 0 = None/Skip)
    //   5: dropIdx            (0..7  — Climax Drop & Title Entry Transition)
    //   6: trophyIdx          (0..3  — Trophy Shape: Chalice, Winged Spire, Shield, Crown)
    //   7: act1Tenths         (0..300 — Act 1 Duration in 0.1s units, default 35 = 3.5s)
    //   8: act2Tenths         (0..300 — Act 2 Duration in 0.1s units, default 25 = 2.5s)
    //   9: holdTenths         (0..150 — Post-Fanfare Lingering Hold in 0.1s units, default 20 = 2.0s)
    //  10: slamSpeedIdx       (0..3  — Title Slam Speed: Fast, Normal, Dramatic, Grand)
    //  11: showNamePreClimax  (0 = Climax Only [Hide in Act 1 & 2], 1 = Show in Act 1 & 2)
    //  12: showTrackPreClimax (0 = Climax Only [Hide in Act 1 & 2], 1 = Show in Act 1 & 2)
    //  13: climaxBadgeStyle   (0 = "★ G1 ★" [Default], 1 = "★ GRADE 1 ★")
    // =========================================================================
    public static final String DEFAULT_G1_PRESET = "0|0|0|0|0|0|0|35|25|20|1|0|0|0";

    public static final String[] G1_INTROS = {
            "Derby Ribbons & Star",
            "Twin Spotlights",
            "Meteor Starfall",
            "Royal Velvet Curtain",
            "Galloping Stampede",
            "Trumpet Brass Fanfare",
            "Sakura Wind Vortex",
            "Prismarine Ocean Wave",
            "Nether Flame Pillars",
            "Clockwork Chrono Gears"
    };

    public static final String[] G1_BRIDGES = {
            "None (Skip Act 2)",
            "Derby Ribbons & Star",
            "Twin Spotlights",
            "Meteor Starfall",
            "Royal Velvet Curtain",
            "Galloping Stampede",
            "Trumpet Brass Fanfare",
            "Sakura Wind Vortex",
            "Prismarine Ocean Wave",
            "Nether Flame Pillars",
            "Clockwork Chrono Gears"
    };

    public static final String[] G1_DROPS = {
            "Gold Flash & Slam",
            "Sonic Shockwave",
            "Lightning Strike Drop",
            "Split-Join Convergence",
            "Royal Unfurl Scroll",
            "Rising Pedestal Ascent",
            "Shatter Starburst",
            "Clean Broadcast Cut"
    };

    public static final String[] G1_EMBLEMS = {
            "Golden Trophy & Laurels",
            "Nether Star Crest",
            "Royal Beacon Crown",
            "Imperial Dragon Crest",
            "Heart of the Sea",
            "Sakura Wreath",
            "Emperor's Trident",
            "Phoenix Blaze Crown",
            "Amethyst Starlight",
            "Enchanted Golden Crest"
    };

    public static final String[] G1_TROPHIES = {
            "Derby Chalice",
            "Winged Spire",
            "Herald Shield",
            "Celestial Crown"
    };

    public static final String[] G1_PYROS = {
            "Pyro Fountains & Confetti",
            "Sakura Petal Storm",
            "Thunder & Lightning",
            "Golden Stardust",
            "Grand Firework Finale",
            "Emerald Totem Cascade",
            "Soul Flame Aura",
            "Blizzard Diamond Dust",
            "Crimson Dragon Breath",
            "Minimal (Clean)"
    };

    public static final String[] G1_THEMES = {
            "Derby Gold & Crimson",
            "Royal Blue & Gold",
            "Sakura Pink & Gold",
            "Emerald Turf & Gold",
            "Imperial Purple & Gold",
            "Obsidian & Diamond",
            "Solar Flame & Gold",
            "Platinum & Navy"
    };

    public static final String[] G1_SLAM_SPEEDS = {
            "Fast (0.25s)",
            "Normal (0.45s)",
            "Dramatic (0.80s)",
            "Grand (1.20s)"
    };

    public static int[] parseG1Preset(String preset) {
        int[] res = new int[]{0, 0, 0, 0, 0, 0, 0, 35, 25, 20, 1, 0, 0, 0};
        if (preset == null || preset.isBlank()) return res;
        String[] parts = preset.split("\\|");
        for (int i = 0; i < Math.min(res.length, parts.length); i++) {
            try {
                res[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        res[0] = res[0] % G1_INTROS.length;
        res[1] = res[1] % G1_EMBLEMS.length;
        res[2] = res[2] % G1_PYROS.length;
        res[3] = res[3] % G1_THEMES.length;
        res[4] = res[4] % G1_BRIDGES.length;
        res[5] = res[5] % G1_DROPS.length;
        res[6] = res[6] % G1_TROPHIES.length;
        res[7] = Mth.clamp(res[7], 0, 300);
        res[8] = Mth.clamp(res[8], 0, 300);
        res[9] = Mth.clamp(res[9], 0, 150);
        res[10] = res[10] % G1_SLAM_SPEEDS.length;
        res[11] = res[11] > 0 ? 1 : 0;
        res[12] = res[12] > 0 ? 1 : 0;
        res[13] = res[13] > 0 ? 1 : 0;
        return res;
    }

    // 5.0 seconds (100 server ticks) delay after gates burst open before the Official Race HUD appears
    // and automatic running strategy evaluation begins!
    public static final long HUD_AND_STRATEGY_DELAY_TICKS = 100L;

    public static long calculatePlacementEarnings(int placement, long firstPlacePurse) {
        if (firstPlacePurse <= 0L) return 0L;
        return switch (placement) {
            case 1 -> firstPlacePurse;
            case 2 -> Math.round(firstPlacePurse * 0.40);
            case 3 -> Math.round(firstPlacePurse * 0.25);
            case 4 -> Math.round(firstPlacePurse * 0.15);
            case 5 -> Math.round(firstPlacePurse * 0.10);
            default -> 0L;
        };
    }

    // =========================================================================
    // 1. PERSISTENT COURSE, CHECKPOINT LINE & RACE DATA STRUCTURES
    // =========================================================================
    public static class CheckpointLine {
        public BlockPos startPos = BlockPos.ZERO;
        public BlockPos endPos = BlockPos.ZERO;

        public CheckpointLine() {
        }

        public CheckpointLine(BlockPos startPos, BlockPos endPos) {
            this.startPos = startPos != null ? startPos.immutable() : BlockPos.ZERO;
            this.endPos = endPos != null ? endPos.immutable() : this.startPos;
        }

        public Vec3 getMidpoint() {
            return new Vec3(
                    (startPos.getX() + endPos.getX()) * 0.5 + 0.5,
                    (startPos.getY() + endPos.getY()) * 0.5,
                    (startPos.getZ() + endPos.getZ()) * 0.5 + 0.5
            );
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.put("Start", writeBlockPos(startPos));
            t.put("End", writeBlockPos(endPos));
            return t;
        }

        public static CheckpointLine fromNBT(CompoundTag t) {
            if (t.contains("Start") && t.contains("End")) {
                return new CheckpointLine(readBlockPos(t.getCompound("Start")), readBlockPos(t.getCompound("End")));
            }
            BlockPos single = readBlockPos(t);
            return new CheckpointLine(single, single);
        }
    }

    public static class RaceDefinition {
        public String raceId = UUID.randomUUID().toString();
        public String raceName = "New Stakes";
        public String grade = "G2";
        public String fanfareFile = "Default";
        public int fanfareVolume = 50; // 5% to 100%, defaults to a comfortable 50%
        public long firstPlacePurse = 5000L;
        public long requiredEarnings = 0L;
        public int ageRequirement = 2;    // 0: 2yo Only, 1: 3yo Only, 2: 3yo+ (Default), 3: 4yo+
        public int genderRequirement = 0; // 0: Open (All Sexes), 1: Fillies & Mares Only
        public String raceSeriesTag = ""; // Default presets: "Classic Triple Crown", "Triple Tiara", "Grand Prix", "Dirt Triple Crown", "Sprint Series", or custom
        public String raceMusicFile = "None";
        public int raceMusicVolume = 50;
        public String spurtMusicFile = "None";
        public int spurtMusicVolume = 50;
        public String g1EffectPreset = DEFAULT_G1_PRESET;

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("RaceId", raceId);
            t.putString("RaceName", raceName);
            t.putString("Grade", grade);
            t.putString("FanfareFile", fanfareFile != null ? fanfareFile : "Default");
            t.putInt("FanfareVolume", Mth.clamp(fanfareVolume, 5, 100));
            t.putLong("FirstPlacePurse", Math.max(0L, firstPlacePurse));
            t.putLong("RequiredEarnings", Math.max(0L, requiredEarnings));
            t.putInt("AgeRequirement", ageRequirement);
            t.putInt("GenderRequirement", genderRequirement);
            t.putString("SeriesTag", raceSeriesTag != null ? raceSeriesTag : "");
            t.putString("RaceMusicFile", raceMusicFile != null ? raceMusicFile : "None");
            t.putInt("RaceMusicVolume", Mth.clamp(raceMusicVolume, 5, 100));
            t.putString("SpurtMusicFile", spurtMusicFile != null ? spurtMusicFile : "None");
            t.putInt("SpurtMusicVolume", Mth.clamp(spurtMusicVolume, 5, 100));
            t.putString("G1EffectPreset", g1EffectPreset != null ? g1EffectPreset : DEFAULT_G1_PRESET);
            return t;
        }

        public static RaceDefinition fromNBT(CompoundTag t) {
            RaceDefinition r = new RaceDefinition();
            r.raceId = t.contains("RaceId") ? t.getString("RaceId") : UUID.randomUUID().toString();
            r.raceName = t.getString("RaceName");
            r.grade = t.contains("Grade") ? t.getString("Grade") : "Open";
            r.fanfareFile = t.contains("FanfareFile") ? t.getString("FanfareFile") : "Default";
            r.fanfareVolume = t.contains("FanfareVolume") ? Mth.clamp(t.getInt("FanfareVolume"), 5, 100) : 50;
            r.firstPlacePurse = t.contains("FirstPlacePurse") ? t.getLong("FirstPlacePurse") : 5000L;
            r.requiredEarnings = t.contains("RequiredEarnings") ? t.getLong("RequiredEarnings") : 0L;
            r.ageRequirement = t.contains("AgeRequirement") ? Mth.clamp(t.getInt("AgeRequirement"), 0, 3) : 2;
            r.genderRequirement = t.contains("GenderRequirement") ? Mth.clamp(t.getInt("GenderRequirement"), 0, 1) : 0;
            r.raceSeriesTag = t.contains("SeriesTag") ? t.getString("SeriesTag") : "";
            r.raceMusicFile = t.contains("RaceMusicFile") ? t.getString("RaceMusicFile") : "None";
            r.raceMusicVolume = t.contains("RaceMusicVolume") ? Mth.clamp(t.getInt("RaceMusicVolume"), 5, 100) : 50;
            r.spurtMusicFile = t.contains("SpurtMusicFile") ? t.getString("SpurtMusicFile") : "None";
            r.spurtMusicVolume = t.contains("SpurtMusicVolume") ? Mth.clamp(t.getInt("SpurtMusicVolume"), 5, 100) : 50;
            r.g1EffectPreset = t.contains("G1EffectPreset") ? t.getString("G1EffectPreset") : DEFAULT_G1_PRESET;
            return r;
        }
    }

    public static final String[] SERIES_TAG_PRESETS = {
            "None",
            "Classic Triple Crown",
            "Triple Tiara",
            "Grand Prix",
            "Dirt Triple Crown",
            "Sprint Series"
    };

    public static final String[] RACE_SERIES_TAGS = {
            "",
            "Classic Triple Crown",
            "Triple Tiara",
            "Grand Prix",
            "Dirt Triple Crown",
            "Sprint Series"
    };

    public static List<String> getAvailableSeriesTags() {
        List<String> tags = new ArrayList<>();
        tags.add("");
        for (String defTag : RACE_SERIES_TAGS) {
            if (!defTag.isEmpty() && !tags.contains(defTag)) {
                tags.add(defTag);
            }
        }
        try {
            List<File> dirs = ThoroughbredData.getTranslationDirectories();
            String defaultFileContent = "# ========================================================================\n"
                    + "# All-In-One Thoroughbred Mod — Custom Race Series Tags\n"
                    + "# ========================================================================\n"
                    + "# Add any custom series tags below (one tag per line).\n"
                    + "# You can also type custom tags directly inside the Racing Console GUI!\n"
                    + "Classic Triple Crown\n"
                    + "Triple Tiara\n"
                    + "Grand Prix\n"
                    + "Dirt Triple Crown\n"
                    + "Sprint Series\n";
            for (File dir : dirs) {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File tagFile = new File(dir, "series_tags.txt");
                if (!tagFile.exists()) {
                    Files.writeString(tagFile.toPath(), defaultFileContent, java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    List<String> lines = Files.readAllLines(tagFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : lines) {
                        if (line == null) continue;
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || "None".equalsIgnoreCase(trimmed)) {
                            continue;
                        }
                        if (ThoroughbredData.isRemovedLegacyTranslationKey(trimmed)) {
                            continue;
                        }
                        boolean exists = false;
                        for (String existing : tags) {
                            if (existing.equalsIgnoreCase(trimmed)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            tags.add(trimmed);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return tags;
    }

    public static void registerCustomSeriesTag(String customTag) {
        if (customTag == null) return;
        String clean = customTag.trim();
        if (clean.isEmpty() || "None".equalsIgnoreCase(clean) || ThoroughbredData.isRemovedLegacyTranslationKey(clean)) {
            return;
        }
        List<String> current = getAvailableSeriesTags();
        for (String existing : current) {
            if (existing.equalsIgnoreCase(clean)) {
                return;
            }
        }
        try {
            for (File dir : ThoroughbredData.getTranslationDirectories()) {
                if (!dir.exists()) dir.mkdirs();
                File tagFile = new File(dir, "series_tags.txt");
                if (tagFile.exists()) {
                    String content = Files.readString(tagFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    if (!content.endsWith("\n")) content += "\n";
                    content += clean + "\n";
                    Files.writeString(tagFile.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static String getLocalizedSeriesTag(String tag) {
        if (tag == null || tag.isBlank() || "None".equalsIgnoreCase(tag.trim())) {
            return "";
        }
        return switch (tag.trim()) {
            case "Classic Triple Crown" -> ThoroughbredData.tr("Classic Triple Crown", "經典三冠大賽", "经典三冠大赛", "クラシック三冠");
            case "Triple Tiara" -> ThoroughbredData.tr("Triple Tiara", "雌馬三冠", "雌马三冠", "牝馬三冠");
            case "Grand Prix" -> ThoroughbredData.tr("Grand Prix", "大獎賽", "大奖赛", "グランプリ");
            case "Dirt Triple Crown" -> ThoroughbredData.tr("Dirt Triple Crown", "泥地三冠", "泥地三冠", "ダート三冠");
            case "Sprint Series" -> ThoroughbredData.tr("Sprint Series", "短途系列賽", "短途系列赛", "スプリントシリーズ");
            default -> ThoroughbredData.tr(tag.trim(), tag.trim(), tag.trim(), tag.trim());
        };
    }

    public static boolean matchesAgeRequirement(int horseAgeYears, int ageRequirement) {
        return switch (ageRequirement) {
            case 0 -> horseAgeYears == 2;
            case 1 -> horseAgeYears == 3;
            case 2 -> horseAgeYears >= 3;
            case 3, 4 -> horseAgeYears >= 4;
            default -> horseAgeYears >= 3;
        };
    }

    public static String getAgeRequirementLabel(int ageRequirement) {
        return switch (ageRequirement) {
            case 0 -> ThoroughbredData.tr("2-Year-Olds Only", "2歲限定", "2岁限定", "2歳限定");
            case 1 -> ThoroughbredData.tr("3-Year-Olds Only", "3歲限定 (經典)", "3岁限定 (经典)", "3歳限定 (クラシック)");
            case 2 -> ThoroughbredData.tr("3-Years-Old and Up", "3歲及以上", "3岁及以上", "3歳以上");
            case 3, 4 -> ThoroughbredData.tr("4-Years-Old and Up", "4歲及以上", "4岁及以上", "4歳以上");
            default -> ThoroughbredData.tr("3-Years-Old and Up", "3歲及以上", "3岁及以上", "3歳以上");
        };
    }

    public static boolean matchesGenderRequirement(String gender, int genderRequirement) {
        if (genderRequirement == 1) {
            return ThoroughbredData.GENDER_FEMALE.equalsIgnoreCase(gender);
        }
        return true;
    }

    public static String getGenderRequirementLabel(int genderRequirement) {
        return (genderRequirement == 1)
                ? ThoroughbredData.tr("Fillies & Mares Only", "雌馬限定 (牝馬)", "雌马限定 (牝马)", "牝馬限定")
                : ThoroughbredData.tr("Open (All Sexes)", "無限制 (雌雄均可)", "无限制 (雌雄均可)", "混合 (性別不問)");
    }

    public static class RaceBaseType {
        public String baseId = UUID.randomUUID().toString();
        public String surface = "Dirt"; // "Turf" or "Dirt"
        public int distanceMeters = 2400;
        public List<BlockPos> startGatePositions = new ArrayList<>();
        public Direction lateralDir = Direction.EAST;
        public Direction gateFacing = Direction.NORTH;
        public List<CheckpointLine> checkpoints = new ArrayList<>();
        public BlockPos finishLineStart = BlockPos.ZERO;
        public BlockPos finishLineEnd = BlockPos.ZERO;
        public List<RaceDefinition> races = new ArrayList<>();

        public String getStructuredBottomName(String racecourseName) {
            String course = (racecourseName == null || racecourseName.isBlank()) ? "Racecourse" : racecourseName.trim();
            String surf = ("Turf".equalsIgnoreCase(surface)) ? "Turf" : "Dirt";
            return course + " " + surf + " " + distanceMeters + "m";
        }

        public Vec3 getStartGateLineMidpoint() {
            if (startGatePositions.isEmpty()) return Vec3.ZERO;
            BlockPos first = startGatePositions.get(0);
            BlockPos last = startGatePositions.get(startGatePositions.size() - 1);
            return new Vec3(
                    (first.getX() + last.getX()) * 0.5 + 0.5,
                    (first.getY() + last.getY()) * 0.5,
                    (first.getZ() + last.getZ()) * 0.5 + 0.5
            );
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("BaseId", baseId);
            t.putString("Surface", surface);
            t.putInt("DistanceMeters", distanceMeters);
            t.putString("LateralDir", lateralDir.getName());
            t.putString("GateFacing", gateFacing.getName());

            ListTag gatesTag = new ListTag();
            for (BlockPos p : startGatePositions) {
                gatesTag.add(writeBlockPos(p));
            }
            t.put("StartGates", gatesTag);

            ListTag cpTag = new ListTag();
            for (CheckpointLine cp : checkpoints) {
                cpTag.add(cp.toNBT());
            }
            t.put("Checkpoints", cpTag);

            t.put("FinishStart", writeBlockPos(finishLineStart));
            t.put("FinishEnd", writeBlockPos(finishLineEnd));

            ListTag racesTag = new ListTag();
            for (RaceDefinition r : races) {
                racesTag.add(r.toNBT());
            }
            t.put("Races", racesTag);
            return t;
        }

        public static RaceBaseType fromNBT(CompoundTag t) {
            RaceBaseType b = new RaceBaseType();
            b.baseId = t.contains("BaseId") ? t.getString("BaseId") : UUID.randomUUID().toString();
            b.surface = t.contains("Surface") ? t.getString("Surface") : "Dirt";
            b.distanceMeters = t.contains("DistanceMeters") ? t.getInt("DistanceMeters") : 2400;
            Direction lat = Direction.byName(t.getString("LateralDir"));
            b.lateralDir = (lat != null && lat.getAxis().isHorizontal()) ? lat : Direction.EAST;
            Direction fac = Direction.byName(t.getString("GateFacing"));
            b.gateFacing = (fac != null && fac.getAxis().isHorizontal()) ? fac : Direction.NORTH;

            ListTag gatesTag = t.getList("StartGates", Tag.TAG_COMPOUND);
            for (int i = 0; i < gatesTag.size(); i++) {
                b.startGatePositions.add(readBlockPos(gatesTag.getCompound(i)));
            }

            ListTag cpTag = t.getList("Checkpoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < cpTag.size(); i++) {
                b.checkpoints.add(CheckpointLine.fromNBT(cpTag.getCompound(i)));
            }

            if (t.contains("FinishStart")) b.finishLineStart = readBlockPos(t.getCompound("FinishStart"));
            if (t.contains("FinishEnd")) b.finishLineEnd = readBlockPos(t.getCompound("FinishEnd"));

            ListTag racesTag = t.getList("Races", Tag.TAG_COMPOUND);
            for (int i = 0; i < racesTag.size(); i++) {
                b.races.add(RaceDefinition.fromNBT(racesTag.getCompound(i)));
            }
            return b;
        }
    }

    public static class ConsoleRaceConfig {
        public String dimension = "minecraft:overworld";
        public BlockPos consolePos = BlockPos.ZERO;
        public String racecourseName = "Racecourse";
        public List<RaceBaseType> baseTypes = new ArrayList<>();

        public RaceBaseType getBaseById(String baseId) {
            if (baseId == null) return null;
            for (RaceBaseType b : baseTypes) {
                if (b.baseId.equals(baseId)) return b;
            }
            return null;
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("Dimension", dimension);
            t.put("ConsolePos", writeBlockPos(consolePos));
            t.putString("RacecourseName", racecourseName != null ? racecourseName : "Racecourse");
            ListTag basesList = new ListTag();
            for (RaceBaseType b : baseTypes) {
                basesList.add(b.toNBT());
            }
            t.put("BaseTypes", basesList);
            return t;
        }

        public static ConsoleRaceConfig fromNBT(CompoundTag t) {
            ConsoleRaceConfig c = new ConsoleRaceConfig();
            c.dimension = t.contains("Dimension") ? t.getString("Dimension") : "minecraft:overworld";
            c.consolePos = readBlockPos(t.getCompound("ConsolePos"));
            c.racecourseName = t.contains("RacecourseName") ? t.getString("RacecourseName") : "Racecourse";
            ListTag basesList = t.getList("BaseTypes", Tag.TAG_COMPOUND);
            for (int i = 0; i < basesList.size(); i++) {
                c.baseTypes.add(RaceBaseType.fromNBT(basesList.getCompound(i)));
            }
            return c;
        }
    }

    public static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag t = new CompoundTag();
        if (pos != null) {
            t.putInt("X", pos.getX());
            t.putInt("Y", pos.getY());
            t.putInt("Z", pos.getZ());
        }
        return t;
    }

    public static BlockPos readBlockPos(CompoundTag t) {
        return new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
    }

    // =========================================================================
    // 2. IN-MEMORY BUILDER SESSIONS, ACTIVE RACE SESSIONS & STRATEGY CONTEXT
    // =========================================================================
    public static class BaseBuildSession {
        public UUID playerUUID;
        public String dimension;
        public BlockPos consolePos;
        public int mode = 0; // 0 = Starting Point, 1 = Checkpoint Line, 2 = Finish Line

        public BlockPos startLineAnchor = null;
        public List<BlockPos> startGatePositions = new ArrayList<>();
        public boolean singleStallAnyDirection = false;
        public Direction lateralDir = Direction.EAST;
        public Direction gateFacing = Direction.NORTH;
        public boolean gateFacingLocked = false;

        public BlockPos checkpointAnchor = null;
        public List<CheckpointLine> checkpoints = new ArrayList<>();

        public BlockPos finishAnchor = null;
        public BlockPos finishLineStart = null;
        public BlockPos finishLineEnd = null;

        public boolean isReadyToSave() {
            return !startGatePositions.isEmpty() && !checkpoints.isEmpty()
                    && finishLineStart != null && finishLineEnd != null;
        }
    }

    public static class RunnerProgress {
        public int gateNumber;
        public int horseId;
        public UUID horseUUID;
        public String horseName;
        public boolean inGate = false;
        public boolean leftStartingGate = false;
        public int nextCheckpointIndex = 0;
        public boolean finished = false;
        public boolean dnf = false;
        public String dnfReason = "";
        public boolean dsq = false;
        public boolean hadMountedJockey = false;
        public UUID lastJockeyUUID = null;
        public boolean hasMovedInRace = false;
        public int midRaceHaltedTicks = 0;
        public long finishTick = 0L;
        public double finishTimeSeconds = 0.0;
        public int placement = 0;

        // Step 13: Real-Time Official Leaderboard & Corner-Aware Arc Progress Tracking Fields
        public int currentRank = 1;
        public double raceMeters = 0.0;
        public double maxReachedMeters = 0.0;
        public float lateralOffset = 0.0F;
        public double currentSpeedKmh = 0.0;
        public double rankingSortScore = 0.0;

        // Per-leg curved path tracking so progress never stalls around wide corners!
        public double lastTrackX = Double.NaN;
        public double lastTrackZ = Double.NaN;
        public double legStartX = Double.NaN;
        public double legStartZ = Double.NaN;
        public double legOdometerBlocks = 0.0;
        public double totalOdometerBlocks = 0.0;
    }

    /**
     * Step 14: Live Race Strategy Context for a running horse in an active race.
     * Used by ThoroughbredRunEngine to automatically detect the horse's active running strategy
     * (Big Escape / Front Runner / Pace Chaser / Late Surger / End Closer) once 5.0 seconds have elapsed!
     */
    public static class RaceStrategyContext {
        public final boolean inActiveRace;
        public final boolean pastFiveSecondStartWindow;
        public final boolean finished;
        public final int rank;
        public final int totalRunners;
        public final double raceMeters;
        public final int totalDistanceMeters;
        public final double remainingMeters;
        public final double lengthsBehindLeader;
        public final double lengthsBehindPackLeader;
        public final double lengthsAheadOfSecond;
        public final double elapsedSeconds;

        public RaceStrategyContext(boolean inActiveRace, boolean pastFiveSecondStartWindow, boolean finished,
                                   int rank, int totalRunners, double raceMeters, int totalDistanceMeters,
                                   double remainingMeters, double lengthsBehindLeader,
                                   double lengthsBehindPackLeader, double lengthsAheadOfSecond,
                                   double elapsedSeconds) {
            this.inActiveRace = inActiveRace;
            this.pastFiveSecondStartWindow = pastFiveSecondStartWindow;
            this.finished = finished;
            this.rank = rank;
            this.totalRunners = totalRunners;
            this.raceMeters = raceMeters;
            this.totalDistanceMeters = totalDistanceMeters;
            this.remainingMeters = remainingMeters;
            this.lengthsBehindLeader = lengthsBehindLeader;
            this.lengthsBehindPackLeader = lengthsBehindPackLeader;
            this.lengthsAheadOfSecond = lengthsAheadOfSecond;
            this.elapsedSeconds = elapsedSeconds;
        }

        public static RaceStrategyContext inactive() {
            return new RaceStrategyContext(false, false, false, 1, 1, 0.0, 2000, 2000.0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static class ActiveRaceSession {
        public ServerLevel level;
        public BlockPos consolePos;
        public String racecourseName;
        public RaceBaseType baseType;
        public String raceId = "";
        public String raceName;
        public String grade;
        public String fanfareFile;
        public int fanfareVolume = 50;
        public long firstPlacePurse = 5000L;
        public long requiredEarnings = 0L;
        public int ageRequirement = 0;
        public int genderRequirement = 0;
        public String raceSeriesTag = "";
        public String raceMusicFile = "None";
        public int raceMusicVolume = 50;
        public String spurtMusicFile = "None";
        public int spurtMusicVolume = 50;
        public String g1EffectPreset = DEFAULT_G1_PRESET;
        public boolean spurtMusicTriggered = false;
        public String linkedScheduleId = null;
        public boolean isTestRace;

        public UUID starterPlayerUUID;
        public Map<Integer, Integer> expectedGateAssignments = new HashMap<>(); // gateNum -> horseId (optional)
        public int semaphoreClicks = 0;
        public boolean flagWavedGatesOpen = false; // Horses CANNOT enter stalls until the Semaphore Flag is waved 3x!
        public boolean fanfarePlaying = false;
        public boolean fanfareFinished = false;    // Gate countdown waits until BOTH fanfare finishes AND horses are loaded!
        public int fanfareTicksRemaining = 0;
        public boolean gateCountdownActive = false;
        public int gateCountdownTicks = 0;
        public boolean raceRunning = false;
        public long raceStartGameTime = 0L;

        public List<BlockPos> spawnedTemporaryGateBlocks = new ArrayList<>();
        public boolean gatesDespawned = false;
        public boolean allHorsesLeftGates = false;
        public int gateDespawnCountdownTicks = -1; // Counts down from 200 ticks (10s) after all horses leave!
        public int postFinishDisplayTicks = 200;   // Keeps final Official standings on screen for 10s after all horses finish!
        public boolean resultsPendingCreated = false;
        public boolean resultsConfirmed = false;

        public Map<Integer, RunnerProgress> runnersByGate = new LinkedHashMap<>();
        public List<RunnerProgress> finishers = new ArrayList<>();
    }

    // =========================================================================
    // STEWARD RACE RESULTS REVIEW & CONFIRMATION STATE (/tb results)
    // =========================================================================
    public static class PendingRunnerEntry {
        public int gateNumber;
        public int horseId;
        public UUID horseUUID;
        public String horseName = "";
        public String ownerUUID = "";
        public String ownerName = "";
        public String ownerSilkCode = "";
        public String jockeyName = "";
        public double finishTimeSeconds = 0.0;
        public double raceMeters = 0.0;
        public boolean dnf = false;
        public String dnfReason = "";
        public boolean dsq = false;
        public int officialPlacement = 1;
        public String formattedTime = "00:00.00";
        public String formattedMargin = "-";
        public long projectedPurse = 0L;
    }

    public static class PendingRaceResult {
        public ServerLevel level;
        public BlockPos consolePos;
        public String racecourseName = "Racecourse";
        public String raceId = "";
        public String raceName = "Official Race";
        public String grade = "Open";
        public String trackSummary = "";
        public int distanceMeters = 2400;
        public int totalStalls = 8;
        public long firstPlacePurse = 5000L;
        public String linkedScheduleId = null;
        public boolean isTestRace = false;
        public boolean confirmed = false;
        public float finalElapsedSeconds = 0.0F;
        public final List<PendingRunnerEntry> runners = new ArrayList<>();

        public void recalculatePlacementsAndPurses() {
            List<PendingRunnerEntry> validFinishers = new ArrayList<>();
            List<PendingRunnerEntry> dnfList = new ArrayList<>();
            List<PendingRunnerEntry> dsqList = new ArrayList<>();

            for (PendingRunnerEntry r : runners) {
                if (r.dsq) {
                    dsqList.add(r);
                } else if (r.dnf || r.finishTimeSeconds <= 0.0) {
                    dnfList.add(r);
                } else {
                    validFinishers.add(r);
                }
            }

            validFinishers.sort((a, b) -> {
                int cmp = Double.compare(a.finishTimeSeconds, b.finishTimeSeconds);
                return cmp != 0 ? cmp : Integer.compare(a.gateNumber, b.gateNumber);
            });
            dnfList.sort(Comparator.comparingInt(a -> a.gateNumber));
            dsqList.sort(Comparator.comparingInt(a -> a.gateNumber));

            double winTime = validFinishers.isEmpty() ? 0.0 : validFinishers.get(0).finishTimeSeconds;
            for (int i = 0; i < validFinishers.size(); i++) {
                PendingRunnerEntry r = validFinishers.get(i);
                r.officialPlacement = i + 1;
                int mins = (int) (r.finishTimeSeconds / 60.0);
                double secs = r.finishTimeSeconds % 60.0;
                r.formattedTime = String.format(Locale.ROOT, "%02d:%05.2f", mins, secs);
                r.formattedMargin = (r.officialPlacement == 1)
                        ? "-"
                        : String.format(Locale.ROOT, "+%.2fs", Math.max(0.01, r.finishTimeSeconds - winTime));
                r.projectedPurse = isTestRace ? 0L : calculatePlacementEarnings(r.officialPlacement, firstPlacePurse);
            }

            for (PendingRunnerEntry r : dnfList) {
                r.officialPlacement = 99;
                r.formattedTime = "DNF";
                r.formattedMargin = (r.dnfReason != null && !r.dnfReason.isBlank()) ? r.dnfReason : "DNF";
                r.projectedPurse = 0L;
            }

            for (PendingRunnerEntry r : dsqList) {
                r.officialPlacement = 100;
                r.formattedTime = "DSQ";
                r.formattedMargin = "Disqualified";
                r.projectedPurse = 0L;
            }

            runners.clear();
            runners.addAll(validFinishers);
            runners.addAll(dnfList);
            runners.addAll(dsqList);
        }
    }

    private static final Map<UUID, BaseBuildSession> BUILD_SESSIONS = new HashMap<>();
    private static final Map<String, Map<Integer, Integer>> PRE_RACE_ASSIGNMENTS = new HashMap<>();
    private static final Map<BlockPos, ActiveRaceSession> ACTIVE_RACES = new HashMap<>();
    private static final Map<Integer, Long> WRONG_STALL_WARN_COOLDOWN = new HashMap<>();
    private static final Map<BlockPos, PendingRaceResult> PENDING_RESULTS_BY_CONSOLE = new HashMap<>();
    private static volatile PendingRaceResult LATEST_PENDING_RESULT = null;

    /**
     * Returns true if the given horse is currently participating in any active race (from stall lock-in until crossing the finish line).
     */
    public static boolean isHorseInActiveRace(Horse horse) {
        if (horse == null) return false;
        for (ActiveRaceSession session : ACTIVE_RACES.values()) {
            if (session.level == horse.level()) {
                for (RunnerProgress rp : session.runnersByGate.values()) {
                    if (rp.horseId == horse.getId() && !rp.finished) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isHorseInGradedStakesRace(Horse horse) {
        if (horse == null) return false;
        for (ActiveRaceSession session : ACTIVE_RACES.values()) {
            if (!session.raceRunning && !session.gateCountdownActive && !session.flagWavedGatesOpen) continue;
            String g = session.grade != null ? session.grade.trim().toUpperCase(Locale.ROOT) : "";
            boolean isGraded = g.equals("G1") || g.equals("G2") || g.equals("G3")
                    || g.contains("GRADE 1") || g.contains("GRADE 2") || g.contains("GRADE 3");
            if (!isGraded) continue;
            for (RunnerProgress rp : session.runnersByGate.values()) {
                if (rp.horseId == horse.getId() && !rp.finished) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the live RaceStrategyContext for a horse currently running in an active race.
     * Used by ThoroughbredRunEngine to automatically detect its running strategy and apply bonuses/penalties
     * once the 5.0-second post-start window has elapsed!
     */
    public static RaceStrategyContext getRunnerStrategyContext(Horse horse) {
        if (horse == null) {
            return RaceStrategyContext.inactive();
        }
        for (ActiveRaceSession race : ACTIVE_RACES.values()) {
            if (race.level == horse.level() && race.raceRunning && race.baseType != null) {
                RunnerProgress targetRp = null;
                for (RunnerProgress rp : race.runnersByGate.values()) {
                    if (rp.horseId == horse.getId()) {
                        targetRp = rp;
                        break;
                    }
                }
                if (targetRp == null) {
                    continue;
                }

                long elapsedTicks = Math.max(0L, race.level.getGameTime() - race.raceStartGameTime);
                boolean pastFiveSeconds = elapsedTicks >= HUD_AND_STRATEGY_DELAY_TICKS;
                double elapsedSeconds = elapsedTicks / 20.0;

                List<RunnerProgress> sorted = new ArrayList<>(race.runnersByGate.values());
                sorted.sort((a, b) -> Double.compare(b.rankingSortScore, a.rankingSortScore));

                int totalRunners = Math.max(1, sorted.size());
                int myRank = targetRp.currentRank;
                double leaderMeters = sorted.get(0).raceMeters;
                double secondMeters = sorted.size() >= 2 ? sorted.get(1).raceMeters : leaderMeters;

                double lengthsAheadOfSecond = 0.0;
                if (myRank == 1 && sorted.size() >= 2) {
                    lengthsAheadOfSecond = Math.max(0.0, (leaderMeters - secondMeters) / 2.5);
                }

                double lengthsBehindLeader = (myRank == 1)
                        ? 0.0
                        : Math.max(0.0, (leaderMeters - targetRp.raceMeters) / 2.5);

                // If 1st place is in a runaway Big Escape (>= 6.0L ahead of 2nd place),
                // measure the chasing pack's gaps relative to 2nd place so the main pack still forms Front/Pace/Late/End!
                double leaderGapOverSecondLengths = Math.max(0.0, (leaderMeters - secondMeters) / 2.5);
                double packRefMeters = (leaderGapOverSecondLengths >= 6.0 && myRank >= 2) ? secondMeters : leaderMeters;
                double lengthsBehindPackLeader = (myRank == 1)
                        ? 0.0
                        : Math.max(0.0, (packRefMeters - targetRp.raceMeters) / 2.5);

                int totalDist = Math.max(100, race.baseType.distanceMeters);
                double remMeters = Math.max(0.0, totalDist - targetRp.raceMeters);

                return new RaceStrategyContext(
                        true,
                        pastFiveSeconds,
                        targetRp.finished,
                        myRank,
                        totalRunners,
                        targetRp.raceMeters,
                        totalDist,
                        remMeters,
                        lengthsBehindLeader,
                        lengthsBehindPackLeader,
                        lengthsAheadOfSecond,
                        elapsedSeconds
                );
            }
        }
        return RaceStrategyContext.inactive();
    }

    // =========================================================================
    // 3. CUSTOM FANFARE FOLDER, DURATION CALCULATOR & VOLUME-CONTROLLED PLAYER
    // =========================================================================

    // =========================================================================
    // CUSTOM RACE MUSIC & 600m FINAL SPURT AUDIO ENGINE
    // =========================================================================
    private static Clip activeClientRaceMusicClip = null;
    private static Clip activeClientSpurtMusicClip = null;

    public static Path getTexturesFolderPath() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path folder = gameDir.resolve("aiothoroughbred_textures");
            Files.createDirectories(folder);
            Files.createDirectories(folder.resolve("items"));
            Files.createDirectories(folder.resolve("blocks"));
            if (!Files.exists(folder.resolve("README_TEXTURES.txt"))) {
                Path readme = folder.resolve("README_TEXTURES.txt");
                String instructions = """
                        === AIO Thoroughbred Custom Textures (Horse Coats, Silks & Item Sprites) ===
                        1. Pure White Horse Coat:
                           Place your custom 64x64 `pure_white.png` horse texture in this folder
                           (or in `src/main/resources/assets/aiothoroughbred/textures/entity/horse/pure_white.png`).
                        2. Custom Item & Block Sprites (`aiothoroughbred_textures/items/`):
                           Editable 16x16 PNG copies of all mod items (`thoroughbred_registry_form.png`,
                           `racing_crop.png`, `trainer_stopwatch.png`, `grooming_brush.png`,
                           `checkpoint_marker.png`, `semaphore_flag.png`) and `tote_board.png` are automatically
                           exported into `aiothoroughbred_textures/items/`. Edit any PNG there (or in
                           `src/main/resources/assets/aiothoroughbred/textures/item/`) to customize its appearance!
                        3. Custom Jockey Silks (`aiothoroughbred_textures/silks/`):
                           Editable PNG templates for jockey silk body patterns, sleeve patterns, and cap overlays.
                        """;
                Files.writeString(readme, instructions, StandardCharsets.UTF_8);
            }
            return folder;
        } catch (Exception e) {
            return new File("aiothoroughbred_textures").toPath();
        }
    }

    public static Path getMusicFolderPath() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path folder = gameDir.resolve("aiothoroughbred_music");
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
                Path readme = folder.resolve("README_MUSIC.txt");
                String instructions = """
                        === AIO Thoroughbred Custom Race Music & Final Spurt Themes ===
                        Place custom .wav or .ogg race background music and 600m final spurt themes in this folder!
                        The Racing Console will automatically detect any .wav or .ogg files here so you can select them for your races.
                        """;
                Files.writeString(readme, instructions, StandardCharsets.UTF_8);
            }
            return folder;
        } catch (Exception e) {
            return new File("aiothoroughbred_music").toPath();
        }
    }

    public static List<String> listAvailableMusic() {
        Set<String> list = new LinkedHashSet<>();
        list.add("None");
        try {
            File dir = getMusicFolderPath().toFile();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".wav") || lower.endsWith(".ogg");
                });
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    for (File f : files) {
                        list.add(f.getName());
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            File fanfareDir = getFanfareFolderPath().toFile();
            if (fanfareDir.exists() && fanfareDir.isDirectory()) {
                File[] ffiles = fanfareDir.listFiles((d, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".wav") || lower.endsWith(".ogg");
                });
                if (ffiles != null) {
                    Arrays.sort(ffiles, Comparator.comparing(File::getName));
                    for (File f : ffiles) {
                        list.add(f.getName());
                    }
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(list);
    }

    public static void stopClientRaceMusic() {
        if (activeClientRaceMusicClip != null) {
            try {
                activeClientRaceMusicClip.stop();
                activeClientRaceMusicClip.close();
            } catch (Exception ignored) {}
            activeClientRaceMusicClip = null;
        }
    }

    public static void stopClientSpurtMusic() {
        if (activeClientSpurtMusicClip != null) {
            try {
                activeClientSpurtMusicClip.stop();
                activeClientSpurtMusicClip.close();
            } catch (Exception ignored) {}
            activeClientSpurtMusicClip = null;
        }
    }

    public static void stopAllClientRaceMusic() {
        stopClientRaceMusic();
        stopClientSpurtMusic();
    }

    private static File findMusicAudioFile(String filename) {
        File f1 = new File(getMusicFolderPath().toFile(), filename);
        if (f1.exists() && f1.isFile()) return f1;
        File f2 = new File(getFanfareFolderPath().toFile(), filename);
        if (f2.exists() && f2.isFile()) return f2;
        return null;
    }

    public static void playClientRaceMusic(String filename, int volumePercent, boolean loop) {
        try {
            stopClientRaceMusic();
            if (filename == null || filename.isBlank() || "None".equalsIgnoreCase(filename)) return;

            File file = findMusicAudioFile(filename);
            if (file == null || !file.exists()) return;

            float linearGain = Mth.clamp(volumePercent / 100.0F, 0.0F, 1.0F);
            String lower = file.getName().toLowerCase(Locale.ROOT);
            Clip clip = null;

            if (lower.endsWith(".ogg")) {
                try (FileInputStream fis = new FileInputStream(file);
                     OggAudioStream oggStream = new OggAudioStream(fis)) {
                    AudioFormat fmt = oggStream.getFormat();
                    ByteBuffer buf = oggStream.readAll();
                    byte[] pcmBytes = new byte[buf.remaining()];
                    buf.get(pcmBytes);
                    scale16BitPcmVolumeInPlace(pcmBytes, linearGain);

                    clip = AudioSystem.getClip();
                    clip.open(fmt, pcmBytes, 0, pcmBytes.length);
                    applyClipGainControl(clip, linearGain);
                }
            } else {
                WavPcm16Data decoded = decodeWavTo16BitPcm(Files.readAllBytes(file.toPath()));
                if (decoded != null) {
                    scale16BitPcmVolumeInPlace(decoded.pcm16Bytes, linearGain);
                    clip = AudioSystem.getClip();
                    clip.open(decoded.format, decoded.pcm16Bytes, 0, decoded.pcm16Bytes.length);
                    applyClipGainControl(clip, linearGain);
                }
            }

            if (clip != null) {
                if (loop) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    clip.start();
                }
                activeClientRaceMusicClip = clip;
            }
        } catch (Exception ignored) {}
    }

    public static void playClientSpurtMusic(String filename, int volumePercent) {
        try {
            stopClientRaceMusic(); // Stop main race background music immediately!
            stopClientSpurtMusic();
            if (filename == null || filename.isBlank() || "None".equalsIgnoreCase(filename)) return;

            File file = findMusicAudioFile(filename);
            if (file == null || !file.exists()) return;

            float linearGain = Mth.clamp(volumePercent / 100.0F, 0.0F, 1.0F);
            String lower = file.getName().toLowerCase(Locale.ROOT);
            Clip clip = null;

            if (lower.endsWith(".ogg")) {
                try (FileInputStream fis = new FileInputStream(file);
                     OggAudioStream oggStream = new OggAudioStream(fis)) {
                    AudioFormat fmt = oggStream.getFormat();
                    ByteBuffer buf = oggStream.readAll();
                    byte[] pcmBytes = new byte[buf.remaining()];
                    buf.get(pcmBytes);
                    scale16BitPcmVolumeInPlace(pcmBytes, linearGain);

                    clip = AudioSystem.getClip();
                    clip.open(fmt, pcmBytes, 0, pcmBytes.length);
                    applyClipGainControl(clip, linearGain);
                }
            } else {
                WavPcm16Data decoded = decodeWavTo16BitPcm(Files.readAllBytes(file.toPath()));
                if (decoded != null) {
                    scale16BitPcmVolumeInPlace(decoded.pcm16Bytes, linearGain);
                    clip = AudioSystem.getClip();
                    clip.open(decoded.format, decoded.pcm16Bytes, 0, decoded.pcm16Bytes.length);
                    applyClipGainControl(clip, linearGain);
                }
            }

            if (clip != null) {
                clip.start();
                activeClientSpurtMusicClip = clip;
            }
        } catch (Exception ignored) {}
    }

    public static Path getFanfareFolderPath() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path folder = gameDir.resolve("aiothoroughbred_fanfares");
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
                Path readme = folder.resolve("README_FANFARES.txt");
                String instructions = """
                        === AIO Thoroughbred Custom Race Fanfares ===
                        Place custom .wav or .ogg fanfare audio files in this folder!
                        The Racing Console will automatically detect any .wav or .ogg files here so you can select them and adjust their volume for your races.
                        """;
                Files.writeString(readme, instructions, StandardCharsets.UTF_8);
            }
            return folder;
        } catch (Exception e) {
            return new File("aiothoroughbred_fanfares").toPath();
        }
    }

    public static List<String> listAvailableFanfares() {
        List<String> list = new ArrayList<>();
        list.add("Default");
        try {
            File dir = getFanfareFolderPath().toFile();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".wav") || lower.endsWith(".ogg");
                });
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    for (File f : files) {
                        list.add(f.getName());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * Calculates the tick duration (20 ticks = 1.0s) of a custom .wav or .ogg fanfare file
     * so the race never starts before the fanfare finishes playing!
     */
    public static int getFanfareDurationTicks(String fanfareFile) {
        if (fanfareFile == null || fanfareFile.isBlank() || "Default".equalsIgnoreCase(fanfareFile)) {
            return 100; // 5.0 seconds for default chime fanfare
        }
        try {
            File chosenFile = new File(getFanfareFolderPath().toFile(), fanfareFile);
            if (!chosenFile.exists() || !chosenFile.isFile()) {
                return 100;
            }

            String lower = chosenFile.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".ogg")) {
                try (FileInputStream fis = new FileInputStream(chosenFile);
                     OggAudioStream oggStream = new OggAudioStream(fis)) {
                    AudioFormat fmt = oggStream.getFormat();
                    ByteBuffer buf = oggStream.readAll();
                    int bytes = buf.remaining();
                    float bytesPerSec = fmt.getSampleRate() * fmt.getChannels() * (fmt.getSampleSizeInBits() / 8.0F);
                    if (bytesPerSec > 0) {
                        double seconds = bytes / bytesPerSec;
                        return Mth.clamp((int) Math.ceil(seconds * 20.0) + 6, 40, 2400);
                    }
                } catch (Throwable ignored) {
                }
            }

            WavPcm16Data decoded = decodeWavTo16BitPcm(Files.readAllBytes(chosenFile.toPath()));
            if (decoded != null) {
                float bytesPerSec = decoded.format.getSampleRate() * decoded.format.getChannels() * 2.0F;
                if (bytesPerSec > 0) {
                    double seconds = decoded.pcm16Bytes.length / bytesPerSec;
                    return Mth.clamp((int) Math.ceil(seconds * 20.0) + 6, 40, 2400);
                }
            }

            try (AudioInputStream ais = AudioSystem.getAudioInputStream(chosenFile)) {
                AudioFormat fmt = ais.getFormat();
                long frames = ais.getFrameLength();
                if (frames > 0 && fmt.getFrameRate() > 0) {
                    double seconds = frames / fmt.getFrameRate();
                    return Mth.clamp((int) Math.ceil(seconds * 20.0) + 6, 40, 2400);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return 120;
    }

    private static Clip activeClientClip = null;
    private static long activeClientClipStartMillis = 0L;

    public static void stopClientFanfareAudio() {
        activeClientClipStartMillis = 0L;
        if (activeClientClip != null) {
            try {
                activeClientClip.stop();
                activeClientClip.close();
            } catch (Exception ignored) {
            }
            activeClientClip = null;
        }
    }

    public static boolean isClientFanfarePlaying() {
        if (activeClientClip == null || !activeClientClip.isOpen()) {
            return false;
        }
        // Java Sound's Clip.start() is asynchronous on the OS mixer thread and may report isRunning() == false
        // for the first few hundred milliseconds after starting, or before reaching the end of the frame buffer.
        long elapsedMs = System.currentTimeMillis() - activeClientClipStartMillis;
        if (elapsedMs >= 0L && elapsedMs < 650L) {
            return true;
        }
        if (activeClientClip.isRunning()) {
            return true;
        }
        int frameLen = activeClientClip.getFrameLength();
        int framePos = activeClientClip.getFramePosition();
        return frameLen > 0 && framePos > 0 && framePos < (frameLen - 256);
    }

    /**
     * Plays the chosen custom fanfare file (.wav of any bit depth/float or .ogg) on the client
     * at the race's configured volume percentage (5% - 100%).
     * Returns the exact duration of the opened Clip in ticks (or -1 if Default / no custom clip).
     */
    public static int playClientFanfareAudio(String fanfareFile, int volumePercent) {
        try {
            stopClientFanfareAudio();

            if (fanfareFile == null || fanfareFile.isBlank() || "Default".equalsIgnoreCase(fanfareFile)) {
                return -1;
            }

            File dir = getFanfareFolderPath().toFile();
            File chosenFile = new File(dir, fanfareFile);
            if (!chosenFile.exists() || !chosenFile.isFile()) {
                AIOThoroughbredMod.LOGGER.warn("Custom fanfare file not found: {}", chosenFile.getAbsolutePath());
                return -1;
            }

            float linearGain = Mth.clamp(volumePercent / 100.0F, 0.0F, 1.0F);
            String lower = chosenFile.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".ogg")) {
                try (FileInputStream fis = new FileInputStream(chosenFile);
                     OggAudioStream oggStream = new OggAudioStream(fis)) {
                    AudioFormat fmt = oggStream.getFormat();
                    ByteBuffer buf = oggStream.readAll();
                    byte[] pcmBytes = new byte[buf.remaining()];
                    buf.get(pcmBytes);
                    scale16BitPcmVolumeInPlace(pcmBytes, linearGain);

                    Clip clip = AudioSystem.getClip();
                    clip.open(fmt, pcmBytes, 0, pcmBytes.length);
                    applyClipGainControl(clip, linearGain);
                    activeClientClipStartMillis = System.currentTimeMillis();
                    clip.start();
                    activeClientClip = clip;
                    return getClipDurationTicks(clip, fmt, pcmBytes.length);
                }
            }

            // Universal WAV loader: handles 8-bit, 16-bit, 24-bit, 32-bit int, and 32-bit IEEE float WAVs!
            try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(chosenFile)) {
                AudioFormat baseFormat = rawStream.getFormat();
                AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                if (AudioSystem.isConversionSupported(targetFormat, baseFormat)) {
                    try (AudioInputStream converted = AudioSystem.getAudioInputStream(targetFormat, rawStream)) {
                        byte[] pcmBytes = converted.readAllBytes();
                        scale16BitPcmVolumeInPlace(pcmBytes, linearGain);
                        Clip clip = AudioSystem.getClip();
                        clip.open(targetFormat, pcmBytes, 0, pcmBytes.length);
                        applyClipGainControl(clip, linearGain);
                        activeClientClipStartMillis = System.currentTimeMillis();
                        clip.start();
                        activeClientClip = clip;
                        return getClipDurationTicks(clip, targetFormat, pcmBytes.length);
                    }
                }
            } catch (Exception ignored) {
                // Fall through to raw RIFF WAV parser below for 24-bit / 32-bit float / extensible WAV headers
            }

            WavPcm16Data decoded = decodeWavTo16BitPcm(Files.readAllBytes(chosenFile.toPath()));
            if (decoded != null) {
                scale16BitPcmVolumeInPlace(decoded.pcm16Bytes, linearGain);
                Clip clip = AudioSystem.getClip();
                clip.open(decoded.format, decoded.pcm16Bytes, 0, decoded.pcm16Bytes.length);
                applyClipGainControl(clip, linearGain);
                activeClientClipStartMillis = System.currentTimeMillis();
                clip.start();
                activeClientClip = clip;
                return getClipDurationTicks(clip, decoded.format, decoded.pcm16Bytes.length);
            }
        } catch (Exception e) {
            AIOThoroughbredMod.LOGGER.warn("Failed to play custom fanfare '{}': {}", fanfareFile, e.getMessage());
        }
        return -1;
    }

    private static int getClipDurationTicks(Clip clip, AudioFormat fmt, int pcmByteLength) {
        long micros = clip.getMicrosecondLength();
        if (micros > 0) {
            return Math.max(20, (int) Math.ceil((micros / 1_000_000.0) * 20.0));
        }
        float bytesPerSec = fmt.getSampleRate() * fmt.getChannels() * 2.0F;
        if (bytesPerSec > 0) {
            return Math.max(20, (int) Math.ceil((pcmByteLength / bytesPerSec) * 20.0));
        }
        return 100;
    }

    /**
     * Called when the client notifies the server that the custom fanfare audio Clip has finished playing!
     */
    public static void handleClientFanfareFinished(ServerPlayer player) {
        if (player == null) return;
        for (ActiveRaceSession race : ACTIVE_RACES.values()) {
            if (race.level == player.serverLevel() && race.flagWavedGatesOpen && !race.fanfareFinished) {
                race.fanfarePlaying = false;
                race.fanfareFinished = true;
                race.fanfareTicksRemaining = 0;
            }
        }
    }

    /**
     * Scales 16-bit signed little-endian PCM samples directly by linearGain (0.0 to 1.0)
     * so volume control works reliably across all OS sound drivers!
     */
    private static void scale16BitPcmVolumeInPlace(byte[] pcm16Bytes, float linearGain) {
        if (pcm16Bytes == null || linearGain >= 0.999F) return;
        float g = Mth.clamp(linearGain, 0.0F, 1.0F);
        for (int i = 0; i + 1 < pcm16Bytes.length; i += 2) {
            short sample = (short) ((pcm16Bytes[i] & 0xFF) | (pcm16Bytes[i + 1] << 8));
            int scaled = Mth.clamp(Math.round(sample * g), -32768, 32767);
            pcm16Bytes[i] = (byte) (scaled & 0xFF);
            pcm16Bytes[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
    }

    private static void applyClipGainControl(Clip clip, float linearGain) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (linearGain <= 0.001F) ? gain.getMinimum() : (float) (20.0 * Math.log10(linearGain));
                gain.setValue(Mth.clamp(dB, gain.getMinimum(), Math.min(0.0F, gain.getMaximum())));
            }
        } catch (Exception ignored) {
        }
    }

    private static class WavPcm16Data {
        final AudioFormat format;
        final byte[] pcm16Bytes;

        WavPcm16Data(AudioFormat format, byte[] pcm16Bytes) {
            this.format = format;
            this.pcm16Bytes = pcm16Bytes;
        }
    }

    /**
     * Pure-Java fallback WAV parser that converts 8/16/24/32-bit PCM and 32-bit float RIFF WAV files
     * (including WAVE_FORMAT_EXTENSIBLE) into standard 16-bit signed little-endian PCM for Java Sound.
     */
    private static WavPcm16Data decodeWavTo16BitPcm(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 44) return null;
        ByteBuffer buf = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getInt(0) != 0x46464952 || buf.getInt(8) != 0x45564157) { // "RIFF" & "WAVE"
            return null;
        }

        int audioFormatCode = 1;
        int channels = 2;
        int sampleRate = 44100;
        int bitsPerSample = 16;
        int dataOffset = -1;
        int dataLength = 0;

        int pos = 12;
        while (pos + 8 <= fileBytes.length) {
            int chunkId = buf.getInt(pos);
            int chunkSize = buf.getInt(pos + 4);
            int chunkDataStart = pos + 8;
            if (chunkSize < 0) break;

            if (chunkId == 0x20746D66 && chunkDataStart + 16 <= fileBytes.length) { // "fmt "
                audioFormatCode = buf.getShort(chunkDataStart) & 0xFFFF;
                channels = Math.max(1, buf.getShort(chunkDataStart + 2) & 0xFFFF);
                sampleRate = buf.getInt(chunkDataStart + 4);
                bitsPerSample = buf.getShort(chunkDataStart + 14) & 0xFFFF;
                if (audioFormatCode == 0xFFFE && chunkSize >= 40 && chunkDataStart + 26 <= fileBytes.length) { // WAVE_FORMAT_EXTENSIBLE
                    audioFormatCode = buf.getShort(chunkDataStart + 24) & 0xFFFF;
                }
            } else if (chunkId == 0x61746164) { // "data"
                dataOffset = chunkDataStart;
                dataLength = Math.min(chunkSize, fileBytes.length - chunkDataStart);
                break;
            }
            if (chunkDataStart + chunkSize > fileBytes.length) break;
            pos = chunkDataStart + chunkSize + (chunkSize & 1);
        }

        if (dataOffset < 0 || dataLength <= 0) return null;
        AudioFormat outFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false
        );

        if (audioFormatCode == 1 && bitsPerSample == 16) {
            byte[] raw16 = Arrays.copyOfRange(fileBytes, dataOffset, Math.min(fileBytes.length, dataOffset + dataLength));
            return new WavPcm16Data(outFormat, raw16);
        }

        int bytesPerSample = Math.max(1, bitsPerSample / 8);
        int totalSamples = dataLength / bytesPerSample;
        ByteArrayOutputStream out = new ByteArrayOutputStream(totalSamples * 2);

        for (int i = 0; i < totalSamples; i++) {
            int offset = dataOffset + i * bytesPerSample;
            if (offset + bytesPerSample > fileBytes.length) break;
            short s16 = 0;
            if (audioFormatCode == 3 && bitsPerSample == 32) { // 32-bit IEEE float
                float f = buf.getFloat(offset);
                s16 = (short) Mth.clamp(Math.round(f * 32767.0F), -32768, 32767);
            } else if (bitsPerSample == 24) { // 24-bit signed PCM (take top 16 bits)
                int b1 = fileBytes[offset + 1] & 0xFF;
                int b2 = fileBytes[offset + 2];
                s16 = (short) ((b2 << 8) | b1);
            } else if (bitsPerSample == 32) { // 32-bit signed int PCM (take top 16 bits)
                int val = buf.getInt(offset);
                s16 = (short) (val >> 16);
            } else if (bitsPerSample == 8) { // 8-bit unsigned PCM
                int u8 = fileBytes[offset] & 0xFF;
                s16 = (short) ((u8 - 128) << 8);
            }
            out.write(s16 & 0xFF);
            out.write((s16 >> 8) & 0xFF);
        }

        return new WavPcm16Data(outFormat, out.toByteArray());
    }

    // =========================================================================
    // 4. OPEN RACING CONSOLE GUI
    // =========================================================================
    private static String assignKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static Map<Integer, Integer> getPreRaceAssignments(ServerLevel level, BlockPos consolePos) {
        Map<Integer, Integer> map = PRE_RACE_ASSIGNMENTS.computeIfAbsent(assignKey(level, consolePos), k -> new HashMap<>());
        // Automatically prune any horse IDs that no longer exist or are > 64 blocks away from the console
        List<Horse> nearby = findNearbyThoroughbreds(level, consolePos, 64.0);
        Set<Integer> nearbyIds = new HashSet<>();
        for (Horse h : nearby) {
            nearbyIds.add(h.getId());
        }
        map.values().removeIf(id -> !nearbyIds.contains(id));
        return map;
    }

    public static void openRacingConsoleGui(ServerPlayer player, ServerLevel level, BlockPos consolePos) {
        getFanfareFolderPath();
        StudbookSavedData studbook = StudbookSavedData.get(level);
        String dim = level.dimension().location().toString();
        ConsoleRaceConfig cfg = studbook.getOrCreateRacingConsole(dim, consolePos);
        BaseBuildSession buildSession = BUILD_SESSIONS.get(player.getUUID());
        boolean buildingForThisConsole = buildSession != null && buildSession.consolePos.equals(consolePos);

        List<Horse> nearbyHorses = findNearbyThoroughbreds(level, consolePos, 64.0);
        Map<Integer, Integer> assignments = getPreRaceAssignments(level, consolePos);
        ActiveRaceSession activeRace = ACTIVE_RACES.get(consolePos);

        ModNetwork.sendOpenRacingConsole(
                player, consolePos, cfg,
                buildingForThisConsole ? buildSession : null,
                listAvailableFanfares(),
                listAvailableMusic(),
                nearbyHorses,
                assignments,
                activeRace != null
        );
    }

    public static List<Horse> findNearbyThoroughbreds(ServerLevel level, BlockPos center, double radius) {
        AABB box = new AABB(center).inflate(radius, 20.0, radius);
        return level.getEntitiesOfClass(Horse.class, box, ThoroughbredData::isThoroughbred);
    }

    // =========================================================================
    // 5. CHECKPOINT MARKER & [M] MODE CYCLING (2-CLICK LINES FOR ALL 3 MODES!)
    // =========================================================================
    public static void startBaseBuildingMode(ServerPlayer player, ServerLevel level, BlockPos consolePos) {
        BaseBuildSession session = new BaseBuildSession();
        session.playerUUID = player.getUUID();
        session.dimension = level.dimension().location().toString();
        session.consolePos = consolePos.immutable();
        session.mode = 0;
        BUILD_SESSIONS.put(player.getUUID(), session);

        giveTemporaryItemIfMissing(player, new ItemStack(ModItems.CHECKPOINT_MARKER.get()));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendSystemMessage(Component.literal(
                "§6§l[Race Base-Building Mode] §eGiven temporary §fCheckpoint Marker§e!\n"
                + "§7• Current Mode: §61. Starting Point Placement Mode\n"
                + "§7• Right-click Stall #1's Main Gate spot, then right-click the end of the starting line (or click the same spot twice for 1 stall).\n"
                + "§7• Press §e§l[M] §7to cycle between Starting Point, Checkpoint Line, and Finish Line modes!"));
    }

    public static void cancelBaseBuildingMode(ServerPlayer player) {
        BUILD_SESSIONS.remove(player.getUUID());
        removeTemporaryItem(player, ModItems.CHECKPOINT_MARKER.get());
        player.sendSystemMessage(Component.literal("§c[Race Console] Exited Race Base-Building Mode and removed Checkpoint Marker."));
    }

    public static void cycleMarkerMode(ServerPlayer player) {
        BaseBuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null) {
            player.displayClientMessage(Component.literal("§cStart 'Create Race Base Type' at a Racing Console first!"), true);
            return;
        }

        if (session.mode == 0 && session.startLineAnchor != null && session.startGatePositions.isEmpty()) {
            session.startGatePositions.add(session.startLineAnchor);
            session.singleStallAnyDirection = true;
            session.startLineAnchor = null;
        }

        int nextMode = (session.mode + 1) % 3;
        if (nextMode == 1 && session.startGatePositions.isEmpty()) {
            player.displayClientMessage(Component.literal("§cPlace your Starting Gate Line first before switching to Checkpoint Mode!"), true);
            return;
        }
        if (nextMode == 2 && session.checkpoints.isEmpty()) {
            player.displayClientMessage(Component.literal("§cPlace at least 1 Checkpoint Line (2 clicks) first so the starting gate direction is set!"), true);
            return;
        }

        session.mode = nextMode;
        session.checkpointAnchor = null;
        session.finishAnchor = null;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.8F, 1.5F);

        String msg = switch (session.mode) {
            case 0 -> "§6§l[Mode 1/3] Starting Point Placement Mode §7— Right-click start & end of starting gates";
            case 1 -> "§b§l[Mode 2/3] Checkpoint Placement Mode §7— Right-click Post A then Post B to draw each checkpoint line";
            case 2 -> "§d§l[Mode 3/3] Finish Line Placement Mode §7— Right-click Post A then Post B of the finish line";
            default -> "";
        };
        player.displayClientMessage(Component.literal(msg), true);
        player.sendSystemMessage(Component.literal(msg));
    }

    public static void handleMarkerLeftClickUndo(ServerPlayer player) {
        BaseBuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null) return;

        if (session.mode == 0) {
            session.startLineAnchor = null;
            session.startGatePositions.clear();
            session.singleStallAnyDirection = false;
            session.gateFacingLocked = false;
            player.displayClientMessage(Component.literal("§e[Starting Point Mode] Cleared starting gate positions."), true);
        } else if (session.mode == 1) {
            if (session.checkpointAnchor != null) {
                session.checkpointAnchor = null;
                player.displayClientMessage(Component.literal("§e[Checkpoint Mode] Cancelled current checkpoint Post A."), true);
            } else if (!session.checkpoints.isEmpty()) {
                session.checkpoints.remove(session.checkpoints.size() - 1);
                if (session.checkpoints.isEmpty()) {
                    session.gateFacingLocked = false;
                }
                player.displayClientMessage(Component.literal("§e[Checkpoint Mode] Removed last checkpoint line (" + session.checkpoints.size() + " remaining)."), true);
            }
        } else if (session.mode == 2) {
            session.finishAnchor = null;
            session.finishLineStart = null;
            session.finishLineEnd = null;
            player.displayClientMessage(Component.literal("§e[Finish Line Mode] Cleared finish line."), true);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    public static void handleMarkerRightClickBlock(ServerPlayer player, BlockPos clickedPos, Direction clickedFace) {
        BaseBuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null) {
            player.displayClientMessage(Component.literal("§cUse a Racing Console and click 'Create Race Base Type' first!"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos targetPos = (clickedFace == Direction.UP) ? clickedPos.above() : clickedPos;

        if (session.mode == 0) {
            if (session.startLineAnchor == null) {
                if (!hasVerticalClearance(level, targetPos, 4)) {
                    player.sendSystemMessage(Component.literal("§c[Starting Gate] Stall #1 position lacks 4-block vertical clearance!"));
                    return;
                }
                session.startLineAnchor = targetPos.immutable();
                session.startGatePositions.clear();

                level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8F, 1.2F);
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§6[Starting Point] §eStall #1 Main Gate anchored at (%d, %d, %d)! Right-click the end of the line to draw connected stalls (1 space apart), or click the same block again for 1 stall.",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ())));
            } else {
                BlockPos p1 = session.startLineAnchor;
                int dx = targetPos.getX() - p1.getX();
                int dz = targetPos.getZ() - p1.getZ();

                if (dx == 0 && dz == 0) {
                    session.singleStallAnyDirection = true;
                    session.gateFacing = player.getDirection();
                    session.lateralDir = session.gateFacing.getClockWise();
                    session.startGatePositions = new ArrayList<>(List.of(p1));
                    session.startLineAnchor = null;

                    if (!session.checkpoints.isEmpty()) {
                        orientGatesTowardFirstCheckpoint(level, player, session, session.checkpoints.get(0));
                    }

                    level.playSound(null, targetPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.4F);
                    player.sendSystemMessage(Component.literal(
                            "§a§l[Starting Gate Marked!] §fConfigured §e1 §fstarting stall! Press §e§l[M] §fto switch to §bCheckpoint Placement Mode§f."));
                    return;
                }

                session.singleStallAnyDirection = false;
                Direction latDir;
                int totalSpan;
                if (Math.abs(dx) >= Math.abs(dz)) {
                    latDir = (dx >= 0) ? Direction.EAST : Direction.WEST;
                    totalSpan = Math.abs(dx);
                } else {
                    latDir = (dz >= 0) ? Direction.SOUTH : Direction.NORTH;
                    totalSpan = Math.abs(dz);
                }

                int stallCount = Mth.clamp((totalSpan / 2) + 1, 1, 16);
                List<BlockPos> candidateGates = new ArrayList<>();
                for (int i = 0; i < stallCount; i++) {
                    candidateGates.add(p1.relative(latDir, i * 2));
                }

                Direction perp1 = latDir.getClockWise();
                Direction perp2 = latDir.getCounterClockWise();
                boolean clearDir1 = checkConnectedGatesClearance(level, candidateGates, latDir, perp1) == null;
                boolean clearDir2 = checkConnectedGatesClearance(level, candidateGates, latDir, perp2) == null;

                if (!clearDir1 && !clearDir2) {
                    String reason = checkConnectedGatesClearance(level, candidateGates, latDir, perp1);
                    player.sendSystemMessage(Component.literal(
                            "§c[Starting Gate Failed] Not enough 1 WIDTH x 4 LENGTH x 4 HEIGHT clearance for "
                            + stallCount + " connected stall(s)! (" + reason + ")"));
                    return;
                }

                session.lateralDir = latDir;
                session.gateFacing = clearDir1 ? perp1 : perp2;
                session.startGatePositions = candidateGates;
                session.startLineAnchor = null;

                if (!session.checkpoints.isEmpty()) {
                    orientGatesTowardFirstCheckpoint(level, player, session, session.checkpoints.get(0));
                }

                level.playSound(null, targetPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.4F);
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§a§l[Starting Gates Marked!] §fConfigured §e%d §fconnected temporary starting stall(s) (1 space apart)!\n"
                        + "§7Press §e§l[M] §7to switch to §bCheckpoint Placement Mode§7.",
                        stallCount)));
            }
        } else if (session.mode == 1) {
            if (session.startGatePositions.isEmpty()) {
                player.sendSystemMessage(Component.literal("§cSet your Starting Gate Line in Mode 1 first!"));
                return;
            }

            int nextCpNum = session.checkpoints.size() + 1;
            if (session.checkpointAnchor == null) {
                session.checkpointAnchor = targetPos.immutable();
                level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8F, 1.3F);
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§b[Checkpoint #%d] §ePost A anchored at (%d, %d, %d)! Right-click Post B across the track to draw the checkpoint line.",
                        nextCpNum, targetPos.getX(), targetPos.getY(), targetPos.getZ())));
            } else {
                CheckpointLine cpLine = new CheckpointLine(session.checkpointAnchor, targetPos.immutable());
                session.checkpointAnchor = null;

                if (session.checkpoints.isEmpty()) {
                    if (!orientGatesTowardFirstCheckpoint(level, player, session, cpLine)) {
                        return;
                    }
                }

                session.checkpoints.add(cpLine);
                int cpNum = session.checkpoints.size();
                level.playSound(null, targetPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.5F);
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§b§l[Checkpoint #%d Line Placed!] §fFrom (%d, %d, %d) to (%d, %d, %d) — Main Gates Facing: §e%s§f!\n"
                        + "§7Right-click two more points for the next checkpoint line, or press §e[M] §7for §dFinish Line Mode§7.",
                        cpNum,
                        cpLine.startPos.getX(), cpLine.startPos.getY(), cpLine.startPos.getZ(),
                        cpLine.endPos.getX(), cpLine.endPos.getY(), cpLine.endPos.getZ(),
                        session.gateFacing.getName().toUpperCase(Locale.ROOT))));
            }
        } else if (session.mode == 2) {
            if (session.finishAnchor == null) {
                session.finishAnchor = targetPos.immutable();
                session.finishLineStart = targetPos.immutable();
                session.finishLineEnd = null;
                level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8F, 1.3F);
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§d[Finish Line] §ePost A anchored at (%d, %d, %d)! Right-click Post B across the track to complete the Finish Line.",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ())));
            } else {
                session.finishLineStart = session.finishAnchor;
                session.finishLineEnd = targetPos.immutable();
                session.finishAnchor = null;

                level.playSound(null, targetPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8F, 1.3F);
                player.sendSystemMessage(Component.literal(
                        "§a§l[Finish Line Complete!] §eYour Race Base Type layout is ready!\n"
                        + "§fRight-click the §6Racing Console §fand click §a§l[Save Race Base Type]§f!"));
            }
        }
    }

    private static boolean orientGatesTowardFirstCheckpoint(ServerLevel level, ServerPlayer player,
                                                            BaseBuildSession session, CheckpointLine firstCp) {
        BlockPos firstGate = session.startGatePositions.get(0);
        BlockPos lastGate = session.startGatePositions.get(session.startGatePositions.size() - 1);
        double gateMidX = (firstGate.getX() + lastGate.getX()) * 0.5 + 0.5;
        double gateMidZ = (firstGate.getZ() + lastGate.getZ()) * 0.5 + 0.5;

        Vec3 cpMid = firstCp.getMidpoint();
        double vx = cpMid.x - gateMidX;
        double vz = cpMid.z - gateMidZ;

        Direction chosenFacing;
        Direction chosenLatDir = session.lateralDir;

        if (session.startGatePositions.size() == 1 && session.singleStallAnyDirection) {
            if (Math.abs(vx) >= Math.abs(vz)) {
                chosenFacing = (vx >= 0) ? Direction.EAST : Direction.WEST;
            } else {
                chosenFacing = (vz >= 0) ? Direction.SOUTH : Direction.NORTH;
            }
            chosenLatDir = chosenFacing.getClockWise();
        } else {
            Direction perp1 = session.lateralDir.getClockWise();
            Direction perp2 = session.lateralDir.getCounterClockWise();
            double dot1 = vx * perp1.getStepX() + vz * perp1.getStepZ();
            double dot2 = vx * perp2.getStepX() + vz * perp2.getStepZ();
            chosenFacing = (dot1 >= dot2) ? perp1 : perp2;
        }

        String clearanceError = checkConnectedGatesClearance(level, session.startGatePositions, chosenLatDir, chosenFacing);
        if (clearanceError != null) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§c[Gate Clearance Failed!] Checkpoint #1 orientates the starting gates facing %s, but there is not enough 1x4x4 space behind the starting line (%s)!",
                    chosenFacing.getName().toUpperCase(Locale.ROOT), clearanceError)));
            return false;
        }

        session.lateralDir = chosenLatDir;
        session.gateFacing = chosenFacing;
        session.gateFacingLocked = true;
        return true;
    }

    // =========================================================================
    // 6. 3D CONNECTED TEMPORARY STARTING GATE VALIDATION, SPAWNING & DESPAWN
    // =========================================================================
    private static boolean hasVerticalClearance(ServerLevel level, BlockPos basePos, int height) {
        for (int dy = 0; dy < height; dy++) {
            BlockState s = level.getBlockState(basePos.above(dy));
            if (!isReplaceableForTempGate(s)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReplaceableForTempGate(BlockState state) {
        return state.isAir()
                || state.canBeReplaced()
                || state.is(ModBlocks.STARTING_GATE_FRAME.get())
                || state.is(ModBlocks.GREEN_STARTING_GATE_FRAME.get())
                || state.is(ModBlocks.STARTING_GATE_SEPARATOR.get())
                || state.is(ModBlocks.STARTING_GATE_NUMBER_SIGN.get())
                || state.is(ModBlocks.MAIN_STARTING_GATE.get())
                || state.is(ModBlocks.REAR_STARTING_GATE.get());
    }

    public static String checkConnectedGatesClearance(ServerLevel level, List<BlockPos> mainGatePositions,
                                                      Direction lateralDir, Direction gateFacing) {
        if (mainGatePositions == null || mainGatePositions.isEmpty()) {
            return "No starting gates specified";
        }
        Direction backDir = gateFacing.getOpposite();
        for (int i = 0; i < mainGatePositions.size(); i++) {
            BlockPos mainPos = mainGatePositions.get(i);
            BlockPos leftWallBase = mainPos.relative(lateralDir.getOpposite(), 1);
            BlockPos rightWallBase = mainPos.relative(lateralDir, 1);

            // Check Number Sign position in front of the top middle frame block (d = -1, y = 3)
            BlockPos pSign = mainPos.relative(gateFacing, 1).above(3);
            if (!isReplaceableForTempGate(level.getBlockState(pSign))) {
                return "Obstructed at (" + pSign.getX() + "," + pSign.getY() + "," + pSign.getZ() + ")";
            }

            // Check 4-block depth (d = 0..3) x 4-block height (y = 0..3)
            for (int depth = 0; depth < 4; depth++) {
                for (int dy = 0; dy < 4; dy++) {
                    BlockPos pLeft = leftWallBase.relative(backDir, depth).above(dy);
                    BlockPos pCenter = mainPos.relative(backDir, depth).above(dy);
                    BlockPos pRight = rightWallBase.relative(backDir, depth).above(dy);

                    if (!isReplaceableForTempGate(level.getBlockState(pLeft))) {
                        return "Obstructed at (" + pLeft.getX() + "," + pLeft.getY() + "," + pLeft.getZ() + ")";
                    }
                    if (!isReplaceableForTempGate(level.getBlockState(pCenter))) {
                        return "Obstructed at (" + pCenter.getX() + "," + pCenter.getY() + "," + pCenter.getZ() + ")";
                    }
                    if (!isReplaceableForTempGate(level.getBlockState(pRight))) {
                        return "Obstructed at (" + pRight.getX() + "," + pRight.getY() + "," + pRight.getZ() + ")";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Spawns the 3D connected temporary starting gates according to the 4-block-deep 3-View Racecourse Diagram:
     *   W = Original Gate Frame Block (STARTING_GATE_FRAME)
     *   G = Green Gate Frame Block    (GREEN_STARTING_GATE_FRAME)
     *   M = Main Gate                 (MAIN_STARTING_GATE)
     *   R = Rear Gate                 (REAR_STARTING_GATE)
     *   S = Gate Separator            (STARTING_GATE_SEPARATOR)
     *   O = Nothing                   (AIR)
     *
     *   FRONT VIEW (d = 0, plus Number Sign at d = -1, y = 3 in front of top middle G):
     *     y=3: GGG
     *     y=2: WOW
     *     y=1: GMG
     *     y=0: GMG
     *
     *   SIDE VIEW (d = 0..3 along Left & Right divider walls, 4 blocks deep):
     *     y=3: GGGG
     *     y=2: WOOW
     *     y=1: GSSG
     *     y=0: GGGG
     *
     *   BACK VIEW (d = 3):
     *     y=3: GGG
     *     y=2: WOW
     *     y=1: GOG
     *     y=0: GRG
     */
    public static List<BlockPos> spawnTemporaryConnectedGates(ServerLevel level, RaceBaseType base, boolean rearGatesOpen) {
        Set<BlockPos> placedPositions = new LinkedHashSet<>();
        Direction latDir = base.lateralDir;
        Direction frontDir = base.gateFacing;
        Direction backDir = frontDir.getOpposite();
        Direction doorBlockFacing = backDir;

        BlockState originalFrameState = ModBlocks.STARTING_GATE_FRAME.get().defaultBlockState();
        BlockState greenFrameState = ModBlocks.GREEN_STARTING_GATE_FRAME.get().defaultBlockState();
        BlockState separatorState = ModBlocks.STARTING_GATE_SEPARATOR.get().defaultBlockState()
                .setValue(ModBlocks.StartingGateSeparatorBlock.FACING, latDir);

        String dim = level.dimension().location().toString();
        StudbookSavedData studbook = StudbookSavedData.get(level);

        for (int i = 0; i < base.startGatePositions.size(); i++) {
            int gateNum = Math.min(16, i + 1);
            BlockPos mainPos = base.startGatePositions.get(i);
            BlockPos leftWallBase = mainPos.relative(latDir.getOpposite(), 1);
            BlockPos rightWallBase = mainPos.relative(latDir, 1);

            // 1. SIDE VIEW: Left & Right Divider Walls across d = 0..3 (4 blocks deep), y = 0..3
            //    y=3: GGGG
            //    y=2: WOOW
            //    y=1: GSSG
            //    y=0: GGGG
            for (int d = 0; d < 4; d++) {
                boolean isEndCol = (d == 0 || d == 3);
                for (int y = 0; y < 4; y++) {
                    BlockPos pL = leftWallBase.relative(backDir, d).above(y);
                    BlockPos pR = rightWallBase.relative(backDir, d).above(y);

                    BlockState wallState;
                    if (y == 0 || y == 3) {
                        wallState = greenFrameState; // GGGG
                    } else if (y == 1) {
                        wallState = isEndCol ? greenFrameState : separatorState; // GSSG
                    } else {
                        wallState = isEndCol ? originalFrameState : null; // WOOW (null = AIR)
                    }

                    if (wallState != null) {
                        level.setBlock(pL, wallState, 3);
                        level.setBlock(pR, wallState, 3);
                        placedPositions.add(pL.immutable());
                        placedPositions.add(pR.immutable());
                    } else {
                        if (!level.getBlockState(pL).isAir()) {
                            level.setBlock(pL, Blocks.AIR.defaultBlockState(), 3);
                        }
                        if (!level.getBlockState(pR).isAir()) {
                            level.setBlock(pR, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }

            // 2. FRONT VIEW Center Column (d = 0):
            //    y=3: G (middle of GGG)
            //    y=2: O (middle of WOW)
            //    y=1: M (middle of GMG)
            //    y=0: M (middle of GMG)
            List<BlockPos> stallMainGates = new ArrayList<>();
            for (int y = 0; y <= 1; y++) {
                BlockPos pFront = mainPos.above(y);
                BlockState mState = ModBlocks.MAIN_STARTING_GATE.get().defaultBlockState()
                        .setValue(StartingGateDoorBlock.FACING, doorBlockFacing)
                        .setValue(StartingGateDoorBlock.OPEN, false)
                        .setValue(StartingGateDoorBlock.GATE_NUMBER, gateNum)
                        .setValue(StartingGateDoorBlock.TOP_DOOR, y == 1);
                level.setBlock(pFront, mState, 3);
                placedPositions.add(pFront.immutable());
                stallMainGates.add(pFront.immutable());
            }
            BlockPos pFrontY2 = mainPos.above(2);
            if (!level.getBlockState(pFrontY2).isAir()) {
                level.setBlock(pFrontY2, Blocks.AIR.defaultBlockState(), 3);
            }
            BlockPos pFrontTopMiddle = mainPos.above(3);
            level.setBlock(pFrontTopMiddle, greenFrameState, 3);
            placedPositions.add(pFrontTopMiddle.immutable());

            // Number Sign in front of the top middle frame block (d = -1, y = 3)
            BlockPos pSign = mainPos.relative(frontDir, 1).above(3);
            BlockState signState = ModBlocks.STARTING_GATE_NUMBER_SIGN.get().defaultBlockState()
                    .setValue(ModBlocks.StartingGateNumberSignBlock.FACING, doorBlockFacing)
                    .setValue(ModBlocks.StartingGateNumberSignBlock.GATE_NUMBER, gateNum);
            level.setBlock(pSign, signState, 3);
            placedPositions.add(pSign.immutable());
            studbook.setNumberSignLink(dim, pSign.immutable(), stallMainGates);

            // 3. MIDDLE INTERIOR (d = 1..2):
            //    y=0..2 -> O (AIR for horse & jockey)
            //    y=3    -> G (Green Frame roof)
            for (int d = 1; d <= 2; d++) {
                for (int y = 0; y <= 2; y++) {
                    BlockPos pInner = mainPos.relative(backDir, d).above(y);
                    if (!level.getBlockState(pInner).isAir()) {
                        level.setBlock(pInner, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                BlockPos pRoof = mainPos.relative(backDir, d).above(3);
                level.setBlock(pRoof, greenFrameState, 3);
                placedPositions.add(pRoof.immutable());
            }

            // 4. BACK VIEW Center Column (d = 3):
            //    y=3: G (middle of GGG)
            //    y=2: O (middle of WOW)
            //    y=1: O (middle of GOG)
            //    y=0: R (middle of GRG) — Spawns CLOSED until the Semaphore Flag is waved 3 times!
            BlockPos pRearBase = mainPos.relative(backDir, 3);
            BlockState rState = ModBlocks.REAR_STARTING_GATE.get().defaultBlockState()
                    .setValue(StartingGateDoorBlock.FACING, doorBlockFacing)
                    .setValue(StartingGateDoorBlock.OPEN, rearGatesOpen)
                    .setValue(StartingGateDoorBlock.GATE_NUMBER, gateNum)
                    .setValue(StartingGateDoorBlock.TOP_DOOR, false);
            level.setBlock(pRearBase, rState, 3);
            placedPositions.add(pRearBase.immutable());

            for (int y = 1; y <= 2; y++) {
                BlockPos pRearAir = pRearBase.above(y);
                if (!level.getBlockState(pRearAir).isAir()) {
                    level.setBlock(pRearAir, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            BlockPos pRearTopMiddle = pRearBase.above(3);
            level.setBlock(pRearTopMiddle, greenFrameState, 3);
            placedPositions.add(pRearTopMiddle.immutable());
        }

        return new ArrayList<>(placedPositions);
    }

    public static void setAllRearGatesOpen(ActiveRaceSession session, boolean open) {
        if (session == null || session.level == null) return;
        Direction backDir = session.baseType.gateFacing.getOpposite();
        for (BlockPos mainPos : session.baseType.startGatePositions) {
            BlockPos rearPos = mainPos.relative(backDir, 3);
            BlockState rearState = session.level.getBlockState(rearPos);
            if (rearState.is(ModBlocks.REAR_STARTING_GATE.get())) {
                StartingGateDoorBlock.setDoorOpenWithVerticalSync(session.level, rearPos, rearState, open);
            }
            session.level.playSound(null, rearPos,
                    open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 1.0F, 1.1F);
        }
    }

    public static void despawnTemporaryGates(ActiveRaceSession session) {
        if (session == null || session.gatesDespawned || session.level == null) return;
        session.gatesDespawned = true;

        String dim = session.level.dimension().location().toString();
        StudbookSavedData studbook = StudbookSavedData.get(session.level);

        for (BlockPos pos : session.spawnedTemporaryGateBlocks) {
            BlockState s = session.level.getBlockState(pos);
            if (s.is(ModBlocks.STARTING_GATE_NUMBER_SIGN.get())) {
                studbook.removeNumberSignLink(dim, pos);
            }
            if (s.is(ModBlocks.STARTING_GATE_FRAME.get())
                    || s.is(ModBlocks.GREEN_STARTING_GATE_FRAME.get())
                    || s.is(ModBlocks.STARTING_GATE_SEPARATOR.get())
                    || s.is(ModBlocks.STARTING_GATE_NUMBER_SIGN.get())
                    || s.is(ModBlocks.MAIN_STARTING_GATE.get())
                    || s.is(ModBlocks.REAR_STARTING_GATE.get())) {
                session.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (!session.baseType.startGatePositions.isEmpty()) {
            BlockPos p0 = session.baseType.startGatePositions.get(0);
            session.level.sendParticles(ParticleTypes.CLOUD,
                    p0.getX() + 0.5, p0.getY() + 1.5, p0.getZ() + 0.5,
                    25, 1.5, 1.0, 1.5, 0.04);
            session.level.playSound(null, p0, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.4F);
        }
        session.spawnedTemporaryGateBlocks.clear();
    }

    // =========================================================================
    // 7. STARTING A RACE: SEMAPHORE FLAG BEFORE STALL ENTRY + FULL FANFARE!
    // =========================================================================
    public static void startRaceFromConsole(ServerPlayer starter, ServerLevel level, BlockPos consolePos,
                                            String baseId, String raceId, boolean isTestRace) {
        StudbookSavedData studbook = StudbookSavedData.get(level);
        ConsoleRaceConfig cfg = studbook.getOrCreateRacingConsole(level.dimension().location().toString(), consolePos);
        RaceBaseType base = cfg.getBaseById(baseId);
        if (base == null || base.startGatePositions.isEmpty()) {
            starter.sendSystemMessage(Component.literal("§cInvalid Race Base Type!"));
            return;
        }

        if (ACTIVE_RACES.containsKey(consolePos)) {
            cancelActiveRace(level, consolePos, false);
        }

        String clearanceErr = checkConnectedGatesClearance(level, base.startGatePositions, base.lateralDir, base.gateFacing);
        if (clearanceErr != null) {
            starter.sendSystemMessage(Component.literal("§cCannot spawn temporary starting gates: " + clearanceErr));
            return;
        }

        RaceDefinition raceDef = null;
        if (!isTestRace && raceId != null) {
            for (RaceDefinition r : base.races) {
                if (r.raceId.equals(raceId)) {
                    raceDef = r;
                    break;
                }
            }
        }

        ActiveRaceSession raceSession = new ActiveRaceSession();
        raceSession.level = level;
        raceSession.consolePos = consolePos.immutable();
        raceSession.racecourseName = cfg.racecourseName;
        raceSession.baseType = base;
        raceSession.isTestRace = isTestRace;
        raceSession.raceId = (!isTestRace && raceDef != null) ? raceDef.raceId : "";
        raceSession.raceName = isTestRace ? "Track Test Race" : (raceDef != null ? raceDef.raceName : "Official Race");
        raceSession.grade = isTestRace ? "TEST" : (raceDef != null ? raceDef.grade : "Open");
        raceSession.fanfareFile = (raceDef != null && raceDef.fanfareFile != null) ? raceDef.fanfareFile : "Default";
        raceSession.fanfareVolume = (raceDef != null) ? Mth.clamp(raceDef.fanfareVolume, 5, 100) : 50;
        raceSession.firstPlacePurse = (raceDef != null) ? Math.max(0L, raceDef.firstPlacePurse) : 10000L;
        raceSession.requiredEarnings = (raceDef != null) ? Math.max(0L, raceDef.requiredEarnings) : 0L;
        raceSession.ageRequirement = (raceDef != null) ? raceDef.ageRequirement : 0;
        raceSession.genderRequirement = (raceDef != null) ? raceDef.genderRequirement : 0;
        raceSession.raceSeriesTag = (raceDef != null && raceDef.raceSeriesTag != null) ? raceDef.raceSeriesTag : "";
        raceSession.raceMusicFile = (raceDef != null && raceDef.raceMusicFile != null) ? raceDef.raceMusicFile : "None";
        raceSession.raceMusicVolume = (raceDef != null) ? Mth.clamp(raceDef.raceMusicVolume, 5, 100) : 50;
        raceSession.spurtMusicFile = (raceDef != null && raceDef.spurtMusicFile != null) ? raceDef.spurtMusicFile : "None";
        raceSession.spurtMusicVolume = (raceDef != null) ? Mth.clamp(raceDef.spurtMusicVolume, 5, 100) : 50;
        raceSession.g1EffectPreset = (raceDef != null && raceDef.g1EffectPreset != null) ? raceDef.g1EffectPreset : DEFAULT_G1_PRESET;
        raceSession.spurtMusicTriggered = false;
        raceSession.starterPlayerUUID = starter.getUUID();

        // Step 15: Link with ScheduledRace and lock betting immediately
        StudbookSavedData.ScheduledRace linkedSched = studbook.findScheduledRaceByRaceId(consolePos, raceSession.raceId);
        if (linkedSched != null) {
            raceSession.linkedScheduleId = linkedSched.scheduleId;
            studbook.lockBettingForRace(consolePos, raceSession.raceId);
            if (linkedSched.firstPlacePurse > 0) raceSession.firstPlacePurse = linkedSched.firstPlacePurse;
            if (linkedSched.requiredEarnings > 0) raceSession.requiredEarnings = linkedSched.requiredEarnings;

            // Pre-assign registered horses to starting stalls if nearby and open!
            List<Horse> nearbyThoroughbreds = findNearbyThoroughbreds(level, consolePos, 64.0);
            for (StudbookSavedData.ScheduledRace.RegistrationEntry reg : linkedSched.registeredHorses) {
                for (Horse h : nearbyThoroughbreds) {
                    if (h.getUUID().equals(reg.horseUUID) && !raceSession.expectedGateAssignments.containsValue(h.getId())) {
                        for (int g = 1; g <= base.startGatePositions.size(); g++) {
                            if (!raceSession.expectedGateAssignments.containsKey(g)) {
                                raceSession.expectedGateAssignments.put(g, h.getId());
                                break;
                            }
                        }
                    }
                }
            }
            for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(raceSession)) {
                sp.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§6§l[Race Standby: %s] §eLinked to Schedule %s! Betting is now §c§lLOCKED§e!",
                        raceSession.raceName, linkedSched.scheduleId)));
            }
        }

        // Copy only valid stall assignments for this Base Type's stall count
        Map<Integer, Integer> rawAssignments = getPreRaceAssignments(level, consolePos);
        for (Map.Entry<Integer, Integer> entry : rawAssignments.entrySet()) {
            int gNum = entry.getKey();
            if (gNum >= 1 && gNum <= base.startGatePositions.size()) {
                Entity e = level.getEntity(entry.getValue());
                if (e instanceof Horse h && h.isAlive() && ThoroughbredData.isThoroughbred(h)) {
                    raceSession.expectedGateAssignments.put(gNum, h.getId());
                }
            }
        }

        // 1. Spawn the 3D connected temporary starting gates with Rear Doors CLOSED (until Semaphore Flag is waved!)
        raceSession.spawnedTemporaryGateBlocks = spawnTemporaryConnectedGates(level, base, false);

        PENDING_RESULTS_BY_CONSOLE.remove(consolePos.immutable());
        if (LATEST_PENDING_RESULT != null && consolePos.equals(LATEST_PENDING_RESULT.consolePos)) {
            LATEST_PENDING_RESULT = null;
        }
        ACTIVE_RACES.put(consolePos.immutable(), raceSession);

        // Ensure any previous Official Race HUD is cleared while waiting for the race to start
        ModNetwork.RaceLeaderboardSyncPacket offPacket = ModNetwork.RaceLeaderboardSyncPacket.inactive();
        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(raceSession)) {
            ModNetwork.sendRaceLeaderboardSync(sp, offPacket);
        }

        // 2. Give the starter the temporary Semaphore Flag!
        giveTemporaryItemIfMissing(starter, new ItemStack(ModItems.SEMAPHORE_FLAG.get()));

        level.playSound(null, consolePos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.1F);
        starter.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§6§l[Race Standby: %s] §eTemporary Starting Gates spawned (%d stalls — locked until flag wave)!\n"
                + "§f1. §eLeft-click the air 3 times §fwith the §c§lSemaphore Flag §fto play the Fanfare and open the Rear Gates!\n"
                + "§f2. Then §alead or ride §fparticipating horses into their starting stalls — the race will start once the fanfare finishes and all horses are loaded!",
                raceSession.raceName, base.startGatePositions.size())));
    }

    public static void handleSemaphoreFlagWave(ServerPlayer player) {
        ActiveRaceSession targetSession = null;
        for (ActiveRaceSession s : ACTIVE_RACES.values()) {
            if (s.level == player.serverLevel() && !s.flagWavedGatesOpen && !s.gateCountdownActive && !s.raceRunning) {
                targetSession = s;
                break;
            }
        }

        if (targetSession == null) {
            player.displayClientMessage(Component.literal("§cNo race is currently waiting for the Semaphore Flag!"), true);
            return;
        }

        targetSession.semaphoreClicks++;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.9F + (targetSession.semaphoreClicks * 0.2F));

        if (targetSession.semaphoreClicks < 3) {
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "§e§l[Semaphore Flag] §fWaved Flag! §6(%d/3) §7— Left-click air %d more time(s)!",
                    targetSession.semaphoreClicks, 3 - targetSession.semaphoreClicks)), true);
            return;
        }

        // 3rd Click! Wave Semaphore Flag BEFORE horses enter stalls:
        // 1. Open all Rear Starting Gates so horses can now enter their stalls!
        // 2. Trigger Fanfare (for its FULL audio length) + 3-Line Race Logo Display (for the FULL fanfare duration!)
        removeTemporaryItem(player, ModItems.SEMAPHORE_FLAG.get());
        targetSession.flagWavedGatesOpen = true;
        targetSession.fanfarePlaying = true;
        targetSession.fanfareFinished = false;
        targetSession.fanfareTicksRemaining = getFanfareDurationTicks(targetSession.fanfareFile);

        setAllRearGatesOpen(targetSession, true);

        String topGrade = targetSession.grade;
        String middleName = targetSession.raceName;
        String bottomBaseType = targetSession.baseType.getStructuredBottomName(targetSession.racecourseName);
        int logoDurationTicks = Math.max(100, targetSession.fanfareTicksRemaining);

        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(targetSession)) {
            ModNetwork.sendRaceFanfareLogo(
                    sp, topGrade, middleName, bottomBaseType,
                    targetSession.fanfareFile, targetSession.fanfareVolume, logoDurationTicks,
                    targetSession.g1EffectPreset
            );
            sp.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Semaphore Flag Waved!] §eRear starting gates are now OPEN for §6%s§e! Lead or ride horses into their stalls!",
                    targetSession.raceName)));
        }
    }

    private static List<ServerPlayer> getPlayersIn1000x1000RaceArea(ActiveRaceSession session) {
        Set<ServerPlayer> players = new LinkedHashSet<>();
        if (session == null || session.level == null) return new ArrayList<>();
        for (ServerPlayer sp : session.level.players()) {
            double dSqConsole = sp.distanceToSqr(Vec3.atCenterOf(session.consolePos));
            if (dSqConsole <= 4_000_000.0) { // Within 2000 blocks
                players.add(sp);
                continue;
            }
            if (!session.baseType.startGatePositions.isEmpty()) {
                double dSqGate = sp.distanceToSqr(Vec3.atCenterOf(session.baseType.startGatePositions.get(0)));
                if (dSqGate <= 4_000_000.0) {
                    players.add(sp);
                }
            }
        }
        return new ArrayList<>(players);
    }

    public static void cancelActiveRace(ServerLevel level, BlockPos consolePos, boolean notify) {
        ActiveRaceSession session = ACTIVE_RACES.remove(consolePos);
        if (session == null) return;

        for (RunnerProgress rp : session.runnersByGate.values()) {
            Entity e = level.getEntity(rp.horseId);
            if (e instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                horse.setStanding(false);
                ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                data.setInRace(false);
                data.clearRaceEndAilments();
                data.saveToHorse(horse);

                ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                state.inStartingGate = false;
                state.gateBreakActive = false;
                state.wHeldBeforeGateOpen = false;
                state.rearVisualTicks = 0;
                state.resetRaceStrategyState();
            }
        }

        // Clear the Official Live Race Leaderboard HUD for all players in the area
        ModNetwork.RaceLeaderboardSyncPacket offPacket = ModNetwork.RaceLeaderboardSyncPacket.inactive();
        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(session)) {
            ModNetwork.sendRaceLeaderboardSync(sp, offPacket);
        }

        despawnTemporaryGates(session);

        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(session)) {
            ModNetwork.sendStopRaceMusic(sp);
        }

        if (session.linkedScheduleId != null) {
            StudbookSavedData studbook = StudbookSavedData.get(level);
            studbook.cancelSchedule(session.linkedScheduleId);
        }

        if (session.starterPlayerUUID != null) {
            ServerPlayer starter = level.getServer().getPlayerList().getPlayer(session.starterPlayerUUID);
            if (starter != null) {
                removeTemporaryItem(starter, ModItems.SEMAPHORE_FLAG.get());
                if (notify) {
                    starter.sendSystemMessage(Component.literal("§c[Racing Console] Active race cancelled and temporary gates removed."));
                }
            }
        }
    }

    public static void ruleRunnerDnf(ActiveRaceSession session, RunnerProgress rp, Horse horse,
                                     String reasonShort, String broadcastMessage) {
        if (session == null || rp == null || rp.finished) return;
        rp.finished = true;
        rp.dnf = true;
        rp.dnfReason = (reasonShort != null && !reasonShort.isBlank()) ? reasonShort : "DNF";
        rp.placement = 99;
        rp.finishTimeSeconds = -1.0;
        rp.currentSpeedKmh = 0.0;

        if (horse != null && ThoroughbredData.isThoroughbred(horse)) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            data.setInRace(false);
            data.clearRaceEndAilments();
            ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
            runState.resetRaceStrategyState();
            data.saveToHorse(horse);
        }

        if (session.level != null) {
            if (horse != null) {
                session.level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 1.1F, 0.65F);
            }
            if (rp.lastJockeyUUID != null && session.level.getServer() != null) {
                ServerPlayer jockey = session.level.getServer().getPlayerList().getPlayer(rp.lastJockeyUUID);
                if (jockey != null) {
                    jockey.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 1.0F, 0.65F);
                    jockey.displayClientMessage(Component.literal(
                            "§4§l[DNF RULING!] §c" + rp.horseName + " — Did Not Finish (" + reasonShort + ")!"), true);
                }
            }
            if (broadcastMessage != null && !broadcastMessage.isBlank()) {
                Component dnfComp = Component.literal(broadcastMessage);
                for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(session)) {
                    sp.sendSystemMessage(dnfComp);
                }
            }
        }
    }

    /**
     * Returns the names of all horses currently participating in any active race session (for /tb dq tab completion).
     */
    public static List<String> getActiveRaceRunnerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ActiveRaceSession session : ACTIVE_RACES.values()) {
            for (RunnerProgress rp : session.runnersByGate.values()) {
                if (rp.horseName != null && !rp.horseName.isBlank() && !rp.dsq) {
                    names.add(rp.horseName);
                }
            }
        }
        if (LATEST_PENDING_RESULT != null && !LATEST_PENDING_RESULT.confirmed) {
            for (PendingRunnerEntry r : LATEST_PENDING_RESULT.runners) {
                if (r.horseName != null && !r.horseName.isBlank() && !r.dsq) {
                    names.add(r.horseName);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Admin command (/tb dq [horse name]): Disqualifies a horse from an active race or pending Steward race review
     * when its jockey is found destructive to racing integrity, marking it with the DSQ tag.
     */
    public static boolean disqualifyHorseInActiveRace(ServerPlayer admin, String horseNameQuery) {
        if (admin == null || horseNameQuery == null || horseNameQuery.isBlank()) {
            return false;
        }
        String query = horseNameQuery.trim();
        if (query.startsWith("\"") && query.endsWith("\"") && query.length() >= 2) {
            query = query.substring(1, query.length() - 1).trim();
        }

        ActiveRaceSession matchedSession = null;
        RunnerProgress matchedRp = null;

        // 1. Exact case-insensitive match in active races
        for (ActiveRaceSession session : ACTIVE_RACES.values()) {
            for (RunnerProgress rp : session.runnersByGate.values()) {
                if (rp.horseName != null && rp.horseName.equalsIgnoreCase(query)) {
                    matchedSession = session;
                    matchedRp = rp;
                    break;
                }
            }
            if (matchedRp != null) break;
        }

        // 2. Partial case-insensitive match fallback
        if (matchedRp == null) {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            for (ActiveRaceSession session : ACTIVE_RACES.values()) {
                for (RunnerProgress rp : session.runnersByGate.values()) {
                    if (rp.horseName != null && rp.horseName.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        matchedSession = session;
                        matchedRp = rp;
                        break;
                    }
                }
                if (matchedRp != null) break;
            }
        }

        if (matchedSession == null || matchedRp == null) {
            // Check if the horse is in LATEST_PENDING_RESULT awaiting Steward confirmation!
            if (LATEST_PENDING_RESULT != null && !LATEST_PENDING_RESULT.confirmed) {
                String lowerQ = query.toLowerCase(Locale.ROOT);
                for (PendingRunnerEntry entry : LATEST_PENDING_RESULT.runners) {
                    if (entry.horseName != null && (entry.horseName.equalsIgnoreCase(query) || entry.horseName.toLowerCase(Locale.ROOT).contains(lowerQ))) {
                        togglePendingRunnerDsq(admin, entry.gateNumber);
                        return true;
                    }
                }
            }
            admin.sendSystemMessage(Component.literal(
                    "§cCould not find a horse named '" + query + "' in any active or pending-review race!"));
            return false;
        }

        if (matchedRp.dsq) {
            admin.sendSystemMessage(Component.literal(
                    "§c" + matchedRp.horseName + " has already been disqualified (DSQ) from this race!"));
            return false;
        }

        boolean wasNormalFinisher = matchedRp.finished && !matchedRp.dnf && !matchedRp.dsq && matchedRp.placement >= 1 && matchedRp.placement < 99;

        // If the horse had already crossed the finish line in this active session, remove from finishers
        if (wasNormalFinisher) {
            matchedSession.finishers.remove(matchedRp);
            for (int i = 0; i < matchedSession.finishers.size(); i++) {
                matchedSession.finishers.get(i).placement = i + 1;
            }
        }

        matchedRp.finished = true;
        matchedRp.dnf = false;
        matchedRp.dsq = true;
        matchedRp.placement = 100;
        matchedRp.finishTimeSeconds = -2.0;
        matchedRp.currentSpeedKmh = 0.0;
        matchedRp.leftStartingGate = true;

        Horse horse = null;
        if (matchedSession.level != null) {
            Entity e = matchedSession.level.getEntity(matchedRp.horseId);
            if (e instanceof Horse h) {
                horse = h;
            }
        }

        if (horse != null && ThoroughbredData.isThoroughbred(horse)) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            data.setInRace(false);
            data.clearRaceEndAilments();
            ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
            runState.inStartingGate = false;
            runState.gateBreakActive = false;
            runState.resetRaceStrategyState();
            data.saveToHorse(horse);
        }

        if (LATEST_PENDING_RESULT != null && !LATEST_PENDING_RESULT.confirmed
                && matchedSession.consolePos.equals(LATEST_PENDING_RESULT.consolePos)) {
            for (PendingRunnerEntry pe : LATEST_PENDING_RESULT.runners) {
                if (pe.gateNumber == matchedRp.gateNumber) {
                    pe.dsq = true;
                    break;
                }
            }
            LATEST_PENDING_RESULT.recalculatePlacementsAndPurses();
        }

        if (matchedSession.level != null) {
            if (horse != null) {
                matchedSession.level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.85F, 0.75F);
            }
            if (matchedRp.lastJockeyUUID != null && matchedSession.level.getServer() != null) {
                ServerPlayer jockey = matchedSession.level.getServer().getPlayerList().getPlayer(matchedRp.lastJockeyUUID);
                if (jockey != null) {
                    jockey.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.85F, 0.75F);
                    jockey.displayClientMessage(Component.literal(
                            "§4§l[DISQUALIFIED — DSQ!] §c" + matchedRp.horseName + " has been disqualified from the race!"), true);
                }
            }
            String dsqBroadcast = "§4§l[RACE STEWARDS — DSQ!] §c" + matchedRp.horseName
                    + " (Gate #" + matchedRp.gateNumber + ") has been §4§lDISQUALIFIED (DSQ) §cfrom §e"
                    + matchedSession.raceName + " §cfor conduct destructive to racing integrity!";
            Component dsqComp = Component.literal(dsqBroadcast);
            for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(matchedSession)) {
                sp.sendSystemMessage(dsqComp);
            }
        }

        boolean allDone = true;
        for (RunnerProgress rp : matchedSession.runnersByGate.values()) {
            if (!rp.finished) {
                allDone = false;
                break;
            }
        }
        updateAndBroadcastRaceLeaderboard(matchedSession, allDone ? 3 : 2);
        return true;
    }

    public static void handleJockeyDismountInRace(ServerLevel level, Horse horse, Player dismountingPlayer) {
        if (level == null || horse == null) return;
        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        // If the horse suffered a Catastrophic Fracture (Poor Prognosis), handleCatastrophicBreakdownInRace handles the DNF message
        if (data.hasPoorPrognosis()) return;

        for (ActiveRaceSession session : ACTIVE_RACES.values()) {
            if (session.level != level || !session.raceRunning) continue;
            for (RunnerProgress rp : session.runnersByGate.values()) {
                if (rp.horseId == horse.getId() && !rp.finished) {
                    if (dismountingPlayer != null) {
                        rp.lastJockeyUUID = dismountingPlayer.getUUID();
                    }
                    String msg = "§4§l[RACE DNF — DISMOUNTED!] §c" + data.getName()
                            + " (Gate #" + rp.gateNumber + ") has been ruled DNF after its jockey dismounted mid-race!";
                    ruleRunnerDnf(session, rp, horse, "Dismounted", msg);
                    updateAndBroadcastRaceLeaderboard(session, 2);
                    break;
                }
            }
        }
    }

    public static void handleCatastrophicBreakdownInRace(ServerLevel level, Horse horse, ThoroughbredData data) {
        if (level == null || horse == null || data == null) return;
        data.setInRace(false);
        data.clearRaceEndAilments();
        for (ActiveRaceSession session : ACTIVE_RACES.values()) {
            if (session.level != level) continue;
            for (RunnerProgress rp : session.runnersByGate.values()) {
                if (rp.horseId == horse.getId() && !rp.finished) {
                    String msg = "§4§l[RACE BREAKDOWN / DNF] §c" + data.getName()
                            + " (Gate #" + rp.gateNumber + ") suffered a Catastrophic Fracture and collapsed on the track!";
                    ruleRunnerDnf(session, rp, horse, "Breakdown", msg);
                    updateAndBroadcastRaceLeaderboard(session, 2);
                    break;
                }
            }
        }
    }

    public static void onConsoleBroken(ServerLevel level, BlockPos pos) {
        cancelActiveRace(level, pos, false);
        StudbookSavedData.get(level).removeRacingConsole(level.dimension().location().toString(), pos);
    }

    // =========================================================================
    // 8. SERVER TICK: FLAG STANDBY, FULL FANFARE + STALL LOADING, COUNTDOWN & RACE
    // =========================================================================
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.84F, 0.15F), 1.2F);
    private static final DustParticleOptions CYAN_DUST = new DustParticleOptions(new Vector3f(0.15F, 0.90F, 1.0F), 1.2F);
    private static final DustParticleOptions RED_DUST  = new DustParticleOptions(new Vector3f(1.0F, 0.22F, 0.25F), 1.3F);

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        int serverTickCount = event.getServer().getTickCount();

        // 1. Render Course-Builder Particle Markers every 6 ticks for players in Base-Building Mode
        if (serverTickCount % 6 == 0) {
            for (BaseBuildSession bSession : BUILD_SESSIONS.values()) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(bSession.playerUUID);
                if (player != null && player.level() instanceof ServerLevel sl) {
                    renderBuilderParticles(sl, bSession);
                }
            }
        }

        // 2. Tick Active Races
        Iterator<Map.Entry<BlockPos, ActiveRaceSession>> it = ACTIVE_RACES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ActiveRaceSession> entry = it.next();
            ActiveRaceSession race = entry.getValue();
            if (race.level == null) {
                it.remove();
                continue;
            }

            // Phase 0: Waiting for the Semaphore Flag to be waved 3 times BEFORE horses can enter the stalls!
            if (!race.flagWavedGatesOpen) {
                keepStallsClosedBeforeFlagWave(race);
                continue;
            }

            // Phase A: Semaphore Flag has been waved!
            // - Fanfare plays to completion (so it is never cut off early!)
            // - Rear Gates are open so horses can enter their stalls during or after the fanfare!
            // - Once BOTH the fanfare has finished AND all participating/assigned horses are loaded, the 3-5s gate countdown begins!
            // - Note: Official HUD remains hidden until 5.0 seconds AFTER the race actually starts!
            if (!race.gateCountdownActive && !race.raceRunning) {
                tickRealisticStallLoading(race);
                holdLoadedHorsesInStalls(race);

                if (race.fanfarePlaying) {
                    if (race.fanfareFile == null || race.fanfareFile.isBlank() || "Default".equalsIgnoreCase(race.fanfareFile)) {
                        playInGameFanfareNotes1000x1000(race, 100 - race.fanfareTicksRemaining);
                    }
                    if ("G1".equalsIgnoreCase(race.grade) && serverTickCount % 4 == 0) {
                        tickG1WorldGateEffects(race);
                    }
                    race.fanfareTicksRemaining--;
                    if (race.fanfareTicksRemaining <= 0) {
                        race.fanfarePlaying = false;
                        race.fanfareFinished = true;
                    }
                }

                if (serverTickCount % 10 == 0) {
                    updateAndBroadcastRaceLeaderboard(race, 0);
                }

                if (race.fanfareFinished && areAllExpectedHorsesLoadedInStalls(race)) {
                    race.gateCountdownActive = true;
                    race.gateCountdownTicks = 60 + race.level.getRandom().nextInt(41); // Silent 3.0s - 5.0s countdown
                    updateAndBroadcastRaceLeaderboard(race, 1);
                    for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
                        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                        sp.displayClientMessage(Component.literal("§6§l[All Horses Loaded — Gates Armed!]"), true);
                    }
                }
                continue;
            }

            // Phase B: Silent 3-5s Gate Countdown phase (2D HUD remains hidden until 5s after gates burst open; 3D Tote Board shows ARMED)
            if (race.gateCountdownActive) {
                holdLoadedHorsesInStalls(race);
                if (serverTickCount % 10 == 0) {
                    updateAndBroadcastRaceLeaderboard(race, 1);
                }
                race.gateCountdownTicks--;
                if (race.gateCountdownTicks <= 0) {
                    race.gateCountdownActive = false;
                    burstRaceGatesOpen(race);
                }
                continue;
            }

            // Phase C: Active Race Running phase: Checkpoints, Finish Line, Official Live Leaderboard (appears at 5.0s!), and 10-second Gate Despawn!
            if (race.raceRunning) {
                if (serverTickCount % 6 == 0) {
                    renderActiveRaceParticles(race);
                }

                boolean anyEventJustHappened = false;
                long elapsedSinceGateOpen = Math.max(0L, race.level.getGameTime() - race.raceStartGameTime);

                // Check if all horses have left the starting gates -> enforce 5-second (100-tick) gate refusal DNF
                // and start the 10-second (200-tick) temporary gate despawn timer once all active horses have left!
                if (!race.gatesDespawned) {
                    if (!race.allHorsesLeftGates) {
                        boolean allLeft = true;
                        Direction backDir = race.baseType.gateFacing.getOpposite();
                        for (int i = 0; i < race.baseType.startGatePositions.size(); i++) {
                            int gateNum = i + 1;
                            RunnerProgress rp = race.runnersByGate.get(gateNum);
                            if (rp == null) continue;
                            if (!rp.leftStartingGate) {
                                Entity e = race.level.getEntity(rp.horseId);
                                if (e instanceof Horse h && h.isAlive() && !rp.finished) {
                                    BlockPos mainGatePos = race.baseType.startGatePositions.get(i);
                                    BlockPos stallCenter = mainGatePos.relative(backDir, 2);
                                    if (h.distanceToSqr(Vec3.atBottomCenterOf(stallCenter)) > 3.5 || rp.totalOdometerBlocks > 1.8) {
                                        rp.leftStartingGate = true;
                                    } else if (elapsedSinceGateOpen >= 100L) {
                                        // Failed to leave the starting gate within 5.0 seconds (100 ticks) after it opened -> DNF!
                                        String gateDnfMsg = "§4§l[RACE DNF — LEFT IN GATE!] §c" + rp.horseName
                                                + " (Gate #" + rp.gateNumber + ") failed to leave the starting gate within 5 seconds after it opened and has been ruled DNF!";
                                        ruleRunnerDnf(race, rp, h, "Left in Gate", gateDnfMsg);
                                        rp.leftStartingGate = true;
                                        anyEventJustHappened = true;
                                    } else {
                                        if (elapsedSinceGateOpen == 60L && h.getControllingPassenger() instanceof ServerPlayer sp) {
                                            sp.displayClientMessage(Component.literal(
                                                    "§c§l[GATE WARNING!] §eLeave the starting gate within 2.0s or you will get a DNF ruling!"), true);
                                        }
                                        allLeft = false;
                                    }
                                } else {
                                    rp.leftStartingGate = true;
                                }
                            }
                        }
                        if (allLeft) {
                            race.allHorsesLeftGates = true;
                            race.gateDespawnCountdownTicks = 200; // Exact 10 seconds (200 ticks) after all horses leave!
                        }
                    } else {
                        race.gateDespawnCountdownTicks--;
                        if (race.gateDespawnCountdownTicks <= 0) {
                            despawnTemporaryGates(race);
                        }
                    }
                }

                // Track checkpoint lines & finish line for each runner
                boolean allFinished = true;
                for (RunnerProgress rp : race.runnersByGate.values()) {
                    if (rp.finished) continue;
                    Entity e = race.level.getEntity(rp.horseId);
                    if (!(e instanceof Horse horse) || !horse.isAlive()) {
                        rp.finished = true;
                        continue;
                    }
                    allFinished = false;

                    // Keep Training Mode forcibly disabled while running in an active race!
                    ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
                    if (runState.trainingMode) {
                        runState.trainingMode = false;
                        runState.trainingRefusalActive = false;
                    }

                    if (horse.getControllingPassenger() instanceof Player mountedPlayer) {
                        rp.hadMountedJockey = true;
                        rp.lastJockeyUUID = mountedPlayer.getUUID();
                    } else if (rp.hadMountedJockey) {
                        // Jockey dismounted mid-race -> immediate DNF ruling!
                        String dismountMsg = "§4§l[RACE DNF — DISMOUNTED!] §c" + rp.horseName
                                + " (Gate #" + rp.gateNumber + ") has been ruled DNF after its jockey dismounted mid-race!";
                        ruleRunnerDnf(race, rp, horse, "Dismounted", dismountMsg);
                        anyEventJustHappened = true;
                        continue;
                    }

                    // Track if the horse has started moving or left the starting gate, then enforce the 2-second (40-tick) halt DNF rule!
                    if (runState.displayedSpeedKmh > 1.0 || rp.leftStartingGate || rp.totalOdometerBlocks > 1.0) {
                        rp.hasMovedInRace = true;
                    }
                    if (rp.hasMovedInRace && runState.gateRearFreezeTicks <= 0) {
                        if (runState.displayedSpeedKmh <= 0.5 && runState.baseSpeedKmh <= 0.5) {
                            rp.midRaceHaltedTicks++;
                            if (rp.midRaceHaltedTicks == 20 && horse.getControllingPassenger() instanceof ServerPlayer sp) {
                                sp.displayClientMessage(Component.literal(
                                        "§c§l[HALT WARNING!] §eAccelerate within 1.0s or you will get a DNF ruling!"), true);
                            }
                            if (rp.midRaceHaltedTicks >= 40) {
                                String haltMsg = "§4§l[RACE DNF — PULLED UP!] §c" + rp.horseName
                                        + " (Gate #" + rp.gateNumber + ") was pulled down to a halt for 2 seconds and has been ruled DNF!";
                                ruleRunnerDnf(race, rp, horse, "Halted 2s+", haltMsg);
                                anyEventJustHappened = true;
                                continue;
                            }
                        } else {
                            rp.midRaceHaltedTicks = 0;
                        }
                    }

                    // Check next 2-click Checkpoint Line!
                    if (rp.nextCheckpointIndex < race.baseType.checkpoints.size()) {
                        CheckpointLine cp = race.baseType.checkpoints.get(rp.nextCheckpointIndex);
                        if (distToSegment2DSqr(horse.getX(), horse.getZ(), cp.startPos, cp.endPos) <= 16.0) {
                            rp.nextCheckpointIndex++;
                            rp.legOdometerBlocks = 0.0;
                            rp.legStartX = horse.getX();
                            rp.legStartZ = horse.getZ();
                            anyEventJustHappened = true;
                            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                                sp.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                                        "§b§l[Checkpoint %d/%d Passed!]",
                                        rp.nextCheckpointIndex, race.baseType.checkpoints.size())), true);
                                race.level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.5F);
                            }
                        }
                    } else {
                        // All checkpoint lines passed -> check Finish Line crossing!
                        if (distToSegment2DSqr(horse.getX(), horse.getZ(),
                                race.baseType.finishLineStart, race.baseType.finishLineEnd) <= 16.0) {
                            rp.finished = true;
                            rp.finishTick = race.level.getGameTime();
                            long elapsedTicks = Math.max(1L, rp.finishTick - race.raceStartGameTime);
                            rp.finishTimeSeconds = elapsedTicks / 20.0;
                            race.finishers.add(rp);
                            rp.placement = race.finishers.size();
                            anyEventJustHappened = true;

                            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                            data.setInRace(false);
                            data.clearRaceEndAilments(); // Laryngeal Hemiplegia & Atrial Fibrillation go away when a race ends!
                            runState.resetRaceStrategyState();

                            // Minor chance of post-race Lameness (higher if raced at elevated Fatigue)
                            if (!data.hasActiveInjury() && !data.hasPoorPrognosis() && !data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
                                double postRaceLameChance = 0.03;
                                if (data.getFatigue() >= 80.0) {
                                    postRaceLameChance = 0.22;
                                } else if (data.getFatigue() >= 50.0) {
                                    postRaceLameChance = 0.10;
                                }
                                if (horse.getRandom().nextDouble() < postRaceLameChance) {
                                    data.addAilment(ThoroughbredData.AILMENT_LAMENESS);
                                    if (horse.getControllingPassenger() instanceof ServerPlayer riderSp) {
                                        riderSp.sendSystemMessage(Component.literal(
                                                "§c§l[Post-Race Lameness] §e" + data.getName()
                                                + " came up lame after crossing the finish line! Racing & Training Mode are locked out until treated or rested."));
                                    }
                                }
                            }

                            int mins = (int) (rp.finishTimeSeconds / 60.0);
                            double secs = rp.finishTimeSeconds % 60.0;
                            String timeStr = String.format(Locale.ROOT, "%02d:%05.2f", mins, secs);

                            data.saveToHorse(horse);

                            race.level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.3F);

                            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                                if (race.isTestRace) {
                                    sp.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                            "§6§l[TEST FINISH!] §e%s §fcrossed the finish line in §6#%d Place §f(Time: §e%s§f)!",
                                            rp.horseName, rp.placement, timeStr)));
                                } else {
                                    sp.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                            "§6§l[PROVISIONAL FINISH!] §e%s §fcrossed the finish line in §6#%d Place §f(Time: §e%s§f) — §eAwaiting Steward Review & Confirmation (/tb results)!",
                                            rp.horseName, rp.placement, timeStr)));
                                }
                            }
                        }
                    }
                }

                // Step 15: Check Final 600m Spurt Theme Trigger
                double nominalDist = Math.max(100.0, race.baseType.distanceMeters);
                double leadMeters = 0.0;
                for (RunnerProgress rprog : race.runnersByGate.values()) {
                    if (rprog.raceMeters > leadMeters) leadMeters = rprog.raceMeters;
                }
                if (!race.spurtMusicTriggered && (nominalDist - leadMeters) <= 600.0) {
                    race.spurtMusicTriggered = true;
                    if (race.spurtMusicFile != null && !"None".equalsIgnoreCase(race.spurtMusicFile)) {
                        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
                            ModNetwork.sendPlaySpurtMusic(sp, race.spurtMusicFile, race.spurtMusicVolume);
                            if (sp.getVehicle() instanceof Horse h && isHorseInActiveRace(h)) {
                                sp.displayClientMessage(Component.literal("§c§l▶▶ FINAL 600m SPURT! ◀◀"), true);
                            }
                        }
                    }
                }

                // Update server-side runner progress every 2 ticks (10 Hz) or immediately on checkpoint/finish,
                // and broadcast the Official Live Leaderboard HUD ONLY once 5.0 seconds (100 ticks) have elapsed since race start!
                if (allFinished && !race.resultsPendingCreated) {
                    // Step 15 & Steward Review: Stop ONLY main loop music (let the 600m Final Spurt theme continue playing to resolution!)
                    // and create the PendingRaceResult for Admin Steward review (/tb results) instead of paying out immediately!
                    race.resultsPendingCreated = true;
                    for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
                        ModNetwork.sendStopMainRaceMusicOnly(sp);
                    }
                    PendingRaceResult pending = buildPendingRaceResultFromSession(race);
                    LATEST_PENDING_RESULT = pending;
                    if (race.consolePos != null) {
                        PENDING_RESULTS_BY_CONSOLE.put(race.consolePos, pending);
                    }
                    if (!race.isTestRace) {
                        notifyStewardsRaceReadyForReview(race, pending);
                    }
                }

                PendingRaceResult consolePending = (race.consolePos != null && PENDING_RESULTS_BY_CONSOLE.containsKey(race.consolePos))
                        ? PENDING_RESULTS_BY_CONSOLE.get(race.consolePos)
                        : LATEST_PENDING_RESULT;
                boolean isConfirmed = race.resultsConfirmed || (consolePending != null && consolePending.confirmed);
                int phaseCode = allFinished ? (isConfirmed ? 4 : 3) : 2;
                long elapsedRaceTicks = Math.max(0L, race.level.getGameTime() - race.raceStartGameTime);
                if (anyEventJustHappened || elapsedRaceTicks == HUD_AND_STRATEGY_DELAY_TICKS || serverTickCount % 2 == 0) {
                    if (allFinished && consolePending != null) {
                        broadcastPendingResultToToteBoards(consolePending, isConfirmed ? 4 : 3);
                    } else {
                        updateAndBroadcastRaceLeaderboard(race, phaseCode);
                    }
                }

                if (allFinished) {
                    if (race.postFinishDisplayTicks > 0) {
                        race.postFinishDisplayTicks--;
                    }
                    if (race.gatesDespawned && race.postFinishDisplayTicks <= 0) {
                        it.remove();
                        if (consolePending != null) {
                            broadcastPendingResultToToteBoards(consolePending, isConfirmed ? 4 : 3);
                        } else {
                            ModNetwork.RaceLeaderboardSyncPacket offPacket = ModNetwork.RaceLeaderboardSyncPacket.inactive();
                            for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
                                ModNetwork.sendRaceLeaderboardSync(sp, offPacket);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes continuous race progress (in meters from 0 to distanceMeters), lateral track position (-1.0 to +1.0),
     * and dynamic live rankings for all participating horses on the server, and broadcasts the Official Live Leaderboard packet
     * ONLY after 5.0 seconds (100 ticks) have elapsed since the gates opened (or once all horses finish)!
     */
    private static void updateAndBroadcastRaceLeaderboard(ActiveRaceSession race, int phaseCode) {
        if (race == null || race.baseType == null || race.runnersByGate.isEmpty()) {
            return;
        }

        int numCps = race.baseType.checkpoints.size();
        int totalLegs = numCps + 1;
        Vec3[] waypoints = new Vec3[totalLegs + 1];
        BlockPos[] targetLineStarts = new BlockPos[totalLegs];
        BlockPos[] targetLineEnds = new BlockPos[totalLegs];

        waypoints[0] = race.baseType.getStartGateLineMidpoint();
        for (int i = 0; i < numCps; i++) {
            CheckpointLine cp = race.baseType.checkpoints.get(i);
            waypoints[i + 1] = cp.getMidpoint();
            targetLineStarts[i] = cp.startPos;
            targetLineEnds[i] = cp.endPos;
        }
        CheckpointLine finLine = new CheckpointLine(race.baseType.finishLineStart, race.baseType.finishLineEnd);
        waypoints[totalLegs] = finLine.getMidpoint();
        targetLineStarts[totalLegs - 1] = race.baseType.finishLineStart;
        targetLineEnds[totalLegs - 1] = race.baseType.finishLineEnd;

        double[] legBlockLengths = new double[totalLegs];
        double[] cumBlockLengths = new double[totalLegs + 1];
        cumBlockLengths[0] = 0.0;
        for (int i = 0; i < totalLegs; i++) {
            double d = Math.hypot(
                    waypoints[i + 1].x - waypoints[i].x,
                    waypoints[i + 1].z - waypoints[i].z
            );
            legBlockLengths[i] = Math.max(1.0, d);
            cumBlockLengths[i + 1] = cumBlockLengths[i] + legBlockLengths[i];
        }
        double totalCourseBlockLen = Math.max(1.0, cumBlockLengths[totalLegs]);
        double nominalCourseMeters = Math.max(100.0, race.baseType.distanceMeters);
        int totalStalls = Math.max(1, race.baseType.startGatePositions.size());

        List<RunnerProgress> sortedRunners = new ArrayList<>(race.runnersByGate.values());

        for (RunnerProgress rp : sortedRunners) {
            if (!race.raceRunning) {
                rp.raceMeters = 0.0;
                rp.maxReachedMeters = 0.0;
                rp.legOdometerBlocks = 0.0;
                rp.totalOdometerBlocks = 0.0;
                rp.currentSpeedKmh = 0.0;
                rp.lateralOffset = totalStalls <= 1 ? 0.0F
                        : Mth.clamp(((rp.gateNumber - 1.0F) / (totalStalls - 1.0F)) * 1.6F - 0.8F, -1.0F, 1.0F);
                rp.rankingSortScore = -rp.gateNumber * 0.001;
                continue;
            }

            if (rp.finished) {
                if (rp.dsq || rp.placement >= 100 || rp.finishTimeSeconds <= -1.5) {
                    rp.raceMeters = Mth.clamp(rp.maxReachedMeters, 0.0, nominalCourseMeters - 0.2);
                    rp.currentSpeedKmh = 0.0;
                    rp.finishTimeSeconds = -2.0;
                    rp.rankingSortScore = -800_000.0 - rp.gateNumber;
                } else if (rp.dnf || rp.placement == 99 || rp.finishTimeSeconds < 0.0) {
                    rp.raceMeters = Mth.clamp(rp.maxReachedMeters, 0.0, nominalCourseMeters - 0.2);
                    rp.currentSpeedKmh = 0.0;
                    rp.finishTimeSeconds = -1.0;
                    rp.rankingSortScore = -500_000.0 - rp.gateNumber;
                } else {
                    rp.raceMeters = nominalCourseMeters;
                    rp.maxReachedMeters = nominalCourseMeters;
                    rp.currentSpeedKmh = 0.0;
                    rp.rankingSortScore = 10_000_000.0 - (rp.placement * 1000.0);
                }
                continue;
            }

            Entity e = race.level.getEntity(rp.horseId);
            if (!(e instanceof Horse horse) || !horse.isAlive()) {
                rp.currentSpeedKmh = 0.0;
                rp.rankingSortScore = -1_000_000.0 - rp.gateNumber;
                continue;
            }

            ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
            rp.currentSpeedKmh = runState.displayedSpeedKmh;

            // Update curved path odometer for this runner (works on straightaways and sweeping 180-degree corners alike!)
            if (Double.isNaN(rp.lastTrackX)) {
                rp.lastTrackX = horse.getX();
                rp.lastTrackZ = horse.getZ();
            } else {
                double stepDist = Math.hypot(horse.getX() - rp.lastTrackX, horse.getZ() - rp.lastTrackZ);
                if (stepDist > 0.002 && stepDist < 10.0) {
                    rp.legOdometerBlocks += stepDist;
                    rp.totalOdometerBlocks += stepDist;
                }
                rp.lastTrackX = horse.getX();
                rp.lastTrackZ = horse.getZ();
            }

            int legIdx = Mth.clamp(rp.nextCheckpointIndex, 0, totalLegs - 1);
            Vec3 wA = waypoints[legIdx];
            Vec3 wB = waypoints[legIdx + 1];

            double startOriginX = Double.isNaN(rp.legStartX) ? wA.x : rp.legStartX;
            double startOriginZ = Double.isNaN(rp.legStartZ) ? wA.z : rp.legStartZ;

            double distFromLegStart = Math.hypot(horse.getX() - startOriginX, horse.getZ() - startOriginZ);
            double distToTargetLine = Math.sqrt(distToSegment2DSqr(
                    horse.getX(), horse.getZ(),
                    targetLineStarts[legIdx], targetLineEnds[legIdx]
            ));

            // 1. Two-Endpoint Geometric Fraction: grows smoothly 0 -> 1 even on 180-degree semicircular bends!
            double endpointFraction = distFromLegStart / Math.max(1.0, distFromLegStart + distToTargetLine);

            // 2. Curved Odometer + Remaining Distance Fraction: grows every tick along any curved corner!
            double arcFraction = rp.legOdometerBlocks / Math.max(1.0, rp.legOdometerBlocks + distToTargetLine);

            // Blend both fractions so corner progress is 100% smooth and responsive on any track layout
            double legFraction = Mth.clamp(0.55 * arcFraction + 0.45 * endpointFraction, 0.0, 0.998);

            double legStartMeters = (cumBlockLengths[legIdx] / totalCourseBlockLen) * nominalCourseMeters;
            double legSpanMeters  = (legBlockLengths[legIdx] / totalCourseBlockLen) * nominalCourseMeters;
            double geometricMeters = legStartMeters + (legFraction * legSpanMeters);

            // Also incorporate direct odometer progress when on a single-leg or wide-bend course so it never stalls
            double odoCappedMeters = Mth.clamp(rp.totalOdometerBlocks, 0.0, legStartMeters + legSpanMeters * 0.98);
            double blendedMeters = Math.max(geometricMeters, Math.min(odoCappedMeters, geometricMeters + legSpanMeters * 0.35));

            if (rp.currentSpeedKmh >= 0.5) {
                rp.maxReachedMeters = Math.max(rp.maxReachedMeters, blendedMeters);
            } else {
                rp.maxReachedMeters = blendedMeters;
            }
            rp.raceMeters = Mth.clamp(rp.maxReachedMeters, 0.0, nominalCourseMeters - 0.2);

            // Compute lateral offset (-1.0 inner to +1.0 outer) relative to current leg vector
            double dx = wB.x - wA.x;
            double dz = wB.z - wA.z;
            double segNorm = Math.max(1.0, Math.hypot(dx, dz));
            double ux = dx / segNorm;
            double uz = dz / segNorm;
            double hx = horse.getX() - wA.x;
            double hz = horse.getZ() - wA.z;
            double cross = hx * (-uz) + hz * ux;
            rp.lateralOffset = Mth.clamp((float) (cross / 7.5), -1.0F, 1.0F);

            // Sort score agrees 100% with checkpoint progress and corner-aware legFraction
            rp.rankingSortScore = (legIdx * 100_000.0) + (legFraction * 50_000.0) - distToTargetLine;
        }

        sortedRunners.sort((a, b) -> Double.compare(b.rankingSortScore, a.rankingSortScore));
        for (int i = 0; i < sortedRunners.size(); i++) {
            RunnerProgress rp = sortedRunners.get(i);
            rp.currentRank = rp.finished && rp.placement > 0 ? rp.placement : (i + 1);
        }

        // Step 14 & Step 22: Broadcast to clients during all race phases so the 3D Multi-Block Trackside Tote Board
        // shows live standby/clock/results immediately, while the 2D Jockey-Cam HUD on the client screen waits until 5.0s!
        long elapsedTicks = race.raceRunning ? Math.max(0L, race.level.getGameTime() - race.raceStartGameTime) : 0L;
        float elapsedSeconds = elapsedTicks / 20.0F;

        List<ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry> packetEntries = new ArrayList<>(sortedRunners.size());
        for (RunnerProgress rp : sortedRunners) {
            packetEntries.add(new ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry(
                    rp.gateNumber,
                    rp.horseId,
                    rp.horseName,
                    rp.currentRank,
                    (float) rp.raceMeters,
                    rp.lateralOffset,
                    (float) rp.currentSpeedKmh,
                    rp.finished,
                    (float) rp.finishTimeSeconds
            ));
        }

        ModNetwork.RaceLeaderboardSyncPacket packet = new ModNetwork.RaceLeaderboardSyncPacket(
                true,
                race.grade,
                race.raceName,
                race.baseType.getStructuredBottomName(race.racecourseName),
                race.baseType.distanceMeters,
                totalStalls,
                phaseCode,
                elapsedSeconds,
                packetEntries
        );

        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
            ModNetwork.sendRaceLeaderboardSync(sp, packet);
        }
    }

    /**
     * Links a Trackside Tote Board to the nearest active race or configured Racecourse Console and syncs its display to the player.
     */
    public static void syncNearestConsoleToToteBoardForPlayer(ServerLevel level, BlockPos totePos, ServerPlayer sp) {
        if (level == null || totePos == null || sp == null) return;

        // 1. Check if there is an active race in this level
        ActiveRaceSession nearestActive = null;
        double bestActiveDist = Double.MAX_VALUE;
        for (ActiveRaceSession race : ACTIVE_RACES.values()) {
            if (race.level == level && race.consolePos != null) {
                double d = race.consolePos.distSqr(totePos);
                if (d < bestActiveDist) {
                    bestActiveDist = d;
                    nearestActive = race;
                }
            }
        }
        if (nearestActive != null) {
            boolean allDone = nearestActive.finishers.size() >= nearestActive.runnersByGate.size() && !nearestActive.runnersByGate.isEmpty();
            PendingRaceResult activePending = nearestActive.consolePos != null ? PENDING_RESULTS_BY_CONSOLE.get(nearestActive.consolePos) : LATEST_PENDING_RESULT;
            boolean confirmed = nearestActive.resultsConfirmed || (activePending != null && activePending.confirmed);
            if (nearestActive.raceRunning && allDone && activePending != null) {
                broadcastPendingResultToToteBoards(activePending, confirmed ? 4 : 3);
                return;
            }
            int phase = nearestActive.raceRunning ? (allDone ? (confirmed ? 4 : 3) : 2)
                    : (nearestActive.gateCountdownActive ? 1 : 0);
            updateAndBroadcastRaceLeaderboard(nearestActive, phase);
            return;
        }

        // 2. Otherwise find the nearest saved Racing Console in this dimension and sync its post-race results (INQUIRY/CONFIRMED) or preview its next race
        StudbookSavedData studbook = StudbookSavedData.get(level);
        String dim = level.dimension().location().toString();
        ConsoleRaceConfig nearestConsole = null;
        double bestDist = Double.MAX_VALUE;
        for (ConsoleRaceConfig cfg : studbook.getAllRacingConsoles()) {
            if (dim.equals(cfg.dimension) && cfg.consolePos != null) {
                double d = cfg.consolePos.distSqr(totePos);
                if (d < bestDist) {
                    bestDist = d;
                    nearestConsole = cfg;
                }
            }
        }

        if (nearestConsole != null && nearestConsole.consolePos != null) {
            PendingRaceResult finishedResult = PENDING_RESULTS_BY_CONSOLE.get(nearestConsole.consolePos);
            if (finishedResult == null && LATEST_PENDING_RESULT != null && nearestConsole.consolePos.equals(LATEST_PENDING_RESULT.consolePos)) {
                finishedResult = LATEST_PENDING_RESULT;
            }
            if (finishedResult != null && !finishedResult.runners.isEmpty()) {
                broadcastPendingResultToToteBoards(finishedResult, finishedResult.confirmed ? 4 : 3);
                return;
            }
        }

        if (nearestConsole != null && !nearestConsole.baseTypes.isEmpty()) {
            RaceBaseType base = nearestConsole.baseTypes.get(0);
            String grade = "Open";
            String raceName = nearestConsole.racecourseName + " Stakes";
            if (!base.races.isEmpty()) {
                RaceDefinition def = base.races.get(0);
                grade = def.grade != null ? def.grade : "Open";
                raceName = def.raceName != null ? def.raceName : raceName;
            }
            int stalls = Math.max(1, base.startGatePositions.size());
            List<Horse> nearby = findNearbyThoroughbreds(level, nearestConsole.consolePos, 64.0);
            List<ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry> entries = new ArrayList<>();
            int gate = 1;
            for (Horse h : nearby) {
                if (gate > Math.min(18, stalls)) break;
                ThoroughbredData d = ThoroughbredData.fromHorse(h);
                if (d.isRegistered()) {
                    entries.add(new ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry(
                            gate, h.getId(), d.getName(), gate, 0.0F, 0.0F, 0.0F, false, 0.0F
                    ));
                    gate++;
                }
            }
            ModNetwork.RaceLeaderboardSyncPacket previewPacket = new ModNetwork.RaceLeaderboardSyncPacket(
                    true,
                    grade,
                    raceName,
                    base.getStructuredBottomName(nearestConsole.racecourseName),
                    base.distanceMeters,
                    stalls,
                    0,
                    0.0F,
                    entries
            );
            ModNetwork.sendRaceLeaderboardSync(sp, previewPacket);
        }
    }

    // =========================================================================
    // STEWARD RACE RESULTS REVIEW & CONFIRMATION METHODS (/tb results)
    // =========================================================================
    public static PendingRaceResult getLatestPendingResult() {
        return LATEST_PENDING_RESULT;
    }

    public static PendingRaceResult buildPendingRaceResultFromSession(ActiveRaceSession race) {
        PendingRaceResult p = new PendingRaceResult();
        p.level = race.level;
        p.consolePos = race.consolePos;
        p.racecourseName = race.racecourseName != null ? race.racecourseName : "Racecourse";
        p.raceId = race.raceId != null ? race.raceId : "";
        p.raceName = race.raceName != null ? race.raceName : "Official Race";
        p.grade = race.grade != null ? race.grade : "Open";
        p.trackSummary = race.baseType != null ? race.baseType.getStructuredBottomName(p.racecourseName) : p.racecourseName;
        p.distanceMeters = race.baseType != null ? race.baseType.distanceMeters : 2400;
        p.totalStalls = race.baseType != null ? Math.max(1, race.baseType.startGatePositions.size()) : Math.max(1, race.runnersByGate.size());
        p.firstPlacePurse = race.firstPlacePurse;
        p.linkedScheduleId = race.linkedScheduleId;
        p.isTestRace = race.isTestRace;
        p.confirmed = false;
        long elapsedTicks = race.raceRunning && race.level != null ? Math.max(0L, race.level.getGameTime() - race.raceStartGameTime) : 0L;
        p.finalElapsedSeconds = elapsedTicks / 20.0F;

        StudbookSavedData studbook = race.level != null ? StudbookSavedData.get(race.level) : null;

        for (RunnerProgress rp : race.runnersByGate.values()) {
            PendingRunnerEntry e = new PendingRunnerEntry();
            e.gateNumber = rp.gateNumber;
            e.horseId = rp.horseId;
            e.horseUUID = rp.horseUUID;
            e.horseName = rp.horseName != null ? rp.horseName : ("Gate #" + rp.gateNumber);
            e.finishTimeSeconds = rp.finishTimeSeconds;
            e.raceMeters = rp.raceMeters;
            e.dnf = rp.dnf;
            e.dnfReason = rp.dnfReason != null ? rp.dnfReason : "";
            e.dsq = rp.dsq;

            if (rp.lastJockeyUUID != null && race.level != null && race.level.getServer() != null) {
                ServerPlayer jp = race.level.getServer().getPlayerList().getPlayer(rp.lastJockeyUUID);
                if (jp != null) {
                    e.jockeyName = jp.getName().getString();
                }
            }

            boolean filledFromEntity = false;
            if (race.level != null) {
                Entity ent = race.level.getEntity(rp.horseId);
                if (ent instanceof Horse h && ThoroughbredData.isThoroughbred(h)) {
                    ThoroughbredData d = ThoroughbredData.fromHorse(h);
                    e.ownerUUID = d.getOwnerUUID();
                    e.ownerName = d.hasOwner() ? d.getOwnerName() : "Unclaimed";
                    e.ownerSilkCode = d.getOwnerSilkCode();
                    if (e.jockeyName.isEmpty() && h.getControllingPassenger() instanceof Player pl) {
                        e.jockeyName = pl.getName().getString();
                    }
                    filledFromEntity = true;
                }
            }
            if (!filledFromEntity && studbook != null) {
                StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(e.horseName);
                if (rec != null) {
                    e.ownerUUID = rec.ownerUUID != null ? rec.ownerUUID : "";
                    e.ownerName = rec.ownerName != null && !rec.ownerName.isBlank() ? rec.ownerName : "Unclaimed";
                    if (rec.thoroughbredNbt != null) {
                        e.ownerSilkCode = rec.thoroughbredNbt.getString("OwnerSilkCode");
                    }
                }
            }
            p.runners.add(e);
        }

        p.recalculatePlacementsAndPurses();
        return p;
    }

    private static void notifyStewardsRaceReadyForReview(ActiveRaceSession race, PendingRaceResult pending) {
        if (race == null || race.level == null || pending == null) return;
        String winnerName = (!pending.runners.isEmpty() && pending.runners.get(0).officialPlacement == 1)
                ? pending.runners.get(0).horseName
                : "None";

        Component publicMsg = Component.literal(String.format(Locale.ROOT,
                "§6§l[RACE STEWARDS — INQUIRY / REVIEW] §eAll runners have finished §f%s§e! Provisional 1st: §6§l%s§e. Official results, prize purses, and career records await Steward confirmation (§f/tb results§e).",
                pending.raceName, winnerName));

        net.minecraft.network.chat.MutableComponent adminActionLine = Component.literal("§6§l[Steward Action] ")
                .append(Component.literal("§a§l[📋 Open Race Results HUD (/tb results)]")
                        .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tb results"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eClick to open the Steward Race Results Review GUI")))))
                .append(Component.literal("  "))
                .append(Component.literal("§b§l[✔ Quick-Confirm Results]")
                        .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tb results confirm"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§aClick to immediately confirm results & award purses")))));

        for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
            sp.sendSystemMessage(publicMsg);
            if (sp.hasPermissions(2) || (race.starterPlayerUUID != null && race.starterPlayerUUID.equals(sp.getUUID()))) {
                sp.sendSystemMessage(adminActionLine);
            }
        }
    }

    public static void openRaceResultsGui(ServerPlayer admin) {
        if (admin == null) return;
        if (LATEST_PENDING_RESULT == null && !ACTIVE_RACES.isEmpty()) {
            ActiveRaceSession active = ACTIVE_RACES.values().iterator().next();
            LATEST_PENDING_RESULT = buildPendingRaceResultFromSession(active);
        }
        if (LATEST_PENDING_RESULT == null) {
            admin.sendSystemMessage(Component.literal(
                    "§e[Race Stewards] No race results are currently awaiting review. Start and finish a race first!"));
            return;
        }
        ModNetwork.sendOpenRaceResultsScreen(admin, LATEST_PENDING_RESULT);
    }

    public static void togglePendingRunnerDsq(ServerPlayer admin, int gateNumber) {
        if (admin == null) return;
        if (!admin.hasPermissions(2)) {
            admin.sendSystemMessage(Component.literal("§cOnly Race Stewards / Admins (OP) can modify race disqualifications!"));
            return;
        }
        if (LATEST_PENDING_RESULT == null) {
            admin.sendSystemMessage(Component.literal("§cNo pending race results found!"));
            return;
        }
        if (LATEST_PENDING_RESULT.confirmed) {
            admin.sendSystemMessage(Component.literal("§cOfficial results for this race have already been confirmed and locked!"));
            ModNetwork.sendOpenRaceResultsScreen(admin, LATEST_PENDING_RESULT);
            return;
        }

        PendingRunnerEntry target = null;
        for (PendingRunnerEntry r : LATEST_PENDING_RESULT.runners) {
            if (r.gateNumber == gateNumber) {
                target = r;
                break;
            }
        }
        if (target == null) return;

        target.dsq = !target.dsq;
        LATEST_PENDING_RESULT.recalculatePlacementsAndPurses();

        // Sync with ActiveRaceSession if still in post-finish display window
        if (LATEST_PENDING_RESULT.consolePos != null) {
            ActiveRaceSession active = ACTIVE_RACES.get(LATEST_PENDING_RESULT.consolePos);
            if (active != null) {
                RunnerProgress rp = active.runnersByGate.get(gateNumber);
                if (rp != null) {
                    rp.dsq = target.dsq;
                    rp.placement = target.officialPlacement;
                    rp.finishTimeSeconds = target.dsq ? -2.0 : (target.dnf ? -1.0 : target.finishTimeSeconds);
                }
            }
        }

        String statusMsg = target.dsq
                ? ("§4§l[STEWARD RULING — DSQ] §cGate #" + target.gateNumber + " " + target.horseName + " has been DISQUALIFIED (DSQ)! Placements & purses recalculated.")
                : ("§a§l[STEWARD RULING — RESTORED] §eGate #" + target.gateNumber + " " + target.horseName + " has been reinstated to #" + target.officialPlacement + " Place!");
        if (LATEST_PENDING_RESULT.level != null) {
            for (ServerPlayer sp : LATEST_PENDING_RESULT.level.players()) {
                sp.sendSystemMessage(Component.literal(statusMsg));
            }
        } else {
            admin.sendSystemMessage(Component.literal(statusMsg));
        }

        broadcastPendingResultToToteBoards(LATEST_PENDING_RESULT, 3);
        ModNetwork.sendOpenRaceResultsScreen(admin, LATEST_PENDING_RESULT);
    }

    public static boolean confirmPendingRaceResults(ServerPlayer admin) {
        if (admin == null) return false;
        if (!admin.hasPermissions(2)) {
            admin.sendSystemMessage(Component.literal("§cOnly Race Stewards / Admins (OP) can confirm official race results!"));
            return false;
        }
        if (LATEST_PENDING_RESULT == null) {
            admin.sendSystemMessage(Component.literal("§cNo pending race results to confirm!"));
            return false;
        }
        if (LATEST_PENDING_RESULT.confirmed) {
            admin.sendSystemMessage(Component.literal("§eOfficial results for §6" + LATEST_PENDING_RESULT.raceName + " §ehave already been confirmed!"));
            ModNetwork.sendOpenRaceResultsScreen(admin, LATEST_PENDING_RESULT);
            return false;
        }

        PendingRaceResult pending = LATEST_PENDING_RESULT;
        pending.confirmed = true;
        pending.recalculatePlacementsAndPurses();
        if (pending.consolePos != null) {
            PENDING_RESULTS_BY_CONSOLE.put(pending.consolePos, pending);
            ActiveRaceSession active = ACTIVE_RACES.get(pending.consolePos);
            if (active != null) {
                active.resultsConfirmed = true;
            }
        }

        ServerLevel level = pending.level != null ? pending.level : (admin.level() instanceof ServerLevel sl ? sl : null);
        if (level != null && !pending.isTestRace) {
            StudbookSavedData studbook = StudbookSavedData.get(level);
            int totalRunners = Math.max(1, pending.runners.size());

            for (PendingRunnerEntry r : pending.runners) {
                ThoroughbredData.CareerRaceRecord rec = new ThoroughbredData.CareerRaceRecord(
                        pending.raceId,
                        pending.raceName,
                        pending.grade,
                        pending.trackSummary,
                        r.officialPlacement,
                        totalRunners,
                        r.formattedTime,
                        r.formattedMargin,
                        r.projectedPurse
                );

                // 1. Cement CareerRaceRecord onto the live Horse entity (or its Studbook backup if unloaded)
                Horse foundHorse = null;
                Entity byId = level.getEntity(r.horseId);
                if (byId instanceof Horse h && ThoroughbredData.isThoroughbred(h)) {
                    foundHorse = h;
                } else if (r.horseUUID != null && level.getServer() != null) {
                    for (ServerLevel sLevel : level.getServer().getAllLevels()) {
                        Entity byUuid = sLevel.getEntity(r.horseUUID);
                        if (byUuid instanceof Horse h2 && ThoroughbredData.isThoroughbred(h2)) {
                            foundHorse = h2;
                            break;
                        }
                    }
                }

                if (foundHorse != null) {
                    ThoroughbredData data = ThoroughbredData.fromHorse(foundHorse);
                    data.addCareerRecord(rec);
                    data.saveToHorse(foundHorse);
                } else {
                    StudbookSavedData.HorseBackupRecord backup = studbook.getLivingHorseByName(r.horseName);
                    if (backup != null && backup.thoroughbredNbt != null) {
                        ThoroughbredData d = new ThoroughbredData();
                        d.loadFromNBT(backup.thoroughbredNbt);
                        d.addCareerRecord(rec);
                        backup.thoroughbredNbt = d.toNBT();
                        studbook.setDirty();
                    }
                }

                // 2. Award Official Purse to the Horse Owner's Bank Balance
                if (r.projectedPurse > 0L && r.ownerUUID != null && !r.ownerUUID.isBlank()) {
                    try {
                        UUID ownerUuid = UUID.fromString(r.ownerUUID);
                        studbook.addBalance(ownerUuid, r.projectedPurse);
                        if (level.getServer() != null) {
                            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
                            if (ownerPlayer != null) {
                                String ord = switch (r.officialPlacement) {
                                    case 1 -> "1st Place";
                                    case 2 -> "2nd Place";
                                    case 3 -> "3rd Place";
                                    default -> r.officialPlacement + "th Place";
                                };
                                ownerPlayer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                        "§6§l[Official Purse Payout!] §a+$%,d §fawarded to your balance for §e%s§f's official §6%s §ffinish in §e%s§f! (Total: §a$%,d§f)",
                                        r.projectedPurse, r.horseName, ord, pending.raceName, studbook.getBalance(ownerUuid))));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // 3. Settle Parimutuel Betting Tickets based on the Steward-Confirmed finish order!
            if (pending.linkedScheduleId != null) {
                List<String> confirmedOrderNames = new ArrayList<>();
                for (PendingRunnerEntry r : pending.runners) {
                    if (!r.dsq && !r.dnf && r.officialPlacement >= 1 && r.officialPlacement < 99) {
                        confirmedOrderNames.add(r.horseName);
                    }
                }
                if (!confirmedOrderNames.isEmpty()) {
                    studbook.processBettingPayouts(pending.linkedScheduleId, confirmedOrderNames, level);
                }
            }
        }

        // 4. Broadcast CONFIRMED (phaseCode = 4) to all 3D Trackside Tote Boards & Players
        broadcastPendingResultToToteBoards(pending, 4);

        String winnerStr = (!pending.runners.isEmpty() && pending.runners.get(0).officialPlacement == 1)
                ? ("Gate #" + pending.runners.get(0).gateNumber + " " + pending.runners.get(0).horseName)
                : "No Valid Finisher";
        Component confirmBroadcast = Component.literal(String.format(Locale.ROOT,
                "§a§l[RACE STEWARDS — OFFICIAL RESULTS CONFIRMED!] §fThe Stewards have confirmed the official results for §6§l[%s] %s§f! Official Winner: §e§l%s§f! Prize purses and career records have been awarded.",
                pending.grade, pending.raceName, winnerStr));

        if (level != null) {
            for (ServerPlayer sp : level.players()) {
                sp.sendSystemMessage(confirmBroadcast);
                sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.2F);
            }
        } else {
            admin.sendSystemMessage(confirmBroadcast);
        }

        ModNetwork.sendOpenRaceResultsScreen(admin, pending);
        return true;
    }

    private static void broadcastPendingResultToToteBoards(PendingRaceResult pending, int phaseCode) {
        if (pending == null || pending.level == null) return;
        List<ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry> packetEntries = new ArrayList<>(pending.runners.size());
        for (PendingRunnerEntry r : pending.runners) {
            float netFinishSec = r.dsq ? -2.0F : (r.dnf ? -1.0F : (float) r.finishTimeSeconds);
            float meters = (r.dsq || r.dnf) ? (float) r.raceMeters : (float) pending.distanceMeters;
            packetEntries.add(new ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry(
                    r.gateNumber,
                    r.horseId,
                    r.horseName,
                    r.officialPlacement,
                    meters,
                    0.0F,
                    0.0F,
                    true,
                    netFinishSec
            ));
        }

        boolean keep2dActive = pending.consolePos != null && ACTIVE_RACES.containsKey(pending.consolePos);
        ModNetwork.RaceLeaderboardSyncPacket pkt = new ModNetwork.RaceLeaderboardSyncPacket(
                keep2dActive,
                pending.grade,
                pending.raceName,
                pending.trackSummary,
                pending.distanceMeters,
                pending.totalStalls,
                phaseCode,
                pending.finalElapsedSeconds,
                packetEntries
        );
        if (pending.level.getServer() != null) {
            for (ServerPlayer sp : pending.level.getServer().getPlayerList().getPlayers()) {
                ModNetwork.sendRaceLeaderboardSync(sp, pkt);
            }
        } else {
            for (ServerPlayer sp : pending.level.players()) {
                ModNetwork.sendRaceLeaderboardSync(sp, pkt);
            }
        }
    }


    /**
     * Ensures no horse can sneak or clip into any starting stall before the Semaphore Flag is waved 3 times!
     */
    private static void keepStallsClosedBeforeFlagWave(ActiveRaceSession race) {
        if (race.baseType.startGatePositions.isEmpty()) return;
        Direction backDir = race.baseType.gateFacing.getOpposite();
        BlockPos firstGate = race.baseType.startGatePositions.get(0);
        List<Horse> nearbyHorses = findNearbyThoroughbreds(race.level, firstGate, 36.0);

        for (int i = 0; i < race.baseType.startGatePositions.size(); i++) {
            BlockPos mainPos = race.baseType.startGatePositions.get(i);
            BlockPos rearPos = mainPos.relative(backDir, 3);
            BlockState rearState = race.level.getBlockState(rearPos);
            if (rearState.is(ModBlocks.REAR_STARTING_GATE.get()) && rearState.getValue(StartingGateDoorBlock.OPEN)) {
                StartingGateDoorBlock.setDoorOpenWithVerticalSync(race.level, rearPos, rearState, false);
            }

            Vec3 rearDoorPos = Vec3.atBottomCenterOf(rearPos);
            for (Horse horse : nearbyHorses) {
                boolean insideClosedStall = isHorseInsideStallFootprint(horse, mainPos, backDir);
                boolean pushingRearDoor = Math.abs(horse.getX() - rearDoorPos.x) <= 0.70
                        && Math.abs(horse.getZ() - rearDoorPos.z) <= 0.70
                        && Math.abs(horse.getY() - rearDoorPos.y) <= 1.5;

                if (insideClosedStall) {
                    Vec3 ejectPos = Vec3.atBottomCenterOf(mainPos.relative(backDir, 4));
                    horse.teleportTo(ejectPos.x, ejectPos.y, ejectPos.z);
                    horse.setDeltaMovement(0, 0, 0);
                }
                if (insideClosedStall || pushingRearDoor) {
                    long now = race.level.getGameTime();
                    long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
                    if (now - lastWarn >= 30) {
                        WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), now);
                        if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.literal(
                                    "§e[Gates Locked] §fWave the §c§lSemaphore Flag §f(3x) before horses can enter their stalls!"), true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Orientation-aware check for whether a horse is inside a stall's 1-wide x 2-deep interior (d = 0.40 to d = 2.85, between d=0 Main Gate and d=3 Rear Gate).
     */
    private static boolean isHorseInsideStallFootprint(Horse horse, BlockPos mainPos, Direction backDir) {
        if (Math.abs(horse.getY() - mainPos.getY()) > 1.6) {
            return false;
        }
        double dx = horse.getX() - (mainPos.getX() + 0.5);
        double dz = horse.getZ() - (mainPos.getZ() + 0.5);
        double depthAlongBack = dx * backDir.getStepX() + dz * backDir.getStepZ();
        double lateralOffset = Math.abs(dx * backDir.getStepZ() - dz * backDir.getStepX());
        return depthAlongBack >= 0.40 && depthAlongBack <= 2.85 && lateralOffset <= 0.65;
    }

    /**
     * Checks whether all expected horses are locked into their starting stalls so the gate countdown can start
     * as soon as the fanfare finishes!
     * - If only 1 horse is assigned (or no other assigned horses are actively ridden/led by a player),
     *   any race (Test or Official) starts as soon as that horse and jockey are in their stall!
     */
    private static boolean areAllExpectedHorsesLoadedInStalls(ActiveRaceSession race) {
        if (race.runnersByGate.isEmpty()) {
            return false;
        }

        // Check how many loaded horses currently have a mounted player jockey
        int loadedJockeyCount = 0;
        for (RunnerProgress rp : race.runnersByGate.values()) {
            Entity e = race.level.getEntity(rp.horseId);
            if (e instanceof Horse h && h.isAlive() && h.getControllingPassenger() instanceof Player) {
                loadedJockeyCount++;
            }
        }

        // In singleplayer (or when only 1 player is in the race area and is ALREADY mounted inside a stall),
        // no other player exists to ride or lead another horse into another stall unless they are currently leading one on foot!
        List<ServerPlayer> areaPlayers = getPlayersIn1000x1000RaceArea(race);
        boolean allPlayersInAreaAreMountedInStalls = !areaPlayers.isEmpty() && loadedJockeyCount >= areaPlayers.size();
        if (allPlayersInAreaAreMountedInStalls) {
            return true;
        }

        // Otherwise, only wait for another assigned horse if it is actively being ridden or led by a player!
        for (Map.Entry<Integer, Integer> entry : race.expectedGateAssignments.entrySet()) {
            int gateNum = entry.getKey();
            if (gateNum >= 1 && gateNum <= race.baseType.startGatePositions.size() && !race.runnersByGate.containsKey(gateNum)) {
                Entity expected = race.level.getEntity(entry.getValue());
                if (expected instanceof Horse h && h.isAlive()) {
                    boolean riddenByPlayer = h.getControllingPassenger() instanceof Player;
                    boolean leashedByPlayer = h.isLeashed() && h.getLeashHolder() instanceof Player;
                    if (riddenByPlayer || leashedByPlayer) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Detects when a horse is ridden or led into one of the open temporary starting gate stalls (after the flag wave),
     * forcibly switches Training Mode to Racing Mode, locks the horse in facing forward, and closes the rear gate behind it!
     */
    private static void tickRealisticStallLoading(ActiveRaceSession race) {
        if (race.baseType.startGatePositions.isEmpty()) return;

        Direction frontDir = race.baseType.gateFacing;
        Direction backDir = frontDir.getOpposite();
        float yaw = frontDir.toYRot();

        BlockPos firstGate = race.baseType.startGatePositions.get(0);
        List<Horse> nearbyHorses = findNearbyThoroughbreds(race.level, firstGate, 36.0);

        for (int i = 0; i < race.baseType.startGatePositions.size(); i++) {
            int gateNum = i + 1;
            if (race.runnersByGate.containsKey(gateNum)) continue; // Already occupied

            BlockPos mainPos = race.baseType.startGatePositions.get(i);
            Vec3 stallCenter = Vec3.atBottomCenterOf(mainPos.relative(backDir, 2));

            for (Horse horse : nearbyHorses) {
                boolean alreadyInAnotherStall = race.runnersByGate.values().stream()
                        .anyMatch(rp -> rp.horseId == horse.getId());
                if (alreadyInAnotherStall) continue;

                if (isHorseInsideStallFootprint(horse, mainPos, backDir)) {
                    ThoroughbredData stallCheckData = ThoroughbredData.fromHorse(horse);
                    if (stallCheckData.isUnderTwoEquineYears() || stallCheckData.isRetired()) {
                        ejectIneligibleHorseFromStall(race, mainPos, backDir, horse,
                                stallCheckData.isFoal()
                                        ? ("§c" + stallCheckData.getName() + " is a Foal (<1yo) and cannot enter starting gates! Minimum racing age is 2 Equine Years.")
                                        : (stallCheckData.isYearling()
                                                ? ("§c" + stallCheckData.getName() + " is a Yearling (1yo) and cannot enter starting gates! Minimum racing age is 2 Equine Years.")
                                                : ("§c" + stallCheckData.getName() + " is retired for breeding and can no longer enter races!")));
                        continue;
                    }
                    int horseAge = stallCheckData.getEquineAgeYears(StudbookSavedData.get(race.level));
                    if (!matchesAgeRequirement(horseAge, race.ageRequirement)) {
                        ejectIneligibleHorseFromStall(race, mainPos, backDir, horse,
                                "§c§l" + ThoroughbredData.tr("[BARRED — AGE RESTRICTION!]", "[參賽限制 — 年齡不合！]", "[参赛限制 — 年龄不合！]", "[出走不可 — 馬齢不一致！]")
                                + " §e" + stallCheckData.getName() + " §c(" + horseAge + "yo) "
                                + ThoroughbredData.tr("does not meet the age requirement for this race", "不符合此賽事的年齡條件", "不符合此赛事的年龄条件", "はこのレースの馬齢条件を満たしていません")
                                + " (" + getAgeRequirementLabel(race.ageRequirement) + ")!");
                        continue;
                    }
                    if (!matchesGenderRequirement(stallCheckData.getGender(), race.genderRequirement)) {
                        ejectIneligibleHorseFromStall(race, mainPos, backDir, horse,
                                "§c§l" + ThoroughbredData.tr("[BARRED — GENDER RESTRICTION!]", "[參賽限制 — 性別不合！]", "[参赛限制 — 性别不合！]", "[出走不可 — 牝馬限定！]")
                                + " §e" + stallCheckData.getName() + " §c(" + ThoroughbredData.localizeGender(stallCheckData.getGender()) + ") "
                                + ThoroughbredData.tr("is barred from this race (Fillies & Mares Only)!", "無法參加此賽事（僅限雌馬/牝馬）！", "无法参加此赛事（仅限雌马/牝马）！", "はこのレースに出走できません（牝馬限定）！"));
                        continue;
                    }
                    // Debut / Maiden Paradox Rule: Prevent a horse that has already won a race from entering a Debut/Maiden race!
                    if (!race.isTestRace && StudbookSavedData.isDebutOrMaidenGrade(race.grade)) {
                        ThoroughbredData checkData = ThoroughbredData.fromHorse(horse);
                        if (checkData.hasWonDebutOrMaiden()) {
                            ejectDebutWinnerFromMaidenStall(race, mainPos, backDir, horse, checkData.getName());
                            continue;
                        }
                    }
                    Integer expectedHorseForThisGate = race.expectedGateAssignments.get(gateNum);
                    Integer thisHorseAssignedGate = null;
                    for (Map.Entry<Integer, Integer> e : race.expectedGateAssignments.entrySet()) {
                        if (e.getValue() == horse.getId()) {
                            thisHorseAssignedGate = e.getKey();
                            break;
                        }
                    }

                    // Only enforce strict wrong-stall ejection if the other stall/horse is actively contested
                    if ((expectedHorseForThisGate != null && expectedHorseForThisGate != horse.getId())
                            || (thisHorseAssignedGate != null && thisHorseAssignedGate != gateNum)) {
                        ejectWrongStallHorse(race, mainPos, backDir, horse, thisHorseAssignedGate);
                        continue;
                    }

                    // Step 19: Quirky Personality — Chance to refuse entering the starting gate (balking at the stall doors)!
                    if (stallCheckData.hasPersonality(ThoroughbredData.PERS_QUIRKY)) {
                        long nowTick = race.level.getGameTime();
                        long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
                        if (nowTick - lastWarn >= 80 && horse.getRandom().nextDouble() < 0.25) {
                            WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), nowTick);
                            ejectIneligibleHorseFromStall(race, mainPos, backDir, horse,
                                    "§3§l[QUIRKY GATE REFUSAL!] §e" + stallCheckData.getName()
                                    + " §fbalked at Stall #" + gateNum + " and refused to load! Try leading or riding it in again!");
                            horse.setStanding(true);
                            ThoroughbredRunEngine.getOrCreateState(horse).rearVisualTicks = 14;
                            race.level.playSound(null, mainPos, SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.1F, 1.0F);
                            continue;
                        }
                    }

                    // Lock horse into this stall and close the Rear Starting Gate behind it!
                    BlockPos rearPos = mainPos.relative(backDir, 3);
                    BlockState rearState = race.level.getBlockState(rearPos);
                    if (rearState.is(ModBlocks.REAR_STARTING_GATE.get())) {
                        StartingGateDoorBlock.setDoorOpenWithVerticalSync(race.level, rearPos, rearState, false);
                    }

                    if (horse.isLeashed()) {
                        horse.dropLeash(true, true);
                    }

                    horse.setStanding(false);
                    horse.teleportTo(stallCenter.x, stallCenter.y, stallCenter.z);
                    horse.setYRot(yaw);
                    horse.setYHeadRot(yaw);
                    horse.setYBodyRot(yaw);
                    horse.setDeltaMovement(0, 0, 0);

                    ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                    data.setInRace(true);
                    data.saveToHorse(horse);

                    ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
                    boolean wasInTrainingMode = runState.trainingMode;
                    runState.trainingMode = false; // Forcibly switch to Racing Mode upon entering a starting gate!
                    runState.trainingRefusalActive = false;
                    runState.trainingSessionTicks = 0;
                    runState.witsCropChain = 0;
                    runState.resetRaceStrategyState();

                    runState.inStartingGate = true;
                    runState.assignedGateNumber = gateNum;
                    runState.gateStallPos = mainPos;
                    runState.gateLockedYaw = yaw;
                    runState.horseYaw = yaw;
                    runState.prevHorseYaw = yaw;
                    runState.horseYawInitialized = true;
                    runState.wHeldBeforeGateOpen = runState.pressingW;
                    runState.penalizedPreWindThisStart = false;
                    runState.gateRearFreezeTicks = 0;
                    runState.rearVisualTicks = 0;
                    runState.gateBreakActive = false;
                    runState.gateBreakAccelMultiplier = 1.0;
                    runState.baseSpeedKmh = 0.0;
                    runState.displayedSpeedKmh = 0.0;
                    runState.distanceRun = 0.0;

                    RunnerProgress rp = new RunnerProgress();
                    rp.gateNumber = gateNum;
                    rp.horseId = horse.getId();
                    rp.horseUUID = horse.getUUID();
                    rp.horseName = data.getName();
                    rp.inGate = true;
                    if (horse.getControllingPassenger() instanceof Player p) {
                        rp.hadMountedJockey = true;
                        rp.lastJockeyUUID = p.getUUID();
                    }
                    rp.lastTrackX = stallCenter.x;
                    rp.lastTrackZ = stallCenter.z;
                    rp.legStartX = stallCenter.x;
                    rp.legStartZ = stallCenter.z;
                    race.runnersByGate.put(gateNum, rp);

                    race.level.playSound(null, mainPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.2F);

                    String statusSuffix = race.fanfarePlaying ? "Waiting for Fanfare to Finish" : "Ready for Gate Countdown";
                    if (horse.getControllingPassenger() instanceof ServerPlayer rider) {
                        if (wasInTrainingMode) {
                            rider.sendSystemMessage(Component.literal(
                                    "§e[Racing Mode Enforced] §fTraining Mode was automatically switched to Racing Mode upon entering Stall #" + gateNum + "!"));
                        }
                        rider.displayClientMessage(Component.literal(
                                "§6[Stall #" + gateNum + " Locked — " + statusSuffix + "]"), true);
                    }
                    if (race.starterPlayerUUID != null) {
                        ServerPlayer starter = race.level.getServer().getPlayerList().getPlayer(race.starterPlayerUUID);
                        if (starter != null) {
                            starter.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                                    "§a[Stall #%d Locked] §e%s §fis in the gate! (%d loaded)",
                                    gateNum, data.getName(), race.runnersByGate.size())), true);
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Keeps loaded horses (including unridden horses led into the stall by a lead) stationary and aligned
     * inside their stall in Racing Mode until the gates burst open!
     */
    private static void holdLoadedHorsesInStalls(ActiveRaceSession race) {
        Direction frontDir = race.baseType.gateFacing;
        Direction backDir = frontDir.getOpposite();
        float yaw = frontDir.toYRot();

        for (Map.Entry<Integer, RunnerProgress> entry : race.runnersByGate.entrySet()) {
            int gateNum = entry.getKey();
            RunnerProgress rp = entry.getValue();
            if (gateNum < 1 || gateNum > race.baseType.startGatePositions.size()) continue;

            Entity e = race.level.getEntity(rp.horseId);
            if (e instanceof Horse horse && horse.isAlive()) {
                if (horse.getControllingPassenger() instanceof Player p) {
                    rp.hadMountedJockey = true;
                    rp.lastJockeyUUID = p.getUUID();
                }
                BlockPos mainPos = race.baseType.startGatePositions.get(gateNum - 1);
                Vec3 stallCenter = Vec3.atBottomCenterOf(mainPos.relative(backDir, 2));
                if (horse.distanceToSqr(stallCenter) > 0.08) {
                    horse.teleportTo(stallCenter.x, stallCenter.y, stallCenter.z);
                }
                horse.setYRot(yaw);
                horse.setYHeadRot(yaw);
                horse.setYBodyRot(yaw);
                horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);

                ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
                runState.inStartingGate = true;
                runState.assignedGateNumber = gateNum;
                runState.gateLockedYaw = yaw;
                runState.horseYaw = yaw;
                runState.prevHorseYaw = yaw;
                runState.horseYawInitialized = true;
                runState.trainingMode = false;
                runState.trainingRefusalActive = false;
            }
        }
    }

    private static void ejectIneligibleHorseFromStall(ActiveRaceSession race, BlockPos mainPos, Direction backDir,
                                                      Horse horse, String warnMsg) {
        BlockPos ejectBlockPos = mainPos.relative(backDir, 4);
        Vec3 ejectPos = Vec3.atBottomCenterOf(ejectBlockPos);
        horse.setStanding(false);
        horse.teleportTo(ejectPos.x, ejectPos.y, ejectPos.z);

        Vec3 pushBack = Vec3.atLowerCornerOf(backDir.getNormal()).scale(0.45);
        horse.setDeltaMovement(pushBack.x, 0.1, pushBack.z);

        ThoroughbredRunEngine.HorseRunState hState = ThoroughbredRunEngine.getOrCreateState(horse);
        hState.stopAndResetSpeed();

        long now = race.level.getGameTime();
        long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
        if (now - lastWarn >= 30) {
            WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), now);
            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal(warnMsg), true);
                sp.sendSystemMessage(Component.literal(warnMsg));
            }
            race.level.playSound(null, mainPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.85F);
        }
    }

    private static void ejectDebutWinnerFromMaidenStall(ActiveRaceSession race, BlockPos mainPos, Direction backDir,
                                                        Horse horse, String horseName) {
        BlockPos ejectBlockPos = mainPos.relative(backDir, 4);
        Vec3 ejectPos = Vec3.atBottomCenterOf(ejectBlockPos);
        horse.setStanding(false);
        horse.teleportTo(ejectPos.x, ejectPos.y, ejectPos.z);

        Vec3 pushBack = Vec3.atLowerCornerOf(backDir.getNormal()).scale(0.45);
        horse.setDeltaMovement(pushBack.x, 0.1, pushBack.z);

        ThoroughbredRunEngine.HorseRunState hState = ThoroughbredRunEngine.getOrCreateState(horse);
        hState.stopAndResetSpeed();

        long now = race.level.getGameTime();
        long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
        if (now - lastWarn >= 30) {
            WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), now);
            String warnMsg = "§c" + horseName + " has already won a race and cannot enter a Debut/Maiden race!";
            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal(warnMsg), true);
                sp.sendSystemMessage(Component.literal(warnMsg));
            }
            race.level.playSound(null, mainPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.85F);
        }
    }

    private static void ejectWrongStallHorse(ActiveRaceSession race, BlockPos wrongMainPos, Direction backDir,
                                             Horse horse, Integer rightGateNum) {
        BlockPos ejectBlockPos = wrongMainPos.relative(backDir, 4);
        Vec3 ejectPos = Vec3.atBottomCenterOf(ejectBlockPos);
        horse.setStanding(false);
        horse.teleportTo(ejectPos.x, ejectPos.y, ejectPos.z);

        Vec3 pushBack = Vec3.atLowerCornerOf(backDir.getNormal()).scale(0.45);
        horse.setDeltaMovement(pushBack.x, 0.1, pushBack.z);

        ThoroughbredRunEngine.HorseRunState hState = ThoroughbredRunEngine.getOrCreateState(horse);
        hState.stopAndResetSpeed();

        long now = race.level.getGameTime();
        long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
        if (now - lastWarn >= 30) {
            WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), now);
            ThoroughbredData hData = ThoroughbredData.fromHorse(horse);
            String pronoun = "Female".equalsIgnoreCase(hData.getGender()) ? "She" : "He";
            String warnMsg = (rightGateNum != null)
                    ? String.format(Locale.ROOT, "§cThis is not %s's stall! %s belongs to Stall #%d!", hData.getName(), pronoun, rightGateNum)
                    : String.format(Locale.ROOT, "§cThis stall is reserved for another horse!");
            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal(warnMsg), true);
            }
            race.level.playSound(null, wrongMainPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.85F);
        }
    }

    private static void burstRaceGatesOpen(ActiveRaceSession race) {
        race.raceRunning = true;
        race.raceStartGameTime = race.level.getGameTime();

        // Step 15: Start Race Theme Music for players in the race area
        if (race.raceMusicFile != null && !"None".equalsIgnoreCase(race.raceMusicFile)) {
            for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
                ModNetwork.sendPlayRaceMusic(sp, race.raceMusicFile, race.raceMusicVolume, true);
            }
        }

        for (int i = 0; i < race.baseType.startGatePositions.size(); i++) {
            BlockPos frontPos = race.baseType.startGatePositions.get(i);
            BlockState frontState = race.level.getBlockState(frontPos);
            if (frontState.is(ModBlocks.MAIN_STARTING_GATE.get())) {
                StartingGateDoorBlock.setDoorOpenWithVerticalSync(race.level, frontPos, frontState, true);
            }
            race.level.playSound(null, frontPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4F, 1.2F);
            race.level.playSound(null, frontPos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2F, 1.5F);
            race.level.sendParticles(ParticleTypes.CRIT,
                    frontPos.getX() + 0.5, frontPos.getY() + 1.2, frontPos.getZ() + 0.5,
                    15, 0.4, 0.4, 0.4, 0.2);

            int gateNum = i + 1;
            RunnerProgress rp = race.runnersByGate.get(gateNum);
            if (rp == null) continue;

            Entity e = race.level.getEntity(rp.horseId);
            if (e instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                if (horse.getControllingPassenger() instanceof Player p) {
                    rp.hadMountedJockey = true;
                    rp.lastJockeyUUID = p.getUUID();
                }
                rp.hasMovedInRace = false;
                rp.midRaceHaltedTicks = 0;
                rp.lastTrackX = horse.getX();
                rp.lastTrackZ = horse.getZ();
                rp.legStartX = horse.getX();
                rp.legStartZ = horse.getZ();
                rp.legOdometerBlocks = 0.0;
                rp.totalOdometerBlocks = 0.0;
                rp.maxReachedMeters = 0.0;
                ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                state.inStartingGate = false;
                state.trainingMode = false;
                state.trainingRefusalActive = false;
                state.gateBreakTicks = 0;
                state.gateBreakAccelMultiplier = 1.0;
                state.resetRaceStrategyState();

                double rearChance = data.getStartingGateRearChance();
                if (rearChance > 0.0 && race.level.getRandom().nextDouble() < rearChance) {
                    state.gateRearFreezeTicks = 25;
                    state.rearVisualTicks = 22;
                    state.gateBreakActive = false;
                    state.wHeldBeforeGateOpen = false;
                    state.penalizedPreWindThisStart = true;
                    state.baseSpeedKmh = 0.0;
                    state.displayedSpeedKmh = 0.0;
                    state.gateBreakResult = "REARED IN STALL! (LATE BREAK)";
                    state.gateBreakResultColor = 0xFF3333;
                    state.gateBreakResultTicks = 55;
                    horse.setStanding(true);
                    race.level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.2F, 1.0F);
                } else if (state.pressingW || state.wHeldBeforeGateOpen) {
                    state.wHeldBeforeGateOpen = true;
                    state.penalizedPreWindThisStart = true;
                    state.gateBreakActive = false;
                    state.baseSpeedKmh = 0.0;
                    state.displayedSpeedKmh = 0.0;
                } else {
                    state.wHeldBeforeGateOpen = false;
                    state.penalizedPreWindThisStart = false;
                    state.gateBreakActive = true;
                }
            }
        }
    }

    private static void playInGameFanfareNotes1000x1000(ActiveRaceSession race, int elapsedTick) {
        float pitch = switch (elapsedTick) {
            case 0 -> 1.0F;
            case 6 -> 1.25F;
            case 12 -> 1.5F;
            case 20 -> 2.0F;
            case 30 -> 1.5F;
            case 38 -> 2.0F;
            default -> -1.0F;
        };
        if (pitch > 0.0F) {
            float vol = Mth.clamp(race.fanfareVolume / 100.0F, 0.05F, 1.0F);
            for (ServerPlayer sp : getPlayersIn1000x1000RaceArea(race)) {
                sp.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.RECORDS, vol, pitch);
                sp.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.RECORDS, vol * 0.85F, pitch);
            }
        }
    }

    private static void renderBuilderParticles(ServerLevel level, BaseBuildSession session) {
        for (BlockPos p : session.startGatePositions) {
            for (int dy = 0; dy <= 3; dy++) {
                level.sendParticles(GOLD_DUST, p.getX() + 0.5, p.getY() + dy + 0.2, p.getZ() + 0.5, 2, 0.15, 0.15, 0.15, 0.0);
            }
            if (session.gateFacingLocked) {
                BlockPos rear = p.relative(session.gateFacing.getOpposite(), 3);
                level.sendParticles(ParticleTypes.END_ROD, rear.getX() + 0.5, rear.getY() + 1.0, rear.getZ() + 0.5, 1, 0.05, 0.2, 0.05, 0.0);
            }
        }
        if (session.startLineAnchor != null) {
            BlockPos a = session.startLineAnchor;
            level.sendParticles(GOLD_DUST, a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5, 5, 0.2, 0.5, 0.2, 0.0);
        }

        if (session.checkpointAnchor != null) {
            BlockPos c = session.checkpointAnchor;
            level.sendParticles(CYAN_DUST, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 5, 0.2, 0.5, 0.2, 0.0);
        }
        for (CheckpointLine cp : session.checkpoints) {
            drawParticleLine(level, cp.startPos, cp.endPos, CYAN_DUST);
        }

        if (session.finishAnchor != null) {
            BlockPos f = session.finishAnchor;
            level.sendParticles(RED_DUST, f.getX() + 0.5, f.getY() + 1.0, f.getZ() + 0.5, 5, 0.2, 0.5, 0.2, 0.0);
        } else if (session.finishLineStart != null && session.finishLineEnd != null) {
            drawParticleLine(level, session.finishLineStart, session.finishLineEnd, RED_DUST);
        }
    }

    /**
     * Spawns synchronized 3D in-world pyrotechnics and celebratory particles above the starting gates
     * during a G1 race fanfare based on the race's configured G1 Special FX preset!
     */
    private static void tickG1WorldGateEffects(ActiveRaceSession race) {
        if (race == null || race.level == null || race.baseType == null || race.baseType.startGatePositions.isEmpty()) {
            return;
        }
        int[] preset = parseG1Preset(race.g1EffectPreset);
        int pyroIdx = preset[2];
        if (pyroIdx == 9) return; // Minimal (no extra world particles)

        List<BlockPos> gates = race.baseType.startGatePositions;
        BlockPos leftGate = gates.get(0);
        BlockPos rightGate = gates.get(gates.size() - 1);
        BlockPos midGate = gates.get(gates.size() / 2);

        double lx = leftGate.getX() + 0.5;
        double ly = leftGate.getY() + 3.4;
        double lz = leftGate.getZ() + 0.5;

        double rx = rightGate.getX() + 0.5;
        double ry = rightGate.getY() + 3.4;
        double rz = rightGate.getZ() + 0.5;

        double mx = midGate.getX() + 0.5;
        double my = midGate.getY() + 3.8;
        double mz = midGate.getZ() + 0.5;

        switch (pyroIdx) {
            case 0 -> { // Pyro Fountains & Confetti
                race.level.sendParticles(ParticleTypes.FIREWORK, lx, ly, lz, 5, 0.15, 0.45, 0.15, 0.12);
                race.level.sendParticles(ParticleTypes.FIREWORK, rx, ry, rz, 5, 0.15, 0.45, 0.15, 0.12);
                race.level.sendParticles(ParticleTypes.END_ROD, mx, my, mz, 3, 1.5, 0.3, 1.5, 0.04);
                race.level.sendParticles(GOLD_DUST, mx, my + 0.5, mz, 6, 2.0, 0.4, 2.0, 0.02);
            }
            case 1 -> { // Sakura Petal Storm
                race.level.sendParticles(ParticleTypes.CHERRY_LEAVES, mx, my + 1.2, mz, 8, 2.5, 0.5, 2.5, 0.05);
                race.level.sendParticles(ParticleTypes.END_ROD, lx, ly, lz, 2, 0.2, 0.3, 0.2, 0.03);
                race.level.sendParticles(ParticleTypes.END_ROD, rx, ry, rz, 2, 0.2, 0.3, 0.2, 0.03);
            }
            case 2 -> { // Thunder & Lightning
                race.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, lx, ly + 0.4, lz, 7, 0.25, 0.5, 0.25, 0.18);
                race.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, rx, ry + 0.4, rz, 7, 0.25, 0.5, 0.25, 0.18);
                race.level.sendParticles(ParticleTypes.GLOW, mx, my + 0.6, mz, 5, 1.8, 0.4, 1.8, 0.05);
            }
            case 3 -> { // Golden Stardust
                race.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, lx, ly + 0.3, lz, 4, 0.2, 0.4, 0.2, 0.08);
                race.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, rx, ry + 0.3, rz, 4, 0.2, 0.4, 0.2, 0.08);
                race.level.sendParticles(GOLD_DUST, mx, my + 0.6, mz, 8, 2.2, 0.5, 2.2, 0.02);
            }
            case 4 -> { // Grand Firework Finale
                race.level.sendParticles(ParticleTypes.FIREWORK, lx, ly + 0.8, lz, 8, 0.4, 0.6, 0.4, 0.16);
                race.level.sendParticles(ParticleTypes.FIREWORK, rx, ry + 0.8, rz, 8, 0.4, 0.6, 0.4, 0.16);
                race.level.sendParticles(ParticleTypes.GLOW, mx, my + 1.0, mz, 8, 2.4, 0.6, 2.4, 0.08);
            }
            case 5 -> { // Emerald Totem Cascade
                race.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mx, my + 0.8, mz, 8, 2.0, 0.5, 2.0, 0.10);
                race.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, lx, ly + 0.3, lz, 5, 0.4, 0.4, 0.4, 0.05);
                race.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, rx, ry + 0.3, rz, 5, 0.4, 0.4, 0.4, 0.05);
            }
            case 6 -> { // Soul Flame Aura
                race.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, lx, ly + 0.3, lz, 6, 0.25, 0.5, 0.25, 0.06);
                race.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, rx, ry + 0.3, rz, 6, 0.25, 0.5, 0.25, 0.06);
                race.level.sendParticles(CYAN_DUST, mx, my + 0.5, mz, 6, 2.0, 0.4, 2.0, 0.02);
            }
            case 7 -> { // Blizzard Diamond Dust
                race.level.sendParticles(ParticleTypes.SNOWFLAKE, mx, my + 1.0, mz, 10, 2.5, 0.6, 2.5, 0.04);
                race.level.sendParticles(ParticleTypes.END_ROD, lx, ly + 0.4, lz, 4, 0.2, 0.4, 0.2, 0.05);
                race.level.sendParticles(ParticleTypes.END_ROD, rx, ry + 0.4, rz, 4, 0.2, 0.4, 0.2, 0.05);
            }
            case 8 -> { // Crimson Dragon Breath
                race.level.sendParticles(ParticleTypes.DRAGON_BREATH, lx, ly + 0.4, lz, 5, 0.25, 0.4, 0.25, 0.05);
                race.level.sendParticles(ParticleTypes.DRAGON_BREATH, rx, ry + 0.4, rz, 5, 0.25, 0.4, 0.25, 0.05);
                race.level.sendParticles(RED_DUST, mx, my + 0.6, mz, 6, 2.0, 0.4, 2.0, 0.02);
            }
        }
    }

    private static void renderActiveRaceParticles(ActiveRaceSession race) {
        for (CheckpointLine cp : race.baseType.checkpoints) {
            drawParticleLine(race.level, cp.startPos, cp.endPos, CYAN_DUST);
        }
        drawParticleLine(race.level, race.baseType.finishLineStart, race.baseType.finishLineEnd, RED_DUST);
    }

    private static void drawParticleLine(ServerLevel level, BlockPos a, BlockPos b, DustParticleOptions particle) {
        if (a == null || b == null) return;
        double dist = Math.sqrt(a.distSqr(b));
        int steps = Mth.clamp((int) (dist * 2), 2, 100);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = Mth.lerp(t, a.getX() + 0.5, b.getX() + 0.5);
            double y = Mth.lerp(t, a.getY() + 0.3, b.getY() + 0.3);
            double z = Mth.lerp(t, a.getZ() + 0.5, b.getZ() + 0.5);
            level.sendParticles(particle, x, y, z, 1, 0.05, 0.25, 0.05, 0.0);
        }
    }

    private static double distToSegment2DSqr(double px, double pz, BlockPos a, BlockPos b) {
        double x1 = a.getX() + 0.5, z1 = a.getZ() + 0.5;
        double x2 = b.getX() + 0.5, z2 = b.getZ() + 0.5;
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 0.0001) {
            return (px - x1) * (px - x1) + (pz - z1) * (pz - z1);
        }
        double t = Mth.clamp(((px - x1) * dx + (pz - z1) * dz) / lenSq, 0.0, 1.0);
        double projX = x1 + t * dx;
        double projZ = z1 + t * dz;
        return (px - projX) * (px - projX) + (pz - projZ) * (pz - projZ);
    }

    // =========================================================================
    // 9. HANDLE RACING CONSOLE GUI PACKET ACTIONS (DELETE ERASES RECORDS, SWITCH BASE!)
    // =========================================================================
    public static void handleConsoleAction(ServerPlayer player, ModNetwork.RacingConsoleActionPacket msg) {
        if (!(player.level() instanceof ServerLevel level)) return;
        StudbookSavedData studbook = StudbookSavedData.get(level);
        String dim = level.dimension().location().toString();
        ConsoleRaceConfig cfg = studbook.getOrCreateRacingConsole(dim, msg.consolePos);

        switch (msg.actionType) {
            case ModNetwork.RacingConsoleActionPacket.ACTION_SET_COURSE_NAME -> {
                String clean = msg.strParam1 != null ? msg.strParam1.trim() : "";
                if (!clean.isEmpty()) {
                    cfg.racecourseName = clean;
                    studbook.setDirty();
                    player.sendSystemMessage(Component.literal("§a[Racing Console] Set Racecourse Name to: §6" + clean));
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_START_BASE_BUILD -> {
                startBaseBuildingMode(player, level, msg.consolePos);
                return;
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_CANCEL_BASE_BUILD -> {
                cancelBaseBuildingMode(player);
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_SAVE_BASE_TYPE -> {
                BaseBuildSession build = BUILD_SESSIONS.get(player.getUUID());
                if (build != null && build.isReadyToSave()) {
                    if (!build.checkpoints.isEmpty()) {
                        orientGatesTowardFirstCheckpoint(level, player, build, build.checkpoints.get(0));
                    }
                    RaceBaseType newBase = new RaceBaseType();
                    newBase.surface = "Turf".equalsIgnoreCase(msg.strParam1) ? "Turf" : "Dirt";
                    newBase.distanceMeters = Mth.clamp((msg.intParam1 / 100) * 100, 400, 10000);
                    newBase.startGatePositions = new ArrayList<>(build.startGatePositions);
                    newBase.lateralDir = build.lateralDir;
                    newBase.gateFacing = build.gateFacing;
                    newBase.checkpoints = new ArrayList<>(build.checkpoints);
                    newBase.finishLineStart = build.finishLineStart;
                    newBase.finishLineEnd = build.finishLineEnd;

                    cfg.baseTypes.add(newBase);
                    studbook.setDirty();

                    BUILD_SESSIONS.remove(player.getUUID());
                    removeTemporaryItem(player, ModItems.CHECKPOINT_MARKER.get());

                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§a§l[Race Base Type Saved!] §fCreated §6%s §f(%d Stalls, %d Checkpoints, Gates Facing %s)!",
                            newBase.getStructuredBottomName(cfg.racecourseName),
                            newBase.startGatePositions.size(),
                            newBase.checkpoints.size(),
                            newBase.gateFacing.getName().toUpperCase(Locale.ROOT))));
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_EDIT_BASE_TYPE -> {
                RaceBaseType base = cfg.getBaseById(msg.strParam1);
                if (base != null) {
                    base.surface = "Turf".equalsIgnoreCase(msg.strParam2) ? "Turf" : "Dirt";
                    base.distanceMeters = Mth.clamp((msg.intParam1 / 100) * 100, 400, 10000);
                    if (msg.intParam2 == 1) {
                        base.gateFacing = base.gateFacing.getOpposite();
                    }
                    studbook.setDirty();
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§a[Base Type Updated] §6%s §f(Gates Facing: §e%s§f)",
                            base.getStructuredBottomName(cfg.racecourseName),
                            base.gateFacing.getName().toUpperCase(Locale.ROOT))));
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_DELETE_BASE_TYPE -> {
                RaceBaseType base = cfg.getBaseById(msg.strParam1);
                if (base != null) {
                    int totalHorsesCleared = 0;
                    for (RaceDefinition r : base.races) {
                        totalHorsesCleared += studbook.eraseRaceRecordsGlobally(level.getServer(), r.raceId, r.raceName);
                    }
                    cfg.baseTypes.remove(base);
                    studbook.setDirty();
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§c[Base Type Deleted] §7Removed %s and erased its race records from %d horse(s).",
                            base.getStructuredBottomName(cfg.racecourseName), totalHorsesCleared)));
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_CREATE_RACE -> {
                RaceBaseType base = cfg.getBaseById(msg.strParam1);
                if (base != null && msg.strParam2 != null && !msg.strParam2.isBlank()) {
                    RaceDefinition r = new RaceDefinition();
                    r.raceName = msg.strParam2.trim();
                    r.grade = (msg.strParam3 != null && !msg.strParam3.isBlank()) ? msg.strParam3 : "G2";
                    r.fanfareFile = (msg.strParam4 != null && !msg.strParam4.isBlank()) ? msg.strParam4 : "Default";
                    r.fanfareVolume = msg.intParam1 > 0 ? Mth.clamp(msg.intParam1, 5, 100) : 50;
                    r.firstPlacePurse = Math.max(100L, (msg.longParam1 / 10L) * 10L);
                    r.requiredEarnings = Math.max(0L, msg.longParam2);
                    r.raceMusicFile = (msg.strParam5 != null && !msg.strParam5.isBlank()) ? msg.strParam5 : "None";
                    r.spurtMusicFile = (msg.strParam6 != null && !msg.strParam6.isBlank()) ? msg.strParam6 : "None";
                    r.raceMusicVolume = msg.intParam3 > 0 ? Mth.clamp(msg.intParam3, 5, 100) : 50;
                    r.spurtMusicVolume = msg.intParam4 > 0 ? Mth.clamp(msg.intParam4, 5, 100) : 50;
                    r.g1EffectPreset = (msg.strParam7 != null && !msg.strParam7.isBlank()) ? msg.strParam7 : DEFAULT_G1_PRESET;
                    r.ageRequirement = Mth.clamp(msg.intParam5, 0, 3);
                    r.genderRequirement = Mth.clamp(msg.intParam6, 0, 1);
                    r.raceSeriesTag = (msg.strParam8 != null) ? msg.strParam8.trim() : "";
                    if (!r.raceSeriesTag.isEmpty()) {
                        registerCustomSeriesTag(r.raceSeriesTag);
                    }
                    base.races.add(r);
                    studbook.setDirty();
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§a§l[Race Created!] §fAdded §e[%s] %s §f(Purse: §a$%,d§f, Req: §e$%,d§f) under §6%s§f!",
                            r.grade, r.raceName, r.firstPlacePurse, r.requiredEarnings, base.getStructuredBottomName(cfg.racecourseName))));
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_EDIT_RACE -> {
                RaceBaseType sourceBase = cfg.getBaseById(msg.strParam1);
                if (sourceBase != null) {
                    RaceDefinition targetRace = null;
                    for (RaceDefinition r : sourceBase.races) {
                        if (r.raceId.equals(msg.strParam2)) {
                            targetRace = r;
                            break;
                        }
                    }
                    if (targetRace != null) {
                        String targetBaseId = sourceBase.baseId;
                        if (msg.strParam3 != null && !msg.strParam3.isBlank()) {
                            String[] parts = msg.strParam3.split("\\|", 3);
                            if (parts.length == 3) {
                                targetBaseId = parts[0];
                                targetRace.grade = parts[1];
                                if (!parts[2].isBlank()) {
                                    targetRace.raceName = parts[2].trim();
                                }
                            } else if (parts.length == 2) {
                                targetRace.grade = parts[0];
                                if (!parts[1].isBlank()) {
                                    targetRace.raceName = parts[1].trim();
                                }
                            }
                        }
                        if (msg.strParam4 != null && !msg.strParam4.isBlank()) {
                            targetRace.fanfareFile = msg.strParam4;
                        }
                        if (msg.intParam1 > 0) {
                            targetRace.fanfareVolume = Mth.clamp(msg.intParam1, 5, 100);
                        }
                        targetRace.firstPlacePurse = Math.max(100L, (msg.longParam1 / 10L) * 10L);
                        targetRace.requiredEarnings = Math.max(0L, msg.longParam2);
                        if (msg.strParam5 != null && !msg.strParam5.isBlank()) targetRace.raceMusicFile = msg.strParam5;
                        if (msg.strParam6 != null && !msg.strParam6.isBlank()) targetRace.spurtMusicFile = msg.strParam6;
                        if (msg.intParam3 > 0) targetRace.raceMusicVolume = Mth.clamp(msg.intParam3, 5, 100);
                        if (msg.intParam4 > 0) targetRace.spurtMusicVolume = Mth.clamp(msg.intParam4, 5, 100);
                        if (msg.strParam7 != null && !msg.strParam7.isBlank()) targetRace.g1EffectPreset = msg.strParam7;
                        targetRace.ageRequirement = Mth.clamp(msg.intParam5, 0, 3);
                        targetRace.genderRequirement = Mth.clamp(msg.intParam6, 0, 1);
                        targetRace.raceSeriesTag = (msg.strParam8 != null) ? msg.strParam8.trim() : "";
                        if (!targetRace.raceSeriesTag.isEmpty()) {
                            registerCustomSeriesTag(targetRace.raceSeriesTag);
                        }

                        RaceBaseType destBase = cfg.getBaseById(targetBaseId);
                        if (destBase != null && !destBase.baseId.equals(sourceBase.baseId)) {
                            sourceBase.races.remove(targetRace);
                            destBase.races.add(targetRace);
                            studbook.setDirty();
                            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                    "§a[Race Updated & Moved!] §e[%s] %s §fmoved to §6%s §7(♪ %s @ %d%% — past stat card records kept as historical memo!)",
                                    targetRace.grade, targetRace.raceName,
                                    destBase.getStructuredBottomName(cfg.racecourseName),
                                    targetRace.fanfareFile, targetRace.fanfareVolume)));
                        } else {
                            studbook.setDirty();
                            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                    "§a[Race Updated] §e[%s] %s §7(♪ %s @ %d%% — past stat card records kept as historical memo!)",
                                    targetRace.grade, targetRace.raceName, targetRace.fanfareFile, targetRace.fanfareVolume)));
                        }
                    }
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_DELETE_RACE -> {
                RaceBaseType base = cfg.getBaseById(msg.strParam1);
                if (base != null) {
                    RaceDefinition deleted = null;
                    for (RaceDefinition r : base.races) {
                        if (r.raceId.equals(msg.strParam2)) {
                            deleted = r;
                            break;
                        }
                    }
                    if (deleted != null) {
                        base.races.remove(deleted);
                        int horsesUpdated = studbook.eraseRaceRecordsGlobally(level.getServer(), deleted.raceId, deleted.raceName);
                        studbook.setDirty();
                        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                                "§c[Race Deleted] §fDeleted §e%s §fand erased its race records from §6%d §fhorse(s).",
                                deleted.raceName, horsesUpdated)));
                    }
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_ASSIGN_STALL -> {
                Map<Integer, Integer> map = getPreRaceAssignments(level, msg.consolePos);
                int gateNum = msg.intParam1;
                int horseId = msg.intParam2;
                map.values().removeIf(id -> id == horseId);
                if (horseId == -1) {
                    map.remove(gateNum);
                } else {
                    map.put(gateNum, horseId);
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_AUTO_ASSIGN -> {
                RaceBaseType base = cfg.getBaseById(msg.strParam1);
                if (base != null) {
                    Map<Integer, Integer> map = getPreRaceAssignments(level, msg.consolePos);
                    map.clear();
                    List<Horse> nearby = findNearbyThoroughbreds(level, msg.consolePos, 64.0);
                    for (int i = 0; i < Math.min(base.startGatePositions.size(), nearby.size()); i++) {
                        map.put(i + 1, nearby.get(i).getId());
                    }
                }
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_START_TEST_RACE -> {
                startRaceFromConsole(player, level, msg.consolePos, msg.strParam1, null, true);
                return;
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_START_OFFICIAL_RACE -> {
                startRaceFromConsole(player, level, msg.consolePos, msg.strParam1, msg.strParam2, false);
                return;
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_CANCEL_ACTIVE_RACE -> {
                cancelActiveRace(level, msg.consolePos, true);
            }
            case ModNetwork.RacingConsoleActionPacket.ACTION_REFRESH_CONSOLE -> {
                // Re-scans fanfares folder and re-opens console GUI
            }
        }

        openRacingConsoleGui(player, level, msg.consolePos);
    }

    private static void giveTemporaryItemIfMissing(ServerPlayer player, ItemStack stackToGive) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(stackToGive.getItem())) {
                return;
            }
        }
        player.getInventory().placeItemBackInInventory(stackToGive);
    }

    private static void removeTemporaryItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
}