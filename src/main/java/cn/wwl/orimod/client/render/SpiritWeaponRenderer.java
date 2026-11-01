package cn.wwl.orimod.client.render;

import cn.wwl.orimod.item.OriItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * R88: the spirit weapons are condensed spirit light rather than flat item sprites ("能量凝聚的武器"). Each is built from
 * soft additive energy beams and glow orbs ({@link SpiritEnergy}) in the usual 0..1 handheld item space (handle at the
 * lower-left, tip at the upper-right, so the vanilla handheld display transforms and our palm grips line up), with a
 * slow breathing pulse and light motes flowing toward the tip. The hammer brightens while it is wound up.
 */
public final class SpiritWeaponRenderer extends BlockEntityWithoutLevelRenderer {
    public static final int CORE = 0xF4FAFF, MID = 0xA8DCFF, OUTER = 0x5AA8FF;
    private static SpiritWeaponRenderer instance;
    private static net.minecraft.world.entity.player.Player owner;

    /** Rendering is on the client thread. Scope the owner so remote bows use their own charge, not ours. */
    static void withOwner(net.minecraft.world.entity.player.Player player, Runnable draw) {
        var previous = owner;
        try { owner = player; draw.run(); } finally { owner = previous; }
    }

    private SpiritWeaponRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static SpiritWeaponRenderer get() {
        if (instance == null) instance = new SpiritWeaponRenderer();
        return instance;
    }

    public static void register(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return get(); }
        }, OriItems.SPIRIT_HAMMER.get(), OriItems.SPIRIT_SPEAR.get(), OriItems.SPIRIT_BLADE.get(),
                OriItems.SPIRIT_BOW.get(), OriItems.SPIRIT_CORE.get());
    }

    private static Vector3f p(float x, float y) { return new Vector3f(x, y, .5f); }
    private static final Vector3f ACROSS = new Vector3f(-1, 1, 0);

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        float t = SpiritEnergy.time();
        float pulse = .88f + .12f * (float) Math.sin(t * 2.4f);
        float charge = charge(stack);
        var pose = poses.last();
        // Fetch the consumer per call: in the GUI's immediate buffer source, asking for another render type ends the
        // previous builder, so holding two consumers at once crashes ("Not building!").
        java.util.function.Supplier<com.mojang.blaze3d.vertex.VertexConsumer> streakS = () -> buffers.getBuffer(SpiritEnergy.streak());
        java.util.function.Supplier<com.mojang.blaze3d.vertex.VertexConsumer> ribbonS = () -> buffers.getBuffer(SpiritEnergy.ribbon());
        java.util.function.Supplier<com.mojang.blaze3d.vertex.VertexConsumer> glowS = () -> buffers.getBuffer(SpiritEnergy.glow());
        float swing = swing(stack);
        // R94: the light flares through a swing / throw, so the weapon reads as energy being spent, not a prop.
        float a = pulse * (1 + .5f * charge + .7f * swing);
        // Icons are tiny: thicker light so the shape reads in the hotbar and inventory.
        float w = context == ItemDisplayContext.GUI ? 1.8f : 1f;
        if (stack.is(OriItems.SPIRIT_BOW)) {
            var player=Minecraft.getInstance().player;
            boolean fp=player!=null&&SpiritFirstPersonGrip.mainHandView(context,player.getMainArm())
                    &&cn.wwl.orimod.spirit.SpiritData.isSpirit(player)&&player.getMainHandItem().is(stack.getItem());
            float pull = bowPull(stack), stage = SpiritWeaponShape.pullStage(pull);
            Vector3f previous = SpiritWeaponShape.bowLimb(-1);
            for (int i = 1; i <= 20; i++) {
                float from = -1 + (i - 1) / 10f, to = -1 + i / 10f;
                var next = SpiritWeaponShape.bowLimb(to);
                var side0 = SpiritWeaponShape.bowSide(from);
                var side1 = SpiritWeaponShape.bowSide(to);
                curvedBeam(ribbonS.get(), pose, previous, next, side0, side1, .34f * w, OUTER, .18f * a);
                curvedBeam(ribbonS.get(), pose, previous, next, side0, side1, .18f * w, MID, .30f * a + stage * .15f);
                curvedBeam(ribbonS.get(), pose, previous, next, side0, side1, .065f * w, CORE, .88f + stage * .12f);
                previous = next;
            }
            var string = SpiritWeaponShape.bowString(pull,fp);
            for (float end : new float[]{-1, 1}) {
                var tip = SpiritWeaponShape.bowLimb(end);
                var side = new Vector3f(tip).sub(string).cross(0, 0, 1).normalize();
                SpiritEnergy.beam(ribbonS.get(), pose, string, tip, .035f * w, side, CORE, .60f + stage * .35f);
                SpiritEnergy.orb(glowS.get(), pose, tip, .09f + stage * .04f, MID, .45f * a);
            }
            if (pull > 0) {
                var tip = p(SpiritWeaponShape.BOW_X - .25f, SpiritWeaponShape.BOW_Y + .25f);
                SpiritEnergy.beam(ribbonS.get(), pose, string, tip, .045f * w, new Vector3f(1, 1, 0), CORE, .8f);
            }
            SpiritEnergy.orb(glowS.get(), pose, p(SpiritWeaponShape.BOW_X, SpiritWeaponShape.BOW_Y), .11f, MID, .5f);
        } else if (stack.is(OriItems.SPIRIT_CORE)) {
            var centre = p(.5f, .5f);
            SpiritEnergy.orb(glowS.get(), pose, centre, .42f, OUTER, .35f * pulse);
            SpiritEnergy.orb(glowS.get(), pose, centre, .23f, MID, .72f);
            SpiritEnergy.orb(glowS.get(), pose, centre, .12f, CORE, 1);
            for (int ring = 0; ring < 2; ring++) {
                Vector3f previous = null;
                for (int i = 0; i <= 32; i++) {
                    float angle = i * (float)Math.PI / 16 + t * .6f * (ring == 0 ? 1 : -1);
                    var next = new Vector3f(.28f * (float)Math.cos(angle), .28f * (float)Math.sin(angle), 0);
                    next.rotateY(ring == 0 ? .65f : -.65f).rotateX(.6f).add(centre);
                    if (previous != null) SpiritEnergy.beam(ribbonS.get(), pose, previous, next, .025f * w,
                            new Vector3f(next).sub(previous).cross(new Vector3f(next).sub(centre)), MID, .6f);
                    previous = next;
                }
            }
        } else if (stack.is(OriItems.SPIRIT_HAMMER)) {
            // Shaft, then a broad crossbar head of denser light with a halo that swells while charging.
            // At arm's length the additive cross-planes and centre orb saturated into a ball.
            // Keep the same T geometry and native animation, but expose its crossbar in first person.
            boolean first = context.firstPerson();
            float halo = first ? .3f : 1;
            SpiritEnergy.beam(streakS.get(), pose, p(.10f, .10f), p(.74f, .74f), .44f * w, ACROSS, OUTER, .18f * a * halo);
            SpiritEnergy.beam(streakS.get(), pose, p(.10f, .10f), p(.74f, .74f), .26f * w, ACROSS, MID, .32f * a * halo);
            SpiritEnergy.beam(streakS.get(), pose, p(.10f, .10f), p(.74f, .74f), .10f * w, ACROSS, CORE, first ? .7f : .95f);
            SpiritEnergy.beam(streakS.get(), pose, p(.48f, 1.0f), p(1.0f, .48f), .52f * w, new Vector3f(1, 1, 0), OUTER, .30f * a * halo);
            SpiritEnergy.beam(streakS.get(), pose, p(.52f, .96f), p(.96f, .52f), .26f * w, new Vector3f(1, 1, 0), CORE, first ? .65f : .92f);
            if (!first) SpiritEnergy.orb(glowS.get(), pose, p(.74f, .74f), .30f + .12f * charge, MID, .42f * a);
            SpiritEnergy.orb(glowS.get(), pose, p(.12f, .12f), .08f, CORE, .7f);
            motes(glowS.get(), pose, t, .12f, .74f, 3, .05f + .03f * charge, a);
        } else if (stack.is(OriItems.SPIRIT_SPEAR)) {
            SpiritEnergy.beam(streakS.get(), pose, p(.02f, .02f), p(.82f, .82f), .38f * w, ACROSS, OUTER, .16f * a);
            SpiritEnergy.beam(streakS.get(), pose, p(.02f, .02f), p(.82f, .82f), .20f * w, ACROSS, MID, .30f * a);
            SpiritEnergy.beam(streakS.get(), pose, p(.02f, .02f), p(.82f, .82f), .07f * w, ACROSS, CORE, .95f);
            SpiritEnergy.beam(streakS.get(), pose, p(.70f, .70f), p(1.0f, 1.0f), .17f * w, ACROSS, CORE, .95f);
            SpiritEnergy.beam(streakS.get(), pose, p(.66f, .80f), p(.80f, .66f), .10f * w, new Vector3f(1, 1, 0), MID, .8f);
            SpiritEnergy.orb(glowS.get(), pose, p(.90f, .90f), .16f, MID, .5f * a);
            motes(glowS.get(), pose, t, .05f, .95f, 4, .04f, a);
        } else {
            // Spirit Edge: grip, a small guard and a long blade of light.
            SpiritEnergy.beam(streakS.get(), pose, p(.18f, .18f), p(.43f, .43f), .08f * w, ACROSS, MID, .8f);
            SpiritEnergy.beam(streakS.get(), pose, p(.35f, .53f), p(.53f, .35f), .09f * w, new Vector3f(1, 1, 0), CORE, .85f);
            SpiritEnergy.beam(streakS.get(), pose, p(.42f, .42f), p(.98f, .98f), .52f * w, ACROSS, OUTER, .18f * a);
            SpiritEnergy.beam(streakS.get(), pose, p(.42f, .42f), p(.98f, .98f), (.40f + .22f * swing) * w, ACROSS, MID, .28f * a);
            if (swing > .01f) SpiritEnergy.beam(streakS.get(), pose, p(.42f, .42f), p(1.04f, 1.04f), .7f * w, ACROSS, OUTER, .16f * swing);
            SpiritEnergy.beam(streakS.get(), pose, p(.42f, .42f), p(.98f, .98f), .17f * w, ACROSS, CORE, .92f);
            SpiritEnergy.orb(glowS.get(), pose, p(.18f, .18f), .07f, CORE, .7f);
            motes(glowS.get(), pose, t, .45f, .95f, 3, .04f, a);
        }
    }

    /** Adjacent limb segments share their cross-section, avoiding cracks between rotated rectangles. */
    private static void curvedBeam(com.mojang.blaze3d.vertex.VertexConsumer out,
                                   com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                   Vector3f a, Vector3f b, Vector3f side0, Vector3f side1,
                                   float width, int rgb, float alpha) {
        float half = width / 2;
        SpiritEnergy.strip(out, pose, new Vector3f(side0).mul(-half).add(a), new Vector3f(side0).mul(half).add(a),
                new Vector3f(side1).mul(-half).add(b), new Vector3f(side1).mul(half).add(b), rgb, alpha, alpha);
        SpiritEnergy.strip(out, pose, new Vector3f(a).add(0, 0, -half), new Vector3f(a).add(0, 0, half),
                new Vector3f(b).add(0, 0, -half), new Vector3f(b).add(0, 0, half), rgb, alpha, alpha);
    }

    private static float bowPull(ItemStack stack) {
        var player = owner == null ? Minecraft.getInstance().player : owner;
        return player != null && player.isUsingItem() && player.getUseItem() == stack
                ? net.minecraft.world.item.BowItem.getPowerForTime(player.getTicksUsingItem()) : 0;
    }

    /** Small motes riding along the weapon toward its tip, wrapping, so the light reads as flowing energy. */
    private static void motes(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack.Pose pose, float t, float from, float to, int n, float r, float a) {
        for (int i = 0; i < n; i++) {
            float k = (t * .45f + i / (float) n) % 1f;
            float s = from + (to - from) * k, fade = (float) Math.sin(Math.PI * k);
            float off = .03f * (float) Math.sin(t * 3 + i * 2.1f);
            SpiritEnergy.orb(out, pose, new Vector3f(s - off, s + off, .5f), r, CORE, .8f * fade * a);
        }
    }

    /** R94: 0..1 swing intensity for a weapon in the local player's hand (blade combo, attack swing). */
    private static float swing(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem() != stack && player.getOffhandItem() != stack) return 0;
        float attack = player.getAttackAnim(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        float k = attack > 0 ? (float) Math.sin(Math.PI * attack) : 0;
        var combat = player.getData(cn.wwl.orimod.spirit.SpiritData.COMBAT_VIEW);
        if (combat.mode().equals("blade")) {
            float total = cn.wwl.orimod.combat.SpiritCombat.duration(combat.stage());
            float t = (player.level().getGameTime() - combat.startedAt()) / total;
            if (t >= 0 && t <= 1) k = Math.max(k, (float) Math.sin(Math.PI * Math.min(1, t * 1.6f)));
        }
        return Math.clamp(k, 0, 1);
    }

    /** 0..1 wind-up of the hammer this stack belongs to (local player only), for a brighter, swelling head. */
    private static float charge(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem() || player.getUseItem() != stack || !stack.is(OriItems.SPIRIT_HAMMER)) return 0;
        return Math.min(1, player.getTicksUsingItem() / (float) cn.wwl.orimod.item.SpiritHammerItem.WIND_UP_TICKS);
    }
}
