package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.CreekSliceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Front-load creek v1 resources before chunk deserialisation or player locate queries.
 * Reload tail is after vanilla clears its template repository, not the earlier datapack event. */
@Mixin(StructureTemplateManager.class)
public abstract class CreekTemplatePrewarmMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void orimod$initialise(CallbackInfo ci){
        CreekSliceSettings.prewarm((StructureTemplateManager)(Object)this,"manager_init");
    }
    @Inject(method="onResourceManagerReload",at=@At("RETURN"))
    private void orimod$reload(CallbackInfo ci){
        CreekSliceSettings.prewarm((StructureTemplateManager)(Object)this,"resource_reload");
    }
}
