package cn.wwl.orimod.client;

import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.WheelSkill;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ControlText {
    private ControlText() {}
    public static Component hint(String id) {
        return switch (id) {
            case "bash_anchor" -> Component.translatable("hint.orimod." + id, Component.keybind(SpiritControls.BASH.getName()));
            case "shard_slot" -> Component.translatable("hint.orimod." + id, Component.keybind(SpiritControls.PANEL.getName()));
            default -> Component.translatable("hint.orimod." + id);
        };
    }
    public static Component description(Ability ability) {
        if (ability != Ability.CHARGE_FLAME) return ability.description();
        var player = Minecraft.getInstance().player;
        if (player != null) for (int i = 0; i < 3; i++)
            if (player.getData(SpiritData.SKILL_BINDINGS).skill(i) == WheelSkill.CHARGE_FLAME)
                return Component.translatable("ability.orimod.charge_flame.description", Component.keybind(SkillControls.key(i).getName()));
        return Component.translatable("ability.orimod.charge_flame.unbound_description", Component.keybind(SpiritControls.PANEL.getName()));
    }
}
