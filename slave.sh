#! /bin/sh
java -Xms1024M -classpath "dependencies/*;build/*" -Dlog4j.configurationFile=config/log4j2-slave.xml org.drftpd.SlaveBoot