package net.shattergui.fly;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/** The block or entity a screen was opened from: where the GUI flies out of and back into. */
public abstract sealed class FlySource {
    /** Where the GUI flies to and from, now. */
    public abstract Vec3 point();

    /** False once the block is gone or the entity is dead or unloaded. */
    public abstract boolean isValid();

    /** For the profile log. */
    public abstract String describe();

    /** For thematic rules: "block:<id>" or "entity:<id>". */
    public abstract String themeKey();

    public static FlySource of(Level level, BlockPos pos) {
        return new BlockSource(level, pos.immutable(), level.getBlockState(pos).getBlock());
    }

    public static FlySource of(Entity entity) {
        return new EntitySource(entity);
    }

    static final class BlockSource extends FlySource {
        /** How high up the block a chest's lid is (the chest model is 14/16 high). */
        private static final double CHEST_LID = 0.8;

        private final Level level;
        private final BlockPos pos;
        private final Block block;

        BlockSource(Level level, BlockPos pos, Block block) {
            this.level = level;
            this.pos = pos;
            this.block = block;
        }

        @Override
        public Vec3 point() {
            BlockState state = level.getBlockState(pos);
            Vec3 center = Vec3.atCenterOf(pos);
            if (!(state.getBlock() instanceof AbstractChestBlock<?>)) {
                return center;
            }
            // Under the lid; for a double chest, between the two halves.
            Vec3 lid = new Vec3(center.x, pos.getY() + CHEST_LID, center.z);
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                Direction other = ChestBlock.getConnectedDirection(state);
                lid = lid.add(other.getStepX() * 0.5, 0, other.getStepZ() * 0.5);
            }
            return lid;
        }

        @Override
        public boolean isValid() {
            return level == Minecraft.getInstance().level && level.getBlockState(pos).getBlock() == block;
        }

        @Override
        public String describe() {
            return "block " + block.getDescriptionId() + " at " + pos.toShortString();
        }

        @Override
        public String themeKey() {
            return "block:" + BuiltInRegistries.BLOCK.getKey(block);
        }
    }

    static final class EntitySource extends FlySource {
        private final Entity entity;

        EntitySource(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Vec3 point() {
            return entity.getBoundingBox().getCenter();
        }

        @Override
        public boolean isValid() {
            return entity.isAlive() && !entity.isRemoved() && entity.level() == Minecraft.getInstance().level;
        }

        @Override
        public String describe() {
            return "entity " + entity.getType().getDescriptionId() + " at " + entity.blockPosition().toShortString();
        }

        @Override
        public String themeKey() {
            return "entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        }
    }
}
