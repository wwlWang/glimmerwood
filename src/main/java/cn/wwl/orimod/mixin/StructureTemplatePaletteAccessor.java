package cn.wwl.orimod.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

/** Read-only palette access to prepare the creek's local-column index at template load. */
@Mixin(StructureTemplate.class)
public interface StructureTemplatePaletteAccessor {
    @Accessor("palettes") List<StructureTemplate.Palette> orimod$palettes();
}
