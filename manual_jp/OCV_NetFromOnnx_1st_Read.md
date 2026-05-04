# OCV_NetFromOnnx_1st_Read（DNNモデルの読み込み）

## 1. 概要
ONNX形式のモデル（YOLOv8 / YOLOX 物体検出、**YOLO 姿勢推定** または YOLO 画像分類）をImageJにロードし、推論の準備を行います。このプラグインは「推論の前段」として機能し、モデル情報を共有メモリに保持します。

---

## 2. GUIの使い方

### model_path
モデルファイルのパスを入力します。
- パスに引用符（`"`または`'`）が含まれている場合、自動的にトリミングされます
- `.txt`ファイルが同じディレクトリにあれば、クラス名リストとして自動ロードされます

### input_width / input_height
モデルが要求する入力解像度を指定します。Netronなどで調べてください。
- 物体検出モデル（YOLOv8等）: 通常 **640x640**
- 画像分類モデル（YOLO26s-cls等）: 通常 **224x224**

### model_format（4択・必須）
モデルの種類と座標形式を選択します。**自動判定はありません。** 以下の4つから選択してください：

| 選択肢 | 説明 | 典型的なモデル |
|--------|------|---------------|
| **YOLO_Object_Pixel** | 物体検出。座標が0~Nのピクセル値 | YOLOv8標準モデル |
| **YOLO_Object_Normalized** | 物体検出。座標が0~1の正規化値 | カスタムYOLOモデル |
| **YOLO_Class** | 画像分類。Top-1クラスを出力 | YOLO26s-cls等 |
| **YOLO_Pose** | 姿勢推定。17点のキーポイントを検出 | YOLOv8-pose等 |
| **YOLOX_Object_Undecoded** | 物体検出。グリッド形式の未デコード座標 | YOLOX標準モデル |

**選択に迷った場合（物体検出）：**
1. まず **YOLO_Object_Pixel** を試す（YOLOv8の標準）
2. うまくいかなければ **YOLO_Object_Normalized** を試す
3. YOLOXモデルの場合は **YOLOX_Object_Undecoded**

### enable_log
チェックを入れると、モデルの読み込み結果がログウィンドウに出力されます（推奨）。

---

## 3. ログ出力の確認方法
`enable_log` を有効にして実行すると、ImageJの `Log` ウィンドウ（Window > Log）に以下の情報が表示されます。

### 物体検出モデルの場合（YOLO_Object_Pixel / Normalized / YOLOX_Object_Undecoded）
```
Input blob shape: [1, 3, 640, 640]
Output shape: [1, 84, 8400]
Number of classes: 80
Has objectness: false
Model Load Complete:
  Format: YOLO_Object_Pixel
  Letterbox Preprocessing: ENABLED
  Custom NMS: ENABLED
```

### 画像分類モデルの場合（YOLO_Class）
```
Input blob shape: [1, 3, 224, 224]
Output shape: [1, 1000]
Number of classes: 1000
Has objectness: false
Model Load Complete:
  Format: YOLO_Class
```
- LetterboxおよびNMSのログは分類モデルでは表示されません

---

## 4. モデル読み込みの内部処理

### 前処理の違い

| 項目 | YOLO物体検出 / Pose | YOLOX物体検出 | YOLO分類 |
|------|------------|--------------|---------|
| 正規化 | 0-1 | なし(0-255) | 0-1 |
| チャンネル順 | RGB | BGR | RGB |
| リサイズ方法 | Letterbox | Letterbox | 単純リサイズ |

**Letterbox処理（物体検出のみ）:**
- アスペクト比を保ったままリサイズ
- 余白をグレー(114,114,114)でパディング
- 学習時と同じ前処理を再現

**単純リサイズ（分類のみ）:**
- 入力解像度に直接リサイズ（アスペクト比非保持）
- 分類モデルはレターボックスが不要なため

---

## 5. 注意事項

### モデルファイルの要件
**物体検出モデル:**
- ✅ ONNX形式（.onnx拡張子）
- ✅ 入力: `[1, 3, H, W]` 形式
- ✅ 出力: `[1, C, 8400]` または `[1, 8400, C]` 形式

**画像分類モデル:**
- ✅ ONNX形式（.onnx拡張子）
- ✅ 入力: `[1, 3, H, W]` 形式（通常224x224）
- ✅ 出力: `[1, numClasses]` 形式（2次元）

### クラス名ファイル
モデルと同じディレクトリに `.txt` ファイルを配置すると自動ロードされます：
```
yolov8n.onnx         ← 物体検出モデル
yolov8n.txt          ← クラス名（1行1クラス）

yolo26s-cls.onnx     ← 分類モデル
yolo26s-cls.txt      ← クラス名（1行1クラス、ImageNetなら1000行）
```

### 入力画像
- **RGB画像のみ対応**（8bitグレースケールは非対応）
- グレースケール画像を処理したい場合はImage > Type > RGB Colorで変換してください

### エラーが出る場合
1. **"Model file not found"** → パスを確認
2. **"Network is empty"** → ONNXファイルが破損しているか、OpenCVが対応していない演算子が含まれている
3. **形状エラー** → モデルの入力/出力形状が想定と異なる

---

## 6. 推奨ワークフロー
1. モデルファイルとクラス名ファイルを同じフォルダに配置
2. `1st_Read` でモデルをロード（`enable_log` をON推奨）
3. ログで形状とクラス数を確認
4. `2nd_Inference` で実際の推論を実行

---

## 7. トラブルシューティング

### ケース1: 座標がおかしい（全部左上に集まる）
→ 座標形式の選択ミス。別の形式を試してください。

### ケース2: 検出数が異常に多い（数千個）
→ 座標形式の選択ミス。または score_threshold が低すぎる。

### ケース3: 何も検出されない
→ score_threshold が高すぎる。0.25程度に下げて試してください。

### ケース4: 分類モデルで何も出力されない
→ score_threshold が高すぎる可能性。0.1程度に下げて試してください。
→ input_width / input_height がモデルと一致しているか確認してください（例: 224x224）。

