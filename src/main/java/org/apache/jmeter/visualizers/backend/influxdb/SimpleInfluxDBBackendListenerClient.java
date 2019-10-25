package org.apache.jmeter.visualizers.backend.influxdb;

import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.apache.jmeter.visualizers.backend.UserMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleInfluxDBBackendListenerClient extends AbstractBackendListenerClient {
    private static final Logger log = LoggerFactory.getLogger(SimpleInfluxDBBackendListenerClient.class);
    private static final String DEFAULT_MEASUREMENT = "jmeter";
    private static final String TAG_TRANSACTION = ",transaction=";
    private static final String TAG_APPLICATION = ",application=";
    private static final String TAG_RESPONSE_CODE = ",responseCode=";
    private static final String METRIC_MEAN_ACTIVE_THREADS = "meanAT=";
    private static final String METRIC_RESPONSE_TIME = "responseTime=";
    private static final String TAGS = ",tags=";
    private static final String TEXT = "text=\"";
    private static final String EVENTS_FOR_ANNOTATION = "events";

    private static final Map<String, String> DEFAULT_ARGS = new LinkedHashMap<>();
    static {
        DEFAULT_ARGS.put("influxdbMetricsSender", HttpMetricsSender.class.getName());
        DEFAULT_ARGS.put("influxdbUrl", "http://host_to_change:8086/write?db=jmeter");
        DEFAULT_ARGS.put("application", "application name");
        DEFAULT_ARGS.put("measurement", DEFAULT_MEASUREMENT);
        DEFAULT_ARGS.put("samplersRegex", ".*");
        DEFAULT_ARGS.put("testTitle", "Test name");
        DEFAULT_ARGS.put("eventTags", "");
    }

    private InfluxdbMetricsSender client;
    private String measurement = "DEFAULT_MEASUREMENT";
    private String samplersRegex = "";
    private Pattern samplersToFilter;
    private String testTitle;
    private String testTags;
    private String applicationName = "";
    private String userTag = "";


    @Override
    public void handleSampleResults(List<SampleResult> results,
                                    BackendListenerContext context) {
        UserMetric userMetrics = getUserMetrics();
        for(SampleResult result : results) {
            userMetrics.add(result);
            Matcher matcher = samplersToFilter.matcher(result.getSampleLabel());
            if (!matcher.find()) {
                continue;
            }

            StringBuilder tag = new StringBuilder(80);
            tag.append(TAG_APPLICATION).append(applicationName);
            tag.append(TAG_TRANSACTION).append(result.getSampleLabel());
            tag.append(TAG_RESPONSE_CODE).append(result.getResponseCode());
            tag.append(userTag);

            StringBuilder field = new StringBuilder(80);
            field.append(METRIC_MEAN_ACTIVE_THREADS).append(userMetrics.getMeanActiveThreads());
            field.append(METRIC_RESPONSE_TIME).append(result.getTime());

            client.addMetric(measurement, tag.toString(), field.toString());
        }
        client.writeAndSendMetrics();
    }

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        samplersRegex = context.getParameter("samplersRegex", "");
        applicationName = AbstractInfluxdbMetricsSender.tagToStringValue(
                context.getParameter("application", ""));
        measurement = AbstractInfluxdbMetricsSender.tagToStringValue(
                context.getParameter("measurement", DEFAULT_MEASUREMENT));
        testTitle = context.getParameter("testTitle", "Test");
        testTags = AbstractInfluxdbMetricsSender.tagToStringValue(
                context.getParameter("eventTags", ""));

        initUserTags(context);
        initInfluxDBClient(context);
        addAnnotation(true);

        samplersToFilter = Pattern.compile(samplersRegex);
        super.setupTest(context);
    }

    private void initInfluxDBClient(BackendListenerContext context) throws Exception {
        Class<?> clazz = Class.forName(context.getParameter("influxdbMetricsSender"));
        client = (InfluxdbMetricsSender) clazz.getDeclaredConstructor().newInstance();

        String influxdbUrl = context.getParameter("influxdbUrl");
        client.setup(influxdbUrl);
    }

    private void initUserTags(BackendListenerContext context) {
        // Check if more rows which started with 'TAG_' are filled ( corresponding to user tag )
        StringBuilder userTagBuilder = new StringBuilder();
        context.getParameterNamesIterator().forEachRemaining(name -> {
            if (StringUtils.isNotBlank(name)
                    && !DEFAULT_ARGS.containsKey(name.trim())
                    && name.startsWith("TAG_")
                    && StringUtils.isNotBlank(context.getParameter(name))) {
                final String tagName = name.trim().substring(4);
                final String tagValue = context.getParameter(name).trim();
                userTagBuilder.append(',')
                        .append(AbstractInfluxdbMetricsSender.tagToStringValue(tagName))
                        .append('=')
                        .append(AbstractInfluxdbMetricsSender.tagToStringValue(tagValue));
                log.debug("Adding '{}' tag with '{}' value ", tagName, tagValue);
            }
        });
        userTag = userTagBuilder.toString();
    }

    private void addAnnotation(boolean isStartOfTest) {
        String tags = TAG_APPLICATION + applicationName +
                ",title=ApacheJMeter" + userTag +
                (StringUtils.isNotEmpty(testTags) ? TAGS + testTags : "");
        String field = TEXT +
                AbstractInfluxdbMetricsSender.fieldToStringValue(
                        testTitle + (isStartOfTest ? " started" : " ended")) + "\"";

        client.addMetric(EVENTS_FOR_ANNOTATION, tags, field);
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        addAnnotation(false);
        log.info("Sending end of test annotation metric");
        client.writeAndSendMetrics();
        client.destroy();
        super.teardownTest(context);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        DEFAULT_ARGS.forEach(arguments::addArgument);
        return arguments;
    }
}