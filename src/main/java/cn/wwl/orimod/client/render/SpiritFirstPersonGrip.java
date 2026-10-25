package cn.wwl.orimod.client.render;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import cn.wwl.orimod.item.OriItems;
import org.joml.Vector3f;

/** Contact contract in centred item coordinates, before display transforms. Left hands reflect the complete
 * transformed basis, not a single handle vector. Secondary contacts are reserved for the bow/hammer review. */
final class SpiritFirstPersonGrip {
    enum Kind { EMPTY, BLOCK, ITEM, BLADE, BOW, HAMMER, SPEAR, ARCHITECTURE }
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
        return new Contact(kind,point,axis.normalize(),new Vector3f(0,0,1));
    }
}
