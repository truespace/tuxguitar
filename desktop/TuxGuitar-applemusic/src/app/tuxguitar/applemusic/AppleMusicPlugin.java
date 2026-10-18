package app.tuxguitar.applemusic;

import app.tuxguitar.app.tools.custom.TGToolItemPlugin;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Plays the recording loaded in the Music app of macOS along with the score.
 */
public class AppleMusicPlugin extends TGToolItemPlugin {

	public static final String MODULE_ID = "tuxguitar-applemusic";

	private AppleMusicSync sync;

	public String getModuleId() {
		return MODULE_ID;
	}

	protected String getItemName() {
		return "applemusic.sync";
	}

	public void connect(TGContext context) throws TGPluginException {
		super.connect(context);

		if( this.sync == null ) {
			try {
				this.sync = new AppleMusicSync(context, AppleMusicHelperFile.find());
				this.sync.connect();
			} catch (Throwable throwable) {
				throw new TGPluginException(throwable.getMessage(), throwable);
			}
		}
	}

	public void disconnect(TGContext context) throws TGPluginException {
		super.disconnect(context);

		if( this.sync != null ) {
			this.sync.disconnect();
			this.sync = null;
		}
	}

	protected void doAction(TGContext context) {
		if( this.sync != null ) {
			new AppleMusicDialog(context, this.sync).show();
		}
	}
}
