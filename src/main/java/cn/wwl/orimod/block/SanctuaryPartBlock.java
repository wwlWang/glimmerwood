package cn.wwl.orimod.block;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Invisible occupancy, with local collision and interaction delegated to exactly one core. No BE. */
public final class SanctuaryPartBlock extends Block {
    public static final EnumProperty<SanctuaryKind> KIND=EnumProperty.create("kind",SanctuaryKind.class);
    // IntegerProperty is unsigned: X/Z encode local -1..1 as 0..2; Y is the actual 0..2 layer.
    public static final IntegerProperty DX=IntegerProperty.create("dx",0,2),DY=IntegerProperty.create("dy",0,2),DZ=IntegerProperty.create("dz",0,2);
    public static BlockPos offset(BlockState s){return new BlockPos(s.getValue(DX)-1,s.getValue(DY),s.getValue(DZ)-1);}
    public SanctuaryPartBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(KIND,SanctuaryKind.WELL).setValue(DX,1).setValue(DY,1).setValue(DZ,1).setValue(SanctuaryBlock.FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(KIND,DX,DY,DZ,SanctuaryBlock.FACING);}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    @Override protected int getLightBlock(BlockState s,BlockGetter l,BlockPos p){return 0;}
    @Override protected boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SanctuaryAssembly.shape(s).outline();}
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SanctuaryAssembly.shape(s).collision();}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){super.onRemove(s,l,p,next,moving);SanctuaryAssembly.removed(s,l,p,next);}
    @Override public ItemStack getCloneItemStack(LevelReader l,BlockPos p,BlockState s){return new ItemStack(s.getValue(KIND).block());}
    @Override protected float getDestroyProgress(BlockState s,Player player,BlockGetter l,BlockPos p){return s.getValue(KIND).block().defaultBlockState().getDestroyProgress(player,l,p);}
    @Override public float getExplosionResistance(BlockState s,BlockGetter l,BlockPos p,Explosion explosion){return s.getValue(KIND).block().defaultBlockState().getExplosionResistance(l,p,explosion);}
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder params){
        var pos=BlockPos.containing(params.getParameter(LootContextParams.ORIGIN));var owner=SanctuaryAssembly.owner(pos,s);
        var actual=SanctuaryAssembly.resolve(params.getLevel(),owner);var core=actual==null?SanctuaryAssembly.coreState(owner):actual;
        return core.getDrops(params.withParameter(LootContextParams.BLOCK_STATE,core)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY,params.getLevel().getBlockEntity(owner.core())));
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        var owner=SanctuaryAssembly.owner(p,s);var core=SanctuaryAssembly.resolve(l,owner);
        return core==null?InteractionResult.PASS:core.useWithoutItem(l,player,new BlockHitResult(hit.getLocation(),hit.getDirection(),owner.core(),hit.isInside()));
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        var owner=SanctuaryAssembly.owner(p,s);var core=SanctuaryAssembly.resolve(l,owner);
        return core==null?ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION:core.useItemOn(stack,l,player,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),owner.core(),hit.isInside()));
    }
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(SanctuaryBlock.FACING,r.rotate(s.getValue(SanctuaryBlock.FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror mirror){
        if(mirror==Mirror.NONE)return s;
        var f=s.getValue(SanctuaryBlock.FACING);var world=SanctuaryShape.rotate(offset(s),f);
        world=new BlockPos(mirror==Mirror.FRONT_BACK?-world.getX():world.getX(),world.getY(),mirror==Mirror.LEFT_RIGHT?-world.getZ():world.getZ());
        var next=mirror.mirror(f);var local=SanctuaryShape.unrotate(world,next);
        return s.setValue(SanctuaryBlock.FACING,next).setValue(DX,local.getX()+1).setValue(DZ,local.getZ()+1);
    }
}
