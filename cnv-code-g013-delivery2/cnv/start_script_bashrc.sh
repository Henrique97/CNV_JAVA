sudo yum install java-1.7.0-openjdk-devel.x86_64
cd
wget http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip
unzip aws-java-sdk.zip
rm aws-java-sdk.zip
aws_name=$(ls | grep aws)
echo "export CLASSPATH=$CLASSPATH:/home/ec2-user/$aws_name/lib/$aws_name.jar:/home/ec2-user/$aws_name/third-party/lib/*:." >> .bashrc
echo "export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS" >> .bashrc
mkdir .aws
cd
source .bashrc
crontab -l | { cat; echo "@reboot /usr/bin/java -cp /home/ec2-user/cnv/cnv-project:/home/ec2-user/$aws_name/lib/$aws_name.jar:/home/ec2-user/$aws_name/third-party/lib/\* pt.ulisboa.tecnico.cnv.server.WebServer"; } | crontab -
crontab -l | { cat; echo "@reboot sh /home/ec2-user/cnv/set_path.sh"; } | crontab -
cd /home/$USER/cnv/cnv-project
javac aws/*.java
javac -cp . pt/ulisboa/tecnico/cnv/server/WebServer.java
java aws/StatisticsAnalyzer /home/$USER/cnv/cnv-project/pt/ulisboa/tecnico/cnv/solver/base /home/$USER/cnv/cnv-project/pt/ulisboa/tecnico/cnv/solver/