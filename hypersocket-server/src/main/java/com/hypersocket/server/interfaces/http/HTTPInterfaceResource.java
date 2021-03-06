package com.hypersocket.server.interfaces.http;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.hypersocket.certificates.CertificateResource;
import com.hypersocket.migration.annotation.AllowNameOnlyLookUp;
import com.hypersocket.server.interfaces.InterfaceResource;

@Entity
@Table(name="http_interfaces")
@AllowNameOnlyLookUp
public class HTTPInterfaceResource extends InterfaceResource {
	
	@Column(name="protocol")
	HTTPProtocol protocol;
	
	@OneToOne
	CertificateResource certificate;

	@Column(name="redirect_https")
	Boolean redirectHTTPS;
	
	@Column(name="redirect_port")
	Integer redirectPort;

	public HTTPProtocol getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = HTTPProtocol.valueOf(protocol.toUpperCase());
	}

	public CertificateResource getCertificate() {
		return certificate;
	}

	public void setCertificate(CertificateResource certificate) {
		this.certificate = certificate;
	}

	public Boolean getRedirectHTTPS() {
		return redirectHTTPS!=null && redirectHTTPS;
	}

	public Integer getRedirectPort() {
		return redirectPort;
	}

	public void setRedirectHTTPS(Boolean redirectHTTPS) {
		this.redirectHTTPS = redirectHTTPS;
	}

	public void setRedirectPort(Integer redirectPort) {
		this.redirectPort = redirectPort;
	}
	
	
	
	
}
