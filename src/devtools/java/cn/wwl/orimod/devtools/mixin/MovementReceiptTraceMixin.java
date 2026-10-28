package cn.wwl.orimod.devtools.mixin;

import cn.wwl.orimod.devtools.MovementPacketTrace;
import cn.wwl.orimod.network.MovementResultPayload;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each ordinal observes an actual return, not a replay of the branch predicates. */
@Mixin(value=MovementResultPayload.class,remap=false)
abstract class MovementReceiptTraceMixin {
    @Inject(method="apply",at=@At("HEAD"))
    private void orimod$begin(Player p,CallbackInfo ci) { MovementPacketTrace.begin((MovementResultPayload)(Object)this,p); }
    @Inject(method="apply",at=@At(value="RETURN",ordinal=0))
    private void orimod$identity(Player p,CallbackInfo ci) { MovementPacketTrace.end(p,"ENTITY_OR_DIMENSION"); }
    @Inject(method="apply",at=@At(value="RETURN",ordinal=1))
    private void orimod$cursor(Player p,CallbackInfo ci) { MovementPacketTrace.end(p,"STALE_ACTION"); }
    @Inject(method="apply",at=@At(value="RETURN",ordinal=2))
    private void orimod$inactive(Player p,CallbackInfo ci) { MovementPacketTrace.end(p,"INACTIVE_AFTER_ACK"); }
    @Inject(method="apply",at=@At(value="RETURN",ordinal=3))
    private void orimod$dash(Player p,CallbackInfo ci) { MovementPacketTrace.end(p,"DASH_CONFIRMED"); }
    @Inject(method="apply",at=@At(value="RETURN",ordinal=4))
    private void orimod$lease(Player p,CallbackInfo ci) { MovementPacketTrace.end(p,"KEEP_LOCAL_PREDICTION"); }
    @Inject(method="apply",at=@At(value="RETURN",ordinal=5))
    private void orimod$applied(Player p,CallbackInfo ci) { MovementPacketTrace.end(p,"APPLIED"); }
}
