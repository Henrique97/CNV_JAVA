package aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;

public class LoadBalancer {

    /*
        CONSTANTS
     */

    private static final String     REGION_NAME = "us-east-1";
    private static final String     SECURITY_GROUP_NAME = "";
    private static final String     IMAGE_ID = "";
    private static final String     INSTANCE_TYPE_NAME = "t2.micro";
    private static final int        MIN_INSTANCES_COUNT = 1;
    private static final int        MAX_INSTANCES_COUNT = 5;
    private static final int        CPU_THRESHOLD = 80; //CPU usage needed to launch a new instance

    /*
        VARIABLES
     */
    private static AmazonEC2        ec2;
    private static AmazonCloudWatch cloudWatch;


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

    public static void main(String[] args) throws Exception {
        init();

    }

}
