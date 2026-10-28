package com.aiothoroughbred;

import com.aiothoroughbred.item.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.HorseRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraft.world.entity.animal.horse.Variant;
import com.aiothoroughbred.block.ModBlocks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ThoroughbredRunEngine {

    private static final double MAX_GAUGE_KMH = 72.6;
    private static final double BASELINE_MAX_CROP_KMH = 72.0;
    private static final double STAMINA_THRESHOLD_KMH = 35.0;
    private static final double POOL_MAX_SPEED_KMH = 6.0;

    private static final double KMH_TO_BLOCKS_PER_TICK = 1.0 / 40.0;
    private static final int CROP_RHYTHM_TICKS = 36;

    private static final Map<UUID, HorseRunState> ACTIVE_RUNS = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_LAST_MOUNT = new HashMap<>();

    // Server-side temporary player yaw preservation across horse.tick() -> AbstractHorse#tickRidden
    private static final Map<UUID, Float> SERVER_SAVED_PLAYER_YROT = new HashMap<>();
    private static final Map<UUID, Float> SERVER_SAVED_PLAYER_YROTO = new HashMap<>();

    private static ClientHudData clientHud = new ClientHudData();
    private static long lastClientCropClickTime = 0;
    private static long lastClientFlagWaveTime = 0;
    private static boolean wasGKeyDown = false;
    private static boolean wasMKeyDown = false;

    // Step 12: Independent [A]/[D] Horse Steering & Smooth Speed-Dependent Cornering State
    private static int lastClientMountedHorseId = -1;
    private static float clientHorseYaw = 0.0F;
    private static float prevClientHorseYaw = 0.0F;
    private static float clientTurnVelocityDeg = 0.0F;

    private static boolean clientPlayerYawSpoofed = false;
    private static float savedClientPlayerYRot = 0.0F;
    private static float savedClientPlayerYRotO = 0.0F;

    // Step 11B & Step 16: Client 3-Line & Multi-Act G1 Broadcast Race Logo State
    private static String clientLogoGrade = "";
    private static String clientLogoRaceName = "";
    private static String clientLogoBottomType = "";
    private static String clientLogoG1Preset = RacingManager.DEFAULT_G1_PRESET;
    private static int clientLogoTicksRemaining = 0;
    private static int clientLogoTotalTicks = 100;
    private static int clientLogoAudioTicks = 100;
    private static int clientLogoHoldAfterAudioTicks = 0;
    private static int clientLogoElapsedTicks = 0;
    private static boolean clientLogoBoundToAudioClip = false;

    // Step 13: Client Official Live Race Leaderboard & Vertical Pack Radar State
    private static ModNetwork.RaceLeaderboardSyncPacket clientRaceLeaderboard =
            ModNetwork.RaceLeaderboardSyncPacket.inactive();
    private static ModNetwork.RaceLeaderboardSyncPacket lastToteBoardSnapshot =
            ModNetwork.RaceLeaderboardSyncPacket.inactive();
    private static long lastLeaderboardPacketMillis = 0L;
    private static boolean radarWindowInitialized = false;
    private static float smoothedWindowTopMeters = 0.0F;
    private static float smoothedWindowSpanMeters = 60.0F;
    private static final Map<Integer, ClientRunnerSmoothState> CLIENT_RUNNER_SMOOTH = new HashMap<>();

    private static class ClientRunnerSmoothState {
        float smoothedMeters = 0.0F;
        float smoothedLateral = 0.0F;
        float smoothedRowIndex = 0.0F;
        boolean initialized = false;
    }

    // Step 17 & 18: Client-side synced Horse Condition Map (Poor Prognosis / Death's Door lying pose, Fever, Race Silks & True Pure-White Coat!)
    public static class ClientHorseCondition {
        public boolean poorPrognosis = false;
        public boolean fever = false;
        public boolean lame = false;
        public boolean careerEnded = false;
        public boolean pureWhiteCoat = false;
        public String ownerSilkCode = "";
        public boolean wearingRaceSilk = false;
        public boolean deathsDoor = false;
    }

    private static final Map<Integer, ClientHorseCondition> CLIENT_HORSE_CONDITIONS = new HashMap<>();
    private static final Map<String, ResourceLocation> CACHED_JOCKEY_SILK_TEXTURES = new HashMap<>();
    private static boolean clientPushedLyingPose = false;
    private static boolean clientHidPlayerOuterSkin = false;
    private static boolean savedJacketVis = true;
    private static boolean savedRightSleeveVis = true;
    private static boolean savedLeftSleeveVis = true;
    private static boolean savedHatVis = true;
    private static ResourceLocation pureWhiteTextureLoc = null;
    private static long lastPureWhiteCheckMillis = 0L;
    private static long lastLoadedFileModified = -1L;
    private static String lastLoadedSourcePath = "";
    private static final ResourceLocation CUSTOM_PURE_WHITE_RESOURCE = new ResourceLocation(AIOThoroughbredMod.MODID, "textures/entity/horse/pure_white.png");
    private static final ResourceLocation CUSTOM_PURE_WHITE_ALT_RESOURCE = new ResourceLocation(AIOThoroughbredMod.MODID, "textures/pure_white.png");

    public static void clearJockeySilkTextureCache() {
        CACHED_JOCKEY_SILK_TEXTURES.clear();
    }

    public static void updateClientHorseCondition(int horseId, boolean poorPrognosis, boolean fever, boolean lame, boolean careerEnded) {
        updateClientHorseCondition(horseId, poorPrognosis, fever, lame, careerEnded, false, "", false, false);
    }

    public static void updateClientHorseCondition(int horseId, boolean poorPrognosis, boolean fever, boolean lame,
                                                  boolean careerEnded, boolean pureWhiteCoat) {
        updateClientHorseCondition(horseId, poorPrognosis, fever, lame, careerEnded, pureWhiteCoat, "", false, false);
    }

    public static void updateClientHorseCondition(int horseId, boolean poorPrognosis, boolean fever, boolean lame,
                                                  boolean careerEnded, boolean pureWhiteCoat, String ownerSilkCode) {
        updateClientHorseCondition(horseId, poorPrognosis, fever, lame, careerEnded, pureWhiteCoat, ownerSilkCode, false, false);
    }

    public static void updateClientHorseCondition(int horseId, boolean poorPrognosis, boolean fever, boolean lame,
                                                  boolean careerEnded, boolean pureWhiteCoat, String ownerSilkCode,
                                                  boolean wearingRaceSilk, boolean deathsDoor) {
        ClientHorseCondition cond = CLIENT_HORSE_CONDITIONS.computeIfAbsent(horseId, k -> new ClientHorseCondition());
        if (pureWhiteCoat && !cond.pureWhiteCoat) {
            // Force an immediate re-check for pure_white.png when a horse is switched to pure white!
            lastPureWhiteCheckMillis = 0L;
        }
        cond.poorPrognosis = poorPrognosis;
        cond.fever = fever;
        cond.lame = lame;
        cond.careerEnded = careerEnded;
        cond.pureWhiteCoat = pureWhiteCoat;
        if (ownerSilkCode != null && !ownerSilkCode.isBlank()) {
            cond.ownerSilkCode = ownerSilkCode.trim();
        }
        cond.wearingRaceSilk = wearingRaceSilk;
        cond.deathsDoor = deathsDoor;
    }

    public static boolean isClientHorseWearingRaceSilk(Horse horse) {
        if (horse == null) return false;
        ClientHorseCondition cond = CLIENT_HORSE_CONDITIONS.get(horse.getId());
        if (cond != null && cond.wearingRaceSilk) {
            return true;
        }
        if (horse.getPersistentData().contains("AIOThoroughbred")) {
            var tag = horse.getPersistentData().getCompound("AIOThoroughbred");
            if (tag.getBoolean("WearingRaceSilk") || tag.getBoolean("InRace")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPlayerCurrentlyWearingRaceSilks(Player player) {
        if (player == null || player.isInvisible() || !(player.getVehicle() instanceof Horse horse)) {
            return false;
        }
        if (!isClientHorseWearingRaceSilk(horse)) {
            return false;
        }
        String silkCode = getClientHorseOwnerSilkCode(horse);
        return silkCode != null && !silkCode.isBlank();
    }

    public static String getClientHorseOwnerSilkCode(Horse horse) {
        if (horse == null) return "";
        ClientHorseCondition cond = CLIENT_HORSE_CONDITIONS.get(horse.getId());
        if (cond != null && cond.ownerSilkCode != null && !cond.ownerSilkCode.isBlank()) {
            return cond.ownerSilkCode;
        }
        if (horse.getPersistentData().contains("AIOThoroughbred")) {
            return horse.getPersistentData().getCompound("AIOThoroughbred").getString("OwnerSilkCode");
        }
        return "";
    }

    public static boolean isClientHorsePureWhite(Horse horse) {
        if (horse == null) return false;
        ClientHorseCondition cond = CLIENT_HORSE_CONDITIONS.get(horse.getId());
        if (cond != null && cond.pureWhiteCoat) {
            return true;
        }
        if (horse.getPersistentData().contains("AIOThoroughbred")) {
            return horse.getPersistentData().getCompound("AIOThoroughbred").getBoolean("PureWhiteCoat");
        }
        return false;
    }

    private static boolean isMatchingPureWhiteFileName(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) return false;
        return lower.equals("pure_white.png")
                || lower.equals("pure_white.png.png")
                || lower.equals("pure white.png")
                || lower.equals("pure white.png.png")
                || lower.equals("pure_white png.png")
                || lower.equals("purewhite.png")
                || lower.startsWith("pure_white")
                || lower.startsWith("pure white");
    }

    private static java.io.File searchDirectoryForPureWhitePng(java.io.File dir, int maxDepth) {
        if (dir == null || !dir.isDirectory() || maxDepth < 0) return null;
        java.io.File[] files = dir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            if (f.isFile() && isMatchingPureWhiteFileName(f.getName())) {
                return f;
            }
        }
        if (maxDepth > 0) {
            for (java.io.File f : files) {
                if (f.isDirectory()) {
                    java.io.File sub = searchDirectoryForPureWhitePng(f, maxDepth - 1);
                    if (sub != null) return sub;
                }
            }
        }
        return null;
    }

    /**
     * Searches all candidate project, resource, and game directories on disk for `pure_white.png`
     * (works even if Gradle `processResources` was not run or if the file was placed in the project root,
     * `src/main/resources`, `run/aiothoroughbred_textures`, or `AIOThoroughbredMod/aiothoroughbred_textures`).
     */
    private static java.io.File findCustomPureWhiteFileOnDisk() {
        List<java.io.File> roots = new ArrayList<>();
        try {
            java.io.File gameDir = Minecraft.getInstance().gameDirectory;
            if (gameDir != null) {
                roots.add(gameDir.getCanonicalFile());
                if (gameDir.getCanonicalFile().getParentFile() != null) {
                    roots.add(gameDir.getCanonicalFile().getParentFile());
                }
            }
        } catch (Exception ignored) {
        }
        try {
            java.io.File cwd = new java.io.File(".").getCanonicalFile();
            if (!roots.contains(cwd)) roots.add(cwd);
            if (cwd.getParentFile() != null && !roots.contains(cwd.getParentFile())) {
                roots.add(cwd.getParentFile());
            }
        } catch (Exception ignored) {
        }
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                java.io.File desktopProj = new java.io.File(userHome, "Desktop/AIOThoroughbredMod");
                if (desktopProj.isDirectory() && !roots.contains(desktopProj)) {
                    roots.add(desktopProj);
                }
            }
        } catch (Exception ignored) {
        }

        String[] directSubPaths = {
                "aiothoroughbred_textures",
                "src/main/resources/assets/aiothoroughbred/textures/entity/horse",
                "src/main/resources/assets/aiothoroughbred/textures",
                "src/main/resources",
                "build/resources/main/assets/aiothoroughbred/textures/entity/horse",
                "assets/aiothoroughbred/textures/entity/horse"
        };

        for (java.io.File root : roots) {
            for (String sub : directSubPaths) {
                java.io.File candidateDir = new java.io.File(root, sub);
                java.io.File match = searchDirectoryForPureWhitePng(candidateDir, sub.endsWith("resources") ? 5 : 2);
                if (match != null) {
                    return match;
                }
            }
            java.io.File rootMatch = searchDirectoryForPureWhitePng(root, 0);
            if (rootMatch != null) {
                return rootMatch;
            }
        }
        return null;
    }

    /**
     * Converts any PNG InputStream (24-bit RGB, 32-bit RGBA, 8-bit Indexed Palette, 16-bit channel, etc.)
     * into a Minecraft NativeImage using javax.imageio.ImageIO (with fallback to NativeImage.read),
     * so editors whose PNGs are rejected by stb_image still load 100% reliably!
     */
    private static NativeImage readPngStreamToNativeImage(java.io.InputStream inStream) {
        try {
            byte[] rawBytes = inStream.readAllBytes();
            if (rawBytes.length == 0) return null;

            try {
                java.awt.image.BufferedImage bufImg = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(rawBytes));
                if (bufImg != null && bufImg.getWidth() > 0 && bufImg.getHeight() > 0) {
                    int w = bufImg.getWidth();
                    int h = bufImg.getHeight();
                    NativeImage nativeImg = new NativeImage(w, h, true);
                    boolean hasAlpha = bufImg.getColorModel().hasAlpha();

                    NativeImage vanillaMask = null;
                    if (!hasAlpha && w == 64 && h == 64) {
                        try {
                            var optVan = Minecraft.getInstance().getResourceManager()
                                    .getResource(new ResourceLocation("textures/entity/horse/horse_white.png"));
                            if (optVan.isPresent()) {
                                try (var vanIn = optVan.get().open()) {
                                    vanillaMask = NativeImage.read(vanIn);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int argb = bufImg.getRGB(x, y);
                            int a = hasAlpha ? ((argb >>> 24) & 0xFF) : 0xFF;
                            if (!hasAlpha && vanillaMask != null && x < vanillaMask.getWidth() && y < vanillaMask.getHeight()) {
                                a = (vanillaMask.getPixelRGBA(x, y) >>> 24) & 0xFF;
                            }
                            // Force visible pixels (alpha >= 20) to 100% opaque (255) so the vanilla grey horse underneath never bleeds through!
                            a = (a >= 20) ? 0xFF : 0x00;
                            int r = (argb >>> 16) & 0xFF;
                            int g = (argb >>> 8) & 0xFF;
                            int b = argb & 0xFF;
                            int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                            nativeImg.setPixelRGBA(x, y, abgr);
                        }
                    }
                    if (vanillaMask != null) {
                        vanillaMask.close();
                    }
                    return nativeImg;
                }
            } catch (Exception ignored) {
            }

            return NativeImage.read(new java.io.ByteArrayInputStream(rawBytes));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves and auto-reloads the Pure White horse coat texture:
     * 1. Automatically scans disk for `pure_white.png` in:
     *    - `<gameDir>/aiothoroughbred_textures/pure_white.png`
     *    - `<projectRoot>/aiothoroughbred_textures/pure_white.png`
     *    - `<projectRoot>/src/main/resources/assets/aiothoroughbred/textures/entity/horse/pure_white.png`
     *    - Anywhere inside `src/main/resources/` (even if Gradle `processResources` hasn't run!)
     *    And hot-reloads automatically within 2 seconds whenever you save changes to the PNG file!
     * 2. Also checks the active ResourceManager & JAR classpath for packaged `pure_white.png`.
     * 3. Converts via `ImageIO` into a `DynamicTexture` so all PNG color modes (RGB, RGBA, Indexed, 16-bit) render cleanly.
     */
    public static ResourceLocation getOrCreatePureWhiteHorseTexture() {
        long now = System.currentTimeMillis();
        if (pureWhiteTextureLoc == null || (now - lastPureWhiteCheckMillis >= 2000L)) {
            lastPureWhiteCheckMillis = now;

            // 1. Check disk files first (supports live hot-reloading & un-copied src/main/resources!)
            java.io.File diskFile = findCustomPureWhiteFileOnDisk();
            if (diskFile != null && diskFile.isFile()) {
                long modTime = diskFile.lastModified();
                String absPath = diskFile.getAbsolutePath();
                if (pureWhiteTextureLoc == null || modTime != lastLoadedFileModified || !absPath.equals(lastLoadedSourcePath)) {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(diskFile)) {
                        NativeImage img = readPngStreamToNativeImage(fis);
                        if (img != null) {
                            DynamicTexture dyn = new DynamicTexture(img);
                            pureWhiteTextureLoc = Minecraft.getInstance().getTextureManager()
                                    .register("aiothoroughbred_pure_white_horse", dyn);
                            lastLoadedFileModified = modTime;
                            lastLoadedSourcePath = absPath;
                            AIOThoroughbredMod.LOGGER.info("Loaded custom Pure White coat texture from disk: {}", absPath);
                        }
                    } catch (Exception e) {
                        AIOThoroughbredMod.LOGGER.warn("Failed to load pure_white.png from {}: {}", absPath, e.getMessage());
                    }
                }
                if (pureWhiteTextureLoc != null) {
                    return pureWhiteTextureLoc;
                }
            }

            // 2. Check ResourceManager & Classpath streams (when packaged inside JAR or Resource Pack)
            if (pureWhiteTextureLoc == null) {
                ResourceLocation[] candidates = {CUSTOM_PURE_WHITE_RESOURCE, CUSTOM_PURE_WHITE_ALT_RESOURCE};
                for (ResourceLocation loc : candidates) {
                    try {
                        var optRes = Minecraft.getInstance().getResourceManager().getResource(loc);
                        if (optRes.isPresent()) {
                            try (var inStream = optRes.get().open()) {
                                NativeImage img = readPngStreamToNativeImage(inStream);
                                if (img != null) {
                                    DynamicTexture dyn = new DynamicTexture(img);
                                    pureWhiteTextureLoc = Minecraft.getInstance().getTextureManager()
                                            .register("aiothoroughbred_pure_white_horse", dyn);
                                    lastLoadedSourcePath = loc.toString();
                                    AIOThoroughbredMod.LOGGER.info("Loaded custom Pure White coat texture from resource: {}", loc);
                                    return pureWhiteTextureLoc;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                String[] classpathCandidates = {
                        "/assets/aiothoroughbred/textures/entity/horse/pure_white.png",
                        "/assets/aiothoroughbred/textures/pure_white.png",
                        "/pure_white.png"
                };
                for (String cp : classpathCandidates) {
                    try (java.io.InputStream cpStream = ThoroughbredRunEngine.class.getResourceAsStream(cp)) {
                        if (cpStream != null) {
                            NativeImage img = readPngStreamToNativeImage(cpStream);
                            if (img != null) {
                                DynamicTexture dyn = new DynamicTexture(img);
                                pureWhiteTextureLoc = Minecraft.getInstance().getTextureManager()
                                        .register("aiothoroughbred_pure_white_horse", dyn);
                                lastLoadedSourcePath = "classpath:" + cp;
                                AIOThoroughbredMod.LOGGER.info("Loaded custom Pure White coat texture from classpath: {}", cp);
                                return pureWhiteTextureLoc;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (pureWhiteTextureLoc != null) {
            return pureWhiteTextureLoc;
        }
        return new ResourceLocation("textures/entity/horse/horse_white.png");
    }

    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        LivingEntityRenderer<Horse, HorseModel<Horse>> renderer = event.getRenderer(EntityType.HORSE);
        if (renderer != null) {
            renderer.addLayer(new PureWhiteCoatLayer(renderer));
        }
        try {
            for (String skinName : event.getSkins()) {
                LivingEntityRenderer<Player, PlayerModel<Player>> playerRenderer = event.getSkin(skinName);
                if (playerRenderer != null) {
                    playerRenderer.addLayer(new JockeySilkLayer(playerRenderer));
                }
            }
        } catch (Exception e) {
            AIOThoroughbredMod.LOGGER.warn("Could not attach JockeySilkLayer to player skins: {}", e.getMessage());
        }
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * Dynamically generates and caches a 64x64 Jockey Silk & Jockey Cap armor-layer texture for a given JockeySilkConfig code.
     * Paints both the base and armor-inflated outer UV boxes:
     * - Torso & Jacket (u:16..40, v:16..32 & v:32..48)
     * - Right Arm & Right Sleeve (u:40..56, v:16..30 & v:32..46)
     * - Left Arm & Left Sleeve (u:32..48, v:48..62 & u:48..64, v:48..62)
     * - Jockey Cap & Helmet Visor on Hat & Head (u:32..64, v:0..16 & u:0..32, v:0..16), leaving the player's face visible!
     */
    public static ResourceLocation getOrCreateJockeySilkTexture3D(String rawSilkCode) {
        ClientScreens.ensureJockeySilkTemplatesExported();
        String cleanCode = (rawSilkCode != null && !rawSilkCode.isBlank()) ? rawSilkCode.trim() : "3,17,0,0,2,3,3,1,0";
        ResourceLocation cached = CACHED_JOCKEY_SILK_TEXTURES.get(cleanCode);
        if (cached != null) {
            return cached;
        }

        StudbookSavedData.JockeySilkConfig cfg = StudbookSavedData.JockeySilkConfig.fromCompactCode(cleanCode);
        int bodyBaseAbgr = argbToAbgr(StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.bodyColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)]);
        int bodyPatAbgr = argbToAbgr(StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.bodyPatternColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)]);
        int slvBaseAbgr = argbToAbgr(StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.sleeveColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)]);
        int slvPatAbgr = argbToAbgr(StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.sleevePatternColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)]);
        int capBaseAbgr = argbToAbgr(StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.capColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)]);
        int capPatAbgr = argbToAbgr(StudbookSavedData.JockeySilkConfig.COLOR_ARGB[
                Mth.clamp(cfg.capPatternColor, 0, StudbookSavedData.JockeySilkConfig.COLOR_ARGB.length - 1)]);
        int collarAbgr = argbToAbgr(0xFFF0F0F4);
        int darkVisorAbgr = (cfg.capPattern == 7) ? capPatAbgr : argbToAbgr(0xFF1A1E26);
        int goggleLensAbgr = argbToAbgr(0xFFD8ECF8);
        int goggleStrapAbgr = argbToAbgr(0xFF282C34);

        NativeImage img = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                img.setPixelRGBA(x, y, 0x00000000);
            }
        }

        // 1. Paint Jockey Cap onto Hat UV box (baseU=32, baseV=0) AND Head UV box (baseU=0, baseV=0)
        paintJockeyCapUvBox(img, 32, 0, cfg.capPattern, capBaseAbgr, capPatAbgr, darkVisorAbgr, goggleLensAbgr, goggleStrapAbgr);
        paintJockeyCapUvBox(img, 0, 0, cfg.capPattern, capBaseAbgr, capPatAbgr, darkVisorAbgr, goggleLensAbgr, goggleStrapAbgr);

        // 2. Paint Torso onto Jacket UV box (baseU=16, baseV=32) AND Body UV box (baseU=16, baseV=16)
        paintTorsoUvBox(img, 16, 32, cfg.bodyPattern, bodyBaseAbgr, bodyPatAbgr, collarAbgr);
        paintTorsoUvBox(img, 16, 16, cfg.bodyPattern, bodyBaseAbgr, bodyPatAbgr, collarAbgr);

        // 3. Paint Right Arm Sleeve onto RightSleeve (40, 32) and RightArm (40, 16)
        paintArmSleeveUvBox(img, 40, 32, cfg.sleevePattern, slvBaseAbgr, slvPatAbgr);
        paintArmSleeveUvBox(img, 40, 16, cfg.sleevePattern, slvBaseAbgr, slvPatAbgr);

        // 4. Paint Left Arm Sleeve onto LeftSleeve (48, 48) and LeftArm (32, 48)
        paintArmSleeveUvBox(img, 48, 48, cfg.sleevePattern, slvBaseAbgr, slvPatAbgr);
        paintArmSleeveUvBox(img, 32, 48, cfg.sleevePattern, slvBaseAbgr, slvPatAbgr);

        String safeName = "aiothoroughbred_jockey_silk_" + cleanCode.replace(',', '_');
        DynamicTexture dyn = new DynamicTexture(img);
        ResourceLocation loc = Minecraft.getInstance().getTextureManager().register(safeName, dyn);
        CACHED_JOCKEY_SILK_TEXTURES.put(cleanCode, loc);
        return loc;
    }

    private static void paintJockeyCapUvBox(NativeImage img, int baseU, int baseV, int capPattern,
                                            int capBaseAbgr, int capPatAbgr, int visorAbgr,
                                            int goggleLensAbgr, int goggleStrapAbgr) {
        // Top of Cap (u: baseU+8 .. baseU+16, v: baseV .. baseV+8)
        for (int v = baseV; v < baseV + 8; v++) {
            int cy = v - baseV;
            for (int u = baseU + 8; u < baseU + 16; u++) {
                int cx = u - (baseU + 8);
                boolean pat = ClientScreens.evalCapPatternPixel(capPattern, cx, cy, 8, 8);
                img.setPixelRGBA(u, v, pat ? capPatAbgr : capBaseAbgr);
            }
        }
        // Front of Cap (u: baseU+8 .. baseU+16, v: baseV+8 .. baseV+11; v+11..v+15 left transparent for face!)
        for (int u = baseU + 8; u < baseU + 16; u++) {
            int cx = u - (baseU + 8);
            boolean pat0 = ClientScreens.evalCapPatternPixel(capPattern, cx, 0, 8, 4);
            img.setPixelRGBA(u, baseV + 8, pat0 ? capPatAbgr : capBaseAbgr);
            // Racing goggles resting on cap band at v = baseV + 9
            boolean isLens = (cx >= 1 && cx <= 3) || (cx >= 4 && cx <= 6);
            img.setPixelRGBA(u, baseV + 9, isLens ? goggleLensAbgr : goggleStrapAbgr);
            // Cap Visor Peak at v = baseV + 10
            img.setPixelRGBA(u, baseV + 10, visorAbgr);
        }
        // Right & Left Sides of Cap (u: baseU..baseU+8 and baseU+16..baseU+24, v: baseV+8 .. baseV+12)
        for (int v = baseV + 8; v < baseV + 12; v++) {
            int cy = v - (baseV + 8);
            for (int u = baseU; u < baseU + 8; u++) {
                int cx = u - baseU;
                if (v == baseV + 9) {
                    img.setPixelRGBA(u, v, goggleStrapAbgr);
                } else {
                    boolean pat = ClientScreens.evalCapPatternPixel(capPattern, cx, cy, 8, 4);
                    img.setPixelRGBA(u, v, pat ? capPatAbgr : capBaseAbgr);
                }
            }
            for (int u = baseU + 16; u < baseU + 24; u++) {
                int cx = u - (baseU + 16);
                if (v == baseV + 9) {
                    img.setPixelRGBA(u, v, goggleStrapAbgr);
                } else {
                    boolean pat = ClientScreens.evalCapPatternPixel(capPattern, cx, cy, 8, 4);
                    img.setPixelRGBA(u, v, pat ? capPatAbgr : capBaseAbgr);
                }
            }
        }
        // Back of Cap (u: baseU+24 .. baseU+32, v: baseV+8 .. baseV+13)
        for (int v = baseV + 8; v < baseV + 13; v++) {
            int cy = v - (baseV + 8);
            for (int u = baseU + 24; u < baseU + 32; u++) {
                int cx = u - (baseU + 24);
                if (v == baseV + 9) {
                    img.setPixelRGBA(u, v, goggleStrapAbgr);
                } else {
                    boolean pat = ClientScreens.evalCapPatternPixel(capPattern, cx, cy, 8, 5);
                    img.setPixelRGBA(u, v, pat ? capPatAbgr : capBaseAbgr);
                }
            }
        }
    }

    private static void paintTorsoUvBox(NativeImage img, int baseU, int baseV, int bodyPattern,
                                        int bodyBaseAbgr, int bodyPatAbgr, int collarAbgr) {
        for (int v = baseV; v < baseV + 4; v++) {
            for (int u = baseU + 4; u < baseU + 20; u++) {
                img.setPixelRGBA(u, v, bodyBaseAbgr);
            }
        }
        for (int v = baseV + 4; v < baseV + 16; v++) {
            int by = v - (baseV + 4); // 0..11
            for (int u = baseU; u < baseU + 24; u++) {
                if (v == baseV + 4 && (u == baseU + 7 || u == baseU + 8)) {
                    img.setPixelRGBA(u, v, collarAbgr);
                    continue;
                }
                int bx;
                if (u >= baseU + 4 && u < baseU + 12) {
                    bx = u - (baseU + 4);
                } else if (u >= baseU + 16 && u < baseU + 24) {
                    bx = 7 - (u - (baseU + 16));
                } else {
                    bx = (u < baseU + 4) ? 0 : 7;
                }
                boolean pat = ClientScreens.evalJraBodyPatternPixel(bodyPattern, bx, by, 8, 12);
                img.setPixelRGBA(u, v, pat ? bodyPatAbgr : bodyBaseAbgr);
            }
        }
    }

    private static void paintArmSleeveUvBox(NativeImage img, int baseU, int baseV, int sleevePattern, int slvBaseAbgr, int slvPatAbgr) {
        // Top of shoulder cap (u: baseU+4 .. baseU+8, v: baseV .. baseV+4)
        for (int v = baseV; v < baseV + 4; v++) {
            for (int u = baseU + 4; u < baseU + 8; u++) {
                img.setPixelRGBA(u, v, slvBaseAbgr);
            }
        }
        // Sleeve cylinder (u: baseU .. baseU+16, v: baseV+4 .. baseV+14, leaving bottom 2px for hand!)
        for (int v = baseV + 4; v < baseV + 14; v++) {
            int sy = v - (baseV + 4); // 0..9
            for (int u = baseU; u < baseU + 16; u++) {
                int sx = (u - baseU) % 4; // 0..3 per face
                boolean pat = ClientScreens.evalJraSleevePatternPixel(sleevePattern, sx, sy, 4, 10);
                img.setPixelRGBA(u, v, pat ? slvPatAbgr : slvBaseAbgr);
            }
        }
    }

    /**
     * Crash-safe 3D Jockey Silk & Cap Armor Layer for players mounted on a Thoroughbred during a race (and until dismount).
     * Renders on the armor-inflated outer cubes (jacket, rightSleeve, leftSleeve, hat) while hiding the player's
     * conflicting second skin layer so there is zero base-skin clipping or outer-layer overlap!
     */
    public static class JockeySilkLayer extends RenderLayer<Player, PlayerModel<Player>> {
        public JockeySilkLayer(LivingEntityRenderer<Player, PlayerModel<Player>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Player player,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!isPlayerCurrentlyWearingRaceSilks(player) || !(player.getVehicle() instanceof Horse horse)) {
                return;
            }
            String silkCode = getClientHorseOwnerSilkCode(horse);
            if (silkCode == null || silkCode.isBlank()) {
                return;
            }
            try {
                ResourceLocation silkTex = getOrCreateJockeySilkTexture3D(silkCode);
                VertexConsumer vc = buffer.getBuffer(RenderType.armorCutoutNoCull(silkTex));
                int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
                PlayerModel<Player> model = this.getParentModel();

                boolean prevJacket = model.jacket.visible;
                boolean prevRSleeve = model.rightSleeve.visible;
                boolean prevLSleeve = model.leftSleeve.visible;
                boolean prevHat = model.hat.visible;

                model.jacket.visible = true;
                model.rightSleeve.visible = true;
                model.leftSleeve.visible = true;
                model.hat.visible = true;

                model.jacket.copyFrom(model.body);
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
                model.hat.copyFrom(model.head);

                model.jacket.render(poseStack, vc, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                model.rightSleeve.render(poseStack, vc, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                model.leftSleeve.render(poseStack, vc, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                model.hat.render(poseStack, vc, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);

                model.jacket.visible = prevJacket;
                model.rightSleeve.visible = prevRSleeve;
                model.leftSleeve.visible = prevLSleeve;
                model.hat.visible = prevHat;
            } catch (Exception ignored) {
            }
        }
    }

    public static class PureWhiteCoatLayer extends RenderLayer<Horse, HorseModel<Horse>> {
        public PureWhiteCoatLayer(LivingEntityRenderer<Horse, HorseModel<Horse>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Horse horse,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (horse.isInvisible() || !isClientHorsePureWhite(horse)) {
                return;
            }
            ResourceLocation whiteTex = getOrCreatePureWhiteHorseTexture();
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(whiteTex));
            // In 1.20.1, HorseModel includes the saddle/bridle cubes (toggled visible when horse.isSaddled())
            // mapped directly to the saddle UV region of the 64x64 horse texture!
            this.getParentModel().renderToBuffer(
                    poseStack, vc, packedLight,
                    LivingEntityRenderer.getOverlayCoords(horse, 0.0F),
                    1.0F, 1.0F, 1.0F, 1.0F
            );
        }
    }

    public static boolean isClientHorsePoorPrognosis(Horse horse) {
        if (horse == null) return false;
        ClientHorseCondition cond = CLIENT_HORSE_CONDITIONS.get(horse.getId());
        if (cond != null && (cond.poorPrognosis || cond.deathsDoor)) {
            return true;
        }
        if (horse.getPersistentData().contains("AIOThoroughbred")) {
            var tag = horse.getPersistentData().getCompound("AIOThoroughbred");
            return tag.getBoolean("PoorPrognosis") || tag.getBoolean("DeathsDoor");
        }
        return false;
    }

    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        clientPushedLyingPose = false;
        clientHidPlayerOuterSkin = false;
        if (event.getEntity() instanceof Player player && isPlayerCurrentlyWearingRaceSilks(player)) {
            if (event.getRenderer().getModel() instanceof PlayerModel<?> pm) {
                savedJacketVis = pm.jacket.visible;
                savedRightSleeveVis = pm.rightSleeve.visible;
                savedLeftSleeveVis = pm.leftSleeve.visible;
                savedHatVis = pm.hat.visible;
                pm.jacket.visible = false;
                pm.rightSleeve.visible = false;
                pm.leftSleeve.visible = false;
                pm.hat.visible = false;
                clientHidPlayerOuterSkin = true;
            }
        }
        if (event.getEntity() instanceof Horse horse && isClientHorsePoorPrognosis(horse)) {
            float bodyYaw = Mth.rotLerp(event.getPartialTick(), horse.yBodyRotO, horse.yBodyRot);
            horse.yHeadRot = horse.yBodyRot;
            horse.yHeadRotO = horse.yBodyRotO;
            horse.setXRot(0.0F);
            horse.xRotO = 0.0F;
            horse.setStanding(false);

            com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            clientPushedLyingPose = true;
            // Conjugate ZP(90) by YP(180 - bodyYaw) so LivingEntityRenderer.setupRotations
            // cancels the inner Y rotation and rolls the horse 90 degrees around its own longitudinal spine
            // at any compass angle — preventing it from ever pitching its head or rear into the ground!
            poseStack.translate(0.0F, 0.35F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
            poseStack.translate(0.0F, -0.75F, 0.0F);
            horse.walkAnimation.setSpeed(0.0F);
        }
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (clientHidPlayerOuterSkin && event.getEntity() instanceof Player) {
            if (event.getRenderer().getModel() instanceof PlayerModel<?> pm) {
                pm.jacket.visible = savedJacketVis;
                pm.rightSleeve.visible = savedRightSleeveVis;
                pm.leftSleeve.visible = savedLeftSleeveVis;
                pm.hat.visible = savedHatVis;
            }
            clientHidPlayerOuterSkin = false;
        }
        if (clientPushedLyingPose && event.getEntity() instanceof Horse) {
            event.getPoseStack().popPose();
            clientPushedLyingPose = false;
        }
    }

    public static void updateClientRaceLeaderboard(ModNetwork.RaceLeaderboardSyncPacket packet) {
        if (packet == null) {
            return;
        }
        if (packet.runners != null && !packet.runners.isEmpty()) {
            lastToteBoardSnapshot = new ModNetwork.RaceLeaderboardSyncPacket(
                    true,
                    packet.grade,
                    packet.raceName,
                    packet.trackSummary,
                    packet.totalDistanceMeters,
                    packet.totalStalls,
                    packet.racePhase,
                    packet.elapsedSeconds,
                    packet.runners
            );
        }
        if (!packet.active) {
            clientRaceLeaderboard = ModNetwork.RaceLeaderboardSyncPacket.inactive();
            CLIENT_RUNNER_SMOOTH.clear();
            radarWindowInitialized = false;
            smoothedWindowTopMeters = 0.0F;
            smoothedWindowSpanMeters = 60.0F;
            return;
        }
        clientRaceLeaderboard = packet;
        lastLeaderboardPacketMillis = System.currentTimeMillis();
    }

    public static void triggerClientRaceLogoDisplay(String grade, String raceName, String bottomBaseType, int durationTicks) {
        triggerClientRaceLogoDisplay(grade, raceName, bottomBaseType, durationTicks, false, RacingManager.DEFAULT_G1_PRESET);
    }

    public static void triggerClientRaceLogoDisplay(String grade, String raceName, String bottomBaseType,
                                                    int durationTicks, boolean boundToAudioClip) {
        triggerClientRaceLogoDisplay(grade, raceName, bottomBaseType, durationTicks, boundToAudioClip, RacingManager.DEFAULT_G1_PRESET);
    }

    public static void triggerClientRaceLogoDisplay(String grade, String raceName, String bottomBaseType,
                                                    int durationTicks, boolean boundToAudioClip, String g1EffectPreset) {
        clientLogoGrade = grade != null ? grade : "Open";
        clientLogoRaceName = raceName != null ? raceName : "Official Race";
        clientLogoBottomType = bottomBaseType != null ? bottomBaseType : "";
        clientLogoG1Preset = (g1EffectPreset != null && !g1EffectPreset.isBlank()) ? g1EffectPreset : RacingManager.DEFAULT_G1_PRESET;
        int[] parsed = RacingManager.parseG1Preset(clientLogoG1Preset);
        int holdTicks = "G1".equalsIgnoreCase(clientLogoGrade) ? (parsed[9] * 2) : 0; // holdTenths * 2 ticks
        clientLogoAudioTicks = Math.max(20, durationTicks);
        clientLogoHoldAfterAudioTicks = Math.max(0, holdTicks);
        clientLogoTotalTicks = clientLogoAudioTicks + clientLogoHoldAfterAudioTicks;
        clientLogoTicksRemaining = clientLogoTotalTicks;
        clientLogoElapsedTicks = 0;
        clientLogoBoundToAudioClip = boundToAudioClip;
    }

    public static void clearClientRaceLogoDisplay() {
        clientLogoBoundToAudioClip = false;
        clientLogoTicksRemaining = 0;
        clientLogoElapsedTicks = 0;
    }

    public static boolean isClientLogoActive() {
        return clientLogoTicksRemaining > 0 || (clientLogoBoundToAudioClip && RacingManager.isClientFanfarePlaying());
    }

    public static float getClientLogoElapsedSeconds(float partialTick) {
        if (!isClientLogoActive()) return -1.0F;
        return (clientLogoElapsedTicks + partialTick) / 20.0F;
    }

    public static class HorseRunState {
        public double lastX = Double.NaN;
        public double lastY = Double.NaN;
        public double lastZ = Double.NaN;

        public double baseSpeedKmh = 0.0;
        public double displayedSpeedKmh = 0.0;
        public double bigStrideBonusKmh = 0.0;
        public double fluctuationOffsetKmh = 0.0;

        public double distanceRun = 0.0;
        public double overweightBurnMeters = 0.0;

        public double currentStamina = 2000.0;
        public double maxStamina = 2000.0;
        public double currentGuts = 150.0;
        public double maxGuts = 150.0;
        public boolean initializedPools = false;

        public double cropEffortRatio = 0.0;
        public int ticksSinceLastCrop = 999;
        public int recklessCropStreak = 0;
        public int panicTicks = 0;

        public double slopeResistancePool = 0.0;
        public double gutsSpeedLockKmh = 56.0;

        public boolean exhaustedCapLocked = false;
        public boolean gutsActive = false;

        public int cropCooldownTicks = 0;
        public int bigStrideTicks = 0;
        public int tickCounter = 0;
        public int unevenBreathingTicks = 0;
        public int stoppedTicks = 0;

        public boolean pressingW = false;
        public boolean pressingS = false;
        public boolean pressingA = false;
        public boolean pressingD = false;
        public boolean clientJumping = false;
        public float lastJumpScale = 0.0F;

        // Independent [A]/[D] Horse Heading Yaw (completely decoupled from jockey mouse look!)
        public float horseYaw = 0.0F;
        public float prevHorseYaw = 0.0F;
        public boolean horseYawInitialized = false;
        public float gateLockedYaw = 0.0F;

        // Training Mode state & Temperament Move Refusal
        public boolean trainingMode = false;
        public int trainingSessionTicks = 0;
        public int witsCropChain = 0;
        public double flyingBreezeMeters = 0.0;
        public double longCanterMeters = 0.0;
        public long lastBrushGameTime = 0;

        public boolean trainingRefusalActive = false;
        public int refusalResumeTimerTicks = 0;
        public int refusalRearSoundCooldown = 0;

        // Visual Rear Timer: ensures horse.setStanding(false) is ALWAYS restored after rearing so it never runs on 2 legs!
        public int rearVisualTicks = 0;

        // High-Bond Vicinity Heart Tracker: only emits hearts ONCE when owner enters vicinity, resets when they leave!
        public boolean ownerInVicinity = false;

        // Volatile Unridden Hostility State & Quirky Lead Refusal/Rear-Kick State
        public boolean hostileToPlayer = false;
        public UUID hostileTargetPlayer = null;
        public int haloAttackCoolTicks = 0;
        public int quirkyLeadRefusalTicks = 0;
        public int quirkyLeadCheckCooldownTicks = 0;
        public int quirkyKickCooldownTicks = 0;

        // Level Up Flash indicator (0=None, 1=SPD, 2=STM, 3=PWR, 4=GUTS, 5=WITS)
        public int levelUpStatCode = 0;
        public int levelUpFlashTicks = 0;

        // Step 15: Pack Collision, Boxing-In & Anti-Brake-Check
        public boolean isBoxedIn = false;
        public double boxedInTargetSpeedKmh = Double.MAX_VALUE;

        // Starting Gate, Stall Rear & 1-Second Gate Break state
        public boolean inStartingGate = false;
        public int assignedGateNumber = 0;
        public BlockPos gateStallPos = null;
        public boolean wHeldBeforeGateOpen = false;
        public boolean penalizedPreWindThisStart = false;
        public int gateRearFreezeTicks = 0;
        public double gateBreakAccelMultiplier = 1.0;
        public boolean gateBreakActive = false;
        public int gateBreakTicks = 0;
        public String gateBreakResult = "";
        public int gateBreakResultColor = 0xFFFFFF;
        public int gateBreakResultTicks = 0;

        // Step 18: Vanilla Love-Mode Breeding Courtship Ritual State
        public boolean breedingRitualActive = false;
        public boolean breedingRitualIsDam = false;
        public UUID breedingPartnerUUID = null;
        public int breedingRitualLoveTicks = 0;
        public int breedingRitualTimeoutTicks = 0;
        public long pendingStudFeePaid = 0L;

        public void clearBreedingRitual() {
            this.breedingRitualActive = false;
            this.breedingRitualIsDam = false;
            this.breedingPartnerUUID = null;
            this.breedingRitualLoveTicks = 0;
            this.breedingRitualTimeoutTicks = 0;
            this.pendingStudFeePaid = 0L;
        }

        // Step 14: Automatic Running Strategy & Homestretch Spurt State
        public int activeRaceStrategyPos = -1;
        public int midRaceEstablishedPos = -1;
        public int midRaceStepsOff = 0;
        public boolean ranBigEscapeThisRace = false;
        public boolean inHomestretchSpurt = false;
        public String activeStrategyHudTag = "";

        public void resetRaceStrategyState() {
            this.activeRaceStrategyPos = -1;
            this.midRaceEstablishedPos = -1;
            this.midRaceStepsOff = 0;
            this.ranBigEscapeThisRace = false;
            this.inHomestretchSpurt = false;
            this.activeStrategyHudTag = "";
        }

        public void stopAndResetSpeed() {
            this.resetRaceStrategyState();
            this.distanceRun = 0.0;
            this.baseSpeedKmh = 0.0;
            this.displayedSpeedKmh = 0.0;
            this.bigStrideBonusKmh = 0.0;
            this.fluctuationOffsetKmh = 0.0;
            this.cropEffortRatio = 0.0;
            this.ticksSinceLastCrop = 999;
            this.recklessCropStreak = 0;
            this.panicTicks = 0;
            this.slopeResistancePool = 0.0;
            this.bigStrideTicks = 0;
            this.pressingW = false;
            this.pressingS = false;
            this.pressingA = false;
            this.pressingD = false;
            this.clientJumping = false;
            this.lastJumpScale = 0.0F;
            this.horseYawInitialized = false;
            this.witsCropChain = 0;
            this.flyingBreezeMeters = 0.0;
            this.longCanterMeters = 0.0;
            this.trainingRefusalActive = false;
            this.refusalResumeTimerTicks = 0;
            this.refusalRearSoundCooldown = 0;
            this.rearVisualTicks = 0;
            this.levelUpStatCode = 0;
            this.levelUpFlashTicks = 0;
            this.isBoxedIn = false;
            this.boxedInTargetSpeedKmh = Double.MAX_VALUE;
            this.inStartingGate = false;
            this.assignedGateNumber = 0;
            this.gateStallPos = null;
            this.wHeldBeforeGateOpen = false;
            this.penalizedPreWindThisStart = false;
            this.gateRearFreezeTicks = 0;
            this.gateBreakAccelMultiplier = 1.0;
            this.gateBreakActive = false;
            this.gateBreakTicks = 0;
            this.gateBreakResult = "";
            this.gateBreakResultTicks = 0;
            this.lastX = Double.NaN;
            this.lastY = Double.NaN;
            this.lastZ = Double.NaN;
        }
    }

    public static class ClientHudData {
        public boolean ridingThoroughbred = false;
        public String horseName = "";
        public double baseSpeedKmh = 0.0;
        public double displayedSpeedKmh = 0.0;
        public double bigStrideBonusKmh = 0.0;
        public double distanceMeters = 0.0;

        public double normalCapKmh = 48.0;
        public double cropMaxKmh = 60.0;
        public double unlockedCropCapKmh = 48.0;

        public double currentStamina = 2000.0;
        public double maxStamina = 2000.0;
        public double currentGuts = 150.0;
        public double maxGuts = 150.0;

        public int zoneCode = 0;
        public int statedMinDist = 1200;
        public int maxDist = 1800;

        public boolean cropActive = false;
        public boolean gutsActive = false;
        public boolean exhaustedCapLocked = false;
        public boolean unevenBreathing = false;
        public boolean panicking = false;
        public boolean onSlope = false;

        public boolean trainingMode = false;
        public boolean trainingRefusalActive = false;
        public boolean inPool = false;
        public boolean weightedTack = false;
        public boolean blinkersHood = false;
        public int trainingSessionTicks = 0;
        public int witsCropChain = 0;

        public int levelUpStatCode = 0;
        public int levelUpFlashTicks = 0;

        public boolean isBoxedIn = false;
        public boolean inStartingGate = false;
        public int gateNumber = 0;
        public float gateLockedYaw = 0.0F;
        public boolean gateBreakActive = false;
        public String gateBreakResult = "";
        public int gateBreakResultColor = 0xFFFFFF;
        public int gateBreakResultTicks = 0;
        public String activeStrategyTag = "";
        public String conditionHudTag = "";
        public boolean careerEnded = false;
        public boolean hasCorneringMaestro = false;
        public String personality = "Competitive";

        public float hunger = 100.0F;
        public float thirst = 100.0F;
        public float fatigue = 0.0F;
        public float bond = 20.0F;
        public float mood = 55.0F;
        public String temperament = "Normal";

        public int spdCur = 200, spdMax = 700;
        public float spdCurExp = 0.0F, spdReqExp = 50.0F;
        public boolean gainingSpd = false;

        public int stmCur = 200, stmMax = 700;
        public float stmCurExp = 0.0F, stmReqExp = 50.0F;
        public boolean gainingStm = false;

        public int pwrCur = 200, pwrMax = 700;
        public float pwrCurExp = 0.0F, pwrReqExp = 50.0F;
        public boolean gainingPwr = false;

        public int gutsCur = 200, gutsMax = 700;
        public float gutsCurExp = 0.0F, gutsReqExp = 50.0F;
        public boolean gainingGuts = false;

        public int witsCur = 200, witsMax = 700;
        public float witsCurExp = 0.0F, witsReqExp = 50.0F;
        public boolean gainingWits = false;
    }

    public static void updateClientHud(ClientHudData data) {
        clientHud = data;
    }

    public static HorseRunState getOrCreateState(Horse horse) {
        return ACTIVE_RUNS.computeIfAbsent(horse.getUUID(), id -> new HorseRunState());
    }

    /**
     * Speed-dependent turning ease (in degrees per tick, 20 ticks = 1 second):
     * - Stationary pivot (0 km/h): 4.0 deg/tick (80 deg/s)
     * - Walk / Slow Trot (0.2 - 15 km/h): 5.6 deg/tick (112 deg/s) — effortless, tight maneuvering
     * - Canter / Slow Gallop (15 - 40 km/h): smoothly scales 5.6 -> 4.2 deg/tick (112 -> 84 deg/s; ~90 deg/s at 35 km/h)
     * - Racing Gallop (40 - 65 km/h): smoothly scales 4.2 -> 2.15 deg/tick (84 -> 43 deg/s; ~42 deg/s sweeping corners at 65 km/h)
     * - Full Sprint / Crop Drive (65 - 72.6 km/h): smoothly scales 2.15 -> 1.70 deg/tick (43 -> 34 deg/s)
     */
    public static float calculateMaxTurnRateDegPerTick(double speedKmh, boolean inPool) {
        if (inPool) {
            return 2.60F;
        }
        if (speedKmh <= 0.2) {
            return 4.00F;
        }
        if (speedKmh <= 15.0) {
            return 5.60F;
        }
        if (speedKmh <= 40.0) {
            float t = (float) ((speedKmh - 15.0) / 25.0);
            return Mth.lerp(t, 5.60F, 4.20F);
        }
        if (speedKmh <= 65.0) {
            float t = (float) ((speedKmh - 40.0) / 25.0);
            return Mth.lerp(t, 4.20F, 2.15F);
        }
        float t = (float) Mth.clamp((speedKmh - 65.0) / (MAX_GAUGE_KMH - 65.0), 0.0, 1.0);
        return Mth.lerp(t, 2.15F, 1.70F);
    }

    private static void applyHorseYaw(Horse horse, float prevYaw, float currentYaw) {
        horse.setYRot(currentYaw);
        horse.yRotO = prevYaw;
        horse.yBodyRot = currentYaw;
        horse.yBodyRotO = prevYaw;
        horse.yHeadRot = currentYaw;
        horse.yHeadRotO = prevYaw;
    }

    // =========================================================================
    // 1. CLIENT INPUT, [A]/[D] STEERING, [M] MARKER KEY, [G] TRAINING KEY & JUMP
    // =========================================================================
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END) {
            if (clientPlayerYawSpoofed && mc.player != null) {
                mc.player.setYRot(savedClientPlayerYRot);
                mc.player.yRotO = savedClientPlayerYRotO;
                clientPlayerYawSpoofed = false;
            }
            if (mc.player != null && mc.player.getVehicle() instanceof Horse horse && clientHud.ridingThoroughbred) {
                applyHorseYaw(horse, prevClientHorseYaw, clientHorseYaw);
            }
            return;
        }

        if (mc.player == null || mc.isPaused()) {
            return;
        }

        // Auto-expire leaderboard if client left server or no packet received for 30s
        if (clientRaceLeaderboard.active && System.currentTimeMillis() - lastLeaderboardPacketMillis > 30000L) {
            clientRaceLeaderboard = ModNetwork.RaceLeaderboardSyncPacket.inactive();
            CLIENT_RUNNER_SMOOTH.clear();
            radarWindowInitialized = false;
        }

        // Keep the Race Logo on screen for the FULL fanfare duration PLUS any configured G1 Post-Fanfare Hold!
        // Never cut clientLogoTicksRemaining short before clientLogoAudioTicks has elapsed even if the OS audio mixer
        // thread starts asynchronously or finishes a few frames early!
        if (isClientLogoActive()) {
            clientLogoElapsedTicks++;
        }
        if (clientLogoTicksRemaining > 0) {
            clientLogoTicksRemaining--;
        }
        if (clientLogoBoundToAudioClip) {
            if (RacingManager.isClientFanfarePlaying()) {
                clientLogoTicksRemaining = Math.max(clientLogoTicksRemaining, clientLogoHoldAfterAudioTicks + 2);
            } else if (clientLogoElapsedTicks >= Math.max(20, clientLogoAudioTicks - 10)) {
                clientLogoBoundToAudioClip = false;
                clientLogoTicksRemaining = Math.max(clientLogoTicksRemaining, clientLogoHoldAfterAudioTicks);
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ClientFanfareFinishedPacket());
            }
        }

        long windowHandle = mc.getWindow().getWindow();

        // [M] Key cycles Checkpoint Marker Mode (works on foot OR on horseback!)
        boolean mDown = mc.screen == null && InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_M);
        if (mDown && !wasMKeyDown) {
            if (mc.player.getMainHandItem().is(ModItems.CHECKPOINT_MARKER.get())
                    || mc.player.getOffhandItem().is(ModItems.CHECKPOINT_MARKER.get())
                    || mc.player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModItems.CHECKPOINT_MARKER.get()))) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.CycleMarkerModePacket());
            }
        }
        wasMKeyDown = mDown;

        if (!(mc.player.getVehicle() instanceof Horse horse)) {
            wasGKeyDown = false;
            lastClientMountedHorseId = -1;
            clientTurnVelocityDeg = 0.0F;
            clientPlayerYawSpoofed = false;
            if (clientHud.ridingThoroughbred) {
                clientHud.ridingThoroughbred = false;
                clientHud.displayedSpeedKmh = 0.0;
                clientHud.baseSpeedKmh = 0.0;
            }
            return;
        }

        if (lastClientMountedHorseId != horse.getId()) {
            lastClientMountedHorseId = horse.getId();
            clientHorseYaw = Mth.wrapDegrees(horse.getYRot());
            prevClientHorseYaw = clientHorseYaw;
            clientTurnVelocityDeg = 0.0F;
        }

        boolean gDown = mc.screen == null && InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_G);
        if (gDown && !wasGKeyDown) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleTrainingModePacket());
        }
        wasGKeyDown = gDown;

        boolean wDown = mc.screen == null && mc.options.keyUp.isDown();
        boolean sDown = mc.screen == null && mc.options.keyDown.isDown();
        boolean aDown = mc.screen == null && mc.options.keyLeft.isDown();
        boolean dDown = mc.screen == null && mc.options.keyRight.isDown();

        if (clientHud.ridingThoroughbred) {
            if (clientHud.inStartingGate) {
                clientTurnVelocityDeg = 0.0F;
                clientHorseYaw = Mth.wrapDegrees(clientHud.gateLockedYaw);
                prevClientHorseYaw = clientHorseYaw;
            } else if (clientHud.trainingRefusalActive || horse.isStanding()) {
                clientTurnVelocityDeg = 0.0F;
                prevClientHorseYaw = clientHorseYaw;
            } else {
                float maxTurnDegPerTick = calculateMaxTurnRateDegPerTick(clientHud.displayedSpeedKmh, clientHud.inPool);
                if (clientHud.hasCorneringMaestro && clientHud.displayedSpeedKmh > 45.0 && !clientHud.inPool) {
                    maxTurnDegPerTick *= 1.15F; // Cornering Maestro: +15% high-speed turning rate above 45 km/h!
                }

                // Step 15: Shoulder-to-Shoulder Lateral Pack Resistance
                float leftTurnMultiplier = 1.0F;
                float rightTurnMultiplier = 1.0F;
                if (mc.level != null && clientHud.displayedSpeedKmh > 10.0 && clientRaceLeaderboard.active) {
                    double yawRad = Math.toRadians(clientHorseYaw);
                    double fwdX = -Math.sin(yawRad);
                    double fwdZ = Math.cos(yawRad);
                    double latX = Math.cos(yawRad);
                    double latZ = Math.sin(yawRad);

                    AABB sideBox = horse.getBoundingBox().inflate(2.5, 1.5, 2.5);
                    List<Horse> nearbyHorses = mc.level.getEntitiesOfClass(Horse.class, sideBox,
                            other -> other != horse && other.isAlive());

                    float pwrNorm = Mth.clamp(clientHud.pwrCur / 1000.0F, 0.05F, 1.0F);

                    for (Horse other : nearbyHorses) {
                        double dx = other.getX() - horse.getX();
                        double dz = other.getZ() - horse.getZ();
                        double dFwd = dx * fwdX + dz * fwdZ;
                        double dLat = dx * latX + dz * latZ;

                        if (Math.abs(dFwd) <= 1.8 && Math.abs(dLat) >= 0.65 && Math.abs(dLat) <= 1.45) {
                            if (dLat < 0.0) { // Other horse on the left flank!
                                float resist = Mth.lerp(pwrNorm, 0.25F, 0.80F);
                                leftTurnMultiplier = Math.min(leftTurnMultiplier, resist);
                            } else { // Other horse on the right flank!
                                float resist = Mth.lerp(pwrNorm, 0.25F, 0.80F);
                                rightTurnMultiplier = Math.min(rightTurnMultiplier, resist);
                            }
                        }
                    }
                }

                float targetTurn = 0.0F;
                if (aDown && !dDown) {
                    targetTurn = -maxTurnDegPerTick * leftTurnMultiplier; // [A] turns Left (decreases yaw)
                } else if (dDown && !aDown) {
                    targetTurn = maxTurnDegPerTick * rightTurnMultiplier;  // [D] turns Right (increases yaw)
                }

                // Smooth angular inertia so entering and exiting corners glides without choppy stepping
                if (targetTurn != 0.0F) {
                    clientTurnVelocityDeg = Mth.lerp(0.38F, clientTurnVelocityDeg, targetTurn);
                } else {
                    clientTurnVelocityDeg = Mth.lerp(0.48F, clientTurnVelocityDeg, 0.0F);
                    if (Math.abs(clientTurnVelocityDeg) < 0.005F) {
                        clientTurnVelocityDeg = 0.0F;
                    }
                }

                prevClientHorseYaw = clientHorseYaw;
                clientHorseYaw += clientTurnVelocityDeg;

                // Keep yaw normalized in [-180, 180) while shifting prevClientHorseYaw by the exact same 360° offset
                // so Mth.lerp and Mth.rotLerp never flash across the ±180° wrap boundary!
                while (clientHorseYaw >= 180.0F) {
                    clientHorseYaw -= 360.0F;
                    prevClientHorseYaw -= 360.0F;
                }
                while (clientHorseYaw < -180.0F) {
                    clientHorseYaw += 360.0F;
                    prevClientHorseYaw += 360.0F;
                }
            }

            applyHorseYaw(horse, prevClientHorseYaw, clientHorseYaw);
        }

        float jumpScale = horse.isInWater() ? 0.0F : mc.player.getJumpRidingScale();
        boolean jumping = !horse.isInWater() && (!horse.onGround() || horse.isJumping() || jumpScale > 0.01F);

        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RiderInputPacket(
                wDown, sDown, aDown, dDown, jumping, jumpScale, clientHorseYaw));
    }

    /**
     * Enforces the horse's decoupled [A]/[D] rotation right before every render frame (60-240+ FPS),
     * guaranteeing butter-smooth sub-tick interpolation around corners while the jockey's mouse look stays 100% free.
     */
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (clientPlayerYawSpoofed && mc.player != null) {
            mc.player.setYRot(savedClientPlayerYRot);
            mc.player.yRotO = savedClientPlayerYRotO;
            clientPlayerYawSpoofed = false;
        }
        if (mc.player != null && mc.player.getVehicle() instanceof Horse horse && clientHud.ridingThoroughbred) {
            applyHorseYaw(horse, prevClientHorseYaw, clientHorseYaw);
        }
    }

    // =========================================================================
    // 2. LIVING TICK: AUTONOMOUS CARE, MOOD, VICINITY HEARTS & A/D VELOCITY!
    // =========================================================================
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) {
            return;
        }

        if (!horse.level().isClientSide) {
            if (!ThoroughbredData.isThoroughbred(horse)) {
                return;
            }

            HorseRunState state = getOrCreateState(horse);
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);

            // Step 18: Foal (0->1 Equine Yr) -> Yearling (1->2 Equine Yrs) -> 2yo Adult Racehorse, plus Mare Heat & Pregnancy!
            if (data.isFoal()) {
                if (!horse.isBaby()) {
                    horse.setBaby(true);
                }
                horse.setAge(-24000);
                if (horse.tickCount % 20 == 0 && data.isFoalReadyToGrowUp() && horse.level() instanceof ServerLevel sl) {
                    growFoalIntoYearling(sl, horse, data);
                }
            } else {
                if (horse.isBaby()) {
                    horse.setBaby(false);
                    horse.setAge(0);
                }
                if (data.isYearling() && horse.tickCount % 20 == 0 && data.isYearlingReadyToMature() && horse.level() instanceof ServerLevel sl) {
                    matureYearlingIntoAdult(sl, horse, data);
                }
            }

            if (state.breedingRitualActive && horse.level() instanceof ServerLevel slRitual) {
                tickBreedingLoveRitual(slRitual, horse, data, state);
            }

            if (horse.tickCount % 40 == 0 && horse.level() instanceof ServerLevel sl) {
                if ("Female".equalsIgnoreCase(data.getGender()) && data.isRetired() && !data.isUnderTwoEquineYears()) {
                    if (data.isPregnant()) {
                        if (data.isInHeat()) {
                            data.setInHeat(false);
                            data.saveToHorse(horse);
                        }
                        if (data.isReadyToGiveBirth()) {
                            deliverFoalFromPregnantMare(sl, horse, data);
                        }
                    } else if (!data.isManualHeatOverride()) {
                        StudbookSavedData studbook = StudbookSavedData.get(sl);
                        boolean seasonHeat = studbook.isCurrentlyBreedingSeasonAprilToOctober();
                        if (data.isInHeat() != seasonHeat) {
                            data.setInHeat(seasonHeat);
                            data.saveToHorse(horse);
                            if (seasonHeat) {
                                sendMessageToOwnerOrNearby(horse, data,
                                        "§d§l[" + ThoroughbredData.tr("Breeding Season — In Heat", "繁殖期 — 發情期", "繁殖期 — 发情期", "繁殖期・発情期") + "] §6" + data.getName()
                                        + " §f" + ThoroughbredData.tr("has entered her In Heat cycle and is ready to be bred!", "已進入發情期，隨時可以進行配種！", "已进入发情期，随时可以进行配种！", "は発情期に入り、交配の準備が整いました！"));
                            }
                        }
                    }
                }
            }

            // Step 17 & 21: Check for Minecraft New Day rollover (for Poor Prognosis & Injury Recovery)
            // and Real-Life Day rollover (every 24 real-life hours for Old Age Death's Door & Peaceful Passing)!
            if (horse.tickCount % 20 == 0) {
                long currentRealDay = System.currentTimeMillis() / 86_400_000L;
                if (data.getLastCheckedDeathsDoorRealDay() < 0L) {
                    data.setLastCheckedDeathsDoorRealDay(currentRealDay);
                    data.saveToHorse(horse);
                } else if (currentRealDay > data.getLastCheckedDeathsDoorRealDay()) {
                    data.setLastCheckedDeathsDoorRealDay(currentRealDay);
                    performRealLifeDayDeathsDoorRoll(horse, data, state);
                    if (!horse.isAlive() || horse.isRemoved()) {
                        return;
                    }
                }

                long currentMcDay = horse.level().getDayTime() / 24000L;
                if (data.getLastCheckedMinecraftDay() < 0L) {
                    data.setLastCheckedMinecraftDay(currentMcDay);
                    data.saveToHorse(horse);
                } else if (currentMcDay > data.getLastCheckedMinecraftDay()) {
                    data.setLastCheckedMinecraftDay(currentMcDay);
                    performNewMinecraftDayRoll(horse, data, state, false);
                    if (!horse.isAlive() || horse.isRemoved()) {
                        return;
                    }
                }
                ModNetwork.broadcastHorseCondition(horse, data);
            }

            // Step 17: Fever visible particles & Colic stomach cramps
            if (horse.level() instanceof ServerLevel sl) {
                if (data.hasAilment(ThoroughbredData.AILMENT_FEVER) && horse.tickCount % 12 == 0) {
                    sl.sendParticles(ParticleTypes.SMOKE,
                            horse.getX(), horse.getY() + 1.35, horse.getZ(),
                            4, 0.35, 0.25, 0.35, 0.01);
                    sl.sendParticles(ParticleTypes.SPLASH,
                            horse.getX(), horse.getY() + 1.15, horse.getZ(),
                            3, 0.35, 0.25, 0.35, 0.02);
                    if (horse.tickCount % 36 == 0) {
                        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                                horse.getX(), horse.getY() + 1.5, horse.getZ(),
                                1, 0.25, 0.2, 0.25, 0.01);
                    }
                }
                if (data.hasAilment(ThoroughbredData.AILMENT_COLIC) && horse.tickCount % 40 == 0) {
                    data.setMood(data.getMood() - 1.5);
                    data.saveToHorse(horse);
                    if (horse.getHealth() > 1.0F) {
                        float beforeHp = horse.getHealth();
                        int maxDisplayedHalfHearts = Math.max(2, ((int) (horse.getMaxHealth() + 0.5F) / 2) * 2);
                        float nextHp = (beforeHp > maxDisplayedHalfHearts)
                                ? Math.max(1.0F, (float) maxDisplayedHalfHearts - 1.0F)
                                : Math.max(1.0F, (float) Math.ceil(beforeHp) - 1.0F);
                        float damageAmount = Math.max(1.0F, beforeHp - nextHp);
                        horse.getPersistentData().putBoolean("AIOColicTick", true);
                        try {
                            horse.invulnerableTime = 0;
                            boolean hurtApplied = horse.hurt(horse.damageSources().generic(), damageAmount);
                            if (!hurtApplied || horse.getHealth() > nextHp + 0.01F) {
                                horse.setHealth(nextHp);
                                horse.hurtDuration = 10;
                                horse.hurtTime = 10;
                                horse.invulnerableTime = 20;
                                horse.level().broadcastDamageEvent(horse, horse.damageSources().generic());
                            } else if (horse.getHealth() < 1.0F) {
                                horse.setHealth(1.0F);
                            }
                        } finally {
                            horse.getPersistentData().remove("AIOColicTick");
                        }
                        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                                horse.getX(), horse.getY() + 1.15, horse.getZ(),
                                2, 0.25, 0.2, 0.25, 0.02);
                    }
                    sl.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_HURT, SoundSource.NEUTRAL, 0.7F, 0.85F);
                    sl.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 0.9F, 0.65F);
                    sl.sendParticles(ParticleTypes.SMOKE,
                            horse.getX(), horse.getY() + 0.9, horse.getZ(),
                            6, 0.3, 0.2, 0.3, 0.01);
                }
            }

            // Step 17 & 21: Poor Prognosis & Death's Door Immobilization (Lies flat on the floor; can only be moved by Boat, Minecart, or pulled with a Lead!)
            if (data.hasPoorPrognosis() || data.isAtDeathsDoor()) {
                if (horse.isVehicle()) {
                    horse.ejectPassengers();
                }
                if (data.isWearingRaceSilk()) {
                    data.setWearingRaceSilk(false);
                    data.saveToHorse(horse);
                }
                // Nullify all Volatile / Quirky hostility or kick rolls while immobilized at Death's Door
                state.hostileToPlayer = false;
                state.hostileTargetPlayer = null;
                state.quirkyLeadRefusalTicks = 0;
                state.stopAndResetSpeed();
                horse.setStanding(false);
                horse.getNavigation().stop();
                horse.setXRot(0.0F);
                horse.xRotO = 0.0F;
                horse.yRotO = horse.getYRot();
                horse.yBodyRot = horse.getYRot();
                horse.yBodyRotO = horse.getYRot();
                horse.yHeadRot = horse.getYRot();
                horse.yHeadRotO = horse.getYRot();
                horse.setYya(0.0F);
                horse.setZza(0.0F);
                horse.setXxa(0.0F);

                // During Death's Door, when visited by the owner (or nearby player) with High Bond (>= 80),
                // continuously spew heart particles (even for Volatile horses!)
                if (data.isAtDeathsDoor() && data.getBond() >= 80.0 && horse.tickCount % 15 == 0 && horse.level() instanceof ServerLevel slVisit) {
                    Player visitor = null;
                    if (data.hasOwner()) {
                        try {
                            UUID ownerUuid = UUID.fromString(data.getOwnerUUID());
                            Player p = slVisit.getPlayerByUUID(ownerUuid);
                            if (p != null && p.isAlive() && !p.isSpectator() && horse.distanceToSqr(p) <= 64.0) {
                                visitor = p;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (visitor == null) {
                        visitor = slVisit.getNearestPlayer(horse, 8.0);
                    }
                    if (visitor != null && !visitor.isSpectator()) {
                        slVisit.sendParticles(ParticleTypes.HEART,
                                horse.getX(), horse.getY() + 0.85, horse.getZ(),
                                4, 0.45, 0.25, 0.45, 0.02);
                    }
                }

                if (!horse.isPassenger()) { // Not inside a Boat or Minecart
                    if (horse.isLeashed() && horse.getLeashHolder() != null) {
                        Entity holder = horse.getLeashHolder();
                        double ldx = holder.getX() - horse.getX();
                        double ldz = holder.getZ() - horse.getZ();
                        double horizDist = Math.sqrt(ldx * ldx + ldz * ldz);
                        if (horizDist > 2.2) {
                            double pullSpeed = Math.min(0.22, (horizDist - 2.0) * 0.12);
                            double vy = horse.getDeltaMovement().y;
                            if (horse.horizontalCollision && horse.onGround()) {
                                vy = 0.38D;
                            }
                            horse.setDeltaMovement((ldx / horizDist) * pullSpeed, vy, (ldz / horizDist) * pullSpeed);
                            horse.hasImpulse = true;
                        } else {
                            horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
                        }
                    } else {
                        horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
                    }
                }
                return;
            }

            if (state.rearVisualTicks > 0) {
                state.rearVisualTicks--;
                if (state.rearVisualTicks == 0 && horse.isStanding()) {
                    horse.setStanding(false);
                }
            } else if (horse.isVehicle() && horse.isStanding() && state.gateRearFreezeTicks <= 0) {
                horse.setStanding(false);
            }

            if (horse.getControllingPassenger() != null) {
                if (!data.isWearingRaceSilk() && (data.isInRace() || RacingManager.isHorseInActiveRace(horse))) {
                    data.setWearingRaceSilk(true);
                    data.saveToHorse(horse);
                }
                if (state.hostileToPlayer) {
                    state.hostileToPlayer = false;
                    state.hostileTargetPlayer = null;
                    horse.setStanding(false);
                    horse.getNavigation().stop();
                }

                // Decouple server-side AbstractHorse#tickRidden from the rider's mouse yaw!
                if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                    float targetYaw = state.inStartingGate
                            ? state.gateLockedYaw
                            : (state.horseYawInitialized ? state.horseYaw : horse.getYRot());
                    float prevYaw = state.inStartingGate
                            ? state.gateLockedYaw
                            : (state.horseYawInitialized ? state.prevHorseYaw : targetYaw);

                    SERVER_SAVED_PLAYER_YROT.put(sp.getUUID(), sp.getYRot());
                    SERVER_SAVED_PLAYER_YROTO.put(sp.getUUID(), sp.yRotO);
                    sp.setYRot(targetYaw);
                    sp.yRotO = prevYaw;
                    applyHorseYaw(horse, prevYaw, targetYaw);
                }
            } else {
                tickUnriddenTemperamentAndBond(horse, data, state);
            }

            if (horse.tickCount % 40 == 0) {
                boolean changed = false;

                if (!data.refusesSolidFood() && data.getHunger() < 75.0 && tryEatNearbyHayBale(horse.level(), horse.blockPosition())) {
                    data.setHunger(data.getHunger() + 60.0);
                    data.setFatigue(data.getFatigue() - 12.0);
                    data.setMood(data.getMood() + 2.5);
                    horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    changed = true;
                }

                if (data.getThirst() < 75.0 && tryDrinkNearbyWaterCauldron(horse.level(), horse.blockPosition())) {
                    data.setThirst(data.getThirst() + 55.0);
                    data.setFatigue(data.getFatigue() - 8.0);
                    data.setMood(data.getMood() + 2.0);
                    horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    changed = true;
                }

                if (horse.getControllingPassenger() == null && data.getHunger() > 50.0 && data.getThirst() > 50.0 && data.getFatigue() > 0.0) {
                    double restFatigueRec = data.hasPersonality(ThoroughbredData.PERS_LAID_BACK) ? 1.0 : 0.8;
                    data.setFatigue(data.getFatigue() - restFatigueRec);
                    changed = true;
                }

                double oldMood = data.getMood();
                if (data.getHunger() < 20.0 || data.getThirst() < 20.0) {
                    double penalty = (data.getHunger() <= 1.0 || data.getThirst() <= 1.0) ? 0.80 : 0.35;
                    data.setMood(oldMood - penalty);
                    changed = true;
                } else if (data.getFatigue() >= 70.0) {
                    double penalty = data.getFatigue() >= 85.0 ? 0.65 : 0.30;
                    data.setMood(oldMood - penalty);
                    changed = true;
                } else if (horse.getControllingPassenger() == null && data.getHunger() >= 75.0 && data.getThirst() >= 75.0 && data.getFatigue() <= 25.0) {
                    if (oldMood < 100.0) {
                        data.setMood(oldMood + 0.18);
                        changed = true;
                    }
                }

                if (changed) {
                    data.saveToHorse(horse);
                } else if (horse.level() instanceof ServerLevel sl) {
                    StudbookSavedData.get(sl).updateHorseRecord(horse, data);
                }
            }

            if (horse.getControllingPassenger() == null) {
                state.stopAndResetSpeed();
                boolean silkCleared = false;
                if (data.isWearingRaceSilk()) {
                    data.setWearingRaceSilk(false);
                    silkCleared = true;
                }
                boolean ailmentsCleared = !data.isInRace() && !RacingManager.isHorseInActiveRace(horse) && data.clearRaceEndAilments();
                if (silkCleared || ailmentsCleared) {
                    data.saveToHorse(horse);
                }
                state.currentStamina = Math.min(state.maxStamina, state.currentStamina + 2.5);
                if (state.exhaustedCapLocked && state.currentStamina >= state.maxStamina * 0.50) {
                    state.exhaustedCapLocked = false;
                }
                if (!state.exhaustedCapLocked) {
                    state.currentGuts = Math.min(state.maxGuts, state.currentGuts + 0.4);
                }
            }
            return;
        }

        if (isClientHorsePoorPrognosis(horse)) {
            horse.setStanding(false);
            horse.setXRot(0.0F);
            horse.xRotO = 0.0F;
            horse.yHeadRot = horse.yBodyRot;
            horse.yHeadRotO = horse.yBodyRotO;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || horse.getControllingPassenger() != mc.player || !clientHud.ridingThoroughbred) {
            return;
        }
        if (!horse.isTamed() || !horse.isSaddled()) {
            return;
        }

        // Suppress vanilla W/S/A/D movement impulses so [A] and [D] exclusively steer the horse's heading without lateral crab-walking!
        mc.player.zza = 0.0F;
        mc.player.xxa = 0.0F;
        if (mc.player.input != null) {
            mc.player.input.forwardImpulse = 0.0F;
            mc.player.input.leftImpulse = 0.0F;
        }

        // Temporarily spoof player.getYRot() during horse.tick() so AbstractHorse#tickRidden and executeRidersJump
        // use clientHorseYaw instead of snapping the horse or jump direction to the jockey's mouse yaw!
        if (!clientPlayerYawSpoofed) {
            savedClientPlayerYRot = mc.player.getYRot();
            savedClientPlayerYRotO = mc.player.yRotO;
            clientPlayerYawSpoofed = true;
        }
        mc.player.setYRot(clientHorseYaw);
        mc.player.yRotO = prevClientHorseYaw;
        applyHorseYaw(horse, prevClientHorseYaw, clientHorseYaw);

        double speedKmh = clientHud.displayedSpeedKmh;
        Vec3 currentVel = horse.getDeltaMovement();

        double vy = currentVel.y;
        if (horse.isInWater()) {
            if (mc.options.keyJump.isDown()) {
                vy = Math.max(vy, 0.14);
            } else {
                vy = Math.min(vy, -0.02);
            }
            if (mc.player.input != null) {
                mc.player.input.jumping = false;
            }
        }

        if (speedKmh <= 0.2) {
            horse.setDeltaMovement(0.0, vy, 0.0);
            return;
        }

        double blocksPerTick = speedKmh * KMH_TO_BLOCKS_PER_TICK;
        double yawRad = Math.toRadians(clientHorseYaw);
        double vx = -Math.sin(yawRad) * blocksPerTick;
        double vz =  Math.cos(yawRad) * blocksPerTick;

        // Step 15: Soft Lateral Separation Nudge when overlapping closely in a pack
        if (mc.level != null && speedKmh > 10.0 && clientRaceLeaderboard.active) {
            double fwdX = -Math.sin(yawRad);
            double fwdZ = Math.cos(yawRad);
            double latX = Math.cos(yawRad);
            double latZ = Math.sin(yawRad);

            AABB closeBox = horse.getBoundingBox().inflate(1.6, 1.0, 1.6);
            List<Horse> closePack = mc.level.getEntitiesOfClass(Horse.class, closeBox,
                    other -> other != horse && other.isAlive());

            for (Horse other : closePack) {
                double dx = other.getX() - horse.getX();
                double dz = other.getZ() - horse.getZ();
                double dFwd = dx * fwdX + dz * fwdZ;
                double dLat = dx * latX + dz * latZ;

                if (Math.abs(dFwd) < 1.6 && Math.abs(dLat) < 0.85) {
                    double pushDir = (dLat >= 0.0) ? -1.0 : 1.0;
                    double nudge = 0.022 * pushDir;
                    vx += latX * nudge;
                    vz += latZ * nudge;
                }
            }
        }

        horse.setDeltaMovement(vx, vy, vz);
    }

    private static void tickUnriddenTemperamentAndBond(Horse horse, ThoroughbredData data, HorseRunState state) {
        if (!(horse.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Step 19: Quirky Personality — Lead Refusal & Rear-Arc Kick Danger!
        if (state.quirkyKickCooldownTicks > 0) {
            state.quirkyKickCooldownTicks--;
        }
        if (state.quirkyLeadCheckCooldownTicks > 0) {
            state.quirkyLeadCheckCooldownTicks--;
        }
        if (data.hasPersonality(ThoroughbredData.PERS_QUIRKY)) {
            if (horse.isLeashed() && horse.getLeashHolder() != null) {
                if (state.quirkyLeadRefusalTicks > 0) {
                    state.quirkyLeadRefusalTicks--;
                    horse.getNavigation().stop();
                    horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
                    if (state.quirkyLeadRefusalTicks % 25 == 0) {
                        state.rearVisualTicks = 10;
                        horse.setStanding(true);
                        serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 0.9F, 1.1F);
                        serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                                horse.getX(), horse.getY() + 1.4, horse.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                    }
                    // Kick anyone standing behind the Quirky horse while it is acting up!
                    checkAndExecuteQuirkyRearKick(serverLevel, horse, data, state);
                    return;
                } else if (state.quirkyLeadCheckCooldownTicks <= 0 && horse.tickCount % 40 == 0) {
                    Entity holder = horse.getLeashHolder();
                    if (holder.distanceToSqr(horse) > 4.5 && horse.getRandom().nextDouble() < 0.22) {
                        state.quirkyLeadRefusalTicks = 80; // Refuses to move on lead for 4 seconds!
                        state.quirkyLeadCheckCooldownTicks = 200;
                        state.rearVisualTicks = 12;
                        horse.setStanding(true);
                        horse.getNavigation().stop();
                        horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
                        serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        if (holder instanceof ServerPlayer spHolder) {
                            spHolder.displayClientMessage(Component.literal(
                                    "§3§l[QUIRKY STUBBORNNESS!] §e" + data.getName() + " planted its hooves and refuses to follow the lead! Watch out behind it!"), true);
                        }
                        checkAndExecuteQuirkyRearKick(serverLevel, horse, data, state);
                        return;
                    }
                }
            } else {
                state.quirkyLeadRefusalTicks = 0;
                // Even when standing loose, a Quirky horse has a small chance to kick someone standing directly behind its hindquarters!
                if (horse.tickCount % 30 == 0 && state.quirkyKickCooldownTicks <= 0 && horse.getRandom().nextDouble() < 0.15) {
                    checkAndExecuteQuirkyRearKick(serverLevel, horse, data, state);
                }
            }
        }

        boolean highBond = data.getBond() >= 80.0;
        boolean maxBond = data.getBond() >= 100.0;

        if (!data.isVolatile()) {
            if (highBond) {
                Player targetOwner = null;
                if (data.hasOwner()) {
                    try {
                        UUID ownerUuid = UUID.fromString(data.getOwnerUUID());
                        Player p = serverLevel.getPlayerByUUID(ownerUuid);
                        if (p != null && p.isAlive() && !p.isSpectator()) {
                            targetOwner = p;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                } else {
                    targetOwner = serverLevel.getNearestPlayer(horse, 9.0);
                }

                if (targetOwner != null) {
                    double distSq = horse.distanceToSqr(targetOwner);
                    if (!state.ownerInVicinity && distSq <= 42.25) {
                        state.ownerInVicinity = true;
                        serverLevel.sendParticles(ParticleTypes.HEART,
                                horse.getX(), horse.getY() + 1.6, horse.getZ(),
                                5, 0.40, 0.30, 0.40, 0.02);
                    } else if (state.ownerInVicinity && distSq > 64.0) {
                        state.ownerInVicinity = false;
                    }
                } else {
                    state.ownerInVicinity = false;
                }
            }
            return;
        }

        if (state.haloAttackCoolTicks > 0) {
            state.haloAttackCoolTicks--;
        }

        if (!state.hostileToPlayer) {
            int checkIntervalTicks = maxBond ? 1200 : 200;
            double aggroChance = maxBond ? 0.30 : 0.50;

            if (horse.tickCount % checkIntervalTicks == 0) {
                AABB searchBox = horse.getBoundingBox().inflate(8.5, 4.0, 8.5);
                List<ServerPlayer> nearbySurvivalPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class, searchBox,
                        p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.getVehicle() != horse);

                ServerPlayer victim = null;
                double bestDistSq = Double.MAX_VALUE;
                for (ServerPlayer sp : nearbySurvivalPlayers) {
                    double dSq = horse.distanceToSqr(sp);
                    if (dSq < bestDistSq) {
                        bestDistSq = dSq;
                        victim = sp;
                    }
                }

                if (victim != null && horse.getRandom().nextDouble() < aggroChance) {
                    state.hostileToPlayer = true;
                    state.hostileTargetPlayer = victim.getUUID();
                    state.rearVisualTicks = 10;
                    horse.setStanding(true);
                    serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.3F, 0.85F);
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            horse.getX(), horse.getY() + 1.5, horse.getZ(),
                            8, 0.4, 0.4, 0.4, 0.05);
                    victim.displayClientMessage(Component.literal(
                            "§4§l[VOLATILE WRATH!] §c" + data.getName() + " turned hostile! Mount it to calm its wrath!"), true);
                }
            }
        } else {
            Player target = (state.hostileTargetPlayer != null) ? serverLevel.getPlayerByUUID(state.hostileTargetPlayer) : null;
            if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()
                    || target.distanceToSqr(horse) > 196.0 || target.getVehicle() == horse) {
                state.hostileToPlayer = false;
                state.hostileTargetPlayer = null;
                state.rearVisualTicks = 0;
                horse.setStanding(false);
                horse.getNavigation().stop();
                return;
            }

            if (state.rearVisualTicks <= 0 && horse.isStanding()) {
                horse.setStanding(false);
            }

            double dx = target.getX() - horse.getX();
            double dz = target.getZ() - horse.getZ();
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            if (horizDist > 0.05) {
                float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                horse.setYRot(targetYaw);
                horse.setYHeadRot(targetYaw);
                horse.setYBodyRot(targetYaw);
            }

            if (state.rearVisualTicks <= 0 && horizDist > 1.35) {
                horse.getNavigation().moveTo(target, 1.5D);
                double chargeSpeed = 0.34;
                double vx = (dx / horizDist) * chargeSpeed;
                double vz = (dz / horizDist) * chargeSpeed;
                double vy = horse.getDeltaMovement().y;
                if (horse.horizontalCollision && horse.onGround()) {
                    vy = 0.42D;
                }
                horse.setDeltaMovement(vx, vy, vz);
                horse.hasImpulse = true;
            }

            if (horse.tickCount % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        horse.getX(), horse.getY() + 1.5, horse.getZ(),
                        2, 0.3, 0.3, 0.3, 0.02);
            }

            double distSq = horse.distanceToSqr(target);
            if (distSq <= 6.25 && state.haloAttackCoolTicks <= 0) {
                state.haloAttackCoolTicks = 20;
                state.rearVisualTicks = 8;
                horse.setStanding(true);
                serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.1F, 0.9F);
                serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.9F);

                target.hurt(horse.damageSources().generic(), 4.0F);

                if (horizDist > 0.01) {
                    target.push((dx / horizDist) * 0.45, 0.25, (dz / horizDist) * 0.45);
                    target.hurtMarked = true;
                }
            }

            if (maxBond) {
                if (horse.tickCount % 40 == 0 && horse.getRandom().nextDouble() < 0.50) {
                    state.hostileToPlayer = false;
                    state.hostileTargetPlayer = null;
                    state.rearVisualTicks = 0;
                    horse.setStanding(false);
                    horse.getNavigation().stop();
                }
            } else {
                if (horse.tickCount % 60 == 0) {
                    boolean continueAttacking = horse.getRandom().nextDouble() < 0.30;
                    if (!continueAttacking) {
                        state.hostileToPlayer = false;
                        state.hostileTargetPlayer = null;
                        state.rearVisualTicks = 0;
                        horse.setStanding(false);
                        horse.getNavigation().stop();
                    }
                }
            }
        }
    }

    private static void checkAndExecuteQuirkyRearKick(ServerLevel serverLevel, Horse horse, ThoroughbredData data, HorseRunState state) {
        if (state.quirkyKickCooldownTicks > 0) return;
        double yawRad = Math.toRadians(horse.getYRot());
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);

        AABB rearBox = horse.getBoundingBox().inflate(2.8, 1.5, 2.8);
        List<net.minecraft.world.entity.LivingEntity> behindTargets = serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, rearBox,
                ent -> ent != horse && ent.isAlive()
                        && !(ent instanceof Player p && (p.isCreative() || p.isSpectator()))
                        && ent.getVehicle() != horse);

        for (net.minecraft.world.entity.LivingEntity target : behindTargets) {
            double dx = target.getX() - horse.getX();
            double dz = target.getZ() - horse.getZ();
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            if (horizDist < 0.3 || horizDist > 2.8) continue;
            // Dot product with forward vector < -0.25 means the entity is in the rear cone behind the horse!
            double dotFwd = (dx * fwdX + dz * fwdZ) / horizDist;
            if (dotFwd < -0.25) {
                state.quirkyKickCooldownTicks = 50;
                state.rearVisualTicks = 8;
                horse.setStanding(true);
                serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.1F, 0.95F);
                serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.85F);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.3, 0.3, 0.3, 0.15);

                target.hurt(horse.damageSources().mobAttack(horse), 4.0F);
                target.push((dx / horizDist) * 0.65, 0.32, (dz / horizDist) * 0.65);
                target.hurtMarked = true;

                if (target instanceof ServerPlayer spTarget) {
                    spTarget.displayClientMessage(Component.literal(
                            "§3§l[QUIRKY HIND KICK!] §c" + data.getName() + " kicked you for standing behind it!"), true);
                }
                break;
            }
        }
    }

    private static boolean tryEatNearbyHayBale(Level level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(Blocks.HAY_BLOCK)) {
                        level.destroyBlock(pos, false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryDrinkNearbyWaterCauldron(Level level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.WATER_CAULDRON)) {
                        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void onClientClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        if (player.getMainHandItem().is(ModItems.RACING_CROP.get())) {
            event.setCanceled(true);
            event.setSwingHand(true);

            long now = System.currentTimeMillis();
            if (now - lastClientCropClickTime >= 150 && player.getVehicle() instanceof Horse) {
                lastClientCropClickTime = now;
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.UseCropPacket());
            }
        } else if (player.getMainHandItem().is(ModItems.TRAINER_STOPWATCH.get())) {
            event.setCanceled(true);
            event.setSwingHand(true);
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.UseStopwatchPacket());
        } else if (player.getMainHandItem().is(ModItems.SEMAPHORE_FLAG.get())) {
            event.setCanceled(true);
            event.setSwingHand(true);
            long now = System.currentTimeMillis();
            if (now - lastClientFlagWaveTime >= 180) {
                lastClientFlagWaveTime = now;
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.WaveSemaphoreFlagPacket());
            }
        } else if (player.getMainHandItem().is(ModItems.CHECKPOINT_MARKER.get())) {
            event.setCanceled(true);
            event.setSwingHand(true);
            if (player.isShiftKeyDown()) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.MarkerUndoPacket());
            }
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().is(ModItems.RACING_CROP.get())
                || event.getEntity().getMainHandItem().is(ModItems.TRAINER_STOPWATCH.get())
                || event.getEntity().getMainHandItem().is(ModItems.SEMAPHORE_FLAG.get())
                || event.getEntity().getMainHandItem().is(ModItems.CHECKPOINT_MARKER.get())) {
            event.setCanceled(true);
        }
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getMainHandItem().is(ModItems.RACING_CROP.get())
                || event.getEntity().getMainHandItem().is(ModItems.TRAINER_STOPWATCH.get())
                || event.getEntity().getMainHandItem().is(ModItems.SEMAPHORE_FLAG.get())
                || event.getEntity().getMainHandItem().is(ModItems.CHECKPOINT_MARKER.get())) {
            event.setCanceled(true);
        }
    }

    public static void handleRiderInput(ServerPlayer player, boolean pressingW, boolean pressingS,
                                        boolean pressingA, boolean pressingD,
                                        boolean clientJumping, float jumpScale, float horseYaw) {
        if (!(player.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            return;
        }
        HorseRunState state = getOrCreateState(horse);
        boolean wasPressingW = state.pressingW;
        state.pressingW = pressingW;
        state.pressingS = pressingS;
        state.pressingA = pressingA;
        state.pressingD = pressingD;

        if (state.inStartingGate) {
            state.horseYaw = state.gateLockedYaw;
            state.prevHorseYaw = state.gateLockedYaw;
            state.horseYawInitialized = true;
        } else {
            if (!state.horseYawInitialized) {
                state.prevHorseYaw = horseYaw;
                state.horseYaw = horseYaw;
                state.horseYawInitialized = true;
            } else {
                state.prevHorseYaw = state.horseYaw;
                state.horseYaw = horseYaw;
            }
        }

        applyHorseYaw(horse, state.prevHorseYaw, state.horseYaw);

        if (state.inStartingGate && pressingW) {
            state.wHeldBeforeGateOpen = true;
        }
        if (!pressingW && state.wHeldBeforeGateOpen) {
            state.wHeldBeforeGateOpen = false;
        }

        if (state.trainingMode && pressingW && !state.inStartingGate) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            if (!state.trainingRefusalActive && !wasPressingW && state.baseSpeedKmh < 2.0) {
                double refusalChance = data.getTrainingMoveRefusalChance();
                if (refusalChance > 0.0 && horse.getRandom().nextDouble() < refusalChance) {
                    triggerTrainingMoveRefusal(player, horse, data, state);
                }
            }
            if (state.trainingRefusalActive && state.refusalRearSoundCooldown <= 0) {
                state.refusalRearSoundCooldown = 30;
                state.rearVisualTicks = 16;
                horse.setStanding(true);
                player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.05F);
                player.displayClientMessage(Component.literal(
                        "§c§l[REFUSING TO TRAIN!] §e" + data.getName() + " refuses to move!"), true);
            }
        }

        if (state.gateBreakActive && pressingW && state.gateRearFreezeTicks <= 0) {
            if (state.wHeldBeforeGateOpen || state.penalizedPreWindThisStart) {
                state.gateBreakActive = false;
                state.gateBreakAccelMultiplier = 1.0;
                player.displayClientMessage(Component.literal("§c§l[PRE-WIND LOCK!] §eRelease [W] and press again to move (No Boost)!"), true);
            } else {
                triggerGateBreak(player, horse, state);
            }
        }

        if (state.trainingMode && !state.trainingRefusalActive && !horse.isInWater() && state.lastJumpScale >= 0.70F && jumpScale < 0.05F && state.baseSpeedKmh >= 20.0) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            if (data.getHunger() > 0.5 && data.getThirst() > 0.5) {
                double fatigueMult = getFatigueExpMultiplier(data.getFatigue());
                double bondTempMult = data.getTemperamentBondExpMultiplier() * data.getMoodExpMultiplier();
                double pwrJumpExp = 10.0 * state.lastJumpScale * fatigueMult * bondTempMult * (data.hasWeightedTack() ? 1.30 : 1.0);
                double witsJumpExp = 6.5 * state.lastJumpScale * fatigueMult * bondTempMult * (data.hasBlinkersHood() ? 1.20 : 1.0);
                if (data.hasEnchantedAppleBlessing()) {
                    pwrJumpExp *= 1.20;
                    witsJumpExp *= 1.20;
                }
                boolean pwrUp = data.addPowerExp(pwrJumpExp);
                boolean witsUp = data.addWitsExp(witsJumpExp);
                data.saveToHorse(horse);
                if (pwrUp) triggerLevelUpFlash(state, 3, player, horse);
                else if (witsUp) triggerLevelUpFlash(state, 5, player, horse);
            }
        }
        state.lastJumpScale = jumpScale;
        state.clientJumping = clientJumping;
    }

    private static void triggerTrainingMoveRefusal(ServerPlayer player, Horse horse, ThoroughbredData data, HorseRunState state) {
        state.trainingRefusalActive = true;
        state.refusalResumeTimerTicks = 200;
        state.refusalRearSoundCooldown = 30;
        state.rearVisualTicks = 18;
        state.baseSpeedKmh = 0.0;
        state.displayedSpeedKmh = 0.0;
        horse.setStanding(true);
        player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.1F, 1.0F);
        player.displayClientMessage(Component.literal(
                "§c§l[REFUSING TO TRAIN!] §e" + data.getName() + " planted its hooves and refuses to move!"), true);
    }

    public static void triggerGateBreak(ServerPlayer player, Horse horse, HorseRunState state) {
        if (!state.gateBreakActive) return;
        state.gateBreakActive = false;

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);

        double rawSec = Math.max(0.04, state.gateBreakTicks / 20.0);
        double effWits = data.getWitsCurrent() * data.getMoodStatMultiplier();
        double witsBonus = (effWits / 1000.0) * 0.08;
        double lightningBonus = data.hasGeneticTrait(ThoroughbredData.TRAIT_LIGHTNING_STARTER) ? 0.10 : 0.0;
        double effectiveSec = Math.max(0.02, rawSec - witsBonus - lightningBonus);
        double lightningAccelBoost = data.hasGeneticTrait(ThoroughbredData.TRAIT_LIGHTNING_STARTER) ? 1.15 : 1.0;

        double fatigueMult = getFatigueExpMultiplier(data.getFatigue());
        double blinkersMult = data.hasBlinkersHood() ? 1.20 : 1.0;
        double appleBoost = data.hasEnchantedAppleBlessing() ? 1.20 : 1.0;
        double bondTempMult = data.getTemperamentBondExpMultiplier() * data.getMoodExpMultiplier();
        double totalExpMult = fatigueMult * blinkersMult * appleBoost * bondTempMult;

        if (effectiveSec < 0.30) {
            state.gateBreakAccelMultiplier = 2.40 * lightningAccelBoost;
            double witsExp = 25.0 * totalExpMult;

            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.2F);

            String title = String.format(Locale.ROOT, "PERFECT START! (%.2fs)", rawSec);
            state.gateBreakResult = title;
            state.gateBreakResultColor = 0x55FF55;
            state.gateBreakResultTicks = 60;

            if (state.trainingMode) {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§a§l[PERFECT START! %.2fs] §eStrong 0-35 km/h acceleration boost! +%.1f Wits EXP!", rawSec, witsExp)));
                if (data.hasEnchantedAppleBlessing() || (data.getHunger() > 0.1 && data.getThirst() > 0.1)) {
                    if (data.addWitsExp(witsExp)) {
                        triggerLevelUpFlash(state, 5, player, horse);
                    }
                    data.saveToHorse(horse);
                }
            } else {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§a§l[PERFECT START! %.2fs] §eStrong 0-35 km/h acceleration boost!", rawSec)));
            }
        } else if (effectiveSec <= 0.60) {
            state.gateBreakAccelMultiplier = 1.75 * lightningAccelBoost;
            double witsExp = 14.0 * totalExpMult;

            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.4F);

            String title = String.format(Locale.ROOT, "GOOD START! (%.2fs)", rawSec);
            state.gateBreakResult = title;
            state.gateBreakResultColor = 0xFFFF55;
            state.gateBreakResultTicks = 50;

            if (state.trainingMode) {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§e§l[GOOD START! %.2fs] §fModerate 0-35 km/h acceleration boost! +%.1f Wits EXP!", rawSec, witsExp)));
                if (data.hasEnchantedAppleBlessing() || (data.getHunger() > 0.1 && data.getThirst() > 0.1)) {
                    if (data.addWitsExp(witsExp)) {
                        triggerLevelUpFlash(state, 5, player, horse);
                    }
                    data.saveToHorse(horse);
                }
            } else {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§e§l[GOOD START! %.2fs] §fModerate 0-35 km/h acceleration boost!", rawSec)));
            }
        } else if (effectiveSec <= 1.00) {
            state.gateBreakAccelMultiplier = 1.25 * lightningAccelBoost;
            double witsExp = 4.0 * totalExpMult;

            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 0.9F);

            String title = String.format(Locale.ROOT, "LATE START! (%.2fs)", rawSec);
            state.gateBreakResult = title;
            state.gateBreakResultColor = 0xFFAA00;
            state.gateBreakResultTicks = 40;

            if (state.trainingMode) {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§6§l[LATE START! %.2fs] §7Small 0-35 km/h acceleration boost. +%.1f Wits EXP", rawSec, witsExp)));
                if (data.hasEnchantedAppleBlessing() || (data.getHunger() > 0.1 && data.getThirst() > 0.1)) {
                    if (data.addWitsExp(witsExp)) {
                        triggerLevelUpFlash(state, 5, player, horse);
                    }
                    data.saveToHorse(horse);
                }
            } else {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "§6§l[LATE START! %.2fs] §7Small 0-35 km/h acceleration boost.", rawSec)));
            }
        }
    }

    public static void handleToggleTrainingMode(ServerPlayer player) {
        if (!(player.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            return;
        }
        HorseRunState state = getOrCreateState(horse);
        ThoroughbredData data = ThoroughbredData.fromHorse(horse);

        if (data.isUnderTwoEquineYears()) {
            state.trainingMode = false;
            state.trainingRefusalActive = false;
            String stage = data.isFoal() ? "a Foal (under 1 Equine Year old)" : "a Yearling (1 Equine Year old)";
            player.displayClientMessage(Component.literal(
                    "§e" + data.getName() + " is still " + stage + " — minimum riding and training age is 2 Equine Years old!"), true);
            return;
        }
        if (data.isRetired()) {
            state.trainingMode = false;
            state.trainingRefusalActive = false;
            player.displayClientMessage(Component.literal(
                    "§e" + data.getName() + " is retired from racing and cannot enter Training Mode!"), true);
            return;
        }
        if (data.hasPoorPrognosis() || data.hasActiveInjury() || data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
            state.trainingMode = false;
            state.trainingRefusalActive = false;
            player.displayClientMessage(Component.literal(
                    "§cTraining Mode is locked out while " + data.getName() + " has Lameness or an Injury!"), true);
            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 0.8F, 0.7F);
            return;
        }

        // Block enabling Training Mode while inside a starting gate or participating in an active race!
        if (state.inStartingGate || data.isInRace() || RacingManager.isHorseInActiveRace(horse)) {
            state.trainingMode = false;
            state.trainingRefusalActive = false;
            player.displayClientMessage(Component.literal(
                    "§cYou cannot activate Training Mode while in a starting gate or during a race!"), true);
            player.sendSystemMessage(Component.literal(
                    "§c[Race in Progress] Training Mode cannot be activated until the race ends!"));
            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.PLAYERS, 0.8F, 0.7F);
            return;
        }

        state.trainingMode = !state.trainingMode;
        state.rearVisualTicks = 0;
        horse.setStanding(false);
        if (state.trainingMode) {
            state.trainingSessionTicks = 0;
            state.witsCropChain = 0;
            state.trainingRefusalActive = false;
            player.displayClientMessage(Component.literal("§a§l[Training Mode Activated] §7Press [G] again to exit."), true);
            player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        } else {
            state.trainingRefusalActive = false;
            player.displayClientMessage(Component.literal("§e[Training Mode Deactivated]"), true);
        }
    }

    private static void triggerLevelUpFlash(HorseRunState state, int statCode, ServerPlayer player, Horse horse) {
        state.levelUpStatCode = statCode;
        state.levelUpFlashTicks = 50;
        player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.5F);
    }

    private static double getFatigueExpMultiplier(double fatigue) {
        if (fatigue <= 40.0) {
            return 1.0;
        } else if (fatigue <= 75.0) {
            return 1.0 - 0.50 * ((fatigue - 40.0) / 35.0);
        } else {
            return Math.max(0.10, 0.50 - 0.40 * ((fatigue - 75.0) / 25.0));
        }
    }

    // =========================================================================
    // 3. WITS & TEMPERAMENT RACING CROP EFFORT & PANIC
    // =========================================================================
    public static void handleServerCropSignal(ServerPlayer player) {
        if (!(player.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            return;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        HorseRunState state = getOrCreateState(horse);

        if (state.cropCooldownTicks > 0) {
            return;
        }
        state.cropCooldownTicks = 5;

        if (data.hasPoorPrognosis() || data.hasActiveInjury() || data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
            player.displayClientMessage(Component.literal(
                    "§c" + data.getName() + " is lame/injured! Racing Mode and the Racing Crop are locked out!"), true);
            return;
        }

        int effectiveMin = data.getEffectiveMinDistance();
        if (state.distanceRun < effectiveMin) {
            player.displayClientMessage(
                    Component.literal("§e" + data.getName() + "'s body hasn't primed up for the racing crop yet!"),
                    true
            );
            return;
        }

        if (state.exhaustedCapLocked) {
            player.displayClientMessage(
                    Component.literal(String.format(Locale.ROOT,
                            "§c%s is exhausted! Recover Stamina to 50%% (%.0f) under 35 km/h first.",
                            data.getName(), state.maxStamina * 0.5)),
                    true
            );
            return;
        }

        if (state.gutsActive) {
            return;
        }

        double witsNorm = Mth.clamp((data.getWitsCurrent() * data.getMoodStatMultiplier()) / 1000.0, 0.0, 1.1);
        boolean recklessTiming = state.ticksSinceLastCrop < CROP_RHYTHM_TICKS;
        state.ticksSinceLastCrop = 0;

        RacingManager.RaceStrategyContext cropRaceCtx = RacingManager.getRunnerStrategyContext(horse);
        int cropEvalPos = -1;
        int cropStepsOff = 0;
        boolean cropInHomestretch = false;
        if (cropRaceCtx.inActiveRace && cropRaceCtx.pastFiveSecondStartWindow && !cropRaceCtx.finished) {
            cropInHomestretch = cropRaceCtx.remainingMeters <= 400.0;
            cropEvalPos = (cropInHomestretch && state.midRaceEstablishedPos >= 0)
                    ? state.midRaceEstablishedPos
                    : (state.activeRaceStrategyPos >= 0 ? state.activeRaceStrategyPos : ThoroughbredData.POS_PACE_CHASER);
            cropStepsOff = (cropInHomestretch && state.midRaceEstablishedPos >= 0)
                    ? state.midRaceStepsOff
                    : data.getStrategyStepsOff(cropEvalPos);
        }

        double tempPanicMult = data.getCropPanicMultiplier();
        if (data.hasPersonality(ThoroughbredData.PERS_STOIC)) {
            tempPanicMult *= 0.75; // Stoic: -25% Crop panic chance!
        }
        if (cropStepsOff == 1) {
            tempPanicMult *= data.hasPersonality(ThoroughbredData.PERS_STOIC) ? 1.15 : 1.25;
        } else if (cropStepsOff >= 2) {
            tempPanicMult *= 1.55;
        }

        if (recklessTiming) {
            state.recklessCropStreak++;
            double panicChance = ((0.55 - 0.42 * witsNorm) + (state.recklessCropStreak * 0.25)) * tempPanicMult;
            if (data.hasBlinkersHood()) {
                panicChance *= 0.65;
            }
            if (horse.getRandom().nextDouble() < panicChance) {
                state.panicTicks = (int) ((90 - 40 * witsNorm) * Math.max(1.0, tempPanicMult * 0.85));
                state.witsCropChain = 0;
                data.setMood(data.getMood() - 1.5);
                data.saveToHorse(horse);
                player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_ANGRY, SoundSource.PLAYERS, 0.8F, 1.15F);
                return;
            }
        } else {
            state.recklessCropStreak = Math.max(0, state.recklessCropStreak - 1);
            if (!data.hasBlinkersHood() && data.getBond() < 60.0) {
                int tIdx = ThoroughbredData.getTemperamentIndex(data.getTemperament());
                double bristleChance = (tIdx == 4) ? 0.06 : (tIdx == 3 ? 0.03 : 0.0);
                if (bristleChance > 0.0 && horse.getRandom().nextDouble() < bristleChance) {
                    state.panicTicks = 50;
                    state.witsCropChain = 0;
                    player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_ANGRY, SoundSource.PLAYERS, 0.8F, 1.2F);
                    return;
                }
            }
        }

        if (state.trainingMode && !state.trainingRefusalActive && data.getHunger() > 0.5 && data.getThirst() > 0.5) {
            if (!recklessTiming) {
                state.witsCropChain = Math.min(8, state.witsCropChain + 1);
                double chainMult = 1.0 + (state.witsCropChain * 0.35);
                double bondTempMult = data.getTemperamentBondExpMultiplier() * data.getMoodExpMultiplier();
                double witsGain = 6.0 * chainMult * getFatigueExpMultiplier(data.getFatigue()) * bondTempMult
                        * (data.hasBlinkersHood() ? 1.20 : 1.0);
                if (data.hasEnchantedAppleBlessing()) {
                    witsGain *= 1.20;
                }
                if (data.addWitsExp(witsGain)) {
                    triggerLevelUpFlash(state, 5, player, horse);
                }
                data.saveToHorse(horse);
            } else {
                state.witsCropChain = 0;
            }
        }

        double minEffortStep = 0.20 + (0.45 * witsNorm);
        double maxEffortStep = 0.32 + (0.68 * witsNorm);
        double effortGain = minEffortStep + horse.getRandom().nextDouble() * (maxEffortStep - minEffortStep);

        if (recklessTiming) {
            effortGain *= 0.45;
        }

        if (cropEvalPos >= 0) {
            if (cropStepsOff == 0 && cropInHomestretch) {
                if (cropEvalPos == ThoroughbredData.POS_LATE_SURGER) {
                    effortGain *= 1.25;
                } else if (cropEvalPos == ThoroughbredData.POS_END_CLOSER) {
                    effortGain *= 1.35;
                } else if (cropEvalPos == ThoroughbredData.POS_PACE_CHASER) {
                    effortGain *= 1.10;
                }
            } else if (cropStepsOff == 1) {
                effortGain *= 0.80;
            } else if (cropStepsOff >= 2) {
                effortGain *= 0.65;
            }
        }

        if (data.hasPersonality(ThoroughbredData.PERS_COMPETITIVE) && isBreezingInCompany(horse)) {
            effortGain *= 1.10; // Competitive: +10% Crop response when battling in company!
        }
        state.cropEffortRatio = Math.min(1.0, state.cropEffortRatio + effortGain);

        player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    // =========================================================================
    // 4. TRACK APTITUDE TIERS & RAIN
    // =========================================================================
    private static class TrackConditionModifiers {
        final double speedCapDeltaKmh;
        final double powerMultiplier;
        final double staminaDrainMod;

        TrackConditionModifiers(double speedCapDeltaKmh, double powerMultiplier, double staminaDrainMod) {
            this.speedCapDeltaKmh = speedCapDeltaKmh;
            this.powerMultiplier = powerMultiplier;
            this.staminaDrainMod = staminaDrainMod;
        }
    }

    private static TrackConditionModifiers evaluateTrackAptitude(int rating) {
        int r = Mth.clamp(rating, 0, 10);
        if (r == 8) {
            return new TrackConditionModifiers(0.0, 1.00, 1.00);
        } else if (r >= 9) {
            int stepsAbove8 = r - 8;
            return new TrackConditionModifiers(stepsAbove8 * 0.30, 1.00 + (stepsAbove8 * 0.05), 1.00 - (stepsAbove8 * 0.05));
        } else if (r >= 5) {
            int stepsBelow8 = 8 - r;
            return new TrackConditionModifiers(-stepsBelow8 * 0.25, 1.00 - (stepsBelow8 * 0.05), 1.00 + (stepsBelow8 * 0.06));
        } else {
            int stepsBelow4 = 4 - r;
            return new TrackConditionModifiers(-1.10 - (stepsBelow4 * 0.30), 0.78 - (stepsBelow4 * 0.04), 1.25 + (stepsBelow4 * 0.06));
        }
    }

    private static double calculateSprinterBaselineFactor(int minDist, int maxDist) {
        double avgDist = (minDist + maxDist) / 2.0;
        int span = Math.max(0, maxDist - minDist);

        double distFactor = Mth.clamp((2700.0 - avgDist) / 1200.0, 0.0, 1.0);
        if (span > 800) {
            double broadPenalty = Mth.clamp((span - 800) / 1600.0, 0.0, 0.35);
            distFactor = Math.max(0.0, distFactor * (1.0 - broadPenalty));
        }
        return distFactor;
    }

    /**
     * Automatically detects which of the 5 running strategies (0..4) a horse is executing in an active race:
     *   0 = Big Escape (Oonige / 大逃げ)
     *   1 = Front Runner (逃げ)
     *   2 = Pace Chaser (先行)
     *   3 = Late Surger (差し)
     *   4 = End Closer (追込)
     *
     * - In Multi-Horse Races (totalRunners >= 2): detected from live rank, lead over 2nd place (in horse lengths),
     *   and gap behind the leader / main pack leader (in horse lengths).
     * - In Solo 1-Horse Test Races (totalRunners == 1): detected from the jockey's cruising pace ratio relative to
     *   the horse's Normal Speed Cap (35.0 km/h to baseNormalCapKmh).
     */
    private static int detectLiveRunningStrategyPos(ThoroughbredData data, HorseRunState state,
                                                    RacingManager.RaceStrategyContext ctx, double baseNormalCapKmh) {
        if (ctx.totalRunners >= 2) {
            if (ctx.rank == 1) {
                if (ctx.lengthsAheadOfSecond >= 5.0) {
                    return ThoroughbredData.POS_BIG_ESCAPE;
                }
                return ThoroughbredData.POS_FRONT_RUNNER;
            }

            double gap = ctx.lengthsBehindPackLeader;
            double rankRatio = (ctx.rank - 1.0) / Math.max(1.0, ctx.totalRunners - 1.0);

            if (ctx.rank == 2 && ctx.lengthsBehindLeader >= 6.0 && gap <= 1.2) {
                return ThoroughbredData.POS_FRONT_RUNNER;
            }
            if (gap <= 1.2 && rankRatio <= 0.25) {
                return ThoroughbredData.POS_FRONT_RUNNER;
            }
            if (gap <= 4.5 && rankRatio <= 0.55) {
                return ThoroughbredData.POS_PACE_CHASER;
            }
            if (gap <= 9.0 && rankRatio <= 0.82) {
                return ThoroughbredData.POS_LATE_SURGER;
            }
            return ThoroughbredData.POS_END_CLOSER;
        }

        // Solo 1-Horse Race: detect from cruising speed relative to Normal Cap (35.0 km/h -> baseNormalCapKmh)
        double span = Math.max(6.0, baseNormalCapKmh - STAMINA_THRESHOLD_KMH);
        double paceRatio = (state.baseSpeedKmh - STAMINA_THRESHOLD_KMH) / span;

        if (state.baseSpeedKmh >= baseNormalCapKmh - 0.5 || state.cropEffortRatio > 0.20) {
            if (data.isInnateBigEscapeRunner() || state.baseSpeedKmh > baseNormalCapKmh + 0.35) {
                return ThoroughbredData.POS_BIG_ESCAPE;
            }
            return ThoroughbredData.POS_FRONT_RUNNER;
        }
        if (paceRatio >= 0.85) {
            return ThoroughbredData.POS_FRONT_RUNNER;
        }
        if (paceRatio >= 0.62) {
            return ThoroughbredData.POS_PACE_CHASER;
        }
        if (paceRatio >= 0.35) {
            return ThoroughbredData.POS_LATE_SURGER;
        }
        return ThoroughbredData.POS_END_CLOSER;
    }

    // =========================================================================
    // 5. SERVER-SIDE STAT HARMONY, MOOD STAT SCALING & 1:1 BLOCK DISTANCE METER
    // =========================================================================
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // On Client: immediately restore player's mouse yaw after horse.tick() finishes and re-apply clientHorseYaw!
        if (event.player.level().isClientSide) {
            if (clientPlayerYawSpoofed) {
                event.player.setYRot(savedClientPlayerYRot);
                event.player.yRotO = savedClientPlayerYRotO;
                clientPlayerYawSpoofed = false;
            }
            if (event.player.getVehicle() instanceof Horse horse && clientHud.ridingThoroughbred) {
                applyHorseYaw(horse, prevClientHorseYaw, clientHorseYaw);
            }
            return;
        }

        if (!(event.player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        UUID playerUuid = serverPlayer.getUUID();

        // Restore any server-side spoofed player yaw immediately when player.tick() starts (right after horse.tick())!
        Float savedYRot = SERVER_SAVED_PLAYER_YROT.remove(playerUuid);
        Float savedYRotO = SERVER_SAVED_PLAYER_YROTO.remove(playerUuid);
        if (savedYRot != null) {
            serverPlayer.setYRot(savedYRot);
        }
        if (savedYRotO != null) {
            serverPlayer.yRotO = savedYRotO;
        }

        if (event.phase == TickEvent.Phase.START) {
            if (serverPlayer.getVehicle() instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                HorseRunState state = getOrCreateState(horse);
                float targetYaw = state.inStartingGate
                        ? state.gateLockedYaw
                        : (state.horseYawInitialized ? state.horseYaw : horse.getYRot());
                float prevYaw = state.inStartingGate
                        ? state.gateLockedYaw
                        : (state.horseYawInitialized ? state.prevHorseYaw : targetYaw);
                applyHorseYaw(horse, prevYaw, targetYaw);
            }
            return;
        }

        if (!(serverPlayer.getVehicle() instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
            UUID prevHorseId = PLAYER_LAST_MOUNT.remove(playerUuid);
            if (prevHorseId != null) {
                HorseRunState prevState = ACTIVE_RUNS.get(prevHorseId);
                if (prevState != null) {
                    prevState.stopAndResetSpeed();
                }
                ClientHudData offHud = new ClientHudData();
                offHud.ridingThoroughbred = false;
                ModNetwork.sendHudSync(serverPlayer, offHud);
            } else if (serverPlayer.tickCount % 10 == 0) {
                ClientHudData offHud = new ClientHudData();
                offHud.ridingThoroughbred = false;
                ModNetwork.sendHudSync(serverPlayer, offHud);
            }
            return;
        }

        ThoroughbredData data = ThoroughbredData.fromHorse(horse);
        HorseRunState state = getOrCreateState(horse);

        // Keep data.inRace in sync with active races, and forcibly keep Training Mode disabled during any race or gate lock-in!
        boolean activelyInRaceSession = RacingManager.isHorseInActiveRace(horse);
        if (data.isInRace() && !activelyInRaceSession && !state.inStartingGate) {
            data.setInRace(false);
            data.saveToHorse(horse);
        }
        if (data.hasPoorPrognosis()) {
            horse.ejectPassengers();
            state.stopAndResetSpeed();
            return;
        }
        if (data.hasActiveInjury() || data.hasAilment(ThoroughbredData.AILMENT_LAMENESS)) {
            if (state.trainingMode) {
                state.trainingMode = false;
                state.trainingRefusalActive = false;
            }
        }
        if (state.inStartingGate || activelyInRaceSession || data.isInRace()) {
            if (state.trainingMode) {
                state.trainingMode = false;
                state.trainingRefusalActive = false;
            }
        }

        UUID lastMounted = PLAYER_LAST_MOUNT.put(playerUuid, horse.getUUID());
        if (!horse.getUUID().equals(lastMounted)) {
            state.stopAndResetSpeed();
            state.horseYaw = horse.getYRot();
            state.prevHorseYaw = horse.getYRot();
            state.horseYawInitialized = true;
            horse.setStanding(false);
            if (data.hasOwner() && data.getOwnerSilkCode().isBlank()) {
                data.saveToHorse(horse);
            } else {
                ModNetwork.broadcastHorseCondition(horse, data);
            }
        } else if (serverPlayer.tickCount % 20 == 0) {
            ModNetwork.broadcastHorseCondition(horse, data);
        }

        // Enforce server-side horseYaw (or gateLockedYaw when in starting stall)
        float enforcedYaw = state.inStartingGate
                ? state.gateLockedYaw
                : (state.horseYawInitialized ? state.horseYaw : horse.getYRot());
        float enforcedPrevYaw = state.inStartingGate
                ? state.gateLockedYaw
                : (state.horseYawInitialized ? state.prevHorseYaw : enforcedYaw);
        applyHorseYaw(horse, enforcedPrevYaw, enforcedYaw);

        if (!horse.isTamed() || !horse.isSaddled()) {
            return;
        }

        state.maxStamina = data.getMaxStaminaPool();
        state.maxGuts = data.getMaxGutsPool();
        if (!state.initializedPools) {
            state.currentStamina = state.maxStamina;
            state.currentGuts = state.maxGuts;
            state.initializedPools = true;
        }

        double dx = Double.isNaN(state.lastX) ? 0.0 : (horse.getX() - state.lastX);
        double dy = Double.isNaN(state.lastY) ? 0.0 : (horse.getY() - state.lastY);
        double dz = Double.isNaN(state.lastZ) ? 0.0 : (horse.getZ() - state.lastZ);
        state.lastX = horse.getX();
        state.lastY = horse.getY();
        state.lastZ = horse.getZ();

        double rawBlocksMoved = Math.sqrt(dx * dx + dz * dz);
        if (rawBlocksMoved > 8.0) {
            rawBlocksMoved = 0.0;
            dy = 0.0;
        }

        state.tickCounter++;
        if (state.trainingMode) {
            state.trainingSessionTicks++;
        }
        if (state.levelUpFlashTicks > 0) {
            state.levelUpFlashTicks--;
        }
        if (state.refusalRearSoundCooldown > 0) {
            state.refusalRearSoundCooldown--;
        }

        if (state.trainingMode && !state.inStartingGate) {
            if (state.trainingRefusalActive) {
                state.baseSpeedKmh = 0.0;
                state.displayedSpeedKmh = 0.0;
                horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);

                state.refusalResumeTimerTicks--;
                if (state.refusalResumeTimerTicks <= 0) {
                    state.refusalResumeTimerTicks = 200;
                    if (horse.getRandom().nextDouble() < 0.20) {
                        state.trainingRefusalActive = false;
                        state.rearVisualTicks = 0;
                        horse.setStanding(false);
                        horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.0F, 1.1F);
                        serverPlayer.displayClientMessage(Component.literal(
                                "§a§l[Training Resumed] §f" + data.getName() + " calmed down and is ready to move!"), true);
                    } else {
                        state.rearVisualTicks = 18;
                        horse.setStanding(true);
                        horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 0.9F, 1.0F);
                        serverPlayer.displayClientMessage(Component.literal(
                                "§c§l[Still Refusing!] §e" + data.getName() + " is still stubbornly refusing to move..."), true);
                    }
                }
            } else if (state.pressingW && state.trainingSessionTicks > 0 && state.trainingSessionTicks % 200 == 0) {
                double refusalChance = data.getTrainingMoveRefusalChance();
                if (refusalChance > 0.0 && horse.getRandom().nextDouble() < refusalChance) {
                    triggerTrainingMoveRefusal(serverPlayer, horse, data, state);
                }
            }
        } else {
            state.trainingRefusalActive = false;
        }

        if (state.inStartingGate) {
            state.baseSpeedKmh = 0.0;
            state.displayedSpeedKmh = 0.0;
            state.gateBreakAccelMultiplier = 1.0;
            horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
            if (state.pressingW) {
                state.wHeldBeforeGateOpen = true;
            }
        } else if (state.gateRearFreezeTicks > 0) {
            state.gateRearFreezeTicks--;
            state.baseSpeedKmh = 0.0;
            state.displayedSpeedKmh = 0.0;
            state.gateBreakAccelMultiplier = 1.0;
            horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
            if (state.gateRearFreezeTicks == 0) {
                state.rearVisualTicks = 0;
                horse.setStanding(false);
            }
        } else if (state.wHeldBeforeGateOpen) {
            state.baseSpeedKmh = 0.0;
            state.displayedSpeedKmh = 0.0;
            state.gateBreakAccelMultiplier = 1.0;
            state.gateBreakActive = false;
            state.penalizedPreWindThisStart = true;
            horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);
            serverPlayer.displayClientMessage(Component.literal("§c§l[PRE-WIND LOCK!] §eRelease [W] and press again to move (No Boost)!"), true);
        }

        if (state.gateBreakActive) {
            state.gateBreakTicks++;
            if (state.gateBreakTicks >= 20) {
                state.gateBreakActive = false;
                state.gateBreakAccelMultiplier = 1.0;
                state.gateBreakResult = "MISSED START! (>1.0s)";
                state.gateBreakResultColor = 0xFF5555;
                state.gateBreakResultTicks = 40;
                serverPlayer.sendSystemMessage(Component.literal("§c§l[MISSED START! > 1.0s] §7Gate break window closed (No Boost, 0 Wits EXP)."));
            }
        }
        if (state.gateBreakResultTicks > 0) {
            state.gateBreakResultTicks--;
        }
        if (state.cropCooldownTicks > 0) state.cropCooldownTicks--;
        if (state.ticksSinceLastCrop < 1000) state.ticksSinceLastCrop++;
        if (state.ticksSinceLastCrop > 90) {
            state.witsCropChain = 0;
        }
        if (state.bigStrideTicks > 0) state.bigStrideTicks--;
        if (state.unevenBreathingTicks > 0) state.unevenBreathingTicks--;
        if (state.panicTicks > 0) state.panicTicks--;

        if (data.isOwnedBy(playerUuid)) {
            data.setLastOwnerInteractionMcDay(horse.level().getDayTime() / 24000L);
        }

        double moodStatMult = data.getMoodStatMultiplier();
        double feverStatMult = data.hasAilment(ThoroughbredData.AILMENT_FEVER) ? 0.50 : 1.0;
        double unstableLegsMult = data.hasAilment(ThoroughbredData.AILMENT_UNSTABLE_LEGS) ? 0.93 : 1.0;

        // Crowd Pleaser: +2.5% stat boost in Graded Stakes (G1/G2/G3) AND +2.5% when 2+ players/spectators are within 48 blocks!
        double crowdPleaserMult = 1.0;
        if (data.hasPersonality(ThoroughbredData.PERS_CROWD_PLEASER)) {
            if (RacingManager.isHorseInGradedStakesRace(horse)) {
                crowdPleaserMult += 0.025;
            }
            AABB specBox = horse.getBoundingBox().inflate(48.0, 20.0, 48.0);
            int nearbyPlayers = horse.level().getEntitiesOfClass(Player.class, specBox, Player::isAlive).size();
            if (nearbyPlayers >= 2) {
                crowdPleaserMult += 0.025;
            }
        }

        boolean hasSmallHooves      = data.hasGeneticTrait(ThoroughbredData.TRAIT_SMALL_HOOVES);
        boolean hasLargeHooves      = data.hasGeneticTrait(ThoroughbredData.TRAIT_LARGE_HOOVES);
        boolean hasBornMudder       = data.hasGeneticTrait(ThoroughbredData.TRAIT_BORN_MUDDER);
        boolean hasXFactor          = data.hasGeneticTrait(ThoroughbredData.TRAIT_X_FACTOR);
        boolean hasHillClimber      = data.hasGeneticTrait(ThoroughbredData.TRAIT_HILL_CLIMBER);
        boolean hasLightningStarter = data.hasGeneticTrait(ThoroughbredData.TRAIT_LIGHTNING_STARTER);
        boolean hasCorneringMaestro = data.hasGeneticTrait(ThoroughbredData.TRAIT_CORNERING_MAESTRO);
        boolean hasDeepLung         = data.hasGeneticTrait(ThoroughbredData.TRAIT_DEEP_LUNG);
        boolean hasSprinterTwitch   = data.hasGeneticTrait(ThoroughbredData.TRAIT_SPRINTER_TWITCH);
        boolean hasDieselStayer     = data.hasGeneticTrait(ThoroughbredData.TRAIT_DIESEL_STAYER);
        boolean hasSoftPalate       = data.hasGeneticTrait(ThoroughbredData.TRAIT_SOFT_PALATE);
        boolean hasBrittleHooves    = data.hasGeneticTrait(ThoroughbredData.TRAIT_BRITTLE_HOOVES);

        double geldingPowerControl = data.isGelding() ? 1.03 : 1.0;
        double speedNorm = Mth.clamp((data.getEffectiveSpeedCurrent() * moodStatMult * unstableLegsMult * crowdPleaserMult) / 1000.0, 0.02, 1.25);
        double maxPwrClamp = hasLargeHooves ? 2.10 : 1.25;
        double basePowerNorm = Mth.clamp((data.getEffectivePowerCurrent() * moodStatMult * feverStatMult * unstableLegsMult * crowdPleaserMult * geldingPowerControl) / 1000.0, 0.02, maxPwrClamp);
        double gutsNorm  = Mth.clamp((data.getEffectiveGutsCurrent()  * moodStatMult * feverStatMult * crowdPleaserMult) / 1000.0, 0.02, 1.25);
        double witsNorm  = Mth.clamp((data.getEffectiveWitsCurrent()  * moodStatMult * feverStatMult * crowdPleaserMult) / 1000.0, 0.02, 1.25);

        boolean isRaining = horse.level().isRaining();
        int surfaceRating = detectSurfaceRating(horse.level(), horse.blockPosition(), data);
        if (isRaining && hasBornMudder) {
            surfaceRating = Math.min(10, surfaceRating + 1);
        }
        TrackConditionModifiers trackMod = evaluateTrackAptitude(surfaceRating);

        double rainMitigationFactor = 1.0 - (0.65 * Math.min(1.0, basePowerNorm));
        double rainSpeedPenaltyKmh;
        double rainPowerMultiplier;
        if (isRaining) {
            if (hasLargeHooves || hasBornMudder) {
                // Large Hooves & Born Mudder: No speed or power penalty during rainy days!
                rainSpeedPenaltyKmh = 0.0;
                rainPowerMultiplier = 1.0;
            } else {
                rainSpeedPenaltyKmh = -2.2 * rainMitigationFactor;
                rainPowerMultiplier = 1.0 - (0.20 * rainMitigationFactor);
                if (hasSmallHooves) {
                    // Small Hooves: Slight extra speed penalty on rainy days
                    rainSpeedPenaltyKmh -= 1.5;
                }
                if (hasBrittleHooves) {
                    // Brittle Hooves: +25% rainy/muddy slipping speed penalty
                    rainSpeedPenaltyKmh *= 1.25;
                }
            }
        } else {
            rainSpeedPenaltyKmh = 0.0;
            rainPowerMultiplier = 1.0;
        }

        // Small Hooves: Higher speed cap (+1.0 km/h base) and does especially well on sunny/clear days (+1.0 km/h firm bonus)!
        double hoofWeatherSpeedBonus = 0.0;
        if (hasSmallHooves) {
            hoofWeatherSpeedBonus += 1.0;
            if (!isRaining) {
                hoofWeatherSpeedBonus += 1.0;
            }
        }

        double effectivePowerMultiplier = trackMod.powerMultiplier * rainPowerMultiplier;
        double effectivePowerNorm = Mth.clamp(basePowerNorm * effectivePowerMultiplier, 0.02, hasLargeHooves ? 2.15 : 1.25);

        double sprinterFactor = calculateSprinterBaselineFactor(data.getMinDistance(), data.getMaxDistance());
        double untrainedNormalCap = 40.0 + (13.0 * sprinterFactor);
        double untrainedCropCap   = 50.0 + (6.0 * sprinterFactor);

        double rawNormalCap = untrainedNormalCap + (56.0 - untrainedNormalCap) * speedNorm;
        double rawCropCap   = untrainedCropCap   + (BASELINE_MAX_CROP_KMH - untrainedCropCap) * speedNorm;

        double weightedPenalty = data.hasWeightedTack() ? -3.0 : 0.0;
        double overweightPenalty = data.hasAilment(ThoroughbredData.AILMENT_OVERWEIGHT) ? -1.5 : 0.0;
        boolean loneWolfSolo = false;
        if (data.hasPersonality(ThoroughbredData.PERS_LONE_WOLF)) {
            AABB wolfBox = horse.getBoundingBox().inflate(4.5, 2.0, 4.5);
            loneWolfSolo = horse.level().getEntitiesOfClass(Horse.class, wolfBox, other -> other != horse && other.isAlive()).isEmpty();
        }
        double loneWolfSpeedBonus = loneWolfSolo ? 0.4 : 0.0;
        double sprinterCropBonus = (hasSprinterTwitch && state.distanceRun <= 1600.0) ? 0.8 : 0.0;

        double totalSpeedCapDelta = trackMod.speedCapDeltaKmh + rainSpeedPenaltyKmh + weightedPenalty + overweightPenalty + hoofWeatherSpeedBonus + loneWolfSpeedBonus;
        double baseNormalCapKmh = Mth.clamp(rawNormalCap + totalSpeedCapDelta, 34.0, 57.5);
        double baseCropMaxKmh   = Mth.clamp(rawCropCap   + totalSpeedCapDelta + sprinterCropBonus, 42.0, MAX_GAUGE_KMH);

        // =====================================================================
        // STEP 14: AUTOMATIC RUNNING STRATEGY DETECTION, BIG ESCAPE (OONIGE),
        //          HOMESTRETCH SPURT BONUSES & OUT-OF-POSITION PENALTIES
        // =====================================================================
        RacingManager.RaceStrategyContext raceCtx = RacingManager.getRunnerStrategyContext(horse);
        double stratNormalCapDelta = 0.0;
        double stratCropCapDelta = 0.0;
        double stratAccelMult = 1.0;
        double stratStaminaDrainMult = 1.0;
        double stratGutsDrainMult = 1.0;
        double stratRaggedBreathMult = 1.0;

        if (!raceCtx.inActiveRace || raceCtx.finished || state.inStartingGate) {
            state.resetRaceStrategyState();
        } else if (!raceCtx.pastFiveSecondStartWindow) {
            // First 5.0 seconds after gates open: pack is sorting itself out (Official HUD is hidden).
            // Innate Big Escape (Oonige) and Front Runners get their natural early break surge to establish position!
            state.activeStrategyHudTag = "";
            if (data.isInnateBigEscapeRunner()) {
                stratNormalCapDelta += 1.4;
                stratAccelMult *= 1.25;
            } else if (data.canUseStrategyIndex(ThoroughbredData.POS_FRONT_RUNNER)) {
                stratNormalCapDelta += 0.5;
                stratAccelMult *= 1.12;
            }
        } else {
            int livePos = detectLiveRunningStrategyPos(data, state, raceCtx, baseNormalCapKmh);
            state.activeRaceStrategyPos = livePos;

            boolean inFinal400m = raceCtx.remainingMeters <= 400.0;
            state.inHomestretchSpurt = inFinal400m;

            if (!inFinal400m || state.midRaceEstablishedPos < 0) {
                state.midRaceEstablishedPos = livePos;
                state.midRaceStepsOff = data.getStrategyStepsOff(livePos);
                if (livePos == ThoroughbredData.POS_BIG_ESCAPE) {
                    state.ranBigEscapeThisRace = true;
                }
            }

            // In the final 400m homestretch, evaluate based on the horse's established mid-race position
            // so Late Surgers and End Closers charging into 1st/2nd place keep their full spurt bonuses!
            int evalPos = inFinal400m ? state.midRaceEstablishedPos : livePos;
            int stepsOff = inFinal400m ? state.midRaceStepsOff : data.getStrategyStepsOff(livePos);
            boolean pureOonige = ThoroughbredData.STRAT_BIG_ESCAPE.equalsIgnoreCase(data.getStrategy());

            if (stepsOff == 0) {
                switch (evalPos) {
                    case ThoroughbredData.POS_BIG_ESCAPE -> {
                        state.ranBigEscapeThisRace = true;
                        if (!inFinal400m) {
                            stratNormalCapDelta += 1.4;
                            stratAccelMult *= 1.25;
                            boolean unpressuredLead = (raceCtx.totalRunners == 1) || (raceCtx.rank == 1 && raceCtx.lengthsAheadOfSecond >= 5.0);
                            if (unpressuredLead) {
                                stratStaminaDrainMult *= Math.max(1.02, 1.08 - (0.05 * Math.min(1.0, witsNorm)));
                                state.activeStrategyHudTag = "§c§l[BIG ESCAPE]";
                            } else if (pureOonige && raceCtx.rank == 1 && raceCtx.lengthsAheadOfSecond < 3.0) {
                                stratStaminaDrainMult *= 1.25;
                                stratRaggedBreathMult *= 1.5;
                                state.activeStrategyHudTag = "§6§l[BIG ESCAPE: PRESSURED]";
                            } else {
                                stratStaminaDrainMult *= 1.10;
                                state.activeStrategyHudTag = "§c§l[BIG ESCAPE]";
                            }
                        } else {
                            // Final 400m Homestretch — "Silence Suzuka Unfading Escape" if leading by >= 4.0L (or solo test)!
                            boolean unfadingLead = (raceCtx.totalRunners == 1) || (raceCtx.rank == 1 && raceCtx.lengthsAheadOfSecond >= 4.0);
                            if (unfadingLead && !state.exhaustedCapLocked) {
                                stratNormalCapDelta += 1.0;
                                stratCropCapDelta += 0.5;
                                stratAccelMult *= 1.15;
                                stratGutsDrainMult *= 0.65; // -35% slower Guts drain!
                                state.activeStrategyHudTag = "§c§l[UNFADING ESCAPE!]";
                            } else {
                                stratNormalCapDelta += 0.5;
                                stratGutsDrainMult *= 0.85;
                                state.activeStrategyHudTag = "§c§l[BIG ESCAPE SPURT]";
                            }
                        }
                    }
                    case ThoroughbredData.POS_FRONT_RUNNER -> {
                        if (!inFinal400m) {
                            stratNormalCapDelta += 0.6;
                            stratAccelMult *= 1.12;
                            boolean clearLead = (raceCtx.rank == 1) && (raceCtx.totalRunners == 1 || raceCtx.lengthsAheadOfSecond >= 1.5);
                            if (clearLead) {
                                stratStaminaDrainMult *= 0.92;
                                state.activeStrategyHudTag = "§e[FRONT: CLEAR LEAD]";
                            } else if (pureOonige) {
                                stratStaminaDrainMult *= 1.25;
                                state.activeStrategyHudTag = "§6[BIG ESCAPE: PRESSURED]";
                            } else {
                                state.activeStrategyHudTag = "§e[FRONT RUNNER]";
                            }
                        } else {
                            stratNormalCapDelta += 0.4;
                            stratCropCapDelta += 0.4;
                            stratAccelMult *= 1.08;
                            stratGutsDrainMult *= 0.75; // -25% slower Guts drain in homestretch!
                            state.activeStrategyHudTag = "§e§l[FRONT SPURT]";
                        }
                    }
                    case ThoroughbredData.POS_PACE_CHASER -> {
                        if (!inFinal400m) {
                            stratNormalCapDelta += 0.3;
                            stratStaminaDrainMult *= 0.92; // -8% stamina drain while stalking
                            state.activeStrategyHudTag = "§a[PACE CHASER]";
                        } else {
                            stratCropCapDelta += 0.6;
                            stratAccelMult *= 1.12;
                            stratGutsDrainMult *= 0.85;
                            state.activeStrategyHudTag = "§a§l[PACE SPURT]";
                        }
                    }
                    case ThoroughbredData.POS_LATE_SURGER -> {
                        if (!inFinal400m) {
                            stratStaminaDrainMult *= 0.86; // -14% stamina drain in mid-pack
                            state.activeStrategyHudTag = "§b[LATE SURGER]";
                        } else {
                            stratCropCapDelta += 1.1;
                            stratAccelMult *= 1.25;
                            stratStaminaDrainMult *= 0.85;
                            state.activeStrategyHudTag = "§b§l[LATE SURGE!]";
                        }
                    }
                    case ThoroughbredData.POS_END_CLOSER -> {
                        if (!inFinal400m) {
                            stratStaminaDrainMult *= 0.80; // -20% stamina drain at the rear
                            state.activeStrategyHudTag = "§d[END CLOSER]";
                        } else {
                            stratCropCapDelta += 1.6;
                            stratAccelMult *= 1.38;
                            stratStaminaDrainMult *= 0.80;
                            state.activeStrategyHudTag = "§d§l[END CLOSER SPURT!]";
                        }
                    }
                }
            } else {
                // Out of Position! (stepsOff >= 1)
                if (evalPos == ThoroughbredData.POS_BIG_ESCAPE && !data.isInnateBigEscapeRunner()) {
                    // Reckless non-Oonige horse attempting a Big Escape (5.0+L runaway lead)!
                    state.ranBigEscapeThisRace = true;
                    stratNormalCapDelta -= 0.4;
                    stratCropCapDelta -= 0.8;
                    stratStaminaDrainMult *= 1.38; // +38% heavy overpace stamina drain!
                    stratRaggedBreathMult *= 2.2;
                    state.activeStrategyHudTag = "§c§l[RECKLESS OVERPACE!]";
                } else if (stepsOff == 1) {
                    // 1 Step Off Preferred Strategy (Mild Discomfort; Stoic suffers 40% less penalty!)
                    double stoicMit = data.hasPersonality(ThoroughbredData.PERS_STOIC) ? 0.60 : 1.0;
                    stratNormalCapDelta -= 0.5 * stoicMit;
                    stratCropCapDelta -= 0.7 * stoicMit;
                    stratAccelMult *= (1.0 - 0.10 * stoicMit);
                    double baseOffDrain = (pureOonige && evalPos == ThoroughbredData.POS_FRONT_RUNNER && raceCtx.rank == 1 && raceCtx.lengthsAheadOfSecond < 3.0)
                            ? 0.25 : 0.18;
                    stratStaminaDrainMult *= (1.0 + baseOffDrain * stoicMit);
                    stratRaggedBreathMult *= (1.0 + 0.60 * stoicMit);
                    if (pureOonige && evalPos == ThoroughbredData.POS_FRONT_RUNNER && raceCtx.rank == 1) {
                        state.activeStrategyHudTag = "§6§l[BIG ESCAPE: PRESSURED]";
                    } else {
                        state.activeStrategyHudTag = data.isHeldTooFarBack(evalPos)
                                ? "§6[HELD BACK / KEEN]"
                                : "§6[OUT OF RHYTHM]";
                    }
                } else {
                    // 2+ Steps Off Preferred Strategy (Severe Fighting the Bit / Severe Overpace)
                    stratNormalCapDelta -= 1.2;
                    stratCropCapDelta -= 1.5;
                    stratAccelMult *= 0.78;
                    stratStaminaDrainMult *= data.isInnateBigEscapeRunner() ? 1.45 : 1.40;
                    stratRaggedBreathMult *= 2.6;
                    state.activeStrategyHudTag = data.isHeldTooFarBack(evalPos)
                            ? "§c§l[FIGHTING BIT!]"
                            : "§c§l[SEVERE OVERPACE!]";
                }
            }
        }

        double horseNormalCapKmh = Mth.clamp(baseNormalCapKmh + stratNormalCapDelta, 34.0, 59.0);
        double horseCropMaxKmh   = Mth.clamp(baseCropMaxKmh   + stratCropCapDelta,   42.0, MAX_GAUGE_KMH);
        double unlockedCropCapKmh = horseNormalCapKmh + (horseCropMaxKmh - horseNormalCapKmh) * state.cropEffortRatio;

        boolean inPool = horse.isInWater();

        if (!inPool && !state.clientJumping && dy > 0.01 && dy <= 1.05 && rawBlocksMoved > 0.01) {
            state.slopeResistancePool = Math.min(1.5, state.slopeResistancePool + dy);
        }

        boolean currentlyOnSlope = !inPool && state.slopeResistancePool > 0.01;
        double slopePassiveDecel = 0.0;
        double slopeStaminaExtra = 0.0;
        double slopeIntensity = 0.0;

        if (state.slopeResistancePool > 0.001) {
            double stepDrain = Math.min(state.slopeResistancePool, 0.08);
            state.slopeResistancePool = Math.max(0.0, state.slopeResistancePool - stepDrain);
            slopeIntensity = stepDrain / 0.08;

            double clampedPower = Math.min(1.0, effectivePowerNorm);
            slopePassiveDecel = slopeIntensity * (0.42 * (1.0 - 0.80 * clampedPower));
            slopeStaminaExtra = slopeIntensity * (0.38 * (1.0 - 0.65 * clampedPower));
            if (hasHillClimber) {
                // Hill Climber: Halves uphill speed deceleration and uphill extra Stamina drain!
                slopePassiveDecel *= 0.50;
                slopeStaminaExtra *= 0.50;
            }
        }

        int effectiveMin = data.getEffectiveMinDistance();
        int maxDist = data.getMaxDistance();
        int effectiveMaxDist = hasDieselStayer ? (maxDist + 100) : maxDist;
        int zoneCode;
        if (state.distanceRun < effectiveMin) {
            zoneCode = 0;
        } else if (state.distanceRun <= effectiveMaxDist) {
            zoneCode = 1;
        } else if (state.distanceRun <= effectiveMaxDist + 50) {
            zoneCode = 2;
        } else {
            zoneCode = 3;
        }

        if (state.exhaustedCapLocked && state.currentStamina >= (state.maxStamina * 0.50)) {
            state.exhaustedCapLocked = false;
        }

        double currentMaxSpeedCap;
        if (data.isCareerEnded()) {
            horseNormalCapKmh = Math.min(horseNormalCapKmh, 40.0);
            horseCropMaxKmh = Math.min(horseCropMaxKmh, 40.0);
            unlockedCropCapKmh = Math.min(unlockedCropCapKmh, 40.0);
        }

        if (inPool) {
            currentMaxSpeedCap = state.exhaustedCapLocked ? 2.5 : POOL_MAX_SPEED_KMH;
            state.cropEffortRatio = 0.0;
        } else if (data.hasAilment(ThoroughbredData.AILMENT_LAMENESS) || data.hasActiveInjury()) {
            currentMaxSpeedCap = 12.0; // Lameness locks out Racing & Training Mode and forces a slow 12 km/h walk
            state.cropEffortRatio = 0.0;
            state.gutsActive = false;
        } else if (data.hasAilment(ThoroughbredData.AILMENT_AFIB)) {
            currentMaxSpeedCap = 30.0; // Atrial Fibrillation causes rapid speed loss regardless of Stamina & Guts!
            state.cropEffortRatio = 0.0;
            state.gutsActive = false;
        } else if (state.exhaustedCapLocked) {
            currentMaxSpeedCap = STAMINA_THRESHOLD_KMH;
            state.cropEffortRatio = 0.0;
        } else if (state.gutsActive) {
            currentMaxSpeedCap = state.gutsSpeedLockKmh;
        } else if (state.cropEffortRatio > 0.0) {
            currentMaxSpeedCap = unlockedCropCapKmh;
        } else {
            currentMaxSpeedCap = horseNormalCapKmh;
        }

        if (data.isCareerEnded()) {
            currentMaxSpeedCap = Math.min(currentMaxSpeedCap, 40.0);
        }

        // Step 15: Pack Collision, Forward Boxing-In & Anti-Brake-Check
        boolean isBoxedIn = false;
        double collisionSpeedCap = Double.MAX_VALUE;
        if (raceCtx.inActiveRace && raceCtx.pastFiveSecondStartWindow && !raceCtx.finished && !state.inStartingGate) {
            AABB searchBox = horse.getBoundingBox().inflate(3.5, 1.5, 3.5);
            List<Horse> nearbyRunners = horse.level().getEntitiesOfClass(Horse.class, searchBox,
                    other -> other != horse && other.isAlive() && ThoroughbredData.isThoroughbred(other) && RacingManager.isHorseInActiveRace(other));

            double hYaw = state.horseYawInitialized ? state.horseYaw : horse.getYRot();
            double hYawRad = Math.toRadians(hYaw);
            double fwdX = -Math.sin(hYawRad);
            double fwdZ = Math.cos(hYawRad);
            double latX = Math.cos(hYawRad);
            double latZ = Math.sin(hYawRad);

            for (Horse other : nearbyRunners) {
                double dX = other.getX() - horse.getX();
                double dZ = other.getZ() - horse.getZ();
                double distFwd = dX * fwdX + dZ * fwdZ;
                double distLat = dX * latX + dZ * latZ;

                // Forward Blocking Lane: within 0.35m to 2.40m ahead and within +/- 0.90m laterally
                if (distFwd > 0.35 && distFwd < 2.40 && Math.abs(distLat) <= 0.90) {
                    HorseRunState otherState = getOrCreateState(other);
                    double otherSpeed = otherState.displayedSpeedKmh;

                    // Anti-Brake-Check & Lag Protection:
                    // If the runner ahead drops below 25.0 km/h mid-race (brake-checking, disconnected, or rearing),
                    // the trailing horse ghosts through them without slamming the brakes!
                    if (otherSpeed >= 25.0) {
                        isBoxedIn = true;
                        double effectiveFrontCap = otherSpeed + 0.2;
                        if (effectiveFrontCap < collisionSpeedCap) {
                            collisionSpeedCap = effectiveFrontCap;
                        }
                    }
                }
            }
        }
        state.isBoxedIn = isBoxedIn;
        if (isBoxedIn) {
            currentMaxSpeedCap = Math.min(currentMaxSpeedCap, collisionSpeedCap);
            if (state.activeStrategyHudTag == null || state.activeStrategyHudTag.isEmpty()) {
                state.activeStrategyHudTag = "§6[BOXED IN]";
            } else if (!state.activeStrategyHudTag.contains("BOXED IN")) {
                state.activeStrategyHudTag += " §6[BOXED IN]";
            }
        }

        if (slopePassiveDecel > 0.0 && state.baseSpeedKmh > 10.0) {
            state.baseSpeedKmh = Math.max(10.0, state.baseSpeedKmh - slopePassiveDecel);
        }

        if (state.pressingS) {
            state.baseSpeedKmh = Math.max(0.0, state.baseSpeedKmh - 0.65);
            state.gateBreakAccelMultiplier = 1.0;
            if (state.gutsActive && state.baseSpeedKmh < state.gutsSpeedLockKmh) {
                state.gutsSpeedLockKmh = state.baseSpeedKmh;
            }
            if (state.baseSpeedKmh < horseNormalCapKmh - 0.5) {
                state.cropEffortRatio = 0.0;
            }
        } else if (state.pressingW && !state.inStartingGate && state.gateRearFreezeTicks <= 0 && !state.wHeldBeforeGateOpen && !state.trainingRefusalActive && state.baseSpeedKmh < currentMaxSpeedCap && !state.gutsActive) {
            if (horse.isStanding()) {
                state.rearVisualTicks = 0;
                horse.setStanding(false);
            }

            if (state.inHomestretchSpurt && data.hasGeneticTrait(ThoroughbredData.TRAIT_EXPLOSIVE_GEAR)) {
                stratAccelMult *= 1.12;
            }
            if (hasSmallHooves && !isRaining) {
                stratAccelMult *= 1.08; // Small Hooves excel on sunny/firm days!
            }
            double baseAccel = (0.34 + 0.50 * effectivePowerNorm) * effectivePowerMultiplier * stratAccelMult;
            if (hasLargeHooves) {
                // Large Hooves Tradeoff: Despite their doubled power cap, their acceleration rate is half of what it was supposed to be!
                baseAccel *= 0.50;
            }
            if (hasSprinterTwitch && state.distanceRun <= 1600.0) {
                baseAccel *= 1.12; // Sprinter's Fast-Twitch: +12% acceleration up to 1,600m
            }
            if (hasDieselStayer) {
                baseAccel *= 0.88; // Diesel Stayer Tradeoff: -12% initial acceleration
            }
            if (hasLightningStarter && state.baseSpeedKmh < STAMINA_THRESHOLD_KMH && state.distanceRun < 60.0) {
                baseAccel *= 1.15; // Lightning Starter: +15% 0-35 km/h gate-break acceleration
            }
            if (data.hasPersonality(ThoroughbredData.PERS_TIMID) && !isBoxedIn) {
                AABB timidBox = horse.getBoundingBox().inflate(2.5, 1.5, 2.5);
                boolean openLane = horse.level().getEntitiesOfClass(Horse.class, timidBox, other -> other != horse && other.isAlive()).isEmpty();
                if (openLane) {
                    baseAccel *= 1.10; // Timid: +10% acceleration when steered into an open lane!
                }
            }
            if (data.hasPersonality(ThoroughbredData.PERS_LAID_BACK) && state.trainingMode && state.ticksSinceLastCrop > 60 && !isBreezingInCompany(horse)) {
                baseAccel *= 0.92; // Laid-Back: takes it easy (92% accel) in solo training unless tapped with Crop or breezing in company
            }

            if (currentlyOnSlope) {
                baseAccel += slopePassiveDecel * (0.45 + 0.50 * Math.min(1.0, effectivePowerNorm));
            }

            if (state.baseSpeedKmh < 20.0) {
                baseAccel *= (1.45 + 0.65 * effectivePowerNorm) * state.gateBreakAccelMultiplier;
            } else if (state.baseSpeedKmh < STAMINA_THRESHOLD_KMH) {
                baseAccel *= (1.25 + 0.35 * effectivePowerNorm) * state.gateBreakAccelMultiplier;
            } else {
                state.gateBreakAccelMultiplier = 1.0;
                double clampedPower = Math.min(1.0, effectivePowerNorm);
                if (state.baseSpeedKmh <= horseNormalCapKmh) {
                    double normZoneSpan = Math.max(3.0, horseNormalCapKmh - STAMINA_THRESHOLD_KMH);
                    double ratio = Mth.clamp((state.baseSpeedKmh - STAMINA_THRESHOLD_KMH) / normZoneSpan, 0.0, 1.0);
                    double dragExp = 1.60 - (0.55 * clampedPower);
                    double airDrag = Math.max(0.18, 1.0 - 0.78 * Math.pow(ratio, dragExp));
                    baseAccel *= airDrag;
                } else {
                    double cropSpan = Math.max(3.0, horseCropMaxKmh - horseNormalCapKmh);
                    double cropProgress = Mth.clamp((state.baseSpeedKmh - horseNormalCapKmh) / cropSpan, 0.0, 1.0);
                    double cropWindWall = Math.max(0.05, 1.0 - 0.93 * Math.pow(cropProgress, 1.15 + 0.85 * clampedPower));
                    baseAccel *= cropWindWall * (0.26 + 0.30 * clampedPower);
                }
            }

            state.baseSpeedKmh = Math.min(currentMaxSpeedCap, state.baseSpeedKmh + baseAccel);
        } else if (state.baseSpeedKmh > currentMaxSpeedCap) {
            double decelRate;
            if (data.hasAilment(ThoroughbredData.AILMENT_AFIB)) {
                decelRate = 0.68; // Rapid pace loss from Atrial Fibrillation!
            } else if (isBoxedIn) {
                decelRate = 0.35; // Capped smooth deceleration limit for realistic boxing-in
            } else if (inPool) {
                decelRate = 0.85;
            } else if (state.exhaustedCapLocked) {
                decelRate = 0.48 * (1.20 - 0.70 * Math.min(1.0, gutsNorm));
                if (state.ranBigEscapeThisRace) {
                    decelRate *= 2.0; // Twin Turbo "Reverse Jet" (逆噴射) 2x exhaustion deceleration!
                }
            } else {
                decelRate = 0.25;
            }
            state.baseSpeedKmh = Math.max(currentMaxSpeedCap, state.baseSpeedKmh - decelRate);
        }

        boolean triggeredBigStrideThisTick = false;
        if (!inPool && state.tickCounter % 40 == 0 && state.baseSpeedKmh >= STAMINA_THRESHOLD_KMH && !state.exhaustedCapLocked && !state.gutsActive) {
            double bigStrideChance;
            if (hasLargeHooves) {
                // Large Hooves: Doubled power cap significantly increases the chance of taking big strides while running!
                bigStrideChance = Mth.clamp(0.35 + (0.45 * effectivePowerNorm), 0.25, 0.94);
            } else {
                bigStrideChance = Mth.clamp(0.20 + (0.55 * effectivePowerNorm), 0.05, 0.85);
                if (hasSmallHooves) {
                    bigStrideChance *= 0.65; // Small Hooves tradeoff: lower chance to take big strides
                }
            }
            if (horse.getRandom().nextDouble() < bigStrideChance) {
                state.bigStrideTicks = 3;
                state.bigStrideBonusKmh = 0.7 + (0.9 * Math.min(1.5, effectivePowerNorm));
                triggeredBigStrideThisTick = true;
            }
        }
        if (state.bigStrideTicks <= 0) {
            state.bigStrideBonusKmh = Math.max(0.0, state.bigStrideBonusKmh - 0.65);
        }

        if (state.baseSpeedKmh > 1.0) {
            if (state.tickCounter % 3 == 0) {
                double wave = Math.sin(state.tickCounter * 0.35) * (inPool ? 0.12 : 0.35);
                double jitter = (horse.getRandom().nextDouble() - 0.5) * (inPool ? 0.15 : 0.45);
                state.fluctuationOffsetKmh = wave + jitter;
            }
            state.displayedSpeedKmh = Mth.clamp(
                    state.baseSpeedKmh + state.bigStrideBonusKmh + state.fluctuationOffsetKmh,
                    0.5,
                    inPool ? POOL_MAX_SPEED_KMH : MAX_GAUGE_KMH
            );
        } else {
            state.fluctuationOffsetKmh = 0.0;
            state.displayedSpeedKmh = 0.0;
        }

        boolean swimmingActive = inPool && state.baseSpeedKmh > 1.0;
        if (state.baseSpeedKmh > STAMINA_THRESHOLD_KMH || swimmingActive) {
            state.stoppedTicks = 0;

            double raggedChance = 0.011 * (1.15 - Math.min(1.0, witsNorm)) * stratRaggedBreathMult
                    * (hasDeepLung ? 0.40 : 1.0);
            if (!inPool && state.unevenBreathingTicks == 0 && state.panicTicks == 0
                    && horse.getRandom().nextDouble() < raggedChance) {
                state.unevenBreathingTicks = 50;
                horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 0.75F, 0.9F);
            }

            double finalDrain;
            if (swimmingActive) {
                double poolRatio = Mth.clamp(state.baseSpeedKmh / POOL_MAX_SPEED_KMH, 0.2, 1.0);
                finalDrain = 0.65 * poolRatio * (1.0 - 0.15 * Math.min(1.0, witsNorm));
            } else {
                double speedAbove35 = state.baseSpeedKmh - STAMINA_THRESHOLD_KMH;
                double airResistanceDrain = 0.16 + 0.95 * Math.pow(speedAbove35 / 21.0, 1.75);

                if (state.baseSpeedKmh > horseNormalCapKmh) {
                    double overNormal = state.baseSpeedKmh - horseNormalCapKmh;
                    airResistanceDrain *= (1.40 + 0.14 * overNormal);
                }

                double witsEfficiency = 1.0 - (0.18 * Math.min(1.0, witsNorm));
                airResistanceDrain *= witsEfficiency;
                if (hasXFactor) {
                    // Active X-Factor (Massive Heart): -12% Stamina drain above 35 km/h due to superior stroke volume & oxygen delivery!
                    airResistanceDrain *= 0.88;
                }

                if (state.panicTicks > 0) {
                    airResistanceDrain *= 2.25;
                } else if (state.unevenBreathingTicks > 0) {
                    airResistanceDrain *= 1.45;
                }

                if (zoneCode == 2) {
                    airResistanceDrain *= 2.50;
                } else if (zoneCode == 3) {
                    airResistanceDrain *= 12.0;
                }

                finalDrain = (airResistanceDrain * trackMod.staminaDrainMod * stratStaminaDrainMult) + slopeStaminaExtra;
                if (hasLargeHooves) {
                    // Large Hooves Tradeoff: Stamina depletes ever so slightly quicker due to large hooves being heavier!
                    finalDrain *= 1.08;
                }
                if (!state.trainingMode && data.hasAilment(ThoroughbredData.AILMENT_LARYNGEAL)) {
                    finalDrain *= 2.0; // Laryngeal Hemiplegia: Stamina depletes 2x faster in Racing Mode!
                }
                if (data.hasAilment(ThoroughbredData.AILMENT_EIPH)) {
                    // Active X-Factor halves the EIPH / Bleeder stamina drain penalty (+17.5% instead of +35%)
                    finalDrain *= (hasXFactor ? 1.175 : 1.35);
                }
                if (hasSprinterTwitch && state.distanceRun > 1800.0) {
                    finalDrain *= 1.20; // Sprinter's Fast-Twitch Tradeoff: +20% Stamina drain past 1,800m!
                }
                if (hasDieselStayer && (maxDist >= 2200 || state.distanceRun >= 1600.0)) {
                    finalDrain *= 0.88; // Diesel Stayer: -12% Stamina drain in stayer distances!
                }
                if (data.hasAilment(ThoroughbredData.AILMENT_OVERWEIGHT)) {
                    finalDrain *= 1.18; // Overweight: +18% Stamina depletion!
                }
                if (loneWolfSolo) {
                    finalDrain *= 0.92; // Lone Wolf: -8% Stamina drain when running clear of the pack!
                }
                if (isBoxedIn) {
                    // High Wits mitigates drafting / boxed-in frustration stamina drain; Timid horses suffer +25% boxed-in drain!
                    double boxedDrain = 0.12 * (1.0 - 0.55 * Math.min(1.0, witsNorm));
                    if (data.hasPersonality(ThoroughbredData.PERS_TIMID)) {
                        boxedDrain *= 1.25;
                        finalDrain *= 1.12;
                    }
                    finalDrain += boxedDrain;
                }
            }

            if (state.currentStamina > 0.0) {
                state.currentStamina = Math.max(0.0, state.currentStamina - finalDrain);
                state.gutsActive = false;

                if (state.currentStamina <= 0.0) {
                    if (zoneCode == 3) {
                        state.gutsActive = false;
                        state.exhaustedCapLocked = true;
                    } else if (state.currentGuts > 0.0) {
                        state.gutsActive = true;
                        state.gutsSpeedLockKmh = state.baseSpeedKmh;
                    } else {
                        state.exhaustedCapLocked = true;
                    }
                }
            } else {
                if (zoneCode == 3) {
                    state.gutsActive = false;
                    state.exhaustedCapLocked = true;
                } else if (state.currentGuts > 0.0) {
                    if (!state.gutsActive) {
                        state.gutsActive = true;
                        state.gutsSpeedLockKmh = state.baseSpeedKmh;
                    }
                    double gutsDrain = (0.45 + (0.35 * (state.baseSpeedKmh / 56.0))) * stratGutsDrainMult;
                    if (hasXFactor) {
                        // Active X-Factor (Massive Heart): -20% Guts drain during a Guts Drive!
                        gutsDrain *= 0.80;
                    }
                    if (data.hasPersonality(ThoroughbredData.PERS_DEFEATIST)) {
                        // Defeatist Personality: Guts stat depletes 2x faster!
                        gutsDrain *= 2.0;
                    }
                    if (data.hasPersonality(ThoroughbredData.PERS_COMPETITIVE) && isBreezingInCompany(horse)) {
                        gutsDrain *= 0.90; // Competitive: -10% Guts drain when dueling another horse!
                    }
                    if (hasSoftPalate && !data.hasBlinkersHood() && state.cropEffortRatio > 0.35) {
                        gutsDrain *= 1.25; // Soft Palate Displacement: +25% Guts drain under heavy Crop drive without Blinkers/Hood!
                    }
                    state.currentGuts = Math.max(0.0, state.currentGuts - gutsDrain);
                    if (state.currentGuts <= 0.0) {
                        state.gutsActive = false;
                        state.exhaustedCapLocked = true;
                    }
                } else {
                    state.gutsActive = false;
                    state.exhaustedCapLocked = true;
                }
            }
        } else {
            state.gutsActive = false;
            double closenessTo35 = Mth.clamp((STAMINA_THRESHOLD_KMH - state.baseSpeedKmh) / STAMINA_THRESHOLD_KMH, 0.0, 1.0);
            double staminaRegen = 0.12 + 3.10 * Math.pow(closenessTo35, 1.45);
            state.currentStamina = Math.min(state.maxStamina, state.currentStamina + staminaRegen);

            if (!state.exhaustedCapLocked) {
                double gutsRegen = 0.05 + 0.45 * Math.pow(closenessTo35, 1.3);
                state.currentGuts = Math.min(state.maxGuts, state.currentGuts + gutsRegen);
            }
        }

        // Exact 1:1 Block-to-Meter Odometer (1 Minecraft Block = 1 Meter on F3 axis!)
        double metersThisTick = 0.0;
        if (state.displayedSpeedKmh > 0.2 && rawBlocksMoved > 0.005) {
            metersThisTick = rawBlocksMoved;
            state.distanceRun += metersThisTick;
            state.stoppedTicks = 0;
        } else if (state.displayedSpeedKmh <= 0.2) {
            state.stoppedTicks++;
            if (state.stoppedTicks >= 100) {
                state.distanceRun = 0.0;
                if (!activelyInRaceSession && !data.isInRace() && data.clearRaceEndAilments()) {
                    data.saveToHorse(horse);
                }
            }
        }

        boolean gainingSpd = false;
        boolean gainingStm = false;
        boolean gainingPwr = false;
        boolean gainingGuts = false;
        boolean gainingWits = false;
        boolean dataDirty = false;

        boolean blessed = data.hasEnchantedAppleBlessing();

        if (state.baseSpeedKmh > 1.0) {
            // >50 km/h High-Speed Track Kick-Up Particles (flings turf/dirt/sand/mud debris behind the horse's rear hooves!)
            if (state.displayedSpeedKmh > 50.0 && horse.onGround() && !inPool && horse.level() instanceof ServerLevel srvLvl) {
                BlockPos groundPos = horse.getOnPos();
                BlockState groundState = srvLvl.getBlockState(groundPos);
                if (groundState.isAir()) {
                    groundState = srvLvl.getBlockState(horse.blockPosition().below());
                }
                if (!groundState.isAir()) {
                    double yawRad = Math.toRadians(horse.getYRot());
                    double rearX = horse.getX() + Math.sin(yawRad) * 0.75;
                    double rearZ = horse.getZ() - Math.cos(yawRad) * 0.75;
                    double hoofY = horse.getY() + 0.12;
                    int debrisCount = state.displayedSpeedKmh >= 58.0 ? 8 : 5;
                    srvLvl.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                            rearX, hoofY, rearZ,
                            debrisCount, 0.28, 0.12, 0.28, 0.18
                    );
                    if (isRaining) {
                        srvLvl.sendParticles(
                                ParticleTypes.SPLASH,
                                rearX, hoofY + 0.08, rearZ,
                                4, 0.30, 0.10, 0.30, 0.15
                        );
                    }
                }
            }
            double effortIntensity = swimmingActive ? 1.6 : Mth.clamp(state.baseSpeedKmh / 35.0, 0.3, 2.2);

            if (!blessed) {
                double feverDrainMult = data.hasAilment(ThoroughbredData.AILMENT_FEVER) ? 1.75 : 1.0;
                double laidBackMult = data.hasPersonality(ThoroughbredData.PERS_LAID_BACK) ? 0.90 : 1.0;
                double gluttonHungerMult = data.hasPersonality(ThoroughbredData.PERS_GLUTTON) ? 1.15 : 1.0;
                data.setHunger(data.getHunger() - (0.0075 * effortIntensity * feverDrainMult * laidBackMult * gluttonHungerMult));
                data.setThirst(data.getThirst() - (0.0095 * effortIntensity * feverDrainMult * laidBackMult));
            }
            if (!data.hasAilment(ThoroughbredData.AILMENT_OVERWEIGHT)) {
                state.overweightBurnMeters = 0.0;
            } else if (metersThisTick > 0.0) {
                state.overweightBurnMeters = Math.max(state.overweightBurnMeters, data.getOverweightExerciseMeters()) + metersThisTick;
                data.setOverweightExerciseMeters(state.overweightBurnMeters);
                if (horse.getPersistentData().contains("AIOThoroughbred")) {
                    horse.getPersistentData().getCompound("AIOThoroughbred").putDouble("OverweightExerciseMeters", state.overweightBurnMeters);
                }
                if (state.overweightBurnMeters >= 600.0) {
                    state.overweightBurnMeters = 0.0;
                    data.removeAilment(ThoroughbredData.AILMENT_OVERWEIGHT);
                    data.setOverfedStreak(0);
                    dataDirty = true;
                    serverPlayer.sendSystemMessage(Component.literal(
                            "§a§l[Overweight Burned Off!] §6" + data.getName() + " §fworked off its excess weight after 600m of exercise and is back to peak racing trim!"));
                }
            }

            double fatigueRate = 0.0022 * effortIntensity;
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_IRON_CONSTITUTION)) {
                fatigueRate *= 0.80;
            }
            if (hasXFactor) {
                // Active X-Factor cardiovascular efficiency reduces running Fatigue buildup by -15%
                fatigueRate *= 0.85;
            }
            if (blessed) {
                fatigueRate *= 0.15;
            }
            if (state.gutsActive || state.exhaustedCapLocked) {
                fatigueRate *= 3.8;
            }
            if (zoneCode == 2) {
                fatigueRate *= 2.5;
            } else if (zoneCode == 3) {
                fatigueRate *= 5.5;
            }
            if (state.panicTicks > 0) {
                fatigueRate *= 3.0;
            }
            data.setFatigue(data.getFatigue() + fatigueRate);

            if (data.getFatigue() > 65.0 || state.exhaustedCapLocked || data.getHunger() < 20.0 || data.getThirst() < 20.0) {
                double moodDrain = 0.006 * (state.exhaustedCapLocked ? 2.5 : 1.0);
                if (data.hasGeneticTrait(ThoroughbredData.TRAIT_HIGH_STRUNG)) {
                    moodDrain *= 1.25;
                }
                data.setMood(data.getMood() - moodDrain);
            } else if (data.getFatigue() <= 30.0 && data.getHunger() >= 75.0 && data.getThirst() >= 75.0 && state.panicTicks == 0) {
                data.setMood(data.getMood() + 0.0025);
            }

            if (state.tickCounter % 40 == 0 && !inPool && state.baseSpeedKmh >= 24.0) {
                if (tickRunningAilmentAndInjuryRolls(serverPlayer, horse, data, state, horseNormalCapKmh, zoneCode, isRaining, activelyInRaceSession)) {
                    dataDirty = true;
                    if (data.hasPoorPrognosis()) {
                        data.saveToHorse(horse);
                        return;
                    }
                }
            }

            if (state.tickCounter % 20 == 0) {
                dataDirty = true;
            }
        } else if (state.stoppedTicks > 40 && data.getHunger() > 50.0 && data.getThirst() > 50.0 && data.getFatigue() > 0.0) {
            data.setFatigue(data.getFatigue() - 0.006);
            if (data.getFatigue() <= 25.0 && data.getHunger() >= 75.0 && data.getThirst() >= 75.0) {
                data.setMood(data.getMood() + 0.003);
            }
            if (state.tickCounter % 20 == 0) {
                dataDirty = true;
            }
        }

        if (state.trainingMode && !state.trainingRefusalActive && (blessed || (data.getHunger() > 0.1 && data.getThirst() > 0.1))) {
            double fatigueMult = getFatigueExpMultiplier(data.getFatigue());
            double weightedMult = data.hasWeightedTack() ? 1.30 : 1.0;
            double blinkersMult = data.hasBlinkersHood() ? 1.20 : 1.0;
            double appleExpBoost = blessed ? 1.20 : 1.0;
            double bondTempMult = data.getTemperamentBondExpMultiplier();
            double moodExpMult = data.getMoodExpMultiplier();

            double spdGain = 0.0;
            double stmGain = 0.0;
            double pwrGain = 0.0;
            double gutsGain = 0.0;
            double witsGain = 0.0;

            if (swimmingActive) {
                double swimFactor = Mth.clamp(state.baseSpeedKmh / POOL_MAX_SPEED_KMH, 0.25, 1.0);
                double deepLungMult = hasDeepLung ? 1.20 : 1.0;
                stmGain  += 0.42 * swimFactor * weightedMult * deepLungMult;
                pwrGain  += 0.40 * swimFactor * weightedMult * deepLungMult;
                gutsGain += 0.20 * swimFactor * deepLungMult;
            }

            if (!inPool && state.baseSpeedKmh > STAMINA_THRESHOLD_KMH) {
                double over35Norm = Mth.clamp((state.baseSpeedKmh - STAMINA_THRESHOLD_KMH) / (MAX_GAUGE_KMH - STAMINA_THRESHOLD_KMH), 0.0, 1.0);
                spdGain += (0.10 + 0.58 * Math.pow(over35Norm, 1.55)) * blinkersMult;

                if (state.baseSpeedKmh >= horseNormalCapKmh * 0.90) {
                    state.flyingBreezeMeters += metersThisTick;
                    if (state.flyingBreezeMeters >= 200.0) {
                        state.flyingBreezeMeters = 0.0;
                        spdGain += 22.0 * blinkersMult;
                        data.setBond(data.getBond() + 0.4);
                        serverPlayer.displayClientMessage(Component.literal("§b§l[200m Flying Breeze Complete! +SPD EXP]"), true);
                    }
                } else {
                    state.flyingBreezeMeters = Math.max(0.0, state.flyingBreezeMeters - 0.5);
                }

                double slowEnduranceFactor = 1.0 - (0.72 * Math.pow(over35Norm, 1.25));
                if (state.baseSpeedKmh <= 45.0) {
                    state.longCanterMeters += metersThisTick;
                } else {
                    state.longCanterMeters = Math.max(0.0, state.longCanterMeters - 1.0);
                }
                double longCanterMult = state.longCanterMeters >= 400.0 ? 1.35 : 1.0;
                stmGain += 0.40 * slowEnduranceFactor * longCanterMult * weightedMult;
            }

            if (currentlyOnSlope && state.baseSpeedKmh > 8.0) {
                double hillClimberExp = hasHillClimber ? 1.15 : 1.0;
                pwrGain  += 0.62 * slopeIntensity * weightedMult * hillClimberExp;
                gutsGain += 0.30 * slopeIntensity;
                spdGain  += 0.18 * slopeIntensity;
            }

            if (triggeredBigStrideThisTick) {
                pwrGain += 6.0 * weightedMult;
            }

            if (state.gutsActive) {
                gutsGain += 0.85;
            }
            if (!inPool && state.baseSpeedKmh > STAMINA_THRESHOLD_KMH && isBreezingInCompany(horse)) {
                double compMult = data.hasPersonality(ThoroughbredData.PERS_COMPETITIVE) ? 1.15 : 1.0;
                gutsGain = (gutsGain + 0.25) * 1.40 * compMult;
            }

            if (!inPool && state.baseSpeedKmh >= 40.0 && state.baseSpeedKmh <= 50.0 && state.panicTicks == 0) {
                witsGain += 0.36 * blinkersMult;
            }

            double totalMult = fatigueMult * appleExpBoost * bondTempMult * moodExpMult;
            spdGain  *= totalMult;
            stmGain  *= totalMult;
            pwrGain  *= totalMult;
            gutsGain *= totalMult;
            witsGain *= totalMult;

            gainingSpd  = spdGain > 0.001 && data.getSpeedCurrent() < data.getSpeedMax();
            gainingStm  = stmGain > 0.001 && data.getStaminaCurrent() < data.getStaminaMax();
            gainingPwr  = pwrGain > 0.001 && data.getPowerCurrent() < data.getPowerMax();
            gainingGuts = gutsGain > 0.001 && data.getGutsCurrent() < data.getGutsMax();
            gainingWits = witsGain > 0.001 && data.getWitsCurrent() < data.getWitsMax();

            boolean spdUp  = (spdGain > 0.0) && data.addSpeedExp(spdGain);
            boolean stmUp  = (stmGain > 0.0) && data.addStaminaExp(stmGain);
            boolean pwrUp  = (pwrGain > 0.0) && data.addPowerExp(pwrGain);
            boolean gutsUp = (gutsGain > 0.0) && data.addGutsExp(gutsGain);
            boolean witsUp = (witsGain > 0.0) && data.addWitsExp(witsGain);

            if (spdUp)  triggerLevelUpFlash(state, 1, serverPlayer, horse);
            else if (stmUp)  triggerLevelUpFlash(state, 2, serverPlayer, horse);
            else if (pwrUp)  triggerLevelUpFlash(state, 3, serverPlayer, horse);
            else if (gutsUp) triggerLevelUpFlash(state, 4, serverPlayer, horse);
            else if (witsUp) triggerLevelUpFlash(state, 5, serverPlayer, horse);

            if (spdUp || stmUp || pwrUp || gutsUp || witsUp) {
                dataDirty = true;
            } else if (state.tickCounter % 10 == 0 && (gainingSpd || gainingStm || gainingPwr || gainingGuts || gainingWits)) {
                dataDirty = true;
            }
        }

        if (dataDirty) {
            data.saveToHorse(horse);
        }

        ClientHudData hud = new ClientHudData();
        hud.ridingThoroughbred = true;
        hud.horseName = data.getName();
        hud.baseSpeedKmh = state.baseSpeedKmh;
        hud.displayedSpeedKmh = state.displayedSpeedKmh;
        hud.bigStrideBonusKmh = state.bigStrideBonusKmh;
        hud.distanceMeters = state.distanceRun;
        hud.normalCapKmh = horseNormalCapKmh;
        hud.cropMaxKmh = horseCropMaxKmh;
        hud.unlockedCropCapKmh = unlockedCropCapKmh;
        hud.currentStamina = state.currentStamina;
        hud.maxStamina = state.maxStamina;
        hud.currentGuts = state.currentGuts;
        hud.maxGuts = state.maxGuts;
        hud.zoneCode = zoneCode;
        hud.statedMinDist = data.getMinDistance();
        hud.maxDist = maxDist;
        hud.cropActive = (state.cropEffortRatio > 0.0);
        hud.gutsActive = state.gutsActive;
        hud.exhaustedCapLocked = state.exhaustedCapLocked;
        hud.unevenBreathing = (state.unevenBreathingTicks > 0);
        hud.panicking = (state.panicTicks > 0);
        hud.onSlope = currentlyOnSlope;

        hud.trainingMode = state.trainingMode;
        hud.trainingRefusalActive = state.trainingRefusalActive;
        hud.inPool = inPool;
        hud.weightedTack = data.hasWeightedTack();
        hud.blinkersHood = data.hasBlinkersHood();
        hud.trainingSessionTicks = state.trainingSessionTicks;
        hud.witsCropChain = state.witsCropChain;

        hud.levelUpStatCode = state.levelUpStatCode;
        hud.levelUpFlashTicks = state.levelUpFlashTicks;

        hud.isBoxedIn = state.isBoxedIn;
        hud.inStartingGate = state.inStartingGate;
        hud.gateNumber = state.assignedGateNumber;
        hud.gateLockedYaw = state.gateLockedYaw;
        hud.gateBreakActive = state.gateBreakActive;
        hud.gateBreakResult = state.gateBreakResult;
        hud.gateBreakResultColor = state.gateBreakResultColor;
        hud.gateBreakResultTicks = state.gateBreakResultTicks;
        hud.activeStrategyTag = state.activeStrategyHudTag != null ? state.activeStrategyHudTag : "";
        hud.conditionHudTag = data.getCompactHudConditionTag();
        hud.careerEnded = data.isCareerEnded();
        hud.hasCorneringMaestro = hasCorneringMaestro;
        hud.personality = data.getPersonality();

        hud.hunger = (float) data.getHunger();
        hud.thirst = (float) data.getThirst();
        hud.fatigue = (float) data.getFatigue();
        hud.bond = (float) data.getBond();
        hud.mood = (float) data.getMood();
        hud.temperament = data.getTemperament();

        hud.spdCur = data.getSpeedCurrent();
        hud.spdMax = data.getSpeedMax();
        hud.spdCurExp = (float) data.getSpeedExp();
        hud.spdReqExp = (float) ThoroughbredData.getExpRequiredForNextPoint(data.getSpeedCurrent());
        hud.gainingSpd = gainingSpd;

        hud.stmCur = data.getStaminaCurrent();
        hud.stmMax = data.getStaminaMax();
        hud.stmCurExp = (float) data.getStaminaExp();
        hud.stmReqExp = (float) ThoroughbredData.getExpRequiredForNextPoint(data.getStaminaCurrent());
        hud.gainingStm = gainingStm;

        hud.pwrCur = data.getPowerCurrent();
        hud.pwrMax = data.getPowerMax();
        hud.pwrCurExp = (float) data.getPowerExp();
        hud.pwrReqExp = (float) ThoroughbredData.getExpRequiredForNextPoint(data.getPowerCurrent());
        hud.gainingPwr = gainingPwr;

        hud.gutsCur = data.getGutsCurrent();
        hud.gutsMax = data.getGutsMax();
        hud.gutsCurExp = (float) data.getGutsExp();
        hud.gutsReqExp = (float) ThoroughbredData.getExpRequiredForNextPoint(data.getGutsCurrent());
        hud.gainingGuts = gainingGuts;

        hud.witsCur = data.getWitsCurrent();
        hud.witsMax = data.getWitsMax();
        hud.witsCurExp = (float) data.getWitsExp();
        hud.witsReqExp = (float) ThoroughbredData.getExpRequiredForNextPoint(data.getWitsCurrent());
        hud.gainingWits = gainingWits;

        ModNetwork.sendHudSync(serverPlayer, hud);
    }

    // =========================================================================
    // STEP 17: FATIGUE-BASED AILMENT & INJURY ROLLS + DAILY PROGNOSIS SYSTEM
    // =========================================================================
    private static boolean tickRunningAilmentAndInjuryRolls(ServerPlayer rider, Horse horse, ThoroughbredData data,
                                                            HorseRunState state, double horseNormalCapKmh,
                                                            int zoneCode, boolean isRaining, boolean inActiveRace) {
        boolean changed = false;
        double fatigue = data.getFatigue();

        // 1. Atrial Fibrillation: rare chance when actively sprinting at top speed (>= Normal Cap - 1.5 km/h or under Crop drive)
        if (!state.trainingMode && !data.hasAilment(ThoroughbredData.AILMENT_AFIB)
                && (state.baseSpeedKmh >= horseNormalCapKmh - 1.5 || state.cropEffortRatio > 0.05)
                && state.baseSpeedKmh >= 42.0) {
            // Safe Zone (0-49% Fatigue): 0.00004 per 2s check (~0.10%-0.15% per race, halved for safe zone!)
            // Caution Zone (50-79% Fatigue): 0.00055 per 2s check (~2.0%-3.5% per race)
            // Supercritical Zone (80-100% Fatigue): 0.00180 per 2s check (~7.5%-11.5% per race)
            double afibChance = 0.00004;
            if (fatigue >= 80.0) {
                afibChance = 0.00180;
            } else if (fatigue >= 50.0) {
                afibChance = 0.00055;
            }
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_X_FACTOR) && fatigue < 80.0) {
                afibChance = 0.0;
            }
            if (horse.getRandom().nextDouble() < afibChance) {
                data.addAilment(ThoroughbredData.AILMENT_AFIB);
                horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_BREATHE, SoundSource.PLAYERS, 1.2F, 0.55F);
                rider.sendSystemMessage(Component.literal(
                        "§4§l[ATRIAL FIBRILLATION!] §c" + data.getName()
                        + " suffered an irregular heart rhythm at top speed and is rapidly losing pace! (Clears after race)"));
                rider.displayClientMessage(Component.literal(
                        "§4§l[ATRIAL FIBRILLATION!] §cRapidly losing speed!"), true);
                changed = true;
            }
        }

        // 2. Laryngeal Hemiplegia: occurs in Racing Mode, causing Stamina to deplete 2x faster until the race ends
        if (!state.trainingMode && !data.hasAilment(ThoroughbredData.AILMENT_LARYNGEAL) && state.baseSpeedKmh >= 38.0) {
            // Safe Zone (0-49% Fatigue in active race): 0.00007 per 2s check (~0.50% per 2,400m race, halved for safe zone!)
            // Caution Zone (50-79% Fatigue): 0.00060 per 2s check (~4.4% per 2,400m race)
            // Supercritical Zone (80-100% Fatigue): 0.00190 per 2s check (~13.3% per 2,400m race)
            double lhChance = 0.0;
            if (fatigue >= 80.0) {
                lhChance = 0.00190;
            } else if (fatigue >= 50.0) {
                lhChance = 0.00060;
            } else if (inActiveRace) {
                lhChance = 0.00007;
            }
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_ROARER_GENE)) {
                lhChance *= 2.5;
            }
            if (lhChance > 0.0 && horse.getRandom().nextDouble() < lhChance) {
                data.addAilment(ThoroughbredData.AILMENT_LARYNGEAL);
                horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.HORSE_BREATHE, SoundSource.PLAYERS, 1.1F, 0.65F);
                rider.sendSystemMessage(Component.literal(
                        "§c§l[LARYNGEAL HEMIPLEGIA!] §e" + data.getName()
                        + "'s airway is obstructed! Stamina will deplete 2x faster until the race ends!"));
                rider.displayClientMessage(Component.literal(
                        "§c§l[LARYNGEAL HEMIPLEGIA!] §e2x Stamina Drain!"), true);
                changed = true;
            }
        }

        // Optimal 0-40% (and up to 49%) Fatigue is safe from fatigue-induced ailments and injuries
        if (fatigue < 50.0) {
            return changed;
        }

        boolean supercritical = fatigue >= 80.0;
        double fatigueFactor = supercritical
                ? (2.2 + ((fatigue - 80.0) / 20.0) * 2.8)
                : (0.25 + ((fatigue - 50.0) / 30.0) * 1.25);

        double weakConstMult = data.hasGeneticTrait(ThoroughbredData.TRAIT_WEAK_CONSTITUTION) ? 2.0 : 1.0;

        // 3. Fever Roll (>= 50% Fatigue)
        if (!data.hasAilment(ThoroughbredData.AILMENT_FEVER)) {
            double feverChance = 0.0022 * fatigueFactor * (isRaining ? 1.75 : 1.0) * weakConstMult;
            if (horse.getRandom().nextDouble() < feverChance) {
                data.addAilment(ThoroughbredData.AILMENT_FEVER);
                rider.sendSystemMessage(Component.literal(
                        "§6§l[AILMENT: FEVER!] §e" + data.getName()
                        + " has developed a Fever from high-fatigue exertion! Power, Guts & Wits are halved, and it refuses solid food."));
                changed = true;
            }
        }

        // 4. EIPH / Bleeder Roll (>= 50% Fatigue when burning Guts, exhausted, or over-distance Zone 3)
        if (!data.hasAilment(ThoroughbredData.AILMENT_EIPH)
                && (state.gutsActive || state.exhaustedCapLocked || zoneCode == 3)) {
            double eiphChance = 0.0055 * fatigueFactor * (data.hasGeneticTrait(ThoroughbredData.TRAIT_DEEP_LUNG) ? 0.50 : 1.0);
            if (horse.getRandom().nextDouble() < eiphChance) {
                data.addAilment(ThoroughbredData.AILMENT_EIPH);
                rider.sendSystemMessage(Component.literal(
                        "§c§l[AILMENT: EIPH (BLEEDER)!] §e" + data.getName()
                        + " suffered Exercise-Induced Pulmonary Hemorrhage! Max Guts is halved and Stamina drains 35% faster."));
                changed = true;
            }
        }

        // 5. Colic Roll (>= 50% Fatigue while dehydrated or starving)
        if (!data.hasAilment(ThoroughbredData.AILMENT_COLIC)
                && (data.getHunger() < 20.0 || data.getThirst() < 20.0)) {
            double colicChance = 0.0050 * fatigueFactor * weakConstMult;
            if (horse.getRandom().nextDouble() < colicChance) {
                data.addAilment(ThoroughbredData.AILMENT_COLIC);
                rider.sendSystemMessage(Component.literal(
                        "§6§l[AILMENT: COLIC!] §e" + data.getName()
                        + " developed Colic from working on an empty/dehydrated stomach! It refuses solid food and is in pain."));
                changed = true;
            }
        }

        // 6. Limb Injury Roll (50-79% = very low but non-zero; 80%+ Unstable Legs = supercritical spike & higher Poor Prognosis risk!)
        if (!data.hasActiveInjury() && !data.hasPoorPrognosis()) {
            double injuryChance;
            if (!supercritical) {
                // 50-79% Fatigue: very low, but never zero
                injuryChance = 0.0007 + 0.0020 * ((fatigue - 50.0) / 30.0);
            } else {
                // 80-100% Supercritical State + Unstable Legs spike!
                double over80 = (fatigue - 80.0) / 20.0;
                injuryChance = (0.0075 + 0.0240 * over80)
                        * (data.hasAilment(ThoroughbredData.AILMENT_UNSTABLE_LEGS) ? 1.55 : 1.0);
            }
            if (state.gutsActive || state.exhaustedCapLocked) {
                injuryChance *= 1.35;
            }
            // Step 18: Small Hooves vs Large Hooves with Unstable Legs ailment!
            if (data.hasAilment(ThoroughbredData.AILMENT_UNSTABLE_LEGS)) {
                if (data.hasGeneticTrait(ThoroughbredData.TRAIT_SMALL_HOOVES)) {
                    injuryChance *= 2.0; // Small Hooves + Unstable Legs doubles injury chance!
                } else if (data.hasGeneticTrait(ThoroughbredData.TRAIT_LARGE_HOOVES)) {
                    injuryChance *= 0.5; // Large Hooves + Unstable Legs halves injury chance!
                }
            }
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_IRON_CONSTITUTION)) {
                injuryChance *= 0.65;
            }
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_GLASS_LEGS)) {
                injuryChance *= 1.50;
            }
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_BRITTLE_HOOVES)) {
                injuryChance *= 1.35;
            }

            if (horse.getRandom().nextDouble() < injuryChance) {
                String chosenInjury = rollSpecificInjuryForFatigue(horse, data, fatigue);
                inflictHorseInjuryWithFeedback(horse, data, state, chosenInjury);
                changed = true;
            }
        }

        return changed;
    }

    private static String rollSpecificInjuryForFatigue(Horse horse, ThoroughbredData data, double fatigue) {
        int r = horse.getRandom().nextInt(100);
        boolean onDirtLowApt = isDirtBlock(horse.level().getBlockState(horse.blockPosition().below()).getBlock())
                && data.getDirtAptitude() < 5;

        if (fatigue < 80.0) {
            // 50-79% Fatigue: mostly minor/moderate injuries, very small (2%) catastrophic chance
            if (data.hasGeneticTrait(ThoroughbredData.TRAIT_BRITTLE_HOOVES) && r < 72) return ThoroughbredData.INJURY_HOOF_CRACK;
            if (onDirtLowApt && r < 50) return ThoroughbredData.INJURY_HOOF_CRACK;
            if (r < 38) return ThoroughbredData.INJURY_HOOF_CRACK;
            if (r < 68) return ThoroughbredData.INJURY_LIGAMENT_STRAIN;
            if (r < 90) return ThoroughbredData.INJURY_BOWED_TENDON;
            if (r < 98) return ThoroughbredData.INJURY_NON_CATASTROPHIC_FRACTURE;
            return ThoroughbredData.INJURY_CATASTROPHIC_FRACTURE;
        } else {
            // 80-100% Supercritical Fatigue (Unstable Legs): Poor Prognosis (Catastrophic Fracture) risk spikes!
            int catChance = 22 + (int) Math.round(((fatigue - 80.0) / 20.0) * 18.0); // 22% at 80% -> 40% at 100%
            if (r < catChance) {
                return ThoroughbredData.INJURY_CATASTROPHIC_FRACTURE;
            }
            int rem = r - catChance;
            int span = Math.max(1, 100 - catChance);
            double norm = (double) rem / span;
            if (norm < 0.30) return ThoroughbredData.INJURY_NON_CATASTROPHIC_FRACTURE;
            if (norm < 0.65) return ThoroughbredData.INJURY_BOWED_TENDON;
            if (norm < 0.88) return ThoroughbredData.INJURY_LIGAMENT_STRAIN;
            return ThoroughbredData.INJURY_HOOF_CRACK;
        }
    }

    public static void inflictHorseInjuryWithFeedback(Horse horse, ThoroughbredData data, HorseRunState state, String injuryType) {
        if (horse == null || data == null || injuryType == null || injuryType.isBlank()) return;

        double baseDays = switch (injuryType) {
            case ThoroughbredData.INJURY_HOOF_CRACK -> 2.0;
            case ThoroughbredData.INJURY_LIGAMENT_STRAIN -> 4.0;
            case ThoroughbredData.INJURY_BOWED_TENDON -> 4.0;
            case ThoroughbredData.INJURY_NON_CATASTROPHIC_FRACTURE -> 6.0;
            case ThoroughbredData.INJURY_CATASTROPHIC_FRACTURE -> 999.0;
            default -> 4.0;
        };

        data.inflictInjury(injuryType, baseDays);
        if (state != null) {
            state.trainingMode = false;
            state.trainingRefusalActive = false;
            state.cropEffortRatio = 0.0;
            state.gutsActive = false;
        }

        if (ThoroughbredData.INJURY_CATASTROPHIC_FRACTURE.equalsIgnoreCase(injuryType)) {
            if (state != null) {
                state.stopAndResetSpeed();
            }
            Entity rider = horse.getControllingPassenger();
            horse.ejectPassengers();
            horse.setStanding(false);
            horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);

            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.HORSE_DEATH, SoundSource.NEUTRAL, 1.2F, 0.85F);
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 1.1F, 0.6F);

            if (horse.level() instanceof ServerLevel sl) {
                RacingManager.handleCatastrophicBreakdownInRace(sl, horse, data);
            }

            String msg = "§4§l[" + ThoroughbredData.tr("CATASTROPHIC FRACTURE — POOR PROGNOSIS!", "粉碎性骨折 — 預後不良！", "粉碎性骨折 — 预后不良！", "粉砕骨折 — 予後不良！") + "] §c"
                    + String.format(Locale.ROOT, ThoroughbredData.tr("%s suffered a catastrophic leg fracture, collapsed to the ground, and cannot move! Mood dropped to Awful.",
                            "%s 遭遇了粉碎性腿部骨折，痛苦倒地無法動彈！心情跌至極差。",
                            "%s 遭遇了粉碎性腿部骨折，痛苦倒地无法动弹！心情跌至极差。",
                            "%s は脚の粉砕骨折を発症し、その場に倒れ込んで動けなくなりました！やる気が絶不調に転落しました。"), data.getName());
            if (rider instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(msg));
                sp.displayClientMessage(Component.literal("§4§l[CATASTROPHIC FRACTURE!] §c" + data.getName() + " collapsed!"), true);
            } else {
                sendMessageToOwnerOrNearby(horse, data, msg);
            }
        } else {
            horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.HORSE_HURT, SoundSource.NEUTRAL, 1.1F, 0.85F);
            String msg = String.format(Locale.ROOT,
                    "§c§l[" + ThoroughbredData.tr("INJURY: %s!", "受傷: %s！", "受伤: %s！", "故障発症: %s！") + "] §e%s §f"
                    + ThoroughbredData.tr("suffered a %s and is now Lame (%.1f MC days recovery; Mood dropped to Awful; Training & Racing locked out)!",
                            "遭遇了 %s，現已跛行（需休養 %.1f 個遊戲日；心情跌至極差；暫停訓練與參賽）！",
                            "遭遇了 %s，现已跛行（需休养 %.1f 个游戏日；心情跌至极差；暂停训练与参赛）！",
                            "により %s を発症し跛行状態となりました（完治まで約 %.1f 日；やる気絶不調；調教・出走停止）！"),
                    injuryType.toUpperCase(Locale.ROOT), data.getName(), injuryType, baseDays);
            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(msg));
                sp.displayClientMessage(Component.literal("§c§l[INJURED: " + injuryType.toUpperCase(Locale.ROOT) + "!] §eSlow walk only!"), true);
            } else {
                sendMessageToOwnerOrNearby(horse, data, msg);
            }
        }

        data.saveToHorse(horse);
    }

    /**
     * Executes the daily Minecraft day rollover check for a Thoroughbred:
     * - Poor Prognosis daily roll:
     *   * Normal: 50% live, 49% die, 1% miracle
     *   * With Laminitis: 39% live, 60% die, 1% miracle (and miracle is 100% career-ending)
     *   * With Enchanted Golden Apple immunity: 99% live, 0% die, 1% miracle
     * - Non-catastrophic injury daily healing & recovery outcome roll
     * - Rested natural recovery for standalone Lameness / Fever / EIPH
     */
    /**
     * Executes the Real-Life Day rollover check (once every 24 real-life hours) for Old Age Death's Door
     * and the 50% Peaceful Passing roll while at Death's Door.
     * Dynamically scales the daily Death's Door entry probability according to the world's chosen Equine Time Flow:
     *   - Default 1-Year Time Flow (baseline):
     *       25yo = 0.005%, 26yo = 0.01%, 27yo = 0.05%, 28yo = 0.1%, 29yo = 0.5%, 30yo = 1%, 31yo+ = +1% per year
     *   - 3-Month & 4-Month Time Flows:
     *       25yo = 2%, 26yo = 4%, 27yo = 7%, 28yo = 12%, 29yo = 18%, 30yo = 25%, 31yo+ = +10% per year
     *   - Custom Time Flow:
     *       Dynamically toned based on customEquineYearDays (log-linearly interpolated between 122d and 365d,
     *       or scaled proportionally outside that range).
     * Once at Death's Door: 50% chance per real-life day to pass away peacefully.
     */
    public static void performRealLifeDayDeathsDoorRoll(Horse horse, ThoroughbredData data, HorseRunState state) {
        if (horse == null || data == null || !(horse.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        StudbookSavedData studbookForAge = StudbookSavedData.get(serverLevel);
        int ageYears = data.getEquineAgeYears(studbookForAge);

        // 1. DEATH'S DOOR REAL-LIFE DAILY 50% PEACEFUL PASSING ROLL
        if (data.isAtDeathsDoor()) {
            if (horse.getRandom().nextBoolean()) {
                // 50% chance of peaceful death hits!
                String passedMsg = "§e§l[" + ThoroughbredData.tr("PASSED ON", "安詳離世", "安详离世", "旅立ち") + "] §f"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s has passed away peacefully, at the age of %d. It will forever be remembered.",
                                "%s 於 %d 歲高齡安詳長眠。牠的傳奇將永遠被銘記。",
                                "%s 于 %d 岁高龄安详长眠。它的传奇将永远被铭记。",
                                "%s は享年 %d 歳、天寿を全うし安らかに旅立ちました。その名は永遠に記憶されるでしょう。"), data.getName(), ageYears);
                sendMessageToOwnerOrNearby(horse, data, passedMsg);

                serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.0F);
                studbookForAge.recordHorseDeath(horse, data,
                        "Passed away peacefully of old age at the age of " + ageYears, true);

                horse.getPersistentData().putBoolean("AIOThoroughbredPurged", true);
                horse.ejectPassengers();
                horse.discard();
            } else {
                data.saveToHorse(horse);
            }
            return;
        }

        // 2. OLD AGE (25-30+ EQUINE YEARS) REAL-LIFE DAILY ROLL TO ENTER DEATH'S DOOR
        if (!data.hasPoorPrognosis() && ageYears >= 25 && studbookForAge != null) {
            double deathsDoorChance = studbookForAge.calculateDailyDeathsDoorChance(ageYears);
            if (horse.getRandom().nextDouble() < deathsDoorChance) {
                data.setAtDeathsDoor(true);
                data.setWearingRaceSilk(false);
                data.setLastCheckedDeathsDoorRealDay(System.currentTimeMillis() / 86_400_000L);
                if (state != null) {
                    state.stopAndResetSpeed();
                    state.hostileToPlayer = false;
                    state.hostileTargetPlayer = null;
                    state.quirkyLeadRefusalTicks = 0;
                }
                horse.ejectPassengers();
                horse.setStanding(false);
                horse.setDeltaMovement(0.0, horse.getDeltaMovement().y, 0.0);

                String ddMsg = "§6§l[" + ThoroughbredData.tr("DEATH'S DOOR", "瀕死垂危", "濒死垂危", "臨死の淵") + "] §e"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s dropped to the floor, it's best if you go and visit it, it might not be here soon.",
                                "%s 癱倒在地動彈不得，建議你盡快前去探望，牠可能時日不多了。",
                                "%s 瘫倒在地动弹不得，建议你尽快前去探望，它可能时日不多了。",
                                "%s が倒れて動けなくなりました。まもなく最期を迎えるかもしれません、早く会いに行ってあげてください。"), data.getName());
                sendMessageToOwnerOrNearby(horse, data, ddMsg);
                data.saveToHorse(horse);
            }
        }
    }

    public static void performNewMinecraftDayRoll(Horse horse, ThoroughbredData data, HorseRunState state) {
        performNewMinecraftDayRoll(horse, data, state, true);
    }

    public static void performNewMinecraftDayRoll(Horse horse, ThoroughbredData data, HorseRunState state, boolean includeRealDayDeathsDoorRoll) {
        if (horse == null || data == null || !(horse.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (includeRealDayDeathsDoorRoll) {
            performRealLifeDayDeathsDoorRoll(horse, data, state);
            if (!horse.isAlive() || horse.isRemoved() || data.isAtDeathsDoor()) {
                return;
            }
        }

        // 1. POOR PROGNOSIS DAILY CHANCE ROLL
        if (data.hasPoorPrognosis()) {
            boolean appleImmune = data.hasPoorPrognosisAppleImmunity();
            boolean normalGoldenActive = data.isPoorPrognosisGoldenAppleActive();
            int normalGoldenDeathChance = data.getPoorPrognosisGoldenAppleDeathChance();
            boolean hasLaminitis = data.hasAilment(ThoroughbredData.AILMENT_LAMINITIS);
            if (appleImmune) {
                data.setPoorPrognosisAppleImmunity(false); // Consumed for today's roll!
            }
            if (normalGoldenActive) {
                data.setPoorPrognosisGoldenAppleActive(false); // Consumed for today's roll!
            }

            int roll = horse.getRandom().nextInt(100); // 0..99
            boolean miracle = (roll == 0); // 1% miracle chance (roll == 0)
            boolean lives;
            if (miracle) {
                lives = true;
            } else if (appleImmune) {
                lives = true; // Enchanted Golden Apple: 99% live (1..99), 0% die!
            } else if (normalGoldenActive) {
                // Normal Golden Apple with diminishing returns: deathChance = 0%, 5%, 10%, ..., 49%
                int liveThreshold = Mth.clamp(99 - normalGoldenDeathChance, 50, 99);
                lives = (roll >= 1 && roll <= liveThreshold);
            } else if (hasLaminitis) {
                lives = (roll >= 1 && roll <= 39); // 39% live (1..39), 60% die (40..99)
            } else {
                lives = (roll >= 1 && roll <= 50); // 50% live (1..50), 49% die (51..99)
            }

            if (miracle) {
                // 1% Miraculous Recovery (Sakura Laurel Miracle!)
                data.clearInjury();

                serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.9F, 1.2F);
                serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 0.9F, 1.25F);
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        horse.getX(), horse.getY() + 1.1, horse.getZ(), 35, 0.5, 0.5, 0.5, 0.12);

                // If Laminitis was present, career ends automatically with no 40/60 roll.
                // Otherwise: 40% Full Recovery, 60% Career-Ending (permanently locks max speed at 40km/h)
                boolean fullRecovery = !hasLaminitis && (horse.getRandom().nextInt(100) < 40);
                if (fullRecovery) {
                    String msg = "§a§l" + String.format(Locale.ROOT, ThoroughbredData.tr("Perhaps by some miracle, %s has recovered! And it is ready to continue running on the tracks!",
                            "奇蹟出現了，%s 竟然完全康復了！牠已經準備好重返賽場奔馳！",
                            "奇迹出现了，%s 竟然完全康复了！它已经准备好重返赛场奔驰！",
                            "奇跡が起きました！%s は見事に回復し、再び競馬場を走る準備が整いました！"), data.getName());
                    sendMessageToOwnerOrNearby(horse, data, msg);
                } else {
                    data.setCareerEnded(true);
                    String msg = "§e§l" + String.format(Locale.ROOT, ThoroughbredData.tr("Perhaps by some miracle, %s has recovered! However, it can no longer run as fast as it was - the injury has completely ruined its legs.",
                            "奇蹟出現了，%s 撿回了一命！然而牠再也無法像從前那樣飛馳——這場重傷徹底摧毀了牠的腿部。",
                            "奇迹出现了，%s 捡回了一命！然而它再也无法像从前那样飞驰——这场重伤彻底摧毁了它的腿部。",
                            "奇跡的に %s は一命を取り留めました！しかし脚の負傷が深刻なため、以前のように速く走ることはもうできません。"), data.getName());
                    sendMessageToOwnerOrNearby(horse, data, msg);
                }
                data.saveToHorse(horse);
                return;
            } else if (lives) {
                // Lives another day on borrowed time
                String msg = "§c" + String.format(Locale.ROOT, ThoroughbredData.tr("%s lives for another day - but on borrowed time. It is in its best interest you euthanize it with /tb euthanize.",
                        "%s 多活了一天——但已時日無多。為了牠的福利著想，建議使用 /tb euthanize 進行人道安樂死。",
                        "%s 多活了一天——但已时日无多。为了它的福利着想，建议使用 /tb euthanize 进行人道安乐死。",
                        "%s は命をつなぎ止めましたが、余命は幾ばくもありません。苦痛を長引かせないため、/tb euthanize による安楽死を推奨します。"), data.getName());
                sendMessageToOwnerOrNearby(horse, data, msg);

                // Chance for Laminitis to appear in horses with Poor Prognosis (25% daily chance)
                if (!hasLaminitis && horse.getRandom().nextDouble() < 0.25) {
                    data.addAilment(ThoroughbredData.AILMENT_LAMINITIS);
                    sendMessageToOwnerOrNearby(horse, data,
                            "§4§l[" + ThoroughbredData.tr("Laminitis Developed", "併發蹄葉炎", "并发蹄叶炎", "蹄葉炎発症") + "] §c"
                            + String.format(Locale.ROOT, ThoroughbredData.tr("%s has developed Laminitis while immobilized! Its daily death rate has risen to 60% (living rate dropped to 39%).",
                                    "%s 在無法動彈期間併發了蹄葉炎！每日死亡機率攀升至 60%（存活率驟降至 39%）。",
                                    "%s 在无法动弹期间并发了蹄叶炎！每日死亡机率攀升至 60%（存活率骤降至 39%）。",
                                    "起立不能の間に %s は蹄葉炎を発症しました！日ごとの死亡率が 60% に上昇しました（生存率は 39% に低下）。"), data.getName()));
                }
                data.saveToHorse(horse);
                return;
            } else {
                if (data.hasTriedToSaveWithApples()) {
                    // Compassionate passing when the owner tried to save the horse with Golden / Enchanted Golden Apples
                    // (No Wither sound, and not in deep red!)
                    String passedOnMsg = "§e[" + ThoroughbredData.tr("PASSED ON", "安詳離世", "安详离世", "旅立ち") + "] §f"
                            + String.format(Locale.ROOT, ThoroughbredData.tr("Despite all your efforts to save it, %s couldn't recover and passed away, with less pain than it was originally. You have tried your best, do not feel bad.",
                                    "儘管你傾盡全力救治，%s 仍未能康復而離世，但比起最初少受了許多痛苦。你已經盡力了，請不要太難過。",
                                    "尽管你竭尽全力救治，%s 仍未能康复而离世，但比起最初减轻了许多痛苦。你已经尽力了，请不要太难过。",
                                    "治療の甲斐なく、%s は回復に至らず息を引き取りました。しかし当初よりも苦痛は和らいでいました。全力を尽くしたのです、自分を責めないでください。"), data.getName());
                    sendMessageToOwnerOrNearby(horse, data, passedOnMsg);

                    StudbookSavedData studbook = StudbookSavedData.get(serverLevel);
                    studbook.recordHorseDeath(horse, data, "Passed away peacefully despite treatment efforts (Poor Prognosis)", true);
                } else {
                    // Default Poor Prognosis death — plays Wither sound just like /tb kill and shows [DEATH NOTIFICATION] in deep red!
                    String deathMsg = "§4§l[" + ThoroughbredData.tr("DEATH NOTIFICATION", "死亡通知", "死亡通知", "死亡通知") + "] "
                            + String.format(Locale.ROOT, ThoroughbredData.tr("%s has passed away - in excruciating pain. You did this to yourself.",
                                    "%s 在極度劇痛中離世了。這是你自找的。",
                                    "%s 在极度剧痛中离世了。这是你自找的。",
                                    "%s は激痛に苦しみながら息を引き取りました。自業自得です。"), data.getName());
                    sendMessageToOwnerOrNearby(horse, data, deathMsg);

                    serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7F, 0.8F);
                    if (data.hasOwner()) {
                        try {
                            UUID ownerUuid = UUID.fromString(data.getOwnerUUID());
                            ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
                            if (ownerPlayer != null && ownerPlayer.distanceToSqr(horse) > 64.0) {
                                ownerPlayer.level().playSound(null, ownerPlayer.getX(), ownerPlayer.getY(), ownerPlayer.getZ(),
                                        SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 0.8F);
                            }
                        } catch (Exception ignored) {}
                    }

                    StudbookSavedData studbook = StudbookSavedData.get(serverLevel);
                    studbook.recordHorseDeath(horse, data, "Passed away in excruciating pain (Poor Prognosis)", true);
                }

                horse.getPersistentData().putBoolean("AIOThoroughbredPurged", true);
                horse.ejectPassengers();
                horse.discard();
                return;
            }
        }

        // 2. NON-CATASTROPHIC INJURY DAILY RECOVERY PROGRESS
        boolean changed = false;
        if (data.hasActiveInjury()) {
            double dailyHeal = (data.getFatigue() <= 25.0 && data.getHunger() >= 60.0 && data.getThirst() >= 60.0) ? 1.35 : 1.0;
            double nextDays = data.getInjuryDaysRemaining() - dailyHeal;
            if (nextDays <= 0.0) {
                completeNonCatastrophicInjuryRecovery(horse, data);
            } else {
                data.setInjuryDaysRemaining(nextDays);
                sendMessageToOwnerOrNearby(horse, data, String.format(Locale.ROOT,
                        "§e[Injury Recovery] §6%s§f's §c%s §fis healing (%.1f MC days remaining).",
                        data.getName(), data.getActiveInjury(), nextDays));
            }
            changed = true;
        } else if (data.hasAilment(ThoroughbredData.AILMENT_LAMENESS) && data.getFatigue() <= 35.0) {
            data.removeAilment(ThoroughbredData.AILMENT_LAMENESS);
            sendMessageToOwnerOrNearby(horse, data,
                    "§a[" + ThoroughbredData.tr("Recovered", "已康復", "已康复", "回復") + "] §6"
                    + String.format(Locale.ROOT, ThoroughbredData.tr("%s's post-race Lameness has cleared after a day of rest!",
                            "%s 的賽後跛行在休息一天後已經完全痊癒！",
                            "%s 的赛后跛行在休息一天后已经完全痊愈！",
                            "%s のレース後の跛行は1日の休養で完治しました！"), data.getName()));
            changed = true;
        }

        // 3. Natural rested recovery chance for Fever & EIPH when kept well-fed and low-fatigue
        if (data.getFatigue() <= 30.0 && data.getHunger() >= 60.0 && data.getThirst() >= 60.0) {
            if (data.hasAilment(ThoroughbredData.AILMENT_FEVER) && horse.getRandom().nextDouble() < 0.50) {
                data.removeAilment(ThoroughbredData.AILMENT_FEVER);
                sendMessageToOwnerOrNearby(horse, data,
                        "§a[" + ThoroughbredData.tr("Recovered", "已康復", "已康复", "回復") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s's Fever broke naturally after resting!",
                                "%s 的發燒在休養後自然退燒康復！",
                                "%s 的发烧在休养后自然退烧康复！",
                                "%s の発熱は休養により自然に回復しました！"), data.getName()));
                changed = true;
            }
            if (data.hasAilment(ThoroughbredData.AILMENT_EIPH) && horse.getRandom().nextDouble() < 0.50) {
                data.removeAilment(ThoroughbredData.AILMENT_EIPH);
                sendMessageToOwnerOrNearby(horse, data,
                        "§a[" + ThoroughbredData.tr("Recovered", "已康復", "已康复", "回復") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s's lungs have cleared from EIPH after resting!",
                                "%s 的肺出血（EIPH）在休養後已完全平復！",
                                "%s 的肺出血（EIPH）在休养后已完全平复！",
                                "%s の肺出血（EIPH）は休養により回復しました！"), data.getName()));
                changed = true;
            }
        }

        // 4. Clingy Personality: loses Mood (-8.0) if its owner hasn't interacted with or ridden it in the past Minecraft day!
        if (data.hasPersonality(ThoroughbredData.PERS_CLINGY) && data.hasOwner()) {
            long curMcDay = serverLevel.getDayTime() / 24000L;
            if (data.getLastOwnerInteractionMcDay() >= 0L && (curMcDay - data.getLastOwnerInteractionMcDay()) >= 1L) {
                data.setMood(data.getMood() - 8.0);
                sendMessageToOwnerOrNearby(horse, data,
                        "§5[" + ThoroughbredData.tr("Clingy Lonely", "黏人孤單", "粘人孤单", "さびしがり") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s missed its owner's attention today and lost -8 Mood!",
                                "%s 今天因為缺少主人的關心陪伴而情緒低落，心情 -8！",
                                "%s 今天因为缺少主人的关心陪伴而情绪低落，心情 -8！",
                                "%s は今日オーナーの愛情不足を感じて寂しがり、やる気が -8 低下しました！"), data.getName()));
                changed = true;
            }
        }

        // 5. Overweight Ailment: clears on a new day if Hunger is kept moderate (< 80%)
        if (data.hasAilment(ThoroughbredData.AILMENT_OVERWEIGHT) && data.getHunger() < 80.0) {
            data.removeAilment(ThoroughbredData.AILMENT_OVERWEIGHT);
            data.setOverfedStreak(0);
            sendMessageToOwnerOrNearby(horse, data,
                    "§a[" + ThoroughbredData.tr("Recovered", "已康復", "已康复", "回復") + "] §6"
                    + String.format(Locale.ROOT, ThoroughbredData.tr("%s returned to its ideal racing weight overnight!",
                            "%s 經過一整夜已恢復理想的競賽體態體重！",
                            "%s 经过一整夜已恢复理想的竞赛体态体重！",
                            "%s は夜の間に理想的な競走馬体重へと戻りました！"), data.getName()));
            changed = true;
        }

        if (changed) {
            data.saveToHorse(horse);
        }
    }

    public static void completeNonCatastrophicInjuryRecovery(Horse horse, ThoroughbredData data) {
        if (horse == null || data == null || !data.hasActiveInjury()) return;
        String injury = data.getActiveInjury();
        boolean vetBonus = data.isVetTreatedThisInjury();
        data.clearInjury();

        int roll = horse.getRandom().nextInt(100);
        if (ThoroughbredData.INJURY_HOOF_CRACK.equalsIgnoreCase(injury)) {
            sendMessageToOwnerOrNearby(horse, data,
                    "§a§l[" + ThoroughbredData.tr("Injury Healed!", "傷勢痊癒！", "伤势痊愈！", "負傷完治！") + "] §6"
                    + String.format(Locale.ROOT, ThoroughbredData.tr("%s has made a 100% full recovery from its Hoof Crack!",
                            "%s 的蹄裂已 100% 完全康復！",
                            "%s 的蹄裂已 100% 完全康复！",
                            "%s の裂蹄は100%完全に回復しました！"), data.getName()));
        } else if (ThoroughbredData.INJURY_LIGAMENT_STRAIN.equalsIgnoreCase(injury)) {
            int fullChance = vetBonus ? 95 : 85;
            if (roll < fullChance) {
                sendMessageToOwnerOrNearby(horse, data,
                        "§a§l[" + ThoroughbredData.tr("Injury Healed!", "傷勢痊癒！", "伤势痊愈！", "負傷完治！") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s has made a full recovery from its Suspensory Ligament Strain!",
                                "%s 的懸韌帶拉傷已完全康復！",
                                "%s 的悬韧带拉伤已完全康复！",
                                "%s の繋靭帯炎は完全に回復しました！"), data.getName()));
            } else {
                data.setSpeedCurrent(data.getSpeedCurrent() - 40);
                data.setSpeedMax(data.getSpeedMax() - 40);
                data.setPowerCurrent(data.getPowerCurrent() - 40);
                data.setPowerMax(data.getPowerMax() - 40);
                sendMessageToOwnerOrNearby(horse, data,
                        "§e§l[" + ThoroughbredData.tr("Healed with Scarring", "癒合但遺留後遺症", "愈合但遗留后遗症", "治癒・後遺症あり") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s recovered from its Suspensory Ligament Strain, but suffered a slight permanent drop (-4.0) in Speed and Power.",
                                "%s 的懸韌帶拉傷已癒合，但遺留了輕微後遺症，速度與力量永久下降 -4.0。",
                                "%s 的悬韧带拉伤已愈合，但遗留了轻微后遗症，速度与力量永久下降 -4.0。",
                                "%s の繋靭帯炎は治癒しましたが、後遺症によりスピードとパワーが永久に -4.0 低下しました。"), data.getName()));
            }
        } else if (ThoroughbredData.INJURY_BOWED_TENDON.equalsIgnoreCase(injury)) {
            // 75%-25% split confirmed by user (or 85%-15% if vet treated)
            int fullChance = vetBonus ? 85 : 75;
            if (roll < fullChance) {
                sendMessageToOwnerOrNearby(horse, data,
                        "§a§l[" + ThoroughbredData.tr("Tendon Healed!", "屈腱痊癒！", "屈腱痊愈！", "屈腱炎完治！") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s has made a full recovery (75% roll) from its Bowed Tendon with no stat loss!",
                                "%s 的屈腱炎已完全康復（75% 機率判定成功），能力值毫無損失！",
                                "%s 的屈腱炎已完全康复（75% 概率判定成功），能力值毫无损失！",
                                "%s の屈腱炎は完全回復しました（75%判定成功）、能力低下はありません！"), data.getName()));
            } else {
                data.setSpeedCurrent(data.getSpeedCurrent() - 50);
                data.setSpeedMax(data.getSpeedMax() - 50);
                data.setPowerCurrent(data.getPowerCurrent() - 50);
                data.setPowerMax(data.getPowerMax() - 50);
                sendMessageToOwnerOrNearby(horse, data,
                        "§c§l[" + ThoroughbredData.tr("Tendon Scarring", "屈腱纖維化疤痕", "屈腱纤维化疤痕", "屈腱炎・瘢痕化") + "] §6"
                        + String.format(Locale.ROOT, ThoroughbredData.tr("%s recovered from its Bowed Tendon (25% roll), but suffered a permanent drop (-5.0) in Speed and Power!",
                                "%s 的屈腱炎雖已癒合（25% 判定），但遺留了纖維化疤痕，速度與力量永久下降 -5.0！",
                                "%s 的屈腱炎虽已愈合（25% 判定），但遗留了纤维化疤痕，速度与力量永久下降 -5.0！",
                                "%s の屈腱炎は治癒しましたが（25%判定）、瘢痕化によりスピードとパワーが永久に -5.0 低下しました！"), data.getName()));
            }
        } else if (ThoroughbredData.INJURY_NON_CATASTROPHIC_FRACTURE.equalsIgnoreCase(injury)) {
            // 80% complete recovery, 20% ends a racing career (locks max speed at 40 km/h)
            int fullChance = vetBonus ? 90 : 80;
            if (roll < fullChance) {
                sendMessageToOwnerOrNearby(horse, data,
                        "§a§l[" + ThoroughbredData.tr("Fracture Healed!", "骨折痊癒！", "骨折痊愈！", "骨折完治！") + "] §6" + data.getName()
                        + " §f" + ThoroughbredData.tr("has made a complete recovery (80% roll) from its Non-Catastrophic Fracture and is ready to resume full training!",
                                "輕微骨折已完全康復（80% 機率判定成功），準備好重返全速訓練與賽事！",
                                "轻微骨折已完全康复（80% 概率判定成功），准备好重返全速训练与赛事！",
                                "軽微な骨折から完全回復しました（80%判定）。本格調教とレースへの復帰が可能です！"));
            } else {
                data.setCareerEnded(true);
                sendMessageToOwnerOrNearby(horse, data,
                        "§e§l[" + ThoroughbredData.tr("Fracture Healed — Permanent Career End", "骨折癒合 — 永久退役繁殖", "骨折愈合 — 永久退役繁殖", "骨折治癒・競走馬引退") + "] §6" + data.getName()
                        + " §f" + ThoroughbredData.tr("healed from its Non-Catastrophic Fracture (20% roll), but can no longer run at racing speed! It has been retired for breeding.",
                                "輕微骨折已癒合（20% 殘留影響判定），但無法再以競速奔馳！現已永久轉為種馬/繁殖牝馬退役。",
                                "轻微骨折已愈合（20% 残留影响判定），但无法再以竞速奔驰！现已永久转为种马/繁殖母马退役。",
                                "軽微な骨折は治癒しましたが（20%判定）、競走速度での走行は不可能となりました。繁殖馬として引退しました。"));
            }
        }
        data.saveToHorse(horse);
    }

    public static void sendMessageToOwnerOrNearby(Horse horse, ThoroughbredData data, String message) {
        if (!(horse.level() instanceof ServerLevel serverLevel)) return;
        Component comp = Component.literal(message);
        boolean sentToOwner = false;
        if (data.hasOwner()) {
            try {
                UUID ownerUuid = UUID.fromString(data.getOwnerUUID());
                ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
                if (ownerPlayer != null) {
                    ownerPlayer.sendSystemMessage(comp);
                    sentToOwner = true;
                }
            } catch (Exception ignored) {}
        }
        AABB box = horse.getBoundingBox().inflate(48.0, 24.0, 48.0);
        for (ServerPlayer sp : serverLevel.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (!sentToOwner || !data.isOwnedBy(sp.getUUID())) {
                sp.sendSystemMessage(comp);
            }
        }
    }

    /**
     * Spawns a newborn Foal beside its mother when a pregnant Broodmare's gestation completes
     * (either naturally or via /tb breed birth [mare name]), rolling its 100 HP genetics,
     * 4-Generation Pedigree Tree, Inbreeding/Linebreeding, Genetic Traits (Small/Large Hooves),
     * and True Pure-White Coat inheritance!
     */
    public static final double MAX_BREEDING_PROXIMITY_BLOCKS = 8.0;
    public static final double MAX_BREEDING_PROXIMITY_SQR = MAX_BREEDING_PROXIMITY_BLOCKS * MAX_BREEDING_PROXIMITY_BLOCKS;

    public static boolean isHorseInBreedingRitual(Horse horse) {
        if (horse == null) return false;
        return getOrCreateState(horse).breedingRitualActive;
    }

    /**
     * Initiates the vanilla-style Love Mode courtship ritual between a Stud Stallion and an In-Heat Broodmare
     * in close proximity (<= 8 blocks). Both horses enter Love Mode, walk up to each other, face each other,
     * and nuzzle with heart particles for ~3 seconds (60 ticks) before gestation begins!
     */
    public static void startBreedingLoveRitual(ServerLevel level, Horse sireHorse, ThoroughbredData sireData,
                                               Horse damHorse, ThoroughbredData damData, long paidStudFee) {
        if (level == null || sireHorse == null || damHorse == null || sireData == null || damData == null) return;

        HorseRunState sireState = getOrCreateState(sireHorse);
        HorseRunState damState  = getOrCreateState(damHorse);

        sireState.breedingRitualActive = true;
        sireState.breedingRitualIsDam = false;
        sireState.breedingPartnerUUID = damHorse.getUUID();
        sireState.breedingRitualLoveTicks = 0;
        sireState.breedingRitualTimeoutTicks = 0;
        sireState.pendingStudFeePaid = 0L;

        damState.breedingRitualActive = true;
        damState.breedingRitualIsDam = true;
        damState.breedingPartnerUUID = sireHorse.getUUID();
        damState.breedingRitualLoveTicks = 0;
        damState.breedingRitualTimeoutTicks = 0;
        damState.pendingStudFeePaid = paidStudFee;

        sireHorse.setStanding(false);
        damHorse.setStanding(false);
        sireHorse.setInLove(null);
        damHorse.setInLove(null);
        level.broadcastEntityEvent(sireHorse, (byte) 18);
        level.broadcastEntityEvent(damHorse, (byte) 18);

        level.playSound(null, damHorse.getX(), damHorse.getY(), damHorse.getZ(),
                SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.HEART,
                damHorse.getX(), damHorse.getY() + 1.3, damHorse.getZ(), 8, 0.45, 0.35, 0.45, 0.02);
        level.sendParticles(ParticleTypes.HEART,
                sireHorse.getX(), sireHorse.getY() + 1.3, sireHorse.getZ(), 8, 0.45, 0.35, 0.45, 0.02);

        String feeTag = paidStudFee > 0L ? String.format(Locale.ROOT, " §7(Stud Fee: §a$%,d§7)", paidStudFee) : "";
        sendMessageToOwnerOrNearby(damHorse, damData, String.format(Locale.ROOT,
                "§d§l[" + ThoroughbredData.tr("Love Mode — Breeding Ritual", "求偶愛心模式 — 配種儀式", "求偶爱心模式 — 配种仪式", "求愛モード・交配の儀") + "] §b%s §f"
                + ThoroughbredData.tr("and", "與", "与", "と") + " §d%s §f"
                + ThoroughbredData.tr("have entered Love Mode and are courting!", "已進入求偶愛心模式並開始親暱！", "已进入求偶爱心模式并开始亲暱！", "が求愛モードに入り、寄り添っています！") + "%s",
                sireData.getName(), damData.getName(), feeTag));
    }

    private static void tickBreedingLoveRitual(ServerLevel level, Horse horse, ThoroughbredData data, HorseRunState state) {
        if (!state.breedingRitualActive || state.breedingPartnerUUID == null) {
            state.clearBreedingRitual();
            horse.resetLove();
            return;
        }

        Entity partnerEntity = level.getEntity(state.breedingPartnerUUID);
        if (!(partnerEntity instanceof Horse partner) || !partner.isAlive() || !ThoroughbredData.isThoroughbred(partner)) {
            abortBreedingLoveRitual(level, horse, data, state, null, null,
                    "§c[Breeding Interrupted] Courting partner is no longer nearby!");
            return;
        }

        HorseRunState partnerState = getOrCreateState(partner);
        ThoroughbredData partnerData = ThoroughbredData.fromHorse(partner);

        // Keep both horses in vanilla love mode while courting
        if (horse.getInLoveTime() < 40) {
            horse.setInLoveTime(100);
        }

        double distSq = horse.distanceToSqr(partner);
        // If horses are pulled > 12 blocks apart or mounted mid-ritual, or timeout (15s = 300 ticks) expires, abort!
        if (distSq > 144.0 || horse.isVehicle() || partner.isVehicle() || state.breedingRitualTimeoutTicks > 300) {
            abortBreedingLoveRitual(level, horse, data, state, partner, partnerState,
                    "§c[Breeding Interrupted] §b" + (state.breedingRitualIsDam ? partnerData.getName() : data.getName())
                    + " §cand §d" + (state.breedingRitualIsDam ? data.getName() : partnerData.getName())
                    + " §cwere separated before completing their Love Mode breeding ritual!");
            return;
        }

        // Face partner and walk up to each other just like vanilla BreedGoal!
        horse.getLookControl().setLookAt(partner, 10.0F, (float) horse.getMaxHeadXRot());
        double dx = partner.getX() - horse.getX();
        double dz = partner.getZ() - horse.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 0.05) {
            float faceYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            horse.setYRot(faceYaw);
            horse.setYHeadRot(faceYaw);
            horse.setYBodyRot(faceYaw);
        }

        if (distSq > 4.0) {
            horse.getNavigation().moveTo(partner, 1.05D);
            if (horizDist > 1.8 && horse.onGround()) {
                double stepSpeed = 0.14;
                horse.setDeltaMovement(
                        (dx / horizDist) * stepSpeed,
                        horse.getDeltaMovement().y,
                        (dz / horizDist) * stepSpeed
                );
                horse.hasImpulse = true;
            }
        } else {
            horse.getNavigation().stop();
        }

        if (horse.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.HEART,
                    horse.getX(), horse.getY() + 1.35, horse.getZ(),
                    3, 0.35, 0.25, 0.35, 0.02);
        }

        // The Mare's state tracks the 60-tick (3.0-second) close-proximity courtship completion!
        if (state.breedingRitualIsDam) {
            state.breedingRitualTimeoutTicks++;
            if (distSq <= 9.5) { // Within ~3 blocks of each other (vanilla BreedGoal threshold)
                state.breedingRitualLoveTicks++;
                if (state.breedingRitualLoveTicks == 30) {
                    level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                            SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.0F, 1.15F);
                }
                if (state.breedingRitualLoveTicks >= 56) {
                    completeBreedingLoveRitual(level, partner, partnerData, partnerState, horse, data, state);
                }
            }
        }
    }

    private static void abortBreedingLoveRitual(ServerLevel level, Horse horseA, ThoroughbredData dataA, HorseRunState stateA,
                                                Horse horseB, HorseRunState stateB, String reasonMsg) {
        long refundFee = stateA.pendingStudFeePaid;
        if (refundFee <= 0L && stateB != null) {
            refundFee = stateB.pendingStudFeePaid;
        }
        ThoroughbredData damData = stateA.breedingRitualIsDam ? dataA : (horseB != null ? ThoroughbredData.fromHorse(horseB) : null);
        ThoroughbredData sireData = !stateA.breedingRitualIsDam ? dataA : (horseB != null ? ThoroughbredData.fromHorse(horseB) : null);

        stateA.clearBreedingRitual();
        horseA.resetLove();
        horseA.getNavigation().stop();
        if (stateB != null) {
            stateB.clearBreedingRitual();
        }
        if (horseB != null) {
            horseB.resetLove();
            horseB.getNavigation().stop();
        }

        if (refundFee > 0L && damData != null && sireData != null && damData.hasOwner() && sireData.hasOwner()) {
            try {
                UUID damOwner = UUID.fromString(damData.getOwnerUUID());
                UUID sireOwner = UUID.fromString(sireData.getOwnerUUID());
                StudbookSavedData sb = StudbookSavedData.get(level);
                sb.tryDeductBalance(sireOwner, Math.min(refundFee, sb.getBalance(sireOwner)));
                sb.addBalance(damOwner, refundFee);
                reasonMsg += String.format(Locale.ROOT, " §e(Stud Fee of $%,d was refunded.)", refundFee);
            } catch (Exception ignored) {}
        }

        sendMessageToOwnerOrNearby(horseA, dataA, reasonMsg);
    }

    public static void completeBreedingLoveRitual(ServerLevel level, Horse sireHorse, ThoroughbredData sireData, HorseRunState sireState,
                                                  Horse damHorse, ThoroughbredData damData, HorseRunState damState) {
        long paidStudFee = damState != null ? damState.pendingStudFeePaid : 0L;
        if (sireState != null) sireState.clearBreedingRitual();
        if (damState != null) damState.clearBreedingRitual();
        if (sireHorse != null) {
            sireHorse.resetLove();
            sireHorse.getNavigation().stop();
        }
        if (damHorse != null) {
            damHorse.resetLove();
            damHorse.getNavigation().stop();
        }

        StudbookSavedData studbook = StudbookSavedData.get(level);
        long gestationMillis = studbook.rollGestationDurationMillis(level.getRandom());
        damData.startPregnancy(sireHorse, sireData, gestationMillis);
        damData.saveToHorse(damHorse);
        studbook.archiveHorsePedigree(sireData);
        studbook.archiveHorsePedigree(damData);

        level.playSound(null, damHorse.getX(), damHorse.getY(), damHorse.getZ(),
                SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.15F);
        level.playSound(null, damHorse.getX(), damHorse.getY(), damHorse.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 1.3F);
        level.sendParticles(ParticleTypes.HEART,
                damHorse.getX(), damHorse.getY() + 1.3, damHorse.getZ(), 18, 0.5, 0.4, 0.5, 0.03);
        if (sireHorse != null) {
            level.sendParticles(ParticleTypes.HEART,
                    sireHorse.getX(), sireHorse.getY() + 1.3, sireHorse.getZ(), 14, 0.5, 0.4, 0.5, 0.03);
        }

        double gestationDays = gestationMillis / 86_400_000.0;
        String feeTag = paidStudFee > 0L ? String.format(Locale.ROOT, " (Stud Fee Paid: §a$%,d§f)", paidStudFee) : "";
        String msg = String.format(Locale.ROOT,
                "§d§l[Breeding Successful — Gestation Started!] §fBroodmare §d%s §f(%d%% Potential | 100/100 HP) has been covered by Stud Stallion §b%s §f(%d%% Potential | 100/100 HP)%s!\n"
                + "§7• Expected Gestation: §e%.1f Real-Life Days",
                damData.getName(), damData.getHereditaryPoints(),
                sireData.getName(), sireData.getHereditaryPoints(),
                feeTag, gestationDays);
        sendMessageToOwnerOrNearby(damHorse, damData, msg);
    }

    public static void completePendingBreedingRitualImmediately(ServerLevel level, Horse damHorse, ThoroughbredData damData) {
        if (level == null || damHorse == null || damData == null) return;
        HorseRunState damState = getOrCreateState(damHorse);
        if (!damState.breedingRitualActive || !damState.breedingRitualIsDam || damState.breedingPartnerUUID == null) {
            return;
        }
        Entity p = level.getEntity(damState.breedingPartnerUUID);
        if (p instanceof Horse sireHorse && ThoroughbredData.isThoroughbred(sireHorse)) {
            ThoroughbredData sireData = ThoroughbredData.fromHorse(sireHorse);
            HorseRunState sireState = getOrCreateState(sireHorse);
            completeBreedingLoveRitual(level, sireHorse, sireData, sireState, damHorse, damData, damState);
        }
    }

    public static void setHorseCoatVariantAndMarkings(Horse horse, Variant variant, Markings markings) {
        if (horse == null || variant == null || markings == null) return;
        int packedVariant = (variant.getId() & 255) | ((markings.getId() << 8) & 0xFF00);
        net.minecraft.nbt.CompoundTag vanillaNbt = new net.minecraft.nbt.CompoundTag();
        horse.addAdditionalSaveData(vanillaNbt);
        vanillaNbt.putInt("Variant", packedVariant);
        horse.readAdditionalSaveData(vanillaNbt);
    }

    public static Horse deliverFoalFromPregnantMare(ServerLevel serverLevel, Horse damEntity, ThoroughbredData damData) {
        if (serverLevel == null || damEntity == null || damData == null) return null;

        ThoroughbredData sireData = new ThoroughbredData();
        if (damData.getPregnantSireNbt() != null && !damData.getPregnantSireNbt().isEmpty()) {
            sireData.loadFromNBT(damData.getPregnantSireNbt());
        } else {
            sireData.generateRandomGenetics(serverLevel.getRandom(),
                    damData.getPregnantSireName().isEmpty() ? "Unknown Sire" : damData.getPregnantSireName(), "Male");
        }

        Horse foalEntity = EntityType.HORSE.create(serverLevel);
        if (foalEntity == null) return null;

        foalEntity.moveTo(damEntity.getX() + 0.6, damEntity.getY(), damEntity.getZ() + 0.6, damEntity.getYRot(), 0.0F);
        foalEntity.setTamed(true);
        foalEntity.setBaby(true);
        foalEntity.setAge(-24000);

        String foalGender = serverLevel.getRandom().nextBoolean() ? "Male" : "Female";
        StudbookSavedData studbook = StudbookSavedData.get(serverLevel);

        String tempFoalName = studbook.generateDefaultFoalName(damData.getName());
        studbook.registerName(tempFoalName);

        ThoroughbredData foalData = new ThoroughbredData();
        foalData.generateBredFoalGenetics(
                serverLevel.getRandom(),
                tempFoalName,
                foalGender,
                sireData,
                damData,
                studbook.getFoalGrowthDurationMillis()
        );

        if (damData.hasOwner()) {
            try {
                foalData.setOwner(UUID.fromString(damData.getOwnerUUID()), damData.getOwnerName());
            } catch (Exception ignored) {}
        }

        // Set coat variant & markings on the Foal entity
        if (foalData.isPureWhiteCoat()) {
            setHorseCoatVariantAndMarkings(foalEntity, Variant.WHITE, Markings.NONE);
        } else {
            Variant inheritedVar = serverLevel.getRandom().nextBoolean()
                    ? damEntity.getVariant()
                    : Variant.byId(serverLevel.getRandom().nextInt(7));
            Markings inheritedMark = serverLevel.getRandom().nextBoolean()
                    ? damEntity.getMarkings()
                    : Markings.byId(serverLevel.getRandom().nextInt(5));
            setHorseCoatVariantAndMarkings(foalEntity, inheritedVar, inheritedMark);
        }

        foalEntity.setCustomName(Component.literal(tempFoalName));
        serverLevel.addFreshEntity(foalEntity);
        foalData.saveToHorse(foalEntity);

        damData.clearPregnancy();
        damData.saveToHorse(damEntity);

        serverLevel.playSound(null, foalEntity.getX(), foalEntity.getY(), foalEntity.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.35F);
        serverLevel.playSound(null, foalEntity.getX(), foalEntity.getY(), foalEntity.getZ(),
                SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.5F);
        serverLevel.sendParticles(ParticleTypes.HEART,
                foalEntity.getX(), foalEntity.getY() + 0.9, foalEntity.getZ(), 18, 0.45, 0.4, 0.45, 0.03);
        if (foalData.isPureWhiteCoat()) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    foalEntity.getX(), foalEntity.getY() + 1.0, foalEntity.getZ(), 25, 0.4, 0.4, 0.4, 0.05);
        }

        String coatTag = foalData.isPureWhiteCoat() ? " §f§l[★ PURE WHITE COAT! ★]" : "";
        String atavismTag = foalData.isAtavismTriggered() ? " §e§l[★ GENERATION-SKIPPING ATAVISM! ★]" : "";
        String birthMsg = String.format(Locale.ROOT,
                "§d§l[FOAL BORN!] §6%s §fgave birth to a healthy §b%s §ffoal sired by §6%s§f!%s%s\n"
                + "§7• Birth Name: §e%s §7(Can be renamed with a §fName Tag §7while a Foal/Yearling, or officially registered with a §6Thoroughbred Registry Form §7at §a2 Equine Years old§7!)\n"
                + "§7• Hereditary Potential: §a%d%% §7(100/100 HP) | Pedigree: §f%s\n"
                + "§7• Genetic Traits: %s",
                damData.getName(),
                "Female".equalsIgnoreCase(foalGender) ? "Filly (Female)" : "Colt (Male)",
                sireData.getName(),
                coatTag,
                atavismTag,
                tempFoalName,
                foalData.getHereditaryPoints(),
                foalData.getInbreedingSummary(),
                foalData.getFormattedTraitsSummary());
        sendMessageToOwnerOrNearby(damEntity, damData, birthMsg);
        return foalEntity;
    }

    public static void growFoalIntoYearling(ServerLevel serverLevel, Horse horse, ThoroughbredData data) {
        if (horse == null || data == null) return;
        long yearlingDuration = (serverLevel != null)
                ? StudbookSavedData.get(serverLevel).getFoalGrowthDurationMillis()
                : data.getFoalGrowDurationMillis();
        data.advanceFoalToYearling(yearlingDuration);
        horse.setBaby(false);
        horse.setAge(0);
        data.saveToHorse(horse);
        if (serverLevel != null) {
            StudbookSavedData.get(serverLevel).updateHorseRecord(horse, data);
            serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.85F, 1.15F);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    horse.getX(), horse.getY() + 1.2, horse.getZ(), 12, 0.45, 0.4, 0.45, 0.05);
        }
        sendMessageToOwnerOrNearby(horse, data,
                "§6§l[" + ThoroughbredData.tr("Yearling (1 Equine Year Old)!", "週歲馬 (1 歲馬)！", "周岁马 (1 岁马)！", "当歳・1歳馬成長！") + "] §e"
                + String.format(Locale.ROOT, ThoroughbredData.tr("%s has reached 1 Equine Year old and grown into a Yearling! (At 2 Equine Years old, it will be eligible for the Thoroughbred Registry Form, riding, training, and racing!)",
                        "%s 已滿 1 馬齡並成長為週歲馬！（滿 2 馬齡時即可使用純種馬登記表註冊、騎乘、調教及參賽！）",
                        "%s 已满 1 马龄并成长为周岁马！（满 2 马龄时即可使用纯种马登记表注册、骑乘、调教及参赛！）",
                        "%s は馬齢1歳を迎え、1歳馬へと成長しました！（2歳になると血統登録表での登録、騎乗、調教、レース出走が可能になります！）"), data.getName()));
    }

    public static void matureYearlingIntoAdult(ServerLevel serverLevel, Horse horse, ThoroughbredData data) {
        if (horse == null || data == null) return;
        long oneYearMillis = (serverLevel != null)
                ? StudbookSavedData.get(serverLevel).getFoalGrowthDurationMillis()
                : data.getFoalGrowDurationMillis();
        data.setEquineAgeYears(2, oneYearMillis);
        horse.setBaby(false);
        horse.setAge(0);
        data.saveToHorse(horse);
        if (serverLevel != null) {
            StudbookSavedData.get(serverLevel).updateHorseRecord(horse, data);
            serverLevel.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.35F);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    horse.getX(), horse.getY() + 1.2, horse.getZ(), 20, 0.45, 0.4, 0.45, 0.05);
        }
        sendMessageToOwnerOrNearby(horse, data,
                "§a§l[" + ThoroughbredData.tr("2 Equine Years Old — Ready to Train & Race!", "2 馬齡成年 — 準備調教與參賽！", "2 马龄成年 — 准备调教与参赛！", "馬齢2歳・調教＆デビュー可能！") + "] §6"
                + String.format(Locale.ROOT, ThoroughbredData.tr("%s has reached 2 Equine Years old! It can now be officially registered with a Thoroughbred Registry Form, mounted, trained, and entered into races!",
                        "%s 已滿 2 馬齡！現在可以使用純種馬登記表進行官方登記註冊、正式騎乘、調教及報名出賽！",
                        "%s 已满 2 马龄！现在可以使用纯种马登记表进行官方登记注册、正式骑乘、调教及报名出赛！",
                        "%s は馬齢2歳に達しました！血統登録表による公式登録、騎乗、本格調教、そしてレースへの出走が可能になりました！"), data.getName()));
    }

    public static void growUpFoalNow(ServerLevel serverLevel, Horse horse, ThoroughbredData data) {
        if (horse == null || data == null) return;
        if (data.isFoal()) {
            growFoalIntoYearling(serverLevel, horse, data);
        } else {
            matureYearlingIntoAdult(serverLevel, horse, data);
        }
    }

    private static boolean isBreezingInCompany(Horse horse) {
        AABB box = horse.getBoundingBox().inflate(6.0, 2.5, 6.0);
        List<Horse> nearby = horse.level().getEntitiesOfClass(Horse.class, box,
                other -> other != horse && other.isAlive());
        return !nearby.isEmpty();
    }

    private static int detectSurfaceRating(Level level, BlockPos horsePos, ThoroughbredData data) {
        Block feetBlock = level.getBlockState(horsePos).getBlock();
        Block belowBlock = level.getBlockState(horsePos.below()).getBlock();

        if (isTurfBlock(feetBlock) || isTurfBlock(belowBlock)) {
            return data.getTurfAptitude();
        }
        if (isDirtBlock(feetBlock) || isDirtBlock(belowBlock)) {
            return data.getDirtAptitude();
        }
        return 8;
    }

    private static boolean isTurfBlock(Block block) {
        return block == Blocks.GRASS_BLOCK
                || block == Blocks.MOSS_BLOCK
                || block == Blocks.MOSS_CARPET
                || block == Blocks.GREEN_CARPET
                || block == Blocks.LIME_CARPET
                || block == Blocks.GREEN_CONCRETE_POWDER
                || block == Blocks.LIME_CONCRETE_POWDER
                || block == Blocks.GREEN_WOOL
                || block == Blocks.LIME_WOOL;
    }

    private static boolean isDirtBlock(Block block) {
        return block == Blocks.DIRT
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT
                || block == Blocks.DIRT_PATH
                || block == Blocks.PODZOL
                || block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.GRAVEL
                || block == Blocks.SOUL_SAND
                || block == Blocks.SOUL_SOIL
                || block == Blocks.BROWN_CONCRETE_POWDER
                || block == Blocks.YELLOW_CONCRETE_POWDER
                || block == Blocks.TERRACOTTA
                || block == Blocks.PACKED_MUD;
    }

    // =========================================================================
    // 6. RENDER 3-LINE RACE LOGO, Official LIVE RACE LEADERBOARD & RACING/TRAINING HUD
    // =========================================================================
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!clientHud.ridingThoroughbred) {
            return;
        }
        if (event.getOverlay() == VanillaGuiOverlay.JUMP_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Render the 3-Line Race Logo Display (or 3-Act G1 Special Effect Broadcast Display!)
        if (isClientLogoActive()) {
            renderThreeLineRaceLogo(graphics, mc, screenWidth, screenHeight, event.getPartialTick());
        }

        int playerMountHorseId = (mc.player.getVehicle() instanceof Horse ridden) ? ridden.getId() : -1;

        try {
            if (mc.getLanguageManager() != null) {
                ThoroughbredData.setActiveClientLangCode(mc.getLanguageManager().getSelected());
            }
        } catch (Throwable ignored) {
        }

        // Render the Live Race Leaderboard & Vertical Pack Radar HUD on the right side (appears 5.0s after race starts!)
        if (clientRaceLeaderboard.active && !clientRaceLeaderboard.runners.isEmpty()
                && clientRaceLeaderboard.racePhase >= 2
                && (clientRaceLeaderboard.elapsedSeconds >= 5.0F || clientRaceLeaderboard.racePhase >= 3)) {
            renderJraRaceLeaderboardHud(graphics, mc, screenWidth, screenHeight, playerMountHorseId);
        }

        if (!(mc.player.getVehicle() instanceof Horse horse) || !clientHud.ridingThoroughbred) {
            return;
        }

        // ---------------------------------------------------------------------
        // TOP-RIGHT MOOD & TEMPERAMENT BADGE
        // ---------------------------------------------------------------------
        renderTopRightMoodBadge(graphics, mc, screenWidth);

        // ---------------------------------------------------------------------
        // DYNAMIC SURVIVAL MODE & MULTI-ROW HORSE HP OFFSET
        // ---------------------------------------------------------------------
        boolean isSurvivalGuiVisible = mc.gameMode != null && mc.gameMode.canHurtPlayer();
        int survivalOffset = 0;
        if (isSurvivalGuiVisible) {
            int mountHearts = Mth.ceil(horse.getMaxHealth() / 2.0F);
            int mountRows = Math.max(1, Mth.ceil(mountHearts / 10.0F));
            boolean underwater = mc.player.getAirSupply() < mc.player.getMaxAirSupply();
            int rightExtraPx = (mountRows - 1) * 10 + (underwater ? 10 : 0);

            int playerHearts = Mth.ceil((mc.player.getMaxHealth() + mc.player.getAbsorptionAmount()) / 2.0F);
            int playerHealthRows = Math.max(1, Mth.ceil(playerHearts / 10.0F));
            int playerRowSpacing = Math.max(10 - (playerHealthRows - 2), 3);
            int leftExtraPx = (playerHealthRows - 1) * playerRowSpacing + (mc.player.getArmorValue() > 0 ? 10 : 0);

            int highestExtraStackPx = Math.max(leftExtraPx, rightExtraPx);
            survivalOffset = 28 + highestExtraStackPx;
        }

        int barWidth = 182;
        int barLeft = (screenWidth / 2) - 91;
        int staminaBarY = screenHeight - 29 - survivalOffset;
        int speedBarY   = screenHeight - 38 - survivalOffset;
        int jumpBarY    = screenHeight - 47 - survivalOffset;
        int barHeight   = 5;

        long gameTime = mc.level != null ? mc.level.getGameTime() : 0;

        float jumpScale = mc.player.getJumpRidingScale();
        boolean chargingJump = !clientHud.inPool && jumpScale > 0.005F;
        int headerBaseY = chargingJump ? (jumpBarY - 11) : (speedBarY - 11);

        if (chargingJump) {
            String jmpTag = ThoroughbredData.tr("JMP", "跳躍", "跳跃", "飛越");
            int jmpTagW = mc.font.width(jmpTag);
            graphics.drawString(mc.font, jmpTag, barLeft - jmpTagW - 5, jumpBarY - 1, 0x55FFFF, true);

            graphics.fill(barLeft - 1, jumpBarY - 1, barLeft + barWidth + 1, jumpBarY + barHeight + 1, 0xFF000000);
            graphics.fill(barLeft, jumpBarY, barLeft + barWidth, jumpBarY + barHeight, 0xFF1A2B3C);

            int jumpFill = (int) Math.round(Mth.clamp(jumpScale, 0.0F, 1.0F) * barWidth);
            int jumpColor = jumpScale > 0.85F ? 0xFF55FF55 : 0xFF00E5FF;
            graphics.fill(barLeft, jumpBarY, barLeft + jumpFill, jumpBarY + barHeight, jumpColor);
        }

        // Starting Gate Break / Results Display
        if (clientHud.gateBreakResultTicks > 0 && !clientHud.gateBreakResult.isEmpty()) {
            String locBreak = ThoroughbredData.localizeHudTag(clientHud.gateBreakResult);
            graphics.drawCenteredString(mc.font, "§l" + locBreak, screenWidth / 2, screenHeight / 2 - 35, clientHud.gateBreakResultColor);
        } else if (clientHud.gateBreakActive && (gameTime / 2) % 2 == 0) {
            String breakPrompt = "§a§l" + ThoroughbredData.tr("▶▶ GATES OPEN! TAP [W] TO BREAK! ◀◀", "▶▶ 閘門開啟！立即按 [W] 出閘！ ◀◀", "▶▶ 闸门开启！立即按 [W] 出闸！ ◀◀", "▶▶ ゲート開！[W]キーでスタート！ ◀◀");
            graphics.drawCenteredString(mc.font, breakPrompt, screenWidth / 2, screenHeight / 2 - 35, 0x55FF55);
        }

        // TOP LINE: Training Timer OR Status Warning + CLEAN [POOL] / [UPHILL] TAGS
        int statusStartX = barLeft;
        if (clientHud.inStartingGate) {
            String gateStr = "§6§l" + String.format(Locale.ROOT,
                    ThoroughbredData.tr("[STALL #%d LOCKED]", "[%d號閘廂已鎖定]", "[%d号闸厢已锁定]", "[%d番ゲートロック中]"),
                    clientHud.gateNumber);
            graphics.drawString(mc.font, gateStr, barLeft, headerBaseY - 10, 0xFFD800, true);
            statusStartX = barLeft + mc.font.width(gateStr) + 5;
        } else if (clientHud.trainingRefusalActive) {
            String refStr = "§c§l" + ThoroughbredData.tr("[REFUSING TO MOVE!]", "[拒絕前進！]", "[拒绝前进！]", "[前進拒否！]");
            graphics.drawString(mc.font, refStr, barLeft, headerBaseY - 10, 0xFF5555, true);
            statusStartX = barLeft + mc.font.width(refStr) + 5;
        } else if (clientHud.trainingMode) {
            double totalSec = clientHud.trainingSessionTicks / 20.0;
            int mins = (int) (totalSec / 60);
            double secs = totalSec % 60.0;
            String timerStr = String.format(Locale.ROOT, "§a§l%s §e⏱ %02d:%04.1f",
                    ThoroughbredData.tr("[TRAINING]", "[訓練模式]", "[训练模式]", "[調教中]"), mins, secs);
            graphics.drawString(mc.font, timerStr, barLeft, headerBaseY - 10, 0xFFFFFF, true);
            statusStartX = barLeft + mc.font.width(timerStr) + 5;
        } else if (clientHud.exhaustedCapLocked) {
            String exStr = "§c" + ThoroughbredData.tr("[EXHAUSTED]", "[體力耗盡]", "[体力耗尽]", "[バテ状態]");
            graphics.drawString(mc.font, exStr, barLeft, headerBaseY - 10, 0xFFFFFF, true);
            statusStartX = barLeft + mc.font.width(exStr) + 5;
        } else if (clientHud.panicking) {
            String panStr = "§c§l" + ThoroughbredData.tr("[PANIC!]", "[失控暴走！]", "[失控暴走！]", "[掛かり/暴走！]");
            graphics.drawString(mc.font, panStr, barLeft, headerBaseY - 10, 0xFFFFFF, true);
            statusStartX = barLeft + mc.font.width(panStr) + 5;
        } else if (clientHud.unevenBreathing) {
            String ragStr = "§3" + ThoroughbredData.tr("[RAGGED BREATH]", "[呼吸紊亂]", "[呼吸紊乱]", "[息乱れ]");
            graphics.drawString(mc.font, ragStr, barLeft, headerBaseY - 10, 0xFFFFFF, true);
            statusStartX = barLeft + mc.font.width(ragStr) + 5;
        }

        String conditionTags = "";
        if (clientHud.conditionHudTag != null && !clientHud.conditionHudTag.isEmpty()) {
            String locCond = ThoroughbredData.localizeHudTag(clientHud.conditionHudTag);
            if (clientHud.conditionHudTag.contains("[COLIC]")) {
                locCond += String.format(Locale.ROOT, " §c(♥ %.1f/%.1f)", horse.getHealth() / 2.0F, horse.getMaxHealth() / 2.0F);
            }
            conditionTags += " " + locCond;
        }
        if (clientHud.activeStrategyTag != null && !clientHud.activeStrategyTag.isEmpty() && !clientHud.trainingMode) {
            conditionTags += " " + ThoroughbredData.localizeHudTag(clientHud.activeStrategyTag);
        }
        if (clientHud.inPool) {
            conditionTags += " §b" + ThoroughbredData.tr("[POOL]", "[泳池訓練]", "[泳池训练]", "[プール調教]");
        }
        if (clientHud.onSlope) {
            conditionTags += " §e" + ThoroughbredData.tr("[UPHILL]", "[坡道]", "[坡道]", "[坂路]");
        }
        if (!conditionTags.isEmpty()) {
            String trimmedTags = conditionTags.trim();
            int maxTagW = Math.max(60, (barLeft + barWidth + 60) - statusStartX);
            int tagW = mc.font.width(trimmedTags);
            if (tagW <= maxTagW) {
                graphics.drawString(mc.font, trimmedTags, statusStartX, headerBaseY - 10, 0xFFFFFF, true);
            } else {
                float scale = Math.max(0.58F, (float) maxTagW / (float) tagW);
                graphics.pose().pushPose();
                graphics.pose().translate((float) statusStartX, (float) (headerBaseY - 10) + (1.0F - scale) * 4.0F, 0.0F);
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.drawString(mc.font, trimmedTags, 0, 0, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }

        // LOWER LINE: Short Mode Header ([PRACTICE] or [RACING]) on left, Distance Aptitude cleanly on right!
        String modeHeader = clientHud.trainingMode
                ? ("§a" + ThoroughbredData.tr("[🐎 PRACTICE]", "[🐎 單騎訓練]", "[🐎 单骑训练]", "[🐎 調教モード]"))
                : ("§b" + ThoroughbredData.tr("[🐎 RACING]", "[🐎 實戰競走]", "[🐎 实战竞走]", "[🐎 レースモード]"));
        graphics.drawString(mc.font, modeHeader, barLeft, headerBaseY, 0xFFFFFF, true);

        String distLabel = String.format(Locale.ROOT, "§7%.0fm / %d-%dm",
                clientHud.distanceMeters, clientHud.statedMinDist, clientHud.maxDist);
        int distWidth = mc.font.width(distLabel);
        graphics.drawString(mc.font, distLabel, barLeft + barWidth - distWidth, headerBaseY, 0xFFFFFF, true);

        // Left Labels: SPD, STM / GUTS
        String spdTag = ThoroughbredData.tr("SPD", "速度", "速度", "速力");
        int spdTagW = mc.font.width(spdTag);
        graphics.drawString(mc.font, spdTag, barLeft - spdTagW - 5, speedBarY - 1, 0xFFFFFF, true);

        boolean showingGuts = clientHud.gutsActive && clientHud.currentGuts > 0.0;
        String bottomLeftTag = showingGuts
                ? ThoroughbredData.tr("GUTS", "根性", "根性", "根性")
                : ThoroughbredData.tr("STM", "耐力", "耐力", "持久");
        int bottomLeftColor = showingGuts ? 0xCC55FF : 0xFFFF3333;
        if (!showingGuts) {
            if (clientHud.panicking && (gameTime / 2) % 2 == 0) {
                bottomLeftColor = 0xFFFFFF;
            } else if (clientHud.unevenBreathing && (gameTime / 4) % 2 == 0) {
                bottomLeftColor = 0x55FFFF;
            }
        }
        int bottomTagW = mc.font.width(bottomLeftTag);
        graphics.drawString(mc.font, bottomLeftTag, barLeft - bottomTagW - 5, staminaBarY - 1, bottomLeftColor, true);

        // Bar 1: Speed Gauge
        graphics.fill(barLeft - 1, speedBarY - 1, barLeft + barWidth + 1, speedBarY + barHeight + 1, 0xFF000000);
        graphics.fill(barLeft, speedBarY, barLeft + barWidth, speedBarY + barHeight, 0xFF222222);

        int x35 = barLeft + (int) Math.round((STAMINA_THRESHOLD_KMH / MAX_GAUGE_KMH) * barWidth);
        int xNormalCap = barLeft + (int) Math.round(Mth.clamp(clientHud.normalCapKmh / MAX_GAUGE_KMH, 0.0, 1.0) * barWidth);
        int xCropMax   = barLeft + (int) Math.round(Mth.clamp(clientHud.cropMaxKmh / MAX_GAUGE_KMH, 0.0, 1.0) * barWidth);
        int xUnlockedCrop = barLeft + (int) Math.round(Mth.clamp(clientHud.unlockedCropCapKmh / MAX_GAUGE_KMH, 0.0, 1.0) * barWidth);

        if (xCropMax < barLeft + barWidth) {
            graphics.fill(xCropMax, speedBarY, barLeft + barWidth, speedBarY + barHeight, 0xFF111111);
        }

        double baseWithoutStride = Math.max(0.0, clientHud.displayedSpeedKmh - clientHud.bigStrideBonusKmh);
        int totalSpeedFillX = barLeft + (int) Math.round(Mth.clamp(clientHud.displayedSpeedKmh / MAX_GAUGE_KMH, 0.0, 1.0) * barWidth);
        int baseSpeedFillX  = barLeft + (int) Math.round(Mth.clamp(baseWithoutStride / MAX_GAUGE_KMH, 0.0, 1.0) * barWidth);

        int greenEnd = Math.min(totalSpeedFillX, x35);
        for (int px = barLeft; px < greenEnd; px += 6) {
            int segEnd = Math.min(px + 5, greenEnd);
            graphics.fill(px, speedBarY + 1, segEnd, speedBarY + barHeight - 1, 0xFF55CC33);
        }

        if (baseSpeedFillX > x35) {
            graphics.fill(x35, speedBarY, baseSpeedFillX, speedBarY + barHeight, 0xFFFFD800);
        }

        if (clientHud.bigStrideBonusKmh > 0.05 && totalSpeedFillX > baseSpeedFillX) {
            int strideStart = Math.max(x35, baseSpeedFillX);
            graphics.fill(strideStart, speedBarY, totalSpeedFillX, speedBarY + barHeight, 0xFF44FF44);
        }

        graphics.fill(x35, speedBarY - 1, x35 + 1, speedBarY + barHeight + 1, 0xFFFFFFFF);
        graphics.fill(xNormalCap, speedBarY - 1, xNormalCap + 1, speedBarY + barHeight + 1, 0xFFFF3333);
        if (xUnlockedCrop > xNormalCap) {
            graphics.fill(xUnlockedCrop, speedBarY - 1, xUnlockedCrop + 1, speedBarY + barHeight + 1, 0xFFFFAA00);
        }
        if (xCropMax < barLeft + barWidth) {
            graphics.fill(xCropMax, speedBarY - 1, xCropMax + 1, speedBarY + barHeight + 1, 0xFF888888);
        }

        String speedText = String.format(Locale.ROOT, "%.1f km/h", clientHud.displayedSpeedKmh);
        graphics.drawString(mc.font, speedText, barLeft + barWidth + 6, speedBarY - 1, 0xFFFFFF, true);

        // Bar 2: Stamina / Guts Bar
        graphics.fill(barLeft - 1, staminaBarY - 1, barLeft + barWidth + 1, staminaBarY + barHeight + 1, 0xFF000000);
        graphics.fill(barLeft, staminaBarY, barLeft + barWidth, staminaBarY + barHeight, 0xFF222222);

        if (showingGuts) {
            double gutsRatio = Mth.clamp(clientHud.currentGuts / Math.max(1.0, clientHud.maxGuts), 0.0, 1.0);
            int gutsFill = (int) Math.round(gutsRatio * barWidth);
            graphics.fill(barLeft, staminaBarY, barLeft + gutsFill, staminaBarY + barHeight, 0xFFB822FF);

            String gutsText = String.format(Locale.ROOT, "%.1f / %.0f", clientHud.currentGuts, clientHud.maxGuts);
            graphics.drawString(mc.font, gutsText, barLeft + barWidth + 6, staminaBarY - 1, 0xCC55FF, true);
        } else {
            double stamRatio = Mth.clamp(clientHud.currentStamina / Math.max(1.0, clientHud.maxStamina), 0.0, 1.0);
            int stamFill = (int) Math.round(stamRatio * barWidth);

            int barColor = 0xFFFF1E56;
            if (clientHud.zoneCode == 2) {
                if ((gameTime / 6) % 2 == 0) {
                    barColor = 0xFFFFAA00;
                }
            } else if (clientHud.zoneCode == 3 || clientHud.panicking) {
                if ((gameTime / 2) % 2 == 0) {
                    barColor = 0xFFFFFFFF;
                } else {
                    barColor = 0xFFFF0000;
                }
            } else if (clientHud.unevenBreathing) {
                if ((gameTime / 5) % 2 == 0) {
                    barColor = 0xFF55CCCC;
                }
            } else if (clientHud.exhaustedCapLocked) {
                int x50Percent = barLeft + (barWidth / 2);
                graphics.fill(x50Percent, staminaBarY - 1, x50Percent + 1, staminaBarY + barHeight + 1, 0xFFFFFF55);
            }

            graphics.fill(barLeft, staminaBarY, barLeft + stamFill, staminaBarY + barHeight, barColor);

            int textColor = (clientHud.displayedSpeedKmh > STAMINA_THRESHOLD_KMH || clientHud.inPool) ? 0xFFAA00 : 0xFFFF3333;
            String stamText = String.format(Locale.ROOT, "%.1f / %.0f", clientHud.currentStamina, clientHud.maxStamina);
            graphics.drawString(mc.font, stamText, barLeft + barWidth + 6, staminaBarY - 1, textColor, true);
        }

        // Left of GUI: Hunger & Fatigue
        int sideBarW = 46;
        int leftSideX = barLeft - 38 - sideBarW;
        if (leftSideX >= 4) {
            String hngLabel = String.format(Locale.ROOT, "§6%s §f%.0f%%", ThoroughbredData.tr("HNG", "飽食", "饱食", "満腹"), clientHud.hunger);
            graphics.drawString(mc.font, hngLabel, leftSideX, speedBarY - 10, 0xFFFFFF, true);
            drawMiniCareBar(graphics, leftSideX, speedBarY, sideBarW, barHeight, clientHud.hunger / 100.0F,
                    clientHud.hunger > 25.0F ? 0xFFFF9922 : 0xFFFF3333);

            drawMiniCareBar(graphics, leftSideX, staminaBarY, sideBarW, barHeight, clientHud.fatigue / 100.0F,
                    clientHud.fatigue < 70.0F ? 0xFFFFDD44 : 0xFFFF2222);
            String ftgTag = String.format(Locale.ROOT, "§c%s §7%.0f%%", ThoroughbredData.tr("FTG", "疲勞", "疲劳", "疲労"), clientHud.fatigue);
            graphics.drawString(mc.font, ftgTag, leftSideX, staminaBarY + 7, 0xFFFFFF, true);
        }

        // Right of GUI: Thirst & Bond
        int rightSideX = barLeft + barWidth + 78;
        if (rightSideX + sideBarW <= screenWidth - 4) {
            String thrLabel = String.format(Locale.ROOT, "§b%s §f%.0f%%", ThoroughbredData.tr("THR", "口渴", "口渴", "水分"), clientHud.thirst);
            graphics.drawString(mc.font, thrLabel, rightSideX, speedBarY - 10, 0xFFFFFF, true);
            drawMiniCareBar(graphics, rightSideX, speedBarY, sideBarW, barHeight, clientHud.thirst / 100.0F,
                    clientHud.thirst > 25.0F ? 0xFF22B8FF : 0xFFFF3333);

            drawMiniCareBar(graphics, rightSideX, staminaBarY, sideBarW, barHeight, clientHud.bond / 100.0F, 0xFFFF66B2);
            String bndTag = String.format(Locale.ROOT, "§d%s §7%.1f%%", ThoroughbredData.tr("BND", "羈絆", "羁绊", "絆"), clientHud.bond);
            graphics.drawString(mc.font, bndTag, rightSideX, staminaBarY + 7, 0xFFFFFF, true);
        }

        // Right-Side Training EXP Bars (only when Training Mode is active and not in an active race)
        if (clientHud.trainingMode && (!clientRaceLeaderboard.active || clientRaceLeaderboard.racePhase < 2)) {
            int panelW = 126;
            int panelH = 110;
            int panelX = screenWidth - panelW - 6;
            int panelY = (screenHeight / 2) - (panelH / 2) + 8;

            if (clientHud.levelUpFlashTicks > 0) {
                String flashText = "";
                int flashColor = 0xFFFFFF;
                switch (clientHud.levelUpStatCode) {
                    case 1 -> { flashText = ThoroughbredData.tr("▲ SPEED UP! (+0.1)", "▲ 速度提升! (+0.1)", "▲ 速度提升! (+0.1)", "▲ スピードUP! (+0.1)");   flashColor = 0xFF55B4FF; }
                    case 2 -> { flashText = ThoroughbredData.tr("▲ STAMINA UP! (+0.1)", "▲ 耐力提升! (+0.1)", "▲ 耐力提升! (+0.1)", "▲ スタミナUP! (+0.1)"); flashColor = 0xFFFF3333; }
                    case 3 -> { flashText = ThoroughbredData.tr("▲ POWER UP! (+0.1)", "▲ 力量提升! (+0.1)", "▲ 力量提升! (+0.1)", "▲ パワーUP! (+0.1)");   flashColor = 0xFFFF9922; }
                    case 4 -> { flashText = ThoroughbredData.tr("▲ GUTS UP! (+0.1)", "▲ 根性提升! (+0.1)", "▲ 根性提升! (+0.1)", "▲ 根性UP! (+0.1)");    flashColor = 0xFFCC55FF; }
                    case 5 -> { flashText = ThoroughbredData.tr("▲ WITS UP! (+0.1)", "▲ 智力提升! (+0.1)", "▲ 智力提升! (+0.1)", "▲ 賢さUP! (+0.1)");    flashColor = 0xFF55FF7F; }
                }
                if (!flashText.isEmpty() && (clientHud.levelUpFlashTicks / 4) % 2 == 0) {
                    graphics.drawString(mc.font, "§l" + flashText, panelX, panelY - 12, flashColor, true);
                }
            }

            graphics.fill(panelX - 4, panelY - 4, panelX + panelW + 2, panelY + panelH, 0xCC0D1117);
            graphics.fill(panelX - 4, panelY - 4, panelX + panelW + 2, panelY - 3, 0xFF55FF55);

            String title = "§a§l" + ThoroughbredData.tr("TRAINING EXP", "訓練經驗", "训练经验", "調教経験値");
            if (clientHud.witsCropChain > 0) {
                title += " §e(x" + clientHud.witsCropChain + ")";
            }
            graphics.drawString(mc.font, title, panelX, panelY, 0xFFFFFF, true);

            int rowY = panelY + 13;
            drawTrainingExpRow(graphics, mc, panelX, rowY,      ThoroughbredData.tr("SPD", "速度", "速度", "速力"),  clientHud.spdCur,  clientHud.spdMax,  clientHud.spdCurExp,  clientHud.spdReqExp,  clientHud.gainingSpd,  0xFF55B4FF, gameTime);
            drawTrainingExpRow(graphics, mc, panelX, rowY + 19, ThoroughbredData.tr("STM", "耐力", "耐力", "持久"),  clientHud.stmCur,  clientHud.stmMax,  clientHud.stmCurExp,  clientHud.stmReqExp,  clientHud.gainingStm,  0xFFFF3333, gameTime);
            drawTrainingExpRow(graphics, mc, panelX, rowY + 38, ThoroughbredData.tr("PWR", "力量", "力量", "馬力"),  clientHud.pwrCur,  clientHud.pwrMax,  clientHud.pwrCurExp,  clientHud.pwrReqExp,  clientHud.gainingPwr,  0xFFFF9922, gameTime);
            drawTrainingExpRow(graphics, mc, panelX, rowY + 57, ThoroughbredData.tr("GUTS", "根性", "根性", "根性"), clientHud.gutsCur, clientHud.gutsMax, clientHud.gutsCurExp, clientHud.gutsReqExp, clientHud.gainingGuts, 0xFFCC55FF, gameTime);
            drawTrainingExpRow(graphics, mc, panelX, rowY + 76, ThoroughbredData.tr("WITS", "智力", "智力", "賢さ"), clientHud.witsCur, clientHud.witsMax, clientHud.witsCurExp, clientHud.witsReqExp, clientHud.gainingWits, 0xFF55FF7F, gameTime);
        }
    }

    // =========================================================================
    // 7. STEP 13: Official-STYLE LIVE RACE LEADERBOARD & VERTICAL PACK RADAR HUD
    // =========================================================================
    /**
     * Official Official 8-Bracket (Waku / 枠番) assignment for gateNumber (1..N) in a race with totalStalls:
     * - 1: White, 2: Black, 3: Red, 4: Blue, 5: Yellow, 6: Green, 7: Orange, 8: Pink
     */
    private static int getJraWakuBracket(int gateNum, int totalStalls) {
        int n = Math.max(1, totalStalls);
        int g = Mth.clamp(gateNum, 1, n);
        if (n <= 8) {
            return g;
        }
        int basePerBracket = n / 8;
        int extraBrackets = n % 8; // Upper brackets (from 8 downward) get +1 horse
        int currentGate = 1;
        for (int waku = 1; waku <= 8; waku++) {
            int countInThisWaku = basePerBracket + (waku > (8 - extraBrackets) ? 1 : 0);
            if (g < currentGate + countInThisWaku) {
                return waku;
            }
            currentGate += countInThisWaku;
        }
        return 8;
    }

    private static int getJraWakuBgColor(int waku) {
        return switch (waku) {
            case 1 -> 0xFFF4F4F6; // 1: White
            case 2 -> 0xFF1E1E22; // 2: Black
            case 3 -> 0xFFE02424; // 3: Red
            case 4 -> 0xFF1D62D8; // 4: Blue
            case 5 -> 0xFFF2CC0C; // 5: Yellow
            case 6 -> 0xFF1FA640; // 6: Green
            case 7 -> 0xFFF07416; // 7: Orange
            case 8 -> 0xFFF264A0; // 8: Pink
            default -> 0xFFCCCCCC;
        };
    }

    private static int getJraWakuTextColor(int waku) {
        return switch (waku) {
            case 1, 5, 7, 8 -> 0xFF111111; // Dark text on White, Yellow, Orange, Pink
            default -> 0xFFFFFFFF;         // White text on Black, Red, Blue, Green
        };
    }

    private static String formatOrdinal(int rank) {
        int mod100 = rank % 100;
        if (mod100 >= 11 && mod100 <= 13) return rank + "th";
        return switch (rank % 10) {
            case 1 -> rank + "st";
            case 2 -> rank + "nd";
            case 3 -> rank + "rd";
            default -> rank + "th";
        };
    }

    /**
     * Renders the Official Jockey-Cam Live Race Leaderboard & Vertical Pack Radar HUD on the right side of the screen:
     * - Top Header: Race Grade, Race Name, Track & Distance, Live Timer / Remaining Distance, and Course Progress Bar.
     * - Right Vertical Radar Lane: Scrolling distance milestone lines (2000m, 1000m, 400m, 200m, FIN) and
     *   real-time proportional Official Waku numbered saddle-cloth badges (with a pulsing focal highlight ring on your horse).
     * - Left Live Standings Column: Dynamically sorted rows showing Rank, Waku Gate Badge, Horse Name, and Length Gap / Finish Time.
     */
    /**
     * Wraps a bold race title so Line 0 fits cleanly to the left of the timer (within firstLineMaxW)
     * and any overflow words wrap onto subsequent lines (within fullLineMaxW), never overlapping the timer!
     */
    private static List<String> wrapBoldRaceTitle(net.minecraft.client.gui.Font font, String rawTitle, int firstLineMaxW, int fullLineMaxW) {
        List<String> lines = new ArrayList<>();
        String title = (rawTitle == null || rawTitle.isBlank()) ? "Official Race" : rawTitle.trim();
        String[] words = title.split("\\s+");

        StringBuilder currentLine = new StringBuilder();
        int currentMaxW = Math.max(24, firstLineMaxW);

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : (currentLine + " " + word);
            int boldWidth = font.width("§l" + candidate);
            if (boldWidth <= currentMaxW) {
                currentLine.setLength(0);
                currentLine.append(candidate);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                    currentMaxW = Math.max(40, fullLineMaxW);
                }
                // If a single very long word exceeds currentMaxW, split it by characters
                if (font.width("§l" + word) > currentMaxW) {
                    StringBuilder part = new StringBuilder();
                    for (int i = 0; i < word.length(); i++) {
                        char c = word.charAt(i);
                        if (font.width("§l" + part + c) > currentMaxW && !part.isEmpty()) {
                            lines.add(part.toString());
                            part.setLength(0);
                            currentMaxW = Math.max(40, fullLineMaxW);
                        }
                        part.append(c);
                    }
                    if (!part.isEmpty()) {
                        currentLine.append(part);
                    }
                } else {
                    currentLine.append(word);
                }
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        if (lines.isEmpty()) {
            lines.add(title);
        }
        return lines;
    }

    private static void renderJraRaceLeaderboardHud(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight, int focalHorseId) {
        ModNetwork.RaceLeaderboardSyncPacket lb = clientRaceLeaderboard;
        List<ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry> runners = lb.runners;
        if (runners.isEmpty()) return;

        // 1. Update 60-240 FPS smoothed runner positions & row indices
        float maxMeters = 0.0F;
        float minMeters = Float.MAX_VALUE;
        for (int i = 0; i < runners.size(); i++) {
            ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(i);
            ClientRunnerSmoothState st = CLIENT_RUNNER_SMOOTH.computeIfAbsent(r.horseId, k -> new ClientRunnerSmoothState());
            if (!st.initialized) {
                st.smoothedMeters = r.raceMeters;
                st.smoothedLateral = r.lateralOffset;
                st.smoothedRowIndex = i;
                st.initialized = true;
            } else {
                st.smoothedMeters = Mth.lerp(0.22F, st.smoothedMeters, r.raceMeters);
                st.smoothedLateral = Mth.lerp(0.22F, st.smoothedLateral, r.lateralOffset);
                st.smoothedRowIndex = Mth.lerp(0.22F, st.smoothedRowIndex, (float) i);
            }
            if (st.smoothedMeters > maxMeters) maxMeters = st.smoothedMeters;
            if (st.smoothedMeters < minMeters) minMeters = st.smoothedMeters;
        }
        if (minMeters == Float.MAX_VALUE) minMeters = 0.0F;

        float targetTopMeters = Math.min((float) lb.totalDistanceMeters + 4.0F, maxMeters + 6.0F);
        float packSpread = Math.max(40.0F, (maxMeters - minMeters) + 20.0F);
        float targetSpanMeters = Mth.clamp(packSpread, 45.0F, 140.0F);

        if (!radarWindowInitialized) {
            smoothedWindowTopMeters = targetTopMeters;
            smoothedWindowSpanMeters = targetSpanMeters;
            radarWindowInitialized = true;
        } else {
            smoothedWindowTopMeters = Mth.lerp(0.22F, smoothedWindowTopMeters, targetTopMeters);
            smoothedWindowSpanMeters = Mth.lerp(0.15F, smoothedWindowSpanMeters, targetSpanMeters);
        }

        // 2. Panel Geometry & Multi-Line Wrapped Title Calculation
        int listColW = 152;
        int radarLaneW = 38;
        int totalPanelW = listColW + radarLaneW + 6;
        int panelX = screenWidth - totalPanelW - 5;
        int panelY = 34;

        String gradeBadge = "[" + lb.grade + "]";
        int badgeW = mc.font.width("§l" + gradeBadge) + 4;

        String phaseOrTimer = switch (lb.racePhase) {
            case 0 -> "§e" + ThoroughbredData.tr("GATE LOAD", "入閘準備", "入闸准备", "枠入り中");
            case 1 -> "§6§l" + ThoroughbredData.tr("ARMED", "準備出閘", "准备出闸", "発走準備");
            case 3 -> "§6§l" + ThoroughbredData.tr("INQUIRY", "審議中", "审议中", "審議中");
            case 4 -> "§a§l" + ThoroughbredData.tr("CONFIRMED", "確定", "确定", "確定");
            default -> {
                int mins = (int) (lb.elapsedSeconds / 60.0F);
                float secs = lb.elapsedSeconds % 60.0F;
                yield String.format(Locale.ROOT, "§e%02d:%04.1f", mins, secs);
            }
        };
        int timerW = mc.font.width(phaseOrTimer);

        int firstLineTitleMaxW = totalPanelW - badgeW - timerW - 8;
        int fullLineTitleMaxW = totalPanelW - 6;
        List<String> wrappedTitleLines = wrapBoldRaceTitle(mc.font, lb.raceName, firstLineTitleMaxW, fullLineTitleMaxW);
        int extraTitleLines = Math.max(0, wrappedTitleLines.size() - 1);

        int headerH = 34 + (extraTitleLines * 10);
        int rowH = 14;
        int availableVerticalSpace = screenHeight - panelY - headerH - 46;
        // Taller, more spacious HUD body (144px to 204px tall) so multiple horses never feel cramped!
        int listBodyH = Mth.clamp(Math.max(148, runners.size() * rowH + 14), 140, Math.max(144, availableVerticalSpace));
        int maxVisibleRows = Math.max(6, (listBodyH - 4) / rowH);
        int totalPanelH = headerH + listBodyH + 4;

        if (panelY + totalPanelH > screenHeight - 42) {
            panelY = Math.max(4, screenHeight - 42 - totalPanelH);
        }

        int gradeColor = switch (lb.grade.toUpperCase(Locale.ROOT)) {
            case "G1" -> 0xFF33B5FF;
            case "G2" -> 0xFFFF44AA;
            case "G3" -> 0xFF33DD66;
            case "LISTED", "L" -> 0xFFFFB300;
            case "OPEN", "OP" -> 0xFFD4AF37;
            case "PRE-OPEN", "PRE-OP" -> 0xFFFFD844;
            case "TEST" -> 0xFF55FFFF;
            default -> 0xFFCCCCCC;
        };

        // Translucent Outer Broadcast Frame (0x66 alpha = ~40% opacity so the track is clearly visible behind it!)
        graphics.fill(panelX - 2, panelY - 2, panelX + totalPanelW + 2, panelY + totalPanelH, 0x660A0E17);
        graphics.fill(panelX - 2, panelY - 2, panelX + totalPanelW + 2, panelY, gradeColor);
        graphics.fill(panelX - 2, panelY + totalPanelH - 1, panelX + totalPanelW + 2, panelY + totalPanelH, 0x77D4AF37);

        // ---------------------------------------------------------------------
        // PART A: Official BROADCAST HEADER (Multi-Line Wrapped Race Name, Timer, Track, Rem m & Progress Bar)
        // ---------------------------------------------------------------------
        graphics.drawString(mc.font, "§l" + gradeBadge, panelX + 2, panelY + 3, gradeColor, true);
        graphics.drawString(mc.font, phaseOrTimer, panelX + totalPanelW - timerW - 2, panelY + 3, 0xFFFFFF, true);

        // Line 0 of Race Name (placed cleanly between [Grade] and Timer)
        graphics.drawString(mc.font, "§f§l" + wrappedTitleLines.get(0), panelX + 2 + badgeW, panelY + 3, 0xFFFFFF, true);

        // Additional wrapped lines of Race Name (if the race name is long)
        for (int lineIdx = 1; lineIdx < wrappedTitleLines.size(); lineIdx++) {
            int lineY = panelY + 3 + (lineIdx * 10);
            graphics.drawString(mc.font, "§f§l" + wrappedTitleLines.get(lineIdx), panelX + 3, lineY, 0xFFFFFF, true);
        }

        int trackRowY = panelY + 14 + (extraTitleLines * 10);
        int remainingMeters = Math.max(0, Math.round(lb.totalDistanceMeters - maxMeters));
        String remStr = lb.racePhase >= 2
                ? ("§b" + ThoroughbredData.tr("Rem:", "剩餘:", "剩余:", "残り:") + " §f" + remainingMeters + "m")
                : ("§7" + lb.runners.size() + " " + ThoroughbredData.tr("Runners", "匹出賽", "匹出赛", "頭立"));
        int remW = mc.font.width(remStr);
        graphics.drawString(mc.font, remStr, panelX + totalPanelW - remW - 2, trackRowY, 0xFFFFFF, true);

        String trackText = lb.trackSummary;
        int maxTrackW = Math.max(30, totalPanelW - remW - 8);
        ClientScreens.drawScaledLeftString(graphics, mc.font, "§7" + trackText, panelX + 2, trackRowY, maxTrackW, 0xCCCCCC);

        // Miniature Course Progress Bar (0m ---> Finish)
        int progX = panelX + 2;
        int progY = panelY + 25 + (extraTitleLines * 10);
        int progW = totalPanelW - 4;
        graphics.fill(progX, progY, progX + progW, progY + 4, 0x661B2230);
        float leadRatio = Mth.clamp(maxMeters / Math.max(1.0F, (float) lb.totalDistanceMeters), 0.0F, 1.0F);
        int leadFill = Math.round(leadRatio * progW);
        graphics.fill(progX, progY, progX + leadFill, progY + 4, gradeColor);

        for (ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r : runners) {
            if (r.horseId == focalHorseId) {
                ClientRunnerSmoothState st = CLIENT_RUNNER_SMOOTH.get(r.horseId);
                float myMeters = st != null ? st.smoothedMeters : r.raceMeters;
                int myDotX = progX + Math.round(Mth.clamp(myMeters / Math.max(1.0F, (float) lb.totalDistanceMeters), 0.0F, 1.0F) * (progW - 3));
                graphics.fill(myDotX - 1, progY - 1, myDotX + 3, progY + 5, 0xFFFF3333);
                graphics.fill(myDotX, progY, myDotX + 2, progY + 4, 0xFFFFFFFF);
                break;
            }
        }

        graphics.fill(panelX, panelY + headerH - 2, panelX + totalPanelW, panelY + headerH - 1, 0x38FFFFFF);

        // ---------------------------------------------------------------------
        // PART B1: LEFT COLUMN — DYNAMIC RANKED STANDINGS LIST
        // ---------------------------------------------------------------------
        int bodyTopY = panelY + headerH;
        int bodyBottomY = bodyTopY + listBodyH;
        int listLeftX = panelX + 2;

        int focalIndex = 0;
        for (int i = 0; i < runners.size(); i++) {
            if (runners.get(i).horseId == focalHorseId) {
                focalIndex = i;
                break;
            }
        }
        int startIndex = 0;
        if (runners.size() > maxVisibleRows && focalIndex >= maxVisibleRows - 1) {
            startIndex = Mth.clamp(focalIndex - (maxVisibleRows - 2), 0, runners.size() - maxVisibleRows);
        }

        float leaderMeters = runners.get(0).raceMeters;

        graphics.enableScissor(listLeftX, bodyTopY, listLeftX + listColW, bodyBottomY);
        for (int i = 0; i < runners.size(); i++) {
            ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(i);
            ClientRunnerSmoothState st = CLIENT_RUNNER_SMOOTH.get(r.horseId);
            float smoothRow = (st != null ? st.smoothedRowIndex : i) - startIndex;
            int rowY = bodyTopY + 2 + Math.round(smoothRow * rowH);
            if (rowY + rowH < bodyTopY || rowY > bodyBottomY) {
                continue;
            }

            boolean isFocal = (r.horseId == focalHorseId);
            // More transparent row backgrounds so the HUD blends lightly over the world
            int rowBg = isFocal ? 0x782A1E14 : ((i % 2 == 0) ? 0x42131A26 : 0x2E0E141D);
            graphics.fill(listLeftX, rowY, listLeftX + listColW - 2, rowY + rowH - 1, rowBg);
            if (isFocal) {
                graphics.fill(listLeftX, rowY, listLeftX + 2, rowY + rowH - 1, 0xFFFFD700);
            }

            boolean isDsq = r.finished && (r.finishTimeSeconds <= -1.5F || r.rank >= 100);
            boolean isDnf = !isDsq && r.finished && (r.finishTimeSeconds < 0.0F || r.rank == 99);
            int rankColor = isDsq ? 0xFFCC2222 : (isDnf ? 0xFFFF5555 : switch (r.rank) {
                case 1 -> 0xFFFFD700; // Gold
                case 2 -> 0xFF55DDFF; // Silver/Cyan
                case 3 -> 0xFFFF9944; // Bronze
                default -> 0xFFCCCCCC;
            });
            String rankStr = (isDsq || isDnf) ? "-" : String.valueOf(r.rank);
            graphics.drawString(mc.font, rankStr, listLeftX + 4, rowY + 3, rankColor, true);

            // Official Official Waku Saddle-Cloth Number Box
            int waku = getJraWakuBracket(r.gateNumber, lb.totalStalls);
            int wakuBg = getJraWakuBgColor(waku);
            int wakuTxt = getJraWakuTextColor(waku);
            int badgeX = listLeftX + 16;
            int badgeY = rowY + 2;
            int badgeWBox = 12;
            int badgeHBox = 10;
            graphics.fill(badgeX - 1, badgeY - 1, badgeX + badgeWBox + 1, badgeY + badgeHBox + 1, isFocal ? 0xFFFF3333 : 0xAA000000);
            graphics.fill(badgeX, badgeY, badgeX + badgeWBox, badgeY + badgeHBox, wakuBg);
            String numStr = String.valueOf(r.gateNumber);
            int numX = badgeX + (badgeWBox - mc.font.width(numStr)) / 2;
            graphics.drawString(mc.font, numStr, numX, badgeY + 1, wakuTxt, false);

            // Right-aligned Gap / Status String
            String gapStr;
            int gapColor = 0xFFDDDDDD;
            if (lb.racePhase < 2) {
                gapStr = "#" + r.gateNumber;
            } else if (isDsq) {
                gapStr = "DSQ";
                gapColor = 0xFFFF3333;
            } else if (isDnf) {
                gapStr = "DNF";
                gapColor = 0xFFFF5555;
            } else if (r.finished) {
                if (r.rank == 1) {
                    int m = (int) (r.finishTimeSeconds / 60.0F);
                    float s = r.finishTimeSeconds % 60.0F;
                    gapStr = String.format(Locale.ROOT, "%d:%04.1f", m, s);
                    gapColor = 0xFF55FF55;
                } else {
                    float winTime = runners.get(0).finishTimeSeconds > 0.0F ? runners.get(0).finishTimeSeconds : r.finishTimeSeconds;
                    float diffSec = Math.max(0.01F, r.finishTimeSeconds - winTime);
                    gapStr = String.format(Locale.ROOT, "+%.2fs", diffSec);
                    gapColor = 0xFFFFD866;
                }
            } else if (r.rank == 1) {
                gapStr = ThoroughbredData.tr("LEAD", "領先", "领先", "先頭");
                gapColor = 0xFFFFD700;
            } else {
                float meterDiff = Math.max(0.0F, leaderMeters - r.raceMeters);
                float lengths = meterDiff / 2.5F; // 1 Horse Length ≈ 2.5m
                if (lengths < 0.12F) {
                    gapStr = ThoroughbredData.tr("Nose", "鼻位", "鼻位", "ハナ");
                } else if (lengths < 0.30F) {
                    gapStr = ThoroughbredData.tr("Head", "頭位", "头位", "アタマ");
                } else if (lengths < 0.60F) {
                    gapStr = ThoroughbredData.tr("Neck", "頸位", "颈位", "クビ");
                } else if (lengths < 10.0F) {
                    gapStr = String.format(Locale.ROOT, "-%.1fL", lengths);
                } else {
                    gapStr = String.format(Locale.ROOT, "-%.0fL", lengths);
                }
            }
            int gapW = mc.font.width(gapStr);
            graphics.drawString(mc.font, gapStr, listLeftX + listColW - gapW - 4, rowY + 3, gapColor, true);

            // Horse Name (fits on 1 line, or wraps onto 2 compact lines inside the row if long!)
            int nameX = badgeX + badgeWBox + 4;
            int maxNameW = Math.max(24, (listLeftX + listColW - gapW - 6) - nameX);
            String hName = r.horseName != null ? r.horseName : "";
            int nameColor = isFocal ? 0xFFFFEA55 : 0xFFFFFFFF;
            if (mc.font.width(hName) <= maxNameW) {
                graphics.drawString(mc.font, hName, nameX, rowY + 3, nameColor, true);
            } else {
                // Wrap long horse name onto 2 compact lines at 0.72x scale so nothing is omitted!
                float wrapScale = 0.72F;
                int scaledMaxW = Math.round(maxNameW / wrapScale);
                List<String> nameLines = wrapBoldRaceTitle(mc.font, hName, scaledMaxW, scaledMaxW);
                graphics.pose().pushPose();
                graphics.pose().translate(nameX, rowY + 1.0F, 0.0F);
                graphics.pose().scale(wrapScale, wrapScale, 1.0F);
                graphics.drawString(mc.font, nameLines.get(0), 0, 0, nameColor, true);
                if (nameLines.size() > 1) {
                    String line2 = String.join(" ", nameLines.subList(1, nameLines.size()));
                    int l2w = mc.font.width(line2);
                    if (l2w > scaledMaxW + 8 && l2w > 0) {
                        float l2Scale = (float) (scaledMaxW + 8) / (float) l2w;
                        graphics.pose().pushPose();
                        graphics.pose().translate(0.0F, 9.0F, 0.0F);
                        graphics.pose().scale(l2Scale, l2Scale, 1.0F);
                        graphics.drawString(mc.font, line2, 0, 0, nameColor, true);
                        graphics.pose().popPose();
                    } else {
                        graphics.drawString(mc.font, line2, 0, 9, nameColor, true);
                    }
                }
                graphics.pose().popPose();
            }
        }
        graphics.disableScissor();

        // ---------------------------------------------------------------------
        // PART B2: RIGHT COLUMN — TALL Official VERTICAL TRACK RADAR LANE
        // ---------------------------------------------------------------------
        int laneX = listLeftX + listColW + 2;
        int laneTopY = bodyTopY + 1;
        int laneBottomY = bodyBottomY - 1;
        int laneH = laneBottomY - laneTopY;

        // Translucent track surface background & white inner/outer rails
        boolean isTurf = lb.trackSummary.toLowerCase(Locale.ROOT).contains("turf");
        int trackSurfaceBg = isTurf ? 0x660F2417 : 0x66241B12;
        graphics.fill(laneX, laneTopY, laneX + radarLaneW, laneBottomY, trackSurfaceBg);
        graphics.fill(laneX, laneTopY, laneX + 1, laneBottomY, 0x77FFFFFF);
        graphics.fill(laneX + radarLaneW - 1, laneTopY, laneX + radarLaneW, laneBottomY, 0x77FFFFFF);

        // Direction arrow at top of vertical track lane
        graphics.drawCenteredString(mc.font, "§8▲", laneX + radarLaneW / 2, laneTopY + 1, 0x66FFFFFF);

        graphics.enableScissor(laneX, laneTopY, laneX + radarLaneW, laneBottomY);

        // 1. Scrolling Distance Milestone Lines (every 100m, plus Finish Line & Start Line)
        float windowBottomMeters = smoothedWindowTopMeters - smoothedWindowSpanMeters;
        int firstMilestone = Math.max(0, ((int) Math.floor(windowBottomMeters / 100.0F)) * 100);
        int lastMilestone = Math.min(lb.totalDistanceMeters, ((int) Math.ceil(smoothedWindowTopMeters / 100.0F)) * 100);

        for (int m = firstMilestone; m <= lastMilestone; m += 100) {
            float normY = (smoothedWindowTopMeters - m) / Math.max(1.0F, smoothedWindowSpanMeters);
            int lineY = laneTopY + 10 + Math.round(normY * (laneH - 20));
            if (lineY >= laneTopY + 2 && lineY <= laneBottomY - 2) {
                boolean isFinish = (m == lb.totalDistanceMeters);
                boolean isStart = (m == 0);
                boolean isMajor200 = (m % 200 == 0);
                int lineCol = isFinish ? 0xCCFF4444 : (isStart ? 0xCCFFD700 : (isMajor200 ? 0x44FFFFFF : 0x24FFFFFF));
                graphics.fill(laneX + 1, lineY, laneX + radarLaneW - 1, lineY + 1, lineCol);

                if (isFinish || isStart || isMajor200) {
                    int remAtMilestone = lb.totalDistanceMeters - m;
                    String mTag = isFinish ? "FIN" : (isStart ? "S" : (remAtMilestone + "m"));
                    graphics.pose().pushPose();
                    graphics.pose().translate(laneX + 3, lineY - 5, 0.0F);
                    graphics.pose().scale(0.65F, 0.65F, 1.0F);
                    graphics.drawString(mc.font, mTag, 0, 0, isFinish ? 0xFFFF7777 : 0x88DDDDDD, false);
                    graphics.pose().popPose();
                }
            }
        }

        // 2. Compute non-overlapping 2D badge coordinates inside the Official vertical track lane
        int nRunners = runners.size();
        int[] badgeCenterX = new int[nRunners];
        int[] badgeCenterY = new int[nRunners];
        int usableTop = laneTopY + 10;
        int usableBottom = laneBottomY - 10;
        int usableH = Math.max(20, usableBottom - usableTop);

        for (int i = 0; i < nRunners; i++) {
            ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(i);
            ClientRunnerSmoothState st = CLIENT_RUNNER_SMOOTH.get(r.horseId);
            float sMeters = st != null ? st.smoothedMeters : r.raceMeters;
            float sLat = st != null ? st.smoothedLateral : r.lateralOffset;

            float normY = Mth.clamp((smoothedWindowTopMeters - sMeters) / Math.max(1.0F, smoothedWindowSpanMeters), 0.0F, 1.0F);
            int cy = usableTop + Math.round(normY * usableH);
            int cx = laneX + (radarLaneW / 2) + Math.round(sLat * ((radarLaneW - 14) * 0.45F));

            // De-cluster badges that are at nearly the same vertical & horizontal pixel so every number is legible
            for (int j = 0; j < i; j++) {
                int dy = Math.abs(cy - badgeCenterY[j]);
                int dx = Math.abs(cx - badgeCenterX[j]);
                if (dy < 9 && dx < 9) {
                    int shift = ((i % 2 == 0) ? 8 : -8);
                    cx = Mth.clamp(cx + shift, laneX + 7, laneX + radarLaneW - 7);
                    if (Math.abs(cx - badgeCenterX[j]) < 7) {
                        cy = Mth.clamp(cy + 7, usableTop, usableBottom);
                    }
                }
            }
            badgeCenterX[i] = Mth.clamp(cx, laneX + 7, laneX + radarLaneW - 7);
            badgeCenterY[i] = Mth.clamp(cy, usableTop, usableBottom);
        }

        // 3. Draw non-focal horse badges back-to-front, then draw focal horse badge on top with Official red/gold ring!
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = nRunners - 1; i >= 0; i--) {
                ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(i);
                boolean isFocal = (r.horseId == focalHorseId);
                if ((pass == 0 && isFocal) || (pass == 1 && !isFocal)) {
                    continue;
                }

                int cx = badgeCenterX[i];
                int cy = badgeCenterY[i];
                int waku = getJraWakuBracket(r.gateNumber, lb.totalStalls);
                int wakuBg = getJraWakuBgColor(waku);
                int wakuTxt = getJraWakuTextColor(waku);

                if (isFocal) {
                    // Pulsing Official Camera-Horse Highlight Ring around the player horse badge!
                    int pulseRing = ((gameTime / 4) % 2 == 0) ? 0xFFFF2222 : 0xFFFFD700;
                    drawRoundedBadge(graphics, cx, cy, 7, pulseRing);
                    drawRoundedBadge(graphics, cx, cy, 6, 0xFF000000);
                } else {
                    drawRoundedBadge(graphics, cx, cy, 6, 0xFF000000);
                }
                drawRoundedBadge(graphics, cx, cy, 5, wakuBg);

                String numStr = String.valueOf(r.gateNumber);
                graphics.pose().pushPose();
                float txtScale = r.gateNumber >= 10 ? 0.72F : 0.85F;
                graphics.pose().translate(cx, cy - 3.2F * txtScale, 0.0F);
                graphics.pose().scale(txtScale, txtScale, 1.0F);
                int tw = mc.font.width(numStr);
                graphics.drawString(mc.font, numStr, -tw / 2, 0, wakuTxt, false);
                graphics.pose().popPose();
            }
        }

        graphics.disableScissor();
    }

    /**
     * Draws an octagonal/circular badge centered at (cx, cy) with radius r.
     */
    private static void drawRoundedBadge(GuiGraphics graphics, int cx, int cy, int r, int color) {
        graphics.fill(cx - r + 1, cy - r, cx + r - 1, cy + r, color);
        graphics.fill(cx - r, cy - r + 1, cx + r, cy + r - 1, color);
    }

    /**
     * Renders the 3-Line Race Logo Display when the Semaphore Flag is waved 3 times:
     *                  G2
     * Doodoo Gaga Stakes
     *  Doodoo Dirt 2400m
     */
    public static void renderG1SpecialEffectOverlayIfActive(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight, float partialTick) {
        if (isClientLogoActive()) {
            renderThreeLineRaceLogo(graphics, mc, screenWidth, screenHeight, partialTick);
        }
    }

    private static void renderThreeLineRaceLogo(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight, float partialTick) {
        if ("G1".equalsIgnoreCase(clientLogoGrade)) {
            renderG1SpecialEffectLogo(graphics, mc, screenWidth, screenHeight, partialTick);
            return;
        }

        int centerX = screenWidth / 2;
        int bannerY = (screenHeight / 2) - 72;

        int accentColor = switch (clientLogoGrade.toUpperCase(Locale.ROOT)) {
            case "G1" -> 0xFF33B5FF;
            case "G2" -> 0xFFFF44AA;
            case "G3" -> 0xFF33DD66;
            case "LISTED", "L" -> 0xFFFFB300;
            case "OPEN", "OP" -> 0xFFD4AF37;
            case "PRE-OPEN", "PRE-OP" -> 0xFFFFD844;
            default -> 0xFFCCCCCC;
        };

        int w1 = mc.font.width(clientLogoGrade);
        int w2 = (int) (mc.font.width(clientLogoRaceName) * 1.4F);
        int w3 = mc.font.width(clientLogoBottomType);
        int boxHalfW = Math.max(95, Math.max(w1, Math.max(w2, w3)) / 2 + 22);

        graphics.fill(centerX - boxHalfW, bannerY - 6, centerX + boxHalfW, bannerY + 46, 0xDD0B0F19);
        graphics.fill(centerX - boxHalfW, bannerY - 6, centerX + boxHalfW, bannerY - 4, accentColor);
        graphics.fill(centerX - boxHalfW, bannerY + 44, centerX + boxHalfW, bannerY + 46, accentColor);

        // Line 1 (Top): Race Grade (e.g. G2)
        graphics.drawCenteredString(mc.font, "§l" + clientLogoGrade, centerX, bannerY, accentColor);

        // Line 2 (Middle, scaled 1.35x): Race Name (e.g. Doodoo Gaga Stakes)
        graphics.pose().pushPose();
        float scale = 1.35F;
        graphics.pose().translate(centerX, bannerY + 13, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(mc.font, "§f§l" + clientLogoRaceName, 0, 0, 0xFFFFFF);
        graphics.pose().popPose();

        // Line 3 (Bottom): Structured Base Race Type (<Racecourse> <Turf/Dirt> <Distance>m, e.g. Doodoo Dirt 2400m)
        graphics.drawCenteredString(mc.font, "§e" + clientLogoBottomType, centerX, bannerY + 31, 0xFFE680);
    }

    // =========================================================================
    // STEP 16: 3-ACT G1 BROADCAST CHOREOGRAPHY & TIMELINE STUDIO ENGINE
    // =========================================================================
    private static ItemStack getG1MainEmblemStack(int emblemIdx) {
        return switch (emblemIdx) {
            case 1 -> new ItemStack(Items.NETHER_STAR);
            case 2 -> new ItemStack(Items.BEACON);
            case 3 -> new ItemStack(Items.DRAGON_EGG);
            case 4 -> new ItemStack(Items.HEART_OF_THE_SEA);
            case 5 -> new ItemStack(Items.CHERRY_SAPLING);
            case 6 -> new ItemStack(Items.TRIDENT);
            case 7 -> new ItemStack(Items.BLAZE_ROD);
            case 8 -> new ItemStack(Items.AMETHYST_CLUSTER);
            case 9 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            default -> new ItemStack(Items.TOTEM_OF_UNDYING);
        };
    }

    private static ItemStack getG1SideEmblemStack(int emblemIdx) {
        return switch (emblemIdx) {
            case 1 -> new ItemStack(Items.GOLD_BLOCK);
            case 2 -> new ItemStack(Items.DIAMOND_BLOCK);
            case 3 -> new ItemStack(Items.END_CRYSTAL);
            case 4 -> new ItemStack(Items.PRISMARINE_SHARD);
            case 5 -> new ItemStack(Items.CHERRY_LEAVES);
            case 6 -> new ItemStack(Items.SEA_LANTERN);
            case 7 -> new ItemStack(Items.MAGMA_BLOCK);
            case 8 -> new ItemStack(Items.BUDDING_AMETHYST);
            case 9 -> new ItemStack(Items.EMERALD_BLOCK);
            default -> new ItemStack(Items.OAK_LEAVES);
        };
    }

    private static int getG1ThemePrimaryBg(int themeIdx) {
        return switch (themeIdx) {
            case 1 -> 0xEE0B2258; // Royal Blue & Gold
            case 2 -> 0xEE6E1642; // Sakura Pink & Gold
            case 3 -> 0xEE0B4424; // Emerald Turf & Gold
            case 4 -> 0xEE35105A; // Imperial Purple & Gold
            case 5 -> 0xEE091B24; // Obsidian & Diamond
            case 6 -> 0xEE6E2208; // Solar Flame & Gold
            case 7 -> 0xEE141E30; // Platinum & Navy
            default -> 0xEE7E081C; // Derby Gold & Crimson
        };
    }

    private static int getG1ThemeTrimColor(int themeIdx) {
        return switch (themeIdx) {
            case 2 -> 0xFFFFB7D5; // Rose Gold
            case 5 -> 0xFF55FFFF; // Diamond Cyan
            case 7 -> 0xFFE5E8F0; // Platinum Silver
            default -> 0xFFFFD700; // Championship Gold
        };
    }

    private static float getSlamDurationTicks(int slamSpeedIdx) {
        return switch (slamSpeedIdx) {
            case 0 -> 5.0F;  // Fast (0.25s)
            case 2 -> 16.0F; // Dramatic (0.80s)
            case 3 -> 24.0F; // Grand (1.20s)
            default -> 9.0F; // Normal (0.45s)
        };
    }

    private static void renderG1SpecialEffectLogo(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight, float partialTick) {
        int[] preset = RacingManager.parseG1Preset(clientLogoG1Preset);
        int introIdx           = preset[0];
        int emblemIdx          = preset[1];
        int pyroIdx            = preset[2];
        int themeIdx           = preset[3];
        int act2Idx            = preset[4];
        int dropIdx            = preset[5];
        int trophyIdx          = preset[6];
        int act1Tenths         = preset[7];
        int act2Tenths         = preset[8];
        int slamSpeedIdx       = preset[10];
        boolean showNameEarly  = preset[11] > 0;
        boolean showTrackEarly = preset[12] > 0;
        boolean climaxFullG1   = preset[13] > 0;

        float elapsedTicks = Math.max(0.0F, clientLogoElapsedTicks + partialTick);
        float act1Ticks = act1Tenths * 2.0F; // 1 tenth of a second = 2 ticks
        float act2Ticks = (act2Idx > 0) ? (act2Tenths * 2.0F) : 0.0F;
        float climaxStartTicks = act1Ticks + act2Ticks;

        int centerX = screenWidth / 2;
        int centerY = (screenHeight / 2) - 48;
        int stripHalfW = Math.min(screenWidth / 2 - 12, 168);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);

        if (elapsedTicks < act1Ticks && act1Ticks > 0.5F) {
            float act1Prog = Mth.clamp(elapsedTicks / act1Ticks, 0.0F, 1.0F);
            renderG1ActSequence(
                    graphics, mc, centerX, centerY, stripHalfW,
                    introIdx, themeIdx, elapsedTicks, act1Prog,
                    "§6§l★ GRADE 1 ★",
                    showNameEarly ? clientLogoRaceName : "",
                    showTrackEarly ? clientLogoBottomType : "",
                    false
            );
        } else if (elapsedTicks < climaxStartTicks && act2Ticks > 0.5F) {
            float act2Elapsed = elapsedTicks - act1Ticks;
            float act2Prog = Mth.clamp(act2Elapsed / act2Ticks, 0.0F, 1.0F);
            int bridgeEffectIdx = (act2Idx - 1) % RacingManager.G1_INTROS.length;
            renderG1ActSequence(
                    graphics, mc, centerX, centerY, stripHalfW,
                    bridgeEffectIdx, themeIdx, elapsedTicks, act2Prog,
                    "§6§l★ GRADE 1 ★",
                    showNameEarly ? clientLogoRaceName : "",
                    showTrackEarly ? clientLogoBottomType : "",
                    true
            );
            // Brief flash at Act 1 -> Act 2 transition
            if (act2Elapsed < 4.0F) {
                int a = Math.round((1.0F - act2Elapsed / 4.0F) * 110.0F);
                graphics.fill(centerX - stripHalfW, centerY - 34, centerX + stripHalfW, centerY + 38, (a << 24) | 0xFFFFFF);
            }
        } else {
            float climaxTicks = Math.max(0.0F, elapsedTicks - climaxStartTicks);
            renderG1ClimaxTrophyEmblem(
                    graphics, mc, centerX, centerY,
                    clientLogoRaceName, clientLogoBottomType,
                    emblemIdx, pyroIdx, themeIdx, dropIdx, trophyIdx, slamSpeedIdx,
                    climaxFullG1, elapsedTicks, climaxTicks
            );
        }

        graphics.pose().popPose();
    }

    /**
     * Renders any of the 10 Opening / Bridge Sequence Animations (0..9) for either Act 1 or Act 2!
     */
    private static void renderG1ActSequence(GuiGraphics graphics, Minecraft mc, int centerX, int centerY,
                                            int stripHalfW, int seqEffectIdx, int themeIdx,
                                            float elapsed, float phaseProg,
                                            String topBannerText, String preClimaxRaceName,
                                            String bottomBannerText, boolean isAct2Bridge) {
        int trimColor = getG1ThemeTrimColor(themeIdx);
        int ribbonBg = getG1ThemePrimaryBg(themeIdx);

        graphics.fill(centerX - stripHalfW, centerY - 34, centerX + stripHalfW, centerY + 38, 0xB8070A12);
        graphics.fill(centerX - stripHalfW, centerY - 34, centerX + stripHalfW, centerY - 32, trimColor);
        graphics.fill(centerX - stripHalfW, centerY + 36, centerX + stripHalfW, centerY + 38, trimColor);

        switch (seqEffectIdx) {
            case 0 -> {
                // 0: Derby Ribbons & Star (2023 Japanese Derby style)
                for (int r = 0; r < 3; r++) {
                    float phase = elapsed * 0.24F + (r * 1.8F);
                    for (int i = -stripHalfW + 8; i <= stripHalfW - 8; i += 4) {
                        float normX = (float) i / stripHalfW;
                        int waveY = Math.round(Mth.sin(normX * 4.2F + phase) * 17.0F * (1.05F - 0.55F * phaseProg));
                        int col = (r == 0) ? 0xFFFFD700 : ((r == 1) ? 0xFFFFF4B8 : trimColor);
                        int rx = centerX + Math.round(i * (1.0F - 0.30F * phaseProg));
                        int ry = centerY + waveY + (r - 1) * 4;
                        graphics.fill(rx - 2, ry - 1, rx + 2, ry + 1, col);
                        if ((i + (int) elapsed) % 16 == 0) {
                            graphics.fill(rx - 1, ry - 3, rx + 1, ry + 3, 0xFFFFFFFF);
                        }
                    }
                }
                drawProceduralFivePointStar(graphics, centerX, centerY, Math.round(9 + 11 * phaseProg), elapsed * 6.0F, 0xFFFFD700, 0xFFFFFFFF);
                ItemStack horseStack = new ItemStack(Items.GOLDEN_HORSE_ARMOR);
                int orbitX = Math.round(Mth.cos(elapsed * 0.18F) * (58.0F * (1.0F - 0.45F * phaseProg)));
                renderScaledGuiItem(graphics, horseStack, centerX - orbitX, centerY - 1, 1.15F, 0.0F);
                renderScaledGuiItem(graphics, horseStack, centerX + orbitX, centerY - 1, 1.15F, 0.0F);
            }
            case 1 -> {
                // 1: Twin Spotlights
                float sweep = Mth.sin(elapsed * 0.22F) * (1.0F - 0.65F * phaseProg);
                int leftBeamTopX = centerX + Math.round(sweep * 72.0F);
                int rightBeamTopX = centerX - Math.round(sweep * 72.0F);
                for (int step = 0; step < 14; step++) {
                    float t = step / 14.0F;
                    int y1 = centerY + 34 - Math.round(t * 64.0F);
                    int lx = Math.round(Mth.lerp(t, (float) (centerX - 96), (float) leftBeamTopX));
                    int rx = Math.round(Mth.lerp(t, (float) (centerX + 96), (float) rightBeamTopX));
                    int w = Math.round(4 + t * 14);
                    graphics.fill(lx - w, y1 - 2, lx + w, y1 + 2, 0x55FFF4AA);
                    graphics.fill(rx - w, y1 - 2, rx + w, y1 + 2, 0x5588EEFF);
                }
                renderScaledGuiItem(graphics, new ItemStack(Items.BEACON), centerX, centerY, 1.30F + 0.40F * phaseProg, elapsed * 5.0F);
            }
            case 2 -> {
                // 2: Meteor Starfall
                ItemStack starStack = new ItemStack(Items.NETHER_STAR);
                ItemStack fwStack = new ItemStack(Items.FIREWORK_ROCKET);
                for (int m = 0; m < 5; m++) {
                    float mp = ((elapsed * 0.085F) + m * 0.20F) % 1.0F;
                    int mx = centerX + Math.round((m % 2 == 0 ? -1 : 1) * (1.0F - mp) * 95.0F);
                    int my = centerY - 26 + Math.round(mp * 30.0F);
                    graphics.fill(mx - 7, my - 5, mx + 2, my + 2, 0x88FFD700);
                    renderScaledGuiItem(graphics, (m % 2 == 0) ? starStack : fwStack, mx, my, 0.95F, elapsed * 8.0F);
                }
                drawProceduralFivePointStar(graphics, centerX, centerY, Math.round(8 + 12 * phaseProg), -elapsed * 5.5F, 0xFF88FFFF, 0xFFFFFFFF);
            }
            case 3 -> {
                // 3: Royal Velvet Curtain
                int openGap = Math.round(phaseProg * (stripHalfW - 16));
                graphics.fill(centerX - stripHalfW, centerY - 32, centerX - openGap, centerY + 36, ribbonBg);
                graphics.fill(centerX + openGap, centerY - 32, centerX + stripHalfW, centerY + 36, ribbonBg);
                graphics.fill(centerX - openGap - 3, centerY - 32, centerX - openGap, centerY + 36, trimColor);
                graphics.fill(centerX + openGap, centerY - 32, centerX + openGap + 3, centerY + 36, trimColor);
                renderScaledGuiItem(graphics, new ItemStack(Items.TOTEM_OF_UNDYING), centerX, centerY, 1.20F + 0.40F * phaseProg, 0.0F);
            }
            case 4 -> {
                // 4: Galloping Stampede (Parade of Golden/Diamond/Iron Horse Armor across golden speed lines!)
                for (int line = 0; line < 8; line++) {
                    int ly = centerY - 20 + line * 5;
                    int offset = (int) ((elapsed * 14.0F + line * 37) % (stripHalfW * 2 - 30));
                    int lx = centerX - stripHalfW + 10 + offset;
                    graphics.fill(lx, ly, Math.min(centerX + stripHalfW - 10, lx + 28), ly + 1, 0x88FFD700);
                }
                ItemStack[] armors = {
                        new ItemStack(Items.GOLDEN_HORSE_ARMOR),
                        new ItemStack(Items.DIAMOND_HORSE_ARMOR),
                        new ItemStack(Items.IRON_HORSE_ARMOR)
                };
                for (int h = 0; h < 3; h++) {
                    float hp = ((elapsed * 0.055F) + h * 0.33F) % 1.0F;
                    int hx = centerX - stripHalfW + 24 + Math.round(hp * (stripHalfW * 2 - 48));
                    int hy = centerY - 4 + Math.round(Mth.sin(elapsed * 0.65F + h * 2.0F) * 5.0F);
                    renderScaledGuiItem(graphics, armors[h], hx, hy, 1.25F, 0.0F);
                }
            }
            case 5 -> {
                // 5: Trumpet Brass Fanfare (Flanking Goat Horns + Bell + expanding concentric soundwave rings!)
                for (int ring = 0; ring < 3; ring++) {
                    float rp = ((elapsed * 0.07F) + ring * 0.33F) % 1.0F;
                    int radX = Math.round(12 + rp * 78.0F);
                    int radY = Math.round(6 + rp * 22.0F);
                    int alpha = Math.round((1.0F - rp) * 180.0F);
                    int ringCol = (alpha << 24) | (trimColor & 0x00FFFFFF);
                    graphics.fill(centerX - radX, centerY - radY, centerX + radX, centerY - radY + 1, ringCol);
                    graphics.fill(centerX - radX, centerY + radY, centerX + radX, centerY + radY + 1, ringCol);
                    graphics.fill(centerX - radX, centerY - radY, centerX - radX + 1, centerY + radY, ringCol);
                    graphics.fill(centerX + radX, centerY - radY, centerX + radX + 1, centerY + radY, ringCol);
                }
                ItemStack horn = new ItemStack(Items.GOAT_HORN);
                renderScaledGuiItem(graphics, horn, centerX - 52, centerY, 1.30F, Mth.sin(elapsed * 0.35F) * 12.0F);
                renderScaledGuiItem(graphics, horn, centerX + 52, centerY, 1.30F, -Mth.sin(elapsed * 0.35F) * 12.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.BELL), centerX, centerY, 1.35F, Mth.sin(elapsed * 0.55F) * 18.0F);
            }
            case 6 -> {
                // 6: Sakura Wind Vortex (Double-helix of Cherry Leaves + Pink Petals!)
                ItemStack cherry = new ItemStack(Items.CHERRY_LEAVES);
                ItemStack sapling = new ItemStack(Items.CHERRY_SAPLING);
                for (int i = -stripHalfW + 12; i <= stripHalfW - 12; i += 5) {
                    float nx = (float) i / stripHalfW;
                    int yA = centerY + Math.round(Mth.sin(nx * 5.0F + elapsed * 0.25F) * 16.0F);
                    int yB = centerY + Math.round(Mth.cos(nx * 5.0F + elapsed * 0.25F) * 16.0F);
                    graphics.fill(centerX + i - 2, yA - 1, centerX + i + 2, yA + 1, 0xFFFFB7C5);
                    graphics.fill(centerX + i - 2, yB - 1, centerX + i + 2, yB + 1, 0xFFFF69B4);
                }
                int swirlX = Math.round(Mth.cos(elapsed * 0.22F) * (48.0F * (1.0F - 0.4F * phaseProg)));
                renderScaledGuiItem(graphics, cherry, centerX - swirlX, centerY, 1.10F, elapsed * 6.0F);
                renderScaledGuiItem(graphics, cherry, centerX + swirlX, centerY, 1.10F, -elapsed * 6.0F);
                renderScaledGuiItem(graphics, sapling, centerX, centerY, 1.30F, 0.0F);
            }
            case 7 -> {
                // 7: Prismarine Ocean Wave (Cresting cyan waves + Trident & Heart of the Sea!)
                for (int x = -stripHalfW + 6; x <= stripHalfW - 6; x += 4) {
                    int waveH = Math.round(Mth.sin(x * 0.08F + elapsed * 0.28F) * 10.0F);
                    graphics.fill(centerX + x, centerY + 6 + waveH, centerX + x + 3, centerY + 22, 0x8800B8FF);
                    graphics.fill(centerX + x, centerY + 4 + waveH, centerX + x + 3, centerY + 6 + waveH, 0xEE88FFFF);
                }
                renderScaledGuiItem(graphics, new ItemStack(Items.TRIDENT), centerX - 42, centerY - 2, 1.20F, 25.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.TRIDENT), centerX + 42, centerY - 2, 1.20F, -25.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.HEART_OF_THE_SEA), centerX, centerY - 2, 1.35F, elapsed * 4.0F);
            }
            case 8 -> {
                // 8: Nether Flame Pillars (Rising columns of Blaze Powder & Magma Cream!)
                for (int c = 0; c < 4; c++) {
                    int colX = centerX + (c - 1) * 44 - 22;
                    int flameH = 14 + Math.round(Mth.sin(elapsed * 0.45F + c * 1.7F) * 10.0F);
                    graphics.fill(colX - 8, centerY + 20 - flameH, colX + 8, centerY + 22, 0xAAFF5500);
                    graphics.fill(colX - 4, centerY + 20 - flameH - 6, colX + 4, centerY + 22, 0xDDFFDD44);
                }
                renderScaledGuiItem(graphics, new ItemStack(Items.BLAZE_POWDER), centerX - 36, centerY - 2, 1.25F, elapsed * 5.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.BLAZE_POWDER), centerX + 36, centerY - 2, 1.25F, -elapsed * 5.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.MAGMA_CREAM), centerX, centerY - 2, 1.35F, elapsed * 4.0F);
            }
            case 9 -> {
                // 9: Clockwork Chrono Gears (Counter-rotating Clock & Compass celestial rings!)
                drawProceduralFivePointStar(graphics, centerX - 44, centerY, 12, elapsed * 5.0F, 0xFFD4AF37, 0xFFFFFFFF);
                drawProceduralFivePointStar(graphics, centerX + 44, centerY, 12, -elapsed * 5.0F, 0xFFD4AF37, 0xFFFFFFFF);
                renderScaledGuiItem(graphics, new ItemStack(Items.CLOCK), centerX, centerY, 1.40F, elapsed * 3.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.COMPASS), centerX - 44, centerY, 1.05F, -elapsed * 4.0F);
                renderScaledGuiItem(graphics, new ItemStack(Items.COMPASS), centerX + 44, centerY, 1.05F, elapsed * 4.0F);
            }
        }

        graphics.drawCenteredString(mc.font, topBannerText, centerX, centerY - 29, trimColor);
        if (preClimaxRaceName != null && !preClimaxRaceName.isBlank()) {
            graphics.drawCenteredString(mc.font, "§f§l" + preClimaxRaceName, centerX, centerY - 18, 0xFFFFFF);
        }
        if (bottomBannerText != null && !bottomBannerText.isBlank()) {
            graphics.drawCenteredString(mc.font, (isAct2Bridge ? "§6★ " : "§e") + bottomBannerText + (isAct2Bridge ? " §6★" : ""), centerX, centerY + 24, 0xFFFFE680);
        }
    }

    private static void renderG1ClimaxTrophyEmblem(GuiGraphics graphics, Minecraft mc, int centerX, int centerY,
                                                   String raceTitle, String bottomText,
                                                   int emblemIdx, int pyroIdx, int themeIdx,
                                                   int dropIdx, int trophyIdx, int slamSpeedIdx,
                                                   boolean climaxFullGrade1, float elapsed, float climaxTicks) {
        int trimColor = getG1ThemeTrimColor(themeIdx);
        int ribbonBg = getG1ThemePrimaryBg(themeIdx);
        float slamDuration = getSlamDurationTicks(slamSpeedIdx);
        float dropFactor = Mth.clamp(climaxTicks / slamDuration, 0.0F, 1.0F);

        // Optional screen-shake offset for Lightning Strike Drop (dropIdx == 2)
        int shakeX = 0;
        int shakeY = 0;
        if (dropIdx == 2 && climaxTicks < 8.0F) {
            float decay = 1.0F - (climaxTicks / 8.0F);
            shakeX = Math.round(Mth.sin(climaxTicks * 2.8F) * 5.0F * decay);
            shakeY = Math.round(Mth.cos(climaxTicks * 3.4F) * 4.0F * decay);
        }
        int cx = centerX + shakeX;
        int cy = centerY + shakeY;

        int titleW = (int) (mc.font.width(raceTitle) * 1.45F);
        int bottomW = mc.font.width(bottomText);
        int boxHalfW = Math.max(118, Math.max(titleW, bottomW) / 2 + 28);

        // Royal Unfurl Scroll (dropIdx == 4) animates frame width from 25% -> 100%
        int curFrameHalfW = (dropIdx == 4)
                ? Math.max(32, Math.round(boxHalfW * Mth.lerp(dropFactor, 0.25F, 1.0F)))
                : boxHalfW;

        // Rising Pedestal Ascent (dropIdx == 5) animates vertical rise of trophy & emblem
        int pedestalOffsetY = (dropIdx == 5)
                ? Math.round((1.0F - dropFactor) * 26.0F)
                : 0;

        // 1. Dark Championship Broadcast Frame with Beveled Trim
        graphics.fill(cx - curFrameHalfW, cy - 36, cx + curFrameHalfW, cy + 46, 0xE6080C14);
        graphics.fill(cx - curFrameHalfW - 2, cy - 38, cx + curFrameHalfW + 2, cy - 36, trimColor);
        graphics.fill(cx - curFrameHalfW - 2, cy + 46, cx + curFrameHalfW + 2, cy + 48, trimColor);
        graphics.fill(cx - curFrameHalfW - 2, cy - 36, cx - curFrameHalfW, cy + 46, trimColor);
        graphics.fill(cx + curFrameHalfW, cy - 36, cx + curFrameHalfW + 2, cy + 46, trimColor);

        // 2. Rotating Sunburst Rays Behind Trophy
        for (int r = 0; r < 10; r++) {
            float angle = (elapsed * 0.04F) + (r * (Mth.TWO_PI / 10.0F));
            int rayX = cx + Math.round(Mth.cos(angle) * 46.0F);
            int rayY = (cy - 2) + Math.round(Mth.sin(angle) * 24.0F);
            graphics.fill(
                    Math.min(cx, rayX),
                    Math.min(cy - 2, rayY),
                    Math.max(cx, rayX) + 1,
                    Math.max(cy - 2, rayY) + 1,
                    0x26FFD700
            );
        }

        // 3. Golden Laurel Wreaths & Vanilla Side Leaf/Block Clusters Flanking the Trophy
        int trophyCenterY = cy - 4 + pedestalOffsetY;
        drawProceduralLaurelWreath(graphics, cx, trophyCenterY + 2, 42, trimColor);
        ItemStack sideStack = getG1SideEmblemStack(emblemIdx);
        int sideSpreadX = (dropIdx == 3) ? Math.round(Mth.lerp(dropFactor, 96.0F, 48.0F)) : 48;
        renderScaledGuiItem(graphics, sideStack, cx - sideSpreadX, trophyCenterY + 2, 1.0F, 0.0F);
        renderScaledGuiItem(graphics, sideStack, cx + sideSpreadX, trophyCenterY + 2, 1.0F, 0.0F);

        // 4. Selected Trophy Shape (Chalice, Winged Spire, Herald Shield, or Celestial Crown) + Centerpiece Vanilla Emblem
        drawSelectedG1TrophyShape(graphics, cx, trophyCenterY, trophyIdx, trimColor, elapsed);
        ItemStack mainEmblem = getG1MainEmblemStack(emblemIdx);
        float pulseScale = 1.30F + 0.08F * Mth.sin(elapsed * 0.25F);
        float spinDeg = (emblemIdx == 1 || emblemIdx == 8) ? (elapsed * 4.5F) : 0.0F;
        renderScaledGuiItem(graphics, mainEmblem, cx, trophyCenterY - 8, pulseScale, spinDeg);

        // 5. Top Climax Crown Pill ("★ G1 ★" or "★ GRADE 1 ★" based on climaxFullGrade1 toggle!)
        int g1PillHalfW = climaxFullGrade1 ? 42 : 28;
        String climaxPillText = climaxFullGrade1 ? "§6★ §b§lGRADE 1 §6★" : "§6★ §b§lG1 §6★";
        graphics.fill(cx - g1PillHalfW, cy - 34, cx + g1PillHalfW, cy - 22, ribbonBg);
        graphics.fill(cx - g1PillHalfW, cy - 34, cx + g1PillHalfW, cy - 33, trimColor);
        graphics.fill(cx - g1PillHalfW, cy - 23, cx + g1PillHalfW, cy - 22, trimColor);
        graphics.drawCenteredString(mc.font, climaxPillText, cx, cy - 32, 0xFFFFFF);

        // 6. Race Title Entry Across the Trophy Center (governed by dropIdx & slamSpeedIdx!)
        float titleScale = switch (dropIdx) {
            case 7 -> 1.40F; // Clean Broadcast Cut
            case 3, 4, 5 -> Mth.lerp(dropFactor, 1.05F, 1.40F);
            default -> Mth.lerp(dropFactor, 2.35F, 1.40F); // Slam-In from 2.35x to 1.40x
        };
        int plateHalfW = Math.min(curFrameHalfW - 8, Math.max(68, titleW / 2 + 14));
        graphics.fill(cx - plateHalfW, cy + 1, cx + plateHalfW, cy + 19, 0xD80B0F19);
        graphics.fill(cx - plateHalfW, cy + 1, cx + plateHalfW, cy + 2, trimColor);
        graphics.fill(cx - plateHalfW, cy + 18, cx + plateHalfW, cy + 19, trimColor);

        if (dropIdx == 3 && dropFactor < 0.98F) {
            // Split-Join Convergence: two ghosted copies slide inward from left & right and fuse at center!
            int splitOffset = Math.round((1.0F - dropFactor) * 65.0F);
            graphics.pose().pushPose();
            graphics.pose().translate((float) (cx - splitOffset), (float) (cy + 5), 20.0F);
            graphics.pose().scale(titleScale, titleScale, 1.0F);
            graphics.drawCenteredString(mc.font, "§e§l" + raceTitle, 0, 0, 0xAAFFD700);
            graphics.pose().popPose();

            graphics.pose().pushPose();
            graphics.pose().translate((float) (cx + splitOffset), (float) (cy + 5), 20.0F);
            graphics.pose().scale(titleScale, titleScale, 1.0F);
            graphics.drawCenteredString(mc.font, "§f§l" + raceTitle, 0, 0, 0xFFFFFF);
            graphics.pose().popPose();
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate((float) cx, (float) (cy + 5), 20.0F);
            graphics.pose().scale(titleScale, titleScale, 1.0F);
            graphics.drawCenteredString(mc.font, "§f§l" + raceTitle, 0, 0, 0xFFFFFF);
            graphics.pose().popPose();
        }

        // 7. Unfurling Notched Ribbon Banner at the Base (Course + Surface + Distance)
        float unfurl = (dropIdx == 7) ? 1.0F : Mth.clamp((climaxTicks - 1.5F) / Math.max(4.0F, slamDuration), 0.15F, 1.0F);
        int ribbonMaxHalfW = Math.min(curFrameHalfW - 10, Math.max(76, bottomW / 2 + 22));
        int ribbonCurHalfW = Math.max(16, Math.round(ribbonMaxHalfW * unfurl));
        int ribTop = cy + 24;
        int ribBot = cy + 40;

        graphics.fill(cx - ribbonCurHalfW - 6, ribTop + 2, cx - ribbonCurHalfW, ribBot - 2, ribbonBg);
        graphics.fill(cx + ribbonCurHalfW, ribTop + 2, cx + ribbonCurHalfW + 6, ribBot - 2, ribbonBg);
        graphics.fill(cx - ribbonCurHalfW, ribTop, cx + ribbonCurHalfW, ribBot, ribbonBg);
        graphics.fill(cx - ribbonCurHalfW, ribTop, cx + ribbonCurHalfW, ribTop + 1, trimColor);
        graphics.fill(cx - ribbonCurHalfW, ribBot - 1, cx + ribbonCurHalfW, ribBot, trimColor);

        if (unfurl >= 0.60F) {
            graphics.drawCenteredString(mc.font, "§e§l" + bottomText, cx, ribTop + 4, 0xFFFFF0A0);
        }

        // 8. Climax Pyrotechnics & Particle Overlay (10 styles!)
        renderG1PyroAndConfettiOverlay(graphics, cx, cy, curFrameHalfW, pyroIdx, elapsed);

        // 9. Climax Drop Transition Special FX Overlays (Shockwave, Lightning Bolt, Shatter Starburst, or Flash)
        renderG1DropTransitionOverlay(graphics, cx, cy, curFrameHalfW, dropIdx, trimColor, climaxTicks, slamDuration);
    }

    private static void renderG1DropTransitionOverlay(GuiGraphics graphics, int cx, int cy, int boxHalfW,
                                                      int dropIdx, int trimColor, float climaxTicks, float slamDuration) {
        float win = Math.max(6.0F, slamDuration);
        if (climaxTicks >= win) return;
        float t = climaxTicks / win; // 0.0 -> 1.0

        switch (dropIdx) {
            case 0 -> { // Gold Flash & Slam
                int alpha = Math.round((1.0F - t) * 155.0F);
                graphics.fill(cx - boxHalfW, cy - 36, cx + boxHalfW, cy + 46, (alpha << 24) | 0xFFF6C8);
            }
            case 1 -> { // Sonic Shockwave (Expanding concentric gold & cyan rings)
                int rx = Math.round(t * (boxHalfW + 18));
                int ry = Math.round(t * 48.0F);
                int alpha = Math.round((1.0F - t) * 220.0F);
                int col = (alpha << 24) | (trimColor & 0x00FFFFFF);
                graphics.fill(cx - rx, cy - ry, cx + rx, cy - ry + 2, col);
                graphics.fill(cx - rx, cy + ry - 2, cx + rx, cy + ry, col);
                graphics.fill(cx - rx, cy - ry, cx - rx + 2, cy + ry, col);
                graphics.fill(cx + rx - 2, cy - ry, cx + rx, cy + ry, col);
            }
            case 2 -> { // Lightning Strike Drop (Vertical sky bolt striking the trophy center!)
                int boltX = cx;
                for (int y = cy - 56; y <= cy + 10; y += 6) {
                    int nextX = cx + Math.round(Mth.sin(y * 0.35F + climaxTicks * 2.0F) * 9.0F);
                    graphics.fill(Math.min(boltX, nextX) - 2, y, Math.max(boltX, nextX) + 2, y + 6, 0xCC88FFFF);
                    graphics.fill(boltX - 1, y, boltX + 1, y + 6, 0xFFFFFFFF);
                    boltX = nextX;
                }
            }
            case 6 -> { // Shatter Starburst (12 golden shards flying outward from center!)
                for (int s = 0; s < 12; s++) {
                    float ang = s * (Mth.TWO_PI / 12.0F);
                    int sx = cx + Math.round(Mth.cos(ang) * (t * 95.0F));
                    int sy = cy + Math.round(Mth.sin(ang) * (t * 42.0F));
                    graphics.fill(sx - 3, sy - 2, sx + 3, sy + 2, 0xFFFFD700);
                    graphics.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0xFFFFFFFF);
                }
            }
        }
    }

    /**
     * Renders the wide G1 Studio Timeline Bar AND Live Animated Preview inside the Racing Console's G1 FX Tab!
     * previewLockMode: 0 = Auto-Cycle (Act 1 -> Act 2 -> Climax), 1 = Lock Act 1, 2 = Lock Act 2, 3 = Lock Climax
     */
    public static void renderG1StudioPreviewAndTimeline(GuiGraphics graphics, Minecraft mc,
                                                        int centerX, int timelineTopY,
                                                        String raceTitle, String bottomText,
                                                        String g1PresetString, int fanfareDurationTicks,
                                                        int previewLockMode, float partialTick) {
        int[] preset = RacingManager.parseG1Preset(g1PresetString);
        int introIdx     = preset[0];
        int emblemIdx    = preset[1];
        int pyroIdx      = preset[2];
        int themeIdx     = preset[3];
        int act2Idx      = preset[4];
        int dropIdx      = preset[5];
        int trophyIdx    = preset[6];
        int act1Tenths   = preset[7];
        int act2Tenths   = preset[8];
        int holdTenths         = preset[9];
        int slamSpeedIdx       = preset[10];
        boolean showNameEarly  = preset[11] > 0;
        boolean showTrackEarly = preset[12] > 0;
        boolean climaxFullG1   = preset[13] > 0;

        int trimColor = getG1ThemeTrimColor(themeIdx);
        int ribbonBg = getG1ThemePrimaryBg(themeIdx);

        int boxHalfW = 194; // Full 388px width inside the 408px Racing Console!
        int barLeft = centerX - boxHalfW;
        int barRight = centerX + boxHalfW;
        int barW = boxHalfW * 2;

        float fanfareSec = Math.max(2.0F, fanfareDurationTicks / 20.0F);
        float holdSec = holdTenths / 10.0F;
        float totalSec = fanfareSec + holdSec;
        float act1Sec = Math.min(totalSec, act1Tenths / 10.0F);
        float act2Sec = (act2Idx > 0) ? Math.min(Math.max(0.0F, totalSec - act1Sec), act2Tenths / 10.0F) : 0.0F;
        float climaxSec = Math.max(0.0F, totalSec - act1Sec - act2Sec);

        // 1. Draw Proportional 3-Phase Timeline Bar (timelineTopY to timelineTopY + 11)
        int wAct1 = Math.round((act1Sec / totalSec) * barW);
        int wAct2 = Math.round((act2Sec / totalSec) * barW);
        int x1 = barLeft + wAct1;
        int x2 = x1 + wAct2;

        graphics.fill(barLeft, timelineTopY, barRight, timelineTopY + 11, 0xFF0E131D);
        if (wAct1 > 0) {
            graphics.fill(barLeft, timelineTopY, x1, timelineTopY + 11, 0xCC997A00); // Act 1: Gold-Amber
        }
        if (wAct2 > 0) {
            graphics.fill(x1, timelineTopY, x2, timelineTopY + 11, 0xCC1E7A44); // Act 2: Emerald Bridge
        }
        if (x2 < barRight) {
            graphics.fill(x2, timelineTopY, barRight, timelineTopY + 11, 0xCC8A1C4A); // Climax + Hold: Crimson-Magenta
        }
        // Mark audio end vs post-fanfare hold start
        if (holdSec > 0.05F) {
            int xAudioEnd = barLeft + Math.round((fanfareSec / totalSec) * barW);
            graphics.fill(xAudioEnd, timelineTopY, xAudioEnd + 1, timelineTopY + 11, 0xFF55FFFF);
        }

        String timelineSummary = String.format(Locale.ROOT,
                "§eAct1: %.1fs §7| %s §7| §dClimax Drop @ %.1fs §7(Total: %.1fs = %.1fs ♪ + %.1fs Hold)",
                act1Sec,
                (act2Idx > 0 && act2Sec > 0.0F) ? String.format(Locale.ROOT, "§aAct2: %.1fs", act2Sec) : "§8Act2: Skip",
                act1Sec + act2Sec,
                totalSec, fanfareSec, holdSec);
        graphics.drawCenteredString(mc.font, timelineSummary, centerX, timelineTopY + 2, 0xFFFFFF);

        // Live playhead cursor when testing audio
        float liveTestSec = getClientLogoElapsedSeconds(partialTick);
        if (liveTestSec >= 0.0F && liveTestSec <= totalSec) {
            int playheadX = barLeft + Math.round((liveTestSec / totalSec) * barW);
            graphics.fill(playheadX - 1, timelineTopY - 1, playheadX + 2, timelineTopY + 12, 0xFF55FF55);
        }

        // 2. Live Animated Preview Viewport (timelineTopY + 13 to timelineTopY + 66)
        int previewTop = timelineTopY + 13;
        int previewBot = timelineTopY + 65;
        int midY = (previewTop + previewBot) / 2 + 1;

        graphics.fill(barLeft, previewTop, barRight, previewBot, 0xFF080C14);
        graphics.fill(barLeft, previewTop, barRight, previewTop + 1, trimColor);
        graphics.fill(barLeft, previewBot - 1, barRight, previewBot, trimColor);

        float simElapsed = (System.currentTimeMillis() % 60000L) / 50.0F + partialTick;
        int activePhase; // 1 = Act 1, 2 = Act 2, 3 = Climax
        if (previewLockMode == 1) {
            activePhase = 1;
        } else if (previewLockMode == 2 && act2Idx > 0) {
            activePhase = 2;
        } else if (previewLockMode == 3) {
            activePhase = 3;
        } else {
            // Auto-cycle smoothly based on configured phase durations!
            float cycleSec = (simElapsed / 20.0F) % Math.max(2.0F, totalSec);
            if (cycleSec < act1Sec) activePhase = 1;
            else if (cycleSec < act1Sec + act2Sec) activePhase = 2;
            else activePhase = 3;
        }

        graphics.enableScissor(barLeft + 1, previewTop + 1, barRight - 1, previewBot - 1);
        if (activePhase == 1) {
            float prog = ((simElapsed % 60.0F) / 60.0F);
            renderG1ActSequence(graphics, mc, centerX, midY, boxHalfW - 4, introIdx, themeIdx, simElapsed, prog,
                    "§e[ACT 1] §6★ GRADE 1 ★",
                    showNameEarly ? raceTitle : "",
                    showTrackEarly ? bottomText : "",
                    false);
        } else if (activePhase == 2) {
            float prog = ((simElapsed % 60.0F) / 60.0F);
            int bridgeFx = Math.max(0, act2Idx - 1) % RacingManager.G1_INTROS.length;
            renderG1ActSequence(graphics, mc, centerX, midY, boxHalfW - 4, bridgeFx, themeIdx, simElapsed, prog,
                    "§a[ACT 2] §6★ GRADE 1 ★",
                    showNameEarly ? raceTitle : "",
                    showTrackEarly ? bottomText : "",
                    true);
        } else {
            // Render Climax Trophy + Emblem + Ribbon + Pyro inside the preview box
            drawProceduralLaurelWreath(graphics, centerX, midY - 4, 34, trimColor);
            drawSelectedG1TrophyShape(graphics, centerX, midY - 4, trophyIdx, trimColor, simElapsed);

            ItemStack sideStack = getG1SideEmblemStack(emblemIdx);
            renderScaledGuiItem(graphics, sideStack, centerX - 44, midY - 4, 0.85F, 0.0F);
            renderScaledGuiItem(graphics, sideStack, centerX + 44, midY - 4, 0.85F, 0.0F);

            ItemStack mainEmblem = getG1MainEmblemStack(emblemIdx);
            float spinDeg = (emblemIdx == 1 || emblemIdx == 8) ? (simElapsed * 4.0F) : 0.0F;
            renderScaledGuiItem(graphics, mainEmblem, centerX, midY - 12, 1.05F, spinDeg);

            String climaxBadgePreview = climaxFullG1 ? "§b§lGRADE 1" : "§b§lG1";
            graphics.drawCenteredString(mc.font,
                    "§d[CLIMAX PREVIEW] §6★ " + climaxBadgePreview + " §6★ §7(" + RacingManager.G1_DROPS[dropIdx] + ")",
                    centerX, previewTop + 2, 0xFFFFFF);

            int plateHalfW = Math.min(126, Math.max(58, mc.font.width(raceTitle) / 2 + 10));
            graphics.fill(centerX - plateHalfW, midY - 2, centerX + plateHalfW, midY + 10, 0xD80B0F19);
            graphics.fill(centerX - plateHalfW, midY - 2, centerX + plateHalfW, midY - 1, trimColor);
            ClientScreens.drawScaledCenteredString(graphics, mc.font, "§f§l" + raceTitle, centerX, midY, (plateHalfW * 2) - 8, 0xFFFFFF);

            int ribHalfW = Math.min(136, Math.max(68, mc.font.width(bottomText) / 2 + 14));
            graphics.fill(centerX - ribHalfW, previewBot - 14, centerX + ribHalfW, previewBot - 2, ribbonBg);
            graphics.fill(centerX - ribHalfW, previewBot - 14, centerX + ribHalfW, previewBot - 13, trimColor);
            ClientScreens.drawScaledCenteredString(graphics, mc.font, "§e" + bottomText, centerX, previewBot - 11, (ribHalfW * 2) - 10, 0xFFFFF0A0);

            renderG1PyroAndConfettiOverlay(graphics, centerX, midY - 4, boxHalfW - 10, pyroIdx, simElapsed);
        }
        graphics.disableScissor();
    }

    private static void renderG1PyroAndConfettiOverlay(GuiGraphics graphics, int centerX, int centerY,
                                                       int boxHalfW, int pyroIdx, float elapsed) {
        if (pyroIdx == 9) return; // Minimal (Clean)

        int leftNozzleX = centerX - boxHalfW + 18;
        int rightNozzleX = centerX + boxHalfW - 18;
        int nozzleBaseY = centerY + 38;

        switch (pyroIdx) {
            case 0 -> {
                // 0: Twin Upward Pyrotechnic Spark Fountains + Falling Gold Confetti
                for (int i = 0; i < 18; i++) {
                    float seed = i * 13.37F;
                    float life = ((elapsed * 0.14F) + (i * 0.07F)) % 1.0F;
                    int dy = Math.round(life * 64.0F);
                    int spread = Math.round(Mth.sin(seed + life * 4.0F) * (life * 14.0F));
                    int sparkCol = (i % 3 == 0) ? 0xFFFFFFFF : ((i % 2 == 0) ? 0xFFFFEA70 : 0xFFFF9900);
                    graphics.fill(leftNozzleX + spread - 1, nozzleBaseY - dy - 2, leftNozzleX + spread + 1, nozzleBaseY - dy + 1, sparkCol);
                    graphics.fill(rightNozzleX - spread - 1, nozzleBaseY - dy - 2, rightNozzleX - spread + 1, nozzleBaseY - dy + 1, sparkCol);
                }
                for (int c = 0; c < 14; c++) {
                    float cLife = ((elapsed * 0.06F) + (c * 0.11F)) % 1.0F;
                    int cx = centerX + Math.round(Mth.sin(c * 9.1F + elapsed * 0.08F) * (boxHalfW - 14));
                    int cy = (centerY - 32) + Math.round(cLife * 72.0F);
                    int confCol = (c % 2 == 0) ? 0xFFFFD700 : 0xFFFFF8DC;
                    graphics.fill(cx - 2, cy - 1, cx + 1, cy + 1, confCol);
                }
            }
            case 1 -> {
                // 1: Swirling Sakura Petal Storm
                for (int p = 0; p < 18; p++) {
                    float pLife = ((elapsed * 0.07F) + (p * 0.09F)) % 1.0F;
                    int px = centerX - boxHalfW + 12 + Math.round(((p * 29 + elapsed * 1.8F) % Math.max(20, boxHalfW * 2 - 24)));
                    int py = (centerY - 30) + Math.round(pLife * 68.0F + Mth.sin(elapsed * 0.15F + p) * 4.0F);
                    int petalCol = (p % 3 == 0) ? 0xFFFFB7C5 : ((p % 2 == 0) ? 0xFFFF69B4 : 0xFFFFE4E1);
                    graphics.fill(px - 2, py - 1, px + 1, py + 1, petalCol);
                    graphics.fill(px - 1, py - 2, px + 2, py, petalCol);
                }
            }
            case 2 -> {
                // 2: Thunder & Lightning Arcs
                for (int b = 0; b < 2; b++) {
                    int startX = (b == 0) ? leftNozzleX : rightNozzleX;
                    int curX = startX;
                    int curY = centerY - 32;
                    for (int seg = 0; seg < 6; seg++) {
                        int nextX = Math.round(Mth.lerp((seg + 1) / 6.0F, (float) startX, (float) centerX))
                                + Math.round(Mth.sin(elapsed * 0.9F + seg * 2.3F + b) * 8.0F);
                        int nextY = curY + 8;
                        graphics.fill(Math.min(curX, nextX) - 1, curY, Math.max(curX, nextX) + 1, nextY, 0xFF88FFFF);
                        graphics.fill(curX, curY, curX + 1, nextY, 0xFFFFFFFF);
                        curX = nextX;
                        curY = nextY;
                    }
                }
            }
            case 3 -> {
                // 3: Golden Stardust
                for (int s = 0; s < 14; s++) {
                    float sLife = ((elapsed * 0.08F) + (s * 0.12F)) % 1.0F;
                    int sx = centerX + Math.round(Mth.cos(s * 4.7F + elapsed * 0.06F) * (boxHalfW - 16));
                    int sy = (centerY + 34) - Math.round(sLife * 64.0F);
                    graphics.fill(sx - 2, sy, sx + 3, sy + 1, 0xFFFFD700);
                    graphics.fill(sx, sy - 2, sx + 1, sy + 3, 0xFFFFFFFF);
                }
            }
            case 4 -> {
                // 4: Grand Firework Finale (Expanding multi-color starbursts!)
                int[] fwCols = {0xFFFF4444, 0xFF44FF55, 0xFF44DDFF, 0xFFFFD700};
                for (int f = 0; f < 4; f++) {
                    float fLife = ((elapsed * 0.09F) + f * 0.25F) % 1.0F;
                    int fx = centerX + ((f % 2 == 0) ? -1 : 1) * (35 + (f / 2) * 38);
                    int fy = centerY - 14 + (f % 2) * 14;
                    int rad = Math.round(fLife * 22.0F);
                    int col = fwCols[f];
                    for (int ray = 0; ray < 8; ray++) {
                        float a = ray * (Mth.TWO_PI / 8.0F);
                        int rx = fx + Math.round(Mth.cos(a) * rad);
                        int ry = fy + Math.round(Mth.sin(a) * rad);
                        graphics.fill(rx - 1, ry - 1, rx + 2, ry + 2, col);
                    }
                }
            }
            case 5 -> {
                // 5: Emerald Totem Cascade
                for (int e = 0; e < 16; e++) {
                    float eLife = ((elapsed * 0.11F) + e * 0.08F) % 1.0F;
                    int ex = centerX + Math.round(Mth.sin(e * 5.3F + elapsed * 0.1F) * (boxHalfW - 16));
                    int ey = (centerY + 36) - Math.round(eLife * 66.0F);
                    int col = (e % 2 == 0) ? 0xFF55FF55 : 0xFFFFD700;
                    graphics.fill(ex - 2, ey - 2, ex + 2, ey + 2, col);
                }
            }
            case 6 -> {
                // 6: Soul Flame Aura (Rising cyan/blue soul fire embers)
                for (int sf = 0; sf < 16; sf++) {
                    float sLife = ((elapsed * 0.10F) + sf * 0.07F) % 1.0F;
                    int sx = centerX + Math.round(Mth.sin(sf * 7.1F + elapsed * 0.12F) * (boxHalfW - 18));
                    int sy = (centerY + 36) - Math.round(sLife * 64.0F);
                    int col = (sf % 2 == 0) ? 0xFF00E5FF : 0xFF77FFFF;
                    graphics.fill(sx - 2, sy - 3, sx + 2, sy + 1, col);
                }
            }
            case 7 -> {
                // 7: Blizzard Diamond Dust
                for (int b = 0; b < 18; b++) {
                    float bLife = ((elapsed * 0.09F) + b * 0.08F) % 1.0F;
                    int bx = centerX - boxHalfW + 14 + Math.round(((b * 31 + elapsed * 2.2F) % Math.max(20, boxHalfW * 2 - 28)));
                    int by = (centerY - 32) + Math.round(bLife * 70.0F);
                    graphics.fill(bx - 2, by, bx + 3, by + 1, 0xFFAAFFFF);
                    graphics.fill(bx, by - 2, bx + 1, by + 3, 0xFFFFFFFF);
                }
            }
            case 8 -> {
                // 8: Crimson Dragon Breath
                for (int d = 0; d < 16; d++) {
                    float dLife = ((elapsed * 0.10F) + d * 0.08F) % 1.0F;
                    int dx = centerX + Math.round(Mth.cos(d * 3.9F + elapsed * 0.14F) * (boxHalfW - 18) * (1.0F - 0.4F * dLife));
                    int dy = (centerY + 34) - Math.round(dLife * 62.0F);
                    int col = (d % 2 == 0) ? 0xFFFF44CC : 0xFFAA22FF;
                    graphics.fill(dx - 2, dy - 2, dx + 2, dy + 2, col);
                }
            }
        }
    }

    private static void drawSelectedG1TrophyShape(GuiGraphics graphics, int cx, int cy, int trophyIdx, int trimColor, float elapsed) {
        switch (trophyIdx) {
            case 1 -> {
                // 1: Imperial Winged Spire
                graphics.fill(cx - 14, cy + 16, cx + 14, cy + 21, 0xFF221A0F);
                graphics.fill(cx - 10, cy + 13, cx + 10, cy + 16, trimColor);
                graphics.fill(cx - 5, cy - 16, cx + 5, cy + 13, 0xFFFFD700);
                // Spread golden wings
                for (int w = 1; w <= 5; w++) {
                    int wx = w * 5;
                    int wy = cy - 4 - w * 2;
                    graphics.fill(cx - 5 - wx, wy, cx - 5, wy + 3, 0xFFFFD700);
                    graphics.fill(cx + 5, wy, cx + 5 + wx, wy + 3, 0xFFFFD700);
                }
            }
            case 2 -> {
                // 2: Heraldic Shield & Crossed Golden Swords
                ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
                renderScaledGuiItem(graphics, sword, cx - 12, cy - 2, 1.35F, -45.0F);
                renderScaledGuiItem(graphics, sword, cx + 12, cy - 2, 1.35F, 45.0F);
                graphics.fill(cx - 16, cy - 14, cx + 16, cy + 8, trimColor);
                graphics.fill(cx - 12, cy + 8, cx + 12, cy + 14, trimColor);
                graphics.fill(cx - 6, cy + 14, cx + 6, cy + 18, trimColor);
                graphics.fill(cx - 14, cy - 12, cx + 14, cy + 7, 0xFF1A1228);
            }
            case 3 -> {
                // 3: Floating Celestial Crown
                int bobY = cy + Math.round(Mth.sin(elapsed * 0.20F) * 2.0F);
                graphics.fill(cx - 20, bobY + 2, cx + 20, bobY + 10, 0xFFD4AF37);
                graphics.fill(cx - 20, bobY + 8, cx + 20, bobY + 11, trimColor);
                // 5 Crown Peaks
                int[] peakX = {-18, -9, 0, 9, 18};
                for (int px : peakX) {
                    graphics.fill(cx + px - 3, bobY - 8, cx + px + 3, bobY + 2, 0xFFFFD700);
                    graphics.fill(cx + px - 1, bobY - 11, cx + px + 1, bobY - 8, 0xFF55FFFF);
                }
            }
            default -> drawProceduralGoldTrophyCup(graphics, cx, cy, elapsed);
        }
    }

    private static void drawProceduralGoldTrophyCup(GuiGraphics graphics, int cx, int cy, float elapsed) {
        // Pedestal Base
        graphics.fill(cx - 14, cy + 16, cx + 14, cy + 21, 0xFF221A0F);
        graphics.fill(cx - 12, cy + 14, cx + 12, cy + 16, 0xFFD4AF37);
        // Stem
        graphics.fill(cx - 4, cy + 6, cx + 4, cy + 14, 0xFFE6C229);
        // Cup Bowl (tiered golden chalice)
        graphics.fill(cx - 12, cy - 2, cx + 12, cy + 6, 0xFFD4AF37);
        graphics.fill(cx - 16, cy - 12, cx + 16, cy - 2, 0xFFFFD700);
        graphics.fill(cx - 18, cy - 15, cx + 18, cy - 12, 0xFFFFF099);
        // Handles (Left & Right)
        graphics.fill(cx - 22, cy - 11, cx - 16, cy - 9, 0xFFD4AF37);
        graphics.fill(cx - 22, cy - 11, cx - 20, cy - 1, 0xFFD4AF37);
        graphics.fill(cx - 22, cy - 3, cx - 12, cy - 1, 0xFFD4AF37);

        graphics.fill(cx + 16, cy - 11, cx + 22, cy - 9, 0xFFD4AF37);
        graphics.fill(cx + 20, cy - 11, cx + 22, cy - 1, 0xFFD4AF37);
        graphics.fill(cx + 12, cy - 3, cx + 22, cy - 1, 0xFFD4AF37);

        // Moving Specular Glint Sweep across the Trophy Bowl
        int glintX = cx + Math.round(Mth.sin(elapsed * 0.18F) * 11.0F);
        graphics.fill(glintX - 2, cy - 14, glintX + 2, cy + 4, 0x99FFFFFF);
    }

    private static void drawProceduralLaurelWreath(GuiGraphics graphics, int cx, int cy, int radiusX, int color) {
        for (int i = 0; i < 9; i++) {
            float t = i / 8.0F;
            float angle = 0.35F + t * 1.95F;
            int lx = cx - Math.round(Mth.sin(angle) * radiusX);
            int rx = cx + Math.round(Mth.sin(angle) * radiusX);
            int ly = cy + Math.round(Mth.cos(angle) * 20.0F);
            int leafCol = (i % 2 == 0) ? color : 0xFFFFF099;
            graphics.fill(lx - 3, ly - 2, lx + 2, ly + 2, leafCol);
            graphics.fill(rx - 2, ly - 2, rx + 3, ly + 2, leafCol);
        }
    }

    private static void drawProceduralFivePointStar(GuiGraphics graphics, int cx, int cy, int radius, float rotDeg, int outerCol, int coreCol) {
        float baseRad = rotDeg * Mth.DEG_TO_RAD;
        for (int p = 0; p < 5; p++) {
            float a = baseRad + p * (Mth.TWO_PI / 5.0F);
            for (int r = 2; r <= radius; r += 2) {
                int px = cx + Math.round(Mth.cos(a) * r);
                int py = cy + Math.round(Mth.sin(a) * r);
                int sz = Math.max(1, (radius - r) / 5 + 1);
                graphics.fill(px - sz, py - sz, px + sz, py + sz, outerCol);
            }
        }
        graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, coreCol);
    }

    private static void renderScaledGuiItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale, float rotDeg) {
        if (stack == null || stack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y, 10.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        if (Math.abs(rotDeg) > 0.01F) {
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        }
        graphics.renderItem(stack, -8, -8);
        graphics.pose().popPose();
    }

    private static void renderTopRightMoodBadge(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        int m = Math.round(clientHud.mood);
        String tierFmt;
        if (m <= 20) {
            tierFmt = "§4§l" + ThoroughbredData.tr("Awful", "極差", "极差", "絶不調").toUpperCase(Locale.ROOT);
        } else if (m <= 40) {
            tierFmt = "§c§l" + ThoroughbredData.tr("Bad", "不佳", "不佳", "不調").toUpperCase(Locale.ROOT);
        } else if (m <= 60) {
            tierFmt = "§e§l" + ThoroughbredData.tr("Normal", "普通", "普通", "普通").toUpperCase(Locale.ROOT);
        } else if (m <= 80) {
            tierFmt = "§a§l" + ThoroughbredData.tr("Good", "良好", "良好", "好調").toUpperCase(Locale.ROOT);
        } else {
            tierFmt = "§d§l" + ThoroughbredData.tr("Great", "極佳", "极佳", "絶好調").toUpperCase(Locale.ROOT);
        }

        int moodColor = ThoroughbredData.getMoodTierColor(clientHud.mood);
        int boxW = 116;
        int boxH = 24;
        int boxX = screenWidth - boxW - 6;
        int boxY = 6;

        graphics.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, 0x780D1117);
        graphics.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY - 1, moodColor);

        String moodHeader = "§7" + ThoroughbredData.tr("MOOD", "心情", "心情", "やる気") + ": " + tierFmt;
        graphics.drawString(mc.font, moodHeader, boxX + 3, boxY + 2, 0xFFFFFF, true);

        String scoreText = String.format(Locale.ROOT, "§f%d", m);
        int scoreW = mc.font.width(scoreText);
        graphics.drawString(mc.font, scoreText, boxX + boxW - scoreW - 3, boxY + 2, 0xFFFFFF, true);

        int barX = boxX + 3;
        int barY = boxY + 14;
        int totalBarW = boxW - 6;
        graphics.fill(barX - 1, barY - 1, barX + totalBarW + 1, barY + 6, 0xFF000000);
        graphics.fill(barX, barY, barX + totalBarW, barY + 5, 0xFF222222);

        int fillW = (int) Math.round(Mth.clamp(clientHud.mood / 100.0F, 0.0F, 1.0F) * totalBarW);
        graphics.fill(barX, barY, barX + fillW, barY + 5, moodColor);

        for (int i = 1; i <= 4; i++) {
            int tickX = barX + (totalBarW * i) / 5;
            graphics.fill(tickX, barY, tickX + 1, barY + 5, 0xAA000000);
        }
    }

    private static void drawMiniCareBar(GuiGraphics graphics, int x, int y, int width, int height, float ratio, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        graphics.fill(x, y, x + width, y + height, 0xFF222222);
        int fill = (int) Math.round(Mth.clamp(ratio, 0.0F, 1.0F) * width);
        graphics.fill(x, y, x + fill, y + height, color);
    }

    private static void drawTrainingExpRow(GuiGraphics graphics, Minecraft mc, int x, int y, String statName,
                                           int cur, int max, float curExp, float reqExp, boolean gaining, int color, long gameTime) {
        boolean isMaxed = cur >= max;
        String label = (gaining ? "§e▶ " : "§f") + statName + String.format(Locale.ROOT, " %.1f", cur / 10.0);
        graphics.drawString(mc.font, label, x, y, 0xFFFFFF, true);

        String expStr = isMaxed
                ? "§6MAX"
                : String.format(Locale.ROOT, "§7%.0f/%.0f", curExp, reqExp);
        int expW = mc.font.width(expStr);
        graphics.drawString(mc.font, expStr, x + 122 - expW, y, 0xFFFFFF, true);

        int barY = y + 9;
        int barW = 122;
        graphics.fill(x - 1, barY - 1, x + barW + 1, barY + 5, 0xFF000000);
        graphics.fill(x, barY, x + barW, barY + 4, 0xFF222222);

        float ratio = reqExp > 0.0F ? (curExp / reqExp) : 1.0F;
        int fillW = (int) Math.round(Mth.clamp(ratio, 0.0F, 1.0F) * barW);
        int fillColor = isMaxed ? 0xFFFFD700 : (gaining && (gameTime / 3) % 2 == 0 ? 0xFFFFFFFF : color);
        graphics.fill(x, barY, x + fillW, barY + 4, fillColor);
    }

    // =========================================================================
    // 8. STEP 22: 3D MULTI-BLOCK TRACKSIDE RESULTS TOTE BOARD WORLD RENDERER
    // =========================================================================
    private static class ToteScreenWall {
        final BlockPos topLeftPos;
        final Direction facing;
        final int widthBlocks;
        final int heightBlocks;
        final int theme;

        ToteScreenWall(BlockPos topLeftPos, Direction facing, int widthBlocks, int heightBlocks, int theme) {
            this.topLeftPos = topLeftPos;
            this.facing = facing;
            this.widthBlocks = widthBlocks;
            this.heightBlocks = heightBlocks;
            this.theme = theme;
        }
    }

    private static final List<ToteScreenWall> CACHED_TOTE_WALLS = new ArrayList<>();
    private static final Map<BlockPos, ToteScreenWall> KNOWN_TOTE_WALLS = new java.util.LinkedHashMap<>();
    private static Level lastToteClientLevel = null;
    private static long lastToteWallScanMillis = 0L;
    private static BlockPos lastToteWallScanCenter = BlockPos.ZERO;

    private static void updateCachedToteWallsIfNeeded(Level level, BlockPos playerPos) {
        if (lastToteClientLevel != level) {
            lastToteClientLevel = level;
            KNOWN_TOTE_WALLS.clear();
            CACHED_TOTE_WALLS.clear();
            lastToteWallScanMillis = 0L;
        }

        long now = System.currentTimeMillis();
        if (now - lastToteWallScanMillis < 1000L && playerPos.distSqr(lastToteWallScanCenter) < 256.0) {
            return;
        }
        lastToteWallScanMillis = now;
        lastToteWallScanCenter = playerPos.immutable();

        // 1. Validate previously discovered walls whose chunks are currently loaded.
        //    Walls in distant/unloaded chunks are preserved so the display stays visible across the whole racecourse.
        KNOWN_TOTE_WALLS.entrySet().removeIf(entry -> {
            BlockPos topLeft = entry.getKey();
            if (!level.hasChunkAt(topLeft)) {
                return false; // Keep remembered wall while its chunk is out of range
            }
            BlockState bs = level.getBlockState(topLeft);
            return !(bs.getBlock() instanceof ModBlocks.ToteBoardBlock);
        });

        // 2. Fast chunk-section palette scan across loaded chunks (up to 24 chunks = 384 blocks radius).
        //    LevelChunkSection#maybeHas checks the 16x16x16 palette in O(1) time without iterating 4,096 blocks.
        Minecraft mc = Minecraft.getInstance();
        int renderDistChunks = mc.options != null ? mc.options.getEffectiveRenderDistance() : 12;
        int chunkRadius = Mth.clamp(renderDistChunks, 12, 24);
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        int minSectionY = level.getMinSection();

        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                    net.minecraft.world.level.chunk.LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (!section.maybeHas(bs -> bs.getBlock() instanceof ModBlocks.ToteBoardBlock)) continue;

                    int baseX = cx << 4;
                    int baseY = (minSectionY + sIdx) << 4;
                    int baseZ = cz << 4;

                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        for (int lx = 0; lx < 16; lx++) {
                            int x = baseX + lx;
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState bs = section.getBlockState(lx, ly, lz);
                                if (!(bs.getBlock() instanceof ModBlocks.ToteBoardBlock)) continue;

                                int z = baseZ + lz;
                                mpos.set(x, y, z);
                                if (visited.contains(mpos)) continue;

                                Direction facing = bs.getValue(ModBlocks.ToteBoardBlock.FACING);
                                int theme = bs.getValue(ModBlocks.ToteBoardBlock.THEME);
                                List<BlockPos> cluster = ModBlocks.ToteBoardBlock.collectConnectedWall(level, mpos, facing);
                                visited.addAll(cluster);

                                if (cluster.isEmpty()) continue;

                                Direction rightDir = facing.getCounterClockWise();
                                BlockPos ref = cluster.get(0);
                                int minRight = 0, maxRight = 0;
                                int minY = ref.getY(), maxY = ref.getY();

                                for (BlockPos p : cluster) {
                                    int rCoord = (p.getX() - ref.getX()) * rightDir.getStepX()
                                            + (p.getZ() - ref.getZ()) * rightDir.getStepZ();
                                    if (rCoord < minRight) minRight = rCoord;
                                    if (rCoord > maxRight) maxRight = rCoord;
                                    if (p.getY() < minY) minY = p.getY();
                                    if (p.getY() > maxY) maxY = p.getY();
                                }

                                BlockPos topLeft = new BlockPos(
                                        ref.getX() + rightDir.getStepX() * minRight,
                                        maxY,
                                        ref.getZ() + rightDir.getStepZ() * minRight
                                );
                                int widthB = Math.max(1, maxRight - minRight + 1);
                                int heightB = Math.max(1, maxY - minY + 1);

                                // Remove any superseded partial anchor from the same cluster before storing the full wall
                                for (BlockPos p : cluster) {
                                    if (!p.equals(topLeft)) {
                                        KNOWN_TOTE_WALLS.remove(p);
                                    }
                                }
                                KNOWN_TOTE_WALLS.put(topLeft, new ToteScreenWall(topLeft, facing, widthB, heightB, theme));
                            }
                        }
                    }
                }
            }
        }

        CACHED_TOTE_WALLS.clear();
        CACHED_TOTE_WALLS.addAll(KNOWN_TOTE_WALLS.values());
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        updateCachedToteWallsIfNeeded(mc.level, mc.player.blockPosition());
        if (CACHED_TOTE_WALLS.isEmpty()) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        ModNetwork.RaceLeaderboardSyncPacket activeOrLast = clientRaceLeaderboard.active
                ? clientRaceLeaderboard
                : lastToteBoardSnapshot;

        // Render Tote Boards up to 512 blocks (32 chunks) away across the full racecourse
        double maxRenderDistSqr = 512.0 * 512.0;
        for (ToteScreenWall wall : CACHED_TOTE_WALLS) {
            if (wall.topLeftPos.distToCenterSqr(camPos.x, camPos.y, camPos.z) > maxRenderDistSqr) {
                continue;
            }
            renderSingleToteWall(poseStack, bufferSource, mc, camPos, wall, activeOrLast);
        }

        bufferSource.endBatch();
    }

    private static void renderSingleToteWall(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                             Minecraft mc, Vec3 camPos, ToteScreenWall wall,
                                             ModNetwork.RaceLeaderboardSyncPacket lb) {
        int bx = wall.topLeftPos.getX();
        int by = wall.topLeftPos.getY();
        int bz = wall.topLeftPos.getZ();

        double startX, startY = by + 1.0, startZ;
        float yawDeg;
        switch (wall.facing) {
            case SOUTH -> {
                startX = bx;
                startZ = bz + 1.012;
                yawDeg = 0.0F;
            }
            case NORTH -> {
                startX = bx + 1.0;
                startZ = bz - 0.012;
                yawDeg = 180.0F;
            }
            case EAST -> {
                startX = bx + 1.012;
                startZ = bz + 1.0;
                yawDeg = 90.0F;
            }
            case WEST -> {
                startX = bx - 0.012;
                startZ = bz;
                yawDeg = -90.0F;
            }
            default -> {
                return;
            }
        }

        float virtualW = 300.0F;
        float virtualH = 180.0F;
        float scaleX = (wall.widthBlocks * 1.0F) / virtualW;
        float scaleY = (wall.heightBlocks * 1.0F) / virtualH;

        poseStack.pushPose();
        poseStack.translate(startX - camPos.x, startY - camPos.y, startZ - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.scale(scaleX, -scaleY, 1.0F);

        Matrix4f mat = poseStack.last().pose();
        int fullLight = LightTexture.FULL_BRIGHT;

        // Theme colors: 0 = Classic Racecourse Emerald, 1 = Midnight Digital LED, 2 = Prestige Gold & Obsidian
        int outerFrameCol = switch (wall.theme) {
            case 1 -> 0xFF0A1428;
            case 2 -> 0xFF1F180D;
            default -> 0xFF0D2618;
        };
        int trimGoldCol = switch (wall.theme) {
            case 1 -> 0xFF28A8FF;
            case 2 -> 0xFFFFD700;
            default -> 0xFFD4AF37;
        };
        int mainBgCol = switch (wall.theme) {
            case 1 -> 0xFF070C18;
            case 2 -> 0xFF0E0D0C;
            default -> 0xFF08140E;
        };
        int panelBgCol = switch (wall.theme) {
            case 1 -> 0xFF0E182B;
            case 2 -> 0xFF181512;
            default -> 0xFF0E2016;
        };

        // 1. Outer Bezel & Inner LED Screen Canvas
        drawToteQuad(mat, bufferSource, 0, 0, virtualW, virtualH, 0.0F, outerFrameCol);
        drawToteQuad(mat, bufferSource, 2, 2, virtualW - 2, virtualH - 2, 0.001F, trimGoldCol);
        drawToteQuad(mat, bufferSource, 4, 4, virtualW - 4, virtualH - 4, 0.002F, mainBgCol);

        // Determine Race Metadata & Weather Track Condition
        boolean hasRace = lb != null && lb.active;
        String grade = hasRace && lb.grade != null && !lb.grade.isBlank() ? lb.grade : "OP";
        String raceTitle = hasRace && lb.raceName != null && !lb.raceName.isBlank()
                ? lb.raceName
                : ThoroughbredData.tr("TRACKSIDE RESULTS TOTE BOARD", "賽馬場賽果顯示板", "赛马场赛果显示板", "レース着順掲示板");
        String trackSummary = hasRace && lb.trackSummary != null && !lb.trackSummary.isBlank()
                ? lb.trackSummary
                : "Racecourse Turf 2400m";
        int distMeters = hasRace ? lb.totalDistanceMeters : 2400;

        boolean isTurf = !trackSummary.toLowerCase(Locale.ROOT).contains("dirt");
        boolean thundering = mc.level.isThundering();
        boolean raining = mc.level.isRaining();

        String surfaceLabel = isTurf
                ? ThoroughbredData.tr("TURF", "草地", "草地", "芝")
                : ThoroughbredData.tr("DIRT", "泥地", "泥地", "ダート");
        int surfaceBadgeCol = isTurf ? 0xFF1B7A38 : 0xFF965A24;

        String condLabel;
        int condBadgeCol;
        if (isTurf) {
            if (thundering) {
                condLabel = ThoroughbredData.trTrackCondition("SOFT", "軟地", "软地", "不良");
                condBadgeCol = 0xFFB83232;
            } else if (raining) {
                condLabel = ThoroughbredData.trTrackCondition("YIELDING", "黏地", "黏地", "稍重");
                condBadgeCol = 0xFFC87D20;
            } else {
                condLabel = ThoroughbredData.trTrackCondition("FIRM", "好地", "好地", "良");
                condBadgeCol = 0xFF228B44;
            }
        } else {
            if (thundering) {
                condLabel = ThoroughbredData.trTrackCondition("MUDDY", "泥濘", "泥泞", "不良");
                condBadgeCol = 0xFFB83232;
            } else if (raining) {
                condLabel = ThoroughbredData.trTrackCondition("GOOD", "好地", "好地", "稍重");
                condBadgeCol = 0xFFC87D20;
            } else {
                condLabel = ThoroughbredData.trTrackCondition("STANDARD", "標準", "标准", "良");
                condBadgeCol = 0xFF8C5828;
            }
        }

        // 2. Top Header Banner (y = 6..26)
        drawToteQuad(mat, bufferSource, 6, 6, virtualW - 6, 26, 0.003F, panelBgCol);
        int gradeCol = switch (grade.toUpperCase(Locale.ROOT)) {
            case "G1" -> 0xFF2288EE;
            case "G2" -> 0xFFE03070;
            case "G3" -> 0xFF28A848;
            default -> 0xFFC89620;
        };
        drawToteQuad(mat, bufferSource, 8, 8, 38, 24, 0.004F, gradeCol);

        // Status Lamp on top-right (CONFIRMED [4] / INQUIRY [3] / LIVE [2] / ARMED [1] / STANDBY [0])
        int phase = hasRace ? lb.racePhase : 0;
        String statusLampText = switch (phase) {
            case 4 -> ThoroughbredData.tr("CONFIRMED", "確定", "确定", "確定");
            case 3 -> ThoroughbredData.tr("INQUIRY", "審議中", "审议中", "審議中");
            case 2 -> ThoroughbredData.tr("LIVE", "競走中", "比赛进行中", "走行中");
            case 1 -> ThoroughbredData.tr("ARMED", "準備出閘", "准备出闸", "発走準備");
            default -> ThoroughbredData.tr("STANDBY", "待機中", "待机中", "待機中");
        };
        boolean blinkOn = ((mc.level.getGameTime() / 8) % 2 == 0);
        int statusLampCol = switch (phase) {
            case 4 -> 0xFFD82222;
            case 3 -> blinkOn ? 0xFFE68A00 : 0xFFB83222;
            case 2 -> 0xFF1FA640;
            case 1 -> 0xFFD88818;
            default -> 0xFF2B4C66;
        };
        drawToteQuad(mat, bufferSource, virtualW - 68, 8, virtualW - 8, 24, 0.004F, statusLampCol);

        // 3. Sub-Header Strip: Surface | Distance | Track Condition | Course (y = 28..44)
        drawToteQuad(mat, bufferSource, 6, 28, virtualW - 6, 44, 0.003F, 0xFF101A16);
        drawToteQuad(mat, bufferSource, 8, 30, 52, 42, 0.004F, surfaceBadgeCol);
        drawToteQuad(mat, bufferSource, 108, 30, 168, 42, 0.004F, condBadgeCol);

        // 4. Main Results Table (Left/Center: x = 6..206, y = 47..173) — Top 5 Finishers (1st..5th)
        int tableLeft = 6;
        int tableRight = 206;
        int rowTopStart = 48;
        int rowHeight = 24;

        List<ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry> runners = hasRace && lb.runners != null
                ? lb.runners : List.of();
        int totalStalls = hasRace ? Math.max(1, lb.totalStalls) : 8;

        for (int idx = 0; idx < 5; idx++) {
            int ry = rowTopStart + idx * (rowHeight + 1);
            int rowBg = (idx % 2 == 0) ? 0xFF0E1D16 : 0xFF0B1611;
            drawToteQuad(mat, bufferSource, tableLeft, ry, tableRight, ry + rowHeight - 1, 0.003F, rowBg);

            // Placing badge box (1st..5th)
            int placeCol = switch (idx) {
                case 0 -> 0xFFD4AF37; // 1st Gold
                case 1 -> 0xFF88A4B8; // 2nd Silver
                case 2 -> 0xFFB87333; // 3rd Bronze
                default -> 0xFF26382E;
            };
            drawToteQuad(mat, bufferSource, tableLeft + 2, ry + 2, tableLeft + 28, ry + rowHeight - 3, 0.004F, placeCol);

            if (idx < runners.size()) {
                ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(idx);
                int waku = getJraWakuBracket(r.gateNumber, totalStalls);
                int wakuBg = getJraWakuBgColor(waku);
                drawToteQuad(mat, bufferSource, tableLeft + 31, ry + 2, tableLeft + 53, ry + rowHeight - 3, 0.004F, wakuBg);
            } else {
                drawToteQuad(mat, bufferSource, tableLeft + 31, ry + 2, tableLeft + 53, ry + rowHeight - 3, 0.004F, 0xFF18241E);
            }
        }

        // 5. Right Telemetry Column (x = 210..294, y = 48..172)
        int rightColLeft = 210;
        int rightColRight = 294;
        // Digital Clock Box
        drawToteQuad(mat, bufferSource, rightColLeft, 48, rightColRight, 98, 0.003F, panelBgCol);
        drawToteQuad(mat, bufferSource, rightColLeft + 3, 64, rightColRight - 3, 93, 0.004F, 0xFF060A08);

        // Field & Course Summary Box
        drawToteQuad(mat, bufferSource, rightColLeft, 102, rightColRight, 172, 0.003F, panelBgCol);

        // 6. Render All Text Layers Slightly Forward (+0.006F in Z) with Full Bright Light
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.006F);
        Matrix4f textMat = poseStack.last().pose();

        // Grade & Race Name
        drawToteCenteredText(mc.font, textMat, bufferSource, "§l" + grade, 23.0F, 12.0F, 0xFFFFFFFF, fullLight);
        drawToteFittedText(mc.font, textMat, bufferSource, "§l" + raceTitle, 43.0F, 12.0F, (virtualW - 74.0F) - 43.0F, 0xFFFFF4C8, fullLight);
        drawToteCenteredText(mc.font, textMat, bufferSource, "§l" + statusLampText, virtualW - 38.0F, 12.0F, 0xFFFFFFFF, fullLight);

        // Surface, Distance, Condition & Course Name
        drawToteCenteredText(mc.font, textMat, bufferSource, "§l" + surfaceLabel, 30.0F, 32.0F, 0xFFFFFFFF, fullLight);
        drawToteText(mc.font, textMat, bufferSource, "§e§l" + distMeters + "m", 58.0F, 32.0F, 0xFFFFEA55, fullLight);
        drawToteCenteredText(mc.font, textMat, bufferSource, "§l" + condLabel, 138.0F, 32.0F, 0xFFFFFFFF, fullLight);

        String courseShort = trackSummary;
        int idxSurf = courseShort.indexOf(" Turf");
        if (idxSurf < 0) idxSurf = courseShort.indexOf(" Dirt");
        if (idxSurf > 0) courseShort = courseShort.substring(0, idxSurf);
        drawToteFittedText(mc.font, textMat, bufferSource, "§7" + courseShort, 174.0F, 32.0F, (virtualW - 10.0F) - 174.0F, 0xFFCCCCCC, fullLight);

        // Top 5 Placing Rows Text
        float leaderMeters = !runners.isEmpty() ? runners.get(0).raceMeters : 0.0F;
        for (int idx = 0; idx < 5; idx++) {
            int ry = rowTopStart + idx * (rowHeight + 1);
            String placeLabel = switch (idx) {
                case 0 -> ThoroughbredData.tr("1st", "1着", "第1", "1着");
                case 1 -> ThoroughbredData.tr("2nd", "2着", "第2", "2着");
                case 2 -> ThoroughbredData.tr("3rd", "3着", "第3", "3着");
                case 3 -> ThoroughbredData.tr("4th", "4着", "第4", "4着");
                default -> ThoroughbredData.tr("5th", "5着", "第5", "5着");
            };
            int placeTxtCol = (idx == 0) ? 0xFF111111 : 0xFFFFFFFF;
            drawToteCenteredText(mc.font, textMat, bufferSource, "§l" + placeLabel, tableLeft + 15.0F, ry + 8.0F, placeTxtCol, fullLight);

            if (idx < runners.size()) {
                ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(idx);
                int waku = getJraWakuBracket(r.gateNumber, totalStalls);
                int wakuTxt = getJraWakuTextColor(waku);
                drawToteCenteredText(mc.font, textMat, bufferSource, "§l" + r.gateNumber, tableLeft + 42.0F, ry + 8.0F, wakuTxt, fullLight);

                String marginStr = computeToteRowMargin(runners, idx, leaderMeters, phase);
                int marginW = mc.font.width(marginStr);
                int marginCol = (idx == 0) ? 0xFFFFD700 : 0xFF88FFAA;
                drawToteText(mc.font, textMat, bufferSource, marginStr, tableRight - marginW - 4.0F, ry + 8.0F, marginCol, fullLight);

                float maxHorseNameW = Math.max(36.0F, (tableRight - marginW - 8.0F) - (tableLeft + 57.0F));
                int hNameCol = (idx == 0) ? 0xFFFFF088 : 0xFFFFFFFF;
                drawToteFittedText(mc.font, textMat, bufferSource, r.horseName, tableLeft + 57.0F, ry + 8.0F, maxHorseNameW, hNameCol, fullLight);
            } else {
                drawToteCenteredText(mc.font, textMat, bufferSource, "-", tableLeft + 42.0F, ry + 8.0F, 0xFF55665E, fullLight);
                drawToteText(mc.font, textMat, bufferSource, "---", tableLeft + 57.0F, ry + 8.0F, 0xFF44554E, fullLight);
            }
        }

        // Right Column: Digital Race Clock
        String timeHeader = ThoroughbredData.tr("RACE TIME", "走破時間", "比赛用时", "タイム");
        drawToteCenteredText(mc.font, textMat, bufferSource, "§e§l" + timeHeader, (rightColLeft + rightColRight) * 0.5F, 53.0F, 0xFFFFD700, fullLight);

        float displayTimeSec = hasRace ? lb.elapsedSeconds : 0.0F;
        if (!runners.isEmpty() && runners.get(0).finished && runners.get(0).finishTimeSeconds > 0.0F) {
            displayTimeSec = runners.get(0).finishTimeSeconds;
        }
        int mins = (int) (displayTimeSec / 60.0F);
        float secs = displayTimeSec % 60.0F;
        String clockDigits = String.format(Locale.ROOT, "%d:%04.1f", mins, secs);
        poseStack.pushPose();
        poseStack.translate((rightColLeft + rightColRight) * 0.5F, 73.0F, 0.0F);
        poseStack.scale(1.45F, 1.45F, 1.0F);
        drawToteCenteredText(mc.font, poseStack.last().pose(), bufferSource, "§l" + clockDigits, 0.0F, 0.0F, 0xFFFFB81C, fullLight);
        poseStack.popPose();

        // Right Column: Field & Winning Summary
        String runnersHeader = ThoroughbredData.tr("RUNNERS", "出賽馬匹", "出赛马匹", "出走頭数") + ": §f" + runners.size();
        drawToteText(mc.font, textMat, bufferSource, "§7" + runnersHeader, rightColLeft + 6.0F, 108.0F, 0xFFCCCCCC, fullLight);

        String weatherLabel = thundering
                ? ThoroughbredData.tr("STORM", "暴風雨", "暴风雨", "暴風雨")
                : (raining ? ThoroughbredData.tr("RAIN", "雨天", "雨天", "雨") : ThoroughbredData.tr("FINE", "晴朗", "晴朗", "晴"));
        String weatherLine = ThoroughbredData.tr("WEATHER", "天氣", "天气", "天候") + ": §b" + weatherLabel;
        drawToteText(mc.font, textMat, bufferSource, "§7" + weatherLine, rightColLeft + 6.0F, 122.0F, 0xFFCCCCCC, fullLight);

        if (!runners.isEmpty()) {
            ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry leader = runners.get(0);
            String leadTag = (phase >= 3 || leader.finished)
                    ? ThoroughbredData.tr("WINNER", "頭馬", "头马", "勝馬")
                    : ThoroughbredData.tr("LEADER", "領放馬", "领放马", "先頭");
            drawToteText(mc.font, textMat, bufferSource, "§6" + leadTag + ": §f#" + leader.gateNumber, rightColLeft + 6.0F, 138.0F, 0xFFFFD700, fullLight);
            drawToteFittedText(mc.font, textMat, bufferSource, "§e" + leader.horseName, rightColLeft + 6.0F, 152.0F, (rightColRight - rightColLeft) - 10.0F, 0xFFFFEA55, fullLight);
        } else {
            drawToteText(mc.font, textMat, bufferSource, "§8" + ThoroughbredData.tr("Awaiting Race", "等待賽事開始", "等待赛事开始", "レース待機中"), rightColLeft + 6.0F, 142.0F, 0xFF778880, fullLight);
        }

        poseStack.popPose();
        poseStack.popPose();
    }

    private static String computeToteRowMargin(List<ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry> runners,
                                               int idx, float leaderMeters, int phase) {
        ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry r = runners.get(idx);
        if (phase < 2) {
            return "#" + r.gateNumber;
        }
        boolean isDsq = r.finished && (r.finishTimeSeconds <= -1.5F || r.rank >= 100);
        boolean isDnf = !isDsq && r.finished && (r.finishTimeSeconds < 0.0F || r.rank == 99);
        if (isDsq) return "DSQ";
        if (isDnf) return "DNF";

        if (idx == 0) {
            if (r.finished) {
                return ThoroughbredData.tr("WIN", "勝出", "胜出", "1着");
            }
            return ThoroughbredData.tr("LEAD", "領先", "领先", "先頭");
        }

        ModNetwork.RaceLeaderboardSyncPacket.RunnerEntry prev = runners.get(idx - 1);
        float lengths;
        if (r.finished && prev.finished && r.finishTimeSeconds > 0.0F && prev.finishTimeSeconds > 0.0F) {
            float dt = Math.max(0.0F, r.finishTimeSeconds - prev.finishTimeSeconds);
            lengths = dt * 5.5F;
        } else {
            float dm = Math.max(0.0F, prev.raceMeters - r.raceMeters);
            lengths = dm / 2.5F;
        }

        if (lengths < 0.08F) return ThoroughbredData.tr("DH", "平頭", "平头", "同着");
        if (lengths < 0.22F) return ThoroughbredData.tr("Nose", "鼻位", "鼻位", "ハナ");
        if (lengths < 0.42F) return ThoroughbredData.tr("Head", "頭位", "头位", "アタマ");
        if (lengths < 0.70F) return ThoroughbredData.tr("Neck", "頸位", "颈位", "クビ");
        if (lengths < 0.90F) return "1/2";
        if (lengths < 1.15F) return "3/4";
        if (lengths < 1.40F) return "1";
        if (lengths < 1.65F) return "1 1/4";
        if (lengths < 1.90F) return "1 1/2";
        if (lengths < 2.15F) return "1 3/4";
        if (lengths < 2.40F) return "2";
        if (lengths < 2.90F) return "2 1/2";
        if (lengths < 3.40F) return "3";
        if (lengths < 3.90F) return "3 1/2";
        if (lengths < 10.0F) return Math.round(lengths) + "L";
        return ThoroughbredData.tr("Dist", "大差", "大差", "大差");
    }

    private static void drawToteQuad(Matrix4f mat, MultiBufferSource bufferSource,
                                     float x1, float y1, float x2, float y2, float z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int light = LightTexture.FULL_BRIGHT;
        VertexConsumer vc = bufferSource.getBuffer(RenderType.textBackground());
        // Emit both windings so negative Y scale never triggers backface culling
        vc.vertex(mat, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(mat, x1, y2, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(mat, x2, y2, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(mat, x2, y1, z).color(r, g, b, a).uv2(light).endVertex();

        vc.vertex(mat, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(mat, x2, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(mat, x2, y2, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(mat, x1, y2, z).color(r, g, b, a).uv2(light).endVertex();
    }

    private static void drawToteText(Font font, Matrix4f mat, MultiBufferSource bufferSource,
                                     String text, float x, float y, int color, int light) {
        if (text == null || text.isEmpty()) return;
        font.drawInBatch(text, x, y, color, false, mat, bufferSource, Font.DisplayMode.NORMAL, 0, light);
    }

    private static void drawToteCenteredText(Font font, Matrix4f mat, MultiBufferSource bufferSource,
                                             String text, float centerX, float y, int color, int light) {
        if (text == null || text.isEmpty()) return;
        int w = font.width(text);
        font.drawInBatch(text, centerX - (w * 0.5F), y, color, false, mat, bufferSource, Font.DisplayMode.NORMAL, 0, light);
    }

    private static void drawToteFittedText(Font font, Matrix4f mat, MultiBufferSource bufferSource,
                                           String text, float x, float y, float maxWidth, int color, int light) {
        if (text == null || text.isEmpty()) return;
        int w = font.width(text);
        if (w <= maxWidth || maxWidth <= 8.0F) {
            font.drawInBatch(text, x, y, color, false, mat, bufferSource, Font.DisplayMode.NORMAL, 0, light);
        } else {
            float scale = maxWidth / (float) w;
            if (scale >= 0.55F) {
                org.joml.Matrix4f scaledMat = new org.joml.Matrix4f(mat);
                scaledMat.translate(x, y, 0.0F);
                scaledMat.scale(scale, scale, 1.0F);
                scaledMat.translate(-x, -y, 0.0F);
                font.drawInBatch(text, x, y, color, false, scaledMat, bufferSource, Font.DisplayMode.NORMAL, 0, light);
            } else {
                int mid = text.length() / 2;
                int spaceIdx = text.indexOf(' ', Math.max(0, mid - 4));
                if (spaceIdx == -1 || spaceIdx > mid + 6) spaceIdx = text.indexOf(' ');
                String line1, line2;
                if (spaceIdx > 0 && spaceIdx < text.length() - 1) {
                    line1 = text.substring(0, spaceIdx).trim();
                    line2 = text.substring(spaceIdx + 1).trim();
                } else {
                    line1 = text.substring(0, mid).trim();
                    line2 = text.substring(mid).trim();
                }
                if (text.startsWith("§") && text.length() >= 2 && !line2.startsWith("§")) {
                    line2 = text.substring(0, 2) + line2;
                }
                int w1 = font.width(line1);
                int w2 = font.width(line2);
                float maxWLine = Math.max(w1, w2);
                float scale2 = Math.min(1.0F, Math.max(0.48F, maxWidth / Math.max(1.0F, maxWLine)));

                org.joml.Matrix4f sm1 = new org.joml.Matrix4f(mat);
                sm1.translate(x, y - 2.0F, 0.0F);
                sm1.scale(scale2, scale2, 1.0F);
                sm1.translate(-x, -(y - 2.0F), 0.0F);
                font.drawInBatch(line1, x, y - 2.0F, color, false, sm1, bufferSource, Font.DisplayMode.NORMAL, 0, light);

                org.joml.Matrix4f sm2 = new org.joml.Matrix4f(mat);
                sm2.translate(x, y + 7.0F, 0.0F);
                sm2.scale(scale2, scale2, 1.0F);
                sm2.translate(-x, -(y + 7.0F), 0.0F);
                font.drawInBatch(line2, x, y + 7.0F, color, false, sm2, bufferSource, Font.DisplayMode.NORMAL, 0, light);
            }
        }
    }
}