package cn.wwl.orimod.item;

import cn.wwl.orimod.world.OriEntities;
import cn.wwl.orimod.world.PracticeTarget;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/** A free practice crystal belongs to the player using the egg, never to a dispenser or spawner. */
public final class PracticeTargetEggItem extends DeferredSpawnEggItem {
    public PracticeTargetEggItem(Properties properties){super(OriEntities.PRACTICE_TARGET,0xAAB8C4,0x9FE8FF,properties);}
    @Override protected DispenseItemBehavior createDispenseBehavior(){return DispenseItemBehavior.NOOP;}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();var pos=context.getClickedPos();
        if(player==null||level.getBlockEntity(pos) instanceof Spawner)return InteractionResult.FAIL;
        if(!(level instanceof ServerLevel server))return InteractionResult.SUCCESS;
        var target=level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()?pos:pos.relative(context.getClickedFace());
        return spawn(server,player,context.getItemInHand(),target,true,!pos.equals(target)&&context.getClickedFace()==Direction.UP)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);var hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.SOURCE_ONLY);
        if(hit.getType()!=HitResult.Type.BLOCK||!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof LiquidBlock))return InteractionResultHolder.pass(stack);
        if(!level.mayInteract(player,hit.getBlockPos())||!player.mayUseItemAt(hit.getBlockPos(),hit.getDirection(),stack))return InteractionResultHolder.fail(stack);
        if(!(level instanceof ServerLevel server))return InteractionResultHolder.success(stack);
        return spawn(server,player,stack,hit.getBlockPos(),false,false)?InteractionResultHolder.consume(stack):InteractionResultHolder.fail(stack);
    }
    private boolean spawn(ServerLevel level,Player player,ItemStack stack,BlockPos pos,boolean offset,boolean down){
        var config=EntityType.<PracticeTarget>createDefaultStackConfig(level,stack,player).andThen(target->target.practiceOwner(player.getUUID()));
        var target=OriEntities.PRACTICE_TARGET.get().spawn(level,config,pos,MobSpawnType.SPAWN_EGG,offset,down);
        if(target==null)return false;
        stack.consume(1,player);player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));level.gameEvent(player,GameEvent.ENTITY_PLACE,target.position());return true;
    }
}
