import { Avatar, Box, Button, Form, Frame, HorizontalStack, Page, Text, TextField, Toast, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import SSOTextfield from './SSOTextfield'
import PasswordTextField from '../../dashboard/components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'
import "../styles.css"

function SignUp() {
  const [ssoList, setSsoList] = useState([])
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loginActive, setLoginActive] = useState(true)
  const [loading, setLoading] = useState(false)

  const oktaUrl = window.OKTA_AUTH_URL
  const azureUrl = window.AZURE_REQUEST_URL
  const githubId = window.GITHUB_CLIENT_ID

  const githubAuthObj = {
    logo: '/public/github_icon.svg',
    text: 'Continue with Github SSO',
    onClickFunc: () => { window.location.href = ("https://github.com/login/oauth/authorize?client_id=" + githubId); }
  }

  const azureAuthObj = {
    logo: '/public/azure_logo.svg',
    text: 'Continue with Azure SSO',
    onClickFunc: () => { window.location.href = azureUrl }
  }

  const oktaAuthObj = {
    logo: '/public/okta_logo.svg',
    text: 'Continue with Okta SSO',
    onClickFunc: () => { window.location.href = oktaUrl }
  }

  useEffect(() => {
    let copySsoList = []
    if (githubId !== undefined && githubId.length > 0) {
      copySsoList.push(githubAuthObj)
    }

    if (oktaUrl !== undefined && oktaUrl.length > 0) {
      copySsoList.push(oktaAuthObj)
    }

    if (azureUrl !== undefined && azureUrl > 0) {
      copySsoList.push(azureAuthObj)
    }

    setSsoList(copySsoList)

    if (window.IS_SAAS && window.IS_SAAS === "true") {
      window.location.href = "/";
    }
  }, [])

  const ssoCard = (
    ssoList.length === 0 ? null :
      <VerticalStack gap={5}>
        {
          ssoList.map((sso, index) => {
            return (
              <VerticalStack gap={5} key={index}>
                <SSOTextfield onClickFunc={sso.onClickFunc} logo={sso.logo} text={sso.text} />
                <HorizontalStack gap={3}>
                  <div style={{ flexGrow: 1, borderBottom: '1px solid #c9cccf' }}></div>
                  <Text variant="bodySm" color="subdued">or</Text>
                  <div style={{ flexGrow: 1, borderBottom: '1px solid #c9cccf' }}></div>
                </HorizontalStack>
              </VerticalStack>
            )
          })
        }
      </VerticalStack>
  )

  const loginFunc = async() => {
    setLoading(true)
    if(loginActive){
      try {
        await api.login(email, password)
      } catch {
        func.setToast(true, true, "Email or password incorrect. Please login again.")
      }
    }else{
      try {
        await api.signupUser(email, password, window.SIGNUP_INVITATION_CODE)
        window.location.href= "/dashboard/onboarding"
      } catch (error) {
        func.setToast(true, true, "Signup error " + error)
      }
    }
    setLoading(false)
  }

  const loginObject = {
    headingText: "Welcome back",
    buttonText: "Sign in",
    linkText: "Sign up",
    descriptionText: "Need to create a new organization?"
  }

  const signupObject = {
    headingText: "Create new account",
    buttonText: "Sign up",
    linkText: "Sign in",
    descriptionText: "Already using Akto?"
  }

  const activeObject = loginActive ? loginObject : signupObject


  const signupEmailCard = (
    <VerticalStack gap={4}>
      <Form onSubmit={loginFunc}>
        <VerticalStack gap={4}>
          <div className='form-class'>
            <TextField onChange={setEmail} value={email} label="Email" placeholder="name@workemail.com" monospaced={true}/>
          </div>
          <div className='form-class'>
            <PasswordTextField setField={(val) => setPassword(val)} onFunc={true} field={password} label="Password" monospaced={true}/>
          </div>
          <Button fullWidth primary onClick={loginFunc} size="large" loading={loading}>{activeObject.buttonText}</Button>
        </VerticalStack>
      </Form>
      <HorizontalStack align="center" gap={1}>
        <Text>{activeObject.descriptionText}</Text>
        <Button plain onClick={() => setLoginActive(!loginActive)}>{activeObject.linkText}</Button>
      </HorizontalStack>
    </VerticalStack>
  )
  return (
    <div className='login-page'>
      <Frame >
        <Page fullWidth >
          <Box padding="10" paddingBlockStart={"24"}>
            <div style={{display: "flex", justifyContent: 'space-between', flexDirection: "column"}}>
              <HorizontalStack align="center">
                <Box width='400px'>
                  <VerticalStack gap={8}>
                    <HorizontalStack align='center'>
                      <div className="akto-logo">
                        <Avatar source="/public/akto_name_with_logo.svg" shape="round" size="2xl-experimental" />
                      </div>
                    </HorizontalStack>
                    <VerticalStack gap={8}>
                      <Text alignment="center" variant="heading2xl">{activeObject.headingText}</Text>
                      <VerticalStack gap={5}>
                        {ssoCard}
                        {signupEmailCard}
                      </VerticalStack>
                    </VerticalStack>

                  </VerticalStack>
                  <div style={{bottom: "40px", position: "absolute", width: '400px'}}>
                    <HorizontalStack gap={3} align="center">
                      <Button plain onClick={() => window.open("https://www.akto.io/terms-and-policies","_blank")}>Terms of use</Button>
                      <div style={{width: '1px', height: '24px', background: "#E1E3E5"}} />
                      <Button plain onClick={() => window.open("https://www.akto.io/terms/privacy","_blank")}>Privacy policy</Button>
                    </HorizontalStack>
                  </div>
                </Box>
              </HorizontalStack>
            </div>
          </Box>
        </Page>
      </Frame>
    </div>
  )
}

export default SignUp