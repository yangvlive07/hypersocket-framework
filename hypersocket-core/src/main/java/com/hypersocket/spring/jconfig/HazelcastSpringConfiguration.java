package com.hypersocket.spring.jconfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.cache.RegionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.config.AwsConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory;

@Configuration
public class HazelcastSpringConfiguration {
	
	static Logger log = LoggerFactory.getLogger(HazelcastSpringConfiguration.class);
	
	@Autowired ApplicationContext applicationContext; 
	@Autowired Environment environment;
	
	@Value("${user.dir}") 
	String userDir; 
	

	@Bean
    Config config(NetworkConfig networkConfig) {
        Config config = new Config();
        config.setInstanceName(applicationContext.getId());
        config.setNetworkConfig(networkConfig);
        config.setProperty( "hazelcast.logging.type", "log4j" );
        return config;
    }
	
	
	@Bean(destroyMethod = "shutdown")
    HazelcastInstance hazelcast(Config config) {
        return Hazelcast.newHazelcastInstance(config);
    }
 
    @Bean
    NetworkConfig networkConfig(JoinConfig joinConfig) {
        NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setJoin(joinConfig);
        networkConfig.setPortAutoIncrement(true);
        networkConfig.setReuseAddress(true);
        return networkConfig;
    }
 
    @Bean
    JoinConfig joinConfig(@Qualifier("hazelcastProperties") Properties hazelcastProperties) {
    	JoinConfig joinConfig = new JoinConfig();
    	awsConfig(joinConfig);
        multicastConfig(joinConfig, hazelcastProperties);
        tcpIpConfig(joinConfig, hazelcastProperties);
        return joinConfig;
    }
    
    @Bean(destroyMethod = "stop")
    RegionFactory regionFactory(HazelcastInstance instance){
    	HazelcastLocalCacheRegionFactory cacheRegionFactory = new HazelcastLocalCacheRegionFactory(instance);
    	return cacheRegionFactory;
    }
    
    @Bean(destroyMethod = "close")
    CacheManager cacheManager(HazelcastInstance instance) throws URISyntaxException{
    	CachingProvider cachingProvider = Caching.getCachingProvider("com.hazelcast.cache.impl.HazelcastServerCachingProvider", null);

    	Properties properties = HazelcastCachingProvider.propertiesByInstanceName(applicationContext.getId());
    	
    	URI cacheManagerName = new URI("hypersocket-cache-manager");
    	return cachingProvider.getCacheManager(cacheManagerName, null, properties);
    } 
    
    @Bean
	@Qualifier("hazelcastProperties")
	public Properties hazelcastProperties() {
    	File file = new File(userDir + "/conf/hazelcast-conf.properties");
    	if(!file.exists()){
    		return new Properties();
    	}
		PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
		propertiesFactoryBean.setLocation(new FileSystemResource(file));
		Properties properties = null;
		try {
			propertiesFactoryBean.afterPropertiesSet();
			properties = propertiesFactoryBean.getObject();

		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		

		return properties;
	}
    
    private void tcpIpConfig(JoinConfig joinConfig, Properties hazelcastProperties) {
        TcpIpConfig tcpIpConfig = new TcpIpConfig();
        if(environment.acceptsProfiles("HA") && "TCP/IP".equals(hazelcastProperties.get("ha.hazelcast.network.type"))){
        	
        	log.info("TCP IP settings enabled for hazelcast.");
        	
        	tcpIpConfig.setEnabled(true);
        	
        	String member = hazelcastProperties.getProperty("ha.hazelcast.tcp.ip.member");
        	if(StringUtils.isNotEmpty(member)){
        		log.info(String.format("TCP/IP member is %s.", member));
        		tcpIpConfig.addMember(member);
        	}else{
        		tcpIpConfig.addMember("127.0.0.1");
        	}
        	
        	String timeout = hazelcastProperties.getProperty("ha.hazelcast.tcp.ip.con.time.out");
        	if(StringUtils.isNotEmpty(timeout)){
        		log.info(String.format("TCP/IP connection timeout is %s.", timeout));
        		tcpIpConfig.setConnectionTimeoutSeconds(Integer.parseInt(timeout));
        	}
        }else{
        	tcpIpConfig.setEnabled(false);
        }
        
        joinConfig.setTcpIpConfig(tcpIpConfig);
    }
    
    private void multicastConfig(JoinConfig joinConfig, Properties hazelcastProperties) {
        MulticastConfig multicastConfig = new MulticastConfig();
        if(environment.acceptsProfiles("HA") && ("Multicast".equals(hazelcastProperties.get("ha.hazelcast.network.type")) || 
        		StringUtils.isEmpty(hazelcastProperties.getProperty("ha.hazelcast.network.type")))){
        	
        	log.info("Multicast settings enabled for hazelcast.");
        	
        	multicastConfig.setEnabled(true);
        	
        	String group = hazelcastProperties.getProperty("ha.hazelcast.multicast.group");
        	if(StringUtils.isNotEmpty(group)){
        		log.info(String.format("Multicast group is %s.", group));
        		multicastConfig.setMulticastGroup(group);
        	}
        	
        	String port = hazelcastProperties.getProperty("ha.hazelcast.multicast.port");
        	if(StringUtils.isNotEmpty(port)){
        		log.info(String.format("Multicast port is %s.", port));
        		multicastConfig.setMulticastPort(Integer.parseInt(port));
        	}
        	
        	String trustedInterfaces = hazelcastProperties.getProperty("ha.hazelcast.multicast.trusted.interfaces");
        	if(StringUtils.isNotEmpty(trustedInterfaces)){
        		log.info(String.format("Multicast trusted interfaces is %s.", trustedInterfaces));
        		multicastConfig.addTrustedInterface(trustedInterfaces);
        	}
        }else{
        	multicastConfig.setEnabled(false);
        }
        joinConfig.setMulticastConfig(multicastConfig);
    }
    

	private void awsConfig(JoinConfig joinConfig){
    	AwsConfig awsConfig = new AwsConfig();
    	awsConfig.setEnabled(false);
    	joinConfig.setAwsConfig(awsConfig);
    }
}
