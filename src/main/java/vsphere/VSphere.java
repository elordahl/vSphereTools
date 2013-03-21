package vsphere;

import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

import com.vmware.vim25.FileFault;
import com.vmware.vim25.InsufficientResourcesFault;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.InvalidState;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.TaskInProgress;
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

	/**
	 * Creates a new VM from a given template with a given name.
	 * 
	 * @param cloneName - name of VM to be created
	 * @param template - vsphere template name to clone
	 * @param verboseOutput - true for extra output to logs
	 * @return - Virtual Machine object of the new VM
	 * @throws Exception 
	 */
	public VirtualMachine shallowCloneVm(String cloneName, String template, boolean verboseOutput) throws Exception {

		System.out.println("Creating a shallow clone of \""+ template + "\" to \""+cloneName);

		VirtualMachine sourceVm = getVmByName(template);

		//TODO put this somewhere else.
		String poolName = "Build";

		//TODO add something to logout.
		//	      if(sourceVm==null)
		//	      {
		//	        System.out.println("No VM " + vmName + " found");
		//	        si.getServerConnection().logout();
		//	        return null;
		//	        
		//	      }

		VirtualMachineRelocateSpec rel  = new VirtualMachineRelocateSpec();
		rel.setDiskMoveType("createNewChildDiskBacking");
		rel.setPool(getResourcePoolByName(poolName).getMOR());

		VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
		cloneSpec.setLocation(rel);
		cloneSpec.setPowerOn(false);
		cloneSpec.setTemplate(false);
		cloneSpec.setSnapshot(sourceVm.getCurrentSnapShot().getMOR());

		Task task = sourceVm.cloneVM_Task((Folder) sourceVm.getParent(), 
				cloneName, cloneSpec);
		System.out.println("Cloning VM. Please wait ...");

		String status = task.waitForMe();
		if(status==Task.SUCCESS)
		{
			System.out.println("VM got cloned successfully.");
		}
		else
		{
			System.out.println("Failure -: VM cannot be cloned");
			return null;
		}

		return getVmByName(cloneName);
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
	public boolean startVm(VirtualMachine vm) throws VmConfigFault, TaskInProgress, FileFault, InvalidState, InsufficientResourcesFault, RuntimeFault, RemoteException, InterruptedException{
		Task task = vm.powerOnVM_Task(null);
		String status = task.waitForTask(10000, 10000);
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

	/**
	 * @param vm - VirtualMachine object of which IP is returned
	 * @return - String containing IP address
	 * @throws InterruptedException
	 */
	public String getIp(VirtualMachine vm) throws InterruptedException{

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

		return getIp(getVmByName(name));
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
	 */
	public boolean destroyVm(VirtualMachine vm) throws TaskInProgress, InvalidState, RuntimeFault, RemoteException{
		Task task;
		String status;
		if (vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn){
			task = vm.powerOffVM_Task();
			status = task.waitForMe();

			//String status = task.waitForTask(10000, 10000);
			if(status==Task.SUCCESS)
			{
				System.out.println("VM was powered down successfully.");
			}
			else
			{
				System.out.println("Failure -: VM cannot be powered down.");
				return false;
			}  
		}

		task = vm.destroy_Task();
		status = task.waitForMe();
		//String status = task.waitForTask(10000, 10000);
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

	/**
	 * Logs out of the connection vSphere Session
	 */
	public void disconnect() {
		si.getServerConnection().logout();
	}
}