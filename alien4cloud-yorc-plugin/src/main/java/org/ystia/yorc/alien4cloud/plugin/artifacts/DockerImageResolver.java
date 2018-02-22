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