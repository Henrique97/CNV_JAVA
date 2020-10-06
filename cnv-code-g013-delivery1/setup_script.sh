 #!/bin/bash
echo "export CLASSPATH=$CLASSPATH:/home/ec2-user/$aws_name/lib/$aws_name.jar:/home/ec2-user/$aws_name/third-party/lib/*:." >> sudo /etc/rc.local
echo "export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS" >> sudo /etc/rc.local
echo "su - ec2-user -c java -cp /home/ec2-user/cnv/cnv-project pt.ulisboa.tecnico.cnv.server.WebServer" >> sudo /etc/rc.local
