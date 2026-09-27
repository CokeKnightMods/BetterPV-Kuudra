package dev.vy.betterpv.mixin;

import com.mojang.authlib.GameProfile;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import dev.vy.betterpv.client.cosmetics.CosmeticRenderer;
import dev.vy.betterpv.client.cosmetics.NameStyler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoCosmeticsMixin {
	@Shadow
	@Final
	private GameProfile profile;

	@Shadow
	private Component tabListDisplayName;

	@Unique
	private Component vypv$unstyledDisplayName;

	@Unique
	private Component vypv$bakedDisplayName;

	// A tab name baked while BetterPV owned cosmetic names must not outlive that ownership,
	// otherwise the next owner (or Off) sees stale styling until the server resends it.
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"))
	private void vypv$restoreUnstyledDisplayName(CallbackInfoReturnable<Component> cir) {
		if (vypv$bakedDisplayName == null || CosmeticRenderer.active()) return;
		if (tabListDisplayName == vypv$bakedDisplayName) {
			tabListDisplayName = vypv$unstyledDisplayName;
		}
		vypv$bakedDisplayName = null;
		vypv$unstyledDisplayName = null;
	}

	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void vypv$styleDisplayName(CallbackInfoReturnable<Component> cir) {
		Component current = cir.getReturnValue();
		Component styled = BetterPvCosmetics.styleTabDisplayName(current, profile);
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}

	@Inject(method = "setTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void vypv$styleIncomingDisplayName(Component text, CallbackInfo ci) {
		vypv$bakedDisplayName = null;
		vypv$unstyledDisplayName = null;
		if (text == null || NameStyler.hasAnimatedStyledProfile(profile)) return;

		Component styled = BetterPvCosmetics.styleTabDisplayName(text, profile);
		if (styled == text) return;

		vypv$unstyledDisplayName = text;
		vypv$bakedDisplayName = styled;
		this.tabListDisplayName = styled;
		ci.cancel();
	}

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void vypv$applyCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin current = cir.getReturnValue();
		PlayerSkin styled = BetterPvCosmetics.applyCape(current, profile);
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}
}
