package alien4cloud.plugin.Janus.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.plugin.Janus.JanusOrchestratorFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Component that creates location configurer for Janus.
 */
@Slf4j
@Component
@Scope("prototype")
public class JanusLocationConfigurerFactory extends AbstractLocationConfigurerFactory {

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
            case JanusOrchestratorFactory.OPENSTACK:
                configurer = applicationContext.getBean(JanusOpenStackLocationConfigurer.class);
                break;
            case JanusOrchestratorFactory.KUBERNETES:
                configurer = applicationContext.getBean(JanusKubernetesLocationConfigurer.class);
                break;
            case JanusOrchestratorFactory.SLURM:
                configurer = applicationContext.getBean(JanusSlurmLocationConfigurer.class);
                break;
            case JanusOrchestratorFactory.AWS:
                configurer = applicationContext.getBean(JanusAWSLocationConfigurer.class);
                break;
            default:
                log.warn("The \"%s\" location type is not handled", locationType);
        }
        return configurer;
    }
}
