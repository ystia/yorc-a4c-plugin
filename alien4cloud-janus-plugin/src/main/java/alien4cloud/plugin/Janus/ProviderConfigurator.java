/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.plugin.IPluginConfigurator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Janus-provider-configurator")
public class ProviderConfigurator implements IPluginConfigurator<ProviderConfig> {

    @Override
    public ProviderConfig getDefaultConfiguration() {
        return null;
    }

    @Override
    public void setConfiguration(ProviderConfig configuration) {
        log.info("In the plugin configurator <" + this.getClass().getName() + ">");
    }
}