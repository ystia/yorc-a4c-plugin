/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.model.common.Tag;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@FormProperties({"urlJanus", "tags", "properties", "javaVersion"})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("PMD.UnusedPrivateField")
public class ProviderConfig {

    private String urlJanus;

    private List<Tag> tags;

    private Map<String, PropertyDefinition> properties;

    @FormPropertyDefinition(type = ToscaType.VERSION, defaultValue = "1.7", constraints = @FormPropertyConstraint(greaterOrEqual = "1.6"))
    private String javaVersion;

}
