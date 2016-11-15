package alien4cloud.plugin.Janus.rest.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class LogEvent {
    private String timestamp;
    private String logs;


}
