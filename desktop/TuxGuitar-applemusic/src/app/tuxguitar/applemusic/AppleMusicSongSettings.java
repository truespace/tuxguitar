package app.tuxguitar.applemusic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Properties;

import app.tuxguitar.util.TGUserFileUtils;

/**
 * Where a recording begins and how fast it runs belongs to the score rather than to TuxGuitar:
 * every song has its own count-in, and a recording is hardly ever at exactly the tempo written
 * down. The two numbers are therefore kept here under the path of the score file, so that a
 * score lined up once lines up again every time it is opened.
 *
 * A plain properties file next to the configuration, because it is a handful of lines per song
 * and worth being readable by hand.
 */
public class AppleMusicSongSettings {

	private static final String FILE_NAME = "apple-music-songs.properties";
	private static final String PREFIX_OFFSET = "offset.";
	private static final String PREFIX_STRETCH = "stretch.";
	private static final String COMMENT = "Offset in milliseconds and speed in tenths of a percent of the recording of each score";

	private final File file;
	private Properties properties;

	public AppleMusicSongSettings() {
		this.file = new File(TGUserFileUtils.PATH_USER_CONFIG, FILE_NAME);
	}

	public boolean isKnown(URI uri) {
		String key = key(uri);

		return (key != null && (this.read(PREFIX_OFFSET + key) != null || this.read(PREFIX_STRETCH + key) != null));
	}

	public int getOffset(URI uri, int fallback) {
		return this.get(PREFIX_OFFSET, uri, fallback);
	}

	public int getStretch(URI uri, int fallback) {
		return this.get(PREFIX_STRETCH, uri, fallback);
	}

	public void setOffset(URI uri, int milliseconds) {
		this.set(PREFIX_OFFSET, uri, milliseconds);
	}

	public void setStretch(URI uri, int tenthsOfPercent) {
		this.set(PREFIX_STRETCH, uri, tenthsOfPercent);
	}

	/** the score goes back to lining up the way an unknown one does */
	public synchronized void forget(URI uri) {
		String key = key(uri);
		if( key != null ) {
			this.load();
			this.properties.remove(PREFIX_OFFSET + key);
			this.properties.remove(PREFIX_STRETCH + key);
			this.save();
		}
	}

	private int get(String prefix, URI uri, int fallback) {
		String key = key(uri);
		String value = (key != null ? this.read(prefix + key) : null);
		if( value != null ) {
			try {
				return Integer.parseInt(value.trim());
			} catch (NumberFormatException malformed) {
				// edited by hand into something that is not a number: the default is better
				// than refusing to play along
			}
		}
		return fallback;
	}

	private synchronized String read(String key) {
		this.load();

		return this.properties.getProperty(key);
	}

	private synchronized void set(String prefix, URI uri, int value) {
		String key = key(uri);
		if( key != null ) {
			this.load();
			this.properties.setProperty(prefix + key, String.valueOf(value));
			this.save();
		}
	}

	/**
	 * The path of the score file. A score that has never been saved has no URI at all, and the
	 * caller keeps its own numbers for that case.
	 */
	private static String key(URI uri) {
		if( uri == null ) {
			return null;
		}
		if( "file".equalsIgnoreCase(uri.getScheme()) ) {
			try {
				return new File(uri).getAbsolutePath();
			} catch (Throwable throwable) {
				// not a path a file can be made of, the URI itself still names the score
			}
		}
		return uri.toString();
	}

	private void load() {
		if( this.properties == null ) {
			this.properties = new Properties();
			if( this.file.exists() ) {
				InputStream stream = null;
				try {
					stream = new FileInputStream(this.file);
					this.properties.load(stream);
				} catch (Throwable throwable) {
					// unreadable: every score lines up the way an unknown one does
				} finally {
					close(stream);
				}
			}
		}
	}

	private void save() {
		OutputStream stream = null;
		try {
			File folder = this.file.getParentFile();
			if( folder != null && !folder.exists() ) {
				folder.mkdirs();
			}
			stream = new FileOutputStream(this.file);
			this.properties.store(stream, COMMENT);
		} catch (Throwable throwable) {
			// the numbers are kept for this session even when they cannot be written down
		} finally {
			close(stream);
		}
	}

	private static void close(InputStream stream) {
		if( stream != null ) {
			try {
				stream.close();
			} catch (Throwable throwable) {
				// nothing left to do about it
			}
		}
	}

	private static void close(OutputStream stream) {
		if( stream != null ) {
			try {
				stream.close();
			} catch (Throwable throwable) {
				// nothing left to do about it
			}
		}
	}
}
