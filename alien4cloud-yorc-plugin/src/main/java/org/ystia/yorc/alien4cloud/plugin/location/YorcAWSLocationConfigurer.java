package org.ystia.yorc.alien4cloud.plugin.location;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ComputeContext;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ImageFlavorContext;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Scope("prototype")
public class YorcAWSLocationConfigurer extends AbstractLocationConfigurer
{
  private static final String IMAGE_ID_PROP = "image_id";
  private static final String FLAVOR_ID_PROP = "instance_type";

  @Override
  public List<String> getResourcesTypes() {
    return getAllResourcesTypes();
  }

  @Override
  public Map<String, MatchingConfiguration> getMatchingConfigurations() {
    return getMatchingConfigurations("aws/resources-matching-config.yml");
  }

  @Override
  protected String[] getLocationArchivePaths() {
    return new String[]{"aws/resources"};
  }

  @Override
  public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
    ImageFlavorContext imageContext = resourceGeneratorService.buildContext("yorc.nodes.aws.Image", "id", resourceAccessor);
    ImageFlavorContext flavorContext = resourceGeneratorService.buildContext("yorc.nodes.aws.InstanceType", "id", resourceAccessor);
    boolean canProceed = true;

    if (CollectionUtils.isEmpty(imageContext.getTemplates())) {
      log.warn("At least one configured image resource is required for the auto-configuration");
      canProceed = false;
    }
    if (CollectionUtils.isEmpty(flavorContext.getTemplates())) {
      log.warn("At least one configured flavor resource is required for the auto-configuration");
      canProceed = false;
    }
    if (!canProceed) {
      log.warn("Skipping auto configuration");
      return null;
    }
    ComputeContext computeContext = resourceGeneratorService
                                        .buildComputeContext("yorc.nodes.aws.Compute", null, IMAGE_ID_PROP, FLAVOR_ID_PROP, resourceAccessor);

    return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, null, resourceAccessor);
  }
}
