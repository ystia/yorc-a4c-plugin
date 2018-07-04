package org.ystia.yorc.alien4cloud.plugin.service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import alien4cloud.tosca.serializer.ToscaPropertySerializerUtils;
import alien4cloud.tosca.serializer.VelocityUtil;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.AttributeDefinition;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.definitions.PropertyValue;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A {@code ToscaComponentUtils} is a helper class for Velocity generation
 *
 * @author Loic Albertin
 */
public class ToscaComponentUtils {

    public static String join(Object[] list, String separator) {
        final StringBuilder buffer = new StringBuilder();
        for (Object o : list) {
            if (buffer.length() > 0) {
                buffer.append(separator);
            }
            buffer.append(ToscaPropertySerializerUtils.formatTextValue(0, o.toString()));
        }
        return buffer.toString();
    }

    public static String join(Collection<? extends Object> col, String separator) {
        return join(col.toArray(), separator);
    }


    public static String formatOccurrences(int lower, int upper) {
        StringBuilder sb = new StringBuilder("occurrences: [");
        sb.append(lower).append(", ");
        if (upper == Integer.MAX_VALUE) {
            sb.append("UNBOUNDED");
        } else {
            sb.append(upper);
        }
        sb.append("]");
        return sb.toString();
    }

    public static String formatOperationInputs(int indentLevel, Map<String, ? extends IValue> properties) throws IOException {
        StringBuilder buffer = new StringBuilder();
        for (Map.Entry<String, ? extends IValue> propertyEntry : properties.entrySet()) {
            if (propertyEntry.getValue() != null) {
                if (propertyEntry.getValue() instanceof PropertyValue && ((PropertyValue) propertyEntry.getValue()).getValue() == null) {
                    continue;
                }
                buffer.append("\n").append(ToscaPropertySerializerUtils.indent(indentLevel)).append(propertyEntry.getKey()).append(": ");
                if (!propertyEntry.getValue().isDefinition()) {
                    buffer.append(ToscaPropertySerializerUtils
                            .formatPropertyValue(indentLevel, (AbstractPropertyValue) propertyEntry.getValue()));
                } else {
                    Map<String, Object> velocityContext = ToscaComponentExporter.getVelocityContext();
                    velocityContext.put("indent", indentLevel + 1);
                    String template;
                    if (propertyEntry.getValue() instanceof PropertyDefinition) {
                        velocityContext.put("property", propertyEntry.getValue());
                        template = "org/ystia/yorc/alien4cloud/plugin/tosca/property_def.vm";
                    } else if (propertyEntry.getValue() instanceof AttributeDefinition) {
                        velocityContext.put("attribute", propertyEntry.getValue());
                        template = "org/ystia/yorc/alien4cloud/plugin/tosca/attribute_def.vm";
                    } else {
                        throw new RuntimeException("Unsupported type: " + propertyEntry.getValue().getClass());
                    }
                    StringWriter writer = new StringWriter();
                    VelocityUtil.generate(template, writer, velocityContext);
                    buffer.append("\n").append(ToscaPropertySerializerUtils
                            .indent(indentLevel + 1)).append(writer.toString());
                }
            }
        }
        return buffer.toString();
    }

    public static String shortInterfaceName(String interfaceName) {
        if (ToscaNodeLifecycleConstants.STANDARD.equals(interfaceName)) {
            return ToscaNodeLifecycleConstants.STANDARD_SHORT;
        } else if (ToscaRelationshipLifecycleConstants.CONFIGURE.equals(interfaceName)) {
            return ToscaRelationshipLifecycleConstants.CONFIGURE_SHORT;
        }
        return interfaceName;
    }


    public static boolean canUseShortNotationForOperationImplementation(Operation operation) {
        return StringUtils.isEmpty(operation.getImplementationArtifact().getArtifactType()) &&
                StringUtils.isEmpty(operation.getImplementationArtifact().getRepositoryName());
    }

    /**
     * Check if short notation can be used for implementation artifact
     * ie if no inputs, no repository and no type provided as these information doesn't appear in short notation
     * @param operation
     * @return boolean true if short notation can be used
     */
    public static boolean canUseShortNotationForImplementationArtifact(Operation operation) {
        return MapUtils.isEmpty(operation.getInputParameters()) &&
                StringUtils.isEmpty(operation.getImplementationArtifact().getRepositoryName()) &&
                StringUtils.isEmpty(operation.getImplementationArtifact().getArtifactType());
    }
}
