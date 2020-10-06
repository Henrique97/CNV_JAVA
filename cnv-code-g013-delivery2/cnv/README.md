# cnv
Computação em Nuvem e Virtualização

Load Balancer:
Ping protocol: HTTP
Ping port: 8000
Ping Path: /healthcheck

Response timeout: 5
Health Check Interval: 30
Unhealthy Threshold: 2
Healthy Threshold: 10

Auto Scaling Group:
Start with: 1 instances
Receive traffic from one or mode load balancers (checked)
Health Check Type: ELB
Health Check Grace Period: 300 seconds
Enable CloudWatch detailed monitoring (checked)

Scale between 1 and 4 instances
-> Increase group Size
Whenever estimated cargo is >= MAX_CARGO_IN_QUEUE for every running instance
Add 1 instance

-> Decrease group Size
Whenever an instance does not process requests for 2 minutes.
Remove 1 instance

Note: Security groups configured as in the labs

#Usage

Go to cnv-project/aws/RequestsAndInstancesManager.java and change: region, security group, image id, instance type and key name.
To setup the environment run: source start_script_bashrc.sh (this script assumes that the cnv folder is in /home/$USER).
Run java aws/RequestsAndInstancesManager (inside cnv-project folder).
Make requests to port 8000.