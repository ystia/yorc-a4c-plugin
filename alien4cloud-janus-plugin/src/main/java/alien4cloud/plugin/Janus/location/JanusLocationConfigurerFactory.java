package alien4cloud.plugin.Janus.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.plugin.Janus.JanusOrchestratorFactory;
import alien4cloud.plugin.Janus.location.AbstractLocationConfigurerFactory;
import alien4cloud.plugin.Janus.location.JanusOpenStackLocationConfigurer;
import alien4cloud.plugin.Janus.location.JanusSlurmLocationConfigurer;
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
        }
        return null;
    }
}
