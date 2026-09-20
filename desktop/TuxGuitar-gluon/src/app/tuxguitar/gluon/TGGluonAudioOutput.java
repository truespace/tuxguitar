package app.tuxguitar.gluon;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;

import app.tuxguitar.midi.synth.TGAudioBuffer;
import app.tuxguitar.midi.synth.TGAudioOutput;

/**
 * Synthesizer audio output for the native image builds (iOS has no javax.sound line):
 * PCM data is handed to the AudioQueue based player of src/native/tgaudio.m.
 * On the JVM (e.g. gluonfx:runagent) open() fails and TGAudioLine uses javax.sound.
 */
public class TGGluonAudioOutput implements TGAudioOutput {

	private static final long LOG_INTERVAL = 5000;

	private long written;
	private long lastLog;
	private int peak;

	public void open(int bufferSize) throws Exception {
		if(!ImageInfo.inImageRuntimeCode()) {
			throw new UnsupportedOperationException("native image only");
		}
		int status = NativeAudio.open((int) TGAudioBuffer.SAMPLE_RATE, TGAudioBuffer.CHANNELS, bufferSize);
		TGGluonLog.log("audio output open: bufferSize=" + bufferSize + " status=" + status + " session error=" + NativeAudio.sessionError());
		if( status != 0 ) {
			throw new IllegalStateException("AudioQueue error " + status);
		}
	}

	public void write(byte[] buffer, int offset, int length) {
		try (PinnedObject pinned = PinnedObject.create(buffer)) {
			NativeAudio.write(pinned.addressOfArrayElement(offset), length);
		}
		this.written += length;
		// 16 bits signed little endian samples (TGAudioLine.AUDIO_FORMAT)
		for(int i = offset; i + 1 < offset + length; i += 2) {
			int sample = Math.abs((short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8)));
			if( sample > this.peak ) {
				this.peak = sample;
			}
		}
		long now = System.currentTimeMillis();
		if( now - this.lastLog > LOG_INTERVAL ) {
			this.lastLog = now;
			TGGluonLog.log("audio output: " + this.written + " bytes written, peak=" + this.peak);
			this.peak = 0;
		}
	}

	private static class NativeAudio {

		@CFunction("tg_audio_open")
		static native int open(int sampleRate, int channels, int bufferBytes);

		@CFunction("tg_audio_write")
		static native int write(CCharPointer data, int length);

		@CFunction("tg_audio_session_error")
		static native int sessionError();
	}
}
