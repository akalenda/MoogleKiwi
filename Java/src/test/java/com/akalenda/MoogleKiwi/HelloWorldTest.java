package com.akalenda.MoogleKiwi;

import junit.framework.TestCase;
import org.junit.Assert;

public class HelloWorldTest extends TestCase {

    public void testGet() throws Exception {
        Assert.assertEquals(HelloWorld.get(), "Hello, world!");
    }
}