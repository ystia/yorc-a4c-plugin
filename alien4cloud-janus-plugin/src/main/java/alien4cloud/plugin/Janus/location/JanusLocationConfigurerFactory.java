package alien4cloud.plugin.Janus.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.plugin.Janus.JanusOrchestratorFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class JanusLocationConfigurerFactory extends AbstractLocationConfigurerFactory {

    @Override
    protected ILocationConfiguratorPlugin newInstanceBasedOnLocation(String locationType) {
        if (JanusOrchestratorFactory.OPENSTACK.equals(locationType)) {
            JanusOpenStackLocationConfigurer configurer = applicationContext.getBean(JanusOpenStackLocationConfigurer.class);
            return configurer;
        } else if (JanusOrchestratorFactory.SLURM.equals(locationType)) {
            JanusSlurmLocationConfigurer configurer = applicationContext.getBean(JanusSlurmLocationConfigurer.class);
            return configurer;
        } else if (JanusOrchestratorFactory.KUBERNETES.equals(locationType)) {
            JanusKubernetesLocationConfigurer configurer = applicationContext.getBean(JanusKubernetesLocationConfigurer.class);
            return configurer;
        }
        return null;
    }
}
