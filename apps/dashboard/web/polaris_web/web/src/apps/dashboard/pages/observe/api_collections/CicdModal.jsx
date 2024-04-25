import { Text, Modal, TextField, VerticalStack, HorizontalStack, Button, Card } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"

import React from 'react'


function CicdModal(props) {

    const navigate = useNavigate()

    const { active, setActive, CicdRef } = props;

    const primaryAction = () =>{

            navigate('/dashboard/settings/integrations/ci-cd')
    }

    const secondaryAction = () =>{

        setActive(false)
    }

    return (<Modal
        medium
        key="modal"
        activator={CicdRef}
        open={active}
        onClose={() => setActive(false)}
        title="Add to CI/CD pipeline"
        primaryAction={{
            id: "add-ci-cd",
            content: 'Create Token',
            onAction: primaryAction
        }}
        secondaryActions={{
            id: "close-ci-cd",
            content: 'Cancel',
            onAction: secondaryAction
        }}

    >
        <Modal.Section>
        
        
       
                
                    
                        <VerticalStack gap={2}>
             
                        <HorizontalStack gap={2} align="start">
                        <Text> Lorem ipsum dolor sit amet consectetur, adipisicing elit. 
                Fugiat explicabo, quidem earum ratione quaerat deleniti nostrum velit sequi 
                pariatur obcaecati id, <span style={{color:"#3385ff",textDecoration:'underline'}} > Learn More</span>
                </Text> 
                           

                        </HorizontalStack>
                        </VerticalStack>
                  
               
    
        </Modal.Section>

    </Modal>)

}

export default CicdModal