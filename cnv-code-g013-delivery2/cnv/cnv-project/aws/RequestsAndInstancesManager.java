package aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static aws.AutoScaling.autoScalerLock;
import static aws.AutoScaling.runningInstances;

public class RequestsAndInstancesManager {

    /*
        CONSTANTS
     */
    public static final String     REGION_NAME = "us-east-1";
    public static final String     SECURITY_GROUP_NAME = "CNV-ssh+http";
    public static final String     IMAGE_ID = "ami-02cf25847d70ee888"; //ami-0f47811701333fa65 T: ami-03a20a13611e97503
    public static final String     INSTANCE_TYPE_NAME = "t2.micro";
    public static final String     KEY_NAME = "CNV-test-proj"; // CNV-test-proj T: CNV-labs-AWS

    public static final int        HEALTH_CHECK_TIMEOUT = 5000;
    public static final int        HEALTH_CHECK_INTERVAL = 20000;
    public static final int        MAX_FAILED_HEALTH_CHECKS = 5;    //Consecutive health check failures
    public static final long MAX_TIMESTAMP_INACTIVE_TIME = 120000; //2 minutes

    public static final int        INSTANCES_TO_LAUNCH = 1;
    public static final int        MIN_INSTANCES_COUNT = 1;
    public static final int        MAX_INSTANCES_COUNT = 4;
    public static final int        MAX_REQUESTS_IN_QUEUE = 5;    //Consecutive health check failures
    public static final double     MAX_CARGO_IN_QUEUE = 10.0e8;    //Consecutive health check failures

    /*
        VARIABLES
     */
    public static AmazonEC2                            ec2;
    public static AmazonCloudWatch                     cloudWatch;
    public static final ConcurrentMap<String, Integer> unhealthyInstances = new ConcurrentHashMap();              // All instances that have failed a health check
    public static final ConcurrentMap<Integer, String> requestsList = new ConcurrentHashMap<>();                  // <Job Id, Instance Id>
    private static final AtomicInteger                 requestIdentifier = new AtomicInteger(0);


    private static void init() throws Exception {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = AmazonEC2ClientBuilder.standard().withRegion(REGION_NAME).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(REGION_NAME).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

    }



    public static void HealthCheck(Executor serverExecutor) {
        while (true) {
            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("\t\t\tDoing Health Check");
            for (SystemInstance instanceElement : runningInstances.values()) {
                if(instanceElement.status != SystemInstance.RUNNING_STATUS &&
                instanceElement.status != SystemInstance.UNSTABLE_STATUS) continue;

                System.out.println("\t\t\tChecking instance id " + instanceElement.instanceId +
                        " with IP " + instanceElement.publicIPAddress);
                // final used to be able to pass to inner class
                final SystemInstance instance = instanceElement;
                serverExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String rerouteUrl = "http://" + instance.publicIPAddress+":8000/healthcheck";
                            System.out.println("\t\t\tHealth check redirected to " + rerouteUrl);
                            URL url = new URL(rerouteUrl);
                            HttpURLConnection con = (HttpURLConnection) url.openConnection();
                            con.setRequestMethod("GET");
                            con.setConnectTimeout(60000);
                            con.setReadTimeout(20000);

                            int status = con.getResponseCode();

                            if (status == 200 && unhealthyInstances.containsKey(instance.instanceId)) {
                                AutoScaling.clearHealthChecks(instance.instanceId);
                                unhealthyInstances.remove(instance.instanceId, 1);
                                if(runningInstances.size() > 1 && System.currentTimeMillis() - instance.lastOperationTimestamp > MAX_TIMESTAMP_INACTIVE_TIME){
                                    instance.terminateInstance(ec2);
                                    runningInstances.remove(instance.instanceId, instance); //Remove because of inactivity
                                }
                                return;
                            }
                            if(runningInstances.size() > 1 && status == 200 && System.currentTimeMillis() - instance.lastOperationTimestamp > MAX_TIMESTAMP_INACTIVE_TIME){
                                instance.terminateInstance(ec2);
                                runningInstances.remove(instance.instanceId, instance); //Remove because of inactivity
                            }

                            if (status != 200){
                                if (!unhealthyInstances.containsKey(instance.instanceId))
                                    unhealthyInstances.put(instance.instanceId, 1);
                                if(AutoScaling.reportFailedHealthCheck(instance.instanceId))
                                    unhealthyInstances.remove(instance.instanceId, 1);
                            }
                        } catch (IOException e) {
                            System.out.printf("\t\t\tHost unreachable: %s\n", instance.publicIPAddress);

                            if (!unhealthyInstances.containsKey(instance.instanceId))
                                unhealthyInstances.put(instance.instanceId, 1);
                            if(AutoScaling.reportFailedHealthCheck(instance.instanceId))
                                unhealthyInstances.remove(instance.instanceId, 1);
                        }
                    }
                });
            }

            System.out.println(unhealthyInstances.toString());
        }
    }


    public static void main(String[] args) throws Exception {
        init();

        final HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8000), 0);

        server.createContext("/climb", new LoadBalancer());
        server.createContext("/terminateJob", new TerminateHandler());
        synchronized (autoScalerLock){
            AutoScaling.launchNewInstance(-1, -1);
        }

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        server.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                HealthCheck(server.getExecutor());
            }
        });

        System.out.println(server.getAddress().toString());
    }

    static class TerminateHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            System.out.println("warning incoming");
            final String query = t.getRequestURI().getQuery();

            System.out.println("> Query:\t" + query);

            final String[] splitParam = query.split("=");
            // Break it down into String[].

            try {
                terminateJob(Integer.parseInt(splitParam[1]));
                t.sendResponseHeaders(200,0);
            }
            catch(Exception e){
                System.out.println("error obtaining id or on terminate job: " + e);
                t.sendResponseHeaders(500,0);
            }
        }
    }

    static class LoadBalancer implements HttpHandler {

        public static void appendWorkToInstance(SystemInstance systemInstance, double estimatedCargo, int jobId){
            systemInstance.cargoInQueue += estimatedCargo;
            systemInstance.requestsInQueue++;
            systemInstance.jobsInQueue.putIfAbsent(jobId, estimatedCargo);
            long currTimestamp = System.currentTimeMillis();
            if(systemInstance.lastOperationTimestamp < currTimestamp) systemInstance.lastOperationTimestamp = System.currentTimeMillis();
        }

        public static void removeWorkFromInstance(SystemInstance systemInstance, double estimatedCargo, int jobId){
            systemInstance.cargoInQueue -= estimatedCargo;
            systemInstance.requestsInQueue--;
            systemInstance.jobsInQueue.remove(jobId, estimatedCargo);
        }

        /**
         * Verifies within a lock, if a certain instance should or not received a request, appending the work if the needed conditions are met
         *
         * @param systemInstance the instance that shall be evaluated
         * @param estimatedCargo the estimated cargo that the request will introduce on that instance
         * @return true if the instance tested received the work, false otherwise
         */
        public static boolean verifyInstanceAvailabilityAndAppendWork(SystemInstance systemInstance, double estimatedCargo, int jobId){
            System.out.println(">>>>>>>Trying to append work to instance "+ systemInstance.instanceId);
            synchronized (systemInstance.instanceLock){
                if (    systemInstance.status == SystemInstance.RUNNING_STATUS &&
                        systemInstance.requestsInQueue + 1 < MAX_REQUESTS_IN_QUEUE &&
                        systemInstance.cargoInQueue + estimatedCargo < MAX_CARGO_IN_QUEUE
                ) {
                    System.out.println("\t Appended work to " + systemInstance.instanceId);
                    appendWorkToInstance(systemInstance, estimatedCargo, jobId);
                    return true;
                }
                return false;
            }
        }

        public static SystemInstance getAvailableInstance(double estimatedCargo){
            SystemInstance selectedInstance = new SystemInstance();
            for(SystemInstance instance : runningInstances.values()){
                if(     instance.status == SystemInstance.RUNNING_STATUS &&
                        instance.cargoInQueue + estimatedCargo < MAX_CARGO_IN_QUEUE &&
                        instance.requestsInQueue + 1 < MAX_REQUESTS_IN_QUEUE &&
                        instance.cargoInQueue < selectedInstance.cargoInQueue
                ){
                    selectedInstance = instance;
                }
            }
            System.out.println(">>>> Selected available instance: "+ selectedInstance);
            return selectedInstance;
        }

        @Override
        public void handle(final HttpExchange t) throws IOException {
            try{
                System.out.println("\tA request entered the handler \n\n");
            /*
                With this implementation we do not ensure FIFO order on the requests since a request X can come at T instant
                and don't have any available instance and another request Y come at T+1 instant and already have one (or more) available
            */

//            Interpret request and extract the parameters
                int requestId = requestIdentifier.getAndIncrement();

                // Get the query.
                final String query = t.getRequestURI().getQuery();

                System.out.println("> Query:\t" + query);

                // Break it down into String[].
                final String[] params = query.split("&");

                // Store as if it was a direct call to SolverMain.
                final ArrayList<String> newArgs = new ArrayList<>();
                for (final String p : params) {
                    final String[] splitParam = p.split("=");
                    newArgs.add("-" + splitParam[0]);
                    newArgs.add(splitParam[1]);
                }

                newArgs.add("-d");

                // Store from ArrayList into regular String[].
                final String[] args = new String[newArgs.size()];
                int i = 0;
                for(String arg: newArgs) {
                    args[i] = arg;
                    i++;
                }

                // Get user-provided flags.
                final SolverArgumentParser ap = new SolverArgumentParser(args);

                String stringArgs="";
                try {
                    stringArgs = String.format("%d,%d,%d,%d,%d,%d,%d,%d",
                            ap.getX0(), ap.getY0(), ap.getX1(), ap.getY1(), ap.getStartX(),
                            ap.getStartY(),ap.getWidth(), ap.getHeight());
                    stringArgs = stringArgs + "," + ap.getSolverStrategy() + ","+ ap.getInputImage();
                } catch(Exception e) {
                    System.out.println("Error getting parameters"+ e);
                    t.sendResponseHeaders(500, 0);
                    return;
                }

                System.out.println(stringArgs);
                //Extract a similar request from Dynamo DB

                List<String> cargoResults= AmazonDynamoDBitems.getItemsConditional(stringArgs,50);

                double estimatedCargo = Double.parseDouble(cargoResults.get(0));
                String multiplierIs = cargoResults.get(1);
                String multiplierIv = cargoResults.get(2);

//            If an instance fails (e.g.: is terminated at the middle of a job, give the request to another instance
                boolean requestCompleted = false;
                while(!requestCompleted){
                    SystemInstance instanceAttendingRequest = null;
                    boolean attended = false;

                    while(!attended) {
                        instanceAttendingRequest = getAvailableInstance(estimatedCargo);

                        if (instanceAttendingRequest.status == SystemInstance.DUMMY_STATUS){
                            instanceAttendingRequest = AutoScaling.requestNewInstance(estimatedCargo, requestId);
                            attended = instanceAttendingRequest.status == SystemInstance.RUNNING_STATUS;
                        }
                        else attended = verifyInstanceAvailabilityAndAppendWork(instanceAttendingRequest, estimatedCargo, requestId);
                    }
                    requestsList.putIfAbsent(requestId, instanceAttendingRequest.instanceId);

                    System.out.println("--->Request " + requestId + " associated to instance " + instanceAttendingRequest.instanceId);
//                Do the request to the selected instance by using the IP address field
                    try{
                        String rerouteUrl = "http://" + instanceAttendingRequest.publicIPAddress+":8000"+"/climb?"+t.getRequestURI().getQuery() + "&cargo=" + estimatedCargo + "&jobid=" +
                                requestId + "&multiplierIs=" + multiplierIs + "&multiplierIv="+ multiplierIv;
                        System.out.println("Request" + requestId + " redirected to " + rerouteUrl);
                        URL url = new URL(rerouteUrl);
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("GET");
                        con.setConnectTimeout(60000);

                        int status = con.getResponseCode();
                        if(status != 200){
                            System.out.println("WebServer responded with " + status);
                        }else{
                            t.sendResponseHeaders(status, 0);
                            OutputStream out = t.getResponseBody();
                            InputStream in = con.getInputStream();
                            
                            byte[] buffer = new byte[1024];
                            while (true) {
                                int bytesRead = in.read(buffer);
                                if (bytesRead == -1)
                                    break;
                                out.write(buffer, 0, bytesRead);
                            }
                            out.close();
                            System.out.println("Job terminated");
                            requestCompleted = true;
                            //Remove from the instance data the current request
                            terminateJob(requestId);
                        }
                    }catch (Exception exception){ // SocketTimeoutException
                        //Instance did not completed the request, give it to another one
                        synchronized (instanceAttendingRequest.instanceLock){
                            instanceAttendingRequest.status = SystemInstance.UNSTABLE_STATUS;
                            removeWorkFromInstance(instanceAttendingRequest, estimatedCargo, requestId);
                        }
                        System.out.println("unstable instance with id:" +  requestId + ": " +exception);
                    }
                }


                //Send received answer to the client
                final Headers hdrs = t.getResponseHeaders();

                hdrs.add("Content-Type", "image/png");

                hdrs.add("Access-Control-Allow-Origin", "*");
                hdrs.add("Access-Control-Allow-Credentials", "true");
                hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
                hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

                System.out.println("> Sent response to " + t.getRemoteAddress().toString());
            }catch (Exception e){
                e.printStackTrace();
                t.sendResponseHeaders(500,0);
            }
        }
    }

    public static void terminateJob(int jobId){
        System.out.println("Terminating job " + jobId);
        String instanceId = requestsList.get(jobId);
        if(instanceId  == null)
            return;
        requestsList.remove(jobId, instanceId);
        SystemInstance systemInstance = runningInstances.get(instanceId);
        if(systemInstance== null)
            throw new IllegalArgumentException("Fail at RequestsAndInstancesManager.terminateJob(): Instance identifier not valid");
        Double jobCargo = systemInstance.jobsInQueue.get(jobId);
        if(jobCargo == null)
            throw new IllegalArgumentException("Fail at RequestsAndInstancesManager.terminateJob(): Job identifier not valid");
        systemInstance.jobsInQueue.remove(jobId, jobCargo.longValue());
        synchronized (systemInstance.instanceLock){
            systemInstance.cargoInQueue -= jobCargo.longValue();
            systemInstance.requestsInQueue--;
            long currTimestamp = System.currentTimeMillis();
            if(systemInstance.lastOperationTimestamp < currTimestamp) systemInstance.lastOperationTimestamp = System.currentTimeMillis();
        }
    }

}