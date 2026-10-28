package com.aiothoroughbred;

import com.aiothoroughbred.item.ModItems;
import com.aiothoroughbred.block.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Mod(AIOThoroughbredMod.MODID)
public class AIOThoroughbredMod {
    public static final String MODID = "aiothoroughbred";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, StopwatchSession> STOPWATCH_SESSIONS = new HashMap<>();

    public static class StopwatchSession {
        public long startTick = 0;
        public double startOdometerMeters = 0.0;
        public double startX = 0;
        public double startZ = 0;
    }

    public AIOThoroughbredMod() {
        ensureStartingGateAssetsInstalled();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onAddPackFinders);
        MinecraftForge.EVENT_BUS.register(this);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEventRegistrar::registerClientEvents);
    }

    private static java.io.File getRuntimeResourcePackDir() {
        try {
            java.nio.file.Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            if (gameDir != null) {
                return gameDir.resolve("aiothoroughbred_textures").resolve(".runtime_pack").toFile().getCanonicalFile();
            }
        } catch (Throwable ignored) {
        }
        try {
            return new java.io.File("aiothoroughbred_textures/.runtime_pack").getCanonicalFile();
        } catch (Exception e) {
            return new java.io.File("aiothoroughbred_textures/.runtime_pack");
        }
    }

    private void onAddPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(packConsumer -> {
                try {
                    ensureStartingGateAssetsInstalled();
                    java.io.File runtimePackDir = getRuntimeResourcePackDir();
                    if (runtimePackDir != null && runtimePackDir.isDirectory()) {
                        net.minecraft.server.packs.repository.Pack pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(
                                "aiothoroughbred_runtime_assets",
                                Component.literal("AIO Thoroughbred Custom Assets"),
                                true,
                                id -> new net.minecraft.server.packs.PathPackResources(id, runtimePackDir.toPath(), true),
                                net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                                net.minecraft.server.packs.repository.Pack.Position.TOP,
                                net.minecraft.server.packs.repository.PackSource.BUILT_IN
                        );
                        if (pack != null) {
                            packConsumer.accept(pack);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not register runtime asset pack: {}", e.getMessage());
                }
            });
        }
    }


    /**
     * Automatically installs/updates the blockstates, block models, item models, translations, and 16x16 PNG textures
     * for Green Starting Gate Stall Frame, Starting Gate Stall Separator, and Starting Gate Number Sign (#1..#16)
     * across both `src/main/resources` and active runtime resource directories (`build/resources/main`, `out/production/resources`)
     * before Minecraft's ModelManager and TextureManager initialize!
     */
    private static void ensureStartingGateAssetsInstalled() {
        try {
            List<java.io.File> projectRoots = new ArrayList<>();
            try {
                java.io.File cur = new java.io.File(".").getCanonicalFile();
                for (int i = 0; i < 6 && cur != null; i++) {
                    if ((new java.io.File(cur, "src/main/resources").isDirectory() || new java.io.File(cur, "build.gradle").isFile())
                            && !projectRoots.contains(cur)) {
                        projectRoots.add(cur);
                    }
                    cur = cur.getParentFile();
                }
            } catch (Exception ignored) {
            }
            try {
                java.nio.file.Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
                if (gameDir != null) {
                    java.io.File cur = gameDir.toFile().getCanonicalFile();
                    for (int i = 0; i < 6 && cur != null; i++) {
                        if ((new java.io.File(cur, "src/main/resources").isDirectory() || new java.io.File(cur, "build.gradle").isFile())
                                && !projectRoots.contains(cur)) {
                            projectRoots.add(cur);
                        }
                        cur = cur.getParentFile();
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                java.net.URL locUrl = AIOThoroughbredMod.class.getProtectionDomain().getCodeSource().getLocation();
                if (locUrl != null) {
                    String raw = locUrl.toString();
                    while (raw.startsWith("union:") || raw.startsWith("jar:") || raw.startsWith("file:")) {
                        if (raw.startsWith("union:")) raw = raw.substring(6);
                        else if (raw.startsWith("jar:")) raw = raw.substring(4);
                        else if (raw.startsWith("file:")) raw = raw.substring(5);
                    }
                    int cutIdx = raw.indexOf("%23");
                    if (cutIdx >= 0) raw = raw.substring(0, cutIdx);
                    cutIdx = raw.indexOf("#");
                    if (cutIdx >= 0) raw = raw.substring(0, cutIdx);
                    cutIdx = raw.indexOf("!");
                    if (cutIdx >= 0) raw = raw.substring(0, cutIdx);
                    String decoded = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
                    if (decoded.length() > 2 && decoded.charAt(0) == '/' && decoded.charAt(2) == ':') {
                        decoded = decoded.substring(1);
                    }
                    java.io.File cur = new java.io.File(decoded).getCanonicalFile();
                    for (int i = 0; i < 8 && cur != null; i++) {
                        if ((new java.io.File(cur, "src/main/resources").isDirectory() || new java.io.File(cur, "build.gradle").isFile())
                                && !projectRoots.contains(cur)) {
                            projectRoots.add(cur);
                        }
                        cur = cur.getParentFile();
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                String userHome = System.getProperty("user.home");
                if (userHome != null && !userHome.isBlank()) {
                    String[] candidateRelPaths = new String[] {
                        "Desktop/AIOThoroughbredMod",
                        "OneDrive/Desktop/AIOThoroughbredMod",
                        "Documents/AIOThoroughbredMod",
                        "IdeaProjects/AIOThoroughbredMod"
                    };
                    for (String rel : candidateRelPaths) {
                        java.io.File candidate = new java.io.File(userHome, rel);
                        if (candidate.isDirectory()) {
                            java.io.File canon = candidate.getCanonicalFile();
                            if (!projectRoots.contains(canon)) {
                                projectRoots.add(canon);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            List<java.io.File> assetRoots = new ArrayList<>();
            try {
                java.io.File runtimePackDir = getRuntimeResourcePackDir();
                if (runtimePackDir != null) {
                    runtimePackDir.mkdirs();
                    String packMcmeta = "{\n  \"pack\": {\n    \"description\": \"AIO Thoroughbred Custom Assets\",\n    \"pack_format\": 15\n  }\n}\n";
                    java.nio.file.Files.writeString(
                            new java.io.File(runtimePackDir, "pack.mcmeta").toPath(),
                            packMcmeta,
                            java.nio.charset.StandardCharsets.UTF_8
                    );
                    assetRoots.add(new java.io.File(runtimePackDir, "assets/aiothoroughbred"));
                }
            } catch (Exception ignored) {
            }
            for (java.io.File root : projectRoots) {
                assetRoots.add(new java.io.File(root, "src/main/resources/assets/aiothoroughbred"));
                assetRoots.add(new java.io.File(root, "build/resources/main/assets/aiothoroughbred"));
                if (new java.io.File(root, "out/production/resources").isDirectory()) {
                    assetRoots.add(new java.io.File(root, "out/production/resources/assets/aiothoroughbred"));
                }
                if (new java.io.File(root, "build/classes/java/main").isDirectory()) {
                    assetRoots.add(new java.io.File(root, "build/classes/java/main/assets/aiothoroughbred"));
                }
            }

            String displayJson = "\"display\": {\n"
                    + "    \"gui\": { \"rotation\": [30, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.625, 0.625, 0.625] },\n"
                    + "    \"ground\": { \"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.25, 0.25, 0.25] },\n"
                    + "    \"fixed\": { \"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [0.5, 0.5, 0.5] },\n"
                    + "    \"thirdperson_righthand\": { \"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.375, 0.375, 0.375] },\n"
                    + "    \"firstperson_righthand\": { \"rotation\": [0, 45, 0], \"translation\": [0, 0, 0], \"scale\": [0.4, 0.4, 0.4] }\n"
                    + "  }";

            String bsGreenFrame = "{\n  \"variants\": {\n    \"\": { \"model\": \"aiothoroughbred:block/green_starting_gate_frame\" }\n  }\n}\n";
            String bsSeparator = "{\n  \"variants\": {\n"
                    + "    \"facing=north\": { \"model\": \"aiothoroughbred:block/starting_gate_separator\" },\n"
                    + "    \"facing=south\": { \"model\": \"aiothoroughbred:block/starting_gate_separator\", \"y\": 180 },\n"
                    + "    \"facing=east\":  { \"model\": \"aiothoroughbred:block/starting_gate_separator\", \"y\": 90 },\n"
                    + "    \"facing=west\":  { \"model\": \"aiothoroughbred:block/starting_gate_separator\", \"y\": 270 }\n"
                    + "  }\n}\n";

            StringBuilder bsSignSb = new StringBuilder("{\n  \"variants\": {\n");
            for (int n = 1; n <= 16; n++) {
                bsSignSb.append("    \"facing=north,gate_number=").append(n).append("\": { \"model\": \"aiothoroughbred:block/gate_sign_").append(n).append("\" },\n");
                bsSignSb.append("    \"facing=south,gate_number=").append(n).append("\": { \"model\": \"aiothoroughbred:block/gate_sign_").append(n).append("\", \"y\": 180 },\n");
                bsSignSb.append("    \"facing=east,gate_number=").append(n).append("\":  { \"model\": \"aiothoroughbred:block/gate_sign_").append(n).append("\", \"y\": 90 },\n");
                bsSignSb.append("    \"facing=west,gate_number=").append(n).append("\":  { \"model\": \"aiothoroughbred:block/gate_sign_").append(n).append("\", \"y\": 270 }")
                        .append(n < 16 ? ",\n" : "\n");
            }
            bsSignSb.append("  }\n}\n");
            String bsSign = bsSignSb.toString();

            String bsMainGate = "{\n  \"multipart\": [\n"
                    + "    { \"when\": { \"facing\": \"north\", \"open\": \"false\" }, \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_closed\" } },\n"
                    + "    { \"when\": { \"facing\": \"south\", \"open\": \"false\" }, \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_closed\", \"y\": 180 } },\n"
                    + "    { \"when\": { \"facing\": \"east\",  \"open\": \"false\" }, \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_closed\", \"y\": 90 } },\n"
                    + "    { \"when\": { \"facing\": \"west\",  \"open\": \"false\" }, \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_closed\", \"y\": 270 } },\n"
                    + "    { \"when\": { \"facing\": \"north\", \"open\": \"true\" },  \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_open\" } },\n"
                    + "    { \"when\": { \"facing\": \"south\", \"open\": \"true\" },  \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_open\", \"y\": 180 } },\n"
                    + "    { \"when\": { \"facing\": \"east\",  \"open\": \"true\" },  \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_open\", \"y\": 90 } },\n"
                    + "    { \"when\": { \"facing\": \"west\",  \"open\": \"true\" },  \"apply\": { \"model\": \"aiothoroughbred:block/main_starting_gate_open\", \"y\": 270 } }\n"
                    + "  ]\n}\n";

            String modelGreenFrame = "{\n"
                    + "  \"parent\": \"minecraft:block/block\",\n"
                    + "  \"textures\": {\n"
                    + "    \"particle\": \"aiothoroughbred:block/green_gate_frame_pillar\",\n"
                    + "    \"pillar\": \"aiothoroughbred:block/green_gate_frame_pillar\"\n"
                    + "  },\n"
                    + "  \"elements\": [\n"
                    + "    {\n"
                    + "      \"from\": [5, 0, 5],\n"
                    + "      \"to\": [11, 16, 11],\n"
                    + "      \"faces\": {\n"
                    + "        \"north\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#pillar\" },\n"
                    + "        \"south\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#pillar\" },\n"
                    + "        \"east\":  { \"uv\": [0, 0, 16, 16], \"texture\": \"#pillar\" },\n"
                    + "        \"west\":  { \"uv\": [0, 0, 16, 16], \"texture\": \"#pillar\" },\n"
                    + "        \"up\":    { \"uv\": [0, 0, 16, 16], \"texture\": \"#pillar\" },\n"
                    + "        \"down\":  { \"uv\": [0, 0, 16, 16], \"texture\": \"#pillar\" }\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ],\n  " + displayJson + "\n}\n";

            String modelSeparator = "{\n"
                    + "  \"parent\": \"minecraft:block/block\",\n"
                    + "  \"textures\": {\n"
                    + "    \"particle\": \"aiothoroughbred:block/gate_separator_panel\",\n"
                    + "    \"panel\": \"aiothoroughbred:block/gate_separator_panel\",\n"
                    + "    \"frame\": \"aiothoroughbred:block/gate_frame_pillar\"\n"
                    + "  },\n"
                    + "  \"elements\": [\n"
                    + "    {\n"
                    + "      \"from\": [0, 2, 6],\n"
                    + "      \"to\": [16, 14, 10],\n"
                    + "      \"faces\": {\n"
                    + "        \"north\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#panel\" },\n"
                    + "        \"south\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#panel\" },\n"
                    + "        \"west\":  { \"uv\": [6, 2, 10, 14], \"texture\": \"#frame\" },\n"
                    + "        \"east\":  { \"uv\": [6, 2, 10, 14], \"texture\": \"#frame\" },\n"
                    + "        \"up\":    { \"uv\": [0, 6, 16, 10], \"texture\": \"#frame\" },\n"
                    + "        \"down\":  { \"uv\": [0, 6, 16, 10], \"texture\": \"#frame\" }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"from\": [0, 14, 5.5],\n"
                    + "      \"to\": [16, 16, 10.5],\n"
                    + "      \"faces\": {\n"
                    + "        \"north\": { \"uv\": [0, 0, 16, 2], \"texture\": \"#frame\" },\n"
                    + "        \"south\": { \"uv\": [0, 0, 16, 2], \"texture\": \"#frame\" },\n"
                    + "        \"west\":  { \"uv\": [5, 0, 11, 2], \"texture\": \"#frame\" },\n"
                    + "        \"east\":  { \"uv\": [5, 0, 11, 2], \"texture\": \"#frame\" },\n"
                    + "        \"up\":    { \"uv\": [0, 5, 16, 11], \"texture\": \"#frame\" },\n"
                    + "        \"down\":  { \"uv\": [0, 5, 16, 11], \"texture\": \"#frame\" }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"from\": [0, 0, 5.5],\n"
                    + "      \"to\": [16, 2, 10.5],\n"
                    + "      \"faces\": {\n"
                    + "        \"north\": { \"uv\": [0, 14, 16, 16], \"texture\": \"#frame\" },\n"
                    + "        \"south\": { \"uv\": [0, 14, 16, 16], \"texture\": \"#frame\" },\n"
                    + "        \"west\":  { \"uv\": [5, 14, 11, 16], \"texture\": \"#frame\" },\n"
                    + "        \"east\":  { \"uv\": [5, 14, 11, 16], \"texture\": \"#frame\" },\n"
                    + "        \"up\":    { \"uv\": [0, 5, 16, 11], \"texture\": \"#frame\" },\n"
                    + "        \"down\":  { \"uv\": [0, 5, 16, 11], \"texture\": \"#frame\" }\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ],\n  " + displayJson + "\n}\n";

            String[][] pngBase64Table = new String[][] {
                { "gate_frame_pillar.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAV0lEQVR4nGPsmTLnv66ODoO2phrDp89fGJDB23cfGBgYGBiEhQRQxPl4eRiuXr/FcPnKFQYmBgoBdQ04deYcQQ3oalAMMDMxImgAuppBFgajBoymA3IBAKfhGtsUXKn5AAAAAElFTkSuQmCC" },
                { "green_gate_frame_pillar.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAO0lEQVR4nGMUclH7z0ABYGFgYGDgkOBj4BLnY/j28hNRmmBqf7z4xMBEie0MDAyjBowaMGoAFDBSmp0BaRkMF0IbX6EAAAAASUVORK5CYII=" },
                { "gate_separator_panel.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAR0lEQVR4nGMsq2n6z0ABYKJEMwMDAwMLAwMDg5GREVzg/oMHDIoKCkTxz507R7kLRg0YNWBwGMDCwABJ08jg/bt3RPMpdgEAQyQZYv8WqkoAAAAASUVORK5CYII=" },
                { "main_gate_panel.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAXklEQVR4nGO8s0rmv5AQA0NwjwnD61evGNjY2Bhmz5nDwMDAwBAfF4eVzy8gwLC25AzDu3cMDEwMSICNjY3h169fRPMZGNAM+PXrFwMbGxvRfAwDRl0w6oJRF5DvAgA/1ZfB7wanhQAAAABJRU5ErkJggg==" },
                { "rear_gate_panel.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAX0lEQVR4nGNsCtL4z8fHx7DgCgvDp0+fGFhZWRmycvMZGBgYGCb29WDlc3JyMiTo/GH49OkTAxMDEmBlZWX4/fs30XwGBgZUA37//s3AyspKNB/DgFEXjLpg1AXkuwAAffqYG1p/3Z4AAAAASUVORK5CYII=" },
                { "gate_num_1.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAsElEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECbIpertrDwMDAwCAc5IAhDwM4XQDTzMDAwPDj6hOcLoAbgG6DeJgLamiT6gJkRdjkCboAWdHAuODtugNwRV9v3sFpONwodBuEgxwYflx9guIKHn0F0tIBh7YMzsREVBiQlBJhaZtUwEhpdgYAiDTFA4inuUsAAAAASUVORK5CYII=" },
                { "gate_num_2.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAx0lEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECdEVv1x2AKxIOcsCQhwGsLoBp5lZXYWBgYGB4u+4AThfADUCWFA5ygGuGgR9XnxDvAhYWFgYObRmGrzfvoLiEaBdg8waPvgL5YYCumWAswADMCwwMDAziYS5YXQBXjSwpHubC8OXiAwZ0gOwSvC748+cPVmeTFAskp0RY2iYVMFKanQGcqsOlj25CXAAAAABJRU5ErkJggg==" },
                { "gate_num_3.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAzElEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECZMmXq/agOFU4yAHDEBjA6gLxMBcGbnUVBm51FQYGBgaGt+sO4HQB3AB0G3j0FRg4tGXgCn9cfYLVBXCj0G1A9gbMJSS5AOYNBgYGhq837zDw6CsQHwbI3oCBLxcfkB8LMJcQHQbiYS4MXy4+QDGEQ1uGeBfAnI8vMREVBiSlRFjaJhUwUpqdASOywZbCqlMeAAAAAElFTkSuQmCC" },
                { "gate_num_4.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAtklEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECZMmXq/YwMDAwMAgHOTC8XXeAgYGBgUE8zAXFEBjA6gIY+HH1CZz95eIDrC6AG4BNEh2Q7IKvN++gGEB0GAgHOcCdj2wINktYsEmysLAwcGjLYHiH6DDAZhPJLsDmHaLDAJd3iI4FslIiLG2TChgpzc4AF6m5aUWSDZYAAAAASUVORK5CYII=" },
                { "gate_num_5.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAvElEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECZMmXq/ZgOFc8zAXFEAwDsElyq6ugGMKjr0CcC3BpItkFX2/ewep8ZEvggYgsKRzkwMCtrgL3wstVe3C6AGcscGjLMPDoK8AVfrn4gPxYgLmE6DAQD3Nh+HLxAYohHNoyxLvgz58/OEOf6DAgOSXC0japgJHS7AwAx4G2RN+Nf+4AAAAASUVORK5CYII=" },
                { "gate_num_6.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAsklEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECdEVv1x1Aca54mAuKPIYB2DRzq6ugGMKjr0CcC3BpItoFMPD15h2szke2BB6I2CRhXni5ag9OF2CNBWzgy8UHxLtAOMgBxQswlxAdBjBDflx9AlfIoS1DWjpgYWHB0ER0GJCVEmFpm1TASGl2BgCal7gEh0H8TwAAAABJRU5ErkJggg==" },
                { "gate_num_7.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAwUlEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECZMmXq/ZgOFc8zAXFEBjA6gLxMBcGbnUVBm51FbjCLxcfEO+CP3/+MPDoK6DYhG4JXhfA6LfrDjAwMDDAXYLNBXADCGmGuYhoFyADdM1EuQCX39HlWXApEg5yYPhx9QlWw4mKBRYWFgYObRmciYmoWCApJcLSNqmAkdLsDAA+LsFRGXs/dgAAAABJRU5ErkJggg==" },
                { "gate_num_8.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAyElEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECdEVv1x2AKxIOcsCQhwGsLoBp5lZXYWBgYGB4u+4AThfADcAmiQx+XH1CvAvEw1wYGBgYGL7evIPiEqJd8HLVHhSNX2/eYeDRVyDeBdjAl4sPiHeBcJADVi9gswRuFHooCwc5MPy4+gSukENbhrR0wMLCgqGJ6DAgKyXC0japgJHS7AwAWAS8r3kHmMIAAAAASUVORK5CYII=" },
                { "gate_num_9.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAw0lEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECdEVv1x2AKxIOcsCQhwGsLoBp5lZXYWBgYGB4u+4AThfADcAmiQx+XH1CvAvEw1wYGBgYGL7evINiCNEugBkC8wI2S/C6AEbz6CsQdAHWWHi5ag+KRphLsLkAbgC6879cfIBiCIe2DGnpgEdfAW9iIioMSEqJsLRNKmCkNDsDADLGvUrsqytbAAAAAElFTkSuQmCC" },
                { "gate_num_10.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA4ElEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECmOTbdQcYhIMcUPgwgCwOAygugCn+cfUJCp9bXYWBgYGB4e26AxgugBvAwsLCIBzkwIDMxwZghmN1AbImZEXoAKcLkDXhcgG64dR1wctVexgYGBgYvt68gxImX2/eYWBgQAQmikuRbRQPc2H4cvEBXJJHX4FBOMiB4cfVJ3AxDm0Z/OmAR18BI9HANCGL4wwDslMiLG2TChgpzc4AubmhWcCL8WoAAAAASUVORK5CYII=" },
                { "gate_num_11.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA0UlEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F2EATPLtugMYil+u2oMhjmEATDMDAwPDj6tPUDTDALI4VhcIBzkwIPP//PnDIB7mghJweF2AbDI252JThxEG6HxkxbR3ASzAvt68gxIrMIAsDteHbLJ4mAvDl4sP4JI8+goMwkEODD+uPkFxBY++AqYBMJN59BUw0gGHtgxGYsIZBqSmRDgLlrZJBYyUZmcAmwu8pM/8WzoAAAAASUVORK5CYII=" },
                { "gate_num_12.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA5UlEQVR4nKWToQoCQRRFj/KCoEUMFoOIoCBoN23wCzb4Ewa/xegvGPwCg8luUFjYYLAYTDpgWNAgb53ZdUXdW4a7M/fOYXlTaHX7d3JIADYz81d4OC0/CwD68ypRFCEiX637yQWAohb8EhaJ730R6OZ5uabme45X2d9VDoEevu2Oji932gCcl+sUQVwgItR8j6TXsErL3xLYzepLvQYmCB2STAK7OfkPNFwZNL8nyApnEpwWKwBMEDqHTBBigpDTYpUmtW+sj0dct4d4M+lVlUEzXaDNNuY7/3EOck2izvavKuR9zg8BDLhSNbXFgQAAAABJRU5ErkJggg==" },
                { "gate_num_13.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA/klEQVR4nKWToY7CQBCGPy4jSIohiBoEuZBAQgIehegTIPoSJ3iWk/cKJ/oEJ6rwCEhIEAgMogqanJjkENwslN6Sg45p/tnsv9+/O6299oc/VCgBmL/nT20ez4KzAcDwo4mqIiL/+q7eDgC8mMEjm0XcuRcCW8ySlNZ0goiw//wqIFtfVV2vQJAlKQDfyx2qShhHBL0uQa8LQJakJQJnICK0phOutarSGHWoD9qub+alCLfOpq9jGImXoOD8qy0GQL7e0Bh1/HfwF4HFsDoutv5XMNx8vSGMo9IrGIn3DsI44rjYusVbDVAftO/PgWX06btzUGkSbbYfrVrV3/kEpy6ylsy/E08AAAAASUVORK5CYII=" },
                { "gate_num_14.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA3UlEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F2EATPLtugNwRS9X7YHz3647wPBy1R64OgwDYIoYGBgYflx9gqLox9UncPaXiw9wu0A4yIEBmY8L4HQBsiZkRV9v3kExAFkdC7IgsiaYi2DORzYERR0+F7CwsDBwaMtgeAdnGLxctQduG3rU4QoDFBeIh7kwfLn4AC7Jo68ANwTZO3jDAFkTOg3zDt5YIDUlwlmwtE0qYKQ0OwMAdWWytFirmRoAAAAASUVORK5CYII=" },
                { "gate_num_15.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA+ElEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECmOTbdQcYhIMcGFhYWBhertqD4WzxMBeGP3/+YBoA08zAwMDw4+oTBg5tGbgibnUVFEN49BXgbLgXWFhYGISDHBiQ+eiaOLRlGHj0FXC7AFkTsqKvN++gOB9ZHYoLUEyGuohbXQXuhZer9mCow4gFdD7M2TDw5eID3LEAC/WvN+8wiIe5YMQCzCU4w0A8zIXhy8UHcEl0PgMDAwOHtgz+dAALZVx8gmFAdkqEpW1SASOl2RkA/XCiR2z8OJwAAAAASUVORK5CYII=" },
                { "gate_num_16.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA8klEQVR4nGNU0tD7z0ABYGFgYGA4NvErWZqt8rkhBjAwMDDozRRk+PPnDwMLCwtR9LXszwwMDAwMTDADSNHMwgK3F+ECmOTbdQcYhIMcUPjIQDzMheHPnz+YBiAr/nH1CQOHtgycz62ugmIIj74CnA33AgsLC4NwkAMDMh9dE4e2DAOPvgJuFyBrQlb09eYdFOcjq0NxAYrJSIpgXni5ag+GOoxYwOYCZPDl4gPcLni5ag/cychhAvMCzCU4w0A8zIXhy8UHcEkefQUG4SAHhh9Xn8DFOLRl8KcDWCgjJxqYJmRxnGFAdkqEpW1SASOl2RkAMvyjsShzMdgAAAAASUVORK5CYII=" },
                { "tote_board.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAeUlEQVR4nGMUkhL7z0ABYGFgYGCQcFIhS/OLfXcgBjAwMDA82nKHJM1yPhBLmZAFOXj4GOR8VBg4ePjgfHw0AwMDwgUMDAwMP758Yni05RMKHzctht0FpNAYBuC3EZMedcGgc8GJ+YoMxAJktYxCUmL/KclMjJRmZwBzFWpFcquiuwAAAABJRU5ErkJggg==" },
                { "starter_console_front.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAxklEQVR4nGOUMVD5z0ABYGFgYGDgExMiS/OnV+8gBjAwMDC8uPyAJM0SugoMDAwMDExkWY0EUAxQeKaGQr/dCfHaW2U1FBoZMMoYqPznExMiywufXr1DdUHQ+pdE0VR1AQuyADmABZkz79EnODtJjo/hX94fOJ9pEgvDpmlODG/eBDAk1eUhxNFNNDcyQuEbeSP4fln7UDRjNeDkuXMo/HNbEfx5TZMYhNx2ocjDA5EcgBGNDAwMDO8fvcBLowOKXcACY5ALAF4rU/pX+p7jAAAAAElFTkSuQmCC" },
                { "starter_console_top.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAmUlEQVR4nGNUMNP4z0ABYGFgYGD49+IbAwMDA8OJBRDaIoGLKD6TBBcDEyW2MzAwMDAqmGn8F5AQIUvzhxdvKHcBhgE870jjE3TBtwVcpLlgwbdvKHyuhG/oSvAbkMCF38ZvNlvwG0AIcB3xocwAdIBhwBch0vjUc8H5d/iUoQKejYiUCzfAUAirWqzgi/8bOJuFgQGSpskFAPxvKOFM7QVpAAAAAElFTkSuQmCC" },
                { "starter_console_side.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAX0lEQVR4nGOUMVD5z0ABYGJgYGCY0TeBgU9EgCQ8o28CAwMDAwOjjIHKfz4RAbJs//TmA8QFFHuBEsCCLvDq/jO8GsQUpfAbgK5g1AVDwQXUSYldVQ0ka4TpYaQ0OwMAh1Udiw36ji0AAAAASUVORK5CYII=" },
                { "starter_console_bottom.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAQUlEQVR4nGOU0lL4z0ABYGFgYGDgEeIjS/OXd58YmGCctPhkojUiq2WU0lL4TxUXkAtGDRg1AMWAAUvKLDAGuQAA/sEUvx4ZmuMAAAAASUVORK5CYII=" },
                { "racing_console_front.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA0ElEQVR4nGOUUdD6z0ABYGFgYGAQEBIjS/OHd68gBjAwMDA8enCLJM1yCmoMDAwMDExkWY0EMAz4dOUpXpqgAXw60nhpggZ8PPoMzt514BCcvT9XAqsBjDIKWv8FhMQYHj24xSDgEYJVETr4sGMNg5yCGmoswCRIBXADYNFCtgEMDAwMV86qMOgY32G43HSAQbfOgeEywycGXQY+hjmzp6NoSknNhLPhYUAOwAiDjvZmDEUVlbUMUgGTMMSfbcijjgsw0sGLZw/w0uiAkdLsDABe30ytoJ2r6AAAAABJRU5ErkJggg==" },
                { "racing_console_top.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAkklEQVR4nGOUUzH4z0ABYGFgYGAQEpEgS/O7Ny8gBjAwMDCcn/CAwbBAgUEhYx5eTQ9mJDFcWGLAYBBzgYGBgYGBUU7F4D9VXMDAwMDw5dM7ojTy8AnB2UzIEksWLySKRgYoXiDVBe/evBh1AXo0IkcPsYCJsBL8AMUFnz+9J0oTL58gdgOQJUhywbs3L0jWCAMAWfNOEd2RRGQAAAAASUVORK5CYII=" },
                { "racing_console_side.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAXklEQVR4nGOUUdD6z0ABYGJgYGDomzKPQUBEgiTcN2UeAwMDAwOjjILWfwERCbJs//DmBcQFlAAWdIEXTx/g1SAhrYDfAHQFoy4YCi6gOCFRx4C6hjaSNcL0MFKanQGvUB4BH4hmCQAAAABJRU5ErkJggg==" },
                { "racing_console_bottom.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAQUlEQVR4nGOUU1D5z0ABYGFgYGAQEZMgS/ObVy8YmGCcgMAAojUiq2WUU1D5TxUXkAtGDRg1AMWAAUvKLDAGuQAAOQYTx82TJXQAAAAASUVORK5CYII=" }
            };

            String[][] itemPngBase64Table = new String[][] {
                { "thoroughbred_registry_form.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAyUlEQVR4nGNkgIIYB7X/DESAJQduMSLzGWGaK/IFidHPwMDAwNAx8T3cIBZkCSXbWQQ1v3vxkKEiv5WBgUHt/5IDtxiZ0BXcO5zGcO9wGk4+OsAwgFSAYQDMG8g24/MaCzZBYsICpwG1BcV4NRRW5OE3oHlCL14D3r14ONRcEHjiGZxtdGI5hoEEXTBNw53BQkAIp4tQDEB3HjrAJs/CwADLYWr/IWkcFXjMlmfYkQrRKHAbUx4la+LK0gZXPsHZF3T4GBCWMjAAAI/uTpCGtjrpAAAAAElFTkSuQmCC" },
                { "racing_crop.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA5ElEQVR4nJ3ToYpCQRTG8b9i2DDhhA0TZblBxGC4yCaj+ASmDT6EiE9g8iEMBqPhhkWMG0UMYrqIccJlmXD7mLxcRRzH03/f+WbgwBujRTktygFU38Fxp89wOEGLckEBV9xqtlklS4Ia3OPoqwFAJSTgp9cm2VlsdgbA2LxSC8GHk8FmBmPzYrH3CWW8T2+xN8CHnwa8guHBJ2pRbj5WbPaRFwPU7vGsHtHpfgApi3X+FN80uOKttcQijM6pFxcBZby0GcBLGKCqRblj0uJzYIhFgnBR/Xeq3f/fd3FhwVM+z9C5AHcrfD1JWO2LAAAAAElFTkSuQmCC" },
                { "trainer_stopwatch.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABFElEQVR4nGNkwAFUNAz+I/Pv3LjAiE0dVkEVDYP/hyZxoIjZ5f3AaghWA2CG9MR9YWBgYGAoWcQD1wxzGYyPYQBMATYXIIvDXMSIrhmm4I3SBhQDRO4FoBiE4R0VDYP/z3ZZ/L9058V/DQOb/z9+/UHBGgY2/y/defF/Q5vKf/QARtEM03Dr0Zv/GgY2/zUMbP7fevQGLo5uCBOuQJSTEGDY8uMNLmk4wDDAwMwBzpa5dIVhy483DH5+AcQbcOHUARS+D4cIhhheA2Dg0YsPDI9efGDYtGkDw6MXH3AaAAfkxgJGOoClPqWwIygW3Ftlw8DAgJoqMQyAGcLAwMAAMwgGShbxMDAwYCYgvHkBmY8rN1IMAEJTxQV1JRDeAAAAAElFTkSuQmCC" },
                { "grooming_brush.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAzklEQVR4nGNgoBAwoguYKAj9x6fhzIN3KHrgHJjGngQzvDaWLDiFYhAjTHNPghnDvUdvGA5deoHXADs9CQYlORGGkgWnGM48eMfIBJOAaW7p0sCpuaVLg+HQpRcM9x69QfWCiYLQ/ywnJbw2o4Np++4xnHnwjhFuAEm6oQBuAMyQvATczkcGkxbcgAciC7KEl58Dw60rtxjUdNTw0gwMN+B6mJANIEazmo4aimtQDCBGM8QFOAygyAVnHrxjzKvbRzAAPYJmoCRnivPCwAMASbKAHE66ETYAAAAASUVORK5CYII=" },
                { "checkpoint_marker.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAq0lEQVR4nGNkIBLIqej9R+Y/unOJkYGBgYGFWM1G5y6iChrp/4cZQlBzwKf//3/8QsUBn/7/l1PR+89ErBcUNl3HyibsBAbsXjhnpM/w6M4lRoIGyKno/T8xjY2BgYGBwSLrF1wc5n+8Bsip6P3fBdHLsOfDB4YuLgEGogIOphk50NCjEQawmkYXm7G6gK42o7gAOaoYGCDRRazNjBQ5mwGamdxg6YNEzVQBAOixjKlx95VlAAAAAElFTkSuQmCC" },
                { "semaphore_flag.png", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA8klEQVR4nL2RMQrCMBiFX6STg4OIZMgkGcXJITiKB3Bwcu7sCXoATyIewslBOhdxkNJBMmQQEYfiIPxOgbSxrS4+yPKT770/L8APkoKTFJzcWVB3uTyLNwMAgFqCUm0YALAqcNfueqaz/IZQ9RCuOlDLDKk2LPgGBIB+kiABMJKiMGcWrgItbJVdDObTMewTKjsogxYuqwUAqTZslt8QPx6NsJvuSQpOWy4of74K53jWJAWn9WJY/42pNiwSnKJSUXXyOvi0nhScQtXDSd89g1ZTggsf9MsLqDVoghuTr/sJXfcTrzhXHx3/kly5gQt9k/wGit+RSZK669QAAAAASUVORK5CYII=" }
            };

            String bsToteBoard = "{\n  \"variants\": {\n"
                    + "    \"facing=north,theme=0\": { \"model\": \"aiothoroughbred:block/tote_board\" },\n"
                    + "    \"facing=south,theme=0\": { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 180 },\n"
                    + "    \"facing=east,theme=0\":  { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 90 },\n"
                    + "    \"facing=west,theme=0\":  { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 270 },\n"
                    + "    \"facing=north,theme=1\": { \"model\": \"aiothoroughbred:block/tote_board\" },\n"
                    + "    \"facing=south,theme=1\": { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 180 },\n"
                    + "    \"facing=east,theme=1\":  { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 90 },\n"
                    + "    \"facing=west,theme=1\":  { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 270 },\n"
                    + "    \"facing=north,theme=2\": { \"model\": \"aiothoroughbred:block/tote_board\" },\n"
                    + "    \"facing=south,theme=2\": { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 180 },\n"
                    + "    \"facing=east,theme=2\":  { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 90 },\n"
                    + "    \"facing=west,theme=2\":  { \"model\": \"aiothoroughbred:block/tote_board\", \"y\": 270 }\n"
                    + "  }\n}\n";

            String modelToteBoard = "{\n"
                    + "  \"parent\": \"minecraft:block/cube_all\",\n"
                    + "  \"textures\": {\n"
                    + "    \"all\": \"aiothoroughbred:block/tote_board\"\n"
                    + "  }\n"
                    + "}\n";

            String bsStarterConsole = "{\n  \"variants\": {\n"
                    + "    \"facing=north\": { \"model\": \"aiothoroughbred:block/starter_console\" },\n"
                    + "    \"facing=east\":  { \"model\": \"aiothoroughbred:block/starter_console\", \"y\": 90 },\n"
                    + "    \"facing=south\": { \"model\": \"aiothoroughbred:block/starter_console\", \"y\": 180 },\n"
                    + "    \"facing=west\":  { \"model\": \"aiothoroughbred:block/starter_console\", \"y\": 270 }\n"
                    + "  }\n}\n";

            String modelStarterConsole = "{\n"
                    + "  \"parent\": \"minecraft:block/orientable_with_bottom\",\n"
                    + "  \"textures\": {\n"
                    + "    \"particle\": \"aiothoroughbred:block/starter_console_front\",\n"
                    + "    \"top\": \"aiothoroughbred:block/starter_console_top\",\n"
                    + "    \"front\": \"aiothoroughbred:block/starter_console_front\",\n"
                    + "    \"side\": \"aiothoroughbred:block/starter_console_side\",\n"
                    + "    \"bottom\": \"aiothoroughbred:block/starter_console_bottom\"\n"
                    + "  }\n}\n";

            String bsRacingConsole = "{\n  \"variants\": {\n"
                    + "    \"facing=north\": { \"model\": \"aiothoroughbred:block/racing_console\" },\n"
                    + "    \"facing=east\":  { \"model\": \"aiothoroughbred:block/racing_console\", \"y\": 90 },\n"
                    + "    \"facing=south\": { \"model\": \"aiothoroughbred:block/racing_console\", \"y\": 180 },\n"
                    + "    \"facing=west\":  { \"model\": \"aiothoroughbred:block/racing_console\", \"y\": 270 }\n"
                    + "  }\n}\n";

            String modelRacingConsole = "{\n"
                    + "  \"parent\": \"minecraft:block/orientable_with_bottom\",\n"
                    + "  \"textures\": {\n"
                    + "    \"particle\": \"aiothoroughbred:block/racing_console_front\",\n"
                    + "    \"top\": \"aiothoroughbred:block/racing_console_top\",\n"
                    + "    \"front\": \"aiothoroughbred:block/racing_console_front\",\n"
                    + "    \"side\": \"aiothoroughbred:block/racing_console_side\",\n"
                    + "    \"bottom\": \"aiothoroughbred:block/racing_console_bottom\"\n"
                    + "  }\n}\n";

            String enUsJson = "{\n"
                    + "  \"aiothoroughbred.lang.code\": \"en_us\",\n"
                    + "  \"item.aiothoroughbred.thoroughbred_registry_form\": \"Thoroughbred Registration Form\",\n"
                    + "  \"item.aiothoroughbred.racing_crop\": \"Jockey's Racing Crop\",\n"
                    + "  \"item.aiothoroughbred.trainer_stopwatch\": \"Trainer's Breeze Stopwatch\",\n"
                    + "  \"item.aiothoroughbred.grooming_brush\": \"Equine Grooming Brush\",\n"
                    + "  \"item.aiothoroughbred.checkpoint_marker\": \"Track Checkpoint Wand ([M] Mode)\",\n"
                    + "  \"item.aiothoroughbred.semaphore_flag\": \"Steward's Semaphore Flag (Left-Click 3x)\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_frame\": \"Starting Gate Stall Frame\",\n"
                    + "  \"block.aiothoroughbred.green_starting_gate_frame\": \"Green Starting Gate Stall Frame\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_separator\": \"Starting Gate Stall Separator\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_number_sign\": \"Starting Gate Number Sign\",\n"
                    + "  \"block.aiothoroughbred.main_starting_gate\": \"Main Starting Gate\",\n"
                    + "  \"block.aiothoroughbred.rear_starting_gate\": \"Rear Starting Gate\",\n"
                    + "  \"block.aiothoroughbred.starter_console\": \"Starter's Gate Console\",\n"
                    + "  \"block.aiothoroughbred.racing_console\": \"Racecourse Steward Console\",\n"
                    + "  \"block.aiothoroughbred.tote_board\": \"Trackside Results Tote Board\"\n"
                    + "}\n";

            String zhTwJson = "{\n"
                    + "  \"aiothoroughbred.lang.code\": \"zh_tw\",\n"
                    + "  \"item.aiothoroughbred.thoroughbred_registry_form\": \"純血馬血統登記表\",\n"
                    + "  \"item.aiothoroughbred.racing_crop\": \"騎師競賽馬鞭\",\n"
                    + "  \"item.aiothoroughbred.trainer_stopwatch\": \"練馬師測速秒錶\",\n"
                    + "  \"item.aiothoroughbred.grooming_brush\": \"馬匹梳理刷\",\n"
                    + "  \"item.aiothoroughbred.checkpoint_marker\": \"賽道檢查點標記杖 ([M] 切換模式)\",\n"
                    + "  \"item.aiothoroughbred.semaphore_flag\": \"司閘員信號旗 (對空左鍵 3 次)\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_frame\": \"出閘廂白色支架\",\n"
                    + "  \"block.aiothoroughbred.green_starting_gate_frame\": \"出閘廂翠綠支架\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_separator\": \"出閘廂隔板\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_number_sign\": \"閘廂號碼牌\",\n"
                    + "  \"block.aiothoroughbred.main_starting_gate\": \"出閘廂前門\",\n"
                    + "  \"block.aiothoroughbred.rear_starting_gate\": \"出閘廂後門\",\n"
                    + "  \"block.aiothoroughbred.starter_console\": \"練習閘門控制台\",\n"
                    + "  \"block.aiothoroughbred.racing_console\": \"賽馬場賽事控制台\",\n"
                    + "  \"block.aiothoroughbred.tote_board\": \"賽馬場賽果顯示板\"\n"
                    + "}\n";

            String zhHkJson = zhTwJson.replace("\"zh_tw\"", "\"zh_hk\"");

            String zhCnJson = "{\n"
                    + "  \"aiothoroughbred.lang.code\": \"zh_cn\",\n"
                    + "  \"item.aiothoroughbred.thoroughbred_registry_form\": \"纯血马血统登记表\",\n"
                    + "  \"item.aiothoroughbred.racing_crop\": \"骑师竞赛马鞭\",\n"
                    + "  \"item.aiothoroughbred.trainer_stopwatch\": \"练马师测速秒表\",\n"
                    + "  \"item.aiothoroughbred.grooming_brush\": \"马匹梳理刷\",\n"
                    + "  \"item.aiothoroughbred.checkpoint_marker\": \"赛道检查点标记杖 ([M] 切换模式)\",\n"
                    + "  \"item.aiothoroughbred.semaphore_flag\": \"司闸员信号旗 (对空左键 3 次)\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_frame\": \"出闸厢白色支架\",\n"
                    + "  \"block.aiothoroughbred.green_starting_gate_frame\": \"出闸厢翠绿支架\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_separator\": \"出闸厢隔板\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_number_sign\": \"闸厢号码牌\",\n"
                    + "  \"block.aiothoroughbred.main_starting_gate\": \"出闸厢前门\",\n"
                    + "  \"block.aiothoroughbred.rear_starting_gate\": \"出闸厢后门\",\n"
                    + "  \"block.aiothoroughbred.starter_console\": \"练习闸门控制台\",\n"
                    + "  \"block.aiothoroughbred.racing_console\": \"赛马场赛事控制台\",\n"
                    + "  \"block.aiothoroughbred.tote_board\": \"赛马场赛果显示板\"\n"
                    + "}\n";

            String jaJpJson = "{\n"
                    + "  \"aiothoroughbred.lang.code\": \"ja_jp\",\n"
                    + "  \"item.aiothoroughbred.thoroughbred_registry_form\": \"サラブレッド血統登録書\",\n"
                    + "  \"item.aiothoroughbred.racing_crop\": \"ジョッキー鞭\",\n"
                    + "  \"item.aiothoroughbred.trainer_stopwatch\": \"調教師のストップウォッチ\",\n"
                    + "  \"item.aiothoroughbred.grooming_brush\": \"グルーミングブラシ\",\n"
                    + "  \"item.aiothoroughbred.checkpoint_marker\": \"コースチェックポイント杖 ([M] モード切替)\",\n"
                    + "  \"item.aiothoroughbred.semaphore_flag\": \"発走委員の赤旗 (空中左クリック3回)\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_frame\": \"スターティングゲート白フレーム\",\n"
                    + "  \"block.aiothoroughbred.green_starting_gate_frame\": \"スターティングゲート緑フレーム\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_separator\": \"ゲート内仕切りパネル\",\n"
                    + "  \"block.aiothoroughbred.starting_gate_number_sign\": \"ゲート番号標識\",\n"
                    + "  \"block.aiothoroughbred.main_starting_gate\": \"メインフロントゲート\",\n"
                    + "  \"block.aiothoroughbred.rear_starting_gate\": \"リアバックゲート\",\n"
                    + "  \"block.aiothoroughbred.starter_console\": \"練習ゲート操作盤\",\n"
                    + "  \"block.aiothoroughbred.racing_console\": \"競馬場運営コンソール\",\n"
                    + "  \"block.aiothoroughbred.tote_board\": \"レース着順掲示板\"\n"
                    + "}\n";

            for (java.io.File assetRoot : assetRoots) {
                writeStringFile(new java.io.File(assetRoot, "blockstates/green_starting_gate_frame.json"), bsGreenFrame);
                writeStringFile(new java.io.File(assetRoot, "blockstates/starting_gate_separator.json"), bsSeparator);
                writeStringFile(new java.io.File(assetRoot, "blockstates/starting_gate_number_sign.json"), bsSign);
                writeStringFile(new java.io.File(assetRoot, "blockstates/main_starting_gate.json"), bsMainGate);
                writeStringFile(new java.io.File(assetRoot, "blockstates/tote_board.json"), bsToteBoard);

                writeStringFile(new java.io.File(assetRoot, "models/block/green_starting_gate_frame.json"), modelGreenFrame);
                writeStringFile(new java.io.File(assetRoot, "models/block/starting_gate_separator.json"), modelSeparator);
                writeOrUpgradeBlockModelFile(new java.io.File(assetRoot, "models/block/tote_board.json"), modelToteBoard, "aiothoroughbred:block/tote_board");

                for (int n = 1; n <= 16; n++) {
                    String modelSignN = "{\n"
                            + "  \"parent\": \"minecraft:block/block\",\n"
                            + "  \"textures\": {\n"
                            + "    \"particle\": \"aiothoroughbred:block/gate_num_" + n + "\",\n"
                            + "    \"num\": \"aiothoroughbred:block/gate_num_" + n + "\",\n"
                            + "    \"frame\": \"aiothoroughbred:block/gate_frame_pillar\"\n"
                            + "  },\n"
                            + "  \"elements\": [\n"
                            + "    {\n"
                            + "      \"from\": [2, 0, 6],\n"
                            + "      \"to\": [14, 12, 10],\n"
                            + "      \"faces\": {\n"
                            + "        \"north\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#num\" },\n"
                            + "        \"south\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#num\" },\n"
                            + "        \"west\":  { \"uv\": [6, 4, 10, 16], \"texture\": \"#frame\" },\n"
                            + "        \"east\":  { \"uv\": [6, 4, 10, 16], \"texture\": \"#frame\" },\n"
                            + "        \"up\":    { \"uv\": [2, 6, 14, 10], \"texture\": \"#frame\" },\n"
                            + "        \"down\":  { \"uv\": [2, 6, 14, 10], \"texture\": \"#frame\" }\n"
                            + "      }\n"
                            + "    },\n"
                            + "    {\n"
                            + "      \"from\": [1, 0, 5.5],\n"
                            + "      \"to\": [2, 13, 10.5],\n"
                            + "      \"faces\": {\n"
                            + "        \"north\": { \"uv\": [1, 3, 2, 16], \"texture\": \"#frame\" },\n"
                            + "        \"south\": { \"uv\": [1, 3, 2, 16], \"texture\": \"#frame\" },\n"
                            + "        \"west\":  { \"uv\": [5, 3, 10, 16], \"texture\": \"#frame\" },\n"
                            + "        \"east\":  { \"uv\": [5, 3, 10, 16], \"texture\": \"#frame\" },\n"
                            + "        \"up\":    { \"uv\": [1, 5, 2, 10], \"texture\": \"#frame\" },\n"
                            + "        \"down\":  { \"uv\": [1, 5, 2, 10], \"texture\": \"#frame\" }\n"
                            + "      }\n"
                            + "    },\n"
                            + "    {\n"
                            + "      \"from\": [14, 0, 5.5],\n"
                            + "      \"to\": [15, 13, 10.5],\n"
                            + "      \"faces\": {\n"
                            + "        \"north\": { \"uv\": [14, 3, 15, 16], \"texture\": \"#frame\" },\n"
                            + "        \"south\": { \"uv\": [14, 3, 15, 16], \"texture\": \"#frame\" },\n"
                            + "        \"west\":  { \"uv\": [5, 3, 10, 16], \"texture\": \"#frame\" },\n"
                            + "        \"east\":  { \"uv\": [5, 3, 10, 16], \"texture\": \"#frame\" },\n"
                            + "        \"up\":    { \"uv\": [14, 5, 15, 10], \"texture\": \"#frame\" },\n"
                            + "        \"down\":  { \"uv\": [14, 5, 15, 10], \"texture\": \"#frame\" }\n"
                            + "      }\n"
                            + "    },\n"
                            + "    {\n"
                            + "      \"from\": [1, 12, 5.5],\n"
                            + "      \"to\": [15, 13, 10.5],\n"
                            + "      \"faces\": {\n"
                            + "        \"north\": { \"uv\": [1, 3, 15, 4], \"texture\": \"#frame\" },\n"
                            + "        \"south\": { \"uv\": [1, 3, 15, 4], \"texture\": \"#frame\" },\n"
                            + "        \"up\":    { \"uv\": [1, 5, 15, 10], \"texture\": \"#frame\" },\n"
                            + "        \"down\":  { \"uv\": [1, 5, 15, 10], \"texture\": \"#frame\" }\n"
                            + "      }\n"
                            + "    }\n"
                            + "  ],\n  " + displayJson + "\n}\n";
                    writeStringFile(new java.io.File(assetRoot, "models/block/gate_sign_" + n + ".json"), modelSignN);
                }

                writeStringFile(new java.io.File(assetRoot, "models/item/green_starting_gate_frame.json"),
                        "{\n  \"parent\": \"aiothoroughbred:block/green_starting_gate_frame\"\n}\n");
                writeStringFile(new java.io.File(assetRoot, "models/item/starting_gate_separator.json"),
                        "{\n  \"parent\": \"aiothoroughbred:block/starting_gate_separator\"\n}\n");
                writeStringFile(new java.io.File(assetRoot, "models/item/starting_gate_number_sign.json"),
                        "{\n  \"parent\": \"aiothoroughbred:block/gate_sign_1\"\n}\n");
                writeStringFile(new java.io.File(assetRoot, "models/item/tote_board.json"),
                        "{\n  \"parent\": \"aiothoroughbred:block/tote_board\"\n}\n");

                writeOrUpgradeItemModelFile(new java.io.File(assetRoot, "blockstates/starter_console.json"), bsStarterConsole);
                writeOrUpgradeBlockModelFile(new java.io.File(assetRoot, "models/block/starter_console.json"), modelStarterConsole, "aiothoroughbred:block/starter_console_front");
                writeOrUpgradeItemModelFile(new java.io.File(assetRoot, "models/item/starter_console.json"),
                        "{\n  \"parent\": \"aiothoroughbred:block/starter_console\"\n}\n");

                writeOrUpgradeItemModelFile(new java.io.File(assetRoot, "blockstates/racing_console.json"), bsRacingConsole);
                writeOrUpgradeBlockModelFile(new java.io.File(assetRoot, "models/block/racing_console.json"), modelRacingConsole, "aiothoroughbred:block/racing_console_front");
                writeOrUpgradeItemModelFile(new java.io.File(assetRoot, "models/item/racing_console.json"),
                        "{\n  \"parent\": \"aiothoroughbred:block/racing_console\"\n}\n");

                String[][] customItemModels = new String[][] {
                    { "thoroughbred_registry_form", "minecraft:item/generated" },
                    { "racing_crop",                "minecraft:item/handheld"  },
                    { "trainer_stopwatch",          "minecraft:item/generated" },
                    { "grooming_brush",             "minecraft:item/generated" },
                    { "checkpoint_marker",          "minecraft:item/handheld"  },
                    { "semaphore_flag",             "minecraft:item/handheld"  }
                };
                for (String[] im : customItemModels) {
                    java.io.File imFile = new java.io.File(assetRoot, "models/item/" + im[0] + ".json");
                    String imJson = "{\n"
                            + "  \"parent\": \"" + im[1] + "\",\n"
                            + "  \"textures\": {\n"
                            + "    \"layer0\": \"aiothoroughbred:item/" + im[0] + "\"\n"
                            + "  }\n"
                            + "}\n";
                    writeOrUpgradeItemModelFile(imFile, imJson);
                }

                writeOrUpgradeLangFile(new java.io.File(assetRoot, "lang/en_us.json"), enUsJson);
                writeOrUpgradeLangFile(new java.io.File(assetRoot, "lang/zh_tw.json"), zhTwJson);
                writeOrUpgradeLangFile(new java.io.File(assetRoot, "lang/zh_hk.json"), zhHkJson);
                writeOrUpgradeLangFile(new java.io.File(assetRoot, "lang/zh_cn.json"), zhCnJson);
                writeOrUpgradeLangFile(new java.io.File(assetRoot, "lang/ja_jp.json"), jaJpJson);

            }

            ensureSurvivalRecipesAndLootTablesInstalled(projectRoots);
            ThoroughbredData.ensureTranslationFilesInitialized();

            // Sync non-PNG resource files (models, blockstates, lang) from src/main/resources into build & runtime pack first
            for (java.io.File root : projectRoots) {
                java.io.File srcAssets = new java.io.File(root, "src/main/resources/assets/aiothoroughbred");
                if (srcAssets.isDirectory()) {
                    syncDirectoryTree(srcAssets, new java.io.File(root, "build/resources/main/assets/aiothoroughbred"));
                    if (new java.io.File(root, "out/production/resources").isDirectory()) {
                        syncDirectoryTree(srcAssets, new java.io.File(root, "out/production/resources/assets/aiothoroughbred"));
                    }
                    if (new java.io.File(root, "build/classes/java/main").isDirectory()) {
                        syncDirectoryTree(srcAssets, new java.io.File(root, "build/classes/java/main/assets/aiothoroughbred"));
                    }
                    try {
                        java.io.File runtimePackDir = getRuntimeResourcePackDir();
                        if (runtimePackDir != null) {
                            syncDirectoryTree(srcAssets, new java.io.File(runtimePackDir, "assets/aiothoroughbred"));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            List<java.io.File> textureSyncRoots = new ArrayList<>(projectRoots);
            try {
                java.nio.file.Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
                if (gameDir != null) {
                    java.io.File gd = gameDir.toFile().getCanonicalFile();
                    if (!textureSyncRoots.contains(gd)) {
                        textureSyncRoots.add(gd);
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                java.io.File runtimeDir = new java.io.File(".").getCanonicalFile();
                if (!textureSyncRoots.contains(runtimeDir)) {
                    textureSyncRoots.add(runtimeDir);
                }
            } catch (Exception ignored) {
            }

            // Resolve and sync all block & item textures across src/main/resources, aiothoroughbred_textures, build, and .runtime_pack,
            // strictly prioritizing user-saved textures over built-in Base64 defaults so user textures are NEVER overwritten!
            syncAllModTextures(projectRoots, textureSyncRoots, assetRoots, pngBase64Table, itemPngBase64Table);
        } catch (Exception e) {
            LOGGER.warn("Could not auto-install starting gate assets: {}", e.getMessage());
        }
    }

    private static void syncAllModTextures(
            List<java.io.File> projectRoots,
            List<java.io.File> textureSyncRoots,
            List<java.io.File> assetRoots,
            String[][] blockTable,
            String[][] itemTable
    ) {
        List<byte[]> allKnownDefaults = new ArrayList<>();
        for (String[] entry : blockTable) {
            try {
                allKnownDefaults.add(java.util.Base64.getDecoder().decode(entry[1]));
            } catch (Exception ignored) {
            }
        }
        for (String[] entry : itemTable) {
            try {
                allKnownDefaults.add(java.util.Base64.getDecoder().decode(entry[1]));
            } catch (Exception ignored) {
            }
        }

        // Support single-file starter_console.png or racing_console.png if the user saved a single texture
        java.io.File bestStarterSingle = findBestUserSingleTexture("starter_console.png", projectRoots, textureSyncRoots, assetRoots, allKnownDefaults);
        java.io.File bestRacingSingle = findBestUserSingleTexture("racing_console.png", projectRoots, textureSyncRoots, assetRoots, allKnownDefaults);

        for (String[] entry : blockTable) {
            String fileName = entry[0];
            byte[] defaultBytes = java.util.Base64.getDecoder().decode(entry[1]);
            java.io.File fallbackSingle = null;
            if (fileName.startsWith("starter_console_")) {
                fallbackSingle = bestStarterSingle;
            } else if (fileName.startsWith("racing_console_")) {
                fallbackSingle = bestRacingSingle;
            }
            resolveAndSyncSingleTextureEntry(
                    fileName,
                    "textures/block",
                    "aiothoroughbred_textures/blocks",
                    defaultBytes,
                    fallbackSingle,
                    projectRoots,
                    textureSyncRoots,
                    assetRoots,
                    allKnownDefaults
            );
        }

        for (String[] entry : itemTable) {
            String fileName = entry[0];
            byte[] defaultBytes = java.util.Base64.getDecoder().decode(entry[1]);
            resolveAndSyncSingleTextureEntry(
                    fileName,
                    "textures/item",
                    "aiothoroughbred_textures/items",
                    defaultBytes,
                    null,
                    projectRoots,
                    textureSyncRoots,
                    assetRoots,
                    allKnownDefaults
            );
        }

        // Also propagate any additional custom .png files the user placed in textures/block or aiothoroughbred_textures/blocks
        syncExtraCustomPngFiles("textures/block", "aiothoroughbred_textures/blocks", projectRoots, textureSyncRoots, assetRoots, allKnownDefaults);
        syncExtraCustomPngFiles("textures/item", "aiothoroughbred_textures/items", projectRoots, textureSyncRoots, assetRoots, allKnownDefaults);
    }

    private static java.io.File findBestUserSingleTexture(
            String fileName,
            List<java.io.File> projectRoots,
            List<java.io.File> textureSyncRoots,
            List<java.io.File> assetRoots,
            List<byte[]> allKnownDefaults
    ) {
        List<java.io.File> candidates = collectUniqueTextureLocations(fileName, "textures/block", "aiothoroughbred_textures/blocks", projectRoots, textureSyncRoots, assetRoots);
        java.io.File best = null;
        for (java.io.File c : candidates) {
            if (isValidPngFile(c) && !isBuiltInDefaultPng(c, null, allKnownDefaults)) {
                if (best == null || c.lastModified() > best.lastModified()) {
                    best = c;
                }
            }
        }
        return best;
    }

    private static List<java.io.File> collectUniqueTextureLocations(
            String fileName,
            String assetRelDir,
            String editableRelDir,
            List<java.io.File> projectRoots,
            List<java.io.File> textureSyncRoots,
            List<java.io.File> assetRoots
    ) {
        List<java.io.File> locations = new ArrayList<>();
        java.util.Set<String> seenCanon = new java.util.HashSet<>();
        // 1. Primary user-facing locations first: src/main/resources and aiothoroughbred_textures
        for (java.io.File root : projectRoots) {
            addUniqueLocation(new java.io.File(root, "src/main/resources/assets/aiothoroughbred/" + assetRelDir + "/" + fileName), locations, seenCanon);
        }
        for (java.io.File root : textureSyncRoots) {
            addUniqueLocation(new java.io.File(root, editableRelDir + "/" + fileName), locations, seenCanon);
        }
        // 2. All other assetRoots (build/resources/main, out/production/resources, .runtime_pack)
        for (java.io.File assetRoot : assetRoots) {
            addUniqueLocation(new java.io.File(assetRoot, assetRelDir + "/" + fileName), locations, seenCanon);
        }
        return locations;
    }

    private static void addUniqueLocation(java.io.File file, List<java.io.File> list, java.util.Set<String> seenCanon) {
        if (file == null) return;
        try {
            String canon = file.getCanonicalPath();
            if (seenCanon.add(canon)) {
                list.add(file);
            }
        } catch (Exception e) {
            String abs = file.getAbsolutePath();
            if (seenCanon.add(abs)) {
                list.add(file);
            }
        }
    }

    private static void resolveAndSyncSingleTextureEntry(
            String fileName,
            String assetRelDir,
            String editableRelDir,
            byte[] defaultBytes,
            java.io.File fallbackUserSingle,
            List<java.io.File> projectRoots,
            List<java.io.File> textureSyncRoots,
            List<java.io.File> assetRoots,
            List<byte[]> allKnownDefaults
    ) {
        List<java.io.File> locations = collectUniqueTextureLocations(fileName, assetRelDir, editableRelDir, projectRoots, textureSyncRoots, assetRoots);
        java.io.File bestUserFile = null;
        for (java.io.File loc : locations) {
            if (isValidPngFile(loc) && !isBuiltInDefaultPng(loc, defaultBytes, allKnownDefaults)) {
                if (bestUserFile == null || loc.lastModified() > bestUserFile.lastModified()) {
                    bestUserFile = loc;
                }
            }
        }
        if (bestUserFile == null && fallbackUserSingle != null && isValidPngFile(fallbackUserSingle)) {
            bestUserFile = fallbackUserSingle;
        }

        if (bestUserFile != null) {
            try {
                byte[] userBytes = java.nio.file.Files.readAllBytes(bestUserFile.toPath());
                java.nio.file.attribute.FileTime userTime = java.nio.file.Files.getLastModifiedTime(bestUserFile.toPath());
                for (java.io.File dst : locations) {
                    try {
                        boolean needsWrite = !dst.isFile();
                        if (!needsWrite) {
                            byte[] existingBytes = java.nio.file.Files.readAllBytes(dst.toPath());
                            needsWrite = !java.util.Arrays.equals(existingBytes, userBytes);
                        }
                        if (needsWrite) {
                            if (dst.getParentFile() != null) {
                                dst.getParentFile().mkdirs();
                            }
                            java.nio.file.Files.write(dst.toPath(), userBytes);
                            java.nio.file.Files.setLastModifiedTime(dst.toPath(), userTime);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            // No user-saved custom texture exists yet: only write built-in default where a valid PNG does not yet exist
            for (java.io.File dst : locations) {
                if (!isValidPngFile(dst)) {
                    try {
                        if (dst.getParentFile() != null) {
                            dst.getParentFile().mkdirs();
                        }
                        java.nio.file.Files.write(dst.toPath(), defaultBytes);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private static void syncExtraCustomPngFiles(
            String assetRelDir,
            String editableRelDir,
            List<java.io.File> projectRoots,
            List<java.io.File> textureSyncRoots,
            List<java.io.File> assetRoots,
            List<byte[]> allKnownDefaults
    ) {
        java.util.Set<String> customNames = new java.util.HashSet<>();
        for (java.io.File root : projectRoots) {
            collectPngNamesInDir(new java.io.File(root, "src/main/resources/assets/aiothoroughbred/" + assetRelDir), customNames);
        }
        for (java.io.File root : textureSyncRoots) {
            collectPngNamesInDir(new java.io.File(root, editableRelDir), customNames);
        }
        for (java.io.File assetRoot : assetRoots) {
            collectPngNamesInDir(new java.io.File(assetRoot, assetRelDir), customNames);
        }
        for (String name : customNames) {
            List<java.io.File> locations = collectUniqueTextureLocations(name, assetRelDir, editableRelDir, projectRoots, textureSyncRoots, assetRoots);
            java.io.File bestUserFile = null;
            for (java.io.File loc : locations) {
                if (isValidPngFile(loc) && !isBuiltInDefaultPng(loc, null, allKnownDefaults)) {
                    if (bestUserFile == null || loc.lastModified() > bestUserFile.lastModified()) {
                        bestUserFile = loc;
                    }
                }
            }
            if (bestUserFile != null) {
                try {
                    byte[] userBytes = java.nio.file.Files.readAllBytes(bestUserFile.toPath());
                    java.nio.file.attribute.FileTime userTime = java.nio.file.Files.getLastModifiedTime(bestUserFile.toPath());
                    for (java.io.File dst : locations) {
                        try {
                            if (!dst.isFile() || !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(dst.toPath()), userBytes)) {
                                if (dst.getParentFile() != null) {
                                    dst.getParentFile().mkdirs();
                                }
                                java.nio.file.Files.write(dst.toPath(), userBytes);
                                java.nio.file.Files.setLastModifiedTime(dst.toPath(), userTime);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void collectPngNamesInDir(java.io.File dir, java.util.Set<String> names) {
        if (dir == null || !dir.isDirectory()) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                names.add(f.getName());
            }
        }
    }

    private static boolean isBuiltInDefaultPng(java.io.File file, byte[] defaultBytes, List<byte[]> allKnownDefaults) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            byte[] actual = java.nio.file.Files.readAllBytes(file.toPath());
            if (defaultBytes != null && java.util.Arrays.equals(actual, defaultBytes)) {
                return true;
            }
            if (allKnownDefaults != null) {
                for (byte[] known : allKnownDefaults) {
                    if (known != null && java.util.Arrays.equals(actual, known)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void writeStringFile(java.io.File file, String content) {
        try {
            // Never overwrite an existing file so custom user edits to models, sizes, blockstates, or textures are always preserved!
            if (file.exists() && file.length() > 0) {
                return;
            }
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static boolean isValidPngFile(java.io.File file) {
        if (file == null || !file.isFile() || file.length() <= 24) {
            return false;
        }
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
            return img != null && img.getWidth() > 0 && img.getHeight() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void writeOrUpgradeBlockModelFile(java.io.File file, String content, String requiredTextureToken) {
        try {
            if (file.exists() && file.length() > 0) {
                String existing = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                // Preserve any existing model that already uses aiothoroughbred textures (never overwrite user edits!)
                if (existing.contains("aiothoroughbred:")) {
                    return;
                }
            }
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void writeOrUpgradeItemModelFile(java.io.File file, String content) {
        try {
            if (file.exists() && file.length() > 0) {
                String existing = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                if (existing.contains("aiothoroughbred:")) {
                    return;
                }
            }
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void writeOrUpgradeLangFile(java.io.File file, String content) {
        try {
            if (file.exists() && file.length() > 0) {
                String existing = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                if (existing.contains("block.aiothoroughbred.tote_board") && existing.contains("aiothoroughbred.lang.code")) {
                    return;
                }
            }
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    /**
     * Mirrors any user-edited models, blockstates, and lang files from `src/main/resources/assets/aiothoroughbred`
     * into `build/resources/main/assets/aiothoroughbred` (and `out/production/resources`) so edits in `src/main/resources`
     * always take effect immediately at runtime even if Gradle cached `processResources`.
     */
    private static void syncDirectoryTree(java.io.File srcDir, java.io.File dstDir) {
        if (srcDir == null || dstDir == null || !srcDir.isDirectory()) return;
        try {
            if (srcDir.getCanonicalPath().equals(dstDir.getCanonicalPath())) return;
        } catch (Exception ignored) {
        }
        java.io.File[] children = srcDir.listFiles();
        if (children == null) return;
        for (java.io.File child : children) {
            java.io.File target = new java.io.File(dstDir, child.getName());
            if (child.isDirectory()) {
                syncDirectoryTree(child, target);
            } else if (child.isFile() && child.length() > 0) {
                // Skip .png files here because syncAllModTextures resolves user vs default PNGs across all directories
                if (child.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    continue;
                }
                try {
                    boolean shouldCopy = !target.exists()
                            || !java.util.Arrays.equals(
                                    java.nio.file.Files.readAllBytes(child.toPath()),
                                    java.nio.file.Files.readAllBytes(target.toPath())
                            );
                    if (shouldCopy) {
                        if (target.getParentFile() != null) {
                            target.getParentFile().mkdirs();
                        }
                        java.nio.file.Files.copy(
                                child.toPath(),
                                target.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
                        );
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }


    /**
     * Automatically installs all 12 SHAPED Survival Crafting Recipes (data/aiothoroughbred/recipes/*.json)
     * and all 8 Block Loot Tables (data/aiothoroughbred/loot_tables/blocks/*.json) into both src/main/resources
     * and active runtime resource directories (build/resources/main, out/production/resources).
     * Strictly uses "minecraft:crafting_shaped" for every recipe (zero shapeless recipes).
     */
    private static void ensureSurvivalRecipesAndLootTablesInstalled(List<java.io.File> projectRoots) {
        try {
            List<java.io.File> dataRoots = new ArrayList<>();
            for (java.io.File root : projectRoots) {
                dataRoots.add(new java.io.File(root, "src/main/resources/data/aiothoroughbred"));
                dataRoots.add(new java.io.File(root, "build/resources/main/data/aiothoroughbred"));
                if (new java.io.File(root, "out/production/resources").isDirectory()) {
                    dataRoots.add(new java.io.File(root, "out/production/resources/data/aiothoroughbred"));
                }
                if (new java.io.File(root, "build/classes/java/main").isDirectory()) {
                    dataRoots.add(new java.io.File(root, "build/classes/java/main/data/aiothoroughbred"));
                }
            }

            Map<String, String> shapedRecipes = new HashMap<>();

            // 1. Racing Crop (Diagonal Shaped: Leather top-left [1], Stick center [5], Stick bottom-right [9])
            shapedRecipes.put("racing_crop.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"L  \",\n"
                    + "    \" S \",\n"
                    + "    \"  S\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"L\": { \"item\": \"minecraft:leather\" },\n"
                    + "    \"S\": { \"item\": \"minecraft:stick\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:racing_crop\", \"count\": 1 }\n"
                    + "}\n");

            // 2. Thoroughbred Registry Form (Shaped: Feather top, Gold Nugget + Paper + Gold Nugget middle, Ink Sac bottom)
            shapedRecipes.put("thoroughbred_registry_form.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \" F \",\n"
                    + "    \"GPG\",\n"
                    + "    \" I \"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"F\": { \"item\": \"minecraft:feather\" },\n"
                    + "    \"G\": { \"item\": \"minecraft:gold_nugget\" },\n"
                    + "    \"P\": { \"item\": \"minecraft:paper\" },\n"
                    + "    \"I\": { \"item\": \"minecraft:ink_sac\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:thoroughbred_registry_form\", \"count\": 1 }\n"
                    + "}\n");

            // 3. Trainer's Stopwatch (Shaped: Stone Button top, Gold Ingots flanking Clock middle, Redstone bottom)
            shapedRecipes.put("trainer_stopwatch.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \" B \",\n"
                    + "    \"GCG\",\n"
                    + "    \" R \"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"B\": { \"item\": \"minecraft:stone_button\" },\n"
                    + "    \"G\": { \"item\": \"minecraft:gold_ingot\" },\n"
                    + "    \"C\": { \"item\": \"minecraft:clock\" },\n"
                    + "    \"R\": { \"item\": \"minecraft:redstone\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:trainer_stopwatch\", \"count\": 1 }\n"
                    + "}\n");

            // 4. Grooming Brush (Shaped: 3 Wheat bristles top, 3 String binding middle, Wood Planks handle bottom)
            shapedRecipes.put("grooming_brush.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"WWW\",\n"
                    + "    \"SSS\",\n"
                    + "    \" P \"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"W\": { \"item\": \"minecraft:wheat\" },\n"
                    + "    \"S\": { \"item\": \"minecraft:string\" },\n"
                    + "    \"P\": { \"tag\": \"minecraft:planks\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:grooming_brush\", \"count\": 1 }\n"
                    + "}\n");

            // 5. Starting Gate Frame (White/Standard) x8
            shapedRecipes.put("starting_gate_frame.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"IWI\",\n"
                    + "    \"IBI\",\n"
                    + "    \"IWI\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" },\n"
                    + "    \"W\": { \"item\": \"minecraft:white_dye\" },\n"
                    + "    \"B\": { \"item\": \"minecraft:iron_bars\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:starting_gate_frame\", \"count\": 8 }\n"
                    + "}\n");

            // 6. Green Starting Gate Frame x8
            shapedRecipes.put("green_starting_gate_frame.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"IGI\",\n"
                    + "    \"IBI\",\n"
                    + "    \"IGI\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" },\n"
                    + "    \"G\": { \"item\": \"minecraft:green_dye\" },\n"
                    + "    \"B\": { \"item\": \"minecraft:iron_bars\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:green_starting_gate_frame\", \"count\": 8 }\n"
                    + "}\n");

            // 7. Starting Gate Separator x6
            shapedRecipes.put("starting_gate_separator.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"BBB\",\n"
                    + "    \"III\",\n"
                    + "    \"BBB\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"B\": { \"item\": \"minecraft:iron_bars\" },\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:starting_gate_separator\", \"count\": 6 }\n"
                    + "}\n");

            // 8. Starting Gate Number Sign x4
            shapedRecipes.put("starting_gate_number_sign.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"III\",\n"
                    + "    \"GSG\",\n"
                    + "    \"III\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_nugget\" },\n"
                    + "    \"G\": { \"item\": \"minecraft:glow_ink_sac\" },\n"
                    + "    \"S\": { \"tag\": \"minecraft:signs\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:starting_gate_number_sign\", \"count\": 4 }\n"
                    + "}\n");

            // 9. Main Starting Gate x2
            shapedRecipes.put("main_starting_gate.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"BIB\",\n"
                    + "    \"DRD\",\n"
                    + "    \"BIB\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"B\": { \"item\": \"minecraft:iron_bars\" },\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" },\n"
                    + "    \"D\": { \"item\": \"minecraft:iron_door\" },\n"
                    + "    \"R\": { \"item\": \"minecraft:redstone\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:main_starting_gate\", \"count\": 2 }\n"
                    + "}\n");

            // 10. Rear Starting Gate x2
            shapedRecipes.put("rear_starting_gate.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"B B\",\n"
                    + "    \"IDI\",\n"
                    + "    \"B B\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"B\": { \"item\": \"minecraft:iron_bars\" },\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" },\n"
                    + "    \"D\": { \"item\": \"minecraft:iron_door\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:rear_starting_gate\", \"count\": 2 }\n"
                    + "}\n");

            // 11. Starter Console x1
            shapedRecipes.put("starter_console.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"IRI\",\n"
                    + "    \"INI\",\n"
                    + "    \"SSS\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" },\n"
                    + "    \"R\": { \"item\": \"minecraft:redstone_torch\" },\n"
                    + "    \"N\": { \"item\": \"minecraft:note_block\" },\n"
                    + "    \"S\": { \"item\": \"minecraft:smooth_stone\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:starter_console\", \"count\": 1 }\n"
                    + "}\n");

            // 12. Racing Console x1
            shapedRecipes.put("racing_console.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"GCG\",\n"
                    + "    \"RNR\",\n"
                    + "    \"III\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"G\": { \"item\": \"minecraft:gold_ingot\" },\n"
                    + "    \"C\": { \"item\": \"minecraft:clock\" },\n"
                    + "    \"R\": { \"item\": \"minecraft:redstone_block\" },\n"
                    + "    \"N\": { \"item\": \"minecraft:note_block\" },\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_block\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:racing_console\", \"count\": 1 }\n"
                    + "}\n");

            // 13. Trackside Results Tote Board x4 (Multi-Block Screen Wall Panel)
            shapedRecipes.put("tote_board.json",
                    "{\n"
                    + "  \"type\": \"minecraft:crafting_shaped\",\n"
                    + "  \"pattern\": [\n"
                    + "    \"IGI\",\n"
                    + "    \"GLG\",\n"
                    + "    \"IRI\"\n"
                    + "  ],\n"
                    + "  \"key\": {\n"
                    + "    \"I\": { \"item\": \"minecraft:iron_ingot\" },\n"
                    + "    \"G\": { \"item\": \"minecraft:gold_nugget\" },\n"
                    + "    \"L\": { \"item\": \"minecraft:redstone_lamp\" },\n"
                    + "    \"R\": { \"item\": \"minecraft:redstone\" }\n"
                    + "  },\n"
                    + "  \"result\": { \"item\": \"aiothoroughbred:tote_board\", \"count\": 4 }\n"
                    + "}\n");

            String[] blockIds = {
                "starting_gate_frame",
                "green_starting_gate_frame",
                "starting_gate_separator",
                "starting_gate_number_sign",
                "main_starting_gate",
                "rear_starting_gate",
                "starter_console",
                "racing_console",
                "tote_board"
            };

            for (java.io.File dataRoot : dataRoots) {
                java.io.File recipesDir = new java.io.File(dataRoot, "recipes");
                java.io.File lootBlocksDir = new java.io.File(dataRoot, "loot_tables/blocks");
                recipesDir.mkdirs();
                lootBlocksDir.mkdirs();

                for (Map.Entry<String, String> entry : shapedRecipes.entrySet()) {
                    writeStringFile(new java.io.File(recipesDir, entry.getKey()), entry.getValue());
                }

                for (String blockId : blockIds) {
                    String lootJson = "{\n"
                            + "  \"type\": \"minecraft:block\",\n"
                            + "  \"pools\": [\n"
                            + "    {\n"
                            + "      \"rolls\": 1.0,\n"
                            + "      \"bonus_rolls\": 0.0,\n"
                            + "      \"entries\": [\n"
                            + "        {\n"
                            + "          \"type\": \"minecraft:item\",\n"
                            + "          \"name\": \"aiothoroughbred:" + blockId + "\"\n"
                            + "        }\n"
                            + "      ],\n"
                            + "      \"conditions\": [\n"
                            + "        {\n"
                            + "          \"condition\": \"minecraft:survives_explosion\"\n"
                            + "        }\n"
                            + "      ]\n"
                            + "    }\n"
                            + "  ]\n"
                            + "}\n";
                    writeStringFile(new java.io.File(lootBlocksDir, blockId + ".json"), lootJson);
                }
            }

            for (java.io.File root : projectRoots) {
                java.io.File srcData = new java.io.File(root, "src/main/resources/data/aiothoroughbred");
                if (srcData.isDirectory()) {
                    syncDirectoryTree(srcData, new java.io.File(root, "build/resources/main/data/aiothoroughbred"));
                    if (new java.io.File(root, "out/production/resources").isDirectory()) {
                        syncDirectoryTree(srcData, new java.io.File(root, "out/production/resources/data/aiothoroughbred"));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not auto-install survival recipes/loot tables: {}", e.getMessage());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            RacingManager.getFanfareFolderPath();
            RacingManager.getMusicFolderPath();
            RacingManager.getTexturesFolderPath();
        });
        LOGGER.info("All-In-One Thoroughbred Mod initialized successfully!");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.THOROUGHBRED_REGISTRY_FORM);
            event.accept(ModItems.RACING_CROP);
            event.accept(ModItems.TRAINER_STOPWATCH);
            event.accept(ModItems.GROOMING_BRUSH);
            event.accept(ModItems.CHECKPOINT_MARKER);
            event.accept(ModItems.SEMAPHORE_FLAG);
            event.accept(ModBlocks.STARTING_GATE_FRAME.get());
            event.accept(ModBlocks.GREEN_STARTING_GATE_FRAME.get());
            event.accept(ModBlocks.STARTING_GATE_SEPARATOR.get());
            event.accept(ModBlocks.STARTING_GATE_NUMBER_SIGN.get());
            event.accept(ModBlocks.MAIN_STARTING_GATE.get());
            event.accept(ModBlocks.REAR_STARTING_GATE.get());
            event.accept(ModBlocks.STARTER_CONSOLE.get());
            event.accept(ModBlocks.RACING_CONSOLE.get());
            event.accept(ModBlocks.TOTE_BOARD.get());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.onRegisterCommands(event);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        StartingGateManager.onServerTick(event);
        RacingManager.onServerTick(event);
        ModCommands.onServerTick(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
            StudbookSavedData studbook = StudbookSavedData.get(serverLevel);
            boolean canConfigure = serverPlayer.hasPermissions(2) || serverLevel.getServer().isSingleplayer();
            if (!studbook.isTimeFlowConfigured() && canConfigure) {
                ModNetwork.sendOpenTimeFlowScreen(
                        serverPlayer,
                        studbook.getTimeFlowMode(),
                        studbook.getCustomEquineYearDays(),
                        true
                );
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        ThoroughbredRunEngine.onPlayerTick(event);
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        ThoroughbredRunEngine.onLivingTick(event);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        ThoroughbredRunEngine.onAttackEntity(event);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ThoroughbredRunEngine.onLeftClickBlock(event);
    }

    /**
     * Claimed Horse Immunity to Player Attacks (/tb claim):
     * If a Thoroughbred is claimed by an owner, it is completely immune to any player attacks!
     */
    /**
     * Prevents vanilla BreedGoal from spawning an instant vanilla baby horse when two registered Thoroughbreds
     * are in Love Mode during the /tb breed courtship ritual (since gestation starts after the ritual instead!).
     */
    @SubscribeEvent
    public void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if ((event.getParentA() instanceof Horse hA && ThoroughbredData.isThoroughbred(hA))
                || (event.getParentB() instanceof Horse hB && ThoroughbredData.isThoroughbred(hB))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player attacker) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            if (data.hasOwner()) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && attacker instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§c" + data.getName() + " is claimed by " + data.getOwnerName() + " and is immune to player attacks!"), true);
                }
            }
        }
    }

    /**
     * Hurting a Thoroughbred:
     * - Bond ONLY drops (-3.0) and Mood plummets (-18.0) when the injury is directly caused by a Player!
     * - Natural/environmental injuries that aren't directly the player's fault (falling from heights, natural fire, etc.)
     *   do NOT lower the horse's Bond with its owner!
     */
    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || horse.level().isClientSide) {
            return;
        }
        if (!ThoroughbredData.isThoroughbred(horse)) {
            return;
        }
        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        if (data.hasAilment(ThoroughbredData.AILMENT_COLIC)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || horse.level().isClientSide) {
            return;
        }
        if (!ThoroughbredData.isThoroughbred(horse)) {
            return;
        }
        if (horse.getPersistentData().getBoolean("AIOColicTick")) {
            return;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        boolean causedByPlayer = (event.getSource().getEntity() instanceof Player)
                || (event.getSource().getDirectEntity() instanceof Player);

        if (data.hasOwner() && causedByPlayer) {
            event.setCanceled(true);
            return;
        }

        if (causedByPlayer) {
            data.setMood(data.getMood() - 18.0);
            data.setBond(data.getBond() - 3.0);
            data.saveToHorse(horse);

            if (horse.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        horse.getX(), horse.getY() + 1.4, horse.getZ(),
                        6, 0.4, 0.4, 0.4, 0.05);
            }

            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                attacker.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "§c%s's Mood plummeted! (%s: %.0f | Bond: %.1f%%)",
                        data.getName(), data.getFormattedMoodTier(), data.getMood(), data.getBond())), true);
            }
        } else {
            data.setMood(data.getMood() - 4.0);
            data.saveToHorse(horse);
        }
    }

    /**
     * Automatically logs any Thoroughbred's death to StudbookSavedData for `/tb deaths` GUI and `/tb revive [UUID]`!
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || horse.level().isClientSide) {
            return;
        }
        if (!ThoroughbredData.isThoroughbred(horse)) {
            return;
        }
        if (horse.getPersistentData().getBoolean("AIOThoroughbredPurged")) {
            return;
        }

        if (horse.level() instanceof ServerLevel serverLevel) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            String reason = event.getSource().getLocalizedDeathMessage(horse).getString();
            StudbookSavedData studbook = StudbookSavedData.get(serverLevel);
            studbook.recordHorseDeath(horse, data, reason, false);
        }
    }

    /**
     * Enforces `/tb lock` so other players cannot mount a locked horse!
     */
    @SubscribeEvent
    public void onEntityMount(EntityMountEvent event) {
        if (!event.isMounting()) {
            if (event.getEntityBeingMounted() instanceof Horse horse
                    && event.getEntityMounting() instanceof Player player
                    && !horse.level().isClientSide
                    && horse.level() instanceof ServerLevel sl
                    && ThoroughbredData.isThoroughbred(horse)) {
                ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                if (data.isWearingRaceSilk()) {
                    data.setWearingRaceSilk(false);
                    data.saveToHorse(horse);
                }
                RacingManager.handleJockeyDismountInRace(sl, horse, player);
            }
            return;
        }
        if (event.getEntityBeingMounted() instanceof Horse horse
                && event.getEntityMounting() instanceof Player player
                && ThoroughbredData.isThoroughbred(horse)) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            if (data.isAtDeathsDoor()) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§6§l[Death's Door] §e" + data.getName() + " is resting peacefully on the floor and cannot be mounted!"), true);
                }
                return;
            }
            if (data.isFoal()) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "§e[Growing Foal (0yo)] §f%s is a Foal (%.0f%% to Yearling, %.1f days left) — minimum riding and training age is 2 Equine Years old!",
                            data.getName(), data.getFoalGrowthProgressRatio() * 100.0, data.getFoalGrowthDaysRemaining())), true);
                }
                return;
            }
            if (data.isYearling()) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "§e[Growing Yearling (1yo)] §f%s is a Yearling (%.0f%% to 2yo, %.1f days left) — minimum riding and training age is 2 Equine Years old!",
                            data.getName(), data.getYearlingGrowthProgressRatio() * 100.0, data.getYearlingGrowthDaysRemaining())), true);
                }
                return;
            }
            if (data.hasPoorPrognosis()) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§4§l[" + ThoroughbredData.tr("Poor Prognosis", "預後不良", "预后不良", "予後不良") + "] §c" + data.getName() + " " + ThoroughbredData.tr("is lying down in critical condition and cannot be mounted! (Transport via Lead, Boat, or Minecart)", "處於預後不良危重狀態臥地，無法騎乘！（請使用韁繩牽引、乘船或礦車運送）", "处于预后不良危重状态卧地，无法骑乘！（请使用缰绳牵引、乘船或矿车运送）", "は予後不良の重体で横たわっており、騎乗できません！（リード、ボート、またはトロッコで移動させてください）")), true);
                }
                return;
            }
            if (data.hasActiveInjury() || data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && player instanceof ServerPlayer sp) {
                    String reason = data.hasActiveInjury() ? (data.getActiveInjury() + " & Lameness") : "Lameness";
                    sp.displayClientMessage(Component.literal(
                            "§c§l[Cannot Mount] §e" + data.getName() + " is suffering from " + reason + " and cannot be mounted!"), true);
                }
                return;
            }
            if (data.isLocked() && !data.isOwnedBy(player.getUUID())) {
                event.setCanceled(true);
                if (!horse.level().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§c" + data.getName() + " is locked by its owner (" + data.getOwnerName() + ")!"), true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getItemStack().is(ModItems.TRAINER_STOPWATCH.get())) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.UseStopwatchPacket());
        } else if (event.getItemStack().is(ModItems.SEMAPHORE_FLAG.get())) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.WaveSemaphoreFlagPacket());
        } else if (event.getItemStack().is(ModItems.CHECKPOINT_MARKER.get()) && event.getEntity().isShiftKeyDown()) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.MarkerUndoPacket());
        }
    }

    public static void handleServerStopwatchClick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = player.level().getGameTime();
        StopwatchSession session = STOPWATCH_SESSIONS.get(uuid);

        if (session == null) {
            StopwatchSession newSession = new StopwatchSession();
            newSession.startTick = now;
            newSession.startX = player.getX();
            newSession.startZ = player.getZ();

            if (player.getVehicle() instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                newSession.startOdometerMeters = state.distanceRun;
            }

            STOPWATCH_SESSIONS.put(uuid, newSession);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
            player.displayClientMessage(Component.literal("§e⏱ [Trainer Stopwatch Started] §7Click again to stop."), true);
        } else {
            STOPWATCH_SESSIONS.remove(uuid);
            long elapsedTicks = Math.max(1, now - session.startTick);
            double totalSec = elapsedTicks / 20.0;
            int mins = (int) (totalSec / 60);
            double secs = totalSec % 60.0;

            double distanceMeters;
            if (player.getVehicle() instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                distanceMeters = Math.max(0.0, state.distanceRun - session.startOdometerMeters);
            } else {
                double dx = player.getX() - session.startX;
                double dz = player.getZ() - session.startZ;
                distanceMeters = Math.sqrt(dx * dx + dz * dz);
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 1.5F);

            String chatLog = String.format(Locale.ROOT,
                    "§6§l[Trainer Stopwatch] §fElapsed Time: §e%02d:%05.2f §7| §fDistance: §b%.1fm",
                    mins, secs, distanceMeters);
            player.sendSystemMessage(Component.literal(chatLog));
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Horse horse)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldStack = event.getItemStack();
        boolean holdingRegistryForm = heldStack.is(ModItems.THOROUGHBRED_REGISTRY_FORM.get());
        boolean holdingNameTag = heldStack.is(Items.NAME_TAG);
        boolean isAlreadyThoroughbred = ThoroughbredData.isThoroughbred(horse);

        if (isAlreadyThoroughbred) {
            // Name Tag Restriction: Only Foals, Yearlings, and unregistered horses can be renamed with a Name Tag!
            if (holdingNameTag) {
                ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                event.setCanceled(true);
                if (!data.isUnderTwoEquineYears() && !horse.isBaby()) {
                    event.setCancellationResult(InteractionResult.FAIL);
                    if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal(
                                "§c[Studbook Locked] Name Tags cannot be used on registered adult Thoroughbreds! (Only Foals, Yearlings, and unregistered horses can use Name Tags.)"), true);
                        sp.sendSystemMessage(Component.literal(
                                "§c[Studbook Locked] §e" + data.getName() + " §cis an adult registered Thoroughbred and cannot be renamed with a Name Tag!"));
                    }
                    return;
                }
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp && event.getLevel() instanceof ServerLevel sl) {
                    if (!heldStack.hasCustomHoverName()) {
                        sp.displayClientMessage(Component.literal("§cRename the Name Tag at an Anvil first before using it!"), true);
                        return;
                    }
                    if (data.hasOwner() && !data.isOwnedBy(player.getUUID()) && !sp.hasPermissions(2)) {
                        sp.displayClientMessage(Component.literal("§cOnly " + data.getOwnerName() + " can rename this " + (data.isFoal() ? "Foal" : "Yearling") + "!"), true);
                        return;
                    }
                    String newTagName = heldStack.getHoverName().getString().trim();
                    if (newTagName.isEmpty()) {
                        return;
                    }
                    StudbookSavedData studbook = StudbookSavedData.get(sl);
                    if (!newTagName.equalsIgnoreCase(data.getName()) && studbook.isNameTaken(newTagName)) {
                        sp.sendSystemMessage(Component.literal("§cAnother horse in the Studbook already has the name '§e" + newTagName + "§c'!"));
                        return;
                    }
                    String oldName = data.getName();
                    studbook.renameHorseRecord(oldName, newTagName);
                    data.setName(newTagName);
                    data.saveToHorse(horse);
                    horse.setCustomName(Component.literal(newTagName));
                    studbook.updateHorseRecord(horse, data);
                    studbook.archiveHorsePedigree(data);
                    if (!player.getAbilities().instabuild) {
                        heldStack.shrink(1);
                    }
                    sp.sendSystemMessage(Component.literal("§a[Name Tag] Renamed " + (data.isFoal() ? "Foal" : "Yearling") + " §e" + oldName + " §ato §6" + newTagName + "§a!"));
                }
                return;
            }

            if (event.getHand() != InteractionHand.MAIN_HAND && !holdingRegistryForm) {
                return;
            }

            // Step 19: Right-clicking a Thoroughbred with Shears opens the Gelding Warning HUD!
            if (!player.isShiftKeyDown() && heldStack.is(Items.SHEARS)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                    ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                    if ("Female".equalsIgnoreCase(data.getGender())) {
                        sp.sendSystemMessage(Component.literal("§cYou cannot spay a filly/mare!"));
                        return;
                    }
                    if (data.isGelding()) {
                        sp.sendSystemMessage(Component.literal("§c" + data.getName() + " has already been gelded!"));
                        return;
                    }
                    StudbookSavedData studbook = StudbookSavedData.get(sl);
                    if (studbook.isGeldingAdminOnly() && !sp.hasPermissions(2)) {
                        sp.sendSystemMessage(Component.literal("§cGelding is restricted to server admins on this server!"));
                        return;
                    }
                    if (!data.isOwnedBy(sp.getUUID()) && !sp.hasPermissions(2)) {
                        sp.sendSystemMessage(Component.literal("§cThat stallion isn't yours to geld!"));
                        return;
                    }
                    ModNetwork.sendOpenGeldingScreen(sp, horse.getId(), data.getName());
                }
                return;
            }

            if (!player.isShiftKeyDown() && handleThoroughbredCareInteraction(event, player, horse, heldStack)) {
                return;
            }

            if (player.isShiftKeyDown() || holdingRegistryForm) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);

                if (!event.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                    if (holdingRegistryForm && !player.isShiftKeyDown()) {
                        if (data.isUnderTwoEquineYears() || horse.isBaby()) {
                            String stage = data.isFoal() ? "Foal (under 1 Equine Year old)" : "Yearling (1 Equine Year old)";
                            serverPlayer.displayClientMessage(Component.literal(
                                    "§c[Too Young to Register] " + data.getName() + " is a " + stage + " — must reach 2 Equine Years old!"), true);
                            serverPlayer.sendSystemMessage(Component.literal(
                                    "§c[Too Young to Register] §e" + data.getName() + " §cis still a §6" + stage
                                    + "§c! The Thoroughbred Registry Form can only be used once a horse reaches §a2 Equine Years old§c."));
                            return;
                        }
                        if (data.isBredFoalPendingName()) {
                            if (serverPlayer.level() instanceof ServerLevel sl) {
                                StudbookSavedData studbook = StudbookSavedData.get(sl);
                                if (!studbook.isTimeFlowConfigured()) {
                                    serverPlayer.displayClientMessage(
                                            Component.literal("§cYou cannot register horses until the World Equine Time Flow has been chosen! Use /tb timeflow first."),
                                            true
                                    );
                                    serverPlayer.sendSystemMessage(
                                            Component.literal("§cYou cannot register horses until the World Equine Time Flow has been chosen! Use /tb timeflow first.")
                                    );
                                    return;
                                }
                                if (!studbook.isRegisteredHorseOwner(serverPlayer.getUUID())) {
                                    serverPlayer.displayClientMessage(
                                            Component.literal("§cYou are not an official horse owner yet! Register via /tb owner to become one first!"),
                                            true
                                    );
                                    serverPlayer.sendSystemMessage(
                                            Component.literal("§cYou are not an official horse owner yet! Register via /tb owner to become one first!")
                                    );
                                    return;
                                }
                            }
                            // Open RegistrationScreen to officially name this 2yo bred Thoroughbred (preserving its bred genetics & pedigree!)
                            ModNetwork.sendOpenScreen(serverPlayer, horse.getId(), false, data.toNBT());
                            return;
                        }
                    }
                    ModNetwork.sendOpenScreen(serverPlayer, horse.getId(), true, data.toNBT());
                }
                return;
            }

            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            if (data.isFoal()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "§e[Growing Foal (0yo)] §f%s is a Foal (%.0f%% to Yearling, %.1f days left) — minimum riding and training age is 2 Equine Years old!",
                            data.getName(), data.getFoalGrowthProgressRatio() * 100.0, data.getFoalGrowthDaysRemaining())), true);
                }
                return;
            }
            if (data.isYearling()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "§e[Growing Yearling (1yo)] §f%s is a Yearling (%.0f%% to 2yo, %.1f days left) — minimum riding and training age is 2 Equine Years old!",
                            data.getName(), data.getYearlingGrowthProgressRatio() * 100.0, data.getYearlingGrowthDaysRemaining())), true);
                }
                return;
            }
            if (data.isAtDeathsDoor()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§6§l[Death's Door] §e" + data.getName() + " is resting peacefully on the floor and cannot be mounted!"), true);
                }
                return;
            }
            if (data.hasPoorPrognosis()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§4§l[" + ThoroughbredData.tr("Poor Prognosis", "預後不良", "预后不良", "予後不良") + "] §c" + data.getName() + " " + ThoroughbredData.tr("is lying down in critical condition and cannot be mounted!", "處於預後不良危重狀態臥地，無法騎乘！", "处于预后不良危重状态卧地，无法骑乘！", "は予後不良の重体で横たわっており、騎乗できません！")), true);
                }
                return;
            }
            if (data.hasActiveInjury() || data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                    String reason = data.hasActiveInjury() ? (data.getActiveInjury() + " & Lameness") : "Lameness";
                    sp.displayClientMessage(Component.literal(
                            "§c§l[Cannot Mount] §e" + data.getName() + " is suffering from " + reason + " and cannot be mounted!"), true);
                }
                return;
            }
            if (data.isLocked() && !data.isOwnedBy(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal(
                            "§c" + data.getName() + " is locked by its owner (" + data.getOwnerName() + ")!"), true);
                }
            }
            return;
        }

        if (holdingRegistryForm) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            if (!event.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (horse.isBaby()) {
                    serverPlayer.displayClientMessage(
                            Component.literal("§cThis foal is too young! Horses must reach 2 Equine Years old (adult) before using a Thoroughbred Registry Form."),
                            true
                    );
                    serverPlayer.sendSystemMessage(
                            Component.literal("§c[Too Young to Register] Horses must reach at least 2 Equine Years old before using a Thoroughbred Registry Form!"));
                    return;
                }
                if (!horse.isTamed()) {
                    serverPlayer.displayClientMessage(
                            Component.literal("§cYou must tame this horse before registering it as a Thoroughbred!"),
                            true
                    );
                    return;
                }
                if (serverPlayer.level() instanceof ServerLevel sl) {
                    StudbookSavedData studbook = StudbookSavedData.get(sl);
                    if (!studbook.isTimeFlowConfigured()) {
                        serverPlayer.displayClientMessage(
                                Component.literal("§cYou cannot register horses until the World Equine Time Flow has been chosen! Use /tb timeflow first."),
                                true
                        );
                        serverPlayer.sendSystemMessage(
                                Component.literal("§cYou cannot register horses until the World Equine Time Flow has been chosen! Use /tb timeflow first.")
                        );
                        return;
                    }
                    if (!studbook.isRegisteredHorseOwner(serverPlayer.getUUID())) {
                        serverPlayer.displayClientMessage(
                                Component.literal("§cYou are not an official horse owner yet! Register via /tb owner to become one first!"),
                                true
                        );
                        serverPlayer.sendSystemMessage(
                                Component.literal("§cYou are not an official horse owner yet! Register via /tb owner to become one first!")
                        );
                        return;
                    }
                }
                ModNetwork.sendOpenScreen(serverPlayer, horse.getId(), false, new CompoundTag());
            }
        }
    }

    private boolean handleThoroughbredCareInteraction(PlayerInteractEvent.EntityInteract event, Player player, Horse horse, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        double hungerGain = 0.0;
        double thirstGain = 0.0;
        double fatigueRelief = 0.0;
        double bondGain = 0.0;
        double moodGain = 0.0;
        boolean isWaterBucket = false;
        boolean isBrush = false;
        boolean isEnchantedApple = false;
        boolean isGoldenApple = false;
        boolean isHoneyBottle = false;
        boolean isColdTherapy = false;
        boolean isSolidFood = false;

        if (stack.is(Items.HAY_BLOCK)) {
            hungerGain = 60.0;
            fatigueRelief = 12.0;
            bondGain = 0.8;
            moodGain = 2.5;
            isSolidFood = true;
        } else if (stack.is(Items.CARROT)) {
            hungerGain = 25.0;
            fatigueRelief = 6.0;
            bondGain = 0.5;
            moodGain = 2.5;
            isSolidFood = true;
        } else if (stack.is(Items.APPLE)) {
            hungerGain = 22.0;
            thirstGain = 8.0;
            fatigueRelief = 6.0;
            bondGain = 0.5;
            moodGain = 3.0;
            isSolidFood = true;
        } else if (stack.is(Items.GOLDEN_CARROT)) {
            hungerGain = 50.0;
            fatigueRelief = 20.0;
            bondGain = 1.2;
            moodGain = 6.0;
            isSolidFood = true;
        } else if (stack.is(Items.GOLDEN_APPLE)) {
            isGoldenApple = true;
            hungerGain = 80.0;
            thirstGain = 20.0;
            fatigueRelief = 40.0;
            bondGain = 2.0;
            moodGain = 12.0;
        } else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            isEnchantedApple = true;
            hungerGain = 100.0;
            thirstGain = 100.0;
            fatigueRelief = 60.0;
            bondGain = 5.0;
            moodGain = 35.0;
        } else if (stack.is(Items.HONEY_BOTTLE)) {
            isHoneyBottle = true;
            hungerGain = 20.0;
            thirstGain = 25.0;
            fatigueRelief = 15.0;
            bondGain = 1.0;
            moodGain = 5.0;
        } else if (stack.is(Items.ICE) || stack.is(Items.PACKED_ICE) || stack.is(Items.BLUE_ICE) || stack.is(Items.SNOWBALL)) {
            isColdTherapy = true;
            fatigueRelief = 14.0;
            bondGain = 0.5;
            moodGain = 2.5;
        } else if (stack.is(Items.SUGAR)) {
            hungerGain = 10.0;
            fatigueRelief = 3.0;
            bondGain = 0.2;
            moodGain = 4.0;
            isSolidFood = true;
        } else if (stack.is(Items.WHEAT)) {
            hungerGain = 16.0;
            fatigueRelief = 4.0;
            bondGain = 0.3;
            moodGain = 1.5;
            isSolidFood = true;
        } else if (stack.is(Items.WATER_BUCKET)) {
            thirstGain = 70.0;
            fatigueRelief = 10.0;
            bondGain = 0.5;
            moodGain = 2.0;
            isWaterBucket = true;
        } else if (stack.is(ModItems.GROOMING_BRUSH.get())) {
            isBrush = true;
        } else {
            return false;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (event.getLevel().isClientSide) {
            return true;
        }

        ServerPlayer serverPlayer = (player instanceof ServerPlayer sp) ? sp : null;
        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);

        state.trainingRefusalActive = false;
        state.rearVisualTicks = 0;
        horse.setStanding(false);

        if (data.isOwnedBy(player.getUUID())) {
            data.setLastOwnerInteractionMcDay(horse.level().getDayTime() / 24000L);
            if (data.hasPersonality(ThoroughbredData.PERS_CLINGY)) {
                bondGain *= 1.30;
            }
        }
        if ((isSolidFood || isGoldenApple || isEnchantedApple || isHoneyBottle) && data.hasPersonality(ThoroughbredData.PERS_GLUTTON)) {
            moodGain *= 1.35;
            bondGain *= 1.35;
        }

        // Step 17: Fever & Colic cause the horse to refuse solid food (still accepts Water, Honey Bottle, Golden/Enchanted Apple, Ice)
        if (isSolidFood && data.refusesSolidFood()) {
            String cond = data.hasAilment(ThoroughbredData.AILMENT_FEVER) ? "a Fever" : "Colic";
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 0.9F, 1.1F);
            if (horse.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SMOKE, horse.getX(), horse.getY() + 1.3, horse.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            }
            if (serverPlayer != null) {
                serverPlayer.displayClientMessage(Component.literal(
                        "§c" + data.getName() + " has " + cond + " and refuses solid food! (Try Water, Honey Bottle, Golden Apple, or /tb vet)"), true);
            }
            return true;
        }

        if (isEnchantedApple) {
            // Cure curable ailments (Laryngeal Hemiplegia & Atrial Fibrillation are in-race only; Unstable Legs clears via Fatigue drop; Overweight requires exercise!)
            boolean curedFever = data.removeAilment(ThoroughbredData.AILMENT_FEVER);
            boolean curedColic = data.removeAilment(ThoroughbredData.AILMENT_COLIC);
            boolean curedEiph = data.removeAilment(ThoroughbredData.AILMENT_EIPH);
            boolean curedLaminitis = data.removeAilment(ThoroughbredData.AILMENT_LAMINITIS);
            boolean curedStandaloneLameness = false;
            if (!data.hasActiveInjury() && !data.hasPoorPrognosis()) {
                curedStandaloneLameness = data.removeAilment(ThoroughbredData.AILMENT_LAMENESS);
            }

            String medicalNote = "";
            if (data.hasPoorPrognosis()) {
                data.setPoorPrognosisAppleImmunity(true);
                data.setTriedToSaveWithApples(true);
                medicalNote = " §d§l[Next Day Death Roll Nullified: 99% Live / 1% Miracle!]";
                if (curedLaminitis) {
                    medicalNote += " §a[Cured Laminitis!]";
                }
            } else if (data.hasActiveInjury()) {
                double newDays = Math.max(0.25, data.getInjuryDaysRemaining() * 0.25);
                data.setInjuryDaysRemaining(newDays);
                medicalNote = String.format(Locale.ROOT, " §a[Accelerated %s recovery to %.1f MC days!]",
                        data.getActiveInjury(), newDays);
            } else if (curedFever || curedColic || curedEiph || curedLaminitis || curedStandaloneLameness) {
                medicalNote = " §a[Cured active ailments!]";
            }

            // Spam-feeding Enchanted Golden Apples while already full triggers Overweight!
            boolean triggeredOverweight = checkAndApplyOverweightFromFeeding(data, state);

            data.setEnchantedAppleBlessing24Hours();
            data.setHunger(100.0);
            data.setThirst(100.0);
            data.setFatigue(Math.max(0.0, data.getFatigue() - fatigueRelief)); // Dropping below 80% automatically clears Unstable Legs!
            data.setBond(data.getBond() + bondGain);
            data.setMood(data.getMood() + moodGain);
            data.saveToHorse(horse);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            horse.heal(20.0F);
            horse.resetLove();

            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
            if (horse.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        horse.getX(), horse.getY() + 1.2, horse.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            }
            if (serverPlayer != null) {
                serverPlayer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§6§l[Golden Blessing] §e%s has been blessed! Mood: %s (%.0f) | FTG: %.0f%%%s",
                        data.getName(), data.getFormattedMoodTier(), data.getMood(), data.getFatigue(), medicalNote)));
                if (triggeredOverweight) {
                    sendOverweightWarningMessage(serverPlayer, data);
                }
            }
            return true;
        }

        if (isGoldenApple) {
            boolean curedFever = data.removeAilment(ThoroughbredData.AILMENT_FEVER);
            boolean curedColic = data.removeAilment(ThoroughbredData.AILMENT_COLIC);
            boolean curedEiph = data.removeAilment(ThoroughbredData.AILMENT_EIPH);
            boolean curedLaminitis = data.removeAilment(ThoroughbredData.AILMENT_LAMINITIS);
            boolean curedStandaloneLameness = false;
            if (!data.hasActiveInjury() && !data.hasPoorPrognosis()) {
                curedStandaloneLameness = data.removeAilment(ThoroughbredData.AILMENT_LAMENESS);
            }
            boolean curedAny = curedFever || curedColic || curedEiph || curedLaminitis || curedStandaloneLameness;

            String medicalNote = "";
            if (data.hasPoorPrognosis()) {
                data.setTriedToSaveWithApples(true);
                if (data.hasPoorPrognosisAppleImmunity() || data.isPoorPrognosisGoldenAppleActive()) {
                    int activeDeath = data.hasPoorPrognosisAppleImmunity() ? 0 : data.getPoorPrognosisGoldenAppleDeathChance();
                    medicalNote = String.format(Locale.ROOT,
                            " §e[Next Day Roll Already Protected: %d%% Live / %d%% Death / 1%% Miracle]",
                            99 - activeDeath, activeDeath);
                } else {
                    int deathChance = data.getNextGoldenAppleDeathChancePercent();
                    int liveChance = 99 - deathChance;
                    data.setPoorPrognosisGoldenAppleDeathChance(deathChance);
                    data.setPoorPrognosisGoldenAppleActive(true);
                    data.setPoorPrognosisGoldenApplesUsed(data.getPoorPrognosisGoldenApplesUsed() + 1);
                    if (deathChance >= 49) {
                        medicalNote = " §c[Diminishing Returns Cap Reached (49% Death): Normal Golden Apples no longer reduce death chance, but your care is remembered]";
                    } else {
                        medicalNote = String.format(Locale.ROOT,
                                " §6§l[Next Day Death Roll Reduced: %d%% Live / %d%% Death / 1%% Miracle!]",
                                liveChance, deathChance);
                    }
                }
                if (curedLaminitis) {
                    medicalNote += " §a[Cured Laminitis!]";
                }
            } else if (data.hasActiveInjury()) {
                double newDays = Math.max(0.25, data.getInjuryDaysRemaining() * 0.25);
                data.setInjuryDaysRemaining(newDays);
                medicalNote = String.format(Locale.ROOT, " §a[Accelerated %s recovery to %.1f MC days!]",
                        data.getActiveInjury(), newDays);
            } else if (curedAny) {
                medicalNote = " §a[Ailments Cured!]";
            }

            // Spam-feeding Golden Apples while already full triggers Overweight!
            boolean triggeredOverweight = checkAndApplyOverweightFromFeeding(data, state);

            data.setHunger(data.getHunger() + hungerGain);
            data.setThirst(data.getThirst() + thirstGain);
            data.setFatigue(Math.max(0.0, data.getFatigue() - fatigueRelief));
            data.setBond(data.getBond() + bondGain);
            data.setMood(data.getMood() + moodGain);
            data.saveToHorse(horse);

            horse.heal(10.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.HORSE_EAT, SoundSource.PLAYERS, 1.0F, 1.1F);
            if (serverPlayer != null) {
                if (data.hasPoorPrognosis() || data.hasActiveInjury()) {
                    serverPlayer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "§6§l[Golden Apple Care] §eFed Golden Apple to %s! Mood: %s (%.0f) | FTG: %.0f%%%s",
                            data.getName(), data.getFormattedMoodTier(), data.getMood(), data.getFatigue(), medicalNote)));
                } else {
                    serverPlayer.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "§6Fed Golden Apple to %s!%s §fMood: %s (%.0f) §7| §cFTG: %.0f%%",
                            data.getName(), medicalNote,
                            data.getFormattedMoodTier(), data.getMood(), data.getFatigue())), true);
                }
                if (triggeredOverweight) {
                    sendOverweightWarningMessage(serverPlayer, data);
                }
            }
            return true;
        }

        if (isHoneyBottle) {
            boolean curedFever = data.removeAilment(ThoroughbredData.AILMENT_FEVER);
            boolean curedColic = data.removeAilment(ThoroughbredData.AILMENT_COLIC);
            boolean triggeredOverweight = checkAndApplyOverweightFromFeeding(data, state);
            data.setHunger(data.getHunger() + hungerGain);
            data.setThirst(data.getThirst() + thirstGain);
            data.setFatigue(data.getFatigue() - fatigueRelief);
            data.setBond(data.getBond() + bondGain);
            data.setMood(data.getMood() + moodGain);
            data.saveToHorse(horse);

            horse.heal(6.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
            }
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (serverPlayer != null) {
                String curedNote = (curedFever || curedColic) ? " §a[Cured Fever/Colic!]" : "";
                serverPlayer.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "§6Gave Honey Remedy to %s!%s §fMood: %s (%.0f) §7| §cFTG: %.0f%%",
                        data.getName(), curedNote, data.getFormattedMoodTier(), data.getMood(), data.getFatigue())), true);
                if (triggeredOverweight) {
                    sendOverweightWarningMessage(serverPlayer, data);
                }
            }
            return true;
        }

        if (isColdTherapy) {
            long now = horse.level().getGameTime();
            if (now - state.lastBrushGameTime < 600) {
                long secsLeft = (600 - (now - state.lastBrushGameTime)) / 20;
                if (serverPlayer != null) {
                    serverPlayer.displayClientMessage(
                            Component.literal("§b" + data.getName() + "'s legs were just iced! Wait " + secsLeft + "s."), true);
                }
                return true;
            }
            state.lastBrushGameTime = now;
            boolean curedFever = data.removeAilment(ThoroughbredData.AILMENT_FEVER);
            boolean curedLameness = false;
            if (!data.hasActiveInjury() && !data.hasPoorPrognosis()) {
                curedLameness = data.removeAilment(ThoroughbredData.AILMENT_LAMENESS);
            } else if (!data.hasPoorPrognosis() && data.hasActiveInjury()) {
                double nextDays = Math.max(0.25, data.getInjuryDaysRemaining() - 0.5);
                data.setInjuryDaysRemaining(nextDays);
            }
            data.setFatigue(data.getFatigue() - fatigueRelief);
            data.setBond(data.getBond() + bondGain);
            data.setMood(data.getMood() + moodGain);
            data.saveToHorse(horse);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.SNOW_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (horse.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SPLASH, horse.getX(), horse.getY() + 0.6, horse.getZ(), 12, 0.35, 0.3, 0.35, 0.05);
            }
            if (serverPlayer != null) {
                String extra = (curedFever || curedLameness) ? " §a[Cured Fever/Lameness!]" : "";
                serverPlayer.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "§bIced %s's legs!%s §cFatigue: %.0f%% §7| §fMood: %s",
                        data.getName(), extra, data.getFatigue(), data.getFormattedMoodTier())), true);
            }
            return true;
        }

        if (isBrush) {
            long now = horse.level().getGameTime();
            if (now - state.lastBrushGameTime < 600) {
                long secsLeft = (600 - (now - state.lastBrushGameTime)) / 20;
                if (serverPlayer != null) {
                    serverPlayer.displayClientMessage(
                            Component.literal("§e" + data.getName() + " was just brushed! Wait " + secsLeft + "s."),
                            true
                    );
                }
                return true;
            }
            state.lastBrushGameTime = now;
            data.setFatigue(data.getFatigue() - 12.0);
            data.setBond(data.getBond() + 0.8);
            data.setMood(data.getMood() + 4.5);
            data.saveToHorse(horse);

            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (horse.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        horse.getX(), horse.getY() + 1.2, horse.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
            }
            if (serverPlayer != null) {
                serverPlayer.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "§aBrushed %s! §fMood: %s (%.0f) §7| §cFatigue: %.0f%% §7| §dBond: %.1f%%",
                        data.getName(), data.getFormattedMoodTier(), data.getMood(), data.getFatigue(), data.getBond())), true);
            }
            return true;
        }

        if (isWaterBucket) {
            data.setThirst(data.getThirst() + thirstGain);
            data.setFatigue(data.getFatigue() - fatigueRelief);
            data.setBond(data.getBond() + bondGain);
            data.setMood(data.getMood() + moodGain);
            boolean brokeFever = false;
            if (data.hasAilment(ThoroughbredData.AILMENT_FEVER) && horse.getRandom().nextDouble() < 0.50) {
                brokeFever = data.removeAilment(ThoroughbredData.AILMENT_FEVER);
            }
            data.saveToHorse(horse);

            if (!player.getAbilities().instabuild) {
                player.setItemInHand(event.getHand(), new ItemStack(Items.BUCKET));
            }
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (serverPlayer != null) {
                serverPlayer.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "§bHydrated %s!%s §fMood: %s (%.0f) §7| §bThirst: %.0f%% §7| §dBond: %.1f%%",
                        data.getName(), brokeFever ? " §a[Fever Broke!]" : "",
                        data.getFormattedMoodTier(), data.getMood(), data.getThirst(), data.getBond())), true);
            }
            return true;
        }

        // Feeding heavy dry fodder (Hay Block / Wheat) while panting at >= 90% Fatigue risks Colic!
        if ((stack.is(Items.HAY_BLOCK) || stack.is(Items.WHEAT)) && data.getFatigue() >= 90.0
                && !data.hasAilment(ThoroughbredData.AILMENT_COLIC) && horse.getRandom().nextDouble() < 0.30) {
            data.addAilment(ThoroughbredData.AILMENT_COLIC);
            if (serverPlayer != null) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "§6§l[AILMENT: COLIC!] §eFeeding heavy dry fodder while " + data.getName() + " was at 90%+ Fatigue triggered Colic!"));
            }
        }

        // Overweight Ailment Check: Spam-feeding food when the horse is already full (Hunger >= 90%)!
        boolean triggeredOverweight = isSolidFood && checkAndApplyOverweightFromFeeding(data, state);

        data.setHunger(data.getHunger() + hungerGain);
        if (thirstGain > 0.0) {
            data.setThirst(data.getThirst() + thirstGain);
        }
        data.setFatigue(data.getFatigue() - fatigueRelief);
        data.setBond(data.getBond() + bondGain);
        data.setMood(data.getMood() + moodGain);
        data.saveToHorse(horse);

        if (triggeredOverweight && serverPlayer != null) {
            sendOverweightWarningMessage(serverPlayer, data);
        }

        if (!data.hasAilment(ThoroughbredData.AILMENT_COLIC)) {
            horse.heal(4.0F);
        }
        horse.resetLove();

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.HORSE_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (horse.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    horse.getX(), horse.getY() + 1.2, horse.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
        }
        if (serverPlayer != null) {
            serverPlayer.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "§6Fed %s! §fMood: %s (%.0f) §7| §6Hunger: %.0f%% §7| §dBond: %.1f%%",
                    data.getName(), data.getFormattedMoodTier(), data.getMood(), data.getHunger(), data.getBond())), true);
        }
        return true;
    }

    private static boolean checkAndApplyOverweightFromFeeding(ThoroughbredData data, ThoroughbredRunEngine.HorseRunState state) {
        if (data == null || data.hasPoorPrognosis()) {
            return false;
        }
        if (data.getHunger() >= 90.0) {
            int nextStreak = data.getOverfedStreak() + 1;
            data.setOverfedStreak(nextStreak);
            int threshold = data.hasPersonality(ThoroughbredData.PERS_GLUTTON) ? 2 : 3;
            if (nextStreak >= threshold) {
                boolean newlyAdded = !data.hasAilment(ThoroughbredData.AILMENT_OVERWEIGHT);
                data.addAilment(ThoroughbredData.AILMENT_OVERWEIGHT);
                if (state != null) {
                    state.overweightBurnMeters = 0.0;
                }
                return newlyAdded;
            }
        } else if (data.getHunger() < 75.0) {
            data.setOverfedStreak(0);
        }
        return false;
    }

    private static void sendOverweightWarningMessage(ServerPlayer serverPlayer, ThoroughbredData data) {
        if (serverPlayer == null || data == null) return;
        serverPlayer.sendSystemMessage(Component.literal(
                "§6§l[AILMENT: OVERWEIGHT!] §e" + data.getName()
                + " §fhas become §6Overweight §ffrom spam-feeding while already full! It suffers a minor drop in speed (-1.5 km/h) and faster stamina depletion (+18%) until exercised for 600m."));
    }

    public static class ClientEventRegistrar {
        public static void registerClientEvents() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(ThoroughbredRunEngine::onAddLayers);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onRenderTick);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onClientClickInput);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onRenderGuiOverlayPre);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onRenderGuiOverlayPost);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onRenderLivingPre);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onRenderLivingPost);
            MinecraftForge.EVENT_BUS.addListener(ThoroughbredRunEngine::onRenderLevelStage);
        }
    }
}