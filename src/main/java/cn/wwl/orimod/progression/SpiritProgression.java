package cn.wwl.orimod.progression;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.network.ProgressReplyPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpiritProgression {
    private SpiritProgression() {}
    public static int slots(Player player) { return Ability.GRAPPLE.unlocked(player) ? 3 : Ability.BASH.unlocked(player) ? 2 : 1; }
    public static int capacityCost(SpiritProgress p) { return 30 + 15 * p.capacityLevel(); }
    public static float maxEnergy(Player player) {
        return 100 + 25 * player.getData(SpiritData.PROGRESS).capacityLevel() + (Shard.CAPACITY.equipped(player) ? 25 : 0);
    }
    public static float energy(Player player) { return Math.clamp(player.getData(SpiritData.ENERGY), 0, maxEnergy(player)); }
    public static boolean spendEnergy(ServerPlayer player, float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || energy(player) < amount) return false;
        player.setData(SpiritData.ENERGY, energy(player) - amount);
        player.getData(SpiritData.ENERGY_STATE).lastSpent = player.level().getGameTime();
        return true;
    }

    public static void request(ServerPlayer player, String action, String target) {
        if (!player.isAlive() || !SpiritData.isAwakened(player)) { reply(player, "progress.orimod.awakening_required"); return; }
        var p = player.getData(SpiritData.PROGRESS);
        switch (action) {
            case "soul_link" -> SoulLink.create(player);
            case "locate" -> cn.wwl.orimod.world.RuinLocator.request(player);
            case "exit_challenge" -> cn.wwl.orimod.world.SpiritChallenge.finish(player, "exit", true);
            case "ability" -> {
                var ability = Ability.byId(target);
                if (ability == null) return;
                if (ability.unlocked(player)) { reply(player, "progress.orimod.already_owned"); return; }
                if (ability.prerequisite != null && !ability.prerequisite.unlocked(player)) { reply(player, "progress.orimod.requires", ability.prerequisite.title()); return; }
                if (p.light() < ability.cost) { reply(player, "progress.orimod.not_enough", ability.cost); return; }
                player.setData(SpiritData.PROGRESS, p.withLight(p.light() - ability.cost));
                player.setData(SpiritData.ABILITIES, player.getData(SpiritData.ABILITIES) | ability.bit);
                reply(player, "progress.orimod.unlocked", ability.title());
            }
            case "shard" -> {
                var shard = Shard.byId(target);
                if (shard == null) return;
                if ((p.ownedShards() & shard.bit) != 0) { reply(player, "progress.orimod.already_owned"); return; }
                if (shard.prerequisite != null && !shard.prerequisite.unlocked(player)) { reply(player, "progress.orimod.requires", shard.prerequisite.title()); return; }
                if (p.light() < Shard.COST) { reply(player, "progress.orimod.not_enough", Shard.COST); return; }
                player.setData(SpiritData.PROGRESS, p.withLight(p.light() - Shard.COST).withOwned(p.ownedShards() | shard.bit));
                reply(player, "progress.orimod.unlocked", shard.title());
            }
            case "equip", "unequip" -> {
                var shard = Shard.byId(target);
                if (shard == null || (p.ownedShards() & shard.bit) == 0) return;
                boolean remove = action.equals("unequip");
                boolean equipped = (p.equippedShards() & shard.bit) != 0;
                if (equipped == !remove) { reply(player, remove ? "progress.orimod.unequipped" : "progress.orimod.equipped", shard.title()); return; }
                if (!remove && Integer.bitCount(p.equippedShards()) >= slots(player)) { reply(player, "progress.orimod.slots_full"); return; }
                player.setData(SpiritData.PROGRESS, p.withEquipped(remove ? p.equippedShards() & ~shard.bit : p.equippedShards() | shard.bit));
                player.setData(SpiritData.ENERGY, energy(player));
                if (shard == Shard.RESILIENCE) player.getData(SpiritData.ENERGY_STATE).pendingHeal = false;
                reply(player, remove ? "progress.orimod.unequipped" : "progress.orimod.equipped", shard.title());
            }
            case "capacity" -> {
                if (p.capacityLevel() >= 3) { reply(player, "progress.orimod.max_capacity"); return; }
                int cost = capacityCost(p);
                if (p.light() < cost) { reply(player, "progress.orimod.not_enough", cost); return; }
                player.setData(SpiritData.PROGRESS, p.withLight(p.light() - cost).withCapacity(p.capacityLevel() + 1));
                reply(player, "progress.orimod.capacity_increased");
            }
            case "claim" -> {
                int bit = target.equals("blade") ? 1 : target.equals("bow") ? 2 : 0;
                if (bit == 0 || (bit == 2 && !Ability.BOW.unlocked(player))) return;
                if ((p.claimedWeapons() & bit) != 0) { reply(player, "progress.orimod.already_claimed"); return; }
                var stack = new ItemStack(bit == 1 ? OriItems.SPIRIT_BLADE.get() : OriItems.SPIRIT_BOW.get());
                if (!player.getInventory().add(stack)) { reply(player, "progress.orimod.inventory_full"); return; }
                player.setData(SpiritData.PROGRESS, p.withClaims(p.claimedWeapons() | bit));
                player.containerMenu.broadcastChanges();
                reply(player, "progress.orimod.weapon_claimed");
            }
        }
    }

    private static void reply(ServerPlayer player, String key, Object... values) {
        PacketDistributor.sendToPlayer(player, new ProgressReplyPayload(Component.translatable(key, values), false));
    }

    public static int grantLight(ServerPlayer player, int amount) {
        if (amount <= 0) return 0;
        var p = player.getData(SpiritData.PROGRESS);
        int granted = Math.min(amount, SpiritProgress.LIGHT_LIMIT - p.light());
        if (granted > 0) player.setData(SpiritData.PROGRESS, p.withLight(p.light() + granted));
        return granted;
    }

    /** Discovery and challenge services must supply a stable world-position key. */
    public static boolean reward(ServerPlayer player, String key, int first, int repeat) {
        var p = player.getData(SpiritData.PROGRESS);
        int count = p.rewards().getOrDefault(key, 0);
        if (count > 0 && repeat <= 0) return false;
        var records = new java.util.HashMap<>(p.rewards());
        records.put(key, Math.min(1_000_000, count + 1));
        player.setData(SpiritData.PROGRESS, p.withRewards(records));
        grantLight(player, count == 0 ? first : repeat);
        return count == 0;
    }

    /** R35: Spirit Light orbs dropped by an enemy with this maximum health (1–8; 20 health gives 2). */
    public static int lightFor(float maxHealth) {
        return Float.isFinite(maxHealth) ? Math.clamp(Math.round(maxHealth / 10), 1, 8) : 1;
    }

    /** R35: radius in which Spirit Light flies to the player: spirit form always pulls nearby orbs, Gathering reaches further. */
    public static double magnetRadius(boolean spirit, boolean gathering) { return gathering ? 4 : spirit ? 2.5 : 0; }

    /** Pull loose Spirit Light orbs toward the player, as orbs fly to Ori. */
    public static int pullLight(ServerPlayer player) {
        double radius = magnetRadius(SpiritData.isSpirit(player), Shard.GATHERING.equipped(player));
        if (radius <= 0) return 0;
        int pulled = 0;
        for (var item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(radius),
                entity -> entity.getItem().is(OriItems.SPIRIT_LIGHT) && !entity.hasPickUpDelay() && player.distanceToSqr(entity) <= radius * radius)) {
            var toward = player.position().add(0, .5, 0).subtract(item.position());
            if (toward.lengthSqr() > .1) { item.setDeltaMovement(toward.normalize().scale(.35)); item.hurtMarked = true; pulled++; }
        }
        return pulled;
    }

    public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Enemy && event.getSource().getEntity() instanceof ServerPlayer player && SpiritData.isAwakened(player)) {
            // R35: Ori-style burst of single orbs, more for tougher enemies (a zombie still gives 2).
            var victim = event.getEntity(); var random = victim.getRandom();
            for (int i = lightFor(victim.getMaxHealth()); i > 0; i--) {
                var orb = new ItemEntity(player.level(), victim.getX(), victim.getY() + .4, victim.getZ(), new ItemStack(OriItems.SPIRIT_LIGHT.get()));
                orb.setDeltaMovement((random.nextDouble() - .5) * .3, .25 + random.nextDouble() * .15, (random.nextDouble() - .5) * .3);
                event.getDrops().add(orb);
            }
        }
    }

    public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && SpiritData.isAwakened(player) && event.getItemEntity().getItem().is(OriItems.SPIRIT_LIGHT)) {
            var entity = event.getItemEntity();
            int taken = grantLight(player, entity.getItem().getCount());
            if (taken == 0) return;
            event.setCanPickup(TriState.FALSE);
            player.take(entity, taken);
            entity.getItem().shrink(taken);
            if (entity.getItem().isEmpty()) entity.discard();
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, .25f, 1.25f);
        }
    }

    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0 && Shard.RESILIENCE.equipped(player)) {
            var s = player.getData(SpiritData.ENERGY_STATE);
            s.lastHurt = player.level().getGameTime();
            s.pendingHeal = true;
        }
    }

    public static void changedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.getData(SpiritData.ENERGY_STATE).pendingHeal = false;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SpiritData.isAwakened(player) || !player.isAlive()) return;
        long now = player.level().getGameTime();
        var s = player.getData(SpiritData.ENERGY_STATE);
        if (now % 4 == 0) {
            float energy = energy(player);
            if (now - s.lastSpent >= 40) energy = Math.min(maxEnergy(player), energy + (Shard.RECOVERY.equipped(player) ? 1.5f : 1));
            if (Float.compare(player.getData(SpiritData.ENERGY), energy) != 0) player.setData(SpiritData.ENERGY, energy);
        }
        if (now % 2 == 0) pullLight(player);
        if (s.pendingHeal) {
            if (!Shard.RESILIENCE.equipped(player)) s.pendingHeal = false;
            else if (now - s.lastHurt >= 80) {
                s.pendingHeal = false;
                if (now >= s.healReadyAt && player.getHealth() < player.getMaxHealth() && spendEnergy(player, 6)) {
                    player.heal(1);
                    s.healReadyAt = now + 200;
                }
            }
        }
        if (now % 20 == 0) {
            grantGuide(player);
            for (var stack : player.getInventory().items) {
                if (stack.is(OriItems.SPIRIT_LIGHT)) stack.shrink(grantLight(player, stack.getCount()));
            }
        }
    }

    public static void grantGuide(ServerPlayer player) {
        var progress = player.getData(SpiritData.PROGRESS);
        if (progress.rewards().getOrDefault("gift/ruin_guide", 0) > 0) return;
        var stack = new ItemStack(OriItems.RUIN_GUIDE.get());
        if (!player.getInventory().add(stack)) return;
        var records = new java.util.HashMap<>(progress.rewards());
        records.put("gift/ruin_guide", 1);
        player.setData(SpiritData.PROGRESS, progress.withRewards(records));
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("guide.orimod.gift"), false);
    }
}
