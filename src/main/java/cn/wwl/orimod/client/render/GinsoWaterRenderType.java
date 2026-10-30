package cn.wwl.orimod.client.render;

import net.minecraft.client.renderer.RenderType;

/** Dynamic atlas mesh; avoid chunk-only translucent shader state (e.g. ChunkOffset). */
public final class GinsoWaterRenderType {
    public static final RenderType WATER = RenderType.entityTranslucentCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
    private GinsoWaterRenderType() {}
}
