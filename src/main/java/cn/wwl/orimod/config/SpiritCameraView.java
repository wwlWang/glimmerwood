package cn.wwl.orimod.config;

/**
 * Spirit-form camera choice. Names equal vanilla {@code CameraType} names, which is how older
 * files stored the value as text, so existing configs load unchanged.
 */
public enum SpiritCameraView implements net.neoforged.neoforge.common.TranslatableEnum {
    FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_FRONT;

    @Override public net.minecraft.network.chat.Component getTranslatedName() {
        return net.minecraft.network.chat.Component.translatable("orimod.camera." + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** @return the view for a vanilla camera name, or the default for anything else. */
    public static SpiritCameraView parse(String name) {
        if (name != null) for (var view : values()) if (view.name().equals(name)) return view;
        return THIRD_PERSON_BACK;
    }
}
