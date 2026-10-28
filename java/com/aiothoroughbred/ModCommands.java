package com.aiothoroughbred;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

import java.util.*;

public class ModCommands {

    // Active 10-second (200-tick) /tb kill purge confirmation requests per player
    public static class PurgeRequest {
        public final UUID playerUUID;
        public final UUID horseUUID;
        public final String horseName;
        public int ticksRemaining;

        public PurgeRequest(UUID playerUUID, UUID horseUUID, String horseName, int ticksRemaining) {
            this.playerUUID = playerUUID;
            this.horseUUID = horseUUID;
            this.horseName = horseName;
            this.ticksRemaining = ticksRemaining;
        }
    }

    // Active /tb trade offers (supports paid sales for $amount as well as $0 free giveaways)
    public static class TradeOffer {
        public final UUID sellerUUID;
        public final String sellerName;
        public final UUID buyerUUID;
        public final String buyerName;
        public final UUID horseUUID;
        public final String horseName;
        public final long price;
        public boolean sellerConfirmed = false;
        public int ticksRemaining = 1200; // 60s expiry

        public TradeOffer(UUID sellerUUID, String sellerName, UUID buyerUUID, String buyerName,
                          UUID horseUUID, String horseName, long price) {
            this.sellerUUID = sellerUUID;
            this.sellerName = sellerName;
            this.buyerUUID = buyerUUID;
            this.buyerName = buyerName;
            this.horseUUID = horseUUID;
            this.horseName = horseName;
            this.price = Math.max(0L, price);
        }
    }

    public static class RetirementRequest {
        public final UUID playerUUID;
        public final UUID horseUUID;
        public final String horseName;
        public int ticksRemaining = 600; // 30s confirmation window

        public RetirementRequest(UUID playerUUID, UUID horseUUID, String horseName) {
            this.playerUUID = playerUUID;
            this.horseUUID = horseUUID;
            this.horseName = horseName;
        }
    }

    public static class BreedingRequest {
        public final UUID sireUUID;
        public final String sireName;
        public final UUID sireOwnerUUID;
        public final String sireOwnerName;
        public final UUID damUUID;
        public final String damName;
        public final UUID damOwnerUUID;
        public final String damOwnerName;
        public final boolean initiatedByMareOwner;
        public long studFee = -1L; // -1 = awaiting stud fee quote from stallion owner; >= 0 = quoted/ready
        public boolean sireOwnerApproved = false;
        public boolean damOwnerApproved = false;
        public int ticksRemaining = 2400; // 120s expiry

        public BreedingRequest(UUID sireUUID, String sireName, UUID sireOwnerUUID, String sireOwnerName,
                               UUID damUUID, String damName, UUID damOwnerUUID, String damOwnerName,
                               boolean initiatedByMareOwner) {
            this.sireUUID = sireUUID;
            this.sireName = sireName;
            this.sireOwnerUUID = sireOwnerUUID;
            this.sireOwnerName = sireOwnerName;
            this.damUUID = damUUID;
            this.damName = damName;
            this.damOwnerUUID = damOwnerUUID;
            this.damOwnerName = damOwnerName;
            this.initiatedByMareOwner = initiatedByMareOwner;
        }
    }

    private static final Map<UUID, PurgeRequest> ACTIVE_PURGE_REQUESTS = new HashMap<>();
    private static final Map<UUID, RetirementRequest> ACTIVE_RETIRE_REQUESTS = new HashMap<>();
    private static final Map<UUID, BreedingRequest> BREED_REQ_BY_SIRE_OWNER = new HashMap<>();
    private static final Map<UUID, BreedingRequest> BREED_REQ_BY_DAM_OWNER = new HashMap<>();
    private static final Map<UUID, TradeOffer> OFFERS_BY_SELLER = new HashMap<>();
    private static final Map<UUID, TradeOffer> OFFERS_BY_BUYER = new HashMap<>();

    private static final List<String> EDITABLE_STATS = Arrays.asList(
            "speed", "speed_max",
            "stamina", "stamina_max",
            "power", "power_max",
            "guts", "guts_max",
            "wits", "wits_max",
            "mood", "temperament", "personality", "bond",
            "hunger", "thirst", "fatigue",
            "turf", "dirt",
            "min_distance", "max_distance",
            "strategy", "gender",
            "ailment", "injury", "career_ended", "roll_day",
            "coat", "trait", "hp", "retired", "heat", "age", "deaths_door"
    );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ACTIVE_RACE_HORSES = (ctx, builder) -> {
        List<String> activeNames = RacingManager.getActiveRaceRunnerNames();
        if (!activeNames.isEmpty()) {
            return SharedSuggestionProvider.suggest(activeNames, builder);
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && player.level() instanceof ServerLevel sl) {
            StudbookSavedData studbook = StudbookSavedData.get(sl);
            List<String> names = new ArrayList<>();
            for (StudbookSavedData.HorseBackupRecord rec : studbook.getAllLivingHorses()) {
                names.add(rec.horseName);
            }
            return SharedSuggestionProvider.suggest(names, builder);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_OWNED_HORSES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && player.level() instanceof ServerLevel sl) {
            StudbookSavedData studbook = StudbookSavedData.get(sl);
            List<String> names = new ArrayList<>();
            for (StudbookSavedData.HorseBackupRecord rec : studbook.getAllLivingHorses()) {
                if (rec.ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
                    names.add(rec.horseName);
                }
            }
            return SharedSuggestionProvider.suggest(names, builder);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SCHEDULE_IDS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && player.level() instanceof ServerLevel sl) {
            StudbookSavedData studbook = StudbookSavedData.get(sl);
            List<String> ids = new ArrayList<>();
            for (StudbookSavedData.ScheduledRace s : studbook.getAllScheduledRaces()) {
                ids.add(s.scheduleId);
            }
            return SharedSuggestionProvider.suggest(ids, builder);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EXISTING_RACE_NAMES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && player.level() instanceof ServerLevel sl) {
            StudbookSavedData studbook = StudbookSavedData.get(sl);
            Set<String> raceNames = new LinkedHashSet<>();
            for (RacingManager.ConsoleRaceConfig cfg : studbook.getAllRacingConsoles()) {
                for (RacingManager.RaceBaseType b : cfg.baseTypes) {
                    for (RacingManager.RaceDefinition r : b.races) {
                        raceNames.add(r.raceName.contains(" ") ? ("\"" + r.raceName + "\"") : r.raceName);
                    }
                }
            }
            return SharedSuggestionProvider.suggest(raceNames, builder);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SCHEDULE_HORSES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && player.level() instanceof ServerLevel sl) {
            try {
                String schedId = StringArgumentType.getString(ctx, "scheduleId");
                StudbookSavedData studbook = StudbookSavedData.get(sl);
                StudbookSavedData.ScheduledRace sched = studbook.getScheduledRace(schedId);
                if (sched != null) {
                    List<String> names = new ArrayList<>();
                    for (StudbookSavedData.ScheduledRace.RegistrationEntry reg : sched.registeredHorses) {
                        names.add(reg.horseName);
                    }
                    return SharedSuggestionProvider.suggest(names, builder);
                }
            } catch (Exception ignored) {}
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EDIT_STATS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(EDITABLE_STATS, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EDIT_VALUES = (ctx, builder) -> {
        String stat = StringArgumentType.getString(ctx, "stat").toLowerCase(Locale.ROOT);
        if ("temperament".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("1", "2", "3", "4", "5"), builder);
        } else if ("strategy".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList(
                    "Big Escape", "Big Escape / Front",
                    "Front Runner", "Front / Pace",
                    "Pace Chaser", "Pace / Late",
                    "Late Surger", "Late / End",
                    "End Closer", "Tactical"
            ), builder);
        } else if ("personality".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList(
                    "competitive", "lone_wolf", "crowd_pleaser", "laid_back", "glutton",
                    "stoic", "clingy", "timid", "defeatist", "quirky"
            ), builder);
        } else if ("gender".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("Male", "Female", "Gelding"), builder);
        } else if ("turf".equals(stat) || "dirt".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("0", "5", "8", "9", "10"), builder);
        } else if ("min_distance".equals(stat) || "max_distance".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("1000", "1200", "1600", "2000", "2400", "3000", "3600", "4000"), builder);
        } else if ("ailment".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList(
                    "fever", "laryngeal", "afib", "lameness", "unstable_legs",
                    "laminitis", "poor_prognosis", "eiph", "colic", "overweight", "roll_day", "clear"
            ), builder);
        } else if ("injury".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList(
                    "hoof_crack", "ligament", "bowed_tendon",
                    "non_catastrophic_fracture", "catastrophic_fracture",
                    "heal", "roll_day", "clear"
            ), builder);
        } else if ("career_ended".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("true", "false"), builder);
        } else if ("roll_day".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("1"), builder);
        } else if ("coat".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList(
                    "bay", "dark_bay", "chestnut", "black", "gray", "dapple_gray", "creamy", "pure_white",
                    "bay stockings", "chestnut stockings", "dark_bay white_dots", "black none"
            ), builder);
        } else if ("trait".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList(
                    "small_hooves", "large_hooves",
                    "x_factor", "x_factor_carrier", "iron_constitution", "explosive_late_gear", "born_mudder",
                    "hill_climber", "lightning_starter", "cornering_maestro", "deep_lung_capacity",
                    "sprinters_fast_twitch", "diesel_stayer",
                    "glass_legs", "roarer_gene", "weak_constitution", "high_strung_nerves",
                    "soft_palate_displacement", "brittle_hooves",
                    "clear"
            ), builder);
        } else if ("hp".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("50", "70", "85", "95", "100"), builder);
        } else if ("retired".equals(stat) || "heat".equals(stat)) {
            return SharedSuggestionProvider.suggest(Arrays.asList("true", "false"), builder);
        }
        return SharedSuggestionProvider.suggest(Arrays.asList("20", "50", "75", "100"), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_HORSES_QUOTED = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && player.level() instanceof ServerLevel sl) {
            StudbookSavedData studbook = StudbookSavedData.get(sl);
            List<String> names = new ArrayList<>();
            for (StudbookSavedData.HorseBackupRecord rec : studbook.getAllLivingHorses()) {
                names.add(rec.horseName.contains(" ") ? ("\"" + rec.horseName + "\"") : rec.horseName);
            }
            return SharedSuggestionProvider.suggest(names, builder);
        }
        return builder.buildFuture();
    };

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        registerRootCommand(dispatcher, "tb");
        registerRootCommand(dispatcher, "h"); // Alias for convenience
    }

    private static void registerRootCommand(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(Commands.literal(rootLiteral)
                // 0A. /tb owner — Horse Owner Registration, Crown Names & Jockey Silk Studio GUI
                //     + Admin /tb owner fee — Configurable defaults for Horse Owner Registration & Itemized Edit Fees
                .then(Commands.literal("owner")
                        .executes(ModCommands::cmdOwnerOpenGui)
                        .then(Commands.literal("fee")
                                .requires(s -> s.hasPermission(2))
                                .executes(ModCommands::cmdAdminOwnerFeeInfo)
                                .then(Commands.literal("registration")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> cmdAdminSetSingleOwnerFee(ctx, "registration"))))
                                .then(Commands.literal("farm")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> cmdAdminSetSingleOwnerFee(ctx, "farm"))))
                                .then(Commands.literal("crown")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> cmdAdminSetSingleOwnerFee(ctx, "crown"))))
                                .then(Commands.literal("silk")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> cmdAdminSetSingleOwnerFee(ctx, "silk"))))
                                .then(Commands.argument("regFee", LongArgumentType.longArg(0))
                                        .then(Commands.argument("farmRenameFee", LongArgumentType.longArg(0))
                                                .then(Commands.argument("crownEditFee", LongArgumentType.longArg(0))
                                                        .then(Commands.argument("silkEditFee", LongArgumentType.longArg(0))
                                                                .executes(ModCommands::cmdAdminSetAllOwnerFees)))))))

                // 0B. /tb studbook — Global Thoroughbred Studbook Browser GUI (by Year / Season)
                .then(Commands.literal("studbook")
                        .executes(ModCommands::cmdStudbookOpenGui))

                // 1. /tb claim
                .then(Commands.literal("claim")
                        .executes(ModCommands::cmdClaim))

                // 2. /tb unclaim
                .then(Commands.literal("unclaim")
                        .executes(ModCommands::cmdUnclaim))

                // 3. /tb trade [player] [amount (optional, defaults to $0)]
                .then(Commands.literal("trade")
                        .then(Commands.literal("confirm")
                                .executes(ModCommands::cmdTradeConfirm))
                        .then(Commands.literal("accept")
                                .executes(ModCommands::cmdTradeAccept))
                        .then(Commands.literal("decline")
                                .executes(ModCommands::cmdTradeDecline))
                        .then(Commands.argument("targetPlayer", EntityArgument.player())
                                .executes(ctx -> cmdTradeInitiate(ctx, 0L))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> cmdTradeInitiate(ctx, LongArgumentType.getLong(ctx, "amount"))))))

                // 4. /tb balance
                .then(Commands.literal("balance")
                        .executes(ModCommands::cmdBalance))

                // 5. /tb give [player name] [amount]
                .then(Commands.literal("give")
                        .then(Commands.argument("targetPlayer", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ModCommands::cmdGive))))

                // 6. /tb cashout [amount]
                .then(Commands.literal("cashout")
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ModCommands::cmdCashout)))

                // 7. /tb cashin [amount (Lightman's Currency $ or Emerald count; omit to cash in all coins)]
                .then(Commands.literal("cashin")
                        .executes(ctx -> cmdCashin(ctx, -1L))
                        .then(Commands.argument("emeraldAmount", LongArgumentType.longArg(0))
                                .executes(ctx -> cmdCashin(ctx, LongArgumentType.getLong(ctx, "emeraldAmount")))))

                // 8. /tb setstable [horse name (optional if riding)]
                .then(Commands.literal("setstable")
                        .executes(ctx -> cmdSetStable(ctx, null))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdSetStable(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 9. /tb stable [horse name]
                .then(Commands.literal("stable")
                        .executes(ctx -> cmdStable(ctx, null))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdStable(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 10. /tb lock [horse name]
                .then(Commands.literal("lock")
                        .executes(ctx -> cmdLock(ctx, null))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdLock(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 11. /tb here [horse name]
                .then(Commands.literal("here")
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdHere(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 12. /tb tp [horse name]
                .then(Commands.literal("tp")
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdTp(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 13. /tb kill & /tb kill confirm
                .then(Commands.literal("kill")
                        .executes(ModCommands::cmdKillRequest)
                        .then(Commands.literal("confirm")
                                .executes(ModCommands::cmdKillConfirm)))

                // 13B. /tb euthanize [horse name (optional)] — Humane euthanasia ONLY for horses with Poor Prognosis (no Wither sound!)
                .then(Commands.literal("euthanize")
                        .executes(ctx -> cmdEuthanize(ctx, null))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdEuthanize(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 13C. /tb vet [horse name (optional)] — Veterinary examination & paid treatment
                .then(Commands.literal("vet")
                        .executes(ctx -> cmdVet(ctx, null))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdVet(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 13D. /tb retire [horse name (optional)] & /tb retire confirm — Retire racehorse for breeding
                .then(Commands.literal("retire")
                        .executes(ctx -> cmdRetireRequest(ctx, null))
                        .then(Commands.literal("confirm")
                                .executes(ModCommands::cmdRetireConfirm))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdRetireRequest(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 13E. /tb pedigree [horse name (optional)] — Open 4-Generation Pedigree Tree GUI
                .then(Commands.literal("pedigree")
                        .executes(ctx -> cmdPedigree(ctx, null))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_OWNED_HORSES)
                                .executes(ctx -> cmdPedigree(ctx, StringArgumentType.getString(ctx, "horseName")))))

                // 13F. /tb breed — Breeding, Stud Fees, Consent & Admin Testing Commands
                .then(Commands.literal("breed")
                        .then(Commands.literal("fee")
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ModCommands::cmdBreedSetFee)))
                        .then(Commands.literal("accept")
                                .executes(ModCommands::cmdBreedAccept))
                        .then(Commands.literal("approve")
                                .executes(ModCommands::cmdBreedAccept))
                        .then(Commands.literal("decline")
                                .executes(ModCommands::cmdBreedDecline))
                        // Admin Testing Subcommands: heat, accelerate, birth, grow
                        .then(Commands.literal("heat")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> cmdAdminBreedHeat(ctx, null))
                                .then(Commands.argument("mareName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_OWNED_HORSES)
                                        .executes(ctx -> cmdAdminBreedHeat(ctx, StringArgumentType.getString(ctx, "mareName")))))
                        .then(Commands.literal("accelerate")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("mareName", StringArgumentType.string())
                                        .suggests(SUGGEST_ALL_HORSES_QUOTED)
                                        .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                                                .executes(ModCommands::cmdAdminBreedAccelerate))))
                        .then(Commands.literal("birth")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> cmdAdminBreedBirth(ctx, null))
                                .then(Commands.argument("mareName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_OWNED_HORSES)
                                        .executes(ctx -> cmdAdminBreedBirth(ctx, StringArgumentType.getString(ctx, "mareName")))))
                        .then(Commands.literal("grow")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> cmdAdminBreedGrow(ctx, null))
                                .then(Commands.argument("foalName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_OWNED_HORSES)
                                        .executes(ctx -> cmdAdminBreedGrow(ctx, StringArgumentType.getString(ctx, "foalName")))))
                        // Standard breeding: /tb breed "Stallion Name" "Mare Name"
                        .then(Commands.argument("stallionName", StringArgumentType.string())
                                .suggests(SUGGEST_ALL_HORSES_QUOTED)
                                .then(Commands.argument("mareName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_OWNED_HORSES)
                                        .executes(ctx -> cmdBreedInitiate(
                                                ctx,
                                                StringArgumentType.getString(ctx, "stallionName"),
                                                StringArgumentType.getString(ctx, "mareName"),
                                                false)))))

                // =============================================================
                // ADMIN USAGE ONLY (Permission Level >= 2)
                // =============================================================
                // 14. /tb forge [money amount] [player name (optional)]
                .then(Commands.literal("forge")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> cmdAdminForge(ctx, false))
                                .then(Commands.argument("targetPlayer", EntityArgument.player())
                                        .executes(ctx -> cmdAdminForge(ctx, true)))))

                // 15. /tb edit [stat] [value]
                .then(Commands.literal("edit")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests(SUGGEST_EDIT_STATS)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_EDIT_VALUES)
                                        .executes(ModCommands::cmdAdminEdit))))

                // 16. /tb deaths (Opens Scrollable Deceased Horses GUI)
                .then(Commands.literal("deaths")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::cmdAdminDeaths))

                // 17. /tb revive [horse UUID]
                .then(Commands.literal("revive")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("horseUUID", StringArgumentType.word())
                                .executes(ModCommands::cmdAdminRevive)))

                // 18. /tb force claim | unclaim | transfer [player] | breed [stallion] [mare]
                .then(Commands.literal("force")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("claim")
                                .executes(ModCommands::cmdAdminForceClaim))
                        .then(Commands.literal("unclaim")
                                .executes(ModCommands::cmdAdminForceUnclaim))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("targetPlayer", EntityArgument.player())
                                        .executes(ModCommands::cmdAdminForceTransfer)))
                        .then(Commands.literal("breed")
                                .then(Commands.argument("stallionName", StringArgumentType.string())
                                        .suggests(SUGGEST_ALL_HORSES_QUOTED)
                                        .then(Commands.argument("mareName", StringArgumentType.greedyString())
                                                .suggests(SUGGEST_OWNED_HORSES)
                                                .executes(ctx -> cmdBreedInitiate(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "stallionName"),
                                                        StringArgumentType.getString(ctx, "mareName"),
                                                        true))))))

                // 18A-0. /tb gelding <all|admin> — Server owner toggle for whether gelding with Shears is open to all players or admin-exclusive
                .then(Commands.literal("gelding")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("all")
                                .executes(ctx -> cmdAdminSetGeldingPermission(ctx, false)))
                        .then(Commands.literal("players")
                                .executes(ctx -> cmdAdminSetGeldingPermission(ctx, false)))
                        .then(Commands.literal("admin")
                                .executes(ctx -> cmdAdminSetGeldingPermission(ctx, true))))

                // 18A. /tb timeflow — World Time Flow Selector GUI & Presets (1-Year, 4-Month, 3-Month, Custom)
                .then(Commands.literal("timeflow")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::cmdAdminTimeFlowGui)
                        .then(Commands.literal("set")
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.asList("1year", "4month", "3month", "custom"), b))
                                        .executes(ctx -> cmdAdminTimeFlowSet(ctx, 60))
                                        .then(Commands.argument("customDays", IntegerArgumentType.integer(1, 3650))
                                                .executes(ctx -> cmdAdminTimeFlowSet(ctx, IntegerArgumentType.getInteger(ctx, "customDays")))))))

                // 18B. /tb dq [horse name] (and /tb dsq [horse name]) — Disqualify a horse from an active or pending race (DSQ)
                .then(Commands.literal("dq")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_ACTIVE_RACE_HORSES)
                                .executes(ModCommands::cmdAdminDisqualifyHorse)))
                .then(Commands.literal("dsq")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                .suggests(SUGGEST_ACTIVE_RACE_HORSES)
                                .executes(ModCommands::cmdAdminDisqualifyHorse)))

                // 18C. /tb results — Admin Steward Race Results Review & Confirmation HUD (/tb results, /tb results confirm, /tb results dq <horse>)
                .then(Commands.literal("results")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::cmdAdminRaceResultsOpenGui)
                        .then(Commands.literal("confirm")
                                .executes(ModCommands::cmdAdminRaceResultsConfirm))
                        .then(Commands.literal("accept")
                                .executes(ModCommands::cmdAdminRaceResultsConfirm))
                        .then(Commands.literal("dq")
                                .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_ACTIVE_RACE_HORSES)
                                        .executes(ModCommands::cmdAdminDisqualifyHorse)))
                        .then(Commands.literal("dsq")
                                .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_ACTIVE_RACE_HORSES)
                                        .executes(ModCommands::cmdAdminDisqualifyHorse))))

                // 18D. /tb reloadlang — Reload user-edited external translation JSON files from aiothoroughbred_translations/
                .then(Commands.literal("reloadlang")
                        .executes(ModCommands::cmdReloadTranslations))

                // 19. /tb schedule (Opens Interactive Racing Schedule & Betting GUI!)
                .then(Commands.literal("schedule")
                        .executes(ModCommands::cmdScheduleOpenGui)
                        .then(Commands.literal("gui")
                                .executes(ModCommands::cmdScheduleOpenGui))
                        .then(Commands.literal("list")
                                .executes(ModCommands::cmdScheduleList))
                        .then(Commands.literal("view")
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ModCommands::cmdScheduleView)))
                        .then(Commands.literal("register")
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ctx -> cmdScheduleRegister(ctx, null))
                                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                                .suggests(SUGGEST_OWNED_HORSES)
                                                .executes(ctx -> cmdScheduleRegister(ctx, StringArgumentType.getString(ctx, "horseName"))))))
                        .then(Commands.literal("unregister")
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ctx -> cmdScheduleUnregister(ctx, null))
                                        .then(Commands.argument("horseName", StringArgumentType.greedyString())
                                                .suggests(SUGGEST_OWNED_HORSES)
                                                .executes(ctx -> cmdScheduleUnregister(ctx, StringArgumentType.getString(ctx, "horseName"))))))
                        .then(Commands.literal("bet")
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .then(Commands.argument("horseName", StringArgumentType.string())
                                                .suggests(SUGGEST_SCHEDULE_HORSES)
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                        .executes(ModCommands::cmdScheduleBet)))))
                        .then(Commands.literal("mybets")
                                .executes(ModCommands::cmdScheduleMyBets))
                        // Admin schedule subcommands:
                        .then(Commands.literal("add")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("raceName", StringArgumentType.string())
                                        .suggests(SUGGEST_EXISTING_RACE_NAMES)
                                        .then(Commands.argument("dateString", StringArgumentType.greedyString())
                                                .executes(ModCommands::cmdScheduleAdminAdd))))
                        .then(Commands.literal("open")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ModCommands::cmdScheduleAdminOpen)))
                        .then(Commands.literal("close")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ModCommands::cmdScheduleAdminClose)))
                        .then(Commands.literal("cancel")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ModCommands::cmdScheduleAdminCancel)))
                        .then(Commands.literal("remove")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("scheduleId", StringArgumentType.word())
                                        .suggests(SUGGEST_SCHEDULE_IDS)
                                        .executes(ModCommands::cmdScheduleAdminRemove))))
        );
    }

    /**
     * Ticks the 10-second `/tb kill` confirmation window, `/tb trade` expiry timers,
     * and cleans up revived horses from the death list after 8 seconds!
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        // 1. Tick 10-second /tb kill confirmation timers
        Iterator<Map.Entry<UUID, PurgeRequest>> it = ACTIVE_PURGE_REQUESTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PurgeRequest> entry = it.next();
            PurgeRequest req = entry.getValue();
            req.ticksRemaining--;
            if (req.ticksRemaining <= 0) {
                it.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(req.playerUUID);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§cConfirmation window time up, purge request disengaged."));
                }
            }
        }

        // 2. Tick trade offers
        Iterator<Map.Entry<UUID, TradeOffer>> tradeIt = OFFERS_BY_SELLER.entrySet().iterator();
        while (tradeIt.hasNext()) {
            Map.Entry<UUID, TradeOffer> entry = tradeIt.next();
            TradeOffer offer = entry.getValue();
            offer.ticksRemaining--;
            if (offer.ticksRemaining <= 0) {
                tradeIt.remove();
                OFFERS_BY_BUYER.remove(offer.buyerUUID);
            }
        }

        // 2B. Tick retirement confirmation & breeding request timers
        Iterator<Map.Entry<UUID, RetirementRequest>> retIt = ACTIVE_RETIRE_REQUESTS.entrySet().iterator();
        while (retIt.hasNext()) {
            RetirementRequest rReq = retIt.next().getValue();
            rReq.ticksRemaining--;
            if (rReq.ticksRemaining <= 0) {
                retIt.remove();
            }
        }
        Iterator<Map.Entry<UUID, BreedingRequest>> brIt = BREED_REQ_BY_SIRE_OWNER.entrySet().iterator();
        while (brIt.hasNext()) {
            BreedingRequest bReq = brIt.next().getValue();
            bReq.ticksRemaining--;
            if (bReq.ticksRemaining <= 0) {
                brIt.remove();
                BREED_REQ_BY_DAM_OWNER.remove(bReq.damOwnerUUID);
            }
        }

        // 3. Every 2 seconds (40 ticks), auto-remove revived horses from the death list after 8 seconds
        if (server.getTickCount() % 40 == 0) {
            StudbookSavedData studbook = StudbookSavedData.get(server.overworld());
            studbook.cleanupExpiredRevivedRecords();
        }
    }

    // =========================================================================
    // HELPER: Check if a horse is currently in a race / starting gate
    // =========================================================================
    private static boolean isHorseCurrentlyInRace(Horse horse, ThoroughbredData data) {
        if (data != null && data.isInRace()) {
            return true;
        }
        if (horse != null) {
            ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
            if (state.inStartingGate || state.gateBreakActive) {
                return true;
            }
        }
        return false;
    }

    /**
     * Locates a living Horse entity across dimensions and even inside unloaded chunks!
     */
    private static Horse findOrLoadHorseEntity(MinecraftServer server, StudbookSavedData.HorseBackupRecord rec) {
        if (rec == null || rec.horseUUID == null) return null;

        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(rec.horseUUID);
            if (e instanceof Horse h && h.isAlive()) {
                return h;
            }
        }

        ServerLevel targetLevel = getLevelByName(server, rec.lastDimension);
        if (targetLevel != null) {
            BlockPos lastPos = BlockPos.containing(rec.lastX, rec.lastY, rec.lastZ);
            int chunkX = lastPos.getX() >> 4;
            int chunkZ = lastPos.getZ() >> 4;
            targetLevel.setChunkForced(chunkX, chunkZ, true);
            LevelChunk chunk = targetLevel.getChunk(chunkX, chunkZ);
            Entity e = targetLevel.getEntity(rec.horseUUID);
            targetLevel.setChunkForced(chunkX, chunkZ, false);
            if (e instanceof Horse h && h.isAlive()) {
                return h;
            }
        }
        return null;
    }

    private static ServerLevel getLevelByName(MinecraftServer server, String dimString) {
        if (dimString == null || dimString.isEmpty()) {
            return server.overworld();
        }
        ResourceLocation loc = ResourceLocation.tryParse(dimString);
        if (loc != null) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
            ServerLevel lvl = server.getLevel(key);
            if (lvl != null) return lvl;
        }
        return server.overworld();
    }

    // =========================================================================
    // 1. /tb claim
    // =========================================================================
    private static int cmdClaim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (!(player.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cYou must be riding a registered Thoroughbred to claim it!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (data.hasOwner()) {
            if (data.isOwnedBy(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cThis horse is already yours!"));
            } else {
                player.sendSystemMessage(Component.literal("§cThis horse is already claimed by another player!"));
            }
            return 0;
        }

        data.setOwner(player.getUUID(), player.getName().getString());
        data.saveToHorse(horse);

        player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a§l[Claimed!] §e%s §fis now registered under §b%s§f and is immune to player attacks!",
                data.getName(), data.getOwnerName())));
        return 1;
    }

    // =========================================================================
    // 2. /tb unclaim
    // =========================================================================
    private static int cmdUnclaim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (!(player.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cYou must be riding a registered Thoroughbred to unclaim it!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou can't unclaim a horse you don't own!"));
            return 0;
        }

        data.clearOwner();
        data.saveToHorse(horse);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§e[Unclaimed] §fYou have unclaimed §6%s§f.", data.getName())));
        return 1;
    }

    // =========================================================================
    // 3. /tb trade [player] [amount] + confirm / accept / decline
    // =========================================================================
    private static int cmdTradeInitiate(CommandContext<CommandSourceStack> ctx, long price) {
        ServerPlayer seller = ctx.getSource().getPlayer();
        if (seller == null) return 0;

        ServerPlayer buyer;
        try {
            buyer = EntityArgument.getPlayer(ctx, "targetPlayer");
        } catch (Exception e) {
            seller.sendSystemMessage(Component.literal("§cCould not find that player!"));
            return 0;
        }

        if (seller.getUUID().equals(buyer.getUUID())) {
            seller.sendSystemMessage(Component.literal("§cYou cannot trade a horse to yourself!"));
            return 0;
        }

        if (!(seller.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            seller.sendSystemMessage(Component.literal("§cYou must be riding the Thoroughbred you want to trade!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(seller.getUUID())) {
            seller.sendSystemMessage(Component.literal("§cThis isn't your horse, thief!"));
            return 0;
        }

        if (isHorseCurrentlyInRace(horse, data)) {
            seller.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        TradeOffer offer = new TradeOffer(
                seller.getUUID(), seller.getName().getString(),
                buyer.getUUID(), buyer.getName().getString(),
                horse.getUUID(), data.getName(),
                price
        );
        OFFERS_BY_SELLER.put(seller.getUUID(), offer);
        OFFERS_BY_BUYER.put(buyer.getUUID(), offer);

        String priceTag = (price > 0L) ? ("for §a$" + price + "§e") : "for §aFREE ($0)§e";
        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "§6[Trade Confirmation] §eAre you sure you want to trade §f%s §eto §b%s %s? ",
                data.getName(), buyer.getName().getString(), priceTag));

        MutableComponent confirmBtn = Component.literal("§a§l[CONFIRM (/tb trade confirm)]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb trade confirm"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm sending this horse trade offer"))));

        seller.sendSystemMessage(msg.append(confirmBtn));
        return 1;
    }

    private static int cmdTradeConfirm(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer seller = ctx.getSource().getPlayer();
        if (seller == null) return 0;

        TradeOffer offer = OFFERS_BY_SELLER.get(seller.getUUID());
        if (offer == null) {
            seller.sendSystemMessage(Component.literal("§cYou have no pending trade offer to confirm!"));
            return 0;
        }

        ServerPlayer buyer = seller.server.getPlayerList().getPlayer(offer.buyerUUID);
        if (buyer == null) {
            OFFERS_BY_SELLER.remove(seller.getUUID());
            seller.sendSystemMessage(Component.literal("§cThe recipient is no longer online!"));
            return 0;
        }

        offer.sellerConfirmed = true;
        offer.ticksRemaining = 1200; // 60 seconds for recipient to respond

        String priceDesc = (offer.price > 0L) ? ("for §a$" + offer.price + "§f") : "for §aFREE ($0)§f";
        seller.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a[Trade Sent] §fSent trade offer for §6%s §fto §b%s %s.",
                offer.horseName, offer.buyerName, priceDesc)));

        MutableComponent buyerMsg = Component.literal(String.format(Locale.ROOT,
                "§6§l[Horse Trade Offer] §b%s §fwants to trade Thoroughbred §6%s §fto you %s! ",
                offer.sellerName, offer.horseName, priceDesc));

        MutableComponent acceptBtn = Component.literal("§a§l[ACCEPT]")
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb trade accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb trade accept"))));

        MutableComponent declineBtn = Component.literal(" §c§l[DECLINE]")
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb trade decline"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb trade decline"))));

        buyer.sendSystemMessage(buyerMsg.append(acceptBtn).append(declineBtn));
        return 1;
    }

    private static int cmdTradeAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer buyer = ctx.getSource().getPlayer();
        if (buyer == null) return 0;

        TradeOffer offer = OFFERS_BY_BUYER.get(buyer.getUUID());
        if (offer == null || !offer.sellerConfirmed) {
            buyer.sendSystemMessage(Component.literal("§cYou have no active horse trade offer to accept!"));
            return 0;
        }

        StudbookSavedData studbook = StudbookSavedData.get(buyer.serverLevel());
        if (offer.price > 0L && studbook.getBalance(buyer.getUUID()) < offer.price) {
            buyer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§cYou do not have enough money ($%d) to accept this horse trade!", offer.price)));
            return 0;
        }

        StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(offer.horseName);
        Horse horse = findOrLoadHorseEntity(buyer.server, rec);

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            OFFERS_BY_BUYER.remove(buyer.getUUID());
            OFFERS_BY_SELLER.remove(offer.sellerUUID);
            buyer.sendSystemMessage(Component.literal("§cCould not locate the traded horse!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (isHorseCurrentlyInRace(horse, data)) {
            buyer.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        OFFERS_BY_BUYER.remove(buyer.getUUID());
        OFFERS_BY_SELLER.remove(offer.sellerUUID);

        if (offer.price > 0L) {
            studbook.tryDeductBalance(buyer.getUUID(), offer.price);
            studbook.addBalance(offer.sellerUUID, offer.price);
        }

        data.setOwner(buyer.getUUID(), buyer.getName().getString());
        data.setLocked(false);
        data.saveToHorse(horse);

        if (offer.price > 0L) {
            buyer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Trade Complete!] §fYou bought §6%s §ffor §a$%d§f! Your balance is now §a$%d§f.",
                    data.getName(), offer.price, studbook.getBalance(buyer.getUUID()))));
        } else {
            buyer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Trade Complete!] §fYou are now the owner of §6%s§f!",
                    data.getName())));
        }

        ServerPlayer seller = buyer.server.getPlayerList().getPlayer(offer.sellerUUID);
        if (seller != null) {
            if (offer.price > 0L) {
                seller.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§a§l[Trade Complete!] §b%s §fbought §6%s §ffor §a$%d§f! Your balance is now §a$%d§f.",
                        buyer.getName().getString(), data.getName(), offer.price, studbook.getBalance(seller.getUUID()))));
            } else {
                seller.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§a§l[Trade Complete!] §b%s §faccepted your trade for §6%s§f!",
                        buyer.getName().getString(), data.getName())));
            }
        }
        return 1;
    }

    private static int cmdTradeDecline(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer buyer = ctx.getSource().getPlayer();
        if (buyer == null) return 0;

        TradeOffer offer = OFFERS_BY_BUYER.remove(buyer.getUUID());
        if (offer == null) {
            buyer.sendSystemMessage(Component.literal("§cYou have no active horse trade offer to decline!"));
            return 0;
        }
        OFFERS_BY_SELLER.remove(offer.sellerUUID);

        buyer.sendSystemMessage(Component.literal("§eYou declined the horse trade offer for §6" + offer.horseName + "§e."));
        ServerPlayer seller = buyer.server.getPlayerList().getPlayer(offer.sellerUUID);
        if (seller != null) {
            seller.sendSystemMessage(Component.literal("§c" + buyer.getName().getString() + " declined your trade offer for " + offer.horseName + "."));
        }
        return 1;
    }

    // =========================================================================
    // 4. /tb balance
    // =========================================================================
    private static int cmdBalance(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        long balance = studbook.getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("§6[Bank] §fYour current balance is §a$" + balance + "§f."));
        return 1;
    }

    // =========================================================================
    // 5. /tb give [player name] [amount]
    // =========================================================================
    private static int cmdGive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer sender = ctx.getSource().getPlayer();
        if (sender == null) return 0;

        ServerPlayer recipient;
        try {
            recipient = EntityArgument.getPlayer(ctx, "targetPlayer");
        } catch (Exception e) {
            sender.sendSystemMessage(Component.literal("§cCould not find that player!"));
            return 0;
        }

        long amount = LongArgumentType.getLong(ctx, "amount");
        String recipientName = recipient.getName().getString();

        StudbookSavedData studbook = StudbookSavedData.get(sender.serverLevel());
        if (studbook.getBalance(sender.getUUID()) < amount) {
            sender.sendSystemMessage(Component.literal(
                    "§cYou do not have the required amount to give to " + recipientName + "!"));
            return 0;
        }

        if (sender.getUUID().equals(recipient.getUUID())) {
            sender.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§eYou just took $%d out of your own balance, then transferred it into your own balance. The bank will be happy with this!",
                    amount)));
            return 1;
        }

        studbook.tryDeductBalance(sender.getUUID(), amount);
        studbook.addBalance(recipient.getUUID(), amount);

        sender.sendSystemMessage(Component.literal(
                "§aYou have given $" + amount + " to " + recipientName + "."));
        recipient.sendSystemMessage(Component.literal(
                "§a" + sender.getName().getString() + " has given you $" + amount + "! Your balance is now $" + studbook.getBalance(recipient.getUUID()) + "."));
        return 1;
    }

    // =========================================================================
    // 6. /tb cashout [amount]
    //    - Primary (Lightman's Currency installed): $1 = 1 Copper Coin, $10 = 1 Iron Coin,
    //      $100 = 1 Gold Coin, $1,000 = 1 Emerald Coin, $10,000 = 1 Diamond Coin, $100,000 = 1 Netherite Coin
    //    - Fallback (if Lightman's Currency is not installed): $10 = 1 Emerald (9 Emeralds = 1 Emerald Block)
    // =========================================================================
    private static final String[] LC_COIN_IDS = {
            "lightmanscurrency:coin_netherite",
            "lightmanscurrency:coin_diamond",
            "lightmanscurrency:coin_emerald",
            "lightmanscurrency:coin_gold",
            "lightmanscurrency:coin_iron",
            "lightmanscurrency:coin_copper"
    };
    private static final long[] LC_COIN_VALUES = {
            100_000L, 10_000L, 1_000L, 100L, 10L, 1L
    };
    private static final String[] LC_COIN_LABELS = {
            "Netherite Coin", "Diamond Coin", "Emerald Coin", "Gold Coin", "Iron Coin", "Copper Coin"
    };

    private static boolean isLightmansCurrencyLoaded() {
        ResourceLocation copperLoc = ResourceLocation.tryParse("lightmanscurrency:coin_copper");
        return copperLoc != null && ForgeRegistries.ITEMS.containsKey(copperLoc)
                && ForgeRegistries.ITEMS.getValue(copperLoc) != Items.AIR;
    }

    private static long getLightmansCoinItemValue(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !"lightmanscurrency".equals(id.getNamespace())) {
            return 0L;
        }
        return switch (id.getPath()) {
            case "coin_copper" -> 1L;
            case "coinpile_copper" -> 9L;
            case "coinblock_copper" -> 36L;
            case "coin_iron" -> 10L;
            case "coinpile_iron" -> 90L;
            case "coinblock_iron" -> 360L;
            case "coin_gold" -> 100L;
            case "coinpile_gold" -> 900L;
            case "coinblock_gold" -> 3_600L;
            case "coin_emerald" -> 1_000L;
            case "coinpile_emerald" -> 9_000L;
            case "coinblock_emerald" -> 36_000L;
            case "coin_diamond" -> 10_000L;
            case "coinpile_diamond" -> 90_000L;
            case "coinblock_diamond" -> 360_000L;
            case "coin_netherite" -> 100_000L;
            case "coinpile_netherite" -> 900_000L;
            case "coinblock_netherite" -> 3_600_000L;
            default -> 0L;
        };
    }

    private static void giveLightmansCoinsForDollarValue(ServerPlayer player, long dollarAmount, List<String> summaryParts) {
        long rem = dollarAmount;
        for (int i = 0; i < LC_COIN_IDS.length && rem > 0L; i++) {
            long val = LC_COIN_VALUES[i];
            if (rem < val) continue;
            ResourceLocation loc = ResourceLocation.tryParse(LC_COIN_IDS[i]);
            Item coinItem = (loc != null) ? ForgeRegistries.ITEMS.getValue(loc) : null;
            if (coinItem == null || coinItem == Items.AIR) continue;

            long count = rem / val;
            rem %= val;
            long toGive = count;
            while (toGive > 0L) {
                int stackSize = (int) Math.min(64L, toGive);
                player.getInventory().placeItemBackInInventory(new ItemStack(coinItem, stackSize));
                toGive -= stackSize;
            }
            if (summaryParts != null) {
                summaryParts.add(count + "x " + LC_COIN_LABELS[i]);
            }
        }
    }

    private static int cmdCashout(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        long amount = LongArgumentType.getLong(ctx, "amount");

        if (amount == 0L) {
            player.sendSystemMessage(Component.literal("§cDon't you have anything better to do?"));
            return 0;
        }

        if (isLightmansCurrencyLoaded()) {
            if (amount < 1L) {
                player.sendSystemMessage(Component.literal("§cCash out failed - minimum withdrawal is $1 (1 Copper Coin)!"));
                return 0;
            }
            StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
            if (!studbook.tryDeductBalance(player.getUUID(), amount)) {
                player.sendSystemMessage(Component.literal("§cYou do not have sufficient funds to cash out!"));
                return 0;
            }

            long remainingBalance = studbook.getBalance(player.getUUID());
            List<String> givenSummary = new ArrayList<>();
            giveLightmansCoinsForDollarValue(player, amount, givenSummary);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS, 0.9F, 1.35F);

            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Bank Cashout] §aYou cashed out §6$%,d §ainto §e%s§a! Your balance is now §6$%,d§a.",
                    amount, String.join(", ", givenSummary), remainingBalance)));
            return 1;
        }

        // Fallback when Lightman's Currency is not installed: $10 = 1 Emerald
        if (amount < 0L || (amount % 10L) != 0L) {
            player.sendSystemMessage(Component.literal("§cCash out failed - emerald exchange rate is one per $10! (Install Lightman's Currency for $1 = 1 Copper Coin)"));
            return 0;
        }

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        if (!studbook.tryDeductBalance(player.getUUID(), amount)) {
            player.sendSystemMessage(Component.literal("§cYou do not have sufficient funds to cash out!"));
            return 0;
        }

        long remainingBalance = studbook.getBalance(player.getUUID());
        long totalEmeralds = amount / 10L;
        long emeraldBlocks = totalEmeralds / 9L;
        int singleEmeralds = (int) (totalEmeralds % 9L);

        long blocksToGive = emeraldBlocks;
        while (blocksToGive > 0L) {
            int stackSize = (int) Math.min(64L, blocksToGive);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD_BLOCK, stackSize));
            blocksToGive -= stackSize;
        }

        if (singleEmeralds > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, singleEmeralds));
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.3F);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§aYou have cashed out $%,d into %d emerald(s)! Your balance is now $%,d.",
                amount, totalEmeralds, remainingBalance)));
        return 1;
    }

    // =========================================================================
    // 7. /tb cashin [amount]
    //    - Primary (Lightman's Currency installed): Deposits $amount worth of Lightman's Currency
    //      coins/coinpiles/coinblocks (1 Copper Coin = $1, 1 Iron Coin = $10, etc.) and automatically
    //      returns exact change if a larger coin is broken! Running `/tb cashin` with no argument
    //      deposits all Lightman's Currency coins in your inventory.
    //    - Fallback (if Lightman's Currency is not installed): 1 Emerald = $10 (1 Emerald Block = 9 Emeralds)
    // =========================================================================
    private static int cmdCashin(CommandContext<CommandSourceStack> ctx, long requestedArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (requestedArg == 0L) {
            player.sendSystemMessage(Component.literal("§cQuit wasting the bank's time, will you?"));
            return 0;
        }

        if (isLightmansCurrencyLoaded()) {
            long totalCoinDollarValue = 0L;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    long unitVal = getLightmansCoinItemValue(s.getItem());
                    if (unitVal > 0L) {
                        totalCoinDollarValue += unitVal * s.getCount();
                    }
                }
            }

            if (totalCoinDollarValue <= 0L) {
                player.sendSystemMessage(Component.literal("§cYou don't have any coins in your inventory to cash in! (1 Copper Coin = $1, 1 Iron Coin = $10)"));
                return 0;
            }

            long depositDollars = (requestedArg < 0L) ? totalCoinDollarValue : requestedArg;
            if (totalCoinDollarValue < depositDollars) {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§cYou only have $%,d worth of coins in your inventory (requested $%,d)!",
                        totalCoinDollarValue, depositDollars)));
                return 0;
            }

            // Collect coins from smallest denomination to largest so we break larger coins only when necessary
            long needed = depositDollars;
            long[] ascendingTargets = {1L, 9L, 10L, 36L, 90L, 100L, 360L, 900L, 1_000L, 3_600L, 9_000L, 10_000L, 36_000L, 90_000L, 100_000L, 360_000L, 900_000L, 3_600_000L};
            for (long denom : ascendingTargets) {
                if (needed <= 0L) break;
                for (int i = 0; i < player.getInventory().getContainerSize() && needed > 0L; i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (s.isEmpty()) continue;
                    long unitVal = getLightmansCoinItemValue(s.getItem());
                    if (unitVal != denom) continue;

                    while (!s.isEmpty() && needed > 0L) {
                        s.shrink(1);
                        if (needed >= unitVal) {
                            needed -= unitVal;
                        } else {
                            long changeDollars = unitVal - needed;
                            needed = 0L;
                            giveLightmansCoinsForDollarValue(player, changeDollars, null);
                        }
                    }
                }
            }

            StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
            long newBalance = studbook.addBalance(player.getUUID(), depositDollars);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS, 0.9F, 1.45F);

            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Bank Deposit] §aYou cashed in §6$%,d §ain coins! Your bank balance is now §6$%,d§a.",
                    depositDollars, newBalance)));
            return 1;
        }

        // Fallback when Lightman's Currency is not installed: 1 Emerald = $10
        long totalAvailableEmeralds = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) {
                totalAvailableEmeralds += s.getCount();
            } else if (s.is(Items.EMERALD_BLOCK)) {
                totalAvailableEmeralds += (long) s.getCount() * 9L;
            }
        }

        long emeraldAmount = (requestedArg < 0L) ? totalAvailableEmeralds : requestedArg;
        if (emeraldAmount <= 0L || totalAvailableEmeralds < emeraldAmount) {
            player.sendSystemMessage(Component.literal("§cYou don't have enough emeralds to cash in!"));
            return 0;
        }

        long needed = emeraldAmount;

        // Pass 1: Consume loose emeralds first
        for (int i = 0; i < player.getInventory().getContainerSize() && needed > 0L; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) {
                int take = (int) Math.min(needed, s.getCount());
                s.shrink(take);
                needed -= take;
            }
        }

        // Pass 2: Consume Emerald Blocks (each block = 9 emeralds), returning loose emerald change if needed < 9
        for (int i = 0; i < player.getInventory().getContainerSize() && needed > 0L; i++) {
            ItemStack s = player.getInventory().getItem(i);
            while (s.is(Items.EMERALD_BLOCK) && !s.isEmpty() && needed > 0L) {
                s.shrink(1);
                if (needed >= 9L) {
                    needed -= 9L;
                } else {
                    int change = (int) (9L - needed);
                    needed = 0L;
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, change));
                }
            }
        }

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        long newBalance = studbook.addBalance(player.getUUID(), emeraldAmount * 10L);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.4F);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§aYou have cashed in %d emeralds! Your balance is now $%,d.",
                emeraldAmount, newBalance)));
        return 1;
    }

    // =========================================================================
    // 8. /tb setstable [horse name]
    // =========================================================================
    private static int cmdSetStable(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Horse horse = null;
        if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            horse = ridden;
        } else if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);
            horse = findOrLoadHorseEntity(player.server, rec);
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cRide your Thoroughbred or specify its name: /tb setstable [horse name]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (horseNameArg != null && !horseNameArg.isBlank() && !data.getName().equalsIgnoreCase(horseNameArg.trim())) {
            player.sendSystemMessage(Component.literal("§cYou are not riding '" + horseNameArg + "'!"));
            return 0;
        }

        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cThis horse is not yours to set a stable for!"));
            return 0;
        }

        if (isHorseCurrentlyInRace(horse, data)) {
            player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        double sx = horse.getX();
        double sy = horse.getY();
        double sz = horse.getZ();
        String dim = horse.level().dimension().location().toString();
        data.setStable(sx, sy, sz, dim);
        data.saveToHorse(horse);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a§l[Stable Set!] §fSet §6%s§f's stable coordinates to §e(%.1f, %.1f, %.1f)§f!",
                data.getName(), sx, sy, sz)));
        return 1;
    }

    // =========================================================================
    // 9. /tb stable [horse name]
    // =========================================================================
    private static int cmdStable(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Horse horse = null;
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());

        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);
            if (rec != null) {
                if (!rec.ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
                    player.sendSystemMessage(Component.literal("§cThis horse is not yours to send back to its stable!"));
                    return 0;
                }
                if (rec.inRace) {
                    player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
                    return 0;
                }
                horse = findOrLoadHorseEntity(player.server, rec);
            }
        } else if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            horse = ridden;
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cCould not find that Thoroughbred! Usage: /tb stable [horse name]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cThis horse is not yours to send back to its stable!"));
            return 0;
        }

        if (isHorseCurrentlyInRace(horse, data)) {
            player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        if (!data.hasStable()) {
            player.sendSystemMessage(Component.literal("§c" + data.getName() + " does not have a stable set yet! Use /tb setstable first."));
            return 0;
        }

        horse.ejectPassengers();
        ServerLevel targetLevel = getLevelByName(player.server, data.getStableDimension());
        BlockPos stableBlockPos = BlockPos.containing(data.getStableX(), data.getStableY(), data.getStableZ());
        targetLevel.getChunkAt(stableBlockPos);

        if (horse.level() != targetLevel) {
            Horse moved = (Horse) horse.changeDimension(targetLevel);
            if (moved != null) {
                moved.teleportTo(data.getStableX(), data.getStableY(), data.getStableZ());
                data.saveToHorse(moved);
            }
        } else {
            horse.teleportTo(data.getStableX(), data.getStableY(), data.getStableZ());
            data.saveToHorse(horse);
        }

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a[Sent to Stable] §6%s §fhas been sent back to its stable at §e(%.1f, %.1f, %.1f)§f!",
                data.getName(), data.getStableX(), data.getStableY(), data.getStableZ())));
        return 1;
    }

    // =========================================================================
    // 10. /tb lock [horse name]
    // =========================================================================
    private static int cmdLock(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Horse horse = null;
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());

        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);
            if (rec != null) {
                if (!rec.ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
                    player.sendSystemMessage(Component.literal("§cThis horse is not yours to lock!"));
                    return 0;
                }
                horse = findOrLoadHorseEntity(player.server, rec);
            }
        } else if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            horse = ridden;
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cCould not find that Thoroughbred! Usage: /tb lock [horse name]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cThis horse is not yours to lock!"));
            return 0;
        }

        boolean nextLocked = !data.isLocked();
        data.setLocked(nextLocked);
        data.saveToHorse(horse);

        if (nextLocked && horse.getControllingPassenger() instanceof ServerPlayer rider && !rider.getUUID().equals(player.getUUID())) {
            horse.ejectPassengers();
            rider.sendSystemMessage(Component.literal("§c" + data.getName() + " was locked by its owner!"));
        }

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                nextLocked ? "§6[Locked] §e%s §fis now locked. Only you can ride it!"
                           : "§a[Unlocked] §e%s §fis now unlocked.",
                data.getName())));
        return 1;
    }

    // =========================================================================
    // 11. /tb here [horse name]
    // =========================================================================
    private static int cmdHere(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);

        if (rec == null) {
            player.sendSystemMessage(Component.literal("§cCould not find a registered Thoroughbred named '" + horseNameArg + "'!"));
            return 0;
        }

        if (!rec.ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§cThat horse is not yours!"));
            return 0;
        }

        if (rec.inRace) {
            player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        Horse horse = findOrLoadHorseEntity(player.server, rec);
        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cCould not load " + rec.horseName + " from its last known location!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cThat horse is not yours!"));
            return 0;
        }
        if (isHorseCurrentlyInRace(horse, data)) {
            player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        horse.ejectPassengers();
        if (horse.level() != player.serverLevel()) {
            Horse moved = (Horse) horse.changeDimension(player.serverLevel());
            if (moved != null) {
                moved.teleportTo(player.getX(), player.getY(), player.getZ());
                data.saveToHorse(moved);
            }
        } else {
            horse.teleportTo(player.getX(), player.getY(), player.getZ());
            data.saveToHorse(horse);
        }

        player.sendSystemMessage(Component.literal("§a[Teleported] §6" + data.getName() + " §fhas been summoned to your position!"));
        return 1;
    }

    // =========================================================================
    // 12. /tb tp [horse name]
    // =========================================================================
    private static int cmdTp(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);

        if (rec == null) {
            player.sendSystemMessage(Component.literal("§cCould not find a registered Thoroughbred named '" + horseNameArg + "'!"));
            return 0;
        }

        if (!rec.ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§cThat horse is not yours!"));
            return 0;
        }

        if (rec.inRace) {
            player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
            return 0;
        }

        Horse horse = findOrLoadHorseEntity(player.server, rec);
        if (horse != null && ThoroughbredData.isThoroughbred(horse)) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            if (!data.isOwnedBy(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cThat horse is not yours!"));
                return 0;
            }
            if (isHorseCurrentlyInRace(horse, data)) {
                player.sendSystemMessage(Component.literal("§cThat horse is in a race!"));
                return 0;
            }
            ServerLevel targetLevel = (ServerLevel) horse.level();
            player.teleportTo(targetLevel, horse.getX(), horse.getY(), horse.getZ(), player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("§a[Teleported] §fTeleported to §6" + data.getName() + "§f!"));
            return 1;
        }

        ServerLevel targetLevel = getLevelByName(player.server, rec.lastDimension);
        player.teleportTo(targetLevel, rec.lastX, rec.lastY, rec.lastZ, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§a[Teleported] §fTeleported to §6" + rec.horseName + "§f's last known location!"));
        return 1;
    }

    // =========================================================================
    // 13. /tb kill & /tb kill confirm (10-Second Permanent Purge Window)
    // =========================================================================
    private static int cmdKillRequest(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (!(player.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cYou must be riding a registered Thoroughbred to use /tb kill!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cThat horse is not yours to kill!"));
            return 0;
        }

        ACTIVE_PURGE_REQUESTS.put(player.getUUID(), new PurgeRequest(
                player.getUUID(), horse.getUUID(), data.getName(), 200 // 10 seconds = 200 ticks
        ));

        player.sendSystemMessage(Component.literal(
                "§4§l[WARNING: PERMANENT PURGE] §cYou are about to permanently kill §6" + data.getName()
                + "§c and erase it from the horse backup database with NO way to revive it! (Its pedigree will be archived.)\n"
                + "§cType §4§l/tb kill confirm §cwithin 10 seconds to commence the purge."));
        return 1;
    }

    private static int cmdKillConfirm(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        PurgeRequest req = ACTIVE_PURGE_REQUESTS.remove(player.getUUID());
        if (req == null || req.ticksRemaining <= 0) {
            player.sendSystemMessage(Component.literal("§cThere are currently no active purge requests!"));
            return 0;
        }

        if (!(player.getVehicle() instanceof Horse horse) || !horse.getUUID().equals(req.horseUUID) || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cYou must still be riding " + req.horseName + " to confirm the purge!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cThat horse is not yours to kill!"));
            return 0;
        }

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        studbook.recordHorseDeath(horse, data, "Permanent Purge (/tb kill by " + player.getName().getString() + ")", true);

        horse.getPersistentData().putBoolean("AIOThoroughbredPurged", true);
        player.stopRiding();
        horse.discard();

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 0.8F);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§4§l[PURGE COMPLETE] §c%s has been permanently erased from the backup database. Its pedigree remains archived in the Studbook.",
                data.getName())));
        return 1;
    }

    // =========================================================================
    // 13B. /tb euthanize [horse name] (Humane Euthanasia for Poor Prognosis ONLY — No Wither Sound!)
    // =========================================================================
    private static int cmdEuthanize(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Horse horse = null;
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());

        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg.trim());
            horse = findOrLoadHorseEntity(player.server, rec);
        } else {
            horse = findNearestOwnedOrTargetHorse(player, 10.0, true);
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§c" + ThoroughbredData.tr(
                    "Could not find that Thoroughbred! Stand near it or specify its name: /tb euthanize [horse name]",
                    "找不到該純種馬！請靠近該馬匹或指定其名稱：/tb euthanize [馬匹名稱]",
                    "找不到该纯种马！请靠近该马匹或指定其名称：/tb euthanize [马匹名称]",
                    "該当するサラブレッドが見つかりません！近くに立つか馬名を指定してください: /tb euthanize [馬名]")));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID()) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("§c" + ThoroughbredData.tr(
                    "That horse does not belong to you!",
                    "這匹馬不屬於你！",
                    "这匹马不属于你！",
                    "その馬の所有者ではありません！")));
            return 0;
        }

        if (!data.hasPoorPrognosis()) {
            player.sendSystemMessage(Component.literal("§c" + String.format(Locale.ROOT, ThoroughbredData.tr(
                    "/tb euthanize can ONLY be used on a horse diagnosed with a Poor Prognosis! (%s does not have a Poor Prognosis.)",
                    "/tb euthanize 只能用於確診為預後不良（Poor Prognosis）的馬匹！（%s 未被診斷為預後不良。）",
                    "/tb euthanize 只能用于确诊为预后不良（Poor Prognosis）的马匹！（%s 未被诊断为预后不良。）",
                    "/tb euthanize は予後不良と診断された馬にのみ使用できます！（%s は予後不良ではありません。）"), data.getName())));
            return 0;
        }

        studbook.recordHorseDeath(horse, data, "Humanely euthanized (/tb euthanize by " + player.getName().getString() + ")", true);

        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD,
                    horse.getX(), horse.getY() + 0.6, horse.getZ(), 20, 0.5, 0.3, 0.5, 0.02);
            sl.sendParticles(ParticleTypes.HEART,
                    horse.getX(), horse.getY() + 1.0, horse.getZ(), 6, 0.4, 0.3, 0.4, 0.01);
        }

        // Gentle, merciful farewell sound — NO Wither sound!
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 0.9F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.75F);

        horse.getPersistentData().putBoolean("AIOThoroughbredPurged", true);
        horse.ejectPassengers();
        horse.discard();

        player.sendSystemMessage(Component.literal("§7[" + ThoroughbredData.tr("Humane Euthanasia", "人道安樂死", "人道安乐死", "安楽死処置") + "] §f"
                + String.format(Locale.ROOT, ThoroughbredData.tr(
                        "[Humane Euthanasia] %s closed its eyes peacefully and was humanely laid to rest, free from pain. Its pedigree remains archived in the Studbook.",
                        "%s 安詳地閉上了雙眼，脫離了病痛長眠。其血統紀錄仍永久保存在血統書中。",
                        "%s 安详地闭上了双眼，脱离了病痛长眠。其血统纪录仍永久保存在血统书中。",
                        "%s は苦痛から解放され、安らかに永遠の眠りにつきました。血統書にはその血筋が永久に記録されます。"), data.getName())));
        return 1;
    }

    // =========================================================================
    // 13C. /tb vet [horse name] (Veterinary Examination & Treatment)
    // =========================================================================
    private static int cmdVet(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Horse horse = null;
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());

        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg.trim());
            horse = findOrLoadHorseEntity(player.server, rec);
        } else if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            horse = ridden;
        } else {
            horse = findNearestOwnedOrTargetHorse(player, 10.0, false);
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§c" + ThoroughbredData.tr(
                    "Ride your Thoroughbred, stand near it, or specify its name: /tb vet [horse name]",
                    "請騎乘純種馬、靠近馬匹，或指定其名稱：/tb vet [馬匹名稱]",
                    "请骑乘纯种马、靠近马匹，或指定其名称：/tb vet [马匹名称]",
                    "サラブレッドに騎乗するか近くに立つか、馬名を指定してください: /tb vet [馬名]")));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID()) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("§c" + ThoroughbredData.tr(
                    "You can only call the vet for a horse that you own!",
                    "你只能為自己擁有的馬匹呼叫獸醫！",
                    "你只能为自己拥有的马匹呼叫兽医！",
                    "自身が所有する馬にのみ獣医を呼ぶことができます！")));
            return 0;
        }

        if (isHorseCurrentlyInRace(horse, data) || RacingManager.isHorseInActiveRace(horse)) {
            player.sendSystemMessage(Component.literal("§c" + ThoroughbredData.tr(
                    "Where are you going to find a vet on the racetrack?",
                    "在賽道競賽中你要去哪裡找獸醫？",
                    "在赛道竞赛中你要去哪里找兽医？",
                    "レース中のコース上で獣医を呼ぶことはできません！")));
            return 0;
        }

        // Case 1: Horse has Poor Prognosis -> Villager disapproval sound + $0 charge + exact requested message!
        if (data.hasPoorPrognosis()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.95F);
            player.sendSystemMessage(Component.literal("§c" + ThoroughbredData.tr(
                    "The vet determines that the horse is beyond saving unless a miracle happens, and euthanizing it is the most humane option - you will not be charged for anything.",
                    "獸醫判定該馬匹除非發生奇蹟否則已無力回天，人道安樂死是最仁慈的選擇——本次看診不收取任何費用。",
                    "兽医判定该马匹除非发生奇迹否则已无力回天，人道安乐死是最仁慈的选择——本次看诊不收取任何费用。",
                    "獣医師の見立てでは、奇跡が起きない限り回復の見込みはありません。安楽死が最も人道的な選択肢です——診察費は請求されません。")));
            return 1;
        }

        // Calculate curable ailments and non-catastrophic injury treatment fee
        boolean hasFever = data.hasAilment(ThoroughbredData.AILMENT_FEVER);
        boolean hasColic = data.hasAilment(ThoroughbredData.AILMENT_COLIC);
        boolean hasEiph  = data.hasAilment(ThoroughbredData.AILMENT_EIPH);
        boolean hasStandaloneLame = !data.hasActiveInjury() && data.hasAilment(ThoroughbredData.AILMENT_LAMENESS);
        boolean hasInjury = data.hasActiveInjury() && !data.isVetTreatedThisInjury();

        long fee = 0L;
        List<String> treatedItems = new ArrayList<>();
        if (hasFever) { fee += 500L; treatedItems.add("Fever"); }
        if (hasColic) { fee += 500L; treatedItems.add("Colic"); }
        if (hasEiph)  { fee += 500L; treatedItems.add("EIPH (Bleeder)"); }
        if (hasStandaloneLame) { fee += 400L; treatedItems.add("Lameness"); }

        if (hasInjury) {
            String inj = data.getActiveInjury();
            if (ThoroughbredData.INJURY_HOOF_CRACK.equalsIgnoreCase(inj)) {
                fee += 1200L;
            } else if (ThoroughbredData.INJURY_LIGAMENT_STRAIN.equalsIgnoreCase(inj)
                    || ThoroughbredData.INJURY_BOWED_TENDON.equalsIgnoreCase(inj)) {
                fee += 1800L;
            } else if (ThoroughbredData.INJURY_NON_CATASTROPHIC_FRACTURE.equalsIgnoreCase(inj)) {
                fee += 2500L;
            } else {
                fee += 1500L;
            }
            treatedItems.add(inj + " Rehab");
        }

        if (fee == 0L) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.05F);
            String vetCheckPrefix = "§a[" + ThoroughbredData.tr("Veterinary Checkup", "獸醫看診", "兽医看诊", "獣医診察") + "] §f";
            if (data.hasActiveInjury() && data.isVetTreatedThisInjury()) {
                player.sendSystemMessage(Component.literal(vetCheckPrefix + String.format(Locale.ROOT, ThoroughbredData.tr(
                        "The vet examined %s: its %s is already under veterinary rehab (%.1f MC days remaining). No charge!",
                        "獸醫為 %s 進行了檢查：其 %s 已在接受獸醫康復治療中（尚餘 %.1f 個遊戲日）。本次不收費！",
                        "兽医为 %s 进行了检查：其 %s 已在接受兽医康复治疗中（尚余 %.1f 个游戏日）。本次不收费！",
                        "獣医師が %s を診察しました: %s はすでに治療・リハビリ中です（残り %.1f MC日）。診察料は無料です！"),
                        data.getName(), data.getActiveInjury(), data.getInjuryDaysRemaining())));
            } else if (data.hasAilment(ThoroughbredData.AILMENT_UNSTABLE_LEGS)) {
                player.sendSystemMessage(Component.literal("§e[" + ThoroughbredData.tr("Veterinary Checkup", "獸醫看診", "兽医看诊", "獣医診察") + "] §f" + String.format(Locale.ROOT, ThoroughbredData.tr(
                        "The vet notes that %s has Unstable Legs from supercritical Fatigue (%.0f%%). Let the horse rest, brush it, or ice its legs to bring Fatigue below 80%%! No charge.",
                        "獸醫提醒：%s 因極度疲勞（%.0f%%）出現雙腿不穩。請讓馬匹充分休息、刷毛或冰敷雙腿，將疲勞降至 80%% 以下！本次不收費。",
                        "兽医提醒：%s 因极度疲劳（%.0f%%）出现双腿不稳。请让马匹充分休息、刷毛或冰敷双腿，将疲劳降至 80%% 以下！本次不收费。",
                        "獣医師のメモ: %s は過度の疲労（%.0f%%）により脚元が不安定になっています。休養させるか、ブラッシングやアイシングで疲労を80%%未満まで回復させてください！無料です。"),
                        data.getName(), data.getFatigue())));
            } else {
                player.sendSystemMessage(Component.literal(vetCheckPrefix + String.format(Locale.ROOT, ThoroughbredData.tr(
                        "The vet examined %s and gave it a clean bill of health! ($0 charged)",
                        "獸醫檢查了 %s，確認其完全健康！（不收取費用）",
                        "兽医检查了 %s，确认其完全健康！（不收取费用）",
                        "獣医師が %s を診察し、健康と診断しました！（診察料 $0）"),
                        data.getName())));
            }
            return 1;
        }

        if (!studbook.tryDeductBalance(player.getUUID(), fee)) {
            long bal = studbook.getBalance(player.getUUID());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.sendSystemMessage(Component.literal("§c[" + ThoroughbredData.tr("Insufficient Funds", "資金不足", "资金不足", "所持金不足") + "] " + String.format(Locale.ROOT, ThoroughbredData.tr(
                    "Veterinary treatment for %s (%s) costs $%,d, but your balance is only $%,d!",
                    "為 %s（%s）進行獸醫治療需花費 $%,d，但你的帳戶餘額僅有 $%,d！",
                    "为 %s（%s）进行兽医治疗需花费 $%,d，但你的账户余额仅有 $%,d！",
                    "%s（%s）の獣医治療費は $%,d ですが、残高が $%,d しかありません！"),
                    data.getName(), String.join(", ", treatedItems), fee, bal)));
            return 0;
        }

        if (hasFever) data.removeAilment(ThoroughbredData.AILMENT_FEVER);
        if (hasColic) data.removeAilment(ThoroughbredData.AILMENT_COLIC);
        if (hasEiph)  data.removeAilment(ThoroughbredData.AILMENT_EIPH);
        if (hasStandaloneLame) data.removeAilment(ThoroughbredData.AILMENT_LAMENESS);

        if (hasInjury) {
            double halved = Math.max(0.5, data.getInjuryDaysRemaining() * 0.5);
            data.setInjuryDaysRemaining(halved);
            data.setVetTreatedThisInjury(true);
        }

        data.setFatigue(Math.max(0.0, data.getFatigue() - 25.0));
        data.setMood(data.getMood() + 8.0);
        data.saveToHorse(horse);
        horse.heal(horse.getMaxHealth());

        // Play Villager approval sound when paying the vet!
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.05F);
        player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.1F);
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    horse.getX(), horse.getY() + 1.2, horse.getZ(), 14, 0.45, 0.4, 0.45, 0.05);
        }

        long remainingBal = studbook.getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("§a§l[" + ThoroughbredData.tr("Veterinary Treatment Complete!", "獸醫治療完成！", "兽医治疗完成！", "獣医治療完了！") + "] §f"
                + String.format(Locale.ROOT, ThoroughbredData.tr(
                        "Paid $%,d to treat %s (%s)! Remaining Balance: $%,d.",
                        "已支付 $%,d 為 %s 治療（%s）！剩餘帳戶餘額：$%,d。",
                        "已支付 $%,d 为 %s 治疗（%s）！剩余账户余额：$%,d。",
                        "%s の治療（%s）に $%,d を支払いました！残り残高: $%,d。"),
                        fee, data.getName(), String.join(", ", treatedItems), remainingBal)));
        return 1;
    }

    private static Horse findNearestOwnedOrTargetHorse(ServerPlayer player, double radius, boolean requirePoorPrognosis) {
        if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            if (!requirePoorPrognosis || ThoroughbredData.fromHorse(ridden).hasPoorPrognosis()) {
                return ridden;
            }
        }
        AABB box = player.getBoundingBox().inflate(radius, radius, radius);
        List<Horse> horses = player.level().getEntitiesOfClass(Horse.class, box,
                h -> h.isAlive() && ThoroughbredData.isThoroughbred(h));
        Horse bestOwned = null;
        double bestOwnedDist = Double.MAX_VALUE;
        Horse bestAny = null;
        double bestAnyDist = Double.MAX_VALUE;

        for (Horse h : horses) {
            ThoroughbredData d = ThoroughbredData.fromHorse(h);
            if (requirePoorPrognosis && !d.hasPoorPrognosis()) continue;
            double dist = h.distanceToSqr(player);
            if (d.isOwnedBy(player.getUUID()) && dist < bestOwnedDist) {
                bestOwnedDist = dist;
                bestOwned = h;
            }
            if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = h;
            }
        }
        return bestOwned != null ? bestOwned : bestAny;
    }

    // =========================================================================
    // 14. ADMIN: /tb forge [money amount] [player name (optional)]
    // =========================================================================
    private static int cmdAdminForge(CommandContext<CommandSourceStack> ctx, boolean hasTargetPlayer) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        long amount = LongArgumentType.getLong(ctx, "amount");
        ServerPlayer target = admin;
        if (hasTargetPlayer) {
            try {
                target = EntityArgument.getPlayer(ctx, "targetPlayer");
            } catch (Exception e) {
                admin.sendSystemMessage(Component.literal("§cCould not find that player!"));
                return 0;
            }
        }

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        long newBalance = studbook.addBalance(target.getUUID(), amount);

        admin.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.5F);

        if (target.getUUID().equals(admin.getUUID())) {
            admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Admin Forge] §fForged §a$%d §finto your balance! Your balance is now §a$%d§f.",
                    amount, newBalance)));
        } else {
            admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Admin Forge] §fForged §a$%d §finto §b%s§f's balance! Their balance is now §a$%d§f.",
                    amount, target.getName().getString(), newBalance)));
            target.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§6[Bank] §fAn admin forged §a$%d §finto your balance! Your balance is now §a$%d§f.",
                    amount, newBalance)));
        }
        return 1;
    }

    // =========================================================================
    // 15. ADMIN: /tb edit [stat] [value] (Temperament uses 1-5!)
    // =========================================================================
    private static int cmdAdminEdit(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        Horse horse = null;
        if (admin.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            horse = ridden;
        } else {
            horse = findNearestOwnedOrTargetHorse(admin, 8.0, false);
        }
        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cRide a registered Thoroughbred or stand within 8 blocks of one to use /tb edit!"));
            return 0;
        }

        String stat = StringArgumentType.getString(ctx, "stat").trim().toLowerCase(Locale.ROOT);
        String rawVal = StringArgumentType.getString(ctx, "value").trim();

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        ThoroughbredRunEngine.HorseRunState runState = ThoroughbredRunEngine.getOrCreateState(horse);
        String displayValue = rawVal;

        try {
            switch (stat) {
                case "speed" -> {
                    int v = parseStatValue(rawVal);
                    data.setSpeedCurrent(v);
                }
                case "speed_max" -> {
                    int v = parseStatValue(rawVal);
                    data.setSpeedMax(v);
                }
                case "stamina" -> {
                    int v = parseStatValue(rawVal);
                    data.setStaminaCurrent(v);
                    runState.maxStamina = data.getMaxStaminaPool();
                    runState.currentStamina = runState.maxStamina;
                }
                case "stamina_max" -> {
                    int v = parseStatValue(rawVal);
                    data.setStaminaMax(v);
                }
                case "power" -> {
                    int v = parseStatValue(rawVal);
                    data.setPowerCurrent(v);
                }
                case "power_max" -> {
                    int v = parseStatValue(rawVal);
                    data.setPowerMax(v);
                }
                case "guts" -> {
                    int v = parseStatValue(rawVal);
                    data.setGutsCurrent(v);
                    runState.maxGuts = data.getMaxGutsPool();
                    runState.currentGuts = runState.maxGuts;
                }
                case "guts_max" -> {
                    int v = parseStatValue(rawVal);
                    data.setGutsMax(v);
                }
                case "wits" -> {
                    int v = parseStatValue(rawVal);
                    data.setWitsCurrent(v);
                }
                case "wits_max" -> {
                    int v = parseStatValue(rawVal);
                    data.setWitsMax(v);
                }
                case "mood" -> data.setMood(Double.parseDouble(rawVal));
                case "bond" -> data.setBond(Double.parseDouble(rawVal));
                case "hunger" -> data.setHunger(Double.parseDouble(rawVal));
                case "thirst" -> data.setThirst(Double.parseDouble(rawVal));
                case "fatigue" -> data.setFatigue(Double.parseDouble(rawVal));
                case "turf" -> data.setTurfAptitude(Integer.parseInt(rawVal));
                case "dirt" -> data.setDirtAptitude(Integer.parseInt(rawVal));
                case "min_distance" -> data.setMinDistance(Integer.parseInt(rawVal));
                case "max_distance" -> data.setMaxDistance(Integer.parseInt(rawVal));
                case "temperament" -> {
                    String matched = matchTemperament(rawVal);
                    if (matched == null) {
                        admin.sendSystemMessage(Component.literal(
                                "§cInvalid temperament! Use 1-5: 1 = Gentle, 2 = Normal, 3 = Rough, 4 = Temperamental, 5 = Volatile"));
                        return 0;
                    }
                    data.setTemperament(matched);
                    runState.trainingRefusalActive = false;
                    runState.rearVisualTicks = 0;
                    horse.setStanding(false);
                    displayValue = matched + " (" + rawVal + ")";
                }
                case "strategy" -> {
                    String matched = matchStrategy(rawVal);
                    if (matched == null) {
                        admin.sendSystemMessage(Component.literal(
                                "§cInvalid strategy! Options: Big Escape, Big Escape / Front, Front Runner, Front / Pace, Pace Chaser, Pace / Late, Late Surger, Late / End, End Closer, Tactical"));
                        return 0;
                    }
                    data.setStrategy(matched);
                    displayValue = matched;
                }
                case "personality" -> {
                    String matchedPers = matchPersonality(rawVal);
                    if (matchedPers == null) {
                        admin.sendSystemMessage(Component.literal(
                                "§cInvalid personality! Options: competitive, lone_wolf, crowd_pleaser, laid_back, glutton, stoic, clingy, timid, defeatist, quirky"));
                        return 0;
                    }
                    data.setPersonality(matchedPers);
                    displayValue = matchedPers;
                }
                case "gender" -> {
                    if ("female".equalsIgnoreCase(rawVal) || "mare".equalsIgnoreCase(rawVal) || "filly".equalsIgnoreCase(rawVal)) {
                        data.setGender("Female");
                        displayValue = "Female";
                    } else if ("gelding".equalsIgnoreCase(rawVal) || "geld".equalsIgnoreCase(rawVal)) {
                        data.geldHorse();
                        displayValue = "Gelding (Temperament: " + data.getTemperament() + ")";
                    } else {
                        data.setGender("Male");
                        displayValue = "Male";
                    }
                }
                case "ailment" -> {
                    String lower = rawVal.toLowerCase(Locale.ROOT);
                    if ("clear".equals(lower) || "none".equals(lower)) {
                        data.clearAllAilments();
                        displayValue = "Cleared All Ailments";
                    } else if ("roll_day".equals(lower)) {
                        data.saveToHorse(horse);
                        ThoroughbredRunEngine.performNewMinecraftDayRoll(horse, data, runState);
                        admin.sendSystemMessage(Component.literal("§a§l[Admin Edit] §fTriggered New Minecraft Day Prognosis/Recovery Roll for §6" + data.getName() + "§f!"));
                        return 1;
                    } else {
                        String matchedAilment = matchAilmentName(lower);
                        if (matchedAilment == null) {
                            admin.sendSystemMessage(Component.literal(
                                    "§cInvalid ailment! Options: fever, laryngeal, afib, lameness, unstable_legs, laminitis, poor_prognosis, eiph, colic, overweight, roll_day, clear"));
                            return 0;
                        }
                        if (ThoroughbredData.AILMENT_UNSTABLE_LEGS.equals(matchedAilment) && data.getFatigue() < 80.0) {
                            data.setFatigue(85.0);
                        }
                        if (ThoroughbredData.AILMENT_OVERWEIGHT.equals(matchedAilment)) {
                            runState.overweightBurnMeters = 0.0;
                        }
                        if (ThoroughbredData.AILMENT_POOR_PROGNOSIS.equals(matchedAilment)) {
                            ThoroughbredRunEngine.inflictHorseInjuryWithFeedback(horse, data, runState, ThoroughbredData.INJURY_CATASTROPHIC_FRACTURE);
                        } else {
                            data.addAilment(matchedAilment);
                        }
                        displayValue = matchedAilment;
                    }
                }
                case "injury" -> {
                    String lower = rawVal.toLowerCase(Locale.ROOT);
                    if ("clear".equals(lower) || "none".equals(lower)) {
                        data.clearInjury();
                        displayValue = "Cleared Injury & Poor Prognosis";
                    } else if ("heal".equals(lower)) {
                        if (data.hasActiveInjury() && !data.hasPoorPrognosis()) {
                            ThoroughbredRunEngine.completeNonCatastrophicInjuryRecovery(horse, data);
                            admin.sendSystemMessage(Component.literal("§a§l[Admin Edit] §fCompleted injury healing roll for §6" + data.getName() + "§f!"));
                            return 1;
                        } else {
                            data.clearInjury();
                            displayValue = "Cleared Injury";
                        }
                    } else if ("roll_day".equals(lower)) {
                        data.saveToHorse(horse);
                        ThoroughbredRunEngine.performNewMinecraftDayRoll(horse, data, runState);
                        admin.sendSystemMessage(Component.literal("§a§l[Admin Edit] §fTriggered New Minecraft Day Prognosis/Recovery Roll for §6" + data.getName() + "§f!"));
                        return 1;
                    } else {
                        String matchedInjury = matchInjuryName(lower);
                        if (matchedInjury == null) {
                            admin.sendSystemMessage(Component.literal(
                                    "§cInvalid injury! Options: hoof_crack, ligament, bowed_tendon, non_catastrophic_fracture, catastrophic_fracture, heal, roll_day, clear"));
                            return 0;
                        }
                        ThoroughbredRunEngine.inflictHorseInjuryWithFeedback(horse, data, runState, matchedInjury);
                        displayValue = matchedInjury;
                    }
                }
                case "career_ended" -> {
                    boolean ce = "true".equalsIgnoreCase(rawVal) || "1".equals(rawVal) || "yes".equalsIgnoreCase(rawVal);
                    data.setCareerEnded(ce);
                    displayValue = ce ? "true (Retired from Injury)" : "false (Active Career)";
                }
                case "roll_day" -> {
                    data.saveToHorse(horse);
                    ThoroughbredRunEngine.performNewMinecraftDayRoll(horse, data, runState);
                    admin.sendSystemMessage(Component.literal("§a§l[Admin Edit] §fTriggered New Minecraft Day Prognosis/Recovery Roll for §6" + data.getName() + "§f!"));
                    return 1;
                }
                case "coat" -> {
                    String lower = rawVal.trim().toLowerCase(Locale.ROOT);
                    String[] tokens = lower.split("\\s+");
                    String coatToken = tokens.length > 0 ? tokens[0] : lower;
                    String markToken = tokens.length > 1 ? tokens[1] : "";

                    if (coatToken.equals("pure_white") || coatToken.equals("purewhite") || lower.equals("pure white")) {
                        data.setPureWhiteCoat(true);
                        ThoroughbredRunEngine.setHorseCoatVariantAndMarkings(
                                horse,
                                net.minecraft.world.entity.animal.horse.Variant.WHITE,
                                net.minecraft.world.entity.animal.horse.Markings.NONE
                        );
                        displayValue = "Pure White";
                    } else {
                        net.minecraft.world.entity.animal.horse.Variant chosenVar = switch (coatToken) {
                            case "bay", "brown" -> net.minecraft.world.entity.animal.horse.Variant.BROWN;
                            case "dark_bay", "darkbay", "dark_brown", "darkbrown" -> net.minecraft.world.entity.animal.horse.Variant.DARK_BROWN;
                            case "chestnut", "sorrel" -> net.minecraft.world.entity.animal.horse.Variant.CHESTNUT;
                            case "black" -> net.minecraft.world.entity.animal.horse.Variant.BLACK;
                            case "gray", "grey", "white", "light_gray" -> net.minecraft.world.entity.animal.horse.Variant.WHITE;
                            case "dapple_gray", "dapple_grey", "dapple" -> net.minecraft.world.entity.animal.horse.Variant.GRAY;
                            case "creamy", "palomino", "buckskin", "cream" -> net.minecraft.world.entity.animal.horse.Variant.CREAMY;
                            default -> null;
                        };
                        if (chosenVar == null) {
                            admin.sendSystemMessage(Component.literal(
                                    "§cInvalid coat! Options: bay, dark_bay, chestnut, black, gray, dapple_gray, creamy, pure_white (optional markings: none, stockings, white_field, white_dots, black_dots)"));
                            return 0;
                        }
                        net.minecraft.world.entity.animal.horse.Markings chosenMark = horse.getMarkings();
                        if (!markToken.isEmpty()) {
                            chosenMark = switch (markToken) {
                                case "none", "solid" -> net.minecraft.world.entity.animal.horse.Markings.NONE;
                                case "white", "stockings", "blaze", "socks" -> net.minecraft.world.entity.animal.horse.Markings.WHITE;
                                case "white_field", "pinto", "patches" -> net.minecraft.world.entity.animal.horse.Markings.WHITE_FIELD;
                                case "white_dots", "snowflake", "appaloosa", "dots" -> net.minecraft.world.entity.animal.horse.Markings.WHITE_DOTS;
                                case "black_dots", "sooty" -> net.minecraft.world.entity.animal.horse.Markings.BLACK_DOTS;
                                default -> chosenMark;
                            };
                        }
                        data.setPureWhiteCoat(false);
                        ThoroughbredRunEngine.setHorseCoatVariantAndMarkings(horse, chosenVar, chosenMark);
                        String coatLabel = switch (chosenVar) {
                            case BROWN -> "Bay";
                            case DARK_BROWN -> "Dark Bay";
                            case CHESTNUT -> "Chestnut";
                            case BLACK -> "Black";
                            case WHITE -> "Gray";
                            case GRAY -> "Dapple Gray";
                            case CREAMY -> "Creamy";
                        };
                        displayValue = markToken.isEmpty() ? coatLabel : (coatLabel + " (" + chosenMark.name().toLowerCase(Locale.ROOT) + ")");
                    }
                }
                case "trait" -> {
                    String lower = rawVal.toLowerCase(Locale.ROOT);
                    if ("clear".equals(lower) || "none".equals(lower)) {
                        data.clearGeneticTraits();
                        displayValue = "Cleared All Genetic Traits";
                    } else if ("x_factor_carrier".equals(lower) || "xfactor_carrier".equals(lower)) {
                        data.removeGeneticTrait(ThoroughbredData.TRAIT_X_FACTOR);
                        data.setXFactorCopies(1);
                        displayValue = "Set Hidden X-Factor Carrier (1 Copy, Unexpressed / Hidden on Stat Card)";
                    } else {
                        String matchedTrait = matchGeneticTraitName(lower);
                        if (matchedTrait == null) {
                            admin.sendSystemMessage(Component.literal(
                                    "§cInvalid trait! Options: small_hooves, large_hooves, x_factor, x_factor_carrier, iron_constitution, explosive_late_gear, born_mudder, hill_climber, lightning_starter, cornering_maestro, deep_lung_capacity, sprinters_fast_twitch, diesel_stayer, glass_legs, roarer_gene, weak_constitution, high_strung_nerves, soft_palate_displacement, brittle_hooves, clear"));
                            return 0;
                        }
                        if (data.hasGeneticTrait(matchedTrait)) {
                            data.removeGeneticTrait(matchedTrait);
                            displayValue = "Removed Trait: " + matchedTrait;
                        } else {
                            data.addGeneticTrait(matchedTrait);
                            displayValue = "Added Trait: " + matchedTrait + " (Power: " + ThoroughbredData.formatStatPair(data.getPowerCurrent(), data.getPowerMax()) + ")";
                        }
                    }
                }
                case "hp" -> {
                    int hpVal = Integer.parseInt(rawVal);
                    data.setHereditaryPoints(hpVal);
                    displayValue = data.getHereditaryPoints() + "% Potential (100/100 HP)";
                }
                case "retired" -> {
                    boolean ret = "true".equalsIgnoreCase(rawVal) || "1".equals(rawVal) || "yes".equalsIgnoreCase(rawVal);
                    data.setRetired(ret);
                    if (ret && "Female".equalsIgnoreCase(data.getGender())) {
                        StudbookSavedData sb = StudbookSavedData.get(admin.serverLevel());
                        data.setInHeat(sb.isCurrentlyBreedingSeasonAprilToOctober());
                    } else if (!ret) {
                        data.setInHeat(false);
                    }
                    displayValue = ret ? "true (Retired)" : "false (Active Racehorse)";
                }
                case "heat" -> {
                    boolean ht = "true".equalsIgnoreCase(rawVal) || "1".equals(rawVal) || "yes".equalsIgnoreCase(rawVal);
                    data.setInHeat(ht);
                    data.setManualHeatOverride(true);
                    displayValue = ht ? "true (In Heat)" : "false (Out of Heat)";
                }
                case "age" -> {
                    int targetAge = Math.max(0, Integer.parseInt(rawVal.trim()));
                    StudbookSavedData sb = StudbookSavedData.get(admin.serverLevel());
                    data.setEquineAgeYears(targetAge, sb.getFoalGrowthDurationMillis());
                    horse.setBaby(targetAge == 0);
                    horse.setAge(targetAge == 0 ? -24000 : 0);
                    displayValue = targetAge + " Equine Years old";
                }
                case "deaths_door" -> {
                    boolean dd = "true".equalsIgnoreCase(rawVal) || "1".equals(rawVal) || "yes".equalsIgnoreCase(rawVal);
                    data.setAtDeathsDoor(dd);
                    if (dd) {
                        data.setLastCheckedDeathsDoorRealDay(System.currentTimeMillis() / 86_400_000L);
                        horse.ejectPassengers();
                        horse.setStanding(false);
                        runState.stopAndResetSpeed();
                        runState.hostileToPlayer = false;
                        runState.hostileTargetPlayer = null;
                        admin.sendSystemMessage(Component.literal(
                                "§6§l[DEATH'S DOOR] §e" + data.getName() + " §fdropped to the floor, it's best if you go and visit it, it might not be here soon."));
                    }
                    displayValue = dd ? "true (At Death's Door)" : "false (Recovered from Death's Door)";
                }
                default -> {
                    admin.sendSystemMessage(Component.literal("§cUnknown stat '" + stat + "'! Available: " + String.join(", ", EDITABLE_STATS)));
                    return 0;
                }
            }
        } catch (NumberFormatException ex) {
            admin.sendSystemMessage(Component.literal("§cInvalid numeric value: '" + rawVal + "'"));
            return 0;
        }

        data.saveToHorse(horse);
        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a§l[Admin Edit] §fUpdated §6%s§f's §e%s §fto §b%s§f!",
                data.getName(), stat, displayValue)));
        return 1;
    }

    private static int parseStatValue(String raw) {
        double d = Double.parseDouble(raw);
        if (raw.contains(".") || d <= 100.0) {
            return (int) Math.round(d * 10.0);
        }
        return (int) Math.round(d);
    }

    private static String matchTemperament(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "1" -> ThoroughbredData.TEMP_GENTLE;
            case "2" -> ThoroughbredData.TEMP_NORMAL;
            case "3" -> ThoroughbredData.TEMP_ROUGH;
            case "4" -> ThoroughbredData.TEMP_TEMPERAMENTAL;
            case "5" -> ThoroughbredData.TEMP_VOLATILE;
            default -> {
                if (s.startsWith("gen")) yield ThoroughbredData.TEMP_GENTLE;
                if (s.startsWith("nor")) yield ThoroughbredData.TEMP_NORMAL;
                if (s.startsWith("rou")) yield ThoroughbredData.TEMP_ROUGH;
                if (s.startsWith("temp")) yield ThoroughbredData.TEMP_TEMPERAMENTAL;
                if (s.startsWith("vol") || s.startsWith("halo")) yield ThoroughbredData.TEMP_VOLATILE;
                yield null;
            }
        };
    }

    private static String matchStrategy(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.equals("0") || s.equals("big escape") || s.equals("oonige") || s.equals("big_escape")) {
            return ThoroughbredData.STRAT_BIG_ESCAPE;
        }
        if (s.contains("big") && s.contains("front") || s.contains("oonige") && s.contains("front") || s.equals("big_escape_front")) {
            return ThoroughbredData.STRAT_BIG_ESCAPE_FRONT;
        }
        if (s.startsWith("big") || s.startsWith("oonige")) {
            return ThoroughbredData.STRAT_BIG_ESCAPE;
        }
        if ((s.contains("front") && s.contains("pace")) || s.equals("front_pace")) {
            return ThoroughbredData.STRAT_FRONT_PACE;
        }
        if ((s.contains("pace") && s.contains("late")) || s.equals("pace_late")) {
            return ThoroughbredData.STRAT_PACE_LATE;
        }
        if ((s.contains("late") && s.contains("end")) || s.equals("late_end")) {
            return ThoroughbredData.STRAT_LATE_END;
        }
        if (s.equals("1") || s.startsWith("front")) return ThoroughbredData.STRAT_FRONT_RUNNER;
        if (s.equals("2") || s.startsWith("pace")) return ThoroughbredData.STRAT_PACE_CHASER;
        if (s.equals("3") || s.startsWith("late")) return ThoroughbredData.STRAT_LATE_SURGER;
        if (s.equals("4") || s.startsWith("end") || s.startsWith("closer")) return ThoroughbredData.STRAT_END_CLOSER;
        if (s.equals("5") || s.startsWith("tac")) return ThoroughbredData.STRAT_TACTICAL;
        return null;
    }

    private static String matchAilmentName(String s) {
        if (s.contains("fev")) return ThoroughbredData.AILMENT_FEVER;
        if (s.contains("lar") || s.contains("hem") || s.contains("roar")) return ThoroughbredData.AILMENT_LARYNGEAL;
        if (s.contains("afib") || s.contains("atrial") || s.contains("fib")) return ThoroughbredData.AILMENT_AFIB;
        if (s.contains("lame") && !s.contains("lamin")) return ThoroughbredData.AILMENT_LAMENESS;
        if (s.contains("unst") || s.contains("leg")) return ThoroughbredData.AILMENT_UNSTABLE_LEGS;
        if (s.contains("lamin")) return ThoroughbredData.AILMENT_LAMINITIS;
        if (s.contains("poor") || s.contains("prog")) return ThoroughbredData.AILMENT_POOR_PROGNOSIS;
        if (s.contains("eiph") || s.contains("bleed")) return ThoroughbredData.AILMENT_EIPH;
        if (s.contains("colic")) return ThoroughbredData.AILMENT_COLIC;
        if (s.contains("overweight") || s.contains("fat") || s.contains("chubby")) return ThoroughbredData.AILMENT_OVERWEIGHT;
        return null;
    }

    private static String matchPersonality(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.contains("comp")) return ThoroughbredData.PERS_COMPETITIVE;
        if (s.contains("lone") || s.contains("wolf")) return ThoroughbredData.PERS_LONE_WOLF;
        if (s.contains("crowd") || s.contains("pleas")) return ThoroughbredData.PERS_CROWD_PLEASER;
        if (s.contains("laid") || s.contains("lazy") || s.contains("chill")) return ThoroughbredData.PERS_LAID_BACK;
        if (s.contains("glut") || s.contains("food")) return ThoroughbredData.PERS_GLUTTON;
        if (s.contains("stoic") || s.contains("calm")) return ThoroughbredData.PERS_STOIC;
        if (s.contains("cling")) return ThoroughbredData.PERS_CLINGY;
        if (s.contains("timid") || s.contains("shy")) return ThoroughbredData.PERS_TIMID;
        if (s.contains("defeat") || s.contains("give")) return ThoroughbredData.PERS_DEFEATIST;
        if (s.contains("quirk") || s.contains("eccen")) return ThoroughbredData.PERS_QUIRKY;
        return null;
    }

    private static int cmdAdminSetGeldingPermission(CommandContext<CommandSourceStack> ctx, boolean adminOnly) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        studbook.setGeldingAdminOnly(adminOnly);
        if (adminOnly) {
            admin.sendSystemMessage(Component.literal(
                    "§6§l[Server Gelding Policy] §eGelding stallions with Shears is now §c§lADMIN-EXCLUSIVE§e!"));
        } else {
            admin.sendSystemMessage(Component.literal(
                    "§6§l[Server Gelding Policy] §aGelding stallions with Shears is now §a§lOPEN TO ALL HORSE OWNERS§a!"));
        }
        return 1;
    }

    private static String matchInjuryName(String s) {
        if (s.contains("hoof") || s.contains("crack")) return ThoroughbredData.INJURY_HOOF_CRACK;
        if (s.contains("lig") || s.contains("susp") || s.contains("strain")) return ThoroughbredData.INJURY_LIGAMENT_STRAIN;
        if (s.contains("bow") || s.contains("tendon")) return ThoroughbredData.INJURY_BOWED_TENDON;
        if (s.contains("non") || s.contains("minor_frac") || s.equals("fracture")) return ThoroughbredData.INJURY_NON_CATASTROPHIC_FRACTURE;
        if (s.contains("cat") || s.contains("fatal") || s.contains("poor")) return ThoroughbredData.INJURY_CATASTROPHIC_FRACTURE;
        return null;
    }

    // =========================================================================
    // 16. ADMIN: /tb deaths (Opens Scrollable Deceased Horses GUI!)
    // =========================================================================
    private static int cmdAdminDeaths(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        studbook.cleanupExpiredRevivedRecords();
        ModNetwork.sendOpenDeathsScreen(admin, studbook.getDeathRecords());
        return 1;
    }

    public static void removePurgedDeathLogByUuidString(ServerPlayer admin, String uuidStr) {
        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        if (studbook.removeDeathRecordByUUID(targetUUID)) {
            admin.sendSystemMessage(Component.literal("§7[Death Log] Removed permanently purged horse entry from the death list."));
        }
        ModNetwork.sendOpenDeathsScreen(admin, studbook.getDeathRecords());
    }

    // =========================================================================
    // 17. ADMIN: /tb revive [horse UUID] + Totem of Undying Particles!
    // =========================================================================
    private static int cmdAdminRevive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String uuidStr = StringArgumentType.getString(ctx, "horseUUID").trim();
        return reviveHorseByUuidString(admin, uuidStr, false);
    }

    public static int reviveHorseByUuidString(ServerPlayer admin, String uuidStr, boolean refreshGui) {
        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            admin.sendSystemMessage(Component.literal("§cInvalid UUID format: " + uuidStr));
            return 0;
        }

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        studbook.cleanupExpiredRevivedRecords();
        StudbookSavedData.DeathRecord d = studbook.findDeathRecordByUUID(targetUUID);

        if (d == null) {
            admin.sendSystemMessage(Component.literal("§cNo deceased Thoroughbred found with UUID: " + uuidStr));
            return 0;
        }

        if (d.permanentlyPurged) {
            admin.sendSystemMessage(Component.literal("§4§l[Cannot Revive] §c" + d.horseName + " (" + d.deathReason + ") is permanently gone and can NEVER be revived!"));
            return 0;
        }

        if (d.revived) {
            admin.sendSystemMessage(Component.literal("§e" + d.horseName + " has already been revived!"));
            return 0;
        }

        ServerLevel level = admin.serverLevel();
        Horse revivedHorse = EntityType.HORSE.create(level);
        if (revivedHorse == null) {
            admin.sendSystemMessage(Component.literal("§cFailed to spawn horse entity!"));
            return 0;
        }

        revivedHorse.moveTo(admin.getX(), admin.getY(), admin.getZ(), admin.getYRot(), 0.0F);
        revivedHorse.setTamed(true);
        if (d.saddled) {
            revivedHorse.equipSaddle(null);
        }

        CompoundTag horseVanillaNbt = new CompoundTag();
        revivedHorse.addAdditionalSaveData(horseVanillaNbt);
        horseVanillaNbt.putInt("Variant", d.horseVariant);
        revivedHorse.readAdditionalSaveData(horseVanillaNbt);

        ThoroughbredData restoredData = new ThoroughbredData();
        restoredData.loadFromNBT(d.thoroughbredNbt);
        revivedHorse.setCustomName(Component.literal(restoredData.getName()));

        level.addFreshEntity(revivedHorse);
        restoredData.saveToHorse(revivedHorse);

        d.revived = true;
        d.revivedAtMillis = System.currentTimeMillis();
        studbook.setDirty();

        level.playSound(null, revivedHorse.getX(), revivedHorse.getY(), revivedHorse.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 1.15F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                revivedHorse.getX(), revivedHorse.getY() + 1.1, revivedHorse.getZ(),
                45, 0.5, 0.6, 0.5, 0.15);

        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a§l[Revived!] §fBrought back §6%s §f(Owner: §b%s§f) with all stats, coat, and claim data intact!",
                restoredData.getName(),
                restoredData.hasOwner() ? restoredData.getOwnerName() : "Unclaimed")));

        if (refreshGui) {
            ModNetwork.sendOpenDeathsScreen(admin, studbook.getDeathRecords());
        }
        return 1;
    }

    // =========================================================================
    // 18. ADMIN: /tb force claim | unclaim | transfer [player name]
    // =========================================================================
    private static int cmdAdminForceClaim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        if (!(admin.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cYou must be riding a registered Thoroughbred to use /tb force claim!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);

        if (data.isOwnedBy(admin.getUUID())) {
            admin.sendSystemMessage(Component.literal(
                    "§eThe horse felt a force in its heart, but nothing changed because you are already its owner!"));
            return 1;
        }

        if (!data.hasOwner()) {
            data.setOwner(admin.getUUID(), admin.getName().getString());
            data.saveToHorse(horse);
            admin.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
            admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Claimed!] §e%s §fis now registered under §b%s§f and is immune to player attacks!",
                    data.getName(), admin.getName().getString())));
            return 1;
        }

        String formerOwnerName = data.getOwnerName();
        data.setOwner(admin.getUUID(), admin.getName().getString());
        data.setLocked(false);
        data.saveToHorse(horse);

        admin.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§6The horse feels a weary feeling that made it believe YOU are its rightful owner instead of %s!",
                formerOwnerName)));
        return 1;
    }

    private static int cmdAdminForceUnclaim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        if (!(admin.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cYou must be riding a registered Thoroughbred to use /tb force unclaim!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);

        if (!data.hasOwner()) {
            admin.sendSystemMessage(Component.literal("§cThis horse does not currently have an owner!"));
            return 0;
        }

        if (data.isOwnedBy(admin.getUUID())) {
            data.clearOwner();
            data.saveToHorse(horse);
            admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§eYou have unclaimed your horse %s, with force. Quite redundant, isn't it?",
                    data.getName())));
            return 1;
        }

        data.clearOwner();
        data.saveToHorse(horse);
        admin.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.0F);
        admin.sendSystemMessage(Component.literal(
                "§6The horse felt anxious - it should have an owner but it forgot who it was."));
        return 1;
    }

    private static int cmdAdminForceTransfer(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        ServerPlayer newOwner;
        try {
            newOwner = EntityArgument.getPlayer(ctx, "targetPlayer");
        } catch (Exception e) {
            admin.sendSystemMessage(Component.literal("§cCould not find that player!"));
            return 0;
        }

        if (!(admin.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cYou must be riding a registered Thoroughbred to use /tb force transfer!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);

        if (data.isOwnedBy(admin.getUUID()) && newOwner.getUUID().equals(admin.getUUID())) {
            admin.sendSystemMessage(Component.literal(
                    "§eThe horse felt a force in its heart, but nothing changed because you are already its owner!"));
            return 1;
        }

        if (newOwner.getUUID().equals(admin.getUUID())) {
            String formerOwnerName = data.hasOwner() ? data.getOwnerName() : "no one";
            data.setOwner(admin.getUUID(), admin.getName().getString());
            data.setLocked(false);
            data.saveToHorse(horse);
            admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§6The horse feels a weary feeling that made it believe YOU are its rightful owner instead of %s!",
                    formerOwnerName)));
            return 1;
        }

        String formerOwnerName = data.hasOwner() ? data.getOwnerName() : "no one";
        data.setOwner(newOwner.getUUID(), newOwner.getName().getString());
        data.setLocked(false);
        data.saveToHorse(horse);

        admin.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§6The horse feels a weary feeling that made it believe %s is its rightful owner instead of %s!",
                newOwner.getName().getString(), formerOwnerName)));

        newOwner.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a§l[Admin Transfer] §e%s §fhas been transferred to your ownership!",
                data.getName())));
        return 1;
    }

    // =========================================================================
    // 18B. ADMIN: /tb dq [horse name] — Disqualify a horse from an active race (DSQ)
    // =========================================================================
    private static int cmdAdminDisqualifyHorse(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String horseName = StringArgumentType.getString(ctx, "horseName").trim();
        if (horseName.isEmpty()) {
            admin.sendSystemMessage(Component.literal("§cSpecify the name of the horse to disqualify: /tb dq [horse name]"));
            return 0;
        }

        boolean ok = RacingManager.disqualifyHorseInActiveRace(admin, horseName);
        return ok ? 1 : 0;
    }

    // =========================================================================
    // STEP 15: /tb schedule GUI & 9-VARIANT BETTING IMPLEMENTATIONS
    // =========================================================================
    private static int cmdScheduleOpenGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ModNetwork.sendOpenScheduleScreen(player);
        return 1;
    }

    public static void handleScheduleGuiAction(ServerPlayer player, ModNetwork.ScheduleGuiActionPacket msg) {
        if (player == null || !(player.level() instanceof ServerLevel sl)) return;
        StudbookSavedData studbook = StudbookSavedData.get(sl);

        switch (msg.actionType) {
            case ModNetwork.ScheduleGuiActionPacket.ACTION_REFRESH -> {
                // Just re-sync and open/refresh GUI below
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_REGISTER_HORSE -> {
                StudbookSavedData.ScheduledRace s = studbook.getScheduledRace(msg.scheduleId);
                if (s == null) {
                    player.sendSystemMessage(Component.literal("§cSchedule not found!"));
                    break;
                }
                Horse horse = null;
                if (!msg.strParam1.isBlank()) {
                    StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(msg.strParam1);
                    horse = findOrLoadHorseEntity(player.server, rec);
                }
                if (horse == null && player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
                    horse = ridden;
                }
                if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
                    player.sendSystemMessage(Component.literal("§cCould not find your Thoroughbred to register! Ride your horse or claim it with /tb claim first."));
                    break;
                }
                ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                if (!data.isOwnedBy(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§cYou can only register Thoroughbreds that you own!"));
                    break;
                }
                String res = studbook.registerHorseForSchedule(msg.scheduleId, horse, data, player);
                if ("QUALIFIED".equals(res)) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.3F);
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§a§l[Registered!] §fRegistered §6%s §finto §e%s §7[%s]§f!",
                            data.getName(), s.raceName, s.scheduleId)));
                } else if ("UNQUALIFIED".equals(res)) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 0.8F, 0.9F);
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§e§l[Registered] §fRegistered §6%s §finto §e%s §7[%s]§f!\n§c§l[UNQUALIFIED NOTICE] §c%s has earned $%,d (below required $%,d) and is listed in RED for admin review!",
                            data.getName(), s.raceName, s.scheduleId, data.getName(), data.getTotalEarnings(), s.requiredEarnings)));
                } else {
                    player.sendSystemMessage(Component.literal("§c[Registration Failed] " + res));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_UNREGISTER_HORSE -> {
                StudbookSavedData.ScheduledRace s = studbook.getScheduledRace(msg.scheduleId);
                if (s == null) break;
                UUID targetUuid = null;
                String hName = msg.strParam1;
                if (hName != null && !hName.isBlank()) {
                    for (StudbookSavedData.ScheduledRace.RegistrationEntry reg : s.registeredHorses) {
                        if (reg.horseName.equalsIgnoreCase(hName.trim()) && player.getUUID().equals(reg.ownerUUID)) {
                            targetUuid = reg.horseUUID;
                            hName = reg.horseName;
                            break;
                        }
                    }
                }
                if (targetUuid == null) {
                    for (StudbookSavedData.ScheduledRace.RegistrationEntry reg : s.registeredHorses) {
                        if (player.getUUID().equals(reg.ownerUUID)) {
                            targetUuid = reg.horseUUID;
                            hName = reg.horseName;
                            break;
                        }
                    }
                }
                if (targetUuid != null && studbook.unregisterHorseFromSchedule(msg.scheduleId, targetUuid)) {
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§e[Unregistered] §fRemoved §6%s §ffrom §e%s §7[%s]§f.", hName, s.raceName, s.scheduleId)));
                } else {
                    player.sendSystemMessage(Component.literal("§cCould not unregister horse (registration may already be closed)."));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_PLACE_BET -> {
                String betType = msg.strParam1.isBlank() ? StudbookSavedData.BET_WIN : msg.strParam1.trim();
                List<String> picks = new ArrayList<>();
                if (!msg.strParam2.isBlank()) {
                    for (String p : msg.strParam2.split("\\|")) {
                        if (!p.isBlank()) picks.add(p.trim());
                    }
                }
                long amount = msg.longParam1;
                String res = studbook.placeVariantBet(msg.scheduleId, player, betType, picks, amount);
                if ("SUCCESS".equals(res)) {
                    StudbookSavedData.ScheduledRace s = studbook.getScheduledRace(msg.scheduleId);
                    int numRunners = s != null ? s.registeredHorses.size() : 2;
                    double mult = StudbookSavedData.calculateFieldSizeMultiplier(betType, numRunners);
                    long newBal = studbook.getBalance(player.getUUID());
                    String sep = StudbookSavedData.isBetTypeOrderSpecific(betType) ? " → " : " + ";
                    String picksFmt = String.join(sep, picks);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 1.4F);
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§a§l[Bet Ticket Placed!] §f%s Ticket: §6%s §7| §fStake: §a$%,d §7(Base Mult: §e%.1fx§7, Est. Win: §a$%,d+§7) in §e%s§f! Balance: §a$%,d§f.",
                            betType, picksFmt, amount, mult, Math.round(amount * mult),
                            (s != null ? s.raceName : msg.scheduleId), newBal)));
                } else {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
                    player.sendSystemMessage(Component.literal("§c[Bet Failed] " + res));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_ADD_SCHEDULE -> {
                if (!player.hasPermissions(2)) break;
                String raceQuery = msg.strParam1.trim();
                String dateStr = msg.strParam2.isBlank() ? "TBD" : msg.strParam2.trim();

                RacingManager.ConsoleRaceConfig targetConsole = null;
                RacingManager.RaceBaseType targetBase = null;
                RacingManager.RaceDefinition targetRace = null;

                for (RacingManager.ConsoleRaceConfig cfg : studbook.getAllRacingConsoles()) {
                    for (RacingManager.RaceBaseType b : cfg.baseTypes) {
                        for (RacingManager.RaceDefinition r : b.races) {
                            if (r.raceId.equalsIgnoreCase(raceQuery) || r.raceName.equalsIgnoreCase(raceQuery)) {
                                targetConsole = cfg;
                                targetBase = b;
                                targetRace = r;
                                break;
                            }
                        }
                        if (targetRace != null) break;
                    }
                    if (targetRace != null) break;
                }

                if (targetRace != null) {
                    String trackSummary = targetBase.getStructuredBottomName(targetConsole.racecourseName);
                    StudbookSavedData.ScheduledRace sched = studbook.createScheduledRace(
                            targetConsole.dimension, targetConsole.consolePos, targetBase.baseId, targetRace.raceId,
                            targetRace.raceName, targetRace.grade, trackSummary,
                            targetRace.firstPlacePurse, targetRace.requiredEarnings, dateStr,
                            targetRace.ageRequirement, targetRace.genderRequirement, targetRace.raceSeriesTag
                    );
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§a§l[Schedule Created!] §fAdded §6%s §e[%s] §ffor §b%s §7(ID: %s)§f! Registration is §aOPEN §7(Betting opens after registrations close).",
                            sched.raceName, sched.grade, sched.scheduledDate, sched.scheduleId)));
                } else {
                    player.sendSystemMessage(Component.literal("§cCould not find that race in any Racing Console!"));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_OPEN_REG -> {
                if (!player.hasPermissions(2)) break;
                if (studbook.openScheduleRegistration(msg.scheduleId)) {
                    player.sendSystemMessage(Component.literal("§a[Schedule] Re-opened horse registration for " + msg.scheduleId + " (Betting paused until registrations close)."));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_CLOSE_REG -> {
                if (!player.hasPermissions(2)) break;
                if (studbook.closeScheduleRegistration(msg.scheduleId)) {
                    player.sendSystemMessage(Component.literal("§e§l[Schedule] §fClosed horse registration & approved field for §6" + msg.scheduleId + "§f! §a§lBetting is now OPEN!"));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_KICK_HORSE -> {
                if (!player.hasPermissions(2)) break;
                if (studbook.adminRemoveHorseFromSchedule(msg.scheduleId, msg.strParam1)) {
                    player.sendSystemMessage(Component.literal("§e[Schedule Admin] Removed horse '" + msg.strParam1 + "' from " + msg.scheduleId + "."));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_CANCEL_SCHEDULE -> {
                if (!player.hasPermissions(2)) break;
                if (studbook.cancelSchedule(msg.scheduleId)) {
                    player.sendSystemMessage(Component.literal("§4[Schedule Cancelled] §cCancelled " + msg.scheduleId + " and refunded 100% of all bets!"));
                }
            }
            case ModNetwork.ScheduleGuiActionPacket.ACTION_ADMIN_REMOVE_SCHEDULE -> {
                if (!player.hasPermissions(2)) break;
                if (studbook.removeSchedule(msg.scheduleId)) {
                    player.sendSystemMessage(Component.literal("§7[Schedule Removed] Removed " + msg.scheduleId + " from the schedule board."));
                }
            }
        }

        ModNetwork.sendOpenScheduleScreen(player);
    }

    private static int cmdScheduleList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        Collection<StudbookSavedData.ScheduledRace> schedules = studbook.getAllScheduledRaces();

        if (schedules.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[Schedule] No races are currently scheduled. Use /tb schedule to open the Schedule GUI!"));
            return 1;
        }

        player.sendSystemMessage(Component.literal("§6§l=== Official Racing Schedule (" + schedules.size() + ") ==="));

        for (StudbookSavedData.ScheduledRace s : schedules) {
            String statusBadge = switch (s.status.toUpperCase(Locale.ROOT)) {
                case "OPEN" -> "§a[REG OPEN / BETS PENDING]";
                case "CLOSED" -> "§e§l[REG CLOSED / BETTING OPEN!]";
                case "ARMED", "RUNNING" -> "§c§l[RACE RUNNING / BETS LOCKED]";
                case "FINISHED" -> "§7[FINISHED]";
                case "CANCELLED" -> "§4[CANCELLED]";
                default -> "§7[" + s.status + "]";
            };

            MutableComponent line1 = Component.literal(String.format(Locale.ROOT,
                    "§e§l[%s] §6%s §7(%s) §7- §b%s %s",
                    s.scheduleId, s.raceName, s.grade, s.scheduledDate, statusBadge));
            player.sendSystemMessage(line1);
        }
        return 1;
    }

    private static int cmdScheduleView(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ModNetwork.sendOpenScheduleScreen(player);
        return 1;
    }

    private static int cmdScheduleRegister(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        StudbookSavedData.ScheduledRace s = studbook.getScheduledRace(scheduleId);

        if (s == null) {
            player.sendSystemMessage(Component.literal("§cCould not find scheduled race '" + scheduleId + "'!"));
            return 0;
        }

        Horse horse = null;
        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);
            horse = findOrLoadHorseEntity(player.server, rec);
        } else if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            horse = ridden;
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cRide your Thoroughbred or specify its name: /tb schedule register " + scheduleId + " [horseName]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou can only register Thoroughbreds that you own!"));
            return 0;
        }

        String result = studbook.registerHorseForSchedule(scheduleId, horse, data, player);
        if ("QUALIFIED".equals(result)) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Registered!] §fSuccessfully registered §6%s §finto §e%s §7[%s]§f!",
                    data.getName(), s.raceName, s.scheduleId)));
        } else if ("UNQUALIFIED".equals(result)) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§e§l[Registered] §fRegistered §6%s §finto §e%s §7[%s]§f!\n§c§l[UNQUALIFIED NOTICE] §c%s has earned $%,d (below the required $%,d) and will be listed in RED for the race host!",
                    data.getName(), s.raceName, s.scheduleId, data.getName(), data.getTotalEarnings(), s.requiredEarnings)));
        } else {
            player.sendSystemMessage(Component.literal("§c" + result));
        }
        return 1;
    }

    private static int cmdScheduleUnregister(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        StudbookSavedData.ScheduledRace s = studbook.getScheduledRace(scheduleId);

        if (s == null) {
            player.sendSystemMessage(Component.literal("§cCould not find scheduled race '" + scheduleId + "'!"));
            return 0;
        }

        UUID targetHorseUUID = null;
        String hName = "";
        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(horseNameArg);
            if (rec != null) {
                targetHorseUUID = rec.horseUUID;
                hName = rec.horseName;
            }
        } else if (player.getVehicle() instanceof Horse ridden && ThoroughbredData.isThoroughbred(ridden)) {
            targetHorseUUID = ridden.getUUID();
            hName = ThoroughbredData.fromHorse(ridden).getName();
        }

        if (targetHorseUUID == null) {
            player.sendSystemMessage(Component.literal("§cSpecify which horse to unregister: /tb schedule unregister " + scheduleId + " [horseName]"));
            return 0;
        }

        if (studbook.unregisterHorseFromSchedule(scheduleId, targetHorseUUID)) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§e[Unregistered] §fRemoved §6%s §ffrom §e%s §7[%s]§f.",
                    hName, s.raceName, s.scheduleId)));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("§cCould not unregister horse (registration might be closed or horse is not registered)."));
            return 0;
        }
    }

    private static int cmdScheduleBet(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        String horseName = StringArgumentType.getString(ctx, "horseName");
        long amount = LongArgumentType.getLong(ctx, "amount");

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        String result = studbook.placeBet(scheduleId, player, horseName, amount);

        if ("SUCCESS".equals(result)) {
            StudbookSavedData.ScheduledRace s = studbook.getScheduledRace(scheduleId);
            long newBal = studbook.getBalance(player.getUUID());
            String odds = (s != null) ? s.getFormattedOdds(horseName) : "-";
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Bet Placed!] §fPlaced §a$%d §fon §6%s §fin §e%s §7(Current Odds: §e%s§7)!\n§7Your remaining balance is §a$%d§7.",
                    amount, horseName, (s != null ? s.raceName : scheduleId), odds, newBal)));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("§c[Bet Failed] " + result));
            return 0;
        }
    }

    private static int cmdScheduleMyBets(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        UUID myUuid = player.getUUID();
        int count = 0;

        player.sendSystemMessage(Component.literal("§6§l=== My Active Race Bets ==="));
        for (StudbookSavedData.ScheduledRace s : studbook.getAllScheduledRaces()) {
            for (StudbookSavedData.ScheduledRace.BetEntry b : s.bets) {
                if (myUuid.equals(b.bettorUUID)) {
                    count++;
                    double mult = StudbookSavedData.calculateFieldSizeMultiplier(b.betType, Math.max(2, s.registeredHorses.size()));
                    player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "  §e[%s] %s §7- §b%s §fTicket: §6%s §7| Stake: §a$%,d §7(Mult: §e%.1fx§7, Status: %s)",
                            s.scheduleId, s.raceName, b.betType, b.getFormattedPicks(), b.amount, mult, s.status)));
                }
            }
        }
        if (count == 0) {
            player.sendSystemMessage(Component.literal("§7You have no active bets. Open the Racing Schedule GUI with /tb schedule!"));
        }
        return 1;
    }

    private static int cmdScheduleAdminAdd(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String raceNameQuery = StringArgumentType.getString(ctx, "raceName").trim();
        if (raceNameQuery.startsWith("\"") && raceNameQuery.endsWith("\"") && raceNameQuery.length() >= 2) {
            raceNameQuery = raceNameQuery.substring(1, raceNameQuery.length() - 1).trim();
        }
        String dateString = StringArgumentType.getString(ctx, "dateString").trim();

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());

        RacingManager.ConsoleRaceConfig targetConsole = null;
        RacingManager.RaceBaseType targetBase = null;
        RacingManager.RaceDefinition targetRace = null;

        for (RacingManager.ConsoleRaceConfig cfg : studbook.getAllRacingConsoles()) {
            for (RacingManager.RaceBaseType b : cfg.baseTypes) {
                for (RacingManager.RaceDefinition r : b.races) {
                    if (r.raceName.equalsIgnoreCase(raceNameQuery) || r.raceId.equalsIgnoreCase(raceNameQuery)) {
                        targetConsole = cfg;
                        targetBase = b;
                        targetRace = r;
                        break;
                    }
                }
                if (targetRace != null) break;
            }
            if (targetRace != null) break;
        }

        if (targetRace == null) {
            admin.sendSystemMessage(Component.literal("§cCould not find an existing race named '" + raceNameQuery + "' in any racing console!"));
            return 0;
        }

        String trackSummary = targetBase.getStructuredBottomName(targetConsole.racecourseName);
        StudbookSavedData.ScheduledRace sched = studbook.createScheduledRace(
                targetConsole.dimension, targetConsole.consolePos, targetBase.baseId, targetRace.raceId,
                targetRace.raceName, targetRace.grade, trackSummary,
                targetRace.firstPlacePurse, targetRace.requiredEarnings, dateString,
                targetRace.ageRequirement, targetRace.genderRequirement, targetRace.raceSeriesTag
        );

        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a§l[Schedule Created!] §fScheduled §6%s §e[%s] §ffor §b%s§f! Schedule ID: §e%s§f. Registration is §aOPEN §7(Betting opens after registrations close).",
                sched.raceName, sched.grade, sched.scheduledDate, sched.scheduleId)));
        return 1;
    }

    private static int cmdScheduleAdminOpen(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());

        if (studbook.openScheduleRegistration(scheduleId)) {
            admin.sendSystemMessage(Component.literal("§a[Schedule] Opened horse registration for " + scheduleId + " (Betting is paused while registration is open)."));
            return 1;
        } else {
            admin.sendSystemMessage(Component.literal("§cCould not open registration for " + scheduleId + "."));
            return 0;
        }
    }

    private static int cmdScheduleAdminClose(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());

        if (studbook.closeScheduleRegistration(scheduleId)) {
            admin.sendSystemMessage(Component.literal("§e[Schedule] Closed horse registration for " + scheduleId + "! §a§lBetting is now OPEN!"));
            return 1;
        } else {
            admin.sendSystemMessage(Component.literal("§cCould not close registration for " + scheduleId + "."));
            return 0;
        }
    }

    private static int cmdScheduleAdminCancel(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());

        if (studbook.cancelSchedule(scheduleId)) {
            admin.sendSystemMessage(Component.literal("§4[Schedule Cancelled] §cCancelled " + scheduleId + " and refunded 100% of all bets!"));
            return 1;
        } else {
            admin.sendSystemMessage(Component.literal("§cCould not cancel schedule " + scheduleId + "."));
            return 0;
        }
    }

    private static int cmdScheduleAdminRemove(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String scheduleId = StringArgumentType.getString(ctx, "scheduleId");
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());

        if (studbook.removeSchedule(scheduleId)) {
            admin.sendSystemMessage(Component.literal("§7[Schedule Removed] Removed " + scheduleId + " from active schedules."));
            return 1;
        } else {
            admin.sendSystemMessage(Component.literal("§cCould not find schedule " + scheduleId + "."));
            return 0;
        }
    }

    private static String matchGeneticTraitName(String s) {
        if (s == null) return null;
        if (s.contains("small") && s.contains("hoof") || s.contains("small_hooves")) return ThoroughbredData.TRAIT_SMALL_HOOVES;
        if (s.contains("large") && s.contains("hoof") || s.contains("large_hooves")) return ThoroughbredData.TRAIT_LARGE_HOOVES;
        if (s.contains("brittle")) return ThoroughbredData.TRAIT_BRITTLE_HOOVES;
        if (s.contains("iron")) return ThoroughbredData.TRAIT_IRON_CONSTITUTION;
        if (s.contains("x_factor") || s.contains("xfactor") || s.contains("x-factor") || s.contains("heart")) return ThoroughbredData.TRAIT_X_FACTOR;
        if (s.contains("explosive") || s.contains("gear")) return ThoroughbredData.TRAIT_EXPLOSIVE_GEAR;
        if (s.contains("mud")) return ThoroughbredData.TRAIT_BORN_MUDDER;
        if (s.contains("hill") || s.contains("climb")) return ThoroughbredData.TRAIT_HILL_CLIMBER;
        if (s.contains("lightning") || s.contains("starter")) return ThoroughbredData.TRAIT_LIGHTNING_STARTER;
        if (s.contains("corner") || s.contains("maestro")) return ThoroughbredData.TRAIT_CORNERING_MAESTRO;
        if (s.contains("deep") || s.contains("lung")) return ThoroughbredData.TRAIT_DEEP_LUNG;
        if (s.contains("sprint") || s.contains("twitch")) return ThoroughbredData.TRAIT_SPRINTER_TWITCH;
        if (s.contains("diesel") || s.contains("stayer")) return ThoroughbredData.TRAIT_DIESEL_STAYER;
        if (s.contains("glass")) return ThoroughbredData.TRAIT_GLASS_LEGS;
        if (s.contains("roar")) return ThoroughbredData.TRAIT_ROARER_GENE;
        if (s.contains("weak")) return ThoroughbredData.TRAIT_WEAK_CONSTITUTION;
        if (s.contains("strung") || s.contains("nerve")) return ThoroughbredData.TRAIT_HIGH_STRUNG;
        if (s.contains("palate") || s.contains("soft")) return ThoroughbredData.TRAIT_SOFT_PALATE;
        return null;
    }

    private static String stripOuterQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    // =========================================================================
    // STEP 18: RETIREMENT, PEDIGREE GUI, BREEDING, STUD FEES & TESTING COMMANDS
    // =========================================================================
    private static int cmdRetireRequest(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Horse horse = null;
        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        if (horseNameArg != null && !horseNameArg.isBlank()) {
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(stripOuterQuotes(horseNameArg));
            horse = findOrLoadHorseEntity(player.server, rec);
        } else {
            horse = findNearestOwnedOrTargetHorse(player, 10.0, false);
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cRide your Thoroughbred, stand near it, or specify its name: /tb retire [horse name]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isOwnedBy(player.getUUID()) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("§cYou can only retire a Thoroughbred that you own!"));
            return 0;
        }
        if (data.isUnderTwoEquineYears()) {
            String stage = data.isFoal() ? "Foal (0yo)" : "Yearling (1yo)";
            player.sendSystemMessage(Component.literal("§c" + data.getName() + " is still a " + stage + " and hasn't reached racing age (2 Equine Years) yet!"));
            return 0;
        }
        if (data.isRetired()) {
            player.sendSystemMessage(Component.literal("§e" + data.getName() + " is already retired for breeding!"));
            return 0;
        }
        if (isHorseCurrentlyInRace(horse, data) || RacingManager.isHorseInActiveRace(horse)) {
            player.sendSystemMessage(Component.literal("§cYou cannot retire a horse while it is in the middle of a race!"));
            return 0;
        }

        ACTIVE_RETIRE_REQUESTS.put(player.getUUID(), new RetirementRequest(player.getUUID(), horse.getUUID(), data.getName()));

        String role = data.isGelding() ? "Retired Gelding" : ("Female".equalsIgnoreCase(data.getGender()) ? "Broodmare" : "Stud Stallion");
        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "§6§l[Retirement Confirmation] §eAre you sure you want to permanently retire §f%s §efrom racing to become a §b%s§e? (Retired horses can breed, but can never enter official races again!) ",
                data.getName(), role));
        MutableComponent confirmBtn = Component.literal("§a§l[CONFIRM RETIREMENT (/tb retire confirm)]")
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb retire confirm"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to permanently retire " + data.getName()))));
        player.sendSystemMessage(msg.append(confirmBtn));
        return 1;
    }

    private static int cmdRetireConfirm(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        RetirementRequest req = ACTIVE_RETIRE_REQUESTS.remove(player.getUUID());
        if (req == null) {
            player.sendSystemMessage(Component.literal("§cYou have no pending retirement confirmation! Run /tb retire [horse name] first."));
            return 0;
        }

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(req.horseName);
        Horse horse = findOrLoadHorseEntity(player.server, rec);
        if (horse == null && player.getVehicle() instanceof Horse ridden && ridden.getUUID().equals(req.horseUUID)) {
            horse = ridden;
        }
        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            player.sendSystemMessage(Component.literal("§cCould not locate " + req.horseName + " to complete retirement!"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        data.setRetired(true);
        boolean isMare = "Female".equalsIgnoreCase(data.getGender());
        if (isMare) {
            data.setInHeat(studbook.isCurrentlyBreedingSeasonAprilToOctober());
        }
        data.saveToHorse(horse);

        player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.25F);
        String role = data.isGelding() ? "Retired Gelding" : (isMare ? ("Broodmare" + (data.isInHeat() ? " §a[Currently IN HEAT]" : " §7(Out of Heat until Equine April)")) : "Stud Stallion");
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§6§l[Official Retirement!] §e%s §fhas retired from the turf with Career Earnings of §a$%,d §fand is now a §b%s§f!",
                data.getName(), data.getTotalEarnings(), role)));
        return 1;
    }

    private static int cmdPedigree(CommandContext<CommandSourceStack> ctx, String horseNameArg) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        ThoroughbredData targetData = null;

        if (horseNameArg != null && !horseNameArg.isBlank()) {
            String cleanName = stripOuterQuotes(horseNameArg);
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(cleanName);
            if (rec != null && rec.thoroughbredNbt != null && !rec.thoroughbredNbt.isEmpty()) {
                targetData = new ThoroughbredData();
                targetData.loadFromNBT(rec.thoroughbredNbt);
            } else {
                StudbookSavedData.PedigreeArchiveRecord arch = studbook.getPedigreeRecord(cleanName);
                if (arch != null) {
                    targetData = new ThoroughbredData();
                    targetData.setName(arch.horseName);
                    targetData.setGender(arch.gender);
                    targetData.setHereditaryPoints(arch.hereditaryPoints);
                    targetData.setPureWhiteCoat(arch.pureWhiteCoat);
                    for (String tr : arch.geneticTraits) targetData.addGeneticTrait(tr);
                    System.arraycopy(arch.pedigreeTree, 0, targetData.getPedigreeTree(), 0, ThoroughbredData.PEDIGREE_SIZE);
                    System.arraycopy(arch.pedigreeHP, 0, targetData.getPedigreeHP(), 0, ThoroughbredData.PEDIGREE_SIZE);
                }
            }
        } else {
            Horse nearby = findNearestOwnedOrTargetHorse(player, 10.0, false);
            if (nearby != null && ThoroughbredData.isThoroughbred(nearby)) {
                targetData = ThoroughbredData.fromHorse(nearby);
            }
        }

        if (targetData == null) {
            player.sendSystemMessage(Component.literal("§cCould not find that Thoroughbred! Ride or stand near a horse, or specify its name: /tb pedigree [horse name]"));
            return 0;
        }

        ModNetwork.sendOpenPedigreeScreen(player, targetData.toNBT());
        return 1;
    }

    private static int cmdBreedInitiate(CommandContext<CommandSourceStack> ctx, String rawSireName, String rawDamName, boolean isAdminForce) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String sireQuery = stripOuterQuotes(rawSireName);
        String damQuery = stripOuterQuotes(rawDamName);
        // Support "Sire Name | Dam Name" if someone separated with a pipe
        if (damQuery.contains("|") && !rawSireName.contains("\"")) {
            String combined = (rawSireName + " " + rawDamName).trim();
            String[] parts = combined.split("\\|", 2);
            sireQuery = stripOuterQuotes(parts[0]);
            damQuery = stripOuterQuotes(parts[1]);
        }

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        StudbookSavedData.HorseBackupRecord sireRec = studbook.getLivingHorseByName(sireQuery);
        StudbookSavedData.HorseBackupRecord damRec  = studbook.getLivingHorseByName(damQuery);

        Horse sireHorse = findOrLoadHorseEntity(player.server, sireRec);
        Horse damHorse  = findOrLoadHorseEntity(player.server, damRec);

        if (sireHorse == null || !ThoroughbredData.isThoroughbred(sireHorse)) {
            player.sendSystemMessage(Component.literal("§cCould not locate a living registered Stallion named '" + sireQuery + "'! (Tip: Put quotes around multi-word names, e.g. /tb breed \"Sire Name\" \"Mare Name\")"));
            return 0;
        }
        if (damHorse == null || !ThoroughbredData.isThoroughbred(damHorse)) {
            player.sendSystemMessage(Component.literal("§cCould not locate a living registered Mare named '" + damQuery + "'!"));
            return 0;
        }

        ThoroughbredData sireData = ThoroughbredData.fromHorse(sireHorse);
        ThoroughbredData damData  = ThoroughbredData.fromHorse(damHorse);

        if (sireData.isGelding()) {
            player.sendSystemMessage(Component.literal("§c" + sireData.getName() + " has been gelded and can no longer reproduce!"));
            return 0;
        }
        if (!"Male".equalsIgnoreCase(sireData.getGender())) {
            player.sendSystemMessage(Component.literal("§c" + sireData.getName() + " is not a Male Stallion!"));
            return 0;
        }
        if (!"Female".equalsIgnoreCase(damData.getGender())) {
            player.sendSystemMessage(Component.literal("§c" + damData.getName() + " is not a Female Mare!"));
            return 0;
        }
        if (sireData.isUnderTwoEquineYears() || damData.isUnderTwoEquineYears()) {
            player.sendSystemMessage(Component.literal("§cFoals and Yearlings cannot breed!"));
            return 0;
        }

        // Strict Retirement Requirement: Active racehorses CANNOT breed!
        if (!sireData.isRetired()) {
            player.sendSystemMessage(Component.literal("§c" + sireData.getName() + " is still an active racehorse! Stallions must be retired (/tb retire) before becoming a breeding Stud."));
            return 0;
        }
        if (!damData.isRetired()) {
            player.sendSystemMessage(Component.literal("§c" + damData.getName() + " is still an active racehorse! Mares must be retired (/tb retire) before becoming a Broodmare."));
            return 0;
        }

        if (damData.isPregnant()) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§c%s is already pregnant by %s (%.0f%% complete, %.1f days remaining)!",
                    damData.getName(), damData.getPregnantSireName(),
                    damData.getPregnancyProgressRatio() * 100.0, damData.getPregnancyDaysRemaining())));
            return 0;
        }

        if (!damData.isInHeat()) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§c%s is not currently In Heat! (Mares naturally enter heat from Equine April to October, or an admin can toggle heat with /tb breed heat %s)",
                    damData.getName(), damData.getName())));
            return 0;
        }

        // Close Proximity Requirement (Vanilla Love Mode Range = 8.0 blocks)
        String proxErr = validateBreedingProximityAndState(sireHorse, sireData, damHorse, damData);
        if (proxErr != null) {
            player.sendSystemMessage(Component.literal(proxErr));
            return 0;
        }

        // Admin Force Breed
        if (isAdminForce && player.hasPermissions(2)) {
            commenceBreeding((ServerLevel) damHorse.level(), sireHorse, sireData, damHorse, damData, 0L);
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Admin Force Breed] §b%s §fand §d%s §fentered Love Mode to commence breeding!",
                    sireData.getName(), damData.getName())));
            return 1;
        }

        boolean ownsSire = sireData.isOwnedBy(player.getUUID());
        boolean ownsDam  = damData.isOwnedBy(player.getUUID());

        // Case 1: Same Owner owns both Stallion & Mare -> Immediate Love Mode Breeding Ritual!
        if (ownsSire && ownsDam) {
            commenceBreeding((ServerLevel) damHorse.level(), sireHorse, sireData, damHorse, damData, 0L);
            return 1;
        }

        // Case 2: Mare Owner requests to breed with another player's Stallion (Stud Fee workflow)
        if (ownsDam && !ownsSire) {
            if (!sireData.hasOwner()) {
                player.sendSystemMessage(Component.literal("§c" + sireData.getName() + " has no owner! Claim it first or use an owned Stud Stallion."));
                return 0;
            }
            UUID sireOwnerUuid = UUID.fromString(sireData.getOwnerUUID());
            ServerPlayer sireOwner = player.server.getPlayerList().getPlayer(sireOwnerUuid);
            if (sireOwner == null) {
                player.sendSystemMessage(Component.literal("§c" + sireData.getName() + "'s owner (" + sireData.getOwnerName() + ") is not currently online!"));
                return 0;
            }

            BreedingRequest req = new BreedingRequest(
                    sireHorse.getUUID(), sireData.getName(), sireOwnerUuid, sireOwner.getName().getString(),
                    damHorse.getUUID(), damData.getName(), player.getUUID(), player.getName().getString(),
                    true
            );
            req.damOwnerApproved = true;
            BREED_REQ_BY_SIRE_OWNER.put(sireOwnerUuid, req);
            BREED_REQ_BY_DAM_OWNER.put(player.getUUID(), req);

            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a[Stud Request Sent] §fSent a breeding request to §b%s §fto cover your mare §d%s §fwith §b%s§f. Waiting for them to quote a Stud Fee...",
                    sireOwner.getName().getString(), damData.getName(), sireData.getName())));

            MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                    "§6§l[Stud Request] §b%s §fwants to breed their mare §d%s §fto your stud stallion §b%s§f!\n"
                    + "§eType §a/tb breed fee [amount] §eto request a Stud Fee, or click: ",
                    player.getName().getString(), damData.getName(), sireData.getName()));
            MutableComponent freeBtn = Component.literal("§a§l[FREE ($0 FEE)] ")
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb breed fee 0"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb breed fee 0"))));
            MutableComponent decBtn = Component.literal("§c§l[DECLINE]")
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb breed decline"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb breed decline"))));
            sireOwner.sendSystemMessage(msg.append(freeBtn).append(decBtn));
            return 1;
        }

        // Case 3: Stallion Owner offers to cover another player's Mare (requires Mare Owner approval)
        if (ownsSire && !ownsDam) {
            if (!damData.hasOwner()) {
                player.sendSystemMessage(Component.literal("§c" + damData.getName() + " has no owner!"));
                return 0;
            }
            UUID damOwnerUuid = UUID.fromString(damData.getOwnerUUID());
            ServerPlayer damOwner = player.server.getPlayerList().getPlayer(damOwnerUuid);
            if (damOwner == null) {
                player.sendSystemMessage(Component.literal("§c" + damData.getName() + "'s owner (" + damData.getOwnerName() + ") is not currently online!"));
                return 0;
            }

            BreedingRequest req = new BreedingRequest(
                    sireHorse.getUUID(), sireData.getName(), player.getUUID(), player.getName().getString(),
                    damHorse.getUUID(), damData.getName(), damOwnerUuid, damOwner.getName().getString(),
                    false
            );
            req.studFee = 0L;
            req.sireOwnerApproved = true;
            BREED_REQ_BY_SIRE_OWNER.put(player.getUUID(), req);
            BREED_REQ_BY_DAM_OWNER.put(damOwnerUuid, req);

            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a[Breeding Proposal Sent] §fAsked §b%s §ffor approval to cover their mare §d%s §fwith your stallion §b%s§f.",
                    damOwner.getName().getString(), damData.getName(), sireData.getName())));

            MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                    "§6§l[Breeding Approval] §b%s §fwants to cover your In-Heat mare §d%s §fwith their stud stallion §b%s §ffor FREE ($0)! ",
                    player.getName().getString(), damData.getName(), sireData.getName()));
            MutableComponent accBtn = Component.literal("§a§l[APPROVE (/tb breed accept)] ")
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb breed accept"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb breed accept"))));
            MutableComponent decBtn = Component.literal("§c§l[DECLINE]")
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb breed decline"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb breed decline"))));
            damOwner.sendSystemMessage(msg.append(accBtn).append(decBtn));
            return 1;
        }

        player.sendSystemMessage(Component.literal("§cYou must own either the Stallion or the Mare (or be an admin using /tb force breed) to initiate breeding!"));
        return 0;
    }

    private static int cmdBreedSetFee(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer sireOwner = ctx.getSource().getPlayer();
        if (sireOwner == null) return 0;

        BreedingRequest req = BREED_REQ_BY_SIRE_OWNER.get(sireOwner.getUUID());
        if (req == null) {
            sireOwner.sendSystemMessage(Component.literal("§cYou have no pending Stud Request to set a fee for!"));
            return 0;
        }

        long fee = LongArgumentType.getLong(ctx, "amount");
        req.studFee = Math.max(0L, fee);
        req.sireOwnerApproved = true;
        req.ticksRemaining = 2400;

        ServerPlayer damOwner = sireOwner.server.getPlayerList().getPlayer(req.damOwnerUUID);
        if (damOwner == null) {
            BREED_REQ_BY_SIRE_OWNER.remove(sireOwner.getUUID());
            BREED_REQ_BY_DAM_OWNER.remove(req.damOwnerUUID);
            sireOwner.sendSystemMessage(Component.literal("§cThe Mare's owner is no longer online!"));
            return 0;
        }

        sireOwner.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a[Stud Fee Quoted] §fQuoted a Stud Fee of §a$%,d §fto §b%s §ffor §b%s §f× §d%s§f. Waiting for their acceptance...",
                req.studFee, req.damOwnerName, req.sireName, req.damName)));

        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "§6§l[Stud Fee Quoted] §b%s §fhas set the Stud Fee for §b%s §fto cover your mare §d%s §fat §a$%,d§f! ",
                req.sireOwnerName, req.sireName, req.damName, req.studFee));
        MutableComponent accBtn = Component.literal("§a§l[ACCEPT & PAY (/tb breed accept)] ")
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb breed accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to pay $" + req.studFee + " and commence breeding"))));
        MutableComponent decBtn = Component.literal("§c§l[DECLINE]")
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tb breed decline"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tb breed decline"))));
        damOwner.sendSystemMessage(msg.append(accBtn).append(decBtn));
        return 1;
    }

    private static int cmdBreedAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        BreedingRequest req = BREED_REQ_BY_DAM_OWNER.get(player.getUUID());
        if (req == null || !req.sireOwnerApproved || req.studFee < 0L) {
            player.sendSystemMessage(Component.literal("§cYou have no ready breeding / stud fee offer to accept!"));
            return 0;
        }

        StudbookSavedData studbook = StudbookSavedData.get(player.serverLevel());
        if (req.studFee > 0L && studbook.getBalance(player.getUUID()) < req.studFee) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§cYou do not have enough money ($%,d required) to pay the Stud Fee!", req.studFee)));
            return 0;
        }

        StudbookSavedData.HorseBackupRecord sireRec = studbook.getLivingHorseByName(req.sireName);
        StudbookSavedData.HorseBackupRecord damRec  = studbook.getLivingHorseByName(req.damName);
        Horse sireHorse = findOrLoadHorseEntity(player.server, sireRec);
        Horse damHorse  = findOrLoadHorseEntity(player.server, damRec);

        if (sireHorse == null || damHorse == null) {
            BREED_REQ_BY_DAM_OWNER.remove(req.damOwnerUUID);
            BREED_REQ_BY_SIRE_OWNER.remove(req.sireOwnerUUID);
            player.sendSystemMessage(Component.literal("§cCould not locate both horses to complete the breeding!"));
            return 0;
        }

        ThoroughbredData sireData = ThoroughbredData.fromHorse(sireHorse);
        ThoroughbredData damData  = ThoroughbredData.fromHorse(damHorse);

        if (damData.isPregnant() || !damData.isInHeat()) {
            BREED_REQ_BY_DAM_OWNER.remove(req.damOwnerUUID);
            BREED_REQ_BY_SIRE_OWNER.remove(req.sireOwnerUUID);
            player.sendSystemMessage(Component.literal("§c" + damData.getName() + " is no longer eligible to be bred (already pregnant or out of heat)!"));
            return 0;
        }

        String proxErr = validateBreedingProximityAndState(sireHorse, sireData, damHorse, damData);
        if (proxErr != null) {
            player.sendSystemMessage(Component.literal(proxErr + " §e(Bring both horses within 8 blocks of each other and run /tb breed accept again!)"));
            return 0;
        }

        BREED_REQ_BY_DAM_OWNER.remove(req.damOwnerUUID);
        BREED_REQ_BY_SIRE_OWNER.remove(req.sireOwnerUUID);

        if (req.studFee > 0L) {
            studbook.tryDeductBalance(req.damOwnerUUID, req.studFee);
            studbook.addBalance(req.sireOwnerUUID, req.studFee);
        }

        commenceBreeding((ServerLevel) damHorse.level(), sireHorse, sireData, damHorse, damData, req.studFee);

        ServerPlayer sireOwner = player.server.getPlayerList().getPlayer(req.sireOwnerUUID);
        if (sireOwner != null && !sireOwner.getUUID().equals(player.getUUID())) {
            sireOwner.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Breeding Ritual Commenced!] §b%s §faccepted and paid the §a$%,d §fStud Fee! §b%s §fand §d%s §fhave entered Love Mode! (Your Balance: §a$%,d§f)",
                    req.damOwnerName, req.studFee, sireData.getName(), damData.getName(), studbook.getBalance(req.sireOwnerUUID))));
        }
        return 1;
    }

    private static int cmdBreedDecline(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        BreedingRequest req = BREED_REQ_BY_DAM_OWNER.remove(player.getUUID());
        if (req == null) {
            req = BREED_REQ_BY_SIRE_OWNER.remove(player.getUUID());
        }
        if (req == null) {
            player.sendSystemMessage(Component.literal("§cYou have no active breeding request to decline!"));
            return 0;
        }
        BREED_REQ_BY_DAM_OWNER.remove(req.damOwnerUUID);
        BREED_REQ_BY_SIRE_OWNER.remove(req.sireOwnerUUID);

        player.sendSystemMessage(Component.literal("§eDeclined the breeding request for §b" + req.sireName + " §e× §d" + req.damName + "§e."));
        ServerPlayer other = player.server.getPlayerList().getPlayer(
                player.getUUID().equals(req.sireOwnerUUID) ? req.damOwnerUUID : req.sireOwnerUUID);
        if (other != null) {
            other.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " declined the breeding request for " + req.sireName + " × " + req.damName + "."));
        }
        return 1;
    }

    private static String validateBreedingProximityAndState(Horse sireHorse, ThoroughbredData sireData,
                                                             Horse damHorse, ThoroughbredData damData) {
        if (sireHorse.level() != damHorse.level()) {
            return "§c[Out of Breeding Range!] §b" + sireData.getName() + " §cand §d" + damData.getName()
                    + " §care in different dimensions! They must be within 8 blocks of each other to breed.";
        }
        double distSq = sireHorse.distanceToSqr(damHorse);
        if (distSq > ThoroughbredRunEngine.MAX_BREEDING_PROXIMITY_SQR) {
            double dist = Math.sqrt(distSq);
            return String.format(Locale.ROOT,
                    "§c[Out of Breeding Range!] §b%s §cand §d%s §cmust be within %.0f blocks of each other (Love Mode range) to breed! (Current distance: %.1fm)",
                    sireData.getName(), damData.getName(), ThoroughbredRunEngine.MAX_BREEDING_PROXIMITY_BLOCKS, dist);
        }
        if (sireHorse.isVehicle() || damHorse.isVehicle()) {
            return "§c[Cannot Breed While Mounted!] Dismount both §b" + sireData.getName() + " §cand §d" + damData.getName() + " §cbefore breeding!";
        }
        if (sireData.hasPoorPrognosis() || damData.hasPoorPrognosis() || sireData.hasActiveInjury() || damData.hasActiveInjury()) {
            return "§cInjured horses or horses with Poor Prognosis cannot breed!";
        }
        if (ThoroughbredRunEngine.isHorseInBreedingRitual(sireHorse) || ThoroughbredRunEngine.isHorseInBreedingRitual(damHorse)) {
            return "§eOne of these horses is already in the middle of a Love Mode breeding ritual!";
        }
        return null;
    }

    private static void commenceBreeding(ServerLevel level, Horse sireHorse, ThoroughbredData sireData,
                                         Horse damHorse, ThoroughbredData damData, long paidStudFee) {
        ThoroughbredRunEngine.startBreedingLoveRitual(level, sireHorse, sireData, damHorse, damData, paidStudFee);
    }

    // =========================================================================
    // ADMIN BREEDING & TIME FLOW TESTING COMMANDS
    // =========================================================================
    private static int cmdAdminBreedHeat(CommandContext<CommandSourceStack> ctx, String mareNameArg) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        Horse horse = null;
        if (mareNameArg != null && !mareNameArg.isBlank()) {
            StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(stripOuterQuotes(mareNameArg));
            horse = findOrLoadHorseEntity(admin.server, rec);
        } else {
            horse = findNearestOwnedOrTargetHorse(admin, 10.0, false);
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cSpecify a Mare's name or stand near her: /tb breed heat [mare name]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!"Female".equalsIgnoreCase(data.getGender())) {
            admin.sendSystemMessage(Component.literal("§c" + data.getName() + " is Male! Only Mares can enter heat."));
            return 0;
        }
        if (!data.isRetired()) {
            data.setRetired(true);
        }
        boolean nextHeat = !data.isInHeat();
        data.setInHeat(nextHeat);
        data.setManualHeatOverride(true);
        data.saveToHorse(horse);

        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§d§l[Admin Breeding Test] §fSet Broodmare §6%s§f's In-Heat status to: %s",
                data.getName(), nextHeat ? "§a§lIN HEAT" : "§7OUT OF HEAT")));
        return 1;
    }

    private static int cmdAdminBreedAccelerate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        String mareName = stripOuterQuotes(StringArgumentType.getString(ctx, "mareName"));
        int pct = IntegerArgumentType.getInteger(ctx, "percent");

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(mareName);
        Horse horse = findOrLoadHorseEntity(admin.server, rec);

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cCould not find a Mare named '" + mareName + "'!"));
            return 0;
        }

        if (horse.level() instanceof ServerLevel sl) {
            ThoroughbredRunEngine.completePendingBreedingRitualImmediately(sl, horse, ThoroughbredData.fromHorse(horse));
        }
        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isPregnant()) {
            admin.sendSystemMessage(Component.literal("§c" + data.getName() + " is not currently pregnant!"));
            return 0;
        }

        long shiftMillis = Math.round(data.getPregnancyDurationMillis() * (pct / 100.0));
        data.setPregnancyConceptionMillis(data.getPregnancyConceptionMillis() - shiftMillis);
        data.saveToHorse(horse);

        if (data.isReadyToGiveBirth()) {
            ThoroughbredRunEngine.deliverFoalFromPregnantMare((ServerLevel) horse.level(), horse, data);
        } else {
            admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a§l[Pregnancy Accelerated] §fAdvanced §6%s§f's gestation by §e+%d%% §f(Now at §a%.0f%% §f| §e%.1f days remaining§f)!",
                    data.getName(), pct, data.getPregnancyProgressRatio() * 100.0, data.getPregnancyDaysRemaining())));
        }
        return 1;
    }

    private static int cmdAdminBreedBirth(CommandContext<CommandSourceStack> ctx, String mareNameArg) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        Horse horse = null;
        if (mareNameArg != null && !mareNameArg.isBlank()) {
            StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(stripOuterQuotes(mareNameArg));
            horse = findOrLoadHorseEntity(admin.server, rec);
        } else {
            horse = findNearestOwnedOrTargetHorse(admin, 12.0, false);
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cStand near a pregnant Mare or specify her name: /tb breed birth [mare name]"));
            return 0;
        }

        if (horse.level() instanceof ServerLevel sl) {
            ThoroughbredRunEngine.completePendingBreedingRitualImmediately(sl, horse, ThoroughbredData.fromHorse(horse));
        }
        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isPregnant()) {
            admin.sendSystemMessage(Component.literal("§c" + data.getName() + " is not currently pregnant! Breed her first with /tb breed [stallion] [mare]."));
            return 0;
        }

        Horse foal = ThoroughbredRunEngine.deliverFoalFromPregnantMare((ServerLevel) horse.level(), horse, data);
        if (foal != null) {
            admin.sendSystemMessage(Component.literal("§a§l[Instant Birth Triggered!] §6" + data.getName() + " §fgave birth immediately!"));
            return 1;
        }
        return 0;
    }

    private static int cmdAdminBreedGrow(CommandContext<CommandSourceStack> ctx, String foalNameArg) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        Horse horse = null;
        if (foalNameArg != null && !foalNameArg.isBlank()) {
            StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
            StudbookSavedData.HorseBackupRecord rec = studbook.getLivingHorseByName(stripOuterQuotes(foalNameArg));
            horse = findOrLoadHorseEntity(admin.server, rec);
        } else {
            AABB box = admin.getBoundingBox().inflate(12.0, 8.0, 12.0);
            List<Horse> youngsters = admin.level().getEntitiesOfClass(Horse.class, box,
                    h -> h.isAlive() && ThoroughbredData.isThoroughbred(h) && ThoroughbredData.fromHorse(h).isUnderTwoEquineYears());
            if (!youngsters.isEmpty()) {
                horse = youngsters.get(0);
            } else {
                horse = findNearestOwnedOrTargetHorse(admin, 10.0, false);
            }
        }

        if (horse == null || !ThoroughbredData.isThoroughbred(horse)) {
            admin.sendSystemMessage(Component.literal("§cStand near a Foal/Yearling or specify its name: /tb breed grow [horse name]"));
            return 0;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (!data.isUnderTwoEquineYears() && !horse.isBaby()) {
            admin.sendSystemMessage(Component.literal("§e" + data.getName() + " is already a full-grown 2+ Equine-Year-old adult!"));
            return 0;
        }

        ThoroughbredRunEngine.growUpFoalNow((ServerLevel) horse.level(), horse, data);
        return 1;
    }

    private static int cmdAdminTimeFlowGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        admin.sendSystemMessage(Component.literal("§6§l[World Time Flow] " + studbook.getFormattedTimeFlowSummary()));
        ModNetwork.sendOpenTimeFlowScreen(admin, studbook.getTimeFlowMode(), studbook.getCustomEquineYearDays(),
                false, studbook.isTimeFlowConfigured());
        return 1;
    }

    private static int cmdAdminTimeFlowSet(CommandContext<CommandSourceStack> ctx, int customDays) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        if (studbook.isTimeFlowConfigured()) {
            admin.sendSystemMessage(Component.literal(
                    "§cThe World Equine Time Flow has already been permanently locked for this world! To use a different Equine Time Flow, you must create a new world."));
            return 0;
        }

        String preset = StringArgumentType.getString(ctx, "preset").trim().toLowerCase(Locale.ROOT);
        int mode = switch (preset) {
            case "1year", "default", "365" -> StudbookSavedData.TIMEFLOW_1_YEAR;
            case "4month", "120" -> StudbookSavedData.TIMEFLOW_4_MONTH;
            case "3month", "90" -> StudbookSavedData.TIMEFLOW_3_MONTH;
            case "custom" -> StudbookSavedData.TIMEFLOW_CUSTOM;
            default -> StudbookSavedData.TIMEFLOW_1_YEAR;
        };

        studbook.setTimeFlowConfiguration(mode, customDays);
        admin.sendSystemMessage(Component.literal("§a§l[World Time Flow Permanently Locked!] " + studbook.getFormattedTimeFlowSummary()));
        return 1;
    }

    // =========================================================================
    // STEP 21: /tb owner, /tb ownerfee & /tb studbook COMMAND HANDLERS
    // =========================================================================
    private static int cmdOwnerOpenGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ModNetwork.sendOpenOwnerScreen(player, "");
        return 1;
    }

    private static int cmdStudbookOpenGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ModNetwork.sendOpenStudbookScreen(player);
        return 1;
    }

    private static int cmdAdminOwnerFeeInfo(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        admin.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§6§l[Horse Owner Fees] §eInitial Registration: §a$%,d §7| §eFarm Rename: §a$%,d §7| §eCrown Name (each): §a$%,d §7| §eJockey Silk Edit: §a$%,d\n"
                + "§7Usage: §f/tb owner fee <regFee> <farmFee> <crownFee> <silkFee> §7or §f/tb owner fee <registration|farm|crown|silk> <amount>",
                studbook.getOwnerRegistrationFee(),
                studbook.getOwnerFarmRenameFee(),
                studbook.getOwnerCrownEditFee(),
                studbook.getOwnerSilkEditFee())));
        return 1;
    }

    private static int cmdAdminSetSingleOwnerFee(CommandContext<CommandSourceStack> ctx, String which) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;
        long amount = Math.max(0L, LongArgumentType.getLong(ctx, "amount"));
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        switch (which) {
            case "registration" -> studbook.setOwnerRegistrationFee(amount);
            case "farm" -> studbook.setOwnerFarmRenameFee(amount);
            case "crown" -> studbook.setOwnerCrownEditFee(amount);
            case "silk" -> studbook.setOwnerSilkEditFee(amount);
        }
        return cmdAdminOwnerFeeInfo(ctx);
    }

    private static int cmdAdminSetAllOwnerFees(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;
        long regFee = Math.max(0L, LongArgumentType.getLong(ctx, "regFee"));
        long farmFee = Math.max(0L, LongArgumentType.getLong(ctx, "farmRenameFee"));
        long crownFee = Math.max(0L, LongArgumentType.getLong(ctx, "crownEditFee"));
        long silkFee = Math.max(0L, LongArgumentType.getLong(ctx, "silkEditFee"));
        StudbookSavedData studbook = StudbookSavedData.get(admin.serverLevel());
        studbook.setOwnerRegistrationFee(regFee);
        studbook.setOwnerFarmRenameFee(farmFee);
        studbook.setOwnerCrownEditFee(crownFee);
        studbook.setOwnerSilkEditFee(silkFee);
        return cmdAdminOwnerFeeInfo(ctx);
    }

    private static int cmdAdminRaceResultsOpenGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;
        RacingManager.openRaceResultsGui(admin);
        return 1;
    }

    private static int cmdAdminRaceResultsConfirm(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;
        return RacingManager.confirmPendingRaceResults(admin) ? 1 : 0;
    }

    private static int cmdReloadTranslations(CommandContext<CommandSourceStack> ctx) {
        int count = ThoroughbredData.reloadExternalTranslations();
        java.util.List<java.io.File> dirs = ThoroughbredData.getTranslationDirectories();
        String pathStr = dirs.isEmpty() ? "aiothoroughbred_translations/" : dirs.get(0).getAbsolutePath();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a§l[Translations Reloaded!] §fLoaded §e" + count + " §fcustomizable phrases from §b" + pathStr + "§f!"), false);
        return 1;
    }
}