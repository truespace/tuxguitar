package app.tuxguitar.gluon;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Debug helper for devices without screenshot tooling (e.g. iOS through devicectl):
 * after a delay, logs every JavaFX window and writes a snapshot of each visible scene
 * as a BMP file in user.home (no AWT/ImageIO needed). Messages also go to user.home/tuxguitar-gluon.log.
 *
 * Enabled with -Dtuxguitar.gluon.snapshot=<delay in seconds>.
 */
public class TGGluonSnapshot {

	private static final String PREFIX = "[TGGluonSnapshot] ";

	public static void schedule(final int delaySeconds) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					// heartbeat: tells a suspended process (no more lines) from a blocked FX thread
					for (int elapsed = 0; elapsed < delaySeconds; elapsed += 5) {
						log("heartbeat " + elapsed + "s");
						Thread.sleep(Math.min(5, delaySeconds - elapsed) * 1000L);
					}
					// dump first: Platform.runLater below may never return when the FX thread is stuck
					log("thread dump before snapshot:");
					dumpThreads();
					final CountDownLatch done = new CountDownLatch(1);
					Platform.runLater(new Runnable() {
						public void run() {
							capture();
							done.countDown();
						}
					});
					if (!done.await(5, TimeUnit.SECONDS)) {
						log("FX application thread did not respond, thread dump:");
						dumpThreads();
					}
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "tuxguitar-snapshot");
		thread.setDaemon(true);
		thread.start();
	}

	private static synchronized void log(String message) {
		String line = PREFIX + System.currentTimeMillis() + " " + message;
		System.err.println(line);
		try (PrintWriter writer = new PrintWriter(new FileWriter(new File(System.getProperty("user.home"), "tuxguitar-gluon.log"), true))) {
			writer.println(line);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void dumpThreads() {
		for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
			Thread thread = entry.getKey();
			log("thread \"" + thread.getName() + "\" state=" + thread.getState());
			for (StackTraceElement element : entry.getValue()) {
				log("    at " + element);
			}
		}
	}

	private static void capture() {
		File folder = new File(System.getProperty("user.home"));
		int index = 0;
		for (Window window : Window.getWindows()) {
			String title = (window instanceof Stage ? ((Stage) window).getTitle() : null);
			log("window " + index + ": " + window.getClass().getSimpleName()
					+ " showing=" + window.isShowing()
					+ " bounds=" + window.getX() + "," + window.getY() + " " + window.getWidth() + "x" + window.getHeight()
					+ " title=" + title);
			if (window.isShowing() && window.getScene() != null) {
				File file = new File(folder, "snapshot-" + index + ".bmp");
				try {
					writeBmp(window.getScene().snapshot(null), file);
					log("wrote " + file.getAbsolutePath());
				} catch (Throwable throwable) {
					throwable.printStackTrace();
				}
			}
			index ++;
		}
	}

	private static void writeBmp(WritableImage image, File file) throws IOException {
		int width = (int) image.getWidth();
		int height = (int) image.getHeight();
		int rowSize = (width * 3 + 3) & ~3;
		int dataSize = rowSize * height;
		PixelReader reader = image.getPixelReader();

		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
			// BITMAPFILEHEADER + BITMAPINFOHEADER, 24 bits per pixel, bottom-up rows
			out.write(new byte[] {'B', 'M'});
			writeInt(out, 54 + dataSize);
			writeInt(out, 0);
			writeInt(out, 54);
			writeInt(out, 40);
			writeInt(out, width);
			writeInt(out, height);
			writeShort(out, 1);
			writeShort(out, 24);
			writeInt(out, 0);
			writeInt(out, dataSize);
			writeInt(out, 2835);
			writeInt(out, 2835);
			writeInt(out, 0);
			writeInt(out, 0);

			byte[] row = new byte[rowSize];
			for (int y = height - 1; y >= 0; y --) {
				for (int x = 0; x < width; x ++) {
					int argb = reader.getArgb(x, y);
					row[x * 3] = (byte) argb;
					row[x * 3 + 1] = (byte) (argb >> 8);
					row[x * 3 + 2] = (byte) (argb >> 16);
				}
				out.write(row);
			}
		}
	}

	private static void writeInt(OutputStream out, int value) throws IOException {
		out.write(value);
		out.write(value >> 8);
		out.write(value >> 16);
		out.write(value >> 24);
	}

	private static void writeShort(OutputStream out, int value) throws IOException {
		out.write(value);
		out.write(value >> 8);
	}
}
