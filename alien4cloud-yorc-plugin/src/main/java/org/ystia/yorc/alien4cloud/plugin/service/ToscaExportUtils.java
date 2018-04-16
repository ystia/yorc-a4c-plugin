/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ystia.yorc.alien4cloud.plugin.service;

import java.util.Arrays;

import java.util.Collections;
import java.util.HashSet;

import java.util.Set;

import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.ystia.yorc.alien4cloud.plugin.tosca.model.templates.YorcServiceNodeTemplate;

/**
 * Helper class for Velocity generation
 *
 */
public class ToscaExportUtils {

    public static Set<String> getDirectives(NodeTemplate nodeTemplate) {

        Set<String> directives = Collections.emptySet();

        if (nodeTemplate instanceof YorcServiceNodeTemplate) {
            YorcServiceNodeTemplate yorcServiceNodeTemplate = (YorcServiceNodeTemplate) nodeTemplate;
            if (yorcServiceNodeTemplate.getDirectives() != null) {
                directives = new HashSet<String>(Arrays.asList(yorcServiceNodeTemplate.getDirectives()));

            }
        }

        return directives;
    }
}
