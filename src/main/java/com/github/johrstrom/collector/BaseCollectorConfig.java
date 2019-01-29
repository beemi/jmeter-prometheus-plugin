package com.github.johrstrom.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.Collector.Type;

/**
 * The base class for turning text/strings (from the JMeter GUI, or a .jmx file, etc.) into Prometheus Collector
 * objects.  Along with being a TestElement so that it can be serialized (for the .jmx file) it also handles all 
 * the logic of parsing strings into double arrays. 
 * 
 * There are also static utility functions to actually instantiate a Collector of a given type from a configuration.
 * 
 * 
 * buckets are comma seperated list of decimals. An example:
 * 		100,200,300,400.3
 * quantiles are comma seperated pair of decimals seperated by |. The first decimal being the quantile and the second being the 
 * error rating. An example:
 * 		0.999,0.1|0.99,0.2|0.75,0.3
 * 
 * @author Jeff Ohrstrom
 *
 */
public class BaseCollectorConfig extends AbstractTestElement  {
	
	private static final long serialVersionUID = 1520731432941268549L;
	
	public static String HELP = "collector.help";
	public static String NAME = "collector.metric_name";
	public static String TYPE = "collector.type";
	public static String LABELS = "collector.labels";
	public static String QUANTILES_OR_BUCKETS = "collector.quantiles_or_buckets";
	
	public static double[] DEFAULT_BUCKET_SIZES = {100,500,1000,3000};
	public static String DEFAULT_BUCKET_SIZES_STRING = "100,500,1000,3000";
	
	public static QuantileDefinition[] DEFAULT_QUANTILES = QuantileDefinition.defaultQuantiles();
	public static String DEFAULT_QUANTILES_STRING = QuantileDefinition.arrayToString(DEFAULT_QUANTILES);
	
	
	public static String DEFAULT_HELP_STRING = "default help string";
	public static String METRIC_NAME_BASE = "jmeter_autogenerated_metric_";
	
	private static Logger log = LoggerFactory.getLogger(BaseCollectorConfig.class);
	
	public BaseCollectorConfig(){
		this.setHelp(DEFAULT_HELP_STRING);
		this.setMetricName(METRIC_NAME_BASE);
		this.setType(Type.COUNTER.name());
		this.setLabels(new String[0]);
		this.setQuantileOrBucket("");
	}

	public String getHelp() {
		return this.getPropertyAsString(HELP, DEFAULT_HELP_STRING);
	}

	public void setHelp(String help) {
		if(help == null || help.isEmpty())
			this.setProperty(HELP, DEFAULT_HELP_STRING);
		else
			this.setProperty(HELP, help);	
	}

	public String getType() {
		return this.getPropertyAsString(TYPE, Type.COUNTER.name());
	}
	
	public Type getPrometheusType() {
		return Type.valueOf(this.getType());
	}

	public void setType(String type) {
		this.setProperty(TYPE, type);
		
		if(type != null && type.equals(Type.HISTOGRAM.name()) && this.getQuantileOrBucket().isEmpty())
			this.setQuantileOrBucket(DEFAULT_BUCKET_SIZES_STRING);
		if(type != null && type.equals(Type.SUMMARY.name()) && this.getQuantileOrBucket().isEmpty())
			this.setQuantileOrBucket(DEFAULT_QUANTILES_STRING);
	}

	public String getQuantileOrBucket() {
		return this.getPropertyAsString(QUANTILES_OR_BUCKETS,"");
	}

	public void setQuantileOrBucket(String quantileOrBucket) {
		this.setProperty(QUANTILES_OR_BUCKETS, quantileOrBucket);
	}
	
	public double[] getBuckets() {
		String buckets = getQuantileOrBucket();
		
		if(buckets == null || buckets.isEmpty()) {
			return DEFAULT_BUCKET_SIZES;
		}else {
			return this.parseBucketsFromString(buckets);
		}
	}
	
	public QuantileDefinition[] getQuantiles() {
		String quantiles = getQuantileOrBucket();
		
		if(quantiles == null || quantiles.isEmpty()) {
			return DEFAULT_QUANTILES;
		}else {
			return QuantileDefinition.parseQuantilesFromString(quantiles);
		}
	}

	public String getMetricName() {
		return this.getPropertyAsString(NAME, getRandomMetricName());
	}

	public void setMetricName(String name) {
		if(name == null || name.isEmpty())
			this.setProperty(NAME, getRandomMetricName());
		else
			this.setProperty(NAME, name);
	}
	
	public String getRandomMetricName() {
		return METRIC_NAME_BASE + RandomStringUtils.randomAlphanumeric(8);
	}
	
	public void setLabels(String labels) {
		this.setLabels(labels.split(","));
	}
	
	public void setLabels(String[] labels) {
		List<String> list = new ArrayList<String>(Arrays.asList(labels));
		
		Iterator<String> it = list.iterator();
		while(it.hasNext()) { // can't have empty strings from Gui
			String item = it.next();
			if(item == null || item.isEmpty()) {
				it.remove();
			}
		}
		
		this.setProperty(new CollectionProperty(LABELS, list));
	}
	
	public String[] getLabels() {
		JMeterProperty prop = this.getProperty(LABELS);
		if(prop == null || prop instanceof NullProperty) {
			return new String[0];
		}
		 
		CollectionProperty colletion = (CollectionProperty) prop;
		String[] retArray = new String[colletion.size()];
		PropertyIterator it = colletion.iterator();
		
		int i=0;
		while(it.hasNext()) {
			String next = it.next().getStringValue();
			retArray[i] = next;
			i++;
		}
		
		return retArray;		
	}
	
	public String getLabelsAsString() {
		StringBuilder sb = new StringBuilder();
		
		String[] labels = this.getLabels();
		for(int i = 0; i < labels.length; i++) {
			sb.append(labels[i]);
			if(i+1 < labels.length)
				sb.append(",");
		}
		
		return sb.toString();
	}
	
	public static Counter newCounter(BaseCollectorConfig cfg) throws Exception {
		io.prometheus.client.Counter.Builder builder = new Counter.Builder()
			.help(cfg.getHelp())
			.name(cfg.getMetricName());
		
		String[] labels = cfg.getLabels();
		if(labels.length != 0) {
			builder.labelNames(labels);
		}
		
		return builder.create();
	}
	
	public static Summary newSummary(BaseCollectorConfig cfg) throws Exception {
		io.prometheus.client.Summary.Builder builder = new Summary.Builder()
				.name(cfg.getMetricName())
				.help(cfg.getHelp());
		
		String[] labels = cfg.getLabels();
		if(labels.length != 0) {
			builder.labelNames(labels);
		}
		
		for(QuantileDefinition def : cfg.getQuantiles()) {
			builder.quantile(def.quantile, def.error);
		}
		
		return builder.create();
	}
	
	public static Histogram newHistogram(BaseCollectorConfig cfg) throws Exception {
		io.prometheus.client.Histogram.Builder builder = new Histogram.Builder()
				.name(cfg.getMetricName())
				.help(cfg.getHelp())
				.buckets(cfg.getBuckets());
		
		String[] labels = cfg.getLabels();
		if(labels.length != 0) {
			builder.labelNames(labels);
		}
		
		return builder.create();
	}
	
	public static Gauge newGauge(BaseCollectorConfig cfg) throws Exception {
		io.prometheus.client.Gauge.Builder builder =  new Gauge.Builder()
				.name(cfg.getMetricName())
				.help(cfg.getHelp());
		
		String[] labels = cfg.getLabels();
		if(labels.length != 0) {
			builder.labelNames(labels);
		}
		
		return builder.create();
	}
	
	public static Collector fromConfig(BaseCollectorConfig cfg) {
		Type t = cfg.getPrometheusType();
		Collector c = null;
		
		try {
			if(t.equals(Type.COUNTER)) {
				c = BaseCollectorConfig.newCounter(cfg);
				
			}else if(t.equals(Type.SUMMARY)) {
				c = BaseCollectorConfig.newSummary(cfg);
				
			}else if(t.equals(Type.HISTOGRAM)) {
				c = BaseCollectorConfig.newHistogram(cfg);
			}else if(t.equals(Type.GAUGE)) {
				c = BaseCollectorConfig.newHistogram(cfg);
			}
		} catch(Exception e) {
			log.error(String.format("Didn't create collector from definition %s because of an error", cfg), e);
		} 
		
		return c;
	}
	
	
	@Override
	public String toString() {
		PropertyIterator it = this.propertyIterator();
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		while(it.hasNext()) {
			JMeterProperty prop = it.next();
			sb.append(prop.getName()).append(": ").append(prop.getStringValue()).append(", ");
		}
		
		sb.append("]");
		return sb.toString();
	}
	
	protected double[] parseBucketsFromString(String fullBucketString) {
		String[] bucketStrings = fullBucketString.split(",");
		List<Double> buckets = new ArrayList<Double>();
		
		for(String bucket : bucketStrings) {
			try {
				double d = Double.parseDouble(bucket);
				buckets.add(d);
			}catch(Exception e) {
				log.warn("couldn't parse {} because of error {}:{}. It wont be included in buckets for the metric {}",
						bucket, e.getClass().toString(), e.getMessage(), this.getMetricName());
			}
		}
		
		if(buckets.isEmpty()) {
			log.warn("Did not parse any buckets for metric {}. Returning defaults", this.getMetricName());
			return DEFAULT_BUCKET_SIZES;
		}else {
			return buckets.stream().mapToDouble(Double::doubleValue).toArray();
		}
	}
	
	
	/**
	 * A very simple POJO for holding Quantiles and the error rating for them.
	 * 
	 * @author Jeff ohrstrom
	 *
	 */
	public static class QuantileDefinition {
		public double quantile;
		public double error;
		public static final String QUANTILE_ERROR_SEPERATOR = ",";
		public static final String QUANTILE_DEFINITION_SEPERATOR = "|";
		public static final String QUANTILE_DEFINITION_SEPERATOR_REGEX = "\\|";
		
		QuantileDefinition(double quantile, double error){
			this.quantile = quantile;
			this.error = error;
		}
		
		QuantileDefinition(String quantile, String error) throws NumberFormatException {
			this(new String[]{quantile, error});
		}
		
		QuantileDefinition(String[] definition) throws NumberFormatException {
			if(definition.length != 2) {
				throw new IllegalArgumentException(String.format("Quantiles need exactly 2 parameters. %d given.", definition.length));
			}
			this.quantile = Double.parseDouble(definition[0]);
			this.error = Double.parseDouble(definition[1]);
		}
		
		public String toString() {
			return new StringBuilder()
					.append(this.quantile)
					.append(QUANTILE_ERROR_SEPERATOR)
					.append(this.error)
					.toString();
		}
		
		public static QuantileDefinition[] defaultQuantiles() {
			QuantileDefinition[] def = new QuantileDefinition[3];
			
			def[0] = new QuantileDefinition(0.75,0.5);
			def[1] = new QuantileDefinition(0.95,0.1);
			def[2] = new QuantileDefinition(0.99,0.01);
			
			return def;
		}
		
		public static String arrayToString(QuantileDefinition[] definitions) {
			StringBuilder sb = new StringBuilder();
			
			for(int i = 0; i < definitions.length; i++) {
				sb.append(definitions[i]);
				if(i+1 < definitions.length)
					sb.append(QUANTILE_DEFINITION_SEPERATOR);
			}
		
			return sb.toString();
		}
		
		public static QuantileDefinition[] parseQuantilesFromString(String fullQuantileString) {
			String[] quantileDefStrings = fullQuantileString.split(QUANTILE_DEFINITION_SEPERATOR_REGEX);
			List<QuantileDefinition> quantiles = new ArrayList<QuantileDefinition>();
			
			for(String quantile : quantileDefStrings) {
				try {
					QuantileDefinition q = new QuantileDefinition(quantile.split(QUANTILE_ERROR_SEPERATOR));
					quantiles.add(q);
				}catch(Exception e) {
					log.warn("couldn't parse {} because of error {}:{}. It wont be included in quantiles for the metric",
							quantile, e.getClass().toString(), e.getMessage());
				}
			}
			
			if(quantiles.isEmpty()) {
				log.warn("Did not parse any quantiles, returning defaults.");
				return DEFAULT_QUANTILES;
			}else {
				return quantiles.toArray(new QuantileDefinition[quantiles.size()]);
			}
		}
		

	}



}


