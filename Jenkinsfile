
pipeline {
  environment {
    DOCKER_NETWORK = ''
  }
  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timestamps()
    disableConcurrentBuilds()
  }
  agent {
    label 'd3-build-agent'
  }
  stages {
    stage('Tests') {
      steps {
        script {
          tmp = docker.image("openjdk:8-jdk")
          env.WORKSPACE = pwd()

          DOCKER_NETWORK = "${env.CHANGE_ID}-${env.GIT_COMMIT}-${BUILD_NUMBER}"
          writeFile file: ".env", text: "SUBNET=${DOCKER_NETWORK}"
          withCredentials([usernamePassword(credentialsId: 'bot-soranet-ro', usernameVariable: 'login', passwordVariable: 'password')]) {
            sh "docker login docker.soramitsu.co.jp -u ${login} -p '${password}'"
          }
          withCredentials([usernamePassword(credentialsId: 'bot-soramitsu-ro', usernameVariable: 'login', passwordVariable: 'password')]) {
            sh "docker login docker.soramitsu.co.jp -u ${login} -p '${password}'"
          }
          withCredentials([usernamePassword(credentialsId: 'nexus-d3-docker', usernameVariable: 'login', passwordVariable: 'password')]) {
            sh "docker login nexus.iroha.tech:19002 -u ${login} -p '${password}'"
          }
          sh "docker-compose -f deploy/docker-compose.yml -f deploy/docker-compose.ci.yml pull"
          sh(returnStdout: true, script: "docker-compose -f deploy/docker-compose.yml -f deploy/docker-compose.ci.yml up --build -d")

          iC = docker.image("openjdk:8-jdk")
          iC.inside("--network='d3-${DOCKER_NETWORK}' -e JVM_OPTS='-Xmx3200m' -e TERM='dumb' -v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp") {
            sh "./gradlew dependencies"
            sh "./gradlew test --info"
            sh "./gradlew compileIntegrationTestKotlin --info"
            sh "./gradlew shadowJar --info"
            sh "./gradlew dockerfileCreate --info"
            sh "./gradlew integrationTest --info"
            sh "./gradlew codeCoverageReport --info"
            sh "./gradlew dokka --info"
            sh "./gradlew d3TestReport"
            // sh "./gradlew pitest --info"
          }
          if (env.BRANCH_NAME == 'develop') {
            iC.inside("--network='d3-${DOCKER_NETWORK}' -e JVM_OPTS='-Xmx3200m' -e TERM='dumb'") {
              withCredentials([string(credentialsId: 'SONAR_TOKEN', variable: 'SONAR_TOKEN')]){
                sh(script: "./gradlew sonarqube -x test --configure-on-demand \
                  -Dsonar.links.ci=${BUILD_URL} \
                  -Dsonar.github.pullRequest=${env.CHANGE_ID} \
                  -Dsonar.github.disableInlineComments=true \
                  -Dsonar.host.url=https://sonar.soramitsu.co.jp \
                  -Dsonar.login=${SONAR_TOKEN} \
                  ")
                }
            }
          }
          publishHTML (target: [
              allowMissing: false,
              alwaysLinkToLastBuild: false,
              keepAll: true,
              reportDir: 'build/reports',
              reportFiles: 'd3-test-report.html',
              reportName: "D3 test report"
            ])
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'build/test-results/**/*.xml'
          jacoco execPattern: 'build/jacoco/test.exec', sourcePattern: '.'
        }
        cleanup {
          sh "mkdir -p build-logs"
          sh """#!/bin/bash
            while read -r LINE; do \
              docker logs \$(echo \$LINE | cut -d ' ' -f1) | gzip -6 > build-logs/\$(echo \$LINE | cut -d ' ' -f2).log.gz; \
            done < <(docker ps --filter "network=d3-${DOCKER_NETWORK}" --format "{{.ID}} {{.Names}}")
          """
          
          sh "tar -zcvf build-logs/notaryIrohaIntegrationTest.gz -C notary-iroha-integration-test/build/reports/tests integrationTest || true"
          sh "tar -zcvf build-logs/jacoco.gz -C build/reports jacoco || true"
          sh "tar -zcvf build-logs/dokka.gz -C build/reports dokka || true"
          archiveArtifacts artifacts: 'build-logs/*.gz'
          sh "docker-compose -f deploy/docker-compose.yml -f deploy/docker-compose.ci.yml down"
          cleanWs()
        }
      }
    }

    stage('Build and push docker images') {
      steps {
        script {
          if (env.BRANCH_NAME ==~ /(master|develop|reserved)/ || env.TAG_NAME) {
                withCredentials([usernamePassword(credentialsId: 'bot-soranet-rw', usernameVariable: 'login', passwordVariable: 'password')]) {
                  TAG = env.TAG_NAME ? env.TAG_NAME : env.BRANCH_NAME
                  iC = docker.image("gradle:4.10.2-jdk8-slim")
                  iC.inside(" -e JVM_OPTS='-Xmx3200m' -e TERM='dumb'"+
                  " -v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp"+
                  " -e DOCKER_REGISTRY_URL='https://docker.soramitsu.co.jp'"+
                  " -e DOCKER_REGISTRY_USERNAME='${login}'"+
                  " -e DOCKER_REGISTRY_PASSWORD='${password}'"+
                  " -e TAG='${TAG}'") {
                    sh "gradle shadowJar"
                    sh "gradle dockerPush"
                  }
                 }
              }
        }
      }
    }
  }
}

