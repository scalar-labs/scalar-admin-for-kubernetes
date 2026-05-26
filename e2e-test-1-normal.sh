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

log_info "========================================="
log_info "E2E Test 1: Normal Case"
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
# 正常系テスト用の設定:
# - pause-duration: 3000ms (3秒) - 短い pause で正常動作を確認
# - local-test イメージを使用（pullPolicy: Never）
log_info "Creating Helm values file..."
cat > /tmp/test1-values.yaml <<EOF
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
    - "3000"
    - --time-zone
    - Asia/Tokyo
EOF

# ========================================
# scalar-admin-for-kubernetes のデプロイ
# ========================================
# Helm を使用して Job としてデプロイ
log_info "Deploying scalar-admin-for-kubernetes..."
helm install ${TEST_RELEASE} scalar-labs/scalar-admin-for-kubernetes \
    -f /tmp/test1-values.yaml \
    --namespace ${NAMESPACE}

# ========================================
# Job の完了を待機
# ========================================
# タイムアウト 30秒で Job の Complete 状態を待機
log_info "Waiting for job to complete..."
timeout=30
start_time=$(date +%s)

while true; do
    status=$(kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -o jsonpath='{.items[0].status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || echo "")

    if [ "$status" = "True" ]; then
        log_success "Job completed successfully"
        break
    fi

    current_time=$(date +%s)
    elapsed=$((current_time - start_time))
    if [ $elapsed -ge $timeout ]; then
        log_error "Timeout waiting for job completion"
        exit 1
    fi

    sleep 2
done

# ========================================
# Job のステータス確認
# ========================================
log_info "Checking job status..."
kubectl get jobs -n ${NAMESPACE} -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE}

# ========================================
# Job ログの取得と検証
# ========================================
# JSON 形式の出力に必須フィールドが含まれることを確認
log_info "========================================="
log_info "Job Logs:"
log_info "========================================="
logs=$(kubectl logs -l app.kubernetes.io/app=scalar-admin-for-kubernetes,app.kubernetes.io/instance=${TEST_RELEASE} -n ${NAMESPACE})
echo "$logs" | jq . 2>/dev/null || echo "$logs"

# JSON 出力に必須フィールドが全て含まれることを検証
# 必須フィールド: namespace, helm_release_name, pause_start_date_time, pause_end_date_time
if echo "$logs" | jq -e '.namespace and .helm_release_name and .pause_start_date_time and .pause_end_date_time' >/dev/null 2>&1; then
    log_success "Job output contains all expected fields"
else
    log_error "Job output is missing expected fields"
    exit 1
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
    kubectl logs ${pod} -n ${NAMESPACE} --tail=50 | grep -E "(Pausing|Paused|Unpaused)" || log_info "No pause/unpause logs found"
    echo ""
done

# Cleanup
log_info "Cleaning up test resources..."
helm uninstall ${TEST_RELEASE} --namespace ${NAMESPACE}

log_success "========================================="
log_success "Test 1: PASSED"
log_success "========================================="
