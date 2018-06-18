package org.ystia.yorc.alien4cloud.plugin.modifiers;

import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.ITopologyModifier;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;
import org.ystia.yorc.alien4cloud.plugin.location.YorcKubernetesLocationConfigurer;

/**
 * Created by danesa on 02/03/18.
 *
 */
@Slf4j
@Component(value = KubernetesTopologyModifier.YORC_KUBERNETES_MODIFIER_TAG)
public class KubernetesTopologyModifier extends TopologyModifierSupport {

    public static final String YORC_KUBERNETES_MODIFIER_TAG = "yorc-kubernetes-modifier";

    /**
     * The below constant's values come from Yorc kubernetes resource types definitions:
     * template_version: ${yorc.types.version}
     */
    protected static final String YORC_KUBERNETES_TYPES_ARCHIVE_NAME = "yorc-kubernetes-types";
    private String yorcKubernetesTypesArchiveVersion = "1.0.0-SNAPSHOT";

    // Yorc K8S resource types
    protected static final String YORC_KUBERNETES_TYPES_DEPLOYMENT_RESOURCE = "yorc.nodes.kubernetes.api.types.DeploymentResource";
    protected static final String YORC_KUBERNETES_TYPES_SERVICE_RESOURCE = "yorc.nodes.kubernetes.api.types.ServiceResource";

    // A4C K8S resource types defined in org.alien4cloud.plugin.kubernetes.modifier
    public static final String K8S_TYPES_DEPLOYMENT_RESOURCE = "org.alien4cloud.kubernetes.api.types.DeploymentResource";
    public static final String K8S_TYPES_SERVICE_RESOURCE = "org.alien4cloud.kubernetes.api.types.ServiceResource";


    @Resource(name="kubernetes-final-modifier")
    ITopologyModifier alien_kubernetes_modifier;

    @Resource
    protected ICSARRepositorySearchService csarRepoService;

    @Inject
    YorcKubernetesLocationConfigurer kubernetesLocationConfigurer;

    @PostConstruct
    public void init() {
        for (PluginArchive pluginArchive : kubernetesLocationConfigurer.pluginArchives()) {
            if (YORC_KUBERNETES_TYPES_ARCHIVE_NAME.equals(pluginArchive.getArchive().getArchive().getName())) {
                yorcKubernetesTypesArchiveVersion = pluginArchive.getArchive().getArchive().getVersion();
            }
        }
    }

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {

        /**
         * Transform a matched K8S topology containing <code>Container</code>s, <code>Deployment</code>s, <code>Service</code>s
         * and replace them with <code>DeploymentResource</code>s and <code>ServiceResource</code>s.
         */
        alien_kubernetes_modifier.process(topology, context);

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {
        log.info("Yorc K8S Plugin : processing topology " + topology.getId());

        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        // Transform alien kubernetes resource types to yorc kubernetes resource types
        // Insure that Yorc kubernetes types archive added as dependency to the topology
        //
        // Treat deployment resource types
        transformKubernetesResourceTypes(topology,  csar, "deployment", yorcKubernetesTypesArchiveVersion);
        // Treat service resource types
        transformKubernetesResourceTypes(topology,  csar, "service", yorcKubernetesTypesArchiveVersion);

    }

    /**
     * Replace alien kubernetes resource nodes by Yorc kubernetes resource nodes
     * The replaceNode ensures that dependencies will be correct.
     * @param topology topology to transform
     * @param csar csar to transform
     * @param resourceType the desired Yorc type
     * @param resourceArchiveVersion the desired Yorc version
     */
    private void transformKubernetesResourceTypes(Topology topology, Csar csar, String resourceType, String resourceArchiveVersion) {
        String sourceResourceType = null;
        String targetResourceType = null;

        log.debug("Yorc K8S Plugin : transform K8S resource type : " + resourceType);
        switch (resourceType) {
            case "service" :
                sourceResourceType = K8S_TYPES_SERVICE_RESOURCE;
                targetResourceType = YORC_KUBERNETES_TYPES_SERVICE_RESOURCE;
                break;
            case "deployment" :
                sourceResourceType = K8S_TYPES_DEPLOYMENT_RESOURCE;
                targetResourceType = YORC_KUBERNETES_TYPES_DEPLOYMENT_RESOURCE;
                break;
            default:
                log.info("Yorc K8S Plugin : currently supported K8S resources are " + "service" + " and " + "deployment");
                break;
        }

        if (sourceResourceType == null || targetResourceType == null) {
            return;
        }

        final String effectiveTargetResourceType = targetResourceType;

        Set<NodeTemplate> serviceNodes = TopologyNavigationUtil.getNodesOfType(topology, sourceResourceType, false);

        serviceNodes.forEach(serviceNodeTemplate -> {

            NodeTemplate yorcServiceNodeTemplate = replaceNode(csar, topology, serviceNodeTemplate, effectiveTargetResourceType, resourceArchiveVersion);

            log.debug("Yorc K8S Plugin : k8s resource node " + yorcServiceNodeTemplate.getName() + " now has type " + yorcServiceNodeTemplate.getType());
        });
    }

}
