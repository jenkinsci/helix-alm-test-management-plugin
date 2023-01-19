/*
 * *****************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2022, Perforce Software, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * *****************************************************************************
 */

package com.perforce.halm.jenkins.globalconfig;

import hudson.util.FormValidation;
import org.junit.Assert;
import org.junit.Test;

/**
 * unit tests for the HALMConnection code
 */
public class HALMConnectionTest {

    /**
     * Verify that a version that equals the min version is valid
     */
    @Test
    public void testCompareVersions_Equal() {
        int[] minVersion = { 2022, 2, 0 };
        String version = "2022.2.0";

        FormValidation formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNull(formValidation);
    }

    /**
     * Verify that versions older than the min version are invalid
     */
    @Test
    public void testCompareVersions_Old() {
        int[] minVersion = { 2022, 2, 0 };
        String version = "2021.2.0";
        FormValidation formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNotNull(formValidation);
        Assert.assertEquals(FormValidation.Kind.ERROR, formValidation.kind);

        version = "2022.1.0";
        formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNotNull(formValidation);
        Assert.assertEquals(FormValidation.Kind.ERROR, formValidation.kind);

        version = "2022.1.2";
        formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNotNull(formValidation);
        Assert.assertEquals(FormValidation.Kind.ERROR, formValidation.kind);
    }

    /**
     * Verify that versions newer than the min version are valid.
     */
    @Test
    public void testCompareVersions_Newer() {
        int[] minVersion = { 2022, 2, 0 };
        String version = "2022.2.1";
        FormValidation formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNull(formValidation);

        version = "2022.3.0";
        formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNull(formValidation);

        version = "2023.0.0";
        formValidation = HALMConnection.DescriptorImpl.compareVersions(minVersion, version);
        Assert.assertNull(formValidation);
    }
}
