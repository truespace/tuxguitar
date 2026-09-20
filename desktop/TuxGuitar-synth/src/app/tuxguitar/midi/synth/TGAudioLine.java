package app.tuxguitar.midi.synth;

import java.util.Iterator;
import java.util.ServiceLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import app.tuxguitar.util.TGException;

public class TGAudioLine {

	public static final AudioFormat AUDIO_FORMAT = new AudioFormat(TGAudioBuffer.SAMPLE_RATE, 16, TGAudioBuffer.CHANNELS, true, TGAudioBuffer.BIGENDIAN);

	private SourceDataLine line;
	private TGAudioOutput output;

	public TGAudioLine(TGSynthesizer synthesizer) {
		int bufferSize = (TGAudioBuffer.CHANNELS * TGAudioBuffer.BUFFER_SIZE) * Math.max(synthesizer.getSettings().getAudioBufferSize(), 1);
		try {
			this.output = openOutput(bufferSize);
			if( this.output == null ) {
				this.line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT));
				this.line.open(AUDIO_FORMAT, bufferSize);
				this.line.start();
			}
		} catch (Throwable e) {
			throw new TGException(e);
		}
	}

	private static TGAudioOutput openOutput(int bufferSize) {
		Iterator<TGAudioOutput> outputs = ServiceLoader.load(TGAudioOutput.class, TGAudioLine.class.getClassLoader()).iterator();
		while( outputs.hasNext() ) {
			try {
				TGAudioOutput output = outputs.next();
				output.open(bufferSize);
				return output;
			} catch (Throwable throwable) {
				// not available on this platform
			}
		}
		return null;
	}

	public void write(TGAudioBuffer buffer) {
		if( this.output != null ) {
			this.output.write(buffer.getBuffer(), 0, buffer.getLength());
		} else {
			this.line.write(buffer.getBuffer(), 0, buffer.getLength());
		}
	}
}
