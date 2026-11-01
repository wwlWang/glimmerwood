package cn.wwl.orimod.block;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exact health survives unload; DAMAGE is only the visual crack stage. No BE ticker. */
public final class GloomCoreBlockEntity extends BlockEntity {
    private int health=BlightRules.HEALTH;
    long lastRefused=Long.MIN_VALUE;
    private long[] terrain=new long[0];
    private boolean naturalEcology;
    private boolean commandEcology;
    public GloomCoreBlockEntity(BlockPos pos,BlockState state){super(OriBlockEntities.GLOOM_CORE.get(),pos,state);}
    public int health(){return health;}
    public void terrain(long[] positions){terrain=positions.clone();setChanged();}
    public void generatedTerrain(long[] positions){naturalEcology=true;commandEcology=false;terrain(positions);registerLoaded();}
    private void registerLoaded(){if(level instanceof ServerLevel server&&server.getServer().isSameThread()&&terrain.length>0){BlightData.get(server).register(worldPosition,terrain,naturalEcology,!commandEcology);BlightEcology.wake(server,worldPosition);}}
    @Override public void onLoad(){
        super.onLoad();registerLoaded();
        // A feature writes its complete footprint after setBlock. Let that transaction finish first.
        if (terrain.length == 0 && level instanceof ServerLevel server && server.getServer().isSameThread())
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
    public void registerDefaultFootprint() {
        if (!(level instanceof ServerLevel server) || terrain.length != 0 || health <= 0) return;
        var floor = worldPosition.below();
        naturalEcology = true;
        commandEcology = true;
        terrain(new long[]{floor.asLong()});
        var data = BlightData.get(server);
        data.register(worldPosition, terrain, true, false);
        // Seed ownership, not a free terrain conversion: all neighbours use normal protection and budgets.
        data.initializeEcology(worldPosition, java.util.List.of(floor), true);
        BlightEcology.wake(server, worldPosition);
    }
    public boolean hit(ServerPlayer player,BlightRules.Source source){
        if(!(level instanceof ServerLevel server) || health<=0)return false;
        int amount=BlightRules.damage(source);
        if(amount==0){GloomCoreBlock.reject(server,worldPosition,player);return false;}
        registerDefaultFootprint();
        if(terrain.length>0)BlightData.get(server).register(worldPosition,terrain,naturalEcology,!commandEcology);
        health=Math.max(0,health-amount);setChanged();
        if(health==0){
            BlightData.get(server).begin(server,worldPosition);
            server.removeBlock(worldPosition,false);
        }else{
            server.setBlock(worldPosition,getBlockState().setValue(GloomCoreBlock.DAMAGE,BlightRules.crack(health)),2);
            BlightData.get(server).defend(server,worldPosition,player);
        }
        return true;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("health",health);tag.putLongArray("terrain",terrain);if(naturalEcology)tag.putBoolean("natural_ecology",true);if(commandEcology)tag.putBoolean("command_ecology",true);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);health=tag.contains("health")?Math.max(0,Math.min(40,tag.getInt("health"))):40;terrain=tag.getLongArray("terrain");naturalEcology=tag.getBoolean("natural_ecology");commandEcology=tag.getBoolean("command_ecology");}
}
