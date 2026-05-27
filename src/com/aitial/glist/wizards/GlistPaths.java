package com.aitial.glist.wizards;

import java.nio.file.Path;

import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Resolves Glist canonical paths at runtime from the workspace location.
 *
 * Shipped layout (under any user home):
 *   <GLIST_HOME>/
 *     GlistEngine/engine/.project
 *     myglistapps/<AppName>/.project
 *     zbin/<glistzbin-os>/eclipse/
 *       eclipsecpp{,-arm64,-x86_64}/...
 *       workspace/                  <-- ResourcesPlugin location
 *
 * GLIST_HOME = workspace/../../../..
 * apps root  = GLIST_HOME/myglistapps
 * engine     = GLIST_HOME/GlistEngine/engine
 */
public final class GlistPaths {

	private GlistPaths() {}

	public static Path workspace() {
		return ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
	}

	public static Path glistHome() {
		return workspace().resolve("../../../..").normalize();
	}

	public static Path appsRoot() {
		return glistHome().resolve("myglistapps");
	}

	public static Path engineProjectDir() {
		return glistHome().resolve("GlistEngine/engine");
	}

	public static Path pluginsRoot() {
		return glistHome().resolve("glistplugins");
	}
}
