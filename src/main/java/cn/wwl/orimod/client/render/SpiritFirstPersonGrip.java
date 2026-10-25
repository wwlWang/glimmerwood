package cn.wwl.orimod.client.render;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import cn.wwl.orimod.item.OriItems;
import org.joml.Vector3f;

/** Contact contract in centred item coordinates, before display transforms. Left hands reflect the complete
 * transformed basis, not a single handle vector. The bow string contact is driven continuously by SpiritWeaponRig. */
final class SpiritFirstPersonGrip {
    enum Kind { EMPTY, BLOCK, ITEM, BLADE, BOW, HAMMER, SPEAR, ARCHITECTURE }
    static boolean newRig(Kind kind) {
        return kind == Kind.EMPTY || kind == Kind.BLOCK || kind == Kind.BLADE
                || kind == Kind.BOW || kind == Kind.HAMMER || kind == Kind.SPEAR;
    }
    /** Item identity alone is insufficient when the same weapon is also equipped in the off hand. */
    static boolean mainHandView(net.minecraft.world.item.ItemDisplayContext context, net.minecraft.world.entity.HumanoidArm mainArm) {
        return context == (mainArm == net.minecraft.world.entity.HumanoidArm.LEFT
                ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }
    record Contact(Kind kind,Vector3f primary,Vector3f axis,Vector3f palmNormal) { }
    /** Pick the viewer-facing, inner side after the complete item transform. Vanilla's left
     * block display has a half-turn, so mirroring an untransformed point is incorrect. */
    static Vector3f blockSupport(org.joml.Matrix4f itemInView,boolean left) {
        Vector3f best=null;float score=-Float.MAX_VALUE;
        for(var p:new Vector3f[]{new Vector3f(-.52f,.42f,0),new Vector3f(.52f,.42f,0),new Vector3f(0,.42f,-.52f),new Vector3f(0,.42f,.52f)}) {
            var v=itemInView.transformPosition(new Vector3f(p));float candidate=(left?1:-1)*v.x+v.z;
            if(candidate>score){best=p;score=candidate;}
        }
        return best;
    }
    static Contact contact(ItemStack stack) {
        Kind kind=stack.isEmpty()?Kind.EMPTY:stack.is(OriItems.SPIRIT_BLADE)?Kind.BLADE:
                stack.is(OriItems.SPIRIT_HAMMER)?Kind.HAMMER:stack.is(OriItems.SPIRIT_SPEAR)?Kind.SPEAR:
                stack.getUseAnimation()==UseAnim.BOW?Kind.BOW:stack.getItem() instanceof BlockItem block ? (block.getBlock() instanceof cn.wwl.orimod.block.SanctuaryBlock ? Kind.ARCHITECTURE : Kind.BLOCK):Kind.ITEM;
        var point=new Vector3f();var axis=new Vector3f(0,1,0);
        if(!stack.isEmpty())SpiritItemGrip.local(stack,point,axis);
        // Support a cube at its near lower edge, where the paw is visible, rather than buried at its centre.
        if(kind==Kind.BLOCK)point.set(-.52f,.42f,0);
        if(kind==Kind.SPEAR)point.set(SpiritWeaponRig.grip(kind));
        return new Contact(kind,point,axis.normalize(),new Vector3f(0,0,1));
    }
}
