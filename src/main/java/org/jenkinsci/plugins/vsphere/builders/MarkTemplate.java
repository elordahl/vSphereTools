package org.jenkinsci.plugins.vsphere.builders;

import hudson.EnvVars;
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

public class MarkTemplate extends Builder {

	private final String vm;
	private final boolean force;
	private final Server server;
	private final String serverName;
	private final VSphereLogger logger = VSphereLogger.getVSphereLogger();
	private VSphere vsphere = null;

	@DataBoundConstructor
	public MarkTemplate(String serverName, String vm, boolean force) throws VSphereException {
		this.serverName = serverName;
		server = getDescriptor().getGlobalDescriptor().getServer(serverName);
		this.force = force;
		this.vm = vm;
	}

	public String getVm() {
		return vm;
	}

	public String getServerName(){
		return serverName;
	}

	public boolean isForce() {
		return force;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {

		PrintStream jLogger = listener.getLogger();
		logger.verboseLogger(jLogger, "Using server configuration: " + server.getName(), true);
		boolean changed = false;

		try {
			//Need to ensure this server still exists.  If it's deleted
			//and a job is not opened, it will still try to connect
			getDescriptor().getGlobalDescriptor().checkServerExistence(server);

			vsphere = VSphere.connect(server);
			changed = markTemplate(build, launcher, listener);

		} catch (VSphereException e) {
			logger.verboseLogger(jLogger, "Error Converting to template: " + e.getMessage(), true);
		}

		if(vsphere!=null)
			vsphere.disconnect();

		return changed;
	}

	/* (non-Javadoc)
	 * @see hudson.tasks.BuildWrapper#setUp(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	private boolean markTemplate(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws VSphereException {
		PrintStream jLogger = listener.getLogger();
		logger.verboseLogger(jLogger, "Marking VM as template. Please wait ...", true);		

		EnvVars env;
		try {
			env = build.getEnvironment(listener);
		} catch (Exception e) {
			throw new VSphereException(e);
		}
		env.overrideAll(build.getBuildVariables()); // Add in matrix axes..
		String expandedVm = env.expand(vm);

		vsphere.markAsTemplate(expandedVm, force);
		logger.verboseLogger(jLogger, "VM \""+expandedVm+"\" is now a template.", true);


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
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return VSphere.vSphereOutput(Messages.vm_title_MarkTemplate());
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckVm(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please enter the template name");
			return FormValidation.ok();
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		private final VSpherePlugin.DescriptorImpl getGlobalDescriptor() {
			return Hudson.getInstance().getDescriptorByType(VSpherePlugin.DescriptorImpl.class);
		}

		public ListBoxModel doFillServerNameItems(){
			return getGlobalDescriptor().doFillServerItems();
		}
	}	
}