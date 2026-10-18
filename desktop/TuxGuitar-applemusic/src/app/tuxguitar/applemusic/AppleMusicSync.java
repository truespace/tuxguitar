package app.tuxguitar.applemusic;

import java.io.File;

import app.tuxguitar.app.util.TGSchemeUrl;
import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.event.TGEvent;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.event.TGEventManager;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.player.base.MidiRepeatController;
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
	// a correction of the score position is only worth its jump beyond this, and the reading
	// right after one is of a score that is still being moved
	private static final long CORRECTION_THRESHOLD = 300;
	private static final long SETTLE_AFTER_CORRECTION = 3000;
	private static final int READINGS = 3;
	private static final String PARAMETER_MUSIC = "music";

	private final TGContext context;
	private final AppleMusicSettings settings;
	private final AppleMusicBridge bridge;
	private final File helper;

	private Thread thread;
	private volatile boolean running;
	private volatile String status;
	private volatile long drift;
	private long correctedAt;
	private int savedVolume = -1;

	public AppleMusicSync(TGContext context, File helper) {
		this.context = context;
		this.settings = new AppleMusicSettings(context);
		this.bridge = new AppleMusicBridge();
		this.helper = helper;
		this.status = "";
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
		TGEventManager.getInstance(this.context).addListener(TGSchemeUrl.EVENT_TYPE, this);
	}

	public void disconnect() {
		MidiPlayer.getInstance(this.context).removeListener(this);
		TGEventManager.getInstance(this.context).removeListener(TGSchemeUrl.EVENT_TYPE, this);
		this.stop();
		this.bridge.close();
	}

	/** length of the score at its written tempo, which the speed of the recording is read against */
	public double getScoreDuration() {
		return this.getScoreSeconds(Long.MAX_VALUE, 1.0);
	}

	/** where the recording is now, or -1 when it cannot be read */
	public double getMusicPosition() {
		try {
			this.bridge.open(this.helper);

			return this.bridge.getPosition().position;
		} catch (Throwable throwable) {
			this.setStatus(String.valueOf(throwable.getMessage()));

			return -1;
		}
	}

	/** length of the track loaded in the Music app, or 0 when it cannot be read */
	public double getTrackDuration() {
		try {
			this.bridge.open(this.helper);

			return this.bridge.getTrackDuration();
		} catch (Throwable throwable) {
			this.setStatus(String.valueOf(throwable.getMessage()));

			return 0;
		}
	}

	public String getTrackName() {
		try {
			this.bridge.open(this.helper);

			return this.bridge.getTrackName();
		} catch (Throwable throwable) {
			this.setStatus(String.valueOf(throwable.getMessage()));

			return null;
		}
	}

	/** seconds of score before the measure the caret sits in, used to line the two up */
	public double getCaretSeconds(long measureStart) {
		return this.getScoreSeconds(this.getPlayerTick(measureStart), 1.0);
	}

	public void processEvent(TGEvent event) {
		if( TGSchemeUrl.EVENT_TYPE.equals(event.getEventType()) ) {
			this.processSchemeUrl((String) event.getAttribute(TGSchemeUrl.PROPERTY_URL));
		}
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

	/**
	 * A "tuxguitar:" link may ask for the recording along with the score, and name the track it
	 * means: tuxguitar://open?path=song.gp5&music=Pretender. Opened that way the score is
	 * silenced, since the recording is what the link is for.
	 */
	private void processSchemeUrl(String text) {
		final String track;
		try {
			track = (text != null ? TGSchemeUrl.getParameter(text, PARAMETER_MUSIC) : null);
		} catch (Throwable throwable) {
			this.setStatus(String.valueOf(throwable.getMessage()));

			return;
		}
		if( track == null ) {
			return;
		}

		this.settings.setEnabled(true);
		this.settings.setMuteScore(true);
		this.setStatus(track.length() > 0 ? ("loading " + track) : "playing along with the current track");

		if( track.length() > 0 ) {
			// loading a track takes about a second, which the user interface should not wait for
			Thread thread = new Thread(new Runnable() {
				public void run() {
					try {
						AppleMusicSync.this.bridge.open(AppleMusicSync.this.helper);
						AppleMusicSync.this.bridge.selectTrack(track);
						AppleMusicSync.this.setStatus("loaded " + track);
					} catch (Throwable throwable) {
						AppleMusicSync.this.setStatus(String.valueOf(throwable.getMessage()));
					}
				}
			}, "tuxguitar-apple-music-select");
			thread.setDaemon(true);
			thread.start();
		}
	}

	private synchronized void start() {
		if(!this.settings.isEnabled() ) {
			return;
		}
		this.stop();

		// before anything is heard of it: the recording is what should be heard
		if( this.settings.isMuteScore() ) {
			MidiPlayer player = MidiPlayer.getInstance(this.context);
			this.savedVolume = player.getVolume();
			player.setVolume(0);
		}

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

		if( this.savedVolume >= 0 ) {
			MidiPlayer.getInstance(this.context).setVolume(this.savedVolume);
			this.savedVolume = -1;
		}
		if( wasRunning ) {
			try {
				this.bridge.pause();
			} catch (Throwable throwable) {
				this.setStatus(String.valueOf(throwable.getMessage()));
			}
		}
	}

	private void follow() {
		try {
			this.bridge.open(this.helper);

			double scoreSeconds = this.getScoreSeconds(this.getPlayerTick());
			this.bridge.seek(this.toMusicSeconds(scoreSeconds));
			this.bridge.play();
			this.correctedAt = 0;
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

		double scoreSeconds = this.getScoreSeconds(this.getPlayerTick());
		// where the recording was when it was read, brought forward to now
		double musicNow = (best.position + ((System.currentTimeMillis() - best.readAt) / 1000.0));
		double expectedScoreSeconds = this.toScoreSeconds(musicNow);

		this.drift = Math.round((expectedScoreSeconds - scoreSeconds) * 1000);
		this.setStatus(String.format("score %.2fs, recording %.2fs, drift %+d ms (read within %d ms)",
			scoreSeconds, musicNow, Long.valueOf(this.drift), Long.valueOf(best.window)));

		boolean settled = ((System.currentTimeMillis() - this.correctedAt) > SETTLE_AFTER_CORRECTION);
		if( correctAnyway || (settled && Math.abs(this.drift) > CORRECTION_THRESHOLD) ) {
			this.setScoreSeconds(expectedScoreSeconds);
			this.correctedAt = System.currentTimeMillis();
			this.setStatus(String.format("moved the score to %.2fs", expectedScoreSeconds));
		}
	}

	private long getPlayerTick() {
		return MidiPlayer.getInstance(this.context).getTickPosition();
	}

	/** recording position of a moment of the score */
	private double toMusicSeconds(double scoreSeconds) {
		return ((this.settings.getOffset() / 1000.0) + (scoreSeconds * this.settings.getStretchFactor()));
	}

	private double toScoreSeconds(double musicSeconds) {
		return ((musicSeconds - (this.settings.getOffset() / 1000.0)) / this.settings.getStretchFactor());
	}

	/**
	 * Seconds of playback before a tick of the player, which plays a measure as often as the
	 * repeats of the score ask for it, each at its own tempo. The speed the player is set to
	 * counts as well: a recording cannot follow a score practised at half speed, and the drift
	 * then says so rather than hiding it.
	 */
	private double getScoreSeconds(long playerTick) {
		return this.getScoreSeconds(playerTick, (MidiPlayer.getInstance(this.context).getMode().getCurrentPercent() / 100.0));
	}

	private double getScoreSeconds(long playerTick, double percent) {
		TGSong song = TGDocumentManager.getInstance(this.context).getSong();
		MidiPlayer player = MidiPlayer.getInstance(this.context);
		MidiRepeatController controller = new MidiRepeatController(song, player.getLoopSHeader(), player.getLoopEHeader());
		long start = this.getStartPoint();
		long length = 0;
		double seconds = 0;

		while(!controller.finished()) {
			TGMeasureHeader header = song.getMeasureHeader(controller.getIndex());
			controller.process();

			if( controller.shouldPlay() ) {
				start += length;
				length = header.getLength();

				double secondsPerTick = this.getSecondsPerTick(header, percent);
				if( playerTick < (start + length) ) {
					return (seconds + (Math.max(0, playerTick - start) * secondsPerTick));
				}
				seconds += (length * secondsPerTick);
			}
		}
		return seconds;
	}

	/** the tick of the player a moment of the score belongs to */
	private long getPlayerTick(double scoreSeconds, double percent) {
		TGSong song = TGDocumentManager.getInstance(this.context).getSong();
		MidiPlayer player = MidiPlayer.getInstance(this.context);
		MidiRepeatController controller = new MidiRepeatController(song, player.getLoopSHeader(), player.getLoopEHeader());
		long start = this.getStartPoint();
		long length = 0;
		double seconds = 0;

		while(!controller.finished()) {
			TGMeasureHeader header = song.getMeasureHeader(controller.getIndex());
			controller.process();

			if( controller.shouldPlay() ) {
				start += length;
				length = header.getLength();

				double secondsPerTick = this.getSecondsPerTick(header, percent);
				double measureSeconds = (length * secondsPerTick);
				if( scoreSeconds < (seconds + measureSeconds) ) {
					return (start + Math.round(Math.max(0, scoreSeconds - seconds) / secondsPerTick));
				}
				seconds += measureSeconds;
			}
		}
		return (start + length);
	}

	/** the tick of the player a position of the score is first played at */
	private long getPlayerTick(long measureStart) {
		TGSong song = TGDocumentManager.getInstance(this.context).getSong();
		MidiPlayer player = MidiPlayer.getInstance(this.context);
		MidiRepeatController controller = new MidiRepeatController(song, player.getLoopSHeader(), player.getLoopEHeader());
		long start = this.getStartPoint();
		long length = 0;

		while(!controller.finished()) {
			TGMeasureHeader header = song.getMeasureHeader(controller.getIndex());
			controller.process();

			if( controller.shouldPlay() ) {
				start += length;
				length = header.getLength();

				if( measureStart >= header.getStart() && measureStart < (header.getStart() + length) ) {
					return (start + (measureStart - header.getStart()));
				}
			}
		}
		return start;
	}

	private double getSecondsPerTick(TGMeasureHeader header, double percent) {
		return (60.0 / (header.getTempo().getQuarterValue() * percent) / TGDuration.QUARTER_TIME);
	}

	private long getStartPoint() {
		MidiPlayer player = MidiPlayer.getInstance(this.context);
		player.updateLoop(false);

		return player.getLoopSPosition();
	}

	/** moves the score playback to a moment given in seconds */
	private void setScoreSeconds(double scoreSeconds) {
		double percent = (MidiPlayer.getInstance(this.context).getMode().getCurrentPercent() / 100.0);

		MidiPlayer.getInstance(this.context).setTickPosition(this.getPlayerTick(scoreSeconds, percent));
	}
}
