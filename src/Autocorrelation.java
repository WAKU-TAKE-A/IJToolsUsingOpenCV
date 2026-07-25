/**
 * Autocorrelation.java
 *
 * 自己相関関数(ACF)の計算
 *
 * ・不偏推定 (n-k補正)
 * ・minPeriod / maxPeriod対応
 */
public final class Autocorrelation {

    private static final double EPSILON = 1e-12;

    /**
     * 不偏推定の分母(count = n - lag)が小さくなりすぎると
     * 少数サンプルでの共分散が乱高下し、ACF値が不安定になる。
     * count が n * MIN_SAMPLE_RATIO を下回るlagは計算対象外とする。
     */
    private static final double MIN_SAMPLE_RATIO = 0.1;

    private Autocorrelation() {
    }

    /**
     * 自己相関を計算する。
     *
     * @param input 平均0付近の信号
     * @param minLag 最小ラグ
     * @param maxLag 最大ラグ
     * @return ACF配列
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
        // Fix #4: cap maxLag so covariance is estimated
        // from a reliable number of samples.
        //----------------------------------

        int minCount = Math.max(1, (int) (n * MIN_SAMPLE_RATIO));
        int reliableMaxLag = n - minCount;

        maxLag = Math.min(maxLag, reliableMaxLag);

        if (minLag > maxLag) {
            return new double[0];
        }

        //----------------------------------
        // 分散
        //----------------------------------

        double variance = 0.0;

        for (double v : input) {
            variance += v * v;
        }

        variance /= n;

        if (variance < EPSILON) {
            return new double[0];
        }

        //----------------------------------
        // 自己相関
        //----------------------------------

        double[] acf = new double[maxLag + 1];

        acf[0] = 1.0;

        for (int lag = 1; lag <= maxLag; lag++) {

            double covariance = 0.0;

            int count = n - lag;

            for (int i = 0; i < count; i++) {
                covariance += input[i] * input[i + lag];
            }

            // 不偏推定
            covariance /= count;

            acf[lag] = covariance / variance;
        }

        return acf;
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
