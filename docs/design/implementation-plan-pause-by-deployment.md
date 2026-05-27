# Implementation Plan: Pause by Deployment Name Feature

## Overview

This document outlines the implementation plan for adding deployment name-based pause functionality to Scalar Admin for Kubernetes. The implementation is divided into multiple phases to ensure backward compatibility and maintainable code structure.

## Current Architecture (DDD/Onion Architecture)

```
cli/
  └── Cli.java (picocli command line interface)
lib/
  ├── domain/
  │   ├── model/pause/
  │   │   ├── PauseCommand (sealed interface)
  │   │   ├── PauseByHelmReleaseCommand (record)
  │   │   ├── PauseDuration (record)
  │   │   └── PauseTarget (aggregate)
  │   ├── repository/
  │   │   └── PauseTargetRepository (interface)
  │   └── service/
  │       └── PauseService
  ├── application/
  │   ├── PauseApplicationService
  │   └── dto/
  │       └── PauseDurationDto (record)
  ├── presentation/
  │   └── PauseController
  └── infrastructure/
      ├── repository/
      │   └── PauseTargetRepositoryImpl
      └── client/
          └── ScalarAdminClientFactory
```

## Goals

1. **Backward Compatibility**: Existing functionality must work without any changes
2. **Extensibility**: Add `--pod-discovery-mode` to support multiple pod discovery methods
3. **DDD Principles**: Maintain clean separation of concerns across layers
4. **Type Safety**: Use sealed interfaces and switch expressions for exhaustive handling

## Design Decisions

### Why PauseRequest uses String for podDiscoveryMode (not enum)

**Problem**: Should PauseRequest (presentation DTO) directly use the PodDiscoveryMode enum (domain model)?

**Decision**: Use `String` in PauseRequest, not the enum.

**Reasoning**:
- **DDD/Onion Architecture**: Domain models should not be exposed in external interfaces (DTOs)
- **Layer separation**:
  - PauseRequest is in the **presentation layer** (DTO)
  - PodDiscoveryMode is in the **domain layer** (domain model)
  - Mixing these violates layer boundaries
- **Conversion**: PauseController (presentation layer) converts String → PodDiscoveryMode using `fromValue()`
- **Flexibility**: If domain model changes, presentation layer remains stable

### Why fromValue() is case-insensitive

**Decision**: `PodDiscoveryMode.fromValue()` uses case-insensitive matching.

**Reasoning**:
- **Consistency**: picocli uses `setCaseInsensitiveEnumValuesAllowed(true)` in CLI layer
- **User experience**: More forgiving for users (`"HELM-RELEASE"`, `"helm-release"`, `"Helm-Release"` all work)
- **Convention**: Common practice for enum values in CLI tools

### Why Phase 1 includes fromValue/getValue (before CLI changes)

**Decision**: Phase 1 adds String conversion methods even though CLI doesn't use them yet.

**Reasoning**:
- **Phase 1**: Internal preparation (add String conversion capability), **no CLI changes**
- **Phase 2**: Actual CLI usage (add options, update DTO)
- **Benefit**:
  - Phase 1 can be independently tested without touching CLI
  - Phase 2 becomes simpler (just wire up existing functionality)
  - Clear separation of internal refactoring vs external API changes

### Why validate() signature changes between phases

**Phase 1**: `validate(String helmReleaseName)` - 1 parameter
- Only HELM_RELEASE mode exists
- Only helmReleaseName needs validation

**Phase 2**: `validate(String helmReleaseName, String deploymentName, Integer adminPort)` - 3 parameters
- DEPLOYMENT mode added
- Each mode validates its own required parameters and rejects incompatible ones
- Switch expression ensures exhaustive handling

---

## Phase 0: PauseRequest Introduction (Refactoring)

### Purpose
Unify Controller interface before introducing PodDiscoveryMode. This is refactoring that should have been done in the previous iteration.

### Why This Phase?
- Current `PauseController` has multiple specific methods (`pauseByHelmRelease()`, `pauseByHelmReleaseWithTls()`)
- Simplify to a single `pause(PauseRequest)` method
- Easier to extend when adding new discovery modes

### Implementation

#### 1. Create PauseRequest DTO

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/dto/PauseRequest.java`

```java
package com.scalar.admin.kubernetes.presentation.dto;

import javax.annotation.Nullable;

/**
 * Request DTO for pause operations.
 *
 * <p>This DTO transfers pause request data from the CLI layer to the presentation layer,
 * encapsulating all parameters needed for a pause operation.
 */
public record PauseRequest(
    String namespace,
    String helmReleaseName,
    int pauseDuration,
    @Nullable Long maxPauseWaitTime,
    boolean tlsEnabled,
    @Nullable String caRootCert,
    @Nullable String overrideAuthority) {

  /**
   * Compact constructor with validation.
   */
  public PauseRequest {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (helmReleaseName == null || helmReleaseName.isBlank()) {
      throw new IllegalArgumentException("helmReleaseName is required");
    }
    if (pauseDuration < 1) {
      throw new IllegalArgumentException("pauseDuration must be positive");
    }
  }
}
```

#### 2. Update PauseController

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/PauseController.java`

**Changes**:
- Add `pause(PauseRequest)` method
- Remove `pauseByHelmRelease()` method
- Remove `pauseByHelmReleaseWithTls()` method

```java
/**
 * Executes a pause operation based on the given request.
 *
 * @param request the pause request containing all necessary parameters
 * @return DTO containing the start and end time of the pause operation
 * @throws PauserException when the pause operation fails
 */
public PauseDurationDto pause(PauseRequest request) throws PauserException {
  PauseByHelmReleaseCommand command = request.tlsEnabled()
      ? PauseByHelmReleaseCommand.createWithTls(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime(),
          request.caRootCert(),
          request.overrideAuthority())
      : PauseByHelmReleaseCommand.create(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime());

  return applicationService.execute(command);
}
```

#### 3. Update Cli.call()

**File**: `cli/src/main/java/com/scalar/admin/kubernetes/Cli.java`

```java
@Override
public Integer call() {
  Result result = null;

  try {
    // Create controller
    PauseController controller = new PauseController();

    // Build PauseRequest
    PauseRequest request = new PauseRequest(
        namespace,
        helmReleaseName,
        pauseDuration,
        maxPauseWaitTime,
        tlsEnabled,
        getCaRootCert(),
        overrideAuthority);

    // Execute pause operation
    PauseDurationDto durationDto = controller.pause(request);

    // Build result
    result = new Result(namespace, helmReleaseName, durationDto, zoneId);
    ObjectMapper mapper = new ObjectMapper();
    System.out.println(mapper.writeValueAsString(result));
  } catch (JsonProcessingException e) {
    // error handling...
  } catch (Exception e) {
    // error handling...
  }

  return 0;
}
```

#### 4. Tests

- Update any existing Controller tests (if they exist)
- Verify all existing tests still pass
- No new test files needed (functionality unchanged)

### Verification

- ✅ All existing CLI commands work identically
- ✅ All tests pass
- ✅ No external behavior changes

---

## Phase 1: Internal PodDiscoveryMode Refactoring

### Purpose
Introduce PodDiscoveryMode concept internally without changing CLI interface. This is pure refactoring to prepare for Phase 2.

### Why This Phase?
- Establish PodDiscoveryMode structure before exposing to CLI
- Ensure validation logic works correctly
- Maintain complete backward compatibility

### Implementation

#### 1. Create PodDiscoveryMode enum

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PodDiscoveryMode.java`

```java
package com.scalar.admin.kubernetes.domain.model.pause;

import javax.annotation.Nullable;

/**
 * Enum representing different methods to discover target pods for pause operations.
 *
 * <p>Each mode requires different CLI options and uses different discovery strategies.
 */
public enum PodDiscoveryMode {
  /**
   * Discover pods by Helm release name using label-based auto-detection.
   * Requires: --release-name
   */
  HELM_RELEASE("helm-release");
  // DEPLOYMENT will be added in Phase 2

  private final String value;

  PodDiscoveryMode(String value) {
    this.value = value;
  }

  /**
   * Returns the string value of this mode.
   *
   * @return the string value (e.g., "helm-release")
   */
  public String getValue() {
    return value;
  }

  /**
   * Converts a string value to PodDiscoveryMode enum.
   *
   * <p>This method uses case-insensitive matching for better user experience, consistent with
   * picocli's setCaseInsensitiveEnumValuesAllowed(true) setting used in the CLI layer.
   *
   * @param value the string value (case-insensitive)
   * @return the corresponding PodDiscoveryMode
   * @throws IllegalArgumentException if the value is invalid
   */
  public static PodDiscoveryMode fromValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("podDiscoveryMode value cannot be null or blank");
    }

    // Case-insensitive comparison for consistency with CLI's setCaseInsensitiveEnumValuesAllowed
    for (PodDiscoveryMode mode : values()) {
      if (mode.value.equalsIgnoreCase(value)) {
        return mode;
      }
    }

    // Phase 1: Only HELM_RELEASE is valid
    throw new IllegalArgumentException(
        "Invalid podDiscoveryMode: " + value + ". Valid values are: helm-release");
  }

  /**
   * Validates that required parameters are present for this mode.
   *
   * <p>Phase 1: Only validates helmReleaseName (deploymentName and adminPort don't exist yet).
   *
   * @param helmReleaseName the Helm release name (nullable)
   * @throws IllegalArgumentException if required parameters are missing for this mode
   */
  public void validate(@Nullable String helmReleaseName) {
    // Phase 1: Only HELM_RELEASE exists
    if (helmReleaseName == null || helmReleaseName.isBlank()) {
      throw new IllegalArgumentException(
          "helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
    }
  }
}
```

#### 2. Extend PauseCommand interface

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PauseCommand.java`

```java
public sealed interface PauseCommand permits PauseByHelmReleaseCommand {

  /**
   * Returns the pod discovery mode for this command.
   * Default implementation returns HELM_RELEASE for backward compatibility.
   */
  default PodDiscoveryMode podDiscoveryMode() {
    return PodDiscoveryMode.HELM_RELEASE;
  }
}
```

#### 3. PauseByHelmReleaseCommand

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PauseByHelmReleaseCommand.java`

**Changes**: None needed (uses default method from PauseCommand interface)

Optionally, can explicitly override for clarity:
```java
@Override
public PodDiscoveryMode podDiscoveryMode() {
  return PodDiscoveryMode.HELM_RELEASE;
}
```

#### 4. Update PauseController

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/PauseController.java`

Add request null check for defensive programming:

```java
/**
 * Executes a pause operation based on the given request.
 *
 * @param request the pause request containing all necessary parameters
 * @return DTO containing the start and end time of the pause operation
 * @throws PauserException when the pause operation fails
 * @throws IllegalArgumentException when the request is null
 */
public PauseDurationDto pause(PauseRequest request) throws PauserException {
  if (request == null) {
    throw new IllegalArgumentException("request is required");
  }

  // Build and execute command (unchanged from Phase 0)
  PauseByHelmReleaseCommand command = request.tlsEnabled()
      ? PauseByHelmReleaseCommand.createWithTls(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime(),
          request.caRootCert(),
          request.overrideAuthority())
      : PauseByHelmReleaseCommand.create(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime());

  return applicationService.execute(command);
}
```

#### 5. CLI Changes

**None** - This is internal refactoring only.

#### 6. Tests

**New Test File**: `lib/src/test/java/com/scalar/admin/kubernetes/domain/model/pause/PodDiscoveryModeTest.java`

```java
class PodDiscoveryModeTest {

  @Nested
  @DisplayName("getValue method")
  class GetValueMethod {

    @Test
    @DisplayName("returns correct value for HELM_RELEASE")
    void returnsCorrectValueForHelmRelease() {
      assertThat(PodDiscoveryMode.HELM_RELEASE.getValue()).isEqualTo("helm-release");
    }
  }

  @Nested
  @DisplayName("fromValue method")
  class FromValueMethod {

    @Nested
    @DisplayName("when given valid values")
    class WhenGivenValidValues {

      @Test
      @DisplayName("converts 'helm-release' to HELM_RELEASE")
      void convertsHelmRelease() {
        assertThat(PodDiscoveryMode.fromValue("helm-release"))
            .isEqualTo(PodDiscoveryMode.HELM_RELEASE);
      }

      @Test
      @DisplayName("converts 'HELM-RELEASE' to HELM_RELEASE (case insensitive)")
      void convertsHelmReleaseCaseInsensitive() {
        assertThat(PodDiscoveryMode.fromValue("HELM-RELEASE"))
            .isEqualTo(PodDiscoveryMode.HELM_RELEASE);
      }
    }

    @Nested
    @DisplayName("when given invalid values")
    class WhenGivenInvalidValues {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException for null or blank")
      void throwsIllegalArgumentExceptionForNullOrBlank(String invalidValue) {
        assertThatThrownBy(() -> PodDiscoveryMode.fromValue(invalidValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("podDiscoveryMode value cannot be null or blank");
      }

      @ParameterizedTest
      @ValueSource(strings = {"invalid", "unknown", "deployment"})
      @DisplayName("throws IllegalArgumentException for unknown value")
      void throwsIllegalArgumentExceptionForUnknownValue(String invalidValue) {
        assertThatThrownBy(() -> PodDiscoveryMode.fromValue(invalidValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid podDiscoveryMode: " + invalidValue + ". Valid values are: helm-release");
      }
    }
  }

  @Nested
  @DisplayName("HELM_RELEASE mode")
  class HelmReleaseMode {

    @Nested
    @DisplayName("validate method")
    class ValidateMethod {

      @Nested
      @DisplayName("when helmReleaseName is valid")
      class WhenHelmReleaseNameIsValid {

        @Test
        @DisplayName("validates successfully")
        void validatesSuccessfully() {
          // Arrange & Act & Assert
          assertThatCode(() -> PodDiscoveryMode.HELM_RELEASE.validate("my-release"))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @DisplayName("when helmReleaseName is invalid")
      class WhenHelmReleaseNameIsInvalid {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "   "})
        @DisplayName("throws IllegalArgumentException")
        void throwsIllegalArgumentException(String invalidHelmReleaseName) {
          // Arrange & Act & Assert
          assertThatThrownBy(() -> PodDiscoveryMode.HELM_RELEASE.validate(invalidHelmReleaseName))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
        }
      }
    }
  }
}
```

### Verification

- ✅ All existing tests pass
- ✅ New PodDiscoveryModeTest passes
- ✅ CLI behavior unchanged
- ✅ PodDiscoveryMode.HELM_RELEASE flows through the system internally

---

## Phase 2: Add --pod-discovery-mode CLI Option

### Purpose
Expose PodDiscoveryMode to CLI users with `--pod-discovery-mode` option, defaulting to HELM_RELEASE for backward compatibility.

### Implementation

#### 1. Extend PodDiscoveryMode enum

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PodDiscoveryMode.java`

**Changes**:
- Add `DEPLOYMENT` enum value with `"deployment"` string
- Update `fromValue()` error message to include "deployment"
- Extend `validate()` method signature from 1 parameter to 3 parameters
- Add switch expression for both modes

```java
public enum PodDiscoveryMode {
  HELM_RELEASE("helm-release"),
  DEPLOYMENT("deployment");  // Added in Phase 2

  // value, getValue(), fromValue() already exist from Phase 1

  /**
   * Converts a string value to PodDiscoveryMode enum.
   * (Updated error message to include "deployment")
   */
  public static PodDiscoveryMode fromValue(String value) {
    // ... same logic as Phase 1 ...
    throw new IllegalArgumentException(
        "Invalid podDiscoveryMode: " + value + ". Valid values are: helm-release, deployment");
        // Updated: now includes "deployment"
  }

  /**
   * Validates the required parameters for this discovery mode.
   * (Signature changed from 1 parameter to 3 parameters)
   */
  public void validate(
      @Nullable String helmReleaseName,
      @Nullable String deploymentName,
      @Nullable Integer adminPort) {

    switch (this) {
      case HELM_RELEASE:
        if (helmReleaseName == null || helmReleaseName.isBlank()) {
          throw new IllegalArgumentException(
              "helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
        }
        if (deploymentName != null || adminPort != null) {
          throw new IllegalArgumentException(
              "deploymentName and adminPort cannot be used when podDiscoveryMode is HELM_RELEASE");
        }
        break;
      case DEPLOYMENT:
        if (deploymentName == null || deploymentName.isBlank()) {
          throw new IllegalArgumentException(
              "deploymentName is required when podDiscoveryMode is DEPLOYMENT");
        }
        if (adminPort == null) {
          throw new IllegalArgumentException(
              "adminPort is required when podDiscoveryMode is DEPLOYMENT");
        }
        if (helmReleaseName != null) {
          throw new IllegalArgumentException(
              "helmReleaseName cannot be used when podDiscoveryMode is DEPLOYMENT");
        }
        break;
    }
  }
}
```

#### 2. Extend PauseRequest

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/dto/PauseRequest.java`

**Important**: Use `String podDiscoveryMode`, **not** `PodDiscoveryMode` enum (see Design Decisions).

```java
public record PauseRequest(
    String namespace,
    String podDiscoveryMode,            // Added (String, not enum!)
    @Nullable String helmReleaseName,   // Made nullable
    @Nullable String deploymentName,    // Added
    @Nullable Integer adminPort,        // Added
    int pauseDuration,
    @Nullable Long maxPauseWaitTime,
    boolean tlsEnabled,
    @Nullable String caRootCert,
    @Nullable String overrideAuthority) {

  public PauseRequest {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (podDiscoveryMode == null || podDiscoveryMode.isBlank()) {
      throw new IllegalArgumentException("podDiscoveryMode is required");
    }
    if (pauseDuration < 1) {
      throw new IllegalArgumentException("pauseDuration must be positive");
    }
    // Mode-specific validation (helmReleaseName, deploymentName, adminPort) is done in Controller
  }
}
```

#### 3. Update PauseController

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/PauseController.java`

**Important**: Controller converts String (from DTO) to PodDiscoveryMode enum (domain model).

```java
/**
 * Executes a pause operation based on the given request.
 *
 * @param request the pause request containing all necessary parameters
 * @return DTO containing the start and end time of the pause operation
 * @throws PauserException when the pause operation fails
 * @throws IllegalArgumentException when the request or podDiscoveryMode is invalid
 * @throws UnsupportedOperationException when DEPLOYMENT mode is requested (not yet implemented)
 */
public PauseDurationDto pause(PauseRequest request) throws PauserException {
  if (request == null) {
    throw new IllegalArgumentException("request is required");
  }

  // Convert String to enum (presentation layer → domain layer)
  PodDiscoveryMode mode = PodDiscoveryMode.fromValue(request.podDiscoveryMode());

  // Validate using the enum
  mode.validate(request.helmReleaseName(), request.deploymentName(), request.adminPort());

  // Phase 2: Only HELM_RELEASE is implemented
  if (mode == PodDiscoveryMode.DEPLOYMENT) {
    throw new UnsupportedOperationException(
        "DEPLOYMENT mode is not yet implemented. Only HELM_RELEASE mode is currently supported.");
  }

  // Build command
  PauseByHelmReleaseCommand command = request.tlsEnabled()
      ? PauseByHelmReleaseCommand.createWithTls(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime(),
          request.caRootCert(),
          request.overrideAuthority())
      : PauseByHelmReleaseCommand.create(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime());

  return applicationService.execute(command);
}
```

#### 4. Add CLI Options

**File**: `cli/src/main/java/com/scalar/admin/kubernetes/Cli.java`

**Important**: CLI uses `String` type (not enum) to avoid exposing domain models to external interfaces.

**Add new options**:

```java
@Option(
    names = {"--pod-discovery-mode"},
    description =
        "The mode to discover the target pods. Valid values: helm-release, deployment. "
            + "helm-release by default.",
    defaultValue = "helm-release")  // String value, not enum
private String podDiscoveryMode;  // String type, not PodDiscoveryMode

@Option(
    names = {"--deployment-name"},
    description =
        "The name of the Kubernetes Deployment for the Scalar product. "
            + "Required when --pod-discovery-mode is deployment.")
@Nullable
private String deploymentName;

@Option(
    names = {"--admin-port"},
    description =
        "The port number of the admin interface of the Scalar product. "
            + "Required when --pod-discovery-mode is deployment.")
@Nullable
private Integer adminPort;
```

**Modify existing option**:

```java
@Option(
    names = {"--release-name", "-r"},
    description =
        "The helm release name that you specify when you run the `helm install <RELEASE_NAME>` command. "
            + "You can see the <RELEASE_NAME> by using the `helm list` command. "
            + "Required when --pod-discovery-mode is helm-release.")
// Remove: required = true
@Nullable
private String helmReleaseName;
```

#### 5. Update Cli.call()

**File**: `cli/src/main/java/com/scalar/admin/kubernetes/Cli.java`

```java
@Override
public Integer call() {
  Result result = null;

  try {
    // Create controller
    PauseController controller = new PauseController();

    // Build PauseRequest (pass String value to DTO, not enum)
    PauseRequest request = new PauseRequest(
        namespace,
        podDiscoveryMode,    // Added: String value (e.g., "helm-release")
        helmReleaseName,     // Now nullable
        deploymentName,      // Added: nullable
        adminPort,           // Added: nullable
        pauseDuration,
        maxPauseWaitTime,
        tlsEnabled,
        getCaRootCert(),
        overrideAuthority);

    // Execute pause operation (Controller will convert String to enum)
    PauseDurationDto durationDto = controller.pause(request);

    // Build result
    result = new Result(namespace, helmReleaseName, durationDto, zoneId);
    ObjectMapper mapper = new ObjectMapper();
    System.out.println(mapper.writeValueAsString(result));
  } catch (JsonProcessingException e) {
    // error handling...
  } catch (Exception e) {
    // error handling...
  }

  return 0;
}
```

#### 6. Tests

**Extend PodDiscoveryModeTest**:

Add tests for DEPLOYMENT mode validation and updated fromValue() error messages:

```java
@Nested
@DisplayName("DEPLOYMENT mode")
class DeploymentMode {

  @Nested
  @DisplayName("validate method")
  class ValidateMethod {

    @Nested
    @DisplayName("when deploymentName and adminPort are valid")
    class WhenDeploymentNameAndAdminPortAreValid {

      @Test
      @DisplayName("validates successfully")
      void validatesSuccessfully() {
        // Arrange & Act & Assert
        assertThatCode(
                () -> PodDiscoveryMode.DEPLOYMENT.validate(null, "my-deployment", 60054))
            .doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("when deploymentName is invalid")
    class WhenDeploymentNameIsInvalid {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidDeploymentName) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () -> PodDiscoveryMode.DEPLOYMENT.validate(null, invalidDeploymentName, 60054))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("deploymentName is required when podDiscoveryMode is DEPLOYMENT");
      }
    }

    @Nested
    @DisplayName("when adminPort is null")
    class WhenAdminPortIsNull {

      @Test
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException() {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () -> PodDiscoveryMode.DEPLOYMENT.validate(null, "my-deployment", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("adminPort is required when podDiscoveryMode is DEPLOYMENT");
      }
    }

    @Nested
    @DisplayName("when helmReleaseName is specified")
    class WhenHelmReleaseNameIsSpecified {

      @Test
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException() {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    PodDiscoveryMode.DEPLOYMENT.validate("my-release", "my-deployment", 60054))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("helmReleaseName cannot be used when podDiscoveryMode is DEPLOYMENT");
      }
    }
  }
}
```

Also update existing fromValue tests to expect "deployment" in error messages:

```java
@ParameterizedTest
@ValueSource(strings = {"invalid", "unknown", "helm_release", "deploy"})
@DisplayName("throws IllegalArgumentException for unknown value")
void throwsIllegalArgumentExceptionForUnknownValue(String invalidValue) {
  assertThatThrownBy(() -> PodDiscoveryMode.fromValue(invalidValue))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
          "Invalid podDiscoveryMode: "
              + invalidValue
              + ". Valid values are: helm-release, deployment");
}
```

**Update PauseRequestTest**:

Update validation tests to handle new nullable fields (helmReleaseName, deploymentName, adminPort) and new required field (podDiscoveryMode). Tests should verify:
- podDiscoveryMode is required (null/blank validation)
- helmReleaseName, deploymentName, adminPort can be null (mode-specific validation happens in Controller)
- pauseDuration validation still works

**Add PauseControllerTest** (if not already exists):

Add tests for:
- Null request handling
- fromValue() conversion (String → enum)
- Mode validation
- UnsupportedOperationException for DEPLOYMENT mode in Phase 2

**Add CLI Integration Tests** (manual or automated):

Test various option combinations:
- `--release-name X` (no `--pod-discovery-mode`) → defaults to "helm-release", works ✅
- `--pod-discovery-mode helm-release --release-name X` → works ✅
- `--pod-discovery-mode HELM-RELEASE --release-name X` → works (case-insensitive) ✅
- `--pod-discovery-mode deployment --deployment-name X --admin-port 60054` → UnsupportedOperationException (not implemented in Phase 2) ✅
- `--pod-discovery-mode deployment` (missing required options) → validation error ✅
- `--pod-discovery-mode helm-release --deployment-name X` → validation error ✅
- `--pod-discovery-mode helm-release --admin-port 60054` → validation error ✅
- `--pod-discovery-mode deployment --release-name X` → validation error ✅
- `--pod-discovery-mode invalid` → IllegalArgumentException from fromValue() ✅

### Verification

- ✅ Backward compatibility: existing commands work without `--pod-discovery-mode`
- ✅ Explicit HELM_RELEASE mode works
- ✅ DEPLOYMENT mode validation works (but execution throws UnsupportedOperationException)
- ✅ All validation error messages are clear

---

## Phase 3: Implement PauseByDeploymentNameCommand

### Purpose
Add the domain model for deployment-based pause operations.

### Implementation

#### 1. Create PauseByDeploymentNameCommand

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PauseByDeploymentNameCommand.java`

```java
package com.scalar.admin.kubernetes.domain.model.pause;

import javax.annotation.Nullable;

/**
 * Command to pause pods identified by deployment name.
 *
 * <p>This command represents the use case of pausing all pods that belong to a specific
 * deployment in a given namespace. The admin port must be explicitly specified.
 *
 * @param namespace the Kubernetes namespace where the deployment exists
 * @param deploymentName the name of the deployment
 * @param adminPort the admin port number for the Scalar product
 * @param pauseDuration the duration to pause in milliseconds
 * @param maxPauseWaitTime the maximum wait time (in milliseconds) for pause operation to complete,
 *     null for default
 * @param tlsConfig the TLS configuration for secure communication, null for non-TLS communication
 */
public record PauseByDeploymentNameCommand(
    String namespace,
    String deploymentName,
    int adminPort,
    int pauseDuration,
    @Nullable Long maxPauseWaitTime,
    @Nullable TlsConfig tlsConfig)
    implements PauseCommand {

  /**
   * Compact constructor with validation.
   */
  public PauseByDeploymentNameCommand {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (deploymentName == null || deploymentName.isBlank()) {
      throw new IllegalArgumentException("deploymentName is required");
    }
    if (adminPort < 1 || adminPort > 65535) {
      throw new IllegalArgumentException(
          "adminPort must be between 1 and 65535, but was: " + adminPort);
    }
    if (pauseDuration < 1) {
      throw new IllegalArgumentException(
          "pauseDuration must be greater than 0 millisecond, but was: " + pauseDuration);
    }
  }

  @Override
  public PodDiscoveryMode podDiscoveryMode() {
    return PodDiscoveryMode.DEPLOYMENT;
  }

  /**
   * Creates a command for pausing pods without TLS.
   */
  public static PauseByDeploymentNameCommand create(
      String namespace,
      String deploymentName,
      int adminPort,
      int pauseDuration,
      Long maxPauseWaitTime) {
    return new PauseByDeploymentNameCommand(
        namespace, deploymentName, adminPort, pauseDuration, maxPauseWaitTime, null);
  }

  /**
   * Creates a command for pausing pods with TLS enabled.
   */
  public static PauseByDeploymentNameCommand createWithTls(
      String namespace,
      String deploymentName,
      int adminPort,
      int pauseDuration,
      Long maxPauseWaitTime,
      String caRootCert,
      String overrideAuthority) {
    return new PauseByDeploymentNameCommand(
        namespace,
        deploymentName,
        adminPort,
        pauseDuration,
        maxPauseWaitTime,
        new TlsConfig(caRootCert, overrideAuthority));
  }
}
```

#### 2. Update PauseCommand permits

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/model/pause/PauseCommand.java`

```java
public sealed interface PauseCommand
    permits PauseByHelmReleaseCommand, PauseByDeploymentNameCommand {

  default PodDiscoveryMode podDiscoveryMode() {
    return PodDiscoveryMode.HELM_RELEASE;
  }
}
```

#### 3. Tests

**New Test File**: `lib/src/test/java/com/scalar/admin/kubernetes/domain/model/pause/PauseByDeploymentNameCommandTest.java`

Test validation, factory methods, and podDiscoveryMode().

---

## Phase 4: Extend Repository Layer (✅ Completed)

### Purpose
Add deployment-based pod discovery to the repository.

### Implementation

#### 1. Extend PauseTargetRepository interface

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/domain/repository/PauseTargetRepository.java`

```java
/**
 * Finds a pause target by deployment name in the specified namespace.
 *
 * @param namespace the Kubernetes namespace
 * @param deploymentName the name of the deployment
 * @param adminPort the admin port number
 * @return a PauseTarget aggregate
 * @throws PauserException if the target cannot be found
 */
PauseTarget findByDeploymentName(
    String namespace,
    String deploymentName,
    int adminPort) throws PauserException;
```

#### 2. Implement in PauseTargetRepositoryImpl

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/infrastructure/repository/PauseTargetRepositoryImpl.java`

**Implementation approach**: The method is split into three package-private sub-methods for better testability:

**Main orchestration method**:
```java
@Override
public PauseTarget findByDeploymentName(String namespace, String deploymentName, int adminPort)
    throws PauserException {
  V1Deployment deployment = getDeployment(namespace, deploymentName);
  String labelSelector = extractLabelSelectorFromDeployment(deployment);
  List<V1Pod> pods = findPodsByLabelSelector(namespace, deploymentName, labelSelector);
  return new PauseTarget(pods, deployment, adminPort);
}
```

**Sub-method 1: getDeployment()**
- Purpose: Retrieve V1Deployment via Kubernetes API
- Implementation: `appsV1Api.readNamespacedDeployment(deploymentName, namespace, null)`
- Error handling: Wraps ApiException into PauserException with descriptive message

**Sub-method 2: extractLabelSelectorFromDeployment()**
- Purpose: Extract and validate label selector from deployment
- Implementation steps:
  1. Extract deployment name and namespace from deployment metadata
  2. Check if `deployment.getSpec()` is not null (defensive programming)
  3. Check if `deployment.getSpec().getSelector()` is not null
  4. Parse selector using `LabelSelector.parse(selector).toString()`
  5. Validate that parsed selector is not empty
- Error handling:
  - Null spec → PauserException
  - Null selector → PauserException
  - Invalid selector (IllegalArgumentException from parse) → PauserException
  - Empty selector → PauserException

**Sub-method 3: findPodsByLabelSelector()**
- Purpose: Retrieve pods matching the label selector
- Implementation: `coreApi.listNamespacedPod(namespace, ..., labelSelector, ...)`
- Error handling:
  - ApiException → PauserException
  - Empty pod list → PauserException

**Design rationale**:
- Package-private visibility enables focused unit testing
- Each sub-method has a single responsibility
- Defensive null checks even with `@Nonnull` annotations (API errors, data corruption)
- Clear error messages include namespace and deployment name for debugging

#### 3. Tests

**File**: `lib/src/test/java/com/scalar/admin/kubernetes/infrastructure/repository/PauseTargetRepositoryImplTest.java`

Added comprehensive tests for each sub-method using `@Nested` and `@DisplayName`:

**getDeployment() tests**:
- ✅ Returns V1Deployment when deployment exists
- ✅ Throws PauserException when readNamespacedDeployment throws ApiException

**extractLabelSelectorFromDeployment() tests**:
- ✅ Returns label selector string when deployment has valid selector
- ✅ Throws PauserException when deployment has no spec
- ✅ Throws PauserException when deployment has no selector
- ✅ Throws PauserException when deployment has empty selector
- ✅ Throws PauserException when selector has invalid operator

**findPodsByLabelSelector() tests**:
- ✅ Returns list of pods when pods are found
- ✅ Throws PauserException when listNamespacedPod throws ApiException
- ✅ Throws PauserException when no pods are found

**findByDeploymentName() tests**:
- ✅ Returns PauseTarget when all operations succeed (integration test)

**Total tests added**: 9 new test cases in 4 `@Nested` classes
**Test framework**: JUnit 5 with AssertJ for exception assertions
**Mock separation**: `mockAppsV1ApiForReadNamespacedDeployment()` separate from Helm Release mocks

---

## Phase 5: Extend Application Service

### Purpose
Handle PauseByDeploymentNameCommand in the application service.

### Implementation

#### Update PauseApplicationService

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/application/PauseApplicationService.java`

```java
public PauseDurationDto execute(PauseCommand command) throws PauserException {
  return switch (command) {
    case PauseByHelmReleaseCommand cmd -> executePauseByHelmRelease(cmd);
    case PauseByDeploymentNameCommand cmd -> executePauseByDeploymentName(cmd);
  };
}

private PauseDurationDto executePauseByDeploymentName(PauseByDeploymentNameCommand command)
    throws PauserException {
  // Get the pause target
  PauseTarget targetBeforePause;
  try {
    targetBeforePause = pauseTargetRepository.findByDeploymentName(
        command.namespace(),
        command.deploymentName(),
        command.adminPort());
  } catch (Exception e) {
    throw new PauserException("Failed to find the target pods to pause.", e);
  }

  // Create client
  ScalarAdminClient client;
  try {
    if (command.tlsConfig() != null) {
      client = clientFactory.createClient(targetBeforePause, command.tlsConfig());
    } else {
      client = clientFactory.createClient(targetBeforePause);
    }
  } catch (Exception e) {
    throw new PauserException("Failed to initialize the Scalar Admin client.", e);
  }

  // Execute pause
  PauseDuration pauseDuration = pauseService.pause(
      targetBeforePause,
      () -> pauseTargetRepository.findByDeploymentName(
          command.namespace(),
          command.deploymentName(),
          command.adminPort()),
      client,
      command.pauseDuration(),
      command.maxPauseWaitTime());

  // Convert to DTO
  return new PauseDurationDto(
      pauseDuration.startTime().toEpochMilli(),
      pauseDuration.endTime().toEpochMilli());
}
```

---

## Phase 6: Complete Presentation Layer (✅ Completed)

### Purpose
Enable deployment mode execution in PauseController.

### Implementation

#### 1. Add package-private test constructor to PauseController

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/PauseController.java`

```java
/**
 * Creates a PauseController with the given application service (for testing).
 *
 * @param applicationService the application service
 */
PauseController(PauseApplicationService applicationService) {
  this.applicationService = applicationService;
}
```

**Rationale**: Enables dependency injection for testing without polluting production code with public test constructors.

#### 2. Update PauseController.pause() method

**File**: `lib/src/main/java/com/scalar/admin/kubernetes/presentation/PauseController.java`

**Note**: Remove the UnsupportedOperationException from Phase 2, now both modes are fully implemented.

```java
public PauseDurationDto pause(PauseRequest request) throws PauserException {
  if (request == null) {
    throw new IllegalArgumentException("request is required");
  }

  // Convert String to enum (presentation layer → domain layer)
  PodDiscoveryMode mode = PodDiscoveryMode.fromValue(request.podDiscoveryMode());

  // Validate using the enum
  mode.validate(request.helmReleaseName(), request.deploymentName(), request.adminPort());

  // Create command based on mode
  PauseCommand command = createCommand(request, mode);

  // Execute
  return applicationService.execute(command);
}

PauseCommand createCommand(PauseRequest request, PodDiscoveryMode mode) {
  return switch (mode) {
    case HELM_RELEASE -> createHelmReleaseCommand(request);
    case DEPLOYMENT -> createDeploymentCommand(request);
  };
}

PauseByHelmReleaseCommand createHelmReleaseCommand(PauseRequest request) {
  return request.tlsEnabled()
      ? PauseByHelmReleaseCommand.createWithTls(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime(),
          request.caRootCert(),
          request.overrideAuthority())
      : PauseByHelmReleaseCommand.create(
          request.namespace(),
          request.helmReleaseName(),
          request.pauseDuration(),
          request.maxPauseWaitTime());
}

PauseByDeploymentNameCommand createDeploymentCommand(PauseRequest request) {
  return request.tlsEnabled()
      ? PauseByDeploymentNameCommand.createWithTls(
          request.namespace(),
          request.deploymentName(),
          request.adminPort(),
          request.pauseDuration(),
          request.maxPauseWaitTime(),
          request.caRootCert(),
          request.overrideAuthority())
      : PauseByDeploymentNameCommand.create(
          request.namespace(),
          request.deploymentName(),
          request.adminPort(),
          request.pauseDuration(),
          request.maxPauseWaitTime());
}
```

**Design decisions**:
- **Method naming**: `createCommand` instead of `buildCommand` for consistency with existing `create()` factory methods
- **Visibility**: Methods are package-private to enable detailed unit testing while keeping them internal
- **Switch expression**: Ensures exhaustive handling of all modes

#### 3. Tests

**New Test File**: `lib/src/test/java/com/scalar/admin/kubernetes/presentation/PauseControllerTest.java`

Added comprehensive tests (12 test cases total):

**pause() method tests (4 tests)**:
- ✅ Executes pause successfully with HELM_RELEASE mode
- ✅ Executes pause successfully with DEPLOYMENT mode
- ✅ Throws IllegalArgumentException when request is null
- ✅ Throws IllegalArgumentException when podDiscoveryMode is invalid

**createCommand() method tests (2 tests)**:
- ✅ Creates PauseByHelmReleaseCommand when mode is HELM_RELEASE
- ✅ Creates PauseByDeploymentNameCommand when mode is DEPLOYMENT

**createHelmReleaseCommand() method tests (3 tests)**:
- ✅ Creates command without TLS when tlsEnabled is false
- ✅ Creates command with TLS when tlsEnabled is true
- ✅ Creates command with null maxPauseWaitTime when not specified

**createDeploymentCommand() method tests (3 tests)**:
- ✅ Creates command without TLS when tlsEnabled is false
- ✅ Creates command with TLS when tlsEnabled is true
- ✅ Creates command with null maxPauseWaitTime when not specified

**Test framework**: JUnit 5 with AssertJ, using `@Nested` and `@DisplayName` for structure
**Mock strategy**: Mock PauseApplicationService using package-private constructor

### Verification

- ✅ All 182 tests pass
- ✅ Both HELM_RELEASE and DEPLOYMENT modes fully functional
- ✅ No UnsupportedOperationException for DEPLOYMENT mode
- ✅ Package-private methods tested in detail
- ✅ Public method (pause()) tested for main paths only

---

## Phase 7: Update Result for Deployment Mode

### Purpose
Include deployment name in JSON output when using deployment mode.

### Implementation

#### Update Result class

**File**: `cli/src/main/java/com/scalar/admin/kubernetes/Result.java`

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

Update constructor to accept both fields.

#### Update Cli.call()

**File**: `cli/src/main/java/com/scalar/admin/kubernetes/Cli.java`

```java
// Execute pause operation
PauseDurationDto durationDto = controller.pause(request);

// Convert String to enum for result building
PodDiscoveryMode mode = PodDiscoveryMode.fromValue(podDiscoveryMode);

// Build result based on mode
result = switch (mode) {
  case HELM_RELEASE -> new Result(namespace, helmReleaseName, null, durationDto, zoneId);
  case DEPLOYMENT -> new Result(namespace, null, deploymentName, durationDto, zoneId);
};
```

---

## Verification & Testing

### Phase-by-Phase Testing

**Phase 0**:
- ✅ All existing tests pass
- ✅ Manual CLI test: existing commands work

**Phase 1**:
- ✅ PodDiscoveryModeTest passes
- ✅ All existing tests pass
- ✅ Manual CLI test: behavior unchanged

**Phase 2**:
- ✅ Extended PodDiscoveryModeTest passes
- ✅ CLI validation tests pass
- ✅ Manual tests of option combinations

**Phase 3-7**:
- ✅ New command tests pass
- ✅ Repository tests pass
- ✅ Application service tests pass
- ✅ Integration tests pass

### Manual Testing Examples

**HELM_RELEASE mode (existing)**:
```bash
./gradlew run --args="--namespace default --release-name my-release"
./gradlew run --args="--namespace default --pod-discovery-mode helm-release --release-name my-release"
```

**DEPLOYMENT mode (new)**:
```bash
./gradlew run --args="--namespace default --pod-discovery-mode deployment --deployment-name scalardb-cluster-node --admin-port 60054"
```

---

## Future Considerations

### Repository Naming
Current: `PauseTargetRepository`
Alternative: `KubernetesClient` or `PauseTargetFinder`

**Decision**: Implement first, then refactor if needed based on:
- Does it only deal with pause targets, or broader K8s operations?
- Is "Repository" semantically correct for what it does?

### Additional Discovery Modes
Easily extensible:
```java
public enum PodDiscoveryMode {
  HELM_RELEASE,
  DEPLOYMENT,
  STATEFULSET,  // Future
  LABEL_SELECTOR  // Future
}
```

---

## Summary

This phased approach ensures:
1. **Zero breaking changes** until Phase 2
2. **Complete backward compatibility** with default values
3. **Clean separation of concerns** across DDD layers
4. **Type-safe** implementation with sealed interfaces
5. **Testable** at every phase
6. **Extensible** for future discovery modes
