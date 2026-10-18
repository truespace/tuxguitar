package app.tuxguitar.cocoa.opendoc;

/*
 * Plugin to open documents on macOS when TuxGuitar is launched by macOS Finder
 * specific to macOS/SWT version of TuxGuitar
 *
 * On macOS, when TuxGuitar is launched by double-clicking a file, no argument is passed to the launch script.
 * Need to register a listener to SWT OpenDocument event
 * This plugin must be initialized early (just after Display creation), and from UI thread
 *
 * Known limitation:
 * no openDoc event is ever sent by SWT to an instance of TuxGuitar launched manually from command line.
 * So, if TuxGuitar is launched from command line, double-clicking a file will NOT open the file.
 *
 */

import app.tuxguitar.cocoa.TGCocoaIntegrationPlugin;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGEarlyInitPlugin;
import app.tuxguitar.util.plugin.TGPluginException;

public class OpenDocPlugin implements TGEarlyInitPlugin {

	private OpenDocListener openDocListener;
	// kept referenced: the delegate calls back into it
	private OpenUrlHandler openUrlHandler;

	@Override
	public String getModuleId() {
		return TGCocoaIntegrationPlugin.MODULE_ID;
	}

	@Override
	public void earlyInit(TGContext context) throws TGPluginException {
		this.openDocListener = new OpenDocListener(context);
		try {
			// "tuxguitar:" links, which open a file from a web page or any other application
			this.openUrlHandler = new OpenUrlHandler(this.openDocListener);
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
	}

	@Override
	public void connect(TGContext context) throws TGPluginException {
		// nothing to do here, initialization is done in .earlyInit()
	}

	@Override
	public void disconnect(TGContext context) throws TGPluginException {
		this.openDocListener.disconnect();
	}

}
