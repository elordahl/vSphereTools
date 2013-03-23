package cloud;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import vsphere.VSphere;
import vsphere.VSphereLogger;

import com.vmware.vim25.mo.VirtualMachine;

public class VSphereCloudStarter extends Builder{

	private final String template;
	private final String serverPerform;
	private final String clone;
	private VSphereLogger logger = VSphereLogger.getVSphereLogger();
	
	@DataBoundConstructor
	public VSphereCloudStarter(String serverPerform, String template,
			String clone) {
		this.template = template;
		this.serverPerform = serverPerform;
		this.clone = clone;
	}
	
	
	public String getTemplate() {
		return template;
	}
	
	public String getClone() {
		return clone;
	}

	public String getServerPerform(){
		return serverPerform;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		
		String vmIP = deployFromTemplate(build, launcher, listener);
		if(vmIP==null)
			return false;
		
		VSphereEnvAction envAction = new VSphereEnvAction();
		envAction.add("VSPHERE_"+clone, vmIP);
		build.addAction(envAction);
		
		return true;
	}
	
	/* (non-Javadoc)
	 * @see hudson.tasks.BuildWrapper#setUp(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public String deployFromTemplate(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {

		PrintStream jLogger = listener.getLogger();
		VSphere xen = null;
		String vmIP = null;

		//Messages.VSPhereCloudBuilder_pathForMe();
		try {
			cloud.ServerToSave serverObject = VSphereDescriptor.DescriptorImpl.getServerObjectFromVM(serverPerform);

			//TODO:  check for /sdk and add if missing
			logger.verboseLogger(jLogger, "Using server configuration: " + serverObject.getName(), true);

			xen = VSphere.connect(serverObject.getServer(), serverObject.getUser(), serverObject.getPw());

			logger.verboseLogger(jLogger, "Cloning VM. Please wait ...", true);
			VirtualMachine vm = xen.shallowCloneVm(clone, template, true);
			if (xen.startVm(vm)){
				logger.verboseLogger(jLogger, "Waiting a maximum of 100 seconds for IP.", true);
				vmIP = xen.getIp(vm); 
			}
		} catch (Throwable e) {
			e.printStackTrace(jLogger);
		}finally{	 
			if(xen!=null)
				xen.disconnect();
		}
		
		if (vmIP!=null){
			logger.verboseLogger(jLogger, "Got IP for \""+clone+"\" ", true);
		}
		
		return vmIP;
	}
	
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl )super.getDescriptor();
	}
	
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		public DescriptorImpl() {
			load();
		}
		
		public static String getServersHTMLOptions(){
			return VSphereDescriptor.DescriptorImpl.getServersHTMLOptions();
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return VSphere.vSphereOutput(Messages.VSphereCloudStarter_build_start());
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
		
		/**
		 * Performs on-the-fly validation of the form field 'clone'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckClone(@QueryParameter String value)
		throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please enter the clone name");
			return FormValidation.ok();
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// TODO Auto-generated method stub
			return true;
		}
	}
	
	/**
	 * This class is used to inject the IP value into the build environment
	 * as a variable so that it can be used with other plugins.
	 * 
	 * @author Lordahl
	 */
	private static class VSphereEnvAction implements EnvironmentContributingAction {
		// Decided not to record this data in build.xml, so marked transient:
		private transient Map<String,String> data = new HashMap<String,String>();

		private void add(String key, String val) {
			if (data==null) return;
			data.put(key, val);
		}

		public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
			if (data!=null) env.putAll(data);
		}

		public String getIconFileName() { return null; }
		public String getDisplayName() { return null; }
		public String getUrlName() { return null; }
	}
	
}