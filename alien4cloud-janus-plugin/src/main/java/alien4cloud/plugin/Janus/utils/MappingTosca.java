package alien4cloud.plugin.Janus.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import org.alien4cloud.tosca.model.workflow.activities.DelegateWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.CallOperationWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.SetStateWorkflowActivity;

import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.model.workflow.NodeWorkflowStep;


import alien4cloud.paas.wf.util.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.springframework.data.util.Pair;

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
