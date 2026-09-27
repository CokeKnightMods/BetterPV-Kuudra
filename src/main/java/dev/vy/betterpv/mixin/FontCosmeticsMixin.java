package dev.vy.betterpv.mixin;

import dev.vy.betterpv.client.cosmetics.NameStyler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Chat lines and Hypixel's tab/scoreboard entries are plain text rather than player profiles,
// so cosmetic names there can only be styled where the text is drawn and measured.
@Mixin(Font.class)
public abstract class FontCosmeticsMixin {
	@Unique
	private static final ThreadLocal<Integer> vypv$decorationDepth = ThreadLocal.withInitial(() -> 0);

	@Unique
	private static boolean vypv$shouldDecorateRenderedText() {
		if (!NameStyler.hasGradientStyles()) return false;

		Minecraft client = Minecraft.getInstance();
		return client != null
			&& client.level != null
			&& client.player != null
			&& vypv$decorationDepth.get() == 0;
	}

	@Unique
	private static boolean vypv$shouldDecorateMeasuredText() {
		if (!NameStyler.hasGradientStyles() && !NameStyler.hasChatHeaderStyles()) return false;

		Minecraft client = Minecraft.getInstance();
		return client != null
			&& client.level != null
			&& client.player != null
			&& vypv$decorationDepth.get() == 0;
	}

	@Unique
	private static <T> T vypv$decorateSafely(java.util.function.Supplier<T> action) {
		vypv$decorationDepth.set(vypv$decorationDepth.get() + 1);
		try {
			return action.get();
		} finally {
			int depth = vypv$decorationDepth.get() - 1;
			if (depth <= 0) {
				vypv$decorationDepth.remove();
			} else {
				vypv$decorationDepth.set(depth);
			}
		}
	}

	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private String vypv$decoratePreparedString(String text) {
		if (!vypv$shouldDecorateRenderedText()) return text;
		return vypv$decorateSafely(() -> {
			String styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToString(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToString(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence vypv$decoratePreparedOrderedText(FormattedCharSequence text) {
		if (text == null || !vypv$shouldDecorateRenderedText()) return text;
		return vypv$decorateSafely(() -> {
			FormattedCharSequence styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToOrderedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToOrderedText(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String vypv$decorateMeasuredString(String text) {
		if (!vypv$shouldDecorateMeasuredText()) return text;
		return vypv$decorateSafely(() -> {
			String styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToString(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToString(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText vypv$decorateMeasuredText(FormattedText text) {
		if (text == null || !vypv$shouldDecorateMeasuredText()) return text;
		return vypv$decorateSafely(() -> {
			FormattedText styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToFormattedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToFormattedText(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence vypv$decorateMeasuredOrderedText(FormattedCharSequence text) {
		if (text == null || !vypv$shouldDecorateMeasuredText()) return text;
		return vypv$decorateSafely(() -> {
			FormattedCharSequence styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToOrderedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToOrderedText(styled);
			}
			return styled;
		});
	}
}
