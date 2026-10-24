package cn.wwl.orimod.client.render;
import cn.wwl.orimod.movement.FeatherGlide;
import cn.wwl.orimod.spirit.SpiritData;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GltfAnimation;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** One feather per spirit glider, following the valid rendered two-paw pose. */
final class FeatherVisual extends GemRenderEntityVisual<Player> {
    FeatherVisual(VisualizationContext context,Player player,float partial){
        super(context,player,partial,GemRenderModels.handle(ResourceLocation.parse("orimod:models/entity/kuro_feather.glb")));
    }
    @Override protected void transform(Matrix4f target,float partial){
        var at=getVisualPosition(partial);
        target.translation(at);
        if (entity.isInvisible() || !FeatherGlide.gliding(entity)) { target.scale(0); return; }
        var frame=SpiritPlayerVisual.frame(entity);
        if(SpiritData.isSpirit(entity) && frame!=null){
            var a=frame.boneMatrix("grip_l",new Matrix4f());var b=frame.boneMatrix("grip_r",new Matrix4f());
            var first=frame.transform().transformPosition(a.getTranslation(new Vector3f()));
            var second=frame.transform().transformPosition(b.getTranslation(new Vector3f()));
            // Use the body's rendered facing (including its turning blend), not the raw entity yaw.
            var forward=frame.transform().transformDirection(new Vector3f(0,0,1));
            var pose=FeatherPose.matrix(first,second,forward);
            if(pose==null)target.scale(0);else target.mul(pose);
        }else target.scale(0); // No stale body-position fallback when a pose disappears or the form changes.
    }
    @Override protected void animate(float partial,GltfAnimation[] clips,float[] times){clips[0]=null;times[0]=0;}
}
