package com.aitial.glist.wizards;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
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
 * Delegates the actual work to {@link GlistAppImporter#importOrCloneApp},
 * which is the same code path the IStartup hook uses for projects the user
 * already has on disk. Difference is the wizard picks a fresh name first.
 */
public class GlistAppProjectWizard extends Wizard implements INewWizard {

	private Text projectNameText;

	public GlistAppProjectWizard() {
		setWindowTitle("AITIAL - Create New GlistApp");
		setNeedsProgressMonitor(true);
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
				projectNameText.setText(GlistAppNaming.nextAvailable("GlistApp"));

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
		String requestedName = projectNameText.getText().trim();
		String finalName = GlistAppNaming.nextAvailable(requestedName);

		// Run the import on the workspace lock so resource events are batched.
		final IProject[] result = new IProject[1];
		final Exception[] failure = new Exception[1];

		try {
			IWorkspaceRunnable op = monitor ->
					{ try { result[0] = GlistAppImporter.importOrCloneApp(finalName, monitor); }
					  catch (RuntimeException re) { throw re; }
					  catch (Exception e) { failure[0] = e; } };
			ResourcesPlugin.getWorkspace().run(op, new NullProgressMonitor());
		} catch (Exception e) {
			failure[0] = e;
		}

		if (failure[0] != null || result[0] == null) {
			StringWriter sw = new StringWriter();
			if (failure[0] != null) {
				failure[0].printStackTrace(new PrintWriter(sw));
			}
			MessageDialog.openError(
					getShell(),
					"Could not create GlistApp",
					"Failed to create " + finalName + ":\n\n"
							+ (failure[0] != null ? failure[0].getMessage() : "Unknown error")
							+ "\n\nCheck that `git` is on PATH and that you have network access.\n\n"
							+ sw);
			return false;
		}
		return true;
	}
}
