#!/bin/sh
# Builds the KaSH application bundle and installs it in a folder that Spotlight and Raycast index, so that it can be launched from them.
# Usage: scripts/install.sh [--dest <folder>]
# The folder is ~/Applications by default (no administrator rights needed), or $KASH_INSTALL_DIR, or --dest (for instance /Applications).
set -e

usage() {
    echo "Usage: scripts/install.sh [--dest <folder>]"
    echo "Builds KaSH.app and installs it in <folder> (default: \$KASH_INSTALL_DIR, else ~/Applications)."
}

fail() {
    echo "install.sh: $1" >&2
    exit 1
}

dest="${KASH_INSTALL_DIR:-$HOME/Applications}"
while [ $# -gt 0 ]; do
    case "$1" in
        --dest)
            [ $# -ge 2 ] || fail "--dest needs a folder"
            dest="$2"
            shift 2
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            exit 1
            ;;
    esac
done

# Gradle's tasks are those of the project, whatever the folder that the script is started from.
cd "$(dirname "$0")/.."

# KaSH only builds and runs on macOS (its JCEF is built for the machine's CPU, Apple Silicon or Intel).
[ "$(uname -s)" = "Darwin" ] || fail "KaSH only runs on macOS"
# The build makes JCEF from this submodule, and its dependencies with vcpkg, a submodule of it. They are not initialized here: the script does
# not change the checkout. The tools that it needs (brew install cmake ninja pkgconf bison) are checked by the build.
[ -f jcef/CMakeLists.txt ] && [ -f jcef/third_party/vcpkg/bootstrap-vcpkg.sh ] \
    || fail "the jcef submodule is missing: run 'git submodule update --init --recursive'"

mkdir -p "$dest"
dest="$(cd "$dest" && pwd -P)"
installed="$dest/KaSH.app"
staging="$dest/.KaSH.app.new"

# Replacing the bundle under a program that runs from it would break it (KaSH, and Chromium's helpers, which are in it too). Nothing is stopped
# for the user: other instances may be theirs, and KaSH has to quit by itself to keep what its services write.
# The command line of a process of the bundle starts with its path (and awk's own does not, which `pgrep -f` would match).
if ps -axo command= | awk -v path="$installed/Contents/" 'index($0, path) == 1 { found = 1 } END { exit !found }'; then
    fail "KaSH is running from $installed: quit it and run this again"
fi

# The bundle, with the executable bits of Chromium's helpers given back (restoreExecutableBits, which follows it).
./gradlew :app:createDistributable

bundle="app/build/compose/binaries/main/app/KaSH.app"
[ -d "$bundle" ] || fail "the build did not make $bundle"

# The new bundle is complete before the old one goes, so a copy that fails leaves the installed application as it was.
# The staging folder does not end with .app, so that Launch Services never takes it for an application.
trap 'rm -rf "$staging"' EXIT
rm -rf "$staging"
ditto "$bundle" "$staging"
rm -rf "$installed"
mv "$staging" "$installed"

# jpackage signs the bundle ad hoc: a copy that lost that would not open.
codesign --verify --deep --strict "$installed" || fail "the signature of the installed $installed is not valid"

# Spotlight and Launch Services find a new application on their own, but after a delay: say it now.
mdimport "$installed" || true
lsregister="/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
if [ -x "$lsregister" ]; then
    "$lsregister" -f "$installed" || true
fi

version="$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$installed/Contents/Info.plist")"
echo "Installed KaSH $version in $installed"
echo "Open it from Spotlight or Raycast, or with: open -a \"$installed\""
