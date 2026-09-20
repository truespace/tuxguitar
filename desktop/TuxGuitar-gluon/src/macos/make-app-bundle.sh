#!/bin/bash
# Wraps the native macOS binary built by "mvn package gluonfx:build" into a .app bundle,
# so that it carries the TuxGuitar icon and can be started from Finder.
# Run it from this module: src/macos/make-app-bundle.sh
set -e

MODULE_DIR=$(cd "$(dirname "$0")/../.." && pwd)
BUILD_DIR="$MODULE_DIR/target/gluonfx/aarch64-darwin"
COMMON_MACOS="$MODULE_DIR/../build-scripts/common-resources/common-macosx"
APP="$MODULE_DIR/target/TuxGuitar.app"
EXECUTABLE="tuxguitar-gluon"

if [ ! -f "$BUILD_DIR/$EXECUTABLE" ]; then
	echo "no native binary at $BUILD_DIR/$EXECUTABLE, run: mvn package gluonfx:build" >&2
	exit 1
fi

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

cp "$BUILD_DIR/$EXECUTABLE" "$APP/Contents/MacOS/$EXECUTABLE"
cp "$COMMON_MACOS/Contents/Resources/icon.icns" "$APP/Contents/Resources/icon.icns"
cp "$COMMON_MACOS/Contents/Resources/tgdoc.icns" "$APP/Contents/Resources/tgdoc.icns"
# the launcher looks for share/ next to the executable
cp -R "$MODULE_DIR/target/share" "$APP/Contents/MacOS/share"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleName</key>
	<string>TuxGuitar</string>
	<key>CFBundleIdentifier</key>
	<string>app.tuxguitar.native</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleExecutable</key>
	<string>$EXECUTABLE</string>
	<key>CFBundleIconFile</key>
	<string>icon.icns</string>
	<key>NSHighResolutionCapable</key>
	<true/>
</dict>
</plist>
PLIST

# refresh the icon Finder shows for the bundle
touch "$APP"

echo "$APP"
