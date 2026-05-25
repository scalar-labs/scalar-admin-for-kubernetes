# オニオンアーキテクチャへのリファクタリング計画

## 目的

現在のコードをDDDのオニオンアーキテクチャに基づいて再設計し、以下を実現する：

- ドメインロジックの明確化
- レイヤー間の依存関係の適切な制御
- テスタビリティの向上
- 保守性・拡張性の向上

## 参考リポジトリ

`/Users/masarunomura/Desktop/git/hands-on-ddd-introduction/chapter-21/CatalogService`

## ディレクトリ構造

```
lib/src/main/java/com/scalar/admin/kubernetes/
├── domain/                          # ドメイン層（最内層）
│   ├── model/
│   │   ├── pause/                   # Pause集約
│   │   │   ├── PauseTarget.java    # 集約ルート
│   │   │   ├── Pod.java            # エンティティ
│   │   │   ├── Deployment.java     # エンティティ
│   │   │   └── PauseDuration.java  # 値オブジェクト
│   │   └── shared/                  # 共通値オブジェクト
│   │       ├── Namespace.java
│   │       ├── AdminPort.java
│   │       ├── Product.java        # Enum
│   │       └── PodDiscoveryMode.java # Enum
│   ├── service/                     # ドメインサービス（必要な場合のみ）
│   └── exception/                   # ドメイン例外
│       ├── DomainException.java
│       ├── PauseOperationException.java
│       ├── UnpauseOperationException.java
│       └── PodDiscoveryException.java
│
├── application/                     # アプリケーション層
│   ├── pause/
│   │   ├── PauseApplicationService.java
│   │   ├── dto/
│   │   │   ├── PauseRequest.java
│   │   │   └── PauseResponse.java
│   │   └── usecase/
│   │       ├── PauseByHelmReleaseUseCase.java
│   │       └── PauseByDeploymentUseCase.java
│   └── exception/                   # アプリケーション例外
│
└── infrastructure/                  # インフラ層（最外層）
    ├── kubernetes/
    │   ├── KubernetesClientConfig.java
    │   ├── repository/
    │   │   ├── PodRepositoryImpl.java
    │   │   ├── DeploymentRepositoryImpl.java
    │   │   └── ServiceRepositoryImpl.java
    │   └── adapter/
    │       └── KubernetesApiAdapter.java
    └── admin/
        └── ScalarAdminClientAdapter.java

cli/src/main/java/com/scalar/admin/kubernetes/
└── Cli.java  (アプリケーション層のDTOを使用)
```

## 既存クラスのマッピング

| 既存クラス | 新しい場所 | 変更内容 |
|-----------|-----------|----------|
| `Product` | `domain/model/shared/Product.java` | Enum、そのまま移行 |
| `PodDiscoveryMode` | `domain/model/shared/PodDiscoveryMode.java` | Enum、バリデーションを保持 |
| `PausedDuration` | `domain/model/pause/PauseDuration.java` | Value Object、record classに変更検討 |
| `TargetSnapshot` | `domain/model/pause/PauseTarget.java` | 集約ルート、名称変更 |
| `TargetStatus` | `domain/model/pause/PauseTarget.java` 内部 | TargetStatusをネストクラスに |
| `PauserException` | `domain/exception/DomainException.java` | 基底例外に |
| `PauseFailedException` | `domain/exception/PauseOperationException.java` | 名称変更 |
| `UnpauseFailedException` | `domain/exception/UnpauseOperationException.java` | 名称変更 |
| その他Exception | `domain/exception/` | 適切な名称に変更 |
| `Pauser` | `application/pause/PauseApplicationService.java` | アプリケーションサービスに |
| `TlsPauser` | `infrastructure/admin/ScalarAdminClientAdapter.java` | インフラ層に |
| `TargetSelector` | `infrastructure/kubernetes/` | Repository実装に分割 |

## 作業方針

### 基本原則

1. **既存クラスベースでリファクタリング**
   - 既存の13個のクラスを1つずつリファクタリング
   - 依存関係の少ないクラスから順に実施
   - 各クラスのリファクタリング完了後にコミット

2. **依存関係を考慮した順序**
   - 依存されるクラス（基底クラス、Value Object、Enum）から先に実施
   - 依存するクラス（Service、Application層）は後に実施

3. **テスト駆動**
   - 既存のテストが壊れないことを確認
   - 新しいクラスには新しいテストを追加
   - 各コミット時点でビルド成功を確認

4. **段階的な移行**
   - 旧クラスは新クラスが完成するまで保持
   - 新クラスが完成し、テストが通ったら旧クラスを削除
   - または、新クラスと旧クラスを並行稼働させて徐々に移行

## 既存クラスのリファクタリング順序

以下の順序で既存の13クラスを1つずつリファクタリング：

### Phase 1: 基礎的なクラス（依存なし）

| # | 既存クラス | 新しい場所 | 理由 |
|---|-----------|-----------|------|
| 1 | **Product.java** | `domain/model/shared/Product.java` | Enum、他に依存しない |
| 2 | **PauserException.java** | `domain/exception/PauserException.java` | 基底例外、他の例外の親（**変更**: DomainExceptionへの名称変更は不要と判断、名称はそのまま維持） |

### Phase 2: 例外クラス（PauserExceptionに依存）

| # | 既存クラス | 新しい場所 | 理由 |
|---|-----------|-----------|------|
| 3 | **PauseFailedException.java** | `domain/exception/PauseFailedException.java` | PauserExceptionに依存（**変更**: 名称変更せずそのまま移動） |
| 4 | **UnpauseFailedException.java** | `domain/exception/UnpauseFailedException.java` | PauserExceptionに依存（**変更**: 名称変更せずそのまま移動） |
| 5 | **GetTargetAfterPauseFailedException.java** | `domain/exception/GetTargetAfterPauseFailedException.java` | PauserExceptionに依存（**変更**: 名称変更せずそのまま移動） |
| 6 | **StatusCheckFailedException.java** | `domain/exception/StatusCheckFailedException.java` | PauserExceptionに依存（**変更**: 名称変更せずそのまま移動） |
| 7 | **StatusUnmatchedException.java** | `domain/exception/StatusUnmatchedException.java` | PauserExceptionに依存（**変更**: 名称変更・統合せずそのまま移動） |

### Phase 3: Value ObjectとEntity

| # | 既存クラス | 新しい場所 | 理由 |
|---|-----------|-----------|------|
| 8 | **PausedDuration.java** | `domain/model/pause/PauseDuration.java` | Value Object、依存なし |
| 9 | **TargetSnapshot.java** + **TargetStatus.java** | `domain/model/pause/PauseTarget.java` | 集約ルート、TargetStatusを内包 |

### Phase 4: インフラ層（Repository実装）

| # | 既存クラス | 新しい場所 | 理由 |
|---|-----------|-----------|------|
| 10 | **TargetSelector.java** | `infrastructure/kubernetes/repository/` | Repository実装に分割 |

### Phase 5: アプリケーション層とインフラ層

| # | 既存クラス | 新しい場所 | 理由 |
|---|-----------|-----------|------|
| 11 | **Pauser.java** | `application/pause/PauseApplicationService.java` | アプリケーションサービス |
| 12 | **TlsPauser.java** | `infrastructure/admin/ScalarAdminClientAdapter.java` | インフラ適応層 |

### Phase 6: CLI統合

| # | 既存クラス | 新しい場所 | 理由 |
|---|-----------|-----------|------|
| 13 | **Cli.java** | 既存のまま、内部実装を変更 | 新アーキテクチャを利用 |

## リファクタリング手順（詳細）

### Phase 1: 基礎的なクラス（依存なし）

#### 1-1. Product.javaのリファクタリング
```
refactor Pause(Product): migrate Product to domain layer

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/Product.java
- 新規: lib/src/main/java/com/scalar/admin/kubernetes/domain/model/shared/Product.java
- テストを移行
- 旧クラスは一旦保持（他のクラスが依存しているため）
```

#### 1-2. PauserException.javaのリファクタリング
```
refactor Pause(PauserException): migrate to domain layer

**変更点**: 当初はDomainExceptionへの名称変更を予定していたが、
基底例外クラスを直接throwする設計上の問題を考慮し、名称はそのまま維持。

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/PauserException.java
- 新規: lib/src/main/java/com/scalar/admin/kubernetes/domain/exception/PauserException.java
- パッケージ移動のみ（名称変更なし）
- Javadocを追加
- 旧クラスを削除し、importを更新
```

#### 1-3. 子例外クラスのリファクタリング
```
refactor Pause(exceptions): migrate all exception classes to domain layer

**変更点**: 当初は名称変更・統合を予定していたが、既存の名称を維持。

- PauseFailedException → domain/exception/PauseFailedException.java
- UnpauseFailedException → domain/exception/UnpauseFailedException.java
- GetTargetAfterPauseFailedException → domain/exception/GetTargetAfterPauseFailedException.java
- StatusCheckFailedException → domain/exception/StatusCheckFailedException.java
- StatusUnmatchedException → domain/exception/StatusUnmatchedException.java

- パッケージ移動のみ（名称変更・統合なし）
- Javadocを追加
- 旧クラスを削除し、importを更新
```

#### 1-4. PausedDuration.javaのリファクタリング
```
refactor Pause(PausedDuration): migrate to PauseDuration as record

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/PausedDuration.java
- 新規: lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PauseDuration.java
- record classに変更
- テスト移行
- 旧クラスは一旦保持
```

#### 1-5. TargetSnapshot + TargetStatusのリファクタリング
```
refactor Pause(TargetSnapshot): migrate to PauseTarget aggregate

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/TargetSnapshot.java
- 既存: lib/src/main/java/com/scalar/admin/kubernetes/TargetStatus.java
- 新規: lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PauseTarget.java
- TargetStatusを内部クラスとして統合
- テスト移行
- 旧クラスは一旦保持
```

### Phase 2: インフラ層の構築（TargetSelectorのリファクタリング）

#### 2-1. TargetSelector.javaのリファクタリング（Repository実装への分割）
```
refactor Pause(TargetSelector): split into repository implementations

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/TargetSelector.java
- 新規: 以下に分割
  - domain/repository/PodRepository.java (interface)
  - domain/repository/DeploymentRepository.java (interface)
  - domain/repository/K8sServiceRepository.java (interface)
  - infrastructure/kubernetes/repository/PodRepositoryImpl.java
  - infrastructure/kubernetes/repository/DeploymentRepositoryImpl.java
  - infrastructure/kubernetes/repository/K8sServiceRepositoryImpl.java
  - infrastructure/kubernetes/KubernetesClientConfig.java
- TargetSelectorの各メソッドを適切なRepositoryに分割
- テスト分割・移行
- 旧クラスは一旦保持
```

### Phase 3: アプリケーション層の構築（Pauser.javaのリファクタリング）

#### 3-1. Pauser.javaのリファクタリング
```
refactor Pause(Pauser): migrate to application layer

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/Pauser.java
- 新規: 以下に分割
  - application/pause/dto/PauseRequest.java (record class)
  - application/pause/dto/PauseResponse.java (record class)
  - application/pause/PauseApplicationService.java
- Pauserのロジックをアプリケーションサービスに移行
- テスト移行
- 旧クラスは一旦保持
```

#### 3-2. TlsPauser.javaのリファクタリング
```
refactor Pause(TlsPauser): migrate to infrastructure layer

- 既存: lib/src/main/java/com/scalar/admin/kubernetes/TlsPauser.java
- 新規: infrastructure/admin/ScalarAdminClientAdapter.java
- TLS対応ロジックをインフラ層に移行
- RequestCoordinatorのラッパーとして実装
- テスト移行
- 旧クラスは一旦保持
```

### Phase 4: Presentation層の統合（Cli.javaの更新）

#### 4-1. Cli.javaの更新
```
refactor Pause(Cli): update Cli to use new architecture

- 既存: cli/src/main/java/com/scalar/admin/kubernetes/Cli.java
- 新アーキテクチャのPauseApplicationServiceを使用
- DTOでやり取り
- Result.javaもDTOに合わせて更新
- テスト確認
```

### Phase 5: 旧コードの削除

#### 5-1. 旧コードの削除
```
refactor Pause(cleanup): remove old classes

- 以下の旧クラスを削除:
  - Product.java
  - PauserException.java
  - PauseFailedException.java
  - UnpauseFailedException.java
  - GetTargetAfterPauseFailedException.java
  - StatusCheckFailedException.java
  - StatusUnmatchedException.java
  - PausedDuration.java
  - TargetSnapshot.java
  - TargetStatus.java
  - TargetSelector.java
  - Pauser.java
  - TlsPauser.java
- 全テスト通過確認
- ビルド成功確認
```

## Java 21の機能活用

### Record Class
- Value Objectに積極的に使用
- DTOに使用

```java
// Before
public class Namespace {
    private final String value;
    // constructor, getter, equals, hashCode, toString
}

// After (Java 21)
public record Namespace(String value) {
    public Namespace {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Namespace must not be blank");
        }
    }
}
```

### Pattern Matching (Switch式)
```java
// Before
switch (mode) {
    case HELM_RELEASE:
        return discoverByHelmRelease();
    case DEPLOYMENT:
        return discoverByDeployment();
    default:
        throw new AssertionError();
}

// After (Java 21)
return switch (mode) {
    case HELM_RELEASE -> discoverByHelmRelease();
    case DEPLOYMENT -> discoverByDeployment();
};
```

### Sealed Classes（検討）
```java
public sealed interface PauseException
    permits PauseOperationException, UnpauseOperationException {
}
```

## テスト戦略

1. **既存テストの維持**: リファクタリング中も既存テストが通過し続けること
2. **新しいテストの追加**: 各新クラスにユニットテストを追加
3. **統合テストの更新**: 最後にE2Eテストを新アーキテクチャで動作確認

## 完了条件

- ✅ 全ての既存テストがパス
- ✅ 新しいアーキテクチャのテストが全てパス
- ✅ ビルドが成功
- ✅ 既存のCLI機能が動作
- ✅ 旧コードが削除されている

## 注意事項

- 1クラスずつリファクタリング & コミット
- 各コミットでビルドが成功すること
- テストが可能な単位でテストを追加
- コミットメッセージは統一フォーマット: `refactor Pause(layer): description`
