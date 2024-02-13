import { Avatar, Box, HorizontalStack, Page, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import SSOTextfield from '../components/SSOTextfield'

function SignUp() {
  const [ssoList, setSsoList] = useState([])

  const githubAuthObj = {
    logo: '/public/github_icon.svg',
    text: 'Continue with Github SSO',
    onClickFunc: () => {console.log("github")}
  }

  const azureAuthObj = {
    logo: '/public/azure_logo.svg',
    text: 'Continue with Azure SSO',
    onClickFunc: () => {console.log("azure")}
  }

  const oktaAuthObj = {
    logo: '/public/okta_logo.svg',
    text: 'Continue with Okta SSO',
    onClickFunc: () => {console.log("okta")}
  }

  useEffect(() => {
    let copySsoList = []
    if(window.GITHUB_CLIENT_ID !== undefined){
      copySsoList.push(githubAuthObj)
    }

    if(window.OKTA_AUTH_URL !== undefined){
      copySsoList.push(oktaAuthObj)
    }

    if(window.AZURE_REQUEST_URL !== undefined){
      copySsoList.push(azureAuthObj)
    }

    setSsoList(copySsoList)

    if(window.IS_SAAS && window.IS_SAAS==="true"){
      window.location.href = "/";
    }
  },[])

  const ssoCard = (
    ssoList.length === 0 ? null :
    <VerticalStack gap={5}>
      {
        ssoList.map((sso, index) => {
          return(
              <VerticalStack gap={5} key={index}>
                <SSOTextfield onClickFunc={sso.onClickFunc} logo={sso.logo} text={sso.text} />
                
              </VerticalStack>
          )
        })
      }
    </VerticalStack>
    
  )

    return (
      <Page>
        <Box width='400px'>
          <VerticalStack gap={8}>
            <HorizontalStack align='center'>
              <Avatar source="/public/akto_name_with_logo.svg" />
            </HorizontalStack>
            <Text alignment="center" variant="headingLg">Create your account</Text>
          </VerticalStack>
        </Box>
      </Page>
    )
}

export default SignUp