package org.jenkinsci.plugins.vsphere.tools;


import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

import org.jenkinsci.plugins.vsphere.Server;

import com.vmware.vim25.FileFault;
import com.vmware.vim25.InsufficientResourcesFault;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.InvalidState;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.TaskInProgress;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VmConfigFault;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;


/**
 * @author Eric Lordahl
 *
 */
public class VSphere {
	private final URL url;
	private final String session;
	
	private VSphere(String url, String user, String pw) throws VSphereException{
		
		try {
			this.url = new URL(url);
			this.session = (new ServiceInstance(this.url, user, pw, true)).getServerConnection().getSessionStr();
		} catch (Exception e) {
			throw new VSphereException(e);
		}
	}

	private ServiceInstance getServiceInstance() throws RemoteException, MalformedURLException{
		return new ServiceInstance(url, session, true);
	}

	/**
	 * Initiates Connection to vSphere Server
	 * @throws VSphereException 
	 */
	public static VSphere connect(Server server) throws VSphereException {
		return new VSphere(server.getServer(), server.getUser(), server.getPw());
	}

	public static String vSphereOutput(String msg){
		return (Messages.VSphereLogger_title()+": ").concat(msg);
	}

	/**
	 * Creates a new VM from a given template with a given name.
	 * 
	 * @param cloneName - name of VM to be created
	 * @param template - vsphere template name to clone
	 * @param verboseOutput - true for extra output to logs
	 * @return - Virtual Machine object of the new VM
	 * @throws Exception 
	 */
	public VirtualMachine shallowCloneVm(String cloneName, String template, boolean powerOn) throws VSphereException {

		System.out.println("Creating a shallow clone of \""+ template + "\" to \""+cloneName);
		try{
			VirtualMachine sourceVm = getVmByName(template);

			if(sourceVm==null) {
				throw new VSphereException("No template " + template + " found");
			}

			if(getVmByName(cloneName)!=null){
				throw new VSphereException("VM " + cloneName + " already exists");
			}

			VirtualMachineRelocateSpec rel  = new VirtualMachineRelocateSpec();
			rel.setDiskMoveType("createNewChildDiskBacking");
			rel.setPool(getResourcePoolByName(Messages.VSphere_pool_default()).getMOR());

			VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
			cloneSpec.setLocation(rel);
			cloneSpec.setPowerOn(powerOn);
			cloneSpec.setTemplate(false);
			cloneSpec.setSnapshot(sourceVm.getCurrentSnapShot().getMOR());

			Task task = sourceVm.cloneVM_Task((Folder) sourceVm.getParent(), 
					cloneName, cloneSpec);
			System.out.println("Cloning VM. Please wait ...");

			String status = task.waitForTask();
			if(status==TaskInfoState.success.toString()) {
				System.out.println("VM got cloned successfully.");
				return getVmByName(cloneName);
			}

		}catch(Exception e){
			throw new VSphereException(e);
		}

		throw new VSphereException("Error cloning \""+template+"!\" Does \""+cloneName+"\" already exist?");
	}	  

	/**
	 * @param vm
	 * @return
	 * @throws VmConfigFault
	 * @throws TaskInProgress
	 * @throws FileFault
	 * @throws InvalidState
	 * @throws InsufficientResourcesFault
	 * @throws RuntimeFault
	 * @throws RemoteException
	 * @throws InterruptedException
	 * @throws VSphereException 
	 */
	public void startVm(String name) throws VSphereException {

		try{
			VirtualMachine vm = getVmByName(name);
			if(isPoweredOn(vm))
				return;

			String status = vm.powerOnVM_Task(null).waitForTask(10000, 10000);
			if(status==Task.SUCCESS){
				System.out.println("VM was powered up successfully.");
				return;
			}
		}catch(Exception e){
			throw new VSphereException("VM cannot be started:", e);
		}

		throw new VSphereException("VM cannot be started");
	}


	public void takeSnapshot(String name, String snapshot, String description) throws VSphereException{

		try {
			Task task = getVmByName(name).createSnapshot_Task(snapshot, description, false, false);
			if (task.waitForTask()==Task.SUCCESS) {
				return;
			}
		} catch (Exception e) {
			throw new VSphereException("Could not take snapshot", e);
		}

		throw new VSphereException("Could not take snapshot");
	}

	public boolean revertToSnapshot(String vm, String snapshotname){

		return true;
	}

	public void markAsTemplate(String vmName, String snapName, String desc, boolean force) throws VSphereException {

		try{
			VirtualMachine vm = getVmByName(vmName);
			if(vm.getConfig().template)
				return;

			if(isPoweredOff(vm) || force){
				powerDown(vm, force);
				takeSnapshot(vmName, snapName, desc);
				vm.markAsTemplate();
				return;
			}
		}catch(Exception e){
			throw new VSphereException("Error: Could not convert to Template", e);
		}

		throw new VSphereException("Error: Could not mark as Template. Check it's power state or select \"force.\"");
	}

	public VirtualMachine markAsVm(String name) throws VSphereException{
		try{
			VirtualMachine vm = getVmByName(name);
			if(vm.getConfig().template){
				vm.markAsVirtualMachine(
						getResourcePoolByName(Messages.VSphere_pool_default()),
						getHostByName(Messages.VSphere_host_default())
				);
			}
			return vm;

		}catch(Exception e){
			throw new VSphereException("Error: Could not convert to VM", e);
		}
	}

	/**
	 * Shortcut
	 * 
	 * @param name - VirtualMachine name of which IP is returned
	 * @return - String containing IP address
	 * @throws VSphereException 
	 */
	public String getIp(VirtualMachine vm) throws VSphereException {

		if (vm==null)
			throw new VSphereException("vm is null");

		for(int count=0; count<VSphereConstants.IP_MAX_TRIES; ++count){
			if(vm.getGuest().getIpAddress()!=null){
				return vm.getGuest().getIpAddress();
			}
			try {
				Thread.sleep(VSphereConstants.IP_MAX_SECONDS * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * @param vmName - name of VM object to retrieve
	 * @return - VirtualMachine object
	 * @throws InvalidProperty
	 * @throws RuntimeFault
	 * @throws RemoteException
	 * @throws MalformedURLException 
	 * @throws VSphereException 
	 */
	private VirtualMachine getVmByName(String vmName) throws InvalidProperty, RuntimeFault, RemoteException, MalformedURLException {

		return (VirtualMachine) new InventoryNavigator(
				getServiceInstance().getRootFolder()).searchManagedEntity(
						"VirtualMachine", vmName);
	}

	/**
	 * @param poolName - Name of pool to use
	 * @return - ResourcePool obect
	 * @throws InvalidProperty
	 * @throws RuntimeFault
	 * @throws RemoteException
	 * @throws MalformedURLException 
	 * @throws VSphereException 
	 */
	private ResourcePool getResourcePoolByName(final String poolName) throws InvalidProperty, RuntimeFault, RemoteException, MalformedURLException {
		return (ResourcePool) new InventoryNavigator(
				getServiceInstance().getRootFolder()).searchManagedEntity(
						"ResourcePool", poolName);
	}

	/**
	 * @param poolName - Name of pool to use
	 * @return - ResourcePool obect
	 * @throws InvalidProperty
	 * @throws RuntimeFault
	 * @throws RemoteException
	 * @throws MalformedURLException 
	 * @throws VSphereException 
	 */
	private HostSystem getHostByName(final String hostName) throws InvalidProperty, RuntimeFault, RemoteException, MalformedURLException {
		return (HostSystem) new InventoryNavigator(
				getServiceInstance().getRootFolder()).searchManagedEntity(
						"HostSystem", hostName);
	}

	/**
	 * Detroys the VM in vSphere
	 * @param vm - VM object to destroy
	 * @throws InterruptedException 
	 */
	public void destroyVm(String name, boolean failOnNoExist) throws VSphereException{
		try{
			VirtualMachine vm = getVmByName(name);
			if(vm==null){
				if(failOnNoExist) throw new VSphereException("VM does not exist");
				
				System.out.println("VM does not exist, or already deleted!");
				return;
			}

			if(vm.getConfig().template)
				throw new VSphereException("Error: Specified name represents a template, not a VM.");

			powerDown(vm, true);

			String status = vm.destroy_Task().waitForTask();
			if(status==Task.SUCCESS)
			{
				System.out.println("VM was deleted successfully.");
				return;
			}

		}catch(Exception e){
			throw new VSphereException(e.getMessage());
		}

		throw new VSphereException("Error destroying VM");
	}

	private boolean isSuspended(VirtualMachine vm){
		return (vm.getRuntime().getPowerState() ==  VirtualMachinePowerState.suspended);
	}

	private boolean isPoweredOn(VirtualMachine vm){
		return (vm.getRuntime().getPowerState() ==  VirtualMachinePowerState.poweredOn);
	}

	private boolean isPoweredOff(VirtualMachine vm){
		return (vm.getRuntime().getPowerState() ==  VirtualMachinePowerState.poweredOff);
	}

	private void powerDown(VirtualMachine vm, boolean evenIfSuspended) throws VSphereException{
		if (isPoweredOn(vm) || (evenIfSuspended && isSuspended(vm))) {
			String status;
			try {
				//TODO is this better?
				//vm.shutdownGuest()
				status = vm.powerOffVM_Task().waitForTask();
			} catch (Exception e) {
				throw new VSphereException(e);
			}

			if(status==Task.SUCCESS) {
				System.out.println("VM was powered down successfully.");
				return;
			}
		}
		else if (isPoweredOff(vm)){
			System.out.println("Machine in already off.");
			return;
		}

		throw new VSphereException("Machine could not be powered down!");
	}

}
