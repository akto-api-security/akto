import { Avatar, Box, Button, HorizontalStack } from '@shopify/polaris';
import React from 'react'
import TooltipText from '../../../../components/shared/TooltipText';

function SelectRepoComp(props) {
    const {repo, project, icon} = props.cardObj
    
    return(
        <Button removeUnderline plain monochrome onClick={() => props.onButtonClick(repo, project)}>
            <Box width="200px">
                <HorizontalStack gap={"2"} wrap={false}>
                    <Avatar size="extraSmall" source={icon}/>
                    <HorizontalStack wrap={false} >
                        <Box maxWidth='165px'>
                            <TooltipText text={repo + "/" + project} tooltip={repo + "/" + project} textProps={{variant: 'bodySm', color: 'subdued'}}/>
                        </Box>
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
        </Button>
    )
}


export default SelectRepoComp