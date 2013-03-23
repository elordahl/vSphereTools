package org.jenkinsci.plugins.vsphere.cloud;

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

import org.jenkinsci.plugins.vsphere.VSphere;
import org.jenkinsci.plugins.vsphere.VSphereLogger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class MarkTemplate extends Builder{

	private final String vm;
	private final boolean force;
	private final String serverPerform;

	private VSphereLogger logger = VSphereLogger.getVSphereLogger();
	private VSphere vsphere = null;
	
	@DataBoundConstructor
	public MarkTemplate(String serverPerform, String vm, boolean force) {
		this.serverPerform = serverPerform;
		this.force = force;
		this.vm = vm;
	}
	
	public String getVm() {
		return vm;
	}

	public String getServerPerform(){
		return serverPerform;
	}
	
	public boolean isForce() {
		return force;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		
		PrintStream jLogger = listener.getLogger();
		
		try {
			ServerToSave serverObject = VSphereDescriptor.DescriptorImpl.getServerObjectFromVM(serverPerform);
			logger.verboseLogger(jLogger, "Using server configuration: " + serverObject.getName(), true);
	
			vsphere = VSphere.connect(serverObject.getServer(), serverObject.getUser(), serverObject.getPw());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		boolean changed = markTemplate(build, launcher, listener);
		
		
		if(vsphere!=null)
			vsphere.disconnect();
		
		return changed;
	}
	
	/* (non-Javadoc)
	 * @see hudson.tasks.BuildWrapper#setUp(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean markTemplate(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		PrintStream jLogger = listener.getLogger();
		logger.verboseLogger(jLogger, "Marking VM as template. Please wait ...", true);		
		try {
			
			if (vsphere.markAsTemplate(vm, force)){
				logger.verboseLogger(jLogger, "VM \""+vm+"\" is now a template.", true);
				return true;
			}
		} catch (Throwable e) {
			e.printStackTrace(jLogger);
			logger.verboseLogger(jLogger, "Error changing VM to template: " + e.getMessage(), true);
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
		
		public static String getServersHTMLOptions(){
			return VSphereDescriptor.DescriptorImpl.getServersHTMLOptions();
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return VSphere.vSphereOutput(Messages.MarkTemplate_vm_template());
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
			// TODO Auto-generated method stub
			return true;
		}
	}	
}