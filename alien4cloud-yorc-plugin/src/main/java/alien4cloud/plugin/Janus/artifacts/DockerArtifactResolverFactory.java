package alien4cloud.plugin.Janus.artifacts;

import alien4cloud.component.repository.IConfigurableArtifactResolver;
import alien4cloud.component.repository.IConfigurableArtifactResolverFactory;

/**
 * When having a DockerImageResolver it is also required to have such bean.
 */
public class DockerArtifactResolverFactory implements IConfigurableArtifactResolverFactory {
    @Override
    public IConfigurableArtifactResolver newInstance() {
        return null;
    }

    @Override
    public Class getResolverConfigurationType() {
        return null;
    }

    @Override
    public String getResolverType() {
        return null;
    }
}
