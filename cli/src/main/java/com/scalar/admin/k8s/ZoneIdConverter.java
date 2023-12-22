package com.scalar.admin.kubernetes;

import java.time.ZoneId;
import picocli.CommandLine.ITypeConverter;

class ZoneIdConverter implements ITypeConverter<ZoneId> {
  public ZoneId convert(String value) throws Exception {
    return ZoneId.of(value);
  }
}
