cd /home/psharma/strategies/historical
if mkdir /var/lock/mylock; then
 echo "locking succeeded" >&2
#java -jar strategies.jar propertyfile=01-globalproperties.txt historical=01-historical.txt
java -jar strategies.jar propertyfile=02-globalproperties.ini historical=historicalstocks.ini
fi
rmdir /var/lock/mylock;
echo "Lock deleted" >&2
