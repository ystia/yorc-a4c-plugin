/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class Event {
    private String timestamp;
    private String deploymentId;
    private String status;
    private String type;
    private String workflowId;
    private String alienExecutionId;
    private String nodeId;
    private String instanceId;
    private String operationName;
    private String alienTaskId;
    private String targetNodeId;
    private String targetInstanceId;
    private String stepId;
    private String attribute;
    private String value;

    public Date getDate() {
        return Date.from(LocalDateTime.parse(this.getTimestamp(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).atZone(
                ZoneId.systemDefault()).toInstant());
    }
}
