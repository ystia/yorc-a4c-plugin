/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package org.ystia.yorc.alien4cloud.plugin;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class for the Yorc PaaS provider plugin for Alien 4 Cloud.
 * Entry point for Alien4cloud
 *
 * @author xBD
 */
@Configuration
@ComponentScan("org.ystia.yorc.alien4cloud.plugin")
public class YorcPluginConfiguration {
}
