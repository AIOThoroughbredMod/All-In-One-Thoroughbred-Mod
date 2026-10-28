package com.aiothoroughbred.block;

import com.aiothoroughbred.StartingGateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class StartingGateDoorBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final IntegerProperty GATE_NUMBER = IntegerProperty.create("gate_number", 1, 16);
    public static final BooleanProperty TOP_DOOR = BooleanProperty.create("top_door");
    private final boolean isFrontGate;

    // Visual / crosshair selection boxes when closed
    private static final VoxelShape CLOSED_SELECT_NORTH_SOUTH = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape CLOSED_SELECT_EAST_WEST   = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    // Physical collision when closed: 24.0D (1.5 blocks) tall like a Fence Gate so horses (1.0 step height)
    // can never step over the single-block Rear Gate (GRG below GOG), and inset 4..12 across width!
    private static final VoxelShape CLOSED_COLLISION_NORTH_SOUTH = Block.box(4.0D, 0.0D, 6.0D, 12.0D, 24.0D, 10.0D);
    private static final VoxelShape CLOSED_COLLISION_EAST_WEST   = Block.box(6.0D, 0.0D, 4.0D, 10.0D, 24.0D, 12.0D);

    // Open selection hitbox (swung-open side panels so you can always click to close it, with unobstructed center!)
    private static final VoxelShape OPEN_SELECT_NORTH_SOUTH = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 2.5D, 16.0D, 16.0D),
            Block.box(13.5D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
    );
    private static final VoxelShape OPEN_SELECT_EAST_WEST = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.5D),
            Block.box(0.0D, 0.0D, 13.5D, 16.0D, 16.0D, 16.0D)
    );

    public StartingGateDoorBlock(Properties properties, boolean isFrontGate) {
        super(properties);
        this.isFrontGate = isFrontGate;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(GATE_NUMBER, 1)
                .setValue(TOP_DOOR, false));
    }

    public boolean isFrontGate() {
        return isFrontGate;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean isXAxis = (facing.getAxis() == Direction.Axis.X);
        if (state.getValue(OPEN)) {
            return isXAxis ? OPEN_SELECT_EAST_WEST : OPEN_SELECT_NORTH_SOUTH;
        }
        return isXAxis ? CLOSED_SELECT_EAST_WEST : CLOSED_SELECT_NORTH_SOUTH;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty(); // Wide open! Zero collision when open
        }
        Direction facing = state.getValue(FACING);
        return (facing.getAxis() == Direction.Axis.X) ? CLOSED_COLLISION_EAST_WEST : CLOSED_COLLISION_NORTH_SOUTH;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && neighborState.getBlock() instanceof StartingGateDoorBlock) {
            return state.setValue(GATE_NUMBER, neighborState.getValue(GATE_NUMBER))
                    .setValue(OPEN, neighborState.getValue(OPEN))
                    .setValue(FACING, neighborState.getValue(FACING))
                    .setValue(TOP_DOOR, false);
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && this.isFrontGate && level instanceof ServerLevel serverLevel) {
            Player player = (placer instanceof Player p) ? p : null;
            StartingGateManager.onMainGatePlaced(serverLevel, pos, state, player);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && this.isFrontGate && level instanceof ServerLevel serverLevel) {
            StartingGateManager.onMainGateRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        // Allow players to Sneak + Right-Click to place blocks (such as upper gates or the Number Sign) on top of this gate!
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        // Toggle open/close manually (and sync across all Main Gates linked to the same Number Sign + vertical stack)
        if (!level.isClientSide) {
            boolean nextOpen = !state.getValue(OPEN);
            setDoorOpenWithVerticalSync(level, pos, state, nextOpen);
            level.playSound(null, pos, nextOpen ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void setDoorOpenWithVerticalSync(Level level, BlockPos pos, BlockState state, boolean open) {
        setSingleColumnDoorOpen(level, pos, open);

        // Also sync any other MAIN_STARTING_GATE blocks linked to the same Number Sign stall!
        if (level instanceof ServerLevel serverLevel && state.is(ModBlocks.MAIN_STARTING_GATE.get())) {
            List<BlockPos> linked = StartingGateManager.getLinkedGatesForGate(serverLevel, pos);
            for (BlockPos peerPos : linked) {
                if (!peerPos.equals(pos)) {
                    setSingleColumnDoorOpen(level, peerPos, open);
                }
            }
        }
    }

    private static void setSingleColumnDoorOpen(Level level, BlockPos pos, boolean open) {
        BlockState cur = level.getBlockState(pos);
        if (cur.getBlock() instanceof StartingGateDoorBlock) {
            level.setBlock(pos, cur.setValue(OPEN, open), 3);
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos upPos = pos.above(dy);
            BlockState upState = level.getBlockState(upPos);
            if (upState.getBlock() instanceof StartingGateDoorBlock) {
                level.setBlock(upPos, upState.setValue(OPEN, open), 3);
            } else {
                break;
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos downPos = pos.below(dy);
            BlockState downState = level.getBlockState(downPos);
            if (downState.getBlock() instanceof StartingGateDoorBlock) {
                level.setBlock(downPos, downState.setValue(OPEN, open), 3);
            } else {
                break;
            }
        }
    }

    public static void setGateNumberWithVerticalSync(Level level, BlockPos pos, BlockState state, int gateNumber) {
        if (state.getBlock() instanceof StartingGateDoorBlock) {
            level.setBlock(pos, state.setValue(GATE_NUMBER, gateNumber), 3);
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos upPos = pos.above(dy);
            BlockState upState = level.getBlockState(upPos);
            if (upState.getBlock() instanceof StartingGateDoorBlock) {
                level.setBlock(upPos, upState.setValue(GATE_NUMBER, gateNumber), 3);
            } else {
                break;
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos downPos = pos.below(dy);
            BlockState downState = level.getBlockState(downPos);
            if (downState.getBlock() instanceof StartingGateDoorBlock) {
                level.setBlock(downPos, downState.setValue(GATE_NUMBER, gateNumber), 3);
            } else {
                break;
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // If placed directly on top of another StartingGateDoorBlock, inherit its facing, open state, and gate number!
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.getBlock() instanceof StartingGateDoorBlock) {
            return this.defaultBlockState()
                    .setValue(FACING, belowState.getValue(FACING))
                    .setValue(OPEN, belowState.getValue(OPEN))
                    .setValue(GATE_NUMBER, belowState.getValue(GATE_NUMBER))
                    .setValue(TOP_DOOR, false);
        }

        Direction facing = context.getHorizontalDirection().getOpposite();
        int gateNumber = 1;

        // Check perpendicular side (1 or 2 blocks away to account for a shared Frame divider wall)
        Direction right = facing.getClockWise();
        Direction left = facing.getCounterClockWise();

        for (int step = 1; step <= 2; step++) {
            BlockState neighborLeft = level.getBlockState(pos.relative(left, step));
            if (neighborLeft.getBlock() instanceof StartingGateDoorBlock) {
                gateNumber = Math.min(16, neighborLeft.getValue(GATE_NUMBER) + 1);
                break;
            }
            BlockState neighborRight = level.getBlockState(pos.relative(right, step));
            if (neighborRight.getBlock() instanceof StartingGateDoorBlock) {
                gateNumber = Math.max(1, neighborRight.getValue(GATE_NUMBER) - 1);
                break;
            }
        }

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OPEN, false)
                .setValue(GATE_NUMBER, gateNumber)
                .setValue(TOP_DOOR, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, GATE_NUMBER, TOP_DOOR);
    }
}