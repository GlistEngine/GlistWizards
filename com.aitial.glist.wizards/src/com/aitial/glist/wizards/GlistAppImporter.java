package com.aitial.glist.wizards;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

/**
 * Single entry point for getting a GlistApp into the workspace.
 *
 * Whether it's the wizard creating a brand new app, or the startup hook
 * adopting an app the user already has on disk, this is the path that runs:
 *
 *   1. If the directory doesn't exist yet, clone from upstream and reset the
 *      git history so the user starts at "Initial commit".
 *   2. Load the project description and create/open the IProject (idempotent).
 *   3. Ensure a CDT Run Configuration matching the app exists.
 *
 * GlistEngine (engine) goes through {@link #importEngine(Path, IProgressMonitor)}
 * which does step 2 only — no clone, no run config.
 */
public final class GlistAppImporter {

	public static final String GLISTAPP_REPO =
			"https://github.com/GlistEngine/GlistApp.git";

	private GlistAppImporter() {}

	/**
	 * Ensure a GlistApp project exists at <appsRoot>/<name>, cloning if absent,
	 * importing if not yet in the workspace, and ensuring a run config.
	 */
	public static IProject importOrCloneApp(String name, IProgressMonitor monitor)
			throws CoreException, IOException, InterruptedException {
		return importOrCloneApp(name, null, monitor);
	}

	/**
	 * Same as {@link #importOrCloneApp(String, IProgressMonitor)} but also
	 * injects the given plugin list into the new project's CMakeLists.txt
	 * {@code set(PLUGINS ...)} line. {@code plugins == null} means no edit.
	 */
	public static IProject importOrCloneApp(String name, List<String> plugins,
			IProgressMonitor monitor)
			throws CoreException, IOException, InterruptedException {

		Path appsRoot = GlistPaths.appsRoot();
		Files.createDirectories(appsRoot);
		File dest = appsRoot.resolve(name).toFile();

		boolean justCloned = false;
		if (!dest.exists()) {
			cloneFresh(dest);
			justCloned = true;
		}
		if (justCloned && plugins != null && !plugins.isEmpty()) {
			injectPluginsIntoCMake(new File(dest, "CMakeLists.txt"), plugins);
		}
		IProject project = ensureImported(dest, monitor);
		ensureRunConfiguration(project);
		return project;
	}

	private static void injectPluginsIntoCMake(File cmakeFile, List<String> plugins)
			throws IOException {
		if (!cmakeFile.exists() || plugins.isEmpty()) return;
		Path path = cmakeFile.toPath();
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		List<String> out = new ArrayList<>(lines.size());
		String pluginList = String.join(" ", plugins);
		for (String line : lines) {
			if (line.contains("set(PLUGINS")) {
				line = line
						.replace("set(PLUGINS)",  "set(PLUGINS " + pluginList + ")")
						.replace("set(PLUGINS )", "set(PLUGINS " + pluginList + ")");
			}
			out.add(line);
		}
		Files.write(path, out, StandardCharsets.UTF_8);
	}

	/**
	 * Engine import: just load the .project at the given dir and open it.
	 * No clone, no run config (engine is a library project, not runnable).
	 */
	public static IProject importEngine(Path engineDir, IProgressMonitor monitor)
			throws CoreException {
		return ensureImportedFromDescription(engineDir.toFile(), null, monitor);
	}

	/* ----- internals ----- */

	private static void cloneFresh(File dest) throws IOException, InterruptedException {
		File parent = dest.getParentFile();
		Files.createDirectories(parent.toPath());

		run(parent, "git", "clone", "--depth", "1", GLISTAPP_REPO, dest.getName());

		deleteRecursive(new File(dest, ".git").toPath());
		run(dest, "git", "init", "-q");
		run(dest, "git", "add", "-A");
		run(dest, "git",
			"-c", "user.email=glistengine@local",
			"-c", "user.name=GlistEngine",
			"commit", "-q", "-m", "Initial commit");
	}

	private static IProject ensureImported(File dest, IProgressMonitor monitor)
			throws CoreException {
		// Name override only applies when the on-disk .project name doesn't
		// match the folder name (rare; wizard-created apps always match).
		return ensureImportedFromDescription(dest, dest.getName(), monitor);
	}

	private static IProject ensureImportedFromDescription(
			File projectFolder, String preferredName, IProgressMonitor monitor)
			throws CoreException {

		org.eclipse.core.runtime.IPath descPath =
				org.eclipse.core.runtime.IPath
						.fromOSString(projectFolder.getAbsolutePath())
						.append(".project");
		IProjectDescription description =
				ResourcesPlugin.getWorkspace().loadProjectDescription(descPath);

		String name = preferredName != null ? preferredName : description.getName();
		description.setName(name);

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (!project.exists()) {
			project.create(description, monitor);
		}
		if (!project.isOpen()) {
			project.open(monitor);
		}
		return project;
	}

	private static void ensureRunConfiguration(IProject project) throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type =
				manager.getLaunchConfigurationType("org.eclipse.cdt.launch.applicationLaunchType");
		if (type == null) {
			return;
		}

		String name = project.getName();
		for (ILaunchConfiguration existing : manager.getLaunchConfigurations(type)) {
			if (name.equals(existing.getName())) {
				return; // already exists
			}
		}

		String programName = isWindows()
				? "_build/Release/" + name + ".exe"
				: "_build/Release/" + name;

		ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);
		wc.setAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", name);
		wc.setAttribute("org.eclipse.cdt.launch.PROGRAM_NAME", programName);
		wc.setAttribute(
				"org.eclipse.debug.core.ATTR_WORKING_DIRECTORY",
				"${project_loc:" + name + "}");
		wc.doSave();
	}

	private static void run(File cwd, String... cmd)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd)
				.directory(cwd)
				.redirectErrorStream(true)
				.inheritIO();
		Process p = pb.start();
		int rc = p.waitFor();
		if (rc != 0) {
			throw new IOException("Command failed (" + rc + "): "
					+ String.join(" ", cmd));
		}
	}

	private static void deleteRecursive(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (var stream = Files.walk(root)) {
			stream.sorted(Comparator.reverseOrder()).forEach(p -> {
				try { Files.delete(p); } catch (IOException ignored) {}
			});
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().startsWith("win");
	}
}
