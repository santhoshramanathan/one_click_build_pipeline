def step_build(){
  node('master') {
      try {
          println (">> before checkout")
          checkout scm
          println (">> after checkout")

          wrap([$class: 'Xvfb', additionalOptions: '', assignedLabels: '', autoDisplayName: true, installationName: 'Xvfb', screen: '']) {
          
            sh ''' echo "BUILD_FOLDER *********" + ${BUILD_FOLDER}
                  . ${ACE_INSTALL_DIR}/server/bin/mqsiprofile
                  pwd
                  mqsicreatebar -data . -b ${APP_NAME}.bar -a ${APP_NAME} -skipWSErrorCheck
                  cp ${APP_NAME}.bar ${BUILD_FOLDER}'''
              
          }
      }
      catch(error) {
        println ">> build failed"
        throw error
      }
    }
}

def step_run_ta() {
    node() {
      try {
        unstash 'barFileComponent'
        println (">> running TA  ${BUILD_NO} <<")
        
        sh '''
            . ${ACE_INSTALL_DIR}/server/bin/mqsiprofile
            mkdir -p /home/ucp4i/play/ta-dir/${APP_NAME}
            export TADataCollectorDirectory=/home/ucp4i/play/ta-dir/${APP_NAME}
            ${ACE_INSTALL_DIR}/server/bin/TADataCollector.sh ace run /home/ucp4i/play/one-click-builds/${APP_NAME}.bar
            '''
         
        println (">> custom image pushed to registry <<")
        
      }
      catch(error) {
        println " failure to push "
        throw error
      }
    
    }
}

def step_createDockerImage() {
    node() {
      try {
        unstash 'barFileComponent'
        println (">> creating custom image  ${BUILD_NO} <<")
        
        sh ''' 
            docker login ${DOCKER_REGISTRY_URL} --username jenkins --password eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJhY2UiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiamVua2lucy10b2tlbi1qczZ0ZCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJqZW5raW5zIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQudWlkIjoiM2QzMGJmZWEtMTBlZi0xMWVhLTgzY2UtMGFkYTUzMDM3ZWUyIiwic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50OmFjZTpqZW5raW5zIn0.i_3ulC-6fy6wmGSQCTLYqo0JJAl2VHrQldEhu3XqwjGp1AD6Lfl_EWnWrjdGWDa_UiVyFq3IshXMLuyH8l4JSALCsicTWXlUQbD_q3s1Np1jy2g2nnyRI14qW4jNDCdaEqF6rpm8WaLup0dtuSVby_x6I9IbKxsQ_E1fGlQZAVGOtkvUyjKuRMYyUtIdysSb7zZgEu8JUrGUh1FwYZYt10lHG3uJsf_21Lu7XWxv-8Zcn9grwDGKv9PJ9BEggCb2qzPl2K7z5ZTUW9iLdsw6XcuLsF8X-C8DCHeskKjx-6TLOBfiiERLAKPSdvrIbBrTjrlHWcTeSu-SJAzHZJBXCQ
            docker build -t barimage:${BUILD_NO} -f- . <<EOF
FROM ${DOCKER_REGISTRY_URL}/ace/ibm-ace-server-prod:11.0.0.7-r1-amd64
COPY *.bar /home/aceuser/bar/*
RUN ace_compile_bars.sh 
EOF
            docker tag barimage:${BUILD_NO} ${DOCKER_REGISTRY_URL}/ace/barimage:${BUILD_NO}-amd64
            docker push ${DOCKER_REGISTRY_URL}/ace/barimage:${BUILD_NO}-amd64

            docker rmi barimage:${BUILD_NO}
            '''
         
        println (">> custom image pushed to registry <<")
        
      }
      catch(error) {
        println " failure to push "
        throw error
      }
    
    }
}

def step_deployImage() {
    node() {
      try {
        
      sh '''
      oc login ${OPEN_SHIFT_URL} --token=eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJhY2UiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiamVua2lucy10b2tlbi1qczZ0ZCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJqZW5raW5zIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQudWlkIjoiM2QzMGJmZWEtMTBlZi0xMWVhLTgzY2UtMGFkYTUzMDM3ZWUyIiwic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50OmFjZTpqZW5raW5zIn0.i_3ulC-6fy6wmGSQCTLYqo0JJAl2VHrQldEhu3XqwjGp1AD6Lfl_EWnWrjdGWDa_UiVyFq3IshXMLuyH8l4JSALCsicTWXlUQbD_q3s1Np1jy2g2nnyRI14qW4jNDCdaEqF6rpm8WaLup0dtuSVby_x6I9IbKxsQ_E1fGlQZAVGOtkvUyjKuRMYyUtIdysSb7zZgEu8JUrGUh1FwYZYt10lHG3uJsf_21Lu7XWxv-8Zcn9grwDGKv9PJ9BEggCb2qzPl2K7z5ZTUW9iLdsw6XcuLsF8X-C8DCHeskKjx-6TLOBfiiERLAKPSdvrIbBrTjrlHWcTeSu-SJAzHZJBXCQ
      cd /opt/certs
      helm init --client-only
      helm repo add ibm-entitled-charts https://raw.githubusercontent.com/IBM/charts/master/repo/entitled/
      helm install --name ${RELEASE_NAME} ibm-entitled-charts/ibm-ace-server-icp4i-prod --namespace ace --set imageType=ace  --set image.aceonly=docker-registry.default.svc:5000/ace/barimage:${BUILD_NO} --set productionDeployment=false --set image.pullSecret=ibm-entitlement-key --set service.iP=icp-console.cp4i-b2e73aa4eddf9dc566faa4f42ccdd306-0001.us-east.containers.appdomain.cloud --set aceonly.replicaCount=1 --set dataPVC.storageClassName=ibmc-file-bronze --set integrationServer.name=intserverkafka --set license=accept --tls
      #oc expose svc ${RELEASE_NAME}-ibm-ace-server-icp4i-prod --port=7800 --name=${RELEASE_NAME}-http
      oc delete all --selector release=${RELEASE_NAME_TO_DEL}
      '''
        
      }
      catch(error) {
        println ">> image deployment failed <<"
        throw error
      }
    
    }
}    
   
