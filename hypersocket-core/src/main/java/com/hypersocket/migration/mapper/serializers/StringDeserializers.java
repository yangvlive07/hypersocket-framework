package com.hypersocket.migration.mapper.serializers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.Deserializers.Base;
import com.hypersocket.encrypt.EncryptionService;
import com.hypersocket.migration.execution.MigrationContext;
import com.hypersocket.migration.execution.stack.MigrationCurrentStack;
import com.hypersocket.properties.AbstractPropertyTemplate;
import com.hypersocket.properties.PropertyTemplate;
import com.hypersocket.properties.ResourceUtils;
import com.hypersocket.realm.Realm;
import com.hypersocket.resource.AbstractResource;

@Component
public class StringDeserializers extends Base {
	
	@Autowired
	MigrationCurrentStack migrationCurrentStack;
	
	@Autowired
	MigrationContext migrationContext;
	
	@Autowired
	EncryptionService encryptionService; 
	
	@Autowired
	SerializersUtil serializersUtil;

	@Override
	public JsonDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config,
			BeanDescription beanDesc) throws JsonMappingException {
		Class<?> raw = type.getRawClass();
		if(String.class.isAssignableFrom(raw)) {
			return new JsonDeserializer<String>() {

				@Override
				public String deserialize(JsonParser p, DeserializationContext ctxt)
						throws IOException, JsonProcessingException {
					String propName = migrationCurrentStack.getState().getPropName();
					Object bean = migrationCurrentStack.getState().getBean();
					Realm realm = migrationContext.getCurrentRealm();

					JsonNode node = p.getCodec().readTree(p);
					String value = node.asText();

					if(bean instanceof AbstractResource) {
						final AbstractResource resource = (AbstractResource) bean;
						PropertyTemplate propertyTemplate = serializersUtil.getPropertyTemplate(resource, propName);
						
						if(propertyTemplate != null && propertyTemplate.isEncrypted() && !ResourceUtils.isEncrypted(value)) {
							return encrypt(propertyTemplate, resource, value, realm);
						} 
						
					} 
					return value;
				}
				
			};
		}
		
		return null;
	}
	
	private String encrypt(AbstractPropertyTemplate template, AbstractResource resource, String value, Realm realm) {

		String cacheKey = createCacheKey(template.getResourceKey(), resource);
		
		return encryptValue(cacheKey, value, realm);
		

	}
	
	private String createCacheKey(String resourceKey, AbstractResource resource) {
		String key = resourceKey;
		if (resource != null) {
			key += "/" + resource.getId();
		}
		return key;
	}
	
	private String encryptValue(String cacheKey, String value, Realm realm) {
		try {
			return "!ENC!" + encryptionService.encryptString(cacheKey, value, realm);
		} catch (Exception e) {
			throw new IllegalStateException("Could not encrypt property value. Check the logs for more detail.", e);
		}
	}
}
