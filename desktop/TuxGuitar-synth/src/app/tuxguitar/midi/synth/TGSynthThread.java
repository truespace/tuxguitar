package app.tuxguitar.midi.synth;

public class TGSynthThread implements Runnable {

	// opening the audio line is a native call that can block for good, e.g. while another
	// application holds the device: waiting for the thread to notice it should stop would then
	// never end, and closing the application would hang. It is a daemon thread, so leaving it
	// behind costs nothing.
	private static final long STOP_TIMEOUT = 2000;
	private static final long STOP_INTERVAL = 10;

	private volatile boolean running;
	private volatile boolean finished;
	private TGSynthesizer synthesizer;

	public TGSynthThread(TGSynthesizer synthesizer) {
		this.synthesizer = synthesizer;
		this.finished = true;
	}

	public void run() {
		try {
			this.finished = false;

			TGAudioSync audioSync = new TGAudioSync();
			TGAudioLine audioLine = new TGAudioLine(this.synthesizer);
			TGAudioBufferProcessor audioProcessor = new TGAudioBufferProcessor(this.synthesizer);

			while(this.isRunning()) {
				audioProcessor.process();
				audioLine.write(audioProcessor.getBuffer());
				audioSync.sync();
			}
		} finally {
			this.finished = true;
		}
	}

	private void startThread(){
		Thread thread = new Thread(this);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.setDaemon(true);
		thread.start();
	}

	private void waitThread(){
		long deadline = (System.currentTimeMillis() + STOP_TIMEOUT);
		while(!this.finished && System.currentTimeMillis() < deadline ){
			try {
				Thread.sleep(STOP_INTERVAL);
			} catch (InterruptedException interruption) {
				Thread.currentThread().interrupt();

				return;
			}
		}
	}

	public void start(){
		if( this.finished ) {
			this.running = true;
			this.startThread();
		}
	}

	public void stop(){
		if(!this.finished ) {
			this.running = false;
			this.waitThread();
		}
	}

	public boolean isRunning(){
		return this.running;
	}
}
