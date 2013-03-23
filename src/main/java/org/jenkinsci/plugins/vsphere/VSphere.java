package org.jenkinsci.plugins.vsphere;


import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

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
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;


/**
 * @author Eric Lordahl
 *
 */
public class VSphere {

	//private PrintStream jLogger;
	//private static VSphereLogger xenLogger = VSphereLogger.getVSphereLogger();

	private ServiceInstance si;

	private VSphere(String url, String user, String pw) throws RemoteException, MalformedURLException{
		//this.jLogger = jLogger;
		si = new ServiceInstance(new URL(url), user, pw, true);   
	}

	/**
	 * Initiates Connection to XenServer
	 */
	public static VSphere connect(String url, String user, String pw) throws RemoteException, MalformedURLException{
		return new VSphere(url,user,pw);
	}

	public static String vSphereOutput(String msg){
		return (Messages._VSphere_build_title()+": ").concat(msg);
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
	public boolean shallowCloneVm(String cloneName, String template, boolean verboseOutput) throws Exception {

		System.out.println("Creating a shallow clone of \""+ template + "\" to \""+cloneName);

		VirtualMachine sourceVm = getVmByName(template);

		if(sourceVm==null) {
			System.out.println("No VM " + template + " found");
			disconnect();
			return false;
		}

		VirtualMachineRelocateSpec rel  = new VirtualMachineRelocateSpec();
		rel.setDiskMoveType("createNewChildDiskBacking");
		rel.setPool(getResourcePoolByName(Messages.VSphere_pool_build()).getMOR());

		VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
		cloneSpec.setLocation(rel);
		cloneSpec.setPowerOn(false);
		cloneSpec.setTemplate(false);
		cloneSpec.setSnapshot(sourceVm.getCurrentSnapShot().getMOR());

		Task task = sourceVm.cloneVM_Task((Folder) sourceVm.getParent(), 
				cloneName, cloneSpec);
		System.out.println("Cloning VM. Please wait ...");

		String status = task.waitForTask();
		if(status==TaskInfoState.success.toString()) {
			System.out.println("VM got cloned successfully.");
		}
		else {
			System.out.println("Failure -: VM cannot be cloned");
			return false;
		}

		return true;
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
	 */
	public boolean startVm(String name) throws VmConfigFault, TaskInProgress, FileFault, InvalidState, InsufficientResourcesFault, RuntimeFault, RemoteException, InterruptedException{

		String status = getVmByName(name).powerOnVM_Task(null).waitForTask(10000, 10000);
		if(status==Task.SUCCESS)
		{
			System.out.println("VM was powered up successfully.");
			return true;
		}
		else
		{
			System.out.println("Failure -: VM cannot be started.");
			return false;
		} 
	}


	public boolean takeSnapshot(String name, String snapshot, String description){

		try {
			Task task = getVmByName(name).createSnapshot_Task(snapshot, description, false, false);
			if (task.waitForTask()==Task.SUCCESS) {
				return true;
			}
		} catch (Exception e) {

			e.printStackTrace();
		}

		return false;
	}

	public boolean revertToSnapshot(String vm, String snapshotname){

		return true;
	}


	public boolean takeSnapshotWithMemory(String name, String snapshot, String description){

		try {

			Task task = getVmByName(name).createSnapshot_Task(snapshot, description, true, false);
			if (task.waitForTask()==Task.SUCCESS) {
				return true;
			}
		} catch (Exception e) {

			e.printStackTrace();
		}

		return false;
	}

	public boolean markAsTemplate(String name, boolean force) throws InvalidProperty, RuntimeFault, RemoteException, InterruptedException{

		VirtualMachine vm = getVmByName(name);
		//TODO need to check if already a template
		if(isPoweredOff(vm) || force){
			powerDown(vm);
			getVmByName(name).markAsTemplate();
			return true;
		}

		return false;
	}

	public boolean markAsVm(String name){
		try{
			//getVmByName(name).markAsVirtualMachine(getResourcePoolByName("Build").getMOR(), host);
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Shortcut
	 * 
	 * @param name - VirtualMachine name of which IP is returned
	 * @return - String containing IP address
	 * @throws InterruptedException
	 * @throws RemoteException 
	 * @throws RuntimeFault 
	 * @throws InvalidProperty 
	 */
	public String getIp(String name) throws InterruptedException, InvalidProperty, RuntimeFault, RemoteException{

		VirtualMachine vm = getVmByName(name);
		final int MAX_TRIES = 20;
		final int SLEEP_SECONDS = 5;

		for(int count=1; count<MAX_TRIES+1; ++count){
			if(vm.getGuest().getIpAddress()!=null){
				return vm.getGuest().getIpAddress();
			}
			Thread.sleep(SLEEP_SECONDS * 1000);
		}
		return null;
	}

	/**
	 * @param vmName - name of VM object to retrieve
	 * @return - VirtualMachine object
	 * @throws InvalidProperty
	 * @throws RuntimeFault
	 * @throws RemoteException
	 */
	private VirtualMachine getVmByName(String vmName) throws InvalidProperty, RuntimeFault, RemoteException{

		return (VirtualMachine) new InventoryNavigator(
				si.getRootFolder()).searchManagedEntity(
						"VirtualMachine", vmName);
	}

	/**
	 * @param poolName - Name of pool to use
	 * @return - ResourcePool obect
	 * @throws InvalidProperty
	 * @throws RuntimeFault
	 * @throws RemoteException
	 */
	private ResourcePool getResourcePoolByName(String poolName) throws InvalidProperty, RuntimeFault, RemoteException{
		return (ResourcePool) new InventoryNavigator(
				si.getRootFolder()).searchManagedEntity(
						"ResourcePool", poolName);
	}

	/**
	 * Detroys the VM in vSphere
	 * @param vm - VM object to destroy
	 * @throws InterruptedException 
	 */
	public boolean destroyVm(String name) throws TaskInProgress, InvalidState, RuntimeFault, RemoteException, InterruptedException{
		VirtualMachine vm = getVmByName(name);
		if(!powerDown(vm))
			return false;

		String status = vm.destroy_Task().waitForTask();
		if(status==Task.SUCCESS)
		{
			System.out.println("VM was deleted successfully.");
			return true;
		}
		else
		{
			System.out.println("Failure -: VM cannot be deleted.");
			return false;
		} 
	}

	private boolean isPoweredOn(VirtualMachine vm){
		return (vm.getRuntime().getPowerState() ==  VirtualMachinePowerState.poweredOn);
	}
	
	private boolean isPoweredOff(VirtualMachine vm){
		return (vm.getRuntime().getPowerState() ==  VirtualMachinePowerState.poweredOff);
	}

	private boolean powerDown(VirtualMachine vm) throws TaskInProgress, InvalidState, RuntimeFault, RemoteException, InterruptedException{
		if (isPoweredOn(vm)) {
			String status = vm.powerOffVM_Task().waitForTask();
			
			//TODO is this better?
			//vm.shutdownGuest()

			if(status==Task.SUCCESS) {
				System.out.println("VM was powered down successfully.");
				return true;
			}
			else {
				System.out.println("Failure -: VM cannot be powered down.");
			}  
		}
		else if (isPoweredOff(vm)){
			System.out.println("Machine in already off.");
			return true;
		}
		else{
			System.out.println("Machine in incorrect state: "+ vm.getRuntime().getPowerState());
		}

		return false;
	}

	/**
	 * Logs out of the connection vSphere Session
	 */
	public void disconnect() {
		si.getServerConnection().logout();
	}
}