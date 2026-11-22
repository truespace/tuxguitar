#!/bin/bash
# Signs the macOS application bundles, which macOS requires before it grants any permission:
# an unsigned TuxGuitar is refused access to the microphone (the tuner), to the Documents
# folder and to the Music app (Apple Music sync), and is not even asked about.
#
#   desktop/build-scripts/macosx-sign-app.sh [path to the .app ...]
#
# Without an argument it signs every bundle that was built. Signs ad hoc, which is enough on
# the machine that builds it; set TG_SIGN_IDENTITY to a Developer ID to sign for other machines.
set -e

IDENTITY=${TG_SIGN_IDENTITY:--}

# Named bundles, or every one that was built: signing only the first of them signed whichever
# flavour sorted first and left the one that had actually been rebuilt unsigned, and so refused
# every permission it had been granted before
if [ $# -gt 0 ]; then
	APPS=("$@")
else
	BUILD_SCRIPTS=$(cd "$(dirname "$0")" && pwd)
	APPS=()
	for CANDIDATE in "$BUILD_SCRIPTS"/tuxguitar-macosx-*/target/*.app; do
		[ -d "$CANDIDATE" ] && APPS+=("$CANDIDATE")
	done
fi
if [ ${#APPS[@]} -eq 0 ]; then
	echo "usage: $(basename "$0") [path to the .app ...]" >&2
	echo "nothing is built under $(dirname "$0")/tuxguitar-macosx-*/target" >&2
	exit 1
fi

sign_app() {
	APP=$1
	if [ ! -d "$APP" ]; then
		echo "no bundle at $APP" >&2
		exit 1
	fi

	# 1. codesign reads every directory below Contents/MacOS as nested code, and gives up on
	#    the runtime there ("bundle format unrecognized, invalid, or unsuitable"): the launcher
	#    looks for the runtime in Contents/runtime as well
	if [ -d "$APP/Contents/MacOS/jre" ]; then
		echo "moving the runtime of $(basename "$APP") to Contents/runtime"
		mkdir -p "$APP/Contents/runtime"
		rm -rf "$APP/Contents/runtime/jre"
		mv "$APP/Contents/MacOS/jre" "$APP/Contents/runtime/jre"
	fi
	if [ ! -x "$APP/Contents/runtime/jre/bin/java" ]; then
		echo "no runtime in $APP: create one with jlink (see INSTALL.md)" >&2
		exit 1
	fi

	# 2. the licence files of a jlink runtime are symbolic links pointing out of their folder,
	#    which codesign refuses as well: the same text as a copy signs fine
	find "$APP/Contents/runtime/jre/legal" -type l -print0 2>/dev/null | while IFS= read -r -d '' LINK; do
		TARGET=$(cd "$(dirname "$LINK")" && readlink -f "$(basename "$LINK")")
		rm "$LINK"
		cp "$TARGET" "$LINK"
	done

	# 3. a crash log the JVM wrote inside the bundle breaks the seal just as an edit does, and
	#    the bundle is then refused with "a sealed resource is missing or invalid"
	find "$APP/Contents" -name 'hs_err_pid*.log' -o -name 'replay_pid*.log' | while IFS= read -r LOG; do
		echo "removing the crash log $(basename "$LOG"), which would break the seal"
		rm -f "$LOG"
	done

	# 4. sign everything, the runtime first
	codesign --force --deep --sign "$IDENTITY" "$APP"
	codesign --verify --verbose=1 "$APP"

	echo "signed $APP"
}

for APP in "${APPS[@]}"; do
	sign_app "$APP"
done

echo "macOS asks about the microphone, the Documents folder and the Music app on first use;"
echo "changing any file inside the bundle breaks the signature, so run this again after that."
