package alien4cloud.plugin.Janus.rest;

import alien4cloud.plugin.Janus.rest.Response.JanusError;
import lombok.Getter;

/**
 * A {@code JanusRestException} is a ...
 *
 * @author Loic Albertin
 */
@Getter
public class JanusRestException extends Exception {
    private int httpStatusCode;
    private String title;
    private String detail;

    @Override
    public String getMessage() {
        return "JanusRestException: [title: \"" + title + "\", http status code: \"" + httpStatusCode + "\", detail: \"" + detail + "\"]";
    }

    public static JanusRestException fromJanusError(JanusError error) {
        JanusRestException jre = new JanusRestException();
        jre.detail = error.getDetail();
        jre.httpStatusCode = error.getStatus();
        jre.title = error.getTitle();
        return jre;
    }
}
