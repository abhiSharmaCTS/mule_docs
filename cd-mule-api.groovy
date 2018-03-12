def gitlab_Url = "http://hsyplsvns001.amwater.net:81/mulesoft-integrations"
def gitlab_credentialsId = "6e426155-40c6-4c99-97fe-247f84ebf361"
def gitlab_apiUrl = "${gitlab_Url}" + "/aw-mule-deployment.git"
def gitlab_branch = "master"

def artifactoryServerUrl = "http://10.1.68.239:8081/artifactory"
def artifactoryCredentialId = "mulesvc Artifactory Credential"

def anypointUserName = "aw-mule-devops"
def anypointPassword = "password goes here"

timestamps {
    node("qa"){
        cleanWs()

		println 'muleApiName=' + muleApiName
		println 'muleApiArtifactsNameLoc=' + muleApiArtifactsNameLoc
		println 'targetEnvironment=' + targetEnvironment
		println 'anypointUserName=' + anypointUserName
        
		if(muleApiName != ''
			&& muleApiArtifactsNameLoc != ''
			&& targetEnvironment != ''
			&& anypointUserName != ''
			&& anypointPassword != '')
		{
			
			stage "Download Artifacts"

			//  Download aw-mule-deployer    
			git branch: "${gitlab_branch}", 
				credentialsId: "${gitlab_credentialsId}", 
				url: "${gitlab_apiUrl}"

			def artifactoryDetails = [url: "${artifactoryServerUrl}", 
							  credentialsId: "${artifactoryCredentialId}"]

			def artifactoryServer = Artifactory.newServer(artifactoryDetails)
			
			def downloadSpec = """{
			 "files": [
			  {
				  "pattern": "libs-release-local/${muleApiArtifactsNameLoc}",
				  "target": ""
				}
			 ]
			}"""

			artifactoryServer.download(downloadSpec)
		    def isArtifactsDownloaded = fileExists muleApiArtifactsNameLoc
		
			if(isArtifactsDownloaded){
		
				stage "Deploy"
				
				def developmentServer = [ 'environment': 'Development', 'serverType': 'server', 'serverName': 'hsynlmuls003-DEV']
				def qaServer = [ 'environment': 'Test', 'serverType': 'cluster', 'serverName': 'TEST-Cluster']
				def prodServer = [ 'environment': 'Production', 'serverType': 'cluster', 'serverName': 'PROD-Cluster']
				
				def targetServerDetail = []
					
				if(targetEnvironment == "Development"){
					targetServerDetail = developmentServer
				}else if(targetEnvironment == "QA"){
					targetEnvironment = "Test"
					targetServerDetail = qaServer
				}else if(targetEnvironment == "Production"){
					targetServerDetail = prodServer
				}
				
				println "Deploy API=" + muleApiName + ", Target Server=" +targetServerDetail
				println "Zip File Location= " + muleApiArtifactsNameLoc

				if(targetServerDetail.environment == targetEnvironment){
						
					println "Deployment Started"
					
					def mavenParam = '-DanypointUserName=' +anypointUserName+ 
						' -DanypointPassword=' +anypointPassword+ 
						' -DtargetEnvironment=' +targetServerDetail.environment+ 
						' -DtargetServerType=' +targetServerDetail.serverType+ 
						' -DtargetServerName=' +targetServerDetail.serverName+ 
						' -DapiArtifactName=' +muleApiName+ 
						' -DapiArtifactFileLocation=' +muleApiArtifactsNameLoc
					
					sh 'mvn clean deploy ' + mavenParam
				}else{
					error 'Artifacts cannot deploy at this moment. Please check the target environment details.'
				}
			}else{
				error 'Unable to find/download artifacts from Artifactory. Please check artifacts details.'			
			}
		}else{
			error 'Artifacts cannot deploy at this moment. Please check the required parameters to deploy the artifacts.'
		}
    }
}
