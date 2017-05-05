package alien4cloud.plugin.Janus.rest.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class Event {
    // refer to janus code to check this values: events/struct.go
    private String timestamp;
    private String node;
    private String instance;
    private String status;
    private String type;
    private String task_id;
}
