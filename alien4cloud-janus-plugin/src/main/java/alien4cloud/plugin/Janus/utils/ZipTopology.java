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

import java.io.*;
import java.net.URI;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.*;

import static com.google.common.io.Files.copy;

public class ZipTopology {

    static final String CSAR = "csar/";

    /**
     *
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
     *
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
            String componentName = dirFolders[dirFolders.length-2] + "/";
            String componentVersion = dirFolders[dirFolders.length-1] + "/";

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
                        zout.putNextEntry(new ZipEntry(struct + name));
                        copy(kid, zout);
                    }
                }
            }
        }
        zout.putNextEntry(new ZipEntry("topology.yml"));
        zout.closeEntry();
        res.close();
    }
}
