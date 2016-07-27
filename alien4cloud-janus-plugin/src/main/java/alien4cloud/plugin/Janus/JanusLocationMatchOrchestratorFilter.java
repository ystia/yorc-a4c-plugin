/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.deployment.matching.services.location.ILocationMatchFilter;
import alien4cloud.model.deployment.matching.ILocationMatch;
import alien4cloud.model.topology.Topology;
import alien4cloud.plugin.model.ManagedPlugin;
import lombok.AllArgsConstructor;

import java.util.Iterator;
import java.util.List;

/**
 * Location match filter that will filter on pluginId.
 *
 */
@AllArgsConstructor
public class JanusLocationMatchOrchestratorFilter implements ILocationMatchFilter {

    private ManagedPlugin selfContext;

    @Override
    public void filter(List<ILocationMatch> toFilter, Topology topology) {
        for (Iterator<ILocationMatch> it = toFilter.iterator(); it.hasNext();) {
            ILocationMatch locationMatch = it.next();
            if (!locationMatch.getOrchestrator().getPluginId().equals(selfContext.getPlugin().getId())) {
                it.remove();
            }
        }
    }

}
