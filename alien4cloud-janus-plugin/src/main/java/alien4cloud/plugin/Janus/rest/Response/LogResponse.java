package alien4cloud.plugin.Janus.rest.Response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import lombok.ToString;

@ToString
@Getter
@Setter
public class LogResponse {

    private List<LogEvent> logs;
    private int last_index;

}
