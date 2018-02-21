package alien4cloud.plugin.Janus.rest.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@ToString
@Getter
@Setter
public class NodeInfosResponse {

    private List<Link> links;
    private String status;
    private String name;

}
