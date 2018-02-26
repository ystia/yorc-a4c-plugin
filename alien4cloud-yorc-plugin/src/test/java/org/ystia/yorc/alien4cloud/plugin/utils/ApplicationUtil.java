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
package org.ystia.yorc.alien4cloud.plugin.utils;

import alien4cloud.application.ApplicationService;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.utils.FileUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.ArchiveParser;
import org.alien4cloud.tosca.model.templates.Topology;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class ApplicationUtil {

    @Resource
    private ApplicationService applicationService;

    @Resource
    protected ElasticSearchDAO alienDAO;

    @Resource
    private ArchiveParser parser;

    public boolean isTopologyExistForLocation(String topologyFileName, String locationName) {
        return Files.exists(Paths.get("src/test/resources/topologies/" + locationName + "/" + topologyFileName + ".yaml"));
    }

    @SneakyThrows
    public Topology createAlienApplication(String applicationName, String topologyFileName, String locationName) {
        log.info(applicationName, topologyFileName, locationName);
        Application application = alienDAO.customFind(Application.class, QueryBuilders.termQuery("name", applicationName));
        if (application != null) {
            applicationService.delete(application.getId());
        }
        Topology topology = parseYamlTopology(topologyFileName, locationName);
        String applicationId = applicationService.create("alien", applicationName, null, null);
        topology.setId(applicationId);
        alienDAO.save(topology);
        return topology;
    }

    private Topology parseYamlTopology(String topologyFileName, String locationName) throws IOException, ParsingException {
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(Paths.get("src/test/resources/topologies/" + locationName + "/" + topologyFileName + ".yaml"), zipPath);
        //ParsingResult<ArchiveRoot> parsingResult = parser.parse(zipPath);
        return null;
        //return parsingResult.getResult().getTopology();
    }
}
