package com.sugar.entity;

import com.sugar.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Roca que cae del techo o del cielo. Su sombra en el suelo sirve de aviso. No coloca bloques. */
public class FallingRock extends Entity {
    public static final BlockState LOOK = Blocks.MAGMA_BLOCK.defaultBlockState();
    private float damage = 7.0F;
    private int life;

    public FallingRock(EntityType<? extends FallingRock> type, Level level) {
        super(type, level);
    }

    /** Crea una roca en (x, y, z) y marca con partículas dónde va a caer. */
    public static void spawn(ServerLevel level, double x, double y, double z, float damage) {
        FallingRock rock = new FallingRock(ModEntities.FALLING_ROCK.get(), level);
        rock.setPos(x, y, z);
        rock.damage = damage;
        level.addFreshEntity(rock);

        BlockPos.MutableBlockPos p = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < 40 && p.getY() > level.getMinBuildHeight(); i++) {
            p.move(0, -1, 0);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, p.getY() + 1.1, z, 12, 0.5, 0.05, 0.5, 0.0);
                break;
            }
        }
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.POINTED_DRIPSTONE_FALL, SoundSource.HOSTILE, 1.5F, 0.5F);
    }

    /** Roca a cierta altura sobre un jugador, sin atravesar el techo. */
    public static void spawnAbove(ServerLevel level, Entity target, int height, float damage) {
        BlockPos base = target.blockPosition();
        int y = base.getY() + 2;
        for (int i = 2; i <= height; i++) {
            if (!level.getBlockState(base.above(i)).isAir()) break;
            y = base.getY() + i;
        }
        spawn(level, target.getX(), y, target.getZ(), damage);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0.0, -0.05, 0.0));
        move(MoverType.SELF, getDeltaMovement());
        if (level().isClientSide) return;

        boolean hitPlayer = !level().getEntitiesOfClass(Player.class, getBoundingBox(), p -> !p.isSpectator()).isEmpty();
        if (hitPlayer || onGround() || ++life > 200) impact();
    }

    private void impact() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(1.5, 0.5, 1.5), p -> !p.isSpectator())) {
            p.hurt(damageSources().fallingBlock(this), damage);
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, LOOK), getX(), getY() + 0.5, getZ(), 30, 0.6, 0.4, 0.6, 0.1);
        sl.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 4, 0.4, 0.2, 0.4, 0.0);
        sl.playSound(null, blockPosition(), SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        damage = tag.getFloat("Damage");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", damage);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
