package app.tuxguitar.applemusic;

import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.view.component.tab.Caret;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UISpinner;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Settings of the synchronisation, and the drift it measures while the score plays: the two
 * numbers below are what the recording needs to line up with the score, and the drift tells
 * whether they do.
 */
public class AppleMusicDialog {

	private static final long REFRESH_INTERVAL = 1000;

	private final TGContext context;
	private final AppleMusicSync sync;

	private UIWindow dialog;
	private UILabel statusLabel;
	private UILabel trackLabel;
	private Thread refresh;

	public AppleMusicDialog(TGContext context, AppleMusicSync sync) {
		this.context = context;
		this.sync = sync;
	}

	public void show() {
		final AppleMusicSettings settings = this.sync.getSettings();
		final UIFactory factory = TGApplication.getInstance(this.context).getFactory();
		final UIWindow parent = TGWindow.getInstance(this.context).getWindow();
		final UITableLayout layout = new UITableLayout();

		this.dialog = factory.createWindow(parent, false, false);
		this.dialog.setLayout(layout);
		this.dialog.setText(TuxGuitar.getProperty("applemusic.sync"));
		this.dialog.setImage(TuxGuitar.getInstance().getIconManager().getAppIcon());

		final UICheckBox enabled = factory.createCheckBox(this.dialog);
		enabled.setText(TuxGuitar.getProperty("applemusic.enabled"));
		enabled.setSelected(settings.isEnabled());
		layout.set(enabled, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		final UICheckBox mute = factory.createCheckBox(this.dialog);
		mute.setText(TuxGuitar.getProperty("applemusic.mute-score"));
		mute.setSelected(settings.isMuteScore());
		layout.set(mute, 2, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		UILabel offsetLabel = factory.createLabel(this.dialog);
		offsetLabel.setText(TuxGuitar.getProperty("applemusic.offset") + ":");
		layout.set(offsetLabel, 3, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false);

		final UISpinner offset = factory.createSpinner(this.dialog);
		offset.setMinimum(-60000);
		offset.setMaximum(600000);
		offset.setIncrement(10);
		offset.setValue(settings.getOffset());
		layout.set(offset, 3, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		UILabel stretchLabel = factory.createLabel(this.dialog);
		stretchLabel.setText(TuxGuitar.getProperty("applemusic.stretch") + ":");
		layout.set(stretchLabel, 4, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false);

		final UISpinner stretch = factory.createSpinner(this.dialog);
		stretch.setMinimum(AppleMusicSettings.MINIMUM_STRETCH * 10);
		stretch.setMaximum(AppleMusicSettings.MAXIMUM_STRETCH * 10);
		stretch.setIncrement(1);
		stretch.setValue(settings.getStretch());
		layout.set(stretch, 4, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		this.trackLabel = factory.createLabel(this.dialog);
		this.trackLabel.setText(TuxGuitar.getProperty("applemusic.track") + ": ...");
		layout.set(this.trackLabel, 5, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 2, 520f, null, null);

		this.statusLabel = factory.createLabel(this.dialog);
		this.statusLabel.setText(" ");
		layout.set(this.statusLabel, 6, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 2, 520f, null, null);

		// two anchors line the score up with the recording: move the caret to a measure, scrub the
		// recording in Music to the moment it is heard, and press. The first sets where the score
		// begins in the recording, the second how fast the recording runs against it.
		UIButton anchorStart = factory.createButton(this.dialog);
		anchorStart.setText(TuxGuitar.getProperty("applemusic.anchor-start"));
		anchorStart.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				double music = AppleMusicDialog.this.sync.getMusicPosition();
				double score = AppleMusicDialog.this.caretSeconds();
				if( music >= 0 ) {
					offset.setValue((int) Math.round((music - (score * (stretch.getValue() / 1000.0))) * 1000));
					settings.setOffset(offset.getValue());
				}
			}
		});
		layout.set(anchorStart, 7, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 220f, 25f, null);

		UIButton anchorSpeed = factory.createButton(this.dialog);
		anchorSpeed.setText(TuxGuitar.getProperty("applemusic.anchor-speed"));
		anchorSpeed.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				double music = AppleMusicDialog.this.sync.getMusicPosition();
				double score = AppleMusicDialog.this.caretSeconds();
				if( music >= 0 && score > 1 ) {
					double factor = ((music - (offset.getValue() / 1000.0)) / score);
					stretch.setValue((int) Math.round(factor * 1000));
					settings.setStretch(stretch.getValue());
				}
			}
		});
		layout.set(anchorSpeed, 7, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 220f, 25f, null);

		// the lengths of both tell how fast the recording runs compared to the score, which is
		// where dialling in the speed starts
		UIButton match = factory.createButton(this.dialog);
		match.setText(TuxGuitar.getProperty("applemusic.match-length"));
		match.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				double score = AppleMusicDialog.this.sync.getScoreDuration();
				double track = AppleMusicDialog.this.sync.getTrackDuration();
				if( score > 0 && track > 0 ) {
					double factor = ((track - (offset.getValue() / 1000.0)) / score);
					stretch.setValue((int) Math.round(factor * 1000));
					settings.setStretch(stretch.getValue());
				}
			}
		});
		layout.set(match, 8, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 220f, 25f, null);

		UIButton close = factory.createButton(this.dialog);
		close.setText(TuxGuitar.getProperty("close"));
		close.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				settings.setEnabled(enabled.isSelected());
				settings.setMuteScore(mute.isSelected());
				settings.setOffset(offset.getValue());
				settings.setStretch(stretch.getValue());
				AppleMusicDialog.this.dialog.dispose();
			}
		});
		layout.set(close, 8, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 80f, 25f, null);

		// the settings take effect while the dialog is open, so that the offset can be dialled in
		// against the drift shown below while the score plays
		UISelectionListener apply = new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				settings.setEnabled(enabled.isSelected());
				settings.setMuteScore(mute.isSelected());
				settings.setOffset(offset.getValue());
				settings.setStretch(stretch.getValue());
			}
		};
		enabled.addSelectionListener(apply);
		mute.addSelectionListener(apply);
		offset.addSelectionListener(apply);
		stretch.addSelectionListener(apply);

		this.startRefresh();

		TGDialogUtil.openDialog(this.dialog, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}

	/** seconds of score before the measure the caret sits in */
	private double caretSeconds() {
		Caret caret = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret();

		return this.sync.getCaretSeconds(caret.getMeasure().getHeader().getStart());
	}

	private static String format(double seconds) {
		return String.format("%d:%02d", Long.valueOf((long) (seconds / 60)), Long.valueOf(Math.round(seconds) % 60));
	}

	private void startRefresh() {
		this.refresh = new Thread(new Runnable() {
			public void run() {
				AppleMusicDialog.this.refreshStatus();
			}
		}, "tuxguitar-apple-music-dialog");
		this.refresh.setDaemon(true);
		this.refresh.start();
	}

	private void refreshStatus() {
		try {
			while( this.dialog != null && !this.dialog.isDisposed() ) {
				final String track = this.sync.getTrackName();
				final String lengths = String.format(" (%s / %s)",
					format(this.sync.getScoreDuration()), format(this.sync.getTrackDuration()));
				final String status = this.sync.getStatus();
				TGSynchronizer.getInstance(this.context).executeLater(new Runnable() {
					public void run() {
						if( AppleMusicDialog.this.dialog != null && !AppleMusicDialog.this.dialog.isDisposed() ) {
							AppleMusicDialog.this.trackLabel.setText(TuxGuitar.getProperty("applemusic.track")
								+ ": " + (track != null ? track + lengths : "-"));
							AppleMusicDialog.this.statusLabel.setText(status != null && status.length() > 0 ? status : " ");
						}
					}
				});
				Thread.sleep(REFRESH_INTERVAL);
			}
		} catch (InterruptedException interruption) {
			Thread.currentThread().interrupt();
		}
	}
}
