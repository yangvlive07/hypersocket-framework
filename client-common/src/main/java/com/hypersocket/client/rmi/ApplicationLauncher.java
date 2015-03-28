package com.hypersocket.client.rmi;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.replace.ReplacementUtils;
import com.hypersocket.utils.CommandExecutor;

public class ApplicationLauncher implements ResourceLauncher, Serializable {

	static Logger log = LoggerFactory.getLogger(ApplicationLauncher.class);
	
	private static final long serialVersionUID = 4922604914995232181L;

	String[] ALLOWED_SYSTEM_PROPERTIES = { "user.name", "user.home", "user.dir" };
	
	String hostname;
	ApplicationLauncherTemplate launcher;
	String username;
	public ApplicationLauncher(String username, String hostname, ApplicationLauncherTemplate launcher) {
		this.username = username;
		this.hostname = hostname;
		this.launcher = launcher;
	}
	
	@Override
	public int launch() {
		
		Map<String,String> env = System.getenv();
		Map<String,String> properties = new HashMap<String,String>();
		
		properties.put("hostname", hostname);
		properties.put("username", username);
		
		for(String prop : ALLOWED_SYSTEM_PROPERTIES) {
			properties.put(prop.replace("user.", "client.user"), System.getProperty(prop));
		}
		
		if(StringUtils.isNotBlank(launcher.getStartupScript())) {
			
			if(log.isInfoEnabled()) {
				log.info("Executing startup script");
			}
			
			ScriptLauncher script = new ScriptLauncher(username, hostname, launcher.getStartupScript());
			int exitCode = script.launch();
			if(exitCode!=0) {
				log.warn("Startup script returned non zero exit code " + exitCode);
			}
		}
		String exe = ReplacementUtils.processTokenReplacements(launcher.getExe(), env);
		exe = ReplacementUtils.processTokenReplacements(exe, properties);
		
		
		final CommandExecutor cmd = new CommandExecutor(exe);
		for(String arg : launcher.getArgs()) {
			arg = ReplacementUtils.processTokenReplacements(arg, env);
			arg = ReplacementUtils.processTokenReplacements(arg, properties);
			cmd.addArg(arg);
		}
		

		try {
			int exitCode = cmd.execute();
			
			if(log.isInfoEnabled()) {
				log.info("Command exited with exit code " + exitCode);
				log.info("---BEGIN CMD OUTPUT----");
				log.info(cmd.getCommandOutput());
				log.info("---END CMD OUTPUT----");
			}
			
			return exitCode;
		} catch (IOException e) {
			log.error("Failed to launch application", e);
			return Integer.MIN_VALUE;
		} finally {

			if(StringUtils.isNotBlank(launcher.getShutdownScript())) {
				
				if(log.isInfoEnabled()) {
					log.info("Executing shutdown script");
				}
				
				ScriptLauncher script = new ScriptLauncher(username, hostname, launcher.getShutdownScript());
				int exitCode = script.launch();
				if(exitCode!=0) {
					log.warn("Shutdown script returned non zero exit code " + exitCode);
				}
			}
		}

		
	}

}
