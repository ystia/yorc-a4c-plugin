package org.ystia.yorc.alien4cloud.plugin.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
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
     * @return
     */
    @Override
    protected ILocationConfiguratorPlugin newInstanceBasedOnLocation(String locationType) {
        AbstractLocationConfigurer configurer = null;
        switch (locationType)
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
            case YstiaOrchestratorFactory.HOSTS_POOL:
                configurer = applicationContext.getBean(YorcHostsPoolLocationConfigurer.class);
                break;
            default:
                log.warn("The \"%s\" location type is not handled", locationType);
        }
        return configurer;
    }
}
