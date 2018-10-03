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
package org.ystia.yorc.alien4cloud.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.model.common.Tag;
import alien4cloud.model.components.CSARSource;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.utils.MapUtil;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.elasticsearch.common.collect.Maps;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;
import org.ystia.yorc.alien4cloud.plugin.tosca.model.templates.YorcServiceNodeTemplate;
import org.ystia.yorc.alien4cloud.plugin.rest.YorcRestException;
import org.ystia.yorc.alien4cloud.plugin.utils.ShowTopology;

import static com.google.common.io.Files.copy;


/**
 * deployment task
 */
@Slf4j
public class DeployTask extends AlienTask {
    // Needed Info
    PaaSTopologyDeploymentContext ctx;
    IPaaSCallback<?> callback;
    private ICSARRepositorySearchService csarRepoSearchService;

    private final int YORC_DEPLOY_TIMEOUT = 1000 * 3600 * 24;  // 24 hours

    public DeployTask(PaaSTopologyDeploymentContext ctx, YorcPaaSProvider prov, IPaaSCallback<?> callback,
            ICSARRepositorySearchService csarRepoSearchService) {
        super(prov);
        this.ctx = ctx;
        this.callback = callback;
        this.csarRepoSearchService = csarRepoSearchService;
    }

    /**
     * Execute the Deployment
     */
    public void run() {
        Throwable error = null;

        // Keep Ids in a Map
        String paasId = ctx.getDeploymentPaaSId();
        String alienId = ctx.getDeploymentId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("Deploying " + paasId + "with id : " + alienId);
        orchestrator.putDeploymentId(paasId, alienId);

        // Init Deployment Info from topology
        DeploymentTopology dtopo = ctx.getDeploymentTopology();

        // Build Monitoring tags for computes
        buildComputeMonitoringTags(dtopo, ctx.getPaaSTopology());

        Map<String, Map<String, InstanceInformation>> curinfo = setupInstanceInformations(dtopo);
        YorcRuntimeDeploymentInfo jrdi = new YorcRuntimeDeploymentInfo(ctx, DeploymentStatus.INIT_DEPLOYMENT, curinfo, deploymentUrl);
        orchestrator.putDeploymentInfo(paasId, jrdi);
        orchestrator.doChangeStatus(paasId, DeploymentStatus.INIT_DEPLOYMENT);

        // Show Topoloy for debug
        //ShowTopology.topologyInLog(ctx);
        //MappingTosca.quoteProperties(ctx);

        // This operation must be synchronized, because it uses the same files topology.yml and topology.zip
        String taskUrl;
        synchronized(this) {

            // Create the yml of our topology and build our zip topology
            try {
                buildZip(ctx);
            } catch (Throwable e) {
                orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }

            // put topology zip to Yorc
            log.info("PUT Topology to Yorc");
            try {
                taskUrl = restClient.sendTopologyToYorc(paasId);
            } catch (Exception e) {
                orchestrator.sendMessage(paasId, "Deployment not accepted by Yorc: " + e.getMessage());
                orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        jrdi.setDeployTaskId(taskId);
        orchestrator.sendMessage(paasId, "Deployment sent to Yorc. TaskKey=" + taskId);

        // wait for Yorc deployment completion
        boolean done = false;
        long timeout = System.currentTimeMillis() + YORC_DEPLOY_TIMEOUT;
        Event evt;
        while (!done && error == null) {
            synchronized (jrdi) {
                // Check deployment timeout
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Deployment Timeout occured");
                    error = new Throwable("Deployment timeout");
                    orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                    break;
                }
                // Wait Deployment Events from Yorc
                log.debug(paasId + ": Waiting for deployment events.");
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for deployment");
                }
                // Check if we received a Deployment Event and process it
                evt = jrdi.getLastEvent();
                if (evt != null && evt.getType().equals(EventListenerTask.EVT_DEPLOYMENT)) {
                    jrdi.setLastEvent(null);
                    switch (evt.getStatus()) {
                        case "deployment_failed":
                            log.warn("Deployment failed: " + paasId);
                            orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                            error = new Exception("Deployment failed");
                            break;
                        case "deployed":
                            log.debug("Deployment success: " + paasId);
                            orchestrator.doChangeStatus(paasId, DeploymentStatus.DEPLOYED);
                            done = true;
                            break;
                        case "deployment_in_progress":
                            orchestrator.doChangeStatus(paasId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
                            break;
                        default:
                            orchestrator.sendMessage(paasId, "Deployment status = " + evt.getStatus());
                            break;
                    }
                    continue;
                }
            }
            // We were awaken for some bad reason or a timeout
            // Check Deployment Status to decide what to do now.
            String status;
            try {
                status = restClient.getStatusFromYorc(deploymentUrl);
            }
            catch(YorcRestException jre) {
                if (jre.getHttpStatusCode() == 404) {
                    // assumes it is undeployed
                    status = "UNDEPLOYED";
                } else {
                    log.error("yorc deployment returned an exception: " + jre.getMessage());
                    error = jre;
                    break;
                }

            }
            catch (Exception e) {
                log.error("yorc deployment returned an exception: " + e.getMessage());
                error = e;
                break;
            }
            switch (status) {
                case "UNDEPLOYED":
                    orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                    error = new Throwable("Deployment has been undeployed");
                    break;
                case "DEPLOYED":
                    // Deployment is OK.
                    orchestrator.changeStatus(paasId, DeploymentStatus.DEPLOYED);
                    done = true;
                    break;
                default:
                    log.debug("Deployment Status is currently " + status);
                    break;
            }
        }
        synchronized (jrdi) {
            // Task is ended: Must remove the taskId and notify a possible undeploy waiting for it.
            jrdi.setDeployTaskId(null);
            jrdi.notify();
        }
        // Return result to a4c
        if (error == null) {
            callback.onSuccess(null);
        } else {
            callback.onFailure(error);
        }
    }

    // supported locations
    private final int LOC_OPENSTACK = 1;
    private final int LOC_KUBERNETES = 2;
    private final int LOC_AWS = 3;
    private final int LOC_SLURM = 4;

    /**
     * Create the zip for yorc, with a modified yaml and all needed archives.
     * Assumes a file original.yml exists in the current directory
     * @param ctx all needed information about the deployment
     * @throws IOException
     */
    private void buildZip(PaaSTopologyDeploymentContext ctx) throws IOException {
        // Check location
        int location = LOC_OPENSTACK;
        Location loc = ctx.getLocations().get("_A4C_ALL");
        Set<CSARDependency> locdeps = loc.getDependencies();
        for (CSARDependency dep : locdeps) {
            if (dep.getName().contains("kubernetes")) {
                location = LOC_KUBERNETES;
                break;
            }
            if (dep.getName().contains("slurm")) {
                location = LOC_SLURM;
                break;
            }
            if (dep.getName().contains("aws")) {
                location = LOC_AWS;
                break;
            }
        }

        // Final zip file will be named topology.zip
        final int finalLocation = location;
        try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream("topology.zip")))
        {
            this.ctx.getDeploymentTopology().getDependencies().forEach(d -> {
                if (!"tosca-normative-types".equals(d.getName())) {
                    Csar csar = csarRepoSearchService.getArchive(d.getName(), d.getVersion());
                    final String importSource = csar.getImportSource();
                    // importSource is null when this is a reference to a Service
                    // provided by another deployment
                    if (importSource == null || CSARSource.ORCHESTRATOR != CSARSource.valueOf(importSource)) {
                        try {
                            csar2zip(zout, csar, finalLocation);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            for (Entry<String, NodeTemplate> nodeTemplateEntry :  this.ctx.getDeploymentTopology().getNodeTemplates().entrySet()) {
                if (nodeTemplateEntry.getValue() instanceof ServiceNodeTemplate) {
                    // Define a service node with a directive to the orchestrator
                    // that this Node Template is substitutable
                    YorcServiceNodeTemplate yorcServiceNodeTemplate =
                            new YorcServiceNodeTemplate((ServiceNodeTemplate)nodeTemplateEntry.getValue());
                    nodeTemplateEntry.setValue(yorcServiceNodeTemplate);
                }
            }

            // Copy overwritten artifacts for each node
            PaaSTopology ptopo = ctx.getPaaSTopology();
            for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
                copyArtifacts(node, zout);
            }

            String topoFileName = "topology.yml";
            // Copy modified topology
            createZipEntries(topoFileName, zout);
            // Get the yaml of the application as built by from a4c
            DeploymentTopology dtopo = ctx.getDeploymentTopology();
            Csar myCsar = new Csar(dtopo.getArchiveName(), dtopo.getArchiveVersion());
            myCsar.setToscaDefinitionsVersion(ToscaParser.LATEST_DSL);
            String yaml = orchestrator.getToscaTopologyExporter().getYaml(myCsar, dtopo, true);
            zout.write(yaml.getBytes(Charset.forName("UTF-8")));
            zout.closeEntry();
        }
    }

    private Object getNestedValue(Map<String, Object> map, String path) {
        String[] parts = path.split(".");
        Object v = map;
        for (String s : parts) {
            if (v == null) return null;
            v = ((Map<String, Object>)v).get(s);
        }
        return v;
    }

    private void setNestedValue(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split(".");
        Object v = map;
        for (int i = 0; i < parts.length-1; i++) {
            v = ((Map<String, Object>)v).get(parts[i]);
        }
        if (v != null) {
            ((Map<String, Object>)v).put(parts[parts.length-1], value);
        }
    }

    private void matchKubernetesImplementation(ArchiveRoot root) {
        root.getNodeTypes().forEach((k, t) -> {
            Map<String,  Interface> interfaces = t.getInterfaces();
            if (interfaces != null) {
                Interface ifce = interfaces.get("tosca.interfaces.node.lifecycle.Standard");
                if (ifce != null) {
                    Operation start = ifce.getOperations().get("start");
                    if (start != null && start.getImplementationArtifact() != null) {
                        String implArtifactType = start.getImplementationArtifact().getArtifactType();
                        // Check implementation artifact type Not null to avoid NPE
                        if (implArtifactType != null) {
                            if (implArtifactType.equals("tosca.artifacts.Deployment.Image.Container.Docker")) {
                                start.getImplementationArtifact().setArtifactType("tosca.artifacts.Deployment.Image.Container.Docker.Kubernetes");
                            }
                        } //else {
                        //System.out.println("Found start implementation artifcat with type NULL : " + start.getImplementationArtifact().toString());
                        // The implementation artifact with type null was :
                        // ImplementationArtifact{} AbstractArtifact{artifactType='null', artifactRef='scripts/kubectl_endpoint_start.sh', artifactRepository='null', archiveName='null', archiveVersion='null', repositoryURL='null', repositoryName='null', artifactPath=null}
                        //}
                    }
                }
            }
        });
    }
    private void matchKubernetesImplementation(Map<String, Object> topology) {
        Map<String, HashMap> nodeTypes = ((Map) topology.get("node_types"));

        nodeTypes.forEach((k,nodeType)->{
            String t = (String) getNestedValue(nodeType, "interfaces.Standard.start.implementation.type");
            if (t.equals("tosca.artifacts.Deployment.Image.Container.Docker")) {
                setNestedValue(nodeType, "interfaces.Standard.start.implementation.type", "tosca.artifacts.Deployment.Image.Container.Docker.Kubernetes");
            }
        });
    }

    /**
     * Copy artifacts to archive
     * @param node
     * @param zout
     */
    private void copyArtifacts(PaaSNodeTemplate node, ZipOutputStream zout) {
        String name = node.getId();

        // Check if this component has artifacts
        Map<String, DeploymentArtifact> map = node.getTemplate().getArtifacts();
        if (map == null) {
            log.debug("Component with no artifact: " + name);
            return;
        }

        // Process each artifact
        for (Map.Entry<String, DeploymentArtifact> da : map.entrySet()) {
            String aname =  name + "/" + da.getKey();
            DeploymentArtifact artifact = da.getValue();
            String artRepo = artifact.getArtifactRepository();
            if (artRepo == null) {
                continue;
            }
            ShowTopology.printArtifact(artifact);
            if  (artRepo.equals(ArtifactRepositoryConstants.ALIEN_TOPOLOGY_REPOSITORY)) {
                // Copy artifact from topology repository to the root of archive.
                String from = artifact.getArtifactPath();
                log.debug("Copying local artifact: " + aname + " path=" + from);
                Path artifactPath = Paths.get(from);
                try {
                    String filename = artifact.getArtifactRef();
                    recursivelyCopyArtifact(artifactPath, filename, zout);
                } catch (Exception e) {
                    log.error("Could not copy local artifact " + aname, e);
                }
            } else {
                // Copy remote artifact
                String from = artifact.getArtifactPath();
                log.debug("Copying remote artifact: " + aname + " path=" + from);
                Path artifactPath = Paths.get(from);
                try {
                    String filename = artifact.getArtifactRef();
                    recursivelyCopyArtifact(artifactPath, filename, zout);
                } catch (Exception e) {
                    log.error("Could not copy remote artifact " + aname, e);
                }
                // Workaround for a bug in a4c: artifact not added in topology.yml
                // TODO Remove this when a4c bug SUPALIEN-926 is fixed.
                addRemoteArtifactInTopology(name, da.getKey(), artifact);
            }
        }
    }

    private void recursivelyCopyArtifact(Path path, String baseTargetName, ZipOutputStream zout) throws IOException {
        if (path.toFile().isDirectory()) {
            String folderName = baseTargetName + "/";
            createZipEntries(folderName, zout);
            for (String file : path.toFile().list()) {
                Path filePath = path.resolve(file);
                recursivelyCopyArtifact(filePath, folderName + file, zout);
            }
        } else {
            createZipEntries(baseTargetName, zout);
            copy(path.toFile(), zout);
        }
    }

    /**
     * Workaround for a4c issue: SUPALIEN-926
     * TODO Remove this when a4c bug is fixed. (planned for 1.5)
     * @param node Node Name
     * @param key Name of the artifact
     * @param da
     */
    private void addRemoteArtifactInTopology(String node, String key, DeploymentArtifact da) {
        log.debug("");
        String oldFileName = "topology.yml";
        String tmpFileName = "tmp_topology.yml";

        log.debug("Add remote artifact in topology (workaround for SUPALIEN-926)");
        log.debug(node + " " + key + " : " + da.getArtifactRef() + " - " + da.getArtifactType());

        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(tmpFileName));
            br = new BufferedReader(new FileReader(oldFileName));
            String line;
            boolean inNode = false;
            boolean done = false;
            while ((line = br.readLine()) != null) {
                if (! done) {
                    if (line.startsWith("    " + node + ":")) {
                        inNode = true;
                        bw.append(line).append("\n");
                        continue;
                    }
                    if (! inNode) {
                        bw.append(line).append("\n");
                        continue;
                    }
                    if (! line.startsWith("      ")) {
                        bw.append("      artifacts:\n");
                        // Add here the 3 lines to describe the remote artifact
                        String l1 = "        " + key + ":\n";
                        String l2 = "          file: " + da.getArtifactRef() + "\n";
                        String l3 = "          type: " + da.getArtifactType() + "\n";
                        bw.append(l1).append(l2).append(l3);
                        done = true;
                        bw.append(line).append("\n");
                        continue;
                    }
                    if (line.startsWith("      artifacts:")) {
                        bw.append(line).append("\n");
                        // Add here the 3 lines to describe the remote artifact
                        String l1 = "        " + key + ":\n";
                        String l2 = "          file: " + da.getArtifactRef() + "\n";
                        String l3 = "          type: " + da.getArtifactType() + "\n";
                        bw.append(l1).append(l2).append(l3);
                        done = true;
                        continue;
                    }
                }
                bw.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("Error while modifying topology.yml");
            return;
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException e) {
                log.error("Error closing " + oldFileName, e);
            }
            try {
                if (bw != null)
                    bw.close();
            } catch (IOException e) {
                log.error("Error closing " + tmpFileName, e);
            }
        }
        // Once everything is complete, delete old file..
        File oldFile = new File(oldFileName);
        oldFile.delete();

        // And rename tmp file's name to old file name
        File newFile = new File(tmpFileName);
        newFile.renameTo(oldFile);
    }

    /**
     * Get csar and add entries in zip file for it
     * @return relative path to the yml, ex: welcome-types/3.0-SNAPSHOT/welcome-types.yaml
     */
    private String csar2zip(ZipOutputStream zout, Csar csar, int location) throws IOException, ParsingException {
        // Get path directory to the needed info:
        // should be something like: ...../runtime/csar/<module>/<version>/expanded
        // We should have a yml or a yaml here
        Path csarPath = orchestrator.getCSAR(csar.getName(), csar.getVersion());
        String dirname = csarPath.toString();
        File directory = new File(dirname);
        String relative = csar.getName() + "/" + csar.getVersion() + "/";
        String ret = relative + csar.getYamlFilePath();
            // All files under this directory must be put in the zip
            URI base = directory.toURI();
            Deque<File> queue = new LinkedList<>();
            queue.push(directory);
            while (!queue.isEmpty()) {
                directory = queue.pop();
                for (File kid : directory.listFiles()) {
                    String name = base.relativize(kid.toURI()).getPath();
                    if (kid.isDirectory()) {
                        queue.push(kid);
                    } else {
                        File file = kid;
                        createZipEntries(relative + name, zout);
                        if (name.equals(csar.getYamlFilePath())) {
                            ToscaContext.Context oldCtx = ToscaContext.get();
                            ParsingResult<ArchiveRoot> parsingResult;
                            try {
                                ToscaContext.init(Sets.newHashSet());
                                parsingResult = orchestrator.getParser().parseFile(Paths.get(file.getAbsolutePath()));
                            } finally {
                                ToscaContext.set(oldCtx);
                            }
                            if (parsingResult.getContext().getParsingErrors().size() > 0) {
                                Boolean hasFatalError = false;
                                for (ParsingError error :
                                        parsingResult.getContext().getParsingErrors()) {
                                    if (error.getErrorLevel().equals(ParsingErrorLevel.ERROR)) {
                                        log.error(error.toString());
                                        hasFatalError = true;
                                    } else {
                                        log.warn(error.toString());
                                    }
                                }
                                if (hasFatalError) {
                                    continue;
                                }
                            }
                            ArchiveRoot root = parsingResult.getResult();
                            if (location == LOC_KUBERNETES) {
                                matchKubernetesImplementation(root);
                            }

                            String yaml;
                            if (root.hasToscaTopologyTemplate()) {
                                log.debug("File has topology template : " + name);
                                yaml = orchestrator.getToscaTopologyExporter().getYaml(csar, root.getTopology(), false);
                            } else {
                                yaml = orchestrator.getToscaComponentExporter().getYaml(root);
                            }

                            zout.write(yaml.getBytes(Charset.forName("UTF-8")));
                        } else {
                            copy(file, zout);
                        }
                    }
                }
            }
        return ret;
    }

    /**
     * Add all ZipEntry for this file path
     * If path is a directory, it must be ended by a "/".
     * All directory entries must be ended by a "/", and all simple file entries must be not.
     * TODO use this method everywhere
     * @param fullpath
     * @param zout
     */
    private void createZipEntries(String fullpath, ZipOutputStream zout) throws IOException {
        log.debug("createZipEntries for " + fullpath);
        int index = 0;
        String name = "";
        while (name.length() < fullpath.length()) {
            index = fullpath.indexOf("/", index) + 1;
            if (index <= 1) {
                name = fullpath;
            } else {
                name = fullpath.substring(0, index);
            }
            try {
                zout.putNextEntry(new ZipEntry(name));
                log.debug("new ZipEntry: " + name);
            } catch (ZipException e) {
                if (e.getMessage().contains("duplicate")) {
                    //log.debug("ZipEntry already added: " + name);
                } else {
                    log.error("Cannot add ZipEntry: " + name, e);
                    throw e;
                }
            }
        }
    }

    private Map<String, Map<String, InstanceInformation>> setupInstanceInformations(Topology topology) {
        log.debug("setupInstanceInformations for " + topology.getArchiveName() + " : " + topology.getArchiveVersion());
        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
        if (nodeTemplates == null) {
            nodeTemplates = Maps.newHashMap();
        }
        Map<String, Map<String, InstanceInformation>> currentInformations = Maps.newHashMap();
        for (Entry<String, NodeTemplate> nodeTemplateEntry : nodeTemplates.entrySet()) {
            Map<String, InstanceInformation> instanceInformations = Maps.newHashMap();
            currentInformations.put(nodeTemplateEntry.getKey(), instanceInformations);
            int initialInstances = 1;
            for (int i = 0; i < initialInstances; i++) {
                InstanceInformation newInstanceInformation = orchestrator.newInstance(i);
                instanceInformations.put(String.valueOf(i), newInstanceInformation);
            }
        }
        return currentInformations;
    }

    private void buildComputeMonitoringTags(final DeploymentTopology depTopology, PaaSTopology ptopo) {
        Map<String, String> depProps = depTopology.getProviderDeploymentProperties();
        if (depProps != null && !depProps.isEmpty()) {
            // Check for Monitoring time interval : it enables monitoring only if time interval is > 0
            String monitoringIntervalStr = (String) MapUtil.get(depProps, YstiaOrchestratorFactory.MONITORING_TIME_INTERVAL);
            log.debug("monitoringIntervalStr='" + monitoringIntervalStr + "'");
            if (monitoringIntervalStr != "") {
                int monitoringInterval = 0;
                // No blocking error for deployment
                try {
                    monitoringInterval = Integer.parseInt(monitoringIntervalStr);
                } catch(NumberFormatException nfe) {
                    log.error(String.format("Failed to parse number from value=\"%s\". No monitoring will be planned but deployment goes on.", monitoringIntervalStr));
                    return;
                }
                if (monitoringInterval > 0) {
                    List<Tag> tags = new ArrayList<>();
                    Tag tag = new Tag();
                    tag.setName(YstiaOrchestratorFactory.MONITORING_TIME_INTERVAL);
                    tag.setValue(monitoringIntervalStr);
                    tags.add(tag);
                    for (PaaSNodeTemplate compute : ptopo.getComputes()) {
                        compute.getTemplate().setTags(tags);
                    }
                }
            }
        }
    }
}
