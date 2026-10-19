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
        }, OriItems.SPIRIT_HAMMER.get(), OriItems.SPIRIT_SPEAR.get(), OriItems.SPIRIT_BLADE.get());
    }

    private static Vector3f p(float x, float y) { return new Vector3f(x, y, .5f); }
    private static final Vector3f ACROSS = new Vector3f(-1, 1, 0);

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        float t = SpiritEnergy.time();
        float pulse = .88f + .12f * (float) Math.sin(t * 2.4f);
        float charge = charge(stack);
        var pose = poses.last();
        var streak = buffers.getBuffer(SpiritEnergy.streak());
        var glow = buffers.getBuffer(SpiritEnergy.glow());
        float a = pulse * (1 + .5f * charge);
        if (stack.is(OriItems.SPIRIT_HAMMER)) {
            // Shaft, then a broad crossbar head of denser light with a halo that swells while charging.
            SpiritEnergy.beam(streak, pose, p(.10f, .10f), p(.74f, .74f), .26f, ACROSS, MID, .32f * a);
            SpiritEnergy.beam(streak, pose, p(.10f, .10f), p(.74f, .74f), .10f, ACROSS, CORE, .95f);
            SpiritEnergy.beam(streak, pose, p(.48f, 1.0f), p(1.0f, .48f), .52f, new Vector3f(1, 1, 0), OUTER, .30f * a);
            SpiritEnergy.beam(streak, pose, p(.52f, .96f), p(.96f, .52f), .26f, new Vector3f(1, 1, 0), CORE, .92f);
            SpiritEnergy.orb(glow, pose, p(.74f, .74f), .30f + .12f * charge, MID, .42f * a);
            SpiritEnergy.orb(glow, pose, p(.12f, .12f), .08f, CORE, .7f);
            motes(glow, pose, t, .12f, .74f, 3, .05f + .03f * charge, a);
        } else if (stack.is(OriItems.SPIRIT_SPEAR)) {
            SpiritEnergy.beam(streak, pose, p(.02f, .02f), p(.82f, .82f), .20f, ACROSS, MID, .30f * a);
            SpiritEnergy.beam(streak, pose, p(.02f, .02f), p(.82f, .82f), .07f, ACROSS, CORE, .95f);
            SpiritEnergy.beam(streak, pose, p(.70f, .70f), p(1.0f, 1.0f), .17f, ACROSS, CORE, .95f);
            SpiritEnergy.beam(streak, pose, p(.66f, .80f), p(.80f, .66f), .10f, new Vector3f(1, 1, 0), MID, .8f);
            SpiritEnergy.orb(glow, pose, p(.90f, .90f), .16f, MID, .5f * a);
            motes(glow, pose, t, .05f, .95f, 4, .04f, a);
        } else {
            // Spirit Edge: grip, a small guard and a long blade of light.
            SpiritEnergy.beam(streak, pose, p(.18f, .18f), p(.43f, .43f), .08f, ACROSS, MID, .8f);
            SpiritEnergy.beam(streak, pose, p(.35f, .53f), p(.53f, .35f), .09f, new Vector3f(1, 1, 0), CORE, .85f);
            SpiritEnergy.beam(streak, pose, p(.42f, .42f), p(.98f, .98f), .40f, ACROSS, MID, .28f * a);
            SpiritEnergy.beam(streak, pose, p(.42f, .42f), p(.98f, .98f), .17f, ACROSS, CORE, .92f);
            SpiritEnergy.orb(glow, pose, p(.18f, .18f), .07f, CORE, .7f);
            motes(glow, pose, t, .45f, .95f, 3, .04f, a);
        }
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

    /** 0..1 wind-up of the hammer this stack belongs to (local player only), for a brighter, swelling head. */
    private static float charge(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem() || player.getUseItem() != stack || !stack.is(OriItems.SPIRIT_HAMMER)) return 0;
        return Math.min(1, player.getTicksUsingItem() / (float) cn.wwl.orimod.item.SpiritHammerItem.WIND_UP_TICKS);
    }
}
