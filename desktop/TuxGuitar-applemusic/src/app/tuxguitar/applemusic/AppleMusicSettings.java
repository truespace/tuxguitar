package app.tuxguitar.applemusic;

import java.net.URI;

import app.tuxguitar.app.document.TGDocument;
import app.tuxguitar.app.document.TGDocumentListManager;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.util.TGContext;

/**
 * Settings of the synchronisation.
 *
 * Whether to play along and whether to silence the score are TuxGuitar's own, and are kept in
 * its configuration. The offset and the speed belong to the score instead, and are kept per
 * score file by {@link AppleMusicSongSettings}: the count-in and the tempo of a recording are
 * properties of that recording, not of the program. Only a score that has never been saved,
 * and so has no file to remember it by, falls back to the configuration.
 */
public class AppleMusicSettings {

	public static final String KEY_ENABLED = "applemusic.enabled";
	public static final String KEY_OFFSET = "applemusic.offset";
	public static final String KEY_STRETCH = "applemusic.stretch";
	public static final String KEY_MUTE_SCORE = "applemusic.mute-score";

	// seconds of the recording before the first measure of the score
	public static final int DEFAULT_OFFSET = 0;
	// recording seconds per score second, in percent: a recording slower than the score is above 100
	public static final int DEFAULT_STRETCH = 100;

	public static final int MINIMUM_STRETCH = 50;
	public static final int MAXIMUM_STRETCH = 200;

	// a minute of count-in either way is more than any recording asks for
	public static final int MINIMUM_OFFSET = -60000;
	public static final int MAXIMUM_OFFSET = 600000;

	private final TGContext context;
	private final AppleMusicSongSettings songs;

	public AppleMusicSettings(TGContext context) {
		this.context = context;
		this.songs = new AppleMusicSongSettings();
	}

	/** silence the instruments of the score while the recording plays, which is the point of it */
	public boolean isMuteScore() {
		return this.getConfig().getBooleanValue(KEY_MUTE_SCORE, true);
	}

	public void setMuteScore(boolean mute) {
		this.getConfig().setValue(KEY_MUTE_SCORE, mute);
	}

	public boolean isEnabled() {
		return this.getConfig().getBooleanValue(KEY_ENABLED);
	}

	public void setEnabled(boolean enabled) {
		this.getConfig().setValue(KEY_ENABLED, enabled);
	}

	/** milliseconds of the recording before the score starts, may be negative */
	public int getOffset() {
		URI uri = this.getSongUri();
		int offset = (uri != null
			? this.songs.getOffset(uri, DEFAULT_OFFSET)
			: this.getConfig().getIntegerValue(KEY_OFFSET, DEFAULT_OFFSET));

		return clampOffset(offset);
	}

	public void setOffset(int milliseconds) {
		URI uri = this.getSongUri();
		if( uri != null ) {
			this.songs.setOffset(uri, clampOffset(milliseconds));
		} else {
			this.getConfig().setValue(KEY_OFFSET, clampOffset(milliseconds));
		}
	}

	/** the dialog cannot show more than this, and a link should not mean more than it shows */
	public static int clampOffset(int milliseconds) {
		return Math.min(MAXIMUM_OFFSET, Math.max(MINIMUM_OFFSET, milliseconds));
	}

	/** tenths of a percent, so that 1000 is "the recording runs exactly at the tempo of the score" */
	public int getStretch() {
		URI uri = this.getSongUri();
		int stretch = (uri != null
			? this.songs.getStretch(uri, DEFAULT_STRETCH * 10)
			: this.getConfig().getIntegerValue(KEY_STRETCH, DEFAULT_STRETCH * 10));

		return clampStretch(stretch);
	}

	public void setStretch(int tenthsOfPercent) {
		URI uri = this.getSongUri();
		if( uri != null ) {
			this.songs.setStretch(uri, clampStretch(tenthsOfPercent));
		} else {
			this.getConfig().setValue(KEY_STRETCH, clampStretch(tenthsOfPercent));
		}
	}

	public static int clampStretch(int tenthsOfPercent) {
		return Math.min(MAXIMUM_STRETCH * 10, Math.max(MINIMUM_STRETCH * 10, tenthsOfPercent));
	}

	/** whether the offset and the speed below were lined up for the score that is open */
	public boolean isRemembered() {
		URI uri = this.getSongUri();

		return (uri != null && this.songs.isKnown(uri));
	}

	/** the score goes back to lining up the way one never played along with does */
	public void forget() {
		URI uri = this.getSongUri();
		if( uri != null ) {
			this.songs.forget(uri);
		}
	}

	/**
	 * The file of the score that is open, which the offset and the speed are remembered by, or
	 * null while the score has never been saved.
	 */
	public URI getSongUri() {
		try {
			TGDocument document = TGDocumentListManager.getInstance(this.context).findCurrentDocument();

			return (document != null ? document.getUri() : null);
		} catch (Throwable throwable) {
			return null;
		}
	}

	public double getStretchFactor() {
		return (this.getStretch() / 1000.0);
	}

	private TGConfigManager getConfig() {
		return TGConfigManager.getInstance(this.context);
	}
}
