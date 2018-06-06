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
package org.ystia.yorc.alien4cloud.plugin.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.ystia.yorc.alien4cloud.plugin.YstiaOrchestratorFactory;

/**
 * Component that creates location configurer for Yorc.
 */
@Slf4j
@Component
@Scope("prototype")
public class YorcLocationConfigurerFactory extends AbstractLocationConfigurerFactory {

    /**
     * Return a Location Configurer suitable for this location type
     * @param locationType OPENSTACK or SLURM
     * @return ILocationConfiguratorPlugin
     */
    @Override
    protected ILocationConfiguratorPlugin newInstanceBasedOnLocation(String locationType) {
        AbstractLocationConfigurer configurer = null;
        switch (locationType != null ? locationType : "")
        {
            case YstiaOrchestratorFactory.OPENSTACK:
                configurer = applicationContext.getBean(YorcOpenStackLocationConfigurer.class);
                break;
            case YstiaOrchestratorFactory.KUBERNETES:
                configurer = applicationContext.getBean(YorcKubernetesLocationConfigurer.class);
                break;
            case YstiaOrchestratorFactory.SLURM:
                configurer = applicationContext.getBean(YorcSlurmLocationConfigurer.class);
                break;
            case YstiaOrchestratorFactory.AWS:
                configurer = applicationContext.getBean(YorcAWSLocationConfigurer.class);
                break;
            case YstiaOrchestratorFactory.GOOGLE:
                configurer = applicationContext.getBean(YorcGoogleLocationConfigurer.class);
                break;
            case YstiaOrchestratorFactory.HOSTS_POOL:
                configurer = applicationContext.getBean(YorcHostsPoolLocationConfigurer.class);
                break;
            default:
                log.warn("The \"%s\" location type is not handled", locationType);
        }
        return configurer;
    }
}
