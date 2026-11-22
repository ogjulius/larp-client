package dev.larpclient.launcher.game;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mojang's patched log4j config for 1.8.9 prints XML events. This turns them back into readable
 * {@code [12:00:00] [thread/LEVEL] (logger) message} lines; non-XML lines pass through unchanged.
 */
final class Log4jXmlFormatter implements Consumer<String> {
    private static final Pattern EVENT = Pattern.compile(
            "<log4j:Event logger=\"([^\"]*)\" timestamp=\"(\\d+)\" level=\"([^\"]*)\" thread=\"([^\"]*)\">");
    private static final Pattern MESSAGE = Pattern.compile("<!\\[CDATA\\[(.*?)(\\]\\]>|$)");

    private final Consumer<String> out;
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");
    private String header;
    private StringBuilder message;
    private boolean inMessage;

    Log4jXmlFormatter(Consumer<String> out) {
        this.out = out;
    }

    @Override
    public void accept(String line) {
        Matcher event = EVENT.matcher(line.trim());
        if (event.matches()) {
            header = "[" + time.format(new Date(Long.parseLong(event.group(2)))) + "] ["
                    + event.group(4) + "/" + event.group(3) + "] (" + event.group(1) + ") ";
            message = new StringBuilder();
            return;
        }
        if (header == null) {
            out.accept(line);
            return;
        }
        if (inMessage) {
            int end = line.indexOf("]]>");
            message.append('\n').append(end >= 0 ? line.substring(0, end) : line);
            inMessage = end < 0;
            return;
        }
        if (line.contains("<log4j:Message>")) {
            Matcher m = MESSAGE.matcher(line);
            if (m.find()) {
                message.append(m.group(1));
                inMessage = m.group(2).isEmpty();
            }
        } else if (line.contains("</log4j:Event>")) {
            out.accept(header + message);
            header = null;
            message = null;
        } else if (line.contains("<log4j:Throwable>") || (!line.trim().startsWith("<") && !line.trim().isEmpty())) {
            // stack traces inside <log4j:Throwable><![CDATA[ ... ]]>
            message.append('\n').append(line.replace("<log4j:Throwable><![CDATA[", "").replace("]]></log4j:Throwable>", ""));
        }
    }
}
