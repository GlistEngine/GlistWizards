package com.aitial.glist.wizards;

import java.io.File;

/**
 * Picks the next available folder name under apps root (e.g. GlistApp,
 * GlistApp-2, GlistApp-3, ...). Used by the wizard so the user gets a sane
 * default name without having to know what's already there.
 */
final class GlistAppNaming {

	private GlistAppNaming() {}

	static String nextAvailable(String baseName) {
		File root = GlistPaths.appsRoot().toFile();
		if (!root.exists()) root.mkdirs();

		if (!new File(root, baseName).exists()) {
			return baseName;
		}
		int counter = 2;
		while (new File(root, baseName + "-" + counter).exists()) {
			counter++;
		}
		return baseName + "-" + counter;
	}
}
