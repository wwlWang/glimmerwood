package cn.wwl.orimod.client.render;
import cn.wwl.orimod.movement.FeatherGlide;
import cn.wwl.orimod.spirit.SpiritData;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GltfAnimation;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** One static feather per glider; visible in first person when looking up, without forcing a camera. */
final class FeatherVisual extends GemRenderEntityVisual<Player> {
    FeatherVisual(VisualizationContext context,Player player,float partial){
        super(context,player,partial,GemRenderModels.handle(ResourceLocation.parse("orimod:models/entity/kuro_feather.glb")));
    }
    @Override protected void transform(Matrix4f target,float partial){
        var at=getVisualPosition(partial);
        target.translation(at);
        var frame=SpiritPlayerVisual.frame(entity);
        if(SpiritData.isSpirit(entity) && frame!=null){
            var a=frame.boneMatrix("grip_l",new Matrix4f());var b=frame.boneMatrix("grip_r",new Matrix4f());
            var centre=new Vector3f((a.m30()+b.m30())*.5f,(a.m31()+b.m31())*.5f,(a.m32()+b.m32())*.5f);
            frame.transform().transformPosition(centre);
            target.translate(centre).rotateY(-Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot)*Mth.DEG_TO_RAD).scale(.85f).translate(0,-.10f,0);
        }else{
            target.rotateY(-Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot)*Mth.DEG_TO_RAD)
                    .translate(0,1.5f,.28f).scale(.85f);
        }
        if(entity.isInvisible() || !FeatherGlide.gliding(entity))target.scale(0);
    }
    @Override protected void animate(float partial,GltfAnimation[] clips,float[] times){clips[0]=null;times[0]=0;}
}
