# OCV_NetFromOnnx_2nd_Inference（物体検出の実行）

## 1. 概要
`1st_Read` でロード済みのモデルを使用して、開いている画像に対して物体検出を行います。検出結果はROIとして画像上に表示され、ResultsTableに座標情報が出力されます。

---

## 2. GUIの使い方

### score_threshold（信頼度しきい値）
検出として採用する最低の信頼度（Confidence）を設定します。
- 低すぎると誤検出が増える
- 高すぎると検出漏れが増える

### nms_threshold（NMSしきい値）
重複したボックスを統合するための閾値（IoU）を設定します。
- 値が小さいほど重複を厳しく除去
- 値が大きいほど重複を許容

### enable_results_table
チェックを入れると、検出結果を `ResultsTable` に一覧表示します。
- 画像名、ラベル、信頼度、座標（X, Y, Width, Height）を出力

### enable_refresh_data
チェックを入れると、`ResultsTable` をリセットしてから新しい結果を追記します。
- 複数画像を連続処理する場合はOFFにすると結果を蓄積できる

### enable_log
チェックを入れると、処理結果がログウィンドウに出力されます。

---

## 3. 処理フロー

### 3.1. 前処理（Letterbox）
入力画像をモデルが要求する解像度（通常640x640）に変換します。

**Letterbox処理の手順：**
1. **アスペクト比保持リサイズ**
   - 元画像 480x640 → ratio=1.0 → 480x640（変化なし）
2. **グレーパディング**
   - 左右に80pxずつグレー(114,114,114)を追加 → 640x640
3. **正規化とチャンネル変換**
   - YOLO: 0-255 → 0-1、BGR → RGB
   - YOLOX: 0-255のまま、BGRのまま

**重要:** この前処理は学習時と同じ処理を再現しています。精度に大きく影響します。

### 3.2. 推論
OpenCV DNNモジュールでONNXモデルを実行します。
```
Input:  [1, 3, 640, 640]
Output: [1, 84, 8400] (YOLO) or [1, 8400, 85] (YOLOX)
```

### 3.3. 後処理

#### YOLO形式（YOLOv8等）
1. **出力の整形**
   - `[1, 84, 8400]` → `[8400, 84]`（各行が1つのボックス）
2. **信頼度計算**
   - 各候補のクラススコア（5列目以降）の最大値を信頼度とする
3. **座標変換**
   - `(cx, cy, w, h)` → `(x1, y1, x2, y2)`
   - パディングオフセット除去: `cx_px = cx - padLeft`
   - スケール: `cx_orig = cx_px / ratio`
4. **しきい値フィルタ**
   - `confidence >= score_threshold` のボックスのみ残す

#### YOLOX形式
1. **出力の整形**
   - `[1, 8400, 85]` → `[8400, 85]`（各行が1つのボックス）
2. **信頼度計算**
   - `confidence = sqrt(objectness) * max(class_scores)`
   - objectnessとクラススコアを掛け合わせることで背景誤検知を抑制
3. **座標デコード**
   - グリッド座標 → ピクセル座標への変換
   - `cx = (tx + grid_x) * stride`
   - `w = exp(tw) * stride`
4. **座標変換**
   - パディングオフセット除去とスケーリング（YOLOと同様）

### 3.4. NMS（Non-Maximum Suppression）

**Per-class NMS** を採用しています：
```
for each class:
    クラスCのボックスだけ抽出
    → NMS処理（IoU > nms_threshold のボックスを削減）
    → 結果をマージ
```

**Agnostic NMSとの違い：**
- Agnostic: 全クラスまとめてNMS → 異なるクラスが重なっていると片方が削除される
- Per-class: クラスごとにNMS → バスの上に人がいても両方検出できる ✅

**NMS実装：**
- OpenCVの`Dnn.NMSBoxes()`は使用していません（per-class非対応のため）
- 独自実装

### 3.5. 結果出力
- **RoiManager**: 検出ボックスをROIとして登録、クラスごとに色分け表示
- **ResultsTable**: 画像名、ラベル、信頼度、座標を一覧表示

---

## 4. 座標変換の詳細

### YOLO Pixel（0~640）の場合
```
モデル出力: 640x640パディング済み画像上のピクセル座標
↓
Step 1: パディングオフセット除去
  cx_px = cx - padLeft
  cy_px = cy - padTop
↓
Step 2: 元画像サイズにスケール
  cx_orig = cx_px / ratio
  cy_orig = cy_px / ratio
↓
Step 3: xywh → xyxy変換
  x1 = cx_orig - w_orig / 2
  y1 = cy_orig - h_orig / 2
```

### YOLO Normalized（0~1）の場合
```
モデル出力: 0~1の正規化座標
↓
Step 1: ピクセル座標化
  cx_px = cx * 640
  cy_px = cy * 640
↓
Step 2以降: YOLO Pixelと同じ
```

### YOLOX Undecodedの場合
```
モデル出力: グリッド形式の未デコード座標 (tx, ty, tw, th)
↓
Step 1: デコード
  cx = (tx + grid_x) * stride
  cy = (ty + grid_y) * stride
  w = exp(tw) * stride
  h = exp(th) * stride
↓
Step 2以降: YOLOと同じ（パディング除去 → スケール → xyxy変換）
```

---

## 5. 注意事項

### 前提条件
- `1st_Read` でモデルが正常にロードされていること
- 画像がRGB（カラー）または8bit Grayscale形式であること

### 座標形式の選択
1st_Readで選択した座標形式と実際のモデルが一致していることが重要です。
- 不一致の場合、座標がおかしくなる（全部左上に集まるなど）

### Letterboxの重要性
- 学習時にletterboxを使用したモデルは、推論時もletterboxが必須
- YOLOv8: Ultralyticsが自動でletterbox適用
- YOLOX: 公式コードがletterbox使用
- アスペクト比を無視した強制リサイズは精度低下の原因になる

### メモリ
- 大きな画像（例: 4000x3000）を処理する場合、ImageJのメモリ設定を増やす必要がある場合があります
- Edit > Options > Memory & Threads

---

## 6. パフォーマンス

### 処理速度
- 640x640画像: 約0.1-0.3秒（CPU）
- NMS: カスタム実装のため、候補数が多い（数千個）と若干遅い
- GPU版OpenCVを使えばさらに高速化可能

### 精度
- Per-class NMSにより、重なり合った異なるクラスの物体も正しく検出

---

## 7. トラブルシューティング

### ケース1: "Model is not loaded"
→ `1st_Read` を先に実行してください

### ケース2: 座標がおかしい
→ `1st_Read` で選択した座標形式が間違っている可能性。別の形式を試す

### ケース3: 検出数が多すぎる（数百個）
→ `score_threshold` を上げる（例: 0.25 → 0.5）

### ケース4: 何も検出されない
→ `score_threshold` を下げる（例: 0.5 → 0.25）

### ケース5: 精度が低い
→ Letterbox版を使用しているか確認。YOLOXは `MyNetFromONNX_YOLOX_Letterbox.java` を推奨

### ケース6: 処理が遅い
→ 画像サイズを小さくするか、NMSの候補数を減らす（score_thresholdを上げる）

---

## 8. 出力データの形式

### RoiManager
各ROIには以下の情報が含まれます：
- 名前: `"person: 0.87"` （クラス名: 信頼度）
- 座標: 元画像上のピクセル座標
- 色: クラスIDに基づいて自動生成

### ResultsTable
| カラム | 説明 |
|--------|------|
| Image | 画像ファイル名 |
| Label | クラス名 |
| Confidence | 信頼度（0-1） |
| X | 左上X座標 |
| Y | 左上Y座標 |
| Width | ボックス幅 |
| Height | ボックス高さ |

---

## 9. 推奨ワークフロー

### 単一画像の処理
1. 画像を開く（File > Open）
2. `2nd_Inference` を実行
3. ROIが画像上に表示される
4. ResultsTableで座標を確認

### 複数画像のバッチ処理
1. `enable_refresh_data` をOFFにする
2. 各画像に対して `2nd_Inference` を実行
3. ResultsTableに全画像の結果が蓄積される
4. ResultsTableを保存（File > Save As）

### 精度調整
1. `score_threshold` を調整して誤検出と検出漏れのバランスを取る
2. `nms_threshold` を調整して重複除去の度合いを調整
3. 座標形式が正しいか再確認
