package dev.vy.betterpv.client;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Opens {@code /pv} from chat the same way SkyBlock Profile Viewer does:
 *
 * <ol>
 *   <li>Remap existing Hypixel {@code /socialoptions} / {@code /viewprofile} clicks (lobby-only
 *       on Hypixel) to {@code /pv &lt;name&gt;} so they work in SkyBlock.</li>
 *   <li>Optionally make whole {@code Party|Guild|Officer|Co-op > Name: ...} lines clickable.</li>
 * </ol>
 *
 * <p>Never invents clicks from arbitrary text (that falsely matched mod banners like
 * {@code [Detexturify] Update available: ...}).
 *
 * <p>Wired via Fabric {@code MODIFY_GAME}.
 */
public final class ChatClickProcessor {
	private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern UUID_PATTERN = Pattern.compile(
		"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
	);
	/** Same hover Hypixel puts on SocialOptions / viewprofile name spans. */
	private static final Pattern VIEW_PROFILE_HOVER = Pattern.compile(
		"(?i)^Click here to view ([A-Za-z0-9_]{1,16})['\u2019\u02BC\u2018]?s profile$"
	);
	/**
	 * SkyBlock PV {@code otherChatRegex}: channel chat only.
	 * {@code Party > [MVP+] Name [Elite]: hello}
	 */
	private static final Pattern CHANNEL_CHAT = Pattern.compile(
		"(?i)^(?:Party|Guild|Officer|Co-op) > (?:\\[[^\\]]*\\]\\s*)?([A-Za-z0-9_]{1,16})(?:\\s*\\[[^\\]]*\\])?: .+"
	);

	private ChatClickProcessor() {
	}

	public static Component process(Component component) {
		if (component == null) {
			return null;
		}

		Component remapped = remapSocialOptions(component);
		if (remapped != component) {
			return remapped;
		}
		return maybeChannelChatClick(component);
	}

	/**
	 * Walk siblings (preserving structure/colors) and swap Hypixel profile clicks to /pv.
	 * Mirrors meowdding/skyblock-pv {@code ClickableChatMessages.onAllChat}.
	 */
	private static Component remapSocialOptions(Component component) {
		List<Component> siblings = component.getSiblings();
		if (siblings.isEmpty()) {
			return component;
		}

		MutableComponent output = component.copy();
		output.getSiblings().clear();
		boolean changed = false;

		for (Component sibling : siblings) {
			String command = runCommandOf(sibling.getStyle());
			String hoverText = hoverPlain(sibling.getStyle());
			boolean hypixelProfile = isHypixelProfileCommand(command) && hoverText != null;
			if (!hypixelProfile) {
				output.append(sibling);
				continue;
			}

			String username = usernameFromSocial(command, hoverText);
			if (username == null) {
				output.append(sibling);
				continue;
			}

			MutableComponent nameComponent = sibling.copy();
			nameComponent.setStyle(pvStyle(nameComponent.getStyle(), username));
			output.append(nameComponent);
			changed = true;
		}

		return changed ? output : component;
	}

	/**
	 * SkyBlock PV {@code onOtherChat}: whole-line click for channel messages only.
	 */
	private static Component maybeChannelChatClick(Component component) {
		String plain = component.getString();
		if (plain == null || plain.isBlank()) {
			return component;
		}
		Matcher matcher = CHANNEL_CHAT.matcher(plain.trim());
		if (!matcher.matches()) {
			return component;
		}
		String username = matcher.group(1);
		if (username == null || !PLAYER_NAME_PATTERN.matcher(username).matches()) {
			return component;
		}
		if (alreadyHasPvClick(component)) {
			return component;
		}
		MutableComponent copy = component.copy();
		copy.setStyle(pvStyle(copy.getStyle(), username));
		return copy;
	}

	private static boolean alreadyHasPvClick(Component component) {
		ClickEvent click = component.getStyle().getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			String cmd = normalizeCommand(run.command());
			return cmd != null && (cmd.equals("pv") || cmd.startsWith("pv ") || cmd.startsWith("betterpv pv"));
		}
		return false;
	}

	private static boolean isHypixelProfileCommand(String command) {
		if (command == null) {
			return false;
		}
		String lower = command.toLowerCase(Locale.ROOT);
		return lower.startsWith("/socialoptions") || lower.startsWith("socialoptions")
			|| lower.startsWith("/viewprofile") || lower.startsWith("viewprofile");
	}

	private static String runCommandOf(Style style) {
		if (style == null) {
			return null;
		}
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			return run.command();
		}
		return null;
	}

	private static String hoverPlain(Style style) {
		if (style == null) {
			return null;
		}
		HoverEvent hover = style.getHoverEvent();
		if (hover instanceof HoverEvent.ShowText show) {
			return show.value().getString();
		}
		return null;
	}

	/**
	 * SkyBlock PV {@code getUsername}: socialoptions arg, else hover profile line.
	 */
	private static String usernameFromSocial(String command, String hoverText) {
		String fromCommand = usernameFromCommand(command);
		if (fromCommand != null) {
			return fromCommand;
		}
		if (hoverText == null || hoverText.isBlank()) {
			return null;
		}
		for (String line : hoverText.split("\\R")) {
			Matcher matcher = VIEW_PROFILE_HOVER.matcher(line.trim());
			if (matcher.matches()) {
				String name = matcher.group(1);
				if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
					return name;
				}
			}
		}
		return null;
	}

	/** Used by {@link ProfileViewerOpener} when clicking an unmapped Hypixel style. */
	static String usernameFromHypixelStyle(Style style) {
		if (style == null) {
			return null;
		}
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			String name = usernameFromCommand(run.command());
			if (name != null) {
				return name;
			}
		}
		if (click instanceof ClickEvent.SuggestCommand suggest) {
			String name = usernameFromCommand(suggest.command());
			if (name != null) {
				return name;
			}
		}

		String hoverText = hoverPlain(style);
		if (hoverText != null) {
			for (String line : hoverText.split("\\R")) {
				Matcher matcher = VIEW_PROFILE_HOVER.matcher(line.trim());
				if (matcher.find()) {
					String name = matcher.group(1);
					if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
						return name;
					}
				}
			}
		}
		return null;
	}

	static String usernameFromCommand(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		String rest;
		if (lower.startsWith("socialoptions")) {
			rest = trimmed.substring("socialoptions".length()).trim();
		} else if (lower.startsWith("viewprofile")) {
			rest = trimmed.substring("viewprofile".length()).trim();
		} else {
			return null;
		}
		if (rest.isEmpty()) {
			return null;
		}
		// Prefer the first non-UUID token (Hypixel sometimes sends uuid, or uuid + name).
		for (String token : rest.split("\\s+")) {
			if (token.isEmpty() || UUID_PATTERN.matcher(token).matches()) {
				continue;
			}
			if (PLAYER_NAME_PATTERN.matcher(token).matches()) {
				return token;
			}
		}
		return null;
	}

	private static String normalizeCommand(String command) {
		if (command == null) {
			return null;
		}
		String t = command.trim();
		if (t.startsWith("/")) {
			t = t.substring(1);
		}
		return t.toLowerCase(Locale.ROOT);
	}

	private static Style pvStyle(Style style, String name) {
		Component tip = Component.translatable("betterpv.chat.click_pv", name);
		Style base = style == null ? Style.EMPTY : style;
		return base
			.withClickEvent(new ClickEvent.RunCommand("/pv " + name))
			.withHoverEvent(new HoverEvent.ShowText(tip));
	}
}
