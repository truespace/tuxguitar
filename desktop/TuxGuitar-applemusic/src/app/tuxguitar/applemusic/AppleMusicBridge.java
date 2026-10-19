package app.tuxguitar.applemusic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Talks to the helper of src/native/tgmusic.swift, which drives the Music app.
 *
 * One command is sent per call and answered on the next line, so every call is a measurement as
 * well: a reading is only known to be true somewhere between the moment it was sent and the
 * moment the answer arrived, and that window is what limits how well the two can be aligned.
 */
public class AppleMusicBridge {

	public static final String HELPER_NAME = "tuxguitar-music-helper";
	private static final String ENCODING = "UTF-8";
	// the very first command waits for the user to allow TuxGuitar to control the Music app;
	// afterwards an answer that does not come quickly means the helper is stuck, and holding the
	// bridge any longer would hold up whoever closes it
	private static final long FIRST_TIMEOUT = 60000;
	private static final long TIMEOUT = 5000;

	private Process process;
	private BufferedWriter output;
	private BufferedReader input;
	private boolean answered;
	private volatile boolean closed;

	/** position of the Music app, with the window in which it was read */
	public static class Reading {

		public final double position;
		public final long readAt;
		public final long window;

		Reading(double position, long sentAt, long receivedAt) {
			this.position = position;
			// attribute the reading to the middle of its own window
			this.readAt = ((sentAt + receivedAt) / 2);
			this.window = (receivedAt - sentAt);
		}
	}

	public synchronized void open(File helper) throws IOException {
		if( this.process != null && this.process.isAlive() ) {
			return;
		}
		if(!helper.isFile() ) {
			throw new IOException("the helper is missing: " + helper.getAbsolutePath());
		}

		ProcessBuilder builder = new ProcessBuilder(helper.getAbsolutePath());
		builder.redirectErrorStream(false);

		this.closed = false;
		this.answered = false;
		this.process = builder.start();
		this.output = new BufferedWriter(new OutputStreamWriter(this.process.getOutputStream(), ENCODING));
		this.input = new BufferedReader(new InputStreamReader(this.process.getInputStream(), ENCODING));
	}

	/**
	 * Ends the helper without waiting for anything: this runs while the application is closing,
	 * where another thread may still be waiting for an answer, and must not queue behind it.
	 */
	public void close() {
		this.closed = true;

		Process process = this.process;
		if( process != null ) {
			try {
				// best effort, the helper ends on this; destroy() settles it either way
				this.output.write("quit\n");
				this.output.flush();
			} catch (Throwable ignored) {
				// the helper is going away in any case
			}
			process.destroy();
			this.process = null;
		}
	}

	private void destroy() {
		Process process = this.process;
		if( process != null ) {
			process.destroy();
			this.process = null;
			this.input = null;
			this.output = null;
		}
	}

	public synchronized boolean isOpen() {
		return (this.process != null && this.process.isAlive());
	}

	public synchronized Reading getPosition() throws IOException {
		long sentAt = System.currentTimeMillis();
		String answer = this.send("pos");
		long receivedAt = System.currentTimeMillis();

		if(!answer.startsWith("pos ") ) {
			throw new IOException(answer);
		}
		return new Reading(Double.parseDouble(answer.substring(4).trim()), sentAt, receivedAt);
	}

	public synchronized String getState() throws IOException {
		String answer = this.send("state");
		if(!answer.startsWith("state ") ) {
			throw new IOException(answer);
		}
		return answer.substring(6).trim();
	}

	/** length of the track loaded in the Music app, in seconds, or 0 when there is none */
	public synchronized double getTrackDuration() throws IOException {
		String answer = this.send("track");
		int separator = answer.indexOf('|');
		if(!answer.startsWith("track ") ) {
			throw new IOException(answer);
		}
		return (separator > 6 ? Double.parseDouble(answer.substring(6, separator).trim()) : 0);
	}

	/** name of the track loaded in the Music app, or null when there is none */
	public synchronized String getTrackName() throws IOException {
		String answer = this.send("track");
		int separator = answer.indexOf('|');
		if(!answer.startsWith("track ") ) {
			throw new IOException(answer);
		}
		return (separator > 0 ? answer.substring(separator + 1) : null);
	}

	/** loads the track of the library whose name contains this, and pauses it at its start */
	public synchronized void selectTrack(String name) throws IOException {
		this.check(this.send("select " + name.replace('\n', ' ')));
	}

	public synchronized void seek(double seconds) throws IOException {
		this.check(this.send("seek " + Math.max(0, seconds)));
	}

	public synchronized void play() throws IOException {
		this.check(this.send("play"));
	}

	public synchronized void pause() throws IOException {
		this.check(this.send("pause"));
	}

	private void check(String answer) throws IOException {
		if(!"ok".equals(answer) ) {
			throw new IOException(answer);
		}
	}

	private String send(String command) throws IOException {
		if( this.closed || !this.isOpen() ) {
			throw new IOException("the helper is not running");
		}

		this.output.write(command);
		this.output.write("\n");
		this.output.flush();

		long deadline = (System.currentTimeMillis() + (this.answered ? TIMEOUT : FIRST_TIMEOUT));
		while(!this.input.ready() ) {
			if( this.closed ) {
				throw new IOException("the helper was closed");
			}
			if( System.currentTimeMillis() > deadline ) {
				// the answer would arrive after this call and offset every following one
				this.destroy();

				throw new IOException("the helper did not answer " + command);
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException interruption) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted");
			}
		}
		String answer = this.input.readLine();
		if( answer == null ) {
			throw new IOException("the helper closed its output");
		}
		this.answered = true;

		return answer;
	}
}
