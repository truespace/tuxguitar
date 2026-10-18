package app.tuxguitar.applemusic;

import java.io.File;
import java.util.Iterator;

import app.tuxguitar.app.util.MidiTickUtil;
import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.event.TGEvent;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.util.TGContext;

/**
 * Plays the recording loaded in the Music app along with the score.
 *
 * The recording is the master, because an interruption of the music is heard while a cursor a
 * few milliseconds off is not: TuxGuitar is told where the music really is, rather than the
 * other way around. Commands to the Music app take about 200 to 300 milliseconds to take
 * effect, which is why the position is measured afterwards instead of being assumed.
 */
public class AppleMusicSync implements TGEventListener {

	// the Music app needs this long before it reports where it really is
	private static final long SETTLE_TIME = 600;
	private static final long CHECK_INTERVAL = 2000;
	// a correction of the score position is only worth its jump beyond this
	private static final long CORRECTION_THRESHOLD = 120;
	private static final int READINGS = 3;

	private final TGContext context;
	private final AppleMusicSettings settings;
	private final AppleMusicBridge bridge;
	private final File helper;

	private Thread thread;
	private volatile boolean running;
	private volatile String status;
	private volatile long drift;

	public AppleMusicSync(TGContext context, File helper) {
		this.context = context;
		this.settings = new AppleMusicSettings(context);
		this.bridge = new AppleMusicBridge();
		this.helper = helper;
		this.setStatus("");
	}

	private void setStatus(String status) {
		this.status = status;
		// the drift of every check lands in the console as well, where a session can be reviewed
		System.out.println("[applemusic] " + status);
	}

	public AppleMusicSettings getSettings() {
		return this.settings;
	}

	/** last line of what the synchronisation is doing, for the dialog */
	public String getStatus() {
		return this.status;
	}

	/** how far the score is behind the recording, in milliseconds */
	public long getDrift() {
		return this.drift;
	}

	public void connect() {
		MidiPlayer.getInstance(this.context).addListener(this);
	}

	public void disconnect() {
		MidiPlayer.getInstance(this.context).removeListener(this);
		this.stop();
		this.bridge.close();
	}

	public String getTrackName() {
		try {
			this.bridge.open(this.helper);

			return this.bridge.getTrackName();
		} catch (Throwable throwable) {
			this.setStatus(throwable.getMessage());

			return null;
		}
	}

	public void processEvent(TGEvent event) {
		if( MidiPlayerEvent.EVENT_TYPE.equals(event.getEventType()) ) {
			Integer type = event.getAttribute(MidiPlayerEvent.PROPERTY_NOTIFICATION_TYPE);
			if( type != null && type.intValue() == MidiPlayerEvent.NOTIFY_STARTED ) {
				this.start();
			}
			if( type != null && type.intValue() == MidiPlayerEvent.NOTIFY_STOPPED ) {
				this.stop();
			}
		}
	}

	private synchronized void start() {
		if(!this.settings.isEnabled() ) {
			return;
		}
		this.stop();

		this.running = true;
		this.thread = new Thread(new Runnable() {
			public void run() {
				AppleMusicSync.this.follow();
			}
		}, "tuxguitar-apple-music");
		this.thread.setDaemon(true);
		this.thread.start();
	}

	private synchronized void stop() {
		boolean wasRunning = this.running;
		this.running = false;
		this.thread = null;

		if( wasRunning ) {
			try {
				this.bridge.pause();
			} catch (Throwable throwable) {
				this.setStatus(throwable.getMessage());
			}
		}
	}

	private void follow() {
		try {
			this.bridge.open(this.helper);

			double scoreSeconds = this.getScoreSeconds(this.getPlayerTick());
			this.bridge.seek(this.toMusicSeconds(scoreSeconds));
			this.bridge.play();
			this.setStatus(String.format("started at %.2fs of the score", scoreSeconds));

			Thread.sleep(SETTLE_TIME);
			// the first correction happens before anyone could follow the score anyway
			this.align(true);

			while( this.running ) {
				Thread.sleep(CHECK_INTERVAL);
				if( this.running ) {
					this.align(false);
				}
			}
		} catch (InterruptedException interruption) {
			Thread.currentThread().interrupt();
		} catch (Throwable throwable) {
			this.setStatus(String.valueOf(throwable.getMessage()));
		}
	}

	/**
	 * Compares where the recording is with where the score is, and moves the score when it is
	 * too far off. Several readings are taken because each one is only known within its own
	 * window: the shortest of them is the most precise.
	 */
	private void align(boolean correctAnyway) throws Exception {
		AppleMusicBridge.Reading best = null;
		for(int i = 0; i < READINGS && this.running; i ++) {
			AppleMusicBridge.Reading reading = this.bridge.getPosition();
			if( best == null || reading.window < best.window ) {
				best = reading;
			}
		}
		if( best == null || !this.running ) {
			return;
		}

		long playerTick = this.getPlayerTick();
		double scoreSeconds = this.getScoreSeconds(playerTick);
		// where the recording was when it was read, brought forward to now
		double musicNow = (best.position + ((System.currentTimeMillis() - best.readAt) / 1000.0));
		double expectedScoreSeconds = this.toScoreSeconds(musicNow);

		this.drift = Math.round((expectedScoreSeconds - scoreSeconds) * 1000);
		this.setStatus(String.format("score %.2fs, recording %.2fs, drift %+d ms (read within %d ms)",
			scoreSeconds, musicNow, Long.valueOf(this.drift), Long.valueOf(best.window)));

		if( correctAnyway || Math.abs(this.drift) > CORRECTION_THRESHOLD ) {
			this.setScoreSeconds(expectedScoreSeconds);
		}
	}

	private long getPlayerTick() {
		return MidiTickUtil.getStart(this.context, MidiPlayer.getInstance(this.context).getTickPosition());
	}

	/** recording position of a moment of the score */
	private double toMusicSeconds(double scoreSeconds) {
		return ((this.settings.getOffset() / 1000.0) + (scoreSeconds * this.settings.getStretchFactor()));
	}

	private double toScoreSeconds(double musicSeconds) {
		return ((musicSeconds - (this.settings.getOffset() / 1000.0)) / this.settings.getStretchFactor());
	}

	/**
	 * Seconds of score before a tick, following the tempo of every measure up to it. The playback
	 * speed of the player is taken into account, so that practising slowly stays in sync.
	 */
	private double getScoreSeconds(long tick) {
		TGSong song = TGDocumentManager.getInstance(this.context).getSong();
		double percent = (MidiPlayer.getInstance(this.context).getMode().getCurrentPercent() / 100.0);
		double seconds = 0;

		Iterator<TGMeasureHeader> headers = song.getMeasureHeaders();
		while( headers.hasNext() ) {
			TGMeasureHeader header = headers.next();
			double secondsPerTick = (60.0 / (header.getTempo().getQuarterValue() * percent) / TGDuration.QUARTER_TIME);
			long length = header.getLength();

			if( tick >= (header.getStart() + length) ) {
				seconds += (length * secondsPerTick);
			} else {
				seconds += (Math.max(0, tick - header.getStart()) * secondsPerTick);

				return seconds;
			}
		}
		return seconds;
	}

	/** moves the score playback to a moment given in seconds */
	private void setScoreSeconds(double scoreSeconds) {
		TGSong song = TGDocumentManager.getInstance(this.context).getSong();
		double percent = (MidiPlayer.getInstance(this.context).getMode().getCurrentPercent() / 100.0);
		double seconds = 0;

		Iterator<TGMeasureHeader> headers = song.getMeasureHeaders();
		while( headers.hasNext() ) {
			TGMeasureHeader header = headers.next();
			double secondsPerTick = (60.0 / (header.getTempo().getQuarterValue() * percent) / TGDuration.QUARTER_TIME);
			double measureSeconds = (header.getLength() * secondsPerTick);

			if( scoreSeconds < (seconds + measureSeconds) ) {
				long start = (header.getStart() + Math.round((scoreSeconds - seconds) / secondsPerTick));
				MidiPlayer.getInstance(this.context).setTickPosition(MidiTickUtil.getTick(this.context, start));

				return;
			}
			seconds += measureSeconds;
		}
	}

}
