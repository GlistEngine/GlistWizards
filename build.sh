#!/bin/bash
# Build com.aitial.glist.wizards plugin JAR.
#
# Requires ECLIPSE_HOME to point at an Eclipse install (the directory containing
# plugins/, configuration/, eclipse.ini). Uses Eclipse's bundled JRE + plugin
# JARs for the compile classpath, so no extra Java setup is needed beyond the
# Eclipse install.

set -e

if [[ -z "$ECLIPSE_HOME" ]]; then
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

# Required-Bundle classpath. Names without versions; we glob for whatever ships.
CP=""
for BUNDLE in \
    org.eclipse.ui \
    org.eclipse.ui.ide \
    org.eclipse.ui.workbench \
    org.eclipse.core.runtime \
    org.eclipse.core.resources \
    org.eclipse.core.jobs \
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
SWT_JAR=$(ls "$PLUGINS"/org.eclipse.swt.cocoa.macosx.aarch64_*.jar "$PLUGINS"/org.eclipse.swt.cocoa.macosx.x86_64_*.jar "$PLUGINS"/org.eclipse.swt.win32.win32.x86_64_*.jar "$PLUGINS"/org.eclipse.swt.gtk.linux.x86_64_*.jar 2>/dev/null | head -1)
[[ -n "$SWT_JAR" ]] && CP="$CP:$SWT_JAR"

# Read the base version (x.y.z) from MANIFEST.MF so we don't drift if it's bumped.
BASE_VERSION=$(grep '^Bundle-Version:' "$ROOT/META-INF/MANIFEST.MF" \
    | sed -E 's/^Bundle-Version:[[:space:]]*([0-9]+\.[0-9]+\.[0-9]+).*/\1/')
if [[ -z "$BASE_VERSION" ]]; then
    echo "Could not parse Bundle-Version from MANIFEST.MF"; exit 1
fi
VERSION="${BASE_VERSION}.$(date -u +%Y%m%d%H%M)"
JAR_NAME="com.aitial.glist.wizards_${VERSION}.jar"

rm -rf "$ROOT/bin"
mkdir "$ROOT/bin"
"$JAVA_HOME/bin/javac" --release 21 -cp "$CP" -d "$ROOT/bin" "$ROOT"/src/com/aitial/glist/wizards/*.java

# Stamp version into manifest before packing. Match any "<base>.qualifier" so
# this works regardless of the manifest's current base.
TMP_MANIFEST=$(mktemp)
sed -E "s/[0-9]+\.[0-9]+\.[0-9]+\.qualifier/${VERSION}/" "$ROOT/META-INF/MANIFEST.MF" > "$TMP_MANIFEST"

(cd "$ROOT" && "$JAVA_HOME/bin/jar" cfm "$ROOT/${JAR_NAME}" "$TMP_MANIFEST" -C bin com -C . plugin.xml -C . icons)
rm -f "$TMP_MANIFEST"

echo "Built $ROOT/$JAR_NAME"
echo
echo "Install into Eclipse by:"
echo "  1. Copy the JAR to \$ECLIPSE_HOME/plugins/"
echo "  2. Append this line to \$ECLIPSE_HOME/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info:"
echo "     com.aitial.glist.wizards,${VERSION},plugins/${JAR_NAME},4,false"
echo "     (remove any older com.aitial.glist.wizards line first)"
