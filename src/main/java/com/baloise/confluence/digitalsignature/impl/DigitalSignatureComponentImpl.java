package com.baloise.confluence.digitalsignature.impl;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.baloise.confluence.digitalsignature.api.DigitalSignatureComponent;

import javax.inject.Inject;
import javax.inject.Named;

@ExportAsService({DigitalSignatureComponent.class})
@Named("digitalSignatureComponent")
public class DigitalSignatureComponentImpl implements DigitalSignatureComponent {
  @ComponentImport
  private final ApplicationProperties applicationProperties;

  public DigitalSignatureComponentImpl() {
    this(null);
  }

  @Inject
  public DigitalSignatureComponentImpl(final ApplicationProperties applicationProperties) {
    this.applicationProperties = applicationProperties;
  }

  public String getName() {
    if (null != applicationProperties) {
      return "digitalSignatureComponent:" + applicationProperties.getDisplayName();
    }

    return "digitalSignatureComponent";
  }
}
