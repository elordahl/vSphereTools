package org.jenkinsci.plugins.vsphere.builders;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.vsphere.Server;
import org.jenkinsci.plugins.vsphere.VSpherePlugin;
import org.jenkinsci.plugins.vsphere.tools.VSphere;
import org.jenkinsci.plugins.vsphere.tools.VSphereLogger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class Starter extends Builder{

	private final String template;
	private final Server server;
	private final String serverName;
	private final String clone;
	private VSphere vsphere = null;
	private final VSphereLogger logger = VSphereLogger.getVSphereLogger();

	@DataBoundConstructor
	public Starter(String serverName, String template,
			String clone) throws Exception {
		this.template = template;
		this.serverName = serverName;
		server = getDescriptor().getGlobalDescriptor().getServer(serverName);
		this.clone = clone;
	}


	public String getTemplate() {
		return template;
	}

	public String getClone() {
		return clone;
	}

	public String getServerName(){
		return serverName;
	}
	
	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		PrintStream jLogger = listener.getLogger();
		boolean success=false;
		try{
			logger.verboseLogger(jLogger, "Using server configuration: " + server.getName(), true);
			vsphere = VSphere.connect(server.getServer(), server.getUser(), server.getPw());

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

	public boolean deployFromTemplate(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		boolean deployed = false;
		PrintStream jLogger = listener.getLogger();
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

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return VSphere.vSphereOutput(Messages.vm_title_Starter());
		}
		

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
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

		
		private final VSpherePlugin.DescriptorImpl getGlobalDescriptor() {
			return Hudson.getInstance().getDescriptorByType(VSpherePlugin.DescriptorImpl.class);
        }

		public ListBoxModel doFillServerNameItems(){
			return getGlobalDescriptor().doFillServerItems();
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