package org.ystia.yorc.alien4cloud.plugin.service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.serializer.VelocityUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * A {@code ToscaComponentExporter} is a ...
 *
 * @author Loic Albertin
 */
@Component("janus-component-exporter-service")
@Slf4j
public class ToscaComponentExporter {


    /**
     * Get the yaml string out of a cloud service archive.
     *
     * @param archive the parsed archive.
     *
     * @return The TOSCA yaml file that describe the archive in Janus format.
     */
    public String getYaml(ArchiveRoot archive) {
        Map<String, Object> velocityCtx = new HashMap<>();
        velocityCtx.put("archive", archive);
        velocityCtx.put("vtPath", "org/ystia/yorc/alien4cloud/plugin/tosca");
        velocityCtx.put("janusUtils", new ToscaComponentUtils());
        velocityCtx.put("stringsUtils", new StringUtils());
        try {
            StringWriter writer = new StringWriter();
            VelocityUtil.generate("org/ystia/yorc/alien4cloud/plugin/tosca/types.yml.vm", writer, velocityCtx);
            return writer.toString();
        } catch (Exception e) {
            log.error("Exception while templating YAML for archive " + archive.getArchive().getName(), e);
            return ExceptionUtils.getFullStackTrace(e);
        }
    }
}
