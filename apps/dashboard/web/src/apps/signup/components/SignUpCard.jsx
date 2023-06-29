import { useState } from "react"
import { useNavigate } from "react-router-dom"

import { TextField, Button, Box, Text, HorizontalStack, Divider, VerticalStack} from "@shopify/polaris"

import AktoLogo from "../images/akto_logo.png"
import AktoLogoText from "../images/akto_logo_text.png"
import GoogleIcon from "../images/google.png"

const SignUpCard = () => {
    const navigate = useNavigate()
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
  
    const handleEmailChange = (inputEmail) => {
      setEmail(inputEmail)
    } 
  
    const handlePasswordChange = (inputPassword) => {
      setPassword(inputPassword)
    } 
  
    const handleContinueWithEmail = async () => {
      try {
        const res = await axios.post("/auth/login", {
          username: email,
          password: password
        })
      } catch(err) {
        console.log(res)
      }
      

      if (loginReq)
      navigate("/")
    }
  
    return (
      <Box background="bg">
        <div style={{width: "30vw", margin: "10vh auto "}}>
          <VerticalStack gap="5">
            <span>
              <img src={AktoLogo}/>
              <img src={AktoLogoText} style={{paddingLeft: "5px"}}/>
            </span>
            <div>
              <Text variant="headingXl">Getting started</Text>
              <Text variant="bodyLg">Start free, no credit card required</Text>
            </div>

            <Button size="large" fullWidth="true" icon={GoogleIcon}>Continue with Google</Button>
            
            <Divider/>
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
        </div>
      </Box>
    )
  }

export default SignUpCard
  