package cn.wwl.orimod.client;

import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.client.render.SpiritEnergy;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class SpiritTargetOverlay {
    private static SpiritTargets.Target candidate;
    private SpiritTargetOverlay() {}

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        candidate = mc.player != null && mc.screen == null && SpiritMovement.canMove(mc.player) && (SpiritData.hasBash(mc.player) || SpiritData.hasGrapple(mc.player))
                ? SpiritTargets.find(mc.player, mc.player.getYRot(), mc.player.getXRot()) : null;
    }

    public static void tickTarget(EntityTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var state = mc.player.getData(SpiritData.MOVEMENT);
        if (state.mode == BASH_AIM && state.activeTicks > 0 && state.targetId == event.getEntity().getId()) {
            event.getEntity().setPos(state.targetPosition);
            event.getEntity().setDeltaMovement(Vec3.ZERO);
        }
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "target_hint"), (graphics, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.options.hideGui || !SpiritData.isSpirit(mc.player)) return;
            var state = mc.player.getData(SpiritData.MOVEMENT);
            boolean aiming = state.mode == BASH_AIM;
            if (!aiming && candidate == null) return;
            Component text = Component.translatable(aiming ? "hud.orimod.bash_aim" : candidate.grapple() ? "hud.orimod.grapple_target" : "hud.orimod.bash_target",
                    SpiritControls.BASH.getTranslatedKeyMessage());
            int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
            graphics.drawString(mc.font, text, x, graphics.guiHeight() / 2 + 28, aiming ? 0xFFF176 : 0xB5F3FF, true);
            if (aiming) {
                int width = (int)(48 * state.activeTicks / 12.0);
                graphics.fill(graphics.guiWidth() / 2 - 24, graphics.guiHeight() / 2 + 41,
                        graphics.guiWidth() / 2 - 24 + width, graphics.guiHeight() / 2 + 43, 0xFFFFE58A);
            }
        });
    }

    /**
     * Soft spirit-light marker instead of a line box (user feedback: "描边框，太丑了，不自然"): a slowly turning ring of
     * three light arcs under the target and a faint glow at its centre, gold while aiming a Bash, cyan for a Bash
     * target, green for a grapple point. The Bash aim arrow is a soft ribbon in the same light.
     */
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !SpiritData.isSpirit(mc.player)) return;
        var state = mc.player.getData(SpiritData.MOVEMENT);
        boolean aiming = state.mode == BASH_AIM;
        if (!aiming && candidate == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // LevelRenderer already applies the camera rotation; subtract the camera in double precision.
        Vec3 camera = event.getCamera().getPosition();
        var pose = new PoseStack().last();
        var source = mc.renderBuffers().bufferSource();
        AABB bounds = aiming ? AABB.ofSize(state.targetPosition, .65, .65, .65) : candidate.bounds();
        if (aiming && state.targetId >= 0) {
            var target = mc.level.getEntity(state.targetId);
            if (target != null) bounds = target.getBoundingBox();
        }
        boolean grapple = !aiming && candidate.grapple();
        int color = aiming ? 0xFFE58A : grapple ? 0x9CFFC0 : 0x8CEBFF;
        // Fade with distance so far targets do not shout; aiming stays fully lit.
        float distance = (float) Math.sqrt(bounds.getCenter().distanceToSqr(camera));
        float strength = aiming ? 1 : Math.clamp(1.25f - distance / 24f, .35f, 1);
        float time = (mc.level.getGameTime() + partial) / 20f;
        double radius = Math.max(bounds.getXsize(), bounds.getZsize()) / 2 + .32;
        Vec3 base = new Vec3(bounds.getCenter().x, bounds.minY + .04, bounds.getCenter().z);
        var ring = source.getBuffer(SpiritEnergy.ribbon());
        int segments = 30;
        for (int arc = 0; arc < 3; arc++) {
            double spin = time * (aiming ? 2.4 : 1.1) + arc * Math.PI * 2 / 3;
            for (int i = 0; i < segments / 3 - 2; i++) {
                double a0 = spin + i * Math.PI * 2 / segments, a1 = spin + (i + 1) * Math.PI * 2 / segments;
                float fade0 = (float) Math.sin(Math.PI * i / (segments / 3 - 2)), fade1 = (float) Math.sin(Math.PI * (i + 1) / (segments / 3 - 2));
                SpiritEnergy.strip(ring, pose, ringPoint(base, a0, radius - .12, camera), ringPoint(base, a0, radius + .12, camera),
                        ringPoint(base, a1, radius - .12, camera), ringPoint(base, a1, radius + .12, camera), color, .75f * strength * fade0, .75f * strength * fade1);
            }
        }
        if (aiming) {
            Vec3 direction = mc.player.getLookAngle();
            // Offset in camera space so the forward arrow does not collapse onto the target/crosshair in first person.
            var offset = event.getCamera().rotation().transform(new org.joml.Vector3f(.45f, .30f, 0));
            Vec3 start = bounds.getCenter().add(offset.x, offset.y, offset.z);
            for (var quad : cn.wwl.orimod.client.render.BashArrowShape.quads(start, direction))
                SpiritEnergy.strip(ring, pose, quad.a().subtract(camera).toVector3f(), quad.b().subtract(camera).toVector3f(),
                        quad.c().subtract(camera).toVector3f(), quad.d().subtract(camera).toVector3f(), color, .7f, .85f);
        }
        source.endBatch(SpiritEnergy.ribbon());
        var glow = source.getBuffer(SpiritEnergy.glow());
        float pulse = .8f + .2f * (float) Math.sin(time * 5);
        SpiritEnergy.orb(glow, pose, bounds.getCenter().subtract(camera).toVector3f(), (float) radius * 1.1f, color, .16f * strength * pulse);
        source.endBatch(SpiritEnergy.glow());
    }

    private static org.joml.Vector3f ringPoint(Vec3 base, double angle, double radius, Vec3 camera) {
        return base.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius).subtract(camera).toVector3f();
    }

}
