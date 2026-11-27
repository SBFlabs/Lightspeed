// Lightspeed Neural Voice Isolation 2.0 - native DSP core (100% offline, no I/O, no network).
//
// Handles everything around the GTCRN neural model except inference itself:
//   48 kHz stereo -> mid/side split -> 16 kHz mono stem (polyphase FIR decimation) -> STFT
//   iSTFT -> 16 kHz stem -> 48 kHz (polyphase FIR interpolation) -> high-band residual bypass
//   (linear-phase mid complement + side channel, delay-aligned) -> RMS VAD gate -> post-mixer soft limiter.
//
// STFT matches the legacy Kotlin StftProcessor exactly (512 FFT, 256 hop, sqrt-Hann, [re|im] layout).
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

namespace {

constexpr int kFft = 512;
constexpr int kBins = kFft / 2 + 1;
constexpr int kHop16 = 256;
constexpr int kFactor = 3;
constexpr int kHop48 = kHop16 * kFactor;
constexpr int kTaps = 63;                      // multiple of kFactor, odd -> integer group delay
constexpr int kGroupDelay = (kTaps - 1) / 2;   // 31 samples @ 48 kHz
constexpr int kPerPhase = kTaps / kFactor;     // 21
constexpr int kAlignDelay = kHop16 * kFactor + 2 * kGroupDelay;  // hop delay of iSTFT + both FIRs
constexpr double kPi = 3.14159265358979323846;

struct SonicDsp {
    // --- STFT ---
    float window[kFft];
    float cosT[kFft / 2], sinT[kFft / 2];
    int bitrev[kFft];
    float analysis[kFft] = {0};
    float overlap[kFft] = {0};
    float re[kFft], im[kFft];

    // --- resamplers ---
    float lp[kTaps];
    float decHist[kTaps] = {0};          // mono 48 kHz history for decimation (circular)
    int decPos = 0;
    float intHist[kPerPhase] = {0};      // 16 kHz history for interpolation
    int intPos = 0;

    // --- high-band residual bypass ---
    // Linear-phase complement: residual = mid[t-62] - LP(LP(mid))[t]  (everything the 16 kHz stem cannot carry)
    float midRing[kTaps + 1] = {0};      // mid history (for the 62-sample matched delay)
    int midPos = 0;
    float lp1Hist[kTaps] = {0};          // mid history for LP stage 1
    int lp1Pos = 0;
    float lp2Hist[kTaps] = {0};          // stage-1 output history for LP stage 2
    int lp2Pos = 0;
    std::vector<float> highDelay, sideDelay;  // hop-long / kAlignDelay-long circular lines
    int highPos = 0, sidePos = 0;
    float alignedHigh[kHop48] = {0};         // delayed residual for the hop being rendered
    float alignedSide[kHop48] = {0};

    // --- VAD gate ---
    float rmsPrev = 0.f, rmsCur = 0.f;
    int hangover = 0;
    bool gateOpen = false;
    float gateGain = 1.f;

    // --- limiter ---
    float limiterGain = 1.f;

    SonicDsp() : highDelay(kHop48, 0.f), sideDelay(kAlignDelay, 0.f) {
        for (int i = 0; i < kFft; ++i)
            window[i] = std::sqrt(0.5f - 0.5f * static_cast<float>(std::cos(2.0 * kPi * i / kFft)));
        for (int i = 0; i < kFft / 2; ++i) {
            cosT[i] = static_cast<float>(std::cos(2.0 * kPi * i / kFft));
            sinT[i] = static_cast<float>(std::sin(2.0 * kPi * i / kFft));
        }
        int bits = 0;
        while ((1 << bits) < kFft) ++bits;
        for (int i = 0; i < kFft; ++i) {
            int r = 0;
            for (int b = 0; b < bits; ++b) if (i & (1 << b)) r |= 1 << (bits - 1 - b);
            bitrev[i] = r;
        }
        // Windowed-sinc low-pass @ 7.2 kHz (fs = 48 kHz), Blackman window, unity DC gain.
        const double fc = 7200.0 / 48000.0;
        double sum = 0;
        double tmp[kTaps];
        for (int n = 0; n < kTaps; ++n) {
            const double m = n - kGroupDelay;
            const double sinc = (m == 0) ? 2.0 * fc : std::sin(2.0 * kPi * fc * m) / (kPi * m);
            const double w = 0.42 - 0.5 * std::cos(2.0 * kPi * n / (kTaps - 1)) +
                             0.08 * std::cos(4.0 * kPi * n / (kTaps - 1));
            tmp[n] = sinc * w;
            sum += tmp[n];
        }
        for (int n = 0; n < kTaps; ++n) lp[n] = static_cast<float>(tmp[n] / sum);
    }

    void reset() {
        std::memset(analysis, 0, sizeof(analysis));
        std::memset(overlap, 0, sizeof(overlap));
        std::memset(decHist, 0, sizeof(decHist));
        std::memset(intHist, 0, sizeof(intHist));
        decPos = intPos = midPos = lp1Pos = lp2Pos = highPos = sidePos = 0;
        std::memset(midRing, 0, sizeof(midRing));
        std::memset(lp1Hist, 0, sizeof(lp1Hist));
        std::memset(lp2Hist, 0, sizeof(lp2Hist));
        std::fill(highDelay.begin(), highDelay.end(), 0.f);
        std::fill(sideDelay.begin(), sideDelay.end(), 0.f);
        std::memset(alignedHigh, 0, sizeof(alignedHigh));
        std::memset(alignedSide, 0, sizeof(alignedSide));
        rmsPrev = rmsCur = 0.f;
        hangover = 0;
        gateOpen = false;
        gateGain = 1.f;
        limiterGain = 1.f;
    }

    void fft(bool inverse) {
        for (int i = 0; i < kFft; ++i) {
            const int j = bitrev[i];
            if (i < j) { std::swap(re[i], re[j]); std::swap(im[i], im[j]); }
        }
        for (int len = 2; len <= kFft; len <<= 1) {
            const int half = len / 2, step = kFft / len;
            for (int off = 0; off < kFft; off += len) {
                for (int k = 0; k < half; ++k) {
                    const float wr = cosT[k * step];
                    const float wi = inverse ? sinT[k * step] : -sinT[k * step];
                    const int o = off + k + half, e = off + k;
                    const float tr = wr * re[o] - wi * im[o];
                    const float ti = wr * im[o] + wi * re[o];
                    re[o] = re[e] - tr; im[o] = im[e] - ti;
                    re[e] += tr;        im[e] += ti;
                }
            }
        }
        if (inverse) {
            const float s = 1.f / kFft;
            for (int i = 0; i < kFft; ++i) { re[i] *= s; im[i] *= s; }
        }
    }

    void stftAnalyze(const float* hop, float* spectrum) {
        std::memmove(analysis, analysis + kHop16, (kFft - kHop16) * sizeof(float));
        std::memcpy(analysis + kFft - kHop16, hop, kHop16 * sizeof(float));
        for (int i = 0; i < kFft; ++i) { re[i] = analysis[i] * window[i]; im[i] = 0.f; }
        fft(false);
        for (int b = 0; b < kBins; ++b) { spectrum[b] = re[b]; spectrum[b + kBins] = im[b]; }
    }

    void stftSynthesize(const float* spectrum, float* hop) {
        for (int b = 0; b < kBins; ++b) { re[b] = spectrum[b]; im[b] = spectrum[b + kBins]; }
        for (int b = 1; b < kBins - 1; ++b) { re[kFft - b] = re[b]; im[kFft - b] = -im[b]; }
        fft(true);
        for (int i = 0; i < kFft; ++i) overlap[i] += re[i] * window[i];
        std::memcpy(hop, overlap, kHop16 * sizeof(float));
        std::memmove(overlap, overlap + kHop16, (kFft - kHop16) * sizeof(float));
        std::memset(overlap + kFft - kHop16, 0, kHop16 * sizeof(float));
    }

    // Mid/side split of one interleaved 48 kHz hop. Produces 16 kHz mono stem + stores
    // delayed mid/side residual for the render stage. Also measures full-band RMS for the VAD.
    void analyze(const int16_t* pcm, float* dry16, float* spectrum) {
        constexpr float kScale = 1.f / 32768.f;
        double energy = 0;
        int outIdx = 0;
        for (int i = 0; i < kHop48; ++i) {
            const float l = pcm[2 * i] * kScale, r = pcm[2 * i + 1] * kScale;
            const float mid = 0.5f * (l + r);
            energy += 0.5 * (static_cast<double>(l) * l + static_cast<double>(r) * r);
            decHist[decPos] = mid;
            if (i % kFactor == 0) {
                float acc = 0.f;
                int idx = decPos;
                for (int t = 0; t < kTaps; ++t) {
                    acc += lp[t] * decHist[idx];
                    if (--idx < 0) idx = kTaps - 1;
                }
                dry16[outIdx++] = acc;
            }
            if (++decPos == kTaps) decPos = 0;
            // Residual path (complement of the neural low band), delayed into alignment.
            midRing[midPos] = mid;
            lp1Hist[lp1Pos] = mid;
            float s1 = 0.f;
            for (int t = 0, idx = lp1Pos; t < kTaps; ++t) {
                s1 += lp[t] * lp1Hist[idx];
                if (--idx < 0) idx = kTaps - 1;
            }
            lp2Hist[lp2Pos] = s1;
            float s2 = 0.f;
            for (int t = 0, idx = lp2Pos; t < kTaps; ++t) {
                s2 += lp[t] * lp2Hist[idx];
                if (--idx < 0) idx = kTaps - 1;
            }
            const float matchedMid = midRing[(midPos + kTaps + 1 - 2 * kGroupDelay) % (kTaps + 1)];
            const float high = matchedMid - s2;
            const float side = 0.5f * (l - r);
            alignedHigh[i] = highDelay[highPos];  // read oldest, then overwrite with newest
            alignedSide[i] = sideDelay[sidePos];
            highDelay[highPos] = high;
            sideDelay[sidePos] = side;
            if (++highPos == kHop48) highPos = 0;
            if (++sidePos == kAlignDelay) sidePos = 0;
            if (++midPos == kTaps + 1) midPos = 0;
            if (++lp1Pos == kTaps) lp1Pos = 0;
            if (++lp2Pos == kTaps) lp2Pos = 0;
        }
        rmsPrev = rmsCur;
        rmsCur = static_cast<float>(std::sqrt(energy / kHop48));
        stftAnalyze(dry16, spectrum);
    }

    void render(const float* mixed16, float airGain, float sideGain, int16_t* out) {
        // VAD: hysteresis + hangover over [previous, current] hop RMS (one-hop look-ahead).
        const float level = std::max(rmsPrev, rmsCur);
        constexpr float kOpen = 0.0020f, kClose = 0.0010f;
        constexpr int kHangHops = 8;
        if (level >= kOpen) { gateOpen = true; hangover = kHangHops; }
        else if (level < kClose) { if (hangover > 0) --hangover; else gateOpen = false; }
        else if (hangover > 0) { --hangover; }
        const float gateTarget = gateOpen ? 1.f : 0.f;
        const float gateAtk = 1.f - std::exp(-1.f / (0.002f * 48000.f));
        const float gateRel = 1.f - std::exp(-1.f / (0.060f * 48000.f));
        const float limRel = 1.f - std::exp(-1.f / (0.060f * 48000.f));
        constexpr float kCeiling = 0.95f;

        // Polyphase interpolation of the 16 kHz mixed stem to 48 kHz.
        for (int n = 0; n < kHop16; ++n) {
            intHist[intPos] = mixed16[n];
            for (int p = 0; p < kFactor; ++p) {
                float acc = 0.f;
                int idx = intPos;
                for (int k = 0; k < kPerPhase; ++k) {
                    acc += lp[k * kFactor + p] * intHist[idx];
                    if (--idx < 0) idx = kPerPhase - 1;
                }
                const float low = acc * kFactor;
                const int si = n * kFactor + p;
                const float high = alignedHigh[si] * airGain;
                const float side = alignedSide[si] * sideGain;

                float l = low + high + side;
                float r = low + high - side;

                gateGain += (gateTarget - gateGain) * (gateTarget > gateGain ? gateAtk : gateRel);
                const float g = 0.02f + 0.98f * gateGain;  // -34 dB floor keeps silence clean
                l *= g; r *= g;

                const float peak = std::max(std::fabs(l), std::fabs(r));
                const float need = peak > kCeiling ? kCeiling / peak : 1.f;
                if (need < limiterGain) limiterGain = need;
                else limiterGain += (1.f - limiterGain) * limRel;
                l *= limiterGain; r *= limiterGain;
                l = softClip(l); r = softClip(r);

                const int o = (n * kFactor + p) * 2;
                out[o] = toPcm(l);
                out[o + 1] = toPcm(r);
            }
            if (++intPos == kPerPhase) intPos = 0;
        }
    }

    static inline float softClip(float x) {
        const float a = std::fabs(x);
        if (a <= 0.92f) return x;
        const float y = 0.92f + 0.07f * std::tanh((a - 0.92f) / 0.07f);
        return x < 0.f ? -y : y;
    }
    static inline int16_t toPcm(float x) {
        const float c = std::min(std::max(x, -1.f), 0.9999695f);
        return static_cast<int16_t>(c * 32768.f);
    }
};

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sbf_lightspeed_system_sonic_NativeSonicDsp_nativeCreate(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new SonicDsp());
}

JNIEXPORT void JNICALL
Java_com_sbf_lightspeed_system_sonic_NativeSonicDsp_nativeDestroy(JNIEnv*, jclass, jlong h) {
    if (!h) return;
    delete reinterpret_cast<SonicDsp*>(h);
}

JNIEXPORT void JNICALL
Java_com_sbf_lightspeed_system_sonic_NativeSonicDsp_nativeReset(JNIEnv*, jclass, jlong h) {
    if (!h) return;
    reinterpret_cast<SonicDsp*>(h)->reset();
}

JNIEXPORT void JNICALL
Java_com_sbf_lightspeed_system_sonic_NativeSonicDsp_nativeAnalyze(
        JNIEnv* env, jclass, jlong h, jshortArray pcm, jfloatArray dry16, jfloatArray spectrum) {
    if (!h || !pcm || !dry16 || !spectrum) return;
    if (env->GetArrayLength(pcm) < kHop48 * 2 ||
        env->GetArrayLength(dry16) < kHop16 ||
        env->GetArrayLength(spectrum) < kBins * 2) {
        return;
    }
    auto* d = reinterpret_cast<SonicDsp*>(h);
    jshort* p = env->GetShortArrayElements(pcm, nullptr);
    jfloat* dr = env->GetFloatArrayElements(dry16, nullptr);
    jfloat* sp = env->GetFloatArrayElements(spectrum, nullptr);
    if (p && dr && sp) {
        d->analyze(reinterpret_cast<const int16_t*>(p), dr, sp);
    }
    if (sp) env->ReleaseFloatArrayElements(spectrum, sp, 0);
    if (dr) env->ReleaseFloatArrayElements(dry16, dr, 0);
    if (p) env->ReleaseShortArrayElements(pcm, p, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_sbf_lightspeed_system_sonic_NativeSonicDsp_nativeSynthesize(
        JNIEnv* env, jclass, jlong h, jfloatArray spectrum, jfloatArray speech16) {
    if (!h || !spectrum || !speech16) return;
    if (env->GetArrayLength(spectrum) < kBins * 2 ||
        env->GetArrayLength(speech16) < kHop16) {
        return;
    }
    auto* d = reinterpret_cast<SonicDsp*>(h);
    jfloat* sp = env->GetFloatArrayElements(spectrum, nullptr);
    jfloat* out = env->GetFloatArrayElements(speech16, nullptr);
    if (sp && out) {
        d->stftSynthesize(sp, out);
    }
    if (out) env->ReleaseFloatArrayElements(speech16, out, 0);
    if (sp) env->ReleaseFloatArrayElements(spectrum, sp, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_sbf_lightspeed_system_sonic_NativeSonicDsp_nativeRender(
        JNIEnv* env, jclass, jlong h, jfloatArray mixed16, jfloat airGain, jfloat sideGain,
        jshortArray out) {
    if (!h || !mixed16 || !out) return;
    if (env->GetArrayLength(mixed16) < kHop16 ||
        env->GetArrayLength(out) < kHop48 * 2) {
        return;
    }
    auto* d = reinterpret_cast<SonicDsp*>(h);
    jfloat* m = env->GetFloatArrayElements(mixed16, nullptr);
    jshort* o = env->GetShortArrayElements(out, nullptr);
    if (m && o) {
        d->render(m, airGain, sideGain, reinterpret_cast<int16_t*>(o));
    }
    if (o) env->ReleaseShortArrayElements(out, o, 0);
    if (m) env->ReleaseFloatArrayElements(mixed16, m, JNI_ABORT);
}

}  // extern "C"
