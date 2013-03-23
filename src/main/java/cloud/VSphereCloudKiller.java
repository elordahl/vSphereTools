package cloud;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import vsphere.VSphere;
import vsphere.VSphereLogger;

public class VSphereCloudKiller extends Builder{

	private final String serverPerform;
	private final String vm;
	private VSphereLogger logger = VSphereLogger.getVSphereLogger();
	
	@DataBoundConstructor
	public VSphereCloudKiller(String serverPerform,	String vm) {
		this.serverPerform = serverPerform;
		this.vm = vm;
	}
	
	public String getVm() {
		return vm;
	}

	public String getServerPerform(){
		return serverPerform;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		
		boolean killed = killVm(build, launcher, listener);
		if(!killed)
			return false;
		
		return true;
	}
	
	/* (non-Javadoc)
	 * @see hudson.tasks.BuildWrapper#setUp(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean killVm(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {

		PrintStream jLogger = listener.getLogger();
		VSphere xen = null;

		//Messages.VSPhereCloudBuilder_pathForMe();
		try {
			ServerToSave serverObject = VSphereDescriptor.DescriptorImpl.getServerObjectFromVM(serverPerform);

			//TODO:  check for /sdk and add if missing
			logger.verboseLogger(jLogger, "Using server configuration: " + serverObject.getName(), true);

			xen = VSphere.connect(serverObject.getServer(), serverObject.getUser(), serverObject.getPw());

			logger.verboseLogger(jLogger, "Destroying VM \""+vm+".\" Please wait ...", true);
			if(!xen.destroyVmByName(vm)){
				logger.verboseLogger(jLogger, "Destroyed!", true);
			}
		} catch (Throwable e) {
			e.printStackTrace(jLogger);
			return false;
		}finally{	 
			if(xen!=null)
				xen.disconnect();
		}
		
		return true;
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
		
		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return VSphere.vSphereOutput(Messages.VSphereCloudKiller_build_delete());
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// TODO Auto-generated method stub
			return true;
		}
	}
	
	
	
}
