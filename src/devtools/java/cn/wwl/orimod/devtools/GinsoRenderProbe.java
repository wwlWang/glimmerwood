package cn.wwl.orimod.devtools;

import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Diagnostic render-only control: same world/light/camera, no tree model draws. Never packaged in the mod. */
final class GinsoRenderProbe {
    private static final String NAME="orimod-dev-ginso-empty-models",ID="file/"+NAME;
    static void set(Minecraft mc,boolean hide)throws Exception{
        if(hide){
            var root=mc.gameDirectory.toPath().resolve("resourcepacks").resolve(NAME);
            var models=root.resolve("assets/orimod/models/block");var states=root.resolve("assets/orimod/blockstates");
            Files.createDirectories(models);Files.createDirectories(states);
            Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":34,\"description\":\"Development only: hide Ginso model draws for paired frame timing\"}}");
            Files.writeString(models.resolve("ginso_probe_empty.json"),"{\"textures\":{\"particle\":\"orimod:block/ginso_bark\"},\"elements\":[]}");
            for(String name:List.of("ginso_wood","ginso_leaves","ginso_canopy","ginso_leaf_core","ginso_fungus","ginso_vine"))
                Files.writeString(states.resolve(name+".json"),"{\"variants\":{\"\":{\"model\":\"orimod:block/ginso_probe_empty\"}}}");
        }
        var packs=mc.getResourcePackRepository();var selected=new ArrayList<>(packs.getSelectedIds());
        selected.remove(ID);packs.reload();if(hide)selected.add(ID);packs.setSelected(selected);
        // Do not save Options: an interrupted probe must not persist into the next client launch.
        mc.reloadResourcePacks();
    }
}
