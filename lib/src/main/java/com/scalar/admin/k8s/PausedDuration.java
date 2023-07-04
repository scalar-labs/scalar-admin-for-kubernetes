package com.scalar.admin.k8s;

import java.util.Date;

class PausedDuration {
  final Date startAt;
  final Date endAt;

  PausedDuration(Date startAt, Date endAt) {
    this.startAt = startAt;
    this.endAt = endAt;
  }

  Date getStartAt() {
    return startAt;
  }

  Date getEndAt() {
    return endAt;
  }
}
