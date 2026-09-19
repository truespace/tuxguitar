package app.tuxguitar.ui.jfx.util;

public class JFXPlatformUtil {

	private JFXPlatformUtil() {
	}

	/**
	 * true when running on iOS (Gluon native image): "javafx.platform" is set by the Gluon launcher.
	 */
	public static boolean isIOS() {
		return "ios".equals(System.getProperty("javafx.platform"));
	}
}
