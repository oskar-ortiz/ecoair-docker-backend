package com.ecoair.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CorsConfigTest {
  @Test
  void rejectsWildcardWhenCredentialsAreEnabled() {
    assertThrows(IllegalArgumentException.class, () -> new CorsConfig("*"));
  }
}
