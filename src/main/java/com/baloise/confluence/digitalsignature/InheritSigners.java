package com.baloise.confluence.digitalsignature;

public enum InheritSigners {
  NONE,
  READERS_AND_WRITERS,
  READERS_ONLY,
  WRITERS_ONLY;

  public static InheritSigners ofValue(String v) {
    try {
      return InheritSigners.valueOf(v.toUpperCase().replaceAll("\\W+", "_"));
    } catch (Exception e) {
      return NONE;
    }
  }
}
