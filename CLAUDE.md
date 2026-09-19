# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

TuxGuitar is a multitrack tablature editor/player written in Java (upstream: helge17/tuxguitar). Desktop builds use Maven; Android uses Gradle.

## Build

There is no root `pom.xml`. Each {platform, UI toolkit} combination has its own aggregator project in `desktop/build-scripts/<variant>/pom.xml`, which lists the modules (from both `desktop/` and `common/`) that go into that variant and assembles the distributable. The shared parent POM is `desktop/pom.xml` (`tuxguitar-pom`, Java `release 9`, platform profiles `platform-linux` / `platform-windows` / `platform-macos-cocoa` / `platform-freebsd`).

SWT is not in Maven Central: it must first be installed into the local repo with `mvn install:install-file` (version 4.37; see `INSTALL.md` for the per-platform command).

```sh
# macOS
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn -e clean verify -P native-modules
# -> target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app
# The launcher (Contents/MacOS/tuxguitar.sh) runs ./jre/bin/java, which the Maven build does NOT create.
# Bundle one as the release script does (misc/build_tuxguitar_from_source.sh); SWT jar arch must match (aarch64 on Apple Silicon):
jlink --add-modules java.desktop --output target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app/Contents/MacOS/jre

# Linux: tuxguitar-linux-swt (or -swt-deb); Windows is cross-built from Linux:
cd desktop/build-scripts/tuxguitar-windows-swt-x86_64
mvn -e clean verify -P native-modules -P -platform-linux -P platform-windows

# Android
cd android/build-scripts/tuxguitar-android && ./gradlew assembleRelease
```

- `-P native-modules` adds the C/JNI modules in `desktop/build-scripts/native-modules/` (ALSA, JACK, FluidSynth, LV2, AudioUnit, WinMM…). CI builds without it.
- Only SWT variants are released; JavaFX (`*-jfx*`) and Qt variants are not guaranteed to work.
- The build output tree has `lib/` (classpath) and `share/` (resources). To run from an IDE, main class is `app.tuxguitar.app.TGMainSingleton` with `-Dtuxguitar.share.path=<target>/.../share/` and `-Dtuxguitar.home.path=<target>/.../` pointing at a previously built variant (see `docs/IDEs.md`).

## Tests

JUnit 5 tests exist only in a few `common/` modules (`TuxGuitar-lib`, `TuxGuitar-editor-utils`, `TuxGuitar-compat`, `TuxGuitar-midi`) and run during `mvn verify`. `-DskipTests` skips them (discouraged). Tests fail if the checkout path contains non-ASCII characters.

Run a single test from a build-script directory, e.g.:

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn test -pl ../../../common/TuxGuitar-lib -am -Dtest=TestVersion -Dsurefire.failIfNoSpecifiedTests=false
```

## Architecture

- **`common/`** — code shared by desktop and Android. `TuxGuitar-lib` is the core: song model (`song`), `TGContext` (DI-ish singleton registry passed everywhere), the action framework (`action`: `TGActionManager`, `TGActionContext`, interceptors, pre/post-execution events), event system, file-format framework (`io.base`: `TGFileFormatManager`, `TGSongReader`/`Writer`, importers/exporters), player, and the plugin system (`util.plugin`). `TuxGuitar-editor-utils` holds the model-editing actions (`editor.action.*`) used by both frontends. Other `common/` modules are file formats (`gtp`, `gpx`, `ptb`, `ascii`, `lilypond`, `pdf`, `midi`) and `compat` (legacy `.tg` format readers). `common/resources/` holds translations (`lang/`), templates, tunings, demo songs.
- **`desktop/TuxGuitar`** — the desktop application: main entry, editor/view, dialogs, and `action/installer/TGActionInstaller`, which registers every action by id (`TGActionIdList`). UI code talks to the abstract toolkit in `TuxGuitar-ui-toolkit` (`UIFactory`, widgets, layouts); `TuxGuitar-ui-toolkit-swt` / `-jfx` / `-qt` are the implementations selected by the build variant.
- **Plugins** — most other `desktop/TuxGuitar-*` modules are plugins (file-format UIs, synths, tuner, converter, MIDI ports…). A plugin implements `app.tuxguitar.util.plugin.TGPlugin` and is discovered via `share/META-INF/services/app.tuxguitar.util.plugin.TGPlugin` (plus a `share/META-INF/*.info` descriptor). Adding a plugin to a variant means adding its module to the relevant `desktop/build-scripts/*/pom.xml` files.
- **Source layout differs**: `common/*` modules use `src/main/java`; `desktop/*` modules use `src/` directly, with resources in `share/`.
- **`android/`** — Android app and Android-specific plugins. The Gradle build does not reference `common/` as Gradle projects: `apk/build.gradle` copies the `common/` module sources into `build/generated/tuxguitar-android-apk-src`, stripping `META-INF/services` plugin registrations. New shared modules must be added there explicitly.

## Conventions

- Package root is `app.tuxguitar`; classes are prefixed `TG` (core) or `UI` (toolkit).
- Existing Java formatting is heterogeneous (tabs are common). New files should follow `misc/tuxguitar_formatting.xml` (Eclipse formatter). Don't reformat existing files as part of a functional change — reformatting goes in its own commit.
- UI strings go in `common/resources/lang/messages.properties` (English); plugin-specific strings live in each plugin's `share/lang/`. `misc/messages_desktop_sort.pl` and `misc/messages_desktop_to_android.pl` maintain/derive the translation files.
- PRs target `master`; risky changes go to `tuxguitar-next`. Upstream discourages AI-generated contributions: every submitted line must be explainable by the contributor (`docs/CONTRIBUTING.md`).
