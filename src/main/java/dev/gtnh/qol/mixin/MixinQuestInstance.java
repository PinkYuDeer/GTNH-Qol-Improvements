package dev.gtnh.qol.mixin;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import betterquesting.questing.QuestInstance;
import dev.gtnh.qol.quest.QuestDetectorSubmitter;

@Mixin(value = QuestInstance.class, remap = false)
public abstract class MixinQuestInstance {

    @Inject(method = "detect", at = @At("HEAD"))
    private void gtnhQol$submitFromMeNetwork(EntityPlayer player, CallbackInfo callback) {
        QuestDetectorSubmitter.submit((QuestInstance) (Object) this, player);
    }
}
