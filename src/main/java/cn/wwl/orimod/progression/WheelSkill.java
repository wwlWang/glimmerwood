package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.player.Player;
import java.util.function.ToLongFunction;

/** Data-driven wheel catalogue. Weapons and movement abilities intentionally stay outside this catalogue. */
public enum WheelSkill {
    LIGHT_BURST(Ability.LIGHT_BURST, Style.TAP, "light_burst", "", "", 0, 15, s -> s.burstReadyAt),
    CHARGE_FLAME(Ability.CHARGE_FLAME, Style.RELEASE, "charge_flame_hold", "charge_flame", "charge_flame_cancel", 12, 10, s -> s.chargeFlameReadyAt),
    FLASH(Ability.FLASH, Style.HOLD, "flash", "flash_stop", "flash_stop", 0, 5, s -> s.flashReadyAt),
    REGENERATE(null, Style.HOLD, "regenerate", "regenerate_stop", "regenerate_stop", 0, RecoveryTuning.CAST_COST, s -> 0);

    public enum Style { TAP, RELEASE, HOLD }
    public final Ability ability;
    public final Style style;
    public final String start, release, cancel;
    public final int minimumHold;
    public final float cost;
    private final ToLongFunction<SpiritCooldowns> cooldown;
    WheelSkill(Ability ability, Style style, String start, String release, String cancel, int minimumHold, float cost, ToLongFunction<SpiritCooldowns> cooldown) {
        this.ability=ability;this.style=style;this.start=start;this.release=release;this.cancel=cancel;this.minimumHold=minimumHold;this.cost=cost;this.cooldown=cooldown;
    }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String titleKey() { return "ability.orimod."+id(); }
    public boolean learned(boolean awakened,int bits) { return awakened && (ability==null || (bits&ability.bit)!=0); }
    public boolean learned(Player p) { return learned(SpiritData.isAwakened(p),p.getData(SpiritData.ABILITIES)); }
    public long readyAt(Player p) { return cooldown.applyAsLong(p.getData(SpiritData.COOLDOWNS)); }
    public static WheelSkill byId(String id) { for(var skill:values())if(skill.id().equals(id))return skill;return null; }
}
