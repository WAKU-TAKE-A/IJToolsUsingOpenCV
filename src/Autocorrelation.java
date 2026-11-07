/**
 * Autocorrelation.java
 *
 * 自己相関関数(ACF)の計算
 *
 * ・偏り推定（分母を n で統一し、|ACF| <= 1 を保証）
 * ・minPeriod / maxPeriod対応
 * ・大きな信号では FFT ベースの計算（O(n log n)）に切り替える
 */
public final class Autocorrelation {

    private static final double EPSILON = 1e-12;

    /**
     * ペア数が少なすぎる lag は統計的に信用できないためのガード。
     * count = n - lag が n * MIN_SAMPLE_RATIO を下回る lag は計算対象外。
     * 0.1（ペア数10%まで許容）は緩すぎたため 0.3 に引き上げた。
     */
    private static final double MIN_SAMPLE_RATIO = 0.3;

    /** これ以上の長さでは FFT ベースの自己相関を使う。 */
    private static final int FFT_THRESHOLD = 256;

    private Autocorrelation() {
    }

    /**
     * 自己相関を計算する。
     *
     * 正規化は偏り推定（biased）に統一した:
     *   acf[lag] = sum_{i}(x[i] * x[i+lag]) / sum_{i}(x[i]^2)
     * 分子・分母が同じ母集団に基づくため |acf[lag]| <= 1 が保証され、
     * acf[0] は常に 1.0 になる（Confidence が 1 を超える不具合の修正）。
     *
     * @param input 平均0付近の信号
     * @param minLag 最小ラグ
     * @param maxLag 最大ラグ
     * @return ACF配列（インデックス = ラグ）
     */
    public static double[] compute(
            double[] input,
            int minLag,
            int maxLag) {

        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        int n = input.length;

        if (n < 2) {
            return new double[0];
        }

        if (minLag < 1) {
            minLag = 1;
        }

        maxLag = Math.min(maxLag, n - 1);

        //----------------------------------
        // 信頼できるペア数を確保できる lag までに制限する。
        //----------------------------------

        int minCount = Math.max(1, (int) (n * MIN_SAMPLE_RATIO));
        int reliableMaxLag = n - minCount;

        maxLag = Math.min(maxLag, reliableMaxLag);

        if (minLag > maxLag) {
            return new double[0];
        }

        //----------------------------------
        // ラグ0の自己相関（= 二乗和）
        //----------------------------------

        double raw0 = 0.0;
        for (double v : input) {
            raw0 += v * v;
        }

        if (raw0 / n < EPSILON) {
            return new double[0];
        }

        //----------------------------------
        // 自己相関
        //----------------------------------

        double[] acf = new double[maxLag + 1];
        acf[0] = 1.0;

        if (n >= FFT_THRESHOLD) {
            double[] raw = rawViaFft(input, maxLag);
            for (int lag = 1; lag <= maxLag; lag++) {
                acf[lag] = clampUnit(raw[lag] / raw0);
            }
        } else {
            for (int lag = 1; lag <= maxLag; lag++) {
                double sum = 0.0;
                int count = n - lag;
                for (int i = 0; i < count; i++) {
                    sum += input[i] * input[i + lag];
                }
                acf[lag] = clampUnit(sum / raw0);
            }
        }

        return acf;
    }

    /** 数値誤差でわずかに範囲を超えた場合の保険。 */
    private static double clampUnit(double v) {
        if (v > 1.0) return 1.0;
        if (v < -1.0) return -1.0;
        return v;
    }

    /**
     * FFT ベースで raw[lag] = sum_{i}(x[i] * x[i+lag]) を求める。
     * 信号を 2n 以上の 2 の冪にゼロ埋めし、パワースペクトルの逆 FFT を取る
     * （Wiener–Khinchin）。計算量は O(n log n)。
     */
    private static double[] rawViaFft(double[] input, int maxLag) {
        int n = input.length;

        int size = 1;
        while (size < 2 * n) {
            size <<= 1;
        }

        double[] re = new double[size];
        double[] im = new double[size];
        System.arraycopy(input, 0, re, 0, n);

        fft(re, im, false);

        for (int i = 0; i < size; i++) {
            re[i] = re[i] * re[i] + im[i] * im[i];
            im[i] = 0.0;
        }

        fft(re, im, true);

        double[] raw = new double[maxLag + 1];
        for (int lag = 0; lag <= maxLag; lag++) {
            raw[lag] = re[lag];
        }
        return raw;
    }

    /** 反復型 Cooley–Tukey FFT。inverse=true で逆変換（1/N スケーリング付き）。 */
    private static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;

        // ビット反転並べ替え
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }

        double sign = inverse ? 1.0 : -1.0;

        for (int len = 2; len <= n; len <<= 1) {
            double ang = sign * 2.0 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            int half = len >> 1;
            for (int i = 0; i < n; i += len) {
                double cwr = 1.0;
                double cwi = 0.0;
                for (int k = 0; k < half; k++) {
                    int u = i + k;
                    int v = u + half;
                    double vr = re[v] * cwr - im[v] * cwi;
                    double vi = re[v] * cwi + im[v] * cwr;
                    re[v] = re[u] - vr;
                    im[v] = im[u] - vi;
                    re[u] += vr;
                    im[u] += vi;
                    double nwr = cwr * wr - cwi * wi;
                    cwi = cwr * wi + cwi * wr;
                    cwr = nwr;
                }
            }
        }

        if (inverse) {
            for (int i = 0; i < n; i++) {
                re[i] /= n;
                im[i] /= n;
            }
        }
    }

    /**
     * 指定範囲だけを取り出す。
     */
    public static double[] crop(
            double[] acf,
            int minLag,
            int maxLag) {

        if (acf == null) {
            throw new IllegalArgumentException("acf is null.");
        }

        minLag = Math.max(0, minLag);
        maxLag = Math.min(acf.length - 1, maxLag);

        if (minLag > maxLag) {
            return new double[0];
        }

        double[] result = new double[maxLag - minLag + 1];

        System.arraycopy(
                acf,
                minLag,
                result,
                0,
                result.length);

        return result;
    }

    /**
     * 最大ラグの初期値を取得。
     *
     * maxPeriod未指定時に使用。
     */
    public static int defaultMaxLag(int dataLength) {
        return Math.max(1, dataLength / 2);
    }

}
