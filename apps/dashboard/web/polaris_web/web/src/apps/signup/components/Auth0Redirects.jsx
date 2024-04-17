import React from 'react'
import { Avatar, Box, Button, HorizontalStack, Page, Text, VerticalStack } from '@shopify/polaris'
import { useSearchParams } from 'react-router-dom' 
import "../styles.css"
import axios from 'axios'

function Auth0Redirects({bodyText, errorText}) {
    const [searchParams, setSearchParams] = useSearchParams();
    const state = searchParams.get("state")
    const handleLogout = () => {
        
        if(state){
            let url = "/addUserToAccount?state=" + state;
            axios.post("/auth0-logout", {"redirectUrl": url}).then((resp) => {
                if(resp.data.logoutUrl){
                    window.location.href = resp.data.logoutUrl;
                    return;
                }
                window.location.href = "/login"
            });
        } else {
            axios.get("/auth0-logout").then((resp) => {
                if(resp.data.logoutUrl){
                    window.location.href = resp.data.logoutUrl;
                    return;
                }
                window.location.href = "/login"
            });
        }
    }

    return (
        <Page fullWidth>
            <Box padding={"20"} paddingBlockEnd={"0"}>
                <HorizontalStack align='center'>
                    <Box width='400px'>
                        <VerticalStack gap={"8"}>
                            <HorizontalStack align="center">
                                <div className="akto-logo">
                                    <Avatar source="/public/akto_name_with_logo.svg" shape="round" size="2xl-experimental" />
                                </div>
                            </HorizontalStack>
                            <VerticalStack gap={"6"}>
                                <HorizontalStack align='center'>
                                    <svg xmlns="http://www.w3.org/2000/svg" width="64" height="65" viewBox="0 0 64 65" fill="none">
                                        <g clipPath="url(#clip0_7639_55607)">
                                            <path d="M0 17.4927V50.0545C0 52.7055 2.14903 54.8545 4.8 54.8545H59.2C61.851 54.8545 64 52.7055 64 50.0545V17.4924L33.6121 35.2187C32.6158 35.7999 31.3837 35.7999 30.3874 35.2187L0 17.4927Z" fill="#5C5F62"/>
                                            <path d="M62.2186 11.1223C61.394 10.4545 60.3437 10.0545 59.2 10.0545H4.8C3.6562 10.0545 2.60584 10.4546 1.78122 11.1224L31.9997 28.7499L62.2186 11.1223Z" fill="#5C5F62"/>
                                        </g>
                                        <defs>
                                            <clipPath id="clip0_7639_55607">
                                            <rect width="64" height="64" fill="white" transform="translate(0 0.454529)"/>
                                            </clipPath>
                                        </defs>
                                    </svg>
                                </HorizontalStack>
                                <VerticalStack gap={"2"}>
                                    <Text alignment="center" variant="headingLg">{errorText}</Text>
                                    <Text alignment="center" variant="bodyMd" color="subdued">{bodyText}</Text>
                                </VerticalStack>
                            </VerticalStack>
                            <Button fullWidth onClick={handleLogout}>
                                Redirect to login
                            </Button>
                        </VerticalStack>
                    </Box>
                </HorizontalStack>
                <div style={{position: 'absolute' , bottom: '3vh', width: '84%'}}>
                    <HorizontalStack gap={3} align="center">
                        <Button plain onClick={() => window.open("https://www.akto.io/terms-and-policies","_blank")}>Terms of use</Button>
                            <div style={{width: '1px', height: '24px', background: "#E1E3E5"}} />
                        <Button plain onClick={() => window.open("https://www.akto.io/terms/privacy","_blank")}>Privacy policy</Button>
                    </HorizontalStack>
                </div>
            </Box>
        </Page>
    )
}

export default Auth0Redirects