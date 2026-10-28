package com.aiothoroughbred.item;

import com.aiothoroughbred.AIOThoroughbredMod;
import com.aiothoroughbred.RacingManager;
import com.aiothoroughbred.ThoroughbredData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AIOThoroughbredMod.MODID);

    public static final RegistryObject<Item> THOROUGHBRED_REGISTRY_FORM = ITEMS.register(
            "thoroughbred_registry_form",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final RegistryObject<Item> RACING_CROP = ITEMS.register(
            "racing_crop",
            () -> new RacingCropItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> TRAINER_STOPWATCH = ITEMS.register(
            "trainer_stopwatch",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> GROOMING_BRUSH = ITEMS.register(
            "grooming_brush",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    // Step 11B: Checkpoint Marker (uses custom checkpoint_marker sprite, cycles modes with [M])
    public static final RegistryObject<Item> CHECKPOINT_MARKER = ITEMS.register(
            "checkpoint_marker",
            () -> new CheckpointMarkerItem(new Item.Properties().stacksTo(1))
    );

    // Step 11B: Semaphore Flag (uses custom semaphore_flag sprite, left-click air 3 times to play fanfare & display 3-line Race Logo)
    public static final RegistryObject<Item> SEMAPHORE_FLAG = ITEMS.register(
            "semaphore_flag",
            () -> new SemaphoreFlagItem(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    /**
     * Racing Crop Item:
     * - Cannot break any blocks in Survival or Creative (like a Sword in Creative Mode)
     * - Cannot deal damage or knockback to any mob or horse when left-clicking
     */
    public static class RacingCropItem extends Item {
        public RacingCropItem(Properties properties) {
            super(properties);
        }

        @Override
        public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
            return false;
        }

        @Override
        public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            return true;
        }
    }

    /**
     * Temporary Checkpoint Marker Item:
     * - Given when entering Race Base-Building Mode on a Racing Console.
     * - Press [M] to cycle between:
     *   1. Starting Point Placement Mode
     *   2. Checkpoint Placement Mode
     *   3. Finish Line Placement Mode
     * - Right-click a block to mark positions; Sneak + Left-Click to undo/clear the current mode's points.
     */
    public static class CheckpointMarkerItem extends Item {
        public CheckpointMarkerItem(Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.YELLOW);
        }

        @Override
        public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
            return false;
        }

        @Override
        public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            return true;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            Player player = context.getPlayer();
            if (player == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                RacingManager.handleMarkerRightClickBlock(sp, context.getClickedPos(), context.getClickedFace());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /**
     * Temporary Semaphore Flag Item:
     * - Given when starting a race from the Racing Console.
     * - Left-click the air at least 3 times to trigger the Race Fanfare, 3-line Race Logo banner, and gate countdown!
     */
    public static class SemaphoreFlagItem extends Item {
        public SemaphoreFlagItem(Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }

        @Override
        public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
            return false;
        }

        @Override
        public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            return true;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal(
                        "§e[" + ThoroughbredData.tr("Semaphore Flag", "司閘員信號旗", "司闸员信号旗", "発走委員の赤旗") + "] §f"
                                + ThoroughbredData.tr(
                                        "Left-click the air 3 times to wave the flag and start the race!",
                                        "對空左鍵揮旗 3 次即可奏響號角並開始賽事！",
                                        "对空左键挥旗 3 次即可奏响号角并开始赛事！",
                                        "空中で左クリックを3回行って旗を振り、ファンファーレと発走を開始します！")),
                        true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }
}