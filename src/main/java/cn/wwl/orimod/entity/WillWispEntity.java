package cn.wwl.orimod.entity;

import cn.wwl.orimod.progression.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** A persistent personal pickup, never a transferable inventory item or an expiring ItemEntity. */
public final class WillWispEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(WillWispEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(WillWispEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private BlockPos source=BlockPos.ZERO;
    public WillWispEntity(EntityType<? extends WillWispEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(KIND,0);b.define(OWNER,Optional.empty());}
    public WillWisp kind(){return WillWisp.values()[Math.clamp(entityData.get(KIND),0,2)];}
    public UUID owner(){return entityData.get(OWNER).orElse(null);}
    public BlockPos source(){return source;}
    public void bind(cn.wwl.orimod.world.RestorationData.Claim c){setUUID(c.entity());entityData.set(KIND,c.kind().ordinal());entityData.set(OWNER,Optional.of(c.owner()));source=c.source();setPos(source.getX()+.5,source.getY()+1.35,source.getZ()+.5);}
    @Override public void tick(){
        super.tick();setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(level() instanceof ServerLevel server){
            if(!WillWisps.valid(this)){discard();return;}
            var player=server.getServer().getPlayerList().getPlayer(owner());
            if(player!=null && player.level()==level() && player.isAlive() && !player.isSpectator()
                    && player.getBoundingBox().inflate(.75).intersects(getBoundingBox()))WillWisps.pickup(player,this);
        }
    }
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag t){entityData.set(KIND,Math.clamp(t.getInt("kind"),0,2));entityData.set(OWNER,t.hasUUID("owner")?Optional.of(t.getUUID("owner")):Optional.empty());source=BlockPos.of(t.getLong("source"));}
    @Override protected void addAdditionalSaveData(CompoundTag t){t.putInt("kind",kind().ordinal());if(owner()!=null)t.putUUID("owner",owner());t.putLong("source",source.asLong());}
}
