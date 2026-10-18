package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Uses Minecraft's block-cell rotation for both template blocks and continuous route positions. */
public record RuinPlacement(BlockPos origin, Rotation rotation) {
    public RuinPlacement { origin = origin.immutable(); }

    public BlockPos block(BlockPos local) { return origin.offset(local.rotate(rotation)); }
    public Vec3 point(Vec3 local) { return Vec3.atLowerCornerOf(origin).add(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO)); }
    public Vec3 local(Vec3 world) {
        Rotation inverse = switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
        return StructureTemplate.transform(world.subtract(Vec3.atLowerCornerOf(origin)), Mirror.NONE, inverse, BlockPos.ZERO);
    }
    public BoundingBox blocks(BlockPos from, BlockPos to) { return BoundingBox.fromCorners(block(from), block(to)); }
    public AABB clearance(RuinKind.Clearance clearance) {
        var box = blocks(clearance.from(), clearance.to());
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }
    public static Rotation fromFacing(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
    public static Rotation readRotation(String name) {
        // Old structure NBT has no rotation and its shrine palette has the default north state.
        return name.isEmpty() ? Rotation.NONE : Rotation.valueOf(name);
    }
    public static RuinPlacement atShrine(RuinKind kind, BlockPos shrine, BlockState state) {
        Rotation rotation = state.hasProperty(RuinShrineBlock.FACING) ? fromFacing(state.getValue(RuinShrineBlock.FACING)) : Rotation.NONE;
        return new RuinPlacement(shrine.subtract(kind.layout().shrine().rotate(rotation)), rotation);
    }
    public static RuinPlacement atMinimum(RuinKind kind, BlockPos minimum, Rotation rotation) {
        var size = kind.layout().size();
        var zeroBounds = new RuinPlacement(BlockPos.ZERO, rotation).blocks(BlockPos.ZERO, size.offset(-1, -1, -1));
        return new RuinPlacement(minimum.offset(-zeroBounds.minX(), 0, -zeroBounds.minZ()), rotation);
    }
}
