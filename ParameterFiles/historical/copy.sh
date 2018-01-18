ssh -p 2158 -i /home/psharma/.ssh/id_rsa_incurrency psharma@incurrency.com bash /home/psharma/scripts/rebuild.sh
scp -P 2158 -i /home/psharma/.ssh/id_rsa_incurrency -r psharma@incurrency.com:/home/psharma/NetBeansProjects/strategies/dist/* /home/psharma/strategies/historical/
