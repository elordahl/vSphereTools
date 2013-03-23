package org.jenkinsci.plugins.vsphere.cloud;

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

import org.jenkinsci.plugins.vsphere.VSphere;
import org.jenkinsci.plugins.vsphere.VSphereLogger;

import com.vmware.vim25.mo.VirtualMachine;

public class Starter extends Builder{

	private final String template;
	private final String serverPerform;
	private final String clone;
	private VSphere vsphere = null;
	private VSphereLogger logger = VSphereLogger.getVSphereLogger();

	@DataBoundConstructor
	public Starter(String serverPerform, String template,
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
		PrintStream jLogger = listener.getLogger();
		boolean success=false;
		try{
			ServerToSave serverObject = VSphereDescriptor.DescriptorImpl.getServerObjectFromVM(serverPerform);
			logger.verboseLogger(jLogger, "Using server configuration: " + serverObject.getName(), true);
			vsphere = VSphere.connect(serverObject.getServer(), serverObject.getUser(), serverObject.getPw());

			if(deployFromTemplate(build, launcher, listener)){
				String vmIP = vsphere.getIp(clone); 
				if(vmIP!=null){
					logger.verboseLogger(jLogger, "Got IP for \""+clone+"\" ", true);
					VSphereEnvAction envAction = new VSphereEnvAction();
					envAction.add("VSPHERE_"+clone, vmIP);
					build.addAction(envAction);
					success=true;
				}
				else{
					logger.verboseLogger(jLogger, "Couldn't get IP Address!", true);
				}
			}			
		} catch(Exception e){
			e.printStackTrace();
		}

		if(vsphere!=null)
			vsphere.disconnect();

		return success;
	}

	/* (non-Javadoc)
	 * @see hudson.tasks.BuildWrapper#setUp(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean deployFromTemplate(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		boolean deployed = false;
		PrintStream jLogger = listener.getLogger();
		String vmIP = null;
		logger.verboseLogger(jLogger, "Cloning VM. Please wait ...", true);
		
		try {
			if (vsphere.shallowCloneVm(clone, template, true) && vsphere.startVm(clone)){
				logger.verboseLogger(jLogger, "Clone successful! Waiting a maximum of 100 seconds for IP.", true);
				deployed=true;
			}
		} catch (Throwable e) {
			e.printStackTrace(jLogger);
		}

		return deployed;
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
			return VSphere.vSphereOutput(Messages.Starter_vm_start());
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