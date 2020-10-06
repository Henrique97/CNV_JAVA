package aws;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SystemInstance {
    public static final int             LAUNCHING_STATUS            = 0,
                                        RUNNING_STATUS              = 1,
                                        TERMINATING_STATUS          = 2,
                                        UNSTABLE_STATUS             = 3,         // Used when an instance fails to respond a request, this state is reset after a successful health check
                                        DUMMY_STATUS                = -1;


    public final String                 instanceId;
    public String                       publicIPAddress;
    public int                          requestsInQueue,                         // Number of requests waiting to be attended by this instance
                                        status,
                                        healthChecksFailed;                      // Number of consecutive health checks failed by this instance
    public long                         cargoInQueue,                            // Measures the cargo that all the requests in queue have
                                        lastOperationTimestamp;                  // Timestamp of the last operation, used to know if the instance doesn't receive work and can be terminated
    public ConcurrentMap<Integer, Double> jobsInQueue = new ConcurrentHashMap<>(); // List of jobs that this instance has <Job ID, Job Cargo*/
    public Object                       instanceLock = new Object();             // Use to ensure that no two requests added their cargo to this instance

    public SystemInstance() {
        this.instanceId = "";
        this.publicIPAddress = "";
        requestsInQueue = 0;
        cargoInQueue = Long.MAX_VALUE;
        healthChecksFailed = 0;
        lastOperationTimestamp = 0;
        status = DUMMY_STATUS;
    }

    public SystemInstance(String instanceId) {
        this.instanceId = instanceId;
        this.publicIPAddress = "";
        requestsInQueue = 0;
        cargoInQueue = 0;
        healthChecksFailed = 0;
        lastOperationTimestamp = 0;
        status = LAUNCHING_STATUS;
    }

    public SystemInstance terminateInstance(AmazonEC2 ec2){
        status = TERMINATING_STATUS;
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
        return this;
    }

    @Override
    public String toString() {
        return "SystemInstance{" +
                "instanceId='" + instanceId + '\'' +
                ", publicIPAddress='" + publicIPAddress + '\'' +
                ", requestsInQueue=" + requestsInQueue +
                ", status=" + status +
                ", healthChecksFailed=" + healthChecksFailed +
                ", cargoInQueue=" + cargoInQueue +
                ", lastOperationTimestamp=" + lastOperationTimestamp +
                '}';
    }
}
