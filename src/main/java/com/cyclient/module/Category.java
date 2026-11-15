package com.cyclient.module;

/** The tabs in the Cy Client menu. */
public enum Category {
	HUD("HUD"),
	VISUAL("Visual"),
	UTILITY("Utility");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
