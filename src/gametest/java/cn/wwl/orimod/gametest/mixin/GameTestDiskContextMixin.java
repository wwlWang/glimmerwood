package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.GameTestDiskBackpressure;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameTestInfo.class)
public abstract class GameTestDiskContextMixin {
    @WrapMethod(method="tick")
    private void orimod$testContext(GameTestRunner runner,Operation<Void> original) {
        String previous=GameTestDiskBackpressure.test(((GameTestInfo)(Object)this).getTestName());
        try { original.call(runner); }
        finally { GameTestDiskBackpressure.test(previous); }
    }
}
