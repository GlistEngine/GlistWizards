package com.aitial.glist.wizards;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IStartup;

/**
 * Runs on Eclipse boot. Walks the canonical Glist directory layout (resolved
 * relative to the workspace location) and imports any projects we find that
 * aren't already in the workspace.
 *
 * Delegates to {@link GlistAppImporter} for app imports so the same logic
 * (import + ensure run config) runs whether the project was just freshly
 * created by the wizard or was already on disk from a previous install.
 *
 * This is what makes the shipped workspace user-agnostic: nothing baked-in,
 * paths discovered each boot from where the bundle actually lives.
 */
public class GlistStartup implements IStartup {

	private static final ILog LOG = Platform.getLog(GlistStartup.class);

	@Override
	public void earlyStartup() {
		try {
			IWorkspaceRunnable op = this::importAll;
			ResourcesPlugin.getWorkspace().run(op, new NullProgressMonitor());
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "GlistStartup failed", e));
		}
	}

	private void importAll(IProgressMonitor monitor) {
		// Engine first (apps depend on it as a project reference).
		Path engine = GlistPaths.engineProjectDir();
		if (Files.isRegularFile(engine.resolve(".project"))) {
			try {
				GlistAppImporter.importEngine(engine, monitor);
				LOG.log(new Status(IStatus.INFO, Activator.PLUGIN_ID,
						"Auto-imported engine from " + engine));
			} catch (CoreException e) {
				LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
						"Could not import engine at " + engine, e));
			}
		}

		// Then every directory under myglistapps/ that has a .project file.
		Path apps = GlistPaths.appsRoot();
		if (!Files.isDirectory(apps)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(apps)) {
			for (Path child : stream) {
				if (!Files.isDirectory(child)) continue;
				if (!Files.isRegularFile(child.resolve(".project"))) continue;

				String name = child.getFileName().toString();
				try {
					GlistAppImporter.importOrCloneApp(name, monitor);
					LOG.log(new Status(IStatus.INFO, Activator.PLUGIN_ID,
							"Auto-imported app: " + name + " -> " + child));
				} catch (Exception e) {
					LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
							"Could not import app at " + child, e));
				}
			}
		} catch (IOException e) {
			LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
					"Could not scan apps root " + apps, e));
		}
	}
}
