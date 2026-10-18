package app.tuxguitar.app.transport;

import app.tuxguitar.app.system.config.TGConfigKeys;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.app.view.component.tab.TGControl;
import app.tuxguitar.app.view.component.tabfolder.TGTabFolder;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.song.models.TGVelocities;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * Countdown shown over the score before playback starts ("3, 2, 1"), one step per second.
 * Unlike the count in of the player, it does not depend on the tempo of the song.
 */
public class TGStartCountdown {

	public static final int DEFAULT_SECONDS = 3;
	public static final int[] SECONDS_CHOICES = {1, 2, 3, 4, 5, 10};

	private static final int MINIMUM_SECONDS = 1;
	private static final int MAXIMUM_SECONDS = 60;
	private static final int CLICK_KEY = 37;
	private static final long STEP_LENGTH = 1000;

	private TGContext context;
	private volatile boolean running;
	private volatile int remaining;
	private Thread thread;

	private TGStartCountdown(TGContext context) {
		this.context = context;
	}

	public boolean isEnabled() {
		return TGConfigManager.getInstance(this.context).getBooleanValue(TGConfigKeys.TRANSPORT_START_COUNTDOWN_ENABLED);
	}

	public void setEnabled(boolean enabled) {
		TGConfigManager.getInstance(this.context).setValue(TGConfigKeys.TRANSPORT_START_COUNTDOWN_ENABLED, enabled);
	}

	public int getSeconds() {
		int seconds = TGConfigManager.getInstance(this.context).getIntegerValue(TGConfigKeys.TRANSPORT_START_COUNTDOWN_SECONDS, DEFAULT_SECONDS);

		return Math.min(MAXIMUM_SECONDS, Math.max(MINIMUM_SECONDS, seconds));
	}

	public void setSeconds(int seconds) {
		TGConfigManager.getInstance(this.context).setValue(TGConfigKeys.TRANSPORT_START_COUNTDOWN_SECONDS, seconds);
	}

	public boolean isRunning() {
		return this.running;
	}

	/**
	 * Seconds left, shown over the score while running.
	 */
	public int getRemaining() {
		return this.remaining;
	}

	/**
	 * Counts down, then runs onFinish in the UI thread, unless cancelled in the meantime.
	 */
	public synchronized void start(final Runnable onFinish) {
		this.cancel();

		this.running = true;
		this.remaining = this.getSeconds();
		this.thread = new Thread(new Runnable() {
			public void run() {
				TGStartCountdown.this.count(onFinish);
			}
		}, "tuxguitar-start-countdown");
		this.thread.setDaemon(true);
		this.thread.start();
	}

	public synchronized void cancel() {
		if( this.running ) {
			this.running = false;
			if( this.thread != null ) {
				this.thread.interrupt();
			}
			this.redraw();
		}
		this.thread = null;
	}

	private void count(Runnable onFinish) {
		try {
			while( this.running && this.remaining > 0 ) {
				this.redraw();
				this.click();
				Thread.sleep(STEP_LENGTH);
				this.remaining --;
			}
		} catch (InterruptedException e) {
			return;
		}
		if( this.running ) {
			this.running = false;
			this.redraw();
			TGSynchronizer.getInstance(this.context).executeLater(onFinish);
		}
	}

	private void click() {
		try {
			MidiPlayer player = MidiPlayer.getInstance(this.context);
			int channelId = player.getPercussionChannelId();
			if( channelId >= 0 && player.getOutputTransmitter() != null ) {
				player.getOutputTransmitter().sendNoteOn(channelId, CLICK_KEY, TGVelocities.DEFAULT, -1, false);
				player.getOutputTransmitter().sendNoteOff(channelId, CLICK_KEY, TGVelocities.DEFAULT, -1, false);
			}
		} catch (Throwable throwable) {
			// the countdown still shows without a sound
		}
	}

	private void redraw() {
		TGSynchronizer.getInstance(this.context).executeLater(new Runnable() {
			public void run() {
				TGControl control = TGTabFolder.getInstance(TGStartCountdown.this.context).findSelectedControl();
				if( control != null ) {
					control.redraw();
				}
			}
		});
	}

	public static TGStartCountdown getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGStartCountdown.class.getName(), new TGSingletonFactory<TGStartCountdown>() {
			public TGStartCountdown createInstance(TGContext context) {
				return new TGStartCountdown(context);
			}
		});
	}
}
