package app.tuxguitar.gluon;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.stage.Window;

/**
 * Debug helper: logs windows (including popups) as they are shown and hidden, and the
 * pointer/touch/action events they receive, with their target.
 * Messages go to stderr and user.home/tuxguitar-gluon.log (see TGGluonSnapshot).
 *
 * Enabled with -Dtuxguitar.gluon.eventlog=true.
 */
public class TGGluonEventLog {

	public static void install() {
		// called before the FX toolkit is started: retry until Platform.runLater is accepted
		Thread thread = new Thread(new Runnable() {
			public void run() {
				while (true) {
					try {
						installLater();
						return;
					} catch (IllegalStateException toolkitNotInitialized) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			}
		}, "tuxguitar-eventlog");
		thread.setDaemon(true);
		thread.start();
	}

	private static void installLater() {
		Platform.runLater(new Runnable() {
			public void run() {
				for (Window window : Window.getWindows()) {
					watch(window);
				}
				Window.getWindows().addListener(new ListChangeListener<Window>() {
					public void onChanged(Change<? extends Window> change) {
						while (change.next()) {
							for (Window window : change.getAddedSubList()) {
								TGGluonSnapshot.log("window added: " + describe(window));
								watch(window);
							}
							for (Window window : change.getRemoved()) {
								TGGluonSnapshot.log("window removed: " + describe(window));
							}
						}
					}
				});
			}
		});
	}

	private static void watch(final Window window) {
		window.addEventFilter(Event.ANY, new EventHandler<Event>() {
			public void handle(Event event) {
				EventType<? extends Event> type = event.getEventType();
				if (type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_RELEASED || type == MouseEvent.MOUSE_CLICKED
						|| type == TouchEvent.TOUCH_PRESSED || type == TouchEvent.TOUCH_RELEASED
						|| type == ActionEvent.ACTION) {
					String position = "";
					if (event instanceof MouseEvent) {
						MouseEvent mouseEvent = (MouseEvent) event;
						position = " at " + (int) mouseEvent.getSceneX() + "," + (int) mouseEvent.getSceneY()
								+ " synthesized=" + mouseEvent.isSynthesized();
					}
					TGGluonSnapshot.log(type + " on " + describe(window) + " target=" + event.getTarget() + position);
				}
			}
		});
	}

	private static String describe(Window window) {
		return window.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(window))
				+ " showing=" + window.isShowing()
				+ " bounds=" + (int) window.getX() + "," + (int) window.getY() + " " + (int) window.getWidth() + "x" + (int) window.getHeight();
	}
}
