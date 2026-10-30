package cn.wwl.orimod.client;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.network.CombatInputPayload;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpiritCombatClient {
    private static boolean blockUse, sentCancel;
    private static int lastAction, awaitingAttackUntil;
    private static final cn.wwl.orimod.item.SpiritWeaponUse.FeedbackLatch REFUSAL = new cn.wwl.orimod.item.SpiritWeaponUse.FeedbackLatch();
    private static int energyFlashUntil;
    public static boolean energyFlashing() {
        var player = Minecraft.getInstance().player;
        return player != null && player.tickCount < energyFlashUntil;
    }
    private SpiritCombatClient() {}

    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if(RestorationClient.active()){event.setCanceled(true);event.setSwingHand(false);return;}
        if (event.isUseItem() && cn.wwl.orimod.item.SpiritWeaponUse.blocked(mc.player, mc.player.getItemInHand(event.getHand()))) {
            event.setCanceled(true); event.setSwingHand(false);
            if (REFUSAL.refuse()) {
                energyFlashUntil = mc.player.tickCount + 8;
                mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), .2f, .65f);
            }
            return;
        }
        if (event.isAttack() && mc.player.getMainHandItem().is(OriItems.SPIRIT_BLADE)) {
            event.setCanceled(true); event.setSwingHand(false);
            if (mc.screen == null && mc.isWindowActive()) {
                awaitingAttackUntil = mc.player.tickCount + 12;
                PacketDistributor.sendToServer(new CombatInputPayload(false));
            }
        } else if (event.isUseItem() && blockUse && mc.player.getItemInHand(event.getHand()).is(OriItems.SPIRIT_BOW)) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }

    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null) cancel();
    }

    private static void cancel() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!sentCancel) PacketDistributor.sendToServer(new CombatInputPayload(true));
        sentCancel = true;
        awaitingAttackUntil = 0;
        blockUse = mc.options.keyUse.isDown();
        if (mc.player.isUsingItem() && mc.player.getUseItem().is(OriItems.SPIRIT_BOW)) mc.player.stopUsingItem();
    }

    public static boolean awaitingAttack() {
        var player = Minecraft.getInstance().player;
        return player != null && player.tickCount < awaitingAttackUntil;
    }

    public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) { lastAction = awaitingAttackUntil = energyFlashUntil = 0; blockUse = sentCancel = false; REFUSAL.release(); return; }
        if (!mc.options.keyUse.isDown() || mc.screen != null || !mc.isWindowActive()) { blockUse = false; REFUSAL.release(); }
        if (mc.screen != null || !mc.isWindowActive() || !SpiritData.isSpirit(mc.player)) cancel();
        else sentCancel = false;
        var view = mc.player.getData(SpiritData.COMBAT_VIEW);
        if (view.actionId() != lastAction) {
            awaitingAttackUntil = 0;
            if (view.mode().equals("idle")) {
                // Vanilla's using-item flag may arrive before this attachment. Latch the held
                // key even when vanilla already stopped use, or its auto-repeat restarts the bow.
                if (mc.player.isUsingItem() && mc.player.getUseItem().is(OriItems.SPIRIT_BOW)) mc.player.stopUsingItem();
                blockUse = mc.options.keyUse.isDown();
            }
            lastAction = view.actionId();
        }
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "combat"), (graphics, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.options.hideGui || !SpiritData.isSpirit(mc.player)) return;
            float energy = SpiritProgression.energy(mc.player), max = SpiritProgression.maxEnergy(mc.player);
            int x = 8, y = graphics.guiHeight() - 62, width = 84;
            boolean low = energyFlashing() || mc.player.getMainHandItem().is(OriItems.SPIRIT_BOW) && energy < SpiritCombat.BOW_COST;
            if (!SpiritHud.enabled()) {
            graphics.drawString(mc.font, Component.translatable("hud.orimod.energy", (int)energy, (int)max), x, y - 12, low ? 0xFFB09E : 0xC6F5FF, true);
            graphics.fill(x - 1, y - 1, x + width + 1, y + 5, 0xBB14232E);
            graphics.fill(x, y, x + (int)(width * energy / max), y + 4, low ? 0xFFE39080 : 0xFF86DDEC);
            }
            var view = mc.player.getData(SpiritData.COMBAT_VIEW);
            int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
            if (mc.player.isUsingItem() && mc.player.getUseItem().is(OriItems.SPIRIT_BOW)) {
                int charge = mc.player.getTicksUsingItem();
                graphics.fill(cx - 28, cy + 14, cx + 28, cy + 19, 0xBB14232E);
                graphics.fill(cx - 27, cy + 15, cx - 27 + Math.min(54, charge * 54 / SpiritCombat.BOW_FULL_CHARGE), cy + 18,
                        charge < SpiritCombat.BOW_MIN_CHARGE ? 0xFF889BA2 : charge < SpiritCombat.BOW_FULL_CHARGE ? 0xFF86DDEC : 0xFFFFE89E);
            } else if (view.mode().equals("blade")) {
                long elapsed = mc.player.level().getGameTime() - view.startedAt();
                if (elapsed < SpiritCombat.duration(view.stage())) {
                    Component label = Component.translatable("hud.orimod.combo", view.stage(), Component.translatable("combat.orimod." + SpiritCombat.phase(view.stage(), elapsed)));
                    graphics.drawString(mc.font, label, cx - mc.font.width(label) / 2, cy + 14, 0xC6F5FF, true);
                }
            }
        });
    }
}
