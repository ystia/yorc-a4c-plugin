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
package org.ystia.yorc.alien4cloud.plugin;

import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.ystia.yorc.alien4cloud.plugin.utils.ApplicationUtil;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Created by a628490 on 04/05/2016.
 */
@Slf4j
@Component
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/test-context.xml")
public class BlueprintTest {

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    @Resource
    private ApplicationUtil applicationUtil;

    public BlueprintTest() {
    }

    public void buildPaaSDeploymentContext(String appName, String topologyName, String locationName) {
        log.info("topology name : " + topologyName);
        Topology topology = applicationUtil.createAlienApplication(appName, topologyName, locationName);

        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();

        log.info("topology nodeTemplate : " + nodeTemplates.keySet());
        log.info("topology test : " + topology.getId());

    }

    @Test
    public void main() {
        log.info("Debut du main ");
        buildPaaSDeploymentContext(SINGLE_COMPUTE_TOPOLOGY, SINGLE_COMPUTE_TOPOLOGY, "slurm");
    }

}
