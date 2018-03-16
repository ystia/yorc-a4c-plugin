package org.ystia.yorc.alien4cloud.plugin.service;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ToscaParser;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ConcatPropertyValue;
import org.alien4cloud.tosca.model.definitions.FunctionPropertyValue;
import org.alien4cloud.tosca.model.types.AbstractInstantiableToscaType;
import org.springframework.stereotype.Component;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * A {@code BasicToscaParser} extends {@link ToscaParser} to remove all postParsing operations.
 *
 * @author Loic Albertin
 */
@Component("yorc-tosca-parser")
public class BasicToscaParser extends ToscaParser {
    @Override
    protected void postParsing(ArchiveRoot result) {
        // Change R_TARGET to TARGET keyword
        safe(result.getRelationshipTypes()).forEach(this::postProcessInstantiableType);
        safe(result.getNodeTypes()).forEach(this::postProcessInstantiableType);
    }

    private void postProcessInstantiableType(String typeName, AbstractInstantiableToscaType type) {
        safe(type.getAttributes()).forEach((an, a) -> {
            if (!a.isDefinition() && a instanceof AbstractPropertyValue) {
                postProcessPropVal((AbstractPropertyValue) a);
            }
        });

        safe(type.getInterfaces())
                .forEach((in, i) -> safe(i.getOperations()).forEach((on, o) -> safe(o.getInputParameters()).forEach((ipn, ip) -> {
                    if (!ip.isDefinition() && ip instanceof AbstractPropertyValue) {
                        postProcessPropVal((AbstractPropertyValue) ip);
                    }
                })));

    }

    private void postProcessPropVal(AbstractPropertyValue value) {
        if (value instanceof ConcatPropertyValue) {
            ConcatPropertyValue concatPropertyValue = (ConcatPropertyValue) value;
            safe(concatPropertyValue.getParameters()).forEach(this::postProcessPropVal);
        } else if (value instanceof FunctionPropertyValue) {
            FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) value;
            if ("R_TARGET".equals(functionPropertyValue.getParameters().get(0))) {
                functionPropertyValue.getParameters().set(0, "TARGET");
            }
        }
    }
}
