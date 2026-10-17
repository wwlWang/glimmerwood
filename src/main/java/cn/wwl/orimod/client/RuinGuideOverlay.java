package cn.wwl.orimod.client;

import cn.wwl.orimod.network.RuinGuidePayload;
import cn.wwl.orimod.world.RuinKind;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class RuinGuideOverlay {
    private RuinGuideOverlay() {}
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.parse("orimod:ruin_guide"), (g, delta) -> {
            var mc = Minecraft.getInstance();
            var guide = RuinGuidePayload.current;
            if (mc.player == null) { RuinGuidePayload.current = null; return; }
            if (guide == null || mc.options.hideGui || mc.screen != null) return;
            if (!guide.dimension().equals(mc.player.level().dimension().location().toString())) { RuinGuidePayload.current = null; return; }
            double dx = guide.entry().getX() + .5 - mc.player.getX(), dz = guide.entry().getZ() + .5 - mc.player.getZ();
            int distance = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
            float angle = Mth.wrapDegrees((float)(Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot()));
            String direction = Math.abs(angle) < 35 ? "ahead" : Math.abs(angle) > 145 ? "behind" : angle > 0 ? "right" : "left";
            var text = Component.translatable("guide.orimod.marker", RuinKind.byId(guide.kind()).title(), distance, Component.translatable("guide.orimod." + direction));
            g.drawCenteredString(mc.font, text, g.guiWidth() / 2, 12, 0xB6F6DF);
            if (distance < 5 && Math.abs(mc.player.getY() - guide.entry().getY()) < 5) RuinGuidePayload.current = null;
        });
    }
}
