package com.aitial.glist.wizards;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

/**
 * Opens the canonical "where to start editing" files for a freshly-created
 * GlistApp — gCanvas.h behind, gCanvas.cpp in front — so a student lands
 * directly on the code that matters instead of an empty workbench.
 *
 * All work goes through {@link Display#asyncExec} so it's safe to call from
 * the startup hook (non-UI thread) or the wizard (UI thread).
 */
public final class GlistAppEditors {

	private static final ILog LOG = Platform.getLog(GlistAppEditors.class);

	private GlistAppEditors() {}

	public static void openCanvas(IProject project) {
		final IFile header = project.getFile("src/gCanvas.h");
		final IFile source = project.getFile("src/gCanvas.cpp");

		Display.getDefault().asyncExec(() -> {
			IWorkbench workbench = PlatformUI.getWorkbench();
			if (workbench == null) return;
			IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
			if (window == null) {
				IWorkbenchWindow[] all = workbench.getWorkbenchWindows();
				if (all.length == 0) return;
				window = all[0];
			}
			IWorkbenchPage page = window.getActivePage();
			if (page == null) return;

			try {
				// Open header first without activating, so source ends up in front.
				if (header.exists()) {
					IDE.openEditor(page, header, false);
				}
				if (source.exists()) {
					IDE.openEditor(page, source, true);
				}
			} catch (PartInitException e) {
				LOG.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
						"Could not open initial GlistApp canvas files", e));
			}
		});
	}
}
