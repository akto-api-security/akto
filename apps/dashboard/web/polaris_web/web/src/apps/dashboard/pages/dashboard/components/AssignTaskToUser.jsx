import { useState } from "react";
import { Button, Popover, OptionList, Tag } from '@shopify/polaris';

function AssignTaskToUser() {
    const [popoverActive, setPopoverActive] = useState(false);
    const [selectedUser, setSelectedUser] = useState([]);

    // Sample users - in real app, this would come from your users list
    const users = [
        {value: 'user1', label: 'John Doe'},
        {value: 'user2', label: 'Jane Smith'},
        {value: 'user3', label: 'Mike Johnson'},
    ];

    const togglePopoverActive = () => {
        setPopoverActive((active) => !active);
    };

    const handleUserSelect = (value) => {
        setSelectedUser(value);
        setPopoverActive(false);
    };

    const activator = (
        <Button onClick={togglePopoverActive} plain removeUnderline>
            Assign Task
        </Button>
    );

    const assignedUser = selectedUser.length > 0 ? users.find(u => u.value === selectedUser[0]) : null;

    return assignedUser ? (
        <Tag onRemove={() => setSelectedUser([])}>
            {assignedUser.label}
        </Tag>
    ) : (
        <Popover
            active={popoverActive}
            activator={activator}
            onClose={() => setPopoverActive(false)}
            autofocusTarget="first-node"
        >
            <OptionList
                title="Assign to"
                onChange={handleUserSelect}
                options={users}
                selected={selectedUser}
            />
        </Popover>
    );
}

export default AssignTaskToUser;