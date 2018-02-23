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
package org.ystia.yorc.alien4cloud.plugin.artifacts;

import alien4cloud.component.repository.IArtifactResolver;
import alien4cloud.repository.model.ValidationResult;
import alien4cloud.repository.model.ValidationStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Artifact resolver to find docker images within a docker repository/hub.
 *
 * Current implementation is faking the actual reposirory support and this will be moved in a separated plugin as this is not directly related to marathon.
 */
 @Component("docker-image-resolver")
public class DockerImageResolver implements IArtifactResolver {

    @Override
    public String getResolverType() {
        return "docker";
    }

    @Override
    public ValidationResult canHandleArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        return getResolverType().equals(repositoryType) ? ValidationResult.SUCCESS : new ValidationResult(ValidationStatus.INVALID_REPOSITORY_TYPE, "");
    }

    @Override
    public String resolveArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        return canHandleArtifact(artifactReference, repositoryURL, repositoryType, credentials) == ValidationResult.SUCCESS ? artifactReference : null;
    }
}