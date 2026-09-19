package app.tuxguitar.ui.jfx.util;

import java.io.File;

public class JFXPlatformUtil {

	private JFXPlatformUtil() {
	}

	/**
	 * true when running on iOS (Gluon native image): "javafx.platform" is set by the Gluon launcher.
	 */
	public static boolean isIOS() {
		return "ios".equals(System.getProperty("javafx.platform"));
	}

	/**
	 * iOS app Documents folder (shown in the Files app when UIFileSharingEnabled is set).
	 * The Gluon launcher sets user.home to <app container>/Library/gluon.
	 */
	public static File getIOSDocumentsDirectory() {
		File container = new File(System.getProperty("user.home")).getAbsoluteFile().getParentFile().getParentFile();
		return new File(container, "Documents");
	}
}
