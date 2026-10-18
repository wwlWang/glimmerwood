package cn.wwl.orimod.client.render;

/** Optional Iris bridge; a shader-free installation does not load Iris classes. */
final class SpiritShaders {
    private static final java.lang.reflect.Method INSTANCE, IN_USE;
    static {
        java.lang.reflect.Method instance = null, inUse = null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            instance = api.getMethod("getInstance"); inUse = api.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException ignored) { }
        INSTANCE = instance; IN_USE = inUse;
    }
    private SpiritShaders() { }
    static boolean active() {
        if (INSTANCE == null) return false;
        try { return (boolean)IN_USE.invoke(INSTANCE.invoke(null)); }
        catch (ReflectiveOperationException ignored) { return false; }
    }
}
