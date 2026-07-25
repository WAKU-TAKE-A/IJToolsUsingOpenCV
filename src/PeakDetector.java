/**
 * PeakDetector.java
 *
 * 自己相関ピーク検出
 *
 * ・HEIGHT
 * ・PROMINENCE
 * ・相対閾値
 */
public final class PeakDetector {

    /**
     * ピーク評価方法
     */
    public enum PeakMethod {
        HEIGHT,
        PROMINENCE
    }

    public static final double DEFAULT_RELATIVE_THRESHOLD = 0.5;

    private PeakDetector() {
    }

    /**
     * ピークを検出する。
     *
     * @param acf 自己相関
     * @param minLag 最小周期
     * @param maxLag 最大周期
     * @param method 評価方法
     * @param relativeThreshold 最大ピーク比率(0～1)
     * @return 周期
     */
    public static int findPeak(
            double[] acf,
            int minLag,
            int maxLag,
            PeakMethod method,
            double relativeThreshold) {

        if (acf == null || acf.length == 0) {
            return -1;
        }

        minLag = Math.max(1, minLag);
        maxLag = Math.min(maxLag, acf.length - 2);

        //------------------------------------------
        // 最大ピーク
        //------------------------------------------

        double maxPeak = Double.NEGATIVE_INFINITY;

        for (int i = minLag; i <= maxLag; i++) {

            if (isPeak(acf, i)) {
                maxPeak = Math.max(maxPeak, acf[i]);
            }
        }

        if (maxPeak == Double.NEGATIVE_INFINITY) {
            return -1;
        }

        // Fix #1: A non-positive maxPeak means there is no meaningful
        // positive correlation in range. Multiplying a negative maxPeak by
        // relativeThreshold (< 1) moves the threshold toward zero, which
        // would incorrectly exclude the max peak itself. Bail out instead.
        if (maxPeak <= 0) {
            return -1;
        }

        //------------------------------------------
        // 相対閾値
        //------------------------------------------

        double threshold = maxPeak * relativeThreshold;

        int bestLag = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        //------------------------------------------
        // 評価
        //------------------------------------------

        for (int lag = minLag; lag <= maxLag; lag++) {

            if (!isPeak(acf, lag)) {
                continue;
            }

            if (acf[lag] < threshold) {
                continue;
            }

            double score;

            switch (method) {

                case HEIGHT:
                    score = acf[lag];
                    break;

                case PROMINENCE:
                    score = prominence(acf, lag);
                    break;

                default:
                    score = acf[lag];
            }

            if (score > bestScore) {

                bestScore = score;
                bestLag = lag;
            }
        }

        return bestLag;
    }

    /**
     * 局所最大判定
     */
    private static boolean isPeak(
            double[] acf,
            int index) {

        return acf[index] > acf[index - 1]
                && acf[index] >= acf[index + 1];
    }

    /**
     * Peak Prominence
     */
    private static double prominence(
            double[] acf,
            int peak) {

        //--------------------------------------
        // 左谷
        //--------------------------------------

        double leftMin = acf[peak];

        for (int i = peak - 1; i >= 0; i--) {

            leftMin = Math.min(leftMin, acf[i]);

            if (acf[i] > acf[peak]) {
                break;
            }
        }

        //--------------------------------------
        // 右谷
        //--------------------------------------

        double rightMin = acf[peak];

        for (int i = peak + 1; i < acf.length; i++) {

            rightMin = Math.min(rightMin, acf[i]);

            if (acf[i] > acf[peak]) {
                break;
            }
        }

        return acf[peak] - Math.max(leftMin, rightMin);
    }

}
