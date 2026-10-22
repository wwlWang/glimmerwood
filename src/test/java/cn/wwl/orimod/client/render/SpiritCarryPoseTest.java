package cn.wwl.orimod.client.render;

import com.google.gson.JsonParser;
import com.wf.gemrender.gltf.NodeTable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SpiritCarryPoseTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void actualRigCarriesOneHandedWeaponsBelowChestAndForward(boolean left) throws Exception {
        // Use the committed rig's rest transforms, not a hand-built substitute for its unusual bone axes.
        byte[] glb = Files.readAllBytes(Path.of(System.getProperty("orimod.projectDir"), "src/main/resources/assets/orimod/models/entity/ori.glb"));
        int length = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        var nodes = JsonParser.parseString(new String(glb, 20, length, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("nodes");
        int count = nodes.size();
        var names = new String[count]; var parents = new int[count]; Arrays.fill(parents, -1);
        var rest = new float[count * NodeTable.TRS_STRIDE];
        for (int i = 0; i < count; i++) {
            var n = nodes.get(i).getAsJsonObject(); names[i] = n.has("name") ? n.get("name").getAsString() : "node_" + i;
            assertFalse(n.has("matrix"), "Rig changed to matrix transforms; update this reader");
            for (int field = 0; field < 3; field++) {
                String key = new String[]{"translation", "rotation", "scale"}[field];
                int offset = new int[]{NodeTable.TRANSLATION, NodeTable.ROTATION, NodeTable.SCALE}[field];
                for (int k = 0; k < (field == 1 ? 4 : 3); k++)
                    rest[i * NodeTable.TRS_STRIDE + offset + k] = n.has(key) ? n.getAsJsonArray(key).get(k).getAsFloat()
                            : field == 2 || field == 1 && k == 3 ? 1 : 0;
            }
            if (n.has("children")) for (var child : n.getAsJsonArray("children")) parents[child.getAsInt()] = i;
        }
        var table = NodeTable.ofNodes(names, parents, rest);
        var pose = new SpiritPoseLayer("carry_test"); pose.bind(table);
        SpiritPoses.lowered(pose, left ? 1 : 0, 0, 1); pose.publish();
        var state = table.newScratch(); table.resetToRest(state);
        for (int bone = 0; bone < SpiritPoseLayer.BONES.length; bone++) {
            int slot = table.slotOfName(SpiritPoseLayer.BONES[bone]);
            if (slot >= 0) table.setRotation(state, slot, pose.target(bone));
        }
        var world = new Matrix4f[count];
        for (int slot : table.evaluationOrder()) {
            var local = new Matrix4f(); table.localTransform(state, slot, local);
            world[slot] = parents[slot] < 0 ? local : new Matrix4f(world[parents[slot]]).mul(local);
        }
        var palm = world[table.slotOfName(left ? "grip_r" : "grip_l")];
        assertTrue(palm.m31() > .15 && palm.m31() < .4, "Palm must rest beside the thigh, not at the chest: " + palm.m31());
        // Left display mirrors its Y/Z angles, so both handheld displays end with the same local rotation.
        var direction = new Matrix4f().rotate(palm.getUnnormalizedRotation(new Quaternionf()))
                .rotateX((float)Math.toRadians(SpiritItemGrip.attachmentPitch(1, 1))).rotateY((float)Math.PI)
                .rotateY(-(float)Math.PI / 2).rotateZ((float)Math.toRadians(55))
                .transformDirection(new Vector3f(1, 1, 0)).normalize();
        assertTrue(direction.y < -.1 && direction.z > .7, "Tip must point forward/down: " + direction);
        assertTrue(palm.m31() + direction.y * .55f > 0, "Tip clips the ground");
        assertEquals(-90, SpiritItemGrip.attachmentPitch(0, 1), "Attack/throw clips retain their socket calibration");
    }
}
