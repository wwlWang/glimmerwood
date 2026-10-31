package cn.wwl.orimod.gametest;
import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class OutpostGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var rotation:Rotation.values())add(tests,"structure_walkable_"+rotation.name().toLowerCase(),h->{
            var level=h.getLevel();var at=GladesOutpostStructure.placement(h.absolutePos(new BlockPos(12,10,12)),rotation);
            var piece=new GladesOutpostPiece(level.getStructureManager(),at);var box=AABB.of(piece.getBoundingBox()).inflate(2);var context=StructurePieceSerializationContext.fromLevel(level);
            try{
                var first=at.block(GladesOutpostPiece.MERCHANTS.getFirst());var chunk=new ChunkPos(first);var b=piece.getBoundingBox();
                place(h,piece,new BoundingBox(chunk.getMinBlockX(),b.minY(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),b.maxY(),chunk.getMaxBlockZ()));
                piece=new GladesOutpostPiece(level.getStructureManager(),piece.createTag(context));place(h,piece,piece.getBoundingBox());
                var merchants=level.getEntitiesOfClass(GladesMerchant.class,box);h.assertValueEqual(merchants.size(),2,"Generated merchant count");
                var well=at.block(GladesOutpostPiece.WELL);h.assertTrue(level.getBlockState(well).is(OriBlocks.GLADES_WELL),"Missing well");h.assertValueEqual(level.getBlockState(well).getValue(SpiritWellBlock.FACING),rotation.rotate(Direction.NORTH),"Well rotation");
                for(var m:merchants){h.assertTrue(m.isPersistenceRequired(),"Merchant can despawn");h.assertValueEqual(m.well(),well,"Wrong well binding");h.assertTrue(level.noCollision(m),"Merchant embedded in stall");h.assertTrue(GladesOutpostPiece.MERCHANTS.stream().map(at::block).anyMatch(p->p.equals(m.blockPosition())),"Wrong merchant point");}
                for(double height:new double[]{1.2,1.8}){
                    var reachable=walkable(h,at,height);for(var goal:List.of(new BlockPos(7,3,13),new BlockPos(23,3,13),new BlockPos(15,3,18)))h.assertTrue(reachable.contains(goal),"Ground entrance cannot reach "+goal+" at height "+height);
                    // Ground entry remains reachable from one-block lower/higher exterior terrain.
                    for(int dy:new int[]{-1,0,1}){var outside=at.block(new BlockPos(15,3+dy,-1));level.setBlock(outside.below(),Blocks.STONE.defaultBlockState(),18);level.setBlock(outside,Blocks.AIR.defaultBlockState(),18);level.setBlock(outside.above(),Blocks.AIR.defaultBlockState(),18);h.assertTrue(level.noCollision(new AABB(Vec3.atBottomCenterOf(outside).add(-.3,0,-.3),Vec3.atBottomCenterOf(outside).add(.3,height,.3))),"Exterior entry blocked");}
                }
                place(h,piece,piece.getBoundingBox());h.assertValueEqual(level.getEntitiesOfClass(GladesMerchant.class,box).size(),2,"Repeated piece duplicated merchant");merchants.forEach(Entity::discard);
                piece=new GladesOutpostPiece(level.getStructureManager(),piece.createTag(context));place(h,piece,piece.getBoundingBox());h.assertTrue(level.getEntitiesOfClass(GladesMerchant.class,box).isEmpty(),"Save/reload respawned consumed slots");
            }finally{level.getEntitiesOfClass(GladesMerchant.class,box).forEach(Entity::discard);}
        });
        for(var rotation:Rotation.values())for(int delta:new int[]{-7,7})add(tests,"terrace_entrance_"+rotation.name().toLowerCase()+"_"+delta,h->{
            var level=h.getLevel();var at=GladesOutpostStructure.placement(h.absolutePos(new BlockPos(12,30,12)),rotation);
            var piece=new GladesOutpostPiece(level.getStructureManager(),at);var door=new RuinPlacement(at.block(GladesOutpostPiece.ENTRY),rotation);
            int ground=door.origin().getY()+delta;int[] terrain=new int[45];Arrays.fill(terrain,ground);
            var approach=new RuinApproachPiece(door,terrain);var context=StructurePieceSerializationContext.fromLevel(level);
            // Save and reload exactly as a structure-start crossing unloaded chunks does.
            approach=new RuinApproachPiece(approach.createTag(context));
            for(int x=0;x<31;x++)for(int z=0;z<31;z++)level.setBlock(at.block(new BlockPos(x,-8,z)),Blocks.STONE.defaultBlockState(),18);
            for(int x=-2;x<=2;x++)for(int z=-10;z<=-1;z++)for(int y=ground-10;y<ground;y++)level.setBlock(door.block(new BlockPos(x,0,z)).atY(y),Blocks.STONE.defaultBlockState(),18);
            var box=AABB.of(piece.getBoundingBox()).inflate(2);
            try{
                place(h,piece,piece.getBoundingBox());
                approach.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(107),approach.getBoundingBox(),new ChunkPos(door.origin()),BlockPos.ZERO);
                int previous=ground;
                for(int z=-9;z<=0;z++){
                    int feetY=z==-9?ground:z==0?door.origin().getY():RuinSiteRules.stepFloor(ground,door.origin().getY(),z+8)+1;
                    h.assertTrue(Math.abs(feetY-previous)<=1,"Entrance has an unwalkable step at "+z);previous=feetY;
                    var p=door.block(new BlockPos(0,0,z)).atY(feetY);var floor=p.below();
                    h.assertFalse(level.getBlockState(floor).getCollisionShape(level,floor).isEmpty(),"Missing stair footing");
                    for(double height:new double[]{1.2,1.8}){var feet=Vec3.atBottomCenterOf(p);h.assertTrue(level.noCollision(new AABB(feet.add(-.3,.001,-.3),feet.add(.3,height,.3))),"Entrance headroom blocked at "+z);}
                }
                for(int depth=1;depth<8;depth++)h.assertTrue(level.getBlockState(at.block(new BlockPos(15,-depth,15))).is(Blocks.DIRT),"Unsupported terrace foundation");
                var reachable=walkable(h,at,1.8);for(var goal:List.of(new BlockPos(7,3,13),new BlockPos(23,3,13),new BlockPos(15,3,18)))h.assertTrue(reachable.contains(goal),"Adapted entrance cannot reach service");
            }finally{level.getEntitiesOfClass(GladesMerchant.class,box).forEach(Entity::discard);}
        });
        add(tests,"biome_contract_excludes_visually_incompatible_sites",h->{
            var registry=h.getLevel().registryAccess();var biomes=registry.registryOrThrow(Registries.BIOME);
            var structure=registry.registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("orimod:glades_outpost"));
            for(String id:List.of("desert","badlands","wooded_badlands","eroded_badlands","ice_spikes","mushroom_fields"))h.assertFalse(structure.biomes().contains(biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,ResourceLocation.withDefaultNamespace(id)))),"Forbidden outpost biome: "+id);
            for(String id:List.of("forest","taiga","savanna","jungle","plains","snowy_plains"))h.assertTrue(structure.biomes().contains(biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,ResourceLocation.withDefaultNamespace(id)))),"Missing intended outpost biome: "+id);
        });
        add(tests,"well_refill_discovery_and_ruin_round_trip",h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var p=c.player();var level=h.getLevel();var a=h.absolutePos(new BlockPos(5,2,7));var b=a.east(8);
                level.setBlock(a,OriBlocks.GLADES_WELL.get().defaultBlockState(),18);level.setBlock(b,OriBlocks.FOREST_SHRINE.get().defaultBlockState(),18);
                for(var well:List.of(a,b)){level.setBlock(well.north().below(),Blocks.STONE.defaultBlockState(),18);level.setBlock(well.north(),Blocks.AIR.defaultBlockState(),18);level.setBlock(well.north().above(),Blocks.AIR.defaultBlockState(),18);}
                p.setPos(Vec3.atBottomCenterOf(a.north()));p.setHealth(4);p.setData(SpiritData.ENERGY,2f);
                level.getBlockState(a).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(a),Direction.NORTH,a,false));
                h.assertValueEqual(p.getHealth(),p.getMaxHealth(),"Well did not heal");h.assertValueEqual(SpiritProgression.energy(p),SpiritProgression.maxEnergy(p),"Well did not recharge");
                String key="discover/"+ChallengeSession.key(level.dimension(),a);h.assertTrue(p.getData(SpiritData.PROGRESS).rewards().containsKey(key),"Outpost not discovered");
                var rewards=new HashMap<>(p.getData(SpiritData.PROGRESS).rewards());rewards.put("discover/"+ChallengeSession.key(level.dimension(),b),1);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withRewards(rewards));
                h.assertTrue(SpiritWell.travel(p,a),"Outpost to ruin travel failed");h.assertValueEqual(p.blockPosition(),b.north(),"Ruin arrival");h.assertFalse(SpiritWell.travel(p,b),"Travel ignored the R113 anti-repeat cooldown");
                p.getData(SpiritData.COOLDOWNS).wellTravelReadyAt=level.getGameTime();
                h.assertTrue(SpiritWell.travel(p,b),"Ruin to outpost travel failed after cooldown");h.assertValueEqual(p.blockPosition(),a.north(),"Outpost arrival");
            }
        });
        add(tests,"outpost_entry_lookup_uses_loaded_references_only",h->{
            var level=h.getLevel();var at=GladesOutpostStructure.placement(h.absolutePos(new BlockPos(15,10,15)),Rotation.NONE);var piece=new GladesOutpostPiece(level.getStructureManager(),at);
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("orimod:glades_outpost"));var origin=level.getChunkAt(at.origin());var point=at.block(new BlockPos(28,3,28));var current=level.getChunkAt(point);
            var old=new HashMap<>(origin.getAllStarts());var refs=new HashMap<net.minecraft.world.level.levelgen.structure.Structure,it.unimi.dsi.fastutil.longs.LongSet>();current.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
            try{origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(piece))));current.addReferenceForStructure(structure,origin.getPos().toLong());h.assertTrue(GladesOutpostPiece.at(level,point)==piece,"Reference entry missed");origin.setAllStarts(old);current.setAllReferences(new HashMap<>());var far=new ChunkPos(1_000_000,1_000_000);current.addReferenceForStructure(structure,far.toLong());h.assertTrue(GladesOutpostPiece.at(level,point)==null,"Invented start");h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null,"Lookup loaded missing start");}finally{origin.setAllStarts(old);current.setAllReferences(refs);}
        });
        add(tests,"worldgen_proto_attachment_promotes_to_level_chunk",h->{
            var level=h.getLevel();var pos=new ChunkPos(500000,500000);
            var proto=new net.minecraft.world.level.chunk.ProtoChunk(pos,net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
            var tree=pos.getWorldPosition().atY(80);proto.setData(FeatureSites.TREES,List.of(tree.asLong()));proto.setData(FeatureSites.BLIGHTS,List.of(tree.east().asLong()));
            var promoted=new net.minecraft.world.level.chunk.LevelChunk(level,proto,null);
            h.assertValueEqual(promoted.getData(FeatureSites.TREES),List.of(tree.asLong()),"Promotion lost tree journal");h.assertValueEqual(promoted.getData(FeatureSites.BLIGHTS),List.of(tree.east().asLong()),"Promotion lost blight journal");
            h.assertTrue(level.getChunkSource().getChunkNow(pos.x,pos.z)==null,"Isolated promotion loaded chunk");
        });
        add(tests,"chunk_journal_survives_nbt_then_merges_once",h->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(10,2,10));var chunk=level.getChunkAt(at);var old=FeatureSites.get(level);var oldTrees=chunk.getData(FeatureSites.TREES);var oldBlock=level.getBlockState(at);
            try{level.setBlock(at,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),18);FeatureSites.generated(level,at,FeatureSites.Kind.TREE);var tag=chunk.writeAttachmentsToNBT(level.registryAccess());h.assertTrue(tag!=null,"Journal not serialized");chunk.removeData(FeatureSites.TREES);chunk.readAttachmentsFromNBT(level.registryAccess(),tag);h.assertTrue(chunk.getData(FeatureSites.TREES).contains(at.asLong()),"Journal lost at NBT boundary");
                var fresh=new FeatureSites();level.getDataStorage().set("orimod_feature_sites",fresh);var event=new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk,false);FeatureSites.loadChunk(event);FeatureSites.loadChunk(event);FeatureSites.merge(level);h.assertValueEqual(fresh.nearest(level,FeatureSites.Kind.TREE,Vec3.atCenterOf(at),p->true).orElseThrow(),at,"Load did not merge");int count=fresh.count(FeatureSites.Kind.TREE);FeatureSites.merge(level);h.assertValueEqual(fresh.count(FeatureSites.Kind.TREE),count,"Duplicate journal merged twice");
            }finally{chunk.setData(FeatureSites.TREES,oldTrees);level.setBlock(at,oldBlock,18);level.getDataStorage().set("orimod_feature_sites",old);}
        });
        return tests;
    }
    /** Ground-only BFS uses real collision shapes, a .6-wide body and independent human/spirit headroom. */
    private static Set<BlockPos> walkable(GameTestHelper h,RuinPlacement at,double height){
        var seen=new HashSet<BlockPos>();var todo=new ArrayDeque<BlockPos>();todo.add(GladesOutpostPiece.ENTRY);seen.add(GladesOutpostPiece.ENTRY);
        while(!todo.isEmpty()){var p=todo.removeFirst();for(var direction:Direction.Plane.HORIZONTAL){var next=p.relative(direction);if(next.getX()<0 || next.getX()>30 || next.getZ()<0 || next.getZ()>30 || seen.contains(next))continue;var world=at.block(next);var feet=Vec3.atBottomCenterOf(world);var box=new AABB(feet.add(-.3,.001,-.3),feet.add(.3,height,.3));var floor=world.below();if(h.getLevel().getBlockState(floor).isFaceSturdy(h.getLevel(),floor,Direction.UP) && h.getLevel().noCollision(box)){seen.add(next);todo.addLast(next);}}}
        return seen;
    }
    private static void place(GameTestHelper h,GladesOutpostPiece piece,BoundingBox bounds){var l=h.getLevel();piece.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(107),bounds,new ChunkPos(piece.placement().origin()),BlockPos.ZERO);}
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run){tests.add(new TestFunction("outpost","outpost."+name,"orimod_gametest:platform_room",100,0,true,h->{run.accept(h);h.succeed();}));}
}
