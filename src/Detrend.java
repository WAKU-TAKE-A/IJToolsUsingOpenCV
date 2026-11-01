/**
 * Detrend.java
 *
 * デトレンド（トレンド除去）処理
 *
 * ・移動平均（累積和 O(N)）
 * ・線形回帰（最小二乗法）
 */
public final class Detrend {

    private static final double EPSILON = 1e-12;

    private Detrend() {
    }

    /**
     * 移動平均を使用してトレンドを除去する。
     * 累積和を利用して O(N) で計算する。
     *
     * @param input 入力信号
     * @param windowSize 窓サイズ（奇数推奨）
     * @return デトレンド後の信号
     */
    public static double[] removeMovingAverage(double[] input, int windowSize) {

        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }

        int n = input.length;

        if (windowSize > n) {
            throw new IllegalArgumentException("windowSize is larger than data length.");
        }

        double[] prefix = new double[n + 1];

        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + input[i];
        }

        double[] output = new double[n];

        int half = windowSize / 2;

        for (int i = 0; i < n; i++) {

            int left = Math.max(0, i - half);
            int right = Math.min(n - 1, i + half);

            int count = right - left + 1;

            double sum = prefix[right + 1] - prefix[left];

            double average = sum / count;

            output[i] = input[i] - average;
        }

        return output;
    }

    /**
     * 最小二乗法による線形トレンド除去
     *
     * y = ax + b
     *
     * @param input 入力信号
     * @return デトレンド後
     */
    public static double[] removeLinearTrend(double[] input) {

        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        int n = input.length;

        if (n <= 1) {
            return input.clone();
        }

        double meanX = (n - 1) * 0.5;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanY += input[i];
        }
        meanY /= n;

        double sumXY = 0.0;
        double sumX2 = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = i - meanX;
            double dy = input[i] - meanY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
        }

        if (sumX2 < EPSILON) {
            return input.clone();
        }

        double a = sumXY / sumX2;
        double[] output = new double[n];
        for (int i = 0; i < n; i++) {
            double trend = a * (i - meanX) + meanY;
            output[i] = input[i] - trend;
        }

        return output;
    }

    /**
     * 平均値を0に揃える。
     * 自己相関前に使用すると数値誤差が少なくなる。
     *
     * @param input 入力信号
     * @return 平均値0の信号
     */
    public static double[] removeMean(double[] input) {

        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        int n = input.length;

        double mean = 0.0;

        for (double v : input) {
            mean += v;
        }

        mean /= n;

        double[] output = new double[n];

        for (int i = 0; i < n; i++) {
            output[i] = input[i] - mean;
        }

        return output;
    }

}