package io.ejekta.bountiful.mixin;

import io.ejekta.bountiful.content.MixinHelper;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public class BountifulVillagerBrainMixin {
    @Inject(method = "refreshBrain", at = @At("RETURN"))
    private void bo_onRefreshBrain(CallbackInfo ci) {
        MixinHelper.INSTANCE.ensureBoardMemoryModule((Villager)(Object)this);
    }
}
