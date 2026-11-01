# プロジェクト進捗: IJToolsUsingOpenCV (Code Quality & Bug Fixes)

このファイルはユーザー向けの累積進捗レポートです。`MANAGER_INSTRUCTIONS.md`/`CODER_STATUS.md`と異なり圧縮・上書きせず、Stepごとに追記していきます。

**正本ではありません。** 目的・原則の正本は `DUAL_MODEL_OPERATION.md` の `Project Objective` / `Project Principle`、Coderの唯一の現在契約は `MANAGER_INSTRUCTIONS.md` です。このファイルはそれらの要約であり、多少ずれても構いません。

## 最終目的（要約）

```text
IJToolsUsingOpenCV の各プラグイングループのコードインスペクションを実施し、リソースリークや例外発生バグ、アルゴリズム精度・ドキュメント乖離を解消して品質を向上させる。
```

## 確定方針（要約）

- 各プラグインの既存の主要な動作やパラメータ体系は保護し、不要な仕様変更やリファクタリングは行わない。
- 調査フェーズで発見された大きな設計変更や共通アーキテクチャの変更は残課題として記録し、各Stepでは明確な不具合の修正に集中する。
- 周期検出モジュール（`PeriodDetector` 系）については、出力周期をサブピクセル精度（`double`）に統一し、`sample/PeriodDetector/` とも同期する。

## 全体STEP

| Step | Instruction ID | 内容 | 状態 |
| --- | --- | --- | --- |
| 1 | `I-0001` | Feature Matching 関連4ファイルから廃止済み `AKAZE` / `BRISK` の定数・コメント・マニュアル記述を整理 | 完了 (`Accepted`) |
| 2 | `I-0002` | G2（エッジ・勾配・コーナー系）の不具合修正（CornerHarrisの例外選択肢除外、Cannyのメモリ解放漏れ、Laplacianのメッセージ誤記） | 完了 (`Accepted`) |
| 3 | `I-0003` | 周期検出（`PeriodDetector` 関連5クラス＋プラグイン）の不具合修正・数値安定化および完全サブピクセル出力への統一 | 完了 (`Accepted`) |
| 4 | `I-0004` | G1（フィルタ・平滑化・モルフォロジー系）の不具合修正（Blur/GaussianBlur/BilateralFilterのメモリリーク修正、GaussianBlurのksize=0&sigma=0クラッシュ防止、EqualizeHistのUndo修正、Blurカーネル整数化、マニュアル更新） | 完了 (`Accepted`) |
| 5 | `I-0005` | G3（二値化・領域分割系）の不具合修正（AdaptiveThreshold/DistanceTransformのメモリリーク修正、Watershed/GrabCutのプレビュー破壊・クラッシュ防止と再描画、FloodFill/InteractiveGrabCutの初期クラス名・リソース修正） | 進行中（Jules実行準備） |

## 現在地

Step 5 (`I-0005`) の `MANAGER_INSTRUCTIONS.md` を作成し、作業ブランチ経由で Jules（Coder）に修正を実行させる段階。

## 残課題（Backlog / 今後の検討事項）

- **微分系フィルタ（Sobel, Scharr, Laplacian等）の負値クリッピング・演算精度統一**:
  - 現在の8-bit/16-bit処理では、負の勾配値がOpenCV内部で0に切り捨てられる仕様となっている（ドキュメントに記載あり）。
  - 必要に応じて、`OCV_Laplacian` の16-bit処理のように「内部で一時的に `CV_32F` で受けてからサチュレーション付きで書き戻す」演算アーキテクチャへの統一を検討する。

---

## Step 1: Feature Matching 関連4ファイルの AKAZE / BRISK 記述クリーンアップ (`I-0001`)

### 目的

OpenCV 5.0.0 で削除された `AKAZE` / `BRISK` の未使用定数および古いコメント・マニュアル表記を現行の `ORB` / `SIFT` に統一する。

### 確定方針

- 変更対象は `src/MyFeatureDetector.java`, `src/OCV_FeatDet_1st_SetQuery.java`, `src/OCV_FeatDet_2nd_Match.java`, `manual_jp/OCV_FeatDet_1st_SetQuery.md` の4ファイルのみとする。

### 受入条件

1. `src/MyFeatureDetector.java` から `STR_AKAZE` と `STR_BRISK` が削除され、実行時ロジックは変更されていないこと。
2. `src/OCV_FeatDet_1st_SetQuery.java` と `src/OCV_FeatDet_2nd_Match.java` の Javadoc コメントが `ORB, SIFT` に更新されていること。
3. `manual_jp/OCV_FeatDet_1st_SetQuery.md` の記述が `ORB` / `SIFT` に更新され、4ファイル以外に変更がないこと。

### 完了内容

- **判定**: `Accepted` (`Based On Instruction ID: I-0001`)
- **変更ファイル**:
  - `src/MyFeatureDetector.java`: 未使用定数 `STR_AKAZE`, `STR_BRISK` を削除
  - `src/OCV_FeatDet_1st_SetQuery.java`: Javadoc コメントを `ORB, SIFT` に更新
  - `src/OCV_FeatDet_2nd_Match.java`: Javadoc コメントを `ORB, SIFT` に更新
  - `manual_jp/OCV_FeatDet_1st_SetQuery.md`: アルゴリズム説明を `ORB` / `SIFT` に更新
- **検証結果**: 全3受入条件 `Pass`、`git grep -n -E "AKAZE|BRISK" src/ manual_jp/` で残存箇所 0 件を確認。

---

## Step 2: G2（エッジ・勾配・コーナー系）の不具合修正 (`I-0002`)

### 目的

CornerHarrisでのサポート外境界タイプ選択によるクラッシュの防止、CannyでのMatインスタンス保持によるメモリリーク解消、Laplacianでのダイアログエラーステータス文字列の誤記修正。

### 確定方針

- `src/OCV_CornerHarris.java`: `INT_BORDERTYPE` および `STR_BORDERTYPE` から OpenCV `cornerHarris` でサポートされていない `BORDER_WRAP` と `BORDER_TRANSPARENT` をコメントアウト。
- `src/OCV_Canny.java`: インスタンス変数 `src_mat`, `dst_mat`, `cachedWidth`, `cachedHeight` およびメソッド `allocateMatIfNeeded`, `releaseResources` を削除し、`run()` 内でローカルに生成して `try ... finally` で確実に解放する。ダイアログキャンセル時の不要な `releaseResources()` 呼び出しも削除。
- `src/OCV_Laplacian.java`: line 102 の `IJ.showStatus("'ksize is odd.");` を `IJ.showStatus("'ksize must be odd.'");` に修正。

### 受入条件

1. `src/OCV_CornerHarris.java` の選択肢から `BORDER_WRAP`, `BORDER_TRANSPARENT` が除外され、OpenCV 例外が発生しないこと。
2. `src/OCV_Canny.java` のネイティブ Mat メモリが `run()` 実行ごとにローカル生成され `finally` で確実に解放されること。
3. `src/OCV_Laplacian.java` のステータス文字列誤記（クォート閉じ忘れ等）が修正されていること。
4. プロジェクトが正常にビルドでき、対象外ファイルに変更がないこと。

### 完了内容

- **判定**: `Accepted` (`Based On Instruction ID: I-0002`)
- **変更ファイル**:
  - `src/OCV_CornerHarris.java`
  - `src/OCV_Canny.java`
  - `src/OCV_Laplacian.java`
- **検証**:
  - `ant jar`: BUILD SUCCESSFUL
  - `git diff`: 仕様に合致したクリーンな差分を確認

---

## Step 3: 周期検出（`PeriodDetector` 系）の不具合修正および完全サブピクセル出力への統一 (`I-0003`)

### 目的

`Autocorrelation`, `Detrend`, `PeakDetector`, `PeriodRefiner`, `PeriodDetector`, `PeriodResult`, `WK_RoiMan_PeriodDetector` におけるアルゴリズム上の不具合（倍周期補正での非ピーク採用・奇数周期切り捨て・半ピクセル周期の減衰、線形トレンド除去の桁落ち）を修正し、出力周期をサブピクセル精度（`double`）に完全統一する。また `sample/PeriodDetector/` 配下のファイルも最新状態に同期する。

### 確定方針（案A: 完全サブピクセル統一）

1. **`PeakDetector.java`**:
   - `isPeak(double[] acf, int index)` を `public static` として公開し、範囲外アクセスを安全に防止する（`acf != null && index >= 1 && index < acf.length - 1`）。
   - 3点放物線近似によるサブピクセルピーク位置 `interpolatePeakPosition(double[] acf, int peakIndex)` と、補間後のピーク頂点値 `interpolatePeakValue(double[] acf, int peakIndex)` を `public static` メソッドとして実装する。
2. **`PeriodRefiner.java`**:
   - 整数除算 `period / divisor` による切り捨てと非ピーク位置の誤採用を廃止。
   - 候補周期 `period` のサブピクセル位置 `subpxPeriod = PeakDetector.interpolatePeakPosition(acf, period)` および補間ピーク値 `peakValue = PeakDetector.interpolatePeakValue(acf, period)` を基準に、各 `divisor` について `expectedLag = subpxPeriod / divisor` の最近傍整数 `center = (int) Math.round(expectedLag)` の近傍 `[center - 1, center + 1]` 内で `PeakDetector.isPeak(acf, cand)` を満たす局所ピークを探索する。
   - 近傍の局所ピークの補間頂点値 `PeakDetector.interpolatePeakValue(acf, cand)` が `peakValue * halfRatio` 以上であれば採用する。
3. **`Detrend.java` & `Autocorrelation.java`**:
   - `Detrend.removeLinearTrend`: $x$ 座標を中心化（`x = i - (n - 1) * 0.5`）して最小二乗法を計算し、桁落ち誤差を防ぐ。
   - `Autocorrelation.compute`: 引数チェックとコメントを整理する。
4. **`PeriodResult.java` & `PeriodDetector.java` & `WK_RoiMan_PeriodDetector.java` & `manual_jp/WK_RoiMan_PeriodDetector.md`**:
   - `PeriodResult`: フィールドをサブピクセル周期 `public final double period;` と確信度 `public final double confidence;` に統一する（`integerPeriod` と `subpixelPeriod` の二重出力を廃止し、`period` にサブピクセル値を格納）。
   - `PeriodDetector`: 倍周期補正後のピークに対して `PeakDetector.interpolatePeakPosition` でサブピクセル周期（`[minPeriod, maxPeriod]` にクランプ）を算出し、確信度は `PeakDetector.interpolatePeakValue(acf, period) / acf[0]` で算出して `new PeriodResult(subpixelPeriod, confidence)` を返す。
   - `WK_RoiMan_PeriodDetector`: ResultsTable の出力列を `ROI_Name`, `Period`（サブピクセル値 `result.period`）, `Confidence`（`result.confidence`）, `Profile_Len` に統一する（`Period_Subpx` 列を廃止し `Period` に一本化）。
   - `manual_jp/WK_RoiMan_PeriodDetector.md`: 出力列の説明を `Period`（サブピクセル精度の周期）に更新する。
   - `sample/PeriodDetector/`: `src/` の5クラス＋`PeriodResult.java` と同一内容に同期する。

### 受入条件

1. `PeakDetector` に `isPeak`, `interpolatePeakPosition`, `interpolatePeakValue` が実装されていること。
2. `PeriodRefiner.refine` が `round(subpxPeriod / divisor)` の近傍 `±1` から `PeakDetector.isPeak` を満たす局所ピークのみを探索し、放物線補間後の頂点値で `halfRatio` 判定を行うこと。
3. `Detrend.removeLinearTrend` が中心化された $x$ 座標を用いて計算されていること。
4. `PeriodResult` が `double period` と `double confidence` に統一され、`WK_RoiMan_PeriodDetector` が ResultsTable の `Period` 列にサブピクセル周期を出力すること。
5. `sample/PeriodDetector/` 配下の Java ファイルが `src/` 側と同期されていること。
6. `ant jar` でコンパイルエラーなくビルドが成功し、一時ファイル等が残っていないこと。

### 完了内容

- **判定**: `Accepted` (`Based On Instruction ID: I-0003`)
- **変更ファイル**:
  - `src/PeakDetector.java`
  - `src/PeriodRefiner.java`
  - `src/Detrend.java`
  - `src/PeriodResult.java`
  - `src/PeriodDetector.java`
  - `src/WK_RoiMan_PeriodDetector.java`
  - `manual_jp/WK_RoiMan_PeriodDetector.md`
  - `sample/PeriodDetector/` (全6クラスを `src/` と同期)
- **検証**:
  - `ant clean jar`: BUILD SUCCESSFUL
  - `TestPeriodSubpixel`: 非整数周期（12.5px）、奇数周期の半周期補正（25 -> 12.5px）、非ピーク傾斜除外のすべてがパスすることを確認。

---

## Step 4: G1（フィルタ・平滑化・モルフォロジー系）の不具合修正 (`I-0004`)

### 目的

`OCV_Blur`, `OCV_GaussianBlur`, `OCV_BilateralFilter` のインスタンスキャッシュによるネイティブメモリリークの解消、`OCV_GaussianBlur` における `ksize = 0` かつ `sigma = 0`（または負値）の際の OpenCV 例外クラッシュ防止、`OCV_EqualizeHist` の `NO_CHANGES` フラグによる Undo 破壊の修正、および `OCV_Blur` のカーネルサイズ整数化とマニュアル整合を行う。

### 確定方針

1. **`src/OCV_Blur.java`**:
   - インスタンス変数 `src_mat`, `dst_mat`, `cachedWidth`, `cachedHeight`, `cachedBitDepth` および `allocateMatIfNeeded`, `releaseResources` を廃止し、`run()` 内の各ビット深度処理でローカル `Mat` を生成して `try ... finally` で確実に `release()` する。
   - `ksize_x`, `ksize_y` を `double` から `int`（初期値 `3`）に変更し、`gd.addNumericField("ksize_x", ksize_x, 0);`, `gd.addNumericField("ksize_y", ksize_y, 0);` とする。
2. **`src/OCV_GaussianBlur.java` & `manual_jp/OCV_GaussianBlur.md`**:
   - インスタンス変数 `srcMat`, `dstMat`, `cachedWidth`, `cachedHeight`, `cachedBitDepth` および `allocateMatIfNeeded`, `releaseResources` を廃止し、`run()` 内の各ビット深度処理でローカル `Mat` を生成して `try ... finally` で確実に `release()` する。
   - `dialogItemChanged` にて：
     - `sigma_x < 0 || sigma_y < 0` の場合は `IJ.showStatus("'0 <= sigma_*' is necessary."); return false;` とする。
     - `ksize_x == 0 && sigma_x <= 0` の場合は `IJ.showStatus("When ksize_x is 0, sigma_x must be > 0."); return false;` とする。
     - `ksize_y == 0 && sigma_y <= 0 && sigma_x <= 0` の場合は `IJ.showStatus("When ksize_y is 0, sigma_x or sigma_y must be > 0."); return false;` とする。
   - `manual_jp/OCV_GaussianBlur.md` の注意点を更新し、`ksize` に `0` を指定する場合は `sigma > 0` が必要である旨を記載する。
3. **`src/OCV_BilateralFilter.java`**:
   - インスタンス変数 `src_mat`, `dst_mat`, `cachedWidth`, `cachedHeight`, `cachedBitDepth` および `allocateMatIfNeeded`, `releaseResources` を廃止し、`run()` 内の各ビット深度処理でローカル `Mat` を生成して `try ... finally` で確実に `release()` する。
   - `dialogItemChanged` の `if(diameter >= 0 && diameter < 1)` を `if(diameter == 0)` に整理する。
4. **`src/OCV_EqualizeHist.java` & `manual_jp/OCV_EqualizeHist.md`**:
   - `FLAGS` から `NO_CHANGES` を外し `private static final int FLAGS = DOES_8G;` とする。
   - 不要なフィールド `ImagePlus impSrc;` と `run()` 内の `impSrc.draw();` を削除し、ImageJ 標準の Undo と再描画が機能するようにする。
   - `manual_jp/OCV_EqualizeHist.md` の注意点から「事前に複製（Duplicate）しておいてください」の記述を整理する。

### 受入条件

1. `OCV_Blur`, `OCV_GaussianBlur`, `OCV_BilateralFilter` からインスタンス変数による `Mat` キャッシュが除去され、ローカル変数＋`try-finally` で確実に `release()` されること。
2. `OCV_Blur` の `ksize_x`, `ksize_y` が `int` 型（小数点0桁）に統一され、`OCV_BilateralFilter` の `diameter == 0` チェックが整理されていること。
3. `OCV_GaussianBlur` で `sigma` の負値チェックおよび `ksize = 0` 時の `sigma > 0` チェックが実装され、`manual_jp/OCV_GaussianBlur.md` に反映されていること。
4. `OCV_EqualizeHist` から `NO_CHANGES` と `impSrc.draw()` が除去され、`manual_jp/OCV_EqualizeHist.md` が更新されていること。

### 完了内容

- **判定**: `Accepted` (`Based On Instruction ID: I-0004`)
- **変更ファイル**:
  - `src/OCV_Blur.java`
  - `src/OCV_GaussianBlur.java`
  - `src/OCV_BilateralFilter.java`
  - `src/OCV_EqualizeHist.java`
  - `manual_jp/OCV_GaussianBlur.md`
  - `manual_jp/OCV_EqualizeHist.md`
- **検証**:
  - `ant clean jar`: BUILD SUCCESSFUL
  - 全5受入条件 `Pass` を確認。

---

## Step 5: G3（二値化・領域分割系）の不具合修正 (`I-0005`)

### 目的

`OCV_AdaptiveThreshold` と `OCV_DistanceTransform` のインスタンスキャッシュによるネイティブメモリリークの解消、`OCV_Watershed` と `OCV_GrabCut` におけるプレビュー実行時の副画像（マスク）破壊・クラッシュの防止および再描画対応、ならびに `OCV_FloodFill` と `OCV_InteractiveGrabCut` の `setup()` エラー時の `null` 表示修正・リソース解放を行う。

### 確定方針

1. **`src/OCV_AdaptiveThreshold.java`**:
   - インスタンス変数 `src_mat`, `dst_mat` および `releaseResources()` を廃止し、`run()` 内でローカル変数として生成して `try ... finally` で確実に `release()` する。
2. **`src/OCV_DistanceTransform.java`**:
   - インスタンス変数 `srcMat32f`, `srcMat8u`, `dstMat32f`, `cachedWidth`, `cachedHeight` および `allocateMatIfNeeded`, `releaseResources` を廃止し、`run()` 内でローカル変数として生成して `try ... finally` で確実に `release()` する。
   - ハードコードされた `CV_DIST_L1 = 1`, `CV_DIST_L2 = 2`, `CV_DIST_C = 3` を `Imgproc.DIST_L1`, `Imgproc.DIST_L2`, `Imgproc.DIST_C` に置き換える（マクロ互換性のため選択肢文字列 `STR_DISTANCETYPE` と `STR_DISTANCETRANSFORMMASKS` は変更しない）。
3. **`src/OCV_Watershed.java`**:
   - 副画像 `impMap` を直接書き換える構造のため、`FLAGS` から `KEEP_PREVIEW` を除外し、`showDialog` から `gd.addPreviewCheckbox(pfr);` を削除してプレビューによるマスク画像破壊を防ぐ。
   - `run()` 内で `matMap32f.get(0, 0, arrMap32f);` の直後に `impMap.updateAndDraw();` を呼び出し、`src` ウィンドウがアクティブな場合でもマスクウィンドウの表示が確実に更新されるようにする。
4. **`src/OCV_GrabCut.java`**:
   - 副画像 `impMsk` を直接書き換える構造のため、`FLAGS` から `KEEP_PREVIEW` を除外し、`showDialog` から `gd.addPreviewCheckbox(pfr);` を削除してプレビュー時の2回目実行クラッシュとマスク破壊を防ぐ。
   - `dialogItemChanged` に `if(iter <= 0) { IJ.showStatus("'0 < iterCount' is necessary."); return false; }` を追加する。
   - `setup()` の ROI 取得時に、ROI が画像全体を覆っている場合（`rectJava.x <= 0 && rectJava.y <= 0 && rectJava.width >= imp.getWidth() && rectJava.height >= imp.getHeight()`）は `new Rectangle(1, 1, Math.max(1, imp.getWidth() - 2), Math.max(1, imp.getHeight() - 2))` に縮小して背景モデル構築失敗による OpenCV 例外を防ぐ。
   - `run()` の最後（`enFgd` 処理の後）に `impMsk.updateAndDraw();` を呼び出してマスクウィンドウを再描画する。
5. **`src/OCV_FloodFill.java` & `src/OCV_InteractiveGrabCut.java`**:
   - `OCV_FloodFill.java`: `private String className = "OCV_FloodFill";` で初期化し、`setup()` 内でエラーが発生した際に `null` が表示されないようにする。
   - `OCV_InteractiveGrabCut.java`:
     - `private String titleCmd = "OCV_InteractiveGrabCut";` で初期化する。
     - `showDialog()` の `opacity = (int)gd.getNextNumber();` を `opacity = gd.getNextNumber();` に修正する。
     - `releaseResources()` で `impOv` が非 `null` の場合に `impOv.flush(); impOv = null;` を実行して複製画像のメモリを解放する。

### 受入条件

1. `OCV_AdaptiveThreshold` と `OCV_DistanceTransform` からインスタンスキャッシュ `Mat` が除去され、`run()` 内のローカル変数＋`try-finally` で確実に `release()` されること。
2. `OCV_Watershed` と `OCV_GrabCut` から `KEEP_PREVIEW` および `gd.addPreviewCheckbox(pfr)` が除去され、`run()` で `impMap.updateAndDraw()` / `impMsk.updateAndDraw()` が呼ばれること。
3. `OCV_GrabCut` に `iter <= 0` バリデーションと全画面 ROI クランプ処理が実装されていること。
4. `OCV_FloodFill` と `OCV_InteractiveGrabCut` のクラス名初期値および `opacity` / `impOv` 解放が修正されていること。
5. `CODER_STATUS.md` が `I-0005` 向けに更新され、対象外ファイルに変更がないこと。
