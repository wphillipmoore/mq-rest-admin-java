package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackageInfoTest {

  @Test
  void packageExists() {
    Package pkg = PackageInfoTest.class.getPackage();
    assertThat(pkg).isNotNull();
    assertThat(pkg.getName()).isEqualTo("io.github.wphillipmoore.mq.rest.admin");
  }
}
