package ut.com.baloise.confluence.digitalsignature;

import com.baloise.confluence.digitalsignature.api.DigitalSignatureComponent;
import com.baloise.confluence.digitalsignature.impl.DigitalSignatureComponentImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MyComponentUnitTest {
  @Test
  void testMyName() {
    DigitalSignatureComponent component = new DigitalSignatureComponentImpl(null);
    Assertions.assertEquals("digitalSignatureComponent", component.getName(), "names do not match!");
  }
}
