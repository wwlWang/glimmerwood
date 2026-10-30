package cn.wwl.orimod.client.render;

/** Item identity alone is insufficient when the same weapon is also equipped in the off hand. */
final class SpiritFirstPersonGrip {
    private SpiritFirstPersonGrip() { }
    static boolean mainHandView(net.minecraft.world.item.ItemDisplayContext context, net.minecraft.world.entity.HumanoidArm mainArm) {
        return context == (mainArm == net.minecraft.world.entity.HumanoidArm.LEFT
                ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }
}
