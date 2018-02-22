package org.ystia.yorc.alien4cloud.plugin.rest.Response;

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
    private String deploymentId;
    private String level;
    private String type;
    private String workflowId;
    private String executionId;
    private String nodeId;
    private String instanceId;
    private String interfaceName;
    private String operationName;
    private String content;

    public Date getDate() {
        return Date.from(LocalDateTime.parse(this.getTimestamp(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).atZone(
            ZoneId.systemDefault()).toInstant());
    }
}
