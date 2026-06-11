package com.aitial.glist.handlers; 
 
import org.eclipse.core.commands.AbstractHandler; 
import org.eclipse.core.commands.ExecutionEvent; 
import org.eclipse.core.commands.ExecutionException; 
import org.eclipse.core.resources.IProject; 
import org.eclipse.core.resources.IProjectDescription; 
import org.eclipse.core.runtime.CoreException; 
import org.eclipse.core.runtime.IPath; 
import org.eclipse.core.runtime.NullProgressMonitor; 
import org.eclipse.debug.core.DebugPlugin; 
import org.eclipse.debug.core.ILaunchConfiguration; 
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy; 
import org.eclipse.debug.core.ILaunchManager; 
import org.eclipse.jface.dialogs.InputDialog; 
import org.eclipse.jface.viewers.ISelection; 
import org.eclipse.jface.viewers.IStructuredSelection; 
import org.eclipse.jface.window.Window; 
import org.eclipse.ui.handlers.HandlerUtil; 
 
public class RenameProjectHandler extends AbstractHandler { 
 
    @Override 
    public Object execute(ExecutionEvent event) throws ExecutionException { 
        ISelection selection = HandlerUtil.getCurrentSelection(event); 
        if (!(selection instanceof IStructuredSelection)) { 
            return null; 
        } 
 
        Object firstElement = ((IStructuredSelection) selection).getFirstElement(); 
        IProject project = null; 
 
        if (firstElement instanceof IProject) { 
            project = (IProject) firstElement; 
        } else if (firstElement instanceof org.eclipse.core.runtime.IAdaptable) { 
            project = ((org.eclipse.core.runtime.IAdaptable) firstElement).getAdapter(IProject.class); 
        } 
 
        if (project == null || !project.isOpen()) { 
            return null; 
        } 
 
        try { 
            if (!project.hasNature("org.eclipse.cdt.core.cnature")) { 
                return null;  
            } 
        } catch (CoreException e) { 
            return null; 
        } 
 
        final String oldProjectName = project.getName(); 
        final java.io.File parentDirectory = project.getLocation().removeLastSegments(1).toFile(); 
 
        InputDialog dialog = new InputDialog( 
                HandlerUtil.getActiveShell(event), 
                "Rename GlistApp Project", 
                "Enter new name for the GlistApp project:", 
                oldProjectName, 
                new org.eclipse.jface.dialogs.IInputValidator() { 
                    @Override 
                    public String isValid(String newText) { 
                        String trimmed = newText.trim(); 
                        if (trimmed.isEmpty()) { 
                            return "Project name cannot be empty."; 
                        } 
                        if (trimmed.equalsIgnoreCase(oldProjectName)) { 
                            return "New name must be different from the current name (Case-Insensitive)."; 
                        } 
                        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) { 
                            return "Only alphanumeric characters, dashes, and underscores are allowed."; 
                        } 
 
                        if (trimmed.equalsIgnoreCase("glistengine")) { 
                            return "The name '" + newText + "' is reserved for the core framework and cannot be used."; 
                        } 
 
                        IProject existingProject = org.eclipse.core.resources.ResourcesPlugin.getWorkspace() 
                                .getRoot().getProject(trimmed); 
                        if (existingProject.exists()) { 
                            return "A project named '" + trimmed + "' already exists in the workspace."; 
                        } 
                         
                        java.io.File targetPhysicalFolder = new java.io.File(parentDirectory, trimmed); 
                        if (targetPhysicalFolder.exists()) { 
                            return "A folder named '" + trimmed + "' already exists physically on the disk at: " + targetPhysicalFolder.getAbsolutePath(); 
                        } 
                         
                        return null; 
                    } 
                } 
        ); 
 
        if (dialog.open() == Window.OK) { 
            String newProjectName = dialog.getValue().trim(); 
            if (!newProjectName.isEmpty() && !newProjectName.equalsIgnoreCase(oldProjectName)) { 
                performCustomRename(project, oldProjectName, newProjectName); 
            } 
        } 
 
        return null; 
    } 
     
    private void performCustomRename(IProject project, String oldName, String newName) { 
        try { 
            NullProgressMonitor monitor = new NullProgressMonitor(); 
 
            // 1. FIRST PURGE: Wipe out stale caches using native Resource APIs while paths are active
            if (project.isOpen()) {
                org.eclipse.core.resources.IFolder buildFolder = project.getFolder("build");
                org.eclipse.core.resources.IFolder underscoreBuildFolder = project.getFolder("_build");

                // Deletes build dirs both synchronously from disk and Project Explorer tree mapping
                if (buildFolder.exists()) {
                    buildFolder.delete(org.eclipse.core.resources.IResource.FORCE, monitor);
                }
                if (underscoreBuildFolder.exists()) {
                    underscoreBuildFolder.delete(org.eclipse.core.resources.IResource.FORCE, monitor);
                }
            }

            // 2. MOVE OPERATION: Physically move the folder and rename project registry
            ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager(); 
            ILaunchConfiguration[] configs = launchManager.getLaunchConfigurations(); 
 
            IPath projectLocation = project.getLocation(); 
            IPath newLocation = projectLocation.removeLastSegments(1).append(newName); 
 
            IProjectDescription description = project.getDescription(); 
            description.setName(newName); 
            description.setLocation(newLocation); 
 
            project.move(description, org.eclipse.core.resources.IResource.FORCE, monitor); 
 
            // 3. RUN CONFIGURATION INTERFACE UPDATES
            for (ILaunchConfiguration config : configs) { 
                String configProjectAttr = config.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", ""); 
                 
                if (configProjectAttr.equals(oldName)) { 
                    ILaunchConfigurationWorkingCopy workingCopy = config.getWorkingCopy(); 
                     
                    workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", newName); 
                     
                    String binaryName = System.getProperty("os.name").toLowerCase().contains("win") ? "GlistApp.exe" : "GlistApp"; 
                    workingCopy.setAttribute("org.eclipse.cdt.launch.PROGRAM_NAME", "_build/Release/" + binaryName); 
                     
                    workingCopy.setAttribute("org.eclipse.debug.core.ATTR_WORKING_DIRECTORY", "${project_loc:" + newName + "}");
                    workingCopy.rename(newName); 
                    workingCopy.doSave(); 
                } 
            } 
            
            // Forces final structural delta verification over the migrated paths
            project.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_INFINITE, monitor);
 
        } catch (CoreException e) { 
            e.printStackTrace(); 
        } 
    } 
}