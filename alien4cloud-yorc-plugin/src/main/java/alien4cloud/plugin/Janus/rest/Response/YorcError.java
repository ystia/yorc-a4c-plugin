package alien4cloud.plugin.Janus.rest.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A {@code Error} is a Janus REST API error description
 *
 * @author Loic Albertin
 */
@ToString
@Getter
@Setter
public class YorcError {
    private String id;
    private int status;
    private String title;
    private String detail;
}
