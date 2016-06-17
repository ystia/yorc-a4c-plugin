package alien4cloud.plugin.Janus;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.plugin.Janus.utils.ApplicationUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Created by a628490 on 04/05/2016.
 */
@Slf4j
@Component
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/test-context.xml")
public class BlueprintTest {

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    @Resource
    private ApplicationUtil applicationUtil;

    public BlueprintTest() {
    }

    public void buildPaaSDeploymentContext(String appName, String topologyName, String locationName) {
        log.info("topology name : " + topologyName);
        Topology topology = applicationUtil.createAlienApplication(appName, topologyName, locationName);

        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();

        log.info("topology nodeTemplate : " + nodeTemplates.keySet());
        log.info("topology test : " + topology.getDelegateId());

    }
    @Test
    public void main() {
        log.info("Debut du main ");
        buildPaaSDeploymentContext(SINGLE_COMPUTE_TOPOLOGY, SINGLE_COMPUTE_TOPOLOGY,"slurm");
    }

}
