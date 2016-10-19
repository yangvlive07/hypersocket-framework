package com.hypersocket.server.interfaces;

import javax.persistence.Column;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MappedSuperclass;

import org.apache.commons.lang3.StringUtils;

import com.hypersocket.resource.RealmResource;

@MappedSuperclass
@Inheritance(strategy=InheritanceType.TABLE_PER_CLASS)
public class InterfaceResource extends RealmResource {

	private static final long serialVersionUID = 3104523837579362221L;

	@Column(name="interfaces", length=1024)
	String interfaces;
	
	@Column(name="port")
	Integer port;
	
	@Column(name="all_interfaces", nullable=true)
	Boolean allInterfaces;
	
	public String getInterfaces() {
		return interfaces;
	}

	public void setInterfaces(String interfaces) {
		this.interfaces = interfaces;
	}

	public Integer getPort() {
		return port == null ? 22 : port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public Boolean getAllInterfaces() {
		return allInterfaces == null ? StringUtils.isBlank(interfaces) : allInterfaces;
	}

	public void setAllInterfaces(Boolean allInterfaces) {
		this.allInterfaces = allInterfaces;
	}
	
	
}
