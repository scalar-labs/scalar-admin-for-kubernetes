# E2E Test Guide for scalar-admin-for-kubernetes

## 概要

このガイドは、scalar-admin-for-kubernetes CLI の E2E テストスクリプトの使用方法を説明します。
3つのテストパターンを用意しており、それぞれ異なるシナリオで動作を検証します。

## 前提条件

- minikube が起動していること
- kubectl が設定されていること
- Helm がインストールされていること
- scalar-admin-for-kubernetes-cli:local-test の Docker イメージが minikube に読み込まれていること
- default namespace に ScalarDB Cluster (3 replicas) がデプロイされていること

### Docker イメージの準備

```bash
# Docker イメージをビルド
./gradlew jibDockerBuild

# minikube に Docker イメージを読み込む
minikube image load scalar-admin-for-kubernetes-cli:local-test
```

## テストスクリプト一覧

### Test 1: Normal Case (`e2e-test-1-normal.sh`)

**目的**: 正常系のテスト - pause/unpause が正常に動作することを確認

**テスト方法**:
1. scalar-admin-for-kubernetes を Helm でデプロイ（pause duration: 3秒）
2. Job が正常に完了するまで待機
3. Job のログを取得し、以下を検証:
   - JSON 形式の出力が含まれること
   - 必須フィールド（namespace, helm_release_name, pause_start_date_time, pause_end_date_time）が存在すること
4. ScalarDB Cluster の各 Pod のログを確認し、Pause/Unpause メッセージが記録されていることを検証

**期待される結果**:
- Job が成功（Complete）
- JSON 出力に全ての必須フィールドが含まれる
- 各 ScalarDB Pod のログに "Pausing"、"Paused"、"Unpaused" メッセージが記録される

**実行方法**:
```bash
bash e2e-test-1-normal.sh
```

---

### Test 2: Error Case - Pod Deletion (`e2e-test-2-pod-deletion.sh`)

**目的**: エラー系のテスト - pause 中に Pod が削除された場合の動作を確認

**テスト方法**:
1. scalar-admin-for-kubernetes を Helm でデプロイ（pause duration: 15秒）
2. Job の起動を待機（5秒）
3. **pause 中に ScalarDB Cluster の 1つの Pod を削除**
4. Pod が再作成されるのを待機
5. Job が失敗することを確認
6. Job のログにエラーメッセージが含まれることを検証

**期待される結果**:
- Job が失敗（Failed）
- Job のログに "Failed"、"Error"、"Exception"、または "target pods were updated" が含まれる
- Pod が再作成され、正常に稼働する

**実行方法**:
```bash
bash e2e-test-2-pod-deletion.sh
```

---

### Test 3: Error Case - Deployment Change (`e2e-test-3-deployment-change.sh`)

**目的**: エラー系のテスト - pause 中に Deployment の変更（スケール操作）が発生した場合の動作を確認

**テスト方法**:
1. 初期状態（3 replicas）の Pod 情報を記録
2. scalar-admin-for-kubernetes を Helm でデプロイ（pause duration: 40秒）
3. Job の起動を待機（5秒）
4. **pause 中に以下の操作を実行**:
   a. Deployment を 4 replicas にスケールアップ
   b. 新しく作成された Pod に低い削除コスト（-100）を設定
   c. 元の 3つの Pod に高い削除コスト（100）を設定
   d. Deployment を 3 replicas にスケールダウン
5. 元の 3つの Pod が変更されていないことを確認
6. Job が失敗することを確認

**Pod Deletion Cost の仕組み**:

Kubernetes 1.22+ では、`controller.kubernetes.io/pod-deletion-cost` アノテーションを使用して、
スケールダウン時にどの Pod を優先的に削除するかを制御できます。

```bash
# 削除したい Pod に低いコスト（優先的に削除される）を設定
kubectl annotate pod <pod-name> controller.kubernetes.io/pod-deletion-cost="-100" -n <namespace>

# 削除したくない Pod に高いコスト（削除されにくい）を設定
kubectl annotate pod <pod-name> controller.kubernetes.io/pod-deletion-cost="100" -n <namespace>

# その後スケールダウン
kubectl scale deployment <deployment-name> --replicas=3 -n <namespace>
```

このテストでは、新しく作成された Pod（4つ目）を優先的に削除するために、
この Pod Deletion Cost 機能を使用しています。

**期待される結果**:
- Job が失敗（Failed）
- Job のログにデプロイメント変更のエラーメッセージが含まれる
- 元の 3つの Pod は変更されていない（restart count が変わらない）
- 新しく作成された Pod のみが削除される

**実行方法**:
```bash
bash e2e-test-3-deployment-change.sh
```

---

## トラブルシューティング

### minikube で Pod の Terminating 状態が長時間続く

minikube 環境では、Pod の削除（Terminating 状態）が 30秒以上かかることがあります。
Test 3 では、この状況を想定して、Terminating 状態の Pod を適切に処理しています。

### Job が期待通りに失敗しない

以下を確認してください:
- ScalarDB Cluster が正常に動作していること
- pause duration が十分に長いこと（Test 2: 15秒、Test 3: 40秒）
- namespace と release name が正しいこと

### Docker イメージが見つからない

以下のコマンドで Docker イメージが minikube に読み込まれているか確認してください:

```bash
minikube image ls | grep scalar-admin-for-kubernetes-cli
```

イメージが見つからない場合は、再度読み込んでください:

```bash
minikube image load scalar-admin-for-kubernetes-cli:local-test
```

## 全テストの実行

3つのテストを順番に実行する場合:

```bash
bash e2e-test-1-normal.sh && \
bash e2e-test-2-pod-deletion.sh && \
bash e2e-test-3-deployment-change.sh
```

## テスト結果の確認

各テストは以下の形式で結果を出力します:

```
[SUCCESS] =========================================
[SUCCESS] Test X: PASSED
[SUCCESS] =========================================
```

エラーが発生した場合は、`[ERROR]` メッセージが表示され、exit code 1 で終了します。

## クリーンアップ

各テストは自動的にクリーンアップを実行しますが、手動でクリーンアップする場合:

```bash
# scalar-admin-test のリリースを削除
helm uninstall scalar-admin-test --namespace default

# ScalarDB Cluster を削除（必要に応じて）
helm uninstall scalardb-cluster --namespace default
```
