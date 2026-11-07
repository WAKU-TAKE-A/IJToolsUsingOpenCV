/**
 * PeriodDetector.java
 *
 * 周期検出API
 *
 * エラー契約: 不正な入力・パラメータでは例外を投げず null を返す。
 * 下位層（Detrend / Autocorrelation）が投げる RuntimeException も
 * ここで吸収し、呼び出し側は null チェックだけでよいように統一した。
 */
public final class PeriodDetector {

    public enum DetrendMethod {
        MOVING_AVERAGE,
        LINEAR_REGRESSION
    }

    /** 倍周期補正で確認する約数の上限のデフォルト（period/2, period/3）。 */
    public static final int DEFAULT_MAX_DIVISOR = 3;

    private PeriodDetector() {
    }

    /**
     * 周期検出（MOVING_AVERAGE専用オーバーロード）。
     *
     * Fix #5: windowSizeはMOVING_AVERAGEでしか使われないため、
     * LINEAR_REGRESSION利用者が意味のない引数を渡す必要がないよう分離。
     */
    public static PeriodResult detectPeriod(
            double[] input,
            int windowSize,
            int minPeriod,
            int maxPeriod,
            PeakDetector.PeakMethod peakMethod,
            double relativeThreshold) {

        return detectPeriod(
                input,
                DetrendMethod.MOVING_AVERAGE,
                windowSize,
                minPeriod,
                maxPeriod,
                peakMethod,
                relativeThreshold);
    }

    /**
     * 周期検出（LINEAR_REGRESSION専用オーバーロード）。
     *
     * Fix #5: windowSizeを渡す必要がない。
     */
    public static PeriodResult detectPeriod(
            double[] input,
            int minPeriod,
            int maxPeriod,
            PeakDetector.PeakMethod peakMethod,
            double relativeThreshold) {

        return detectPeriod(
                input,
                DetrendMethod.LINEAR_REGRESSION,
                0,
                minPeriod,
                maxPeriod,
                peakMethod,
                relativeThreshold);
    }

    /**
     * 周期検出（従来のフル引数版）。
     * 拡張パラメータはデフォルト値で委譲する。
     * ※倍周期補正の約数上限は従来の 2（半周期のみ）から
     *   DEFAULT_MAX_DIVISOR (=3) に変わっている。
     */
    public static PeriodResult detectPeriod(
            double[] input,
            DetrendMethod detrendMethod,
            int windowSize,
            int minPeriod,
            int maxPeriod,
            PeakDetector.PeakMethod peakMethod,
            double relativeThreshold) {

        return detectPeriod(
                input,
                detrendMethod,
                windowSize,
                minPeriod,
                maxPeriod,
                peakMethod,
                relativeThreshold,
                DEFAULT_MAX_DIVISOR,
                PeriodRefiner.DEFAULT_HALF_RATIO,
                false);
    }

    /**
     * 周期検出（拡張版フル引数）。
     *
     * @param maxDivisor 倍周期補正で確認する約数の上限（2以上）
     * @param halfRatio  約数候補を採用するピーク値の比率（0〜1）
     * @param smoothAcf  ピーク探索時に ACF を 3 点移動平均で平滑化するか
     *                   （補間・確信度は元の ACF で計算する）
     * @return 検出結果。検出できない・入力が不正な場合は null
     */
    public static PeriodResult detectPeriod(
            double[] input,
            DetrendMethod detrendMethod,
            int windowSize,
            int minPeriod,
            int maxPeriod,
            PeakDetector.PeakMethod peakMethod,
            double relativeThreshold,
            int maxDivisor,
            double halfRatio,
            boolean smoothAcf) {

        if (input == null || input.length < 2) {
            return null;
        }

        if (detrendMethod == null || peakMethod == null
                || !Double.isFinite(relativeThreshold)
                || relativeThreshold < 0 || relativeThreshold > 1
                || !Double.isFinite(halfRatio)
                || halfRatio < 0 || halfRatio > 1) {
            return null;
        }

        if (detrendMethod == DetrendMethod.MOVING_AVERAGE && windowSize <= 0) {
            return null;
        }

        if (maxPeriod > 0 && maxPeriod < Math.max(1, minPeriod)) {
            return null;
        }

        if (maxDivisor < 2) {
            maxDivisor = 2;
        }

        try {

            //----------------------------------------
            // デトレンド
            //----------------------------------------

            double[] signal;

            switch (detrendMethod) {

                case MOVING_AVERAGE:

                    signal = Detrend.removeMovingAverage(
                            input,
                            windowSize);

                    break;

                case LINEAR_REGRESSION:

                    signal = Detrend.removeLinearTrend(
                            input);

                    break;

                default:

                    return null;
            }

            //----------------------------------------
            // 平均除去
            //----------------------------------------

            signal = Detrend.removeMean(signal);

            //----------------------------------------
            // maxPeriod補正
            //----------------------------------------

            if (maxPeriod <= 0) {
                maxPeriod =
                        Autocorrelation.defaultMaxLag(
                                signal.length);
            }

            //----------------------------------------
            // 自己相関
            //----------------------------------------

            minPeriod = Math.max(1, minPeriod);
            maxPeriod = Math.min(maxPeriod, signal.length - 2);
            if (minPeriod > maxPeriod) return null;

            // Include the right neighbor so a peak at maxPeriod can be evaluated.
            double[] acf =
                    Autocorrelation.compute(
                            signal,
                            minPeriod,
                            maxPeriod + 1);

            if (acf.length == 0) {
                return null;
            }
            maxPeriod = Math.min(maxPeriod, acf.length - 2);
            if (minPeriod > maxPeriod) return null;

            //----------------------------------------
            // ピーク検出
            // （平滑化は探索専用のコピーにのみ適用する）
            //----------------------------------------

            double[] searchAcf = smoothAcf ? smooth3(acf) : acf;

            int period =
                    PeakDetector.findPeak(
                            searchAcf,
                            minPeriod,
                            maxPeriod,
                            peakMethod,
                            relativeThreshold);

            if (period < 0) {
                return null;
            }

            //----------------------------------------
            // 倍周期補正（元の ACF で評価する）
            //----------------------------------------

            int refined = PeriodRefiner.refine(
                    acf, period, halfRatio, maxDivisor);
            if (refined >= minPeriod && refined <= maxPeriod) {
                period = refined;
            }

            //----------------------------------------
            // サブピクセル精度の推定
            //----------------------------------------
            double subpixelPeriod = PeakDetector.interpolatePeakPosition(acf, period);
            subpixelPeriod = Math.max(minPeriod, Math.min(maxPeriod, subpixelPeriod));

            //----------------------------------------
            // 確信度（正規化自己相関値）の計算
            //----------------------------------------
            double confidence = 0.0;
            if (acf.length > 0 && acf[0] != 0.0) {
                confidence = PeakDetector.interpolatePeakValue(acf, period) / acf[0];
            }
            // 正規化の統一により理論上は [0, 1] だが、数値誤差への保険として丸める。
            confidence = Math.max(0.0, Math.min(1.0, confidence));

            return new PeriodResult(subpixelPeriod, confidence);

        } catch (RuntimeException e) {
            // 下位層の例外をエラー契約（null）に統一する。
            return null;
        }
    }

    /** 3点移動平均による平滑化。端点は元の値を保持する。 */
    private static double[] smooth3(double[] acf) {
        double[] out = acf.clone();
        for (int i = 1; i < acf.length - 1; i++) {
            out[i] = (acf[i - 1] + acf[i] + acf[i + 1]) / 3.0;
        }
        return out;
    }

}
