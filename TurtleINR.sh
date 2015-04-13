#!/bin/bash
cd /home/psharma/TurtleTradingProductionNew/
java -jar inStrat.jar datasource=127.0.0.1 topic=INR symbolfile=symbols-inr.csv connectionfile=connection-inr.csv adr=inradr.properties adrpublisher=inradrpublisher.properties