package com.aitial.glist.wizards;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

/**
 * Professional Wizard for GlistApp Programs.
 * Strictly aligned with GlistEngine installation standards for Windows (C:\dev\glist) and Linux (~/dev/glist).
 * Automatically resolves os-specific archetype paths for win64 and linux builds.
 */
public class GlistAppProjectWizard extends Wizard implements INewWizard {

    private static final String DEV_ROOT;
    private static final String TEMPLATE_ROOT;
    private static final String DESTINATION_ROOT;
    private static final String PLUGINS_ROOT;
    
    // Core structural paths resolved dynamically based on targeted cross-platform architecture
    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            // Windows Deployment Standard
            DEV_ROOT = "C:" + File.separator + "dev" + File.separator + "glist";
            TEMPLATE_ROOT = DEV_ROOT + File.separator + "zbin" + File.separator + "glistzbin-win64" + File.separator + "eclipse" + File.separator + "glistapp-template";
        } else {
            // Linux Ubuntu Deployment Standard (~/dev/glist) with glistzbin-linux
            DEV_ROOT = System.getProperty("user.home") + File.separator + "dev" + File.separator + "glist";
            TEMPLATE_ROOT = DEV_ROOT + File.separator + "zbin" + File.separator + "glistzbin-linux" + File.separator + "eclipse" + File.separator + "glistapp-template";
        }
        
        DESTINATION_ROOT = DEV_ROOT + File.separator + "myglistapps";
        PLUGINS_ROOT = DEV_ROOT + File.separator + "glistplugins";
    }
    
    // Project Type Definitions
    public enum ProjectType {
        GAME_GRAPHIC,
        GUI,
        CONSOLE
    }

    private Text projectNameText;
    private Table pluginsTable;
    private String finalProjectName;
    private ProjectType selectedType = ProjectType.GAME_GRAPHIC; // Default fallback

    public GlistAppProjectWizard() {
        setWindowTitle("GlistEngine - Create New GlistApp");
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

                // 1. Project Name Input Field
                new Label(container, SWT.NONE).setText("Project Name:");
                projectNameText = new Text(container, SWT.BORDER | SWT.SINGLE);
                projectNameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
                projectNameText.setText(getUniqueProjectName("GlistApp"));
                projectNameText.addModifyListener(e -> validate());

                // 2. Project Type Section
                new Label(container, SWT.NONE).setText("Project Type:");
                
                Group typeGroup = new Group(container, SWT.NONE);
                typeGroup.setLayout(new GridLayout(3, false)); 
                typeGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

                Button gameGraphicRadio = new Button(typeGroup, SWT.RADIO);
                gameGraphicRadio.setText("Game/Graphic App");
                gameGraphicRadio.setSelection(true); 
                gameGraphicRadio.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
                    @Override
                    public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                        if (gameGraphicRadio.getSelection()) selectedType = ProjectType.GAME_GRAPHIC;
                    }
                });

                Button guiRadio = new Button(typeGroup, SWT.RADIO);
                guiRadio.setText("GUI App");
                guiRadio.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
                    @Override
                    public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                        if (guiRadio.getSelection()) selectedType = ProjectType.GUI;
                    }
                });

                Button consoleRadio = new Button(typeGroup, SWT.RADIO);
                consoleRadio.setText("Console App");
                consoleRadio.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
                    @Override
                    public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                        if (consoleRadio.getSelection()) selectedType = ProjectType.CONSOLE;
                    }
                });

                // 3. Plugins Section Label
                Label pluginsLabel = new Label(container, SWT.NONE);
                pluginsLabel.setText("Select Plugins:");
                GridData labelData = new GridData();
                labelData.verticalAlignment = SWT.TOP;
                pluginsLabel.setLayoutData(labelData);

                // 4. Plugins Checkbox Table (Single Column Style)
                pluginsTable = new Table(container, SWT.BORDER | SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL);
                GridData tableData = new GridData(GridData.FILL_BOTH);
                tableData.heightHint = 150;
                pluginsTable.setLayoutData(tableData);

                // Dynamically gauge cell sizes to prevent text clipping
                pluginsTable.addListener(SWT.MeasureItem, new Listener() {
                    @Override
                    public void handleEvent(Event event) {
                        TableItem item = (TableItem) event.item;
                        String pluginName = item.getText();
                        String description = (String) item.getData("desc");
                        
                        GC gc = event.gc;
                        int nameWidth = gc.textExtent(pluginName).x;
                        int descWidth = 0;
                        
                        if (description != null && !description.isEmpty()) {
                            descWidth = gc.textExtent(" - " + description).x;
                        }
                        
                        event.width = nameWidth + descWidth + 30; 
                        event.height = Math.max(event.height, gc.getFontMetrics().getHeight() + 4); 
                    }
                });

                // Advanced Custom Painter: Perfectly centered multi-color rendering
                pluginsTable.addListener(SWT.PaintItem, new Listener() {
                    @Override
                    public void handleEvent(Event event) {
                        TableItem item = (TableItem) event.item;
                        String pluginName = item.getText();
                        String description = (String) item.getData("desc");

                        if (description != null && !description.isEmpty()) {
                            GC gc = event.gc;
                            
                            Point nameExtent = gc.textExtent(pluginName);
                            int startX = event.x + nameExtent.x + 6; 
                            
                            int cellHeight = pluginsTable.getItemHeight();
                            int textHeight = nameExtent.y;
                            int startY = event.y + ((cellHeight - textHeight) / 2);

                            Color originalColor = gc.getForeground();
                            Color grayColor = Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY);
                            
                            gc.setForeground(grayColor);
                            gc.drawString(" - " + description, startX, startY, true);
                            gc.setForeground(originalColor);
                        }
                    }
                });

                populatePluginsTable();

                // Spacer for layout alignment
                new Label(container, SWT.NONE);

                // 5. External Hyperlink Configuration
                org.eclipse.swt.widgets.Link downloadLink = new org.eclipse.swt.widgets.Link(container, SWT.NONE);
                downloadLink.setText("Download more plugins: <a href=\"https://github.com/GlistPlugins\">https://github.com/GlistPlugins</a>");
                downloadLink.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
                downloadLink.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
                    @Override
                    public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                        org.eclipse.swt.program.Program.launch(e.text);
                    }
                });

                setControl(container);
                validate();
            }

            private void populatePluginsTable() {
                File pluginsDir = new File(PLUGINS_ROOT);
                if (pluginsDir.exists() && pluginsDir.isDirectory()) {
                    File[] subFiles = pluginsDir.listFiles();
                    if (subFiles != null) {
                        for (File file : subFiles) {
                            if (file.isDirectory()) {
                                String folderName = file.getName();
                                if (!folderName.equals("gipEmptyComponent") && !folderName.equals("gipEmptyPlugin")) {
                                    TableItem item = new TableItem(pluginsTable, SWT.NONE);
                                    
                                    item.setText(folderName);
                                    
                                    File cmakeFile = new File(file, "CMakeLists.txt");
                                    String description = parsePluginDescription(cmakeFile);
                                    
                                    if (!description.isEmpty()) {
                                        item.setData("desc", description);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            private String parsePluginDescription(File cmakeFile) {
                if (!cmakeFile.exists() || !cmakeFile.isFile()) {
                    return "";
                }
                try {
                    String content = Files.readString(cmakeFile.toPath(), StandardCharsets.UTF_8);
                    Pattern pattern = Pattern.compile("set\\s*\\(\\s*(projectdescription|plugindescription)\\s+\"?([^\")*]+)\"?", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        return matcher.group(2).trim();
                    }
                } catch (IOException e) {
                    // Fail silently
                }
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

                org.eclipse.core.resources.IProject[] allWorkspaceProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                for (org.eclipse.core.resources.IProject wsProject : allWorkspaceProjects) {
                    if (wsProject.getName().equalsIgnoreCase(name)) {
                        setErrorMessage("A project named '" + wsProject.getName() + "' already exists in the Eclipse workspace.");
                        setPageComplete(false);
                        return;
                    }
                }

                File rootDir = new File(DESTINATION_ROOT);
                if (rootDir.exists() && rootDir.isDirectory()) {
                    File[] physicalFiles = rootDir.listFiles();
                    if (physicalFiles != null) {
                        for (File file : physicalFiles) {
                            if (file.isDirectory() && file.getName().equalsIgnoreCase(name)) {
                                setErrorMessage("A folder named '" + file.getName() + "' already exists physically on the disk.");
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
        try {
            String requestedName = projectNameText.getText().trim();
            finalProjectName = getUniqueProjectName(requestedName);
            File destFolder = new File(DESTINATION_ROOT, finalProjectName);

            // 1. Collect selected plugins
            List<String> selectedPlugins = new ArrayList<>();
            for (TableItem item : pluginsTable.getItems()) {
                if (item.getChecked()) {
                    selectedPlugins.add(item.getText()); 
                }
            }

            // 2. Resolve Dynamic Template Path based on selected Project Type
            String dynamicTemplatePath = resolveTemplatePath(selectedType);

            // 3. Copy resolved Archetype structure to the destination
            copyFolder(new File(dynamicTemplatePath).toPath(), destFolder.toPath());

            // 4. Inject Chosen Plugins into CMakeLists.txt dynamically (Only registry injection, no source copying)
            injectPluginsIntoCMake(new File(destFolder, "CMakeLists.txt"), selectedPlugins);

            // 5. Import the freshly mutated project into Workspace Registry
            IProject project = importProject(destFolder, finalProjectName);

            // 6. Build dynamic launch runtime parameters
            createRunConfiguration(project);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String resolveTemplatePath(ProjectType type) {
        switch (type) {
            case GUI:
                return TEMPLATE_ROOT + File.separator + "GlistGUIApp";
            case CONSOLE:
                return TEMPLATE_ROOT + File.separator + "GlistConsoleApp";
            case GAME_GRAPHIC:
            default:
                return TEMPLATE_ROOT + File.separator + "GlistApp";
        }
    }

    private void injectPluginsIntoCMake(File cmakeFile, List<String> plugins) throws IOException {
        if (!cmakeFile.exists() || plugins.isEmpty()) return;

        Path path = cmakeFile.toPath();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> modifiedLines = new ArrayList<>();

        // Merges plugins separated with space; guarantees no trailing space before closing parentheses
        String pluginString = String.join(" ", plugins);

        for (String line : lines) {
            if (line.contains("set(PLUGINS")) {
                // Safeguards both empty set(PLUGINS) and single-spaced set(PLUGINS ) variants for full mapping matches
                String updatedLine = line.replace("set(PLUGINS)", "set(PLUGINS " + pluginString + ")")
                                         .replace("set(PLUGINS )", "set(PLUGINS " + pluginString + ")");
                modifiedLines.add(updatedLine);
            } else {
                modifiedLines.add(line);
            }
        }

        Files.write(path, modifiedLines, StandardCharsets.UTF_8);
    }

    private String getUniqueProjectName(String baseName) {
        File root = new File(DESTINATION_ROOT);
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

    private void copyFolder(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private IProject importProject(File projectFolder, String projectName) throws CoreException {
        org.eclipse.core.runtime.IPath projectDescriptionPath = new org.eclipse.core.runtime.Path(projectFolder.getAbsolutePath()).append(".project");
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
        
        String binaryName = System.getProperty("os.name").toLowerCase().contains("win") ? "GlistApp.exe" : "GlistApp";
        workingCopy.setAttribute("org.eclipse.cdt.launch.PROGRAM_NAME", "_build/Release/" + binaryName);
        
        workingCopy.setAttribute("org.eclipse.add_attr_working_dir", "${project_loc:" + project.getName() + "}");
        workingCopy.setAttribute("org.eclipse.debug.core.ATTR_WORKING_DIRECTORY", "${project_loc:" + project.getName() + "}");
        workingCopy.doSave();
    }
}