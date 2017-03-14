/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.utils;

import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.io.Files.copy;

@Slf4j
public class ZipTopology {

    /**
     * @param deploymentContext
     * @return
     */
    public List<File> createListTopology(PaaSTopologyDeploymentContext deploymentContext) {
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
            System.out.println(parentPathChild);
            files.add(new File(parentPathChild));
            putCsarPathChildrenIntoFiles(child, files);
        }
    }

    /**
     * @param zipfile
     * @param deploymentContext
     * @throws IOException
     */
    public void buildZip(File zipfile, PaaSTopologyDeploymentContext deploymentContext) throws IOException {

        List<File> folders = createListTopology(deploymentContext);
        OutputStream out = new FileOutputStream(zipfile);
        Closeable res;

        ZipOutputStream zout = new ZipOutputStream(out);
        //set the compression method and level
//        zout.setMethod(ZipOutputStream.DEFLATED);
//        zout.setLevel(9);
        res = zout;

        //clean import topology (delete all precedent import)
        cleanImportInTopology();

        if(deploymentContext.getLocations().get("_A4C_ALL").getDependencies().stream().filter(csar -> csar.getName().contains(("slurm"))).findFirst().isPresent()) {
            addImportInTopology("<janus-slurm-types.yml>");
        } else {
            addImportInTopology("<janus-openstack-types.yml>");
        }

        for (File directory : folders) {

            //Get info name path for component
            log.info("Path directory : " + directory.toString());
            String[] dirFolders = directory.toString().split("/");
            String componentName = dirFolders[dirFolders.length - 2] + "/";
            String componentVersion = dirFolders[dirFolders.length - 1] + "/";

            //create structure of our component folder
            log.info(componentName);
            try {
                zout.putNextEntry(new ZipEntry(componentName));
                zout.putNextEntry(new ZipEntry(componentName + componentVersion));

                String struct = componentName + componentVersion;
                // Set it to true after adding imports into the TOSCA definition file
                // corresponding to the component.
                // Normally the TOSCA definition file is the first yaml or yml encountered (its at the highest level in the tree)
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
                            name = name.endsWith("/") ? struct + name : struct + name + "/";
                            zout.putNextEntry(new ZipEntry(name));
                        } else {
                            File file;
                            //we check if the file is a tosca file or not (because there are also json file for example)
                            //MAPPING TOSCA ALIEN -> TOSCA JANUS
                            if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                                String[] parts = kid.getPath().split("runtime/csar/");
                                if (addedImports) {
                                    file = kid;
                                } else {
                                    // This is the TOSCA definition, treate it !!
                                    addImportInTopology(parts[1]);
                                    file = removeLineBetween(kid, "imports:", "node_types:");
                                    addedImports = true;
                                }
                            } else {
                                file = kid;
                            }
                            zout.putNextEntry(new ZipEntry(struct + name));
                            copy(file, zout);
                        }
                    }
                }


            } catch (Exception e) {
                log.info(e.getMessage());
            }


        }
        zout.putNextEntry(new ZipEntry("topology.yml"));
        copy(new File("topology.yml"), zout);
        zout.closeEntry();
        res.close();
    }

     /*TODO REFACTOR cleanImportInTopology AND addImportInTopology => DUPLICATED CODE */
    private void addImportInTopology(String ymlPath) {
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
                    log.debug("add an import to topology.yml :");
                    log.debug("  - path: " + ymlPath);
                    bw.append("  - path: ").append(ymlPath).append("\n");
                }
            }
        } catch (Exception e) {
            return;
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (bw != null)
                    bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Once everything is complete, delete old file..
        File oldFile = new File(oldFileName);
        oldFile.delete();

        // And rename tmp file's name to old file name
        File newFile = new File(tmpFileName);
        newFile.renameTo(oldFile);

    }

    public void cleanImportInTopology() {
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
                    bw.append(line).append("\n");
                    clean = false;
                }
            }
        } catch (Exception e) {
            return;
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (bw != null)
                    bw.close();
            } catch (IOException e) {
                e.printStackTrace();
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

        try (FileWriter fw = new FileWriter(file);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            boolean clean = false;
            Scanner sc = new Scanner(fileToRead);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
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
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;

    }
}
