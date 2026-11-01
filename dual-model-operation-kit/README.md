# Dual Model Operation Kit

このフォルダは、2つのAIロールを使って大きめの作業を安定して進めるための汎用テンプレートです。

目的は、会話ログやコピペに依存せず、ファイルを通じて次の2つを分けることです。

- Manager: 方針、設計の方向性、優先順位、境界、停止条件を決める
- Coder: 指示に従って実装、検証、状態報告を行う

## ファイル構成

```text
dual-model-operation-kit/
  README.md
  DUAL_MODEL_OPERATION.md
  MANAGER_INSTRUCTIONS.md
  CODER_STATUS.md
  STEPS.md
```

## 使い方

1. プロジェクトにこのフォルダをコピーします。
2. ユーザーがプロジェクトの内容、計画書、AIとのやり取りの要点を整理します。
3. `DUAL_MODEL_OPERATION.md` の `Project Objective` と `Project Principle` を確認します。ユーザーが指定していない場合、Manager役のAIが文脈から案を作り、`Provisional` としてユーザーに確認を求めます。
4. Manager役とCoder役の両方に、次の5ファイルを読ませます。

```text
README.md
DUAL_MODEL_OPERATION.md
MANAGER_INSTRUCTIONS.md
CODER_STATUS.md
STEPS.md
```

5. それぞれのAIに、自分の役割がManagerかCoderかを明示します。
6. Manager役のAIが `MANAGER_INSTRUCTIONS.md` を初回作成・更新します。初回作成時、Managerは同時に `STEPS.md` を初期化します。
7. Coder役のAIに `MANAGER_INSTRUCTIONS.md` を読ませます。Coderは `STEPS.md` を文脈理解のためだけに読み、直接編集したり、先のStepを勝手に実装したりしてはいけません。作業の契約は常に現在の `MANAGER_INSTRUCTIONS.md` だけです。
8. Coder役のAIが作業し、`CODER_STATUS.md` を更新します。
9. Manager役のAIに `CODER_STATUS.md` を読ませます。Managerはこのタイミングで `STEPS.md` を更新します。
10. 以後、6〜9を繰り返します。

## サブエージェントモード（任意）

Manager役とCoder役を、同一ランタイムのサブエージェント機能（例: Claude Codeのサブエージェント）で実行できる場合、手順6・7を短縮できます。

- Managerがサブエージェントとして直接Coderを呼び出し、`MANAGER_INSTRUCTIONS.md`をユーザーが手動で受け渡す必要がなくなります。
- ただし`MANAGER_INSTRUCTIONS.md`と`CODER_STATUS.md`への書き込みは省略しません。Instruction ID単位の粒度を維持し、複数の指示をまとめて1回で報告しません。
- Manager役とCoder役に異なるベンダー/モデルを使う場合（例: ManagerはClaude、CoderはGPT）は、従来どおり手動でのファイル受け渡しが必要です。サブエージェントモードは同一ランタイム内で完結する場合のみの短縮です。

## 役割

Manager は「何をするか」「何をしないか」「どこまで変えてよいか」「完了条件」を決める。

Coder は「決められた範囲だけを実装し、指定された検証を行い、結果と問題点を報告する」ことに集中する。

Coder は、範囲外の変更・仕様判断・設計判断が必要になった場合、勝手に進めず停止してManagerへ報告する。

## 適している作業

- 大きなリファクタリング
- 長期的な設計変更
- 複数段階のリリース準備
- ヒューリスティックや判断ロジックが絡む変更
- AIに任せると作業目標がぶれやすいプロジェクト

## 運用上の重要点

- AI向けの全運用ルールとファイル契約は `DUAL_MODEL_OPERATION.md` にあります。
- プロジェクト目標の正本は `DUAL_MODEL_OPERATION.md` の `Project Objective` だけです。
- プロジェクト原則の正本は `DUAL_MODEL_OPERATION.md` の `Project Principle` だけです。
- AIは未指定の目標や原則を提案できますが、ユーザー確認なしに `Confirmed` にしません。
- Coderにとって、現在の `MANAGER_INSTRUCTIONS.md` が唯一の作業契約です。Coderが `STEPS.md` の先のStepを先回りして実装することはありません。
- Managerにとって、現在の `MANAGER_INSTRUCTIONS.md` と `CODER_STATUS.md` が次の判断の入口です。
- 古い情報は短く圧縮し、現在の状態を先頭に置きます。
- ユーザー向けの累積した進捗の要約は `STEPS.md` にあります。これは正本ではなく、多少ずれても構わない要約です。

## 最小ルール

- Managerは方針に迷ったら、ユーザーに声出しします。
- Coderは指示された範囲を越えず、方針判断が必要ならManagerに戻します。
- やり取りがちぐはぐになったら、`DUAL_MODEL_OPERATION.md` を再読させます。
- このキットはAIの挙動を強制する仕組みではなく、2つのロールが誠実に動くことを前提に、やり取りを構造化するプロトコルです。
