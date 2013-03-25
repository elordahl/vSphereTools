package org.jenkinsci.plugins.vsphere.cloud;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.ServletException;

import org.jenkinsci.plugins.vsphere.VSphere;
import org.jenkinsci.plugins.vsphere.VSphereLogger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class Destroyer extends Builder{

	private final String vm;
	private final Server server;
	private final String serverName;
	private VSphere vsphere = null;
	private final VSphereLogger logger = VSphereLogger.getVSphereLogger();

	@DataBoundConstructor
	public Destroyer(String serverName,	String vm) throws Exception {
		this.serverName = serverName;
		server = getDescriptor().getGlobalDescriptor().getServer(serverName);
		this.vm = vm;
	}

	public String getVm() {
		return vm;
	}

	public String getServerName(){
		return serverName;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		PrintStream jLogger = listener.getLogger();
		boolean killed = false;

		try {

			vsphere = VSphere.connect(server.getServer(), server.getUser(), server.getPw());
			logger.verboseLogger(jLogger, "Using server configuration: " + server.getName(), true);

			killed = killVm(build, launcher, listener);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if(vsphere!=null)
			vsphere.disconnect();

		return killed;
	}

	public boolean killVm(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {

		PrintStream jLogger = listener.getLogger();

		try {
			logger.verboseLogger(jLogger, "Destroying VM \""+vm+".\" Please wait ...", true);
			if(vsphere.destroyVm(vm)){
				logger.verboseLogger(jLogger, "Destroyed!", true);
				return true;
			}
		} catch (Throwable e) {
			e.printStackTrace(jLogger);
		}	 

		return false;
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
		 * Performs on-the-fly validation of the form field 'clone'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckVm(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please enter the VM name");
				//TODO check if Vm exists
			return FormValidation.ok();
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return VSphere.vSphereOutput(Messages.Destroyer_vm_delete());
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// TODO Auto-generated method stub
			return true;
		}

		private final VSphereDescriptor.DescriptorImpl getGlobalDescriptor() {
			return Hudson.getInstance().getDescriptorByType(VSphereDescriptor.DescriptorImpl.class);
        }

		public ListBoxModel doFillServerNameItems(){
			return getGlobalDescriptor().doFillServerItems();
		}
	}


//TODO:  Make sure to set a default blank option in case the saved item gets deleted
}
