package cn.wwl.orimod.progression;

/** Stable save/wire bits, independent of ability and shard masks. */
public enum WillWisp {
    HEART(1,0xFFD58A), WING(2,0xC6A0FF), EARTH(4,0x75E8B0);
    public final int bit, color;
    WillWisp(int bit,int color){this.bit=bit;this.color=color;}
    public String id(){return name().toLowerCase(java.util.Locale.ROOT);}
    public static WillWisp byId(String id){for(var w:values())if(w.id().equals(id))return w;return null;}
    public static int purifications(SpiritProgress p){return (int)Math.min(3,p.rewards().entrySet().stream().filter(e->e.getKey().startsWith("purify/") && e.getValue()>0).count());}
}
