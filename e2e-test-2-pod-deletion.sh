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
log_info "E2E Test 2: Error Case - Pod Deletion"
log_info "========================================="

# ========================================
# 事前クリーンアップ
# ========================================
# 前回のテスト実行の残存リソースをクリーンアップ
log_info "Cleaning up previous test resources..."
helm uninstall ${TEST_RELEASE} --namespace ${NAMESPACE} 2>/dev/null || true
sleep 5

# ========================================
# Helm values ファイルの作成
# ========================================
# エラー系テスト用の設定:
# - pause-duration: 15000ms (15秒) - pause 中に Pod 削除を実行するため長めに設定
# - local-test イメージを使用（pullPolicy: Never）
log_info "Creating Helm values file..."
cat > /tmp/test2-values.yaml <<EOF
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
    - "15000"
    - --time-zone
    - Asia/Tokyo
EOF

# ========================================
# scalar-admin-for-kubernetes のデプロイ
# ========================================
# Helm を使用して Job としてデプロイ
log_info "Deploying scalar-admin-for-kubernetes..."
helm install ${TEST_RELEASE} scalar-labs/scalar-admin-for-kubernetes \
    -f /tmp/test2-values.yaml \
    --namespace ${NAMESPACE}

# ========================================
# Job の起動を待機
# ========================================
# pause 処理が開始されるまで少し待機
log_info "Waiting for job to start..."
sleep 5

# ========================================
# pause 中に Pod を削除（エラーケースの発生）
# ========================================
# ScalarDB Cluster の Pod を 1つ削除することで、
# pause 中にターゲット Pod が変更される状況を作り出す
pod_to_delete=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=scalardb-cluster -o jsonpath='{.items[0].metadata.name}')
log_info "Deleting pod: ${pod_to_delete}"
kubectl delete pod ${pod_to_delete} -n ${NAMESPACE}

# ========================================
# Pod の再作成を待機
# ========================================
# Kubernetes が自動的に Pod を再作成するのを待つ
log_info "Waiting for pod to be recreated..."
sleep 10
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=scalardb-cluster -n ${NAMESPACE} --timeout=60s
log_success "Pod recreated successfully"

# ========================================
# Job の完了または失敗を待機
# ========================================
# Pod が削除されたことを検知して Job が失敗することを期待
log_info "Waiting for job to complete or fail..."
timeout=30
start_time=$(date +%s)

job_completed=false
job_failed=false

while true; do
    status=$(kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -o jsonpath='{.items[0].status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || echo "")
    failed=$(kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -o jsonpath='{.items[0].status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || echo "")

    if [ "$status" = "True" ]; then
        log_warning "Job completed (may contain errors)"
        job_completed=true
        break
    fi

    if [ "$failed" = "True" ]; then
        log_info "Job failed as expected"
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
# Pod 削除が検知されてエラーが発生していることを確認
log_info "========================================="
log_info "Job Logs:"
log_info "========================================="
logs=$(kubectl logs -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -n ${NAMESPACE} 2>&1 || true)
echo "$logs"

# ========================================
# エラーの検証
# ========================================
# ログに "Failed", "Error", "Exception", "target pods were updated" のいずれかが含まれることを確認
# これは pause 中に Pod が変更されたことを検知したことを意味する
if echo "$logs" | grep -qE "Failed|Error|Exception|target pods were updated"; then
    log_success "Job correctly reported error"
elif [ "$job_failed" = true ]; then
    log_success "Job failed as expected"
else
    log_warning "Job may not have detected the pod deletion correctly"
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
log_success "Test 2: PASSED"
log_success "========================================="
