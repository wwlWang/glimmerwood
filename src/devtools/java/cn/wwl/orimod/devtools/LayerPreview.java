package cn.wwl.orimod.devtools;

import cn.wwl.orimod.client.render.SpiritPlayerVisual;
import cn.wwl.orimod.spirit.SpiritData;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only visual fixtures; these never join a server or enter its save data. */
final class LayerPreview {
    private static ClientLevel level;
    private static final Map<String, PreviewPlayer> PLAYERS = new LinkedHashMap<>();

    static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (level != Minecraft.getInstance().level) { PLAYERS.clear(); level = null; }
    }

    static Map<String, Object> configure(JsonObject action) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw new IllegalStateException("World required");
        if (level != mc.level) { PLAYERS.clear(); level = mc.level; }
        String name = action.has("name") ? action.get("name").getAsString() : "Ori layers";
        if (action.has("enabled") && !action.get("enabled").getAsBoolean()) {
            var removed = PLAYERS.remove(name);
            if (removed != null) level.removeEntity(removed.getId(), Entity.RemovalReason.DISCARDED);
            return Map.of("removed", removed != null);
        }
        PreviewPlayer player = PLAYERS.get(name);
        if (player == null) {
            if (PLAYERS.size() >= 4) throw new IllegalStateException("At most four layer previews");
            player = new PreviewPlayer(level, new GameProfile(UUID.randomUUID(), name));
            player.setId(900_000 + PLAYERS.size());
            player.setPos(mc.player.getX() + 2, mc.player.getY(), mc.player.getZ());
            player.setNoGravity(true);
            player.setData(SpiritData.FORM, true);
            player.setData(SpiritData.AWAKENED, true);
            level.addEntity(player);
            PLAYERS.put(name, player);
        }
        if (action.has("x")) player.setPos(action.get("x").getAsDouble(), action.get("y").getAsDouble(), action.get("z").getAsDouble());
        if (action.has("yaw")) {
            float yaw = action.get("yaw").getAsFloat();
            player.setYRot(yaw); player.yRotO = yaw; player.yBodyRot = player.yBodyRotO = yaw;
            player.setYHeadRot(yaw); player.yHeadRotO = yaw;
        }
        if (action.has("spirit")) player.setData(SpiritData.FORM, action.get("spirit").getAsBoolean());
        player.refreshDimensions();
        if (action.has("glowing")) player.glowing(action.get("glowing").getAsBoolean());
        if (action.has("invisible")) player.setInvisible(action.get("invisible").getAsBoolean());
        if (action.has("arrows")) player.setArrowCount(action.get("arrows").getAsInt());
        if (action.has("stingers")) player.setStingerCount(action.get("stingers").getAsInt());
        if (action.has("cape")) player.cape = action.get("cape").getAsBoolean();
        if (action.has("shoulders")) player.shoulders(action.get("shoulders").getAsBoolean());
        if (action.has("nameTag") || action.has("teamColor")) {
            var scoreboard = level.getScoreboard();
            var team = scoreboard.getPlayerTeam("ori_layer_preview");
            if (team == null) team = scoreboard.addPlayerTeam("ori_layer_preview");
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            if (action.has("nameTag")) team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.valueOf(action.get("nameTag").getAsString()));
            if (action.has("teamColor")) team.setColor(net.minecraft.ChatFormatting.valueOf(action.get("teamColor").getAsString()));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String key = slot.getName();
            if (action.has(key)) player.setItemSlot(slot, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(action.get(key).getAsString()))));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("id", player.getId()); result.put("name", name); result.put("spirit", SpiritData.isSpirit(player));
        result.put("arrows", player.getArrowCount()); result.put("stingers", player.getStingerCount());
        result.put("glowing", player.isCurrentlyGlowing()); result.put("invisible", player.isInvisible());
        result.put("visual", SpiritPlayerVisual.diagnostics(player));
        return result;
    }

    private static final class PreviewPlayer extends RemotePlayer {
        boolean cape;
        PreviewPlayer(ClientLevel level, GameProfile profile) { super(level, profile); }
        void glowing(boolean value) { setSharedFlag(6, value); }
        void shoulders(boolean value) {
            CompoundTag left = new CompoundTag(), right = new CompoundTag();
            if (value) { left.putString("id", "minecraft:parrot"); left.putInt("Variant", 0); right.putString("id", "minecraft:parrot"); right.putInt("Variant", 2); }
            setShoulderEntityLeft(left); setShoulderEntityRight(right);
        }
        @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }
        @Override public PlayerSkin getSkin() {
            PlayerSkin skin = Minecraft.getInstance().player.getSkin();
            return new PlayerSkin(skin.texture(), skin.textureUrl(), cape ? ResourceLocation.fromNamespaceAndPath("orimod_devtools", "textures/layer_test_cape.png") : null,
                    null, skin.model(), skin.secure());
        }
    }
}
