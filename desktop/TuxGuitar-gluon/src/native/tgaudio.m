/*
 * Audio output for the Gluon (GraalVM native image) builds, called from TGGluonAudioOutput
 * through @CFunction: a ring buffer filled by the synthesizer thread and played by an AudioQueue.
 * tg_audio_write blocks while the ring buffer is full, like SourceDataLine.write, which paces
 * the synthesizer. Builds for iOS and macOS.
 */
#include <AudioToolbox/AudioToolbox.h>
#include <TargetConditionals.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#if TARGET_OS_IPHONE
#import <AVFoundation/AVFoundation.h>
#endif

#define TG_AUDIO_QUEUE_BUFFERS 3
#define TG_AUDIO_QUEUE_BUFFER_BYTES 4096

static AudioQueueRef tg_queue = NULL;
static unsigned char *tg_ring = NULL;
static int tg_ring_size = 0;
static int tg_ring_read = 0;
static int tg_ring_fill = 0;
static pthread_mutex_t tg_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t tg_cond = PTHREAD_COND_INITIALIZER;
static int tg_session_error = 0;

static void tg_audio_callback(void *userData, AudioQueueRef queue, AudioQueueBufferRef buffer) {
	unsigned char *data = (unsigned char *) buffer->mAudioData;
	int capacity = (int) buffer->mAudioDataBytesCapacity;
	int copied = 0;

	pthread_mutex_lock(&tg_mutex);
	while (copied < capacity && tg_ring_fill > 0) {
		int chunk = capacity - copied;
		int contiguous = tg_ring_size - tg_ring_read;
		if (chunk > tg_ring_fill) chunk = tg_ring_fill;
		if (chunk > contiguous) chunk = contiguous;
		memcpy(data + copied, tg_ring + tg_ring_read, chunk);
		tg_ring_read = (tg_ring_read + chunk) % tg_ring_size;
		tg_ring_fill -= chunk;
		copied += chunk;
	}
	pthread_cond_signal(&tg_cond);
	pthread_mutex_unlock(&tg_mutex);

	// underrun: play silence
	if (copied < capacity) {
		memset(data + copied, 0, capacity - copied);
	}
	buffer->mAudioDataByteSize = capacity;
	AudioQueueEnqueueBuffer(queue, buffer, 0, NULL);
}

int tg_audio_open(int sampleRate, int channels, int bufferBytes) {
	if (tg_queue != NULL) {
		return 0;
	}

#if TARGET_OS_IPHONE
	// play even with the silent switch on, and activate the session
	AVAudioSession *session = [AVAudioSession sharedInstance];
	NSError *error = nil;
	if (![session setCategory:AVAudioSessionCategoryPlayback error:&error]) {
		tg_session_error = (error != nil ? (int) error.code : -1);
	}
	error = nil;
	if (![session setActive:YES error:&error] && tg_session_error == 0) {
		tg_session_error = (error != nil ? (int) error.code : -2);
	}
#endif

	AudioStreamBasicDescription format;
	memset(&format, 0, sizeof(format));
	format.mSampleRate = sampleRate;
	format.mFormatID = kAudioFormatLinearPCM;
	format.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked;
	format.mBitsPerChannel = 16;
	format.mChannelsPerFrame = channels;
	format.mBytesPerFrame = 2 * channels;
	format.mFramesPerPacket = 1;
	format.mBytesPerPacket = format.mBytesPerFrame;

	tg_ring_size = (bufferBytes > TG_AUDIO_QUEUE_BUFFER_BYTES ? bufferBytes : TG_AUDIO_QUEUE_BUFFER_BYTES);
	tg_ring = (unsigned char *) malloc(tg_ring_size);
	tg_ring_read = 0;
	tg_ring_fill = 0;

	// NULL run loop: callbacks run on an internal AudioQueue thread
	OSStatus status = AudioQueueNewOutput(&format, tg_audio_callback, NULL, NULL, NULL, 0, &tg_queue);
	if (status != noErr) {
		tg_queue = NULL;
		return (int) status;
	}
	for (int i = 0; i < TG_AUDIO_QUEUE_BUFFERS; i++) {
		AudioQueueBufferRef buffer;
		status = AudioQueueAllocateBuffer(tg_queue, TG_AUDIO_QUEUE_BUFFER_BYTES, &buffer);
		if (status != noErr) {
			return (int) status;
		}
		tg_audio_callback(NULL, tg_queue, buffer);
	}
	return (int) AudioQueueStart(tg_queue, NULL);
}

int tg_audio_write(const char *data, int length) {
	const unsigned char *bytes = (const unsigned char *) data;
	pthread_mutex_lock(&tg_mutex);
	while (length > 0) {
		while (tg_ring_fill == tg_ring_size) {
			pthread_cond_wait(&tg_cond, &tg_mutex);
		}
		int write = (tg_ring_read + tg_ring_fill) % tg_ring_size;
		int chunk = tg_ring_size - tg_ring_fill;
		int contiguous = tg_ring_size - write;
		if (chunk > length) chunk = length;
		if (chunk > contiguous) chunk = contiguous;
		memcpy(tg_ring + write, bytes, chunk);
		tg_ring_fill += chunk;
		bytes += chunk;
		length -= chunk;
	}
	pthread_mutex_unlock(&tg_mutex);
	return 0;
}

/* error code of the AVAudioSession setup on iOS, 0 when successful (always 0 on macOS) */
int tg_audio_session_error(void) {
	return tg_session_error;
}
