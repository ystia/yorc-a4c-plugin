/**
 * Copyright 2019 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
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


import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Update deployment task
 */
@Slf4j
public class UpdateTask extends DeployTask {

    public UpdateTask(PaaSTopologyDeploymentContext ctx, YorcPaaSProvider prov, IPaaSCallback<?> callback,
            ICSARRepositorySearchService csarRepoSearchService) {
        super(ctx, prov, callback, csarRepoSearchService);
    }

    /**
     * Update the Deployment
     */
    public void run() {
        String paasId = ctx.getDeploymentPaaSId();
        String alienId = ctx.getDeploymentId();

        log.info("Updating deployment" + paasId + "with id : " + alienId);
        deploy(paasId, alienId);
    }

    // Overriding parent class method to set a UPDATE_FAILURE status
    // when the operation fails before even being run by the orchestrator
    protected void changeStatusToFailure(String paasId) {
        orchestrator.doChangeStatus(paasId, DeploymentStatus.UPDATE_FAILURE);
    }

}
