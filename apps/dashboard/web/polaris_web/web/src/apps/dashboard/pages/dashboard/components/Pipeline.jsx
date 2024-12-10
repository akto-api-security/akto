import { Card, DataTable, Scrollable, Text,InlineStack , BlockStack,Modal,Button } from '@shopify/polaris'
import React , {useState} from 'react'
import transform from '../transform'
import { useNavigate, Link} from "react-router-dom"


function Pipeline({riskScoreMap, collections, collectionsMap}) {

    const [active, setActive] = useState(false);

    const handleShowModal = () => {
        setActive(true);
    };

    const navigate = useNavigate();

    function CicdModal({ active, setActive }) {

        
    
        const primaryAction = () => {
            navigate('/dashboard/settings/integrations/ci-cd');
        };
    
        const secondaryAction = () => {
            setActive(false);
        };
    
        return (
            <Modal
                key="modal"
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
                    <BlockStack gap={200}>
                        <InlineStack gap={200} align="start">
                            <Text breakWord truncate>
                            Akto's integration with GitHub enterprise and Github.com allows you to maintain API security through GitHub pull requests itself. &nbsp;

                            <Link to='https://docs.akto.io/api-security-testing/how-to/setup-github-integration-for-ci-cd' target="_blank" rel="noopener noreferrer" style={{ color: "#3385ff", textDecoration: 'none' }}>
                                Learn More
                            </Link>
                            </Text>
                        </InlineStack>
                    </BlockStack>
                </Modal.Section>
            </Modal>
        );
    }



    const tableRows = transform.prepareTableData(riskScoreMap,collections, collectionsMap, setActive);

    return (
        <Card>
            <BlockStack gap={500}>
                <BlockStack gap={200}>
                    <Text variant="bodyLg" fontWeight="semibold">Add in your CI/CD pipeline</Text>
                    <Text>Seamlessly enhance your web application security with CI/CD integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses.</Text>
                </BlockStack>
                <Scrollable style={{maxHeight: '200px', paddingBottom:'10px'}} shadow>
                    <DataTable headings={[]}
                        columnContentTypes={[
                            'text'
                        ]}
                        rows={tableRows}
                        increasedTableDensity
                        truncate
                    /> 
                </Scrollable>
                { active && (
    <CicdModal
        active={active}
        setActive={setActive}
    />
)}
            </BlockStack>
        </Card>
    );
}

export default Pipeline