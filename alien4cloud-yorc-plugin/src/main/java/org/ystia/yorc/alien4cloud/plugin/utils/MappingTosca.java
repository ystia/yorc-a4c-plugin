/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
