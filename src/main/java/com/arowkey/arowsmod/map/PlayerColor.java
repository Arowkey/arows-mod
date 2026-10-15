package com.arowkey.arowsmod.map;

import java.util.Locale;

public final class PlayerColor {
	private PlayerColor() {
	}

	public static String parse(String raw) {
		if (raw == null || !raw.matches("#[0-9a-fA-F]{6}")) {
			return null;
		}
		return "#" + raw.substring(1).toLowerCase(Locale.ROOT);
	}

	public static int rgb(String color) {
		return Integer.parseInt(color.substring(1), 16);
	}
}
