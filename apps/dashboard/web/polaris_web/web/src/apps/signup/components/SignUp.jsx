import { Button, Form,  HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import SSOTextfield from './SSOTextfield'
import PasswordTextField from '../../dashboard/components/layouts/PasswordTextField'
import { useLocation, useNavigate } from "react-router-dom"
import api from '../api'
import func from '@/util/func'
import "../styles.css"
import PersistStore from '../../main/PersistStore'
import { usePolling } from '../../main/PollingProvider'
import SignUpPageLayout from './SignUpPageLayout'

function SignUp() {

  const location = useLocation()

  const [ssoList, setSsoList] = useState([])
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loginActive, setLoginActive] = useState(location.pathname.includes("login"))
  const [loading, setLoading] = useState(false)

  const oktaUrl = window.OKTA_AUTH_URL
  const githubId = window.GITHUB_CLIENT_ID
  const githubUrl = window.GITHUB_URL ? window.GITHUB_URL : "https://github.com"
  const resetAll = PersistStore(state => state.resetAll)
  const { clearPollingInterval } = usePolling();

  const githubAuthObj = {
    logo: '/public/github_icon.svg',
    text: 'Continue with Github SSO',
    onClickFunc: () => { window.location.href = (githubUrl + "/login/oauth/authorize?client_id=" + githubId); }
  }

  const oktaAuthObj = {
    logo: '/public/okta_logo.svg',
    text: 'Continue with Okta SSO',
    onClickFunc: () => { window.location.href = oktaUrl }
  }

  useEffect(() => {
    resetAll()
    clearPollingInterval()
    let copySsoList = []
    if (githubId !== undefined && githubId.length > 0) {
      copySsoList.push(githubAuthObj)
    }

    if (oktaUrl !== undefined && oktaUrl.length > 0) {
      copySsoList.push(oktaAuthObj)
    }

    setSsoList(copySsoList)

    if (window.IS_SAAS && window.IS_SAAS === "true") {
      navigate('/dashboard/observe/inventory');
    }
  }, [])

  const ssoCard = (
    ssoList.length === 0 ? null :
      <VerticalStack gap={5}>
        {
          ssoList.map((sso, index) => {
            return (
              <VerticalStack gap={5} key={index}>
                <SSOTextfield onClickFunc={sso.onClickFunc} logos={[sso.logo]} text={sso.text} />
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
        api.signupUser(email, password, window.SIGNUP_INVITATION_CODE).then((resp)=> {
          if (resp && resp.indexOf("<")== -1) {
            func.setToast(true, true, "Signup error " + resp)
          }else{
            navigate('/dashboard/onboarding')
          }
        })
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
    descriptionText: "Need to create a new organization?",
    targetUrl: '/signup'
  }

  const signupObject = {
    headingText: "Create new account",
    buttonText: "Sign up",
    linkText: "Sign in",
    descriptionText: "Already using Akto?",
    targetUrl: '/login'
  }

  const activeObject = loginActive ? loginObject : signupObject
  const navigate = useNavigate()

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
          <Button fullWidth primary onClick={loginFunc} size="large" loading={loading}><div data-testid="signin_signup_button">{activeObject.buttonText}</div></Button>
        </VerticalStack>
      </Form>
      <HorizontalStack align="center" gap={1}>
        <Text>{activeObject.descriptionText}</Text>
        <Button plain onClick={() => {setLoginActive(!loginActive); navigate(activeObject.targetUrl)}}>{activeObject.linkText}</Button>
      </HorizontalStack>
    </VerticalStack>
  )

  const customComponent = (
    <VerticalStack gap={8}>
      <Text alignment="center" variant="heading2xl">{activeObject.headingText}</Text>
      <VerticalStack gap={5}>
        {ssoCard}
        {!func.checkLocal() ? <SSOTextfield onClickFunc={() => window.location.href="/sign-in-with-sso"} logos={['/public/azure_logo.svg', '/public/gcp.svg']} text={"Sign in with SSO"} /> : null}
        {signupEmailCard}
      </VerticalStack>
    </VerticalStack>
  )
  
  return (
   <SignUpPageLayout
    customComponent={customComponent}
    />
  )
}

export default SignUp