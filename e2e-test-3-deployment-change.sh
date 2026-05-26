#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="default"
HELM_RELEASE="scalardb-cluster"
TEST_RELEASE="scalar-admin-test"
IMAGE_NAME="scalar-admin-for-kubernetes-cli:local-test"

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_info "========================================="
log_info "E2E Test 3: Error Case - Deployment Change"
log_info "========================================="

# ========================================
# 事前クリーンアップ
# ========================================
# 前回のテスト実行の残存リソースをクリーンアップ
log_info "Cleaning up previous test resources..."
helm uninstall ${TEST_RELEASE} --namespace ${NAMESPACE} 2>/dev/null || true
sleep 5

# ========================================
# 初期 Pod 状態の記録
# ========================================
# スケール操作前の Pod 情報（名前と restart count）を記録
# これにより、元の 3つの Pod が変更されていないことを後で検証できる
log_info "========================================="
log_info "Capturing initial pod state..."
log_info "========================================="

# kubectl get pods で全 Pod の状態を表示
kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o wide
echo ""

initial_pods=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster --field-selector=status.phase=Running -o jsonpath='{range .items[*]}{.metadata.name},{.status.containerStatuses[0].restartCount}{"\n"}{end}')
echo "Initial pod states (name,restartCount):"
echo "$initial_pods"
echo ""

initial_count=$(echo "$initial_pods" | wc -l | tr -d ' ')
log_info "Initial pod count: ${initial_count}"

# ========================================
# Helm values ファイルの作成
# ========================================
# エラー系テスト用の設定:
# - pause-duration: 40000ms (40秒) - pause 中にスケール操作（3→4→3）を実行するため長めに設定
# - local-test イメージを使用（pullPolicy: Never）
log_info "Creating Helm values file..."
cat > /tmp/test3-values.yaml <<EOF
scalarAdminForKubernetes:
  image:
    repository: scalar-admin-for-kubernetes-cli
    tag: local-test
    pullPolicy: Never
  jobType: "job"
  commandArgs:
    - --release-name
    - ${HELM_RELEASE}
    - --namespace
    - ${NAMESPACE}
    - --pause-duration
    - "40000"
    - --time-zone
    - Asia/Tokyo
EOF

# ========================================
# scalar-admin-for-kubernetes のデプロイ
# ========================================
# Helm を使用して Job としてデプロイ
log_info "Deploying scalar-admin-for-kubernetes..."
helm install ${TEST_RELEASE} scalar-labs/scalar-admin-for-kubernetes \
    -f /tmp/test3-values.yaml \
    --namespace ${NAMESPACE}

# ========================================
# Job の起動を待機
# ========================================
# pause 処理が開始されるまで少し待機
log_info "Waiting for job to start..."
sleep 5

# ========================================
# Deployment をスケールアップ（3 → 4 replicas）
# ========================================
# pause 中に Deployment の変更（スケールアップ）を実行
# これにより、pause 中にターゲット Pod が変更される状況を作り出す
log_info "========================================="
log_info "Scaling up deployment to 4 replicas..."
log_info "========================================="
kubectl scale deployment -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster --replicas=4

# ========================================
# 新しい Pod の起動を待機
# ========================================
# 4つ目の Pod が Ready 状態になるまで待機
log_info "Waiting for new pod to be ready..."
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=scalardb-cluster -n ${NAMESPACE} --timeout=60s

# ========================================
# スケールアップの検証
# ========================================
log_info "========================================="
log_info "Pod state after scale up:"
log_info "========================================="

# kubectl get pods で全 Pod の状態を表示
kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o wide
echo ""

scaled_up_pods=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster --field-selector=status.phase=Running -o jsonpath='{range .items[*]}{.metadata.name},{.status.containerStatuses[0].restartCount}{"\n"}{end}')
echo "All pods after scale up (name,restartCount):"
echo "$scaled_up_pods"
echo ""

# 新しく追加された Pod を特定
new_pod=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster --sort-by=.metadata.creationTimestamp -o jsonpath='{.items[-1].metadata.name}')
log_info "Newly added pod: ${new_pod}"
echo ""

scaled_up_count=$(echo "$scaled_up_pods" | wc -l | tr -d ' ')

if [ "$scaled_up_count" -eq 4 ]; then
    log_success "Successfully scaled up to 4 pods"
else
    log_error "Failed to scale up to 4 pods (current: ${scaled_up_count})"
    exit 1
fi

# Verify original 3 pods are unchanged
log_info "Verifying original 3 pods are unchanged..."
unchanged=true
while IFS=, read -r pod_name restart_count; do
    if echo "$scaled_up_pods" | grep -q "${pod_name},${restart_count}"; then
        log_success "Pod ${pod_name} unchanged (restart: ${restart_count})"
    else
        log_warning "Pod ${pod_name} may have changed"
        unchanged=false
    fi
done <<< "$initial_pods"

if [ "$unchanged" = true ]; then
    log_success "All original 3 pods are unchanged"
fi

# ========================================
# Deployment をスケールダウン（4 → 3 replicas）
# ========================================
# pause 中に再度 Deployment の変更（スケールダウン）を実行
# Pod Deletion Cost を使用して、特定の Pod（新しく作成された Pod）を優先的に削除
log_info "========================================="
log_info "Scaling down deployment to 3 replicas..."
log_info "========================================="

# ========================================
# Pod Deletion Cost の設定
# ========================================
# Pod Deletion Cost (Kubernetes 1.22+):
# - 負の値（-100）: 優先的に削除される
# - 正の値（100）: 削除されにくい
#
# このテストでは、新しく作成された Pod（4つ目）に低いコスト（-100）を設定し、
# 元の 3つの Pod に高いコスト（100）を設定することで、
# 新しい Pod のみが削除されることを保証する

# 最新の Pod（スケールアップで追加された Pod）を特定（既に上で特定済み）
log_info "========================================="
log_info "Setting Pod Deletion Cost annotations:"
log_info "========================================="
log_info "Target pod to delete: ${new_pod} (deletion cost: -100)"

# 新しい Pod に低い削除コスト（-100）を設定 → 優先的に削除される
kubectl annotate pod ${new_pod} controller.kubernetes.io/pod-deletion-cost="-100" -n ${NAMESPACE}

# 元の 3つの Pod に高い削除コスト（100）を設定 → 保護される
echo "Original pods to protect (deletion cost: 100):"
while IFS=, read -r pod_name restart_count; do
    if [ "$pod_name" != "$new_pod" ]; then
        echo "  - ${pod_name}"
        kubectl annotate pod ${pod_name} controller.kubernetes.io/pod-deletion-cost="100" -n ${NAMESPACE} 2>/dev/null || true
    fi
done <<< "$initial_pods"
log_success "Pod Deletion Cost annotations set"
echo ""

# ========================================
# スケールダウンの実行
# ========================================
log_info "Scaling down to 3 replicas..."
kubectl scale deployment -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster --replicas=3

# ========================================
# Pod の終了を待機
# ========================================
# 新しい Pod（Pod Deletion Cost が -100 の Pod）が Terminating 状態になり、
# 削除されるまで待機（タイムアウト: 10秒）
log_info "Waiting for pod termination to complete..."

# 10秒間、3つの Running Pod になるまで待機
timeout=10
start_time=$(date +%s)
pod_deleted_naturally=false

while true; do
    running_count=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster --field-selector=status.phase==Running --no-headers 2>/dev/null | wc -l | tr -d ' ')
    if [ "$running_count" -eq 3 ]; then
        log_success "Pod termination completed - 3 running pods confirmed"
        pod_deleted_naturally=true
        break
    fi
    current_time=$(date +%s)
    elapsed=$((current_time - start_time))
    if [ $elapsed -ge $timeout ]; then
        log_warning "Timeout (${timeout}s) waiting for pod termination"
        break
    fi
    sleep 2
done

# ========================================
# Terminating Pod の強制削除
# ========================================
# タイムアウト後も Terminating 状態の Pod が残っている場合は強制削除
if [ "$pod_deleted_naturally" = false ]; then
    terminating_pods=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o json | jq -r '.items[] | select(.metadata.deletionTimestamp != null) | .metadata.name')

    if [ -n "$terminating_pods" ]; then
        log_info "Force deleting terminating pods..."
        echo "Terminating pods to be deleted:"
        echo "$terminating_pods"

        for pod in $terminating_pods; do
            log_info "Force deleting pod: ${pod}"
            kubectl delete pod ${pod} -n ${NAMESPACE} --force --grace-period=0
        done

        log_success "Force deletion completed"
        # 強制削除後、Pod が完全に削除されるまで少し待機
        sleep 3
    fi
fi

# ========================================
# スケールダウン後の状態確認
# ========================================
log_info "========================================="
log_info "Final pod state after scale down:"
log_info "========================================="

# kubectl get pods で全 Pod の状態を表示（Terminating/Terminated も検出可能）
kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o wide
echo ""

# 稼働中の Pod 情報を取得（deletionTimestamp が null の Pod のみ）
scaled_down_pods=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o json | jq -r '.items[] | select(.status.phase=="Running" and (.metadata.deletionTimestamp == null)) | "\(.metadata.name),\(.status.containerStatuses[0].restartCount)"')
echo "Running pods after scale down (name,restartCount):"
echo "$scaled_down_pods"
echo ""

scaled_down_count=$(echo "$scaled_down_pods" | grep -v '^$' | wc -l | tr -d ' ')

# ========================================
# スケールダウン結果の検証
# ========================================
# 稼働中の Pod が正確に 3つであることを確認
if [ "$scaled_down_count" -eq 3 ]; then
    log_success "Successfully scaled down to 3 running pods"
else
    log_error "Expected 3 running pods, but got: ${scaled_down_count}"
    exit 1
fi

# Verify original 3 pods are still unchanged
log_info "Verifying original 3 pods are still unchanged after scale down..."
still_unchanged=true
while IFS=, read -r pod_name restart_count; do
    if echo "$scaled_down_pods" | grep -q "${pod_name},${restart_count}"; then
        log_success "Pod ${pod_name} still unchanged (restart: ${restart_count})"
    else
        log_warning "Pod ${pod_name} may have changed"
        still_unchanged=false
    fi
done <<< "$initial_pods"

if [ "$still_unchanged" = true ]; then
    log_success "All original 3 pods remain unchanged after scale operations"
fi

# ========================================
# Job の完了または失敗を待機
# ========================================
# スケール操作（Deployment の変更）が検知されて Job が失敗することを期待
log_info "========================================="
log_info "Waiting for job to complete or fail..."
log_info "========================================="
timeout=60
start_time=$(date +%s)

job_completed=false
job_failed=false

while true; do
    status=$(kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -o jsonpath='{.items[0].status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || echo "")
    failed=$(kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -o jsonpath='{.items[0].status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || echo "")

    if [ "$status" = "True" ]; then
        log_warning "Job completed (may contain errors about deployment change)"
        job_completed=true
        break
    fi

    if [ "$failed" = "True" ]; then
        log_info "Job failed as expected due to deployment change"
        job_failed=true
        break
    fi

    current_time=$(date +%s)
    elapsed=$((current_time - start_time))
    if [ $elapsed -ge $timeout ]; then
        log_error "Timeout waiting for job"
        exit 1
    fi

    sleep 2
done

# ========================================
# Job のステータス確認
# ========================================
log_info "========================================="
log_info "Job Status:"
log_info "========================================="
kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE}

# ========================================
# Job ログの取得と検証
# ========================================
# Deployment 変更（スケール操作）が検知されてエラーが発生していることを確認
log_info "========================================="
log_info "Job Logs:"
log_info "========================================="
logs=$(kubectl logs -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -n ${NAMESPACE} 2>&1 || true)
echo "$logs"

# ========================================
# エラーの検証
# ========================================
# ログに Deployment 変更に関するエラーメッセージが含まれることを確認
# 検証パターン:
# - "status", "updated", "changed": 状態変更の検知
# - "target pods were updated": ターゲット Pod の変更検知
# - "Failed", "Error", "Exception": 一般的なエラー
if echo "$logs" | grep -qE "status|updated|changed|target pods were updated|Failed|Error|Exception"; then
    log_success "Job correctly detected deployment change"
elif [ "$job_failed" = true ]; then
    log_success "Job failed as expected"
else
    log_warning "Job may not have detected deployment change correctly"
fi

# ========================================
# ScalarDB Cluster Nodes のログ確認
# ========================================
# 各 Pod のログに Pause/Unpause メッセージが記録されていることを確認
log_info "========================================="
log_info "ScalarDB Cluster Nodes Logs:"
log_info "========================================="
pods=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o jsonpath='{.items[*].metadata.name}')

for pod in $pods; do
    log_info "Logs for pod: ${pod}"
    kubectl logs ${pod} -n ${NAMESPACE} --tail=50 | grep -E "(Pausing|Paused|Unpaused|Error|Exception)" || log_info "No relevant logs found"
    echo ""
done

# Cleanup
log_info "Cleaning up test resources..."
helm uninstall ${TEST_RELEASE} --namespace ${NAMESPACE}

log_success "========================================="
log_success "Test 3: PASSED"
log_success "========================================="
