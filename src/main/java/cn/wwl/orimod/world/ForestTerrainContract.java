package cn.wwl.orimod.world;

import cn.wwl.orimod.mixin.StructureTemplatePaletteAccessor;
import com.google.common.collect.MapMaker;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Derives the new terrain contract from the frozen template; never edits its NBT. */
final class ForestTerrainContract {
    private static final ConcurrentMap<StructureTemplate,ForestTerrainContract> CACHE=new MapMaker().weakKeys().makeMap();
    static final List<BlockPos> LATE=List.of(new BlockPos(10,0,51),new BlockPos(13,0,3),new BlockPos(25,0,3),
            new BlockPos(28,0,51),new BlockPos(29,0,50),new BlockPos(30,0,49),new BlockPos(36,0,22),new BlockPos(37,0,21));
    final RuinTerrainKernel.Contract terrain;
    final Set<BlockPos> land;
    final Map<BlockPos,net.minecraft.world.level.material.Fluid> fluids;
    final List<BlockPos> clearance;
    static ForestTerrainContract of(StructureTemplate template){return CACHE.computeIfAbsent(template,ForestTerrainContract::new);}
    private ForestTerrainContract(StructureTemplate template){
        var palettes=((StructureTemplatePaletteAccessor)template).orimod$palettes();
        if(palettes.size()!=1)throw new IllegalArgumentException("Forest terrain requires its single frozen palette");
        var infos=palettes.getFirst().blocks();var ground=new TreeSet<BlockPos>();var explicit=new HashSet<BlockPos>();
        for(var i:infos){explicit.add(i.pos());if(i.pos().getY()==0&&i.state().is(Blocks.DIRT))ground.add(i.pos());}
        var water=new HashMap<BlockPos,net.minecraft.world.level.material.Fluid>();
        for(var i:infos)if(!i.state().getFluidState().isEmpty())water.put(i.pos(),i.state().getFluidState().getType());
        fluids=Map.copyOf(water);
        land=Set.copyOf(ground);var extra=new ArrayList<BlockPos>();
        for(var p:LATE){if(!land.contains(p))throw new IllegalArgumentException("Missing forest foot "+p);
            for(int y=6;y<template.getSize().getY();y++)if(!explicit.contains(p.atY(y)))extra.add(p.atY(y));}
        clearance=List.copyOf(extra);
        var columns=ground.stream().map(p->new CreekTerrainRules.Ground(p.getX(),p.getZ(),5)).toList();
        var size=template.getSize();int rear=Integer.MAX_VALUE;
        terrain=new RuinTerrainKernel.Contract(size.getX(),size.getY(),size.getZ(),columns,19,2,5,rear,5,5,
                new CreekTerrainRules(size.getX(),size.getZ(),columns,19,2,rear));
    }
    boolean land(BlockPos raw){return land.contains(raw.atY(0));}
}
