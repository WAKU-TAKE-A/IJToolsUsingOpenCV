# プロジェクト進捗: IJToolsUsingOpenCV (Feature Matching Cleanup)

このファイルはユーザー向けの累積進捗レポートです。`MANAGER_INSTRUCTIONS.md`/`CODER_STATUS.md`と異なり圧縮・上書きせず、Stepごとに追記していきます。

**正本ではありません。** 目的・原則の正本は `DUAL_MODEL_OPERATION.md` の `Project Objective` / `Project Principle`、Coderの唯一の現在契約は `MANAGER_INSTRUCTIONS.md` です。このファイルはそれらの要約であり、多少ずれても構いません。

## 最終目的（要約）

```text
IJToolsUsingOpenCV のコード内定数・Javadoc・日本語マニュアルを OpenCV 5.0.0 の実装（ORB / SIFT）と一致させ、廃止済みの AKAZE / BRISK の残骸を整理する。
```

## 確定方針（要約）

- 実行時ロジックや既存のプラグイン挙動は変更しない（定数・コメント・マニュアルの同期のみ）
- `ReleaseNotes.md` や `README_AI.md` の履歴・ガイド上の言及は残す

## 全体STEP

| Step | Instruction ID | 内容 | 状態 |
| --- | --- | --- | --- |
| 1 | `I-0001` | Feature Matching 関連4ファイルから廃止済み `AKAZE` / `BRISK` の定数・コメント・マニュアル記述を整理 | 進行中（Jules実行準備完了） |

## 現在地

Step 1 (`I-0001`) の `MANAGER_INSTRUCTIONS.md` を作成済み。`Project Objective` / `Project Principle`（Provisional）のユーザー確認後、Jules API で Coder セッションを実行する段階。

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

（Coder 実行待ち）
