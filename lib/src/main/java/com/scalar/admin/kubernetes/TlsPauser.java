package com.scalar.admin.kubernetes;

import com.scalar.admin.RequestCoordinator;
import com.scalar.admin.TlsRequestCoordinator;
import java.net.InetSocketAddress;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class TlsPauser extends Pauser {

  private final String caRootCert;
  private final String overrideAuthority;

  public TlsPauser(
      String namespace,
      String helmReleaseName,
      @Nullable String caRootCert,
      @Nullable String overrideAuthority)
      throws PauserException {
    super(namespace, helmReleaseName);

    this.caRootCert = caRootCert;
    this.overrideAuthority = overrideAuthority;
  }

  @Override
  RequestCoordinator getRequestCoordinator(TargetSnapshot target) {
    return new TlsRequestCoordinator(
        target.getPods().stream()
            .map(p -> new InetSocketAddress(p.getStatus().getPodIP(), target.getAdminPort()))
            .collect(Collectors.toList()),
        caRootCert,
        overrideAuthority);
  }
}
