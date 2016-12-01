package alien4cloud.plugin.Janus.rest.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@ToString
@Getter
@Setter
public class LogEvent {
    private String timestamp;
    private String logs;

    public Date getDate() {
        return Date.from(LocalDateTime.parse(this.getTimestamp(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant());
    }
}
