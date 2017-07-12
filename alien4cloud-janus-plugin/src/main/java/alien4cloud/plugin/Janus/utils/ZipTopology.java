/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.utils;

import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;

import java.nio.file.Path;
import java.io.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static com.google.common.io.Files.copy;

@Slf4j
public class ZipTopology {

    /**
     * Create a list of File, one per component of the topology (PaaSNodeTemplate)
     * @param deploymentContext
     * @return
     */
    private List<File> createListTopology(PaaSTopologyDeploymentContext deploymentContext) {
        List<File> files = new LinkedList();
        for (PaaSNodeTemplate node : deploymentContext.getPaaSTopology().getComputes()) {
            putCsarPathChildrenIntoFiles(node, files);
        }
        return files;
    }


    private void putCsarPathChildrenIntoFiles(PaaSNodeTemplate node, List<File> files) {
        List<PaaSNodeTemplate> children = node.getChildren();
        for (PaaSNodeTemplate child : children) {
            String parentPathChild = child.getCsarPath().getParent().toString();
            files.add(new File(parentPathChild));
            putCsarPathChildrenIntoFiles(child, files);
        }
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

    /**
     * Build the zip file that will be sent to janus at deployment
     * @param zipfile
     * @param deploymentContext
     * @throws IOException
     */
    public void buildZip(File zipfile, PaaSTopologyDeploymentContext deploymentContext) throws IOException {

        OutputStream out = new FileOutputStream(zipfile);
        ZipOutputStream zout = new ZipOutputStream(out);
        // set the compression method and level
        // zout.setMethod(ZipOutputStream.DEFLATED);
        // zout.setLevel(9);
        Closeable res = zout;

        // remove input section
        removeInputInTopology();

        // clean import topology (delete all precedent import)
        cleanImportInTopology();

        if (deploymentContext.getLocations().get("_A4C_ALL").getDependencies().stream().filter(csar -> csar.getName().contains(("slurm"))).findFirst().isPresent()) {
            addImportInTopology("<janus-slurm-types.yml>");
        } else {
            addImportInTopology("<janus-openstack-types.yml>");
        }

        List<File> folders = createListTopology(deploymentContext);
        for (File directory : folders) {

            // Get info name path for component
            log.info("Path directory : " + directory.toString());
            String[] dirFolders = directory.toString().split("/");
            String componentName = dirFolders[dirFolders.length - 2] + "/";
            String componentVersion = dirFolders[dirFolders.length - 1] + "/";
            String struct = componentName + componentVersion;

            // create structure of our component folder
            try {
                //createZipEntries(struct, zout);

                // Set it to true after adding imports into the TOSCA definition file
                // corresponding to the component.
                // Normally the TOSCA definition file is the first yaml or yml encountered (it is at the highest level in the tree)
                boolean addedImports = false;

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
                            createZipEntries(struct + name, zout);
                            if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                                // MAPPING TOSCA ALIEN -> TOSCA JANUS
                                String[] parts = kid.getPath().split("runtime/csar/");
                                if (! addedImports) {
                                    log.debug("processing TOSCA " + name);
                                    addImportInTopology(parts[1]);
                                    file = removeLineBetween(kid, "imports:", "node_types:");
                                    addedImports = true;
                                }
                            }
                            copy(file, zout);
                        }
                    }
                }
            } catch (Exception e) {
                log.info(e.getMessage());
            }
        }
        // Copy overwritten artifacts for each node
        PaaSTopology ptopo = deploymentContext.getPaaSTopology();
        for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
            copyArtifacts(node, zout);
        }

        // Copy modified topology
        String filename = "topology.yml";
        createZipEntries(filename, zout);
        copy(new File(filename), zout);

        zout.closeEntry();
        res.close();
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
                    createZipEntries(filename, zout);
                    copy(artifactPath.toFile(), zout);
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
                    createZipEntries(filename, zout);
                    copy(artifactPath.toFile(), zout);
                } catch (Exception e) {
                    log.error("Could not copy remote artifact " + aname, e);
                }
                // Workaround for a bug in a4c: artifact not added in topology.yml
                // TODO Remove this when a4c bug is fixed.
                addRemoteArtifactInTopology(name, da.getKey(), artifact);
            }
        }
    }

    /**
     * Workaround for a4c issue: SUPALIEN-926
     // TODO Remove this when a4c bug is fixed.
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
     * Add an import in topology.yml
     * @param ymlPath path to be imported
     */
    private void addImportInTopology(String ymlPath) {
        log.debug("");
        String oldFileName = "topology.yml";
        String tmpFileName = "tmp_topology.yml";

        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(tmpFileName));
            br = new BufferedReader(new FileReader(oldFileName));
            String line;
            while ((line = br.readLine()) != null) {
                bw.append(line).append("\n");
                if (line.contains("imports:")) {
                    log.debug("add an import to topology.yml : " + ymlPath);
                    bw.append("  - path: ").append(ymlPath).append("\n");
                }
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
     * Remove input section in topology
     * Inputs has been already processed by alien4cloud
     * This is a workaround for a bug in alien4cloud 1.3
     */
    private void removeInputInTopology() {
        log.debug("");
        String oldFileName = "topology.yml";
        String tmpFileName = "tmp1_topology.yml";

        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(tmpFileName));
            br = new BufferedReader(new FileReader(oldFileName));
            String line;
            boolean clean = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("inputs:")) {
                    clean = true;
                    continue;
                }
                if (line.contains("node_templates:")) {
                    clean = false;
                }
                if (!clean) {
                    bw.append(line).append("\n");
                }
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
     * Remove all imports in topology.yml
     */
    private void cleanImportInTopology() {
        log.debug("");
        String oldFileName = "topology.yml";
        String tmpFileName = "tmp_topology.yml";

        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(tmpFileName));
            br = new BufferedReader(new FileReader(oldFileName));
            String line;
            boolean clean = false;
            while ((line = br.readLine()) != null) {
                if (!clean) {
                    bw.append(line).append("\n");
                }
                if (line.contains("imports:")) {
                    clean = true;
                } else if (line.contains("topology_template:")) {
                    bw.append("\n").append(line).append("\n");
                    clean = false;
                }
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

    private File removeLineBetween(File fileToRead, String begin, String end) throws IOException {
        File file = new File("tmp.yml");
        file.createNewFile();

        FileWriter fw = new FileWriter(file);
        BufferedWriter bw = new BufferedWriter(fw);
        PrintWriter out = new PrintWriter(bw);

        FileReader fr = new FileReader(fileToRead);
        BufferedReader fin = new  BufferedReader(fr);
        String line;
        boolean clean = false;
        for (; ; ) {
            // Read a line.
            line = fin.readLine();
            if (line == null) {
                break;
            }
            if (!clean) {
                out.println(line);
            }
            if (line.contains(begin)) {
                clean = true;
            } else if (line.contains(end)) {
                out.println(line);
                clean = false;
            }
        }
        out.close();
        return file;
    }
}
