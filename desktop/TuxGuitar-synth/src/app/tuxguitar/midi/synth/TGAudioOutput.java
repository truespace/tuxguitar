package app.tuxguitar.midi.synth;

/**
 * Alternative audio output for platforms without a javax.sound SourceDataLine (e.g. iOS).
 * Implementations are looked up with java.util.ServiceLoader by TGAudioLine; when none
 * can be opened, the default javax.sound line is used.
 */
public interface TGAudioOutput {

	/**
	 * @param bufferSize size in bytes of the audio buffer, in TGAudioLine.AUDIO_FORMAT
	 * @throws Exception when the output is not available on this platform
	 */
	void open(int bufferSize) throws Exception;

	/**
	 * Blocks while the audio buffer is full, like SourceDataLine.write.
	 */
	void write(byte[] buffer, int offset, int length);
}
