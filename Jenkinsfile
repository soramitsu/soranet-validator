def dockerVolumes = '-v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp'
def dockerRunArgs = '-e JVM_OPTS="-Xmx3200m" -e TERM="dumb"'
def tagPattern = /(master|develop|reserved)/

pipeline {
    agent { label 'd3-build-agent' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Tests') {
            environment {
                SORANET_DOCKER = credentials('bot-soranet-ro')
                D3_DOCKER = credentials('nexus-d3-docker')
                SONAR_TOKEN = credentials('SONAR_TOKEN')
            }
            steps {
                script {
                    env.WORKSPACE = pwd()

                    sh "docker login docker.soramitsu.co.jp -u ${SORANET_DOCKER_USR} -p '${SORANET_DOCKER_PSW}'"
                    sh "docker login nexus.iroha.tech:19002 -u ${D3_DOCKER_USR} -p '${D3_DOCKER_PSW}'"

                    docker.withRegistry('https://docker.soramitsu.co.jp/', 'bot-build-tools-ro') {

                        iC = docker.image("docker.soramitsu.co.jp/build-tools/openjdk-8:latest")
                        
                        iC.inside("${dockerRunArgs} ${dockerVolumes}") {

                            sh "docker login docker.soramitsu.co.jp -u ${SORANET_DOCKER_USR} -p '${SORANET_DOCKER_PSW}'"
                            sh "docker login nexus.iroha.tech:19002 -u ${D3_DOCKER_USR} -p '${D3_DOCKER_PSW}'"

                            sh "./gradlew dependencies"
                            sh "./gradlew test --info"
                            sh "./gradlew compileIntegrationTestKotlin --info"
                            sh "./gradlew shadowJar --info"
                            sh "./gradlew dockerfileCreate --info"
                            sh "./gradlew integrationTest --info"
                            sh "./gradlew codeCoverageReport --info"
                            sh "./gradlew dokka --info"
                            sh "./gradlew d3TestReport"

                        }

                        if (env.BRANCH_NAME == 'develop') {
                            iC.inside(dockerRunArgs) {
                                sh "./gradlew sonarqube -x test --configure-on-demand \
                                    -Dsonar.links.ci=${BUILD_URL} \
                                    -Dsonar.github.pullRequest=${env.CHANGE_ID} \
                                    -Dsonar.github.disableInlineComments=true \
                                    -Dsonar.host.url=https://sonar.soramitsu.co.jp \
                                    -Dsonar.login=${SONAR_TOKEN}"
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
                    sh ".jenkinsci/prepare-logs.sh"

                    archiveArtifacts artifacts: 'build-logs/*.gz'

                    sh "docker-compose -f deploy/docker-compose.yml -f deploy/docker-compose.ci.yml down"
                }
            }
        }

        stage('Build and push docker images') {
            environment {
                SORANET_DOCKER = credentials('bot-soranet-rw')
            }
            when {
                expression { return (env.BRANCH_NAME ==~ tagPattern || env.TAG_NAME) }
            }
            steps {
                script {
                    env.DOCKER_TAG = env.TAG_NAME ? env.TAG_NAME : env.BRANCH_NAME

                    def dockerPushConfig =  " -e DOCKER_REGISTRY_URL='https://docker.soramitsu.co.jp'" +
                                            " -e DOCKER_REGISTRY_USERNAME='${SORANET_DOCKER_USR}'" +
                                            " -e DOCKER_REGISTRY_PASSWORD='${SORANET_DOCKER_PSW}'" +
                                            " -e TAG='${DOCKER_TAG}'"

                    iC = docker.image("gradle:4.10.2-jdk8-slim")
                    iC.inside("${dockerRunArgs} ${dockerConfig}") {
                        sh "gradle shadowJar"
                        sh "gradle dockerPush"
                    }
                }
            }
        }
    }
    
    post {
        cleanup { cleanWs() }
    }
}

