package cn.wwl.orimod.progression;

/** R100: Ori learns from a spirit tree; the cheapest eligible unlearned skill wins, ties keep enum order. */
public final class SpiritTree {
    public static final int LIGHT_GIFT = 100;
    private SpiritTree() {}
    public static Ability gift(int abilities) {
        Ability chosen = null;
        for (var ability : Ability.values()) {
            if ((abilities & ability.bit) != 0 || ability.prerequisite != null && (abilities & ability.prerequisite.bit) == 0) continue;
            if (chosen == null || ability.cost < chosen.cost) chosen = ability;
        }
        return chosen;
    }
}
