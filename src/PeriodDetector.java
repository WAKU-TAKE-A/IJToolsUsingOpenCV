/**
 * PeriodDetector.java
 *
 * 周期検出API
 */
public final class PeriodDetector {

    public enum DetrendMethod {
        MOVING_AVERAGE,
        LINEAR_REGRESSION
    }

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
     * 周期検出（フル引数版）。
     */
    public static PeriodResult detectPeriod(
            double[] input,
            DetrendMethod detrendMethod,
            int windowSize,
            int minPeriod,
            int maxPeriod,
            PeakDetector.PeakMethod peakMethod,
            double relativeThreshold) {

        if (input == null || input.length < 2) {
            return null;
        }

        // Fix #3: other invalid-input cases (null input, etc.) return -1
        // instead of throwing, so keep that convention consistent here
        // instead of letting the switch below throw an NPE.
        if (detrendMethod == null) {
            return null;
        }

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

        double[] acf =
                Autocorrelation.compute(
                        signal,
                        minPeriod,
                        maxPeriod);

        if (acf.length == 0) {
            return null;
        }

        //----------------------------------------
        // ピーク検出
        //----------------------------------------

        int period =
                PeakDetector.findPeak(
                        acf,
                        minPeriod,
                        maxPeriod,
                        peakMethod,
                        relativeThreshold);

        if (period < 0) {
            return null;
        }

        //----------------------------------------
        // 倍周期補正
        //----------------------------------------

        period =
                PeriodRefiner.refine(
                        acf,
                        period,
                        PeriodRefiner.DEFAULT_HALF_RATIO);

        //----------------------------------------
        // サブピクセル精度の推定
        //----------------------------------------
        double subpixelPeriod = period;
        if (period > 0 && period < acf.length - 1) {
            double y1 = acf[period - 1];
            double y2 = acf[period];
            double y3 = acf[period + 1];
            
            double denom = 2.0 * (y1 - 2.0 * y2 + y3);
            if (denom != 0) {
                double delta = (y1 - y3) / denom;
                subpixelPeriod = period + delta;
            }
        }
        
        //----------------------------------------
        // 確信度（正規化自己相関値）の計算
        //----------------------------------------
        double confidence = 0.0;
        if (acf.length > 0 && acf[0] != 0.0) {
            confidence = acf[period] / acf[0];
        }

        return new PeriodResult(period, subpixelPeriod, confidence);
    }

}
