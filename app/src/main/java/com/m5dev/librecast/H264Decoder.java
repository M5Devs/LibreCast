package com.m5dev.librecast;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class H264Decoder {

    private static final String TAG = "H264Decoder";
    private static final int MAX_QUEUE_SIZE = 5;

    public interface DecoderListener {
        void onFirstFrameRendered();
        void onOutputFormatChanged(MediaFormat format);
        void onError(Exception e);
    }

    private static class FrameData {
        final byte[] data;
        final long presentationTimeUs;
        final int flags;

        FrameData(byte[] data, long presentationTimeUs, int flags) {
            this.data = data;
            this.presentationTimeUs = presentationTimeUs;
            this.flags = flags;
        }
    }

    private final Surface surface;
    private final DecoderListener listener;
    private final Queue<FrameData> frameQueue = new LinkedList<>();

    private MediaCodec codec;
    private Thread workerThread;
    private volatile boolean isRunning = false;
    private volatile boolean firstFrameRendered = false;

    public H264Decoder(Surface surface) {
        this(surface, null);
    }

    public H264Decoder(Surface surface, DecoderListener listener) {
        this.surface = surface;
        this.listener = listener;
    }

    public synchronized void start() {
        if (isRunning) {
            return;
        }
        if (surface == null || !surface.isValid()) {
            Log.e(TAG, "Cannot start decoder: Surface is invalid or null");
            return;
        }
        try {
            codec = MediaCodec.createDecoderByType("video/avc");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", 1920, 1080);
            codec.configure(format, surface, null, 0);
            codec.start();
            isRunning = true;
            firstFrameRendered = false;

            workerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    workerLoop();
                }
            }, "H264DecoderWorker");
            workerThread.start();
            Log.i(TAG, "H264Decoder started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MediaCodec H264 decoder", e);
            stopInternal();
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    public synchronized void stop() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(500);
            } catch (InterruptedException ignored) {
            }
            workerThread = null;
        }
        stopInternal();
        synchronized (frameQueue) {
            frameQueue.clear();
        }
        Log.i(TAG, "H264Decoder stopped");
    }

    private void stopInternal() {
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            try {
                codec.release();
            } catch (Exception ignored) {
            }
            codec = null;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void feedFrame(ByteBuffer buffer) {
        if (!isRunning || buffer == null || !buffer.hasRemaining()) {
            return;
        }
        byte[] bytes = new byte[buffer.remaining()];
        int origPos = buffer.position();
        buffer.get(bytes);
        buffer.position(origPos);
        feedFrame(bytes);
    }

    public void feedFrame(byte[] bytes) {
        if (!isRunning || bytes == null || bytes.length == 0) {
            return;
        }
        int flags = detectFlags(bytes);
        FrameData frame = new FrameData(bytes, System.nanoTime() / 1000, flags);

        synchronized (frameQueue) {
            while (frameQueue.size() >= MAX_QUEUE_SIZE) {
                FrameData removed = null;
                for (FrameData f : frameQueue) {
                    if ((f.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        removed = f;
                        break;
                    }
                }
                if (removed != null) {
                    frameQueue.remove(removed);
                } else {
                    frameQueue.poll();
                }
            }
            frameQueue.add(frame);
        }
    }

    private int detectFlags(byte[] data) {
        int flags = 0;
        if (data == null || data.length < 4) {
            return flags;
        }
        int nalType = -1;
        if (data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1) {
            if (data.length > 4) {
                nalType = data[4] & 0x1F;
            }
        } else if (data[0] == 0 && data[1] == 0 && data[2] == 1) {
            nalType = data[3] & 0x1F;
        } else {
            nalType = data[0] & 0x1F;
        }

        if (nalType == 7 || nalType == 8) { // SPS or PPS NAL unit
            flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        }
        return flags;
    }

    private void workerLoop() {
        ByteBuffer[] inputBuffers = null;
        ByteBuffer[] outputBuffers = null;

        try {
            inputBuffers = codec.getInputBuffers();
            outputBuffers = codec.getOutputBuffers();
        } catch (Exception e) {
            Log.e(TAG, "Error getting codec buffers", e);
            if (listener != null) {
                listener.onError(e);
            }
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            boolean workDone = false;

            // Drain output buffers
            try {
                while (isRunning) {
                    int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000); // 1ms timeout
                    if (outputBufferIndex >= 0) {
                        codec.releaseOutputBuffer(outputBufferIndex, true);
                        workDone = true;
                        if (!firstFrameRendered) {
                            firstFrameRendered = true;
                            if (listener != null) {
                                listener.onFirstFrameRendered();
                            }
                        }
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = codec.getOutputBuffers();
                        workDone = true;
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = codec.getOutputFormat();
                        Log.d(TAG, "Output format changed: " + newFormat);
                        if (listener != null) {
                            listener.onOutputFormatChanged(newFormat);
                        }
                        workDone = true;
                    } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Error dequeuing output buffer", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }

            // Feed input buffers
            try {
                while (isRunning) {
                    FrameData frame;
                    synchronized (frameQueue) {
                        frame = frameQueue.peek();
                    }
                    if (frame == null) {
                        break;
                    }

                    int inputBufferIndex = codec.dequeueInputBuffer(0);
                    if (inputBufferIndex >= 0) {
                        synchronized (frameQueue) {
                            frameQueue.poll();
                        }
                        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                        inputBuffer.clear();
                        inputBuffer.put(frame.data);
                        codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                frame.data.length,
                                frame.presentationTimeUs,
                                frame.flags
                        );
                        workDone = true;
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Error queuing input buffer", e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }

            if (!workDone) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
