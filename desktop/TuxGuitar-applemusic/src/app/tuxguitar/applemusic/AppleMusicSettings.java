package app.tuxguitar.applemusic;

import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.util.TGContext;

/**
 * Settings of the synchronisation, kept in the configuration of TuxGuitar.
 */
public class AppleMusicSettings {

	public static final String KEY_ENABLED = "applemusic.enabled";
	public static final String KEY_OFFSET = "applemusic.offset";
	public static final String KEY_STRETCH = "applemusic.stretch";

	// seconds of the recording before the first measure of the score
	public static final int DEFAULT_OFFSET = 0;
	// recording seconds per score second, in percent: a recording slower than the score is above 100
	public static final int DEFAULT_STRETCH = 100;

	public static final int MINIMUM_STRETCH = 50;
	public static final int MAXIMUM_STRETCH = 200;

	private final TGContext context;

	public AppleMusicSettings(TGContext context) {
		this.context = context;
	}

	public boolean isEnabled() {
		return this.getConfig().getBooleanValue(KEY_ENABLED);
	}

	public void setEnabled(boolean enabled) {
		this.getConfig().setValue(KEY_ENABLED, enabled);
	}

	/** milliseconds of the recording before the score starts, may be negative */
	public int getOffset() {
		return this.getConfig().getIntegerValue(KEY_OFFSET, DEFAULT_OFFSET);
	}

	public void setOffset(int milliseconds) {
		this.getConfig().setValue(KEY_OFFSET, milliseconds);
	}

	/** tenths of a percent, so that 1000 is "the recording runs exactly at the tempo of the score" */
	public int getStretch() {
		int stretch = this.getConfig().getIntegerValue(KEY_STRETCH, DEFAULT_STRETCH * 10);

		return Math.min(MAXIMUM_STRETCH * 10, Math.max(MINIMUM_STRETCH * 10, stretch));
	}

	public void setStretch(int tenthsOfPercent) {
		this.getConfig().setValue(KEY_STRETCH, tenthsOfPercent);
	}

	public double getStretchFactor() {
		return (this.getStretch() / 1000.0);
	}

	private TGConfigManager getConfig() {
		return TGConfigManager.getInstance(this.context);
	}
}
