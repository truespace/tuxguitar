#!/bin/bash
# Signs the macOS application bundle, which macOS requires before it grants any permission:
# an unsigned TuxGuitar is refused access to the microphone (the tuner), to the Documents
# folder and to the Music app (Apple Music sync), and is not even asked about.
#
#   desktop/build-scripts/macosx-sign-app.sh [path to the .app]
#
# Signs ad hoc by default, which is enough on the machine that builds it. Set TG_SIGN_IDENTITY
# to a Developer ID to sign for other machines.
set -e

APP=${1:-}
IDENTITY=${TG_SIGN_IDENTITY:--}

if [ -z "$APP" ]; then
	BUILD_SCRIPTS=$(cd "$(dirname "$0")" && pwd)
	APP=$(ls -d "$BUILD_SCRIPTS"/tuxguitar-macosx-*/target/*.app 2>/dev/null | head -1)
fi
if [ ! -d "$APP" ]; then
	echo "usage: $(basename "$0") <path to the .app>" >&2
	exit 1
fi

# 1. codesign reads every directory below Contents/MacOS as nested code, and gives up on the
#    runtime there ("bundle format unrecognized, invalid, or unsuitable"): the launcher looks
#    for the runtime in Contents/runtime as well
if [ -d "$APP/Contents/MacOS/jre" ]; then
	echo "moving the runtime to Contents/runtime"
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

# 3. sign everything, the runtime first
codesign --force --deep --sign "$IDENTITY" "$APP"
codesign --verify --verbose=1 "$APP"

echo "signed $APP"
echo "macOS asks about the microphone, the Documents folder and the Music app on first use;"
echo "changing any file inside the bundle breaks the signature, so run this again after that."
