package alien4cloud.plugin.Janus.location;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Scope("prototype")
public class JanusAWSLocationConfigurer extends AbstractLocationConfigurer
{

  @Override
  public List<String> getResourcesTypes() {
    return getAllResourcesTypes();
  }

  @Override
  public Map<String, MatchingConfiguration> getMatchingConfigurations() {
    // does not need matching configuration for now
    return null;
  }

  @Override
  protected String[] getLocationArchivePaths() {
    return new String[]{"aws/resources"};
  }

  @Override
  public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor)
  {
    // does not support auto-config
    return null;
  }
}
