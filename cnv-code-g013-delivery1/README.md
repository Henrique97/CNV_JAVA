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
Whenever Average of CPU Utilization is >= 80% for at least 1 consecutive period of 5 minutes
Add 1 instance

-> Decrease group Size
Whenever Average of CPU Utilization is < 20% for at least 1 consecutive period of 5 minutes
Remove 1 instance

Note: Security groups configured as in the labs