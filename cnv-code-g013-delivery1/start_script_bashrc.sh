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