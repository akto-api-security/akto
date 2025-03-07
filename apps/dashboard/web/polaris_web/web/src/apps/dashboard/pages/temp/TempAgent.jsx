import React, { useState } from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { LegacyCard, Text, TextField } from '@shopify/polaris'
import api from '../agent_team/api';

function TempAgent() {

    const [projectDir, setProjectDir] = useState('');

    const handleSubmit = async() => {
        await api.createAgentRun({
            agent: "FIND_VULNERABILITIES_FROM_SOURCE_CODE",
            data: {
                projectDir: projectDir
            }
        })
    }

    const initComponent = (
        <LegacyCard title= "Init vulnerability agent" primaryFooterAction={{content: "Init", onAction: () => handleSubmit()}}>
            <LegacyCard.Section >
                <TextField label="Project directory" value={projectDir} onChange={setProjectDir} /> 
            </LegacyCard.Section>
        </LegacyCard>
    )

    const components = [initComponent];
    return (
        <PageWithMultipleCards
            title={<Text variant="headingLg">Agents</Text>}
            components={components}
            firstPage={true}
            divider
        />
    )
}

export default TempAgent