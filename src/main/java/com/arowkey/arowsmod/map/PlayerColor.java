package com.arowkey.arowsmod.map;

import java.util.Locale;

public final class PlayerColor {
	private PlayerColor() {
	}

	public static String parse(String raw) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		String hex = raw.charAt(0) == '#' ? raw.substring(1) : raw;
		if (!hex.matches("[0-9a-fA-F]{6}")) {
			return null;
		}
		return "#" + hex.toLowerCase(Locale.ROOT);
	}

	public static int rgb(String color) {
		return Integer.parseInt(color.substring(1), 16);
	}
}
