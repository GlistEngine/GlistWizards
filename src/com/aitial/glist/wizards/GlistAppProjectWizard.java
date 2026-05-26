package com.aitial.glist.wizards;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

/**
 * Wizard for new GlistApp projects.
 *
 * Clones https://github.com/GlistEngine/GlistApp into
 * <GLIST_HOME>/myglistapps/<name>/, then reinitialises the repo so the user
 * gets a clean history (no upstream commits, no "origin" remote) and imports
 * it into Eclipse. Destination is resolved at runtime from the workspace
 * location (see {@link GlistPaths}).
 */
public class GlistAppProjectWizard extends Wizard implements INewWizard {

	private static final String GLISTAPP_REPO = "https://github.com/GlistEngine/GlistApp.git";

	private Text projectNameText;
	private String finalProjectName;

	public GlistAppProjectWizard() {
		setWindowTitle("AITIAL - Create New GlistApp");
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {}

	@Override
	public void addPages() {
		addPage(new WizardPage("GlistAppPage") {
			{
				setTitle("GlistApp Project Details");
				setDescription("Enter a project name. The wizard will automatically suggest the next available index.");
			}

			@Override
			public void createControl(Composite parent) {
				Composite container = new Composite(parent, SWT.NONE);
				container.setLayout(new GridLayout(2, false));

				new Label(container, SWT.NONE).setText("Project Name:");
				projectNameText = new Text(container, SWT.BORDER | SWT.SINGLE);
				projectNameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				projectNameText.setText(getUniqueProjectName("GlistApp"));

				projectNameText.addModifyListener(e -> validate());
				setControl(container);
				validate();
			}

			private void validate() {
				String name = projectNameText.getText().trim();
				if (name.isEmpty()) {
					setErrorMessage("Project name cannot be empty");
					setPageComplete(false);
				} else if (!name.matches("^[a-zA-Z0-9_-]+$")) {
					setErrorMessage("Only alphanumeric characters, dashes, and underscores are allowed.");
					setPageComplete(false);
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
	}

	@Override
	public boolean performFinish() {
		try {
			String requestedName = projectNameText.getText().trim();
			finalProjectName = getUniqueProjectName(requestedName);

			Path appsRoot = GlistPaths.appsRoot();
			Files.createDirectories(appsRoot);

			File destFolder = appsRoot.resolve(finalProjectName).toFile();

			cloneGlistApp(destFolder);
			resetGitRepo(destFolder);
			IProject project = importProject(destFolder, finalProjectName);
			createRunConfiguration(project);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private void cloneGlistApp(File dest) throws IOException, InterruptedException {
		run(dest.getParentFile(),
			"git", "clone", "--depth", "1", GLISTAPP_REPO, dest.getName());
	}

	private void resetGitRepo(File dest) throws IOException, InterruptedException {
		File gitDir = new File(dest, ".git");
		deleteRecursive(gitDir.toPath());
		run(dest, "git", "init", "-q");
		run(dest, "git", "add", "-A");
		run(dest, "git", "-c", "user.email=glistengine@local",
			"-c", "user.name=GlistEngine",
			"commit", "-q", "-m", "Initial commit");
	}

	private static void run(File cwd, String... cmd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd)
				.directory(cwd)
				.redirectErrorStream(true)
				.inheritIO();
		Process p = pb.start();
		int rc = p.waitFor();
		if (rc != 0) {
			throw new IOException("Command failed (" + rc + "): " + String.join(" ", cmd));
		}
	}

	private static void deleteRecursive(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (var stream = Files.walk(root)) {
			stream.sorted(java.util.Comparator.reverseOrder())
				.forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
		}
	}

	private String getUniqueProjectName(String baseName) {
		File root = GlistPaths.appsRoot().toFile();
		if (!root.exists()) root.mkdirs();

		String currentName = baseName;
		if (new File(root, currentName).exists()) {
			int counter = 2;
			while (new File(root, baseName + "-" + counter).exists()) {
				counter++;
			}
			currentName = baseName + "-" + counter;
		}
		return currentName;
	}

	private IProject importProject(File projectFolder, String projectName) throws CoreException {
		org.eclipse.core.runtime.IPath projectDescriptionPath =
				org.eclipse.core.runtime.IPath.fromOSString(projectFolder.getAbsolutePath()).append(".project");
		IProjectDescription description = ResourcesPlugin.getWorkspace().loadProjectDescription(projectDescriptionPath);
		description.setName(projectName);

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		project.create(description, new NullProgressMonitor());
		project.open(new NullProgressMonitor());
		return project;
	}

	private void createRunConfiguration(IProject project) throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType("org.eclipse.cdt.launch.applicationLaunchType");
		if (type == null) return;

		ILaunchConfigurationWorkingCopy workingCopy = type.newInstance(null, project.getName());
		workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", project.getName());
		String programName = isWindows()
				? "_build/Release/GlistApp.exe"
				: "_build/Release/GlistApp";
		workingCopy.setAttribute("org.eclipse.cdt.launch.PROGRAM_NAME", programName);
		workingCopy.setAttribute("org.eclipse.debug.core.ATTR_WORKING_DIRECTORY", "${project_loc:" + project.getName() + "}");
		workingCopy.doSave();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().startsWith("win");
	}
}
