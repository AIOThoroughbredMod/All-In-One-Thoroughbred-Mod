package com.aiothoroughbred;

import com.aiothoroughbred.block.ModBlocks;
import com.aiothoroughbred.block.StartingGateDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.util.*;

public class StartingGateManager {

    public static class StallData {
        public int gateNumber = 1;
        public BlockPos frontGatePos;
        public BlockPos numberSignPos = null;
        public List<BlockPos> linkedMainGatePositions = new ArrayList<>();
        public BlockPos stallFrontInnerPos;
        public BlockPos stallCenterPos;
        public Direction facing = Direction.NORTH;
        public int assignedHorseId = -1;
        public String assignedHorseName = "";
        public boolean horseInPosition = false;
        public boolean hadRiderWhenLocked = false;

        public StallData(int gateNumber, BlockPos frontGatePos, Direction facing) {
            this.gateNumber = gateNumber;
            this.frontGatePos = frontGatePos;
            this.facing = facing;
            this.linkedMainGatePositions.add(frontGatePos.immutable());
            this.stallFrontInnerPos = frontGatePos.relative(facing.getOpposite(), 1);
            // Set back by 2 blocks behind frontGatePos so its head doesn't clip through the closed front door!
            this.stallCenterPos = frontGatePos.relative(facing.getOpposite(), 2);
        }
    }

    public static class ConsoleSession {
        public ServerLevel level;
        public BlockPos consolePos;
        public List<StallData> stalls = new ArrayList<>();
        public boolean countdownActive = false;
        public int countdownTicks = 0;
        public int initialCountdownTicks = 0;
        public int postBurstCooldownTicks = 0;

        public ConsoleSession(ServerLevel level, BlockPos consolePos) {
            this.level = level;
            this.consolePos = consolePos;
        }

        public StallData getStallByGateNumber(int gateNum) {
            for (StallData s : stalls) {
                if (s.gateNumber == gateNum) return s;
            }
            return null;
        }

        public StallData getStallByFrontPos(BlockPos pos) {
            for (StallData s : stalls) {
                if (s.frontGatePos.equals(pos)) return s;
                if (s.numberSignPos != null && s.numberSignPos.equals(pos)) return s;
            }
            return null;
        }

        public StallData getStallByHorseId(int horseId) {
            for (StallData s : stalls) {
                if (s.assignedHorseId == horseId) return s;
            }
            return null;
        }
    }

    private static final Map<BlockPos, ConsoleSession> SESSIONS = new HashMap<>();
    private static final Map<Integer, Long> WRONG_STALL_WARN_COOLDOWN = new HashMap<>();

    // Sequential placement batching:
    // Tracks MAIN_STARTING_GATE blocks placed since the last STARTING_GATE_NUMBER_SIGN was placed.
    // Once a STARTING_GATE_NUMBER_SIGN is placed, it claims this batch and clears it so:
    //  1) The sign connects ONLY to the main gates placed right before it and ignores any main gates placed after it.
    //  2) Subsequent placed Number Signs ignore all main gates placed before the previous Number Sign.
    private static final Map<UUID, List<BlockPos>> PENDING_MAIN_GATES_BY_PLAYER = new HashMap<>();
    private static final List<BlockPos> PENDING_MAIN_GATES_GLOBAL = new ArrayList<>();

    private static String getDimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static void onMainGatePlaced(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        BlockPos imm = pos.immutable();
        String dim = getDimensionId(level);
        StudbookSavedData.get(level).removeMainGateFromAllLinks(dim, imm);

        if (player != null) {
            List<BlockPos> list = PENDING_MAIN_GATES_BY_PLAYER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
            if (!list.contains(imm)) {
                list.add(imm);
            }
        }
        if (!PENDING_MAIN_GATES_GLOBAL.contains(imm)) {
            PENDING_MAIN_GATES_GLOBAL.add(imm);
        }
    }

    public static void onMainGateRemoved(ServerLevel level, BlockPos pos) {
        BlockPos imm = pos.immutable();
        for (List<BlockPos> list : PENDING_MAIN_GATES_BY_PLAYER.values()) {
            list.remove(imm);
        }
        PENDING_MAIN_GATES_GLOBAL.remove(imm);
        StudbookSavedData.get(level).removeMainGateFromAllLinks(getDimensionId(level), imm);
    }

    public static int suggestNextSignNumber(ServerLevel level, BlockPos pos, @Nullable Player player) {
        Set<Integer> usedNearby = new HashSet<>();
        int r = 24;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (p.equals(pos)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.is(ModBlocks.STARTING_GATE_NUMBER_SIGN.get()) && s.hasProperty(StartingGateDoorBlock.GATE_NUMBER)) {
                        usedNearby.add(s.getValue(StartingGateDoorBlock.GATE_NUMBER));
                    }
                }
            }
        }
        for (int num = 1; num <= 16; num++) {
            if (!usedNearby.contains(num)) {
                return num;
            }
        }
        return 1;
    }

    private static List<BlockPos> findSpatialMainGatesUnderOrBehindSign(ServerLevel level, BlockPos signPos, String dim, StudbookSavedData studbook) {
        List<BlockPos> found = new ArrayList<>();
        // 1. Directly below the sign (dy = 1..4)
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos belowPos = signPos.below(dy);
            if (level.getBlockState(belowPos).is(ModBlocks.MAIN_STARTING_GATE.get())
                    && studbook.findSignForMainGate(dim, belowPos) == null) {
                found.add(belowPos.immutable());
            }
        }
        if (!found.isEmpty()) {
            return found;
        }
        // 2. One block horizontally behind the sign (for signs mounted on the front face of the top middle G frame block!)
        for (Direction horiz : Direction.Plane.HORIZONTAL) {
            BlockPos colTop = signPos.relative(horiz, 1);
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos cand = colTop.below(dy);
                if (level.getBlockState(cand).is(ModBlocks.MAIN_STARTING_GATE.get())
                        && studbook.findSignForMainGate(dim, cand) == null) {
                    found.add(cand.immutable());
                }
            }
            if (!found.isEmpty()) {
                return found;
            }
        }
        return found;
    }

    public static void onNumberSignPlaced(ServerLevel level, BlockPos signPos, BlockState signState, @Nullable Player player) {
        BlockPos immSign = signPos.immutable();
        String dim = getDimensionId(level);
        StudbookSavedData studbook = StudbookSavedData.get(level);

        List<BlockPos> claimedGates = new ArrayList<>();
        List<BlockPos> rawPending = null;
        if (player != null) {
            rawPending = PENDING_MAIN_GATES_BY_PLAYER.get(player.getUUID());
        }
        if (rawPending == null || rawPending.isEmpty()) {
            rawPending = PENDING_MAIN_GATES_GLOBAL;
        }

        if (rawPending != null && !rawPending.isEmpty()) {
            for (BlockPos p : rawPending) {
                if (level.getBlockState(p).is(ModBlocks.MAIN_STARTING_GATE.get())
                        && p.distSqr(immSign) <= 32.0 * 32.0
                        && !claimedGates.contains(p.immutable())) {
                    claimedGates.add(p.immutable());
                }
            }
            // Clear the pending batch immediately so this sign ignores future gates and future signs ignore these gates!
            rawPending.clear();
            PENDING_MAIN_GATES_GLOBAL.removeAll(claimedGates);
            if (player != null) {
                for (List<BlockPos> otherList : PENDING_MAIN_GATES_BY_PLAYER.values()) {
                    otherList.removeAll(claimedGates);
                }
            }
        }

        // Fallback if a player replaces a broken sign directly above or in front of existing unclaimed Main Starting Gates:
        if (claimedGates.isEmpty()) {
            claimedGates.addAll(findSpatialMainGatesUnderOrBehindSign(level, immSign, dim, studbook));
        }

        int gateNum = signState.hasProperty(StartingGateDoorBlock.GATE_NUMBER)
                ? signState.getValue(StartingGateDoorBlock.GATE_NUMBER)
                : 1;

        studbook.setNumberSignLink(dim, immSign, claimedGates);

        // Sync the sign's GATE_NUMBER and align FACING with the linked Main Gates
        Direction gateFacing = null;
        for (BlockPos gatePos : claimedGates) {
            BlockState gState = level.getBlockState(gatePos);
            if (gState.is(ModBlocks.MAIN_STARTING_GATE.get())) {
                if (gateFacing == null && gState.hasProperty(StartingGateDoorBlock.FACING)) {
                    gateFacing = gState.getValue(StartingGateDoorBlock.FACING);
                }
                level.setBlock(gatePos, gState.setValue(StartingGateDoorBlock.GATE_NUMBER, gateNum), 3);
            }
        }
        if (gateFacing != null && signState.hasProperty(StartingGateDoorBlock.FACING)
                && signState.getValue(StartingGateDoorBlock.FACING) != gateFacing) {
            level.setBlock(immSign, signState
                    .setValue(StartingGateDoorBlock.FACING, gateFacing)
                    .setValue(StartingGateDoorBlock.GATE_NUMBER, gateNum), 3);
        }

        if (player != null) {
            if (!claimedGates.isEmpty()) {
                player.displayClientMessage(Component.literal(
                        "§a[Starting Gate] Stall #" + gateNum + " Sign connected to " + claimedGates.size() + " Main Gate(s)!"),
                        true);
            } else {
                player.displayClientMessage(Component.literal(
                        "§e[Starting Gate] Stall #" + gateNum + " Sign placed (Place Main Starting Gates before placing the Sign to link them)."),
                        true);
            }
        }
    }

    public static void onNumberSignRemoved(ServerLevel level, BlockPos signPos) {
        StudbookSavedData.get(level).removeNumberSignLink(getDimensionId(level), signPos.immutable());
    }

    public static int syncSignNumberToLinkedGates(ServerLevel level, BlockPos signPos, int gateNumber) {
        String dim = getDimensionId(level);
        StudbookSavedData studbook = StudbookSavedData.get(level);
        List<BlockPos> linked = new ArrayList<>(studbook.getLinkedGatesForSign(dim, signPos));

        // If no saved links exist yet, check directly below or 1 block behind the sign for unclaimed Main Starting Gates
        if (linked.isEmpty()) {
            linked.addAll(findSpatialMainGatesUnderOrBehindSign(level, signPos, dim, studbook));
            if (!linked.isEmpty()) {
                studbook.setNumberSignLink(dim, signPos.immutable(), linked);
            }
        }

        int updated = 0;
        for (BlockPos gatePos : linked) {
            BlockState gState = level.getBlockState(gatePos);
            if (gState.is(ModBlocks.MAIN_STARTING_GATE.get())) {
                level.setBlock(gatePos, gState.setValue(StartingGateDoorBlock.GATE_NUMBER, gateNumber), 3);
                updated++;
            }
        }
        return updated;
    }

    public static int getLinkedGatesCount(ServerLevel level, BlockPos signPos) {
        List<BlockPos> linked = StudbookSavedData.get(level).getLinkedGatesForSign(getDimensionId(level), signPos);
        int valid = 0;
        for (BlockPos p : linked) {
            if (level.getBlockState(p).is(ModBlocks.MAIN_STARTING_GATE.get())) {
                valid++;
            }
        }
        return valid;
    }

    public static List<BlockPos> getLinkedGatesForGate(ServerLevel level, BlockPos gatePos) {
        String dim = getDimensionId(level);
        StudbookSavedData studbook = StudbookSavedData.get(level);
        BlockPos signPos = studbook.findSignForMainGate(dim, gatePos);
        if (signPos == null) {
            return Collections.emptyList();
        }
        List<BlockPos> all = studbook.getLinkedGatesForSign(dim, signPos);
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos p : all) {
            if (level.getBlockState(p).is(ModBlocks.MAIN_STARTING_GATE.get())) {
                valid.add(p);
            }
        }
        return valid;
    }

    public static void setStallFrontGatesOpen(ServerLevel level, StallData stall, boolean open) {
        if (stall.linkedMainGatePositions != null && !stall.linkedMainGatePositions.isEmpty()) {
            for (BlockPos gatePos : stall.linkedMainGatePositions) {
                BlockState s = level.getBlockState(gatePos);
                if (s.is(ModBlocks.MAIN_STARTING_GATE.get())) {
                    StartingGateDoorBlock.setDoorOpenWithVerticalSync(level, gatePos, s, open);
                }
            }
        } else {
            BlockState frontState = level.getBlockState(stall.frontGatePos);
            if (frontState.is(ModBlocks.MAIN_STARTING_GATE.get())) {
                StartingGateDoorBlock.setDoorOpenWithVerticalSync(level, stall.frontGatePos, frontState, open);
            }
        }
    }

    public static ConsoleSession getOrCreateSession(ServerLevel level, BlockPos consolePos) {
        ConsoleSession session = SESSIONS.computeIfAbsent(consolePos, p -> new ConsoleSession(level, p));
        session.level = level;
        scanNearbyStalls(level, session);
        return session;
    }

    public static ConsoleSession getSession(BlockPos consolePos) {
        return SESSIONS.get(consolePos);
    }

    /**
     * Scans for stalls within 24 blocks of the console:
     * 1) First discovers stalls defined by a STARTING_GATE_NUMBER_SIGN and its connected MAIN_STARTING_GATE blocks.
     * 2) Also discovers any standalone/temporary MAIN_STARTING_GATE stalls not yet claimed by a Number Sign.
     */
    public static void scanNearbyStalls(ServerLevel level, ConsoleSession session) {
        BlockPos center = session.consolePos;
        int r = 24;
        String dim = getDimensionId(level);
        StudbookSavedData studbook = StudbookSavedData.get(level);

        List<StallData> foundStalls = new ArrayList<>();
        Set<BlockPos> claimedGatePositions = new HashSet<>();

        // 1. Scan all STARTING_GATE_NUMBER_SIGN blocks first
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos signPos = center.offset(dx, dy, dz);
                    BlockState signState = level.getBlockState(signPos);
                    if (!signState.is(ModBlocks.STARTING_GATE_NUMBER_SIGN.get())) {
                        continue;
                    }

                    int gateNum = signState.hasProperty(StartingGateDoorBlock.GATE_NUMBER)
                            ? signState.getValue(StartingGateDoorBlock.GATE_NUMBER)
                            : 1;

                    List<BlockPos> savedGates = studbook.getLinkedGatesForSign(dim, signPos);
                    List<BlockPos> validGates = new ArrayList<>();
                    for (BlockPos gp : savedGates) {
                        if (level.getBlockState(gp).is(ModBlocks.MAIN_STARTING_GATE.get())) {
                            validGates.add(gp.immutable());
                        }
                    }

                    // Spatial fallback if a sign was placed in structure/worldgen directly above or 1 block in front of Main Gates
                    if (validGates.isEmpty()) {
                        validGates.addAll(findSpatialMainGatesUnderOrBehindSign(level, signPos, dim, studbook));
                        if (!validGates.isEmpty()) {
                            studbook.setNumberSignLink(dim, signPos.immutable(), validGates);
                        }
                    }

                    if (validGates.isEmpty()) {
                        continue;
                    }

                    // Pick the lowest (ground-level) Main Gate as the stall's primary anchor
                    validGates.sort(Comparator.comparingInt((BlockPos p) -> p.getY())
                            .thenComparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getZ));
                    BlockPos primaryGatePos = validGates.get(0);
                    BlockState primaryState = level.getBlockState(primaryGatePos);
                    Direction facing = primaryState.hasProperty(StartingGateDoorBlock.FACING)
                            ? primaryState.getValue(StartingGateDoorBlock.FACING)
                            : signState.getValue(StartingGateDoorBlock.FACING);

                    // Ensure all connected Main Gates match the Number Sign's gate_number
                    for (BlockPos gp : validGates) {
                        claimedGatePositions.add(gp.immutable());
                        BlockState gs = level.getBlockState(gp);
                        if (gs.is(ModBlocks.MAIN_STARTING_GATE.get())
                                && gs.getValue(StartingGateDoorBlock.GATE_NUMBER) != gateNum) {
                            level.setBlock(gp, gs.setValue(StartingGateDoorBlock.GATE_NUMBER, gateNum), 3);
                        }
                        // Also mark any vertically stacked doors in the same column as claimed
                        for (int v = 1; v <= 3; v++) {
                            if (level.getBlockState(gp.above(v)).is(ModBlocks.MAIN_STARTING_GATE.get())) {
                                claimedGatePositions.add(gp.above(v).immutable());
                            }
                            if (level.getBlockState(gp.below(v)).is(ModBlocks.MAIN_STARTING_GATE.get())) {
                                claimedGatePositions.add(gp.below(v).immutable());
                            }
                        }
                    }

                    StallData existing = session.getStallByFrontPos(primaryGatePos);
                    if (existing == null) {
                        existing = session.getStallByFrontPos(signPos);
                    }
                    StallData stall = new StallData(gateNum, primaryGatePos, facing);
                    stall.numberSignPos = signPos.immutable();
                    stall.linkedMainGatePositions = new ArrayList<>(validGates);
                    if (existing != null) {
                        stall.assignedHorseId = existing.assignedHorseId;
                        stall.assignedHorseName = existing.assignedHorseName;
                        stall.horseInPosition = existing.horseInPosition;
                        stall.hadRiderWhenLocked = existing.hadRiderWhenLocked;
                    }
                    foundStalls.add(stall);
                }
            }
        }

        // 2. Also scan any unclaimed ground-level MAIN_STARTING_GATE blocks
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (claimedGatePositions.contains(p)) continue;

                    BlockState state = level.getBlockState(p);
                    if (state.is(ModBlocks.MAIN_STARTING_GATE.get())) {
                        // Skip upper block if someone stacked 2 Main Starting Gates vertically
                        if (level.getBlockState(p.below()).is(ModBlocks.MAIN_STARTING_GATE.get())) {
                            continue;
                        }

                        Direction facing = state.getValue(StartingGateDoorBlock.FACING);
                        int gateNum = state.hasProperty(StartingGateDoorBlock.GATE_NUMBER)
                                ? state.getValue(StartingGateDoorBlock.GATE_NUMBER)
                                : 1;

                        StartingGateDoorBlock.setGateNumberWithVerticalSync(level, p, state, gateNum);

                        StallData existing = session.getStallByFrontPos(p);
                        StallData stall = new StallData(gateNum, p, facing);
                        if (existing != null) {
                            stall.assignedHorseId = existing.assignedHorseId;
                            stall.assignedHorseName = existing.assignedHorseName;
                            stall.horseInPosition = existing.horseInPosition;
                            stall.hadRiderWhenLocked = existing.hadRiderWhenLocked;
                        }
                        foundStalls.add(stall);
                    }
                }
            }
        }

        // Sort stalls by gate number, then by coordinate so order is deterministic
        foundStalls.sort(Comparator.<StallData>comparingInt(a -> a.gateNumber)
                .thenComparingInt(a -> a.frontGatePos.getX())
                .thenComparingInt(a -> a.frontGatePos.getZ()));

        session.stalls = foundStalls;
    }

    public static List<Horse> findNearbyThoroughbreds(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(24.0, 10.0, 24.0);
        return level.getEntitiesOfClass(Horse.class, box, ThoroughbredData::isThoroughbred);
    }

    public static void assignHorseToStall(ServerLevel level, BlockPos consolePos, int gateNum, int horseId) {
        ConsoleSession session = getOrCreateSession(level, consolePos);
        StallData stall = session.getStallByGateNumber(gateNum);
        if (stall == null) return;

        for (StallData other : session.stalls) {
            if (other.assignedHorseId == horseId) {
                other.assignedHorseId = -1;
                other.assignedHorseName = "";
                other.horseInPosition = false;
                other.hadRiderWhenLocked = false;
            }
        }

        if (horseId == -1) {
            stall.assignedHorseId = -1;
            stall.assignedHorseName = "";
            stall.horseInPosition = false;
            stall.hadRiderWhenLocked = false;
            return;
        }

        Entity e = level.getEntity(horseId);
        if (e instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
            ThoroughbredData data = ThoroughbredData.fromHorse(horse);
            stall.assignedHorseId = horseId;
            stall.assignedHorseName = data.getName();
            stall.horseInPosition = false;
            stall.hadRiderWhenLocked = false;
            session.postBurstCooldownTicks = 0;
        }
    }

    public static void loadHorseIntoStall(ServerLevel level, BlockPos consolePos, int gateNum) {
        ConsoleSession session = getOrCreateSession(level, consolePos);
        StallData stall = session.getStallByGateNumber(gateNum);
        if (stall == null || stall.assignedHorseId == -1) return;

        Entity e = level.getEntity(stall.assignedHorseId);
        if (e instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
            // Close all front gates linked to this stall's Number Sign (and any vertically stacked door blocks)
            setStallFrontGatesOpen(level, stall, false);

            // Close rear gate if present (check 1 to 5 blocks behind stall front)
            setRearGateOpen(level, stall, false);

            // Center horse smoothly inside the stall (set back 2 blocks behind frontGatePos so its head doesn't clip!)
            Vec3 target = Vec3.atBottomCenterOf(stall.stallCenterPos);
            float yaw = stall.facing.toYRot();
            horse.setStanding(false);
            horse.teleportTo(target.x, target.y, target.z);
            horse.setYRot(yaw);
            horse.setYHeadRot(yaw);
            horse.setYBodyRot(yaw);
            horse.setDeltaMovement(0, 0, 0);

            stall.horseInPosition = true;
            stall.hadRiderWhenLocked = (horse.getControllingPassenger() != null);

            ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
            state.inStartingGate = true;
            state.assignedGateNumber = stall.gateNumber;
            state.gateStallPos = stall.frontGatePos;
            state.gateLockedYaw = yaw;
            state.horseYaw = yaw;
            state.prevHorseYaw = yaw;
            state.horseYawInitialized = true;
            state.wHeldBeforeGateOpen = state.pressingW;
            state.penalizedPreWindThisStart = false;
            state.gateRearFreezeTicks = 0;
            state.rearVisualTicks = 0;
            state.gateBreakAccelMultiplier = 1.0;
            state.baseSpeedKmh = 0.0;
            state.displayedSpeedKmh = 0.0;

            level.playSound(null, stall.frontGatePos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.2F);

            if (horse.getControllingPassenger() instanceof ServerPlayer rider) {
                rider.displayClientMessage(Component.literal("§6[Stall #" + stall.gateNumber + " Locked]"), true);
            }
        }
    }

    private static void setRearGateOpen(ServerLevel level, StallData stall, boolean open) {
        for (int dist = 1; dist <= 5; dist++) {
            BlockPos rearPos = stall.frontGatePos.relative(stall.facing.getOpposite(), dist);
            BlockState rearState = level.getBlockState(rearPos);
            if (rearState.is(ModBlocks.REAR_STARTING_GATE.get())) {
                StartingGateDoorBlock.setDoorOpenWithVerticalSync(level, rearPos, rearState, open);
            }
        }
    }

    private static boolean areAllStallsReadyAndMounted(ServerLevel level, ConsoleSession session) {
        int assignedCount = 0;
        int mountedJockeyCount = 0;

        for (StallData s : session.stalls) {
            if (s.assignedHorseId == -1) continue;
            assignedCount++;
            if (!s.horseInPosition) {
                return false;
            }
            Entity e = level.getEntity(s.assignedHorseId);
            if (!(e instanceof Horse horse) || !ThoroughbredData.isThoroughbred(horse)) {
                return false;
            }
            if (horse.getControllingPassenger() instanceof Player) {
                mountedJockeyCount++;
            }
        }

        if (assignedCount == 0) {
            return false;
        }

        MinecraftServer server = level.getServer();
        boolean isSingleplayer = server.isSingleplayer() && server.getPlayerCount() <= 1;
        if (isSingleplayer) {
            return mountedJockeyCount >= 1;
        } else {
            return mountedJockeyCount == assignedCount;
        }
    }

    public static void startCountdown(ServerLevel level, BlockPos consolePos) {
        ConsoleSession session = getOrCreateSession(level, consolePos);
        if (session.countdownActive) return;

        boolean hasHorses = session.stalls.stream().anyMatch(s -> s.assignedHorseId != -1);
        if (!hasHorses) return;

        for (StallData s : session.stalls) {
            if (s.assignedHorseId != -1 && !s.horseInPosition) {
                loadHorseIntoStall(level, consolePos, s.gateNumber);
            }
        }

        if (!areAllStallsReadyAndMounted(level, session)) {
            return;
        }

        RandomSource rng = level.getRandom();
        session.countdownTicks = 60 + rng.nextInt(41); // Silent random 3.0s to 5.0s countdown
        session.initialCountdownTicks = session.countdownTicks;
        session.countdownActive = true;

        level.playSound(null, consolePos, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
        for (StallData s : session.stalls) {
            if (s.assignedHorseId != -1) {
                level.playSound(null, s.frontGatePos, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
            }
        }
    }

    public static void emergencyEject(ServerLevel level, BlockPos consolePos) {
        ConsoleSession session = getOrCreateSession(level, consolePos);
        session.countdownActive = false;
        session.countdownTicks = 0;
        session.postBurstCooldownTicks = 100;

        for (StallData s : session.stalls) {
            setStallFrontGatesOpen(level, s, true);
            setRearGateOpen(level, s, true);

            if (s.assignedHorseId != -1) {
                Entity e = level.getEntity(s.assignedHorseId);
                if (e instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                    horse.setStanding(false);
                    ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                    state.inStartingGate = false;
                    state.wHeldBeforeGateOpen = false;
                    state.penalizedPreWindThisStart = false;
                    state.gateRearFreezeTicks = 0;
                    state.rearVisualTicks = 0;
                    state.gateBreakActive = false;
                    state.gateBreakAccelMultiplier = 1.0;

                    Vec3 exitDir = Vec3.atLowerCornerOf(s.facing.getOpposite().getNormal()).scale(0.35);
                    horse.setDeltaMovement(exitDir.x, 0.1, exitDir.z);

                    if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal("§c§l[Emergency Eject Triggered] §7Gate released."), true);
                    }
                }
                s.horseInPosition = false;
                s.hadRiderWhenLocked = false;
            }
        }

        level.playSound(null, consolePos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8F, 1.5F);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ConsoleSession session : SESSIONS.values()) {
            if (session.level == null) continue;

            if (session.postBurstCooldownTicks > 0) {
                session.postBurstCooldownTicks--;
            }

            // 1. DISMOUNT FAILSAFE
            boolean dismountDetected = false;
            for (StallData s : session.stalls) {
                if (!s.horseInPosition || s.assignedHorseId == -1) continue;
                Entity e = session.level.getEntity(s.assignedHorseId);
                if (!(e instanceof Horse horse) || !horse.isAlive()) {
                    s.horseInPosition = false;
                    s.hadRiderWhenLocked = false;
                    dismountDetected = true;
                    continue;
                }

                boolean currentlyHasRider = (horse.getControllingPassenger() instanceof Player);
                if (s.hadRiderWhenLocked && !currentlyHasRider) {
                    dismountDetected = true;
                    s.horseInPosition = false;
                    s.hadRiderWhenLocked = false;

                    horse.setStanding(false);
                    ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                    state.inStartingGate = false;
                    state.wHeldBeforeGateOpen = false;
                    state.gateBreakActive = false;
                    state.rearVisualTicks = 0;

                    setRearGateOpen(session.level, s, true);
                }
            }

            if (dismountDetected || (session.countdownActive && !areAllStallsReadyAndMounted(session.level, session))) {
                if (session.countdownActive || dismountDetected) {
                    session.countdownActive = false;
                    session.countdownTicks = 0;
                    session.postBurstCooldownTicks = 40;
                    session.level.playSound(null, session.consolePos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.2F, 0.7F);

                    AABB notifyBox = new AABB(session.consolePos).inflate(24.0, 8.0, 24.0);
                    for (ServerPlayer sp : session.level.getEntitiesOfClass(ServerPlayer.class, notifyBox)) {
                        sp.displayClientMessage(Component.literal("§c§l[Starter Aborted] §eJockey dismounted! Gates held closed."), true);
                    }
                }
                continue;
            }

            // 2. STALL ENTRY DETECTION
            if (!session.countdownActive && session.postBurstCooldownTicks == 0) {
                List<Horse> nearbyHorses = findNearbyThoroughbreds(session.level, session.consolePos);

                for (StallData s : session.stalls) {
                    Vec3 stallCenter = Vec3.atBottomCenterOf(s.stallCenterPos);
                    Vec3 stallFrontInner = Vec3.atBottomCenterOf(s.stallFrontInnerPos);

                    for (Horse horse : nearbyHorses) {
                        boolean nearBackCenter = Math.abs(horse.getX() - stallCenter.x) <= 0.45
                                && Math.abs(horse.getZ() - stallCenter.z) <= 0.65
                                && Math.abs(horse.getY() - stallCenter.y) <= 1.5;
                        boolean nearFrontInner = Math.abs(horse.getX() - stallFrontInner.x) <= 0.45
                                && Math.abs(horse.getZ() - stallFrontInner.z) <= 0.65
                                && Math.abs(horse.getY() - stallFrontInner.y) <= 1.5;

                        if (nearBackCenter || nearFrontInner) {
                            if (horse.getId() == s.assignedHorseId) {
                                if (!s.horseInPosition) {
                                    ThoroughbredData hData = ThoroughbredData.fromHorse(horse);
                                    long nowTick = session.level.getGameTime();
                                    long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
                                    if (hData.hasPersonality(ThoroughbredData.PERS_QUIRKY)
                                            && nowTick - lastWarn >= 80
                                            && horse.getRandom().nextDouble() < 0.25) {
                                        WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), nowTick);
                                        BlockPos ejectBlockPos = s.frontGatePos.relative(s.facing.getOpposite(), 4);
                                        Vec3 ejectPos = Vec3.atBottomCenterOf(ejectBlockPos);
                                        horse.teleportTo(ejectPos.x, ejectPos.y, ejectPos.z);
                                        horse.setStanding(true);
                                        ThoroughbredRunEngine.HorseRunState hState = ThoroughbredRunEngine.getOrCreateState(horse);
                                        hState.stopAndResetSpeed();
                                        hState.rearVisualTicks = 14;
                                        session.level.playSound(null, s.frontGatePos, SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.1F, 1.0F);
                                        if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                                            sp.displayClientMessage(Component.literal(
                                                    "§3§l[QUIRKY GATE REFUSAL!] §e" + hData.getName() + " refused to enter Stall #" + s.gateNumber + "! Try again!"), true);
                                        }
                                    } else {
                                        loadHorseIntoStall(session.level, session.consolePos, s.gateNumber);
                                    }
                                }
                            } else {
                                StallData rightStall = session.getStallByHorseId(horse.getId());
                                if (rightStall != null || s.assignedHorseId != -1) {
                                    ejectWrongStallHorse(session.level, s, rightStall, horse);
                                }
                            }
                        }
                    }
                }

                if (areAllStallsReadyAndMounted(session.level, session)) {
                    startCountdown(session.level, session.consolePos);
                }
            }

            // 3. Tick active silent 3-5s countdown
            if (!session.countdownActive) continue;

            session.countdownTicks--;

            if (session.countdownTicks <= 0) {
                session.countdownActive = false;
                burstGatesOpenWithLevel(session.level, session);
            }
        }
    }

    private static void ejectWrongStallHorse(ServerLevel level, StallData wrongStall, StallData rightStall, Horse horse) {
        setRearGateOpen(level, wrongStall, true);

        BlockPos ejectBlockPos = wrongStall.frontGatePos.relative(wrongStall.facing.getOpposite(), 4);
        Vec3 ejectPos = Vec3.atBottomCenterOf(ejectBlockPos);
        horse.setStanding(false);
        horse.teleportTo(ejectPos.x, ejectPos.y, ejectPos.z);

        Vec3 pushBack = Vec3.atLowerCornerOf(wrongStall.facing.getOpposite().getNormal()).scale(0.45);
        horse.setDeltaMovement(pushBack.x, 0.1, pushBack.z);

        ThoroughbredRunEngine.HorseRunState hState = ThoroughbredRunEngine.getOrCreateState(horse);
        hState.stopAndResetSpeed();

        long now = level.getGameTime();
        long lastWarn = WRONG_STALL_WARN_COOLDOWN.getOrDefault(horse.getId(), 0L);
        if (now - lastWarn >= 30) {
            WRONG_STALL_WARN_COOLDOWN.put(horse.getId(), now);

            ThoroughbredData hData = ThoroughbredData.fromHorse(horse);
            String pronoun = "Female".equalsIgnoreCase(hData.getGender()) ? "She" : "He";
            String warnMsg;
            if (rightStall != null) {
                warnMsg = String.format(Locale.ROOT,
                        "§cThis is not %s's stall! %s belongs to Stall #%d!",
                        hData.getName(), pronoun, rightStall.gateNumber);
            } else {
                warnMsg = String.format(Locale.ROOT,
                        "§cThis is not %s's stall! %s has not been assigned to a stall yet!",
                        hData.getName(), pronoun);
            }

            if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal(warnMsg), true);
                sp.sendSystemMessage(Component.literal(warnMsg));
            }
            level.playSound(null, wrongStall.stallCenterPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.85F);
        }
    }

    public static void burstGatesOpenWithLevel(ServerLevel level, ConsoleSession session) {
        session.countdownActive = false;
        session.countdownTicks = 0;
        session.postBurstCooldownTicks = 120;

        for (StallData s : session.stalls) {
            setStallFrontGatesOpen(level, s, true);

            level.playSound(null, s.frontGatePos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4F, 1.2F);
            level.playSound(null, s.frontGatePos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2F, 1.5F);

            level.sendParticles(ParticleTypes.CRIT,
                    s.frontGatePos.getX() + 0.5, s.frontGatePos.getY() + 1.2, s.frontGatePos.getZ() + 0.5,
                    15, 0.4, 0.4, 0.4, 0.2);

            if (s.assignedHorseId != -1) {
                Entity e = level.getEntity(s.assignedHorseId);
                if (e instanceof Horse horse && ThoroughbredData.isThoroughbred(horse)) {
                    ThoroughbredData data = ThoroughbredData.fromHorse(horse);
                    ThoroughbredRunEngine.HorseRunState state = ThoroughbredRunEngine.getOrCreateState(horse);
                    state.inStartingGate = false;
                    state.gateBreakTicks = 0;
                    state.gateBreakAccelMultiplier = 1.0;

                    // Check Temperament Stall Rear Chance when gates burst open!
                    // Gentle: 0%, Normal: 0.3%, Rough: 1%, Temperamental: 5%, Volatile: 10%
                    double rearChance = data.getStartingGateRearChance();
                    if (rearChance > 0.0 && level.getRandom().nextDouble() < rearChance) {
                        state.gateRearFreezeTicks = 25; // 1.25s rear freeze in the stall!
                        state.rearVisualTicks = 22;     // Automatically drops back onto 4 legs before running!
                        state.gateBreakActive = false;
                        state.wHeldBeforeGateOpen = false;
                        state.penalizedPreWindThisStart = true;
                        state.baseSpeedKmh = 0.0;
                        state.displayedSpeedKmh = 0.0;
                        state.gateBreakResult = "REARED IN STALL! (LATE BREAK)";
                        state.gateBreakResultColor = 0xFF3333;
                        state.gateBreakResultTicks = 55;

                        horse.setStanding(true);
                        level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                                SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.2F, 1.0F);

                        if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.literal("§c§l[REARED IN STALL!] §e" + data.getName() + " panicked and reared up at the gate open!"), true);
                            sp.sendSystemMessage(Component.literal("§c§l[STALL REAR!] §e" + data.getName() + " reared in the stall as the gates opened!"));
                        }
                    } else if (state.pressingW || state.wHeldBeforeGateOpen) {
                        // STRICT ANTI-CHEAT: If rider was holding W before/as gates opened, lock movement completely
                        // until W is released and pressed again, and forfeit any gate-break bonus!
                        state.wHeldBeforeGateOpen = true;
                        state.penalizedPreWindThisStart = true;
                        state.gateBreakActive = false;
                        state.baseSpeedKmh = 0.0;
                        state.displayedSpeedKmh = 0.0;

                        if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.literal("§c§l[PRE-WIND DETECTED!] §eRelease [W] and press again to move (No Boost)!"), true);
                        }
                    } else {
                        state.wHeldBeforeGateOpen = false;
                        state.penalizedPreWindThisStart = false;
                        state.gateBreakActive = true;

                        if (horse.getControllingPassenger() instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.literal("§a§l▶▶ GATES OPEN! BREAK NOW! [W] ◀◀"), true);
                        }
                    }
                }
                s.horseInPosition = false;
                s.hadRiderWhenLocked = false;
            }
        }
    }
}