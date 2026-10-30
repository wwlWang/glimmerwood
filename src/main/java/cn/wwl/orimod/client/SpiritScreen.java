package cn.wwl.orimod.client;

import cn.wwl.orimod.network.ProgressActionPayload;
import cn.wwl.orimod.network.ProgressReplyPayload;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.Shard;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public final class SpiritScreen extends Screen {
    private boolean outpostRequested;
    private long outpostRevision;
    private int page, left, top, panelWidth, panelHeight, bodyTop, bodyHeight, detailX, detailWidth, detailScroll, detailOverflow;
    private String selectedId = "";
    private ChoiceList choices;
    private SpiritButton actionButton;
    private Component requirement = Component.empty();
    private final long openedRevision = ProgressReplyPayload.revision;

    public SpiritScreen(boolean guide) { super(Component.translatable("screen.orimod.title")); page = guide ? 2 : 0; }

    @Override protected void init() {
        clearWidgets();
        panelWidth = Math.min(456, width - 16);
        panelHeight = Math.min(300, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        bodyTop = top + 60; bodyHeight = panelHeight - 94;
        int listWidth = Math.max(104, panelWidth / 3);
        detailX = left + listWidth + 22;
        detailWidth = panelWidth - listWidth - 34;
        int tabWidth = (panelWidth - 32) / 4;
        String[] tabs = {"abilities", "shards", "guide", "wisps"};
        for (int i = 0; i < tabs.length; i++) {
            int tab = i;
            var button = addRenderableWidget(new SpiritButton(left + 10 + i * (tabWidth + 4), top + 34, tabWidth, 20,
                    Component.translatable("screen.orimod." + tabs[i]), b -> { page = tab; selectedId = ""; detailScroll = 0; rebuildWidgets(); }));
            button.active = i != page;
        }
        choices = addRenderableWidget(new ChoiceList(listWidth, bodyHeight, bodyTop));
        choices.setX(left + 10);
        for (var definition : entries()) choices.append(new Choice(definition));
        actionButton = addRenderableWidget(new SpiritButton(detailX, bodyTop + bodyHeight - 23, detailWidth, 22,
                Component.empty(), b -> perform()));
        addRenderableWidget(new SpiritButton(left + panelWidth - 78, top + panelHeight - 25, 68, 19,
                Component.translatable("gui.done"), b -> onClose()));
        Choice initial = choices.children().stream().filter(c -> c.definition.id.equals(selectedId)).findFirst().orElse(choices.getFirstElement());
        choices.setSelected(initial);
        updateAction();
    }

    private List<Definition> entries() {
        var entries = new ArrayList<Definition>();
        if (page == 0) {
            entries.add(new Definition("claim", "blade", tr("item.orimod.spirit_blade"), tr("guide.orimod.blade")));
            for (var ability : Ability.values()) entries.add(new Definition("ability", ability.id(), ability.title(), ability.description()));
        } else if (page == 1) {
            for (var shard : Shard.values()) entries.add(new Definition("shard", shard.id(), shard.title(), shard.description()));
            entries.add(new Definition("capacity", "capacity_upgrade", tr("screen.orimod.energy_capacity"), tr("guide.orimod.capacity")));
        } else if(page==3) {
            for(var w:cn.wwl.orimod.progression.WillWisp.values())entries.add(new Definition("wisp",w.id(),tr("wisp.orimod."+w.id()),tr("wisp.orimod."+w.id()+".source")));
        } else {
            entries.add(new Definition("locate", "ruins", tr("guide.orimod.ruins.title"), tr("guide.orimod.ruins")));
            entries.add(new Definition("exit_challenge", "exit_challenge", tr("challenge.orimod.exit"), tr("guide.orimod.exit_challenge")));
            for (String id : List.of("awakening", "movement", "bash", "light", "shards", "weapons"))
                entries.add(new Definition("guide", id, tr("guide.orimod." + id + ".title"), guideText(id)));
        }
        return entries;
    }

    private Component guideText(String id) {
        if (id.equals("movement")) return Component.translatable("guide.orimod.movement", SpiritControls.TOGGLE.getTranslatedKeyMessage(),
                minecraft.options.keyJump.getTranslatedKeyMessage(), SpiritControls.DASH.getTranslatedKeyMessage(), minecraft.options.keyShift.getTranslatedKeyMessage());
        if (id.equals("bash")) return Component.translatable("guide.orimod.bash", SpiritControls.BASH.getTranslatedKeyMessage());
        if (id.equals("awakening")) return Component.translatable("guide.orimod.awakening", SpiritControls.PANEL.getTranslatedKeyMessage(), SpiritControls.TOGGLE.getTranslatedKeyMessage());
        return tr("guide.orimod." + id);
    }

    private void updateAction() {
        if (actionButton == null || choices == null || choices.getSelected() == null || minecraft.player == null) return;
        var player = minecraft.player;
        var p = player.getData(SpiritData.PROGRESS);
        var d = choices.getSelected().definition;
        actionButton.visible = !d.kind.equals("guide") && !d.kind.equals("wisp");
        if(d.kind.equals("wisp")){requirement=Component.empty();return;}
        actionButton.active = SpiritData.isAwakened(player);
        requirement = Component.empty();
        if (!SpiritData.isAwakened(player) && !d.kind.equals("guide")) {
            actionButton.setMessage(tr("screen.orimod.not_awakened"));
            requirement = tr("progress.orimod.awakening_required");
            return;
        }
        int price = 0;
        if (d.kind.equals("locate") || d.kind.equals("exit_challenge")) {
            actionButton.setMessage(tr(d.kind.equals("locate") ? "guide.orimod.locate" : "challenge.orimod.exit"));
            if (d.kind.equals("exit_challenge") && !player.getData(SpiritData.CHALLENGE_ACTIVE)) {
                actionButton.active = false; actionButton.setMessage(tr("challenge.orimod.inactive"));
            }
            return;
        }
        Ability prerequisite = null;
        if (d.kind.equals("ability")) {
            var a = Ability.byId(d.id);
            prerequisite = a.prerequisite; price = a.cost;
            if (a.unlocked(player)) {
                var gift = cn.wwl.orimod.progression.WeaponGift.forAbility(a);
                if (gift != null) { claimState(gift.bit); return; }
                actionButton.setMessage(tr("screen.orimod.unlocked")); actionButton.active = false; return;
            }
        } else if (d.kind.equals("shard")) {
            var shard = Shard.byId(d.id);

            if ((p.ownedShards() & shard.bit) != 0) {
                boolean equipped = (p.equippedShards() & shard.bit) != 0;
                actionButton.setMessage(tr(equipped ? "screen.orimod.unequip" : "screen.orimod.equip"));
                if (!equipped && Integer.bitCount(p.equippedShards()) >= SpiritProgression.slots(player)) {
                    actionButton.active = false; requirement = tr("progress.orimod.slots_full");
                } else requirement = tr("screen.orimod.shards_spirit_only");
                return;
            }
            price=cn.wwl.orimod.progression.MerchantRules.panelPrice(shard);
            actionButton.setMessage(Component.translatable("merchant.orimod.buy",price));actionButton.active=p.light()>=price;
            if(!outpostRequested){outpostRequested=true;outpostRevision=ProgressReplyPayload.revision;PacketDistributor.sendToServer(new ProgressActionPayload("locate_outpost",""));}
            var guide=cn.wwl.orimod.network.RuinGuidePayload.current;
            if(guide!=null && guide.kind().equals("glades_outpost") && guide.dimension().equals(player.level().dimension().location().toString())){
                double dx=guide.entry().getX()+.5-player.getX(),dz=guide.entry().getZ()+.5-player.getZ();
                float angle=net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-player.getYRot());
                String direction=Math.abs(angle)<35?"ahead":Math.abs(angle)>145?"behind":angle>0?"right":"left";
                requirement=Component.translatable("guide.orimod.marker",cn.wwl.orimod.network.RuinGuidePayload.title("glades_outpost"),(int)Math.round(Math.hypot(dx,dz)),tr("guide.orimod."+direction));
            }else requirement=ProgressReplyPayload.revision>outpostRevision?ProgressReplyPayload.latest:tr("guide.orimod.searching");
            return;
        } else if (d.kind.equals("capacity")) {
            price = SpiritProgression.capacityCost(p);
            if (p.capacityLevel() >= 3) {
                actionButton.setMessage(tr("progress.orimod.max_capacity")); actionButton.active = false; return;
            }
        } else if (d.kind.equals("claim")) { claimState(1); return; }
        actionButton.setMessage(Component.translatable("screen.orimod.purchase", price));
        if (prerequisite != null && !prerequisite.unlocked(player)) {
            actionButton.active = false; requirement = Component.translatable("progress.orimod.requires", prerequisite.title());
        } else if (p.light() < price) {
            actionButton.active = false; requirement = Component.translatable("screen.orimod.need_light", price - p.light());
        }
    }

    private void claimState(int bit) {
        boolean claimed = (minecraft.player.getData(SpiritData.PROGRESS).claimedWeapons() & bit) != 0;
        actionButton.active = SpiritData.isAwakened(minecraft.player) && !claimed;
        actionButton.setMessage(tr(claimed ? "screen.orimod.claimed" : "screen.orimod.claim"));
        requirement = tr(claimed ? "screen.orimod.recraft" : "screen.orimod.first_gift");
    }

    private void perform() {
        var d = choices.getSelected().definition;
        String action = d.kind, target = d.id;
        if (d.kind.equals("shard") && (minecraft.player.getData(SpiritData.PROGRESS).ownedShards() & Shard.byId(d.id).bit) != 0)
            action = (minecraft.player.getData(SpiritData.PROGRESS).equippedShards() & Shard.byId(d.id).bit) != 0 ? "unequip" : "equip";
        if (d.kind.equals("ability") && cn.wwl.orimod.progression.WeaponGift.byId(d.id) != null
                && Ability.byId(d.id).unlocked(minecraft.player)) action = "claim";
        PacketDistributor.sendToServer(new ProgressActionPayload(action, target));
    }

    @Override public void tick() { updateAction(); }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The panel draws its backdrop before labels. Screen.render must not blur those labels again.
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (SpiritControls.PANEL.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= detailX && x < detailX + detailWidth && y >= bodyTop + 18 && y < bodyTop + bodyHeight - 48) {
            detailScroll = Math.clamp(detailScroll - (int)Math.signum(vertical), 0, detailOverflow);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB309181E);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xF211292F);
        g.fill(left, top, left + panelWidth, top + 2, 0xFF8CDAC8);
        g.drawString(font, title, left + 10, top + 9, SpiritButton.TEXT, false);
        if (minecraft.player != null) {
            var p = minecraft.player.getData(SpiritData.PROGRESS);
            Component light = Component.translatable("screen.orimod.light", p.light());
            g.drawString(font, light, left + font.width(title) + 24, top + 9, 0xF2D892, false);
            g.drawString(font, Component.translatable("screen.orimod.stats", (int)SpiritProgression.energy(minecraft.player),
                    (int)SpiritProgression.maxEnergy(minecraft.player), Integer.bitCount(p.equippedShards()), SpiritProgression.slots(minecraft.player)),
                    left + 10, top + 22, SpiritButton.MUTED, false);
        }
        if (choices != null && choices.getSelected() != null) {
            var d = choices.getSelected().definition;
            g.drawString(font, d.title, detailX, bodyTop + 4, SpiritButton.ACCENT, false);
            var description = d.description;
            if (page == 1 && minecraft.player != null) {
                int learned = Integer.bitCount(minecraft.player.getData(SpiritData.ABILITIES));
                var slots = learned >= cn.wwl.orimod.progression.ShardEffects.FOURTH_SLOT_ABILITIES
                        ? tr("screen.orimod.fourth_slot_unlocked") : Component.translatable("screen.orimod.fourth_slot", learned);
                description = description.copy().append("\n\n").append(tr("merchant.orimod.panel_premium")).append("\n\n").append(slots);
            }
            if(page==3 && minecraft.player!=null){
                var w=cn.wwl.orimod.progression.WillWisp.byId(d.id);boolean owned=(minecraft.player.getData(SpiritData.PROGRESS).wisps()&w.bit)!=0;
                description=description.copy().append("\n\n").append(tr(owned?"wisp.orimod.collected":"wisp.orimod.missing"));
                for(var pending:RestorationClient.state().pending())if(pending.kind()==w)description=description.copy().append("\n\n").append(Component.translatable("wisp.orimod.pending",pending.dimension(),pending.pos().getX(),pending.pos().getY(),pending.pos().getZ()));
                if(w==cn.wwl.orimod.progression.WillWisp.EARTH)description=description.copy().append("\n").append(Component.translatable("wisp.orimod.purifications",cn.wwl.orimod.progression.WillWisp.purifications(minecraft.player.getData(SpiritData.PROGRESS))));
                description=description.copy().append("\n\n").append(tr(RestorationClient.state().completed()?"message.orimod.restoration.complete":"wisp.orimod.ritual"));
            }
            var lines = font.split(description, detailWidth - 7);
            int count = Math.max(1, (bodyHeight - (page >= 2 ? 24 : 66)) / 10);
            detailOverflow = Math.max(0, lines.size() - count);
            detailScroll = Math.clamp(detailScroll, 0, detailOverflow);
            for (int i = 0; i < count && i + detailScroll < lines.size(); i++)
                g.drawString(font, lines.get(i + detailScroll), detailX, bodyTop + 22 + i * 10, SpiritButton.TEXT, false);
            if (detailOverflow > 0) {
                int barHeight = count * 10;
                int thumb = Math.max(8, barHeight * count / lines.size());
                int offset = (barHeight - thumb) * detailScroll / detailOverflow;
                g.fill(detailX + detailWidth - 3, bodyTop + 22, detailX + detailWidth - 1, bodyTop + 22 + barHeight, 0xFF355357);
                g.fill(detailX + detailWidth - 3, bodyTop + 22 + offset, detailX + detailWidth - 1, bodyTop + 22 + offset + thumb, 0xFF9AC4B8);
            }
            if (page < 2) g.drawWordWrap(font, requirement, detailX, bodyTop + bodyHeight - 44, detailWidth, 0xD8C69B);
        }
        if (ProgressReplyPayload.revision > openedRevision) g.drawWordWrap(font, ProgressReplyPayload.latest,
                left + 10, top + panelHeight - 25, panelWidth - 100, SpiritButton.ACCENT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static Component tr(String key) { return Component.translatable(key); }
    private record Definition(String kind, String id, Component title, Component description) {}

    private final class ChoiceList extends ObjectSelectionList<Choice> {
        ChoiceList(int width, int height, int y) { super(Minecraft.getInstance(), width, height, y, 25); }
        void append(Choice entry) { addEntry(entry); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        @Override protected void renderListBackground(GuiGraphics g) { g.fill(getX(), getY(), getRight(), getBottom(), 0xFF10252B); }
        @Override protected void renderListSeparators(GuiGraphics g) {}
        @Override public void setSelected(Choice selected) {
            super.setSelected(selected);
            if (selected != null) { selectedId = selected.definition.id; detailScroll = 0; updateAction(); }
        }
    }
    private final class Choice extends ObjectSelectionList.Entry<Choice> {
        final Definition definition;
        Choice(Definition definition) { this.definition = definition; }
        @Override public Component getNarration() { return definition.title.copy().append(". ").append(definition.description); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; choices.setSelected(this); return true; }
        @Override public void render(GuiGraphics g, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean selected = choices.getSelected() == this;
            g.fill(x, y, x + width, y + height, selected ? 0xFF29494D : hovered ? 0xFF1D393F : 0xFF142D33);
            if (selected) g.fill(x, y + 3, x + 2, y + height - 3, 0xFFADE4D1);
            if(definition.kind.equals("wisp") && minecraft.player!=null){
                var w=cn.wwl.orimod.progression.WillWisp.byId(definition.id);boolean owned=(minecraft.player.getData(SpiritData.PROGRESS).wisps()&w.bit)!=0;
                var icon=net.minecraft.resources.ResourceLocation.parse("orimod:textures/gui/wisp_"+w.id()+(owned?"":"_missing")+".png");
                g.blit(icon,x+5,y+4,0,0,16,16,16,16);
            }
            g.drawString(font, definition.title, x + (definition.kind.equals("wisp")?25:6), y + 7, selected ? SpiritButton.TEXT : SpiritButton.MUTED, false);
        }
    }
}
