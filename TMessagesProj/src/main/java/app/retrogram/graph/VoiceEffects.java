package app.retrogram.graph;

/**
 * Voice-note effects on 16-bit mono PCM (WP 6.4, docs/plans/06-graph-features.md).
 *
 * Pure Java, no Android dependencies, so the DSP is testable on the JVM with golden signals. Every method
 * returns a new array and never modifies its input. The hook into `MediaController` recording (before Opus
 * encode), the preview-before-send UI and the setting in `AudioRecorderSettingsActivity` are separate work and
 * not done yet. CPU cost on the target device (SM6375) is unmeasured; port to native code only if it is too slow.
 *
 * Designed from the plan (pitch shift by WSOLA + resampling, ring modulator, echo), not from Graph Messenger.
 */
public final class VoiceEffects {

    private VoiceEffects() {}

    private static short clip(double v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) Math.round(v);
    }

    /** "Robot": multiplies the signal by a sine carrier. */
    public static short[] ringModulate(short[] in, int sampleRate, double carrierHz) {
        return ringModulate(in, sampleRate, carrierHz, 0);
    }

    /** Same, for a segment that starts {@code startSample} samples into the recording, so the carrier phase continues across segments. */
    public static short[] ringModulate(short[] in, int sampleRate, double carrierHz, long startSample) {
        final short[] out = new short[in.length];
        final double step = 2 * Math.PI * carrierHz / sampleRate;
        for (int i = 0; i < in.length; i++) {
            out[i] = clip(in[i] * Math.sin(step * (startSample + i)));
        }
        return out;
    }

    public static final int EFFECT_OFF = 0;
    public static final int EFFECT_ROBOT = 1;
    public static final int EFFECT_ECHO = 2;
    public static final int EFFECT_HIGHER = 3;
    public static final int EFFECT_DEEPER = 4;

    /**
     * One entry point for the recorder: applies the selected effect to one segment of a recording.
     *
     * @param finalSegment the last segment of the recording; only then may the echo tail extend the audio. Earlier segments keep their length
     * @param startSample  samples already processed before this segment (carrier phase for the robot voice)
     */
    public static short[] apply(int effect, short[] segment, int sampleRate, boolean finalSegment, long startSample) {
        switch (effect) {
            case EFFECT_ROBOT:
                return ringModulate(segment, sampleRate, 55, startSample);
            case EFFECT_ECHO: {
                final short[] withTail = echo(segment, sampleRate, 220, 0.45, 2);
                return finalSegment ? withTail : java.util.Arrays.copyOf(withTail, segment.length);
            }
            case EFFECT_HIGHER:
                return pitchShift(segment, sampleRate, 1.35);
            case EFFECT_DEEPER:
                return pitchShift(segment, sampleRate, 0.75);
            default:
                return segment;
        }
    }

    /**
     * Echo: adds the signal delayed by {@code delayMs}, scaled by {@code decay} (0..1), repeated {@code repeats}
     * times with the decay applied again each time. The output is longer by the echo tail.
     */
    public static short[] echo(short[] in, int sampleRate, int delayMs, double decay, int repeats) {
        final int delay = Math.max(1, (int) ((long) delayMs * sampleRate / 1000));
        final int reps = Math.max(0, repeats);
        final double[] acc = new double[in.length + delay * reps];
        for (int i = 0; i < in.length; i++) {
            acc[i] += in[i];
        }
        double gain = 1;
        for (int r = 1; r <= reps; r++) {
            gain *= decay;
            final int offset = delay * r;
            for (int i = 0; i < in.length; i++) {
                acc[i + offset] += in[i] * gain;
            }
        }
        final short[] out = new short[acc.length];
        for (int i = 0; i < acc.length; i++) {
            out[i] = clip(acc[i]);
        }
        return out;
    }

    /**
     * Pitch shift by {@code factor} (2.0 = one octave up) keeping the duration: WSOLA time-stretch by
     * {@code factor}, then resample by {@code factor}. Output length equals input length (+/- rounding).
     */
    public static short[] pitchShift(short[] in, int sampleRate, double factor) {
        if (in.length == 0 || factor <= 0 || Math.abs(factor - 1) < 1e-6) {
            return in.clone();
        }
        final short[] stretched = wsolaStretch(in, sampleRate, factor);
        final int outLen = in.length;
        final short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            final double pos = i * factor;
            final int p = (int) pos;
            if (p + 1 >= stretched.length) {
                out[i] = p < stretched.length ? stretched[p] : 0;
                continue;
            }
            final double frac = pos - p;
            out[i] = clip(stretched[p] * (1 - frac) + stretched[p + 1] * frac);
        }
        return out;
    }

    /** Time-stretches by {@code ratio} (>1 longer) without changing pitch. */
    static short[] wsolaStretch(short[] in, int sampleRate, double ratio) {
        final int frame = Math.max(64, sampleRate / 50);       // 20 ms
        final int overlap = frame / 2;
        final int hopOut = frame - overlap;
        final int hopIn = Math.max(1, (int) Math.round(hopOut / ratio));
        final int search = frame / 4;
        final int outLen = (int) Math.ceil(in.length * ratio) + frame;
        final double[] out = new double[outLen + frame];
        final double[] norm = new double[outLen + frame];
        final double[] window = new double[frame];
        for (int i = 0; i < frame; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * (i + 0.5) / frame); // Hann
        }
        int inPos = 0;
        int outPos = 0;
        int prevStart = 0;
        boolean first = true;
        while (outPos + frame < out.length && inPos < in.length) {
            int start = inPos;
            if (!first) {
                // choose the start near inPos whose beginning best continues the previous frame's natural continuation
                final int target = prevStart + hopOut;
                double best = Double.NEGATIVE_INFINITY;
                int bestStart = start;
                for (int d = -search; d <= search; d++) {
                    final int cand = inPos + d;
                    if (cand < 0 || cand + overlap >= in.length || target + overlap >= in.length) continue;
                    double corr = 0;
                    for (int k = 0; k < overlap; k += 2) {
                        corr += (double) in[target + k] * in[cand + k];
                    }
                    if (corr > best) {
                        best = corr;
                        bestStart = cand;
                    }
                }
                start = bestStart;
            }
            for (int i = 0; i < frame; i++) {
                final int idx = start + i;
                final double v = idx < in.length ? in[idx] : 0;
                out[outPos + i] += v * window[i];
                norm[outPos + i] += window[i];
            }
            prevStart = start;
            first = false;
            inPos += hopIn;
            outPos += hopOut;
        }
        final int n = Math.min((int) Math.round(in.length * ratio), out.length);
        final short[] res = new short[n];
        for (int i = 0; i < n; i++) {
            res[i] = clip(norm[i] > 1e-6 ? out[i] / norm[i] : 0);
        }
        return res;
    }
}
