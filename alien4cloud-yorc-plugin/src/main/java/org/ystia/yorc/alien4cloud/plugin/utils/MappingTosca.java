package org.ystia.yorc.alien4cloud.plugin.utils;

import java.util.Map;

import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;

@Slf4j
public class MappingTosca {


    public static void quoteProperties(final PaaSTopologyDeploymentContext ctx) {
        PaaSTopology ptopo = ctx.getPaaSTopology();

        for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
            NodeTemplate nt = node.getTemplate();

            Map<String, AbstractPropertyValue> ntProperties = nt.getProperties();
            for (String prop : ntProperties.keySet()) {
                AbstractPropertyValue absval = ntProperties.get(prop);
                if (absval instanceof ScalarPropertyValue) {
                    ScalarPropertyValue scaval = (ScalarPropertyValue) absval;
                    if (scaval.getValue().contains("\"")) {
                        scaval.setValue(scaval.getValue().replace("\"", "\\\""));
                    }
                    log.debug("Property: " + prop + "=" + ((ScalarPropertyValue) nt.getProperties().get(prop)).getValue());
                }
            }
        }

    }

}
