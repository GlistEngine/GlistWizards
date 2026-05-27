# Eclipse-Plugins

Eclipse plugins shipped inside the GlistEngine Eclipse bundles
(`glistzbin-win64`, `glistzbin-macos`, `glistzbin-linux`).

## Plugins

- **`com.aitial.glist.wizards`** — `File > New > Project > GlistApp > GlistApp
  Project` wizard. Clones `https://github.com/GlistEngine/GlistApp`, resets git
  history, lets the user pick a project type and plugins, injects the chosen
  plugin list into `CMakeLists.txt`, imports the project, creates a CDT run
  configuration, and opens `gCanvas.cpp` / `gCanvas.h`.

  Also includes an `org.eclipse.ui.startup` (`IStartup`) hook: on every Eclipse
  boot it walks the canonical Glist layout (resolved relative to the workspace
  location — no baked-in absolute paths) and imports any projects it finds that
  aren't already in the workspace. On a fresh install with no apps, it bootstraps
  a starter `GlistApp` automatically.

- **`com.aitial.glist.refactoring`** — adds a *Rename GlistApp Project...*
  context-menu action to every CDT project. Renames the workspace project, moves
  the on-disk folder, and rewrites matching CDT run configurations.

## Path discovery

Both plugins resolve `<GLIST_HOME>` at runtime from the workspace location:

```
<GLIST_HOME>/
├── GlistEngine/engine/
├── myglistapps/<AppName>/
├── glistplugins/<PluginName>/
└── zbin/<glistzbin-os>/eclipse/
    ├── eclipsecpp{,-arm64,-x86_64}/
    └── workspace/                   <-- ResourcesPlugin.getWorkspace().getRoot()
```

`GLIST_HOME = workspace/../../../..` — so the same JAR works for any user, any
arch, anywhere the zbin is extracted.

## Building

Requires an Eclipse 2025-12+ install (for the JRE 21 + plugin classpath). The
build script discovers every plugin under this repo and produces one JAR per
plugin into the plugin's own directory.

```bash
# macOS (Apple Silicon)
export ECLIPSE_HOME=~/dev/glist/zbin/glistzbin-macos/eclipse/eclipsecpp-arm64/Eclipse.app/Contents/Eclipse
./build.sh

# macOS (Intel)
export ECLIPSE_HOME=~/dev/glist/zbin/glistzbin-macos/eclipse/eclipsecpp-x86_64/Eclipse.app/Contents/Eclipse
./build.sh

# Linux
export ECLIPSE_HOME=~/dev/glist/zbin/glistzbin-linux/eclipse/eclipsecpp
./build.sh
```

```bat
REM Windows
set ECLIPSE_HOME=C:\dev\glist\zbin\glistzbin-win64\eclipse\eclipsecpp
build.bat
```

Both scripts iterate every subdirectory with `META-INF/MANIFEST.MF`, parse the
base version (`x.y.z`) from `Bundle-Version`, stamp a fresh timestamp suffix,
compile against Eclipse's bundled plugin classpath, and emit
`<symbolic-name>_<base>.<timestamp>.jar`. Compiled JARs are platform-neutral —
build once on any OS, ship the same artefact to every zbin.

The zbin builders pick the JARs up automatically; see
[InstallScripts/tools/zbin-builders/README.md](https://github.com/GlistEngine/InstallScripts/tree/main/tools/zbin-builders).

## Adding a new plugin

1. Create a subdirectory with the standard PDE layout:
   ```
   com.aitial.glist.<thing>/
     META-INF/MANIFEST.MF        (Bundle-SymbolicName + Bundle-Version: x.y.z.qualifier)
     plugin.xml
     build.properties
     src/<package>/*.java
     icons/                       (optional)
   ```
2. Run `./build.sh` — your plugin gets discovered and built automatically.
3. Re-run the macOS zbin builder; the new JAR is registered in `bundles.info`
   alongside the others.

## License

Same terms as GlistEngine — see [LICENSE](LICENSE).
