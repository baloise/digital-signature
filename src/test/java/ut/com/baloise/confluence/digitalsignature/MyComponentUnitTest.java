package ut.com.baloise.confluence.digitalsignature;

import org.junit.Test;
import com.baloise.confluence.digitalsignature.api.DigitalSignatureComponent;
import com.baloise.confluence.digitalsignature.impl.DigitalSignatureComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        DigitalSignatureComponent component = new DigitalSignatureComponentImpl(null);
        assertEquals("names do not match!", "digitalSignatureComponent",component.getName());
    }
}
