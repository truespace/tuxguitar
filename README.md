# TuxGuitar

## Description

TuxGuitar is an open source multitrack tablature editor and player written in Java.

## Download and installation

The official website for TuxGuitar is https://www.tuxguitar.app/.

You can find ready to use installation packages for Linux, Windows, macOS, FreeBSD and Android here: [releases](https://github.com/helge17/tuxguitar/releases/).

Please download TuxGuitar only from the website or the GitHub link provided above.

To build TuxGuitar from source code, refer to the [INSTALL.md](INSTALL.md) file.

## iPad / iOS build (experimental, this fork only)

This fork adds an iOS build of TuxGuitar: the JavaFX user interface compiled to a native
iOS app with GraalVM and Gluon. The editor, the file formats, opening and saving through
the Files app and sound playback work; PDF export, printing, the tuner and external MIDI
devices are not part of the build.

There is no ready made package: build and sign it yourself with an Apple developer
account, as described in [INSTALL.md](INSTALL.md#build-for-ipados--ios-experimental).

### Opening a file from a link

Both this iOS build and the macOS build register the `tuxguitar:` URL scheme, so a link on a
web page (or in any other application) can open a song in TuxGuitar:

```html
<a href="tuxguitar://open?path=songs/song.gp5">Open in TuxGuitar</a>
<a href="tuxguitar:///Users/someone/songs/song.gp5">Open in TuxGuitar</a>
```

A relative path is resolved against the Documents folder of the app on iOS (the folder the
Files app shows as "On My iPad / TuxGuitar"), and against the home folder on macOS. If
TuxGuitar is already running, the file is opened in a new tab.

On macOS the app reads the file with the permissions it was granted: a build that is not
signed and not listed under Privacy & Security cannot read the Documents, Desktop or
Downloads folders, and opening a file from there fails with "Operation not permitted".

## Playing along with a recording (macOS, experimental)

The `tuxguitar-applemusic` plugin plays the recording loaded in the Music app along with the
score: pick the song in Music, open **Tools > Apple Music sync**, switch it on, and press play.
The recording is the master, and the score is moved onto it, because a cursor a few
milliseconds off is not noticed while an interruption of the music is.

Two settings line the two up: how much of the recording comes before the first measure, and
how fast the recording runs compared to the tempo of the score. Rather than typing them, put
the caret on a measure, scrub the recording in Music to the moment that measure is heard, and
press one of the two anchor buttons: the first takes the offset from it, the second the speed.
The dialog shows the drift that remains, so they can be checked while the score plays.

While the recording plays, the instruments of the score are silenced, which is usually the
point; the dialog has a switch for it.

Measured on an M4 Mac mini, a recording streamed from Apple Music stayed within 35 to 45 ms of
the score over a minute, without growing. What it cannot follow is a recording that does not
keep a steady tempo: it has no beat map, only an offset and a speed.

The plugin needs permission to control the Music app, which macOS only asks for when the
application bundle is signed (see INSTALL.md): an unsigned build is refused without a prompt.

## Contribute

If you want to contribute to TuxGuitar, you will find a helpful description in the [CONTRIBUTING.md](docs/CONTRIBUTING.md) file.

## License

TuxGuitar is released under the GNU Lesser General Public License.

Copyright (C) 2005-2022 Julián Casadesús
              2023-2025 guiv42, helge17

## Third party products

TuxGuitar includes the following third party products:

* SWT version: SWT (Standard Widget Toolkit): https://www.eclipse.org/swt/
* JFX version: JavaFX (Java client application platform): https://openjfx.io/
* Gervill (Java Software Synthesizer)
* iText (Free Java-PDF library): https://itextpdf.com/
* Magic Sound Font v2.0 - Contributed by Dennis Deutschmann
