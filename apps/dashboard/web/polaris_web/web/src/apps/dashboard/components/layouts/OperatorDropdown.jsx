import { Popover, Button, ActionList } from "@shopify/polaris"
import { useState } from "react"

function OperatorDropdown(props) {

    const { label, items, selected, designer } = props
    const [popoverActive, setPopoverActive] = useState(false);
    const togglePopoverActive = () => setPopoverActive((popoverActive) => !popoverActive);

    return (

        <div style={{ display: "flex", justifyContent: "center", alignItems: "center" }}>
            <Popover
                active={popoverActive}
                activator={
                    designer ?
                        <Button onClick={togglePopoverActive} disclosure>{label}</Button>
                        :
                        <Button onClick={togglePopoverActive} disclosure plain removeUnderline>{label}</Button>
                }
                onClose={togglePopoverActive}
            >
                <ActionList
                    actionRole="menuitem"
                    items={
                        items.map((item) => {
                            return {
                                content: item.label,
                                onAction: () => { selected(item.value); togglePopoverActive(); },
                            }
                        })
                    }
                />
            </Popover>
        </div>
    )

}

export default OperatorDropdown
