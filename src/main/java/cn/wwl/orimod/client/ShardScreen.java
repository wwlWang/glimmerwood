package cn.wwl.orimod.client;

import cn.wwl.orimod.network.ProgressActionPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;

/** Vanilla-sized recessed slots, standard buttons and tooltips. Equipment always comes from the server. */
public final class ShardScreen extends Screen {
    private static final int WIDTH = 246, HEIGHT = 222;
    private final Screen parent;
    private int left, top;
    private Shard selected, held;
    private int fromSlot = -1;
    private double pressX, pressY;
    private boolean dragging;
    private Button buy, capacity;
    public ShardScreen(Screen parent) { super(Component.translatable("screen.orimod.shards")); this.parent = parent; }
    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }
    private SpiritProgress progress() { return minecraft.player.getData(SpiritData.PROGRESS); }
    private int slots() { return SpiritProgression.slots(minecraft.player); }
    private boolean owned(Shard shard) { return (progress().ownedShards() & shard.bit) != 0; }
    private void send(String action, String target) { PacketDistributor.sendToServer(new ProgressActionPayload(action, target)); }
    private Shard inSlot(int index) {
        int bit = progress().shardSlots().get(index);
        for (var shard : Shard.values()) if (shard.bit == bit) return shard;
        return null;
    }
    @Override protected void init() {
        left = (width - WIDTH) / 2; top = (height - HEIGHT) / 2;
        buy = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (selected != null && !owned(selected)) send("shard", selected.id());
        }).bounds(left + 8, top + 146, WIDTH - 16, 20).build());
        capacity = addRenderableWidget(Button.builder(Component.empty(), b -> send("capacity", "capacity_upgrade"))
                .bounds(left + 8, top + 170, WIDTH - 16, 20).build());
        addRenderableWidget(Button.builder(tr("gui.done"), b -> onClose()).bounds(left + WIDTH - 70, top + 195, 62, 20).build());
        tick();
    }
    @Override public void tick() {
        if (minecraft.player == null) return;
        buy.visible = selected != null && !owned(selected);
        if (buy.visible) {
            int cost = MerchantRules.panelPrice(selected);
            buy.setMessage(tr("screen.orimod.shard_buy", selected.title(), cost));
            buy.active = SpiritData.isAwakened(minecraft.player) && progress().light() >= cost;
        }
        capacity.setMessage(progress().capacityLevel() >= 3 ? tr("progress.orimod.max_capacity")
                : tr("screen.orimod.shard_capacity", SpiritProgression.capacityCost(progress())));
        capacity.active = SpiritData.isAwakened(minecraft.player) && progress().capacityLevel() < 3
                && progress().light() >= SpiritProgression.capacityCost(progress());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { held = null; minecraft.setScreen(parent); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (SpiritControls.PANEL.matches(key, scan)) { minecraft.setScreen(null); return true; }
        return super.keyPressed(key, scan, mods);
    }
    private int equipX(int i) { return left + 87 + i * 18; }
    private int catalogX(int i) { return left + 42 + i % 9 * 18; }
    private int catalogY(int i) { return top + 91 + i / 9 * 18; }
    private boolean hit(double x, double y, int sx, int sy) { return x >= sx && x < sx + 18 && y >= sy && y < sy + 18; }
    private int equipAt(double x, double y) {
        for (int i = 0; i < ShardSlots.MAX; i++) if (hit(x, y, equipX(i), top + 34)) return i;
        return -1;
    }
    private Shard catalogAt(double x, double y) {
        for (var shard : Shard.values()) if (hit(x, y, catalogX(shard.ordinal()), catalogY(shard.ordinal()))) return shard;
        return null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || minecraft.player == null) return super.mouseClicked(x, y, button);
        fromSlot = equipAt(x, y);
        var shard = fromSlot >= 0 ? inSlot(fromSlot) : catalogAt(x, y);
        if (shard == null) return super.mouseClicked(x, y, button);
        selected = shard; tick();
        if (owned(shard)) { held = shard; pressX = x; pressY = y; dragging = false; }
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (held != null && button == 0) { dragging |= Math.hypot(x - pressX, y - pressY) > 3; return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (held == null || button != 0) return super.mouseReleased(x, y, button);
        int destination = equipAt(x, y);
        if (dragging) {
            if (destination >= 0 && destination < slots()) send("shard_slot", destination + ":" + held.id());
            else if (fromSlot >= 0 && destination < 0) send("unequip", held.id());
        } else send((progress().equippedShards() & held.bit) != 0 ? "unequip" : "equip", held.id());
        held = null; dragging = false; return true;
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) {}
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderTransparentBackground(g);
        // Spirit panel framing; vanilla-sized recessed slots and hit boxes below stay unchanged.
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xF211292F);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFF5C8A82);
        g.fill(left, top, left + WIDTH, top + 2, 0xFF8CDAC8);
        g.drawString(font, title, left + 8, top + 8, SpiritButton.TEXT, false);
        if (minecraft.player == null) { super.render(g, mx, my, partial); return; }
        var light = tr("screen.orimod.light", progress().light());
        g.drawString(font, light, left + WIDTH - font.width(light) - 8, top + 8, SpiritButton.TEXT, false);
        g.drawString(font, tr("screen.orimod.shard_slots", slots(), ShardSlots.MAX), left + 8, top + 22, SpiritButton.TEXT, false);
        for (int i = 0; i < ShardSlots.MAX; i++) {
            slot(g, equipX(i), top + 34, inSlot(i), false, mx, my);
            if (i >= slots() && inSlot(i) == null) g.drawString(font, "×", equipX(i) + 5, top + 39, 0x555555, false);
        }
        g.drawString(font, tr("screen.orimod.shard_gestures"), left + 8, top + 59, SpiritButton.TEXT, false);
        g.drawString(font, tr("screen.orimod.shard_collection"), left + 8, top + 77, SpiritButton.TEXT, false);
        for (var shard : Shard.values()) slot(g, catalogX(shard.ordinal()), catalogY(shard.ordinal()), shard, !owned(shard), mx, my);
        g.drawString(font, tr("screen.orimod.shards_spirit_only"), left + 8, top + 133, SpiritButton.MUTED, false);
        super.render(g, mx, my, partial);
        if (held != null && dragging) {
            g.pose().pushPose(); g.pose().translate(0, 0, 300); g.renderItem(icon(held), mx - 8, my - 8); g.pose().popPose();
        } else tooltip(g, mx, my);
    }
    private void slot(GuiGraphics g, int x, int y, Shard shard, boolean missing, int mx, int my) {
        g.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x, y, x + 17, y + 17, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        if (shard != null && !(dragging && held == shard && fromSlot >= 0 && y == top + 34)) {
            if (missing) g.setColor(.12f, .12f, .12f, 1);
            g.renderItem(icon(shard), x + 1, y + 1);
            g.setColor(1, 1, 1, 1);
            if (!missing && y != top + 34 && (progress().equippedShards() & shard.bit) != 0)
                g.fill(x + 12, y + 12, x + 16, y + 16, 0xFF65CE77);
        }
        if (hit(mx, my, x, y)) g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
    }
    private void tooltip(GuiGraphics g, int mx, int my) {
        int index = equipAt(mx, my);
        var shard = index >= 0 ? inSlot(index) : catalogAt(mx, my);
        var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        if (shard != null) {
            lines.add(shard.title().getVisualOrderText());
            lines.addAll(font.split(shard.description(net.minecraft.client.Minecraft.getInstance().player).copy().withStyle(net.minecraft.ChatFormatting.GRAY), 220));
            lines.addAll(font.split(tr(owned(shard) ? "screen.orimod.shard_gestures" : "screen.orimod.shard_source", MerchantRules.price(shard.id()), MerchantRules.panelPrice(shard)).copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY), 220));
            if (owned(shard) && (progress().equippedShards() & shard.bit) == 0 && Integer.bitCount(progress().equippedShards()) >= slots())
                lines.addAll(font.split(tr("progress.orimod.slots_full").copy().withStyle(net.minecraft.ChatFormatting.GRAY), 220));
        } else if (index >= 0) {
            lines.add(tr(index < slots() ? "screen.orimod.shard_empty" : "screen.orimod.shard_locked").getVisualOrderText());
            if (index >= slots()) lines.addAll(font.split(tr("screen.orimod.shard_unlock_" + index), 220));
        }
        if (!lines.isEmpty()) g.renderTooltip(font, lines, mx, my);
    }
    private static ItemStack icon(Shard shard) {
        return new ItemStack(switch (shard) {
            case CAPACITY -> Items.PRISMARINE_CRYSTALS; case RECOVERY -> Items.GHAST_TEAR; case GATHERING -> Items.SLIME_BALL;
            case RESILIENCE -> Items.IRON_CHESTPLATE; case AIR_CONTROL -> Items.FEATHER; case TRIPLE_JUMP -> Items.RABBIT_FOOT;
            case LIFE_HARVEST -> Items.APPLE; case ENERGY_HARVEST -> Items.GLOWSTONE_DUST; case ULTRA_BASH -> Items.FIRE_CHARGE;
            case THORN -> Items.ROSE_BUSH; case STICKY -> Items.HONEY_BOTTLE; case MAGNET -> Items.COMPASS;
            case DEEP_BREATH -> Items.NAUTILUS_SHELL; case LUMEN_SIGHT -> Items.GLOW_INK_SAC; case PROSPECTOR -> Items.RAW_GOLD;
            case EMBER_SKIN -> Items.MAGMA_CREAM; case FORAGER -> Items.WHEAT;
        });
    }
}
