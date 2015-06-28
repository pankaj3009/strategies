#!/bin/bash
cd /home/psharma/TurtleTradingProductionNew/
rm logs.zip
zip -r logs.zip logs/*.*
mutt -s "USD Strategy Reports Attached" -a logs.zip -- reporting@incurrency.com < logs/body.txt
#echo "Strategy Reports attached" | mail -s "Reports:USD" -a USDADROrders.csv -a USDADRTrades.csv -a USDIDTOrders.csv -a USDIDTTrades.csv -a ADR.csv -a logs/IDT.0.log -a logs/bars.log -a logs/application.err -a Equity.csv reporting@incurrency.com < body.txt
rm logs/ADR.csv
rm logs/body.txt
rm logs/Equity.csv
rm logs/ADROrders.csv
rm logs/usdadr.csv
rm logs/usdidtdatalogs.csv