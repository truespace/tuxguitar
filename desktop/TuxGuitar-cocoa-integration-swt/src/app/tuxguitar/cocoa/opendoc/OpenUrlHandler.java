package app.tuxguitar.cocoa.opendoc;

import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.cocoa.NSApplication;
import org.eclipse.swt.internal.cocoa.NSString;
import org.eclipse.swt.internal.cocoa.OS;

import app.tuxguitar.cocoa.TGCocoa;

/*
 * Receives the "tuxguitar:" links macOS sends to the application, e.g. from a web page.
 *
 * SWT.OpenUrl cannot be used for them: SWT adds "application:openUrls:" to its delegate, while
 * AppKit calls "application:openURLs:", so the event is never fired. Adding the method AppKit
 * looks for is not enough either, as the application delegate is only asked about it while the
 * application finishes launching, long before the plugins are loaded. What is left is the Apple
 * event behind it, whose handler can be installed at any time.
 */
public class OpenUrlHandler {

	private static final String DELEGATE_CLASS = "SWTApplicationDelegate";
	private static final String HANDLER_SELECTOR = "tgHandleGetUrlEvent:withReplyEvent:";

	// 'GURL', both the class and the id of the apple event, and '----', its direct parameter
	private static final int GET_URL_EVENT = 0x4755524C;
	private static final int KEY_DIRECT_OBJECT = 0x2D2D2D2D;

	private final OpenDocListener listener;

	// the callback must stay referenced for as long as the handler may be called
	private Callback callback;

	public OpenUrlHandler(OpenDocListener listener) throws Throwable {
		this.listener = listener;

		long delegateClass = TGCocoa.objc_lookUpClass(DELEGATE_CLASS);
		long delegate = OS.objc_msgSend(NSApplication.sharedApplication().id, TGCocoa.sel_registerName("delegate"));
		if( delegateClass == 0 || delegate == 0 ) {
			return;
		}

		this.callback = TGCocoa.newCallback(this, "callbackProc64", "callbackProc32", 4);

		long callbackProc = TGCocoa.getCallbackAddress(this.callback);
		if( callbackProc != 0 ) {
			long selector = TGCocoa.sel_registerName(HANDLER_SELECTOR);
			TGCocoa.class_addMethod(delegateClass, selector, callbackProc, "@:@@");

			long manager = OS.objc_msgSend(TGCocoa.objc_lookUpClass("NSAppleEventManager"),
				TGCocoa.sel_registerName("sharedAppleEventManager"));
			OS.objc_msgSend(manager, TGCocoa.sel_registerName("setEventHandler:andSelector:forEventClass:andEventID:"),
				delegate, selector, GET_URL_EVENT, GET_URL_EVENT);
		}
	}

	public long callbackProc64(long id, long sel, long event, long reply) {
		return this.callbackProc(event);
	}

	public int callbackProc32(int id, int sel, int event, int reply) {
		return (int) this.callbackProc(event);
	}

	private long callbackProc(long event) {
		try {
			long parameter = OS.objc_msgSend(event, TGCocoa.sel_registerName("paramDescriptorForKeyword:"), KEY_DIRECT_OBJECT);
			long text = (parameter != 0 ? OS.objc_msgSend(parameter, TGCocoa.sel_registerName("stringValue")) : 0);
			if( text != 0 ) {
				this.listener.handleUrl(new NSString(text).getString());
			}
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
		return 0;
	}
}
