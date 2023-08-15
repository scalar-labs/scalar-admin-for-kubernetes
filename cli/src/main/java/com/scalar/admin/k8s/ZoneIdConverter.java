package com.scalar.admin.k8s;

import java.time.ZoneId;
import picocli.CommandLine.ITypeConverter;

class ZoneIdConverter implements ITypeConverter<ZoneId> {
  public ZoneId convert(String value) throws Exception {
    return ZoneId.of(value);
  }
}
