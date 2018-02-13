/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@FormProperties({"urlJanus", "insecureTLS"})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("PMD.UnusedPrivateField")
public class ProviderConfig {

    @FormPropertyDefinition(type = "string", defaultValue= "http://127.0.0.1:8800", description = "URL of a Janus REST API instance.", constraints = @FormPropertyConstraint
            (pattern = "https?://.+"))
    private String urlJanus= "http://127.0.0.1:8800";

    @FormPropertyDefinition(type = "boolean", description = "Do not check host certificate. This is not recommended for production use " +
            "and may expose to man in the middle attacks.")
    private Boolean insecureTLS;

}
