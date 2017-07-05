package it.com.baloise.confluence.digitalsignature;

import org.junit.Test;
import org.junit.runner.RunWith;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import com.baloise.confluence.digitalsignature.api.DigitalSignatureComponent;
import com.atlassian.sal.api.ApplicationProperties;

import static org.junit.Assert.assertEquals;

@RunWith(AtlassianPluginsTestRunner.class)
public class MyComponentWiredTest
{
    private final ApplicationProperties applicationProperties;
    private final DigitalSignatureComponent digitalSignatureComponent;

    public MyComponentWiredTest(ApplicationProperties applicationProperties,DigitalSignatureComponent digitalSignatureComponent)
    {
        this.applicationProperties = applicationProperties;
        this.digitalSignatureComponent = digitalSignatureComponent;
    }

    @Test
    public void testMyName()
    {
        assertEquals("names do not match!", "digitalSignature:" + applicationProperties.getDisplayName(),digitalSignatureComponent.getName());
    }
}