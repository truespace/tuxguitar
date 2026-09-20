package app.tuxguitar.gluon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Debug log of the Gluon builds. On iOS the console is only visible when the app is started
 * through devicectl, so stdout/stderr are also copied to user.home/tuxguitar-gluon.log
 * (Library/gluon in the app container).
 */
public class TGGluonLog {

	private static final String FILE_NAME = "tuxguitar-gluon.log";

	public static void log(String message) {
		System.err.println("[TuxGuitar-gluon] " + System.currentTimeMillis() + " " + message);
	}

	public static void copyConsoleToFile() {
		try {
			OutputStream file = new FileOutputStream(new File(System.getProperty("user.home"), FILE_NAME), true);
			System.setOut(new PrintStream(new TeeOutputStream(System.out, file), true));
			System.setErr(new PrintStream(new TeeOutputStream(System.err, file), true));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class TeeOutputStream extends OutputStream {

		private final OutputStream console;
		private final OutputStream file;

		TeeOutputStream(OutputStream console, OutputStream file) {
			this.console = console;
			this.file = file;
		}

		public void write(int b) throws IOException {
			this.console.write(b);
			this.file.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			this.console.write(b, off, len);
			this.file.write(b, off, len);
		}

		public void flush() throws IOException {
			this.console.flush();
			this.file.flush();
		}
	}
}
