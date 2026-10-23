package cn.wwl.orimod.world;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.util.Locale;

public final class GreatTreeCommands {
    private GreatTreeCommands(){}
    public static void register(com.mojang.brigadier.tree.CommandNode<CommandSourceStack> root){
        root.addChild(Commands.literal("great_tree").requires(s->s.hasPermission(2))
                .then(Commands.literal("site").executes(c->report(c.getSource())))
                .then(Commands.literal("repick").executes(c->{
                    var s=c.getSource();var level=s.getServer().overworld();var data=GreatTreeSite.get(level);
                    if(data.begun()){s.sendFailure(Component.translatable("commands.orimod.great_tree.started"));return 0;}
                    if(!GreatTreeSites.choose(level,(int)level.getGameTime())){s.sendFailure(Component.translatable("commands.orimod.great_tree.none"));return 0;}
                    return report(s);
                }))
                .then(Commands.literal("force_place").executes(c->{
                    var s=c.getSource();var level=s.getServer().overworld();var data=GreatTreeSite.get(level);
                    if(data.site()==null && !GreatTreeSites.choose(level,0)){s.sendFailure(Component.translatable("commands.orimod.great_tree.none"));return 0;}
                    // Generate the reserved chunks through normal worldgen, never stamp over existing terrain/builds.
                    var p=data.site().origin();int r=GreatTreeShape.radius(data.site().shapeVersion());
                    for(int x=(p.getX()-r)>>4;x<=(p.getX()+r)>>4;x++)for(int z=(p.getZ()-r)>>4;z<=(p.getZ()+r)>>4;z++)level.getChunk(x,z);
                    return report(s);
                })).build());
    }
    private static int report(CommandSourceStack s){
        var level=s.getServer().overworld();var data=GreatTreeSite.get(level);var site=data.site();
        if(site==null){s.sendFailure(Component.translatable("commands.orimod.great_tree.none"));return 0;}
        var timings=GreatTreeSites.timings(level);double worst=timings.stream().mapToDouble(GreatTreeSites.Timing::writeMs).max().orElse(0);
        s.sendSuccess(()->Component.translatable("commands.orimod.great_tree.site",site.origin().toShortString(),
                site.spawn().toShortString(),String.format(Locale.ROOT,"%.1f",site.spawnDistance()),data.begun(),timings.size(),String.format(Locale.ROOT,"%.2f",worst)),false);
        return 1;
    }
}
