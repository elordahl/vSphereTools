package cloud;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import vsphere.VSphere;

/**
 * Descriptor for {@link XenCloudBuilder}. Used as a singleton.
 * The class is marked as public so that it can be accessed from views.
 *
 * <p>
 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
 * for the actual HTML fragment for the configuration screen.
 */
// This indicates to Jenkins that this is an implementation of an extension point.
@Extension
public class VSphereDescriptor extends Builder {




	@Override
	public DescriptorImpl getDescriptor() {
		// see Descriptor javadoc for more about what a descriptor is.
		return (DescriptorImpl)super.getDescriptor();
	}

	/**
	 * Descriptor for {@link HelloWorldBuilder}.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>views/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	// this annotation tells Hudson that this is the implementation of an extension point
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public String getDisplayName() {
			// TODO Auto-generated method stub
			return null;
		}
		/**
		 * 
		 * 
		 */


		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private static ArrayList<ServerToSave> serversToSave;

		public DescriptorImpl () {
			//super();
			load();
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

			// To persist global configuration information,
			// set that to properties and call save().
			serversToSave = saveFormServers(formData);

			save();
			return true;
			//super.configure(req,formData);
		}

		public ArrayList<ServerToSave> saveFormServers(JSONObject formData){

			//clear each time
			ArrayList<ServerToSave> servers = new ArrayList<ServerToSave>();

			JSONArray serverJSONObjectArray;
			try{
				serverJSONObjectArray = formData.getJSONArray("serversToSave");
			}catch(JSONException jsone){
				serverJSONObjectArray = new JSONArray();
				serverJSONObjectArray.add(formData.getJSONObject("serversToSave"));
			}

			for (int i=0, j=serverJSONObjectArray.size(); i<j; i++){

				JSONObject serverCreds = serverJSONObjectArray.getJSONObject(i);

				servers.add(new ServerToSave(
						serverCreds.getString("server"), 
						serverCreds.getString("user"), 
						serverCreds.getString("pw"), 
						serverCreds.getString("name")
				));
			}

			return servers;
		}


		public List<ServerToSave> getServersToSave() {
			if (serversToSave == null) {
				serversToSave = new ArrayList<ServerToSave>();
			}

			return serversToSave;
		}

		//generates html options because jelly code has
		//problems with loops and am unable to parse code on client
		public static String getServersHTMLOptions(){

			String returnStr = "";
			for(ServerToSave server : serversToSave){
				returnStr += "<option value=\""+server.getName()+"\">" + server.getName() + "</option>";
			}

			return returnStr;
		}


		public static ServerToSave getServerObjectFromVM(String serverName) throws Exception{

			for(ServerToSave server : serversToSave){
				if(server.getName().equalsIgnoreCase(serverName)){
					return server;
				}
			}

			throw new Exception("Server not found!");
		}


		public FormValidation doTestConnection(@QueryParameter("server") final String server,
				@QueryParameter("user") final String user, 
				@QueryParameter("pw") final String pw) {

			VSphere vSphere = null;

			try {

				vSphere = VSphere.connect(server, user, pw);

				return FormValidation.ok("Success");
			} catch (Exception e) {
				return FormValidation.error("Failure");
			}finally{
				if (vSphere != null){
					try {
						vSphere.disconnect();
					} catch (Exception e) {
						e.printStackTrace();
					} 
				}
			}
		}
	}
}