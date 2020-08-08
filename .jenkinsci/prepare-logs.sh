#!/usr/bin/env bash
mkdir -p build-logs

while read -r LINE 
do
  docker logs $(echo $LINE | cut -d ' ' -f1) | gzip -6 > build-logs/`echo $LINE | cut -d ' ' -f2`.log.gz
done < <(docker ps --format "{{.ID}} {{.Names}}")

tar -zcvf build-logs/notaryIrohaIntegrationTest.gz -C notary-iroha-integration-test/build/reports/tests integrationTest || true
tar -zcvf build-logs/jacoco.gz -C build/reports jacoco || true
tar -zcvf build-logs/dokka.gz -C build/reports dokka || true
