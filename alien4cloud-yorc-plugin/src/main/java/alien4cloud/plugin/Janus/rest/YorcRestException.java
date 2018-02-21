package alien4cloud.plugin.Janus.rest;

import alien4cloud.plugin.Janus.rest.Response.YorcError;
import lombok.Getter;

/**
 * A {@code YorcRestException} is a ...
 *
 * @author Loic Albertin
 */
@Getter
public class YorcRestException extends Exception {
    private int httpStatusCode;
    private String title;
    private String detail;

    @Override
    public String getMessage() {
        return "YorcRestException: [title: \"" + title + "\", http status code: \"" + httpStatusCode + "\", detail: \"" + detail + "\"]";
    }

    public static YorcRestException fromYorcError(YorcError error) {
        YorcRestException jre = new YorcRestException();
        jre.detail = error.getDetail();
        jre.httpStatusCode = error.getStatus();
        jre.title = error.getTitle();
        return jre;
    }
}
