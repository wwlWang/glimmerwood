package cn.wwl.orimod.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

/** Constructor access only; vanilla template behavior is not injected or redirected. */
@Mixin(StructureTemplate.Palette.class)
public interface StructurePaletteInvoker {
    @Invoker("<init>")
    static StructureTemplate.Palette orimod$create(List<StructureTemplate.StructureBlockInfo> blocks) {
        throw new AssertionError("Mixin constructor invoker was not applied");
    }
}
