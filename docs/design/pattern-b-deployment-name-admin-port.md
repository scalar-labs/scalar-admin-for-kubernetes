# 仕様書: Pattern B - `--deployment-name` / `--admin-port` オプション追加

## 1. 背景と目的

### 現状の課題

現在の Scalar Admin for Kubernetes は、Helm release name (`--release-name`) を起点に以下の自動検出フローで Pod を発見している:

1. `app.kubernetes.io/instance=<helmReleaseName>` label で Pod を検索
2. `app.kubernetes.io/app` label で Product を判定 (`Product` enum)
3. Envoy Pod を除外
4. label で Deployment を検索
5. label で Service を検索 (`-headless` suffix でフィルタ)
6. Service の port name から admin port を解決

この仕組みには以下の問題がある:

- **Helm Chart への強い依存**: `app.kubernetes.io/instance` や `app.kubernetes.io/app` label の値に依存している
- **Product enum のハードコード**: `SCALARDB_SERVER`, `SCALARDB_CLUSTER`, `SCALARDL_LEDGER`, `SCALARDL_AUDITOR` を定義しており、新しい Product が追加されるたびにコード修正が必要
- **Helm 以外のデプロイに非対応**: Operator やユーザー独自 manifest でデプロイした場合に動作しない

### 目的

`--pod-discovery-mode` オプションを追加し、Pod の検出方法を明示的に選択できるようにする。
新しい `deployment` モードでは Deployment 名と admin port を直接指定し、Helm 依存を完全に排除する。

## 2. 仕様

### 2.1 `--pod-discovery-mode` による設計

Pod の検出方法を `PodDiscoveryMode` enum で明示的に管理する。
各モードには固有の必須オプションがある。

```
--pod-discovery-mode:
  helm-release (デフォルト) ... 現在の方法
    必須: --release-name
  deployment ... 今回追加
    必須: --deployment-name, --admin-port
```

### 2.2 CLI オプション一覧

| オプション | 型 | デフォルト | 説明 |
|-----------|----|-----------|----- |
| `--pod-discovery-mode` | Enum | `helm-release` | Pod の検出方法 |
| `--release-name`, `-r` | String | null | Helm release 名 (`helm-release` モードで必須) |
| `--deployment-name` | String | null | 対象 Deployment の名前 (`deployment` モードで必須) |
| `--admin-port` | Integer | null | Admin port の番号 (`deployment` モードで必須) |

### 2.3 バリデーションルール

各モードは「必須オプション」と「指定してはいけないオプション」の両方を検証する。

| `--pod-discovery-mode` | 必須 | 指定不可 |
|------------------------|------|---------|
| `helm-release` (デフォルト) | `--release-name` | `--deployment-name`, `--admin-port` |
| `deployment` | `--deployment-name`, `--admin-port` | `--release-name` |

エラーメッセージ例:
- `--release-name is required when --pod-discovery-mode is helm-release.`
- `--deployment-name and --admin-port cannot be used when --pod-discovery-mode is helm-release.`
- `--deployment-name and --admin-port are required when --pod-discovery-mode is deployment.`
- `--release-name cannot be used when --pod-discovery-mode is deployment.`

### 2.4 デフォルト値を設定しない理由

- **`--deployment-name`**: Deployment 名は Helm の release 名に依存するため、暗黙的なデフォルト値は設定できない
- **`--admin-port`**: ScalarDB と ScalarDL でデフォルト port が異なるため、どちらを採用するかの問題になる

### 2.5 実行イメージ

```bash
# deployment モード (明示的)
java -jar scalar-admin-for-kubernetes.jar \
  --namespace ns \
  --pod-discovery-mode deployment \
  --deployment-name scalardb-cluster-node \
  --admin-port 60054

# helm-release モード (明示的)
java -jar scalar-admin-for-kubernetes.jar \
  --namespace ns \
  --pod-discovery-mode helm-release \
  --release-name my-release

# helm-release モード (デフォルト、既存互換)
java -jar scalar-admin-for-kubernetes.jar \
  --namespace ns \
  --release-name my-release

# エラー: deployment モードで必須オプション不足
java -jar scalar-admin-for-kubernetes.jar \
  --pod-discovery-mode deployment \
  --deployment-name scalardb-cluster-node
# => Error: --deployment-name and --admin-port are required when --pod-discovery-mode is deployment.
```

## 3. 実装方針

### 3.0 事前リファクタリング: Pauser から TargetSelector の生成責務を分離

#### 現状の問題

現在、`Pauser` のコンストラクタ内で以下を行っている:

1. K8s クライアントの初期化 (`Config.defaultClient()`)
2. `TargetSelector` の生成

```java
// Current Pauser constructor
public Pauser(String namespace, String helmReleaseName) throws PauserException {
    // null checks
    Configuration.setDefaultApiClient(Config.defaultClient());  // K8s client init
    targetSelector = new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);  // TS creation
}
```

`Pauser` の本来の責務は pause/unpause 操作であり、「Pod をどう見つけるか」は知る必要がない。
`TargetSelector` の生成と K8s クライアント初期化は `Pauser` の外で行うべきである。

#### リファクタリング後の設計

```
Cli.call()
  1. 引数の組み合わせ検証 (PodDiscoveryMode.validate())
  2. TargetSelectorFactory で TargetSelector 生成 (K8s 初期化も内部で実施)
  3. Pauser 生成 (TargetSelector を注入)
  4. pause 実行
  5. Result 出力
```

**新規クラス: `TargetSelectorFactory`**

K8s クライアントの初期化と TargetSelector の生成を担当する。
CLI は K8s client の詳細 (CoreV1Api, AppsV1Api 等) を知る必要がない。

```java
class TargetSelectorFactory {

  /**
   * Creates a TargetSelector for HELM_RELEASE mode.
   * Initializes the Kubernetes client internally.
   */
  static TargetSelector fromHelmRelease(String namespace, String helmReleaseName)
      throws PauserException {
    initializeKubernetesClient();
    return new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);
  }

  /**
   * Creates a TargetSelector for DEPLOYMENT mode.
   * Initializes the Kubernetes client internally.
   */
  static TargetSelector fromDeployment(String namespace, String deploymentName, int adminPort)
      throws PauserException {
    initializeKubernetesClient();
    return new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, deploymentName, adminPort);
  }

  private static void initializeKubernetesClient() throws PauserException {
    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new PauserException("Failed to set default Kubernetes client.", e);
    }
  }
}
```

**変更内容:**

- `Pauser`: コンストラクタを `Pauser(TargetSelector targetSelector)` に変更 (DI)
  - K8s クライアント初期化と TargetSelector 生成を削除
  - `namespace`, `helmReleaseName` フィールドを削除
- `TlsPauser`: コンストラクタを `TlsPauser(TargetSelector targetSelector, caRootCert, overrideAuthority)` に変更
- `Cli.call()`: `TargetSelectorFactory` でTargetSelector を生成し、Pauser に注入
- `TargetSelector.select()`: 既存の `select()` を単一の public エントリポイントとして維持し、内部でモードに応じて分岐

**リファクタリング後の Pauser:**

```java
public class Pauser {
    private final TargetSelector targetSelector;

    public Pauser(TargetSelector targetSelector) {
        this.targetSelector = targetSelector;
    }

    // getTarget() simply delegates to targetSelector.select()
    // pause/unpause logic remains unchanged
}
```

**リファクタリング後の TargetSelector:**

```java
class TargetSelector {
    private final PodDiscoveryMode mode;
    // ... fields per mode

    // Constructor for HELM_RELEASE mode (called by TargetSelectorFactory)
    TargetSelector(CoreV1Api coreApi, AppsV1Api appsApi, String namespace, String helmReleaseName) {
        this.mode = PodDiscoveryMode.HELM_RELEASE;
        // ...
    }

    // Constructor for DEPLOYMENT mode (called by TargetSelectorFactory)
    TargetSelector(CoreV1Api coreApi, AppsV1Api appsApi, String namespace, String deploymentName, int adminPort) {
        this.mode = PodDiscoveryMode.DEPLOYMENT;
        // ...
    }

    // Single public entry point
    TargetSnapshot select() throws PauserException {
        switch (mode) {
            case HELM_RELEASE:
                return selectByHelmRelease();
            case DEPLOYMENT:
                return selectByDeploymentName();
            default:
                throw new AssertionError("Unknown PodDiscoveryMode: " + mode);
        }
    }
}
```

**リファクタリング後の Cli.call():**

```java
public Integer call() {
    // 1. Validate CLI options for the selected pod discovery mode.
    try {
        podDiscoveryMode.validate(helmReleaseName, deploymentName, adminPort);
    } catch (IllegalArgumentException e) {
        logger.error(e.getMessage());
        return 1;
    }

    // 2. Create TargetSelector via factory (K8s client init is handled internally).
    TargetSelector targetSelector;
    switch (podDiscoveryMode) {
        case HELM_RELEASE:
            targetSelector = TargetSelectorFactory.fromHelmRelease(namespace, helmReleaseName);
            break;
        case DEPLOYMENT:
            targetSelector = TargetSelectorFactory.fromDeployment(namespace, deploymentName, adminPort);
            break;
    }

    // 3. Create Pauser with injected TargetSelector.
    Pauser pauser = tlsEnabled
        ? new TlsPauser(targetSelector, getCaRootCert(), overrideAuthority)
        : new Pauser(targetSelector);

    // 4. Pause and output result.
    PausedDuration duration = pauser.pause(pauseDuration, maxPauseWaitTime);
    // ...
}
```

**テストへの影響:**

- `PauserTest`: `Config.defaultClient()` の MockedStatic が不要になる。TargetSelector を mock して注入するだけでよい。
- `TargetSelectorTest`: 変更なし (TargetSelector のコンストラクタを直接呼んでいるため)。

### 3.1 `PodDiscoveryMode` enum

**新規ファイル:** `lib/src/main/java/com/scalar/admin/kubernetes/PodDiscoveryMode.java`

```java
public enum PodDiscoveryMode {
  /** Discover target pods by Helm release name using label-based auto-detection. */
  HELM_RELEASE,
  /** Discover target pods by Deployment name using its spec.selector.matchLabels. */
  DEPLOYMENT;

  /**
   * Validates that CLI options are consistent with this mode.
   * Checks for missing required options and rejects options belonging to other modes.
   */
  void validate(
      @Nullable String helmReleaseName,
      @Nullable String deploymentName,
      @Nullable Integer adminPort) throws IllegalArgumentException {
    switch (this) {
      case HELM_RELEASE:
        if (helmReleaseName == null) {
          throw new IllegalArgumentException(
              "--release-name is required when --pod-discovery-mode is helm-release.");
        }
        if (deploymentName != null || adminPort != null) {
          throw new IllegalArgumentException(
              "--deployment-name and --admin-port cannot be used when --pod-discovery-mode is helm-release.");
        }
        break;
      case DEPLOYMENT:
        if (deploymentName == null || adminPort == null) {
          throw new IllegalArgumentException(
              "--deployment-name and --admin-port are required when --pod-discovery-mode is deployment.");
        }
        if (helmReleaseName != null) {
          throw new IllegalArgumentException(
              "--release-name cannot be used when --pod-discovery-mode is deployment.");
        }
        break;
    }
  }
}
```

- `TargetSelector` がモード分岐に使用する
- `Cli.call()` がバリデーションに使用する
- picocli の case-insensitive enum 機能により、CLI では `helm-release` / `deployment` で指定可能
  - picocli は enum 名のハイフンとアンダースコアを自動変換する (`helm-release` → `HELM_RELEASE`)

### 3.2 新フローの処理フロー

```
CLI (--pod-discovery-mode deployment --deployment-name X --admin-port N)
  |
  v
PodDiscoveryMode.DEPLOYMENT.validate(...)
  |
  v
TargetSelectorFactory.fromDeployment(namespace, X, N)
  |  K8s client init + TargetSelector (DEPLOYMENT mode) creation
  v
Pauser(targetSelector)
  |
  v
targetSelector.select() -> selectByDeploymentName()
  |
  +-- 1. appsApi.readNamespacedDeployment(deploymentName, namespace)
  |      -> V1Deployment を取得
  |
  +-- 2. deployment.getSpec().getSelector().getMatchLabels()
  |      -> Pod 検索用の label selector を構築
  |
  +-- 3. coreApi.listNamespacedPod(namespace, ..., labelSelector)
  |      -> Pod リストを取得
  |
  +-- 4. new TargetSnapshot(pods, deployment, adminPort)
         -> 指定された admin port をそのまま使用
```

**不要になる処理 (DEPLOYMENT モードでは呼ばれない):**
- `findPodsCreatedByHelmRelease()` - Helm label ベースの Pod 検索
- `selectPodsRunScalarProduct()` - Product enum による判定、Envoy 除外
- `findDeploymentCreatedByHelmReleaseForProduct()` - label ベースの Deployment 検索
- `findServiceCreatedByHelmReleaseForProduct()` - Service 検索
- `findAdminPortInService()` - Service からの port 解決

これらは HELM_RELEASE モードで引き続き使用されるため削除はしない。

### 3.3 変更対象ファイルと変更内容

#### 3.3.1 `lib/src/main/java/com/scalar/admin/kubernetes/PodDiscoveryMode.java` (新規)

```java
public enum PodDiscoveryMode {
  /** Helm release name をもとに label ベースで自動検出する。 */
  HELM_RELEASE,
  /** Deployment 名を直接指定し、matchLabels で Pod を検出する。 */
  DEPLOYMENT
}
```

#### 3.3.2 `cli/src/main/java/com/scalar/admin/kubernetes/Cli.java`

**変更内容:**

1. `--pod-discovery-mode` オプション追加 (デフォルト: `helm-release`)
2. `--deployment-name` オプション追加
3. `--admin-port` オプション追加
4. `--release-name` の `required = true` を削除
5. `call()` メソッドにモード別バリデーションとフロー分岐を追加

```java
@Option(
    names = {"--pod-discovery-mode"},
    description =
        "The mode to discover the target pods. Valid values: ${COMPLETION-CANDIDATES}."
            + " `helm-release` by default.",
    defaultValue = "helm-release")
private PodDiscoveryMode podDiscoveryMode;

@Option(
    names = {"--deployment-name"},
    description =
        "The name of the Kubernetes Deployment for the Scalar product."
            + " Required when --pod-discovery-mode is deployment.")
@Nullable
private String deploymentName;

@Option(
    names = {"--admin-port"},
    description =
        "The port number of the admin interface of the Scalar product."
            + " Required when --pod-discovery-mode is deployment.")
@Nullable
private Integer adminPort;
```

`call()` メソッドでのバリデーション + オブジェクト生成:

```java
@Override
public Integer call() {
  // 1. Validate CLI options.
  try {
    podDiscoveryMode.validate(helmReleaseName, deploymentName, adminPort);
  } catch (IllegalArgumentException e) {
    logger.error(e.getMessage());
    return 1;
  }

  // 2. Create TargetSelector via factory.
  TargetSelector targetSelector;
  switch (podDiscoveryMode) {
    case HELM_RELEASE:
      targetSelector = TargetSelectorFactory.fromHelmRelease(namespace, helmReleaseName);
      break;
    case DEPLOYMENT:
      targetSelector = TargetSelectorFactory.fromDeployment(namespace, deploymentName, adminPort);
      break;
  }

  // 3. Create Pauser with injected TargetSelector.
  Pauser pauser = tlsEnabled
      ? new TlsPauser(targetSelector, getCaRootCert(), overrideAuthority)
      : new Pauser(targetSelector);

  // 4. Pause and output result.
  // ...
}
```

#### 3.3.3 `lib/src/main/java/com/scalar/admin/kubernetes/TargetSelectorFactory.java` (新規)

K8s クライアントの初期化と TargetSelector の生成を担当するファクトリクラス。

```java
class TargetSelectorFactory {

  static TargetSelector fromHelmRelease(String namespace, String helmReleaseName)
      throws PauserException {
    initializeKubernetesClient();
    return new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);
  }

  static TargetSelector fromDeployment(String namespace, String deploymentName, int adminPort)
      throws PauserException {
    initializeKubernetesClient();
    return new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, deploymentName, adminPort);
  }

  private static void initializeKubernetesClient() throws PauserException {
    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new PauserException("Failed to set default Kubernetes client.", e);
    }
  }
}
```

#### 3.3.4 `lib/src/main/java/com/scalar/admin/kubernetes/TargetSelector.java`

**変更内容 (リファクタリング + 新機能):**

1. `PodDiscoveryMode` フィールドと DEPLOYMENT 用フィールド・コンストラクタを追加
2. 既存の `select()` を単一 public エントリポイントにし、内部でモード分岐
3. 既存ロジックを `selectByHelmRelease()` private メソッドに移動
4. `selectByDeploymentName()` private メソッドを追加

```java
class TargetSelector {
    private final PodDiscoveryMode mode;
    private final CoreV1Api coreApi;
    private final AppsV1Api appsApi;
    private final String namespace;
    @Nullable private final String helmReleaseName;
    @Nullable private final String deploymentName;
    private final int adminPort;

    /** HELM_RELEASE mode */
    TargetSelector(CoreV1Api coreApi, AppsV1Api appsApi, String namespace, String helmReleaseName) {
        this.mode = PodDiscoveryMode.HELM_RELEASE;
        // ...
    }

    /** DEPLOYMENT mode */
    TargetSelector(CoreV1Api coreApi, AppsV1Api appsApi, String namespace, String deploymentName, int adminPort) {
        this.mode = PodDiscoveryMode.DEPLOYMENT;
        // ...
    }

    TargetSnapshot select() throws PauserException {
        switch (mode) {
            case HELM_RELEASE:  return selectByHelmRelease();
            case DEPLOYMENT:    return selectByDeploymentName();
            default: throw new AssertionError("Unknown PodDiscoveryMode: " + mode);
        }
    }

    private TargetSnapshot selectByHelmRelease() throws PauserException { /* 既存ロジック */ }
    private TargetSnapshot selectByDeploymentName() throws PauserException { /* 新ロジック */ }
}
```

新しい private メソッド:
- `findDeploymentByName()` - `appsApi.readNamespacedDeployment()` で取得
- `findPodsByDeploymentSelector()` - `deployment.getSpec().getSelector().getMatchLabels()` で Pod 検索

#### 3.3.5 `lib/src/main/java/com/scalar/admin/kubernetes/Pauser.java`

**変更内容 (リファクタリング):**

TargetSelector を外部から注入する設計に変更。K8s クライアント初期化を削除。

```java
public class Pauser {
    private final TargetSelector targetSelector;

    public Pauser(TargetSelector targetSelector) {
        if (targetSelector == null) {
            throw new IllegalArgumentException("targetSelector is required");
        }
        this.targetSelector = targetSelector;
    }

    @VisibleForTesting
    TargetSnapshot getTarget() throws PauserException {
        return targetSelector.select();  // Mode dispatch is handled inside TargetSelector
    }

    // pause/unpause logic remains unchanged
}
```

- `namespace`, `helmReleaseName` フィールドを削除
- K8s クライアント初期化 (`Config.defaultClient()`) を削除
- `PodDiscoveryMode` を持たない (Pauser は Pod 検出方法を知る必要がない)

#### 3.3.6 `lib/src/main/java/com/scalar/admin/kubernetes/TlsPauser.java`

**変更内容 (リファクタリング):**

```java
public class TlsPauser extends Pauser {
    public TlsPauser(
            TargetSelector targetSelector,
            @Nullable String caRootCert,
            @Nullable String overrideAuthority) {
        super(targetSelector);
        this.caRootCert = caRootCert;
        this.overrideAuthority = overrideAuthority;
    }
}
```

- モード別の複数コンストラクタは不要 (TargetSelector にモード情報が含まれるため)
- `getRequestCoordinator()` のオーバーライドは変更なし

#### 3.3.7 `cli/src/main/java/com/scalar/admin/kubernetes/Result.java`

**変更内容:**

新フロー用に `deployment_name` フィールドを追加し、`helm_release_name` を nullable に:

```java
@JsonProperty("helm_release_name")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Nullable
public final String helmReleaseName;

@JsonProperty("deployment_name")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Nullable
public final String deploymentName;
```

### 3.4 テスト

#### 3.4.1 `lib/src/test/java/com/scalar/admin/kubernetes/PauserTest.java`

追加するテスト:

- **Constructor (DEPLOYMENT モード)**
  - `constructor_WithDeploymentModeArgs_ReturnPauser` - 正しい引数 → Pauser 生成成功
  - `constructor_WithNullDeploymentName_ShouldThrowIllegalArgumentException`
  - `constructor_WithZeroAdminPort_ShouldThrowIllegalArgumentException`
  - `constructor_WithNegativeAdminPort_ShouldThrowIllegalArgumentException`
- **Pause (DEPLOYMENT モード)**
  - `pause_DeploymentMode_WhenPauseSucceeded_ReturnPausedDuration` - 正常系

既存テストは変更不要 (HELM_RELEASE モードの動作に影響なし)。

#### 3.4.2 `lib/src/test/java/com/scalar/admin/kubernetes/TargetSelectorTest.java`

追加するテスト:

- `selectByDeploymentName_NormalCase_ShouldReturnTargetSnapshot` - 正常系
- `selectByDeploymentName_ReadDeploymentThrowApiException_ShouldThrowPauserException`
- `selectByDeploymentName_DeploymentHasNoPods_ShouldThrowPauserException`
- `selectByDeploymentName_ListPodThrowApiException_ShouldThrowPauserException`

既存テストは変更不要。

## 4. 拡張性

`PodDiscoveryMode` enum を拡張することで、将来新しい検出方法を追加できる:

```java
public enum PodDiscoveryMode {
  HELM_RELEASE,
  DEPLOYMENT,
  // Future examples:
  // STATEFULSET,
  // LABEL_SELECTOR,
}
```

各モードの必須オプション検証は `Cli.call()` の `switch` 文に case を追加するだけでよい。

## 5. 検証方法

### 5.1 自動テスト

```bash
./gradlew test
```

全テスト (HELM_RELEASE + DEPLOYMENT) が pass することを確認。

### 5.2 ビルド確認

```bash
./gradlew build
```

### 5.3 CLI 引数パターン確認

| パターン | 期待結果 |
|---------|---------|
| `--pod-discovery-mode deployment --deployment-name X --admin-port 60054` | DEPLOYMENT モードで実行 |
| `--release-name X` | HELM_RELEASE モード (デフォルト) で実行 |
| `--pod-discovery-mode helm-release --release-name X` | HELM_RELEASE モードで実行 |
| `--pod-discovery-mode deployment --deployment-name X` | エラー: `--admin-port` 不足 |
| `--pod-discovery-mode deployment` | エラー: `--deployment-name` と `--admin-port` 不足 |
| 引数なし | エラー: `--release-name` 不足 |
| `--pod-discovery-mode deployment --deployment-name X --admin-port 60054 --tls` | DEPLOYMENT + TLS |

## 6. 後続対応 (スコープ外)

- Helm Chart 側の bugfix (Envoy を無効にすると admin port が expose されてしまう bug)
  - 本改修の release 後に対応
