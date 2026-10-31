package cn.wwl.orimod.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Interior timber: fine, subdued sap lines; small luminous knots provide actual stair lighting. */
public final class GreatSpiritRootBlock extends Block {
    public enum Light implements StringRepresentable {
        NONE(0), VEIN(6), KNOT(14);
        public final int emission;
        Light(int emission){this.emission=emission;}
        @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
    }
    public static final EnumProperty<Light> LIGHT=EnumProperty.create("light",Light.class);
    public GreatSpiritRootBlock(Properties properties){super(ProtectedBlock.durable(properties));registerDefaultState(stateDefinition.any().setValue(LIGHT,Light.NONE));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(LIGHT);}
}
