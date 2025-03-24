import { Button, Form,  HorizontalStack, Modal, Text, TextField, VerticalStack } from '@shopify/polaris'
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
import LocalStore from '../../main/LocalStorageStore'

function SignUp() {

  const location = useLocation()

  const [ssoList, setSsoList] = useState([])
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loginActive, setLoginActive] = useState(location.pathname.includes("login"))
  const [loading, setLoading] = useState(false)

  const [forgotPasswordState, setForgotPasswordState] = useState({
    isForgotPasswordActive: false,
    passwordResetToken: "",
    passwordResetActive: false,
    newPassword: "",
    newConfirmPassword: ""
  })

  const setForgotPasswordStateHelper = (field, value) => {
    setForgotPasswordState(prevState => ({
      ...prevState,
      [field]: value
    }))
  }

  const activeSSO = window.ACTIVE_SSO
  const githubAuthUrl = window.GITHUB_AUTH_URL
  const oktaAuthUrl = window.OKTA_AUTH_URL
  const resetAll = PersistStore(state => state.resetAll)
  const resetStore = LocalStore(state => state.resetStore)
  const { clearPollingInterval } = usePolling();

  const githubAuthObj = {
    logo: '/public/github_icon.svg',
    text: 'Continue with Github SSO',
    onClickFunc: () => { window.location.href = githubAuthUrl }
  }

  const oktaAuthObj = {
    logo: '/public/okta_logo.svg',
    text: 'Continue with Okta SSO',
    onClickFunc: () => { window.location.href = oktaAuthUrl }
  }

  const azureAuthObj = {
    logo: '/public/azure_logo.svg',
    text: 'Continue with Azure SSO',
    onClickFunc: () => { window.location.href = "/trigger-saml-sso" }
  }

  const googleSamlAuthObj = {
    logo: '/public/gcp.svg',
    text: 'Continue with Google SAML SSO',
    onClickFunc: () => { window.location.href = "/trigger-saml-sso" }
  }

  useEffect(() => {
    resetAll()
    resetStore()
    clearPollingInterval()
    let copySsoList = []
    switch (activeSSO?.toLowerCase()) {
      case "github":
        if (githubAuthUrl?.length > 0) {
          copySsoList.push(githubAuthObj);
        }
        break;
      case "okta":
        if (oktaAuthUrl?.length > 0) {
          copySsoList.push(oktaAuthObj);
        }
        break;
      case "azure":
        copySsoList.push(azureAuthObj);
        break;
      case "google_saml":
        copySsoList.push(googleSamlAuthObj);
        break;
      default:
        break;
    }

    setSsoList(copySsoList)

    if (window.IS_SAAS && window.IS_SAAS === "true") {
      navigate('/dashboard/observe/inventory');
    }
  }, [])

  useEffect(() => {
    const queryParams = new URLSearchParams(window.location.search)
    const tokenFromUrl = queryParams.get('token')
    setForgotPasswordStateHelper("passwordResetToken", tokenFromUrl)
    setForgotPasswordStateHelper("passwordResetActive", tokenFromUrl && tokenFromUrl.length > 0)
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

  const websiteHostName = window.location.origin

  const handleForgotPassword = async () => {
    await api.sendPasswordResetLink(email).then((resp) => {
      func.setToast(true, false, "Reset password link has been sent!")
    }).catch((error) => {
      if(error?.code === "ERR_NETWORK") {
        func.setToast(true, true, "Unable to send email. Please contact your account admin to reset your password.")
        return
      }
      const errorMessage = error?.response?.data || "Reset password link has been sent!"
      const errorStatus = error?.response?.data !== undefined && error?.response?.data.length > 0
      func.setToast(true, errorStatus, errorMessage)
    }).finally(() => {
      setForgotPasswordStateHelper("isForgotPasswordActive", false)
      setEmail("")
    })
  }

  const forgotPasswordComp = (
    <Modal
      small
      activator={
        <div style={{textAlign: 'end'}}>
          <Button plain onClick={() => setForgotPasswordStateHelper("isForgotPasswordActive", true)}>Forgot Password?</Button>
        </div>
      }
      open={forgotPasswordState.isForgotPasswordActive}
      onClose={() => setForgotPasswordStateHelper("isForgotPasswordActive", false)}
      title="Forgot Password"
      primaryAction={{
          content: 'Send',
          onAction: handleForgotPassword,
      }}
      secondaryActions={[
          {
              content: 'Cancel',
              onAction: () => setForgotPasswordStateHelper("isForgotPasswordActive", false),
          },
      ]}

    >
      <Modal.Section>
          <TextField
              label="Email"
              value={email}
              inputMode='email'
              placeholder="name@workemail.com"
              onChange={(email) => setEmail(email)}
              autoComplete="off"
          />
          <Text variant="bodyMd" color="subdued">We'll use this email to send a password reset link.</Text>
      </Modal.Section>
    </Modal>
  )

  const handleResetPassword = async () => {
    if(!forgotPasswordState.passwordResetToken || forgotPasswordState.passwordResetToken.length === 0) return
    if(!func.validatePassword(forgotPasswordState.newPassword, forgotPasswordState.newConfirmPassword)) return

    await api.resetPassword(forgotPasswordState.passwordResetToken, forgotPasswordState.newPassword).then(() => {
      func.setToast(true, false, "Password changed successfully!")
    }).catch((error) => {
      const errorMessage = error?.response?.data || "Password reset link is expired or invalid."
      func.setToast(true, true, errorMessage)
    }).finally(() => {
      setForgotPasswordStateHelper("passwordResetActive", false)
      const urlWithoutToken = window.location.pathname
      window.history.replaceState({}, document.title, urlWithoutToken)
    })
  }

  const resetPasswordComp = (
    <Modal
      small
      open={forgotPasswordState.passwordResetActive}
      onClose={() => setForgotPasswordStateHelper("passwordResetActive", false)}
      title="Reset Password"
      primaryAction={{
          content: 'Save',
          onAction: handleResetPassword,
      }}
      secondaryActions={[
          {
              content: 'Cancel',
              onAction: () => setForgotPasswordStateHelper("passwordResetActive", false),
          },
      ]}

    >
      <Modal.Section>
          <PasswordTextField
              label="Password"
              field={forgotPasswordState.newPassword}
              setField={(val) => setForgotPasswordStateHelper("newPassword", val)}
              monospaced={true}
              onFunc={true}
          />
          <PasswordTextField
              label="Confirm Password"
              field={forgotPasswordState.newConfirmPassword}
              setField={(val) => setForgotPasswordStateHelper("newConfirmPassword", val)}
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
            <TextField onChange={setEmail} inputMode='email' value={forgotPasswordState.isForgotPasswordActive ? "" : email} label="Email" placeholder="name@workemail.com" monospaced={true}/>
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

  const customComponent = (
    <VerticalStack gap={8}>
      <Text alignment="center" variant="heading2xl">{activeObject.headingText}</Text>
      <VerticalStack gap={5}>
        {ssoCard}
        {signupEmailCard}
        {loginActive && isOnPrem && resetPasswordComp}
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