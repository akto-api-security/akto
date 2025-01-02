import { Modal, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import func from '@/util/func'
import homeRequests from "../pages/home/api"

const WelcomeBackDetailsModal = ({ isAdmin }) => {

    const extractEmailDetails = (email) => {
        // Define the regex pattern
        const pattern = /^(.*?)@([\w.-]+)\.[a-z]{2,}$/;
      
        // Match the regex pattern
        const match = email.match(pattern);
      
        if (match) {
          let rawUsername = match[1]; // Extract username
          let mailserver = match[2]; // Extract mailserver (including subdomains)
      
          let username = rawUsername
          .split(/[^a-zA-Z]+/) // Split by any non-alphabet character
          .filter(Boolean) // Remove empty segments
          .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1)) // Capitalize each segment
          .join(' '); // Join segments with a space
              
          mailserver = mailserver.charAt(0).toUpperCase() + mailserver.slice(1);
      
          return { username, mailserver };
        } else {
          return { error: "Invalid email format" };
        }
      };
            
      function suggestOrgName() {
        if (window.ORGANIZATION_NAME?.length > 0) {
            return window.ORGANIZATION_NAME
        } else {
            return extractEmailDetails(window.USER_NAME)?.mailserver || ""
        }
    }

    function suggestFullName() {
        if (window.USER_FULL_NAME?.length > 0) {
            return window.USER_FULL_NAME
        } else {
            return extractEmailDetails(window.USER_NAME)?.username || ""
        }
    }

    const [modalToggle, setModalToggle] = useState(!window.localStorage.getItem("username"))

    const [username, setUsername] = useState(suggestFullName())
    const [organization, setOrganization] = useState(suggestOrgName())

    const handleWelcomeBackDetails = async () => {

        const nameReg = /^[\w\s-]{1,}$/;
        const orgReg = /^[\w\s.&-]{1,}$/;
        if(!nameReg.test(username.trim()) || (isAdmin && !orgReg.test(organization.trim()))) {
            func.setToast(true, true, "Please enter valid details.")
            return
        }

        if  (window.Intercom) {
            let updateObj = {name: username}
            if (window.USER_NAME.indexOf("@")> 0) {
                updateObj["company"] = {name: organization, company_id: window.USER_NAME.split("@")[1]}
            }
            window.Intercom("update", updateObj)
        }

        if (window.mixpanel) {
            const mixpanelUserProps = {
                "name": username,
                "company": organization,
                "$name": username,
                "$company": organization
            }
            window.mixpanel.people.set(mixpanelUserProps);
        }

        window.localStorage.setItem("username", username)
        window.localStorage.setItem("organization", organization)        

        homeRequests.updateUsernameAndOrganization(username, organization).then((resp) => {
            try {
                setModalToggle(false)
            } catch (error) {
                func.setToast(true, true, "Invalid username or organization name")
            }
        })
    }

    return (
        <Modal
            size="small"
            open={modalToggle}
            title={<div className='welcome-back-modal-title'>Welcome back</div>}
            primaryAction={{
                content: 'Save',
                onAction: () => handleWelcomeBackDetails()
            }}
        >
            <Modal.Section>
                <VerticalStack gap={4}>
                    <Text variant="bodyMd" color="subdued">
                        Please tell us more...
                    </Text>

                    <TextField
                        label="Your full name"
                        value={username}
                        disabled={window.USER_FULL_NAME.length !== 0}
                        placeholder="Joe doe"
                        onChange={setUsername}
                        autoComplete="off"
                        maxLength="40"
                        suffix={(
                            (username.length > 20) && <Text>{username.length}/40</Text>
                        )}
                    />

                    {
                        isAdmin && <TextField
                            label="Your organization name"
                            disabled={!isAdmin || window.ORGANIZATION_NAME.length !== 0}
                            value={organization}
                            placeholder="Acme Inc"
                            onChange={setOrganization}
                            autoComplete="off"
                            maxLength={"24"}
                            suffix={(
                                (organization.length) > 20 && <Text>{organization.length}/40</Text>
                            )}
                        />
                    }
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default WelcomeBackDetailsModal