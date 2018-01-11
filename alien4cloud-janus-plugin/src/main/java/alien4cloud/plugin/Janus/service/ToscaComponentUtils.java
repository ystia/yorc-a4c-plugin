package alien4cloud.plugin.Janus.service;

/**
 * A {@code ToscaComponentUtils} is a ...
 *
 * @author Loic Albertin
 */
public class ToscaComponentUtils {


    public static String join(Object[] list, String separator) {
        final StringBuffer buffer = new StringBuffer();
        for (Object o : list) {
            if (buffer.length() > 0) {
                buffer.append(separator);
            }
            buffer.append(o.toString());
        }
        return buffer.toString();
    }
}
