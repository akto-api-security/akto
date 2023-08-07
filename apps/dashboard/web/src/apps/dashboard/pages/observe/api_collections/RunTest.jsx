import { Button, Modal, TextContainer } from "@shopify/polaris";
import { useCallback, useRef, useState } from "react";


function RunTest() {
    const [active, setActive] = useState(false);

    const runTestRef = useRef(null);

    const toggleRunTest = () => setActive(prev => !prev)

    const activator = (
        <div ref={runTestRef}>
            <Button onClick={toggleRunTest} primary>Run Test</Button>
        </div>
    );

    return (
        // <Button primary>Run Test</Button>
        <div>
            {activator}
            <Modal
                activator={runTestRef}
                open={active}
                onClose={toggleRunTest}
                title="Configure test"
                primaryAction={{
                    content: 'Run once now',
                    onAction: toggleRunTest,
                }}
            >
                <Modal.Section>
                    <TextContainer>
                        <p>
                            Use Instagram posts to share your products with millions of
                            people. Let shoppers buy from your store without leaving
                            Instagram.
                        </p>
                    </TextContainer>
                </Modal.Section>
            </Modal>
        </div>
    )
}

export default RunTest