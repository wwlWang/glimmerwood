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
    private static int lastAction;
    private static long awaitingAttackUntil;
    private static final cn.wwl.orimod.item.SpiritWeaponUse.FeedbackLatch REFUSAL = new cn.wwl.orimod.item.SpiritWeaponUse.FeedbackLatch();
    private static long energyFlashUntil;
    public static boolean energyFlashing() {
        SpiritClientSession.observe();
        var player = Minecraft.getInstance().player;
        return player != null && SpiritClientSession.now() < energyFlashUntil;
    }
    private SpiritCombatClient() {}
    static void resetSession() {
        lastAction = 0; awaitingAttackUntil = energyFlashUntil = 0;
        blockUse = sentCancel = false; REFUSAL.release();
    }

    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        SpiritClientSession.observe();
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if(RestorationClient.active()){event.setCanceled(true);event.setSwingHand(false);return;}
        if (event.isAttack() && mc.player.getMainHandItem().is(OriItems.SPIRIT_BLADE)) {
            // The server still resolves the spirit combo, but vanilla owns its visible swing,
            // including repeated swings while the attack key is held against a block.
            event.setCanceled(true); event.setSwingHand(true);
            if (mc.screen == null && mc.isWindowActive()) {
                if (mc.hitResult != null && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK)
                    mc.player.resetAttackStrengthTicker();
                awaitingAttackUntil = SpiritClientSession.now() + 12;
                PacketDistributor.sendToServer(new CombatInputPayload(false));
            }
        }
    }

    /** Called only after vanilla has offered the click to the targeted block/entity. */
    public static boolean refuseItemUse(net.minecraft.world.InteractionHand hand) {
        SpiritClientSession.observe();
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var stack = mc.player.getItemInHand(hand);
        if (cn.wwl.orimod.item.SpiritWeaponUse.blocked(mc.player, stack)) {
            if (REFUSAL.refuse()) {
                energyFlashUntil = SpiritClientSession.now() + 8;
                mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), .2f, .65f);
            }
            return true;
        }
        return blockUse && (stack.is(OriItems.SPIRIT_BOW) || stack.is(OriItems.SPIRIT_SPEAR));
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
        if (mc.player.isUsingItem() && (mc.player.getUseItem().is(OriItems.SPIRIT_BOW)
                || mc.player.getUseItem().is(OriItems.SPIRIT_SPEAR))) mc.player.stopUsingItem();
    }

    public static boolean awaitingAttack() {
        SpiritClientSession.observe();
        var player = Minecraft.getInstance().player;
        return player != null && SpiritClientSession.now() < awaitingAttackUntil;
    }

    public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
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
            if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.player.isSpectator() || !SpiritData.isSpirit(mc.player)) return;
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
