/**
 * PeriodRefiner.java
 *
 * 倍周期補正
 */
public final class PeriodRefiner {

    /**
     * 半周期判定時の許容率
     *
     * 例：
     * 30の95%以上なら15を採用
     */
    public static final double DEFAULT_HALF_RATIO = 0.95;

    /**
     * デフォルトの約数チェック上限。
     * 2の場合は従来通り半周期のみをチェックする（後方互換）。
     */
    public static final int DEFAULT_MAX_DIVISOR = 2;

    private PeriodRefiner() {
    }

    /**
     * 倍周期を補正する（従来シグネチャ、後方互換用）。
     * maxDivisor = 2（半周期のみ）で動作する。
     *
     * @param acf 自己相関
     * @param period 候補周期
     * @param halfRatio 半周期採用比率
     * @return 補正後周期
     */
    public static int refine(
            double[] acf,
            int period,
            double halfRatio) {

        return refine(acf, period, halfRatio, DEFAULT_MAX_DIVISOR);
    }

    /**
     * 倍周期を補正する。
     *
     * period/2, period/3, ... period/maxDivisor の順に確認し、
     * 最初に条件を満たした（＝最も大きい）約数を採用する。
     *
     * 各チェックはO(1)（配列参照のみ）のため、maxDivisorを増やしても
     * 全体の処理時間への影響はごく僅か（O(maxDivisor)）。
     *
     * @param acf 自己相関
     * @param period 候補周期
     * @param halfRatio 採用比率
     * @param maxDivisor 確認する約数の上限（2以上）
     * @return 補正後周期
     */
    public static int refine(
            double[] acf,
            int period,
            double halfRatio,
            int maxDivisor) {

        if (period <= 1) {
            return period;
        }

        if (acf == null || period >= acf.length) {
            return period;
        }

        double peakValue = acf[period];

        // Fix #2: If the peak itself is non-positive, the ratio comparison
        // (candidate >= peakValue * halfRatio) is not meaningful (a negative
        // peakValue times halfRatio < 1 moves the bar toward zero, i.e. the
        // opposite of the intended "stricter" comparison). Skip refinement.
        if (peakValue <= 0) {
            return period;
        }

        if (maxDivisor < 2) {
            maxDivisor = 2;
        }

        int best = period;

        for (int divisor = 2; divisor <= maxDivisor; divisor++) {

            int candidate = period / divisor;

            if (candidate < 1 || candidate >= acf.length) {
                continue;
            }

            if (acf[candidate] >= peakValue * halfRatio) {
                best = candidate;
                break;
            }
        }

        return best;
    }

}
