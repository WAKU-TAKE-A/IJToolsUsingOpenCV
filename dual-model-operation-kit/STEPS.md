# プロジェクト進捗: IJToolsUsingOpenCV (Code Quality & Bug Fixes)

このファイルはユーザー向けの累積進捗レポートです。`MANAGER_INSTRUCTIONS.md`/`CODER_STATUS.md`と異なり圧縮・上書きせず、Stepごとに追記していきます。

**正本ではありません。** 目的・原則の正本は `DUAL_MODEL_OPERATION.md` の `Project Objective` / `Project Principle`、Coderの唯一の現在契約は `MANAGER_INSTRUCTIONS.md` です。このファイルはそれらの要約であり、多少ずれても構いません。

## 最終目的（要約）

```text
IJToolsUsingOpenCV の各プラグイングループのコードインスペクションを実施し、リソースリークや例外発生バグ、ドキュメント乖離を解消して品質を向上させる。
```

## 確定方針（要約）

- 各プラグインの既存の主要な動作やパラメータ体系は保護し、不要な仕様変更やリファクタリングは行わない。
- 調査フェーズで発見された大きな設計変更や共通アーキテクチャの変更は残課題として記録し、各Stepでは明確な不具合の修正に集中する。

## 全体STEP

| Step | Instruction ID | 内容 | 状態 |
| --- | --- | --- | --- |
| 1 | `I-0001` | Feature Matching 関連4ファイルから廃止済み `AKAZE` / `BRISK` の定数・コメント・マニュアル記述を整理 | 完了 (`Accepted`) |
| 2 | `I-0002` | G2（エッジ・勾配・コーナー系）の不具合修正（CornerHarrisの例外選択肢除外、Cannyのメモリ解放漏れ、Laplacianのメッセージ誤記） | 進行中（Jules実行準備） |

## 現在地

Step 2 (`I-0002`) の `MANAGER_INSTRUCTIONS.md` を作成し、作業ブランチ経由で Jules（Coder）に修正を実行させる段階。

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

コードインスペクションで特定された G2 グループの3つの明確な不具合（CornerHarris の非対応境界による例外、Canny の正常終了時メモリリーク、Laplacian のステータス文言誤記）を修正する。

### 確定方針

- 微分演算の内部型変換（負値クリッピング仕様）には手を加えず、残課題とする。
- `OCV_CornerHarris` の `BORDER_WRAP` / `BORDER_TRANSPARENT` は `OCV_Sobel` と同様にコメントアウトまたは削除する。
- `OCV_Canny` は `run()` 内でローカル `Mat` を確保し `finally` で `release()` する安全なライフサイクルへ統一する。
- `OCV_Laplacian` の偶数入力時のメッセージを `'ksize must be odd.'` に修正する。

### 受入条件

1. `src/OCV_CornerHarris.java`: `INT_BORDERTYPE` および `STR_BORDERTYPE` から `BORDER_WRAP` と `BORDER_TRANSPARENT` が除外され、非対応モードによる例外が発生しないこと。
2. `src/OCV_Canny.java`: `src_mat` / `dst_mat` が `run()` のローカル変数として `try-finally` で確実に `release()` され、インスタンス変数としてのキャッシュ放置によるメモリリークが解消されていること。
3. `src/OCV_Laplacian.java`: 偶数 `ksize` 入力時のステータスメッセージが `'ksize must be odd.'` に修正されていること。
4. 対象の3ファイルおよび `CODER_STATUS.md` 以外に変更がないこと。

### 完了内容

（Coder 実行待ち）
