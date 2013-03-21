package cloud;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.vmware.vim25.mo.VirtualMachine;

import vsphere.VSphere;
import vsphere.VSphereLogger;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link XenCloudBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Eric Lordahl
 */
public class VSphereCloudBuilder extends BuildWrapper {

	private final String template;
	private final String serverPerform;
	//private final String destination;
	//private final String javaPath;
	//private final boolean kill;
	//private final boolean verboseOutput;

	private static VSphereLogger vSphereLogger = VSphereLogger.getVSphereLogger();

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"

	//public VSphereCloudBuilder(String serverPerform, String template, String destination, boolean kill, boolean verboseOutput, String javaPath) {
	@DataBoundConstructor
	public VSphereCloudBuilder(String serverPerform, String template){//, boolean kill, boolean verboseOutput, String javaPath) {
		this.template = template;
		this.serverPerform = serverPerform;
		//this.destination = destination;
		//this.kill = kill;
		//		this.verboseOutput = verboseOutput;
		//		
		//		if(javaPath==null){
		//			this.javaPath = "java";
		//		}
		//		else{
		//			this.javaPath = javaPath;
		//		}
		//		xenLogger.setVerboseOutput(verboseOutput);
	}

	/**
	 * Getter methods for local variables.
	 */

	public String getTemplate() {
		return template;
	}

	/*public String getJavaPath() {
		return javaPath;
	}*/

	public String getServerPerform(){
		return serverPerform;
	}

	/*public String getDestination() {
		return destination;
	}*/

	/*public boolean getKill() {
		return kill;
	}

	public boolean getVerboseOutput() {
		return verboseOutput;
	}*/

	/* (non-Javadoc)
	 * @see hudson.tasks.BuildWrapper#setUp(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {

		PrintStream jLogger = listener.getLogger();
		VSphere xen = null;
		String vmIP = null;


		//Messages.XenCloudBuilder_pathForMe();
		try {
			String buildID = build.getEnvironment(listener).get("BUILD_ID");
			//String workspace = build.getEnvironment(listener).get("WORKSPACE");
			//String jenkinsUrl = build.getEnvironment(listener).get("JENKINS_URL");
			//String operatingSystemType = System.getProperty("os.name");
			ServerToSave serverObject = getDescriptor().getServerObjectFromVM(serverPerform);

			//TODO:  check for /sdk and add if missing
			vSphereLogger.verboseLogger(jLogger, "Using server configuration: " + serverObject.getName(), true);

			xen = VSphere.connect(serverObject.getServer(), serverObject.getUser(), serverObject.getPw());
			//new XenUtils(serverObject.getServer(), serverObject.getUser(), serverObject.getPw(), jLogger);

			vSphereLogger.verboseLogger(jLogger, "Cloning VM. Please wait ...", true);
			VirtualMachine vm = xen.shallowCloneVm(template+"-"+buildID, template, true);
			if (xen.startVm(vm)){
				vSphereLogger.verboseLogger(jLogger, "Waiting a maximum of 100 seconds for IP.", true);
				vmIP = xen.getIp(vm); 
			}

			if (vmIP!=null){
				vSphereLogger.verboseLogger(jLogger, "Got IP for \""+template+"-"+buildID+"\" ", true);
				XenEnvAction envAction = new XenEnvAction();
				envAction.add(vmIP);
				build.addAction(envAction);
			}
			//xenLogger.verboseLogger(jLogger, operatingSystemType + " Slave Running on: "+vmIP);

		} catch (Throwable e) {
			e.printStackTrace(jLogger);
			return null;
		}finally{	 
			xen.disconnect();
		}
		boolean kill = true;
		return new XenCloudEnvironment(kill);
	}


	private void slaveStuff(){
		/*	String jarPath = fetchSlaveJar(workspace, jLogger);

		///////////////////////////////////////////////////////////////////////////////////////////////////////////
		//////////////the code in this block needs to be encapsulated into a class (for linux)/////////////////////
		//////////////with some method like "start" that will also exist in the other [windows] class./////////////
		//////////////parent should be abstract////////////////////////////////////////////////////////////////////
		///////////////////////////////////////////////////////////////////////////////////////////////////////////
		//TODO Should create class using virtualization to copy/run on windows or linux
		String jarDestination = destination + File.separator + "swarm-client.jar";

		//ssh command to run
		String starter = javaPath+" -jar "+jarDestination+" -labels "+vmIP+" -executors 1 " +
		"-fsroot "+destination+ File.separator + "jenkins -master "+jenkinsUrl+" -name XenCloud-"+vmIP;

		xenLogger.verboseLogger(jLogger, "Command to start slave (without creds):");
		xenLogger.verboseLogger(jLogger, starter);

		if(serverObject.getJenkinsPw()!=null && serverObject.getJenkinsUser()!=null){
			starter+= " -username " + serverObject.getJenkinsUser() +
			" -password "+ serverObject.getJenkinsPw();
		}

		starter+=" > /dev/null &";
		///xenLogger.verboseLogger(jLogger, starter, true);

		SSHSite ssh = new SSHSite(vmIP, "22", serverObject.getVmUser(), serverObject.getVmPw(), serverObject.getKey());

		//copy slave jar to new machine
		int exitCode = ssh.scpToVM(jarPath, jarDestination, jLogger, verboseOutput);
					if(exitCode!=0){
				xenLogger.verboseLogger(jLogger, "Unable to transfer file to VM.", true);
				return null;        		
			}

			//run slave on new machine via ssh
			exitCode = ssh.executeCommand(jLogger, starter, verboseOutput);
			if(exitCode!=0){
				xenLogger.verboseLogger(jLogger, "Unable to start slave on VM.");
				return null;
			}
			////////////////////////////////////////////////////////////////////////////////////////////////////////
		 */
	}


	/**
	 * @author Lordahl
	 * 
	 * This class is used so that Jenkins can destroy the VM it created
	 * Functionality is in it's tearDown method.
	 * 
	 * kill parameter is used to determine if the VM should be destroyed
	 * or not.
	 *
	 */
	class XenCloudEnvironment extends Environment{

		private final boolean kill;
		XenCloudEnvironment(boolean kill){
			this.kill = kill;
		}

		/* (non-Javadoc)
		 * @see hudson.tasks.BuildWrapper.Environment#tearDown(hudson.model.AbstractBuild, hudson.model.BuildListener)
		 */
		public boolean tearDown( AbstractBuild build, BuildListener listener ){
			/*PrintStream jLogger = listener.getLogger();
			XenUtils xen = null;

			boolean tearDownSuccessful = false;
			try {
				String buildID = build.getEnvironment(listener).get("BUILD_ID");

				//if required, kill the VM that was created.
				if(kill){
					ServerToSave serverObject = getDescriptor().getServerObjectFromVM(serverPerform);
					xen = new XenUtils(serverObject.getServer(), serverObject.getUser(), serverObject.getPw(), jLogger);
					xen.killVM(template+"-"+buildID);
				}
				else{
					xenLogger.verboseLogger(jLogger, "Skipping VM destruction.", true);
				}

				tearDownSuccessful = super.tearDown(build, listener);
			} catch (Exception e) {
				e.printStackTrace(jLogger);
				return tearDownSuccessful;
			} finally{	 
				if(xen!=null){
					xen.disconnect();
				}
			}

			return tearDownSuccessful;*/
			return true;
		}
	}

	/**
	 * This method downloads the Swarm-client.jar file from the internet.  A lot of this method
	 * needs to be moved to a property file.
	 * 
	 * @param directory - location the fetched file will be stored on the local machine
	 * @param jLogger - stream to send all logs
	 * @return absolute path of new file
	 */
	private String fetchSlaveJar(String directory, PrintStream jLogger){
		boolean isRetrieved = false;
		int RETRY_COUNT = 5;

		//TODO move values to properties file
		String swarmVersion = "1.7"; //need to get from properties file
		String swarmJar = directory + File.separator +"swarm-client.jar";
		String urlStr = "http://maven.jenkins-ci.org" +
		"/content/repositories/releases/org/jenkins-ci/plugins/swarm-client/" +
		swarmVersion + "/swarm-client-"+swarmVersion+"-jar-with-dependencies.jar";

		//Messages.XenCloudBuilder_pathForMe();
		vSphereLogger.verboseLogger(jLogger, "Swarm Plugin Jar location:");
		vSphereLogger.verboseLogger(jLogger, urlStr);

		for(int i=1; (i<=RETRY_COUNT && !isRetrieved); i++){
			try {	     
				vSphereLogger.verboseLogger(jLogger, "["+i+"/"+RETRY_COUNT+"] Attempting to fetch swarm-plugin jar file...");
				URL url = new URL(urlStr);

				ReadableByteChannel rbc = Channels.newChannel(url.openStream());
				FileOutputStream fos = new FileOutputStream(swarmJar);
				fos.getChannel().transferFrom(rbc, 0, 1 << 24);

				File f = new File(swarmJar);
				isRetrieved = f.exists();
			}catch(Exception e){
				e.printStackTrace(jLogger);
			}		
		}

		if(!isRetrieved){
			jLogger.println("Could not download file.");
			return null;
		}

		vSphereLogger.verboseLogger(jLogger, "Successfully downloaded file to:");
		vSphereLogger.verboseLogger(jLogger, swarmJar);

		return swarmJar;
	}



	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	/**
	 * Descriptor for {@link XenCloudBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an extension point.
	public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private ArrayList<ServerToSave> serversToSave;

		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckTemplate(@QueryParameter String value)
		throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please enter the template name");
			return FormValidation.ok();
		}


		/*public FormValidation doCheckDestination(@QueryParameter String value)
		throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please enter the destination on the new template");
			return FormValidation.ok();
		}*/

		/*public FormValidation doCheckJavaPath(@QueryParameter String value)
		throws IOException, ServletException {
			if (!value.endsWith("java"))
				return FormValidation.error("You must provide a path to the java executable (e.g. /bin/jdk1.4/java)");
			return FormValidation.ok();
		}*/

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types 
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "vSphere: Create Slave from Template";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

			// To persist global configuration information,
			// set that to properties and call save().
			setServersToSave(formData);

			save();
			return true;
			//super.configure(req,formData);
		}

		public void setServersToSave(JSONObject formData){

			//clear each time
			serversToSave = new ArrayList<ServerToSave>();

			JSONArray serverJSONObjectArray;
			try{
				serverJSONObjectArray = formData.getJSONArray("serversToSave");
			}catch(JSONException jsone){
				serverJSONObjectArray = new JSONArray();
				serverJSONObjectArray.add(formData.getJSONObject("serversToSave"));
			}

			for (int i=0, j=serverJSONObjectArray.size(); i<j; i++){

				JSONObject serverCreds = serverJSONObjectArray.getJSONObject(i);

				String server = serverCreds.getString("server");
				String user = serverCreds.getString("user");
				String pw = serverCreds.getString("pw");
				String name = serverCreds.getString("name");
				/*String key = serverCreds.getString("key");
				String vmUser = serverCreds.getString("vmUser");
				String vmPw = serverCreds.getString("vmPw");
				String jenkinsUser = serverCreds.getString("jenkinsUser");
				String jenkinsPw = serverCreds.getString("jenkinsPw");*/

				serversToSave.add(new ServerToSave(server, user, pw, name));//, key, vmUser, vmPw, jenkinsUser, jenkinsPw));
			}
		}


		public List<ServerToSave> getServersToSave() {
			if (serversToSave == null) {
				serversToSave = new ArrayList<ServerToSave>();
			}

			return serversToSave;
		}

		//generates html options because jelly code has
		//problems with loops and am unable to parse code on client
		public String getServersHTMLOptions(){

			String returnStr = "";
			for(int i=0, j=serversToSave.size(); i<j; i++){
				returnStr += "<option value=\""+serversToSave.get(i).getName()+"\">" + serversToSave.get(i).getName() + "</option>";
			}

			return returnStr;
		}


		private ServerToSave getServerObjectFromVM(String serverName) throws Exception{

			for(int i=0, j=serversToSave.size(); i<j; i++){

				if(serversToSave.get(i).getName().equalsIgnoreCase(serverName)){
					return serversToSave.get(i);
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
				if (vSphere != null)
				{
					try {
						vSphere.disconnect();
					} catch (Exception e) {
						e.printStackTrace();
					} 
				}
			}
		}
	}

	/**
	 * This is a class used to hold all of the configuration information for the
	 * XenCloud plugin.
	 * 
	 * @author Lordahl
	 */
	public static final class ServerToSave implements Serializable {
		private static final long serialVersionUID = 1L;
		private String server;
		private String user;
		private String pw;
		private String name;
		/*		private String key;
		private String vmUser;
		private String vmPw;
		private String jenkinsUser;
		private String jenkinsPw;

		public String getJenkinsUser() {
			return jenkinsUser;
		}

		public String getJenkinsPw() {
			return jenkinsPw;
		}

		public String getVmUser() {
			return vmUser;
		}

		public String getVmPw() {
			return vmPw;
		}*/

		public String getName() {
			return name;
		}

		/*public String getKey() {
			return key;
		}*/

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

		public ServerToSave(String server, String user, String pw, String name){  
			//String key, String vmUser, String vmPw, String jenkinsUser, String jenkinsPw) {
			this.server = server;
			this.user = user;
			this.pw = pw;
			this.name = name;
			//			this.key = key;
			//			this.vmUser = vmUser;
			//			this.vmPw = vmPw;
			//			this.jenkinsPw = jenkinsPw;
			//			this.jenkinsUser = jenkinsUser;
		}  
	}

	/**
	 * This class is used to inject the IP value into the build environment
	 * as a variable so that it can be used with other plugins.
	 * 
	 * @author Lordahl
	 */
	private static class XenEnvAction implements EnvironmentContributingAction {
		// Decided not to record this data in build.xml, so marked transient:
		private transient Map<String,String> data = new HashMap<String,String>();

		private void add(String vmIP) {
			if (data==null) return;
			data.put("VSPHERE_VM_LABEL", vmIP);
		}

		public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
			if (data!=null) env.putAll(data);
		}

		public String getIconFileName() { return null; }
		public String getDisplayName() { return null; }
		public String getUrlName() { return null; }
	}
}
