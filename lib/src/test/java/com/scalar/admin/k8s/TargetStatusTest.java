package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TargetStatusTest {

  @Test
  public void equals_IfTargetStatusAreTheSame_ShouldReturnTrue() {
    // Arrage
    Map<String, Integer> podRestartCounts1 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions1 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion1 = "deploymentResourceVersion1";

    Map<String, Integer> podRestartCounts2 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions2 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion2 = "deploymentResourceVersion1";

    TargetStatus status1 =
        new TargetStatus(podRestartCounts1, podResourceVersions1, deploymentResourceVersion1);
    TargetStatus status2 =
        new TargetStatus(podRestartCounts2, podResourceVersions2, deploymentResourceVersion2);

    // Act && Assert
    assertTrue(status1.equals(status2));
  }

  @Test
  public void equals_IfOnePodNameIsDifferent_ShouldReturnFalse() {
    // Arrage
    Map<String, Integer> podRestartCounts1 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions1 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion1 = "deploymentResourceVersion1";

    Map<String, Integer> podRestartCounts2 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("different-name", 2);
          }
        };
    Map<String, String> podResourceVersions2 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion2 = "deploymentResourceVersion1";

    TargetStatus status1 =
        new TargetStatus(podRestartCounts1, podResourceVersions1, deploymentResourceVersion1);
    TargetStatus status2 =
        new TargetStatus(podRestartCounts2, podResourceVersions2, deploymentResourceVersion2);

    // Act && Assert
    assertFalse(status1.equals(status2));
  }

  @Test
  public void equals_IfPodAmountsAreDifferent_ShouldReturnFalse() {
    // Arrage
    Map<String, Integer> podRestartCounts1 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions1 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion1 = "deploymentResourceVersion1";

    Map<String, Integer> podRestartCounts2 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
          }
        };
    Map<String, String> podResourceVersions2 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
          }
        };
    String deploymentResourceVersion2 = "deploymentResourceVersion1";

    TargetStatus status1 =
        new TargetStatus(podRestartCounts1, podResourceVersions1, deploymentResourceVersion1);
    TargetStatus status2 =
        new TargetStatus(podRestartCounts2, podResourceVersions2, deploymentResourceVersion2);

    // Act && Assert
    assertFalse(status1.equals(status2));
  }

  @Test
  public void equals_IfPodRestartCountsAreDifferent_ShouldReturnFalse() {
    // Arrage
    Map<String, Integer> podRestartCounts1 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions1 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion1 = "deploymentResourceVersion1";

    Map<String, Integer> podRestartCounts2 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 2);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions2 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion2 = "deploymentResourceVersion1";

    TargetStatus status1 =
        new TargetStatus(podRestartCounts1, podResourceVersions1, deploymentResourceVersion1);
    TargetStatus status2 =
        new TargetStatus(podRestartCounts2, podResourceVersions2, deploymentResourceVersion2);

    // Act && Assert
    assertFalse(status1.equals(status2));
  }

  @Test
  public void equals_IfPodResourceVersionsAreDifferent_ShouldReturnFalse() {
    // Arrage
    Map<String, Integer> podRestartCounts1 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions1 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion1 = "deploymentResourceVersion1";

    Map<String, Integer> podRestartCounts2 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions2 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion-different");
          }
        };
    String deploymentResourceVersion2 = "deploymentResourceVersion1";

    TargetStatus status1 =
        new TargetStatus(podRestartCounts1, podResourceVersions1, deploymentResourceVersion1);
    TargetStatus status2 =
        new TargetStatus(podRestartCounts2, podResourceVersions2, deploymentResourceVersion2);

    // Act && Assert
    assertFalse(status1.equals(status2));
  }

  @Test
  public void equals_IfDeploymentResourceVersionsAreDifferent_ShouldReturnFalse() {
    // Arrage
    Map<String, Integer> podRestartCounts1 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions1 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion1 = "deploymentResourceVersion1";

    Map<String, Integer> podRestartCounts2 =
        new HashMap<String, Integer>() {
          {
            put("pod1", 1);
            put("pod2", 2);
          }
        };
    Map<String, String> podResourceVersions2 =
        new HashMap<String, String>() {
          {
            put("pod1", "resourceVersion1");
            put("pod2", "resourceVersion2");
          }
        };
    String deploymentResourceVersion2 = "deploymentResourceVersion-different";

    TargetStatus status1 =
        new TargetStatus(podRestartCounts1, podResourceVersions1, deploymentResourceVersion1);
    TargetStatus status2 =
        new TargetStatus(podRestartCounts2, podResourceVersions2, deploymentResourceVersion2);

    // Act && Assert
    assertFalse(status1.equals(status2));
  }
}
