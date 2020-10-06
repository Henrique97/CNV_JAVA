package aws;

import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static aws.RequestsAndInstancesManager.*;

public class AutoScaling {
    public static ConcurrentMap<String, SystemInstance> runningInstances = new ConcurrentHashMap<>();        // <InstanceId, Estimated Cargo>
    public static final Object autoScalerLock = new Object();

    public static SystemInstance getLaunchingInstance(double estimatedCargo){
        SystemInstance selectedInstance = new SystemInstance();
        for(SystemInstance instance : runningInstances.values()){
            if(     instance.status == SystemInstance.LAUNCHING_STATUS &&
                    instance.cargoInQueue  + estimatedCargo< MAX_CARGO_IN_QUEUE &&
                    instance.requestsInQueue + 1 < MAX_REQUESTS_IN_QUEUE &&
                    instance.cargoInQueue < selectedInstance.cargoInQueue
            )
                selectedInstance = instance;
        }
        System.out.println(">>>> Selected LAUNCHING instance: "+ selectedInstance);
        return selectedInstance;
    }

    private static Instance getInstanceStatusCode(String newInstanceId){
        try{
            final DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
            final List<Reservation> reservations = describeInstancesResult.getReservations();
            Instance requestInstance = null;

            for (Reservation reservation : reservations) {
                boolean found = false;
                for(Instance reserved : reservation.getInstances()){
                    if( reserved.getInstanceId().equals(newInstanceId)) {
                        requestInstance = reserved;
                        found = true;
                        break;
                    }
                }
                if(found) break;
            }
            if(requestInstance == null)
                throw new IllegalArgumentException("Some problem occurred while obtaining new instance status");
            return requestInstance;
        }catch (Exception e){
            System.out.println("Impossible to obtain instances description");
        }
        Instance errorReturn = new Instance();
        InstanceState state = new InstanceState();
        state.setCode(64);
        errorReturn.setState(state);
        return errorReturn;
    }

    public static SystemInstance launchNewInstance(double estimatedCargo, int jobId){
        if(runningInstances.size() >= MAX_INSTANCES_COUNT){
            try {
                autoScalerLock.wait(5000);      //Give some time so that this request doesn't come back right away
            } catch (InterruptedException e) {
                // Exception not needed
            }
            return new SystemInstance();
        }

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId(IMAGE_ID)
                .withInstanceType(INSTANCE_TYPE_NAME)
                .withMinCount(1)
                .withMaxCount(INSTANCES_TO_LAUNCH)
                .withKeyName(KEY_NAME)
                .withSecurityGroups(SECURITY_GROUP_NAME);

        final RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
        Instance instance = runInstancesResult.getReservation().getInstances().get(0);
        final String newInstanceId = instance.getInstanceId();
        final SystemInstance newSystemInstance = new SystemInstance(newInstanceId);
        if(jobId != -1) LoadBalancer.appendWorkToInstance(newSystemInstance, estimatedCargo, jobId);
        runningInstances.putIfAbsent(newInstanceId, newSystemInstance);

        /*
            According to AWS official documentation, the codes are:
                0 : pending; 16 : running; 32 : shutting-down; 48 : terminated; 64 : stopping; 80 : stopped
         */

        while (true){
            try {
                System.out.println("Waiting for instance "+ newInstanceId + " to be running");
                autoScalerLock.wait(15000);                                  // Use the wait to release the lock and allow other threads to have visibility on the runningInstances list
            } catch (InterruptedException e) {
                System.out.println("An error occurred on Thread.Sleep in AutoScaling.launchNewInstance()");
            }
            final int code = getInstanceStatusCode(newInstanceId).getState().getCode();
            if(code > 16){
                //If the cargo is the maximum long, then the check will fail and the request will try to use/create another instance
                System.out.println("Unable to create an instance");
                runningInstances.remove(newInstanceId, newSystemInstance);
                newSystemInstance.status = SystemInstance.DUMMY_STATUS;             // DUMMY state invalidates the instance
                break;
            } else if (code == 16){
                newSystemInstance.publicIPAddress = getInstanceStatusCode(newInstanceId).getPublicIpAddress();
                try {
                    autoScalerLock.wait(10000);
                } catch (InterruptedException e) {

                }
                newSystemInstance.status = SystemInstance.RUNNING_STATUS;
                System.out.println("Instance created with IP " + newSystemInstance.publicIPAddress + "\n " +
                        "\tObject: " + newSystemInstance.toString());
                break;
            }
            if (code == 0){
                //If while waiting another instance is available, then use it
                SystemInstance availableRunningInstance = LoadBalancer.getAvailableInstance(estimatedCargo);
                if(availableRunningInstance.status != SystemInstance.DUMMY_STATUS){
                    LoadBalancer.removeWorkFromInstance(newSystemInstance, estimatedCargo, jobId);
                    System.out.println("Another instance was attributed to a job that created an instance");
                    new Thread(new Runnable() {         //Ensure that threads that block waiting for this instance to launch are not there forever
                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    System.out.println("Waiting for instance "+ newInstanceId + " to be running - inside dedicated Thread");
                                    Thread.sleep(10000);
                                } catch (InterruptedException e) {
                                    System.out.println("An error occurred on Thread.Sleep in AutoScaling.launchNewInstance()");
                                }
                                final Instance instanceAwaitingLaunch = getInstanceStatusCode(newInstanceId);
                                final int statusCode = instanceAwaitingLaunch.getState().getCode();
                                if (statusCode > 16) {
                                    //If the cargo is the maximum long, then the check will fail and the request will try to use/create another instance
                                    runningInstances.remove(newInstanceId, newSystemInstance);
                                    newSystemInstance.status = SystemInstance.DUMMY_STATUS;             // DUMMY state invalidates the instance
                                    break;
                                } else if (statusCode == 16) {
                                        newSystemInstance.publicIPAddress = instanceAwaitingLaunch.getPublicIpAddress();
                                        synchronized (autoScalerLock){
                                            try {
                                                autoScalerLock.wait(10000);
                                            } catch (InterruptedException e) {

                                            }
                                            newSystemInstance.status = SystemInstance.RUNNING_STATUS;
                                            autoScalerLock.notifyAll();
                                        }
                                    System.out.println("Instance created (inside thread) with IP " + newSystemInstance.publicIPAddress);
                                    break;
                                }
                            }
                        }
                    });

                    return availableRunningInstance;
                }
            }
        }
        autoScalerLock.notifyAll();
        return newSystemInstance;
    }

    /**
     * Verifies if there is a need to launch a new instance upon receiving a request and not having any available instance to attend it.
     * If there is an instance already being launched and still accepting requests (i.e., did not reach yet the limits for cargo & requests),
     * this request will be associated to it, otherwise, another instance will be launch
     *
     * /!\ This method can only be executed by one thread at a time
     * @param estimatedCargo the value of the cargo for the current request
     * @param jobId identifier of the current request
     *
     * @return the selected instance
     */
    public static SystemInstance requestNewInstance(double estimatedCargo, int jobId)  {
        System.out.println("Requesting a new instance | Estimated cargo: " + estimatedCargo);
        synchronized (autoScalerLock){
            while(true){
                // If between the time the handler verified that there were no instances and the executed this method, there is already an available instance, use it
                SystemInstance availableRunningInstance = LoadBalancer.getAvailableInstance(estimatedCargo);

                if(availableRunningInstance.status != SystemInstance.DUMMY_STATUS) return availableRunningInstance;

                SystemInstance availableInstance = getLaunchingInstance(estimatedCargo);

                if(availableInstance.status != SystemInstance.DUMMY_STATUS) {
                    System.out.println("-->>Instance launching found: " + availableInstance.instanceId);
                    LoadBalancer.appendWorkToInstance(availableInstance, estimatedCargo, jobId);
                    try {
                        autoScalerLock.wait(10000);
                    } catch (InterruptedException e) {
                        LoadBalancer.removeWorkFromInstance(availableInstance, estimatedCargo, jobId);
                    }
                    if (availableInstance.status == SystemInstance.RUNNING_STATUS){
                        return availableInstance;
                    }
                    //If not running, try again and remove our cargo (instead of locking on this instance, we try to find another one that is already available or with less cargo)
                    LoadBalancer.removeWorkFromInstance(availableInstance, estimatedCargo, jobId);
                }else{
                    System.out.println("Creating a new instance");
                    availableInstance = launchNewInstance(estimatedCargo, jobId);
                    if(availableInstance.status == SystemInstance.RUNNING_STATUS){
                        return availableInstance;
                    }
                }
            }
        }
    }

    /**
     * Increments the instance healthChecksFailed counter and, if the counter is greater than the defined threshold,
     * terminates that instance
     *
     * @param instanceId identifier of the instance
     * @return true if the instance has been terminated, therefore, the load balancer MUST remove this instance from its list
     * of unhealthy instances, false otherwise
     */
    public static boolean reportFailedHealthCheck(String instanceId){
        System.out.println(instanceId + " failed a Health Check");
        SystemInstance systemInstance = runningInstances.get(instanceId);
        if(systemInstance == null)
            throw new IllegalArgumentException("Fail at AutoScaling.reportFailedHealthCheck(): SystemInstance identifier not valid");
        systemInstance.status = SystemInstance.UNSTABLE_STATUS;
        if(++systemInstance.healthChecksFailed >= MAX_FAILED_HEALTH_CHECKS){
            systemInstance.terminateInstance(ec2);
            runningInstances.remove(instanceId, systemInstance);
            return true;
        }
        return false;
    }

    /**
     * This method is called only if an instance has failed an health check, therefore having a healthChecksFailed greater than 0,
     * if the instance is responsive again, than that counter needs to be reset.
     * To know which instances have failed the health check, the load balancer needs to maintain in memory which are the unhealthy instances
     *
     * @param instanceId identifier of the instance
     */
    public static void clearHealthChecks(String instanceId){
        System.out.println(instanceId + " revived after a Health Check");
        SystemInstance systemInstance = runningInstances.get(instanceId);
        if(systemInstance == null)
            throw new IllegalArgumentException("Fail at AutoScaling.clearHealthChecks(): SystemInstance identifier not valid");
        systemInstance.healthChecksFailed = 0;
        systemInstance.status = SystemInstance.RUNNING_STATUS;
    }
}
