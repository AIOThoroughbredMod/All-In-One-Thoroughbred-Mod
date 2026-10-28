package com.aiothoroughbred;

import com.aiothoroughbred.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AIOThoroughbredMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++,
                OpenHorseScreenPacket.class,
                OpenHorseScreenPacket::encode,
                OpenHorseScreenPacket::decode,
                OpenHorseScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                RegisterHorsePacket.class,
                RegisterHorsePacket::encode,
                RegisterHorsePacket::decode,
                RegisterHorsePacket::handle);

        CHANNEL.registerMessage(packetId++,
                RegistrationResultPacket.class,
                RegistrationResultPacket::encode,
                RegistrationResultPacket::decode,
                RegistrationResultPacket::handle);

        CHANNEL.registerMessage(packetId++,
                UseCropPacket.class,
                UseCropPacket::encode,
                UseCropPacket::decode,
                UseCropPacket::handle);

        CHANNEL.registerMessage(packetId++,
                RiderInputPacket.class,
                RiderInputPacket::encode,
                RiderInputPacket::decode,
                RiderInputPacket::handle);

        CHANNEL.registerMessage(packetId++,
                ToggleTrainingModePacket.class,
                ToggleTrainingModePacket::encode,
                ToggleTrainingModePacket::decode,
                ToggleTrainingModePacket::handle);

        CHANNEL.registerMessage(packetId++,
                ToggleTrainingGearPacket.class,
                ToggleTrainingGearPacket::encode,
                ToggleTrainingGearPacket::decode,
                ToggleTrainingGearPacket::handle);

        CHANNEL.registerMessage(packetId++,
                UseStopwatchPacket.class,
                UseStopwatchPacket::encode,
                UseStopwatchPacket::decode,
                UseStopwatchPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenStarterConsolePacket.class,
                OpenStarterConsolePacket::encode,
                OpenStarterConsolePacket::decode,
                OpenStarterConsolePacket::handle);

        CHANNEL.registerMessage(packetId++,
                AssignGateHorsePacket.class,
                AssignGateHorsePacket::encode,
                AssignGateHorsePacket::decode,
                AssignGateHorsePacket::handle);

        CHANNEL.registerMessage(packetId++,
                LoadGateHorsePacket.class,
                LoadGateHorsePacket::encode,
                LoadGateHorsePacket::decode,
                LoadGateHorsePacket::handle);

        CHANNEL.registerMessage(packetId++,
                StartGateCountdownPacket.class,
                StartGateCountdownPacket::encode,
                StartGateCountdownPacket::decode,
                StartGateCountdownPacket::handle);

        CHANNEL.registerMessage(packetId++,
                EmergencyEjectPacket.class,
                EmergencyEjectPacket::encode,
                EmergencyEjectPacket::decode,
                EmergencyEjectPacket::handle);

        CHANNEL.registerMessage(packetId++,
                SyncHudPacket.class,
                SyncHudPacket::encode,
                SyncHudPacket::decode,
                SyncHudPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenDeathsScreenPacket.class,
                OpenDeathsScreenPacket::encode,
                OpenDeathsScreenPacket::decode,
                OpenDeathsScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                ReviveHorsePacket.class,
                ReviveHorsePacket::encode,
                ReviveHorsePacket::decode,
                ReviveHorsePacket::handle);

        CHANNEL.registerMessage(packetId++,
                RemoveDeathRecordPacket.class,
                RemoveDeathRecordPacket::encode,
                RemoveDeathRecordPacket::decode,
                RemoveDeathRecordPacket::handle);

        // Step 11B: Racing Console, Checkpoint Marker ([M]), Semaphore Flag & Fanfare Logo Packets
        CHANNEL.registerMessage(packetId++,
                CycleMarkerModePacket.class,
                CycleMarkerModePacket::encode,
                CycleMarkerModePacket::decode,
                CycleMarkerModePacket::handle);

        CHANNEL.registerMessage(packetId++,
                MarkerUndoPacket.class,
                MarkerUndoPacket::encode,
                MarkerUndoPacket::decode,
                MarkerUndoPacket::handle);

        CHANNEL.registerMessage(packetId++,
                WaveSemaphoreFlagPacket.class,
                WaveSemaphoreFlagPacket::encode,
                WaveSemaphoreFlagPacket::decode,
                WaveSemaphoreFlagPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenRacingConsolePacket.class,
                OpenRacingConsolePacket::encode,
                OpenRacingConsolePacket::decode,
                OpenRacingConsolePacket::handle);

        CHANNEL.registerMessage(packetId++,
                RacingConsoleActionPacket.class,
                RacingConsoleActionPacket::encode,
                RacingConsoleActionPacket::decode,
                RacingConsoleActionPacket::handle);

        CHANNEL.registerMessage(packetId++,
                RaceFanfareLogoPacket.class,
                RaceFanfareLogoPacket::encode,
                RaceFanfareLogoPacket::decode,
                RaceFanfareLogoPacket::handle);

        CHANNEL.registerMessage(packetId++,
                ClientFanfareFinishedPacket.class,
                ClientFanfareFinishedPacket::encode,
                ClientFanfareFinishedPacket::decode,
                ClientFanfareFinishedPacket::handle);

        // Step 13: Official-Style Live Race Leaderboard & Pack Radar Sync Packet
        CHANNEL.registerMessage(packetId++,
                RaceLeaderboardSyncPacket.class,
                RaceLeaderboardSyncPacket::encode,
                RaceLeaderboardSyncPacket::decode,
                RaceLeaderboardSyncPacket::handle);

        // Step 15: Custom Race Music & 600m Spurt Music Packets
        CHANNEL.registerMessage(packetId++,
                PlayRaceMusicPacket.class,
                PlayRaceMusicPacket::encode,
                PlayRaceMusicPacket::decode,
                PlayRaceMusicPacket::handle);

        CHANNEL.registerMessage(packetId++,
                PlaySpurtMusicPacket.class,
                PlaySpurtMusicPacket::encode,
                PlaySpurtMusicPacket::decode,
                PlaySpurtMusicPacket::handle);

        CHANNEL.registerMessage(packetId++,
                StopRaceMusicPacket.class,
                StopRaceMusicPacket::encode,
                StopRaceMusicPacket::decode,
                StopRaceMusicPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenScheduleScreenPacket.class,
                OpenScheduleScreenPacket::encode,
                OpenScheduleScreenPacket::decode,
                OpenScheduleScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                ScheduleGuiActionPacket.class,
                ScheduleGuiActionPacket::encode,
                ScheduleGuiActionPacket::decode,
                ScheduleGuiActionPacket::handle);

        // Step 17: Client Entity Condition Sync (Poor Prognosis lying-down pose, Fever particles & Pure White coat)
        CHANNEL.registerMessage(packetId++,
                SyncHorseConditionPacket.class,
                SyncHorseConditionPacket::encode,
                SyncHorseConditionPacket::decode,
                SyncHorseConditionPacket::handle);

        // Step 18: World Time Flow Pop-Up & 4-Generation Pedigree Tree GUI Packets
        CHANNEL.registerMessage(packetId++,
                OpenTimeFlowScreenPacket.class,
                OpenTimeFlowScreenPacket::encode,
                OpenTimeFlowScreenPacket::decode,
                OpenTimeFlowScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                SetTimeFlowPacket.class,
                SetTimeFlowPacket::encode,
                SetTimeFlowPacket::decode,
                SetTimeFlowPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenPedigreeScreenPacket.class,
                OpenPedigreeScreenPacket::encode,
                OpenPedigreeScreenPacket::decode,
                OpenPedigreeScreenPacket::handle);

        // Step 19: Gelding Shears Warning HUD & Confirmation Packets
        CHANNEL.registerMessage(packetId++,
                OpenGeldingScreenPacket.class,
                OpenGeldingScreenPacket::encode,
                OpenGeldingScreenPacket::decode,
                OpenGeldingScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                ConfirmGeldingPacket.class,
                ConfirmGeldingPacket::encode,
                ConfirmGeldingPacket::decode,
                ConfirmGeldingPacket::handle);

        // Step 21: Horse Owner Registration (/tb owner), Jockey Silks & Global Studbook (/tb studbook) Packets
        CHANNEL.registerMessage(packetId++,
                OpenOwnerScreenPacket.class,
                OpenOwnerScreenPacket::encode,
                OpenOwnerScreenPacket::decode,
                OpenOwnerScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                SaveOwnerProfilePacket.class,
                SaveOwnerProfilePacket::encode,
                SaveOwnerProfilePacket::decode,
                SaveOwnerProfilePacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenStudbookScreenPacket.class,
                OpenStudbookScreenPacket::encode,
                OpenStudbookScreenPacket::decode,
                OpenStudbookScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                RequestStudbookHorseCardPacket.class,
                RequestStudbookHorseCardPacket::encode,
                RequestStudbookHorseCardPacket::decode,
                RequestStudbookHorseCardPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenRaceResultsScreenPacket.class,
                OpenRaceResultsScreenPacket::encode,
                OpenRaceResultsScreenPacket::decode,
                OpenRaceResultsScreenPacket::handle);

        CHANNEL.registerMessage(packetId++,
                RaceResultsActionPacket.class,
                RaceResultsActionPacket::encode,
                RaceResultsActionPacket::decode,
                RaceResultsActionPacket::handle);
    }

    public static void broadcastHorseCondition(Horse horse, ThoroughbredData data) {
        if (horse == null || data == null || horse.level().isClientSide) return;
        SyncHorseConditionPacket pkt = new SyncHorseConditionPacket(
                horse.getId(),
                data.hasPoorPrognosis(),
                data.hasAilment(ThoroughbredData.AILMENT_FEVER),
                data.hasAilment(ThoroughbredData.AILMENT_LAMENESS) || data.hasActiveInjury(),
                data.isCareerEnded(),
                data.isPureWhiteCoat(),
                data.getOwnerSilkCode(),
                data.isWearingRaceSilk(),
                data.isAtDeathsDoor()
        );
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> horse), pkt);
    }

    public static void sendOpenTimeFlowScreen(ServerPlayer player, int currentMode, int customDays, boolean isFirstJoinPrompt) {
        if (player == null) return;
        boolean alreadyConfigured = (player.level() instanceof ServerLevel sl)
                && StudbookSavedData.get(sl).isTimeFlowConfigured();
        sendOpenTimeFlowScreen(player, currentMode, customDays, isFirstJoinPrompt, alreadyConfigured);
    }

    public static void sendOpenTimeFlowScreen(ServerPlayer player, int currentMode, int customDays,
                                              boolean isFirstJoinPrompt, boolean alreadyConfigured) {
        if (player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenTimeFlowScreenPacket(currentMode, customDays, isFirstJoinPrompt, alreadyConfigured));
    }

    public static void sendOpenPedigreeScreen(ServerPlayer player, CompoundTag horseDataTag) {
        if (player == null || horseDataTag == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenPedigreeScreenPacket(horseDataTag));
    }

    public static void sendOpenGeldingScreen(ServerPlayer player, int horseId, String horseName) {
        if (player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenGeldingScreenPacket(horseId, horseName != null ? horseName : "Stallion"));
    }

    public static void sendOpenStarterConsole(ServerPlayer player, BlockPos consolePos, StartingGateManager.ConsoleSession session, List<Horse> nearbyHorses) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenStarterConsolePacket(consolePos, session, nearbyHorses));
    }

    public static void sendOpenScreen(ServerPlayer player, int horseId, boolean alreadyRegistered, CompoundTag horseDataTag) {
        CompoundTag payload = (horseDataTag != null) ? horseDataTag.copy() : new CompoundTag();
        if (!alreadyRegistered && player != null && player.level() instanceof ServerLevel sl) {
            StudbookSavedData studbook = StudbookSavedData.get(sl);
            StudbookSavedData.HorseOwnerProfile prof = studbook.getOwnerProfile(player.getUUID());
            if (prof != null) {
                payload.putString("_OwnerFarmName", prof.farmName != null ? prof.farmName : "");
                payload.putString("_OwnerCrown1", prof.crownName1 != null ? prof.crownName1 : "");
                payload.putBoolean("_OwnerCrown1Prefix", prof.crown1Prefix);
                payload.putString("_OwnerCrown2", prof.crownName2 != null ? prof.crownName2 : "");
                payload.putBoolean("_OwnerCrown2Prefix", prof.crown2Prefix);
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenHorseScreenPacket(horseId, alreadyRegistered, payload));
    }

    public static void sendHudSync(ServerPlayer player, ThoroughbredRunEngine.ClientHudData hud) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncHudPacket(hud));
    }

    public static void sendOpenDeathsScreen(ServerPlayer player, List<StudbookSavedData.DeathRecord> deathRecords) {
        List<OpenDeathsScreenPacket.DeathEntry> entries = new ArrayList<>();
        if (deathRecords != null) {
            for (StudbookSavedData.DeathRecord r : deathRecords) {
                entries.add(new OpenDeathsScreenPacket.DeathEntry(
                        r.horseUUID != null ? r.horseUUID.toString() : "",
                        r.horseName,
                        r.ownerName,
                        r.deathDate,
                        r.deathReason,
                        r.permanentlyPurged,
                        r.revived
                ));
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDeathsScreenPacket(entries));
    }

    public static void sendOpenRacingConsole(ServerPlayer player, BlockPos consolePos,
                                             RacingManager.ConsoleRaceConfig cfg,
                                             RacingManager.BaseBuildSession buildSession,
                                             List<String> fanfares,
                                             List<String> music,
                                             List<Horse> nearbyHorses,
                                             Map<Integer, Integer> preAssignments,
                                             boolean activeRaceRunning) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenRacingConsolePacket(consolePos, cfg, buildSession, fanfares, music, nearbyHorses, preAssignments, activeRaceRunning));
    }

    public static void sendRaceFanfareLogo(ServerPlayer player, String grade, String raceName,
                                           String bottomBaseType, String fanfareFile, int fanfareVolume, int durationTicks) {
        sendRaceFanfareLogo(player, grade, raceName, bottomBaseType, fanfareFile, fanfareVolume, durationTicks, RacingManager.DEFAULT_G1_PRESET);
    }

    public static void sendRaceFanfareLogo(ServerPlayer player, String grade, String raceName,
                                           String bottomBaseType, String fanfareFile, int fanfareVolume, int durationTicks,
                                           String g1EffectPreset) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RaceFanfareLogoPacket(grade, raceName, bottomBaseType, fanfareFile, fanfareVolume, durationTicks, g1EffectPreset));
    }

    public static void sendRaceLeaderboardSync(ServerPlayer player, RaceLeaderboardSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendPlayRaceMusic(ServerPlayer player, String musicFile, int volume, boolean loop) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayRaceMusicPacket(musicFile, volume, loop));
    }

    public static void sendPlaySpurtMusic(ServerPlayer player, String spurtFile, int volume) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlaySpurtMusicPacket(spurtFile, volume));
    }

    public static void sendStopRaceMusic(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StopRaceMusicPacket(true));
    }

    public static void sendStopMainRaceMusicOnly(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StopRaceMusicPacket(false));
    }

    public static void sendOpenScheduleScreen(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel sl)) return;
        StudbookSavedData studbook = StudbookSavedData.get(sl);
        boolean isAdmin = player.hasPermissions(2);
        long balance = studbook.getBalance(player.getUUID());
        String playerName = player.getName().getString();

        List<String> ownedHorses = new ArrayList<>();
        String riddenHorseName = "";
        if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            ThoroughbredData rd = ThoroughbredData.fromHorse(ridden);
            if (rd.isOwnedBy(player.getUUID())) {
                riddenHorseName = rd.getName();
                ownedHorses.add(riddenHorseName);
            }
        }
        for (StudbookSavedData.HorseBackupRecord rec : studbook.getAllLivingHorses()) {
            if (rec.ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
                if (!ownedHorses.contains(rec.horseName)) {
                    ownedHorses.add(rec.horseName);
                }
            }
        }

        List<OpenScheduleScreenPacket.ConsoleRaceOption> consoleRaces = new ArrayList<>();
        for (RacingManager.ConsoleRaceConfig cfg : studbook.getAllRacingConsoles()) {
            for (RacingManager.RaceBaseType b : cfg.baseTypes) {
                String trackSummary = b.getStructuredBottomName(cfg.racecourseName);
                for (RacingManager.RaceDefinition r : b.races) {
                    consoleRaces.add(new OpenScheduleScreenPacket.ConsoleRaceOption(
                            r.raceId, r.raceName, r.grade, trackSummary, r.firstPlacePurse, r.requiredEarnings, r.ageRequirement, r.genderRequirement, r.raceSeriesTag
                    ));
                }
            }
        }

        List<StudbookSavedData.ScheduledRace> schedules = new ArrayList<>(studbook.getAllScheduledRaces());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenScheduleScreenPacket(isAdmin, balance, playerName, riddenHorseName, ownedHorses, consoleRaces, schedules));
    }

    public static class OpenHorseScreenPacket {
        private final int horseId;
        private final boolean alreadyRegistered;
        private final CompoundTag horseDataTag;

        public OpenHorseScreenPacket(int horseId, boolean alreadyRegistered, CompoundTag horseDataTag) {
            this.horseId = horseId;
            this.alreadyRegistered = alreadyRegistered;
            this.horseDataTag = horseDataTag;
        }

        public static void encode(OpenHorseScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.horseId);
            buf.writeBoolean(msg.alreadyRegistered);
            buf.writeNbt(msg.horseDataTag);
        }

        public static OpenHorseScreenPacket decode(FriendlyByteBuf buf) {
            int horseId = buf.readInt();
            boolean alreadyRegistered = buf.readBoolean();
            CompoundTag tag = buf.readNbt();
            return new OpenHorseScreenPacket(horseId, alreadyRegistered, tag != null ? tag : new CompoundTag());
        }

        public static void handle(OpenHorseScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenScreen(msg.horseId, msg.alreadyRegistered, msg.horseDataTag))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RegisterHorsePacket {
        private final int horseId;
        private final String name;
        private final int languageOrdinal;
        private final String gender;

        public RegisterHorsePacket(int horseId, String name, int languageOrdinal, String gender) {
            this.horseId = horseId;
            this.name = name;
            this.languageOrdinal = languageOrdinal;
            this.gender = gender;
        }

        public static void encode(RegisterHorsePacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.horseId);
            buf.writeUtf(msg.name, 64);
            buf.writeInt(msg.languageOrdinal);
            buf.writeUtf(msg.gender, 16);
        }

        public static RegisterHorsePacket decode(FriendlyByteBuf buf) {
            return new RegisterHorsePacket(
                    buf.readInt(),
                    buf.readUtf(64),
                    buf.readInt(),
                    buf.readUtf(16)
            );
        }

        public static void handle(RegisterHorsePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
                    return;
                }

                Entity entity = serverLevel.getEntity(msg.horseId);
                if (!(entity instanceof Horse horse) || !horse.isTamed()) {
                    return;
                }

                boolean alreadyRegistered = ThoroughbredData.isThoroughbred(horse);
                ThoroughbredData existingData = alreadyRegistered ? ThoroughbredData.fromHorse(horse) : null;
                if (horse.isBaby() || (existingData != null && existingData.isUnderTwoEquineYears())) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new RegistrationResultPacket(false, "Horses must reach 2 Equine Years old before registering!", msg.horseId, new CompoundTag()));
                    return;
                }
                if (alreadyRegistered && (existingData == null || !existingData.isBredFoalPendingName())) {
                    return;
                }

                NameValidator.NamingLanguage[] langs = NameValidator.NamingLanguage.values();
                NameValidator.NamingLanguage lang = (msg.languageOrdinal >= 0 && msg.languageOrdinal < langs.length)
                        ? langs[msg.languageOrdinal]
                        : NameValidator.NamingLanguage.ENGLISH;

                NameValidator.ValidationResult validation = NameValidator.validate(msg.name, lang);
                if (!validation.isValid()) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new RegistrationResultPacket(false, validation.getWarningMessage(), msg.horseId, new CompoundTag()));
                    return;
                }

                StudbookSavedData studbook = StudbookSavedData.get(serverLevel);
                if (!studbook.isTimeFlowConfigured()) {
                    player.sendSystemMessage(Component.literal("§cYou cannot register horses until the World Equine Time Flow has been chosen! Use /tb timeflow first."));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new RegistrationResultPacket(
                                    false,
                                    "World Equine Time Flow is not set yet! Use /tb timeflow first!",
                                    msg.horseId,
                                    new CompoundTag()
                            ));
                    return;
                }
                if (!studbook.isRegisteredHorseOwner(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§cYou are not an official horse owner yet! Register via /tb owner to become one first!"));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new RegistrationResultPacket(
                                    false,
                                    "You are not an official horse owner yet! Register via /tb owner to become one first!",
                                    msg.horseId,
                                    new CompoundTag()
                            ));
                    return;
                }
                if (studbook.isNameTaken(msg.name)) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new RegistrationResultPacket(
                                    false,
                                    "Another horse has already registered this name in the Studbook! Use a unique one!",
                                    msg.horseId,
                                    new CompoundTag()
                            ));
                    return;
                }

                String formattedOwner = studbook.getFormattedOwnerName(player.getUUID(), player.getName().getString());
                String silkCode = studbook.getOwnerSilkCompactCode(player.getUUID());
                String currentCrop = studbook.computeCurrentCropTag();

                ThoroughbredData data;
                if (alreadyRegistered && existingData != null && existingData.isBredFoalPendingName()) {
                    // Step 18: Preserve bred foal's 100 HP genetics, biological sex, 4-generation pedigree, traits & coat!
                    studbook.renameHorseRecord(existingData.getName(), msg.name);
                    data = existingData;
                    data.setName(msg.name);
                    data.setBredFoalPendingName(false);
                    if (!data.hasOwner()) {
                        data.setOwner(player.getUUID(), formattedOwner);
                    } else if (data.isOwnedBy(player.getUUID())) {
                        data.setOwner(player.getUUID(), formattedOwner);
                    }
                    data.setOwnerSilkCode(silkCode);
                    if (data.getCropTag().isBlank()) {
                        data.setCropTag(currentCrop);
                    }
                    data.saveToHorse(horse);
                } else {
                    studbook.registerName(msg.name);
                    String cleanGender = "Female".equalsIgnoreCase(msg.gender) ? "Female" : "Male";
                    data = new ThoroughbredData();
                    data.generateRandomGenetics(horse.getRandom(), msg.name, cleanGender);
                    data.setOwner(player.getUUID(), formattedOwner);
                    data.setOwnerSilkCode(silkCode);
                    data.setCropTag(currentCrop);
                    data.saveToHorse(horse);
                }

                horse.setCustomName(Component.literal(msg.name));
                studbook.updateHorseRecord(horse, data);
                studbook.archiveHorsePedigree(data);
                broadcastHorseCondition(horse, data);

                if (!player.getAbilities().instabuild) {
                    ItemStack mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
                    ItemStack offItem = player.getItemInHand(InteractionHand.OFF_HAND);
                    if (mainItem.is(ModItems.THOROUGHBRED_REGISTRY_FORM.get())) {
                        mainItem.shrink(1);
                    } else if (offItem.is(ModItems.THOROUGHBRED_REGISTRY_FORM.get())) {
                        offItem.shrink(1);
                    }
                }

                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new RegistrationResultPacket(true, "", msg.horseId, data.toNBT()));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RegistrationResultPacket {
        private final boolean success;
        private final String errorMessage;
        private final int horseId;
        private final CompoundTag horseDataTag;

        public RegistrationResultPacket(boolean success, String errorMessage, int horseId, CompoundTag horseDataTag) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.horseId = horseId;
            this.horseDataTag = horseDataTag;
        }

        public static void encode(RegistrationResultPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.success);
            buf.writeUtf(msg.errorMessage, 256);
            buf.writeInt(msg.horseId);
            buf.writeNbt(msg.horseDataTag);
        }

        public static RegistrationResultPacket decode(FriendlyByteBuf buf) {
            boolean success = buf.readBoolean();
            String error = buf.readUtf(256);
            int horseId = buf.readInt();
            CompoundTag tag = buf.readNbt();
            return new RegistrationResultPacket(success, error, horseId, tag != null ? tag : new CompoundTag());
        }

        public static void handle(RegistrationResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleRegistrationResult(msg.success, msg.errorMessage, msg.horseId, msg.horseDataTag))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class UseCropPacket {
        public UseCropPacket() {}
        public static void encode(UseCropPacket msg, FriendlyByteBuf buf) {}
        public static UseCropPacket decode(FriendlyByteBuf buf) { return new UseCropPacket(); }
        public static void handle(UseCropPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ThoroughbredRunEngine.handleServerCropSignal(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class UseStopwatchPacket {
        public UseStopwatchPacket() {}
        public static void encode(UseStopwatchPacket msg, FriendlyByteBuf buf) {}
        public static UseStopwatchPacket decode(FriendlyByteBuf buf) { return new UseStopwatchPacket(); }
        public static void handle(UseStopwatchPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    AIOThoroughbredMod.handleServerStopwatchClick(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RiderInputPacket {
        private final boolean pressingW;
        private final boolean pressingS;
        private final boolean pressingA;
        private final boolean pressingD;
        private final boolean clientJumping;
        private final float jumpScale;
        private final float horseYaw;

        public RiderInputPacket(boolean pressingW, boolean pressingS, boolean pressingA, boolean pressingD,
                                boolean clientJumping, float jumpScale, float horseYaw) {
            this.pressingW = pressingW;
            this.pressingS = pressingS;
            this.pressingA = pressingA;
            this.pressingD = pressingD;
            this.clientJumping = clientJumping;
            this.jumpScale = jumpScale;
            this.horseYaw = horseYaw;
        }

        public static void encode(RiderInputPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.pressingW);
            buf.writeBoolean(msg.pressingS);
            buf.writeBoolean(msg.pressingA);
            buf.writeBoolean(msg.pressingD);
            buf.writeBoolean(msg.clientJumping);
            buf.writeFloat(msg.jumpScale);
            buf.writeFloat(msg.horseYaw);
        }

        public static RiderInputPacket decode(FriendlyByteBuf buf) {
            return new RiderInputPacket(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat()
            );
        }

        public static void handle(RiderInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ThoroughbredRunEngine.handleRiderInput(
                            player,
                            msg.pressingW,
                            msg.pressingS,
                            msg.pressingA,
                            msg.pressingD,
                            msg.clientJumping,
                            msg.jumpScale,
                            msg.horseYaw
                    );
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ToggleTrainingModePacket {
        public ToggleTrainingModePacket() {}
        public static void encode(ToggleTrainingModePacket msg, FriendlyByteBuf buf) {}
        public static ToggleTrainingModePacket decode(FriendlyByteBuf buf) { return new ToggleTrainingModePacket(); }
        public static void handle(ToggleTrainingModePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ThoroughbredRunEngine.handleToggleTrainingMode(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ToggleTrainingGearPacket {
        private final int horseId;
        private final boolean weightedTack;
        private final boolean blinkersHood;

        public ToggleTrainingGearPacket(int horseId, boolean weightedTack, boolean blinkersHood) {
            this.horseId = horseId;
            this.weightedTack = weightedTack;
            this.blinkersHood = blinkersHood;
        }

        public static void encode(ToggleTrainingGearPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.horseId);
            buf.writeBoolean(msg.weightedTack);
            buf.writeBoolean(msg.blinkersHood);
        }

        public static ToggleTrainingGearPacket decode(FriendlyByteBuf buf) {
            return new ToggleTrainingGearPacket(buf.readInt(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(ToggleTrainingGearPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
                    return;
                }
                Entity entity = serverLevel.getEntity(msg.horseId);
                if (entity instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                    ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                    data.setWeightedTack(msg.weightedTack);
                    data.setBlinkersHood(msg.blinkersHood);
                    data.saveToHorse(horse);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncHudPacket {
        private final ThoroughbredRunEngine.ClientHudData data;

        public SyncHudPacket(ThoroughbredRunEngine.ClientHudData data) {
            this.data = data;
        }

        public static void encode(SyncHudPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.data.ridingThoroughbred);
            buf.writeUtf(msg.data.horseName, 64);
            buf.writeDouble(msg.data.baseSpeedKmh);
            buf.writeDouble(msg.data.displayedSpeedKmh);
            buf.writeDouble(msg.data.bigStrideBonusKmh);
            buf.writeDouble(msg.data.distanceMeters);
            buf.writeDouble(msg.data.normalCapKmh);
            buf.writeDouble(msg.data.cropMaxKmh);
            buf.writeDouble(msg.data.unlockedCropCapKmh);
            buf.writeDouble(msg.data.currentStamina);
            buf.writeDouble(msg.data.maxStamina);
            buf.writeDouble(msg.data.currentGuts);
            buf.writeDouble(msg.data.maxGuts);
            buf.writeInt(msg.data.zoneCode);
            buf.writeInt(msg.data.statedMinDist);
            buf.writeInt(msg.data.maxDist);
            buf.writeBoolean(msg.data.cropActive);
            buf.writeBoolean(msg.data.gutsActive);
            buf.writeBoolean(msg.data.exhaustedCapLocked);
            buf.writeBoolean(msg.data.unevenBreathing);
            buf.writeBoolean(msg.data.panicking);
            buf.writeBoolean(msg.data.onSlope);

            buf.writeBoolean(msg.data.trainingMode);
            buf.writeBoolean(msg.data.trainingRefusalActive);
            buf.writeBoolean(msg.data.inPool);
            buf.writeBoolean(msg.data.weightedTack);
            buf.writeBoolean(msg.data.blinkersHood);
            buf.writeInt(msg.data.trainingSessionTicks);
            buf.writeInt(msg.data.witsCropChain);

            buf.writeInt(msg.data.levelUpStatCode);
            buf.writeInt(msg.data.levelUpFlashTicks);

            buf.writeFloat(msg.data.hunger);
            buf.writeFloat(msg.data.thirst);
            buf.writeFloat(msg.data.fatigue);
            buf.writeFloat(msg.data.bond);
            buf.writeFloat(msg.data.mood);
            buf.writeUtf(msg.data.temperament != null ? msg.data.temperament : "Normal", 32);

            buf.writeInt(msg.data.spdCur);
            buf.writeInt(msg.data.spdMax);
            buf.writeFloat(msg.data.spdCurExp);
            buf.writeFloat(msg.data.spdReqExp);
            buf.writeBoolean(msg.data.gainingSpd);

            buf.writeInt(msg.data.stmCur);
            buf.writeInt(msg.data.stmMax);
            buf.writeFloat(msg.data.stmCurExp);
            buf.writeFloat(msg.data.stmReqExp);
            buf.writeBoolean(msg.data.gainingStm);

            buf.writeInt(msg.data.pwrCur);
            buf.writeInt(msg.data.pwrMax);
            buf.writeFloat(msg.data.pwrCurExp);
            buf.writeFloat(msg.data.pwrReqExp);
            buf.writeBoolean(msg.data.gainingPwr);

            buf.writeInt(msg.data.gutsCur);
            buf.writeInt(msg.data.gutsMax);
            buf.writeFloat(msg.data.gutsCurExp);
            buf.writeFloat(msg.data.gutsReqExp);
            buf.writeBoolean(msg.data.gainingGuts);

            buf.writeInt(msg.data.witsCur);
            buf.writeInt(msg.data.witsMax);
            buf.writeFloat(msg.data.witsCurExp);
            buf.writeFloat(msg.data.witsReqExp);
            buf.writeBoolean(msg.data.gainingWits);

            buf.writeBoolean(msg.data.inStartingGate);
            buf.writeInt(msg.data.gateNumber);
            buf.writeFloat(msg.data.gateLockedYaw);
            buf.writeBoolean(msg.data.gateBreakActive);
            buf.writeUtf(msg.data.gateBreakResult != null ? msg.data.gateBreakResult : "", 64);
            buf.writeInt(msg.data.gateBreakResultColor);
            buf.writeInt(msg.data.gateBreakResultTicks);
            buf.writeUtf(msg.data.activeStrategyTag != null ? msg.data.activeStrategyTag : "", 64);
            buf.writeUtf(msg.data.conditionHudTag != null ? msg.data.conditionHudTag : "", 96);
            buf.writeBoolean(msg.data.careerEnded);
            buf.writeBoolean(msg.data.hasCorneringMaestro);
            buf.writeUtf(msg.data.personality != null ? msg.data.personality : "Competitive", 32);
        }

        public static SyncHudPacket decode(FriendlyByteBuf buf) {
            ThoroughbredRunEngine.ClientHudData d = new ThoroughbredRunEngine.ClientHudData();
            d.ridingThoroughbred = buf.readBoolean();
            d.horseName = buf.readUtf(64);
            d.baseSpeedKmh = buf.readDouble();
            d.displayedSpeedKmh = buf.readDouble();
            d.bigStrideBonusKmh = buf.readDouble();
            d.distanceMeters = buf.readDouble();
            d.normalCapKmh = buf.readDouble();
            d.cropMaxKmh = buf.readDouble();
            d.unlockedCropCapKmh = buf.readDouble();
            d.currentStamina = buf.readDouble();
            d.maxStamina = buf.readDouble();
            d.currentGuts = buf.readDouble();
            d.maxGuts = buf.readDouble();
            d.zoneCode = buf.readInt();
            d.statedMinDist = buf.readInt();
            d.maxDist = buf.readInt();
            d.cropActive = buf.readBoolean();
            d.gutsActive = buf.readBoolean();
            d.exhaustedCapLocked = buf.readBoolean();
            d.unevenBreathing = buf.readBoolean();
            d.panicking = buf.readBoolean();
            d.onSlope = buf.readBoolean();

            d.trainingMode = buf.readBoolean();
            d.trainingRefusalActive = buf.readBoolean();
            d.inPool = buf.readBoolean();
            d.weightedTack = buf.readBoolean();
            d.blinkersHood = buf.readBoolean();
            d.trainingSessionTicks = buf.readInt();
            d.witsCropChain = buf.readInt();

            d.levelUpStatCode = buf.readInt();
            d.levelUpFlashTicks = buf.readInt();

            d.hunger = buf.readFloat();
            d.thirst = buf.readFloat();
            d.fatigue = buf.readFloat();
            d.bond = buf.readFloat();
            d.mood = buf.readFloat();
            d.temperament = buf.readUtf(32);

            d.spdCur = buf.readInt();
            d.spdMax = buf.readInt();
            d.spdCurExp = buf.readFloat();
            d.spdReqExp = buf.readFloat();
            d.gainingSpd = buf.readBoolean();

            d.stmCur = buf.readInt();
            d.stmMax = buf.readInt();
            d.stmCurExp = buf.readFloat();
            d.stmReqExp = buf.readFloat();
            d.gainingStm = buf.readBoolean();

            d.pwrCur = buf.readInt();
            d.pwrMax = buf.readInt();
            d.pwrCurExp = buf.readFloat();
            d.pwrReqExp = buf.readFloat();
            d.gainingPwr = buf.readBoolean();

            d.gutsCur = buf.readInt();
            d.gutsMax = buf.readInt();
            d.gutsCurExp = buf.readFloat();
            d.gutsReqExp = buf.readFloat();
            d.gainingGuts = buf.readBoolean();

            d.witsCur = buf.readInt();
            d.witsMax = buf.readInt();
            d.witsCurExp = buf.readFloat();
            d.witsReqExp = buf.readFloat();
            d.gainingWits = buf.readBoolean();

            d.inStartingGate = buf.readBoolean();
            d.gateNumber = buf.readInt();
            d.gateLockedYaw = buf.readFloat();
            d.gateBreakActive = buf.readBoolean();
            d.gateBreakResult = buf.readUtf(64);
            d.gateBreakResultColor = buf.readInt();
            d.gateBreakResultTicks = buf.readInt();
            d.activeStrategyTag = buf.readUtf(64);
            d.conditionHudTag = buf.readUtf(96);
            d.careerEnded = buf.readBoolean();
            d.hasCorneringMaestro = buf.readBoolean();
            d.personality = buf.readUtf(32);

            return new SyncHudPacket(d);
        }

        public static void handle(SyncHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ThoroughbredRunEngine.updateClientHud(msg.data))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenStarterConsolePacket {
        public final BlockPos consolePos;
        public final List<StallEntry> stalls;
        public final List<HorseEntry> horses;
        public final boolean countdownActive;
        public final int countdownTicks;

        public static class StallEntry {
            public int gateNumber;
            public BlockPos frontGatePos;
            public int assignedHorseId;
            public String assignedHorseName;
            public boolean inPosition;

            public StallEntry(int gateNumber, BlockPos frontGatePos, int assignedHorseId, String assignedHorseName, boolean inPosition) {
                this.gateNumber = gateNumber;
                this.frontGatePos = frontGatePos;
                this.assignedHorseId = assignedHorseId;
                this.assignedHorseName = assignedHorseName != null ? assignedHorseName : "";
                this.inPosition = inPosition;
            }
        }

        public static class HorseEntry {
            public int horseId;
            public String name;
            public double distance;
            public boolean isRidden;
            public String riderName;
            public int equineAgeYears;
            public String gender;

            public HorseEntry(int horseId, String name, double distance, boolean isRidden, String riderName) {
                this(horseId, name, distance, isRidden, riderName, 3, "Male");
            }

            public HorseEntry(int horseId, String name, double distance, boolean isRidden, String riderName, int equineAgeYears, String gender) {
                this.horseId = horseId;
                this.name = name != null ? name : "";
                this.distance = distance;
                this.isRidden = isRidden;
                this.riderName = riderName != null ? riderName : "";
                this.equineAgeYears = Math.max(0, equineAgeYears);
                this.gender = gender != null ? gender : "Male";
            }
        }

        public OpenStarterConsolePacket(BlockPos consolePos, StartingGateManager.ConsoleSession session, List<Horse> nearbyHorses) {
            this.consolePos = consolePos;
            this.stalls = new ArrayList<>();
            if (session != null) {
                for (StartingGateManager.StallData s : session.stalls) {
                    this.stalls.add(new StallEntry(s.gateNumber, s.frontGatePos, s.assignedHorseId, s.assignedHorseName, s.horseInPosition));
                }
                this.countdownActive = session.countdownActive;
                this.countdownTicks = session.countdownTicks;
            } else {
                this.countdownActive = false;
                this.countdownTicks = 0;
            }
            this.horses = new ArrayList<>();
            if (nearbyHorses != null) {
                for (Horse h : nearbyHorses) {
                    ThoroughbredData d = ThoroughbredData.fromHorse(h);
                    double dist = Math.sqrt(h.distanceToSqr(Vec3.atCenterOf(consolePos)));
                    boolean ridden = h.getControllingPassenger() != null;
                    String rider = ridden ? h.getControllingPassenger().getName().getString() : "";
                    this.horses.add(new HorseEntry(h.getId(), d.getName(), dist, ridden, rider, d.getEquineAgeYears(), d.getGender()));
                }
            }
        }

        public OpenStarterConsolePacket(BlockPos consolePos, List<StallEntry> stalls, List<HorseEntry> horses, boolean countdownActive, int countdownTicks) {
            this.consolePos = consolePos;
            this.stalls = stalls;
            this.horses = horses;
            this.countdownActive = countdownActive;
            this.countdownTicks = countdownTicks;
        }

        public static void encode(OpenStarterConsolePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
            buf.writeBoolean(msg.countdownActive);
            buf.writeInt(msg.countdownTicks);

            buf.writeInt(msg.stalls.size());
            for (StallEntry s : msg.stalls) {
                buf.writeInt(s.gateNumber);
                buf.writeBlockPos(s.frontGatePos);
                buf.writeInt(s.assignedHorseId);
                buf.writeUtf(s.assignedHorseName, 64);
                buf.writeBoolean(s.inPosition);
            }

            buf.writeInt(msg.horses.size());
            for (HorseEntry h : msg.horses) {
                buf.writeInt(h.horseId);
                buf.writeUtf(h.name, 64);
                buf.writeDouble(h.distance);
                buf.writeBoolean(h.isRidden);
                buf.writeUtf(h.riderName, 64);
                buf.writeInt(h.equineAgeYears);
                buf.writeUtf(h.gender != null ? h.gender : "Male", 32);
            }
        }

        public static OpenStarterConsolePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean counting = buf.readBoolean();
            int ticks = buf.readInt();

            int stallCount = buf.readInt();
            List<StallEntry> stalls = new ArrayList<>();
            for (int i = 0; i < stallCount; i++) {
                int gNum = buf.readInt();
                BlockPos gPos = buf.readBlockPos();
                int hId = buf.readInt();
                String hName = buf.readUtf(64);
                boolean inPos = buf.readBoolean();
                stalls.add(new StallEntry(gNum, gPos, hId, hName, inPos));
            }

            int horseCount = buf.readInt();
            List<HorseEntry> horses = new ArrayList<>();
            for (int i = 0; i < horseCount; i++) {
                int hId = buf.readInt();
                String name = buf.readUtf(64);
                double dist = buf.readDouble();
                boolean ridden = buf.readBoolean();
                String rider = buf.readUtf(64);
                int ageYears = buf.readableBytes() >= 4 ? buf.readInt() : 3;
                String gender = buf.readableBytes() > 0 ? buf.readUtf(32) : "Male";
                horses.add(new HorseEntry(hId, name, dist, ridden, rider, ageYears, gender));
            }

            return new OpenStarterConsolePacket(pos, stalls, horses, counting, ticks);
        }

        public static void handle(OpenStarterConsolePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenStarterConsole(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class AssignGateHorsePacket {
        private final BlockPos consolePos;
        private final int gateNumber;
        private final int horseId;

        public AssignGateHorsePacket(BlockPos consolePos, int gateNumber, int horseId) {
            this.consolePos = consolePos;
            this.gateNumber = gateNumber;
            this.horseId = horseId;
        }

        public static void encode(AssignGateHorsePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
            buf.writeInt(msg.gateNumber);
            buf.writeInt(msg.horseId);
        }

        public static AssignGateHorsePacket decode(FriendlyByteBuf buf) {
            return new AssignGateHorsePacket(buf.readBlockPos(), buf.readInt(), buf.readInt());
        }

        public static void handle(AssignGateHorsePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return;
                StartingGateManager.assignHorseToStall(serverLevel, msg.consolePos, msg.gateNumber, msg.horseId);
                StartingGateManager.ConsoleSession session = StartingGateManager.getOrCreateSession(serverLevel, msg.consolePos);
                List<Horse> nearby = StartingGateManager.findNearbyThoroughbreds(serverLevel, msg.consolePos);
                sendOpenStarterConsole(player, msg.consolePos, session, nearby);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class LoadGateHorsePacket {
        private final BlockPos consolePos;
        private final int gateNumber;

        public LoadGateHorsePacket(BlockPos consolePos, int gateNumber) {
            this.consolePos = consolePos;
            this.gateNumber = gateNumber;
        }

        public static void encode(LoadGateHorsePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
            buf.writeInt(msg.gateNumber);
        }

        public static LoadGateHorsePacket decode(FriendlyByteBuf buf) {
            return new LoadGateHorsePacket(buf.readBlockPos(), buf.readInt());
        }

        public static void handle(LoadGateHorsePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return;
                StartingGateManager.loadHorseIntoStall(serverLevel, msg.consolePos, msg.gateNumber);
                StartingGateManager.ConsoleSession session = StartingGateManager.getOrCreateSession(serverLevel, msg.consolePos);
                List<Horse> nearby = StartingGateManager.findNearbyThoroughbreds(serverLevel, msg.consolePos);
                sendOpenStarterConsole(player, msg.consolePos, session, nearby);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class StartGateCountdownPacket {
        private final BlockPos consolePos;

        public StartGateCountdownPacket(BlockPos consolePos) {
            this.consolePos = consolePos;
        }

        public static void encode(StartGateCountdownPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
        }

        public static StartGateCountdownPacket decode(FriendlyByteBuf buf) {
            return new StartGateCountdownPacket(buf.readBlockPos());
        }

        public static void handle(StartGateCountdownPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return;
                StartingGateManager.startCountdown(serverLevel, msg.consolePos);
                StartingGateManager.ConsoleSession session = StartingGateManager.getOrCreateSession(serverLevel, msg.consolePos);
                List<Horse> nearby = StartingGateManager.findNearbyThoroughbreds(serverLevel, msg.consolePos);
                sendOpenStarterConsole(player, msg.consolePos, session, nearby);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class EmergencyEjectPacket {
        private final BlockPos consolePos;

        public EmergencyEjectPacket(BlockPos consolePos) {
            this.consolePos = consolePos;
        }

        public static void encode(EmergencyEjectPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
        }

        public static EmergencyEjectPacket decode(FriendlyByteBuf buf) {
            return new EmergencyEjectPacket(buf.readBlockPos());
        }

        public static void handle(EmergencyEjectPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return;
                StartingGateManager.emergencyEject(serverLevel, msg.consolePos);
                StartingGateManager.ConsoleSession session = StartingGateManager.getOrCreateSession(serverLevel, msg.consolePos);
                List<Horse> nearby = StartingGateManager.findNearbyThoroughbreds(serverLevel, msg.consolePos);
                sendOpenStarterConsole(player, msg.consolePos, session, nearby);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // DECEASED HORSES GUI PACKETS (/tb deaths)
    // =========================================================================
    public static class OpenDeathsScreenPacket {
        public final List<DeathEntry> entries;

        public static class DeathEntry {
            public String horseUUID;
            public String horseName;
            public String ownerName;
            public String deathDate;
            public String deathReason;
            public boolean permanentlyPurged;
            public boolean revived;

            public DeathEntry(String horseUUID, String horseName, String ownerName,
                              String deathDate, String deathReason, boolean permanentlyPurged, boolean revived) {
                this.horseUUID = horseUUID != null ? horseUUID : "";
                this.horseName = horseName != null ? horseName : "";
                this.ownerName = ownerName != null ? ownerName : "Unclaimed";
                this.deathDate = deathDate != null ? deathDate : "";
                this.deathReason = deathReason != null ? deathReason : "";
                this.permanentlyPurged = permanentlyPurged;
                this.revived = revived;
            }
        }

        public OpenDeathsScreenPacket(List<DeathEntry> entries) {
            this.entries = entries != null ? entries : new ArrayList<>();
        }

        public static void encode(OpenDeathsScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entries.size());
            for (DeathEntry d : msg.entries) {
                buf.writeUtf(d.horseUUID, 64);
                buf.writeUtf(d.horseName, 64);
                buf.writeUtf(d.ownerName, 64);
                buf.writeUtf(d.deathDate, 64);
                buf.writeUtf(d.deathReason, 256);
                buf.writeBoolean(d.permanentlyPurged);
                buf.writeBoolean(d.revived);
            }
        }

        public static OpenDeathsScreenPacket decode(FriendlyByteBuf buf) {
            int count = buf.readInt();
            List<DeathEntry> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(new DeathEntry(
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(256),
                        buf.readBoolean(),
                        buf.readBoolean()
                ));
            }
            return new OpenDeathsScreenPacket(list);
        }

        public static void handle(OpenDeathsScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenDeathsScreen(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ReviveHorsePacket {
        private final String horseUUID;

        public ReviveHorsePacket(String horseUUID) {
            this.horseUUID = horseUUID != null ? horseUUID : "";
        }

        public static void encode(ReviveHorsePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.horseUUID, 64);
        }

        public static ReviveHorsePacket decode(FriendlyByteBuf buf) {
            return new ReviveHorsePacket(buf.readUtf(64));
        }

        public static void handle(ReviveHorsePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    ModCommands.reviveHorseByUuidString(player, msg.horseUUID, true);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RemoveDeathRecordPacket {
        private final String horseUUID;

        public RemoveDeathRecordPacket(String horseUUID) {
            this.horseUUID = horseUUID != null ? horseUUID : "";
        }

        public static void encode(RemoveDeathRecordPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.horseUUID, 64);
        }

        public static RemoveDeathRecordPacket decode(FriendlyByteBuf buf) {
            return new RemoveDeathRecordPacket(buf.readUtf(64));
        }

        public static void handle(RemoveDeathRecordPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    ModCommands.removePurgedDeathLogByUuidString(player, msg.horseUUID);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // STEP 11B: RACING CONSOLE, CHECKPOINT MARKER & SEMAPHORE FLAG PACKETS
    // =========================================================================
    public static class CycleMarkerModePacket {
        public CycleMarkerModePacket() {}
        public static void encode(CycleMarkerModePacket msg, FriendlyByteBuf buf) {}
        public static CycleMarkerModePacket decode(FriendlyByteBuf buf) { return new CycleMarkerModePacket(); }
        public static void handle(CycleMarkerModePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    RacingManager.cycleMarkerMode(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class MarkerUndoPacket {
        public MarkerUndoPacket() {}
        public static void encode(MarkerUndoPacket msg, FriendlyByteBuf buf) {}
        public static MarkerUndoPacket decode(FriendlyByteBuf buf) { return new MarkerUndoPacket(); }
        public static void handle(MarkerUndoPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    RacingManager.handleMarkerLeftClickUndo(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class WaveSemaphoreFlagPacket {
        public WaveSemaphoreFlagPacket() {}
        public static void encode(WaveSemaphoreFlagPacket msg, FriendlyByteBuf buf) {}
        public static WaveSemaphoreFlagPacket decode(FriendlyByteBuf buf) { return new WaveSemaphoreFlagPacket(); }
        public static void handle(WaveSemaphoreFlagPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    RacingManager.handleSemaphoreFlagWave(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenRacingConsolePacket {
        public final BlockPos consolePos;
        public final RacingManager.ConsoleRaceConfig config;
        public final boolean inBuildMode;
        public final boolean buildReadyToSave;
        public final int buildGateCount;
        public final int buildCheckpointCount;
        public final boolean buildHasFinishLine;
        public final List<String> availableFanfares;
        public final List<String> availableMusic;
        public final List<OpenStarterConsolePacket.HorseEntry> nearbyHorses;
        public final Map<Integer, Integer> preAssignments;
        public final boolean activeRaceRunning;

        public OpenRacingConsolePacket(BlockPos consolePos,
                                       RacingManager.ConsoleRaceConfig cfg,
                                       RacingManager.BaseBuildSession buildSession,
                                       List<String> fanfares,
                                       List<String> music,
                                       List<Horse> horses,
                                       Map<Integer, Integer> preAssignments,
                                       boolean activeRaceRunning) {
            this.consolePos = consolePos;
            this.config = cfg != null ? cfg : new RacingManager.ConsoleRaceConfig();
            this.inBuildMode = buildSession != null;
            this.buildReadyToSave = buildSession != null && buildSession.isReadyToSave();
            this.buildGateCount = buildSession != null ? buildSession.startGatePositions.size() : 0;
            this.buildCheckpointCount = buildSession != null ? buildSession.checkpoints.size() : 0;
            this.buildHasFinishLine = buildSession != null && buildSession.finishLineStart != null && buildSession.finishLineEnd != null;
            this.availableFanfares = (fanfares != null && !fanfares.isEmpty()) ? new ArrayList<>(fanfares) : new ArrayList<>(List.of("Default"));
            this.availableMusic = (music != null && !music.isEmpty()) ? new ArrayList<>(music) : new ArrayList<>(List.of("None"));
            this.nearbyHorses = new ArrayList<>();
            if (horses != null) {
                for (Horse h : horses) {
                    ThoroughbredData d = ThoroughbredData.fromHorse(h);
                    double dist = Math.sqrt(h.distanceToSqr(Vec3.atCenterOf(consolePos)));
                    boolean ridden = h.getControllingPassenger() != null;
                    String rider = ridden ? h.getControllingPassenger().getName().getString() : "";
                    this.nearbyHorses.add(new OpenStarterConsolePacket.HorseEntry(h.getId(), d.getName(), dist, ridden, rider, d.getEquineAgeYears(), d.getGender()));
                }
            }
            this.preAssignments = preAssignments != null ? new HashMap<>(preAssignments) : new HashMap<>();
            this.activeRaceRunning = activeRaceRunning;
        }

        public OpenRacingConsolePacket(BlockPos consolePos,
                                       RacingManager.ConsoleRaceConfig config,
                                       boolean inBuildMode,
                                       boolean buildReadyToSave,
                                       int buildGateCount,
                                       int buildCheckpointCount,
                                       boolean buildHasFinishLine,
                                       List<String> availableFanfares,
                                       List<String> availableMusic,
                                       List<OpenStarterConsolePacket.HorseEntry> nearbyHorses,
                                       Map<Integer, Integer> preAssignments,
                                       boolean activeRaceRunning) {
            this.consolePos = consolePos;
            this.config = config != null ? config : new RacingManager.ConsoleRaceConfig();
            this.inBuildMode = inBuildMode;
            this.buildReadyToSave = buildReadyToSave;
            this.buildGateCount = buildGateCount;
            this.buildCheckpointCount = buildCheckpointCount;
            this.buildHasFinishLine = buildHasFinishLine;
            this.availableFanfares = (availableFanfares != null && !availableFanfares.isEmpty())
                    ? availableFanfares : new ArrayList<>(List.of("Default"));
            this.availableMusic = (availableMusic != null && !availableMusic.isEmpty())
                    ? availableMusic : new ArrayList<>(List.of("None"));
            this.nearbyHorses = nearbyHorses != null ? nearbyHorses : new ArrayList<>();
            this.preAssignments = preAssignments != null ? preAssignments : new HashMap<>();
            this.activeRaceRunning = activeRaceRunning;
        }

        public static void encode(OpenRacingConsolePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
            buf.writeNbt(msg.config.toNBT());
            buf.writeBoolean(msg.inBuildMode);
            buf.writeBoolean(msg.buildReadyToSave);
            buf.writeInt(msg.buildGateCount);
            buf.writeInt(msg.buildCheckpointCount);
            buf.writeBoolean(msg.buildHasFinishLine);

            buf.writeInt(msg.availableFanfares.size());
            for (String f : msg.availableFanfares) {
                buf.writeUtf(f, 128);
            }

            buf.writeInt(msg.availableMusic.size());
            for (String m : msg.availableMusic) {
                buf.writeUtf(m, 128);
            }

            buf.writeInt(msg.nearbyHorses.size());
            for (OpenStarterConsolePacket.HorseEntry h : msg.nearbyHorses) {
                buf.writeInt(h.horseId);
                buf.writeUtf(h.name, 64);
                buf.writeDouble(h.distance);
                buf.writeBoolean(h.isRidden);
                buf.writeUtf(h.riderName, 64);
                buf.writeInt(h.equineAgeYears);
                buf.writeUtf(h.gender != null ? h.gender : "Male", 32);
            }

            buf.writeInt(msg.preAssignments.size());
            for (Map.Entry<Integer, Integer> e : msg.preAssignments.entrySet()) {
                buf.writeInt(e.getKey());
                buf.writeInt(e.getValue());
            }

            buf.writeBoolean(msg.activeRaceRunning);
        }

        public static OpenRacingConsolePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            CompoundTag cfgTag = buf.readNbt();
            RacingManager.ConsoleRaceConfig cfg = cfgTag != null
                    ? RacingManager.ConsoleRaceConfig.fromNBT(cfgTag)
                    : new RacingManager.ConsoleRaceConfig();

            boolean inBuild = buf.readBoolean();
            boolean readySave = buf.readBoolean();
            int gCount = buf.readInt();
            int cpCount = buf.readInt();
            boolean hasFin = buf.readBoolean();

            int fCount = buf.readInt();
            List<String> ffs = new ArrayList<>();
            for (int i = 0; i < fCount; i++) {
                ffs.add(buf.readUtf(128));
            }

            int mCount = buf.readInt();
            List<String> mus = new ArrayList<>();
            for (int i = 0; i < mCount; i++) {
                mus.add(buf.readUtf(128));
            }

            int hCount = buf.readInt();
            List<OpenStarterConsolePacket.HorseEntry> hs = new ArrayList<>();
            for (int i = 0; i < hCount; i++) {
                int hId = buf.readInt();
                String hName = buf.readUtf(64);
                double dist = buf.readDouble();
                boolean ridden = buf.readBoolean();
                String rName = buf.readUtf(64);
                int aYears = buf.readableBytes() >= 4 ? buf.readInt() : 2;
                String gen = buf.readableBytes() > 0 ? buf.readUtf(32) : "Male";
                hs.add(new OpenStarterConsolePacket.HorseEntry(hId, hName, dist, ridden, rName, aYears, gen));
            }

            int assignCount = buf.readInt();
            Map<Integer, Integer> assigns = new HashMap<>();
            for (int i = 0; i < assignCount; i++) {
                assigns.put(buf.readInt(), buf.readInt());
            }

            boolean activeRun = buf.readBoolean();

            return new OpenRacingConsolePacket(
                    pos, cfg, inBuild, readySave, gCount, cpCount, hasFin,
                    ffs, mus, hs, assigns, activeRun
            );
        }

        public static void handle(OpenRacingConsolePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenRacingConsole(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RacingConsoleActionPacket {
        public static final int ACTION_SET_COURSE_NAME = 1;
        public static final int ACTION_START_BASE_BUILD = 2;
        public static final int ACTION_CANCEL_BASE_BUILD = 3;
        public static final int ACTION_SAVE_BASE_TYPE = 4;
        public static final int ACTION_EDIT_BASE_TYPE = 5;
        public static final int ACTION_DELETE_BASE_TYPE = 6;
        public static final int ACTION_CREATE_RACE = 7;
        public static final int ACTION_EDIT_RACE = 8;
        public static final int ACTION_DELETE_RACE = 9;
        public static final int ACTION_ASSIGN_STALL = 10;
        public static final int ACTION_AUTO_ASSIGN = 11;
        public static final int ACTION_START_TEST_RACE = 12;
        public static final int ACTION_START_OFFICIAL_RACE = 13;
        public static final int ACTION_CANCEL_ACTIVE_RACE = 14;
        public static final int ACTION_REFRESH_CONSOLE = 15;

        public final BlockPos consolePos;
        public final int actionType;
        public final String strParam1;
        public final String strParam2;
        public final String strParam3;
        public final String strParam4;
        public final int intParam1;
        public final int intParam2;
        public final long longParam1; // firstPlacePurse
        public final long longParam2; // requiredEarnings
        public final String strParam5; // raceMusicFile
        public final String strParam6; // spurtMusicFile
        public final int intParam3;   // raceMusicVol
        public final int intParam4;   // spurtMusicVol
        public final String strParam7; // g1EffectPreset
        public final int intParam5;   // ageRequirement (0..4)
        public final int intParam6;   // genderRequirement (0..1)
        public final String strParam8; // raceSeriesTag

        public RacingConsoleActionPacket(BlockPos consolePos, int actionType,
                                         String strParam1, String strParam2, String strParam3, String strParam4,
                                         int intParam1, int intParam2) {
            this(consolePos, actionType, strParam1, strParam2, strParam3, strParam4, intParam1, intParam2,
                    5000L, 0L, "None", "None", 50, 50, RacingManager.DEFAULT_G1_PRESET);
        }

        public RacingConsoleActionPacket(BlockPos consolePos, int actionType,
                                         String strParam1, String strParam2, String strParam3, String strParam4,
                                         int intParam1, int intParam2,
                                         long longParam1, long longParam2,
                                         String strParam5, String strParam6,
                                         int intParam3, int intParam4) {
            this(consolePos, actionType, strParam1, strParam2, strParam3, strParam4, intParam1, intParam2,
                    longParam1, longParam2, strParam5, strParam6, intParam3, intParam4, RacingManager.DEFAULT_G1_PRESET);
        }

        public RacingConsoleActionPacket(BlockPos consolePos, int actionType,
                                         String strParam1, String strParam2, String strParam3, String strParam4,
                                         int intParam1, int intParam2,
                                         long longParam1, long longParam2,
                                         String strParam5, String strParam6,
                                         int intParam3, int intParam4,
                                         String strParam7) {
            this(consolePos, actionType, strParam1, strParam2, strParam3, strParam4, intParam1, intParam2,
                    longParam1, longParam2, strParam5, strParam6, intParam3, intParam4, strParam7, 0, 0, "");
        }

        public RacingConsoleActionPacket(BlockPos consolePos, int actionType,
                                         String strParam1, String strParam2, String strParam3, String strParam4,
                                         int intParam1, int intParam2,
                                         long longParam1, long longParam2,
                                         String strParam5, String strParam6,
                                         int intParam3, int intParam4,
                                         String strParam7,
                                         int intParam5, int intParam6, String strParam8) {
            this.consolePos = consolePos;
            this.actionType = actionType;
            this.strParam1 = strParam1 != null ? strParam1 : "";
            this.strParam2 = strParam2 != null ? strParam2 : "";
            this.strParam3 = strParam3 != null ? strParam3 : "";
            this.strParam4 = strParam4 != null ? strParam4 : "";
            this.intParam1 = intParam1;
            this.intParam2 = intParam2;
            this.longParam1 = longParam1;
            this.longParam2 = longParam2;
            this.strParam5 = strParam5 != null ? strParam5 : "None";
            this.strParam6 = strParam6 != null ? strParam6 : "None";
            this.intParam3 = intParam3;
            this.intParam4 = intParam4;
            this.strParam7 = (strParam7 != null && !strParam7.isBlank()) ? strParam7 : RacingManager.DEFAULT_G1_PRESET;
            this.intParam5 = intParam5;
            this.intParam6 = intParam6;
            this.strParam8 = strParam8 != null ? strParam8 : "";
        }

        public static void encode(RacingConsoleActionPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.consolePos);
            buf.writeInt(msg.actionType);
            buf.writeUtf(msg.strParam1, 128);
            buf.writeUtf(msg.strParam2, 128);
            buf.writeUtf(msg.strParam3, 128);
            buf.writeUtf(msg.strParam4, 128);
            buf.writeInt(msg.intParam1);
            buf.writeInt(msg.intParam2);
            buf.writeLong(msg.longParam1);
            buf.writeLong(msg.longParam2);
            buf.writeUtf(msg.strParam5, 128);
            buf.writeUtf(msg.strParam6, 128);
            buf.writeInt(msg.intParam3);
            buf.writeInt(msg.intParam4);
            buf.writeUtf(msg.strParam7, 128);
            buf.writeInt(msg.intParam5);
            buf.writeInt(msg.intParam6);
            buf.writeUtf(msg.strParam8 != null ? msg.strParam8 : "", 128);
        }

        public static RacingConsoleActionPacket decode(FriendlyByteBuf buf) {
            BlockPos cPos = buf.readBlockPos();
            int aType = buf.readInt();
            String s1 = buf.readUtf(128);
            String s2 = buf.readUtf(128);
            String s3 = buf.readUtf(128);
            String s4 = buf.readUtf(128);
            int i1 = buf.readInt();
            int i2 = buf.readInt();
            long l1 = buf.readLong();
            long l2 = buf.readLong();
            String s5 = buf.readUtf(128);
            String s6 = buf.readUtf(128);
            int i3 = buf.readInt();
            int i4 = buf.readInt();
            String s7 = buf.readUtf(128);
            int i5 = buf.readableBytes() >= 4 ? buf.readInt() : 0;
            int i6 = buf.readableBytes() >= 4 ? buf.readInt() : 0;
            String s8 = buf.readableBytes() > 0 ? buf.readUtf(128) : "";
            return new RacingConsoleActionPacket(cPos, aType, s1, s2, s3, s4, i1, i2, l1, l2, s5, s6, i3, i4, s7, i5, i6, s8);
        }

        public static void handle(RacingConsoleActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    RacingManager.handleConsoleAction(player, msg);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RaceFanfareLogoPacket {
        public final String grade;
        public final String raceName;
        public final String bottomBaseType;
        public final String fanfareFile;
        public final int fanfareVolume;
        public final int durationTicks;
        public final String g1EffectPreset;

        public RaceFanfareLogoPacket(String grade, String raceName, String bottomBaseType,
                                     String fanfareFile, int fanfareVolume, int durationTicks) {
            this(grade, raceName, bottomBaseType, fanfareFile, fanfareVolume, durationTicks, RacingManager.DEFAULT_G1_PRESET);
        }

        public RaceFanfareLogoPacket(String grade, String raceName, String bottomBaseType,
                                     String fanfareFile, int fanfareVolume, int durationTicks,
                                     String g1EffectPreset) {
            this.grade = grade != null ? grade : "Open";
            this.raceName = raceName != null ? raceName : "";
            this.bottomBaseType = bottomBaseType != null ? bottomBaseType : "";
            this.fanfareFile = fanfareFile != null ? fanfareFile : "Default";
            this.fanfareVolume = fanfareVolume;
            this.durationTicks = durationTicks;
            this.g1EffectPreset = (g1EffectPreset != null && !g1EffectPreset.isBlank()) ? g1EffectPreset : RacingManager.DEFAULT_G1_PRESET;
        }

        public static void encode(RaceFanfareLogoPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.grade, 32);
            buf.writeUtf(msg.raceName, 64);
            buf.writeUtf(msg.bottomBaseType, 128);
            buf.writeUtf(msg.fanfareFile, 128);
            buf.writeInt(msg.fanfareVolume);
            buf.writeInt(msg.durationTicks);
            buf.writeUtf(msg.g1EffectPreset, 128);
        }

        public static RaceFanfareLogoPacket decode(FriendlyByteBuf buf) {
            return new RaceFanfareLogoPacket(
                    buf.readUtf(32),
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(128)
            );
        }

        public static void handle(RaceFanfareLogoPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        int clipTicks = RacingManager.playClientFanfareAudio(msg.fanfareFile, msg.fanfareVolume);
                        int effectiveTicks = clipTicks > 0 ? clipTicks : msg.durationTicks;
                        ThoroughbredRunEngine.triggerClientRaceLogoDisplay(
                                msg.grade, msg.raceName, msg.bottomBaseType, effectiveTicks, clipTicks > 0, msg.g1EffectPreset);
                    })
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ClientFanfareFinishedPacket {
        public ClientFanfareFinishedPacket() {}
        public static void encode(ClientFanfareFinishedPacket msg, FriendlyByteBuf buf) {}
        public static ClientFanfareFinishedPacket decode(FriendlyByteBuf buf) { return new ClientFanfareFinishedPacket(); }
        public static void handle(ClientFanfareFinishedPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    RacingManager.handleClientFanfareFinished(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // STEP 13: Official-STYLE LIVE RACE LEADERBOARD & VERTICAL PACK RADAR PACKET
    // =========================================================================
    public static class RaceLeaderboardSyncPacket {
        public final boolean active;
        public final String grade;
        public final String raceName;
        public final String trackSummary;
        public final int totalDistanceMeters;
        public final int totalStalls;
        public final int racePhase; // 0 = Gate Loading, 1 = Gates Armed, 2 = Live Race, 3 = Finished
        public final float elapsedSeconds;
        public final List<RunnerEntry> runners;

        public static class RunnerEntry {
            public final int gateNumber;
            public final int horseId;
            public final String horseName;
            public final int rank;
            public final float raceMeters;
            public final float lateralOffset; // -1.0 (inner rail) to +1.0 (outer rail)
            public final float speedKmh;
            public final boolean finished;
            public final float finishTimeSeconds;

            public RunnerEntry(int gateNumber, int horseId, String horseName, int rank,
                               float raceMeters, float lateralOffset, float speedKmh,
                               boolean finished, float finishTimeSeconds) {
                this.gateNumber = gateNumber;
                this.horseId = horseId;
                this.horseName = horseName != null ? horseName : "";
                this.rank = rank;
                this.raceMeters = raceMeters;
                this.lateralOffset = lateralOffset;
                this.speedKmh = speedKmh;
                this.finished = finished;
                this.finishTimeSeconds = finishTimeSeconds;
            }
        }

        public RaceLeaderboardSyncPacket(boolean active, String grade, String raceName, String trackSummary,
                                         int totalDistanceMeters, int totalStalls, int racePhase,
                                         float elapsedSeconds, List<RunnerEntry> runners) {
            this.active = active;
            this.grade = grade != null ? grade : "Open";
            this.raceName = raceName != null ? raceName : "";
            this.trackSummary = trackSummary != null ? trackSummary : "";
            this.totalDistanceMeters = Math.max(100, totalDistanceMeters);
            this.totalStalls = Math.max(1, totalStalls);
            this.racePhase = racePhase;
            this.elapsedSeconds = elapsedSeconds;
            this.runners = runners != null ? runners : new ArrayList<>();
        }

        public static RaceLeaderboardSyncPacket inactive() {
            return new RaceLeaderboardSyncPacket(false, "", "", "", 1000, 1, 0, 0.0F, List.of());
        }

        public static void encode(RaceLeaderboardSyncPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.active);
            boolean hasSnapshot = msg.runners != null && !msg.runners.isEmpty();
            buf.writeBoolean(hasSnapshot);
            if (!msg.active && !hasSnapshot) {
                return;
            }
            buf.writeUtf(msg.grade, 32);
            buf.writeUtf(msg.raceName, 64);
            buf.writeUtf(msg.trackSummary, 128);
            buf.writeInt(msg.totalDistanceMeters);
            buf.writeInt(msg.totalStalls);
            buf.writeInt(msg.racePhase);
            buf.writeFloat(msg.elapsedSeconds);
            buf.writeInt(msg.runners.size());
            for (RunnerEntry r : msg.runners) {
                buf.writeInt(r.gateNumber);
                buf.writeInt(r.horseId);
                buf.writeUtf(r.horseName, 64);
                buf.writeInt(r.rank);
                buf.writeFloat(r.raceMeters);
                buf.writeFloat(r.lateralOffset);
                buf.writeFloat(r.speedKmh);
                buf.writeBoolean(r.finished);
                buf.writeFloat(r.finishTimeSeconds);
            }
        }

        public static RaceLeaderboardSyncPacket decode(FriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            boolean hasSnapshot = buf.readBoolean();
            if (!active && !hasSnapshot) {
                return inactive();
            }
            String grade = buf.readUtf(32);
            String raceName = buf.readUtf(64);
            String trackSummary = buf.readUtf(128);
            int totalDist = buf.readInt();
            int totalStalls = buf.readInt();
            int phase = buf.readInt();
            float elapsed = buf.readFloat();
            int count = buf.readInt();
            List<RunnerEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new RunnerEntry(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readUtf(64),
                        buf.readInt(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readBoolean(),
                        buf.readFloat()
                ));
            }
            return new RaceLeaderboardSyncPacket(active, grade, raceName, trackSummary, totalDist, totalStalls, phase, elapsed, list);
        }

        public static void handle(RaceLeaderboardSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ThoroughbredRunEngine.updateClientRaceLeaderboard(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // STEP 15: CUSTOM RACE MUSIC & 600m FINAL SPURT PACKETS
    // =========================================================================
    public static class PlayRaceMusicPacket {
        public final String musicFile;
        public final int volume;
        public final boolean loop;

        public PlayRaceMusicPacket(String musicFile, int volume, boolean loop) {
            this.musicFile = musicFile != null ? musicFile : "None";
            this.volume = volume;
            this.loop = loop;
        }

        public static void encode(PlayRaceMusicPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.musicFile, 128);
            buf.writeInt(msg.volume);
            buf.writeBoolean(msg.loop);
        }

        public static PlayRaceMusicPacket decode(FriendlyByteBuf buf) {
            return new PlayRaceMusicPacket(buf.readUtf(128), buf.readInt(), buf.readBoolean());
        }

        public static void handle(PlayRaceMusicPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            RacingManager.playClientRaceMusic(msg.musicFile, msg.volume, msg.loop))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class PlaySpurtMusicPacket {
        public final String spurtFile;
        public final int volume;

        public PlaySpurtMusicPacket(String spurtFile, int volume) {
            this.spurtFile = spurtFile != null ? spurtFile : "None";
            this.volume = volume;
        }

        public static void encode(PlaySpurtMusicPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.spurtFile, 128);
            buf.writeInt(msg.volume);
        }

        public static PlaySpurtMusicPacket decode(FriendlyByteBuf buf) {
            return new PlaySpurtMusicPacket(buf.readUtf(128), buf.readInt());
        }

        public static void handle(PlaySpurtMusicPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            RacingManager.playClientSpurtMusic(msg.spurtFile, msg.volume))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class StopRaceMusicPacket {
        public final boolean stopSpurtToo;

        public StopRaceMusicPacket() {
            this(true);
        }

        public StopRaceMusicPacket(boolean stopSpurtToo) {
            this.stopSpurtToo = stopSpurtToo;
        }

        public static void encode(StopRaceMusicPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.stopSpurtToo);
        }

        public static StopRaceMusicPacket decode(FriendlyByteBuf buf) {
            return new StopRaceMusicPacket(buf.readBoolean());
        }

        public static void handle(StopRaceMusicPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        if (msg.stopSpurtToo) {
                            RacingManager.stopAllClientRaceMusic();
                        } else {
                            RacingManager.stopClientRaceMusic();
                        }
                    })
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenScheduleScreenPacket {
        public final boolean isAdmin;
        public final long playerBalance;
        public final String playerName;
        public final String riddenHorseName;
        public final List<String> ownedHorseNames;
        public final List<ConsoleRaceOption> consoleRaces;
        public final List<StudbookSavedData.ScheduledRace> schedules;

        public static class ConsoleRaceOption {
            public final String raceId;
            public final String raceName;
            public final String grade;
            public final String trackSummary;
            public final long firstPlacePurse;
            public final long requiredEarnings;
            public final int ageRequirement;
            public final int genderRequirement;
            public final String raceSeriesTag;

            public ConsoleRaceOption(String raceId, String raceName, String grade, String trackSummary,
                                     long firstPlacePurse, long requiredEarnings) {
                this(raceId, raceName, grade, trackSummary, firstPlacePurse, requiredEarnings, 2, 0, "");
            }

            public ConsoleRaceOption(String raceId, String raceName, String grade, String trackSummary,
                                     long firstPlacePurse, long requiredEarnings,
                                     int ageRequirement, int genderRequirement, String raceSeriesTag) {
                this.raceId = raceId != null ? raceId : "";
                this.raceName = raceName != null ? raceName : "";
                this.grade = grade != null ? grade : "Open";
                this.trackSummary = trackSummary != null ? trackSummary : "";
                this.firstPlacePurse = firstPlacePurse;
                this.requiredEarnings = requiredEarnings;
                this.ageRequirement = ageRequirement;
                this.genderRequirement = genderRequirement;
                this.raceSeriesTag = raceSeriesTag != null ? raceSeriesTag : "";
            }
        }

        public OpenScheduleScreenPacket(boolean isAdmin, long playerBalance, String playerName,
                                        String riddenHorseName, List<String> ownedHorseNames,
                                        List<ConsoleRaceOption> consoleRaces,
                                        List<StudbookSavedData.ScheduledRace> schedules) {
            this.isAdmin = isAdmin;
            this.playerBalance = playerBalance;
            this.playerName = playerName != null ? playerName : "";
            this.riddenHorseName = riddenHorseName != null ? riddenHorseName : "";
            this.ownedHorseNames = ownedHorseNames != null ? ownedHorseNames : new ArrayList<>();
            this.consoleRaces = consoleRaces != null ? consoleRaces : new ArrayList<>();
            this.schedules = schedules != null ? schedules : new ArrayList<>();
        }

        public static void encode(OpenScheduleScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.isAdmin);
            buf.writeLong(msg.playerBalance);
            buf.writeUtf(msg.playerName, 64);
            buf.writeUtf(msg.riddenHorseName, 64);

            buf.writeInt(msg.ownedHorseNames.size());
            for (String h : msg.ownedHorseNames) {
                buf.writeUtf(h, 64);
            }

            buf.writeInt(msg.consoleRaces.size());
            for (ConsoleRaceOption c : msg.consoleRaces) {
                buf.writeUtf(c.raceId, 64);
                buf.writeUtf(c.raceName, 64);
                buf.writeUtf(c.grade, 32);
                buf.writeUtf(c.trackSummary, 128);
                buf.writeLong(c.firstPlacePurse);
                buf.writeLong(c.requiredEarnings);
                buf.writeInt(c.ageRequirement);
                buf.writeInt(c.genderRequirement);
                buf.writeUtf(c.raceSeriesTag != null ? c.raceSeriesTag : "", 128);
            }

            buf.writeInt(msg.schedules.size());
            for (StudbookSavedData.ScheduledRace s : msg.schedules) {
                buf.writeNbt(s.toNBT());
            }
        }

        public static OpenScheduleScreenPacket decode(FriendlyByteBuf buf) {
            boolean isAdmin = buf.readBoolean();
            long bal = buf.readLong();
            String pName = buf.readUtf(64);
            String riddenName = buf.readUtf(64);

            int ownedCount = buf.readInt();
            List<String> owned = new ArrayList<>(ownedCount);
            for (int i = 0; i < ownedCount; i++) {
                owned.add(buf.readUtf(64));
            }

            int consoleCount = buf.readInt();
            List<ConsoleRaceOption> cRaces = new ArrayList<>(consoleCount);
            for (int i = 0; i < consoleCount; i++) {
                cRaces.add(new ConsoleRaceOption(
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(32),
                        buf.readUtf(128),
                        buf.readLong(),
                        buf.readLong()
                ));
            }

            int schedCount = buf.readInt();
            List<StudbookSavedData.ScheduledRace> scheds = new ArrayList<>(schedCount);
            for (int i = 0; i < schedCount; i++) {
                CompoundTag t = buf.readNbt();
                if (t != null) {
                    scheds.add(StudbookSavedData.ScheduledRace.fromNBT(t));
                }
            }

            return new OpenScheduleScreenPacket(isAdmin, bal, pName, riddenName, owned, cRaces, scheds);
        }

        public static void handle(OpenScheduleScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenScheduleScreen(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ScheduleGuiActionPacket {
        public static final int ACTION_REFRESH = 0;
        public static final int ACTION_REGISTER_HORSE = 1;
        public static final int ACTION_UNREGISTER_HORSE = 2;
        public static final int ACTION_PLACE_BET = 3;
        public static final int ACTION_ADMIN_ADD_SCHEDULE = 4;
        public static final int ACTION_ADMIN_OPEN_REG = 5;
        public static final int ACTION_ADMIN_CLOSE_REG = 6;
        public static final int ACTION_ADMIN_KICK_HORSE = 7;
        public static final int ACTION_ADMIN_CANCEL_SCHEDULE = 8;
        public static final int ACTION_ADMIN_REMOVE_SCHEDULE = 9;

        public final int actionType;
        public final String scheduleId;
        public final String strParam1;
        public final String strParam2;
        public final long longParam1;

        public ScheduleGuiActionPacket(int actionType, String scheduleId, String strParam1, String strParam2, long longParam1) {
            this.actionType = actionType;
            this.scheduleId = scheduleId != null ? scheduleId : "";
            this.strParam1 = strParam1 != null ? strParam1 : "";
            this.strParam2 = strParam2 != null ? strParam2 : "";
            this.longParam1 = longParam1;
        }

        public static void encode(ScheduleGuiActionPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.actionType);
            buf.writeUtf(msg.scheduleId, 64);
            buf.writeUtf(msg.strParam1, 256);
            buf.writeUtf(msg.strParam2, 256);
            buf.writeLong(msg.longParam1);
        }

        public static ScheduleGuiActionPacket decode(FriendlyByteBuf buf) {
            return new ScheduleGuiActionPacket(
                    buf.readInt(),
                    buf.readUtf(64),
                    buf.readUtf(256),
                    buf.readUtf(256),
                    buf.readLong()
            );
        }

        public static void handle(ScheduleGuiActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ModCommands.handleScheduleGuiAction(player, msg);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SyncHorseConditionPacket {
        public final int horseId;
        public final boolean poorPrognosis;
        public final boolean fever;
        public final boolean lame;
        public final boolean careerEnded;
        public final boolean pureWhiteCoat;
        public final String ownerSilkCode;
        public final boolean wearingRaceSilk;
        public final boolean deathsDoor;

        public SyncHorseConditionPacket(int horseId, boolean poorPrognosis, boolean fever, boolean lame,
                                        boolean careerEnded, boolean pureWhiteCoat) {
            this(horseId, poorPrognosis, fever, lame, careerEnded, pureWhiteCoat, "", false, false);
        }

        public SyncHorseConditionPacket(int horseId, boolean poorPrognosis, boolean fever, boolean lame,
                                        boolean careerEnded, boolean pureWhiteCoat, String ownerSilkCode) {
            this(horseId, poorPrognosis, fever, lame, careerEnded, pureWhiteCoat, ownerSilkCode, false, false);
        }

        public SyncHorseConditionPacket(int horseId, boolean poorPrognosis, boolean fever, boolean lame,
                                        boolean careerEnded, boolean pureWhiteCoat, String ownerSilkCode,
                                        boolean wearingRaceSilk, boolean deathsDoor) {
            this.horseId = horseId;
            this.poorPrognosis = poorPrognosis;
            this.fever = fever;
            this.lame = lame;
            this.careerEnded = careerEnded;
            this.pureWhiteCoat = pureWhiteCoat;
            this.ownerSilkCode = ownerSilkCode != null ? ownerSilkCode : "";
            this.wearingRaceSilk = wearingRaceSilk;
            this.deathsDoor = deathsDoor;
        }

        public static void encode(SyncHorseConditionPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.horseId);
            buf.writeBoolean(msg.poorPrognosis);
            buf.writeBoolean(msg.fever);
            buf.writeBoolean(msg.lame);
            buf.writeBoolean(msg.careerEnded);
            buf.writeBoolean(msg.pureWhiteCoat);
            buf.writeUtf(msg.ownerSilkCode, 64);
            buf.writeBoolean(msg.wearingRaceSilk);
            buf.writeBoolean(msg.deathsDoor);
        }

        public static SyncHorseConditionPacket decode(FriendlyByteBuf buf) {
            return new SyncHorseConditionPacket(
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(64),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        public static void handle(SyncHorseConditionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ThoroughbredRunEngine.updateClientHorseCondition(
                                    msg.horseId, msg.poorPrognosis, msg.fever, msg.lame,
                                    msg.careerEnded, msg.pureWhiteCoat, msg.ownerSilkCode,
                                    msg.wearingRaceSilk, msg.deathsDoor))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // STEP 18 PACKETS: World Time Flow Selector & 4-Generation Pedigree Tree GUI
    // =========================================================================

    public static class OpenTimeFlowScreenPacket {
        public final int currentMode;
        public final int customDays;
        public final boolean isFirstJoinPrompt;
        public final boolean alreadyConfigured;

        public OpenTimeFlowScreenPacket(int currentMode, int customDays, boolean isFirstJoinPrompt) {
            this(currentMode, customDays, isFirstJoinPrompt, false);
        }

        public OpenTimeFlowScreenPacket(int currentMode, int customDays, boolean isFirstJoinPrompt, boolean alreadyConfigured) {
            this.currentMode = currentMode;
            this.customDays = customDays;
            this.isFirstJoinPrompt = isFirstJoinPrompt;
            this.alreadyConfigured = alreadyConfigured;
        }

        public static void encode(OpenTimeFlowScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.currentMode);
            buf.writeInt(msg.customDays);
            buf.writeBoolean(msg.isFirstJoinPrompt);
            buf.writeBoolean(msg.alreadyConfigured);
        }

        public static OpenTimeFlowScreenPacket decode(FriendlyByteBuf buf) {
            return new OpenTimeFlowScreenPacket(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        public static void handle(OpenTimeFlowScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenTimeFlowScreen(
                                    msg.currentMode, msg.customDays, msg.isFirstJoinPrompt, msg.alreadyConfigured))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SetTimeFlowPacket {
        public final int mode;
        public final int customDays;

        public SetTimeFlowPacket(int mode, int customDays) {
            this.mode = mode;
            this.customDays = customDays;
        }

        public static void encode(SetTimeFlowPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.mode);
            buf.writeInt(msg.customDays);
        }

        public static SetTimeFlowPacket decode(FriendlyByteBuf buf) {
            return new SetTimeFlowPacket(
                    buf.readInt(),
                    buf.readInt()
            );
        }

        public static void handle(SetTimeFlowPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel sl)) return;
                StudbookSavedData studbook = StudbookSavedData.get(sl);
                if (studbook.isTimeFlowConfigured()) {
                    player.sendSystemMessage(Component.literal(
                            "§cThe World Equine Time Flow has already been permanently locked for this world! To use a different Equine Time Flow, you must create a new world."));
                    return;
                }
                boolean allowed = player.hasPermissions(2)
                        || (player.server != null && player.server.isSingleplayer())
                        || !studbook.isTimeFlowConfigured();
                if (!allowed) {
                    player.sendSystemMessage(Component.literal("§cOnly server admins can set the World Equine Time Flow!"));
                    return;
                }
                studbook.setTimeFlowConfiguration(msg.mode, msg.customDays);
                player.sendSystemMessage(Component.literal(
                        "§6§l[EQUINE CALENDAR LOCKED] §aWorld Equine Time Flow permanently set! " + studbook.getFormattedTimeFlowSummary()));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenPedigreeScreenPacket {
        public final CompoundTag horseDataTag;

        public OpenPedigreeScreenPacket(CompoundTag horseDataTag) {
            this.horseDataTag = horseDataTag != null ? horseDataTag : new CompoundTag();
        }

        public static void encode(OpenPedigreeScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.horseDataTag);
        }

        public static OpenPedigreeScreenPacket decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new OpenPedigreeScreenPacket(tag != null ? tag : new CompoundTag());
        }

        public static void handle(OpenPedigreeScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenPedigreeScreen(msg.horseDataTag))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // STEP 19 PACKETS: Gelding Confirmation HUD & Execution
    // =========================================================================
    public static class OpenGeldingScreenPacket {
        public final int horseId;
        public final String horseName;

        public OpenGeldingScreenPacket(int horseId, String horseName) {
            this.horseId = horseId;
            this.horseName = horseName != null ? horseName : "Stallion";
        }

        public static void encode(OpenGeldingScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.horseId);
            buf.writeUtf(msg.horseName, 64);
        }

        public static OpenGeldingScreenPacket decode(FriendlyByteBuf buf) {
            return new OpenGeldingScreenPacket(buf.readInt(), buf.readUtf(64));
        }

        public static void handle(OpenGeldingScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenGeldingScreen(msg.horseId, msg.horseName))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ConfirmGeldingPacket {
        public final int horseId;

        public ConfirmGeldingPacket(int horseId) {
            this.horseId = horseId;
        }

        public static void encode(ConfirmGeldingPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.horseId);
        }

        public static ConfirmGeldingPacket decode(FriendlyByteBuf buf) {
            return new ConfirmGeldingPacket(buf.readInt());
        }

        public static void handle(ConfirmGeldingPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel sl)) return;

                Entity e = sl.getEntity(msg.horseId);
                if (!(e instanceof Horse horse) || !horse.isAlive() || !ThoroughbredData.isThoroughbred(horse)) {
                    return;
                }
                if (player.distanceToSqr(horse) > 64.0) {
                    player.sendSystemMessage(Component.literal("§cYou are too far away from the stallion!"));
                    return;
                }

                StudbookSavedData studbook = StudbookSavedData.get(sl);
                if (studbook.isGeldingAdminOnly() && !player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§cGelding is restricted to server admins on this server!"));
                    return;
                }

                ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                if ("Female".equalsIgnoreCase(data.getGender())) {
                    player.sendSystemMessage(Component.literal("§cYou cannot spay a filly/mare!"));
                    return;
                }
                if (data.isGelding()) {
                    player.sendSystemMessage(Component.literal("§c" + data.getName() + " has already been gelded!"));
                    return;
                }
                if (!data.isOwnedBy(player.getUUID()) && !player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§cThat stallion isn't yours to geld!"));
                    return;
                }

                // Damage held shears if present
                ItemStack mainStack = player.getMainHandItem();
                ItemStack offStack = player.getOffhandItem();
                if (mainStack.is(net.minecraft.world.item.Items.SHEARS)) {
                    mainStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                } else if (offStack.is(net.minecraft.world.item.Items.SHEARS)) {
                    offStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.OFF_HAND));
                }

                // Instant gelding operation!
                data.geldHorse();
                data.setLastOwnerInteractionMcDay(sl.getDayTime() / 24000L);
                data.saveToHorse(horse);

                ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
                runState.trainingRefusalActive = false;
                runState.rearVisualTicks = 0;
                horse.setStanding(false);

                sl.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                sl.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        net.minecraft.sounds.SoundEvents.HORSE_BREATHE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.1F);

                // Exact chat log message requested by user
                player.sendSystemMessage(Component.literal(
                        data.getName() + " has been gelded and can no longer reproduce. It now has better control over its power, and is now meeker than before."));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // =========================================================================
    // STEP 21 PACKETS: Horse Owner Registration (/tb owner), Jockey Silks & Global Studbook (/tb studbook)
    // =========================================================================
    public static void sendOpenOwnerScreen(ServerPlayer player, String statusMessage) {
        if (player == null || !(player.level() instanceof ServerLevel sl)) return;
        StudbookSavedData studbook = StudbookSavedData.get(sl);
        if (!studbook.isTimeFlowConfigured()) {
            player.sendSystemMessage(Component.literal(
                    "§cYou cannot register as a Horse Owner until the World Equine Time Flow has been chosen! Use /tb timeflow first."));
            return;
        }
        StudbookSavedData.HorseOwnerProfile prof = studbook.getOwnerProfile(player.getUUID());
        boolean registered = studbook.isRegisteredHorseOwner(player.getUUID());
        long balance = studbook.getBalance(player.getUUID());

        String farmName = (prof != null && prof.farmName != null) ? prof.farmName : "";
        String crown1 = (prof != null && prof.crownName1 != null) ? prof.crownName1 : "";
        boolean crown1Prefix = (prof == null) || prof.crown1Prefix;
        String crown2 = (prof != null && prof.crownName2 != null) ? prof.crownName2 : "";
        boolean crown2Prefix = (prof == null) || prof.crown2Prefix;
        String silkCode = (prof != null && prof.silkConfig != null)
                ? prof.silkConfig.toCompactCode()
                : new StudbookSavedData.JockeySilkConfig().toCompactCode();

        // Compute Farm Career Statistics across owned horses
        int ownedCount = 0;
        int totalStarts = 0;
        int totalWins = 0;
        int totalSeconds = 0;
        int totalThirds = 0;
        int g1Wins = 0;
        long totalEarnings = 0L;
        String uuidStr = player.getUUID().toString();

        for (StudbookSavedData.HorseBackupRecord rec : studbook.getAllLivingHorses()) {
            if (uuidStr.equalsIgnoreCase(rec.ownerUUID)) {
                ownedCount++;
                if (rec.thoroughbredNbt != null && !rec.thoroughbredNbt.isEmpty()) {
                    ThoroughbredData d = new ThoroughbredData();
                    d.loadFromNBT(rec.thoroughbredNbt);
                    totalEarnings += d.getTotalEarnings();
                    for (ThoroughbredData.CareerRaceRecord r : d.getCareerHistory()) {
                        totalStarts++;
                        if (r.placement == 1) {
                            totalWins++;
                            if ("G1".equalsIgnoreCase(r.grade)) g1Wins++;
                        } else if (r.placement == 2) {
                            totalSeconds++;
                        } else if (r.placement == 3) {
                            totalThirds++;
                        }
                    }
                }
            }
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenOwnerScreenPacket(
                registered,
                player.getName().getString(),
                balance,
                studbook.getOwnerRegistrationFee(),
                studbook.getOwnerFarmRenameFee(),
                studbook.getOwnerCrownEditFee(),
                studbook.getOwnerSilkEditFee(),
                farmName,
                crown1,
                crown1Prefix,
                crown2,
                crown2Prefix,
                silkCode,
                ownedCount,
                totalStarts,
                totalWins,
                totalSeconds,
                totalThirds,
                g1Wins,
                totalEarnings,
                statusMessage != null ? statusMessage : ""
        ));
    }

    public static void sendOpenStudbookScreen(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel sl)) return;
        StudbookSavedData studbook = StudbookSavedData.get(sl);
        String currentCropTag = studbook.computeCurrentCropTag();
        String timeFlowSummary = studbook.getFormattedTimeFlowSummary();

        List<OpenStudbookScreenPacket.StudbookHorseSummary> list = new ArrayList<>();
        for (StudbookSavedData.HorseBackupRecord rec : studbook.getAllLivingHorses()) {
            if (rec.thoroughbredNbt == null || rec.thoroughbredNbt.isEmpty()) continue;
            ThoroughbredData d = new ThoroughbredData();
            d.loadFromNBT(rec.thoroughbredNbt);
            String crop = d.getCropTag();
            if (crop == null || crop.isBlank()) crop = currentCropTag;
            String silk = d.getOwnerSilkCode();
            if ((silk == null || silk.isBlank()) && rec.ownerUUID != null && !rec.ownerUUID.isBlank()) {
                try {
                    silk = studbook.getOwnerSilkCompactCode(java.util.UUID.fromString(rec.ownerUUID));
                } catch (Exception ignored) {
                }
            }
            int wins = 0;
            for (ThoroughbredData.CareerRaceRecord r : d.getCareerHistory()) {
                if (r.placement == 1) wins++;
            }
            String roleTag = d.isFoal() ? "Foal (0yo)"
                    : (d.isYearling() ? "Yearling (1yo)"
                    : (d.isGelding() ? (d.isRetired() ? "Retired Gelding" : "Active Gelding")
                    : (d.isRetired() ? ("Female".equalsIgnoreCase(d.getGender()) ? "Broodmare" : "Stud") : "Active")));
            list.add(new OpenStudbookScreenPacket.StudbookHorseSummary(
                    rec.horseName,
                    d.getGender(),
                    d.isGelding(),
                    roleTag,
                    crop,
                    d.hasOwner() ? d.getOwnerName() : "Unclaimed",
                    silk != null ? silk : "",
                    d.getSireName(),
                    d.getDamName(),
                    d.getHereditaryPoints(),
                    d.getCareerHistory().size(),
                    wins,
                    d.getTotalEarnings(),
                    false
            ));
        }

        for (StudbookSavedData.DeathRecord dRec : studbook.getDeathRecords()) {
            if (dRec.revived) {
                continue;
            }
            ThoroughbredData d = new ThoroughbredData();
            if (dRec.thoroughbredNbt != null && !dRec.thoroughbredNbt.isEmpty()) {
                d.loadFromNBT(dRec.thoroughbredNbt);
            } else if (dRec.horseName != null && !dRec.horseName.isBlank()) {
                StudbookSavedData.PedigreeArchiveRecord pRec = studbook.getPedigreeRecord(dRec.horseName);
                if (pRec != null) {
                    pRec.applyToData(d);
                } else {
                    d.setName(dRec.horseName);
                }
                if (dRec.ownerName != null && !dRec.ownerName.isBlank() && d.getOwnerName().isEmpty()) {
                    d.setOwner(null, dRec.ownerName);
                }
            } else {
                continue;
            }
            String crop = d.getCropTag();
            if (crop == null || crop.isBlank()) crop = currentCropTag;
            String silk = d.getOwnerSilkCode();
            int wins = 0;
            for (ThoroughbredData.CareerRaceRecord r : d.getCareerHistory()) {
                if (r.placement == 1) wins++;
            }
            list.add(new OpenStudbookScreenPacket.StudbookHorseSummary(
                    dRec.horseName,
                    d.getGender(),
                    d.isGelding(),
                    "Deceased",
                    crop,
                    dRec.ownerName != null && !dRec.ownerName.isBlank() ? dRec.ownerName : "Unclaimed",
                    silk != null ? silk : "",
                    d.getSireName(),
                    d.getDamName(),
                    d.getHereditaryPoints(),
                    d.getCareerHistory().size(),
                    wins,
                    d.getTotalEarnings(),
                    true
            ));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenStudbookScreenPacket(currentCropTag, timeFlowSummary, list));
    }

    public static class OpenOwnerScreenPacket {
        public final boolean registered;
        public final String playerName;
        public final long playerBalance;
        public final long registrationFee;
        public final long farmRenameFee;
        public final long crownEditFee;
        public final long silkEditFee;
        public final String farmName;
        public final String crownName1;
        public final boolean crown1Prefix;
        public final String crownName2;
        public final boolean crown2Prefix;
        public final String silkCode;
        public final int ownedCount;
        public final int totalStarts;
        public final int totalWins;
        public final int totalSeconds;
        public final int totalThirds;
        public final int g1Wins;
        public final long totalEarnings;
        public final String statusMessage;

        public OpenOwnerScreenPacket(boolean registered, String playerName, long playerBalance,
                                     long registrationFee, long farmRenameFee, long crownEditFee, long silkEditFee,
                                     String farmName, String crownName1, boolean crown1Prefix,
                                     String crownName2, boolean crown2Prefix, String silkCode,
                                     int ownedCount, int totalStarts, int totalWins, int totalSeconds,
                                     int totalThirds, int g1Wins, long totalEarnings, String statusMessage) {
            this.registered = registered;
            this.playerName = playerName != null ? playerName : "";
            this.playerBalance = playerBalance;
            this.registrationFee = registrationFee;
            this.farmRenameFee = farmRenameFee;
            this.crownEditFee = crownEditFee;
            this.silkEditFee = silkEditFee;
            this.farmName = farmName != null ? farmName : "";
            this.crownName1 = crownName1 != null ? crownName1 : "";
            this.crown1Prefix = crown1Prefix;
            this.crownName2 = crownName2 != null ? crownName2 : "";
            this.crown2Prefix = crown2Prefix;
            this.silkCode = silkCode != null ? silkCode : "3,5,0,0,2,3";
            this.ownedCount = ownedCount;
            this.totalStarts = totalStarts;
            this.totalWins = totalWins;
            this.totalSeconds = totalSeconds;
            this.totalThirds = totalThirds;
            this.g1Wins = g1Wins;
            this.totalEarnings = totalEarnings;
            this.statusMessage = statusMessage != null ? statusMessage : "";
        }

        public static void encode(OpenOwnerScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.registered);
            buf.writeUtf(msg.playerName, 64);
            buf.writeLong(msg.playerBalance);
            buf.writeLong(msg.registrationFee);
            buf.writeLong(msg.farmRenameFee);
            buf.writeLong(msg.crownEditFee);
            buf.writeLong(msg.silkEditFee);
            buf.writeUtf(msg.farmName, 64);
            buf.writeUtf(msg.crownName1, 32);
            buf.writeBoolean(msg.crown1Prefix);
            buf.writeUtf(msg.crownName2, 32);
            buf.writeBoolean(msg.crown2Prefix);
            buf.writeUtf(msg.silkCode, 64);
            buf.writeInt(msg.ownedCount);
            buf.writeInt(msg.totalStarts);
            buf.writeInt(msg.totalWins);
            buf.writeInt(msg.totalSeconds);
            buf.writeInt(msg.totalThirds);
            buf.writeInt(msg.g1Wins);
            buf.writeLong(msg.totalEarnings);
            buf.writeUtf(msg.statusMessage, 256);
        }

        public static OpenOwnerScreenPacket decode(FriendlyByteBuf buf) {
            return new OpenOwnerScreenPacket(
                    buf.readBoolean(),
                    buf.readUtf(64),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readUtf(64),
                    buf.readUtf(32),
                    buf.readBoolean(),
                    buf.readUtf(32),
                    buf.readBoolean(),
                    buf.readUtf(64),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readUtf(256)
            );
        }

        public static void handle(OpenOwnerScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenOwnerScreen(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SaveOwnerProfilePacket {
        public final String farmName;
        public final String crownName1;
        public final boolean crown1Prefix;
        public final String crownName2;
        public final boolean crown2Prefix;
        public final String silkCode;

        public SaveOwnerProfilePacket(String farmName, String crownName1, boolean crown1Prefix,
                                      String crownName2, boolean crown2Prefix, String silkCode) {
            this.farmName = farmName != null ? farmName.trim() : "";
            this.crownName1 = crownName1 != null ? crownName1.trim() : "";
            this.crown1Prefix = crown1Prefix;
            this.crownName2 = crownName2 != null ? crownName2.trim() : "";
            this.crown2Prefix = crown2Prefix;
            this.silkCode = silkCode != null ? silkCode.trim() : "3,5,0,0,2,3";
        }

        public static void encode(SaveOwnerProfilePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.farmName, 64);
            buf.writeUtf(msg.crownName1, 32);
            buf.writeBoolean(msg.crown1Prefix);
            buf.writeUtf(msg.crownName2, 32);
            buf.writeBoolean(msg.crown2Prefix);
            buf.writeUtf(msg.silkCode, 64);
        }

        public static SaveOwnerProfilePacket decode(FriendlyByteBuf buf) {
            return new SaveOwnerProfilePacket(
                    buf.readUtf(64),
                    buf.readUtf(32),
                    buf.readBoolean(),
                    buf.readUtf(32),
                    buf.readBoolean(),
                    buf.readUtf(64)
            );
        }

        public static void handle(SaveOwnerProfilePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel sl)) return;
                StudbookSavedData studbook = StudbookSavedData.get(sl);

                String cleanFarm = msg.farmName != null ? msg.farmName.trim() : "";
                if (cleanFarm.length() < 2 || cleanFarm.length() > 24) {
                    sendOpenOwnerScreen(player, "§cFarm / Horse Owner Alias must be between 2 and 24 characters!");
                    return;
                }
                if (studbook.isFarmNameTakenByOther(cleanFarm, player.getUUID())) {
                    sendOpenOwnerScreen(player, "§cAnother Horse Owner has already registered '" + cleanFarm + "'!");
                    return;
                }

                String c1 = msg.crownName1 != null ? msg.crownName1.trim() : "";
                String c2 = msg.crownName2 != null ? msg.crownName2.trim() : "";
                if (c1.length() > 14 || c2.length() > 14) {
                    sendOpenOwnerScreen(player, "§cCrown names must be 14 characters or fewer!");
                    return;
                }

                StudbookSavedData.JockeySilkConfig newSilk = StudbookSavedData.JockeySilkConfig.fromCompactCode(msg.silkCode);
                boolean alreadyOwner = studbook.isRegisteredHorseOwner(player.getUUID());
                StudbookSavedData.HorseOwnerProfile existing = studbook.getOwnerProfile(player.getUUID());

                long totalCost = 0L;
                List<String> feeBreakdown = new ArrayList<>();

                if (!alreadyOwner || existing == null) {
                    totalCost = studbook.getOwnerRegistrationFee();
                    feeBreakdown.add("Initial Registration: $" + String.format(Locale.ROOT, "%,d", totalCost));
                } else {
                    boolean farmChanged = !cleanFarm.equals(existing.farmName != null ? existing.farmName : "");
                    boolean c1Changed = !c1.equals(existing.crownName1 != null ? existing.crownName1 : "")
                            || (!c1.isEmpty() && msg.crown1Prefix != existing.crown1Prefix);
                    boolean c2Changed = !c2.equals(existing.crownName2 != null ? existing.crownName2 : "")
                            || (!c2.isEmpty() && msg.crown2Prefix != existing.crown2Prefix);
                    boolean silkChanged = existing.silkConfig == null || !newSilk.matches(existing.silkConfig);

                    if (farmChanged) {
                        long fFee = studbook.getOwnerFarmRenameFee();
                        totalCost += fFee;
                        feeBreakdown.add("Farm Name ($" + String.format(Locale.ROOT, "%,d", fFee) + ")");
                    }
                    if (c1Changed) {
                        long cFee = studbook.getOwnerCrownEditFee();
                        totalCost += cFee;
                        feeBreakdown.add("Crown #1 ($" + String.format(Locale.ROOT, "%,d", cFee) + ")");
                    }
                    if (c2Changed) {
                        long cFee = studbook.getOwnerCrownEditFee();
                        totalCost += cFee;
                        feeBreakdown.add("Crown #2 ($" + String.format(Locale.ROOT, "%,d", cFee) + ")");
                    }
                    if (silkChanged) {
                        long sFee = studbook.getOwnerSilkEditFee();
                        totalCost += sFee;
                        feeBreakdown.add("Jockey Silk ($" + String.format(Locale.ROOT, "%,d", sFee) + ")");
                    }

                    if (!farmChanged && !c1Changed && !c2Changed && !silkChanged) {
                        sendOpenOwnerScreen(player, "§eNo changes detected in your Horse Owner profile.");
                        return;
                    }
                }

                long balance = studbook.getBalance(player.getUUID());
                if (totalCost > 0L && balance < totalCost) {
                    sendOpenOwnerScreen(player, String.format(Locale.ROOT,
                            "§cInsufficient funds! Need $%,d (Your Balance: $%,d).", totalCost, balance));
                    return;
                }

                if (totalCost > 0L && !studbook.tryDeductBalance(player.getUUID(), totalCost)) {
                    sendOpenOwnerScreen(player, "§cPayment failed!");
                    return;
                }

                StudbookSavedData.HorseOwnerProfile prof = (existing != null) ? existing : new StudbookSavedData.HorseOwnerProfile();
                prof.playerUUID = player.getUUID();
                prof.playerName = player.getName().getString();
                prof.farmName = cleanFarm;
                prof.crownName1 = c1;
                prof.crown1Prefix = msg.crown1Prefix;
                prof.crownName2 = c2;
                prof.crown2Prefix = msg.crown2Prefix;
                prof.silkConfig = newSilk;
                if (prof.registeredAtMillis <= 0L) {
                    prof.registeredAtMillis = System.currentTimeMillis();
                }

                // Store in studbook and cascade to all owned horses (including pre-existing claimed horses failsafe!)
                saveOwnerProfileAndCascade(studbook, player, prof, !alreadyOwner, totalCost, feeBreakdown);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void saveOwnerProfileAndCascade(StudbookSavedData studbook, ServerPlayer player,
                                                   StudbookSavedData.HorseOwnerProfile prof,
                                                   boolean isFirstRegistration, long paidFee, List<String> feeBreakdown) {
        // Put into studbook's ownerProfiles via a CompoundTag round-trip or direct setter
        studbook.putOwnerProfile(prof);
        int updatedHorses = studbook.cascadeOwnerProfileToAllHorses(player.server, player.getUUID());

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.25F);

        String formattedDisplay = prof.getFormattedOwnerDisplay(player.getName().getString());
        if (isFirstRegistration) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§6§l[Official Horse Owner Registered!] §fWelcome, §b%s§f! Paid §a$%,d§f. You can now register Thoroughbreds with the §6Thoroughbred Registry Form§f!%s",
                    formattedDisplay,
                    paidFee,
                    updatedHorses > 0 ? (" §7(Updated " + updatedHorses + " claimed horse(s) to " + formattedDisplay + "!)") : "")));
            sendOpenOwnerScreen(player, "§a✔ Officially registered as " + formattedDisplay + "!");
        } else {
            String itemsStr = String.join(", ", feeBreakdown);
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§6§l[Horse Owner Profile Updated!] §fUpdated §b%s §7(%s) §ffor §a$%,d§f! (Synced across §e%d §fhorse record(s))",
                    formattedDisplay, itemsStr, paidFee, updatedHorses)));
            sendOpenOwnerScreen(player, String.format(Locale.ROOT,
                    "§a✔ Saved changes (%s) for $%,d!", itemsStr, paidFee));
        }
    }

    public static class OpenStudbookScreenPacket {
        public final String currentCropTag;
        public final String timeFlowSummary;
        public final List<StudbookHorseSummary> horses;

        public static class StudbookHorseSummary {
            public final String horseName;
            public final String gender;
            public final boolean gelding;
            public final String roleTag;
            public final String cropTag;
            public final String ownerName;
            public final String ownerSilkCode;
            public final String sireName;
            public final String damName;
            public final int hereditaryPoints;
            public final int starts;
            public final int wins;
            public final long totalEarnings;
            public final boolean deceased;

            public StudbookHorseSummary(String horseName, String gender, boolean gelding, String roleTag,
                                        String cropTag, String ownerName, String ownerSilkCode,
                                        String sireName, String damName, int hereditaryPoints,
                                        int starts, int wins, long totalEarnings, boolean deceased) {
                this.horseName = horseName != null ? horseName : "";
                this.gender = gender != null ? gender : "Male";
                this.gelding = gelding;
                this.roleTag = roleTag != null ? roleTag : "Active";
                this.cropTag = cropTag != null ? cropTag : "";
                this.ownerName = ownerName != null ? ownerName : "Unclaimed";
                this.ownerSilkCode = ownerSilkCode != null ? ownerSilkCode : "";
                this.sireName = sireName != null ? sireName : "Unknown";
                this.damName = damName != null ? damName : "Unknown";
                this.hereditaryPoints = hereditaryPoints;
                this.starts = starts;
                this.wins = wins;
                this.totalEarnings = totalEarnings;
                this.deceased = deceased;
            }
        }

        public OpenStudbookScreenPacket(String currentCropTag, String timeFlowSummary, List<StudbookHorseSummary> horses) {
            this.currentCropTag = currentCropTag != null ? currentCropTag : "";
            this.timeFlowSummary = timeFlowSummary != null ? timeFlowSummary : "";
            this.horses = horses != null ? horses : new ArrayList<>();
        }

        public static void encode(OpenStudbookScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.currentCropTag, 32);
            buf.writeUtf(msg.timeFlowSummary, 256);
            buf.writeInt(msg.horses.size());
            for (StudbookHorseSummary h : msg.horses) {
                buf.writeUtf(h.horseName, 64);
                buf.writeUtf(h.gender, 16);
                buf.writeBoolean(h.gelding);
                buf.writeUtf(h.roleTag, 32);
                buf.writeUtf(h.cropTag, 32);
                buf.writeUtf(h.ownerName, 64);
                buf.writeUtf(h.ownerSilkCode, 64);
                buf.writeUtf(h.sireName, 64);
                buf.writeUtf(h.damName, 64);
                buf.writeInt(h.hereditaryPoints);
                buf.writeInt(h.starts);
                buf.writeInt(h.wins);
                buf.writeLong(h.totalEarnings);
                buf.writeBoolean(h.deceased);
            }
        }

        public static OpenStudbookScreenPacket decode(FriendlyByteBuf buf) {
            String curCrop = buf.readUtf(32);
            String tfSum = buf.readUtf(256);
            int count = buf.readInt();
            List<StudbookHorseSummary> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new StudbookHorseSummary(
                        buf.readUtf(64),
                        buf.readUtf(16),
                        buf.readBoolean(),
                        buf.readUtf(32),
                        buf.readUtf(32),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readLong(),
                        buf.readBoolean()
                ));
            }
            return new OpenStudbookScreenPacket(curCrop, tfSum, list);
        }

        public static void handle(OpenStudbookScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenStudbookScreen(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RequestStudbookHorseCardPacket {
        public final String horseName;

        public RequestStudbookHorseCardPacket(String horseName) {
            this.horseName = horseName != null ? horseName : "";
        }

        public static void encode(RequestStudbookHorseCardPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.horseName, 64);
        }

        public static RequestStudbookHorseCardPacket decode(FriendlyByteBuf buf) {
            return new RequestStudbookHorseCardPacket(buf.readUtf(64));
        }

        public static void handle(RequestStudbookHorseCardPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !(player.level() instanceof ServerLevel sl)) return;
                StudbookSavedData studbook = StudbookSavedData.get(sl);
                StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(msg.horseName);
                if (rec != null && rec.thoroughbredNbt != null && !rec.thoroughbredNbt.isEmpty()) {
                    CompoundTag tag = rec.thoroughbredNbt.copy();
                    tag.putBoolean("_FromStudbookBrowser", true);
                    sendOpenScreen(player, -1, true, tag);
                    return;
                }
                for (StudbookSavedData.DeathRecord dRec : studbook.getDeathRecords()) {
                    if (dRec.revived || dRec.horseName == null || !dRec.horseName.equalsIgnoreCase(msg.horseName)) {
                        continue;
                    }
                    CompoundTag tag;
                    if (dRec.thoroughbredNbt != null && !dRec.thoroughbredNbt.isEmpty()) {
                        tag = dRec.thoroughbredNbt.copy();
                    } else {
                        ThoroughbredData d = new ThoroughbredData();
                        StudbookSavedData.PedigreeArchiveRecord pRec = studbook.getPedigreeRecord(dRec.horseName);
                        if (pRec != null) {
                            pRec.applyToData(d);
                        } else {
                            d.setName(dRec.horseName);
                        }
                        if (dRec.ownerName != null && !dRec.ownerName.isBlank() && d.getOwnerName().isEmpty()) {
                            d.setOwner(null, dRec.ownerName);
                        }
                        tag = d.toNBT();
                    }
                    tag.putBoolean("_FromStudbookBrowser", true);
                    sendOpenScreen(player, -1, true, tag);
                    return;
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendOpenRaceResultsScreen(ServerPlayer player, RacingManager.PendingRaceResult pending) {
        if (player == null || pending == null) return;
        List<OpenRaceResultsScreenPacket.ResultRowEntry> rows = new ArrayList<>(pending.runners.size());
        for (RacingManager.PendingRunnerEntry r : pending.runners) {
            rows.add(new OpenRaceResultsScreenPacket.ResultRowEntry(
                    r.gateNumber,
                    r.horseName,
                    r.ownerName,
                    r.ownerSilkCode,
                    r.jockeyName,
                    r.officialPlacement,
                    r.dnf,
                    r.dsq,
                    r.formattedTime,
                    r.formattedMargin,
                    r.projectedPurse
            ));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenRaceResultsScreenPacket(
                        pending.raceName,
                        pending.grade,
                        pending.trackSummary,
                        pending.totalStalls,
                        pending.firstPlacePurse,
                        pending.isTestRace,
                        pending.confirmed,
                        rows
                ));
    }

    public static class OpenRaceResultsScreenPacket {
        public final String raceName;
        public final String grade;
        public final String trackSummary;
        public final int totalStalls;
        public final long firstPlacePurse;
        public final boolean isTestRace;
        public final boolean confirmed;
        public final List<ResultRowEntry> runners;

        public static class ResultRowEntry {
            public final int gateNumber;
            public final String horseName;
            public final String ownerName;
            public final String ownerSilkCode;
            public final String jockeyName;
            public final int placement;
            public final boolean dnf;
            public final boolean dsq;
            public final String formattedTime;
            public final String formattedMargin;
            public final long projectedPurse;

            public ResultRowEntry(int gateNumber, String horseName, String ownerName, String ownerSilkCode,
                                  String jockeyName, int placement, boolean dnf, boolean dsq,
                                  String formattedTime, String formattedMargin, long projectedPurse) {
                this.gateNumber = gateNumber;
                this.horseName = horseName != null ? horseName : "";
                this.ownerName = ownerName != null ? ownerName : "Unclaimed";
                this.ownerSilkCode = ownerSilkCode != null ? ownerSilkCode : "";
                this.jockeyName = jockeyName != null ? jockeyName : "";
                this.placement = placement;
                this.dnf = dnf;
                this.dsq = dsq;
                this.formattedTime = formattedTime != null ? formattedTime : "00:00.00";
                this.formattedMargin = formattedMargin != null ? formattedMargin : "-";
                this.projectedPurse = projectedPurse;
            }
        }

        public OpenRaceResultsScreenPacket(String raceName, String grade, String trackSummary,
                                           int totalStalls, long firstPlacePurse,
                                           boolean isTestRace, boolean confirmed,
                                           List<ResultRowEntry> runners) {
            this.raceName = raceName != null ? raceName : "Official Race";
            this.grade = grade != null ? grade : "Open";
            this.trackSummary = trackSummary != null ? trackSummary : "";
            this.totalStalls = Math.max(1, totalStalls);
            this.firstPlacePurse = firstPlacePurse;
            this.isTestRace = isTestRace;
            this.confirmed = confirmed;
            this.runners = runners != null ? runners : new ArrayList<>();
        }

        public static void encode(OpenRaceResultsScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.raceName, 64);
            buf.writeUtf(msg.grade, 16);
            buf.writeUtf(msg.trackSummary, 64);
            buf.writeInt(msg.totalStalls);
            buf.writeLong(msg.firstPlacePurse);
            buf.writeBoolean(msg.isTestRace);
            buf.writeBoolean(msg.confirmed);
            buf.writeInt(msg.runners.size());
            for (ResultRowEntry r : msg.runners) {
                buf.writeInt(r.gateNumber);
                buf.writeUtf(r.horseName, 64);
                buf.writeUtf(r.ownerName, 64);
                buf.writeUtf(r.ownerSilkCode, 64);
                buf.writeUtf(r.jockeyName, 64);
                buf.writeInt(r.placement);
                buf.writeBoolean(r.dnf);
                buf.writeBoolean(r.dsq);
                buf.writeUtf(r.formattedTime, 32);
                buf.writeUtf(r.formattedMargin, 32);
                buf.writeLong(r.projectedPurse);
            }
        }

        public static OpenRaceResultsScreenPacket decode(FriendlyByteBuf buf) {
            String rName = buf.readUtf(64);
            String grd = buf.readUtf(16);
            String trk = buf.readUtf(64);
            int stalls = buf.readInt();
            long purse = buf.readLong();
            boolean test = buf.readBoolean();
            boolean conf = buf.readBoolean();
            int count = buf.readInt();
            List<ResultRowEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new ResultRowEntry(
                        buf.readInt(),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readInt(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readUtf(32),
                        buf.readUtf(32),
                        buf.readLong()
                ));
            }
            return new OpenRaceResultsScreenPacket(rName, grd, trk, stalls, purse, test, conf, list);
        }

        public static void handle(OpenRaceResultsScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientScreens.handleOpenRaceResultsScreen(msg))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RaceResultsActionPacket {
        public static final int ACTION_TOGGLE_DSQ = 1;
        public static final int ACTION_CONFIRM_RESULTS = 2;

        public final int action;
        public final int gateNumber;

        public RaceResultsActionPacket(int action, int gateNumber) {
            this.action = action;
            this.gateNumber = gateNumber;
        }

        public static void encode(RaceResultsActionPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.action);
            buf.writeInt(msg.gateNumber);
        }

        public static RaceResultsActionPacket decode(FriendlyByteBuf buf) {
            return new RaceResultsActionPacket(buf.readInt(), buf.readInt());
        }

        public static void handle(RaceResultsActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (msg.action == ACTION_TOGGLE_DSQ) {
                    RacingManager.togglePendingRunnerDsq(player, msg.gateNumber);
                } else if (msg.action == ACTION_CONFIRM_RESULTS) {
                    RacingManager.confirmPendingRaceResults(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}