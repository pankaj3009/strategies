#!/bin/bash
cd /home/psharma/TurtleTradingProductionNew/
rm logs.zip
zip -r logs.zip logs/*.*
mutt -s "INR Strategy Reports Attached" -a logs.zip -- reporting@incurrency.com < logs/body.txt
#echo "Strategy Reports attached" | mail -s "Reports:INR" -a logs.zip reporting@incurrency.com < logs/body.txt
#echo "Strategy Reports attached" | mail -s "Reports:INR" -a INRADROrders.csv -a INRADRTrades.csv -a INRIDTOrders.csv -a INRIDTTrades.csv -a ADR.csv -a inridtdatalogs.csv -a logs/IDT.0.log -a logs/bars.log -a logs/application.err -a Equity.csv reporting@incurrency.com < body.txt

rm logs/ADR.csv
rm logs/body.txt
rm logs/Equity.csv
rm logs/ADROrders.csv
rm logs/inradr.csv
rm logs/inridtdatalogs.csv
