package app.tuxguitar.gluon;

import java.net.URL;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.action.impl.file.TGReadURLAction;
import app.tuxguitar.app.util.TGSchemeUrl;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.ui.jfx.util.JFXPlatformUtil;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Opens the file of a "tuxguitar:" link on iOS, where such a link reaches the application
 * through the delegate hook of src/native/tgurl.m. Links are picked up from there, and
 * processed once TuxGuitar is ready to open a song: one may arrive before that, having
 * started the application.
 */
public class TGGluonUrlScheme {

	private static final int BUFFER_SIZE = 4096;
	private static final long POLL_INTERVAL = 500;
	private static final String ENCODING = "UTF-8";

	public static void install() {
		if(!ImageInfo.inImageRuntimeCode()) {
			return;
		}

		Thread thread = new Thread(new Runnable() {
			public void run() {
				TGGluonUrlScheme.poll();
			}
		}, "tuxguitar-url-scheme");
		thread.setDaemon(true);
		thread.start();
	}

	private static void poll() {
		byte[] buffer = new byte[BUFFER_SIZE];
		while(true) {
			try {
				Thread.sleep(POLL_INTERVAL);

				if( TuxGuitar.getInstance().isInitialized() ) {
					String text = take(buffer);
					if( text != null ) {
						open(text);
					}
				}
			} catch (InterruptedException interruption) {
				return;
			} catch (Throwable throwable) {
				TGGluonLog.log("url scheme: " + throwable);
			}
		}
	}

	private static String take(byte[] buffer) throws Exception {
		int length = 0;
		try (PinnedObject pinned = PinnedObject.create(buffer)) {
			length = NativeUrl.take(pinned.addressOfArrayElement(0), buffer.length);
		}
		return (length > 0 ? new String(buffer, 0, length, ENCODING) : null);
	}

	private static void open(String text) throws Exception {
		final TGContext context = TuxGuitar.getInstance().getContext();
		final URL url = TGSchemeUrl.toFileUrl(text, JFXPlatformUtil.getIOSDocumentsDirectory());

		TGGluonLog.log("url scheme: " + text + " -> " + url);

		if( url != null ) {
			TGSynchronizer.getInstance(context).executeLater(new Runnable() {
				public void run() {
					TGActionProcessor tgActionProcessor = new TGActionProcessor(context, TGReadURLAction.NAME);
					tgActionProcessor.setAttribute(TGReadURLAction.ATTRIBUTE_URL, url);
					tgActionProcessor.process();
				}
			});
		}
	}

	private static class NativeUrl {

		@CFunction("tg_url_take")
		static native int take(CCharPointer buffer, int size);
	}
}
