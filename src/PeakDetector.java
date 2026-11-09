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
     * ピークを検出する（最小 prominence なし）。
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

        return findPeak(acf, minLag, maxLag, method, relativeThreshold, 0.0);
    }

    /**
     * ピークを検出する。
     *
     * @param acf 自己相関
     * @param minLag 最小周期
     * @param maxLag 最大周期
     * @param method 評価方法
     * @param relativeThreshold 最大ピーク比率(0～1)
     * @param minProminence 候補ピークに要求する prominence の絶対下限。
     *                      相対閾値だけでは「最大ピークの近辺」という理由で
     *                      ノイズの小波も通ってしまうため、谷との差が
     *                      この値未満のピークは周期性なしとして棄却する。
     *                      0 以下で無効（従来動作）。ACF がラグ0で
     *                      正規化されている前提の値（例: 0.10）。
     * @return 周期
     */
    public static int findPeak(
            double[] acf,
            int minLag,
            int maxLag,
            PeakMethod method,
            double relativeThreshold,
            double minProminence) {

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

            // 最小 prominence（絶対下限）: 満たさない候補は棄却する。
            // PROMINENCE 法では score がそのまま prominence なので流用する。
            if (minProminence > 0) {
                double prom = (method == PeakMethod.PROMINENCE)
                        ? score : prominence(acf, lag);
                if (prom < minProminence) {
                    continue;
                }
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
    public static boolean isPeak(
            double[] acf,
            int index) {

        if (acf == null || index < 1 || index >= acf.length - 1) {
            return false;
        }

        return acf[index] > acf[index - 1]
                && acf[index] >= acf[index + 1];
    }

    /**
     * 3点放物線近似によるサブピクセルピーク位置を算出する。
     */
    public static double interpolatePeakPosition(double[] acf, int peakIndex) {
        if (acf == null || peakIndex <= 0 || peakIndex >= acf.length - 1) {
            return peakIndex;
        }
        double y1 = acf[peakIndex - 1];
        double y2 = acf[peakIndex];
        double y3 = acf[peakIndex + 1];
        double denom = 2.0 * (y1 - 2.0 * y2 + y3);
        if (denom < 0) {
            double delta = (y1 - y3) / denom;
            if (Double.isFinite(delta) && Math.abs(delta) <= 0.5) {
                return peakIndex + delta;
            }
        }
        return peakIndex;
    }

    /**
     * 3点放物線近似による補間後のピーク頂点値を算出する。
     */
    public static double interpolatePeakValue(double[] acf, int peakIndex) {
        if (acf == null || peakIndex < 0 || peakIndex >= acf.length) {
            return 0.0;
        }
        if (peakIndex == 0 || peakIndex == acf.length - 1) {
            return acf[peakIndex];
        }
        double y1 = acf[peakIndex - 1];
        double y2 = acf[peakIndex];
        double y3 = acf[peakIndex + 1];
        double denom = 2.0 * (y1 - 2.0 * y2 + y3);
        if (denom < 0) {
            double delta = (y1 - y3) / denom;
            if (Double.isFinite(delta) && Math.abs(delta) <= 0.5) {
                return y2 - 0.25 * (y1 - y3) * delta;
            }
        }
        return y2;
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
