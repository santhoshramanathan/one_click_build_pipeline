def step_barbuild(){
  node('master') {
      try {
          println (">> before checkout")
          checkout scm
          println (">> after checkout")

          wrap([$class: 'Xvfb', additionalOptions: '', assignedLabels: '', autoDisplayName: true, installationName: 'Xvfb', screen: '']) {
          
            sh ''' echo "BUILD_FOLDER *********" + ${BUILD_FOLDER}
                  . ${ACE_INSTALL_DIR}/server/bin/mqsiprofile
                  pwd
                  dir
                  mqsicreatebar -data . -b ${APP_NAME}.bar -a ${APP_NAME} -skipWSErrorCheck
'''
        
          }
      }
      catch(error) {
        println ">> build failed"
        throw error
      }
    }
}



def step_oc_login_imagecreate(){
node(){
try {
sh ''' echo "build number " + ${BUILD_NO}
			oc login ${OPEN_SHIFT_URL} --token=eyJhbGciOiJSUzI1NiIsImtpZCI6Ill2T1N3V3JQTjI2YkE2WWVzajlxTDZXVW40QUdCSDgtMUxmV21wSW1tY3cifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJhY2UiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiZGV2b3BzYWNjLXRva2VuLWpnZHRnIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImRldm9wc2FjYyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjMwZDgxMjU2LWVjYzItNDQ3Yi1hZTQ0LTAxZDAxYWM4YjVhYiIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDphY2U6ZGV2b3BzYWNjIn0.QlULKiWdtLk0r3vBiqyYb_iBPzY89mNcz0gHJ-adLj11QPvJjw_LzAwbX45JlKJCqB4mEo7XYaWYijbI2ldndAl6_97Occ1b0FE_ckmSt4RUevTuCw24x78yUp22RJTpf_FWrQOQc4Ly3DOnGpAhnBV1VuJcOYnKh_0WuMs4osKzzt6AG0R6o3DQOzaHOb7aZpzDv1vwjQBOOdv2gbVGUp9ntGY_8VHf6XVpZ2-HSt4qcQTMWLKL2ylcuWHtS-_pwmnOOz_lMSMSpWwqP6U2LQZLYw66d4_Xw3xXkCVwiQPVAW0ThwZV2uk7_1HgrVQl8FmIS7AsJANsT4ORSx1VxA
			oc project ace
			echo "create a base image stream"
oc apply -f - <<EOF
apiVersion: image.openshift.io/v1
kind: ImageStream
metadata: 
  name: ace-server-prod-10-base
  namespace: ace
EOF
echo "Tagging v10 base image with image stream"
oc tag -n ace cp.icr.io/cp/appc/ace-server-prod@sha256:dd3c1e8d204b37775b792fc25a0bad4daba4fa35cd5aad996b29b1db63959baf ace-server-prod-10-new:latest-amd64
echo "Create a custom image stream"
oc apply -f - <<EOF
apiVersion: image.openshift.io/v1
kind: ImageStream
metadata: 
  name: my-custom-ace-image-${BUILD_NO}
  namespace: ace
EOF
'''
}
catch(error) {
		println ">> build failed"
        throw error
}
}
}

def step_create_buildConfig(){
node(){
try {
println ("create a build config for deployment")
sh ''' 
oc apply -f - <<EOF
apiVersion: build.openshift.io/v1
kind: BuildConfig
metadata: 
  labels: 
    name: my-custom-ace-image-${BUILD_NO}
  name: my-custom-ace-image-${BUILD_NO}
  namespace: ace
spec: 
  output: 
    to: 
      kind: ImageStreamTag
      name: "my-custom-ace-image-${BUILD_NO}:latest-amd64"
      namespace: ace
  source: 
    dockerfile: |
        FROM ace-server-prod-10-base:latest-amd64
        COPY ${APP_NAME}.bar /home/aceuser/initial-config/bars/${APP_NAME}.bar
    type: dockerfile
  strategy: 
    dockerStrategy: 
      from: 
        kind: ImageStreamTag
        name: "ace-server-prod-10-new:latest-amd64"
        namespace: ace
    type: Docker
  triggers: []
EOF
oc set build-secret --pull bc/my-custom-ace-image-${BUILD_NO} ibm-entitlement-key
'''
}
catch(error) {
		println ">> build failed"
        throw error
}
}
}

def step_startbuild(){
node(){
try {
println ("Start the build using build config")
sh ''' 
oc start-build my-custom-ace-image-${BUILD_NO} --from-file ./${APP_NAME}.bar
'''
}
catch(error) {
		println ">> build failed"
        throw error
}
}
}

def twistlock_scan(){
node(){
try{
   twistlockScan ca: '',
                 cert: '',
				 compliancePolicy: 'critical',
				 dockerAddress: 'unix://var/run/docker.sock',
				 gracePeriodDays: 0,
				 ignoreImageBuildTime: true,
				 image: 'default-route-openshift-image-registry.cloud-integration-ocp45-6fb0b86391cd68c8282858623a1dddff-0000.eu-gb.containers.appdomain.cloud/ace/my-custom-ace-image-96:latest-amd64',
				 key: '',
				 logLevel: 'true',
				 policy: 'warn',
				 requirePackageUpdate: false,
				 timeout:10
}
catch(error) {
		println ">> image scan failed"
        throw error
}


}
}

def twistlock_publish(){
node(){
try{
   twistlockScan ca: '',
                 cert: '',
				 dockerAddress: 'unix://var/run/docker.sock',
				 ignoreImageBuildTime: true,
				 image: 'default-route-openshift-image-registry.cloud-integration-ocp45-6fb0b86391cd68c8282858623a1dddff-0000.eu-gb.containers.appdomain.cloud/ace/my-custom-ace-image-96:latest-amd64',
				 key: '',
				 logLevel: 'true',
				 timeout:10
}
catch(error) {
		println ">> image scan publish failed"
        throw error
}


}
}
def step_deploy(){
node(){
try {
println ("Deploy the integration server")
sh ''' 
oc apply -f - <<EOF 
apiVersion: appconnect.ibm.com/v1beta1
kind: IntegrationServer
metadata: 
  name: "${RELEASE_NAME}"
spec: 
  barURL: ""
  designerFlowsOperationMode: disabled
  license: 
    accept: true
    license: L-APEH-BPUCJK
    use: CloudPakForIntegrationNonProduction
  pod: 
    containers: 
      runtime: 
        image: "image-registry.openshift-image-registry.svc:5000/ace/my-custom-ace-image-${BUILD_NO}:latest-amd64"
  replicas: 1
  service: 
    endpointType: http
  useCommonServices: true
  version: 11.0.0.10-r1
EOF

'''
println ("Started Integration server creation. Please check the pod description for further info")
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
   
