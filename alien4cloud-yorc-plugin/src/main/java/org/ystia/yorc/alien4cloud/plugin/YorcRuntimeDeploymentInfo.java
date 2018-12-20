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

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;

import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
public class YorcRuntimeDeploymentInfo {
    @NonNull
    private PaaSTopologyDeploymentContext deploymentContext;
    @NonNull
    private DeploymentStatus status;
    /**
     * Represents the status of every instance of node templates currently deployed.
     * <p>
     * NodeTemplateId -> InstanceId -> InstanceInformation
     */
    @NonNull
    private Map<String, Map<String, InstanceInformation>> instanceInformations;

    @NonNull
    private String deploymentUrl;

    // Last event received from Yorc
    private Event lastEvent;

    // TaskKey of the current deployment
    private String deployTaskId;


}
