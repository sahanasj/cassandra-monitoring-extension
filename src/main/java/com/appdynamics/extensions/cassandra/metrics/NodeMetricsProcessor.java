package com.appdynamics.extensions.cassandra.metrics;

import com.appdynamics.extensions.cassandra.CassandraUtil;
import com.appdynamics.extensions.cassandra.JMXConnectionAdapter;
import com.appdynamics.extensions.cassandra.filters.IncludeFilter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.appdynamics.extensions.cassandra.Constants.*;

public class NodeMetricsProcessor {
    private static final Logger logger = LoggerFactory.getLogger(NodeMetricsProcessor.class);
    private JMXConnectionAdapter jmxConnectionAdapter;
    private JMXConnector jmxConnector;
    private final MetricValueTransformer valueConverter = new MetricValueTransformer();
    private final MetricKeyFormatter metricKeyFormatter = new MetricKeyFormatter();

    public NodeMetricsProcessor (JMXConnectionAdapter jmxConnectionAdapter, JMXConnector jmxConnector) {
        this.jmxConnectionAdapter = jmxConnectionAdapter;
        this.jmxConnector = jmxConnector;
    }

    public List<Metric> getNodeMetrics (Map mBean, Map<String, MetricProperties> metricsPropertiesMap) throws
            MalformedObjectNameException, IOException, IntrospectionException, InstanceNotFoundException,
            ReflectionException {
        Set<Attribute> attributes = Sets.newHashSet();
        List<Metric> nodeMetrics = Lists.newArrayList();
        String configObjectName = CassandraUtil.convertToString(mBean.get(OBJECT_NAME), "");
        Set<ObjectInstance> objectInstances = jmxConnectionAdapter.queryMBeans(jmxConnector, ObjectName.getInstance
                (configObjectName));
        for (ObjectInstance instance : objectInstances) {
            List<String> metricNamesDictionary = jmxConnectionAdapter.getReadableAttributeNames(jmxConnector, instance);
            List<String> metricNamesToBeExtracted = applyFilters(mBean, metricNamesDictionary);
            attributes = jmxConnectionAdapter.getAttributes(jmxConnector, instance.getObjectName(),
                    metricNamesToBeExtracted.toArray(new String[metricNamesToBeExtracted.size()]));
            collect(nodeMetrics, attributes, instance, metricsPropertiesMap);
        }
        return nodeMetrics;
    }

    private List<String> applyFilters (Map aConfigMBean, List<String> metricNamesDictionary) throws
            IntrospectionException, ReflectionException, InstanceNotFoundException, IOException {
        Set<String> filteredSet = Sets.newHashSet();
        Map configMetrics = (Map) aConfigMBean.get(METRICS);
        List includeDictionary = (List) configMetrics.get(INCLUDE);
        new IncludeFilter(includeDictionary).applyFilter(filteredSet, metricNamesDictionary);
        return Lists.newArrayList(filteredSet);
    }

    private void collect (List<Metric> nodeMetrics, Set<Attribute> attributes, ObjectInstance instance, Map<String,
            MetricProperties> metricPropsPerMetricName) {
        for (Attribute attribute : attributes) {
            try {
                if (isCurrentObjectComposite(attribute)) {
                    Set<String> attributesFound = ((CompositeDataSupport) attribute.getValue()).getCompositeType()
                            .keySet();
                    for (String str : attributesFound) {
                        String key = attribute.getName() + "." + str;
                        if (metricPropsPerMetricName.containsKey(key)) {
                            Object attributeValue = ((CompositeDataSupport) attribute.getValue()).get(str);
                            setMetricDetails(key, attributeValue, instance, metricPropsPerMetricName, nodeMetrics);
                        }
                    }
                } else {
                    setMetricDetails(attribute.getName(), attribute.getValue(), instance, metricPropsPerMetricName,
                            nodeMetrics);
                }
            } catch (Exception e) {
                logger.error("Error collecting value for {} {}", instance.getObjectName(), attribute.getName(), e);
            }
        }
    }

    private void setMetricDetails (String attributeName, Object attributeValue, ObjectInstance instance, Map<String,
            MetricProperties> metricPropsPerMetricName, List<Metric> nodeMetrics) {
        MetricProperties props = metricPropsPerMetricName.get(attributeName);
        if (props == null) {
            logger.error("Could not find metric properties for {} ", attributeName);
        }
        BigDecimal metricValue = valueConverter.transform(attributeName, attributeValue, props);
        if (metricValue != null) {
            String instanceKey = metricKeyFormatter.getInstanceKey(instance);
            Metric nodeMetric = new Metric();
            nodeMetric.setProperties(props);
            nodeMetric.setMetricName(attributeName);
            nodeMetric.setInstanceKey(instanceKey);
            String metricName = nodeMetric.getMetricNameOrAlias();
            String nodeMetricKey = metricKeyFormatter.getNodeKey(instance, metricName, instanceKey);
            nodeMetric.setMetricKey(nodeMetricKey);
            nodeMetric.setMetricValue(metricValue);
            nodeMetrics.add(nodeMetric);
        }
    }

    private boolean isCurrentObjectComposite (Attribute attribute) {
        return attribute.getValue().getClass().equals(CompositeDataSupport.class);
    }
}


















/*            compositeAttributeNames = Lists.newArrayList();
            for(String str : metricNamesToBeExtracted) {
                if(CassandraUtil.isCompositeObject(str)) {
                    String[] temp = str.split("\\.");
                    String compositeMetricName = temp[0];
                    compositeAttributeNames.add(temp[1]);
                    compositeMetricNamesToBeExtracted.add(compositeMetricName);
                    isCurrentMetricComposite = true;
                    attributes = jmxConnectionAdapter.getAttributes(jmxConnector, instance.getObjectName(),
                            compositeMetricNamesToBeExtracted.toArray(new String[metricNamesToBeExtracted.size()]));
                }
            }
            if(!isCurrentMetricComposite) {
                attributes = jmxConnectionAdapter.getAttributes(jmxConnector, instance.getObjectName(),
                metricNamesToBeExtracted.toArray(new String[metricNamesToBeExtracted.size()]));
            }*/

/*for (String compositeAttributeName : compositeAttributeNames) {
                        String attributeName = attribute.getName();
                        attributeName += "." + compositeAttributeName;
                        attributeValue = ((CompositeDataSupport) attribute.getValue()).get(compositeAttributeName);
                        setMetricDetails(attributeName, attributeValue, instance, metricPropsPerMetricName,
                        nodeMetrics);
                    }*/