package cn.wwl.orimod.client;

import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
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

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !SpiritData.isSpirit(mc.player)) return;
        var state = mc.player.getData(SpiritData.MOVEMENT);
        boolean aiming = state.mode == BASH_AIM;
        if (!aiming && candidate == null) return;
        var poses = new PoseStack();
        // LevelRenderer already applies the camera matrix to RenderSystem at this stage.
        Vec3 camera = event.getCamera().getPosition();
        poses.translate(-camera.x, -camera.y, -camera.z);
        var source = mc.renderBuffers().bufferSource();
        var vertices = source.getBuffer(RenderType.lines());
        AABB bounds = aiming ? AABB.ofSize(state.targetPosition, .65, .65, .65) : candidate.bounds();
        if (aiming && state.targetId >= 0) {
            var target = mc.level.getEntity(state.targetId);
            if (target != null) bounds = target.getBoundingBox().inflate(.08);
        }
        boolean grapple = !aiming && candidate.grapple();
        LevelRenderer.renderLineBox(poses, vertices, bounds, aiming ? 1 : grapple ? .45f : .55f, 1, aiming ? .45f : grapple ? .55f : 1, 1);
        if (aiming) {
            Vec3 direction = mc.player.getLookAngle();
            Vec3 across = direction.cross(new Vec3(0, 1, 0));
            if (across.lengthSqr() < .01) across = new Vec3(1, 0, 0);
            across = across.normalize();
            Vec3 up = across.cross(direction).normalize();
            Vec3 start = mc.options.getCameraType().isFirstPerson()
                    ? camera.add(direction.scale(1.8)).add(across.scale(.5)).add(up.scale(.35))
                    : mc.player.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false))
                        .add(0, mc.player.getBbHeight() + .55, 0).add(direction.scale(.5));
            Vec3 end = start.add(direction.scale(2));
            across = across.scale(.22);
            line(vertices, poses.last(), start, end);
            line(vertices, poses.last(), end, end.subtract(direction.scale(.4)).add(across));
            line(vertices, poses.last(), end, end.subtract(direction.scale(.4)).subtract(across));
            line(vertices, poses.last(), end, end.subtract(direction.scale(.4)).add(up.scale(.22)));
            line(vertices, poses.last(), end, end.subtract(direction.scale(.4)).subtract(up.scale(.22)));
        }
        source.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer vertices, PoseStack.Pose pose, Vec3 start, Vec3 end) {
        Vec3 normal = end.subtract(start).normalize();
        vertices.addVertex(pose, (float)start.x, (float)start.y, (float)start.z).setColor(1f, .94f, .45f, 1f)
                .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
        vertices.addVertex(pose, (float)end.x, (float)end.y, (float)end.z).setColor(1f, .94f, .45f, 1f)
                .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
    }
}
