package jmri.jmrit.vsdecoder;

import jmri.util.JUnitUtil;

import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 */
public class ToggleSoundEventTest {

    @Test
    public void testCTor() {
        ToggleSoundEvent t = new ToggleSoundEvent("test");
        Assert.assertNotNull("exists",t);
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(ToggleSoundEventTest.class);

}
