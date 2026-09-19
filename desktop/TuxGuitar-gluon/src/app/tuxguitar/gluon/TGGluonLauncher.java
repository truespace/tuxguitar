package app.tuxguitar.gluon;

import java.io.File;

import app.tuxguitar.app.TGMainSingleton;

/**
 * Entry point for the GraalVM native-image (Gluon) builds.
 *
 * Native executables are not started through tuxguitar.sh, so the home and
 * share paths that the script normally passes as system properties are
 * derived here from the location of the executable.
 */
public class TGGluonLauncher {

	private static final String TG_HOME_PATH = "tuxguitar.home.path";
	private static final String TG_SHARE_PATH = "tuxguitar.share.path";

	public static void main(String[] args) {
		File homeDir = findHomeDir();
		if (homeDir != null) {
			if (System.getProperty(TG_HOME_PATH) == null) {
				System.setProperty(TG_HOME_PATH, homeDir.getAbsolutePath());
			}
			if (System.getProperty(TG_SHARE_PATH) == null) {
				System.setProperty(TG_SHARE_PATH, new File(homeDir, "share").getAbsolutePath());
			}
		}
		TGMainSingleton.main(args);
	}

	private static File findHomeDir() {
		String command = ProcessHandle.current().info().command().orElse(null);
		if (command != null) {
			File parent = new File(command).getAbsoluteFile().getParentFile();
			if (parent != null && new File(parent, "share").isDirectory()) {
				return parent;
			}
		}
		File workingDir = new File(System.getProperty("user.dir"));
		if (new File(workingDir, "share").isDirectory()) {
			return workingDir;
		}
		return null;
	}
}
