package alien4cloud.plugin.Janus.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.plugin.Janus.JanusOrchestratorFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Component that creates location configurer for Janus.
 */
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
        if (JanusOrchestratorFactory.OPENSTACK.equals(locationType)) {
            configurer = applicationContext.getBean(JanusOpenStackLocationConfigurer.class);
        } else if (JanusOrchestratorFactory.SLURM.equals(locationType)) {
            configurer = applicationContext.getBean(JanusSlurmLocationConfigurer.class);
        } else if (JanusOrchestratorFactory.KUBERNETES.equals(locationType)) {
            configurer = applicationContext.getBean(JanusKubernetesLocationConfigurer.class);
        }
        return configurer;
    }
}
