package cn.wwl.orimod.movement;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Spirit-only feather. Human and third-party elytra retain vanilla movement, rockets and durability. */
public final class FeatherGlide {
    private FeatherGlide() {}
    public static final class State {
        public long migrationUntil = Long.MIN_VALUE / 2;
        public boolean heldDuringHandoff, fireworkNotified;
        public String dimension = "";
    }
    public static boolean wearing(Player p) { return p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA); }
    /** Equipment is an alternative to buying Glide, not a grant of its saved ability bit. */
    public static boolean available(Player p) {
        return FeatherRules.permitted(SpiritData.isSpirit(p), SpiritData.hasGlide(p), wearing(p), p.getAbilities().flying);
    }
    public static boolean permitted(Player p) {
        return (available(p) || migrating(p))
                && p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping()
                && !cn.wwl.orimod.block.OriFluids.inWater(p) && !p.isInLava() && !p.onClimbable()
                && !(p instanceof ServerPlayer s && cn.wwl.orimod.progression.Restoration.active(s));
    }
    public static boolean migrating(Player p) {
        return SpiritData.isSpirit(p) && wearing(p) && !p.onGround() && !p.getAbilities().flying
                && (p.level().isClientSide ? p.getData(SpiritData.FEATHER_HANDOFF)
                    : p.level().getGameTime() < p.getData(SpiritData.FEATHER_STATE).migrationUntil);
    }
    public static boolean gliding(Player p) {
        return permitted(p) && (p.getData(SpiritData.FEATHER_GLIDING)
                || p.level().isClientSide && p.getData(SpiritData.MOVEMENT).mode == MoveMode.GLIDE);
    }
    public static Vec3 velocity(Player p, Vec3 wish) {
        Vec3 v = FeatherRules.velocity(p.getDeltaMovement(), wish, wearing(p),
                cn.wwl.orimod.progression.Shard.AIR_CONTROL.equipped(p));
        if (cn.wwl.orimod.block.UpdraftBlock.lifting(p.level(), p)) v = new Vec3(v.x, MovementRules.updraftVelocity(v.y), v.z);
        return v;
    }
    /** A 2-second takeover gives the falling player time to hold jump. No skill is permanently granted. */
    public static void migrate(PlayerTickEvent.Pre event) { startHandoff(event.getEntity()); }
    public static void startHandoff(Player p) {
        if (!SpiritData.isSpirit(p) || !wearing(p) || !p.isFallFlying()) return;
        p.stopFallFlying();
        if (!p.getAbilities().flying && !p.onGround()) {
            State f=p.getData(SpiritData.FEATHER_STATE);
            f.migrationUntil=p.level().getGameTime()+FeatherRules.MIGRATION_TICKS; f.heldDuringHandoff=false;
            p.setData(SpiritData.FEATHER_HANDOFF,true);p.setData(SpiritData.FEATHER_GLIDING,true);
            var s=p.getData(SpiritData.MOVEMENT);s.mode=MoveMode.GLIDE;s.activeTicks=MovementRules.GLIDE_LEASE_TICKS;
            // Preserve all velocity; FeatherRules decays surplus flight speed over subsequent ticks.
            p.fallDistance=0;
        }
    }
    public static void stopHandoff(Player p) {
        p.getData(SpiritData.FEATHER_STATE).migrationUntil=Long.MIN_VALUE/2;
        if(p.getData(SpiritData.FEATHER_HANDOFF))p.setData(SpiritData.FEATHER_HANDOFF,false);
    }
    /** Uses the existing movement input stream; a deliberate release/crouch/menu ends the grace immediately. */
    public static void input(Player p,boolean active,boolean jump,boolean crouch) {
        if(!migrating(p))return;
        State f=p.getData(SpiritData.FEATHER_STATE);
        if(!active || crouch || f.heldDuringHandoff && !jump)stopHandoff(p);
        if(jump)f.heldDuringHandoff=true;
    }
    public static void formChanged(ServerPlayer p) {
        if(SpiritData.isSpirit(p)){startHandoff(p);return;}
        stopHandoff(p);p.setData(SpiritData.FEATHER_GLIDING,false);
        var s=p.getData(SpiritData.MOVEMENT);
        if(s.mode==MoveMode.GLIDE){s.mode=MoveMode.IDLE;s.activeTicks=0;s.active=false;}
        // Never start FallFlying on the way back. Vanilla's next deliberate jump may start it.
    }
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        if (p.level().getGameTime() % 20 == 0) offerTutorial(p);
        State f=p.getData(SpiritData.FEATHER_STATE);String dim=p.level().dimension().location().toString();
        if(!f.dimension.isEmpty() && !f.dimension.equals(dim) || !permitted(p) || p.onGround() || p.isShiftKeyDown() || p.horizontalCollision)stopHandoff(p);
        f.dimension=dim;
        if(!migrating(p) && p.getData(SpiritData.FEATHER_HANDOFF))p.setData(SpiritData.FEATHER_HANDOFF,false);
        boolean active=permitted(p) && !p.onGround() && !p.isShiftKeyDown() && !p.horizontalCollision
                && (p.getData(SpiritData.MOVEMENT).mode==MoveMode.GLIDE || migrating(p));
        if(active){p.fallDistance=0;if(p.connection instanceof cn.wwl.orimod.mixin.ServerFloatingAccessor a)a.orimod$setAboveGroundTickCount(0);}
        else f.fireworkNotified=false;
        if(p.getData(SpiritData.FEATHER_GLIDING)!=active)p.setData(SpiritData.FEATHER_GLIDING,active);
    }
    public static final String TUTORIAL_RECEIPT = "hint/kuro_feather_controls";
    /** Inventory, armor and offhand all count; the receipt survives death and shape changes. */
    public static boolean offerTutorial(ServerPlayer p) {
        var progress = p.getData(SpiritData.PROGRESS);
        if (!p.isAlive() || p.isSpectator() || progress.rewards().containsKey(TUTORIAL_RECEIPT)) return false;
        boolean owns = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(Items.ELYTRA)) { owns = true; break; }
        if (!owns) return false;
        p.displayClientMessage(Component.translatable("message.orimod.feather.controls", Component.keybind("key.jump")), false);
        var rewards = new java.util.HashMap<>(progress.rewards()); rewards.put(TUTORIAL_RECEIPT, 1);
        p.setData(SpiritData.PROGRESS, progress.withRewards(rewards));
        return true;
    }
    public static boolean refuseFirework(Player p) {
        if(!SpiritData.isSpirit(p) || !gliding(p) || p.onGround())return false;
        State f=p.getData(SpiritData.FEATHER_STATE);
        if(!p.level().isClientSide && !f.fireworkNotified){f.fireworkNotified=true;p.displayClientMessage(Component.translatable("message.orimod.feather.firework"),true);}
        return true;
    }
}
