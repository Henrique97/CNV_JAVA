package aws;
/*
 * Copyright 2012-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Timestamp;
import java.util.concurrent.Future;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;

/**
 * This sample demonstrates how to perform a few simple operations with the
 * Amazon DynamoDB service.
 */
public class AmazonDynamoDBitems {

    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

    static AmazonDynamoDB dynamoDB;
    static AmazonDynamoDBAsync dynamoDB2;
    private static final String[] column_names = {"x0", "y0", "x1", "y1", "StartX", "StartY","width", "height","strat","img"};
    private static final List<String> list_column_names =new ArrayList<String>(){{ add("x0"); add("y0"); add("x1"); add("y1"); add("Startx"); add("StartY"); add("width"); add("height"); add("strat"); add("img");}};

    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.ProfilesConfigFile
     * @see com.amazonaws.ClientConfiguration
     */
    public static void init() throws Exception {
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        dynamoDB2 = AmazonDynamoDBAsyncClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
    }

    public static void main(String[] args) throws Exception {
        init();
        //example
        //String[] arguments= {"0", "0", "1024", "1024", "0", "0", "1024", "1024" , "imgPATH", "BFS", "12","773684","0","3619516","581495281","1289190","28698137", "10","0"};
        //String arguments = "0,0,1024,1024,0,0,1024,1024,BFS,imgPATH,12,773684,0,3619516,581495281,1289190,28698137,10,0";
        //writeDB(arguments);

    }

    private static Map<String, AttributeValue> newItem(String keyArgVec, /*String timestamp, */String x0, String y0, String x1, String y1, String Sx, String Sy,
                                                       String w, String h, String strat, String img, String lc, String is, String iv) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("ArgsVec", new AttributeValue(keyArgVec));
//        item.put("TimeSt", new AttributeValue(timestamp));
        item.put("x0", new AttributeValue().withN(x0));
        item.put("y0", new AttributeValue().withN(y0));
        item.put("x1", new AttributeValue().withN(x1));
        item.put("y1", new AttributeValue().withN(y1));
        item.put("StartX", new AttributeValue().withN(Sx));
        item.put("StartY", new AttributeValue().withN(Sy));
        item.put("img", new AttributeValue(img));
        item.put("strat", new AttributeValue(strat));
        item.put("width", new AttributeValue().withN(w));
        item.put("height", new AttributeValue().withN(h));
        item.put("Lc", new AttributeValue().withN(lc));
        item.put("Is", new AttributeValue().withN(is));
        item.put("Iv", new AttributeValue().withN(iv));


        return item;
    }


    private static void writeLine(List<String> rec, String tableName, String keyArgVec) throws AmazonServiceException, AmazonClientException {
        // Add an item
        Date date = new Date();
        Timestamp timestamp = new Timestamp(date.getTime());
        Map<String, AttributeValue> item = newItem(keyArgVec + "," + timestamp.toString(),
                rec.get(0) ,rec.get(1), rec.get(2), rec.get(3), rec.get(4), rec.get(5),
                rec.get(6), rec.get(7), rec.get(8), rec.get(9), rec.get(11), rec.get(12),
                rec.get(13));
        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
        System.out.println(putItemRequest);
        dynamoDB2.putItemAsync(putItemRequest,
                new AsyncHandler<PutItemRequest,PutItemResult>() {
                    public void onSuccess(PutItemRequest request ,PutItemResult result) {
                        System.out.println("Written");
                        dynamoDB2.shutdown();
                    }
                    public void onError(Exception exception) {
                        System.out.println("\n\n\n\n\nError writing table: " + exception.getMessage());
                        dynamoDB2.shutdown();
                    }
                }
        );
    }

    public static List<String> getItemsConditional(String args, int interval) {
        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<String> argList = Arrays.asList(args.split(","));
        Map<String, AttributeValue> eav = new HashMap<String, AttributeValue>();

        String filterExpression = "";
        // until index 7
        boolean addStrat = false;
        for(int i = 0; i < column_names.length; i++) {
            if ((!column_names[i].equals("strat")) && (!column_names[i].equals("img"))) {
                int value_arg = Integer.parseInt(argList.get(i));
                String left_cond = value_arg - interval + "";
                String right_cond = value_arg + interval + "";

                eav.put(":valr" + i, new AttributeValue().withN(right_cond));
                eav.put(":vall" + i, new AttributeValue().withN(left_cond));

                filterExpression += column_names[i] + " > " + ":vall" + i + " and " + column_names[i] + " < " + ":valr" + i + " and ";
            } else {
                // check if same algorithm
                filterExpression += column_names[i] + " = :vala" + i +  (!addStrat ? " and " : "");
                addStrat = true;
                eav.put(":vala" + i , new AttributeValue().withS(argList.get(i)));
            }
        }
        ScanRequest request = new ScanRequest().withTableName("statistics").withFilterExpression(filterExpression).withExpressionAttributeValues(eav);
        return getMostSimilarItems(dynamoDB2.scan(request), args);
    }

    public static void writeDB(String args) throws Exception {
        init();// this can be done before the server starts
        try {
            final String tableName = "statistics";
            final String rawArgs=args;
            final List<String>toWrite=Arrays.asList(args.split(","));
            // Create a table with a primary hash key named 'name', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                    .withKeySchema(new KeySchemaElement().withAttributeName("ArgsVec").withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition().withAttributeName("ArgsVec").withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            //TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            dynamoDB2.createTableAsync(createTableRequest,
                    new AsyncHandler<CreateTableRequest,CreateTableResult>() {
                        public void onSuccess(CreateTableRequest request ,CreateTableResult result) {
                            System.out.println("Table created: " + result);
                            try {TableUtils.waitUntilActive(dynamoDB2, tableName);}
                            catch (InterruptedException e) {System.out.println("Error Waiting for ACTIVE STATE");}
                            writeLine(toWrite, tableName, rawArgs);
                        }
                        public void onError(Exception exception) {
                            if (exception instanceof ResourceInUseException){
                                System.out.println("Table Already Exists");
                                // wait for the table to move into ACTIVE state
                                try {TableUtils.waitUntilActive(dynamoDB2, tableName);}
                                catch (InterruptedException e) {System.out.println("\n\n\n\n\nError Waiting for ACTIVE STATE");}
                                writeLine(toWrite, tableName, rawArgs);
                            } else {
                                System.out.println("Error creating table: " + exception);
                            }
                        }
                    }
            );

		/*
		    Describe our new table
            DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
            TableDescription tableDescription = dynamoDB2.describeTable(describeTableRequest).getTable();
            System.out.println("Table Description: " + tableDescription);
        */
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static List<String>  getMostSimilarItems(ScanResult scanR, String args) {
        long multiplierIs=0;
        long multiplierIv=0;
        List<String>  result = new ArrayList<>();
        List<Map<String, AttributeValue>> itemList = scanR.getItems();
        List<String> argList = Arrays.asList(args.split(","));
        Map<String, Integer> distanceList = new HashMap<>();
        int iter = 0;
        String argsVecKey = "";
        System.out.println(argList);
        for (Map<String, AttributeValue> item : itemList) {
            System.out.println("item: " + item);
            argsVecKey = item.get("ArgsVec").toString();
            distanceList.put(argsVecKey, 0);
             for (Map.Entry<String, AttributeValue> cols : item.entrySet()) {
                 if ((!cols.getKey().equals("strat")) && (!cols.getKey().equals("img") && (!cols.getKey().equals("ArgsVec")))) {
                     iter = list_column_names.indexOf(cols.getKey());
                     if (iter == -1) {
                         continue;
                     }
                    distanceList.put(argsVecKey, distanceList.get(argsVecKey).intValue() + Math.abs(Integer.parseInt(cols.getValue().getN().toString()) -  Integer.parseInt(argList.get(iter))));
                 }
             }
        }
        int minimum = Integer.MAX_VALUE;
        String minKey = "";
        for (Map.Entry<String, Integer> entry : distanceList.entrySet()) {
            if (entry.getValue() < minimum) {
                minimum = entry.getValue();
                minKey = entry.getKey();
            }
        }
        //0.5*loads + 0.3 invokes
        double estimation = -1.0;
        for (Map<String, AttributeValue> item : itemList) {
            if(item.get("ArgsVec").toString().equals(minKey)) {
                multiplierIs= 0;// item.get("Lc").getN().length()-item.get("Is").getN().length();
                multiplierIv= 0;//item.get("Lc").getN().length()-item.get("Iv").getN().length();
                estimation = Long.parseLong(item.get("Lc").getN()) * 0.05 + Long.parseLong(item.get("Iv").getN()) * 0.5 * Math.pow(10,multiplierIs) +
                        Long.parseLong(item.get("Is").getN()) * 0.45 * Math.pow(10,multiplierIv);
                break;
            }
        }
        result.add(estimation+"");
        result.add(multiplierIs+"");
        result.add(multiplierIv+"");
        return  result;
    }
}

