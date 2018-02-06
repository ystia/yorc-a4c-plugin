package alien4cloud.plugin.Janus.modifiers;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import alien4cloud.model.orchestrators.locations.LocationModifierReference;
import alien4cloud.orchestrators.locations.events.AfterLocationCreated;
import alien4cloud.orchestrators.locations.services.LocationModifierService;
import alien4cloud.plugin.Janus.JanusOrchestratorFactory;
import alien4cloud.plugin.model.ManagedPlugin;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.modifiers.FlowPhases;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;


/**
 * A {@code LocationCreationListener} is a ...
 *
 * @author Loic Albertin
 */
@Component
@Slf4j
public class LocationCreationListener implements ApplicationListener<AfterLocationCreated> {


    @Inject
    private ManagedPlugin selfContext;

    @Resource
    private LocationModifierService locationModifierService;

    private LocationModifierReference openstackFipModifierRef;

    @PostConstruct
    public synchronized void init() {
        openstackFipModifierRef = new LocationModifierReference();
        openstackFipModifierRef.setPluginId(selfContext.getPlugin().getId());
        openstackFipModifierRef.setBeanName(FipTopologyModifier.JANUS_OPENSTACK_FIP_MODIFIER_TAG);
        openstackFipModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);
    }


    @Override
    public void onApplicationEvent(AfterLocationCreated event) {
       log.debug("Got location creation event for infrastructure type {}", event.getLocation().getInfrastructureType());
       if (JanusOrchestratorFactory.OPENSTACK.equals(event.getLocation().getInfrastructureType())) {
           locationModifierService.add(event.getLocation(), openstackFipModifierRef);
       }
    }
}
