package com.aitial.glist.wizards;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
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
 * This is what makes the shipped workspace user-agnostic: nothing baked-in,
 * paths discovered each boot from where the bundle actually lives.
 */
public class GlistStartup implements IStartup {

	private static final ILog LOG = Platform.getLog(GlistStartup.class);

	@Override
	public void earlyStartup() {
		try {
			IWorkspaceRunnable op = monitor -> importAll(monitor);
			ResourcesPlugin.getWorkspace().run(op, new NullProgressMonitor());
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "GlistStartup failed", e));
		}
	}

	private void importAll(IProgressMonitor monitor) {
		List<Path> candidates = new ArrayList<>();

		Path engine = GlistPaths.engineProjectDir();
		if (Files.isRegularFile(engine.resolve(".project"))) {
			candidates.add(engine);
		}

		Path apps = GlistPaths.appsRoot();
		if (Files.isDirectory(apps)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(apps)) {
				for (Path child : stream) {
					if (Files.isDirectory(child) && Files.isRegularFile(child.resolve(".project"))) {
						candidates.add(child);
					}
				}
			} catch (Exception e) {
				LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
						"Could not scan apps root " + apps, e));
			}
		}

		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		for (Path projectDir : candidates) {
			importIfMissing(root, projectDir, monitor);
		}
	}

	private void importIfMissing(IWorkspaceRoot root, Path projectDir, IProgressMonitor monitor) {
		try {
			org.eclipse.core.runtime.IPath descPath =
					org.eclipse.core.runtime.IPath.fromOSString(
							projectDir.toFile().getAbsolutePath()).append(".project");
			IProjectDescription description = ResourcesPlugin.getWorkspace().loadProjectDescription(descPath);
			String name = description.getName();

			IProject existing = root.getProject(name);
			if (existing.exists()) {
				if (!existing.isOpen()) {
					existing.open(monitor);
				}
				File existingLoc = existing.getLocation() != null ? existing.getLocation().toFile() : null;
				File wantedLoc = projectDir.toFile().getAbsoluteFile();
				if (existingLoc != null && !existingLoc.equals(wantedLoc)) {
					LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
							"Project '" + name + "' already in workspace at " + existingLoc
									+ " but expected " + wantedLoc + " — leaving as is."));
				}
				return;
			}

			existing.create(description, monitor);
			existing.open(monitor);
			LOG.log(new Status(IStatus.INFO, Activator.PLUGIN_ID,
					"Auto-imported project: " + name + " -> " + projectDir));
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
					"Could not import project at " + projectDir, e));
		}
	}
}
