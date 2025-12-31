def call(Map configMap) {
    pipeline{
        agent{
            node{
                label 'AGENT-1'
            }
        }
        environment{
            appVersion = configMap.get("appVersion")
            ACC_ID = "500532068743"
            PROJECT = configMap.get("project")
            COMPONENT = configMap.get("component")
            deploy_to = configMap.get("deploy_to")
            REGION = "us-east-1"
        }
        stages{
            stage('Deploy'){
                steps{
                    script{
                        withAWS(region:'us-east-1',credentials:'aws_access') {
                            sh """
                                set -e
                                aws eks update-kubeconfig --region ${REGION} --name ${PROJECT}-${deploy_to}
                                kubectl get nodes
                                sed -i "s/IMAGE_VERSION/${appVersion}/g" values.yaml
                                helm upgrade --install ${COMPONENT} -f values-${deploy_to}.yaml -n ${PROJECT} --atomic --wait --timeout=5m .
                        }    """
                    }
                }
            }
        }
    }
}
