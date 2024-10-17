import { Avatar, Box, Button, Form, Frame, HorizontalStack, Modal, Page, Text, TextField, Toast, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import SSOTextfield from './SSOTextfield'
import PasswordTextField from '../../dashboard/components/layouts/PasswordTextField'
import { useLocation, useNavigate } from "react-router-dom"
import api from '../api'
import func from '@/util/func'
import "../styles.css"
import Store from '../../dashboard/store'
import PersistStore from '../../main/PersistStore'
import { usePolling } from '../../main/PollingProvider'

function SignUp() {

  const location = useLocation()

  const [ssoList, setSsoList] = useState([])
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loginActive, setLoginActive] = useState(location.pathname.includes("login"))
  const [loading, setLoading] = useState(false)

  const [isForgotPasswordActive, setIsForgotPasswordActive] = useState(false)
  const [forgotPasswordEmail, setForgotPasswordEmail] = useState("")
  const [passwordResetToken, setPasswordResetToken] = useState("")
  const [passwordResetActive, setPasswordResetActive] = useState(false)
  const [newPassword, setNewPassword] = useState("")
  const [newConfirmPassword, setNewConfirmPassword] = useState("")

  const oktaUrl = window.OKTA_AUTH_URL
  const azureUrl = window.AZURE_REQUEST_URL
  const githubId = window.GITHUB_CLIENT_ID
  const githubUrl = window.GITHUB_URL ? window.GITHUB_URL : "https://github.com"
  const resetAll = PersistStore(state => state.resetAll)
  const { clearPollingInterval } = usePolling();

  const githubAuthObj = {
    logo: '/public/github_icon.svg',
    text: 'Continue with Github SSO',
    onClickFunc: () => { window.location.href = (githubUrl + "/login/oauth/authorize?client_id=" + githubId); }
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
    resetAll()
    clearPollingInterval()
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
      navigate('/dashboard/observe/inventory');
    }
  }, [])

  useEffect(() => {
    const queryParams = new URLSearchParams(window.location.search)
    const tokenFromUrl = queryParams.get('token')
    setPasswordResetToken(tokenFromUrl)
    setPasswordResetActive(tokenFromUrl && tokenFromUrl.length > 0)
  }, [])

  useEffect(() => {
    if(func.validateEmail(email)) {
      setForgotPasswordEmail(email)
    } else {
      setForgotPasswordEmail("")
    }
  }, [email])

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
  
  const websiteHostName = window.location.origin

  const handleForgotPassword = async () => {
    if(!func.validateEmail(forgotPasswordEmail)) {
      func.setToast(true, true, "Please enter a valid email!")
      return
    }
    
    await api.sendPasswordResetLink(forgotPasswordEmail, websiteHostName).then((resp) => {
      func.setToast(true, false, "Reset password link has been sent!")
    }).catch((error) => {
      if(error?.response?.status === 429) {
        func.setToast(true, error, "Too many requests. Please try again later.")
      } else {
        const errorMessage = error?.response?.data || "Reset password link has been sent!"
        const errorStatus = error?.response?.data !== undefined && error?.response?.data.length > 0
        func.setToast(true, errorStatus, errorMessage)
      }
    }).finally(() => {
      setIsForgotPasswordActive(false)
      setForgotPasswordEmail("")
    })
  }

  const forgotPasswordComp = (
    <Modal
      small
      activator={
        <div style={{textAlign: 'end'}}>
          <Button plain onClick={() => setIsForgotPasswordActive(true)}>Forgot Password?</Button>
        </div>
      }
      open={isForgotPasswordActive}
      onClose={() => setIsForgotPasswordActive(false)}
      title="Forgot Password"
      primaryAction={{
          content: 'Send',
          onAction: handleForgotPassword,
      }}
      secondaryActions={[
          {
              content: 'Cancel',
              onAction: () => setIsForgotPasswordActive(false),
          },
      ]}
      
    >
      <Modal.Section>
          <TextField
              label="Email"
              value={forgotPasswordEmail}
              placeholder="name@workemail.com"
              onChange={(email) => setForgotPasswordEmail(email)}
              autoComplete="off"
          />
          <Text variant="bodyMd" color="subdued">We'll use this email to send a password reset link.</Text>
      </Modal.Section>
    </Modal>
  )

  const handleResetPassword = async () => {
    if(!passwordResetToken || passwordResetToken.length === 0) return
    if(!func.validatePassword(newPassword, newConfirmPassword)) return

    await api.resetPassword(passwordResetToken, newPassword).then(() => {
      func.setToast(true, false, "Password changed successfully!")
    }).catch((error) => {
      if(error?.response?.status === 429) {
        func.setToast(true, error, "Too many requests. Please try again later.")
      } else {
        const errorMessage = error?.response?.data || "Password reset link is expired or invalid."
        func.setToast(true, true, errorMessage)
      }
    }).finally(() => {
      setPasswordResetActive(false)
      const urlWithoutToken = window.location.pathname
      window.history.replaceState({}, document.title, urlWithoutToken)
    })
  }

  const resetPasswordComp = (
    <Modal
      small
      open={passwordResetActive}
      onClose={() => setPasswordResetActive(false)}
      title="Reset Password"
      primaryAction={{
          content: 'Save',
          onAction: handleResetPassword,
      }}
      secondaryActions={[
          {
              content: 'Cancel',
              onAction: () => setPasswordResetActive(false),
          },
      ]}
      
    >
      <Modal.Section>
          <PasswordTextField
              label="Password"
              field={newPassword}
              setField={(val) => setNewPassword(val)}
              monospaced={true}
              onFunc={true} 
          />
          <PasswordTextField
              label="Confirm Password"
              field={newConfirmPassword}
              setField={(val) => setNewConfirmPassword(val)}
              monospaced={true}
              onFunc={true} 
          />
      </Modal.Section>
    </Modal>
  )

  const notOnPremHostnames = ["app.akto.io", "localhost", "127.0.0.1", "[::1]"]
  const isOnPrem = websiteHostName && !notOnPremHostnames.includes(window.location.hostname)

  const signupEmailCard = (
    <VerticalStack gap={4}>
      <Form onSubmit={loginFunc}>
        <VerticalStack gap={4}>
          <div className='form-class'>
            <TextField onChange={setEmail} value={email} label="Email" placeholder="name@workemail.com" monospaced={true}/>
          </div>
          <div className='form-class'>
            <PasswordTextField setField={(val) => setPassword(val)} onFunc={true} field={password} label="Password" monospaced={true}/>
            {loginActive && isOnPrem && forgotPasswordComp}
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

  const toastConfig = Store(state => state.toastConfig)
  const setToastConfig = Store(state => state.setToastConfig)

  const disableToast = () => {
    setToastConfig({
      isActive: false,
      isError: false,
      message: ""
    })
  }

  const toastMarkup = toastConfig.isActive ? (
    <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={2000} />
  ) : null;
  
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
                        {loginActive && isOnPrem && resetPasswordComp}
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
        {toastMarkup}
      </Frame>
    </div>
  )
}

export default SignUp