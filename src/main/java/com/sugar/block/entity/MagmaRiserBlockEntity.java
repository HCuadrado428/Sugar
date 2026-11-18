package com.sugar.block.entity;

import com.sugar.registry.ModBlockEntities;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ciclo del magma: SUBIENDO → ESPERA arriba → BAJANDO → ESPERA abajo → ...
 * Se puede ajustar con /data merge block: HalfSize, MaxRise, RiseInterval, HoldTicks, DrainInterval, IdleTicks.
 */
public class MagmaRiserBlockEntity extends BlockEntity {
    private static final int IDLE = 0, RISING = 1, HOLD = 2, DRAINING = 3;

    private int halfSize = 4;
    private int maxRise = 22;
    private int riseInterval = 30;
    private int holdTicks = 60;
    private int drainInterval = 4;
    private int idleTicks = 100;

    private int height;
    private int phase = IDLE;
    private int timer;

    public MagmaRiserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGMA_RISER.get(), pos, state);
    }

    public void configure(int halfSize, int maxRise, int riseInterval) {
        this.halfSize = halfSize;
        this.maxRise = maxRise;
        this.riseInterval = riseInterval;
        setChanged();
    }

    public void serverTick(ServerLevel world, BlockPos pos) {
        boolean active = TempleSavedData.get(world.getServer()).findAt(world.dimension(), pos)
                .map(d -> d.active).orElse(false);

        if (!active) {
            // Fuera del evento el magma baja poco a poco y se queda quieto
            if (height > 0 && ++timer >= drainInterval) {
                timer = 0;
                setLayer(world, pos, height, false);
                height--;
                setChanged();
            }
            phase = IDLE;
            return;
        }

        timer++;
        switch (phase) {
            case IDLE -> {
                if (timer >= idleTicks) next(RISING);
            }
            case RISING -> {
                if (timer >= riseInterval) {
                    timer = 0;
                    height++;
                    setLayer(world, pos, height, true);
                    if (height % 4 == 0) {
                        world.playSound(null, pos.above(height), SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.6F);
                    }
                    if (height >= maxRise) next(HOLD);
                    setChanged();
                }
            }
            case HOLD -> {
                if (timer >= holdTicks) next(DRAINING);
            }
            case DRAINING -> {
                if (timer >= drainInterval) {
                    timer = 0;
                    setLayer(world, pos, height, false);
                    height--;
                    if (height <= 0) {
                        height = 0;
                        next(IDLE);
                    }
                    setChanged();
                }
            }
            default -> next(IDLE);
        }
    }

    private void next(int newPhase) {
        phase = newPhase;
        timer = 0;
        setChanged();
    }

    private void setLayer(ServerLevel world, BlockPos origin, int layer, boolean fill) {
        if (layer <= 0) return;
        BlockState magma = ModBlocks.RISING_MAGMA.get().defaultBlockState();
        int y = origin.getY() + layer;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                p.set(origin.getX() + dx, y, origin.getZ() + dz);
                BlockState current = world.getBlockState(p);
                if (fill && current.isAir()) {
                    world.setBlock(p, magma, Block.UPDATE_CLIENTS);
                } else if (!fill && current.is(ModBlocks.RISING_MAGMA.get())) {
                    world.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Quita todo el magma de golpe (reset). */
    public void drainAll(ServerLevel world, BlockPos pos) {
        for (int layer = Math.max(height, maxRise); layer >= 1; layer--) {
            setLayer(world, pos, layer, false);
        }
        height = 0;
        phase = IDLE;
        timer = 0;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("HalfSize", halfSize);
        tag.putInt("MaxRise", maxRise);
        tag.putInt("RiseInterval", riseInterval);
        tag.putInt("HoldTicks", holdTicks);
        tag.putInt("DrainInterval", drainInterval);
        tag.putInt("IdleTicks", idleTicks);
        tag.putInt("Level", height);
        tag.putInt("Phase", phase);
        tag.putInt("Timer", timer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("HalfSize")) halfSize = Math.max(0, Math.min(16, tag.getInt("HalfSize")));
        if (tag.contains("MaxRise")) maxRise = Math.max(1, Math.min(64, tag.getInt("MaxRise")));
        if (tag.contains("RiseInterval")) riseInterval = Math.max(1, tag.getInt("RiseInterval"));
        if (tag.contains("HoldTicks")) holdTicks = Math.max(0, tag.getInt("HoldTicks"));
        if (tag.contains("DrainInterval")) drainInterval = Math.max(1, tag.getInt("DrainInterval"));
        if (tag.contains("IdleTicks")) idleTicks = Math.max(0, tag.getInt("IdleTicks"));
        height = tag.getInt("Level");
        phase = tag.getInt("Phase");
        timer = tag.getInt("Timer");
    }
}
