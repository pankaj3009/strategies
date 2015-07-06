cd /home/psharma/strategies/historical
if mkdir /var/lock/mylock; then
#lock created. run job
java -jar strategies.jar propertyfile=02-globalproperties.txt historical=02-historical.txt
else
  echo "entered else loop" >&2 
  while ! mkdir /var/lock/mylock;
  do
  echo "Lock failed" >&2
  sleep 600
  done
  echo "exited while loop" >&2
  java -jar strategies.jar propertyfile=02-globalproperties.txt historical=02-historical.txt 
fi
rmdir /var/lock/mylock;

