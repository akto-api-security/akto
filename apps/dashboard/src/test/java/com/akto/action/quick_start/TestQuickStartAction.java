package com.akto.action.quick_start;

import org.junit.Test;

import static com.akto.action.quick_start.QuickStartAction.convertStreamToString;
import static org.junit.Assert.assertEquals;

public class TestQuickStartAction {


    @Test
    public void testKubernetesTemplate() throws Exception {
        String template = convertStreamToString(TestQuickStartAction.class.getResourceAsStream("/cloud_formation_templates/kubernetes_setup.yaml"));
        int count = countOccurrences(template, "m5a.xlarge");
        assertEquals(2, count);
    }

    @Test
    public void testMirroringTemplate() throws Exception{
        String template = convertStreamToString(TestQuickStartAction.class.getResourceAsStream("/cloud_formation_templates/akto_aws_mirroring.yaml"));
        int count = countOccurrences(template, "m5a.xlarge");
        assertEquals(2, count);
    }

    private int countOccurrences(String template, String instanceType) {
        int count = 0;
        int index = 0;
        while (index != -1) {
            index = template.indexOf(instanceType, index);
            if (index != -1) {
                count++;
                index += instanceType.length();
            }
        }
        return count;
    }
}
