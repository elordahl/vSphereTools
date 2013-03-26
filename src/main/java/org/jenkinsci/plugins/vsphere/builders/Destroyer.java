package org.jenkinsci.plugins.vsphere.builders;

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

import org.jenkinsci.plugins.vsphere.Server;
import org.jenkinsci.plugins.vsphere.VSpherePlugin;
import org.jenkinsci.plugins.vsphere.tools.VSphere;
import org.jenkinsci.plugins.vsphere.tools.VSphereException;
import org.jenkinsci.plugins.vsphere.tools.VSphereLogger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class Destroyer extends Builder{

	private final String vm;
	private final Server server;
	private final String serverName;
	private VSphere vsphere = null;
	private final VSphereLogger logger = VSphereLogger.getVSphereLogger();

	@DataBoundConstructor
	public Destroyer(String serverName,	String vm) throws VSphereException {
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
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)  {
		PrintStream jLogger = listener.getLogger();
		logger.verboseLogger(jLogger, "Using server configuration: " + server.getName(), true);

		boolean killed = false;

		try {
			vsphere = VSphere.connect(server.getServer(), server.getUser(), server.getPw());

			killed = killVm(build, launcher, listener);
		} catch (VSphereException e) {
			logger.verboseLogger(jLogger, "Error Converting to deleting VM: " + e.getMessage(), true);
		}

		if(vsphere!=null)
			vsphere.disconnect();

		return killed;
	}

	private boolean killVm(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws VSphereException {

		PrintStream jLogger = listener.getLogger();

		logger.verboseLogger(jLogger, "Destroying VM \""+vm+".\" Please wait ...", true);
		vsphere.destroyVm(vm);
		logger.verboseLogger(jLogger, "Destroyed!", true);

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
			return VSphere.vSphereOutput(Messages.vm_title_Destroyer());
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// TODO Auto-generated method stub
			return true;
		}

		private final VSpherePlugin.DescriptorImpl getGlobalDescriptor() {
			return Hudson.getInstance().getDescriptorByType(VSpherePlugin.DescriptorImpl.class);
		}

		public ListBoxModel doFillServerNameItems(){
			return getGlobalDescriptor().doFillServerItems();
		}
	}


	//TODO:  Make sure to set a default blank option in case the saved item gets deleted
}
