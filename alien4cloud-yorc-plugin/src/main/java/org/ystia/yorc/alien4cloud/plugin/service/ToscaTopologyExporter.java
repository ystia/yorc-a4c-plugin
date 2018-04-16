package org.ystia.yorc.alien4cloud.plugin.service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.model.components.CSARSource;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.security.model.User;
import alien4cloud.tosca.serializer.VelocityUtil;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.ArchiveDelegateType;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * A {@code ToscaTopologyExporter} exports a {@link Topology} into a YAML compatible with the Yorc engine DSL.
 *
 * @author Loic Albertin
 */
@Service("yorc-topology-exporter-service")
@Slf4j
public class ToscaTopologyExporter {

    @Resource
    private ICSARRepositorySearchService csarRepoSearchService;

    /**
     * Get the yaml string out of a cloud service archive and topology.
     *
     * @param csar             The csar that contains archive meta-data.
     * @param topology         The topology template within the archive.
     * @param generateWorkflow check if we generate the workflow
     *
     * @return The TOSCA yaml file that describe the topology.
     */
    public String getYaml(Csar csar, Topology topology, boolean generateWorkflow) {
        Map<String, Object> velocityCtx = new HashMap<>();
        velocityCtx.put("topology", topology);
        velocityCtx.put("template_name", csar.getName());
        velocityCtx.put("template_version", csar.getVersion());
        velocityCtx.put("hasCustomWorkflows", hasCustomWorkflows(topology));
        velocityCtx.put("generateWorkflow", generateWorkflow);
        if (csar.getDescription() == null) {
            velocityCtx.put("template_description", "");
        } else {
            velocityCtx.put("template_description", csar.getDescription());
        }
        User loggedUser = AuthorizationUtil.getCurrentUser();
        String author = csar.getTemplateAuthor();
        if (author == null) {
            author = loggedUser != null ? loggedUser.getUsername() : null;
        }
        velocityCtx.put("template_author", author);

        velocityCtx.put("topology_description", topology.getDescription());

        if (topology.getDescription() == null && ArchiveDelegateType.APPLICATION.toString().equals(csar.getDelegateType())) {
            // if the archive has no description let's use the one of the application
            //             Application application = applicationService.getOrFail(csar.getDelegateId());
            //             velocityCtx.put("topology_description", application.getDescription());
            // Too much noise here for just a description
            velocityCtx.put("topology_description", "");
        }
        velocityCtx.put("importsUtils", new ToscaImportsUtils());
        velocityCtx.put("exportUtils", new ToscaExportUtils());
        ClassLoader oldctccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            StringWriter writer = new StringWriter();
            VelocityUtil.generate("org/ystia/yorc/alien4cloud/plugin/tosca/topology-alien_dsl_2_0_0.yml.vm", writer, velocityCtx);
            return writer.toString();
        } catch (Exception e) {
            log.error("Exception while templating YAML for topology " + topology.getId(), e);
            return ExceptionUtils.getFullStackTrace(e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldctccl);
        }
    }

    // check the presence of at least one custom workflow
    private boolean hasCustomWorkflows(Topology topology) {
        for (Workflow wf : safe(topology.getWorkflows()).values()) {
            if (wf.isHasCustomModifications()) {
                return true;
            }
        }
        return false;
    }


    public class ToscaImportsUtils {

        public String generateImports(Set<CSARDependency> dependencies) {
            StringBuilder sb = new StringBuilder();
            dependencies.forEach(d -> {
                if (!"tosca-normative-types".equals(d.getName())) {
                    if (sb.length() != 0) {
                        sb.append("\n  - ");
                    } else {
                        sb.append("  - ");
                    }
                    Csar csar = csarRepoSearchService.getArchive(d.getName(), d.getVersion());
                    final String importSource = csar.getImportSource();
                    // importSource is null when this is a reference to a Service
                    // provided by another deployment
                    if (importSource != null && CSARSource.valueOf(importSource) == CSARSource.ORCHESTRATOR) {
                        sb.append("<").append(d.getName()).append(".yml>");
                    } else {
                        sb.append(d.getName()).append("/").append(d.getVersion()).append("/").append(csar.getYamlFilePath());
                    }
                }
            });
            return sb.toString();
        }
    }
}
