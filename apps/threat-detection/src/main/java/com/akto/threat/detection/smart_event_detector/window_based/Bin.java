package com.akto.threat.detection.smart_event_detector.window_based;

public class Bin {
  int binId;
  long count;

  public Bin(int binId, long count) {
    this.binId = binId;
    this.count = count;
  }

  public int getBinId() {
    return binId;
  }

  public long getCount() {
    return count;
  }
}
