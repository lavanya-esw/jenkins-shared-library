def call (Map configMap){
    pipeline{
        agent{
            node{
                label 'AGENT-1'
            }
        }
        environment{
            appVersion = ""
            ACC_ID = "500532068743"
            PROJECT = configMap.get("project")
            COMPONENT = configMap.get("component")
        }
        options{
            ansiColor('xterm')
        }
        stages{
            stage('Read Version'){
                steps{
                    script{
                        def fileContent = readFile(file: 'version.txt').trim()
                        appVersion = packageJSON.fileContent
                        echo "app version:${appVersion}"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    script{
                        sh """
                            npm install
                        """
                    }
                }
            }
            stage('Dependabot security scan'){
                environment {
                    GITHUB_OWNER = 'lavanya-esw'
                    GITHUB_REPO  = 'catalogue-cicd'
                    GITHUB_API   = 'https://api.github.com'
                    GITHUB_TOKEN = credentials('GITHUB-TOKEN')
                }

                steps {
                    script{
                        /* Use sh """ when you want to use Groovy variables inside the shell.
                        Use sh ''' when you want the script to be treated as pure shell. */
                        sh '''
                        echo "Fetching Dependabot alerts..."

                        response=$(curl -s \
                            -H "Authorization: token ${GITHUB_TOKEN}" \
                            -H "Accept: application/vnd.github+json" \
                            "${GITHUB_API}/repos/${GITHUB_OWNER}/${GITHUB_REPO}/dependabot/alerts?per_page=100")

                        echo "${response}" > dependabot_alerts.json

                        high_critical_open_count=$(echo "${response}" | jq '[.[] 
                            | select(
                                .state == "open"
                                and (.security_advisory.severity == "high"
                                    or .security_advisory.severity == "critical")
                            )
                        ] | length')

                        echo "Open HIGH/CRITICAL Dependabot alerts: ${high_critical_open_count}"

                        if [ "${high_critical_open_count}" -gt 0 ]; then
                            echo "❌ Blocking pipeline due to OPEN HIGH/CRITICAL Dependabot alerts"
                            echo "Affected dependencies:"
                            echo "$response" | jq '.[] 
                            | select(.state=="open" 
                            and (.security_advisory.severity=="high" 
                            or .security_advisory.severity=="critical"))
                            | {dependency: .dependency.package.name, severity: .security_advisory.severity, advisory: .security_advisory.summary}'
                            exit 1
                        else
                            echo "✅ No OPEN HIGH/CRITICAL Dependabot alerts found"
                        fi
                        ''' 
                    }
                }
            }
            stage('Build Image') {
                steps {
                    script{
                        withAWS(region:'us-east-1',credentials:'aws_access') {
                            sh """
                                aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                                docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                docker images
                                docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                            """
                        }
                    }
                }
            }
            stage('trivy scan'){
                steps{
                    script{
                        sh """
                            trivy image \
                            --scanners vuln \
                            --severity HIGH,CRITICAL,MEDIUM \
                            --pkg-types os \
                            --exit-code 1 \
                            --format table \
                            ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                        """
                    }
                }
            }
            stage('test'){
                steps{
                    script{
                        sh """
                            echo "testing..."
                        """
                    }
                }
            }
        }
        post{
            always{
                echo 'I will always say Hello again!'
                cleanWs()
            }
            success {
                echo 'I will run if success'
            }
            failure {
                echo 'I will run if failure'
            }
            aborted {
                echo 'pipeline is aborted'
            }
        }
    }
}