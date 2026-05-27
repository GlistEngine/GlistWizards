package com.aitial.glist.wizards;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

/**
 * Wizard for new GlistApp projects.
 *
 * UI lets the user pick:
 *   - project name (auto-incremented to first free GlistApp-N)
 *   - project type (Game/Graphic, GUI, Console)
 *   - plugins from <GLIST_HOME>/glistplugins/ (descriptions parsed from each
 *     plugin's CMakeLists.txt)
 *
 * Backed by {@link GlistAppImporter#importOrCloneApp(String, List, org.eclipse.core.runtime.IProgressMonitor)}:
 * clones the appropriate upstream repo, resets git history, injects the
 * selected plugin list into CMakeLists.txt, imports the project, ensures a
 * CDT run configuration, and finally opens gCanvas.cpp/.h.
 */
public class GlistAppProjectWizard extends Wizard implements INewWizard {

	public enum ProjectType {
		GAME_GRAPHIC, GUI, CONSOLE
	}

	private Text projectNameText;
	private Table pluginsTable;
	private ProjectType selectedType = ProjectType.GAME_GRAPHIC;

	public GlistAppProjectWizard() {
		setWindowTitle("GlistEngine - Create New GlistApp");
		setNeedsProgressMonitor(true);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {}

	@Override
	public void addPages() {
		addPage(new WizardPage("GlistAppPage") {
			{
				setTitle("GlistApp Program Details");
				setDescription("Configure your program parameters, project type, and core framework plugins.");
			}

			@Override
			public void createControl(Composite parent) {
				Composite container = new Composite(parent, SWT.NONE);
				container.setLayout(new GridLayout(2, false));

				// --- Project Name ---
				new Label(container, SWT.NONE).setText("Project Name:");
				projectNameText = new Text(container, SWT.BORDER | SWT.SINGLE);
				projectNameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				projectNameText.setText(GlistAppNaming.nextAvailable("GlistApp"));
				projectNameText.addModifyListener(e -> validate());

				// --- Project Type ---
				new Label(container, SWT.NONE).setText("Project Type:");
				Group typeGroup = new Group(container, SWT.NONE);
				typeGroup.setLayout(new GridLayout(3, false));
				typeGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

				addTypeRadio(typeGroup, "Game/Graphic App", ProjectType.GAME_GRAPHIC, true);
				addTypeRadio(typeGroup, "GUI App",          ProjectType.GUI,          false);
				addTypeRadio(typeGroup, "Console App",      ProjectType.CONSOLE,      false);

				// --- Plugins ---
				Label pluginsLabel = new Label(container, SWT.NONE);
				pluginsLabel.setText("Select Plugins:");
				GridData labelData = new GridData();
				labelData.verticalAlignment = SWT.TOP;
				pluginsLabel.setLayoutData(labelData);

				pluginsTable = new Table(container, SWT.BORDER | SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL);
				GridData tableData = new GridData(GridData.FILL_BOTH);
				tableData.heightHint = 150;
				pluginsTable.setLayoutData(tableData);

				pluginsTable.addListener(SWT.MeasureItem, new Listener() {
					@Override
					public void handleEvent(Event event) {
						TableItem item = (TableItem) event.item;
						String pluginName = item.getText();
						String description = (String) item.getData("desc");
						GC gc = event.gc;
						int nameWidth = gc.textExtent(pluginName).x;
						int descWidth = description != null && !description.isEmpty()
								? gc.textExtent(" - " + description).x : 0;
						event.width = nameWidth + descWidth + 30;
						event.height = Math.max(event.height, gc.getFontMetrics().getHeight() + 4);
					}
				});
				pluginsTable.addListener(SWT.PaintItem, new Listener() {
					@Override
					public void handleEvent(Event event) {
						TableItem item = (TableItem) event.item;
						String pluginName = item.getText();
						String description = (String) item.getData("desc");
						if (description == null || description.isEmpty()) return;
						GC gc = event.gc;
						Point nameExtent = gc.textExtent(pluginName);
						int startX = event.x + nameExtent.x + 6;
						int cellHeight = pluginsTable.getItemHeight();
						int textHeight = nameExtent.y;
						int startY = event.y + ((cellHeight - textHeight) / 2);
						Color original = gc.getForeground();
						Color gray = Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY);
						gc.setForeground(gray);
						gc.drawString(" - " + description, startX, startY, true);
						gc.setForeground(original);
					}
				});

				populatePluginsTable();

				// Spacer under the "Select Plugins:" label so the marketplace link aligns properly.
				new Label(container, SWT.NONE);

				Link downloadLink = new Link(container, SWT.NONE);
				downloadLink.setText("Download more plugins: <a href=\"https://github.com/GlistPlugins\">https://github.com/GlistPlugins</a>");
				downloadLink.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				downloadLink.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						Program.launch(e.text);
					}
				});

				setControl(container);
				validate();
			}

			private void addTypeRadio(Composite parent, String label, ProjectType type, boolean defaultPick) {
				Button b = new Button(parent, SWT.RADIO);
				b.setText(label);
				b.setSelection(defaultPick);
				b.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if (b.getSelection()) selectedType = type;
					}
				});
			}

			private void populatePluginsTable() {
				File pluginsDir = GlistPaths.pluginsRoot().toFile();
				if (!pluginsDir.isDirectory()) return;
				File[] entries = pluginsDir.listFiles();
				if (entries == null) return;
				for (File entry : entries) {
					if (!entry.isDirectory()) continue;
					String folderName = entry.getName();
					if (folderName.equals("gipEmptyComponent") || folderName.equals("gipEmptyPlugin")) continue;
					TableItem item = new TableItem(pluginsTable, SWT.NONE);
					item.setText(folderName);
					String description = parsePluginDescription(new File(entry, "CMakeLists.txt"));
					if (!description.isEmpty()) {
						item.setData("desc", description);
					}
				}
			}

			private String parsePluginDescription(File cmakeFile) {
				if (!cmakeFile.exists() || !cmakeFile.isFile()) return "";
				try {
					String content = Files.readString(cmakeFile.toPath(), StandardCharsets.UTF_8);
					Pattern p = Pattern.compile(
							"set\\s*\\(\\s*(projectdescription|plugindescription)\\s+\"?([^\")*]+)\"?",
							Pattern.CASE_INSENSITIVE);
					Matcher m = p.matcher(content);
					if (m.find()) return m.group(2).trim();
				} catch (IOException ignored) {}
				return "";
			}

			private void validate() {
				String name = projectNameText.getText().trim();

				if (name.isEmpty()) {
					setErrorMessage("Project name cannot be empty");
					setPageComplete(false);
					return;
				}
				if (!name.matches("^[a-zA-Z0-9_-]+$")) {
					setErrorMessage("Only alphanumeric characters, dashes, and underscores are allowed.");
					setPageComplete(false);
					return;
				}
				if (name.equalsIgnoreCase("glistengine")) {
					setErrorMessage("The name '" + name + "' is reserved for the core framework and cannot be used.");
					setPageComplete(false);
					return;
				}

				IProject[] all = ResourcesPlugin.getWorkspace().getRoot().getProjects();
				for (IProject ws : all) {
					if (ws.getName().equalsIgnoreCase(name)) {
						setErrorMessage("A project named '" + ws.getName() + "' already exists in the workspace.");
						setPageComplete(false);
						return;
					}
				}
				File appsRoot = GlistPaths.appsRoot().toFile();
				if (appsRoot.isDirectory()) {
					File[] siblings = appsRoot.listFiles();
					if (siblings != null) {
						for (File s : siblings) {
							if (s.isDirectory() && s.getName().equalsIgnoreCase(name)) {
								setErrorMessage("A folder named '" + s.getName() + "' already exists on disk.");
								setPageComplete(false);
								return;
							}
						}
					}
				}

				setErrorMessage(null);
				setPageComplete(true);
			}
		});
	}

	@Override
	public boolean performFinish() {
		String requestedName = projectNameText.getText().trim();
		String finalName = GlistAppNaming.nextAvailable(requestedName);

		List<String> selectedPlugins = new ArrayList<>();
		for (TableItem item : pluginsTable.getItems()) {
			if (item.getChecked()) selectedPlugins.add(item.getText());
		}

		final IProject[] result = new IProject[1];
		final Exception[] failure = new Exception[1];

		try {
			IWorkspaceRunnable op = monitor -> {
				try {
					result[0] = GlistAppImporter.importOrCloneApp(finalName, selectedPlugins, monitor);
				} catch (RuntimeException re) { throw re; }
				  catch (Exception e) { failure[0] = e; }
			};
			ResourcesPlugin.getWorkspace().run(op, new NullProgressMonitor());
		} catch (Exception e) {
			failure[0] = e;
		}

		if (failure[0] != null || result[0] == null) {
			StringWriter sw = new StringWriter();
			if (failure[0] != null) failure[0].printStackTrace(new PrintWriter(sw));
			MessageDialog.openError(
					getShell(),
					"Could not create GlistApp",
					"Failed to create " + finalName + ":\n\n"
							+ (failure[0] != null ? failure[0].getMessage() : "Unknown error")
							+ "\n\nCheck that `git` is on PATH and that you have network access.\n\n"
							+ sw);
			return false;
		}

		// TODO: project type is currently captured but unused — once
		// GlistGUIApp / GlistConsoleApp upstream repos exist, branch on
		// selectedType to clone the right one in GlistAppImporter.
		GlistAppEditors.openCanvas(result[0]);
		return true;
	}
}
