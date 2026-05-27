#!/bin/bash
# Build every Eclipse plugin under this repo into a JAR.
#
# Each plugin lives in a sibling directory with the standard PDE layout:
#   <plugin-symbolic-name>/
#     META-INF/MANIFEST.MF      (Bundle-SymbolicName + Bundle-Version: x.y.z.qualifier)
#     plugin.xml
#     build.properties
#     src/<package>/*.java
#     icons/                    (optional)
#
# Output: one <symbolic-name>_<base>.<timestamp>.jar per plugin in the plugin's
# own directory.
#
# Requires ECLIPSE_HOME to point at an Eclipse install (the directory containing
# plugins/, configuration/, eclipse.ini). Uses Eclipse's bundled JRE + plugin
# JARs for the compile classpath.

set -e

if [[ -z "${ECLIPSE_HOME:-}" ]]; then
    echo "ECLIPSE_HOME not set. Point it at an Eclipse install, e.g."
    echo "  export ECLIPSE_HOME=~/dev/glist/zbin/glistzbin-macos/eclipse/eclipsecpp-arm64/Eclipse.app/Contents/Eclipse"
    exit 1
fi

ROOT="$(cd "$(dirname "$0")" && pwd -P)"
PLUGINS="$ECLIPSE_HOME/plugins"

JRE_DIR=$(ls -d "$PLUGINS"/org.eclipse.justj.openjdk.hotspot.jre.full.*_*/jre 2>/dev/null | head -1)
if [[ -z "$JRE_DIR" ]]; then
    echo "No bundled JRE found under $PLUGINS"; exit 1
fi
JAVA_HOME="$JRE_DIR"

# Build the shared compile classpath once — every plugin gets the same set of
# Eclipse bundles available.
CP=""
for BUNDLE in \
    org.eclipse.ui \
    org.eclipse.ui.ide \
    org.eclipse.ui.workbench \
    org.eclipse.core.runtime \
    org.eclipse.core.resources \
    org.eclipse.core.jobs \
    org.eclipse.core.commands \
    org.eclipse.core.expressions \
    org.eclipse.debug.core \
    org.eclipse.jface \
    org.eclipse.swt \
    org.eclipse.equinox.common \
    org.eclipse.osgi
do
    JAR=$(ls "$PLUGINS/${BUNDLE}_"*.jar 2>/dev/null | head -1)
    [[ -n "$JAR" ]] && CP="${CP:+$CP:}$JAR"
done

# Pick a platform-specific SWT (any will do for compile-time refs).
SWT_JAR=$(ls "$PLUGINS"/org.eclipse.swt.cocoa.macosx.aarch64_*.jar \
              "$PLUGINS"/org.eclipse.swt.cocoa.macosx.x86_64_*.jar \
              "$PLUGINS"/org.eclipse.swt.win32.win32.x86_64_*.jar \
              "$PLUGINS"/org.eclipse.swt.gtk.linux.x86_64_*.jar 2>/dev/null | head -1)
[[ -n "$SWT_JAR" ]] && CP="$CP:$SWT_JAR"

TIMESTAMP="$(date -u +%Y%m%d%H%M)"

build_plugin() {
    local plugin_dir="$1"
    local manifest="$plugin_dir/META-INF/MANIFEST.MF"
    [[ -f "$manifest" ]] || return 0

    local sym_name base_version
    sym_name=$(grep '^Bundle-SymbolicName:' "$manifest" \
        | sed -E 's/^Bundle-SymbolicName:[[:space:]]*([^;]+).*/\1/' \
        | tr -d '\r' | tr -d ' ')
    base_version=$(grep '^Bundle-Version:' "$manifest" \
        | sed -E 's/^Bundle-Version:[[:space:]]*([0-9]+\.[0-9]+\.[0-9]+).*/\1/')

    if [[ -z "$sym_name" || -z "$base_version" ]]; then
        echo "  Skipping $plugin_dir (could not parse manifest)"
        return 0
    fi

    local version="${base_version}.${TIMESTAMP}"
    local jar_name="${sym_name}_${version}.jar"

    echo "== Building ${sym_name} (${version}) =="

    # Drop any previously-built JARs for this symbolic name in-place to avoid
    # accumulating stale artefacts.
    rm -f "$plugin_dir/${sym_name}_"*.jar
    rm -rf "$plugin_dir/bin"
    mkdir "$plugin_dir/bin"

    "$JAVA_HOME/bin/javac" --release 21 -cp "$CP" -d "$plugin_dir/bin" \
        $(find "$plugin_dir/src" -name "*.java")

    local tmp_manifest
    tmp_manifest=$(mktemp)
    sed -E "s/[0-9]+\.[0-9]+\.[0-9]+\.qualifier/${version}/" "$manifest" > "$tmp_manifest"

    local jar_args=("cfm" "$plugin_dir/$jar_name" "$tmp_manifest"
                    "-C" "$plugin_dir/bin" ".")
    # Include optional resource roots (plugin.xml + icons/) if present.
    [[ -f "$plugin_dir/plugin.xml" ]] && jar_args+=("-C" "$plugin_dir" "plugin.xml")
    [[ -d "$plugin_dir/icons"      ]] && jar_args+=("-C" "$plugin_dir" "icons")
    [[ -d "$plugin_dir/OSGI-INF"   ]] && jar_args+=("-C" "$plugin_dir" "OSGI-INF")

    "$JAVA_HOME/bin/jar" "${jar_args[@]}"
    rm -f "$tmp_manifest"

    echo "  -> $plugin_dir/$jar_name"
}

found_any=false
for d in "$ROOT"/*/; do
    [[ -f "$d/META-INF/MANIFEST.MF" ]] || continue
    found_any=true
    build_plugin "${d%/}"
done

if ! $found_any; then
    echo "No plugin directories found under $ROOT"
    exit 1
fi

echo
echo "Install into Eclipse by copying each *.jar to \$ECLIPSE_HOME/plugins/ and"
echo "appending a matching line to bundles.info, or rely on the zbin builder to"
echo "wire them in automatically."
