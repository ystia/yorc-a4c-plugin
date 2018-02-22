package org.ystia.yorc.alien4cloud.plugin.rest.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class Link {

    private String rel;
    private String href;
    private String type;

}
