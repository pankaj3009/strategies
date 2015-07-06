cd /home/psharma/strategies/historical
if mkdir /var/lock/mylock; then
 echo "locking succeeded" >&2
java -jar strategies.jar propertyfile=01-globalproperties.txt historical=01-historical.txt
fi
rmdir /var/lock/mylock;
echo "Lock deleted" >&2
