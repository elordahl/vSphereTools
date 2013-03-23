package cloud;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public final class ServerToSave extends AbstractDescribableImpl<ServerToSave> implements Serializable {
	private static final long serialVersionUID = 1L;
	private String server;
	private String user;
	private String pw;
	private String name;

	@Extension
    public static class DescriptorImpl extends Descriptor<ServerToSave> {
        public String getDisplayName() { return ""; }
    }
	
	public String getName() {
		return name;
	}

	public String getServer() {
		return server;
	}

	public String getUser() {
		return user;
	}

	public String getPw() {
		return pw;
	}

	public String toString(){
		return ("Server: "+server+", User: "+user+", Password: "+pw);
	}

	@DataBoundConstructor
	public ServerToSave(String server, String user, String pw, String name){  
		this.server = server;
		this.user = user;
		this.pw = pw;
		this.name = name;
	}  
}