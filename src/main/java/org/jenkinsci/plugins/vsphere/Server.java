package org.jenkinsci.plugins.vsphere;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;

public class Server extends AbstractDescribableImpl<Server> {
	private String server;
	private String user;
	private Secret secretPassphrase;
	private String name;

	public final String getName() {
		return name;
	}

	public final String getServer() {
		return server;
	}

	public final String getUser() {
		return user;
	}

	public final String getPw() {
		return Secret.toString(secretPassphrase);
	}

	public final void setPassphrase(final String pw) { 
		secretPassphrase = Secret.fromString(pw); 
	}

	public final String getEncryptedPassphrase() {
		return (secretPassphrase == null) ? null : secretPassphrase.getEncryptedValue();
	}

	public String toString(){
		return ("Server: "+server+", User: "+user+", Name: "+name);
	}

	@DataBoundConstructor
	public Server(final String server, final String user, final String pw, final String name){  

		secretPassphrase = Secret.fromString(pw);
		//TODO:  check for /sdk and add if missing
		this.server = server;
		this.user = user;
		this.name = name;
	}  

	public Server(final JSONObject obj){
		this(obj.getString("server"), obj.getString("user"),obj.getString("pw"),obj.getString("name"));
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<Server> {
		public String getDisplayName() { return ""; }
	}

	/*public FormValidation doTest(@QueryParameter String server,
			@QueryParameter String user, 
			@QueryParameter String pw) {

		VSphere vSphere = null;

		try {

			vSphere = VSphere.connect(server, user, pw);

			return FormValidation.ok("Success");
		} catch (Exception e) {
			return FormValidation.error("Failure: "+ e.getMessage());
		}finally{
			if (vSphere != null){
				try {
					vSphere.disconnect();
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		}
	}*/
}
