package cn.wwl.orimod.block;

import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * R64 Burrow, in a deliberately safe form (after WotW's sand burrow): only this ruin-placed, unbreakable spirit sand
 * can be burrowed, never ordinary terrain. It is solid for everything except a spirit-form player who knows Water Dash
 * (the same "swim" skill); that player passes through and is steered along the look direction, then pops out with a boost.
 * Collision is decided per entity (like powder snow), so mobs, items and other players still stand on it.
 */
public final class SpiritSandBlock extends ProtectedBlock {
    public static final double SPEED = .42, EXIT_BOOST = .85;

    public SpiritSandBlock(Properties properties) { super(properties); }

    public static boolean canBurrow(Entity entity) {
        return entity instanceof Player player && SpiritData.isSpirit(player) && Ability.WATER_DASH.unlocked(player) && !player.isSpectator();
    }

    /** @return whether the player's body overlaps spirit sand. */
    public static boolean inside(Player player) {
        var box = player.getBoundingBox().deflate(.05);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (player.level().getBlockState(pos).getBlock() instanceof SpiritSandBlock) return true;
        return false;
    }

    public static Vec3 burrowVelocity(Vec3 look) { return look.normalize().scale(SPEED); }
    public static Vec3 exitVelocity(Vec3 look) { var l = look.normalize(); return new Vec3(l.x * EXIT_BOOST, Math.max(.35, l.y * EXIT_BOOST), l.z * EXIT_BOOST); }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && canBurrow(entityContext.getEntity())) return Shapes.empty();
        return Shapes.block();
    }

    @Override protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (canBurrow(entity)) entity.resetFallDistance();
    }

}
