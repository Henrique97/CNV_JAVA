 #!/bin/bash
cd
aws_name=$(ls | grep aws)
echo "export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS" >> .bashrc
echo "export CLASSPATH=$CLASSPATH:/home/ec2-user/$aws_name/lib/$aws_name.jar:/home/ec2-user/$aws_name/third-party/lib/*:." >> .bashrc