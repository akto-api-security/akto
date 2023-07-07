import { useState } from "react"
import { useNavigate } from "react-router-dom"
import api from "../api"

import { TextField, Button, Box, Text, HorizontalStack, Divider, VerticalStack, Toast, Frame } from "@shopify/polaris"

import AktoLogo from "../images/akto_logo.png"
import AktoLogoText from "../images/akto_logo_text.png"
import GoogleIcon from "../images/google.png"
import Store from "../../dashboard/store"

const SignUpCard = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const toastConfig = Store(state => state.toastConfig)
  const setToastConfig = Store(state => state.setToastConfig)
  const storeUsername = Store(state => state.storeUsername)

  const isLocalDeploy = window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy'

  const setLoginErrorToast = () => {
      setToastConfig({
        isActive: true,
        isError: true,
        message: "Please, Log in Again"
      })
  }

  const disableLoginErrorToast = () => {
    setToastConfig({
      isActive: false,
      isError: false,
      message: ""
    })
  }

  const loginToastMarkup = toastConfig.isActive ? (
    <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableLoginErrorToast} duration={1500} />
  ) : null;

  const handleEmailChange = (inputEmail) => {
    setEmail(inputEmail)
  }

  const handlePasswordChange = (inputPassword) => {
    setPassword(inputPassword)
  }

  const handleContinueWithEmail = async () => {
    api.login(email, password).then((resp)=>{
      storeUsername(email)
      console.log(resp);
    }).catch((err) => {
      console.log(err);
      setLoginErrorToast()
    })
  }

  return (
      <Box background="bg">
        <div style={{ width: "30vw", margin: "10vh auto" }}>
          <VerticalStack gap="5">
            <span>
              <img src={AktoLogo} />
              <img src={AktoLogoText} style={{ paddingLeft: "5px" }} />
            </span>
            <div>
              <Text variant="headingXl">Getting started</Text>
              <Text variant="bodyLg">Start free, no credit card required</Text>
            </div>

            <Divider />
            <Text variant="bodySm" alignment="center">or</Text>
            <Divider />

            <TextField
              label="Email"
              type="email"
              value={email}
              placeholder="name@workemail.com"
              onChange={handleEmailChange}
              autoComplete="email"
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              placeholder="password"
              onChange={handlePasswordChange}
              autoComplete="password"
            />
            <Button size="large" fullWidth primary onClick={handleContinueWithEmail}>Continue with Email</Button>

            <HorizontalStack gap="3" align="end">
              <Button plain monochrome removeUnderline>Help</Button>
              <Button plain monochrome removeUnderline>Privacy</Button>
              <Button plain monochrome removeUnderline>Terms</Button>
            </HorizontalStack>
          </VerticalStack>
          <div style={{width: "0px", height: "0px"}}>
            <Frame>
              {loginToastMarkup}
            </Frame>
          </div>
        </div>
      </Box>
  )
}

export default SignUpCard
