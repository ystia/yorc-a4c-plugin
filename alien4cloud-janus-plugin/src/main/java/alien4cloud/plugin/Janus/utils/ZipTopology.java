/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.utils;

/**
 * Created by a628490 on 22/07/2016.
 */

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

    static final String CSAR = "csar/";

    /**
     * @param deploymentContext
     * @return
     */
    public List<File> createListTopology(PaaSTopologyDeploymentContext deploymentContext) {
        List<File> files = new LinkedList();
        for (PaaSNodeTemplate node : deploymentContext.getPaaSTopology().getComputes()) {
            String parentPathNode = node.getCsarPath().getParent().toString();
            File fileN = new File(parentPathNode);
            files.add(fileN);
            List<PaaSNodeTemplate> children = node.getChildren();
            for (PaaSNodeTemplate child : children) {
                String parentPathChild = child.getCsarPath().getParent().toString();
//                int index=parentPath.lastIndexOf('/');
//                System.out.println(parentPath.substring(0,index));
                File fileC = new File(parentPathChild);
                files.add(fileC);
            }
        }
        return files;
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
        zout.putNextEntry(new ZipEntry(CSAR));
        for (File directory : folders) {

            //Get info name path for component
            String[] dirFolders = directory.toString().split("/");
            String componentName = dirFolders[dirFolders.length - 2] + "/";
            String componentVersion = dirFolders[dirFolders.length - 1] + "/";

            //create structure of our component folder
            zout.putNextEntry(new ZipEntry(CSAR + componentName));
            zout.putNextEntry(new ZipEntry(CSAR + componentName + componentVersion));

            String struct = CSAR + componentName + componentVersion;

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
                            file = mappingTosca(kid);
                        } else {
                            file = kid;
                        }
                        zout.putNextEntry(new ZipEntry(struct + name));
                        copy(file, zout);
                    }
                }
            }
        }
        zout.putNextEntry(new ZipEntry("topology.yml"));
        zout.closeEntry();
        res.close();
    }

    /**
     *
     * @param yml
     * @return TOSCA file for janus
     * @throws IOException
     */
    public File mappingTosca(File yml) throws IOException {
        File file = new File("tmp.yml");
        // creates the file
        file.createNewFile();
        int cpt = 0;
        log.info("[ZIP]MAPPING TOSCA");
        try (FileWriter fw = new FileWriter(file);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            Scanner sc = new Scanner(yml);
            String scriptRepo = null;
            while (sc.hasNextLine()) {
                String entry = sc.nextLine();
                if(cpt == 2){
                    out.println("      scripts:");
                    scriptRepo = entry;
                    cpt--;
                }else if(cpt == 1){
                    out.println("        file:"+scriptRepo.split(":")[1]);
                    cpt--;
                }
                if(entry.contains("artifacts:")){
                    cpt = 2;
                    out.println(entry);
                }
                if (cpt == 0){
                    out.println(entry);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }
}
