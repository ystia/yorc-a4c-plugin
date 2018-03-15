package org.ystia.yorc.alien4cloud.plugin.service;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ToscaParser;
import org.springframework.stereotype.Component;

/**
 * A {@code BasicToscaParser} extends {@link ToscaParser} to remove all postParsing operations.
 *
 * @author Loic Albertin
 */
@Component("yorc-tosca-parser")
public class BasicToscaParser extends ToscaParser {
    @Override
    protected void postParsing(ArchiveRoot result) {
        // do not post process parsing
    }
}
