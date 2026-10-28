package com.aiothoroughbred.block;

import com.aiothoroughbred.AIOThoroughbredMod;
import com.aiothoroughbred.RacingManager;
import com.aiothoroughbred.StartingGateManager;
import com.aiothoroughbred.ThoroughbredData;
import com.aiothoroughbred.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AIOThoroughbredMod.MODID);

    // 1. Starting Gate Frame (Slim divider collision so 1.4m-wide horses fit through 1-block stalls!)
    public static final RegistryObject<Block> STARTING_GATE_FRAME = registerBlock(
            "starting_gate_frame",
            () -> new StartingGateFrameBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    // 2. Green Starting Gate Frame (Green stall frame variant matching racecourse gate superstructure)
    public static final RegistryObject<Block> GREEN_STARTING_GATE_FRAME = registerBlock(
            "green_starting_gate_frame",
            () -> new StartingGateFrameBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    // 3. Starting Gate Separator (Gray horizontal partition between horses & jockeys)
    public static final RegistryObject<Block> STARTING_GATE_SEPARATOR = registerBlock(
            "starting_gate_separator",
            () -> new StartingGateSeparatorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    // 4. Starting Gate Number Sign (Standalone stall number plaque that connects to the Main Gates placed right before it)
    public static final RegistryObject<Block> STARTING_GATE_NUMBER_SIGN = registerBlock(
            "starting_gate_number_sign",
            () -> new StartingGateNumberSignBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    // 5. Main Starting Gate (Front double-doors where horses burst out)
    public static final RegistryObject<Block> MAIN_STARTING_GATE = registerBlock(
            "main_starting_gate",
            () -> new StartingGateDoorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion(), true)
    );

    // 6. Rear Starting Gate (Back door where horses enter to lock in)
    public static final RegistryObject<Block> REAR_STARTING_GATE = registerBlock(
            "rear_starting_gate",
            () -> new StartingGateDoorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion(), false)
    );

    // 7. Starter Console Block (Practice Podium that manages nearby manual stalls)
    public static final RegistryObject<Block> STARTER_CONSOLE = registerBlock(
            "starter_console",
            () -> new StarterConsoleBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F)
                    .sound(SoundType.ANVIL))
    );

    // 8. Racing Console Block (Official Racecourse Builder, Base Types, Temporary 3D Stalls & Race Manager)
    public static final RegistryObject<Block> RACING_CONSOLE = registerBlock(
            "racing_console",
            () -> new RacingConsoleBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.ANVIL))
    );

    // 9. Multi-Block Trackside Results Tote Board (Assemble into any rectangular screen wall to display live race clock, surface/condition, and Top 5 results!)
    public static final RegistryObject<Block> TOTE_BOARD = registerBlock(
            "tote_board",
            () -> new ToteBoardBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(s -> 7))
    );

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> blockSupplier) {
        RegistryObject<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    private static boolean isStartingGateStructureBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof StartingGateFrameBlock
                || b instanceof StartingGateSeparatorBlock
                || b instanceof StartingGateDoorBlock
                || b instanceof StartingGateNumberSignBlock;
    }

    /**
     * Custom Frame Block (Standard & Green):
     * Vanilla horses are ~1.4 blocks wide, so they cannot fit between two full 1x1x1 blocks!
     * - Ground-level frames use a slim 4-pixel center post collision (leaving 1.75 blocks of width clearance between side frames).
     * - Upper/roof frames (placed above air or another gate structure block) have empty physical collision so horses and mounted riders never get wedged!
     */
    public static class StartingGateFrameBlock extends Block {
        private static final VoxelShape SLIM_POST_COLLISION = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 24.0D, 10.0D);

        public StartingGateFrameBlock(Properties properties) {
            super(properties);
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            return Collections.singletonList(new ItemStack(this));
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            BlockState below = level.getBlockState(pos.below());
            if (below.isAir() || isStartingGateStructureBlock(below)) {
                return Shapes.empty();
            }
            return SLIM_POST_COLLISION;
        }
    }

    /**
     * Custom Separator Block:
     * Represents the gray horizontal partition/railings between horses and jockeys inside adjacent stalls.
     * Rotates horizontally with FACING and uses slim horse-safe collision so mounted jockeys and 1.4m-wide horses never get wedged.
     */
    public static class StartingGateSeparatorBlock extends HorizontalDirectionalBlock {
        private static final VoxelShape SELECT_NORTH_SOUTH = Block.box(0.0D, 0.0D, 5.0D, 16.0D, 16.0D, 11.0D);
        private static final VoxelShape SELECT_EAST_WEST   = Block.box(5.0D, 0.0D, 0.0D, 11.0D, 16.0D, 16.0D);
        private static final VoxelShape SLIM_POST_COLLISION = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 24.0D, 10.0D);

        public StartingGateSeparatorBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            Direction facing = state.getValue(FACING);
            return (facing.getAxis() == Direction.Axis.X) ? SELECT_EAST_WEST : SELECT_NORTH_SOUTH;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            BlockState below = level.getBlockState(pos.below());
            if (below.isAir() || isStartingGateStructureBlock(below)) {
                return Shapes.empty();
            }
            return SLIM_POST_COLLISION;
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState below = level.getBlockState(pos.below());
            if (below.getBlock() instanceof StartingGateSeparatorBlock) {
                return this.defaultBlockState().setValue(FACING, below.getValue(FACING));
            }
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            return Collections.singletonList(new ItemStack(this));
        }
    }

    /**
     * Standalone Starting Gate Number Sign Block:
     * - Automatically connects to all MAIN_STARTING_GATE blocks placed right before it (since the previous Number Sign),
     *   forming a proper stall while ignoring gates placed before the previous Number Sign and ignoring gates placed after it.
     * - Sneak + Right-Click cycles the stall number (1..16) and syncs it to all connected MAIN_STARTING_GATE blocks.
     */
    public static class StartingGateNumberSignBlock extends HorizontalDirectionalBlock {
        public static final IntegerProperty GATE_NUMBER = StartingGateDoorBlock.GATE_NUMBER;

        private static final VoxelShape SIGN_SELECT_NORTH_SOUTH = Block.box(2.0D, 0.0D, 5.5D, 14.0D, 12.0D, 10.5D);
        private static final VoxelShape SIGN_SELECT_EAST_WEST   = Block.box(5.5D, 0.0D, 2.0D, 10.5D, 12.0D, 14.0D);

        public StartingGateNumberSignBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(GATE_NUMBER, 1));
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            Direction facing = state.getValue(FACING);
            return (facing.getAxis() == Direction.Axis.X) ? SIGN_SELECT_EAST_WEST : SIGN_SELECT_NORTH_SOUTH;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            Direction facing = context.getHorizontalDirection().getOpposite();

            BlockState below = level.getBlockState(pos.below());
            if (below.hasProperty(FACING)) {
                facing = below.getValue(FACING);
            }

            int nextNum = 1;
            if (level instanceof ServerLevel serverLevel) {
                nextNum = StartingGateManager.suggestNextSignNumber(serverLevel, pos, context.getPlayer());
            } else {
                Direction right = facing.getClockWise();
                Direction left = facing.getCounterClockWise();
                for (int step = 1; step <= 3; step++) {
                    BlockState nL = level.getBlockState(pos.relative(left, step));
                    if (nL.getBlock() instanceof StartingGateNumberSignBlock) {
                        nextNum = Math.min(16, nL.getValue(GATE_NUMBER) + 1);
                        break;
                    }
                    BlockState nR = level.getBlockState(pos.relative(right, step));
                    if (nR.getBlock() instanceof StartingGateNumberSignBlock) {
                        nextNum = Math.max(1, nR.getValue(GATE_NUMBER) - 1);
                        break;
                    }
                }
            }

            return this.defaultBlockState()
                    .setValue(FACING, facing)
                    .setValue(GATE_NUMBER, nextNum);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                Player player = (placer instanceof Player p) ? p : null;
                StartingGateManager.onNumberSignPlaced(serverLevel, pos, state, player);
            }
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (player.isShiftKeyDown()) {
                if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                    int cur = state.getValue(GATE_NUMBER);
                    int next = (cur >= 16) ? 1 : cur + 1;
                    level.setBlock(pos, state.setValue(GATE_NUMBER, next), 3);
                    int linkedCount = StartingGateManager.syncSignNumberToLinkedGates(serverLevel, pos, next);
                    player.displayClientMessage(Component.literal(
                            "§6[Starting Gate] Set to Stall #" + next
                                    + (linkedCount > 0 ? " §7(Synced to " + linkedCount + " Main Gate" + (linkedCount == 1 ? "" : "s") + ")" : "")),
                            true);
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 0.8F, 1.4F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                int cur = state.getValue(GATE_NUMBER);
                int linkedCount = StartingGateManager.getLinkedGatesCount(serverLevel, pos);
                player.displayClientMessage(Component.literal(
                        "§e[Stall #" + cur + " Sign] §7Connected to §f" + linkedCount + " §7Main Gate(s). §8(Sneak + Right-Click to change number)"),
                        true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
                StartingGateManager.onNumberSignRemoved(serverLevel, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, GATE_NUMBER);
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            return Collections.singletonList(new ItemStack(this));
        }
    }

    /**
     * Multi-Block Physical Trackside Results Tote Board:
     * - Place multiple ToteBoardBlocks in a flat vertical wall (e.g., 3x2, 5x3, 7x4, or 1x1) facing the track/spectators.
     * - Automatically connects with adjacent ToteBoardBlocks facing the same direction to render a unified, high-contrast
     *   3D LED Trackside Results Tote Board across the entire multi-block surface!
     * - Sneak + Right-Click cycles the board color theme across the connected wall (Classic Emerald / Midnight Digital / Prestige Gold).
     * - Right-Click syncs with the nearest Racecourse Console and shows the current screen size.
     */
    public static class ToteBoardBlock extends HorizontalDirectionalBlock {
        public static final IntegerProperty THEME = IntegerProperty.create("theme", 0, 2);

        public ToteBoardBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(THEME, 0));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            Direction defaultFacing = context.getHorizontalDirection().getOpposite();
            int theme = 0;

            for (Direction dir : Direction.values()) {
                BlockState neighbor = level.getBlockState(pos.relative(dir));
                if (neighbor.getBlock() instanceof ToteBoardBlock) {
                    defaultFacing = neighbor.getValue(FACING);
                    theme = neighbor.getValue(THEME);
                    break;
                }
            }

            return this.defaultBlockState()
                    .setValue(FACING, defaultFacing)
                    .setValue(THEME, theme);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                Direction facing = state.getValue(FACING);
                List<BlockPos> wall = collectConnectedWall(serverLevel, pos, facing);

                if (player.isShiftKeyDown()) {
                    int nextTheme = (state.getValue(THEME) + 1) % 3;
                    for (BlockPos bp : wall) {
                        BlockState bs = serverLevel.getBlockState(bp);
                        if (bs.getBlock() instanceof ToteBoardBlock) {
                            serverLevel.setBlock(bp, bs.setValue(THEME, nextTheme), 3);
                        }
                    }
                    String themeName = switch (nextTheme) {
                        case 1 -> ThoroughbredData.tr("Midnight Digital LED", "午夜數碼LED", "午夜数码LED", "ミッドナイトLED");
                        case 2 -> ThoroughbredData.tr("Prestige Gold & Obsidian", "尊尚黑金", "尊尚黑金", "プレステージゴールド");
                        default -> ThoroughbredData.tr("Classic Racecourse Emerald", "經典翠綠賽馬場", "经典翠绿赛马场", "クラシックターフグリーン");
                    };
                    sp.displayClientMessage(Component.literal(
                            "§6[" + ThoroughbredData.tr("Trackside Tote Board", "賽果顯示板", "赛果显示板", "着順掲示板") + "] §e"
                                    + ThoroughbredData.tr("Theme:", "主題:", "主题:", "テーマ:") + " §f" + themeName
                                    + " §7(" + wall.size() + " " + ThoroughbredData.tr("blocks", "方塊", "方块", "ブロック") + ")"),
                            true);
                    serverLevel.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 0.8F, 1.3F);
                } else {
                    RacingManager.syncNearestConsoleToToteBoardForPlayer(serverLevel, pos, sp);
                    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                    Direction rightDir = facing.getCounterClockWise();
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    for (BlockPos bp : wall) {
                        int relX = (bp.getX() - pos.getX()) * rightDir.getStepX() + (bp.getZ() - pos.getZ()) * rightDir.getStepZ();
                        if (relX < minX) minX = relX;
                        if (relX > maxX) maxX = relX;
                        if (bp.getY() < minY) minY = bp.getY();
                        if (bp.getY() > maxY) maxY = bp.getY();
                    }
                    int w = Math.max(1, maxX - minX + 1);
                    int h = Math.max(1, maxY - minY + 1);
                    sp.displayClientMessage(Component.literal(
                            "§a[" + ThoroughbredData.tr("Trackside Tote Board", "賽果顯示板", "赛果显示板", "着順掲示板") + "] §f"
                                    + w + "×" + h + " " + ThoroughbredData.tr("Multi-Block Screen Active", "多方塊大螢幕運作中", "多方块大屏幕运行中", "マルチブロックスクリーン稼働中")
                                    + " §8(" + ThoroughbredData.tr("Sneak + Right-Click to change theme", "潛行+右鍵切換主題", "潜行+右键切换主题", "スニーク+右クリックでテーマ切替") + ")"),
                            true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        public static List<BlockPos> collectConnectedWall(BlockGetter level, BlockPos origin, Direction facing) {
            List<BlockPos> result = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            BlockPos immOrigin = origin.immutable();
            queue.add(immOrigin);
            visited.add(immOrigin);

            Direction leftDir = facing.getClockWise();
            Direction rightDir = facing.getCounterClockWise();
            Direction[] planeDirs = new Direction[]{ Direction.UP, Direction.DOWN, leftDir, rightDir };

            while (!queue.isEmpty() && result.size() < 256) {
                BlockPos cur = queue.poll();
                result.add(cur);
                for (Direction d : planeDirs) {
                    BlockPos next = cur.relative(d);
                    if (!visited.contains(next)) {
                        BlockState ns = level.getBlockState(next);
                        if (ns.getBlock() instanceof ToteBoardBlock && ns.getValue(FACING) == facing) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
            return result;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, THEME);
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            return Collections.singletonList(new ItemStack(this));
        }
    }
}