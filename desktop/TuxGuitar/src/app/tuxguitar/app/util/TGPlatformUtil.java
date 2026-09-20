package app.tuxguitar.app.util;

public class TGPlatformUtil {

	private TGPlatformUtil() {
	}

	/**
	 * true when running on iOS, where the JavaFX toolkit renders images through a texture
	 * the size of the image times the screen scale: the score is then blurry, and fails to
	 * render at all when it grows past the maximum texture size of the device.
	 */
	public static boolean isIOS() {
		return "ios".equals(System.getProperty("javafx.platform"));
	}
}
