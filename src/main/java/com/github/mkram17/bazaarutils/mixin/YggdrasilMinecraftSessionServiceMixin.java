package com.github.mkram17.bazaarutils.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//thanks skyblocker
@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public class YggdrasilMinecraftSessionServiceMixin {

	//TODO perhaps investigate if we could fix this
	@WrapWithCondition(method = "unpackTextures", remap = false, at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V", ordinal = 0, remap = false))
	private boolean skyblocker$dontLogIncorrectEndingByteExceptions(Logger logger, String message, Throwable throwable) {
//		return !Utils.isOnHypixel() && throwable instanceof IllegalArgumentException;
		return false;
	}
}
