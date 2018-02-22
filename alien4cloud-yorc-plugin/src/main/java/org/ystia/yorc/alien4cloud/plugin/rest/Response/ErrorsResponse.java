package org.ystia.yorc.alien4cloud.plugin.rest.Response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A {@code ErrorsResponse} is a Yorc REST API errors list return
 *
 * @author Loic Albertin
 */
@ToString
@Getter
@Setter
public class ErrorsResponse {

    private List<YorcError> errors;

}
