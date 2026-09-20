package app.tuxguitar.gluon;

import java.io.File;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

import app.tuxguitar.app.TGMainSingleton;
import javafx.application.Platform;

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
	private static final String TG_SNAPSHOT = "tuxguitar.gluon.snapshot";
	private static final String TG_EVENT_LOG = "tuxguitar.gluon.eventlog";

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
		if (isIOS()) {
			TGGluonLog.copyConsoleToFile();

			// iOS apps never quit by themselves: closing the splash (or any other) stage must not end the FX runtime
			Platform.setImplicitExit(false);

			// only one instance can run on iOS: a lock left by a killed process (TGMainSingleton) would
			// make this one hand its URL over to that "running instance" and exit
			File lockFile = new File(System.getProperty("java.io.tmpdir"), "tuxguitar-" + System.getProperty("user.name") + File.separator + "tuxguitar.lock");
			if (lockFile.exists() && !lockFile.delete()) {
				System.err.println("[TGGluonLauncher] could not delete " + lockFile);
			}
		}

		TGGluonLog.log("home=" + System.getProperty(TG_HOME_PATH) + " share=" + System.getProperty(TG_SHARE_PATH));

		String snapshotDelay = System.getProperty(TG_SNAPSHOT);
		if (snapshotDelay != null && !snapshotDelay.isEmpty()) {
			TGGluonSnapshot.schedule(Integer.parseInt(snapshotDelay));
		}
		if (Boolean.getBoolean(TG_EVENT_LOG)) {
			TGGluonEventLog.install();
		}
		TGMainSingleton.main(args);
	}

	/**
	 * Folder containing share/: next to the executable (inside the .app bundle on iOS),
	 * or the working directory when running on the JVM.
	 */
	private static File findHomeDir() {
		File executableDir = getExecutableDir();
		if (executableDir != null && new File(executableDir, "share").isDirectory()) {
			return executableDir;
		}
		File workingDir = new File(System.getProperty("user.dir"));
		if (new File(workingDir, "share").isDirectory()) {
			return workingDir;
		}
		return null;
	}

	private static boolean isIOS() {
		// set by the Gluon iOS launcher
		return "ios".equals(System.getProperty("javafx.platform"));
	}

	private static File getExecutableDir() {
		String executable = null;
		try {
			// _NSGetExecutablePath on darwin, also inside the iOS sandbox
			if (ImageInfo.inImageRuntimeCode()) {
				executable = ProcessProperties.getExecutableName();
			}
		} catch (Throwable throwable) {
			// not running on GraalVM
		}
		if (executable == null) {
			executable = ProcessHandle.current().info().command().orElse(null);
		}
		return (executable != null ? new File(executable).getAbsoluteFile().getParentFile() : null);
	}
}
