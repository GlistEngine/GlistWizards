# GlistWizards

Eclipse plugin that integrates the [GlistEngine](https://github.com/GlistEngine/GlistEngine)
toolchain into the IDE. Ships inside the GlistEngine Eclipse bundles (`glistzbin-win64`,
`glistzbin-macos`) and provides:

- **GlistApp Project wizard** — `File > New > Project > GlistApp > GlistApp Project`.
  Clones `https://github.com/GlistEngine/GlistApp` into `<GLIST_HOME>/myglistapps/<name>/`,
  re-initialises the git repo so the user starts with a clean history, imports it as a
  CDT project, and creates a matching CDT Run Configuration.

- **Startup auto-import** — on every Eclipse boot the plugin walks the canonical
  GlistEngine directory layout (resolved at runtime from the workspace location, no
  baked-in absolute paths) and imports any projects it finds that aren't already in
  the workspace. This is what makes the shipped Eclipse bundles user-agnostic: students
  can extract the same archive to any home directory and existing projects under
  `~/dev/glist/GlistEngine/engine` and `~/dev/glist/myglistapps/*` light up automatically.

## Layout assumption

The plugin discovers paths relative to the Eclipse workspace directory:

```
<GLIST_HOME>/
├── GlistEngine/engine/         <-- auto-imported on boot
├── myglistapps/<AppName>/      <-- auto-imported on boot; wizard creates new ones here
└── zbin/<glistzbin-os>/eclipse/
    ├── eclipsecpp{,-arm64,-x86_64}/
    └── workspace/              <-- ResourcesPlugin.getWorkspace().getRoot()
```

`GLIST_HOME` is computed as `workspace/../../../..` — so the path follows wherever the
zbin is extracted.

## Building

Requires an Eclipse 2025-12+ install (for the JRE 21 + Eclipse plugin classpath).
Point `ECLIPSE_HOME` at the Eclipse install directory:

```bash
# macOS (Apple Silicon)
export ECLIPSE_HOME=~/dev/glist/zbin/glistzbin-macos/eclipse/eclipsecpp-arm64/Eclipse.app/Contents/Eclipse
./build.sh

# macOS (Intel)
export ECLIPSE_HOME=~/dev/glist/zbin/glistzbin-macos/eclipse/eclipsecpp-x86_64/Eclipse.app/Contents/Eclipse
./build.sh

# Windows / Linux (set to the directory containing plugins/, configuration/, eclipse.ini)
export ECLIPSE_HOME=C:/dev/glist/zbin/glistzbin-win64/eclipse/eclipsecpp
./build.sh
```

The compiled JAR is platform-neutral; build on whatever host is convenient
and ship the same JAR to every zbin (arm64, x86_64, Windows, Linux).

The script produces `com.aitial.glist.wizards_<version>.jar` in the repo root. To install
it into an existing Eclipse bundle, drop the JAR into `Contents/Eclipse/plugins/` (macOS)
or `eclipsecpp/plugins/` (Windows) and append a matching line to
`configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`:

```
com.aitial.glist.wizards,<version>,plugins/com.aitial.glist.wizards_<version>.jar,4,false
```

Remove any previous version's line and JAR first.

## License

Same terms as GlistEngine — see [LICENSE](LICENSE).
