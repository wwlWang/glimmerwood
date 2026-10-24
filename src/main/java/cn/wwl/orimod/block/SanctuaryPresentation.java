package cn.wwl.orimod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Visual-only fit for an adjacent legacy cave basin/map pair. Never moves either interaction core. */
public final class SanctuaryPresentation {
    private SanctuaryPresentation() {}
    public record Fit(double x,double z,float horizontal,float vertical) {
        public AABB bounds(AABB box) {
            return new AABB(box.minX*horizontal+x,box.minY*vertical,box.minZ*horizontal+z,
                    box.maxX*horizontal+x,box.maxY*vertical,box.maxZ*horizontal+z);
        }
    }
    public static Fit fit(Direction other,boolean shrine) {
        if(other==null)return new Fit(0,0,1,1);
        return shrine?new Fit(-other.getStepX()*.16,-other.getStepZ()*.16,.45f,.68f)
                :new Fit(0,0,.48f,.8f);
    }
}
